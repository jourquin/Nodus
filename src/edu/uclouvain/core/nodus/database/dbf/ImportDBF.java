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

package edu.uclouvain.core.nodus.database.dbf;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.ImportBatch;
import edu.uclouvain.core.nodus.database.ImportProgress;
import edu.uclouvain.core.nodus.database.ImportSource;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableImport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.function.Consumer;

/**
 * Imports .dbf tables in the database used by the Nodus project.
 *
 * @author Bart Jourquin
 */
public class ImportDBF {
  private static Connection jdbcConnection;

  private static int maxBatchSize;

  /** Default constructor. */
  public ImportDBF() {}

  /**
   * Creates a table using the DBF field descriptors.
   *
   * @param tableName String
   * @param dbfReader DBFReader
   * @throws SQLException if the schema cannot be created
   */
  private static void createTable(String tableName, DBFReader dbfReader) throws SQLException {
    StringBuilder sqlStmt = new StringBuilder("CREATE TABLE ");
    sqlStmt.append(JDBCUtils.getQuotedCompliantIdentifier(tableName)).append(" (");
    int n = dbfReader.getFieldCount();

    for (int i = 0; i < n; i++) {
      DBFField field = dbfReader.getField(i);

      sqlStmt.append(sqlType(field));

      if (i < n - 1) {
        sqlStmt.append(", ");
      }
    }

    sqlStmt.append(')');
    try (Statement stmt = jdbcConnection.createStatement()) {
      stmt.execute(sqlStmt.toString());
    }
  }

  /**
   * Loads fixed-width DBF records, identifying the failing record or batch on error.
   *
   * @param tableName String
   * @param dbfReader DBFReader
   * @throws Exception if a record cannot be read or inserted
   */
  private static void fillTable(String tableName, DBFReader dbfReader, ImportProgress progress)
      throws Exception {
    progress.phase("Loading");
    StringBuilder sqlStmt = new StringBuilder("INSERT INTO ");
    sqlStmt.append(JDBCUtils.getQuotedCompliantIdentifier(tableName)).append(" VALUES (");
    int n = dbfReader.getFieldCount();

    for (int i = 0; i < n; i++) {
      sqlStmt.append('?');

      if (i < n - 1) {
        sqlStmt.append(',');
      }
    }

    sqlStmt.append(')');

    // Get the fields description
    DBFField[] fields = new DBFField[n];
    for (int i = 0; i < n; i++) {
      fields[i] = dbfReader.getField(i);
    }

    long recordNumber = 0;
    try (PreparedStatement prepStmt = jdbcConnection.prepareStatement(sqlStmt.toString())) {

      ImportBatch batch =
          new ImportBatch(jdbcConnection, prepStmt, maxBatchSize, "DBF record", progress);

      while (dbfReader.hasNextRecord()) {
        progress.checkCancelled();
        recordNumber++;
        Object[] o = dbfReader.nextRecord();

        // If the record is marked as deleted
        if (o == null) {
          continue;
        }

        if (o.length != n) {
          throw new SQLException("record does not match DBF field count " + n);
        }
        for (int i = 0; i < o.length; i++) {

          // QGIS (?) sometimes stores null values. Replace numeric and string values with
          // the defaults historically used by Nodus, and bind real SQL NULLs for dates
          // or unsupported field types so that every PreparedStatement parameter is set.
          if (o[i] == null) {
            if (fields[i].getType() == 'N') {
              o[i] = BigDecimal.ZERO;
            } else if (fields[i].getType() == 'C') {
              o[i] = "";
            } else if (fields[i].getType() == 'D') {
              prepStmt.setNull(i + 1, Types.DATE);
              continue;
            } else {
              prepStmt.setNull(i + 1, Types.NULL);
              continue;
            }
          }

          if (o[i] instanceof BigDecimal) {
            try {
              BigDecimal number = (BigDecimal) o[i];
              long integerDigits = Math.max(0L, (long) number.precision() - number.scale());
              if (number.signum() != 0
                  && integerDigits > (long) fields[i].getLength() - fields[i].getDecimalCount()) {
                throw new ArithmeticException("number exceeds the declared DBF precision");
              }
              if (number.stripTrailingZeros().scale() > fields[i].getDecimalCount()) {
                throw new ArithmeticException("number requires rounding to the declared DBF scale");
              }
              BigDecimal value =
                  number.setScale(fields[i].getDecimalCount(), RoundingMode.UNNECESSARY);
              if (value.precision() > fields[i].getLength()) {
                throw new ArithmeticException("number exceeds the declared DBF precision");
              }
              prepStmt.setBigDecimal(i + 1, value);
            } catch (ArithmeticException invalid) {
              throw new SQLException(
                  "column " + (i + 1) + " (" + fields[i].getName() + "): " + invalid.getMessage(),
                  invalid);
            }
          } else if (o[i] instanceof String) {
            prepStmt.setString(i + 1, (String) o[i]);
          } else if (o[i] instanceof java.util.Date) {
            java.util.Date d = (java.util.Date) o[i];
            Date date = new Date(d.getTime());
            prepStmt.setDate(i + 1, date);
          } else {
            throw new SQLException("Unsupported DBF data type in column " + (i + 1));
          }
        }

        batch.add(recordNumber);
      }

      batch.flush();

    } catch (Exception error) {
      if (error instanceof SQLException
          && error.getMessage() != null
          && (error.getMessage().startsWith("DBF record ")
              || "57014".equals(((SQLException) error).getSQLState()))) {
        throw error;
      }
      throw new SQLException("DBF record " + recordNumber + ": " + error.getMessage(), error);
    }
  }

  /**
   * Imports tableName.dbf, located in the project directory in the database. The imported table
   * will have the same name as the .dbf file name (without the ".dbf" extension.
   *
   * @param project The Nodus project.
   * @param tableName The name of the file to import, without extension.
   * @return boolean
   */
  public static synchronized boolean importTable(NodusProject project, String tableName) {
    return importTable(project, tableName, TableImport::reportError);
  }

  /**
   * Imports one DBF table, reporting failures to the supplied console callback.
   *
   * @param project the destination project and input directory
   * @param tableName the destination table and source basename
   * @param reportError receives the first import failure
   * @return true after the complete table has been published
   */
  public static synchronized boolean importTable(
      NodusProject project, String tableName, Consumer<Exception> reportError) {
    return importTable(project, tableName, reportError, new ImportProgress());
  }

  /**
   * Imports exact DBF numeric values with progress and cooperative cancellation.
   *
   * @param project destination project and source directory
   * @param tableName destination table and file basename
   * @param reportError receiver for validation or database errors
   * @param progress import progress and cancellation control
   * @return true after the entire import succeeds
   */
  public static synchronized boolean importTable(
      NodusProject project,
      String tableName,
      Consumer<Exception> reportError,
      ImportProgress progress) {
    jdbcConnection = project.getMainJDBCConnection();
    if (jdbcConnection == null) {
      return false;
    }

    String path = project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

    maxBatchSize = project.getSqlBatchSize();

    try {
      ImportSource source = new ImportSource(Path.of(path, tableName + NodusC.TYPE_DBF));
      progress.phase("Reading");
      TableImport.replace(
          project,
          tableName,
          (connection, staged) -> {
            jdbcConnection = connection;
            try (DBFReader reader =
                DbfFiles.reader(project, Path.of(path, tableName + NodusC.TYPE_DBF))) {
              if (!reader.isOpen()) {
                throw new SQLException("Could not read the DBF table");
              }
              createTable(staged, reader);
              fillTable(staged, reader, progress);
            }
            source.verify();
          },
          progress);
      return true;
    } catch (Exception e) {
      reportError.accept(e);
      return false;
    } finally {
      jdbcConnection = null;
    }
  }

  /**
   * Builds a string that represent the SQL type to use for a given JDBField.
   *
   * @param field JDBField
   * @return String
   */
  private static String sqlType(DBFField field) throws SQLException {
    String sql;

    String formattedField = field.getName();
    DatabaseMetaData dmd = jdbcConnection.getMetaData();

    // Lower or Upper case?
    if (dmd.storesMixedCaseIdentifiers()) {
      formattedField = field.getName();
    } else if (dmd.storesLowerCaseIdentifiers()) {
      formattedField = field.getName().toLowerCase();
    } else if (dmd.storesUpperCaseIdentifiers()) {
      formattedField = field.getName().toUpperCase();
    }

    String quotes = dmd.getIdentifierQuoteString();
    if (!quotes.equals(" ")) {
      formattedField = quotes + formattedField.replace(quotes, quotes + quotes) + quotes;
    }

    sql = formattedField + " ";

    if (field.getType() == 'N') {
      if (field.getDecimalCount() > 0) {
        sql += "NUMERIC(" + field.getLength() + "," + field.getDecimalCount() + ")";
      } else {
        sql += "NUMERIC(" + field.getLength() + ")";
      }
    } else if (field.getType() == 'C') {
      sql += "VARCHAR(" + field.getLength() + ")";
    }
    if (field.getType() == 'D') {
      sql += "DATE";
    }

    return sql;
  }
}
