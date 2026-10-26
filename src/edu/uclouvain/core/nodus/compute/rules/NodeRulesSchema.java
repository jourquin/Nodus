/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.rules;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.Locale;

/** Keeps node-rule scenario storage compatible with the full application scenario range. */
public final class NodeRulesSchema {
  /** Includes generic scenario -1 and every scenario up to MAXSCENARIOS. */
  public static final String SCENARIO_TYPE =
      "NUMERIC(" + Integer.toString(NodusC.MAXSCENARIOS).length() + ",0)";

  private NodeRulesSchema() {}

  /**
   * Widens legacy numeric scenario fields without committing pending edits on the project session.
   *
   * @param project project owning the table
   * @param table existing rules table
   * @throws SQLException if metadata or schema migration fails
   */
  public static void ensureScenarioRange(NodusProject project, String table) throws SQLException {
    Connection main = project.getMainJDBCConnection();
    if (main == null) {
      throw new SQLException("No project database connection");
    }
    try (Statement statement = main.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT " + quote(NodusC.DBF_SCENARIO) + " FROM " + quote(table) + " WHERE 1=0")) {
      int type = result.getMetaData().getColumnType(1);
      int precision = result.getMetaData().getPrecision(1);
      if ((type != Types.NUMERIC && type != Types.DECIMAL)
          || precision == 0
          || precision >= Integer.toString(NodusC.MAXSCENARIOS).length()) {
        return;
      }
    }
    if (main.getMetaData().supportsDataDefinitionAndDataManipulationTransactions()
        && !main.getMetaData().dataDefinitionCausesTransactionCommit()) {
      Savepoint boundary = main.getAutoCommit() ? null : main.setSavepoint();
      try {
        widen(main, table);
        if (boundary != null) {
          main.releaseSavepoint(boundary);
        }
      } catch (SQLException failure) {
        if (boundary != null) {
          try {
            main.rollback(boundary);
          } catch (SQLException rollback) {
            failure.addSuppressed(rollback);
          }
        }
        throw failure;
      }
    } else {
      try (Connection schema =
          DriverManager.getConnection(
              project.getLocalProperty(NodusC.PROP_JDBC_URL, main.getMetaData().getURL()),
              project.getLocalProperty(NodusC.PROP_JDBC_USERNAME, main.getMetaData().getUserName()),
              project.getLocalProperty(NodusC.PROP_JDBC_PASSWORD, ""))) {
        if (main.getCatalog() != null) {
          schema.setCatalog(main.getCatalog());
        }
        if (main.getSchema() != null) {
          schema.setSchema(main.getSchema());
        }
        widen(schema, table);
      }
    }
  }

  private static void widen(Connection connection, String table) throws SQLException {
    String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    String change = product.contains("mysql") || product.contains("mariadb") ? "MODIFY" : "ALTER";
    String type = product.contains("postgresql") ? " TYPE " : " ";
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(10);
      statement.executeUpdate(
          "ALTER TABLE "
              + quote(table)
              + " "
              + change
              + " COLUMN "
              + quote(NodusC.DBF_SCENARIO)
              + type
              + SCENARIO_TYPE);
    }
  }

  private static String quote(String name) {
    return JDBCUtils.getQuotedCompliantIdentifier(name);
  }
}
