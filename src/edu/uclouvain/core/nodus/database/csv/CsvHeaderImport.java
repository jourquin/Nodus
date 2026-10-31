/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>http://www.uclouvain.be
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.database.csv;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableImport;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Two-pass CSV header imports with inferred schemas and validated, transactional publication. */
final class CsvHeaderImport {
  private static final Pattern NUMBER =
      Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
  private static final Pattern LEADING_ZERO = Pattern.compile("[+-]?0[0-9].*");

  private CsvHeaderImport() {}

  @SuppressWarnings("resource") // The project owns and closes its JDBC connection.
  static boolean importTable(NodusProject project, String table, Consumer<String> reportError) {
    Path source =
        Path.of(project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + table + NodusC.TYPE_CSV);
    if (!Files.isRegularFile(source)) {
      return false;
    }
    try {
      Connection connection = project.getMainJDBCConnection();
      if (connection == null) {
        throw new SQLException("No project database connection");
      }
      boolean existing = JDBCUtils.tableExists(connection, table);
      Plan plan = scan(connection, source, table, existing);
      if (existing) {
        replaceRows(connection, source, table, plan, project.getSqlBatchSize());
      } else {
        TableImport.replace(
            project,
            table,
            (staging, name) -> {
              try (Statement statement = staging.createStatement()) {
                statement.execute(plan.createStatement(staging, name));
              }
              load(staging, source, name, plan, project.getSqlBatchSize());
            });
      }
      return true;
    } catch (Exception error) {
      reportError.accept(error.getMessage() == null ? error.toString() : error.getMessage());
      return false;
    }
  }

  private static Plan scan(Connection connection, Path source, String table, boolean existing)
      throws Exception {
    Plan plan = new Plan();
    plan.fingerprint =
        read(
            source,
            csv -> {
              Iterator<CSVRecord> records = csv.iterator();
              plan.headers = header(records);
              if (existing) {
                try (Statement statement = connection.createStatement();
                    ResultSet rows =
                        statement.executeQuery("SELECT * FROM " + quoted(table) + " WHERE 1=0")) {
                  ResultSetMetaData metadata = rows.getMetaData();
                  if (plan.headers.size() != metadata.getColumnCount()) {
                    throw new SQLException(
                        "CSV header has "
                            + plan.headers.size()
                            + " columns; table "
                            + table
                            + " expects "
                            + metadata.getColumnCount());
                  }
                  Set<Integer> sources = new HashSet<>();
                  for (int i = 0; i < metadata.getColumnCount(); i++) {
                    Column column = new Column(metadata.getColumnName(i + 1), -1);
                    for (int h = 0; h < plan.headers.size(); h++) {
                      if (column.name.equalsIgnoreCase(plan.headers.get(h))) {
                        column.source = h;
                        break;
                      }
                    }
                    if (column.source < 0) {
                      throw new SQLException("CSV header is missing table column: " + column.name);
                    }
                    if (!sources.add(column.source)) {
                      throw new SQLException(
                          "Ambiguous CSV header mapping for table column: " + column.name);
                    }
                    column.type = metadata.getColumnType(i + 1);
                    column.precision = metadata.getPrecision(i + 1);
                    column.scale = metadata.getScale(i + 1);
                    column.nullable = metadata.isNullable(i + 1) != ResultSetMetaData.columnNoNulls;
                    plan.columns.add(column);
                  }
                }
              } else {
                for (int i = 0; i < plan.headers.size(); i++) {
                  plan.columns.add(new Column(plan.headers.get(i), i));
                }
              }
              while (records.hasNext()) {
                CSVRecord row = records.next();
                plan.checkWidth(row);
                for (Column column : plan.columns) {
                  if (existing) {
                    column.value(row);
                  } else {
                    column.observe(row.get(column.source));
                  }
                }
              }
              if (!existing) {
                for (Column column : plan.columns) {
                  column.finishInference();
                }
              }
            });
    return plan;
  }

  private static List<String> header(Iterator<CSVRecord> records) throws SQLException {
    if (!records.hasNext()) {
      throw new SQLException("CSV has no header record");
    }
    CSVRecord row = records.next();
    List<String> names = new ArrayList<>();
    Set<String> unique = new HashSet<>();
    for (int i = 0; i < row.size(); i++) {
      String name = row.get(i);
      if (i == 0 && name.startsWith("\uFEFF")) {
        name = name.substring(1);
      }
      name = name.trim();
      if (name.isEmpty() || !unique.add(name.toUpperCase(Locale.ROOT))) {
        throw problem(row, i, "empty or duplicate header name: " + name);
      }
      names.add(name);
    }
    return names;
  }

  private static void replaceRows(
      Connection connection, Path source, String table, Plan plan, int batchSize) throws Exception {
    boolean autoCommit = connection.getAutoCommit();
    boolean restore = autoCommit;
    Savepoint savepoint = null;
    try {
      if (autoCommit) {
        connection.setAutoCommit(false);
      }
      savepoint = connection.setSavepoint();
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM " + quoted(table));
      }
      load(connection, source, table, plan, batchSize);
      if (autoCommit) {
        connection.commit();
      } else {
        connection.releaseSavepoint(savepoint);
      }
    } catch (Exception error) {
      try {
        if (savepoint != null) {
          connection.rollback(savepoint);
        } else if (autoCommit) {
          connection.rollback();
        }
      } catch (SQLException recovery) {
        restore = false;
        error.addSuppressed(recovery);
      }
      throw error;
    } finally {
      if (restore) {
        connection.setAutoCommit(true);
      }
    }
  }

  private static void load(
      Connection connection, Path source, String table, Plan plan, int batchSize) throws Exception {
    String parameters = String.join(",", java.util.Collections.nCopies(plan.columns.size(), "?"));
    boolean batching = connection.getMetaData().supportsBatchUpdates();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO " + quoted(table) + " VALUES (" + parameters + ")")) {
      byte[] fingerprint =
          read(
              source,
              csv -> {
                Iterator<CSVRecord> records = csv.iterator();
                if (!plan.headers.equals(header(records))) {
                  throw new SQLException("CSV header changed during import");
                }
                int pending = 0;
                while (records.hasNext()) {
                  CSVRecord row = records.next();
                  plan.checkWidth(row);
                  for (int i = 0; i < plan.columns.size(); i++) {
                    Column column = plan.columns.get(i);
                    Object value = column.value(row);
                    if (value == null) {
                      statement.setNull(i + 1, column.type);
                    } else {
                      statement.setObject(i + 1, value);
                    }
                  }
                  if (batching) {
                    statement.addBatch();
                    if (++pending >= Math.max(1, batchSize)) {
                      statement.executeBatch();
                      statement.clearBatch();
                      pending = 0;
                    }
                  } else {
                    statement.executeUpdate();
                  }
                }
                if (pending > 0) {
                  statement.executeBatch();
                  statement.clearBatch();
                }
              });
      if (!Arrays.equals(plan.fingerprint, fingerprint)) {
        throw new SQLException("CSV file changed during import; retry with a stable source file");
      }
    }
  }

  /** Consumes one file pass while hashing the actual bytes parsed, without retaining records. */
  private static byte[] read(Path source, Visitor visitor) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = new DigestInputStream(Files.newInputStream(source), digest);
        Reader reader = new InputStreamReader(input, Charset.defaultCharset());
        CSVParser csv = CSVFormat.RFC4180.parse(reader)) {
      visitor.read(csv);
      return digest.digest();
    }
  }

  @FunctionalInterface
  private interface Visitor {
    void read(CSVParser csv) throws Exception;
  }

  private static String quoted(String table) {
    return JDBCUtils.getQuotedCompliantIdentifier(table);
  }

  private static SQLException problem(CSVRecord row, int column, String message) {
    return new SQLException(
        "CSV record " + row.getRecordNumber() + ", column " + (column + 1) + ": " + message);
  }

  private static final class Plan {
    List<String> headers;
    final List<Column> columns = new ArrayList<>();
    byte[] fingerprint;

    void checkWidth(CSVRecord row) throws SQLException {
      if (row.size() != headers.size()) {
        throw new SQLException(
            "CSV record "
                + row.getRecordNumber()
                + " has "
                + row.size()
                + " fields; expected "
                + headers.size());
      }
    }

    String createStatement(Connection connection, String table) throws SQLException {
      List<String> definitions = new ArrayList<>();
      String quote = connection.getMetaData().getIdentifierQuoteString().trim();
      if (quote.isEmpty()) {
        throw new SQLException("Database cannot quote CSV column names");
      }
      for (Column column : columns) {
        String name = JDBCUtils.getCompliantIdentifier(column.name);
        definitions.add(
            quote + name.replace(quote, quote + quote) + quote + " " + column.definition);
      }
      return "CREATE TABLE " + quoted(table) + " (" + String.join(",", definitions) + ")";
    }
  }

  private static final class Column {
    final String name;
    int source;
    int type;
    int precision;
    int scale;
    boolean nullable = true;
    String definition;
    private String kind = "";
    private int length = 1;
    private int integers;
    private boolean fitsLong = true;

    Column(String name, int source) {
      this.name = name;
      this.source = source;
    }

    void observe(String value) {
      length = Math.max(length, value.length());
      if (value.isEmpty()) {
        return;
      }
      String next = "text";
      if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
        next = "boolean";
      } else if (NUMBER.matcher(value).matches() && !LEADING_ZERO.matcher(value).matches()) {
        try {
          BigDecimal number = new BigDecimal(value);
          integers =
              Math.max(
                  integers,
                  (int) Math.min(39L, Math.max(0L, (long) number.precision() - number.scale())));
          scale = Math.max(scale, Math.min(39, Math.max(0, number.scale())));
          if (integers > 19 || number.stripTrailingZeros().scale() > 0) {
            fitsLong = false;
          }
          if (fitsLong) {
            try {
              number.longValueExact();
            } catch (ArithmeticException outsideLong) {
              fitsLong = false;
            }
          }
          next = "number";
        } catch (NumberFormatException invalid) {
          next = "text";
        }
      }
      kind = kind.isEmpty() ? next : kind.equals(next) ? kind : "text";
    }

    void finishInference() {
      precision = Math.max(1, integers + scale);
      if (kind.equals("number") && precision <= 38) {
        type = fitsLong && scale == 0 ? Types.BIGINT : Types.DECIMAL;
        definition = type == Types.BIGINT ? "BIGINT" : "DECIMAL(" + precision + "," + scale + ")";
      } else if (kind.equals("boolean")) {
        type = Types.BOOLEAN;
        definition = "BOOLEAN";
      } else {
        type = Types.VARCHAR;
        precision = length;
        definition = "VARCHAR(" + precision + ")";
      }
    }

    private static void checkInteger(BigDecimal number, int digits) {
      if ((number.signum() != 0 && (long) number.precision() - number.scale() > digits)
          || number.stripTrailingZeros().scale() > 0) {
        throw new IllegalArgumentException("expected a whole number within the column range");
      }
    }

    Object value(CSVRecord row) throws SQLException {
      String value = row.get(source);
      try {
        if (value.isEmpty()) {
          if (!nullable) {
            throw new IllegalArgumentException("empty field for a NOT NULL column");
          }
          return null;
        }
        switch (type) {
          case Types.CHAR:
          case Types.VARCHAR:
          case Types.LONGVARCHAR:
          case Types.NCHAR:
          case Types.NVARCHAR:
          case Types.LONGNVARCHAR:
          case Types.CLOB:
          case Types.NCLOB:
            if (precision > 0 && value.codePointCount(0, value.length()) > precision) {
              throw new IllegalArgumentException("text exceeds column length " + precision);
            }
            return value;
          case Types.TINYINT:
          case Types.SMALLINT:
          case Types.INTEGER:
          case Types.BIGINT:
          case Types.DECIMAL:
          case Types.NUMERIC:
          case Types.FLOAT:
          case Types.REAL:
          case Types.DOUBLE:
            BigDecimal number = new BigDecimal(value.trim());
            if (type == Types.DECIMAL || type == Types.NUMERIC) {
              // Precision zero denotes an unconstrained/unknown numeric definition on some drivers.
              if (precision == 0) {
                return number;
              }
              long integerDigits = Math.max(0L, (long) number.precision() - number.scale());
              if (precision > 0 && integerDigits > (long) precision - scale) {
                throw new IllegalArgumentException("number exceeds precision " + precision);
              }
              if (number.stripTrailingZeros().scale() > scale) {
                throw new IllegalArgumentException(
                    "number requires rounding to column scale " + scale);
              }
              number = number.setScale(scale, RoundingMode.UNNECESSARY);
            } else if (type == Types.BIGINT) {
              checkInteger(number, 19);
              number.longValueExact();
            } else if (type == Types.INTEGER || type == Types.SMALLINT || type == Types.TINYINT) {
              checkInteger(number, 10);
              int integer = number.intValueExact();
              if ((type == Types.SMALLINT
                      && (integer < Short.MIN_VALUE || integer > Short.MAX_VALUE))
                  || (type == Types.TINYINT
                      && (integer < Byte.MIN_VALUE || integer > Byte.MAX_VALUE))) {
                throw new IllegalArgumentException("integer exceeds column range");
              }
            } else if (!Double.isFinite(number.doubleValue())
                || (number.signum() != 0 && number.doubleValue() == 0)
                || (type == Types.REAL
                    && (!Float.isFinite(number.floatValue())
                        || (number.signum() != 0 && number.floatValue() == 0)))) {
              throw new IllegalArgumentException("number exceeds floating-point column range");
            }
            return number;
          case Types.BOOLEAN:
          case Types.BIT:
            if (value.equalsIgnoreCase("true") || value.equals("1")) {
              return true;
            }
            if (value.equalsIgnoreCase("false") || value.equals("0")) {
              return false;
            }
            throw new IllegalArgumentException("expected true or false");
          case Types.DATE:
            return LocalDate.parse(value);
          case Types.TIME:
            return LocalTime.parse(value);
          case Types.TIMESTAMP:
            return LocalDateTime.parse(value.replace(' ', 'T'));
          case Types.TIME_WITH_TIMEZONE:
            return OffsetTime.parse(value);
          case Types.TIMESTAMP_WITH_TIMEZONE:
            return OffsetDateTime.parse(value.replace(' ', 'T'));
          default:
            throw new IllegalArgumentException("unsupported destination JDBC type " + type);
        }
      } catch (RuntimeException invalid) {
        throw problem(row, source, name + ": " + invalid.getMessage());
      }
    }
  }
}
