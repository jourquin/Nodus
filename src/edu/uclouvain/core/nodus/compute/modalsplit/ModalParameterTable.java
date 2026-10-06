/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

/** Database-backed parameters shared by the three embedded modal-choice methods. */
final class ModalParameterTable {
  static final String POINTER = "@paramTable";
  static final String METHOD = "@nodus.method";
  static final String PIVOTS = "@nodus.estimatePivots";
  static final String PIVOT_MAX_ABS = "@nodus.pivotMaxAbs";
  static final double DEFAULT_PIVOT_MAX_ABS = 8;
  // Fewer JDBC bulk calls for short pivot rows, with a cap on each batch's payload.
  private static final int MAX_BATCH_ROWS = 5000;
  private static final int MAX_BATCH_CHARS = 512 * 1024;
  // A 191-character UTF-8 key stays below MariaDB's 1000-byte index limit with utf8mb4.
  private static final int MAX_KEY_LENGTH = 191;
  private static final String COLUMNS =
      " (param_key VARCHAR("
          + MAX_KEY_LENGTH
          + ") NOT NULL PRIMARY KEY,"
          + " param_value VARCHAR(4096) NOT NULL, param_type VARCHAR(32) NOT NULL)";

  private ModalParameterTable() {}

  /** Uses the selected cost file's stem so its parameter table has a predictable name. */
  static String nameForCostFile(String fileName) {
    if (fileName == null
        || fileName.length() <= ".costs".length()
        || !fileName.endsWith(".costs")) {
      throw new IllegalArgumentException("Select a .costs file for modal choice estimation");
    }
    return validateName(fileName.substring(0, fileName.length() - ".costs".length()) + "_params");
  }

  static String validateName(String name) {
    if (name == null || name.length() > 64 || !name.matches("[A-Za-z0-9_&()-][A-Za-z0-9_ &()-]*")) {
      throw new IllegalArgumentException(
          "The cost file name must produce a table name of at most 64 characters, "
              + "using only letters, digits, spaces, underscores, hyphens, ampersands "
              + "or parentheses (without a leading space)");
    }
    return name;
  }

  /** Quotes a validated identifier using the active database's own quote character. */
  private static String quoted(Connection connection, String name) throws SQLException {
    validateName(name);
    if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      return name;
    }
    String quote = connection.getMetaData().getIdentifierQuoteString();
    if (quote == null || quote.isBlank()) {
      throw new SQLException("This database cannot quote parameter table name: " + name);
    }
    return quote + name.replace(quote, quote + quote) + quote;
  }

  static boolean exists(Connection connection, String name) throws Exception {
    validateName(name);
    try (ResultSet tables =
        connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
      while (tables.next()) {
        if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
          return true;
        }
      }
    }
    return false;
  }

  static Properties load(AssignmentParameters assignment, String method) {
    String name = assignment.getCostFunctions().getProperty(POINTER);
    if (name == null || name.isBlank()) {
      return assignment
          .getCostFunctions(); // Existing projects retain their saved coefficient files.
    }
    Connection connection = assignment.getNodusProject().getMainJDBCConnection();
    Properties values = new Properties();
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT param_key, param_value FROM " + quoted(connection, name))) {
      while (rows.next()) {
        String key = rows.getString(1);
        String value = rows.getString(2);
        if (key == null || value == null || values.putIfAbsent(key, value) != null) {
          throw new IllegalArgumentException("Invalid or duplicate parameter key in " + name);
        }
      }
    } catch (Exception failure) {
      throw new IllegalArgumentException(
          "Cannot read modal-choice parameter table " + name, failure);
    }
    String storedMethod = values.getProperty(METHOD);
    if (!method.equals(storedMethod)) {
      throw new IllegalArgumentException(
          "Parameter table " + name + " is for " + storedMethod + ", not " + method);
    }
    return values;
  }

  static void checkSchema(Connection connection, String name) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet ignored =
            statement.executeQuery(
                "SELECT param_key, param_value, param_type FROM "
                    + quoted(connection, name)
                    + " WHERE 1=0")) {
      // A selected existing table must have the same key/value layout as the export.
    }
  }

  static double validatePivotMaxAbs(double maxAbs) {
    if (!Double.isFinite(maxAbs) || maxAbs <= 0) {
      throw new IllegalArgumentException(
          "Pivot maximum absolute value must be finite and positive");
    }
    return maxAbs;
  }

  static double pivotMaxAbs(Properties values) {
    String text = values.getProperty(PIVOT_MAX_ABS, Double.toString(DEFAULT_PIVOT_MAX_ABS));
    try {
      return validatePivotMaxAbs(Double.parseDouble(text));
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException("Invalid pivot maximum absolute value: " + text, failure);
    }
  }

  static double pivot(
      Properties values, int mode, int origin, int destination, int group, double maxAbs) {
    String value = values.getProperty(pivotKey(mode, origin, destination, group));
    if (value == null) {
      return 0;
    }
    try {
      double pivot = Double.parseDouble(value);
      if (Double.isFinite(pivot) && Math.abs(pivot) <= Math.nextUp(maxAbs)) {
        return pivot;
      }
    } catch (NumberFormatException ignored) {
      // Report the key below.
    }
    throw new IllegalArgumentException("Invalid modal-choice pivot: " + value);
  }

  static String pivotKey(int mode, int origin, int destination, int group) {
    return "pivot." + mode + "." + origin + "." + destination + "." + group;
  }

  static void save(Connection connection, String name, Properties values) throws Exception {
    save(connection, name, values, () -> true);
  }

  static void save(
      Connection connection,
      String name,
      Properties values,
      java.util.function.BooleanSupplier proceed)
      throws Exception {
    String table = quoted(connection, name);
    for (String key : values.stringPropertyNames()) {
      if (key.length() > MAX_KEY_LENGTH) {
        throw new IllegalArgumentException(
            "Modal parameter key exceeds " + MAX_KEY_LENGTH + " characters: " + key);
      }
    }
    boolean present = exists(connection, name);
    if (present) {
      checkSchema(connection, name);
    } else {
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("CREATE TABLE " + table + COLUMNS);
      }
    }
    boolean autoCommit = connection.getAutoCommit();
    Savepoint savepoint = null;
    try {
      connection.setAutoCommit(false);
      if (!autoCommit) {
        savepoint = connection.setSavepoint();
      }
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM " + table);
      }
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO " + table + " (param_key,param_value,param_type) VALUES (?,?,?)")) {
        int count = 0;
        int batchChars = 0;
        for (Map.Entry<Object, Object> entry : values.entrySet()) {
          String key = (String) entry.getKey();
          String value = (String) entry.getValue();
          insert.setString(1, key);
          insert.setString(2, value);
          insert.setString(
              3,
              key.startsWith("pivot.")
                  ? "calibration"
                  : key.startsWith("@") ? "setting" : "coefficient");
          insert.addBatch();
          count++;
          batchChars += key.length() + value.length() + 32;
          if (count >= MAX_BATCH_ROWS || batchChars >= MAX_BATCH_CHARS) {
            if (!proceed.getAsBoolean() || Thread.currentThread().isInterrupted()) {
              throw new java.util.concurrent.CancellationException(
                  "Modal parameter saving canceled");
            }
            insert.executeBatch();
            count = 0;
            batchChars = 0;
          }
        }
        if (!proceed.getAsBoolean() || Thread.currentThread().isInterrupted()) {
          throw new java.util.concurrent.CancellationException("Modal parameter saving canceled");
        }
        if (count > 0) {
          insert.executeBatch();
        }
      }
      connection.commit();
    } catch (Exception failure) {
      if (savepoint == null) {
        connection.rollback();
      } else {
        connection.rollback(savepoint);
      }
      if (!present) {
        try (Statement statement = connection.createStatement()) {
          statement.executeUpdate("DROP TABLE IF EXISTS " + table);
        } catch (Exception cleanup) {
          failure.addSuppressed(cleanup);
        }
      }
      throw failure;
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }
}
