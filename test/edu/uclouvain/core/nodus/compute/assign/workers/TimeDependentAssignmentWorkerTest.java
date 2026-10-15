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

package edu.uclouvain.core.nodus.compute.assign.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.AssignmentTestProject;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Direct worker checks with real graph generation, demand relocation and temporary path output. */
@ResourceLock("JDBCUtils")
@Timeout(20)
class TimeDependentAssignmentWorkerTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> timedRoutes() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      String prefix = compact ? "compact: " : "linked: ";
      tests.add(
          dynamicTest(
              prefix + "static arrivals use half-open time slices",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{1, 60}, {1, 120}}, 3, 1)) {
                  fixture.demand(1, 100, 0, (byte) 0);
                  fixture.runStatic(compact, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 0, 100, 0);
                  fixture.assertFlows(12, 0, 100, 0);
                  fixture.finish();
                  assertEquals(
                      100, fixture.project.number("SELECT SUM(qty) FROM mini_paths1_header"));
                  assertEquals(
                      List.of(List.of("11"), List.of("12")),
                      fixture.project.rows("SELECT link FROM mini_paths1_detail ORDER BY link"));
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "static fractional travel times accumulate before bucketing",
              () -> {
                // 37.5 + 22.5 seconds arrives exactly at the start of the second slice.
                try (Fixture fixture = new Fixture(new float[][] {{0.5f, 48}, {0.5f, 80}}, 3, 1)) {
                  fixture.demand(1, 100, 0, (byte) 0);
                  fixture.runStatic(compact, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 100, 0, 0);
                  fixture.assertFlows(12, 0, 100, 0);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "static long links retain their actual arrival slice",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{1.5f, 60}, {0.5f, 60}}, 4, 1)) {
                  fixture.demand(1, 100, 0, (byte) 0);
                  fixture.runStatic(compact, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 0, 100, 0, 0);
                  fixture.assertFlows(12, 0, 0, 100, 0);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "dynamic boundary arrival continues once in the next slice",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{1, 60}, {1, 120}}, 3, 1)) {
                  ODCell demand = fixture.demand(1, 100, 0, (byte) 0);
                  fixture.runDynamic(compact, 0, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 100, 0, 0);
                  fixture.assertFlows(12, 0, 0, 0);
                  assertEquals(2, demand.getRelocatedOriginNodeId());
                  assertEquals(60, demand.getRelocatedStartingTime());
                  fixture.assertDemand(100);
                  fixture.runDynamic(compact, 1, (byte) 0, (byte) 0);
                  fixture.runDynamic(compact, 2, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 100, 0, 0);
                  fixture.assertFlows(12, 0, 100, 0);
                  assertEquals(3, demand.getRelocatedOriginNodeId());
                  assertEquals(90, demand.getRelocatedStartingTime());
                  fixture.assertDemand(100);
                  fixture.assertOperationTotal(VirtualLink.TYPE_LOAD, 100);
                  fixture.assertOperationTotal(VirtualLink.TYPE_UNLOAD, 100);
                  fixture.finish();
                  assertEquals(
                      1,
                      fixture.project.number(
                          "SELECT COUNT(DISTINCT pathidx) FROM mini_paths1_header"));
                  assertEquals(
                      List.of(List.of("11"), List.of("12")),
                      fixture.project.rows("SELECT link FROM mini_paths1_detail ORDER BY link"));
                  assertEquals(
                      5, fixture.project.number("SELECT SUM(mvcost) FROM mini_paths1_header"));
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "dynamic long links preserve pending demand",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{1.5f, 60}, {0.5f, 60}}, 4, 1)) {
                  ODCell demand = fixture.demand(1, 100, 0, (byte) 0);
                  fixture.runDynamic(compact, 0, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 0, 0, 0, 0);
                  assertEquals(1, demand.getRelocatedOriginNodeId());
                  fixture.assertDemand(100);
                  for (int slice = 1; slice < 4; slice++) {
                    fixture.runDynamic(compact, slice, (byte) 0, (byte) 0);
                    fixture.assertDemand(100);
                  }
                  fixture.assertFlows(11, 0, 100, 0, 0);
                  fixture.assertFlows(12, 0, 100, 0, 0);
                  assertEquals(3, demand.getRelocatedOriginNodeId());
                  assertEquals(120, demand.getRelocatedStartingTime());
                  fixture.assertOperationTotal(VirtualLink.TYPE_LOAD, 100);
                  fixture.assertOperationTotal(VirtualLink.TYPE_UNLOAD, 100);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "fractional times survive dynamic relocation",
              () -> {
                // 37.5 + 45 + 37.5 = 120 seconds, including relocation after the first link.
                try (Fixture fixture =
                    new Fixture(new float[][] {{0.5f, 48}, {0.5f, 40}, {0.5f, 48}}, 3, 1)) {
                  ODCell demand = fixture.demand(1, 100, 0, (byte) 0);
                  fixture.runDynamic(compact, 0, (byte) 0, (byte) 0);
                  assertEquals(2, demand.getRelocatedOriginNodeId());
                  assertEquals(37.5, demand.getPreciseRelocatedStartingTime(), 1e-12);
                  assertEquals(37, demand.getRelocatedStartingTime());
                  fixture.runDynamic(compact, 1, (byte) 0, (byte) 0);
                  assertEquals(4, demand.getRelocatedOriginNodeId());
                  assertEquals(120, demand.getRelocatedStartingTime());
                  fixture.runDynamic(compact, 2, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 100, 0, 0);
                  fixture.assertFlows(12, 0, 100, 0);
                  fixture.assertFlows(13, 0, 100, 0);
                  fixture.assertDemand(100);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "dynamic departures inside a slice start in that slice",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{0.5f, 60}}, 6, 2)) {
                  ODCell demand = fixture.demand(1, 100, 1, (byte) 0);
                  fixture.runDynamic(compact, 0, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 100, 0, 0);
                  assertEquals(90, demand.getRelocatedStartingTime());
                  fixture.runDynamic(compact, 1, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 100, 0, 0);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "future departures and other groups/classes stay separate",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{0.5f, 60}}, 3, 1)) {
                  ODCell future = fixture.demand(1, 100, 1, (byte) 0);
                  fixture.demand(1, 23, 0, (byte) 1);
                  fixture.demand(2, 47, 0, (byte) 0);
                  fixture.runDynamic(compact, 0, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 0, 0, 0);
                  assertEquals(1, future.getRelocatedOriginNodeId());
                  fixture.runDynamic(compact, 0, (byte) 0, (byte) 1);
                  fixture.runDynamic(compact, 0, (byte) 1, (byte) 0);
                  fixture.runDynamic(compact, 1, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 23, 100, 0);
                  assertEquals(47, fixture.forward(11).getCurrentVolume((byte) 1, 0));
                  assertEquals(0, fixture.forward(11).getCurrentVolume((byte) 1, 1));
                  fixture.assertDemand(170);
                  fixture.finish();
                  assertEquals(
                      170, fixture.project.number("SELECT SUM(qty) FROM mini_paths1_header"));
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "static arrival at the horizon is excluded",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{1, 60}, {1, 60}}, 2, 1)) {
                  fixture.demand(1, 100, 0, (byte) 0);
                  PrintStream original = System.out;
                  ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
                  try (PrintStream captured =
                      new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
                    System.setOut(captured);
                    fixture.runStatic(compact, (byte) 0, (byte) 0);
                  } finally {
                    System.setOut(original);
                  }
                  assertTrue(
                      diagnostics.toString(StandardCharsets.UTF_8).contains("out of time frame"));
                  fixture.assertFlows(11, 0, 100);
                  fixture.assertFlows(12, 0, 0);
                  fixture.assertOperationTotal(VirtualLink.TYPE_UNLOAD, 0);
                  fixture.assertDemand(100);
                  fixture.finish();
                  assertEquals(
                      List.of(List.of("11")),
                      fixture.project.rows("SELECT link FROM mini_paths1_detail"));
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "dynamic horizon retains unfinished demand",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{1.5f, 60}}, 1, 1)) {
                  ODCell demand = fixture.demand(1, 100, 0, (byte) 0);
                  fixture.runDynamic(compact, 0, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 0);
                  assertEquals(1, demand.getRelocatedOriginNodeId());
                  fixture.assertDemand(100);
                  fixture.assertOperationTotal(VirtualLink.TYPE_LOAD, 100);
                  fixture.assertOperationTotal(VirtualLink.TYPE_UNLOAD, 0);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "dynamic worker accepts slice indices beyond byte range",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{0.5f, 60}}, 202, 1)) {
                  ODCell demand = fixture.demand(1, 100, 200, (byte) 0);
                  fixture.runDynamic(compact, 199, (byte) 0, (byte) 0);
                  fixture.runDynamic(compact, 200, (byte) 0, (byte) 0);
                  fixture.runDynamic(compact, 201, (byte) 0, (byte) 0);
                  assertEquals(2, demand.getRelocatedOriginNodeId());
                  for (int slice = 0; slice < fixture.slices; slice++) {
                    assertEquals(
                        slice == 200 ? 100 : 0,
                        fixture.forward(11).getCurrentVolume((byte) 0, slice));
                  }
                  fixture.assertDemand(100);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "unreachable dynamic demand is retained without flow",
              () -> {
                try (Fixture fixture = new Fixture(new float[][] {{0.5f, 60}}, 3, 1)) {
                  fixture.demand(1, 100, 0, (byte) 0);
                  fixture.forward(11).setCost((byte) 0, -1);
                  fixture.runDynamic(compact, 0, (byte) 0, (byte) 0);
                  fixture.assertFlows(11, 0, 0, 0);
                  fixture.assertDemand(100);
                  fixture.finish();
                  assertEquals(
                      0, fixture.project.number("SELECT COUNT(*) FROM mini_paths1_header"));
                  assertEquals(
                      0, fixture.project.number("SELECT COUNT(*) FROM mini_paths1_detail"));
                }
              }));
    }
    return tests;
  }

  private final class Fixture implements AutoCloseable {
    final AssignmentTestProject project;
    final AssignmentParameters parameters;
    final VirtualNetwork network;
    final PathWriter writer;
    final int sliceMinutes;
    final int slices;
    final List<VirtualLink> allLinks = new ArrayList<>();

    Fixture(float[][] lengthAndSpeed, int endMinutes, int sliceMinutes) throws Exception {
      this.sliceMinutes = sliceMinutes;
      slices = endMinutes / sliceMinutes;
      double[][] rows = new double[lengthAndSpeed.length][5];
      for (int i = 0; i < rows.length; i++) {
        rows[i] = new double[] {11 + i, i + 1, i + 2, i + 2, 0};
      }
      project = new AssignmentTestProject(directory, rows.length + 1, rows);
      parameters = project.parameters(1);
      parameters.setTimeDependent(true);
      network = new VirtualNetwork(parameters);
      network.setAssignmentTimeParameters(0, endMinutes, sliceMinutes);
      assertTrue(network.generate());
      network.setGroups(new byte[] {1, 2});
      network.setMaxClass((byte) 1);
      for (int i = 0; i < rows.length; i++) {
        RealLink real =
            (RealLink)
                project.getLinkLayers()[0].getEsriGraphicList().getOMGraphicAt(i).getAttribute(0);
        real.setLength(lengthAndSpeed[i][0]);
        real.setSpeed(lengthAndSpeed[i][1]);
      }
      for (VirtualNodeList list : network.getVirtualNodeLists()) {
        for (VirtualNode node : list.getVirtualNodeList()) {
          for (VirtualLink link : node.getVirtualLinkList()) {
            allLinks.add(link);
            double cost =
                link.getType() == VirtualLink.TYPE_MOVE
                    ? link.getBeginVirtualNode().getRealLinkId() - 9
                    : 0;
            link.setCost((byte) 0, cost);
            link.setCost((byte) 1, cost);
          }
        }
      }
      writer = new PathWriter(parameters);
    }

    ODCell demand(int group, double quantity, int minute, byte odClass) {
      ODCell demand =
          new ODCell(group, 1, network.getVirtualNodeLists().length, quantity, minute, odClass);
      network.getVirtualNodeLists()[network.getNodeIndexInVirtualNodeList(1, true)].addDemand(
          demand);
      return demand;
    }

    void runStatic(boolean compact, byte group, byte odClass) {
      StaticAoNTimeDependentAssignmentWorker worker =
          new StaticAoNTimeDependentAssignmentWorker(new WorkQueue()) {
            @Override
            boolean updateProgress(String message) {
              return true;
            }

            @Override
            public void setErrorMessage(String message) {
              fail(message);
            }
          };
      worker.setTimeParameters(0, slices * sliceMinutes, sliceMinutes);
      run(worker, compact, group, odClass);
    }

    void runDynamic(boolean compact, int slice, byte group, byte odClass) {
      DynamicTimeDependentAssignmentWorker worker =
          new DynamicTimeDependentAssignmentWorker(new WorkQueue()) {
            @Override
            boolean updateProgress(String message) {
              return true;
            }

            @Override
            public void setErrorMessage(String message) {
              fail(message);
            }
          };
      worker.setTimeParameters(slice, slice * sliceMinutes, sliceMinutes);
      run(worker, compact, group, odClass);
    }

    void run(AssignmentWorker worker, boolean compact, byte group, byte odClass) {
      boolean original = NodusC.useCompactShortestPaths;
      try (var times = parameters.getComputingTimes().newWorkerTimes()) {
        NodusC.useCompactShortestPaths = compact;
        worker.virtualNet = network;
        worker.assignmentParameters = parameters;
        worker.groupIndex = group;
        worker.currentGroup = network.getGroups()[group];
        worker.odClass = odClass;
        worker.workerTimes = times;
        worker.pathBuffer = writer.newBuffer(times);
        assertTrue(worker.doAssignment());
        assertFalse(worker.isCancelled());
        assertTrue(worker.pathBuffer.flush());
      } finally {
        NodusC.useCompactShortestPaths = original;
      }
    }

    VirtualLink forward(int realId) {
      return allLinks
          .stream()
          .filter(
              link ->
                  link.getType() == VirtualLink.TYPE_MOVE
                      && link.getBeginVirtualNode().getRealLinkId() == realId
                      && link.getUpStream())
          .findFirst()
          .orElseThrow();
    }

    void assertFlows(int realId, double... expected) {
      VirtualLink forward = forward(realId);
      VirtualLink reverse =
          allLinks
              .stream()
              .filter(
                  link ->
                      link.getType() == VirtualLink.TYPE_MOVE
                          && link.getBeginVirtualNode().getRealLinkId() == realId
                          && !link.getUpStream())
              .findFirst()
              .orElseThrow();
      assertEquals(slices, expected.length);
      for (int slice = 0; slice < slices; slice++) {
        assertEquals(
            expected[slice],
            forward.getCurrentVolume((byte) 0, slice),
            1e-12,
            "link " + realId + ", slice " + slice);
        assertEquals(0, reverse.getCurrentVolume((byte) 0, slice), 1e-12);
      }
    }

    void assertOperationTotal(byte type, double expected) {
      double total = 0;
      for (VirtualLink link : allLinks) {
        if (link.getType() == type) {
          for (int slice = 0; slice < slices; slice++) {
            total += link.getCurrentVolume((byte) 0, slice);
          }
        }
      }
      assertEquals(expected, total, 1e-12);
    }

    void assertDemand(double expected) {
      double total = 0;
      for (VirtualNodeList node : network.getVirtualNodeLists()) {
        for (int list = 0; list < node.getNbDemandLists(); list++) {
          for (int group : new int[] {1, 2}) {
            for (byte odClass = 0; odClass < 2; odClass++) {
              List<ODCell> demands = node.getDemandForGroup(list, group, odClass);
              if (demands != null) {
                total += demands.stream().mapToDouble(ODCell::getQuantity).sum();
              }
            }
          }
        }
      }
      assertEquals(expected, total, 1e-12);
    }

    void finish() {
      assertTrue(writer.close());
    }

    @Override
    public void close() {
      writer.close();
      network.dispose();
      project.close();
    }
  }
}
