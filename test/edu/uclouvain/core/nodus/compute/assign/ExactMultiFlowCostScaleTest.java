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
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real OD loading, costs, Exact MF routing, modal shares and SQL outputs at small cost scales. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("NodusC.useCompactShortestPaths")
@Timeout(30)
class ExactMultiFlowCostScaleTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> arbitraryCostsAndZeroCostMovementProduceTheExpectedRoutesAndFlows() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean compact : new boolean[] {false, true}) {
        for (int threads : new int[] {1, 4}) {
          for (int alternatives : new int[] {1, 2}) {
            tests.add(
                dynamicTest(
                    "HSQLDB="
                        + hsql
                        + "/compact="
                        + compact
                        + "/threads="
                        + threads
                        + "/alternatives="
                        + alternatives,
                    () -> checkAssignment(hsql, compact, threads, alternatives)));
          }
        }
      }
    }
    return tests;
  }

  private void checkAssignment(boolean hsql, boolean compact, int threads, int alternatives)
      throws Exception {
    final boolean previous = NodusC.useCompactShortestPaths;
    try (AssignmentTestProject project =
        new AssignmentTestProject(
            directory,
            3,
            new double[][] {{11, 1, 3, 0.5, 0}, {12, 1, 2, 0.1, 0}, {13, 2, 3, 0.1, 0}},
            hsql)) {
      NodusC.useCompactShortestPaths = compact;
      // The cheapest route initially travels away from the goal. Geographic distance has
      // no prescribed relationship to these user-defined costs.
      final double[] longitudes = {0, -10, 10};
      for (int node = 0; node < 3; node++) {
        ((OMPoint) project.nodes.getEsriGraphicList().getOMGraphicAt(node))
            .set(0, longitudes[node]);
      }
      geometry(project, 0, longitudes[0], longitudes[2]);
      geometry(project, 1, longitudes[0], longitudes[1]);
      geometry(project, 2, longitudes[1], longitudes[2]);
      project.execute("INSERT INTO mini_od VALUES(1,1,3,210),(2,1,3,420)");
      final AssignmentParameters parameters = project.parameters(threads);
      parameters.setNbIterations(alternatives);
      parameters.setModalSplitMethodName("Proportional");
      parameters.setCostMarkup(5);
      parameters.setMaxDetourRatio(0);

      project.run(new ExactMFAssignment(parameters), 2);
      assertOutputs(project, alternatives, 0.5, 0.2);

      // A zero-cost movement between distinct points must remain eligible. Re-running must
      // replace earlier results and retain all demand, including with concurrent group jobs.
      project.links.getModel().setValueAt(0.0, 1, 9);
      project.run(new ExactMFAssignment(parameters), 2);
      assertOutputs(project, alternatives, 0.5, 0.1);

      // Changing the cost unit or function can reverse which route is cheapest.
      project.links.getModel().setValueAt(0.05, 0, 9);
      project.run(new ExactMFAssignment(parameters), 2);
      assertOutputs(project, alternatives, 0.05, 0.1);
      assertEquals(630, project.number("SELECT SUM(qty) FROM mini_od"));
    } finally {
      NodusC.useCompactShortestPaths = previous;
    }
  }

  private static void geometry(AssignmentTestProject project, int row, double from, double to) {
    ((OMPoly) project.links.getEsriGraphicList().getOMGraphicAt(row))
        .setLocation(new double[] {0, from, 0, to}, OMGraphic.DECIMAL_DEGREES);
  }

  private static void assertOutputs(
      AssignmentTestProject project, int alternatives, double directCost, double detourCost)
      throws Exception {
    assertEquals(alternatives * 2, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
    for (int group : new int[] {1, 2}) {
      final double demand = 210 * group;
      final double direct =
          alternatives == 1
              ? directCost < detourCost ? demand : 0
              : demand * detourCost / (directCost + detourCost);
      final double detour = demand - direct;
      assertEquals(
          demand,
          project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE grp=" + group),
          0.001);
      assertRoute(project, group, 11, direct, directCost);
      assertRoute(project, group, 12, detour, detourCost);
      assertRoute(project, group, 13, detour, detourCost);
      assertEquals(
          direct * directCost + detour * detourCost,
          project.number("SELECT SUM(qty*mvcost) FROM mini_paths1_header WHERE grp=" + group),
          0.001);
    }
    assertEquals(
        630,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_LOAD),
        0.001);
    assertEquals(
        630,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_UNLOAD),
        0.001);
  }

  private static void assertRoute(
      AssignmentTestProject project, int group, int link, double quantity, double cost)
      throws Exception {
    final String route =
        " FROM mini_paths1_header h JOIN mini_paths1_detail d "
            + "ON h.pathidx=d.pathidx WHERE h.grp="
            + group
            + " AND d.link="
            + link;
    assertEquals(quantity, project.number("SELECT SUM(h.qty)" + route), 0.001);
    assertEquals(quantity == 0 ? 0 : 1, project.number("SELECT COUNT(*)" + route));
    if (quantity > 0) {
      assertEquals(cost, project.number("SELECT h.mvcost" + route), 0.001);
    }
    assertEquals(
        quantity,
        project.number(
            "SELECT SUM(qty"
                + group
                + ") FROM mini_vnet1 WHERE vtype="
                + VirtualLink.TYPE_MOVE
                + " AND link1="
                + link),
        0.001);
  }
}
