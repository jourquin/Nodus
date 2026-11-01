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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    replace(project, table, loader, new ImportProgress());
  }

  /**
   * Stages an import with cancellation accepted until the completed table is published.
   *
   * @param project project supplying the database connection
   * @param table destination table
   * @param loader operation that creates and fills the staging table
   * @param progress import progress and cancellation control
   * @throws Exception if metadata protection, loading, cancellation or publication fails
   */
  @SuppressWarnings("resource") // The main connection is borrowed from the project.
  public static void replace(
      NodusProject project, String table, Loader loader, ImportProgress progress) throws Exception {
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
        replace(connection, table, loader, true, progress);
      }
    } else {
      replace(main, table, loader, false, progress);
    }
  }

  static void replace(Connection connection, String table, Loader loader, boolean committingDdl)
      throws Exception {
    replace(connection, table, loader, committingDdl, new ImportProgress());
  }

  private static void replace(
      Connection connection,
      String table,
      Loader loader,
      boolean committingDdl,
      ImportProgress progress)
      throws Exception {
    progress.checkCancelled();
    TableDefinitionGuard.check(connection, table);
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String staged = "NDI_" + suffix;
    String backup = "NDB_" + suffix;
    boolean autoCommit = connection.getAutoCommit();
    boolean restoreAutoCommit = autoCommit;
    Savepoint savepoint = null;
    boolean originalRenamed = false;
    boolean installed = false;
    MySqlTablePublication grouped = null;
    try {
      if (autoCommit) {
        connection.setAutoCommit(false);
      }
      if (!committingDdl) {
        savepoint = connection.setSavepoint();
      }
      loader.load(connection, staged);
      progress.checkCancelled();
      TableDefinitionGuard.check(connection, table);
      TableDefinitionGuard.checkReplacementPermissions(connection, table, staged);
      progress.finishing();
      if (committingDdl) {
        connection.commit();
      }
      if (committingDdl && MySqlTablePublication.isSupported(connection)) {
        grouped = new MySqlTablePublication(connection);
        originalRenamed = grouped.add(table, staged, backup);
        grouped.publish();
      } else {
        if (exists(connection, table)) {
          rename(connection, table, backup);
          originalRenamed = true;
        }
        rename(connection, staged, table);
      }
      installed = true;
      if (committingDdl) {
        // Retain the original until the replacement's final commit has succeeded.
        connection.commit();
      }
      if (originalRenamed) {
        if (grouped == null) {
          execute(connection, "DROP TABLE " + quoted(backup));
        } else {
          // Publication is committed. Optional cleanup cannot undo a successful import.
          try {
            execute(connection, "DROP TABLE " + quoted(backup));
          } catch (SQLException cleanup) {
            Logger.getLogger(TableImport.class.getName())
                .log(
                    Level.WARNING,
                    "Imported table published; check backup cleanup for " + backup,
                    cleanup);
          }
        }
        originalRenamed = false;
      }
      if (autoCommit && !committingDdl) {
        connection.commit();
      } else if (!committingDdl) {
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
          if (grouped != null) {
            try {
              grouped.restore();
              installed = false;
            } catch (SQLException recovery) {
              failure.addSuppressed(
                  new SQLException("Retain backup " + backup + " for manual recovery.", recovery));
              throw failure;
            }
          } else if (originalRenamed) {
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
    return JDBCUtils.tableExists(connection, table);
  }

  private static void rename(Connection connection, String from, String to) throws SQLException {
    execute(connection, "ALTER TABLE " + quoted(from) + " RENAME TO " + quoted(to));
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
   * Refuses in-place row replacement when the destination cannot reliably roll back changes.
   *
   * @param connection borrowed destination connection
   * @param table destination whose storage engine must support rollback
   * @throws SQLException if transaction support is absent or cannot be established
   */
  public static void requireTransactionalRows(Connection connection, String table)
      throws SQLException {
    if (!connection.getMetaData().supportsTransactions()) {
      throw new SQLException(
          "Cannot safely import into table \""
              + table
              + "\": this database does not support rollback.");
    }
    String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    if (!product.contains("mysql") && !product.contains("mariadb")) {
      return;
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT e.TRANSACTIONS FROM information_schema.TABLES t"
                + " LEFT JOIN information_schema.ENGINES e ON t.ENGINE=e.ENGINE"
                + " WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME=?")) {
      String name = table;
      if (connection.getMetaData().storesLowerCaseIdentifiers()) {
        name = table.toLowerCase(Locale.ROOT);
      } else if (connection.getMetaData().storesUpperCaseIdentifiers()) {
        name = table.toUpperCase(Locale.ROOT);
      }
      statement.setString(1, name);
      statement.setQueryTimeout(10);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next() || !"YES".equalsIgnoreCase(rows.getString(1))) {
          throw new SQLException(
              "Cannot safely import into table \""
                  + table
                  + "\": its storage engine does not provide verified rollback support."
                  + " Use a transactional table or import into a new table.");
        }
      }
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
