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

package edu.uclouvain.core.nodus.compute.assign;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.modalsplit.LogitCalibration;
import edu.uclouvain.core.nodus.compute.modalsplit.LogitCalibrationSettings;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** End-to-end calibration with actual routing, modal splitting, cost files and result tables. */
@ResourceLock("JDBCUtils")
@ResourceLock(Resources.SYSTEM_OUT)
@Timeout(30)
class LogitCalibrationIntegrationTest {
  @TempDir Path directory;
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private PrintStream originalOutput;
  private PrintStream capturedOutput;

  @BeforeEach
  void captureDiagnostics() {
    originalOutput = System.out;
    capturedOutput = new PrintStream(output, true, StandardCharsets.UTF_8);
    System.setOut(capturedOutput);
  }

  @AfterEach
  void restoreOutput() {
    System.setOut(originalOutput);
    capturedOutput.close();
  }

  @Test
  void fastAndExactFitBothGroupsAndAssignObservedDemandWithoutScripts() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean exact : new boolean[] {false, true}) {
        try (AssignmentTestProject project = project(hsql)) {
          AssignmentParameters parameters = parameters(project);
          estimate(project, parameters, exact);
          selectObservedDemand(project, parameters);
          project.run(
              exact ? new ExactMFAssignment(parameters) : new FastMFAssignment(parameters), 2);
          assertCoefficients(parameters.getCostFunctions());
          assertEquals(1110, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
          assertEquals(12, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
          assertEquals(
              30,
              project.number(
                  "SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=1 AND ldmode=2"),
              1e-3);
          assertEquals(
              320,
              project.number(
                  "SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=5 AND ldmode=1"),
              1e-3);
          assertEquals(
              "observed_total",
              parameters.getODMatrix(),
              "Assignment uses its explicit OD selection");
          assertNoTemporaryTables(project);
          String file = Files.readString(directory.resolve("model.costs"));
          assertTrue(file.contains("# Keep this user comment"));
          assertTrue(file.contains("# Group 0:"));
          assertTrue(file.contains("# Group 1:"));
          assertTrue(file.contains("mv.2,1 = BASECOST"));
        }
      }
    }
  }

  @Test
  void missingParametersWarnOncePerAssignmentAndUseDefaultsAcrossGroupsAndWorkers()
      throws Exception {
    for (String method : new String[] {"MNL", "MNP"}) {
      for (boolean exact : new boolean[] {false, true}) {
        try (AssignmentTestProject project = project()) {
          AssignmentParameters parameters = parameters(project);
          parameters.setModalSplitMethodName(method);
          byte[] originalFile = Files.readAllBytes(parameters.getCostFunctionsPath());
          Properties originalCosts = (Properties) parameters.getCostFunctions().clone();
          for (int repeat = 0; repeat < 2; repeat++) {
            project.run(
                exact ? new ExactMFAssignment(parameters) : new FastMFAssignment(parameters), 2, 1);
            assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
            assertEquals(
                50,
                project.number(
                    "SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=1 AND ldmode=1"),
                1e-3);
            double roadShare = "MNL".equals(method) ? 0.7310585786300049 : 0.7602499389065233;
            for (int group = 0; group < 2; group++) {
              assertEquals(
                  100 * (group + 1) * roadShare,
                  project.number(
                      "SELECT qty FROM mini_paths1_header WHERE grp="
                          + group
                          + " AND org=3 AND ldmode=1"),
                  1e-3);
            }
          }
          assertArrayEquals(originalFile, Files.readAllBytes(parameters.getCostFunctionsPath()));
          assertEquals(originalCosts, parameters.getCostFunctions());
        }
      }
    }
  }

  @Test
  void everyModelCanSaveAsWithoutUsingOrModifyingTheOldOutputCosts() throws Exception {
    for (String method : new String[] {"MNL", "MNP", "Proportional"}) {
      for (boolean exists : new boolean[] {false, true}) {
        try (AssignmentTestProject project = project()) {
          AssignmentParameters parameters =
              "MNP".equals(method)
                  ? probitParameters(project)
                  : "Proportional".equals(method)
                      ? proportionalParameters(project)
                      : parameters(project);
          Path source = parameters.getCostFunctionsPath();
          final byte[] original = Files.readAllBytes(source);
          Path target = directory.resolve(method + "-" + exists + ".costs");
          if (exists) {
            Files.writeString(
                target, "# Old output must not supply routing costs\nmv.1,1 = INVALID\n");
          }
          project.panel.prepareRun(2);
          try (LogitCalibration calibration =
              new LogitCalibration(parameters, settings(parameters))) {
            assertTrue(calibration.estimate(false, target));
          }
          assertArrayEquals(original, Files.readAllBytes(source));
          String saved = Files.readString(target);
          assertTrue(saved.contains("# Keep this user comment"));
          assertTrue(saved.contains("# BEGIN NODUS ESTIMATED " + method));
          assertTrue(saved.contains("mv.2,1 = BASECOST"));
          assertFalse(saved.contains("INVALID"));
          assertEquals(source, parameters.getCostFunctionsPath());
          assertNoTemporaryTables(project);
          // The saved file is available to a later, separately configured assignment.
          parameters.setCostFunctions(target.getFileName().toString());
          project.run(new FastMFAssignment(parameters), 2);
          assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
        }
      }
    }
  }

  @Test
  void saveAsCancellationAndFitFailureLeaveSourceAndOutputUntouched() throws Exception {
    for (boolean exists : new boolean[] {false, true}) {
      for (boolean cancel : new boolean[] {false, true}) {
        try (AssignmentTestProject project = project()) {
          AssignmentParameters parameters = parameters(project);
          final byte[] original = Files.readAllBytes(parameters.getCostFunctionsPath());
          Path target = directory.resolve("output-" + exists + "-" + cancel + ".costs");
          if (exists) {
            Files.writeString(target, "# Keep the previous output\n");
          }
          project.panel.prepareRun(2);
          if (cancel) {
            project.panel.cancelAt = "Saving MNL coefficients";
          } else {
            project.execute("UPDATE observed_rail SET qty=0 WHERE grp=1");
          }
          try (LogitCalibration calibration =
              new LogitCalibration(parameters, settings(parameters))) {
            if (cancel) {
              assertThrows(
                  java.util.concurrent.CancellationException.class,
                  () -> calibration.estimate(false, target));
            } else {
              assertThrows(
                  IllegalArgumentException.class, () -> calibration.estimate(false, target));
            }
          }
          assertArrayEquals(original, Files.readAllBytes(parameters.getCostFunctionsPath()));
          if (exists) {
            assertEquals("# Keep the previous output\n", Files.readString(target));
          } else {
            assertFalse(Files.exists(target));
          }
          assertNoTemporaryTables(project);
        }
      }
    }
  }

  @Test
  void proportionalFitsFactorsAndReusesThemForBothRoutingMethodsAndDatabases() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean exact : new boolean[] {false, true}) {
        try (AssignmentTestProject project = project(hsql)) {
          final AssignmentParameters parameters = proportionalParameters(project);
          estimate(project, parameters, exact);
          assertEquals(
              0.5,
              Double.parseDouble(
                  parameters.getCostFunctions().getProperty("proportional.costFactor.2.0")),
              1e-8);
          assertEquals(
              2,
              Double.parseDouble(
                  parameters.getCostFunctions().getProperty("proportional.costFactor.2.1")),
              1e-8);
          assertEquals(
              "1.0", parameters.getCostFunctions().getProperty("proportional.costFactor.1.0"));
          assertEquals("1", parameters.getCostFunctions().getProperty("proportional.reference.0"));
          assertEquals(
              "4", parameters.getCostFunctions().getProperty("proportional.costFactor.2.9"));
          assertEquals("-7", parameters.getCostFunctions().getProperty("log(cost).1.0"));
          assertEquals("-8", parameters.getCostFunctions().getProperty("probit.log(cost).1.0"));
          String report = Files.readString(directory.resolve("model.costs"));
          assertTrue(report.contains("# Cost factors (modes [1, 2]):"));
          assertTrue(report.contains("# Cost-factor SEs (delta method):"));
          assertTrue(report.contains("unadjusted proportional log-likelihood="));
          final byte[] saved = Files.readAllBytes(directory.resolve("model.costs"));
          // Reload, as a later normal assignment would, on separate forecast demand.
          parameters.setCostFunctions("model.costs");
          project.run(
              exact ? new ExactMFAssignment(parameters) : new FastMFAssignment(parameters), 2);
          assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
          assertEquals(
              50,
              project.number(
                  "SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=3 AND ldmode=2"),
              1e-3);
          assertEquals(
              40,
              project.number(
                  "SELECT qty FROM mini_paths1_header WHERE grp=1 AND org=3 AND ldmode=2"),
              1e-3);
          assertArrayEquals(saved, Files.readAllBytes(directory.resolve("model.costs")));
          assertEquals("mini_od", parameters.getODMatrix());
          assertNoTemporaryTables(project);
        }
      }
    }
  }

  @Test
  void proportionalSkipsUnroutableObservationsAndHonorsQuietLogging() throws Exception {
    try (AssignmentTestProject project = project()) {
      final AssignmentParameters parameters = proportionalParameters(project);
      project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
      project.panel.prepareRun(2);
      output.reset();
      try (LogitCalibration calibration =
          new LogitCalibration(parameters, settings(parameters), false)) {
        assertTrue(calibration.estimate(false));
      }
      assertEquals("", output.toString(StandardCharsets.UTF_8));
      assertTrue(
          Files.readString(directory.resolve("model.costs")).contains("skipped 2 of 6 OD records"));
      assertEquals(
          0.5,
          Double.parseDouble(
              parameters.getCostFunctions().getProperty("proportional.costFactor.2.0")),
          1e-8);
      assertEquals(
          2,
          Double.parseDouble(
              parameters.getCostFunctions().getProperty("proportional.costFactor.2.1")),
          1e-8);
      assertNoTemporaryTables(project);
    }
  }

  @Test
  void proportionalCancellationAndLaterGroupFailureDoNotSaveAnyEstimates() throws Exception {
    for (boolean cancelled : new boolean[] {false, true}) {
      try (AssignmentTestProject project = project()) {
        final AssignmentParameters parameters = proportionalParameters(project);
        project.panel.prepareRun(2);
        if (cancelled) {
          project.panel.cancelAt = "Estimating Proportional for group";
          project.panel.cancelAfterChecks = 2;
        } else {
          project.execute("UPDATE observed_rail SET qty=0 WHERE grp=1");
        }
        project.execute("CREATE TABLE mini_paths1_header (sentinel INT)");
        project.execute("INSERT INTO mini_paths1_header VALUES (83)");
        final byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
        try (LogitCalibration calibration =
            new LogitCalibration(parameters, settings(parameters))) {
          if (cancelled) {
            assertThrows(
                java.util.concurrent.CancellationException.class,
                () -> calibration.estimate(false));
          } else {
            Exception failure =
                assertThrows(IllegalArgumentException.class, () -> calibration.estimate(false));
            assertTrue(failure.getMessage().contains("Proportional group 1"));
          }
        }
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
        assertEquals(83, project.number("SELECT sentinel FROM mini_paths1_header"));
        assertEquals("mini_od", parameters.getODMatrix());
        assertNoTemporaryTables(project);
      }
    }
  }

  private AssignmentParameters proportionalParameters(AssignmentTestProject project)
      throws Exception {
    AssignmentParameters parameters = parameters(project);
    parameters.setModalSplitMethodName("Proportional");
    for (int group = 0; group < 2; group++) {
      double factor = group == 0 ? 0.5 : 2;
      for (int row = 0; row < 3; row++) {
        double adjustedCost = factor * Math.pow(2, row);
        double rail = 100 / (1 + adjustedCost);
        String where = " WHERE grp=" + group + " AND org=" + (2 * row + 1);
        project.execute("UPDATE observed_road SET qty=" + (100 - rail) + where);
        project.execute("UPDATE observed_rail SET qty=" + rail + where);
      }
    }
    Files.writeString(
        directory.resolve("model.costs"),
        "log(cost).1.0 = -7\nprobit.log(cost).1.0 = -8\n"
            + "proportional.reference.9 = 1\nproportional.costFactor.1.9 = 1\n"
            + "proportional.costFactor.2.9 = 4\n",
        java.nio.file.StandardOpenOption.APPEND);
    parameters.setCostFunctions("model.costs");
    return parameters;
  }

  @Test
  void estimationWritesCoverageWithoutConsoleLogging() throws Exception {
    for (boolean probit : new boolean[] {false, true}) {
      Properties quietCoefficients = null;
      for (boolean logging : new boolean[] {false, true}) {
        try (AssignmentTestProject project = project()) {
          AssignmentParameters parameters =
              probit ? probitParameters(project) : parameters(project);
          parameters.getCostFunctions().setProperty(NodusC.VARNAME_MAX_DETOUR_REF_MODE, "1");
          // Exclude one OD pair in each group, keeping two cost ratios for identification.
          project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
          project.panel.prepareRun(2);
          output.reset();
          try (LogitCalibration calibration =
              new LogitCalibration(parameters, settings(parameters), logging)) {
            assertTrue(calibration.estimate(probit));
          }
          assertEquals("", output.toString(StandardCharsets.UTF_8));
          if (logging) {
            assertEquals(quietCoefficients, parameters.getCostFunctions());
          } else {
            quietCoefficients = (Properties) parameters.getCostFunctions().clone();
          }
          String report = Files.readString(directory.resolve("model.costs"));
          assertTrue(report.contains("skipping OD record:"));
          assertTrue(report.contains("calibration coverage [all groups/modes]"));
          assertTrue(report.contains("skipped 2 of 6 OD records"));
          assertTrue(report.contains("# Group 0:"));
          assertTrue(report.contains("# Group 1:"));
          assertNoTemporaryTables(project);
        }
      }
    }
  }

  @Test
  void quietEmptyCalibrationPreservesTheCostFileWithoutTerminalOutput() throws Exception {
    for (boolean probit : new boolean[] {false, true}) {
      try (AssignmentTestProject project = project()) {
        final AssignmentParameters parameters =
            probit ? probitParameters(project) : parameters(project);
        for (int row : new int[] {1, 3, 5}) {
          project.links.getModel().setValueAt(0.0, row, NodusC.DBF_IDX_ENABLED);
        }
        final byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
        project.panel.prepareRun(2);
        output.reset();
        try (LogitCalibration calibration =
            new LogitCalibration(parameters, settings(parameters), false)) {
          assertFalse(calibration.estimate(probit));
        }
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
        assertNoTemporaryTables(project);
      }
    }
  }

  @Test
  void calibrationIgnoresAssignmentScenarioAndAlternativeRouteControls() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean exact : new boolean[] {false, true}) {
        try (AssignmentTestProject project = project(hsql)) {
          AssignmentParameters parameters = parameters(project);
          // A stale scenario selection would change the cost ratio and fitted intercept.
          Files.writeString(
              directory.resolve("model.costs"),
              "1.mv.2,1 = 100 * BASECOST\n",
              java.nio.file.StandardOpenOption.APPEND);
          parameters.setCostFunctions("model.costs");
          parameters.setScenario(1);
          parameters.setNbIterations(9);
          parameters.setCostMarkup(0.75);
          estimate(project, parameters, exact);
          assertCoefficients(parameters.getCostFunctions());
          // Calibration must not change the controls later used for a normal assignment.
          assertEquals(1, parameters.getScenario());
          assertEquals(9, parameters.getNbIterations());
          assertEquals(0.75, parameters.getCostMarkup());
          String report = Files.readString(directory.resolve("model.costs"));
          assertTrue(report.contains("base costs (no numbered scenario overrides)"));
          assertTrue(report.contains("one search per mode/means, no cost markup"));
          assertEquals("100 * BASECOST", parameters.getCostFunctions().getProperty("1.mv.2,1"));
        }
      }
    }
  }

  @Test
  void unavailableModesWithZeroFlowAndDuplicateRowsAreHandled() throws Exception {
    try (AssignmentTestProject project = project()) {
      final AssignmentParameters parameters = parameters(project);
      project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
      project.execute("UPDATE observed_rail SET qty=0 WHERE org=1");
      project.execute("UPDATE observed_road SET qty=10 WHERE grp=0 AND org=1");
      project.execute("INSERT INTO observed_road VALUES (0,1,2,10), (0,1,2,NULL)");
      estimate(project, parameters, true);
      selectObservedDemand(project, parameters);
      project.run(new ExactMFAssignment(parameters), 2);
      assertCoefficients(parameters.getCostFunctions());
      assertEquals(1020, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
      assertEquals(10, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
      assertCoverage(
          Files.readString(directory.resolve("model.costs")),
          "all groups/modes",
          "skipped records=0/6 (0.000%)",
          "observed quantity=1020.0",
          "unroutable quantity=0.0 (0.000%)",
          "other excluded quantity=0.0");
      assertNoTemporaryTables(project);
    }
  }

  @Test
  void cancellationAfterRoutingDoesNotSaveCoefficientsOrResults() throws Exception {
    try (AssignmentTestProject project = project()) {
      final AssignmentParameters parameters = parameters(project);
      project.panel.prepareRun(2);
      project.panel.cancelAt = "Estimating MNL for group";
      project.execute("CREATE TABLE mini_paths1_header (sentinel INT)");
      project.execute("INSERT INTO mini_paths1_header VALUES (31)");
      byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
      try (LogitCalibration calibration = new LogitCalibration(parameters, settings(parameters))) {
        assertThrows(
            java.util.concurrent.CancellationException.class, () -> calibration.estimate(false));
      }
      assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
      assertEquals(31, project.number("SELECT sentinel FROM mini_paths1_header"));
      assertNoTemporaryTables(project);
    }
  }

  @Test
  void cancellationDuringEitherParallelFitIsNotReportedAsAFailedFit() throws Exception {
    for (boolean probit : new boolean[] {false, true}) {
      try (AssignmentTestProject project = project()) {
        final AssignmentParameters parameters =
            probit ? probitParameters(project) : parameters(project);
        project.panel.prepareRun(2);
        project.panel.cancelAt = "Estimating " + (probit ? "MNP" : "MNL") + " for group";
        // Let both groups be submitted, then cancel while waiting for fitted results.
        project.panel.cancelAfterChecks = 3;
        project.execute("CREATE TABLE mini_paths1_header (sentinel INT)");
        project.execute("INSERT INTO mini_paths1_header VALUES (37)");
        byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
        try (LogitCalibration calibration =
            new LogitCalibration(parameters, settings(parameters))) {
          assertThrows(
              java.util.concurrent.CancellationException.class, () -> calibration.estimate(false));
        }
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
        assertEquals(37, project.number("SELECT sentinel FROM mini_paths1_header"));
        assertEquals("mini_od", parameters.getODMatrix());
        assertNoTemporaryTables(project);
      }
    }
  }

  @Test
  void calibrationAndStoredCoefficientsBothWorkOnSeparateForecastDemand() throws Exception {
    try (AssignmentTestProject project = project()) {
      final AssignmentParameters parameters = parameters(project);
      estimate(project, parameters, false);
      project.run(new FastMFAssignment(parameters), 2);
      assertCoefficients(parameters.getCostFunctions());
      assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
      assertEquals(
          60,
          project.number("SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=1 AND ldmode=2"),
          1e-3);
      final byte[] saved = Files.readAllBytes(directory.resolve("model.costs"));
      project.properties.setProperty(
          "logitCalibration",
          "{\"estimate\":true,\"sumObserved\":true,\"referenceMode\":1,"
              + "\"tables\":{\"1\":\"no_such_table\",\"2\":\"neither_this_one\"}}");
      output.reset();
      parameters.setCostFunctions("model.costs");
      project.run(new ExactMFAssignment(parameters), 2);
      assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
      assertEquals(
          60,
          project.number("SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=1 AND ldmode=2"),
          1e-3);
      assertArrayEquals(saved, Files.readAllBytes(directory.resolve("model.costs")));
      assertFalse(output.toString(StandardCharsets.UTF_8).contains("calibration"));
    }
  }

  @Test
  void invalidTargetsDoNotReplaceCostsOrExistingScenarioResults() throws Exception {
    try (AssignmentTestProject project = project()) {
      final AssignmentParameters parameters = parameters(project);
      project.execute("CREATE TABLE mini_paths1_header (sentinel INT)");
      project.execute("INSERT INTO mini_paths1_header VALUES (17)");
      project.execute("UPDATE observed_road SET qty=-1 WHERE grp=1");
      byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
      try (LogitCalibration calibration = new LogitCalibration(parameters, settings(parameters))) {
        assertThrows(IllegalArgumentException.class, () -> calibration.estimate(false));
      }
      assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
      assertEquals(17, project.number("SELECT sentinel FROM mini_paths1_header"));
      assertNoTemporaryTables(project);
    }
  }

  @Test
  void unidentifiedModelsFailBeforeUpdatingTheCostFile() throws Exception {
    try (AssignmentTestProject project = project()) {
      final AssignmentParameters parameters = parameters(project);
      project.panel.prepareRun(2);
      project.execute("CREATE TABLE mini_paths1_header (sentinel INT)");
      project.execute("INSERT INTO mini_paths1_header VALUES (23)");
      project.execute("UPDATE observed_rail SET qty=0");
      byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
      try (LogitCalibration calibration = new LogitCalibration(parameters, settings(parameters))) {
        Exception failure =
            assertThrows(IllegalArgumentException.class, () -> calibration.estimate(false));
        assertTrue(failure.getMessage().contains("group 0"));
      }
      assertEquals("", output.toString(StandardCharsets.UTF_8));
      assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
      assertEquals(23, project.number("SELECT sentinel FROM mini_paths1_header"));
      assertNoTemporaryTables(project);
    }
  }

  @Test
  void missingRoutesAreLoggedAndWholeObservationsAreSkippedDuringEstimation() throws Exception {
    for (boolean probit : new boolean[] {false, true}) {
      for (boolean hsql : new boolean[] {false, true}) {
        for (boolean exact : new boolean[] {false, true}) {
          try (AssignmentTestProject project = project(hsql)) {
            output.reset();
            final AssignmentParameters parameters =
                probit ? probitParameters(project) : parameters(project);
            project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
            String odClass = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_CLASS);
            project.execute("ALTER TABLE observed_road ADD " + odClass + " INT DEFAULT 0");
            project.execute("ALTER TABLE observed_rail ADD " + odClass + " INT DEFAULT 0");
            // Same OD in another class, with no observed rail demand, must remain usable.
            project.execute("INSERT INTO observed_road VALUES (0,1,2,15,1), (0,99,100,5,0)");
            project.execute("INSERT INTO observed_rail VALUES (0,99,100,7,0)");
            estimate(project, parameters, exact);
            String log = Files.readString(directory.resolve("model.costs"));
            assertEquals(
                3, log.lines().filter(line -> line.contains("skipping OD record:")).count());
            assertTrue(log.contains("group=0, org=1, dst=2, class=0"));
            assertTrue(log.contains("group=1, org=1, dst=2, class=0"));
            assertTrue(log.contains("group=0, org=99, dst=100, class=0"));
            assertTrue(
                log.contains("mode(s) 1 (observed quantity=5.0), 2 (observed quantity=7.0)"));
            assertTrue(log.contains("skipped 3 of 8 OD records"));
            assertTrue(log.contains("Exclusions apply only to estimation"));
            assertTrue(
                log.contains("excluded quantity=" + (probit ? "92.0" : "142.0") + "; retained 5."));
            assertCoverage(
                log,
                "all groups/modes",
                "skipped records=3/8 (37.500%)",
                "missing-route records=3",
                "observed quantity=" + (probit ? "707.0" : "1137.0"),
                "excluded quantity=" + (probit ? "92.0 (13.013%)" : "142.0 (12.489%)"),
                "unroutable quantity=" + (probit ? "52.0 (7.355%)" : "102.0 (8.971%)"),
                "other excluded quantity=40.0");
            assertCoverage(
                log,
                "group=1",
                "skipped records=1/3 (33.333%)",
                "observed quantity=" + (probit ? "200.0" : "600.0"),
                "excluded quantity=" + (probit ? "40.0 (20.000%)" : "80.0 (13.333%)"),
                "unroutable quantity=" + (probit ? "20.0 (10.000%)" : "60.0 (10.000%)"),
                "other excluded quantity=20.0");
            assertCoverage(
                log,
                "mode=2",
                "skipped records=3/7 (42.857%)",
                "missing-route records=3",
                "observed quantity=" + (probit ? "127.0" : "277.0"),
                "unroutable quantity=" + (probit ? "47.0 (37.008%)" : "97.0 (35.018%)"),
                "other excluded quantity=0.0");
            assertCoverage(
                log,
                "group=0, mode=1",
                "skipped records=2/5 (40.000%)",
                "missing-route records=1",
                "observed quantity=440.0",
                "excluded quantity=25.0 (5.682%)",
                "unroutable quantity=5.0 (1.136%)",
                "other excluded quantity=20.0");
            String costFile = Files.readString(directory.resolve("model.costs"));
            assertTrue(costFile.contains("calibration coverage [all groups/modes]"));
            assertTrue(
                Files.readString(directory.resolve("model.costs"))
                    .contains("skipped 3 of 8 OD records"));
            if (probit) {
              assertTrue(
                  Double.isFinite(
                      Double.parseDouble(
                          parameters.getCostFunctions().getProperty("probit.log(cost).1.0"))));
            } else {
              assertCoefficients(parameters.getCostFunctions());
            }
            assertEquals(
                probit ? 707 : 1137,
                project.number("SELECT SUM(qty) FROM observed_road")
                    + project.number("SELECT SUM(qty) FROM observed_rail"),
                1e-3);
            assertEquals("mini_od", parameters.getODMatrix());
            assertNoTemporaryTables(project);
          }
        }
      }
    }
  }

  @Test
  void userCanExplicitlyAssignAnObservedTotalIncludingExcludedCalibrationRecords()
      throws Exception {
    for (boolean probit : new boolean[] {false, true}) {
      for (boolean hsql : new boolean[] {false, true}) {
        for (boolean exact : new boolean[] {false, true}) {
          try (AssignmentTestProject project = project(hsql)) {
            output.reset();
            AssignmentParameters parameters =
                probit ? probitParameters(project) : parameters(project);
            project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
            estimate(project, parameters, exact);
            selectObservedDemand(project, parameters);
            project.run(
                exact ? new ExactMFAssignment(parameters) : new FastMFAssignment(parameters), 2);
            String log = Files.readString(directory.resolve("model.costs"));
            assertTrue(log.contains("skipped 2 of 6 OD records"));
            assertTrue(log.contains("Exclusions apply only to estimation"));
            assertEquals(
                probit ? 680 : 1110,
                project.number("SELECT SUM(qty) FROM mini_paths1_header"),
                1e-3);
            // Calibration drops these records; assignment reallocates their full sums to road.
            assertEquals(
                probit ? 40 : 50,
                project.number(
                    "SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=1 AND ldmode=1"),
                1e-3);
            assertEquals(
                probit ? 40 : 80,
                project.number(
                    "SELECT qty FROM mini_paths1_header WHERE grp=1 AND org=1 AND ldmode=1"),
                1e-3);
            assertEquals(10, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
            if (probit) {
              assertTrue(
                  Double.isFinite(
                      Double.parseDouble(
                          parameters.getCostFunctions().getProperty("probit.log(cost).1.0"))));
            } else {
              assertCoefficients(parameters.getCostFunctions());
            }
            assertTrue(
                Files.readString(directory.resolve("model.costs"))
                    .contains("Exclusions apply only to estimation"));
            assertEquals("observed_total", parameters.getODMatrix());
            assertNoTemporaryTables(project);
          }
        }
      }
    }
  }

  @Test
  void skippingCalibrationObservationsDoesNotFilterSeparateForecastDemand() throws Exception {
    for (boolean probit : new boolean[] {false, true}) {
      try (AssignmentTestProject project = project()) {
        AssignmentParameters parameters = probit ? probitParameters(project) : parameters(project);
        project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
        estimate(project, parameters, false);
        project.run(new FastMFAssignment(parameters), 2);
        assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
        assertEquals(
            300, project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE org=1"), 1e-3);
        assertEquals("mini_od", parameters.getODMatrix());
        assertNoTemporaryTables(project);
      }
    }
  }

  @Test
  void skippingEveryObservationStopsWithoutDialogOrChangesToFilesAndResults() throws Exception {
    for (boolean probit : new boolean[] {false, true}) {
      try (AssignmentTestProject project = project()) {
        output.reset();
        final AssignmentParameters parameters =
            probit ? probitParameters(project) : parameters(project);
        for (int row : new int[] {1, 3, 5}) {
          project.links.getModel().setValueAt(0.0, row, NodusC.DBF_IDX_ENABLED);
        }
        project.execute("CREATE TABLE mini_paths1_header (sentinel INT)");
        project.execute("INSERT INTO mini_paths1_header VALUES (61)");
        final byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
        project.panel.prepareRun(2);
        try (LogitCalibration calibration =
            new LogitCalibration(parameters, settings(parameters))) {
          assertFalse(calibration.estimate(false));
        }
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
        assertEquals(61, project.number("SELECT sentinel FROM mini_paths1_header"));
        assertEquals("mini_od", parameters.getODMatrix());
        assertTrue(project.panel.getAssignmentMenuItem().isEnabled());
        assertNoTemporaryTables(project);
      }
    }
  }

  @Test
  void interruptionLeavesFilesAndScenarioTablesUntouched() throws Exception {
    try (AssignmentTestProject project = project()) {
      final AssignmentParameters parameters = parameters(project);
      byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
      try (LogitCalibration calibration = new LogitCalibration(parameters, settings(parameters))) {
        Thread.currentThread().interrupt();
        try {
          assertThrows(
              java.util.concurrent.CancellationException.class, () -> calibration.estimate(false));
        } finally {
          Thread.interrupted();
        }
      }
      assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
      assertNoTemporaryTables(project);
    }
  }

  @Test
  void probitFitsBothGroupsAndReusesSavedCoefficientsForForecasts() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean exact : new boolean[] {false, true}) {
        try (AssignmentTestProject project = project(hsql)) {
          AssignmentParameters parameters = probitParameters(project);
          setKnownProbitObservations(project);
          estimate(project, parameters, exact);
          selectObservedDemand(project, parameters);
          project.run(
              exact ? new ExactMFAssignment(parameters) : new FastMFAssignment(parameters), 2);
          assertEquals(
              -Math.sqrt(2) / Math.log(2),
              Double.parseDouble(parameters.getCostFunctions().getProperty("probit.log(cost).1.0")),
              1e-7);
          assertEquals(
              -Math.sqrt(2) / (2 * Math.log(2)),
              Double.parseDouble(parameters.getCostFunctions().getProperty("probit.log(cost).1.1")),
              1e-7);
          assertEquals("-7", parameters.getCostFunctions().getProperty("probit.log(cost).1.9"));
          assertEquals("1", parameters.getCostFunctions().getProperty("probit.reference.9"));
          assertEquals("-99", parameters.getCostFunctions().getProperty("log(cost).1.0"));
          assertEquals(600, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
          assertEquals(
              97.72498680518208,
              project.number(
                  "SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=5 AND ldmode=1"),
              1e-3);
          assertEquals(
              30.85375387259869,
              project.number(
                  "SELECT qty FROM mini_paths1_header WHERE grp=1 AND org=3 AND ldmode=2"),
              1e-3);
          assertEquals("observed_total", parameters.getODMatrix());
          assertNoTemporaryTables(project);
          final byte[] saved = Files.readAllBytes(directory.resolve("model.costs"));
          String report = Files.readString(directory.resolve("model.costs"));
          assertTrue(report.contains("# Log-cost SE="));
          assertTrue(report.contains("independent N(0,1)"));
          assertTrue(report.contains("# Group 0:"));
          assertTrue(report.contains("# Group 1:"));
          parameters.setODMatrix("mini_od");
          parameters.setCostFunctions("model.costs");
          project.run(
              exact ? new ExactMFAssignment(parameters) : new FastMFAssignment(parameters), 2);
          assertEquals(900, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 1e-3);
          assertEquals(
              15.86552539314571,
              project.number(
                  "SELECT qty FROM mini_paths1_header WHERE grp=0 AND org=3 AND ldmode=2"),
              1e-3);
          assertEquals(
              61.70750774519738,
              project.number(
                  "SELECT qty FROM mini_paths1_header WHERE grp=1 AND org=3 AND ldmode=2"),
              1e-3);
          assertArrayEquals(saved, Files.readAllBytes(directory.resolve("model.costs")));
        }
      }
    }
  }

  @Test
  void probitFailuresAndCancellationPreserveAllGroupsAndPreviousResults() throws Exception {
    for (String reason : new String[] {"rank", "separation", "cancel"}) {
      try (AssignmentTestProject project = project()) {
        final AssignmentParameters parameters = probitParameters(project);
        project.panel.prepareRun(2);
        project.execute("CREATE TABLE mini_paths1_header (sentinel INT)");
        project.execute("INSERT INTO mini_paths1_header VALUES (53)");
        if (reason.equals("rank")) {
          project.execute("DELETE FROM observed_road WHERE grp=1 AND org<>3");
          project.execute("DELETE FROM observed_rail WHERE grp=1 AND org<>3");
        } else if (reason.equals("separation")) {
          project.execute("UPDATE observed_rail SET qty=0 WHERE grp=1");
        } else {
          project.panel.cancelAt = "Saving MNP coefficients";
        }
        byte[] before = Files.readAllBytes(directory.resolve("model.costs"));
        try (LogitCalibration calibration =
            new LogitCalibration(parameters, settings(parameters))) {
          if (reason.equals("cancel")) {
            assertThrows(
                java.util.concurrent.CancellationException.class,
                () -> calibration.estimate(false));
          } else {
            Exception failure =
                assertThrows(IllegalArgumentException.class, () -> calibration.estimate(false));
            assertTrue(failure.getMessage().contains("MNP group 1"));
          }
        }
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("model.costs")));
        assertEquals(53, project.number("SELECT sentinel FROM mini_paths1_header"));
        assertEquals("mini_od", parameters.getODMatrix());
        assertNoTemporaryTables(project);
      }
    }
  }

  private LogitCalibrationSettings settings(AssignmentParameters parameters) {
    return new LogitCalibrationSettings(1, Map.of(1, "observed_road", 2, "observed_rail"));
  }

  private void estimate(
      AssignmentTestProject project, AssignmentParameters parameters, boolean exact)
      throws Exception {
    project.panel.prepareRun(2);
    project.execute("CREATE TABLE mini_paths1_header (sentinel INT)");
    project.execute("INSERT INTO mini_paths1_header VALUES (73)");
    final String selectedDemand = parameters.getODMatrix();
    try (LogitCalibration calibration = new LogitCalibration(parameters, settings(parameters))) {
      assertTrue(calibration.estimate(exact));
    }
    assertFalse(project.panel.fittingProgressLengths.isEmpty());
    assertTrue(
        project.panel.fittingProgressLengths.stream().allMatch(length -> length == 0),
        "Fitting must stay indeterminate regardless of cancellation-check count");
    assertEquals(
        73,
        project.number("SELECT sentinel FROM mini_paths1_header"),
        "Successful estimation must preserve existing assignment results");
    assertEquals(selectedDemand, parameters.getODMatrix());
    assertNoTemporaryTables(project);
    project.execute("DROP TABLE mini_paths1_header");
  }

  private void selectObservedDemand(AssignmentTestProject project, AssignmentParameters parameters)
      throws Exception {
    // A normal, explicitly selected OD table, independent of the estimator's temporary data.
    project.execute("CREATE TABLE observed_total (grp INT, org INT, dst INT, qty DOUBLE)");
    project.execute(
        "INSERT INTO observed_total SELECT grp, org, dst, SUM(qty) AS qty "
            + "FROM (SELECT * FROM observed_road UNION ALL SELECT * FROM observed_rail) AS demand "
            + "GROUP BY grp, org, dst");
    parameters.setODMatrix("observed_total");
  }

  private AssignmentParameters probitParameters(AssignmentTestProject project) throws Exception {
    project.execute("UPDATE observed_rail SET qty=20");
    project.execute("UPDATE observed_road SET qty=40 WHERE grp=1 AND org=3");
    project.execute("UPDATE observed_road SET qty=80 WHERE grp=1 AND org=5");
    AssignmentParameters parameters = parameters(project);
    Files.writeString(
        directory.resolve("model.costs"),
        "probit.reference.9 = 1\nprobit.log(cost).1.9 = -7\nlog(cost).1.0 = -99\n",
        java.nio.file.StandardOpenOption.APPEND);
    parameters.setCostFunctions("model.costs");
    parameters.setModalSplitMethodName("MNP");
    return parameters;
  }

  private void setKnownProbitObservations(AssignmentTestProject project) throws Exception {
    // Independent standard-normal CDF values at [0,1,2] and [0,0.5,1].
    double[][] probabilities = {
      {0.5, 0.8413447460685429, 0.9772498680518208},
      {0.5, 0.6914624612740131, 0.8413447460685429}
    };
    for (int group = 0; group < 2; group++) {
      for (int row = 0; row < 3; row++) {
        String where = " WHERE grp=" + group + " AND org=" + (2 * row + 1);
        double road = 100 * probabilities[group][row];
        project.execute("UPDATE observed_road SET qty=" + road + where);
        project.execute("UPDATE observed_rail SET qty=" + (100 - road) + where);
      }
    }
  }

  private AssignmentTestProject project() throws Exception {
    return project(false);
  }

  private AssignmentTestProject project(boolean hsql) throws Exception {
    AssignmentTestProject project =
        new AssignmentTestProject(
            directory,
            6,
            new double[][] {
              {11, 1, 2, 1, 0}, {12, 1, 2, 1, 0},
              {13, 3, 4, 1, 0}, {14, 3, 4, 2, 0},
              {15, 5, 6, 1, 0}, {16, 5, 6, 4, 0}
            },
            hsql);
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
        project.execute(
            "INSERT INTO observed_road VALUES ("
                + group
                + ","
                + origin
                + ","
                + destination
                + ","
                + road
                + ")");
        project.execute(
            "INSERT INTO observed_rail VALUES ("
                + group
                + ","
                + origin
                + ","
                + destination
                + ","
                + rail
                + ")");
        project.execute(
            "INSERT INTO mini_od VALUES ("
                + group
                + ","
                + origin
                + ","
                + destination
                + ","
                + 100 * (group + 1)
                + ")");
      }
    }
    return project;
  }

  private AssignmentParameters parameters(AssignmentTestProject project) throws Exception {
    Files.writeString(
        directory.resolve("model.costs"),
        "# Keep this user comment\n"
            + "ld.1,1 = 0\nul.1,1 = 0\ntr.1,1 = 0\nmv.1,1 = BASECOST\n"
            + "ld.2,1 = 0\nul.2,1 = 0\ntr.2,1 = 0\nmv.2,1 = BASECOST\n");
    AssignmentParameters parameters = project.parameters(2);
    parameters.setCostFunctions("model.costs");
    parameters.setModalSplitMethodName("MNL");
    return parameters;
  }

  private void assertCoverage(String log, String scope, String... fields) {
    String coverage =
        log.lines()
            .filter(line -> line.contains("calibration coverage [" + scope + "]: "))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing coverage for " + scope));
    for (String field : fields) {
      assertTrue(coverage.contains(field), "Expected " + field + " in " + coverage);
    }
  }

  private void assertCoefficients(Properties coefficients) {
    for (int group = 0; group < 2; group++) {
      assertEquals(-2, Double.parseDouble(coefficients.getProperty("log(cost).1." + group)), 1e-6);
      assertEquals(
          Math.log(1.5 * (group + 1)),
          Double.parseDouble(coefficients.getProperty("(intercept).2." + group)),
          1e-6);
    }
  }

  private void assertNoTemporaryTables(AssignmentTestProject project) throws Exception {
    assertEquals(
        0,
        project.number(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_NAME LIKE 'NODUS_MNL_%'"));
  }
}
