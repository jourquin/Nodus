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

import static edu.uclouvain.core.nodus.compute.assign.ComputingTimesTestSupport.capture;
import static edu.uclouvain.core.nodus.compute.assign.ComputingTimesTestSupport.count;
import static edu.uclouvain.core.nodus.compute.assign.ComputingTimesTestSupport.seconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Auditing on/off must produce the same saved routes, flows and costs in real coordinators. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("NodusC.displayComputingTimes")
@ResourceLock("NodusC.useCompactShortestPaths")
@ResourceLock("NodusC.useLegacyAStar")
@ResourceLock(Resources.SYSTEM_OUT)
@Timeout(30)
class ComputingTimesAssignmentIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> auditingPreservesFastAndExactResultsWithBothGraphsAndWorkerCounts() {
    return Stream.of(false, true)
        .flatMap(
            compact ->
                Stream.of(false, true)
                    .flatMap(
                        exact ->
                            Stream.of(1, 4)
                                .flatMap(
                                    threads ->
                                        (exact ? Stream.of(false, true) : Stream.of(false))
                                            .map(
                                                legacy ->
                                                    dynamicTest(
                                                        "compact="
                                                            + compact
                                                            + ", exact="
                                                            + exact
                                                            + ", threads="
                                                            + threads
                                                            + ", legacy="
                                                            + legacy,
                                                        () ->
                                                            checkAssignment(
                                                                compact, exact, threads,
                                                                legacy))))));
  }

  private void checkAssignment(boolean compact, boolean exact, int threads, boolean legacy)
      throws Exception {
    boolean previousAudit = NodusC.displayComputingTimes;
    boolean previousGraph = NodusC.useCompactShortestPaths;
    boolean previousLegacy = NodusC.useLegacyAStar;
    try (AssignmentTestProject project =
        new AssignmentTestProject(
            directory,
            6,
            new double[][] {
              {11, 1, 2, 2, 0}, {12, 2, 4, 3, 0}, {13, 1, 3, 4, 0},
              {14, 3, 4, 4, 0}, {15, 1, 5, 1, 0}, {16, 6, 4, 1, 0}
            })) {
      NodusC.useCompactShortestPaths = compact;
      NodusC.useLegacyAStar = legacy;
      // Mode 2 can be loaded at the first origin but cannot reach its requested destination.
      for (int row : new int[] {4, 5}) {
        project.links.getModel().setValueAt(2.0, row, NodusC.DBF_IDX_MODE);
      }
      project.execute("INSERT INTO mini_od VALUES (1,1,4,100),(2,2,4,40)");
      List<List<String>> expected = null;
      for (boolean enabled : new boolean[] {false, true, false}) {
        NodusC.displayComputingTimes = enabled;
        final ComputingTimesTestSupport.Clock clock = new ComputingTimesTestSupport.Clock();
        clock.stepEachRead();
        final AssignmentParameters parameters = parameters(project, threads, clock);
        for (String operation : new String[] {"ld", "ul", "tr"}) {
          parameters.getCostFunctions().setProperty(operation + ".2,1", "0");
        }
        parameters.getCostFunctions().setProperty("mv.2,1", "BASECOST");
        parameters.setNbIterations(4);
        parameters.setCostMarkup(1);
        parameters.setModalSplitMethodName("Proportional");
        final Assignment assignment =
            exact ? new ExactMFAssignment(parameters) : new FastMFAssignment(parameters);
        String report = capture(() -> project.run(assignment, 2));
        assertEquals(enabled, report.contains("Computing times:"));
        assertEquals(enabled && !exact, report.contains("unreachable-destination diagnostic"));
        if (enabled) {
          assertTrue(report.contains("completed"), report);
          assertTrue(report.contains(exact ? "A*:" : "Dijkstra:"), report);
          assertTrue(seconds(report, exact ? "A*" : "Dijkstra") > 0, report);
          assertTrue(report.contains("Outside-phase breakdown"), report);
          if (!exact) {
            assertEquals(12, count(report, "Completed Dijkstra searches"));
            assertEquals(4, count(report, "Searches ending with unreachable destinations"));
            assertEquals(3, count(report, "Potentially entirely skippable searches"));
          }
        }
        assertEquals(enabled, clock.reads() > 0);
        assertFalse(report.contains("NaN"), report);
        assertEquals(140, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.005);
        final List<List<String>> actual = snapshot(project);
        if (expected != null) {
          assertEquals(expected, actual);
        }
        expected = actual;
      }
    } finally {
      NodusC.displayComputingTimes = previousAudit;
      NodusC.useCompactShortestPaths = previousGraph;
      NodusC.useLegacyAStar = previousLegacy;
    }
  }

  private static AssignmentParameters parameters(
      AssignmentTestProject project, int threads, ComputingTimesTestSupport.Clock clock) {
    final AssignmentComputingTimes audit = new AssignmentComputingTimes(clock);
    final AssignmentParameters parameters =
        new AssignmentParameters(project) {
          @Override
          public AssignmentComputingTimes getComputingTimes() {
            return audit;
          }
        };
    parameters.setCostFunctions(project.parameters(threads).getCostFunctions());
    parameters.setScenario(1);
    parameters.setODMatrix("mini_od");
    parameters.setWhereStmt("");
    parameters.setThreads(threads);
    parameters.setDetailedPaths(true);
    return parameters;
  }

  private static List<List<String>> snapshot(AssignmentTestProject project) throws Exception {
    List<List<String>> result =
        new ArrayList<>(
            project.rows("SELECT * FROM mini_vnet1 ORDER BY node1,node2,link1,link2,vtype"));
    result.addAll(
        project.rows(
            "SELECT h.grp,h.org,h.dst,h.iteration,h.qty,h.ldcost,h.ulcost,h.trcost,h.tpcost,"
                + "h.stcost,h.swcost,h.mvcost,d.link,d.mode,d.means "
                + "FROM mini_paths1_header h JOIN mini_paths1_detail d ON h.pathidx=d.pathidx "
                + "ORDER BY h.grp,h.org,h.dst,h.iteration,d.link"));
    return result;
  }
}
