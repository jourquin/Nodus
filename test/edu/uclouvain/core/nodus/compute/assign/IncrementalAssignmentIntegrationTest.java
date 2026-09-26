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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Three triangular increments load 300, 200 and 100 units, recomputing congestion between them. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class IncrementalAssignmentIntegrationTest {
  @TempDir Path directory;

  @Test
  void incrementsConserveDemandAndRecomputeCostsBeforeChoosingEachRoute() throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 600)) {
      Assignment assignment = new IncrementalAssignment(congestedParameters(project, 4, 3));
      project.run(assignment, 2);
      assertIncrements(project);
      assertEquals(
          AssignmentCompletion.Reason.FIXED_ITERATIONS_COMPLETED,
          assignment.getCompletion().getReason());
      assertEquals(3, assignment.getCompletion().getIterationsPerformed());
      assertFalse(assignment.getCompletion().hasFinalRelativeGap());
    }
  }

  @Test
  void workerCountsAndRepeatedRunsHaveTheSameFlowsAndPathQuantities() throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 600)) {
      project.run(new IncrementalAssignment(congestedParameters(project, 1, 3)), 2);
      assertIncrements(project);
      final List<List<String>> expected = results(project);
      project.run(new IncrementalAssignment(congestedParameters(project, 4, 3)), 2);
      assertIncrements(project);
      assertEquals(expected, results(project));
      project.run(new IncrementalAssignment(congestedParameters(project, 4, 3)), 2);
      assertIncrements(project);
      assertEquals(expected, results(project));
    }
  }

  @Test
  void oneIncrementMatchesAllOrNothingAssignment() throws Exception {
    try (AssignmentTestProject project = congestedProject(directory, 600)) {
      project.run(new AllOrNothingAssignment(congestedParameters(project, 1, 1)), 2);
      final List<List<String>> expected = results(project);
      project.run(new IncrementalAssignment(congestedParameters(project, 4, 1)), 2);
      assertEquals(expected, results(project));
      assertEquals(600, forwardFlow(project, 11), 1e-12);
      assertEquals(0, forwardFlow(project, 12), 1e-12);
      assertSavedDemand(project, 600);
    }
  }

  private static void assertIncrements(AssignmentTestProject project) throws Exception {
    assertEquals(400, forwardFlow(project, 11), 1e-9);
    assertEquals(200, forwardFlow(project, 12), 1e-9);
    assertSavedDemand(project, 600);
    assertEquals(
        List.of(
            List.of("1", "300.000", "10.000", "11"),
            List.of("2", "200.000", "12.000", "12"),
            List.of("3", "100.000", "13.000", "11")),
        project.rows(
            "SELECT h.iteration,SUM(h.qty),h.mvcost,d.link FROM mini_paths1_header h "
                + "JOIN mini_paths1_detail d ON h.pathidx=d.pathidx "
                + "GROUP BY h.iteration,h.mvcost,d.link ORDER BY h.iteration"));
    assertEquals(6, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
    assertEquals(6700, project.number("SELECT SUM(qty*mvcost) FROM mini_paths1_header"), 1e-9);
  }

  private static List<List<String>> results(AssignmentTestProject project) throws Exception {
    List<List<String>> results =
        project.rows("SELECT * FROM mini_vnet1 ORDER BY node1,node2,link1,link2");
    results.addAll(
        project.rows(
            "SELECT h.grp,h.qty,h.mvcost,d.link FROM mini_paths1_header h "
                + "JOIN mini_paths1_detail d ON h.pathidx=d.pathidx "
                + "ORDER BY h.grp,h.qty,h.mvcost,d.link"));
    return results;
  }
}
