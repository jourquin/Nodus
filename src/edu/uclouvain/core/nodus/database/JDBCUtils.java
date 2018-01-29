/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.database;

import edu.uclouvain.core.nodus.NodusC;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

/**
 * Some handy JDBC utilities.
 *
 * @author Bart Jourquin
 */
public class JDBCUtils {

  /** Apache Derby. */
  public static final int DB_DERBY = 6;

  /** H2. */
  public static final int DB_H2 = 5;

  /** HSQLDB. */
  public static final int DB_HSQLDB = 2;

  /** MySQL - MariaDB. */
  public static final int DB_MYSQL = 1;

  /** Oracle (not fully tested). */
  public static final int DB_ORACLE = 4;

  /** PostgreSQL. */
  public static final int DB_POSTGRESQL = 3;

  /** SQLite. */
  public static final int DB_SQLITE = 7;

  /** Firebird (doesn't work yet on 20161223). */
  public static final int DB_FIREBIRD = 8;

  /** Any other DBMS - Could work with Nodus or not, as not tested. */
  public static final int DB_UNKNOWN = -1;

  private static int dbEngine;

  private static DatabaseMetaData dmd;

  /**
   * H2 and HSQLDB databases can be compacted at shutdown time.
   *
   * @param jdbcConnection An open JDBC connection.
   */
  public static void shutdownCompact(Connection jdbcConnection) {
    if (getDbEngine(jdbcConnection) == DB_HSQLDB || getDbEngine(jdbcConnection) == DB_H2) {

      // Compact database
      try {
        Statement stmt = jdbcConnection.createStatement();
        stmt.execute("SHUTDOWN COMPACT");
        stmt.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Tries to convert the content of an object to an byte.
   *
   * @param object The Object to convert.
   * @return The converted value or Byte.MIN_VALUE on error.
   */
  public static byte getByte(Object object) {
    if (object instanceof BigDecimal) {
      BigDecimal v = (BigDecimal) object;

      return v.byteValue();
    }

    if (object instanceof BigInteger) {
      BigInteger v = (BigInteger) object;

      return v.byteValue();
    }

    if (object instanceof Short) {
      Short v = (Short) object;

      return v.byteValue();
    }

    if (object instanceof Integer) {
      Integer v = (Integer) object;

      return v.byteValue();
    }

    if (object instanceof Long) {
      Long v = (Long) object;

      return v.byteValue();
    }

    if (object instanceof Float) {
      Float v = (Float) object;

      return v.byteValue();
    }

    if (object instanceof Double) {
      Double v = (Double) object;

      return v.byteValue();
    }

    System.err.println("Unsuported type " + object.toString());

    return Byte.MIN_VALUE;
  }

  /**
   * Returns the ID of the DBMS.
   *
   * @param jdbcConnection A Connection to a database.
   * @return The ID of the database.
   */
  public static int getDbEngine(Connection jdbcConnection) {
    try {
      DatabaseMetaData dmd = jdbcConnection.getMetaData();
      String productName = dmd.getDatabaseProductName();

      if (productName.toLowerCase().indexOf("mysql") != -1) {
        return DB_MYSQL;
      }

      if (productName.toLowerCase().indexOf("hsql") != -1) {
        return DB_HSQLDB;
      }

      if (productName.toLowerCase().indexOf("postgresql") != -1) {
        return DB_POSTGRESQL;
      }

      if (productName.toLowerCase().indexOf("oracle") != -1) {
        return DB_ORACLE;
      }

      if (productName.toLowerCase().indexOf("h2") != -1) {
        return DB_H2;
      }

      if (productName.toLowerCase().indexOf("derby") != -1) {
        return DB_DERBY;
      }

      if (productName.toLowerCase().indexOf("sqlite") != -1) {
        return DB_SQLITE;
      }

      if (productName.toLowerCase().indexOf("firebird") != -1) {
        return DB_FIREBIRD;
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }

    return DB_UNKNOWN;
  }

  /**
   * Returns the name of the DB engine.
   *
   * @param jdbcConnection A Connection to a database.
   * @return The product name of the DBMS.
   */
  public static String getDbEngineName(Connection jdbcConnection) {
    try {
      DatabaseMetaData dmd = jdbcConnection.getMetaData();
      return dmd.getDatabaseProductName();

    } catch (SQLException e) {
      e.printStackTrace();
    }

    return "";
  }

  /**
   * Tries to convert the content of an object to an double.
   *
   * @param object The Object to convert.
   * @return The converted value or Double.MIN_VALUE on error.
   */
  public static double getDouble(Object object) {
    if (object instanceof BigDecimal) {
      BigDecimal v = (BigDecimal) object;

      return v.doubleValue();
    }

    if (object instanceof BigInteger) {
      BigInteger v = (BigInteger) object;

      return v.doubleValue();
    }

    if (object instanceof Short) {
      Short v = (Short) object;

      return v.doubleValue();
    }

    if (object instanceof Integer) {
      Integer v = (Integer) object;

      return v.doubleValue();
    }

    if (object instanceof Long) {
      Long v = (Long) object;

      return v.doubleValue();
    }

    if (object instanceof Float) {
      Float v = (Float) object;

      return v.doubleValue();
    }

    if (object instanceof Double) {
      Double v = (Double) object;

      return v.doubleValue();
    }

    System.err.println("Unsuported type " + object.toString());

    return Double.MIN_VALUE;
  }

  /**
   * Tries to convert the content of an object to an float.
   *
   * @param object The Object to convert.
   * @return The converted value or Float.MIN_VALUE on error.
   */
  public static float getFloat(Object object) {
    double d = getDouble(object);

    if (d == Double.MIN_VALUE) {
      return Float.MIN_VALUE;
    } else {
      return (float) d;
    }
  }

  /**
   * Tries to convert the content of an object to an int.
   *
   * @param object The Object to convert.
   * @return The converted value or Integer.MIN_VALUE on error.
   */
  public static int getInt(Object object) {
    if (object instanceof BigDecimal) {
      BigDecimal v = (BigDecimal) object;

      return v.intValue();
    }

    if (object instanceof BigInteger) {
      BigInteger v = (BigInteger) object;

      return v.intValue();
    }

    if (object instanceof Short) {
      Short v = (Short) object;

      return v.intValue();
    }

    if (object instanceof Integer) {
      Integer v = (Integer) object;

      return v.intValue();
    }

    if (object instanceof Long) {
      Long v = (Long) object;

      return v.intValue();
    }

    if (object instanceof Float) {
      Float v = (Float) object;

      return v.intValue();
    }

    if (object instanceof Double) {
      Double v = (Double) object;

      return v.intValue();
    }

    if (object instanceof String) {
      Double v = Double.parseDouble((String) object);

      return v.intValue();
    }

    System.err.println("Unsuported type " + object.toString());

    return Integer.MIN_VALUE;
  }

  /**
   * Tries to convert the content of an object to an long.
   *
   * @param object The Object to convert.
   * @return The converted value or Long.MIN_VALUE on error.
   */
  public static long getLong(Object object) {
    if (object instanceof BigDecimal) {
      BigDecimal v = (BigDecimal) object;

      return v.longValue();
    }

    if (object instanceof BigInteger) {
      BigInteger v = (BigInteger) object;

      return v.longValue();
    }

    if (object instanceof Short) {
      Short v = (Short) object;

      return v.longValue();
    }

    if (object instanceof Integer) {
      Integer v = (Integer) object;

      return v.longValue();
    }

    if (object instanceof Long) {
      Long v = (Long) object;

      return v.longValue();
    }

    if (object instanceof Float) {
      Float v = (Float) object;

      return v.longValue();
    }

    if (object instanceof Double) {
      Double v = (Double) object;

      return v.longValue();
    }

    if (object instanceof String) {
      Double v = Double.parseDouble((String) object);

      return v.longValue();
    }

    System.err.println("Unsuported type " + object.toString());

    return Long.MIN_VALUE;
  }

  /**
   * Tests if SQLite is installed on the computer.
   *
   * @return True if SQLite is installed on the computer.
   */
  public static boolean isSQliteInstalled() {

    try {
      try {
        Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance();
      } catch (IllegalArgumentException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
      } catch (SecurityException e) {
        e.printStackTrace();
      }

      Connection jdbcConnection = DriverManager.getConnection("jdbc:sqlite::memory:");
      if (jdbcConnection == null) {
        return false;
      } else {
        jdbcConnection.close();
        return true;
      }
    } catch (InstantiationException e) {
      return false;
    } catch (IllegalAccessException e) {
      return false;
    } catch (ClassNotFoundException e) {
      return false;
    } catch (SQLException e) {
      return false;
    }
  }

  Connection jdbcConnection;

  List<String> tableList = null;

  /**
   * Constructor.
   *
   * @param con JDBC connection
   */
  public JDBCUtils(Connection con) {
    jdbcConnection = con;
    dbEngine = getDbEngine(con);
    try {
      dmd = jdbcConnection.getMetaData();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  /**
   * Create an index on a table.
   *
   * @param index A JDBCIndex object.
   * @return True on succes.
   */
  public boolean createIndex(JDBCIndex index) {

    try {
      Statement stmt = jdbcConnection.createStatement();
      String sqlStmt =
          "CREATE INDEX "
              + getQuotedCompliantIdentifier(index.getIndexName())
              + " ON "
              + index.getTableName()
              + "("
              + getQuotedCompliantIdentifier(index.getIndexFieldName())
              + ")";
      stmt.execute(sqlStmt);
    } catch (SQLException e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }
    return true;
  }

  /**
   * Create table without index.
   *
   * @param tableName The name of the table.
   * @param fields An array of fields.
   * @return True on success.
   */
  public boolean createTable(String tableName, JDBCField[] fields) {
    return createTable(tableName, fields, null);
  }

  /**
   * Create a table with index(es).
   *
   * @param tableName The name of the table.
   * @param fields An array of fields.
   * @param indexes An array of indexes.
   * @return True on success.
   */
  public boolean createTable(String tableName, JDBCField[] fields, JDBCIndex[] indexes) {
    try {
      final Statement stmt = jdbcConnection.createStatement();
      String sqlStmt = null;

      dropTable(tableName);

      // Create a new table
      sqlStmt = "CREATE TABLE " + getCompliantIdentifier(tableName) + " (";

      for (int i = 0; i < fields.length; i++) {
        String fieldType = fields[i].getFieldType();

        if (fieldType == null) {
          return false;
        }

        sqlStmt += getQuotedCompliantIdentifier(fields[i].getFieldName()) + " " + fieldType;

        if (i < fields.length - 1) {
          sqlStmt += ", ";
        } else {
          sqlStmt += ")";
        }
      }

      stmt.execute(sqlStmt);
      stmt.close();
      if (tableList != null) {
        tableList.add(tableName.toLowerCase());
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      JOptionPane.showMessageDialog(null, ex.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);

      return false;
    }

    // Create the indexes
    if (indexes != null) {
      for (int i = 0; i < indexes.length; i++) {
        if (!createIndex(indexes[i])) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Drops a table from the database.
   *
   * @param tableName The name of the table to drop.
   */
  public void dropTable(String tableName) {
    try {
      // Only drop existent tables
      ResultSet rs = dmd.getTables(null, null, getCompliantIdentifier(tableName), null);
      if (rs.next()) {
        rs.close();
        Statement stmt = jdbcConnection.createStatement();
        stmt.execute("DROP TABLE " + getCompliantIdentifier(tableName));
        if (tableList != null) {
          tableList.remove(tableName.toLowerCase());
        }
      }

    } catch (SQLException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Returns an identifier (table or field name) that complies with the constraints of the DBMS,
   * i.e. change to lower of upper cases if needed.
   *
   * @param identifier The identifier to test.
   * @return The compliant identifier.
   */
  public String getCompliantIdentifier(String identifier) {
    return getIdentifier(identifier, false);
  }

  /** Returns a compliant identifier, with or without quotes. */
  private String getIdentifier(String identifier, boolean quoted) {
    String formattedIdentifier = identifier;
    if (jdbcConnection == null) {
      return identifier;
    } else {

      try {

        // Lower or Upper case?
        if (dmd.storesMixedCaseIdentifiers()) {
          formattedIdentifier = identifier;
        } else if (dmd.storesLowerCaseIdentifiers()) {
          formattedIdentifier = identifier.toLowerCase();
        } else if (dmd.storesUpperCaseIdentifiers()) {
          formattedIdentifier = identifier.toUpperCase();
        }

        if (quoted) {
          String quotes = dmd.getIdentifierQuoteString();
          if (!quotes.equals(" ")) {
            // Could already be quoted...
            if (!formattedIdentifier.startsWith(quotes)) {
              formattedIdentifier = quotes + formattedIdentifier;
            }
            if (!formattedIdentifier.endsWith(quotes)) {
              formattedIdentifier = formattedIdentifier + quotes;
            }
          }
        }

      } catch (Exception e) {
        System.err.println(e.toString());
        return null;
      }

      return formattedIdentifier;
    }
  }

  /**
   * Returns an identifier (table or field name) that complies with the constraints of the DBMS,
   * i.e. change to lower of upper cases if needed. The method also adds quotes, depending on those
   * used by the DBMS (none, single or double quotes).
   *
   * @param identifier The identifier to test.
   * @return The compliant identifier.
   */
  public String getQuotedCompliantIdentifier(String identifier) {
    return getIdentifier(identifier, true);
  }

  /**
   * Tests if the given field is present in a table.
   *
   * @param tableName The name of the table to check.
   * @param fieldName The name of the field to check.
   * @return True on success.
   */
  public boolean hasField(String tableName, String fieldName) {

    try {
      // Create a result set
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);

      // Get result set meta data
      ResultSetMetaData rsmd = rs.getMetaData();
      rs.close();

      int numColumns = rsmd.getColumnCount();

      // Get the column names; column indices start from 1
      for (int i = 1; i < numColumns + 1; i++) {
        String columnName = rsmd.getColumnName(i);
        if (columnName.compareToIgnoreCase(fieldName) == 0) {
          return true;
        }
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }

    return false;
  }

  /**
   * Return true if a column in a table contains several different values.
   *
   * @param tableName The name of the table to check.
   * @param fieldName The name of the field to check.
   * @return True on success.
   */
  public boolean hasSeveralValues(String tableName, String fieldName) {

    try {
      // Create a result set
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs =
          stmt.executeQuery("SELECT COUNT(*) FROM " + tableName + " GROUP BY " + fieldName);

      int rowCount = 0;
      while (rs.next()) {
        rowCount++;
        if (rowCount > 1) {
          break;
        }
      }

      rs.close();
      stmt.close();
      if (rowCount > 1) {
        return true;
      }
    } catch (SQLException e) {
      // e.printStackTrace();
      return false;
    }

    return false;
  }

  /**
   * Renames a table in the database.
   *
   * @param currentTableName The current table name.
   * @param newTableName The new name of the table.
   * @return True on success.
   */
  public boolean renameTable(String currentTableName, String newTableName) {

    currentTableName = getCompliantIdentifier(currentTableName);
    newTableName = getCompliantIdentifier(newTableName);

    String sqlStmt;
    switch (dbEngine) {
      case DB_DERBY:
      case DB_ORACLE:
        sqlStmt = "rename table " + currentTableName + " to " + newTableName;
        break;
      default:
        sqlStmt = "alter table " + currentTableName + " rename to " + newTableName;
    }

    try {
      // Only rename existent tables
      ResultSet rs = dmd.getTables(null, null, getCompliantIdentifier(currentTableName), null);
      if (rs.next()) {
        Statement stmt = jdbcConnection.createStatement();
        stmt.execute(sqlStmt);
        if (tableList != null) {
          tableList.remove(currentTableName.toLowerCase());
          tableList.add(newTableName.toLowerCase());
        }
        stmt.close();
        rs.close();
      }

    } catch (SQLException ex) {
      ex.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Returns true if a the table already exists in the database.
   *
   * @param tableName The name of the table to check.
   * @return True on success.
   */
  public boolean tableExists(String tableName) {
    return tableExists(tableName, false);
  }

  /**
   * Returns true if a the table already exists in the database.
   *
   * @param tableName The name of the table to check.
   * @param reloadList If true, forces the JDBCUtils to reload the list of tables from the database
   *     to the list it maintains in memory for performance reasons.
   * @return True on success.
   */
  public boolean tableExists(String tableName, boolean reloadList) {
    // Create table list if needed
    if (tableList == null || reloadList) {
      tableList = new ArrayList<>();

      try {
        ResultSet rs = dmd.getTables(null, null, "%", new String[] {"TABLE"});
        while (rs.next()) {
          tableList.add(rs.getString(3).toLowerCase());
        }
        rs.close();

      } catch (Exception e) {
        System.out.println(e.toString());
        return false;
      }
    }

    return tableList.contains(tableName.toLowerCase());
  }
}
