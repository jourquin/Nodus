/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.swing.JOptionPane;

/**
 * Imports .dbf tables in the database used by the Nodus project.
 *
 * @author Bart Jourquin
 */
public class ImportDBF {
  private static Connection jdbcConnection;

  private static int maxBatchSize;

  private static boolean hasBatchSupport = false;

  /**
   * Creates a new table, based on the structure of the dbfReader. Returns true on success.
   *
   * @param tableName String
   * @param dbfReader DBFReader
   * @return boolean
   */
  private static boolean createTable(String tableName, DBFReader dbfReader) {

    JDBCUtils.dropTable(tableName);

    String sqlStmt = "CREATE TABLE " + tableName + " (";
    int n = dbfReader.getFieldCount();

    for (int i = 0; i < n; i++) {
      DBFField field = dbfReader.getField(i);

      sqlStmt += sqlType(field);

      if (i < n - 1) {
        sqlStmt += ", ";
      }
    }

    sqlStmt += ")";
    try {
      Statement stmt = jdbcConnection.createStatement();
      stmt.execute(sqlStmt);
      stmt.close();

    } catch (Exception e) {
      e.printStackTrace();
      System.out.println(e.toString());
      return false;
    }

    return true;
  }

  /**
   * Fills the table with the records contained in the dbfReader. Returns true on success.
   *
   * @param tableName String
   * @param dbfReader DBFReader
   * @return boolean
   */
  private static boolean fillTable(String tableName, DBFReader dbfReader) {
    String sqlStmt = "INSERT INTO " + tableName + " VALUES (";
    int n = dbfReader.getFieldCount();

    for (int i = 0; i < n; i++) {
      sqlStmt += "?";

      if (i < n - 1) {
        sqlStmt += ",";
      }
    }

    sqlStmt += ")";

    // Get the fields description
    DBFField[] fields = new DBFField[n];
    for (int i = 0; i < n; i++) {
      fields[i] = dbfReader.getField(i);
    }

    try {
      PreparedStatement prepStmt = jdbcConnection.prepareStatement(sqlStmt);

      boolean oldAutoCommit = jdbcConnection.getAutoCommit();
      int batchSize = 0;

      while (dbfReader.hasNextRecord()) {
        Object[] o = dbfReader.nextRecord();

        // If the record is marked as deleted
        if (o == null) {
          continue;
        }

        for (int i = 0; i < o.length; i++) {

          // QGIS (?) sometimes  stores null values. Replace with 0 or empty String (ugly trick)
          if (o[i] == null) {
            if (fields[i].getType() == 'N') {
              o[i] = new BigDecimal(0);
            } else if (fields[i].getType() == 'N') {
              o[i] = new String("");
            }
          }

          if (o[i] instanceof BigDecimal) {
            BigDecimal z = (BigDecimal) o[i];
            if (fields[i].getDecimalCount() == 0) {
              prepStmt.setInt(i + 1, z.intValue());
            } else {
              prepStmt.setDouble(i + 1, z.doubleValue());
            }
          } else if (o[i] instanceof String) {
            prepStmt.setString(i + 1, (String) o[i]);
          } else if (o[i] instanceof java.util.Date) {
            java.util.Date d = (java.util.Date) o[i];
            Date date = new Date(d.getTime());
            prepStmt.setDate(i + 1, date);
          } else {
            System.err.println("Unsupported data type");
          }
        }

        // Save into table according to batch policy;
        if (hasBatchSupport) {
          batchSize++;
          prepStmt.addBatch();
          if (batchSize >= maxBatchSize) {
            jdbcConnection.setAutoCommit(false);
            prepStmt.executeBatch();
            jdbcConnection.commit();
            jdbcConnection.setAutoCommit(oldAutoCommit);
            batchSize = 0;
          }
        } else {
          prepStmt.executeUpdate();
        }

        // prepStmt.execute();
      }

      // Flush remaining records in batch
      if (hasBatchSupport) {
        jdbcConnection.setAutoCommit(false);
        prepStmt.executeBatch();
        jdbcConnection.commit();
        jdbcConnection.setAutoCommit(oldAutoCommit);
      }

      prepStmt.close();

      if (!jdbcConnection.getAutoCommit()) {
        jdbcConnection.commit();
      }

    } catch (Exception e) {
      System.out.println(e.toString());
      return false;
    }

    return true;
  }

  /**
   * Imports tableName.dbf, located in the project directory in the database. The imported table
   * will have the same name as the .dbf file name (without the ".dbf" extension.
   *
   * @param project The Nodus project.
   * @param tableName The name of the file to import, without extension.
   * @return boolean
   */
  public static boolean importTable(NodusProject project, String tableName) {
    jdbcConnection = project.getMainJDBCConnection();

    String path = project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

    maxBatchSize = project.getLocalProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, NodusC.MAXBATCHSIZE);

    // Test if database engine has batch support
    try {
      DatabaseMetaData dbmd = jdbcConnection.getMetaData();
      hasBatchSupport = false;
      if (dbmd != null) {
        if (dbmd.supportsBatchUpdates()) {
          hasBatchSupport = true;
        }
      }
    } catch (SQLException e1) {
      e1.printStackTrace();
    }

    // Uppercases? Lowercases?, Mixed? Depends on database capabilities ...
    String jdbcTableName = JDBCUtils.getCompliantIdentifier(tableName);

    // Create table
    try {
      // Create new table and drop existent one
      JDBCUtils.dropTable(jdbcTableName);

      // Open .dbf file
      DBFReader dbfReader = new DBFReader(path + tableName + NodusC.TYPE_DBF);

      if (dbfReader.isOpen()) {
        if (!createTable(jdbcTableName, dbfReader)) {
          return false;
        }

        if (!fillTable(jdbcTableName, dbfReader)) {
          return false;
        }
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    return true;
  }

  /**
   * Builds a string that represent the SQL type to use for a given JDBField.
   *
   * @param field JDBField
   * @return String
   */
  private static String sqlType(DBFField field) {
    String sql;

    String formattedField = field.getName();
    try {
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
        formattedField = quotes + formattedField + quotes;
      }

    } catch (Exception e) {
      System.err.println(e.toString());
      return "";
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
