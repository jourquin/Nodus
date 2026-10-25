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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.omGraphics.OMPoint;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusC.ExactMFAlgorithm;
import edu.uclouvain.core.nodus.compute.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.modalsplit.PathsForMode;
import edu.uclouvain.core.nodus.compute.modalsplit.Proportional;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Shared first routes, unavailable loading modes and OD-local subsequent penalties. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("NodusC.useCompactShortestPaths")
@ResourceLock("NodusC.exactMFAlgorithm")
@ResourceLock("NodusC.displayComputingTimes")
@ResourceLock(Resources.SYSTEM_OUT)
@Timeout(30)
class ExactMultiFlowFirstRouteIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> firstRoutesAndModeHandlingAreSharedByAllExactAlgorithms() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean compact : new boolean[] {false, true}) {
        for (ExactMFAlgorithm algorithm : ExactMFAlgorithm.values()) {
          String label = "HSQLDB=" + hsql + "/compact=" + compact + "/algorithm=" + algorithm;
          tests.add(dynamicTest("one route/" + label, () -> firstRoutes(hsql, compact, algorithm)));
          tests.add(dynamicTest("intermodal/" + label, () -> intermodal(hsql, compact, algorithm)));
          tests.add(
              dynamicTest("OD penalties/" + label, () -> penalties(hsql, compact, algorithm)));
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> destinationTreesStayIndependentAcrossOriginsClassesAndRuns() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean compact : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                "classes/HSQLDB=" + hsql + "/compact=" + compact,
                () -> {
                  try (Settings settings = new Settings(compact, ExactMFAlgorithm.IMPROVED_ASTAR);
                      AssignmentTestProject project =
                          new AssignmentTestProject(
                              directory,
                              3,
                              new double[][] {
                                {11, 1, 3, 0.5, 0}, {12, 1, 2, 0.1, 0}, {13, 2, 3, 0.1, 0}
                              },
                              hsql)) {
                    String classColumn = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_CLASS);
                    project.execute(
                        "ALTER TABLE mini_od ADD " + classColumn + " INTEGER DEFAULT 0");
                    project.execute(
                        "INSERT INTO mini_od VALUES (1,1,3,100,0),(1,2,3,50,0),(1,1,3,200,1),(1,2,3,100,1),"
                            + "(2,1,3,200,0),(2,2,3,100,0),(2,1,3,400,1),(2,2,3,200,1)");
                    AssignmentParameters parameters = parameters(project, 2);
                    parameters.setCostMarkup(5);
                    // Class 0 prefers 1-2-3; class 1 prefers the direct 1-3 link.
                    parameters.getCostFunctions().setProperty("mv.1,1-1", "6-BASECOST");
                    String headers =
                        "SELECT grp,org,dst,iteration,qty,mvcost FROM mini_paths1_header ORDER BY grp,org,dst,iteration,mvcost";
                    capture(() -> project.run(new ExactMFAssignment(parameters), 2));
                    assertEquals(
                        8,
                        count(ComputingTimesTestSupport.report(parameters), "Dijkstra searches"));
                    for (double markup : new double[] {5, -0.5}) {
                      parameters.setCostMarkup(markup);
                      NodusC.exactMFAlgorithm = ExactMFAlgorithm.IMPROVED_ASTAR;
                      capture(() -> project.run(new ExactMFAssignment(parameters), 2));
                      List<List<String>> expected = project.rows(headers);
                      List<List<String>> details = project.pathDetails();
                      for (int repeat = 0; repeat < 2; repeat++) {
                        NodusC.exactMFAlgorithm = ExactMFAlgorithm.DESTINATION_ASTAR;
                        capture(() -> project.run(new ExactMFAssignment(parameters), 2));
                        assertEquals(expected, project.rows(headers));
                        assertEquals(details, project.pathDetails());
                        assertEquals(
                            1350, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.005);
                        String report = ComputingTimesTestSupport.report(parameters);
                        assertEquals(4, count(report, "Reverse Dijkstra searches"));
                        assertEquals(8, count(report, "Destination A* searches"));
                        assertTrue(
                            project
                                .panel
                                .routingProgressLengths
                                .stream()
                                .allMatch(length -> length == 2));
                      }
                    }
                    NodusC.exactMFAlgorithm = ExactMFAlgorithm.DESTINATION_ASTAR;
                    project.setProgressHandler(
                        message ->
                            !(Thread.currentThread()
                                instanceof
                                edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker));
                    capture(() -> project.runCancelled(new ExactMFAssignment(parameters), 1));
                    assertTrue(ComputingTimesTestSupport.report(parameters).contains("cancelled"));
                  }
                }));
      }
    }
    return tests;
  }

  private void firstRoutes(boolean hsql, boolean compact, ExactMFAlgorithm algorithm)
      throws Exception {
    try (Settings settings = new Settings(compact, algorithm);
        AssignmentTestProject project =
            new AssignmentTestProject(
                directory,
                6,
                new double[][] {
                  {11, 1, 3, 0.5, 0},
                  {12, 1, 2, 0.1, 0},
                  {13, 2, 3, 0.1, 0},
                  {14, 3, 4, 0.1, 0},
                  {15, 5, 6, 1, 0}
                },
                hsql)) {
      // Mode 2 exists in this group but cannot be loaded at either demand origin.
      project.links.getModel().setValueAt(2.0, 4, NodusC.DBF_IDX_MODE);
      double[] longitudes = {0, -10, 10, 11, 0, 1};
      for (int node = 0; node < longitudes.length; node++) {
        ((OMPoint) project.nodes.getEsriGraphicList().getOMGraphicAt(node))
            .set(0, longitudes[node]);
      }
      project.execute(
          "INSERT INTO mini_od VALUES (1,1,3,210),(1,1,4,120),(1,3,1,30),"
              + "(2,1,3,420),(2,1,4,240),(2,3,1,60)");
      AssignmentParameters parameters = parameters(project, 1);
      capture(() -> project.run(new FastMFAssignment(parameters), 2));
      List<List<String>> expected = project.snapshot();
      capture(() -> project.run(new ExactMFAssignment(parameters), 2));

      assertEquals(expected, project.snapshot());
      assertEquals(1080, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.005);
      assertEquals(0, project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE mvcost>=0.5"));
      // Two origin trees or three destination trees per group; mode 2 is never loadable.
      String report = ComputingTimesTestSupport.report(parameters);
      assertEquals(
          algorithm == ExactMFAlgorithm.DESTINATION_ASTAR ? 6 : 4,
          count(report, firstSearchLabel(algorithm) + " searches"),
          report);
      assertFalse(report.contains("Improved A*:"), report);
      assertFalse(report.contains("Destination A*:"), report);

      parameters.setNbIterations(2);
      capture(() -> project.run(new ExactMFAssignment(parameters), 2));
      report = ComputingTimesTestSupport.report(parameters);
      assertEquals(
          algorithm == ExactMFAlgorithm.DESTINATION_ASTAR ? 6 : 4,
          count(report, firstSearchLabel(algorithm) + " searches"),
          report);
      assertEquals(6, count(report, searchLabel(algorithm) + " searches"), report);
      assertEquals(1080, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.005);
    }
  }

  private void intermodal(boolean hsql, boolean compact, ExactMFAlgorithm algorithm)
      throws Exception {
    try (Settings settings = new Settings(compact, algorithm);
        AssignmentTestProject project =
            new AssignmentTestProject(
                directory,
                4,
                new double[][] {
                  {11, 1, 4, 20, 0}, {12, 1, 4, 30, 0},
                  {13, 1, 2, 4, 0}, {14, 2, 4, 8, 0}
                },
                hsql)) {
      for (int row : new int[] {1, 3}) {
        project.links.getModel().setValueAt(2.0, row, NodusC.DBF_IDX_MODE);
      }
      project.setNodeHandling(2, NodusC.HANDLING_ALL);
      project.execute("INSERT INTO mini_od VALUES (1,1,4,132),(2,1,4,264)");
      AssignmentParameters parameters = parameters(project, 3);
      RecordingSplit split = new RecordingSplit(project);
      FastMFAssignment fast =
          new FastMFAssignment(parameters) {
            @Override
            public ModalSplitMethod getModalSplitMethod() {
              return split;
            }
          };
      final ExactMFAssignment exact =
          new ExactMFAssignment(parameters) {
            @Override
            public ModalSplitMethod getModalSplitMethod() {
              return split;
            }
          };
      capture(() -> project.run(fast, 2));
      List<String> expected = split.snapshot();
      assertEquals(6, expected.size(), "Unimodal 1, unimodal 2 and intermodal must stay separate");
      assertTrue(
          expected.stream().anyMatch(value -> value.contains(":" + (NodusC.MAXMM + 2) + ":")));
      split.choices.clear();
      capture(() -> project.run(exact, 2));
      assertEquals(expected, split.snapshot());
      assertEquals(396, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.005);
      assertTrue(
          project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE ldmode<>ulmode") > 0);
    }
  }

  private void penalties(boolean hsql, boolean compact, ExactMFAlgorithm algorithm)
      throws Exception {
    try (Settings settings = new Settings(compact, algorithm);
        AssignmentTestProject project =
            new AssignmentTestProject(
                directory,
                6,
                new double[][] {
                  {11, 1, 2, 1, 0},
                  {12, 2, 3, 1, 0},
                  {13, 1, 5, 1.1, 0},
                  {14, 5, 3, 1.1, 0},
                  {15, 5, 4, 0.5, 0},
                  {16, 1, 6, 1.3, 0},
                  {17, 6, 3, 1.3, 0}
                },
                hsql)) {
      for (int node = 0; node < 6; node++) {
        ((OMPoint) project.nodes.getEsriGraphicList().getOMGraphicAt(node)).set(0, 0);
      }
      project.execute("INSERT INTO mini_od VALUES (1,1,3,132),(1,1,4,60),(2,1,3,264),(2,1,4,120)");
      AssignmentParameters parameters = parameters(project, 2);
      capture(() -> project.run(new ExactMFAssignment(parameters), 2));
      String report = ComputingTimesTestSupport.report(parameters);
      assertEquals(
          algorithm == ExactMFAlgorithm.DESTINATION_ASTAR ? 4 : 2,
          count(report, firstSearchLabel(algorithm) + " searches"),
          report);
      assertEquals(4, count(report, searchLabel(algorithm) + " searches"), report);
      for (int group : new int[] {1, 2}) {
        assertEquals(
            2.2,
            project.number(
                "SELECT mvcost FROM mini_paths1_header WHERE grp="
                    + group
                    + " AND dst=3 AND iteration=1"),
            0.001);
      }
      assertEquals(576, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.005);
      // Fast MF penalizes 1-5 for destination 4 as well, selecting the 2.6 route to 3.
      capture(() -> project.run(new FastMFAssignment(parameters), 2));
      assertEquals(
          2.6,
          project.number(
              "SELECT mvcost FROM mini_paths1_header WHERE grp=1 AND dst=3 AND iteration=1"),
          0.001);
    }
  }

  private static AssignmentParameters parameters(AssignmentTestProject project, int alternatives) {
    AssignmentParameters parameters = project.parameters(4);
    for (int mode : new int[] {1, 2}) {
      for (String operation : new String[] {"ld", "ul", "tr"}) {
        parameters.getCostFunctions().setProperty(operation + "." + mode + ",1", "0");
      }
      parameters.getCostFunctions().setProperty("mv." + mode + ",1", "BASECOST");
    }
    parameters.getCostFunctions().setProperty("tp.1,1-2,1", "0");
    parameters.getCostFunctions().setProperty("tp.2,1-1,1", "0");
    parameters.setNbIterations(alternatives);
    parameters.setModalSplitMethodName("Proportional");
    parameters.setCostMarkup(1);
    parameters.setMaxDetourRatio(0);
    parameters.setKeepOnlyCheapestIntermodalPath(false);
    return parameters;
  }

  private static String firstSearchLabel(ExactMFAlgorithm algorithm) {
    return algorithm == ExactMFAlgorithm.DESTINATION_ASTAR ? "Reverse Dijkstra" : "Dijkstra";
  }

  private static String searchLabel(ExactMFAlgorithm algorithm) {
    return algorithm == ExactMFAlgorithm.DESTINATION_ASTAR ? "Destination A*" : "Improved A*";
  }

  private static final class RecordingSplit extends Proportional {
    final List<String> choices = Collections.synchronizedList(new ArrayList<>());

    RecordingSplit(AssignmentTestProject project) {
      super(project);
    }

    @Override
    public boolean split(ODCell demand, List<PathsForMode> modes) {
      for (PathsForMode mode : modes) {
        choices.add(
            demand.getGroup()
                + ":"
                + mode.pathList.getFirst().intermodalModeKey
                + ":"
                + mode.pathList
                    .stream()
                    .map(path -> Double.toString(path.weights.getCost()))
                    .sorted()
                    .collect(Collectors.joining(",")));
      }
      return super.split(demand, modes);
    }

    List<String> snapshot() {
      return choices.stream().sorted().collect(Collectors.toList());
    }
  }

  private static final class Settings implements AutoCloseable {
    private final boolean compact = NodusC.useCompactShortestPaths;
    private final boolean audit = NodusC.displayComputingTimes;
    private final ExactMFAlgorithm algorithm = NodusC.exactMFAlgorithm;

    Settings(boolean compact, ExactMFAlgorithm algorithm) {
      NodusC.useCompactShortestPaths = compact;
      NodusC.exactMFAlgorithm = algorithm;
      NodusC.displayComputingTimes = true;
    }

    @Override
    public void close() {
      NodusC.useCompactShortestPaths = compact;
      NodusC.exactMFAlgorithm = algorithm;
      NodusC.displayComputingTimes = audit;
    }
  }
}
