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

package edu.uclouvain.core.nodus.compute.costs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.AssignmentTestProject;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.CompactShortestPathGraph;
import edu.uclouvain.core.nodus.compute.rules.NodeRule;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real cost jobs must enforce rules before parsing and before building shortest-path graphs. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(15)
class CostParserRulesIntegrationTest {
  @TempDir Path directory;

  @Test
  void inclusionsKeepPermittedCostsAndRemoveTheCheapestForbiddenRoute() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.allow(1);
      fixture.allow(2);
      fixture.loading(1).setCost((byte) 0, 999);
      fixture.loading(3).setCost((byte) 0, 999);
      fixture.loading(3).setCost((byte) 1, 77);
      fixture.run(7, (byte) 0);

      assertEquals(5, fixture.loading(1).getCost((byte) 0));
      assertEquals(7, fixture.loading(1).getDuration((byte) 0));
      assertEquals(6, fixture.loading(2).getCost((byte) 0));
      assertEquals(8, fixture.loading(2).getDuration((byte) 0));
      assertEquals(CostParser.UNDEFINED_FUNCTION, fixture.loading(3).getCost((byte) 0));
      assertEquals(77, fixture.loading(3).getCost((byte) 1));
      fixture.assertShortestCost(8, (byte) 0);
    }
  }

  @Test
  void excludedLoadingLinksAreNotParsedWhileUnloadingRemainsAvailable() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.origin.addExclusion(new NodeRule(-1, 7, 3, 0, 0, 3, 1));
      VirtualNodeList destination =
          fixture
              .network
              .getVirtualNodeLists()[fixture.network.getNodeIndexInVirtualNodeList(2, true)];
      destination.addExclusion(new NodeRule(-2, 7, 3, 0, 0, 3, 1));
      fixture.costs.setProperty("ld.3,1", "unknown_variable + 1");
      fixture.run(7, (byte) 0);

      assertEquals(CostParser.UNDEFINED_FUNCTION, fixture.loading(3).getCost((byte) 0));
      assertEquals(5, fixture.loading(1).getCost((byte) 0));
      assertEquals(6, fixture.loading(2).getCost((byte) 0));
      VirtualLink unloading =
          destination
              .getVirtualNodeList()
              .stream()
              .flatMap(node -> node.getVirtualLinkList().stream())
              .filter(
                  link ->
                      link.getType() == VirtualLink.TYPE_UNLOAD
                          && link.getBeginVirtualNode().getMode() == 3)
              .findFirst()
              .orElseThrow();
      assertEquals(0.1, unloading.getCost((byte) 0), 1e-12);
      fixture.assertShortestCost(8, (byte) 0);
    }
  }

  @Test
  void repeatedScenarioAndGroupJobsRecomputeAvailabilityIndependently() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.allow(1);
      fixture.allow(2);
      fixture.run(7, (byte) 0);
      assertEquals(CostParser.UNDEFINED_FUNCTION, fixture.loading(3).getCost((byte) 0));
      fixture.run(7, (byte) 1);
      assertEquals(0.1, fixture.loading(3).getCost((byte) 1), 1e-12);
      fixture.assertShortestCost(0.45, (byte) 1);
      fixture.run(8, (byte) 0);
      assertEquals(0.1, fixture.loading(3).getCost((byte) 0), 1e-12);
      fixture.assertShortestCost(0.45, (byte) 0);
      fixture.run(7, (byte) 0);
      assertEquals(CostParser.UNDEFINED_FUNCTION, fixture.loading(3).getCost((byte) 0));
      fixture.assertShortestCost(8, (byte) 0);
    }
  }

  private final class Fixture implements AutoCloseable {
    final AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 2, new double[][] {{11, 1, 2, 2, 0}, {12, 1, 2, 3, 0}, {13, 1, 2, 0.25, 0}});
    final AssignmentParameters parameters = project.parameters(1);
    final Properties costs = parameters.getCostFunctions();
    final VirtualNetwork network;
    final VirtualNodeList origin;
    final WorkQueue queue = new WorkQueue();
    final CostParserWorker worker = new CostParserWorker(queue);

    Fixture() throws Exception {
      project.setNodeHandling(1, NodusC.HANDLING_ALL);
      for (int mode = 1; mode <= 3; mode++) {
        project
            .getLinkLayers()[0]
            .getModel()
            .setValueAt((double) mode, mode - 1, NodusC.DBF_IDX_MODE);
        costs.setProperty("ld." + mode + ",1", mode == 3 ? "0.1" : "" + (mode + 4));
        costs.setProperty("ld@" + mode + ",1", "" + (mode + 6));
        costs.setProperty("ul." + mode + ",1", mode == 3 ? "0.1" : "1");
        costs.setProperty("tr." + mode + ",1", "0");
        costs.setProperty("mv." + mode + ",1", "BASECOST");
      }
      network = new VirtualNetwork(parameters);
      assertTrue(network.generate());
      network.setGroups(new byte[] {3, 4});
      network.setMaxClass((byte) 0);
      origin = network.getVirtualNodeLists()[network.getNodeIndexInVirtualNodeList(1, true)];
    }

    void allow(int mode) {
      origin.addExclusion(new NodeRule(1, 7, 3, 0, 0, mode, 1));
    }

    void run(int scenario, byte groupIndex) {
      CostParser parser =
          new CostParser(costs, project, scenario, network.getGroups()[groupIndex], (byte) 0, -1);
      assertTrue(parser.isInitialized(), parser.getErrorMessage());
      queue.addWork(
          new CostParserWorkerParameters(
              new CostParserWorker[] {worker},
              project,
              scenario,
              (byte) 0,
              groupIndex,
              network,
              parser));
      queue.addWork(WorkQueue.NO_MORE_WORK);
      worker.run();
      assertFalse(worker.isCancelled(), worker.getErrorMessage());
      assertNull(worker.getErrorMessage());
    }

    VirtualLink loading(int mode) {
      return origin
          .getVirtualNodeList()
          .stream()
          .flatMap(node -> node.getVirtualLinkList().stream())
          .filter(
              link ->
                  link.getType() == VirtualLink.TYPE_LOAD
                      && link.getEndVirtualNode().getMode() == mode)
          .findFirst()
          .orElseThrow();
    }

    void assertShortestCost(double expected, byte groupIndex) {
      int begin = origin.getLoadingVirtualNodeId();
      int end =
          network.getVirtualNodeLists()[network.getNodeIndexInVirtualNodeList(2, true)]
              .getUnloadingVirtualNodeId();
      AdjacencyNode[] graph = network.generateAdjacencyList(groupIndex);
      for (boolean compact : new boolean[] {false, true}) {
        BinaryHeapDijkstra search =
            new BinaryHeapDijkstra(
                graph, network, compact ? new CompactShortestPathGraph(graph) : null);
        search.compute(begin, end);
        assertEquals(expected, search.getWeights()[end], 1e-12);
      }
    }

    @Override
    public void close() {
      network.dispose();
      project.close();
    }
  }
}
