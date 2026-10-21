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

package edu.uclouvain.core.nodus.compute.rules.gui;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.table.TableModel;

/** Headless persistence and exact comparisons for the node-rule editor. */
final class NodeRulesPersistence {
  static final String SINGLE_ARROW = "\u2192"; // Simple arrow
  static final String DOUBLE_ARROW = "\u2194"; // Bidirectional arrow
  private static final String[] FIELDS = {
    NodusC.DBF_NUM, NodusC.DBF_SCENARIO, NodusC.DBF_GROUP, NodusC.DBF_MODE1,
    NodusC.DBF_MEANS1, NodusC.DBF_MODE2, NodusC.DBF_MEANS2, NodusC.DBF_SYMMETRY
  };

  private NodeRulesPersistence() {}

  /** Copies cells rather than their concatenation or hash, including invalid pending edits. */
  static List<List<Object>> snapshot(TableModel model) {
    final List<List<Object>> rows = new ArrayList<>();
    for (int row = 0; row < model.getRowCount(); row++) {
      final List<Object> cells = new ArrayList<>();
      for (int col = 0; col < model.getColumnCount(); col++) {
        cells.add(model.getValueAt(row, col));
      }
      rows.add(Collections.unmodifiableList(cells));
    }
    return Collections.unmodifiableList(rows);
  }

  /** Applies the editor's loading/unloading, wildcard and non-transit constraints. */
  static boolean isValid(TableModel model, int row) {
    if (row == -1) {
      return true;
    }
    try {
      parse(model, row);
      return true;
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  /** Reads all rows before publishing any; a missing table is an empty editor, without DDL. */
  static List<String[]> load(NodusProject project, String table, int node, int scenario)
      throws SQLException {
    final Connection connection = connection(project);
    final List<String[]> rows = new ArrayList<>();
    if (!exists(connection, table)) {
      return rows;
    }
    final String sql =
        "SELECT "
            + columns()
            + " FROM "
            + quote(table)
            + " WHERE "
            + scope()
            + " ORDER BY "
            + quote(NodusC.DBF_SCENARIO)
            + ","
            + quote(NodusC.DBF_GROUP)
            + ","
            + quote(NodusC.DBF_MODE1)
            + ","
            + quote(NodusC.DBF_MEANS1)
            + ","
            + quote(NodusC.DBF_MODE2)
            + ","
            + quote(NodusC.DBF_MEANS2);
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bindScope(statement, node, scenario);
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          final String[] cells = new String[8];
          for (int col = 0; col < 6; col++) {
            cells[col] = result.getString(col + 2);
          }
          final int symmetry = result.getInt(8);
          if (result.wasNull()) {
            throw new SQLException("Missing node-rule symmetry");
          }
          cells[6] = symmetry == 0 ? SINGLE_ARROW : DOUBLE_ARROW;
          cells[7] = result.getString(1);
          rows.add(cells);
        }
      }
    }
    return rows;
  }

  /**
   * Replaces only this node's generic/current-scenario rules. On an existing transaction, success
   * remains pending and failure rolls back to a savepoint, preserving unrelated work. Auto-commit
   * callers get one complete transaction. Missing-table DDL is isolated on committing-DDL engines.
   * The caller's model is never changed, including when duplicate rows are omitted from storage.
   *
   * @return whether exact duplicates or directions covered by symmetric rules were omitted
   */
  static boolean save(NodusProject project, String table, int node, int scenario, TableModel model)
      throws SQLException {
    final List<Row> rows = new ArrayList<>();
    final Set<List<Integer>> seen = new HashSet<>();
    final List<Row> parsed = new ArrayList<>();
    for (int row = 0; row < model.getRowCount(); row++) {
      final Row rule = parse(model, row);
      if (Math.abs((long) rule.key.get(0)) != Math.abs((long) node)
          || rule.key.get(1) != -1 && rule.key.get(1) != scenario) {
        throw new IllegalArgumentException("Rule is outside the edited node/scenario");
      }
      parsed.add(rule);
    }
    // Symmetric rows cover both directions even when asymmetric rows appeared first in the UI.
    for (boolean symmetric : new boolean[] {true, false}) {
      for (Row rule : parsed) {
        if (rule.symmetric == symmetric && seen.add(rule.key)) {
          rows.add(rule);
          if (symmetric) {
            seen.add(rule.reverseKey());
          }
        }
      }
    }
    final Connection connection = connection(project);
    boolean missing = !exists(connection, table);
    if (missing
        && (!connection.getMetaData().supportsDataDefinitionAndDataManipulationTransactions()
            || connection.getMetaData().dataDefinitionCausesTransactionCommit())) {
      try (Connection schema =
          DriverManager.getConnection(
              project.getLocalProperty(NodusC.PROP_JDBC_URL, connection.getMetaData().getURL()),
              project.getLocalProperty(
                  NodusC.PROP_JDBC_USERNAME, connection.getMetaData().getUserName()),
              project.getLocalProperty(NodusC.PROP_JDBC_PASSWORD, ""))) {
        final String name = connection.getSchema();
        if (name != null) {
          schema.setSchema(name);
        }
        if (!exists(schema, table)) {
          create(schema, table);
        }
      }
      missing = false;
    }
    replace(connection, table, node, scenario, rows, missing);
    return rows.size() != parsed.size();
  }

  private static void replace(
      Connection connection, String table, int node, int scenario, List<Row> rows, boolean create)
      throws SQLException {
    final boolean autoCommit = connection.getAutoCommit();
    boolean restoreAutoCommit = false;
    Savepoint savepoint = null;
    Exception primaryFailure = null;
    try {
      if (autoCommit) {
        connection.setAutoCommit(false);
        restoreAutoCommit = true;
      }
      savepoint = connection.setSavepoint();
      if (create) {
        create(connection, table);
      }
      try (PreparedStatement delete =
          connection.prepareStatement("DELETE FROM " + quote(table) + " WHERE " + scope())) {
        bindScope(delete, node, scenario);
        delete.executeUpdate();
      }
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO " + quote(table) + " (" + columns() + ") VALUES(?,?,?,?,?,?,?,?)")) {
        for (Row row : rows) {
          for (int col = 0; col < 7; col++) {
            insert.setInt(col + 1, row.key.get(col));
          }
          insert.setInt(8, row.symmetric ? 1 : 0);
          insert.executeUpdate();
        }
      }
      if (autoCommit) {
        connection.commit();
      } else {
        connection.releaseSavepoint(savepoint);
      }
    } catch (SQLException | RuntimeException failure) {
      primaryFailure = failure;
      if (savepoint != null) {
        try {
          connection.rollback(savepoint);
        } catch (SQLException rollback) {
          // Enabling auto-commit after a failed rollback could publish the partial replacement.
          restoreAutoCommit = false;
          failure.addSuppressed(rollback);
        }
      }
      throw failure;
    } finally {
      if (restoreAutoCommit) {
        try {
          connection.setAutoCommit(true);
        } catch (SQLException restore) {
          if (primaryFailure != null) {
            primaryFailure.addSuppressed(restore);
          } else {
            throw restore;
          }
        }
      }
    }
  }

  private static Row parse(TableModel model, int row) {
    try {
      final List<Integer> key =
          List.of(
              number(model, row, 7),
              number(model, row, 0),
              number(model, row, 1),
              number(model, row, 2),
              number(model, row, 3),
              number(model, row, 4),
              number(model, row, 5));
      final Object symmetry = model.getValueAt(row, 6);
      if (key.get(1) < -1
          || key.get(2) < -1
          || key.get(2) >= NodusC.MAXMM
          || !validPair(key.get(3), key.get(4))
          || !validPair(key.get(5), key.get(6))
          || key.get(3).equals(key.get(5)) && key.get(4).equals(key.get(6))
          || !SINGLE_ARROW.equals(symmetry) && !DOUBLE_ARROW.equals(symmetry)) {
        throw new IllegalArgumentException("Invalid node rule in row " + (row + 1));
      }
      return new Row(key, DOUBLE_ARROW.equals(symmetry));
    } catch (NullPointerException | IndexOutOfBoundsException failure) {
      throw new IllegalArgumentException("Incomplete node rule in row " + (row + 1), failure);
    }
  }

  private static int number(TableModel model, int row, int col) {
    return Integer.parseInt(model.getValueAt(row, col).toString());
  }

  private static boolean validPair(int mode, int means) {
    return mode >= -1
        && mode < NodusC.MAXMM
        && means >= -1
        && means < NodusC.MAXMM
        && (mode == -1 ? means == -1 : mode == 0 ? means == 0 : means != 0);
  }

  private static Connection connection(NodusProject project) throws SQLException {
    final Connection connection = project.getMainJDBCConnection();
    if (connection == null) {
      throw new SQLException("No project database connection");
    }
    return connection;
  }

  private static boolean exists(Connection connection, String table) throws SQLException {
    final String name = JDBCUtils.getCompliantIdentifier(table);
    final String schema = connection.getSchema();
    try (ResultSet tables =
        connection
            .getMetaData()
            .getTables(
                connection.getCatalog(), connection.getSchema(), null, new String[] {"TABLE"})) {
      while (tables.next()) {
        if (name.equals(tables.getString("TABLE_NAME"))
            && (schema == null || schema.equals(tables.getString("TABLE_SCHEM")))) {
          return true;
        }
      }
    }
    return false;
  }

  private static void create(Connection connection, String table) throws SQLException {
    final String[] types = {
      "NUMERIC(11,0)",
      "NUMERIC(2,0)",
      "NUMERIC(2,0)",
      "NUMERIC(2,0)",
      "NUMERIC(2,0)",
      "NUMERIC(2,0)",
      "NUMERIC(2,0)",
      "NUMERIC(1,0)"
    };
    final List<String> fields = new ArrayList<>();
    for (int col = 0; col < FIELDS.length; col++) {
      fields.add(quote(FIELDS[col]) + " " + types[col]);
    }
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(10);
      statement.executeUpdate(
          "CREATE TABLE " + quote(table) + " (" + String.join(",", fields) + ")");
    }
  }

  private static String columns() {
    final List<String> fields = new ArrayList<>();
    for (String field : FIELDS) {
      fields.add(quote(field));
    }
    return String.join(",", fields);
  }

  private static String scope() {
    return "ABS("
        + quote(NodusC.DBF_NUM)
        + ")=? AND ("
        + quote(NodusC.DBF_SCENARIO)
        + "=-1 OR "
        + quote(NodusC.DBF_SCENARIO)
        + "=?)";
  }

  private static void bindScope(PreparedStatement statement, int node, int scenario)
      throws SQLException {
    statement.setLong(1, Math.abs((long) node));
    statement.setInt(2, scenario);
  }

  private static String quote(String identifier) {
    return JDBCUtils.getQuotedCompliantIdentifier(identifier);
  }

  private static final class Row {
    final List<Integer> key;
    final boolean symmetric;

    Row(List<Integer> key, boolean symmetric) {
      this.key = key;
      this.symmetric = symmetric;
    }

    List<Integer> reverseKey() {
      return List.of(
          key.get(0), key.get(1), key.get(2), key.get(5), key.get(6), key.get(3), key.get(4));
    }
  }
}
