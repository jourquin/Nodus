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

package edu.uclouvain.core.nodus.database.parquet;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableImport;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.DateLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.EnumLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.IntLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.JsonLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.StringLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimestampLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/** Imports a flat Parquet schema and rows through staged, failure-safe table replacement. */
public final class ImportParquet {
  private ImportParquet() {}

  /**
   * Creates/replaces a table using the schema in its project-directory Parquet file.
   *
   * @param project project supplying the database and input directory
   * @param tableName destination table and source basename
   * @return true after the complete import has been published
   */
  public static boolean importTable(NodusProject project, String tableName) {
    Path source =
        Path.of(
            project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                + tableName
                + NodusC.TYPE_PARQUET);
    try {
      TableImport.replace(
          project,
          tableName,
          (connection, staged) -> {
            ParquetReadOptions options =
                new org.apache.parquet.NodusReadOptions(new ParquetCodecs());
            try (ParquetFileReader reader =
                ParquetFileReader.open(new LocalInputFile(source), options)) {
              MessageType schema = reader.getFileMetaData().getSchema();
              List<Column> columns = columns(schema);
              load(connection, staged, reader, schema, columns, project.getSqlBatchSize());
            }
          });
      return true;
    } catch (Exception error) {
      TableImport.reportError(error);
      return false;
    }
  }

  private static List<Column> columns(MessageType schema) throws SQLException {
    List<Column> columns = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (Type field : schema.getFields()) {
      if (!field.isPrimitive() || field.isRepetition(Type.Repetition.REPEATED))
        throw new SQLException(
            "Nested/repeated Parquet field is not a table column: " + field.getName());
      if (!names.add(field.getName().toUpperCase(Locale.ROOT)))
        throw new SQLException("Duplicate Parquet column name: " + field.getName());
      columns.add(new Column(field.asPrimitiveType()));
    }
    if (columns.isEmpty()) throw new SQLException("Parquet schema contains no columns");
    return columns;
  }

  private static void load(
      Connection connection,
      String table,
      ParquetFileReader reader,
      MessageType schema,
      List<Column> columns,
      int batchSize)
      throws Exception {
    String quoted = JDBCUtils.getQuotedCompliantIdentifier(table);
    List<String> definitions = new ArrayList<>();
    for (Column column : columns)
      definitions.add(
          JDBCUtils.getQuotedCompliantIdentifier(column.type.getName())
              + " "
              + storageType(connection, column.sqlType));
    try (Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE " + quoted + " (" + String.join(",", definitions) + ")");
    }
    String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
    boolean batching = connection.getMetaData().supportsBatchUpdates();
    try (PreparedStatement insert =
        connection.prepareStatement("INSERT INTO " + quoted + " VALUES (" + placeholders + ")")) {
      MessageColumnIO io = new ColumnIOFactory().getColumnIO(schema);
      PageReadStore pages;
      int pending = 0;
      while ((pages = reader.readNextRowGroup()) != null) {
        try (PageReadStore group = pages) {
          RecordReader<Group> records = io.getRecordReader(group, new GroupRecordConverter(schema));
          for (long row = 0; row < group.getRowCount(); row++) {
            Group record = records.read();
            if (record == null) throw new SQLException("Unexpected end of Parquet row group");
            for (int c = 0; c < columns.size(); c++) {
              Object value =
                  record.getFieldRepetitionCount(c) == 0 ? null : columns.get(c).value(record, c);
              insert.setObject(c + 1, value);
            }
            if (batching) {
              insert.addBatch();
              if (++pending >= Math.max(1, batchSize)) {
                insert.executeBatch();
                pending = 0;
              }
            } else insert.executeUpdate();
          }
        }
      }
      if (pending > 0) insert.executeBatch();
    }
  }

  /** Uses driver type names for long text/binary columns (for example TEXT or BYTEA). */
  private static String storageType(Connection connection, String type) throws SQLException {
    boolean text = type.equals("LONGVARCHAR");
    if (!text && !type.equals("LONGVARBINARY")) return type;
    int[] preferred =
        text
            ? new int[] {java.sql.Types.LONGVARCHAR, java.sql.Types.VARCHAR}
            : new int[] {
              java.sql.Types.LONGVARBINARY, java.sql.Types.VARBINARY, java.sql.Types.BINARY
            };
    for (int jdbcType : preferred) {
      String sized = null;
      try (java.sql.ResultSet types = connection.getMetaData().getTypeInfo()) {
        while (types.next()) {
          if (types.getInt("DATA_TYPE") != jdbcType) continue;
          String name = types.getString("TYPE_NAME");
          String parameters = types.getString("CREATE_PARAMS");
          if (parameters == null || parameters.isEmpty()) return name;
          long precision = types.getLong("PRECISION");
          if (precision > 0 && sized == null)
            sized = name + "(" + Math.min(precision, 16777216L) + ")";
        }
      }
      if (sized != null) return sized;
    }
    throw new SQLException("Database does not report a storage type for " + type);
  }

  private static final class Column {
    final PrimitiveType type;
    final LogicalTypeAnnotation logical;
    final String sqlType;

    Column(PrimitiveType type) throws SQLException {
      this.type = type;
      logical = type.getLogicalTypeAnnotation();
      if (logical instanceof DecimalLogicalTypeAnnotation) {
        DecimalLogicalTypeAnnotation decimal = (DecimalLogicalTypeAnnotation) logical;
        sqlType = "DECIMAL(" + decimal.getPrecision() + "," + decimal.getScale() + ")";
      } else if (logical instanceof StringLogicalTypeAnnotation
          || logical instanceof EnumLogicalTypeAnnotation
          || logical instanceof JsonLogicalTypeAnnotation) {
        sqlType = "LONGVARCHAR";
      } else if (logical instanceof DateLogicalTypeAnnotation) {
        sqlType = "DATE";
      } else if (logical instanceof TimeLogicalTypeAnnotation) {
        if (((TimeLogicalTypeAnnotation) logical).isAdjustedToUTC())
          throw new SQLException("UTC-adjusted Parquet TIME is unsupported: " + type.getName());
        sqlType = "TIME(9)";
      } else if (logical instanceof TimestampLogicalTypeAnnotation) {
        sqlType =
            "TIMESTAMP(9)"
                + (((TimestampLogicalTypeAnnotation) logical).isAdjustedToUTC()
                    ? " WITH TIME ZONE"
                    : "");
      } else if (logical instanceof IntLogicalTypeAnnotation) {
        IntLogicalTypeAnnotation integer = (IntLogicalTypeAnnotation) logical;
        sqlType =
            !integer.isSigned()
                ? (integer.getBitWidth() == 64 ? "DECIMAL(20,0)" : "BIGINT")
                : integer.getBitWidth() <= 32 ? "INTEGER" : "BIGINT";
      } else if (logical != null) {
        throw new SQLException(
            "Unsupported Parquet logical type " + logical + ": " + type.getName());
      } else {
        switch (type.getPrimitiveTypeName()) {
          case BOOLEAN:
            sqlType = "BOOLEAN";
            break;
          case INT32:
            sqlType = "INTEGER";
            break;
          case INT64:
            sqlType = "BIGINT";
            break;
          case FLOAT:
            sqlType = "REAL";
            break;
          case DOUBLE:
            sqlType = "DOUBLE PRECISION";
            break;
          case BINARY:
          case FIXED_LEN_BYTE_ARRAY:
            sqlType = "LONGVARBINARY";
            break;
          case INT96:
            sqlType = "TIMESTAMP(9)";
            break;
          default:
            throw new SQLException("Unsupported Parquet type: " + type);
        }
      }
    }

    Object value(Group row, int c) throws SQLException {
      if (logical instanceof DecimalLogicalTypeAnnotation) {
        BigInteger integer;
        switch (type.getPrimitiveTypeName()) {
          case INT32:
            integer = BigInteger.valueOf(row.getInteger(c, 0));
            break;
          case INT64:
            integer = BigInteger.valueOf(row.getLong(c, 0));
            break;
          default:
            integer = new BigInteger(row.getBinary(c, 0).getBytes());
            break;
        }
        return new BigDecimal(integer, ((DecimalLogicalTypeAnnotation) logical).getScale());
      }
      if (logical instanceof StringLogicalTypeAnnotation
          || logical instanceof EnumLogicalTypeAnnotation
          || logical instanceof JsonLogicalTypeAnnotation) return row.getString(c, 0);
      if (logical instanceof DateLogicalTypeAnnotation)
        return LocalDate.ofEpochDay(row.getInteger(c, 0));
      if (logical instanceof TimeLogicalTypeAnnotation) {
        TimeLogicalTypeAnnotation time = (TimeLogicalTypeAnnotation) logical;
        long value =
            type.getPrimitiveTypeName() == PrimitiveType.PrimitiveTypeName.INT32
                ? row.getInteger(c, 0)
                : row.getLong(c, 0);
        return LocalTime.ofNanoOfDay(Math.multiplyExact(value, nanosPerUnit(time.getUnit())));
      }
      if (logical instanceof TimestampLogicalTypeAnnotation) {
        TimestampLogicalTypeAnnotation time = (TimestampLogicalTypeAnnotation) logical;
        long value = row.getLong(c, 0);
        long perSecond = 1000000000L / nanosPerUnit(time.getUnit());
        LocalDateTime timestamp =
            LocalDateTime.ofEpochSecond(
                Math.floorDiv(value, perSecond),
                (int) (Math.floorMod(value, perSecond) * nanosPerUnit(time.getUnit())),
                ZoneOffset.UTC);
        return time.isAdjustedToUTC() ? timestamp.atOffset(ZoneOffset.UTC) : timestamp;
      }
      if (logical instanceof IntLogicalTypeAnnotation
          && !((IntLogicalTypeAnnotation) logical).isSigned()) {
        return ((IntLogicalTypeAnnotation) logical).getBitWidth() == 64
            ? new BigDecimal(Long.toUnsignedString(row.getLong(c, 0)))
            : Integer.toUnsignedLong(row.getInteger(c, 0));
      }
      switch (type.getPrimitiveTypeName()) {
        case BOOLEAN:
          return row.getBoolean(c, 0);
        case INT32:
          return row.getInteger(c, 0);
        case INT64:
          return row.getLong(c, 0);
        case FLOAT:
          return row.getFloat(c, 0);
        case DOUBLE:
          return row.getDouble(c, 0);
        case BINARY:
        case FIXED_LEN_BYTE_ARRAY:
          return row.getBinary(c, 0).getBytes();
        case INT96:
          ByteBuffer bytes =
              ByteBuffer.wrap(row.getInt96(c, 0).getBytes()).order(ByteOrder.LITTLE_ENDIAN);
          long nanos = bytes.getLong();
          return LocalDate.ofEpochDay((long) bytes.getInt() - 2440588L)
              .atTime(LocalTime.ofNanoOfDay(nanos));
        default:
          throw new SQLException("Unsupported Parquet type: " + type);
      }
    }
  }

  private static long nanosPerUnit(TimeUnit unit) {
    switch (unit) {
      case MILLIS:
        return 1000000L;
      case MICROS:
        return 1000L;
      case NANOS:
        return 1L;
      default:
        throw new IllegalArgumentException("Unsupported Parquet time unit: " + unit);
    }
  }
}
