/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.compute.assign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.modalsplit.LogitCalibration;
import edu.uclouvain.core.nodus.compute.modalsplit.LogitCalibrationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Routes, estimates, stores and reuses table-backed coefficients for each embedded method. */
@ResourceLock("JDBCUtils")
@Timeout(45)
class ModalParameterTableIntegrationTest {
  @TempDir Path directory;

  @Test
  void emptyFitWritesReportWithoutChangingCostsOrCreatingParameters() throws Exception {
    try (AssignmentTestProject project = project()) {
      Path file = directory.resolve("model.costs");
      Files.writeString(file,
          "ld.1,1=0\nul.1,1=0\ntr.1,1=0\nmv.1,1=BASECOST\n"
              + "ld.2,1=0\nul.2,1=0\ntr.2,1=0\nmv.2,1=BASECOST\n");
      AssignmentParameters parameters = project.parameters(2);
      parameters.setCostFunctions("model.costs");
      parameters.setModalSplitMethodName("MNL");
      byte[] original = Files.readAllBytes(file);
      for (int row : new int[] {1, 3, 5}) {
        project.links.getModel().setValueAt(0.0, row, NodusC.DBF_IDX_ENABLED);
      }
      project.panel.prepareRun(2);
      try (LogitCalibration estimation = new LogitCalibration(parameters,
          new LogitCalibrationSettings(1,
              Map.of(1, "observed_road", 2, "observed_rail")))) {
        assertFalse(estimation.estimateToTable(false, "empty_params", true));
      }
      assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(file)));
      assertTrue(Files.readString(directory.resolve("model_params.txt"))
          .contains("No coefficients were saved"));
      assertFalse(Files.exists(directory.resolve("empty_params.params")));
      assertEquals(0, project.number("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
          + "WHERE TABLE_NAME='EMPTY_PARAMS'"));
    }
  }

  @Test
  void behavioralOnlyEstimateStoresNoPivotRows() throws Exception {
    try (AssignmentTestProject project = project()) {
      Files.writeString(directory.resolve("model-test.costs"),
          "ld.1,1=0\nul.1,1=0\ntr.1,1=0\nmv.1,1=BASECOST\n"
              + "ld.2,1=0\nul.2,1=0\ntr.2,1=0\nmv.2,1=BASECOST\n");
      AssignmentParameters parameters = project.parameters(2);
      parameters.setCostFunctions("model-test.costs");
      parameters.setModalSplitMethodName("MNL");
      project.panel.prepareRun(2);
      try (LogitCalibration estimation = new LogitCalibration(parameters,
          new LogitCalibrationSettings(1,
              Map.of(1, "observed_road", 2, "observed_rail")))) {
        assertTrue(estimation.estimateToTable(false, "model-test_params", false));
      }
      assertEquals(0, project.number(
          "SELECT COUNT(*) FROM \"model-test_params\" WHERE param_type='calibration'"));
      assertTrue(Files.readString(directory.resolve("model-test.costs"))
          .contains("@paramTable=model-test_params"));
      assertTrue(Files.readString(directory.resolve("model-test_params.txt"))
          .contains("# Model: MNL"));
      project.run(new FastMFAssignment(parameters), 2);
      assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
    }
  }

  @Test
  void parallelGroupFittingMatchesSingleWorkerIncludingPivots() throws Exception {
    try (AssignmentTestProject project = project()) {
      String costs = "ld.1,1=0\nul.1,1=0\ntr.1,1=0\nmv.1,1=BASECOST\n"
          + "ld.2,1=0\nul.2,1=0\ntr.2,1=0\nmv.2,1=BASECOST\n";
      Files.writeString(directory.resolve("single.costs"), costs);
      Files.writeString(directory.resolve("parallel.costs"), costs);
      AssignmentParameters parameters = project.parameters(1);
      parameters.setModalSplitMethodName("MNL");
      LogitCalibrationSettings settings = new LogitCalibrationSettings(1,
          Map.of(1, "observed_road", 2, "observed_rail"));
      parameters.setCostFunctions("single.costs");
      project.panel.prepareRun(1);
      try (LogitCalibration estimation = new LogitCalibration(parameters, settings)) {
        assertTrue(estimation.estimateToTable(false, "single_params", true));
      }
      parameters.setThreads(2);
      parameters.setCostFunctions("parallel.costs");
      project.panel.prepareRun(2);
      try (LogitCalibration estimation = new LogitCalibration(parameters, settings)) {
        assertTrue(estimation.estimateToTable(false, "parallel_params", true));
      }
      Map<String, String> single = tableValues(project, "single_params");
      assertTrue(single.keySet().stream().anyMatch(key -> key.startsWith("pivot.")));
      assertEquals(single, tableValues(project, "parallel_params"));
      String report = Files.readString(directory.resolve("parallel_params.txt"));
      assertTrue(report.indexOf("# Group 0:") < report.indexOf("# Group 1:"));
    }
  }

  @Test
  void allEmbeddedMethodsUseStoredBehaviorAndPivotsDuringAssignment() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      for (String method : new String[] {"MNL", "MNP", "Proportional"}) {
        try (AssignmentTestProject project = project(hsql)) {
          Path costFile = directory.resolve("model.costs");
          Files.writeString(costFile,
              "# Transport cost expressions\n"
                  + "ld.1,1=0\nul.1,1=0\ntr.1,1=0\nmv.1,1=BASECOST\n"
                  + "ld.2,1=0\nul.2,1=0\ntr.2,1=0\nmv.2,1=BASECOST\n");
          AssignmentParameters parameters = project.parameters(2);
          parameters.setCostFunctions("model.costs");
          parameters.setModalSplitMethodName(method);
          String table = "parameters_" + method.toLowerCase();
          project.panel.prepareRun(2);
          try (LogitCalibration estimation = new LogitCalibration(parameters,
              new LogitCalibrationSettings(1,
                  Map.of(1, "observed_road", 2, "observed_rail")))) {
            assertTrue(estimation.estimateToTable(false, table, true, 4));
          }
          String costs = Files.readString(costFile);
          assertTrue(costs.contains("@paramTable=" + table));
          assertTrue(costs.contains("# Transport cost expressions"));
          assertTrue(Files.readString(directory.resolve("model_params.txt"))
              .contains("Estimated bounded OD/group pivot constants"));
          assertTrue(project.number("SELECT COUNT(*) FROM " + table
              + " WHERE param_type='calibration'") > 0);
          assertEquals(4, project.number("SELECT CAST(param_value AS DOUBLE) FROM " + table
              + " WHERE param_key='@nodus.pivotMaxAbs'"));
          assertEquals(0, project.number("SELECT COUNT(*) FROM " + table
              + " WHERE param_key='@nodus.pivotLevel' OR param_key LIKE 'nodus.pivot.%'"));
          project.run(new FastMFAssignment(parameters), 2);
          assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
          double originalRail = project.number(
              "SELECT SUM(qty) FROM mini_paths1_header WHERE grp=0 AND org=1 AND ldmode=2");
          project.execute("UPDATE " + table + " SET param_value='-4' "
              + "WHERE param_key='pivot.2.1.2.0'");
          project.run(new FastMFAssignment(parameters), 2);
          double reducedRail = project.number(
              "SELECT SUM(qty) FROM mini_paths1_header WHERE grp=0 AND org=1 AND ldmode=2");
          assertTrue(reducedRail < originalRail, method + " must apply the stored OD pivot");
          if ("MNL".equals(method)) {
            project.run(new ExactMFAssignment(parameters), 2);
            assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
            assertTrue(project.number(
                "SELECT SUM(qty) FROM mini_paths1_header WHERE grp=0 AND org=1 AND ldmode=2")
                < originalRail);
          }
        }
      }
    }
  }

  private AssignmentTestProject project() throws Exception {
    return project(false);
  }

  private Map<String, String> tableValues(AssignmentTestProject project, String table)
      throws Exception {
    Map<String, String> values = new TreeMap<>();
    try (Statement statement = project.getMainJDBCConnection().createStatement();
        ResultSet rows = statement.executeQuery("SELECT param_key,param_value FROM " + table)) {
      while (rows.next()) {
        values.put(rows.getString(1), rows.getString(2));
      }
    }
    return values;
  }

  private AssignmentTestProject project(boolean hsql) throws Exception {
    AssignmentTestProject project = new AssignmentTestProject(directory, 6,
        new double[][] {
            {11, 1, 2, 1, 0}, {12, 1, 2, 1, 0},
            {13, 3, 4, 1, 0}, {14, 3, 4, 2, 0},
            {15, 5, 6, 1, 0}, {16, 5, 6, 4, 0}
        }, hsql);
    for (int row : new int[] {1, 3, 5}) {
      project.links.getModel().setValueAt(2.0, row, NodusC.DBF_IDX_MODE);
    }
    project.execute("CREATE TABLE observed_road (grp INT,org INT,dst INT,qty DOUBLE)");
    project.execute("CREATE TABLE observed_rail (grp INT,org INT,dst INT,qty DOUBLE)");
    for (int group = 0; group < 2; group++) {
      for (int row = 0; row < 3; row++) {
        int origin = 2 * row + 1;
        int destination = origin + 1;
        double road = 20 * Math.pow(4, row);
        int rail = 30 * (group + 1);
        project.execute("INSERT INTO observed_road VALUES (" + group + "," + origin + ","
            + destination + "," + road + ")");
        project.execute("INSERT INTO observed_rail VALUES (" + group + "," + origin + ","
            + destination + "," + rail + ")");
        project.execute("INSERT INTO mini_od VALUES (" + group + "," + origin + ","
            + destination + "," + 100 * (group + 1) + ")");
      }
    }
    return project;
  }
}
