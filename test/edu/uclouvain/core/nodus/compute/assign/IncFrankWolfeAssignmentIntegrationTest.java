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

import static edu.uclouvain.core.nodus.compute.assign.ParallelRouteTestCase.assertSavedDemand;
import static edu.uclouvain.core.nodus.compute.assign.ParallelRouteTestCase.congestedParameters;
import static edu.uclouvain.core.nodus.compute.assign.ParallelRouteTestCase.congestedProject;
import static edu.uclouvain.core.nodus.compute.assign.ParallelRouteTestCase.forwardFlow;
import static edu.uclouvain.core.nodus.compute.assign.ParallelRouteTestCase.pathRoundingTolerance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.FrankWolfeAssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.IncrementalAssignmentWorker;
import edu.uclouvain.core.nodus.compute.costs.CostParserWorker;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Complete hybrid assignments, including the boundary between incremental and FW flows. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class IncFrankWolfeAssignmentIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> warmUpAndFrankWolfeConserveDemandAndRepeatAcrossWorkerCounts() {
    return Stream.of(false, true)
        .map(
            detailedPaths ->
                DynamicTest.dynamicTest(
                    "detailedPaths=" + detailedPaths,
                    () ->
                        checkWarmUpAndFrankWolfeConserveDemandAndRepeatAcrossWorkerCounts(
                            detailedPaths)));
  }

  private void checkWarmUpAndFrankWolfeConserveDemandAndRepeatAcrossWorkerCounts(
      boolean detailedPaths) throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 600)) {
      List<List<String>> expected = null;
      int expectedIterations = 0;
      for (int threads : new int[] {1, 4, 4}) {
        final ObservedHybrid assignment = observed(project, threads, 30, detailedPaths);
        project.run(assignment, 2);
        assertWarmUp(assignment);
        assertTrue(assignment.lambdas.get(0) > 0 && assignment.lambdas.get(0) < 1);
        assertCompletion(assignment, AssignmentCompletion.Reason.CONVERGED, 30);
        assertTrue(assignment.getCompletion().getFinalRelativeGap() < 0.0001);
        // c1=10+q1/100, c2=12+q2/100, q1+q2=600 => (400,200), common cost 14.
        // Two groups round their vehicle counts upward independently, allowing two PCUs.
        assertEquals(400, forwardFlow(project, 11), 2.1);
        assertEquals(200, forwardFlow(project, 12), 2.1);
        assertOutput(project, 600, detailedPaths);
        for (int link : new int[] {11, 12}) {
          assertEquals(
              14,
              project.number(
                  "SELECT ucost FROM mini_vnet1 WHERE link1="
                      + link
                      + " AND vtype="
                      + VirtualLink.TYPE_MOVE
                      + " AND ABS(node1)<ABS(node2)"),
              0.05);
        }
        final List<List<String>> actual = results(project, detailedPaths);
        if (expected != null) {
          assertEquals(expected, actual);
          assertEquals(expectedIterations, assignment.getCompletion().getIterationsPerformed());
        }
        expected = actual;
        expectedIterations = assignment.getCompletion().getIterationsPerformed();
      }
    }
  }

  @Test
  void togglingDetailedPathsPreservesFlowsAndHeadersAndRemovesStaleDetails() throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 600)) {
      project.run(observed(project, 1, 30, false), 2);
      final List<List<String>> expected = results(project, false);
      project.run(observed(project, 4, 30, true), 2);
      assertEquals(expected, results(project, false));
      assertOutput(project, 600, true);
      project.run(observed(project, 4, 30, false), 2);
      assertEquals(expected, results(project, false));
      assertOutput(project, 600, false);
    }
  }

  @TestFactory
  Stream<DynamicTest> zeroFrankWolfeIterationsStillPerformsExactlyFourWarmUpIncrements() {
    return Stream.of(false, true)
        .map(
            detailedPaths ->
                DynamicTest.dynamicTest(
                    "detailedPaths=" + detailedPaths,
                    () ->
                        checkZeroFrankWolfeIterationsStillPerformsExactlyFourWarmUpIncrements(
                            detailedPaths)));
  }

  private void checkZeroFrankWolfeIterationsStillPerformsExactlyFourWarmUpIncrements(
      boolean detailedPaths) throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 600)) {
      ObservedHybrid assignment = observed(project, 4, 0, detailedPaths);
      project.run(assignment, 2);
      assertWarmUp(assignment);
      assertCompletion(assignment, AssignmentCompletion.Reason.MAX_ITERATIONS_REACHED, 0);
      assertFalse(assignment.getCompletion().hasFinalRelativeGap());
      assertEquals(420, forwardFlow(project, 11), 1e-9);
      assertEquals(180, forwardFlow(project, 12), 1e-9);
      assertOutput(project, 600, detailedPaths);
      assertEquals(
          List.of(
              List.of("1", "240.000"),
              List.of("2", "180.000"),
              List.of("3", "120.000"),
              List.of("4", "60.000")),
          project.rows(
              "SELECT iteration,SUM(qty) FROM mini_paths1_header "
                  + "GROUP BY iteration ORDER BY iteration"));
      if (detailedPaths) {
        assertEquals(
            List.of(
                List.of("1", "240.000", "10.000", "11"),
                List.of("2", "180.000", "12.000", "12"),
                List.of("3", "120.000", "12.400", "11"),
                List.of("4", "60.000", "13.600", "11")),
            project.rows(
                "SELECT h.iteration,SUM(h.qty),h.mvcost,d.link "
                    + "FROM mini_paths1_header h JOIN mini_paths1_detail d ON h.pathidx=d.pathidx "
                    + "GROUP BY h.iteration,h.mvcost,d.link ORDER BY h.iteration"));
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> oneFrankWolfeIterationUpdatesWarmUpFlowsAndReportsItsOwnLimit() {
    return Stream.of(false, true)
        .map(
            detailedPaths ->
                DynamicTest.dynamicTest(
                    "detailedPaths=" + detailedPaths,
                    () ->
                        checkOneFrankWolfeIterationUpdatesWarmUpFlowsAndReportsItsOwnLimit(
                            detailedPaths)));
  }

  private void checkOneFrankWolfeIterationUpdatesWarmUpFlowsAndReportsItsOwnLimit(
      boolean detailedPaths) throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 600)) {
      ObservedHybrid assignment = observed(project, 4, 1, detailedPaths);
      project.run(assignment, 2);
      assertWarmUp(assignment);
      assertCompletion(assignment, AssignmentCompletion.Reason.MAX_ITERATIONS_REACHED, 1);
      assertTrue(assignment.getCompletion().getFinalRelativeGap() > 0.0001);
      assertEquals(400, forwardFlow(project, 11), 2.1);
      assertEquals(200, forwardFlow(project, 12), 2.1);
      assertOutput(project, 600, detailedPaths);
      assertTrue(project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE iteration<5") > 0);
      assertTrue(project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE iteration=5") > 0);
      assertEquals(5, project.number("SELECT MAX(iteration) FROM mini_paths1_header"));
    }
  }

  @TestFactory
  Stream<DynamicTest> zeroLambdaRetainsWarmUpPathsAndConvergesInOneFrankWolfeIteration() {
    return Stream.of(false, true)
        .map(
            detailedPaths ->
                DynamicTest.dynamicTest(
                    "detailedPaths=" + detailedPaths,
                    () ->
                        checkZeroLambdaRetainsWarmUpPathsAndConvergesInOneFrankWolfeIteration(
                            detailedPaths)));
  }

  private void checkZeroLambdaRetainsWarmUpPathsAndConvergesInOneFrankWolfeIteration(
      boolean detailedPaths) throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 1000)) {
      ObservedHybrid assignment = observed(project, 4, 10, detailedPaths);
      project.run(assignment, 2);
      assertEquals(List.of(0.0), assignment.lambdas);
      assertCompletion(assignment, AssignmentCompletion.Reason.CONVERGED, 10);
      assertEquals(0, assignment.getCompletion().getFinalRelativeGap(), 1e-12);
      assertEquals(600, forwardFlow(project, 11), 1e-9);
      assertEquals(400, forwardFlow(project, 12), 1e-9);
      assertOutput(project, 1000, detailedPaths);
      assertEquals(
          1000, project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE iteration<5"));
      assertEquals(0, project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE iteration=5"));
    }
  }

  @TestFactory
  Stream<DynamicTest> unitLambdaReplacesAllWarmUpFlowsAndPaths() {
    return Stream.of(false, true)
        .map(
            detailedPaths ->
                DynamicTest.dynamicTest(
                    "detailedPaths=" + detailedPaths,
                    () -> checkUnitLambdaReplacesAllWarmUpFlowsAndPaths(detailedPaths)));
  }

  private void checkUnitLambdaReplacesAllWarmUpFlowsAndPaths(boolean detailedPaths)
      throws Exception {
    // Economies of scale keep route 2 cheapest even when all demand moves there (cost 6).
    try (AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 2, new double[][] {{11, 1, 2, 10, 0.01}, {12, 1, 2, 12, -0.01}})) {
      project.execute("INSERT INTO mini_od VALUES (1,1,2,300),(2,1,2,300)");
      ObservedHybrid assignment = observed(project, 4, 1, detailedPaths);
      project.run(assignment, 2);
      assertEquals(List.of(1.0), assignment.lambdas);
      assertCompletion(assignment, AssignmentCompletion.Reason.MAX_ITERATIONS_REACHED, 1);
      assertEquals(0, forwardFlow(project, 11), 1e-9);
      assertEquals(600, forwardFlow(project, 12), 1e-9);
      assertOutput(project, 600, detailedPaths);
      assertEquals(0, project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE iteration<5"));
      assertEquals(
          600, project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE iteration=5"));
    }
  }

  @TestFactory
  Stream<DynamicTest> cancellationPreservesPreviousResultsAndAllowsRetry() {
    return Stream.of("warm-up", "FW routing", "FW line search")
        .flatMap(
            phase ->
                Stream.of(1, 4)
                    .flatMap(
                        threads ->
                            Stream.of(false, true)
                                .map(
                                    detailedPaths ->
                                        DynamicTest.dynamicTest(
                                            phase
                                                + ", threads="
                                                + threads
                                                + ", detailedPaths="
                                                + detailedPaths,
                                            () ->
                                                checkCancellation(
                                                    phase, threads, detailedPaths)))));
  }

  private void checkCancellation(String phase, int threads, boolean detailedPaths)
      throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 600)) {
      project.run(observed(project, 1, 0, true), 2);
      final List<List<String>> previous = results(project, true);
      final Set<Thread> workers = ConcurrentHashMap.newKeySet();
      final AtomicInteger warmUpJobs = new AtomicInteger();
      final AtomicBoolean frankWolfeStarted = new AtomicBoolean();
      final AtomicBoolean canceled = new AtomicBoolean();
      project.setProgressHandler(
          message -> {
            Thread worker = Thread.currentThread();
            if (worker instanceof AssignmentWorker || worker instanceof CostParserWorker) {
              workers.add(worker);
            }
            if (worker instanceof FrankWolfeAssignmentWorker) {
              frankWolfeStarted.set(true);
            }
            boolean stop =
                (phase.equals("warm-up")
                        && worker instanceof IncrementalAssignmentWorker
                        && warmUpJobs.incrementAndGet() > 2)
                    || (phase.equals("FW routing") && worker instanceof FrankWolfeAssignmentWorker)
                    || (phase.equals("FW line search")
                        && frankWolfeStarted.get()
                        && worker instanceof CostParserWorker);
            if (stop) {
              canceled.set(true);
            }
            return !stop;
          });
      project.runCancelled(observed(project, threads, 30, detailedPaths), 2);
      assertTrue(canceled.get(), "Cancellation must reach " + phase);
      assertEquals(previous, results(project, true));
      for (Thread worker : workers) {
        assertFalse(worker.isAlive(), "Canceled worker still running");
      }
      assertEquals(
          0,
          project.number(
              "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                  + "WHERE SUBSTRING(TABLE_NAME,1,3) IN ('NO_', 'NB_')"));
      project.setProgressHandler(null);
      ObservedHybrid retry = observed(project, threads, 30, detailedPaths);
      project.run(retry, 2);
      assertCompletion(retry, AssignmentCompletion.Reason.CONVERGED, 30);
      assertOutput(project, 600, detailedPaths);
    }
  }

  private static ObservedHybrid observed(
      AssignmentTestProject project, int threads, int iterations, boolean detailedPaths)
      throws Exception {
    AssignmentParameters parameters = congestedParameters(project, threads, iterations);
    parameters.setDetailedPaths(detailedPaths);
    return new ObservedHybrid(parameters, project.number("SELECT SUM(qty) FROM mini_od") / 2);
  }

  private static void assertWarmUp(ObservedHybrid assignment) {
    assertEquals(List.of(240.0, 240.0, 360.0, 420.0), assignment.warmUpFlows);
  }

  private static void assertCompletion(
      ObservedHybrid assignment, AssignmentCompletion.Reason reason, int maximum) {
    AssignmentCompletion completion = assignment.getCompletion();
    assertEquals(reason, completion.getReason());
    assertEquals(4, completion.getInitializationIterations());
    assertEquals(maximum, completion.getMaximumIterations());
    assertEquals(assignment.lambdas.size(), completion.getIterationsPerformed());
    assertEquals(4 + assignment.lambdas.size(), completion.getTotalIterationsPerformed());
    assertEquals(0.0001, completion.getConvergenceThreshold());
    if (reason == AssignmentCompletion.Reason.MAX_ITERATIONS_REACHED) {
      assertEquals(maximum, completion.getIterationsPerformed());
    } else {
      assertTrue(completion.getIterationsPerformed() > 0);
      assertTrue(completion.getIterationsPerformed() < maximum);
    }
  }

  private static void assertOutput(AssignmentTestProject project, int demand, boolean detailedPaths)
      throws Exception {
    for (int type :
        new int[] {VirtualLink.TYPE_LOAD, VirtualLink.TYPE_MOVE, VirtualLink.TYPE_UNLOAD}) {
      assertEquals(
          demand, project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + type), 0.002);
    }
    assertEquals(0, project.number("SELECT COUNT(*) FROM mini_vnet1 WHERE qty<0"));
    assertEquals(demand, project.number("SELECT SUM(qty) FROM mini_od"));
    assertSavedDemand(project, demand);
    assertEquals(0, project.number("SELECT COUNT(*) FROM mini_paths1_header WHERE qty<0"));
    if (detailedPaths) {
      for (int link : new int[] {11, 12}) {
        assertEquals(
            forwardFlow(project, link),
            project.number(
                "SELECT SUM(h.qty) "
                    + "FROM mini_paths1_header h JOIN mini_paths1_detail d ON h.pathidx=d.pathidx "
                    + "WHERE d.link="
                    + link),
            pathRoundingTolerance(project));
      }
    } else {
      assertTrue(JDBCUtils.tableExists("mini_paths1_header"));
      assertFalse(JDBCUtils.tableExists("mini_paths1_detail"));
    }
  }

  private static List<List<String>> results(AssignmentTestProject project, boolean detailedPaths)
      throws Exception {
    List<List<String>> rows =
        project.rows("SELECT * FROM mini_vnet1 ORDER BY node1,node2,link1,link2,vtype");
    rows.addAll(
        project.rows(
            "SELECT grp,iteration,qty,ldcost,ulcost,trcost,tpcost,stcost,swcost,mvcost "
                + "FROM mini_paths1_header ORDER BY grp,iteration,qty,mvcost"));
    if (detailedPaths) {
      rows.addAll(
          project.rows(
              "SELECT h.grp,h.iteration,h.qty,h.mvcost,d.link "
                  + "FROM mini_paths1_header h JOIN mini_paths1_detail d ON h.pathidx=d.pathidx "
                  + "ORDER BY h.grp,h.iteration,h.qty,h.mvcost,d.link"));
    }
    return rows;
  }

  /**
   * Observes real worker completion and volume updates without replacing routing or line search.
   */
  private static final class ObservedHybrid extends IncFrankWolfeAssignment {
    final List<Double> warmUpFlows = new ArrayList<>();
    final List<Double> lambdas = new ArrayList<>();
    private final double groupDemand;

    ObservedHybrid(AssignmentParameters parameters, double groupDemand) {
      super(parameters);
      this.groupDemand = groupDemand;
    }

    @Override
    protected boolean waitForAssignmentWorkers() {
      boolean completed = super.waitForAssignmentWorkers();
      if (completed
          && assignmentWorkers != null
          && assignmentWorkers[0] instanceof IncrementalAssignmentWorker
          && !assignmentWorkers[0].isCancelled()) {
        double flow = 0;
        for (VirtualLink link : links()) {
          if (link.getType() == VirtualLink.TYPE_MOVE
              && link.getBeginVirtualNode().getRealNodeId(false) == 1
              && link.getBeginVirtualNode().getRealLinkId() == 11) {
            for (byte group = 0; group < virtualNet.getGroups().length; group++) {
              flow += link.getCurrentVolume(group);
            }
          }
        }
        warmUpFlows.add(flow);
      }
      return completed;
    }

    @Override
    public boolean splitVolumes(double lambda) {
      assertTrue(lambda >= 0 && lambda <= 1, "Line search must stay in [0,1]");
      assertConservation(true);
      assertConservation(false);
      lambdas.add(lambda);
      boolean completed = super.splitVolumes(lambda);
      assertConservation(false);
      for (VirtualLink link : links()) {
        for (byte group = 0; group < virtualNet.getGroups().length; group++) {
          assertEquals(0, link.getAuxiliaryVolume(group), 1e-12);
        }
      }
      return completed;
    }

    private void assertConservation(boolean auxiliary) {
      for (byte group = 0; group < virtualNet.getGroups().length; group++) {
        double[] totals = new double[3];
        for (VirtualLink link : links()) {
          double volume = auxiliary ? link.getAuxiliaryVolume(group) : link.getCurrentVolume(group);
          assertTrue(Double.isFinite(volume) && volume >= 0);
          if (link.getType() == VirtualLink.TYPE_LOAD) {
            totals[0] += volume;
          } else if (link.getType() == VirtualLink.TYPE_MOVE) {
            totals[1] += volume;
          } else if (link.getType() == VirtualLink.TYPE_UNLOAD) {
            totals[2] += volume;
          }
        }
        for (double total : totals) {
          assertEquals(groupDemand, total, 1e-9);
        }
      }
    }

    private List<VirtualLink> links() {
      List<VirtualLink> links = new ArrayList<>();
      for (VirtualNodeList nodeList : virtualNet.getVirtualNodeLists()) {
        for (VirtualNode node : nodeList.getVirtualNodeList()) {
          links.addAll(node.getVirtualLinkList());
        }
      }
      return links;
    }
  }
}
