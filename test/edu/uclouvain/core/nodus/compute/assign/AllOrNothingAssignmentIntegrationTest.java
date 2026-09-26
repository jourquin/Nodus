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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Complete assignments through real demand, virtual-network, cost, worker and SQL output code.
 * Nodes A=1, B=2, C=3 and D=4 form two bidirectional routes: A-B-D costs 2+3, A-C-D costs 4+4.
 * Demand is 100 from A to D (group 1) and 40 from B to D (group 2).
 */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class AllOrNothingAssignmentIntegrationTest {
  @TempDir Path directory;

  @Test
  void fourNodeAssignmentMatchesHandCalculatedPathsFlowsAndCosts() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      project.runAssignment(1);
      assertBaseline(project);
    }
  }

  @Test
  void concurrentWorkersProduceTheSameResultsAsOneWorker() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      project.runAssignment(1);
      assertBaseline(project);
      final List<List<String>> serial = project.snapshot();
      project.runAssignment(4);
      assertBaseline(project);
      assertEquals(2, project.panel.workers.size(), "Both commodity jobs must run concurrently");
      assertEquals(serial, project.snapshot());
    }
  }

  @Test
  void closingBToDReroutesBothDemands() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
      project.runAssignment(4);
      assertEquals(2, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
      assertEquals(8, project.number("SELECT mvcost FROM mini_paths1_header WHERE grp=1"));
      assertEquals(10, project.number("SELECT mvcost FROM mini_paths1_header WHERE grp=2"));
      assertEquals(
          List.of(
              List.of("1", "1", "4", "13", "1", "1"),
              List.of("1", "1", "4", "14", "1", "1"),
              List.of("2", "2", "4", "-11", "1", "1"),
              List.of("2", "2", "4", "13", "1", "1"),
              List.of("2", "2", "4", "14", "1", "1")),
          project.pathDetails());
      assertFlow(project, 11, 0, 0, 2, true);
      assertFlow(project, 11, 0, 40, 2, false);
      assertFlow(project, 13, 100, 40, 4);
      assertFlow(project, 14, 100, 40, 4);
      assertEquals(
          0,
          project.number(
              "SELECT COUNT(*) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_MOVE
                  + " AND link1=12"));
      assertConservation(project, 140, 1200);
    }
  }

  @Test
  void isolatingBLeavesItsDemandUnassignedWhileAReroutes() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      project.links.getModel().setValueAt(0.0, 0, NodusC.DBF_IDX_ENABLED);
      project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
      project.runAssignment(4);
      assertEquals(1, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
      assertEquals(100, project.number("SELECT SUM(qty) FROM mini_paths1_header"));
      assertEquals(8, project.number("SELECT mvcost FROM mini_paths1_header WHERE grp=1"));
      assertEquals(0, project.number("SELECT COUNT(*) FROM mini_paths1_header WHERE grp=2"));
      assertEquals(
          List.of(List.of("1", "1", "4", "13", "1", "1"), List.of("1", "1", "4", "14", "1", "1")),
          project.pathDetails());
      assertFlow(project, 13, 100, 0, 4);
      assertFlow(project, 14, 100, 0, 4);
      assertEquals(
          0,
          project.number(
              "SELECT COUNT(*) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_MOVE
                  + " AND link1 IN (11,12)"));
      assertConservation(project, 100, 800);
      assertEquals(140, project.number("SELECT SUM(qty) FROM mini_od"));
    }
  }

  @Test
  void repeatingTheSameAssignmentReplacesOutputsAndDoesNotAccumulateFlow() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      project.runAssignment(4);
      assertBaseline(project);
      final List<List<String>> first = project.snapshot();
      project.runAssignment(4);
      assertBaseline(project);
      assertEquals(first, project.snapshot());
    }
  }

  private static void assertBaseline(AssignmentTestProject project) throws Exception {
    assertEquals(
        List.of(
            List.of("1", "1", "4", "100.000", "5.000"), List.of("2", "2", "4", "40.000", "3.000")),
        project.rows("SELECT grp, org, dst, qty, mvcost FROM mini_paths1_header ORDER BY grp"));
    assertEquals(
        List.of(
            List.of("1", "1", "4", "11", "1", "1"),
            List.of("1", "1", "4", "12", "1", "1"),
            List.of("2", "2", "4", "12", "1", "1")),
        project.pathDetails());
    assertEquals(
        0,
        project.number(
            "SELECT SUM(ldcost+ulcost+trcost+tpcost+stcost+swcost) " + "FROM mini_paths1_header"));
    assertFlow(project, 11, 100, 0, 2);
    assertFlow(project, 12, 100, 40, 3);
    assertFlow(project, 13, 0, 0, 4);
    assertFlow(project, 14, 0, 0, 4);
    assertConservation(project, 140, 620);
    assertEquals(140, project.number("SELECT SUM(qty) FROM mini_od"));
  }

  private static void assertFlow(
      AssignmentTestProject project, int link, double group1, double group2, double cost)
      throws Exception {
    assertFlow(project, link, group1, group2, cost, true);
    assertFlow(project, link, 0, 0, cost, false);
  }

  private static void assertFlow(
      AssignmentTestProject project,
      int link,
      double group1,
      double group2,
      double cost,
      boolean forward)
      throws Exception {
    String moving =
        " FROM mini_vnet1 WHERE vtype="
            + VirtualLink.TYPE_MOVE
            + " AND link1="
            + link
            + (forward ? " AND ABS(node1)<ABS(node2)" : " AND ABS(node1)>ABS(node2)");
    assertEquals(1, project.number("SELECT COUNT(*)" + moving));
    assertEquals(group1, project.number("SELECT qty1" + moving));
    assertEquals(group2, project.number("SELECT qty2" + moving));
    assertEquals(group1 + group2, project.number("SELECT qty" + moving));
    assertEquals((group1 + group2) / 10, project.number("SELECT veh" + moving));
    assertEquals(cost, project.number("SELECT ucost1" + moving));
    assertEquals(cost, project.number("SELECT ucost2" + moving));
  }

  private static void assertConservation(AssignmentTestProject project, double demand, double cost)
      throws Exception {
    assertEquals(
        demand,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_LOAD));
    assertEquals(
        demand,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_UNLOAD));
    assertEquals(
        cost,
        project.number(
            "SELECT SUM(qty*ucost) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_MOVE));
    assertEquals(
        cost,
        project.number(
            "SELECT SUM(qty*(ldcost+ulcost+trcost+tpcost+stcost+swcost+mvcost)) "
                + "FROM mini_paths1_header"));
    // Every internal node conserves flow after accounting for loading and unloading there.
    for (int node = 1; node <= 4; node++) {
      String at = " FROM mini_vnet1 WHERE ABS(node1)=" + node + " AND vtype=";
      double departing = project.number("SELECT SUM(qty)" + at + VirtualLink.TYPE_MOVE);
      double arriving =
          project.number(
              "SELECT SUM(qty) FROM mini_vnet1 WHERE ABS(node2)="
                  + node
                  + " AND vtype="
                  + VirtualLink.TYPE_MOVE);
      double loaded = project.number("SELECT SUM(qty)" + at + VirtualLink.TYPE_LOAD);
      double unloaded = project.number("SELECT SUM(qty)" + at + VirtualLink.TYPE_UNLOAD);
      assertEquals(arriving + loaded, departing + unloaded, "Flow balance at node " + node);
    }
  }
}
