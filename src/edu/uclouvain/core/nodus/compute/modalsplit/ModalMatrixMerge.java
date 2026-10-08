/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Optional assignment demand formed by summing the selected modal matrices. */
final class ModalMatrixMerge {
  private ModalMatrixMerge() {}

  static String validateName(String name, Collection<String> sources, String parameterTable) {
    if (name == null || name.isBlank()) {
      return "";
    }
    name = name.trim();
    if (name.length() > 64 || !name.matches("[A-Za-z0-9_&()-][A-Za-z0-9_ &()-]*")) {
      throw new IllegalArgumentException(
          "The merged OD table name must have at most 64 characters and use only letters, "
              + "digits, spaces, underscores, hyphens, ampersands or parentheses.");
    }
    for (String source : sources) {
      if (name.equalsIgnoreCase(source)) {
        throw new IllegalArgumentException(
            "The merged OD table must differ from every selected modal matrix: " + source);
      }
    }
    if (name.equalsIgnoreCase(parameterTable)) {
      throw new IllegalArgumentException(
          "The merged OD table must differ from the parameter table.");
    }
    return name;
  }

  /** Finds the exact identifier in the active schema, including case-preserving table names. */
  private static String existing(Connection connection, String name) throws SQLException {
    try (ResultSet tables =
        connection
            .getMetaData()
            .getTables(connection.getCatalog(), connection.getSchema(), "%", null)) {
      while (tables.next()) {
        if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
          String type = tables.getString("TABLE_TYPE");
          if (!"TABLE".equals(type) && !"BASE TABLE".equals(type)) {
            throw new SQLException("The merged OD name refers to a non-table object: " + name);
          }
          return tables.getString("TABLE_NAME");
        }
      }
    }
    return null;
  }

  static boolean exists(Connection connection, String name) throws SQLException {
    String actual = existing(connection, name);
    if (actual == null) {
      return false;
    }
    columns(connection, actual);
    return true;
  }

  private static String quoted(Connection connection, String name) throws SQLException {
    String quote = connection.getMetaData().getIdentifierQuoteString();
    if (quote == null || quote.isBlank()) {
      throw new SQLException("This database cannot quote OD table identifiers.");
    }
    return quote + name.replace(quote, quote + quote) + quote;
  }

  private static Map<String, String> columns(Connection connection, String table)
      throws SQLException {
    Map<String, String> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery("SELECT * FROM " + quoted(connection, table) + " WHERE 1=0")) {
      for (int index = 1; index <= result.getMetaData().getColumnCount(); index++) {
        String name = result.getMetaData().getColumnName(index);
        columns.put(name, quoted(connection, name));
      }
    }
    if (!columns.keySet().containsAll(List.of("grp", "org", "dst", "qty"))
        || columns.containsKey("iteration")) {
      throw new SQLException("Not a basic OD table: " + table);
    }
    return columns;
  }

  /**
   * Creates an empty destination before the parameter transaction (DDL can implicitly commit). Its
   * contents are subsequently written inside that transaction, so a failed or canceled save
   * restores an existing destination together with the previous parameters.
   */
  static Prepared prepare(
      Connection connection,
      String name,
      Collection<String> sources,
      String parameterTable,
      boolean overwrite)
      throws SQLException {
    return prepare(connection, connection, name, sources, parameterTable, overwrite);
  }

  static Prepared prepare(
      Connection connection,
      Connection reader,
      String name,
      Collection<String> sources,
      String parameterTable,
      boolean overwrite)
      throws SQLException {
    name = validateName(name, sources, parameterTable);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Supply a merged OD table name.");
    }
    List<String> queries = new ArrayList<>();
    for (String source : sources) {
      String actual = existing(reader, source);
      if (actual == null) {
        throw new SQLException("Modal OD table not found: " + source);
      }
      Map<String, String> fields = columns(reader, actual);
      queries.add(
          "SELECT "
              + fields.get("grp")
              + " AS grp, "
              + fields.get("org")
              + " AS org, "
              + fields.get("dst")
              + " AS dst, COALESCE("
              + fields.get("qty")
              + ", 0) AS qty FROM "
              + quoted(reader, actual));
    }
    if (queries.isEmpty()) {
      throw new IllegalArgumentException("Select modal OD tables to merge.");
    }
    String actual = existing(connection, name);
    boolean created = actual == null;
    if (!created && !overwrite) {
      throw new SQLException(
          "The merged OD table now exists; confirm replacement before retrying: " + name);
    }
    if (created) {
      if (!connection.getAutoCommit()) {
        throw new SQLException(
            "Create the merged OD table using a separate auto-commit connection.");
      }
      actual = name;
      if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        if (connection.getMetaData().storesUpperCaseIdentifiers()) {
          actual = name.toUpperCase(java.util.Locale.ROOT);
        } else if (connection.getMetaData().storesLowerCaseIdentifiers()) {
          actual = name.toLowerCase(java.util.Locale.ROOT);
        }
      }
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "CREATE TABLE "
                + quoted(connection, actual)
                + " (grp INTEGER, org INTEGER, dst INTEGER, qty DECIMAL(12,0))");
      }
    }
    Map<String, String> fields = columns(connection, actual);
    String table = quoted(connection, actual);
    String insert =
        "INSERT INTO "
            + table
            + " ("
            + fields.get("grp")
            + ","
            + fields.get("org")
            + ","
            + fields.get("dst")
            + ","
            + fields.get("qty")
            + (fields.containsKey("class") ? "," + fields.get("class") : "")
            + ") VALUES (?,?,?,?"
            + (fields.containsKey("class") ? ",0" : "")
            + ")";
    String query =
        "SELECT grp, org, dst, SUM(qty) FROM ("
            + String.join(" UNION ALL ", queries)
            + ") modal_quantities GROUP BY grp, org, dst";
    return new Prepared(connection, reader, table, insert, query, created);
  }

  static final class Prepared implements AutoCloseable {
    private final Connection connection;
    private final Connection reader;
    private final String table;
    private final String insert;
    private final String query;
    private final boolean created;
    private boolean completed;

    private Prepared(
        Connection connection,
        Connection reader,
        String table,
        String insert,
        String query,
        boolean created) {
      this.connection = connection;
      this.reader = reader;
      this.table = table;
      this.insert = insert;
      this.query = query;
      this.created = created;
    }

    void write() throws SQLException {
      if (connection.getAutoCommit()) {
        throw new SQLException("Merge OD quantities inside the parameter save transaction.");
      }
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM " + table);
      }
      // Read through the project connection: selected matrices can have pending edits which
      // must be included without committing unrelated project work on the saving connection.
      try (Statement select = reader.createStatement();
          ResultSet rows = select.executeQuery(query);
          PreparedStatement output = connection.prepareStatement(insert)) {
        int batch = 0;
        while (rows.next()) {
          if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("OD matrix merging canceled");
          }
          for (int column = 1; column <= 3; column++) {
            output.setObject(column, rows.getObject(column));
          }
          output.setBigDecimal(4, rows.getBigDecimal(4));
          output.addBatch();
          if (++batch == 1000) {
            output.executeBatch();
            batch = 0;
          }
        }
        if (batch > 0) {
          output.executeBatch();
        }
      }
    }

    void complete() {
      completed = true;
    }

    @Override
    public void close() throws SQLException {
      if (created && !completed) {
        try (Statement statement = connection.createStatement()) {
          statement.executeUpdate("DROP TABLE " + table);
        }
      }
    }
  }
}
