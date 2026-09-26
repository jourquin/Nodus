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

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import java.nio.file.Path;

/** Shared parallel-route inputs and conservation checks for iterative assignments. */
final class ParallelRouteTestCase {
  private ParallelRouteTestCase() {}

  static AssignmentTestProject congestedProject(Path directory, int demand) throws Exception {
    AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 2, new double[][] {{11, 1, 2, 10, 0.01}, {12, 1, 2, 12, 0.01}});
    project.execute(
        "INSERT INTO mini_od VALUES (1,1,2," + demand / 2 + "),(2,1,2," + demand / 2 + ")");
    return project;
  }

  static AssignmentParameters congestedParameters(
      AssignmentTestProject project, int threads, int iterations) {
    AssignmentParameters parameters = project.parameters(threads);
    parameters.getCostFunctions().setProperty("mv.1,1", "BASECOST + SLOPE * VOLUME");
    parameters.getCostFunctions().setProperty("AVGLOAD.1,1", "1");
    parameters.setNbIterations(iterations);
    parameters.setPrecision(0.0001);
    return parameters;
  }

  static double forwardFlow(AssignmentTestProject project, int link) throws Exception {
    return project.number(
        "SELECT qty FROM mini_vnet1 WHERE link1="
            + link
            + " AND vtype="
            + VirtualLink.TYPE_MOVE
            + " AND ABS(node1)<ABS(node2)");
  }

  static double pathRoundingTolerance(AssignmentTestProject project) throws Exception {
    // Path quantities are NUMERIC(...,3) and get rounded at each SQL blending step.
    // Each row can therefore accumulate at most 0.0005 per iteration.
    return Math.max(
        0.002,
        0.0005
            * project.number("SELECT COUNT(*) FROM mini_paths1_header")
            * project.number("SELECT MAX(iteration) FROM mini_paths1_header"));
  }

  static void assertSavedDemand(AssignmentTestProject project, double demand) throws Exception {
    for (int type :
        new int[] {VirtualLink.TYPE_LOAD, VirtualLink.TYPE_UNLOAD, VirtualLink.TYPE_MOVE}) {
      assertEquals(
          demand, project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + type), 0.002);
    }
    final double rounding = pathRoundingTolerance(project);
    assertEquals(demand, project.number("SELECT SUM(qty) FROM mini_paths1_header"), rounding);
    assertEquals(
        demand / 2,
        project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE grp=1"),
        rounding / 2);
    assertEquals(
        demand / 2,
        project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE grp=2"),
        rounding / 2);
    assertEquals(
        0,
        project.number(
            "SELECT SUM(qty) FROM mini_vnet1 WHERE vtype="
                + VirtualLink.TYPE_MOVE
                + " AND ABS(node1)>ABS(node2)"),
        1e-12);
    assertEquals(demand, project.number("SELECT SUM(qty) FROM mini_od"));
  }
}
