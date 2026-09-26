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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Parallel routes: c1=10+q1/100, c2=12+q2/100, q1+q2=1000 => (600,400), cost 16. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class EquilibriumAssignmentIntegrationTest {
  @TempDir Path directory;

  @Test
  void frankWolfeFindsKnownEquilibriumAndAgreesAcrossWorkerCounts() throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 1000)) {
      AssignmentParameters serial = congestedParameters(project, 1, 30);
      Assignment first = new FrankWolfeAssignment(serial);
      project.run(first, 2);
      assertConverged(first);
      // PCUs round upward independently for each of the two commodity groups.
      assertEquilibrium(project, 2.1);
      final List<List<String>> flows =
          project.rows("SELECT * FROM mini_vnet1 ORDER BY node1,node2,link1,link2");
      Assignment parallel = new FrankWolfeAssignment(congestedParameters(project, 4, 30));
      project.run(parallel, 2);
      assertConverged(parallel);
      assertEquilibrium(project, 2.1);
      assertEquals(
          first.getCompletion().getIterationsPerformed(),
          parallel.getCompletion().getIterationsPerformed());
      assertEquals(
          flows, project.rows("SELECT * FROM mini_vnet1 ORDER BY node1,node2,link1,link2"));
    }
  }

  @Test
  void successiveAveragesApproachesKnownEquilibrium() throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 1000)) {
      AssignmentParameters parameters = congestedParameters(project, 4, 400);
      parameters.setPrecision(0.005);
      Assignment assignment = new MSAAssignment(parameters);
      project.run(assignment, 2);
      assertConverged(assignment);
      // MSA oscillates around the solution by at most one update, plus two rounded group PCUs.
      double tolerance = 1000.0 / assignment.getCompletion().getIterationsPerformed() + 2;
      assertEquilibrium(project, tolerance);
    }
  }

  @Test
  void iterationLimitIsReportedWithoutClaimingConvergence() throws Exception {
    final boolean original = NodusC.displayAssignmentInformationDialogs;
    try (AssignmentTestProject project = congestedProject(directory, 1000)) {
      // Disabling routine notifications must still report a failure to converge.
      NodusC.displayAssignmentInformationDialogs = false;
      Assignment assignment = new FrankWolfeAssignment(congestedParameters(project, 4, 1));
      project.run(assignment, 2);
      assertEquals(
          AssignmentCompletion.Reason.MAX_ITERATIONS_REACHED,
          assignment.getCompletion().getReason());
      assertEquals(1, assignment.getCompletion().getIterationsPerformed());
      assertFalse(assignment.getCompletion().hasFinalRelativeGap());
      assertEquals(1000, forwardFlow(project, 11), 1e-9);
      assertEquals(0, forwardFlow(project, 12), 1e-9);
      assertSavedDemand(project, 1000);
    } finally {
      NodusC.displayAssignmentInformationDialogs = original;
    }
  }

  @Test
  void uncongestedSuccessiveAveragesConvergesAfterTwoIterations() throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 1000)) {
      AssignmentParameters parameters = congestedParameters(project, 4, 20);
      parameters.getCostFunctions().setProperty("mv.1,1", "BASECOST");
      Assignment assignment = new MSAAssignment(parameters);
      project.run(assignment, 2);
      assertConverged(assignment);
      assertEquals(2, assignment.getCompletion().getIterationsPerformed());
      assertEquals(0, assignment.getCompletion().getFinalRelativeGap(), 1e-12);
      assertEquals(1000, forwardFlow(project, 11), 1e-9);
      assertEquals(0, forwardFlow(project, 12), 1e-9);
      assertSavedDemand(project, 1000);
    }
  }

  private static void assertConverged(Assignment assignment) {
    AssignmentCompletion completion = assignment.getCompletion();
    assertNotNull(completion);
    assertEquals(AssignmentCompletion.Reason.CONVERGED, completion.getReason());
    assertTrue(completion.getIterationsPerformed() >= 2);
    assertTrue(completion.getIterationsPerformed() < completion.getMaximumIterations());
    assertTrue(completion.getFinalRelativeGap() < completion.getConvergenceThreshold());
  }

  private static void assertEquilibrium(AssignmentTestProject project, double tolerance)
      throws Exception {
    assertEquals(600, forwardFlow(project, 11), tolerance);
    assertEquals(400, forwardFlow(project, 12), tolerance);
    assertEquals(1000, forwardFlow(project, 11) + forwardFlow(project, 12), 0.002);
    for (int link : new int[] {11, 12}) {
      assertEquals(
          16,
          project.number(
              "SELECT ucost FROM mini_vnet1 WHERE link1="
                  + link
                  + " AND vtype="
                  + VirtualLink.TYPE_MOVE
                  + " AND ABS(node1)<ABS(node2)"),
          tolerance * 0.01 + 0.02);
      assertEquals(
          forwardFlow(project, link),
          project.number(
              "SELECT SUM(h.qty) FROM mini_paths1_header h "
                  + "JOIN mini_paths1_detail d ON h.pathidx=d.pathidx WHERE d.link="
                  + link),
          pathRoundingTolerance(project));
    }
    assertSavedDemand(project, 1000);
  }
}
