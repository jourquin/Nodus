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

package edu.uclouvain.core.nodus.compute.virtual;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.MySqlTablePublication;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Stages scenario output until all writers finish, preserving previous results on failure.
 *
 * <p>DDL and writer commits use the project connection. They may commit earlier unrelated work;
 * savepoints protect only inserts made after table creation. Publication uses backup table names
 * because DDL itself cannot be rolled back on every supported database. A failed restoration leaves
 * its backup in place for recovery rather than deleting the last complete output.
 */
public final class AssignmentOutputTables implements AutoCloseable {
  private final Connection connection;
  private final List<Table> tables = new ArrayList<>();
  private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  private boolean published;

  private static final class Table {
    final String destination;
    final String staged;
    final String backup;
    final boolean required;
    boolean backupAttempted;
    boolean installAttempted;

    Table(String destination, String suffix, String kind, boolean required) {
      this.destination = JDBCUtils.getCompliantIdentifier(destination);
      staged = "no_" + suffix + "_" + kind;
      backup = "nb_" + suffix + "_" + kind;
      this.required = required;
    }
  }

  /**
   * Creates private table names for one output attempt without changing existing tables.
   *
   * @param parameters scenario and output options
   * @param withPaths whether this attempt also owns the scenario path tables
   */
  public AssignmentOutputTables(AssignmentParameters parameters, boolean withPaths) {
    this(
        parameters.getNodusProject(),
        parameters.getScenario(),
        withPaths,
        parameters.isSavePaths(),
        parameters.isDetailedPaths());
  }

  /**
   * Stages an arithmetic scenario result and removes obsolete path output only on publication.
   *
   * @param project project containing the scenario
   * @param scenario result scenario ID
   */
  public AssignmentOutputTables(NodusProject project, int scenario) {
    this(project, scenario, true, false, false);
  }

  private AssignmentOutputTables(
      NodusProject project,
      int scenario,
      boolean withPaths,
      boolean headersRequired,
      boolean detailsRequired) {
    connection = project.getMainJDBCConnection();
    tables.add(
        new Table(VirtualNetworkWriter.destinationName(project, scenario), suffix, "v", true));
    if (withPaths) {
      String prefix =
          project.getLocalProperty(
              NodusC.PROP_PATH_TABLE_PREFIX, project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME));
      tables.add(new Table(prefix + scenario + NodusC.SUFFIX_HEADER, suffix, "h", headersRequired));
      tables.add(
          new Table(
              prefix + scenario + NodusC.SUFFIX_DETAIL,
              suffix,
              "d",
              headersRequired && detailsRequired));
    }
  }

  /**
   * Creates the staged virtual-network schema without replacing the public scenario table.
   *
   * @param groups commodity groups in output-column order
   * @return true when table creation succeeds
   */
  public boolean initializeVirtualNetwork(byte[] groups) {
    return VirtualNetworkWriter.initTable(virtualNetwork(), groups);
  }

  /**
   * Returns the private virtual-network output table.
   *
   * @return staging table name
   */
  public String virtualNetwork() {
    return tables.get(0).staged;
  }

  /**
   * Returns the private path-header output table.
   *
   * @return staging table name
   */
  public String pathHeaders() {
    return tables.get(1).staged;
  }

  /**
   * Returns the private path-detail output table.
   *
   * @return staging table name
   */
  public String pathDetails() {
    return tables.get(2).staged;
  }

  /**
   * Publishes the complete output set, restoring previous tables if a rename or commit fails.
   *
   * @throws SQLException when output is missing or publication fails
   */
  public void publish() throws SQLException {
    for (Table table : tables) {
      if (table.required && !tableExists(table.staged)) {
        throw new SQLException("Missing assignment output: " + table.destination);
      }
    }
    MySqlTablePublication grouped = null;
    try {
      if (MySqlTablePublication.isSupported(connection)) {
        grouped = new MySqlTablePublication(connection);
        for (Table table : tables) {
          table.backupAttempted =
              grouped.add(table.destination, table.required ? table.staged : null, table.backup);
        }
        grouped.publish();
      } else {
        for (Table table : tables) {
          if (tableExists(table.destination)) {
            // Record the attempt first: the driver can throw after executing the rename.
            table.backupAttempted = true;
            rename(table.destination, table.backup);
          }
          if (table.required) {
            table.installAttempted = true;
            rename(table.staged, table.destination);
          }
        }
      }
      if (!connection.getAutoCommit()) {
        connection.commit();
      }
      published = true;
    } catch (SQLException failure) {
      if (grouped == null) {
        restore(failure);
      } else {
        try {
          grouped.restore();
        } catch (SQLException recovery) {
          failure.addSuppressed(recovery);
          System.err.println(
              "Cannot verify output restoration; retain any nb_ backup tables for recovery.");
        }
      }
      throw failure;
    }
    // Backups are no longer needed once the whole output set has been published successfully.
    for (Table table : tables) {
      if (table.backupAttempted) {
        try {
          drop(table.backup);
          table.backupAttempted = false;
        } catch (SQLException cleanupFailure) {
          cleanupFailure.printStackTrace();
        }
      }
    }
  }

  private void restore(SQLException failure) {
    List<Table> reverse = new ArrayList<>(tables);
    Collections.reverse(reverse);
    for (Table table : reverse) {
      try {
        if (table.installAttempted) {
          drop(table.destination);
          table.installAttempted = false;
        }
        if (table.backupAttempted) {
          if (tableExists(table.backup)) {
            rename(table.backup, table.destination);
          }
          table.backupAttempted = false;
        }
      } catch (SQLException restoreFailure) {
        failure.addSuppressed(restoreFailure);
        System.err.println(
            "Cannot restore " + table.destination + "; retained backup: " + table.backup);
        restoreFailure.printStackTrace();
      }
    }
  }

  /** Checks this output attempt's connection without hiding metadata failures. */
  private boolean tableExists(String name) throws SQLException {
    return JDBCUtils.tableExists(connection, name);
  }

  private void rename(String from, String to) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE "
              + JDBCUtils.getQuotedCompliantIdentifier(from)
              + " RENAME TO "
              + JDBCUtils.getQuotedCompliantIdentifier(to));
    }
  }

  private void drop(String name) throws SQLException {
    if (tableExists(name)) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("DROP TABLE " + JDBCUtils.getQuotedCompliantIdentifier(name));
      }
    }
  }

  /** Deletes unpublished staging tables without touching the previous scenario output. */
  @Override
  public void close() {
    for (Table table : tables) {
      if (!published) {
        try {
          drop(table.staged);
        } catch (SQLException failure) {
          failure.printStackTrace();
        }
      }
    }
  }
}
