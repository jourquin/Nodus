/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Replaces prepared MySQL/MariaDB tables by changing their names together in one SQL statement.
 * "Publication" means making the completed staging tables available under the names normally read
 * by Nodus. This helper is used for imported tables and for an assignment's related output tables.
 *
 * <p>For example, an assignment prepares a new virtual network and path headers under private
 * names. Its previous results remain at their public names until both new tables are ready.
 * Publication then issues one statement, illustrated here with simplified names:
 *
 * <pre>{@code
 * RENAME TABLE vnet56      TO backup_v,
 *              staged_v   TO vnet56,
 *              path56_header TO backup_h,
 *              staged_h   TO path56_header;
 * }</pre>
 *
 * <p>The previous tables now exist under backup names, and the new tables have the public names.
 * Using one grouped rename avoids the gap between separate rename statements, during which public
 * tables could be missing or belong to different assignment runs. This is needed with InnoDB too:
 * schema operations can implicitly commit, so a JDBC rollback alone cannot undo these renames.
 *
 * <p>The caller follows this lifecycle, using a fresh helper for each publication attempt:
 *
 * <ol>
 *   <li>Create, populate and finish writing the staging tables.
 *   <li>Call {@link #add} for each destination, supplying unique staging and backup names in the
 *       connection's current database. Pass a null staging name for obsolete output to move aside.
 *   <li>Call {@link #publish}, then complete any caller-managed publication commit.
 *   <li>On success, delete the backups. If publication or its final commit throws, call {@link
 *       #restore} before cleaning up staging tables. Retain backups if restoration fails.
 * </ol>
 *
 * <p>This class only inspects metadata and renames tables. The caller owns table creation, data
 * loading, cleanup, error reporting, the connection, and explicit commits/rollbacks. The helper
 * does not change auto-commit or close the connection. Identifier quoting uses the configured
 * {@link JDBCUtils} database dialect. Table names must be unquoted, unqualified identifiers.
 *
 * <p>Recovery handles an SQL exception both before the server executes a rename and after it has
 * executed it but JDBC reports an error (for example, a lost acknowledgement). It inspects actual
 * table names rather than assuming that an exception means nothing changed. Restoration reverses
 * name changes; it does not undo edits made to the published tables by other connections. Callers
 * must coordinate access to these destinations. This is an in-process recovery helper, not a
 * persistent recovery journal; server-crash guarantees depend on the server version/storage engine.
 *
 * @see TableImport
 * @see edu.uclouvain.core.nodus.compute.virtual.AssignmentOutputTables
 */
public final class MySqlTablePublication {
  private final Connection connection;
  private final List<Replacement> replacements = new ArrayList<>();
  // Set before SQL execution: even an exception from execute/close can follow a successful rename.
  private boolean attempted;

  /** One planned name change, including whether a previous public table must be preserved. */
  private static final class Replacement {
    final String destination;
    final String staged;
    final String backup;
    final boolean original;

    Replacement(String destination, String staged, String backup, boolean original) {
      this.destination = destination;
      this.staged = staged;
      this.backup = backup;
      this.original = original;
    }
  }

  /**
   * Creates an empty publication plan without executing SQL or changing connection settings.
   *
   * @param connection caller-owned MySQL/MariaDB connection; retained for publication and recovery
   */
  public MySqlTablePublication(Connection connection) {
    this.connection = connection;
  }

  /**
   * Identifies MySQL/MariaDB so callers can select this SQL dialect instead of their generic path.
   * This checks the product name; it does not probe server privileges or crash-recovery guarantees.
   *
   * @param connection connection whose database product is inspected
   * @return whether the reported product is MySQL or MariaDB
   * @throws SQLException if database metadata cannot be read
   */
  public static boolean isSupported(Connection connection) throws SQLException {
    String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    return product.contains("mysql") || product.contains("mariadb");
  }

  /**
   * Records a replacement without renaming anything. The destination's current existence is saved
   * for recovery; the backup name must be unused. The staging table is checked later by {@link
   * #publish}, after every replacement has been registered.
   *
   * <p>If the destination does not exist, publication only renames staging to destination. If
   * staging is null, publication only moves an existing destination to backup: this removes
   * obsolete path details from the public output set while keeping them available for recovery.
   *
   * @param destination public table name
   * @param staged complete new table, or null to remove obsolete output on publication
   * @param backup unique private name for the previous table
   * @return whether the destination currently exists
   * @throws SQLException if metadata is unavailable or the backup name is already occupied
   */
  public boolean add(String destination, String staged, String backup) throws SQLException {
    if (exists(backup)) {
      throw new SQLException("Backup table already exists: " + backup);
    }
    boolean original = exists(destination);
    replacements.add(new Replacement(destination, staged, backup, original));
    return original;
  }

  /**
   * Validates the staging tables and submits the entire plan as one {@code RENAME TABLE} statement.
   * Original tables move to backup names before their staging replacements take the public names.
   * Backups remain until the caller explicitly cleans them up after success.
   *
   * <p>Never falls back to sequential renames on error: the caller must attempt {@link #restore}.
   * Statement atomicity and crash recovery are distinct; crash guarantees depend on server/engine.
   *
   * @throws SQLException if a staged table is missing or the rename fails
   */
  public void publish() throws SQLException {
    StringJoiner names = new StringJoiner(", ");
    for (Replacement replacement : replacements) {
      if (replacement.staged != null && !exists(replacement.staged)) {
        throw new SQLException("Missing staged table: " + replacement.staged);
      }
      if (replacement.original) {
        rename(names, replacement.destination, replacement.backup);
      }
      if (replacement.staged != null) {
        rename(names, replacement.staged, replacement.destination);
      }
    }
    attempted = true;
    execute(names);
  }

  /**
   * Reverses publication with one grouped rename, without dropping any table. Call this on a failed
   * publication or failed final commit, while all backup tables are still available.
   *
   * <p>If publication never reached execution, this does nothing. Otherwise, actual names determine
   * whether the server executed the rename: a remaining staging table normally means it did not; a
   * backup and a public replacement mean it did. New public tables move back to staging names, and
   * backups return to their public names. On a first assignment/import without previous output, the
   * new public tables simply move back to staging for caller cleanup.
   *
   * <p>If metadata is unavailable or the names do not permit safe restoration, this throws. Nothing
   * is dropped, so any remaining backups are available for manual recovery.
   *
   * @throws SQLException if the original tables cannot be safely restored
   */
  public void restore() throws SQLException {
    if (!attempted) {
      return;
    }
    StringJoiner names = new StringJoiner(", ");
    for (Replacement replacement : replacements) {
      boolean backup = exists(replacement.backup);
      boolean staged = replacement.staged != null && exists(replacement.staged);
      boolean destination = exists(replacement.destination);
      // Neither the old backup nor the untouched staging table can identify a safe prior state.
      if (replacement.original && !backup && !staged && replacement.staged != null) {
        throw new SQLException("Cannot locate original backup: " + replacement.backup);
      }
      if (replacement.staged != null
          && !staged
          && destination
          && (backup || !replacement.original)) {
        // Free the public name while keeping the newly computed/imported table for caller cleanup.
        rename(names, replacement.destination, replacement.staged);
      }
      if (backup) {
        rename(names, replacement.backup, replacement.destination);
      }
    }
    execute(names);
  }

  private boolean exists(String name) throws SQLException {
    return JDBCUtils.tableExists(connection, name);
  }

  /** Appends a rename pair to the plan; SQL is executed only after the whole group is assembled. */
  private static void rename(StringJoiner names, String from, String to) {
    names.add(
        JDBCUtils.getQuotedCompliantIdentifier(from)
            + " TO "
            + JDBCUtils.getQuotedCompliantIdentifier(to));
  }

  private void execute(StringJoiner names) throws SQLException {
    if (names.length() == 0) {
      return;
    }
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(10);
      statement.executeUpdate("RENAME TABLE " + names);
    }
  }
}
