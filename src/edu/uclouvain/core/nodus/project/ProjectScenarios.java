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

/** Maintains scenario tables and their associated project properties. */
public final class ProjectScenarios {
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
   * Removes all the output tables (vnet and path_xxx) for a given scenario.
   *
   * @param scenario ID of the scenario to delete from database.
   */
  public void removeScenario(int scenario) {
    String tableName;

    // Virtual network
    tableName = project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName = project.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + scenario;
    if (JDBCUtils.tableExists(tableName)) {
      JDBCUtils.dropTable(tableName);
    }

    // Paths
    tableName = project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    tableName = project.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, tableName);

    if (JDBCUtils.tableExists(tableName + scenario + NodusC.SUFFIX_HEADER)) {
      JDBCUtils.dropTable(tableName + scenario + NodusC.SUFFIX_HEADER);
    }

    if (JDBCUtils.tableExists(tableName + scenario + NodusC.SUFFIX_DETAIL)) {
      JDBCUtils.dropTable(tableName + scenario + NodusC.SUFFIX_DETAIL);
    }

    project.removeLocalProperty(NodusC.PROP_COST_FUNCTIONS + scenario);
    project.removeLocalProperty(NodusC.PROP_OD_TABLE + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_TAB + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_METHOD + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_NB_ITERATIONS + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_PRECISION + scenario);
    project.removeLocalProperty(NodusC.PROP_COST_MARKUP + scenario);
    project.removeLocalProperty(NodusC.PROP_MAX_DETOUR + scenario);
    project.removeLocalProperty(NodusC.PROP_THREADS + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_PATHS + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS + scenario);
    project.removeLocalProperty(NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_QUERY + scenario);
    project.removeLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + scenario);

    panel.updateScenarioComboBox(false);
  }

  /**
   * Renames all the output tables (vnet and path_xxx) for a given scenario.
   *
   * @param oldNum ID of the scenario to rename.
   * @param newNum New ID of the scenario.
   */
  public void renameScenario(int oldNum, int newNum) {
    String tableName;

    // Virtual network
    tableName = project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName = project.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName);
    if (JDBCUtils.tableExists(tableName + oldNum)) {
      if (!JDBCUtils.tableExists(tableName + newNum)) {
        JDBCUtils.renameTable(tableName + oldNum, tableName + newNum);
      }
    }

    // Paths
    tableName = project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    tableName = project.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, tableName);

    if (JDBCUtils.tableExists(tableName + oldNum + NodusC.SUFFIX_HEADER)) {
      if (!JDBCUtils.tableExists(tableName + newNum + NodusC.SUFFIX_HEADER)) {
        JDBCUtils.renameTable(
            tableName + oldNum + NodusC.SUFFIX_HEADER, tableName + newNum + NodusC.SUFFIX_HEADER);
      }
    }
    if (JDBCUtils.tableExists(tableName + oldNum + NodusC.SUFFIX_DETAIL)) {
      if (!JDBCUtils.tableExists(tableName + newNum + NodusC.SUFFIX_DETAIL)) {
        JDBCUtils.renameTable(
            tableName + oldNum + NodusC.SUFFIX_DETAIL, tableName + newNum + NodusC.SUFFIX_DETAIL);
      }
    }
    project.renameLocalProperty(
        NodusC.PROP_COST_FUNCTIONS + oldNum, NodusC.PROP_COST_FUNCTIONS + newNum);
    project.renameLocalProperty(NodusC.PROP_OD_TABLE + oldNum, NodusC.PROP_OD_TABLE + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_TAB + oldNum, NodusC.PROP_ASSIGNMENT_TAB + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_METHOD + oldNum, NodusC.PROP_ASSIGNMENT_METHOD + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_NB_ITERATIONS + oldNum,
        NodusC.PROP_ASSIGNMENT_NB_ITERATIONS + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_PRECISION + oldNum, NodusC.PROP_ASSIGNMENT_PRECISION + newNum);
    project.renameLocalProperty(NodusC.PROP_COST_MARKUP + oldNum, NodusC.PROP_COST_MARKUP + newNum);
    project.renameLocalProperty(NodusC.PROP_MAX_DETOUR + oldNum, NodusC.PROP_MAX_DETOUR + newNum);
    project.renameLocalProperty(NodusC.PROP_THREADS + oldNum, NodusC.PROP_THREADS + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD + oldNum,
        NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_SAVE_PATHS + oldNum, NodusC.PROP_ASSIGNMENT_SAVE_PATHS + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS + oldNum,
        NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS + newNum);
    project.renameLocalProperty(
        NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY + oldNum,
        NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS + oldNum,
        NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT + oldNum,
        NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT + oldNum,
        NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA + oldNum,
        NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_QUERY + oldNum, NodusC.PROP_ASSIGNMENT_QUERY + newNum);
    project.renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_DESCRIPTION + oldNum, NodusC.PROP_ASSIGNMENT_DESCRIPTION + newNum);

    // Change current scenario to new one
    project.setLocalProperty(NodusC.PROP_SCENARIO, newNum);

    project.getNodusMapPanel().updateScenarioComboBox(false);
  }
}
