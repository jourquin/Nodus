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

package edu.uclouvain.core.nodus.project;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Maintains scenario outputs, node rules and associated project properties. */
public final class ProjectScenarios {
  private static final String[] PROPERTY_PREFIXES = {
    NodusC.PROP_COST_FUNCTIONS,
    NodusC.PROP_OD_TABLE,
    NodusC.PROP_ASSIGNMENT_TAB,
    NodusC.PROP_ASSIGNMENT_METHOD,
    NodusC.PROP_ASSIGNMENT_NB_ITERATIONS,
    NodusC.PROP_ASSIGNMENT_PRECISION,
    NodusC.PROP_COST_MARKUP,
    NodusC.PROP_MAX_DETOUR,
    NodusC.PROP_THREADS,
    NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD,
    NodusC.PROP_ASSIGNMENT_SAVE_PATHS,
    NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS,
    NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY,
    NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS,
    NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT,
    NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT,
    NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA,
    NodusC.PROP_ASSIGNMENT_QUERY,
    NodusC.PROP_ASSIGNMENT_DESCRIPTION,
    NodusC.PROP_NODES_QUANTITIES_QUERY,
    NodusC.PROP_LINKS_QUANTITIES_QUERY,
    NodusC.PROP_LINKS_VEHICLES_QUERY,
    NodusC.PROP_DYNAMIC_QUANTITIES_QUERY,
    NodusC.PROP_DYNAMIC_VEHICLES_QUERY,
    NodusC.PROP_PATH_QUERY
  };

  private final NodusProject project;
  private final NodusMapPanel panel;

  /**
   * Creates a scenario manager for a project.
   *
   * @param project project containing scenario tables and properties
   * @param panel map panel used for scenario operations
   */
  public ProjectScenarios(NodusProject project, NodusMapPanel panel) {
    this.project = project;
    this.panel = panel;
  }

  /**
   * Removes scenario outputs, node rules and saved settings, preserving generic node rules.
   *
   * <p>DDL may commit independently. If a later drop fails, rules, settings and remaining tables
   * are kept so deletion can be retried; previously dropped tables cannot be recovered by rollback.
   *
   * @param scenario ID of the scenario to delete from database
   * @throws IllegalStateException if table discovery or a deletion fails
   */
  public void removeScenario(int scenario) {
    requireScenarioId(scenario);
    try {
      final Connection connection = connection();
      final Set<String> existing = existingTables();
      final String rules = nodeRulesTable(connection, existing);
      try (Statement statement = connection.createStatement()) {
        for (String table : tableNames(scenario)) {
          if (existing.contains(table)) {
            statement.execute("DROP TABLE " + JDBCUtils.getQuotedCompliantIdentifier(table));
          }
        }
      }
      changeNodeRules(connection, rules, scenario, null);
    } catch (SQLException failure) {
      throw new IllegalStateException(
          "Could not finish deleting scenario "
              + scenario
              + ". Settings and remaining tables were kept; deletion can be retried. "
              + failure.getMessage(),
          failure);
    }
    for (String prefix : PROPERTY_PREFIXES) {
      project.removeLocalProperty(prefix + scenario);
    }
    panel.updateScenarioComboBox(false);
  }

  /**
   * Renames scenario outputs, node rules and settings to an unused scenario ID.
   *
   * <p>All destination tables, rules and settings are checked before changing anything. If a table
   * rename or rule update fails, completed renames are reversed before reporting the failure;
   * transaction rollback alone cannot restore schema changes on every supported database.
   *
   * @param oldNum ID of the scenario to rename
   * @param newNum new ID of the scenario
   * @throws IllegalStateException if the destination is occupied or a database operation fails
   */
  public void renameScenario(int oldNum, int newNum) {
    requireScenarioId(oldNum);
    requireScenarioId(newNum);
    if (oldNum == newNum) {
      return;
    }
    final String[] source = tableNames(oldNum);
    final String[] target = tableNames(newNum);
    try {
      final Connection connection = connection();
      final Set<String> existing = existingTables();
      final String rules = nodeRulesTable(connection, existing);
      for (String table : target) {
        if (existing.contains(table)) {
          throw new IllegalStateException(
              "Scenario "
                  + newNum
                  + " already has output table "
                  + table
                  + ". Choose an unused scenario ID.");
        }
      }
      for (String prefix : PROPERTY_PREFIXES) {
        if (project.getLocalProperty(prefix + newNum) != null) {
          throw new IllegalStateException(
              "Scenario " + newNum + " already has saved settings. Choose an unused scenario ID.");
        }
      }
      if (hasNodeRules(connection, rules, newNum)) {
        throw new IllegalStateException(
            "Scenario " + newNum + " already has node rules. Choose an unused scenario ID.");
      }
      renameTables(connection, existing, source, target, rules, oldNum, newNum);
    } catch (SQLException failure) {
      throw new IllegalStateException(
          "Could not rename scenario "
              + oldNum
              + " to "
              + newNum
              + ". Saved settings were not changed. "
              + failure.getMessage(),
          failure);
    }
    for (String prefix : PROPERTY_PREFIXES) {
      project.renameLocalProperty(prefix + oldNum, prefix + newNum);
    }
    project.setLocalProperty(NodusC.PROP_SCENARIO, newNum);
    panel.updateScenarioComboBox(false);
  }

  /** Uses exact metadata names, not JDBC table-name patterns where underscores are wildcards. */
  private Set<String> existingTables() throws SQLException {
    final Set<String> names = new HashSet<>();
    try (ResultSet result = JDBCUtils.getTables()) {
      while (result.next()) {
        names.add(result.getString("TABLE_NAME"));
      }
    }
    return names;
  }

  /** Borrows the same connection used by JDBCUtils metadata and identifier formatting. */
  private Connection connection() throws SQLException {
    final Connection connection = JDBCUtils.getConnection();
    if (connection == null || connection.isClosed()) {
      throw new SQLException("No active project database connection");
    }
    return connection;
  }

  private String[] tableNames(int scenario) {
    final String name = project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    final String network =
        project.getLocalProperty(NodusC.PROP_VNET_TABLE, name + NodusC.SUFFIX_VNET);
    final String paths = project.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, name);
    return new String[] {
      JDBCUtils.getCompliantIdentifier(network + scenario),
      JDBCUtils.getCompliantIdentifier(paths + scenario + NodusC.SUFFIX_HEADER),
      JDBCUtils.getCompliantIdentifier(paths + scenario + NodusC.SUFFIX_DETAIL)
    };
  }

  private void renameTables(
      Connection connection,
      Set<String> existing,
      String[] source,
      String[] target,
      String rules,
      int oldScenario,
      int newScenario)
      throws SQLException {
    final List<Integer> renamed = new ArrayList<>();
    try {
      try (Statement statement = connection.createStatement()) {
        for (int i = 0; i < source.length; i++) {
          if (existing.contains(source[i])) {
            statement.execute(renameSql(source[i], target[i]));
            renamed.add(i);
          }
        }
      }
      changeNodeRules(connection, rules, oldScenario, newScenario);
    } catch (SQLException failure) {
      final StringBuilder unresolved = new StringBuilder();
      for (int i = renamed.size() - 1; i >= 0; i--) {
        final int index = renamed.get(i);
        try (Statement statement = connection.createStatement()) {
          statement.execute(renameSql(target[index], source[index]));
        } catch (SQLException recoveryFailure) {
          failure.addSuppressed(recoveryFailure);
          unresolved.append(" ").append(target[index]).append(" -> ").append(source[index]);
        }
      }
      if (unresolved.length() > 0) {
        throw new IllegalStateException(
            "Scenario renaming failed and some tables could not be "
                + "restored. Saved settings were kept. Restore these names before retrying:"
                + unresolved,
            failure);
      }
      throw failure;
    }
  }

  private String renameSql(String source, String target) {
    return "ALTER TABLE "
        + JDBCUtils.getQuotedCompliantIdentifier(source)
        + " RENAME TO "
        + JDBCUtils.getQuotedCompliantIdentifier(target);
  }

  /** The negative scenario ID in the rules table denotes generic rules, not a scenario to edit. */
  private void requireScenarioId(int scenario) {
    if (scenario < 0) {
      throw new IllegalStateException(
          "Scenario IDs must be nonnegative; generic node rules are shared.");
    }
  }

  /** Returns the configured rules table, leaving old tables without a scenario column untouched. */
  private String nodeRulesTable(Connection connection, Set<String> existing) throws SQLException {
    final String table =
        JDBCUtils.getCompliantIdentifier(
            project.getLocalProperty(
                NodusC.PROP_EXC_TABLE,
                project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC));
    if (!existing.contains(table)) {
      return null;
    }
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT * FROM " + JDBCUtils.getQuotedCompliantIdentifier(table) + " WHERE 1=0")) {
      for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
        if (NodusC.DBF_SCENARIO.equalsIgnoreCase(result.getMetaData().getColumnName(column))) {
          return table;
        }
      }
    }
    return null;
  }

  private boolean hasNodeRules(Connection connection, String table, int scenario)
      throws SQLException {
    if (table == null) {
      return false;
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT 1 FROM "
                + JDBCUtils.getQuotedCompliantIdentifier(table)
                + " WHERE "
                + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
                + "=?")) {
      statement.setInt(1, scenario);
      try (ResultSet result = statement.executeQuery()) {
        return result.next();
      }
    }
  }

  /**
   * Changes only this scenario's rules after all output DDL, then persists the completed change.
   */
  private void changeNodeRules(
      Connection connection, String table, int scenario, Integer newScenario) throws SQLException {
    if (table == null || !hasNodeRules(connection, table, scenario)) {
      return;
    }
    final boolean autoCommit = connection.getAutoCommit();
    final Savepoint savepoint;
    if (autoCommit) {
      connection.setAutoCommit(false);
      savepoint = null;
    } else {
      // DDL above can commit and invalidate earlier savepoints on H2/HSQLDB.
      savepoint = connection.setSavepoint();
    }
    try {
      final String target = JDBCUtils.getQuotedCompliantIdentifier(table);
      final String column = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO);
      final String sql =
          newScenario == null
              ? "DELETE FROM " + target + " WHERE " + column + "=?"
              : "UPDATE " + target + " SET " + column + "=? WHERE " + column + "=?";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        int parameter = 1;
        if (newScenario != null) {
          statement.setInt(parameter++, newScenario);
        }
        statement.setInt(parameter, scenario);
        statement.executeUpdate();
      }
      connection.commit();
    } catch (SQLException failure) {
      try {
        if (autoCommit) {
          connection.rollback();
        } else {
          connection.rollback(savepoint);
        }
      } catch (SQLException recoveryFailure) {
        failure.addSuppressed(recoveryFailure);
        throw new SQLException(
            "Could not restore node rules in "
                + table
                + ". Verify scenario "
                + scenario
                + " before retrying.",
            failure);
      }
      throw failure;
    } finally {
      if (autoCommit) {
        connection.setAutoCommit(true);
      }
    }
  }
}
