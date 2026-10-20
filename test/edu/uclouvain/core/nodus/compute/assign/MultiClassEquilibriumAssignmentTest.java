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

import static edu.uclouvain.core.nodus.compute.assign.ParallelRouteTestCase.forwardFlow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Independent two-class Wardrop reference: 800 units of A and 200 of B share two routes. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("NodusC.useCompactShortestPaths")
@Timeout(30)
class MultiClassEquilibriumAssignmentTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> differentClassCostsConvergeToTheCorrectEquilibrium() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean hybrid : new boolean[] {false, true}) {
        for (boolean sparse : new boolean[] {false, true}) {
          for (boolean compact : new boolean[] {false, true}) {
            tests.add(
                dynamicTest(
                    "hsql="
                        + hsql
                        + "/hybrid="
                        + hybrid
                        + "/sparse="
                        + sparse
                        + "/compact="
                        + compact,
                    () -> checkEquilibrium(hsql, hybrid, sparse, compact)));
          }
        }
      }
    }
    return tests;
  }

  private void checkEquilibrium(boolean hsql, boolean hybrid, boolean sparse, boolean compact)
      throws Exception {
    final boolean previousGraph = NodusC.useCompactShortestPaths;
    final int first = sparse ? 2 : 0;
    final int second = sparse ? 7 : 1;
    try (AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 2, new double[][] {{11, 1, 2, 10, 0.01}, {12, 1, 2, 12, 0.01}}, hsql)) {
      NodusC.useCompactShortestPaths = compact;
      project.execute("ALTER TABLE mini_od ADD class INTEGER DEFAULT 0");
      for (int group : new int[] {1, 2}) {
        project.execute(
            "INSERT INTO mini_od VALUES ("
                + group
                + ",1,2,400,"
                + first
                + "),("
                + group
                + ",1,2,100,"
                + second
                + ")");
      }
      List<List<String>> serial = null;
      for (int threads : new int[] {1, 4, 4}) {
        final AssignmentParameters parameters = project.parameters(threads);
        parameters.setNbIterations(40);
        parameters.setPrecision(0.0001);
        parameters.getCostFunctions().setProperty("AVGLOAD.1,1", "1");
        // Unused classes must not enter the derivative, even if they have very different costs.
        parameters.getCostFunctions().setProperty("mv.1,1", "1000 + 7 * BASECOST");
        parameters.getCostFunctions().setProperty("mv.1,1-" + first, "BASECOST + SLOPE * VOLUME");
        parameters
            .getCostFunctions()
            .setProperty("mv.1,1-" + second, "20 - BASECOST + SLOPE * VOLUME");
        parameters.setDetailedPaths(threads == 1);
        final ClassSnapshot classFlows = new ClassSnapshot(first, second);
        final Assignment assignment = observed(parameters, hybrid ? "hybrid" : "FW", classFlows);
        project.run(assignment, 2);
        assertEquals(AssignmentCompletion.Reason.CONVERGED, assignment.getCompletion().getReason());
        // A: c1=10+q1/100, c2=12+q2/100. B: c1=10+q1/100, c2=8+q2/100.
        // At equilibrium A uses (600,200), B uses (0,200), hence total (600,400).
        // Each group's vehicle conversion rounds independently, within two PCUs overall.
        assertEquals(600, forwardFlow(project, 11), 2.1);
        assertEquals(400, forwardFlow(project, 12), 2.1);
        assertEquals(1000, forwardFlow(project, 11) + forwardFlow(project, 12), 0.002);
        final double costA1 = 10 + forwardFlow(project, 11) / 100;
        final double costA2 = 12 + forwardFlow(project, 12) / 100;
        assertEquals(costA1, costA2, 0.05);
        assertTrue(8 + forwardFlow(project, 12) / 100 < costA1);
        // The combined table must average class costs, rather than use B's last parsed costs.
        for (int group : new int[] {1, 2}) {
          assertEquals(
              16,
              project.number(
                  "SELECT ucost"
                      + group
                      + " FROM mini_vnet1 WHERE link1=11 AND vtype=0 AND ABS(node1)<ABS(node2)"),
              0.05);
          assertEquals(
              14,
              project.number(
                  "SELECT ucost"
                      + group
                      + " FROM mini_vnet1 WHERE link1=12 AND vtype=0 AND ABS(node1)<ABS(node2)"),
              0.05);
        }
        classFlows.assertEquilibrium(1.1);
        assertEquals(1000, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.05);
        if (threads == 1) {
          for (int link : new int[] {11, 12}) {
            assertEquals(
                forwardFlow(project, link),
                project.number(
                    "SELECT SUM(h.qty) FROM mini_paths1_header h JOIN mini_paths1_detail d "
                        + "ON h.pathidx=d.pathidx WHERE d.link="
                        + link),
                0.05);
          }
        }
        final List<List<String>> snapshot =
            project.rows("SELECT * FROM mini_vnet1 ORDER BY node1,node2,link1,link2,vtype");
        snapshot.addAll(
            project.rows(
                "SELECT grp,iteration,qty,mvcost FROM mini_paths1_header "
                    + "ORDER BY grp,iteration,qty,mvcost"));
        if (serial == null) {
          serial = snapshot;
        } else {
          assertEquals(serial, snapshot);
        }
      }
    } finally {
      NodusC.useCompactShortestPaths = previousGraph;
    }
  }

  @TestFactory
  List<DynamicTest> successiveAveragesRetainsClassConservationAndApproachesTheSameEquilibrium() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "hsql=" + hsql,
              () -> {
                try (AssignmentTestProject project =
                    new AssignmentTestProject(
                        directory,
                        2,
                        new double[][] {{11, 1, 2, 10, 0.01}, {12, 1, 2, 12, 0.01}},
                        hsql)) {
                  project.execute("ALTER TABLE mini_od ADD class INTEGER DEFAULT 0");
                  project.execute("INSERT INTO mini_od VALUES (1,1,2,800,2),(1,1,2,200,7)");
                  final AssignmentParameters parameters = project.parameters(1);
                  parameters.setNbIterations(400);
                  parameters.setPrecision(0.005);
                  parameters.setDetailedPaths(false);
                  parameters.getCostFunctions().setProperty("AVGLOAD.1,1", "1");
                  parameters
                      .getCostFunctions()
                      .setProperty("mv.1,1-2", "BASECOST + SLOPE * VOLUME");
                  parameters
                      .getCostFunctions()
                      .setProperty("mv.1,1-7", "20 - BASECOST + SLOPE * VOLUME");
                  final ClassSnapshot flows = new ClassSnapshot(2, 7);
                  final Assignment assignment = observed(parameters, "MSA", flows);
                  project.run(assignment, 1);
                  assertEquals(
                      AssignmentCompletion.Reason.CONVERGED,
                      assignment.getCompletion().getReason());
                  final double tolerance =
                      1000.0 / assignment.getCompletion().getIterationsPerformed() + 1;
                  flows.assertEquilibrium(tolerance);
                  assertEquals(600, forwardFlow(project, 11), tolerance);
                  assertEquals(400, forwardFlow(project, 12), tolerance);
                  assertEquals(
                      1000, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.5);
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> cancellationDuringFinalClassCostsPreservesPreviousOutputAndAllowsRetry() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean hybrid : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                "hsql=" + hsql + "/hybrid=" + hybrid,
                () -> {
                  try (AssignmentTestProject project =
                      new AssignmentTestProject(
                          directory,
                          2,
                          new double[][] {{11, 1, 2, 10, 0.01}, {12, 1, 2, 12, 0.01}},
                          hsql)) {
                    project.execute("ALTER TABLE mini_od ADD class INTEGER DEFAULT 0");
                    project.execute("INSERT INTO mini_od VALUES (1,1,2,800,2),(1,1,2,200,7)");
                    final AssignmentParameters parameters = project.parameters(4);
                    parameters.setNbIterations(40);
                    parameters.setPrecision(0.0001);
                    parameters.getCostFunctions().setProperty("AVGLOAD.1,1", "1");
                    parameters
                        .getCostFunctions()
                        .setProperty("mv.1,1-2", "BASECOST + SLOPE * VOLUME");
                    parameters
                        .getCostFunctions()
                        .setProperty("mv.1,1-7", "20 - BASECOST + SLOPE * VOLUME");
                    final ClassSnapshot flows = new ClassSnapshot(2, 7);
                    final Assignment assignment =
                        observed(parameters, hybrid ? "hybrid" : "FW", flows);
                    project.run(assignment, 1);
                    final List<List<String>> previous = project.snapshot();
                    flows.converged = false;
                    project.setProgressHandler(message -> !flows.converged);
                    project.runCancelled(assignment, 1);
                    assertTrue(
                        flows.converged, "Cancellation must occur in final class cost evaluation");
                    assertEquals(previous, project.snapshot());
                    project.setProgressHandler(null);
                    project.run(assignment, 1);
                    flows.assertEquilibrium(1.1);
                    assertEquals(previous, project.snapshot());
                  }
                }));
      }
    }
    return tests;
  }

  @Test
  void oppositeClassShiftsCannotProduceFalseConvergenceFromUnchangedGroupTotals() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      final AssignmentParameters parameters = project.parameters(1);
      final VirtualNetwork network = new VirtualNetwork(parameters);
      try {
        assertTrue(network.generate());
        assertTrue(new ODReader(parameters).loadDemand(network));
        final VirtualLink link = links(network).get(0);
        link.initializeEquilibriumClasses(8);
        link.addEquilibriumVolume((byte) 0, (byte) 2, 80);
        link.addEquilibriumVolume((byte) 0, (byte) 7, 20);
        link.addAuxiliaryVolume((byte) 0, (byte) 2, 20);
        link.addAuxiliaryVolume((byte) 0, (byte) 7, 80);
        link.combineVolumes((byte) 0, 1);
        final FrankWolfeAssignment assignment =
            new FrankWolfeAssignment(parameters) {
              {
                virtualNet = network;
              }
            };
        assertEquals(100, link.getCurrentVolume((byte) 0));
        assertEquals(100, link.getPreviousVolume((byte) 0));
        assertFalse(assignment.stopRule(2, 0.0001));
        link.combineVolumes((byte) 0, 0);
        assertTrue(assignment.stopRule(3, 0.0001));
      } finally {
        network.dispose();
      }
    }
  }

  private static Assignment observed(
      AssignmentParameters parameters, String kind, ClassSnapshot snapshot) {
    switch (kind) {
      case "hybrid":
        return new IncFrankWolfeAssignment(parameters) {
          @Override
          public boolean stopRule(int iteration, double precision) {
            snapshot.read(virtualNet);
            snapshot.converged = super.stopRule(iteration, precision);
            return snapshot.converged;
          }
        };
      case "MSA":
        return new MSAAssignment(parameters) {
          @Override
          public boolean stopRule(int iteration, double precision) {
            snapshot.read(virtualNet);
            snapshot.converged = super.stopRule(iteration, precision);
            return snapshot.converged;
          }
        };
      default:
        return new FrankWolfeAssignment(parameters) {
          @Override
          public boolean stopRule(int iteration, double precision) {
            snapshot.read(virtualNet);
            snapshot.converged = super.stopRule(iteration, precision);
            return snapshot.converged;
          }
        };
    }
  }

  private static List<VirtualLink> links(VirtualNetwork network) {
    final List<VirtualLink> links = new ArrayList<>();
    for (VirtualNodeList nodeList : network.getVirtualNodeLists()) {
      for (VirtualNode node : nodeList.getVirtualNodeList()) {
        links.addAll(node.getVirtualLinkList());
      }
    }
    return links;
  }

  /** Reads class flows before normal coordinator disposal, without changing routing or stopping. */
  private static final class ClassSnapshot {
    final int first;
    final int second;
    final Map<String, Double> moving = new TreeMap<>();
    int groups;
    volatile boolean converged;

    ClassSnapshot(int first, int second) {
      this.first = first;
      this.second = second;
    }

    void read(VirtualNetwork network) {
      groups = network.getGroups().length;
      moving.clear();
      final Map<String, Double> totals = new TreeMap<>();
      for (VirtualLink link : links(network)) {
        for (byte group = 0; group < groups; group++) {
          double sum = 0;
          for (int odClass : new int[] {first, second}) {
            final double value = link.getEquilibriumVolume(group, (byte) odClass);
            assertTrue(Double.isFinite(value) && value >= 0);
            sum += value;
            final String key = group + ":" + odClass + ":" + link.getType();
            totals.merge(key, value, Double::sum);
            if (link.getType() == VirtualLink.TYPE_MOVE && link.getUpStream()) {
              moving.put(
                  group + ":" + odClass + ":" + link.getBeginVirtualNode().getRealLinkId(), value);
            }
          }
          assertEquals(link.getCurrentVolume(group), sum, 1e-8);
        }
      }
      for (byte group = 0; group < groups; group++) {
        for (int type :
            new int[] {VirtualLink.TYPE_LOAD, VirtualLink.TYPE_MOVE, VirtualLink.TYPE_UNLOAD}) {
          assertEquals(800.0 / groups, totals.get(group + ":" + first + ":" + type), 1e-8);
          assertEquals(200.0 / groups, totals.get(group + ":" + second + ":" + type), 1e-8);
        }
      }
    }

    void assertEquilibrium(double tolerance) {
      for (byte group = 0; group < groups; group++) {
        assertEquals(600.0 / groups, moving.get(group + ":" + first + ":11"), tolerance);
        assertEquals(200.0 / groups, moving.get(group + ":" + first + ":12"), tolerance);
        assertEquals(0, moving.get(group + ":" + second + ":11"), tolerance);
        assertEquals(200.0 / groups, moving.get(group + ":" + second + ":12"), tolerance);
      }
    }
  }
}
