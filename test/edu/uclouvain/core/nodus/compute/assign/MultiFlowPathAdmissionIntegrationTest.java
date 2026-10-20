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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Complete fast/exact multi-flow assignments with independently known feasible route sets. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class MultiFlowPathAdmissionIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> detourBoundaryIsInclusiveAndZeroDisablesTheLimit() {
    return Stream.of(false, true)
        .flatMap(
            exact ->
                Stream.of(0.0, Math.nextDown(2.0), 2.0, Math.nextUp(2.0))
                    .map(
                        ratio ->
                            dynamicTest(
                                "exact=" + exact + ", ratio=" + ratio,
                                () -> {
                                  try (AssignmentTestProject project = parallelProject(false)) {
                                    AssignmentParameters parameters = parameters(project, 4, 8);
                                    parameters.setMaxDetourRatio(ratio);
                                    project.run(assignment(parameters, exact), 2);
                                    boolean both = ratio == 0 || ratio >= 2;
                                    assertParallelRoutes(project, both);
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> detourReferenceModeChangesOnlyTheLengthBenchmark() {
    return Stream.of(false, true)
        .flatMap(
            exact ->
                Stream.of(-1, 1, 2)
                    .map(
                        reference ->
                            dynamicTest(
                                "exact=" + exact + ", reference=" + reference,
                                () -> {
                                  try (AssignmentTestProject project = parallelProject(true)) {
                                    AssignmentParameters parameters = parameters(project, 4, 4);
                                    parameters.setMaxDetourRatio(1);
                                    parameters
                                        .getCostFunctions()
                                        .setProperty(
                                            NodusC.VARNAME_MAX_DETOUR_REF_MODE,
                                            reference.toString());
                                    project.run(assignment(parameters, exact), 2);
                                    assertParallelRoutes(project, reference == 2);
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> duplicateSearchesDoNotDuplicateHeadersOrChangeDemandAllocation() {
    return Stream.of(false, true)
        .flatMap(
            exact ->
                Stream.of(false, true)
                    .map(
                        zeroMarkup ->
                            dynamicTest(
                                "exact=" + exact + ", zero markup=" + zeroMarkup,
                                () -> {
                                  try (AssignmentTestProject project = parallelProject(false)) {
                                    List<List<String>> expected = null;
                                    for (int threads : new int[] {1, 4, 4}) {
                                      final AssignmentParameters parameters =
                                          parameters(project, threads, 8);
                                      parameters.setCostMarkup(zeroMarkup ? 0 : 1);
                                      project.run(assignment(parameters, exact), 2);
                                      assertParallelRoutes(project, !zeroMarkup);
                                      final List<List<String>> actual = snapshot(project);
                                      if (expected != null) {
                                        assertEquals(expected, actual);
                                      }
                                      expected = actual;
                                    }
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> unreachableModeCannotSupplyTheDetourBenchmark() {
    return Stream.of(false, true)
        .map(
            exact ->
                dynamicTest(
                    "exact=" + exact,
                    () -> {
                      try (AssignmentTestProject project =
                          new AssignmentTestProject(
                              directory,
                              4,
                              new double[][] {
                                {11, 1, 4, 10, 0}, {12, 1, 2, 2, 0}, {13, 3, 4, 3, 0}
                              })) {
                        for (int row : new int[] {1, 2}) {
                          project.links.getModel().setValueAt(2.0, row, NodusC.DBF_IDX_MODE);
                        }
                        project.execute("INSERT INTO mini_od VALUES (1,1,4,132),(2,1,4,264)");
                        AssignmentParameters parameters = parameters(project, 4, 4);
                        parameters.setMaxDetourRatio(2);
                        project.run(assignment(parameters, exact), 2);
                        assertDemand(project);
                        assertEquals(2, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
                        assertEquals(
                            396,
                            project.number(
                                "SELECT SUM(qty) FROM mini_paths1_header WHERE ldmode=1"));
                        assertNetworkMatchesPaths(project);
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> distinctRoutesWithTheSameFormerChecksumMustBothSurvive() {
    return Stream.of(false, true)
        .map(
            exact ->
                dynamicTest(
                    "exact=" + exact,
                    () -> {
                      try (AssignmentTestProject project = parallelProject(false)) {
                        final AssignmentParameters parameters = parameters(project, 4, 8);
                        final VirtualNetwork network = new VirtualNetwork(parameters);
                        final int[] sums = new int[2];
                        try {
                          assertTrue(network.generate());
                          for (VirtualNodeList nodes : network.getVirtualNodeLists()) {
                            for (VirtualNode node : nodes.getVirtualNodeList()) {
                              for (VirtualLink link : node.getVirtualLinkList()) {
                                int realLink = -1;
                                if (link.getType() == VirtualLink.TYPE_LOAD
                                    && link.getBeginVirtualNode().getRealNodeId(false) == 1) {
                                  realLink = link.getEndVirtualNode().getRealLinkId();
                                } else if ((link.getType() == VirtualLink.TYPE_MOVE
                                        && link.getBeginVirtualNode().getRealNodeId(false) == 1)
                                    || (link.getType() == VirtualLink.TYPE_UNLOAD
                                        && link.getBeginVirtualNode().getRealNodeId(false) == 2)) {
                                  realLink = link.getBeginVirtualNode().getRealLinkId();
                                }
                                if (realLink == 11 || realLink == 12) {
                                  sums[realLink - 11] += link.getId();
                                }
                              }
                            }
                          }
                        } finally {
                          network.dispose();
                        }
                        assertTrue(sums[0] > 0 && sums[1] > 0);
                        // Former key = sum(virtual-link IDs) * length. Integer-kilometer lengths
                        // make two distinct physical routes collide exactly in that checksum.
                        for (int row = 0; row < 2; row++) {
                          ((OMPoly) project.links.getEsriGraphicList().getOMGraphicAt(row))
                              .setLocation(
                                  new double[] {0, 0, 0, sums[1 - row] / 6378.137},
                                  OMGraphic.RADIANS);
                        }
                        project.run(assignment(parameters, exact), 2);
                        final double first =
                            ((RealLink)
                                    project
                                        .links
                                        .getEsriGraphicList()
                                        .getOMGraphicAt(0)
                                        .getAttribute(0))
                                .getLength();
                        final double second =
                            ((RealLink)
                                    project
                                        .links
                                        .getEsriGraphicList()
                                        .getOMGraphicAt(1)
                                        .getAttribute(0))
                                .getLength();
                        assertEquals(sums[0] * first, sums[1] * second);
                        assertParallelRoutes(project, true);
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> cheapestIntermodalOnlyRejectsCostlierRoutesAndPreservesTies() {
    return Stream.of(false, true)
        .flatMap(
            exact ->
                Stream.of(false, true)
                    .flatMap(
                        keepCheapest ->
                            Stream.of(
                                    "costlier", "tied with unimodal", "cheapest", "intermodal tie")
                                .map(
                                    choice ->
                                        dynamicTest(
                                            "exact="
                                                + exact
                                                + ", cheapest="
                                                + keepCheapest
                                                + ", "
                                                + choice,
                                            () -> checkIntermodal(exact, keepCheapest, choice)))));
  }

  private void checkIntermodal(boolean exact, boolean keepCheapest, String choice)
      throws Exception {
    final double unimodal =
        choice.equals("costlier") ? 10 : choice.equals("tied with unimodal") ? 12 : 20;
    final double second = choice.equals("intermodal tie") ? 12 : 14;
    try (AssignmentTestProject project = intermodalProject(unimodal, 30, second)) {
      AssignmentParameters parameters = parameters(project, 4, 8);
      parameters.setKeepOnlyCheapestIntermodalPath(keepCheapest);
      project.run(assignment(parameters, exact), 2);
      List<Double> expected =
          !keepCheapest
              ? List.of(12.0, second)
              : unimodal < 12 ? List.of() : second == 12 ? List.of(12.0, 12.0) : List.of(12.0);
      assertIntermodalRoutes(project, expected);
    }
  }

  @TestFactory
  Stream<DynamicTest> intermodalCheapestCheckIsIndependentOfTheDetourReferenceMode() {
    return Stream.of(false, true)
        .flatMap(
            exact ->
                Stream.of(1, 2)
                    .flatMap(
                        cheaperMode ->
                            Stream.of(1, 2)
                                .map(
                                    reference ->
                                        dynamicTest(
                                            "exact="
                                                + exact
                                                + ", cheaper mode="
                                                + cheaperMode
                                                + ", reference="
                                                + reference,
                                            () -> {
                                              try (AssignmentTestProject project =
                                                  intermodalProject(
                                                      cheaperMode == 1 ? 10 : 30,
                                                      cheaperMode == 2 ? 10 : 30,
                                                      14)) {
                                                AssignmentParameters parameters =
                                                    parameters(project, 4, 8);
                                                parameters.setKeepOnlyCheapestIntermodalPath(true);
                                                parameters
                                                    .getCostFunctions()
                                                    .setProperty(
                                                        NodusC.VARNAME_MAX_DETOUR_REF_MODE,
                                                        reference.toString());
                                                project.run(assignment(parameters, exact), 2);
                                                assertIntermodalRoutes(project, List.of());
                                              }
                                            }))));
  }

  private AssignmentTestProject parallelProject(boolean differentModes) throws Exception {
    AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 2, new double[][] {{11, 1, 2, 10, 0}, {12, 1, 2, 12, 0}});
    // Exact 1:2 lengths on the equator; the two physical links connect the same endpoints.
    geometry(project, 0, 0, 0, 0, 1);
    geometry(project, 1, 0, 0, 0, -0.5, 0, 1);
    if (differentModes) {
      project.links.getModel().setValueAt(2.0, 1, NodusC.DBF_IDX_MODE);
    }
    project.execute("INSERT INTO mini_od VALUES (1,1,2,132),(2,1,2,264)");
    return project;
  }

  private AssignmentTestProject intermodalProject(double road, double rail, double second)
      throws Exception {
    AssignmentTestProject project =
        new AssignmentTestProject(
            directory,
            4,
            new double[][] {
              {11, 1, 4, road, 0}, {12, 1, 4, rail, 0}, {13, 1, 2, 4, 0},
              {14, 2, 4, 8, 0}, {15, 1, 3, 6, 0}, {16, 3, 4, second - 6, 0}
            });
    for (int row : new int[] {1, 3, 5}) {
      project.links.getModel().setValueAt(2.0, row, NodusC.DBF_IDX_MODE);
    }
    project.setNodeHandling(2, NodusC.HANDLING_ALL);
    project.setNodeHandling(3, NodusC.HANDLING_ALL);
    project.execute("INSERT INTO mini_od VALUES (1,1,4,132),(2,1,4,264)");
    return project;
  }

  private static AssignmentParameters parameters(
      AssignmentTestProject project, int threads, int alternatives) {
    AssignmentParameters parameters = project.parameters(threads);
    for (int mode : new int[] {1, 2}) {
      for (String prefix : new String[] {"ld.", "ul.", "tr."}) {
        parameters.getCostFunctions().setProperty(prefix + mode + ",1", "0");
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

  private static Assignment assignment(AssignmentParameters parameters, boolean exact) {
    return exact ? new ExactMFAssignment(parameters) : new FastMFAssignment(parameters);
  }

  private static void geometry(AssignmentTestProject project, int row, double... coordinates) {
    ((OMPoly) project.links.getEsriGraphicList().getOMGraphicAt(row))
        .setLocation(coordinates, OMGraphic.DECIMAL_DEGREES);
  }

  private static void assertParallelRoutes(AssignmentTestProject project, boolean both)
      throws Exception {
    assertDemand(project);
    assertEquals(both ? 4 : 2, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
    for (int group : new int[] {1, 2}) {
      assertEquals(
          both ? 72 * group : 132 * group,
          project.number(
              "SELECT SUM(h.qty) FROM mini_paths1_header h JOIN mini_paths1_detail d "
                  + "ON h.pathidx=d.pathidx WHERE h.grp="
                  + group
                  + " AND d.link=11"),
          0.001);
      assertEquals(
          both ? 60 * group : 0,
          project.number(
              "SELECT SUM(h.qty) FROM mini_paths1_header h JOIN mini_paths1_detail d "
                  + "ON h.pathidx=d.pathidx WHERE h.grp="
                  + group
                  + " AND d.link=12"),
          0.001);
    }
    assertNetworkMatchesPaths(project);
  }

  private static void assertIntermodalRoutes(AssignmentTestProject project, List<Double> costs)
      throws Exception {
    assertDemand(project);
    assertEquals(2 * (2 + costs.size()), project.number("SELECT COUNT(*) FROM mini_paths1_header"));
    for (int group : new int[] {1, 2}) {
      assertEquals(
          costs,
          project
              .rows(
                  "SELECT mvcost FROM mini_paths1_header WHERE grp="
                      + group
                      + " AND ldmode<>ulmode ORDER BY mvcost,iteration")
              .stream()
              .map(row -> Double.parseDouble(row.get(0)))
              .collect(Collectors.toList()));
      for (int mode : new int[] {1, 2}) {
        assertEquals(
            1,
            project.number(
                "SELECT COUNT(*) FROM mini_paths1_header WHERE grp="
                    + group
                    + " AND ldmode="
                    + mode
                    + " AND ulmode="
                    + mode));
      }
    }
    final double intermodal =
        project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE ldmode<>ulmode");
    assertEquals(
        intermodal,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_TRANSHIP),
        0.005);
    assertNetworkMatchesPaths(project);
  }

  private static void assertDemand(AssignmentTestProject project) throws Exception {
    assertEquals(396, project.number("SELECT SUM(qty) FROM mini_paths1_header"), 0.005);
    for (int group : new int[] {1, 2}) {
      assertEquals(
          132 * group,
          project.number("SELECT SUM(qty) FROM mini_paths1_header WHERE grp=" + group),
          0.003);
    }
    assertEquals(0, project.number("SELECT COUNT(*) FROM mini_paths1_header WHERE qty<=0"));
    assertEquals(0, project.number("SELECT COUNT(*) FROM mini_vnet1 WHERE qty<0"));
    for (int type : new int[] {VirtualLink.TYPE_LOAD, VirtualLink.TYPE_UNLOAD}) {
      assertEquals(
          396, project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + type), 0.005);
    }
    assertEquals(396, project.number("SELECT SUM(qty) FROM mini_od"));
  }

  private static void assertNetworkMatchesPaths(AssignmentTestProject project) throws Exception {
    for (int row = 0; row < project.links.getModel().getRowCount(); row++) {
      int link = ((Number) project.links.getModel().getValueAt(row, NodusC.DBF_IDX_NUM)).intValue();
      double paths =
          project.number(
              "SELECT SUM(h.qty) FROM mini_paths1_header h "
                  + "JOIN mini_paths1_detail d ON h.pathidx=d.pathidx WHERE ABS(d.link)="
                  + link);
      assertEquals(
          paths,
          project.number(
              "SELECT SUM(qty) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_MOVE
                  + " AND link1="
                  + link),
          0.006);
    }
  }

  private static List<List<String>> snapshot(AssignmentTestProject project) throws Exception {
    List<List<String>> rows =
        new ArrayList<>(
            project.rows("SELECT * FROM mini_vnet1 ORDER BY node1,node2,link1,link2,vtype"));
    rows.addAll(
        project.rows(
            "SELECT h.grp,h.iteration,h.qty,h.mvcost,d.link FROM mini_paths1_header h "
                + "JOIN mini_paths1_detail d ON h.pathidx=d.pathidx "
                + "ORDER BY h.grp,h.iteration,d.link"));
    return rows;
  }
}
