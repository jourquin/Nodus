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

package edu.uclouvain.core.nodus.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** Refuses schema replacement when it would discard an existing table's definition or grants. */
final class TableDefinitionGuard {
  private TableDefinitionGuard() {}

  static void check(Connection connection, String table) throws SQLException {
    if (!JDBCUtils.tableExists(connection, table)) {
      return;
    }
    DatabaseMetaData metadata = connection.getMetaData();
    String product = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
    boolean mysql = product.contains("mysql") || product.contains("mariadb");
    boolean postgres = product.contains("postgresql");
    boolean hsql = product.contains("hsql");
    if (!mysql && !postgres && !hsql && !product.contains("h2")) {
      throw new SQLException(
          "Cannot verify the definition of table \""
              + table
              + "\" before replacement on "
              + metadata.getDatabaseProductName()
              + ". Import into a new table instead.");
    }
    String catalog = null;
    String schema = null;
    String name = null;
    try (ResultSet columns = JDBCUtils.getColumns(connection, table)) {
      while (columns.next()) {
        catalog = columns.getString("TABLE_CAT");
        schema = columns.getString("TABLE_SCHEM");
        name = columns.getString("TABLE_NAME");
        if (columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
            || columns.getString("COLUMN_DEF") != null
            || "YES".equalsIgnoreCase(columns.getString("IS_AUTOINCREMENT"))
            || "YES".equalsIgnoreCase(columns.getString("IS_GENERATEDCOLUMN"))) {
          throw protectedTable(table, "column constraints, defaults or generated values");
        }
      }
    }
    if (name == null) {
      throw new SQLException("Cannot read the definition of table \"" + table + "\".");
    }
    try (ResultSet indexes = metadata.getIndexInfo(catalog, schema, name, false, false)) {
      while (indexes.next()) {
        if (indexes.getShort("TYPE") != DatabaseMetaData.tableIndexStatistic) {
          throw protectedTable(table, "indexes");
        }
      }
    }
    try (ResultSet keys = metadata.getExportedKeys(catalog, schema, name)) {
      if (keys.next()) {
        throw protectedTable(table, "foreign keys referencing it");
      }
    }
    String tableSchema = mysql ? catalog : schema;
    if (tableSchema == null) {
      throw new SQLException("Cannot determine the schema of table \"" + table + "\".");
    }
    if (mysql) {
      requireTriggerVisibility(connection, table, tableSchema, name);
    }
    if (hasRows(
        connection,
        "SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
            + " WHERE TABLE_SCHEMA=? AND TABLE_NAME=?",
        tableSchema,
        name)) {
      throw protectedTable(table, "table constraints");
    }
    if (hasRows(
        connection,
        "SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS"
            + " WHERE EVENT_OBJECT_SCHEMA=? AND EVENT_OBJECT_TABLE=?",
        tableSchema,
        name)) {
      throw protectedTable(table, "triggers");
    }
    String owner = null;
    if (postgres) {
      owner =
          scalar(
              connection,
              "SELECT tableowner FROM pg_catalog.pg_tables" + " WHERE schemaname=? AND tablename=?",
              tableSchema,
              name);
      if (!owner.equals(scalar(connection, "SELECT CURRENT_USER"))) {
        throw protectedTable(table, "ownership that would change during replacement");
      }
    } else if (hsql) {
      owner =
          scalar(
              connection,
              "SELECT SCHEMA_OWNER FROM INFORMATION_SCHEMA.SCHEMATA" + " WHERE SCHEMA_NAME=?",
              tableSchema);
    }
    try (ResultSet grants =
        metadata.getTablePrivileges(catalog, pattern(metadata, schema), pattern(metadata, name))) {
      while (grants.next()) {
        if (name.equals(grants.getString("TABLE_NAME"))
            && (schema == null || schema.equals(grants.getString("TABLE_SCHEM")))
            && !ownerGrant(grants, owner, hsql, postgres)) {
          throw protectedTable(table, "table permissions");
        }
      }
    }
    try (ResultSet grants = metadata.getColumnPrivileges(catalog, schema, name, null)) {
      while (grants.next()) {
        if (!ownerGrant(grants, owner, hsql, postgres)) {
          throw protectedTable(table, "column permissions");
        }
      }
    }
  }

  /** PostgreSQL defaults can grant new permissions to a replacement; preserve the old policy. */
  static void checkReplacementPermissions(Connection connection, String table, String staged)
      throws SQLException {
    DatabaseMetaData metadata = connection.getMetaData();
    if (!metadata.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgresql")
        || !JDBCUtils.tableExists(connection, table)) {
      return;
    }
    String owner = scalar(connection, "SELECT CURRENT_USER");
    try (ResultSet columns = JDBCUtils.getColumns(connection, staged)) {
      if (!columns.next()) {
        throw new SQLException(
            "Cannot verify replacement permissions for table \"" + table + "\".");
      }
      String catalog = columns.getString("TABLE_CAT");
      String schema = columns.getString("TABLE_SCHEM");
      String name = columns.getString("TABLE_NAME");
      try (ResultSet grants =
          metadata.getTablePrivileges(
              catalog, pattern(metadata, schema), pattern(metadata, name))) {
        while (grants.next()) {
          if (name.equals(grants.getString("TABLE_NAME"))
              && schema.equals(grants.getString("TABLE_SCHEM"))
              && !owner.equals(grants.getString("GRANTEE"))) {
            throw protectedTable(table, "permissions that would change due to database defaults");
          }
        }
      }
      try (ResultSet grants = metadata.getColumnPrivileges(catalog, schema, name, null)) {
        while (grants.next()) {
          if (!owner.equals(grants.getString("GRANTEE"))) {
            throw protectedTable(
                table, "column permissions that would change due to database defaults");
          }
        }
      }
    }
  }

  private static boolean ownerGrant(ResultSet grants, String owner, boolean hsql, boolean postgres)
      throws SQLException {
    return owner != null
        && owner.equals(grants.getString("GRANTEE"))
        && (postgres || (hsql && "_SYSTEM".equals(grants.getString("GRANTOR"))));
  }

  /** MySQL hides triggers from accounts without TRIGGER; an empty list alone is not sufficient. */
  private static void requireTriggerVisibility(
      Connection connection, String table, String schema, String name) throws SQLException {
    String account = scalar(connection, "SELECT CURRENT_USER()");
    int separator = account.lastIndexOf('@');
    if (separator < 0) {
      throw new SQLException("Cannot verify trigger visibility for table \"" + table + "\".");
    }
    String grantee =
        "'" + account.substring(0, separator) + "'@'" + account.substring(separator + 1) + "'";
    boolean visible =
        hasRows(
                connection,
                "SELECT 1 FROM information_schema.USER_PRIVILEGES"
                    + " WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER'",
                grantee)
            || hasRows(
                connection,
                "SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES"
                    + " WHERE GRANTEE=? AND TABLE_SCHEMA=? AND PRIVILEGE_TYPE='TRIGGER'",
                grantee,
                schema)
            || hasRows(
                connection,
                "SELECT 1 FROM information_schema.TABLE_PRIVILEGES"
                    + " WHERE GRANTEE=? AND TABLE_SCHEMA=? AND TABLE_NAME=?"
                    + " AND PRIVILEGE_TYPE='TRIGGER'",
                grantee,
                schema,
                name);
    if (!visible) {
      throw new SQLException(
          "Cannot safely replace table \""
              + table
              + "\": trigger visibility cannot be verified for this account."
              + " Import into a new table or use an import that preserves"
              + " the existing definition.");
    }
  }

  private static String pattern(DatabaseMetaData metadata, String name) throws SQLException {
    String escape = metadata.getSearchStringEscape();
    if (name == null || escape == null || escape.isEmpty() || name.contains(escape)) {
      return null;
    }
    return name.replace("_", escape + "_").replace("%", escape + "%");
  }

  private static boolean hasRows(Connection connection, String sql, String... parameters)
      throws SQLException {
    try (PreparedStatement statement = prepare(connection, sql, parameters);
        ResultSet rows = statement.executeQuery()) {
      return rows.next();
    }
  }

  private static String scalar(Connection connection, String sql, String... parameters)
      throws SQLException {
    try (PreparedStatement statement = prepare(connection, sql, parameters);
        ResultSet rows = statement.executeQuery()) {
      if (!rows.next()) {
        throw new SQLException("Cannot determine the owner of the table being replaced.");
      }
      return rows.getString(1);
    }
  }

  private static PreparedStatement prepare(Connection connection, String sql, String[] parameters)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    try {
      statement.setQueryTimeout(10);
      for (int i = 0; i < parameters.length; i++) {
        statement.setString(i + 1, parameters[i]);
      }
      return statement;
    } catch (SQLException error) {
      statement.close();
      throw error;
    }
  }

  private static SQLException protectedTable(String table, String detail) {
    return new SQLException(
        "Cannot replace table \""
            + table
            + "\": it has "
            + detail
            + ". Import into a new table or use an import that preserves the existing definition.");
  }
}
