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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.awt.GraphicsEnvironment;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.UUID;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Stages a complete import before replacing a table, retaining the original on failure. */
public final class TableImport {
  private TableImport() {}

  /** Creates and fills the supplied staging table, throwing if any part of the input is invalid. */
  @FunctionalInterface
  public interface Loader {
    /**
     * Loads data into a staging table before it replaces the destination.
     *
     * @param connection connection on which the staging table exists
     * @param table name of the staging table to fill
     * @throws Exception if the input cannot be loaded
     */
    void load(Connection connection, String table) throws Exception;
  }

  /**
   * Replaces a table only after its replacement has been completely loaded. Engines with committing
   * DDL use an independent connection so failed imports cannot commit unrelated work on the project
   * connection. Successful schema replacements on those engines are necessarily committed
   * independently. A locked destination must be unlocked by the caller.
   *
   * @param project project providing the database connection
   * @param table destination table to replace
   * @param loader operation that fills the staging table
   * @throws Exception if loading or replacing the table fails
   */
  public static void replace(NodusProject project, String table, Loader loader) throws Exception {
    Connection main = project.getMainJDBCConnection();
    if (main == null) {
      throw new SQLException("No project database connection");
    }
    boolean separate =
        !main.getMetaData().supportsDataDefinitionAndDataManipulationTransactions()
            || main.getMetaData().dataDefinitionCausesTransactionCommit();
    if (separate) {
      String url = project.getLocalProperty(NodusC.PROP_JDBC_URL, main.getMetaData().getURL());
      String user =
          project.getLocalProperty(NodusC.PROP_JDBC_USERNAME, main.getMetaData().getUserName());
      String password = project.getLocalProperty(NodusC.PROP_JDBC_PASSWORD, "");
      try (Connection connection = DriverManager.getConnection(url, user, password)) {
        String schema = main.getSchema();
        if (schema != null) {
          connection.setSchema(schema);
        }
        replace(connection, table, loader, true);
      }
    } else {
      replace(main, table, loader, false);
    }
  }

  static void replace(Connection connection, String table, Loader loader, boolean committingDdl)
      throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String staged = "NDI_" + suffix;
    String backup = "NDB_" + suffix;
    boolean autoCommit = connection.getAutoCommit();
    boolean restoreAutoCommit = autoCommit;
    Savepoint savepoint = null;
    boolean originalRenamed = false;
    boolean installed = false;
    try {
      if (autoCommit) {
        connection.setAutoCommit(false);
      }
      if (!committingDdl) {
        savepoint = connection.setSavepoint();
      }
      loader.load(connection, staged);
      if (committingDdl) {
        connection.commit();
      }
      if (exists(connection, table)) {
        rename(connection, table, backup);
        originalRenamed = true;
      }
      rename(connection, staged, table);
      installed = true;
      if (originalRenamed) {
        execute(connection, "DROP TABLE " + quoted(backup));
        originalRenamed = false;
      }
      if (autoCommit || committingDdl) {
        connection.commit();
      } else {
        connection.releaseSavepoint(savepoint);
      }
    } catch (Exception failure) {
      try {
        if (!committingDdl) {
          if (savepoint != null) {
            connection.rollback(savepoint);
          } else if (autoCommit) {
            connection.rollback();
          }
        } else {
          connection.rollback();
          if (originalRenamed) {
            try {
              if (installed) {
                rename(connection, table, staged);
                installed = false;
              }
              rename(connection, backup, table);
            } catch (SQLException recovery) {
              failure.addSuppressed(
                  new SQLException(
                      "Original table is preserved as " + backup + ". Restore it before retrying.",
                      recovery));
              throw failure;
            }
          }
          if (!installed && exists(connection, staged)) {
            execute(connection, "DROP TABLE " + quoted(staged));
          }
        }
      } catch (SQLException recovery) {
        // Enabling auto-commit after a failed rollback could commit the failed import.
        restoreAutoCommit = false;
        if (recovery != failure) {
          failure.addSuppressed(recovery);
        }
      }
      throw failure;
    } finally {
      if (restoreAutoCommit) {
        connection.setAutoCommit(true);
      }
    }
  }

  private static boolean exists(Connection connection, String table) throws SQLException {
    String name = JDBCUtils.getCompliantIdentifier(table);
    try (java.sql.ResultSet tables =
        connection
            .getMetaData()
            .getTables(
                connection.getCatalog(), connection.getSchema(), null, new String[] {"TABLE"})) {
      while (tables.next()) {
        if (name.equals(tables.getString("TABLE_NAME"))) {
          return true;
        }
      }
    }
    return false;
  }

  private static void rename(Connection connection, String from, String to) throws SQLException {
    String command =
        connection.getMetaData().getDatabaseProductName().contains("Derby")
            ? "RENAME TABLE "
            : "ALTER TABLE ";
    execute(
        connection,
        command
            + quoted(from)
            + (command.startsWith("ALTER") ? " RENAME TO " : " TO ")
            + quoted(to));
  }

  private static String quoted(String name) {
    return JDBCUtils.getQuotedCompliantIdentifier(name);
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(10);
      statement.executeUpdate(sql);
    }
  }

  /**
   * Reports failures in interactive use and remains usable from headless scripts and tests.
   *
   * @param error failure to report
   */
  public static void reportError(Exception error) {
    error.printStackTrace();
    if (!GraphicsEnvironment.isHeadless()) {
      Runnable report =
          () ->
              JOptionPane.showMessageDialog(
                  null, error.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      if (SwingUtilities.isEventDispatchThread()) {
        report.run();
      } else {
        SwingUtilities.invokeLater(report);
      }
    }
  }
}
