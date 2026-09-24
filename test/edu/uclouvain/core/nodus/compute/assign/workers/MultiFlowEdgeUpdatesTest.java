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
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.CompactShortestPathGraph;
import edu.uclouvain.core.nodus.compute.virtual.PathODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Protects edge penalties, mode restrictions and demand consumption between alternatives. */
class MultiFlowEdgeUpdatesTest {
  @TestFactory
  List<DynamicTest> edgeUpdates() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      String name = compact ? "compact: " : "linked: ";
      tests.add(
          dynamicTest(
              name + "shared edges are penalized once per pass",
              () -> {
                Fixture fixture = new Fixture(compact);
                fixture.updates.markPathEdge(fixture.first);
                fixture.updates.markPathEdge(fixture.first);
                fixture.updates.increaseCosts(2);
                assertEquals(4, fixture.first.edgeWeight, 1e-12);
                assertEquals(2, fixture.second.edgeWeight, 1e-12);
                assertEquals(6, fixture.costToGoal(), 1e-12);
                fixture.updates.increaseCosts(2);
                assertEquals(8, fixture.first.edgeWeight, 1e-12);
                assertEquals(7, fixture.costToGoal(), 1e-12);
                fixture.updates.restoreWeights();
                assertEquals(4, fixture.costToGoal(), 1e-12);
              }));
      tests.add(
          dynamicTest(
              name + "clearing marks retains weights until restoration",
              () -> {
                Fixture fixture = new Fixture(compact);
                fixture.updates.markPathEdge(fixture.first);
                fixture.first.isIncreased = true;
                fixture.updates.increaseCosts(3);
                fixture.updates.clearPathMarks();
                assertFalse(fixture.first.inCurrentTree);
                assertFalse(fixture.first.isIncreased);
                fixture.updates.increaseCosts(2);
                assertEquals(6, fixture.first.edgeWeight, 1e-12);
                fixture.updates.restoreWeights();
                assertEquals(4, fixture.costToGoal(), 1e-12);
                fixture.updates.markPathEdge(fixture.first);
                fixture.updates.increaseCosts(2);
                assertEquals(6, fixture.costToGoal(), 1e-12);
              }));
      tests.add(
          dynamicTest(
              name + "loading restrictions cover intermediate nodes",
              () -> {
                Fixture fixture = new Fixture(compact);
                fixture.updates.restrictLoading(fixture.nodes[2].getModeMeansKey());
                assertEquals(2, fixture.first.edgeWeight, 1e-12);
                assertEquals(Double.POSITIVE_INFINITY, fixture.second.edgeWeight);
                // The direct moving edge uses mode 2, but loading restrictions must not disable it.
                assertEquals(7, fixture.direct.edgeWeight, 1e-12);
                assertEquals(7, fixture.costToGoal(), 1e-12);
                fixture.updates.restoreWeights();
                assertEquals(4, fixture.costToGoal(), 1e-12);
              }));
      tests.add(
          dynamicTest(
              name + "demand survives clearing path marks and is consumed once",
              () -> {
                Fixture fixture = new Fixture(compact);
                Path route = new Path();
                route.marketShare = 0.4;
                fixture.first.virtualLink.addCell((byte) 0, new PathODCell(0, 100));
                fixture.updates.markPathEdge(fixture.first);
                fixture.updates.markPathEdge(fixture.first);
                fixture.updates.clearPathMarks();
                fixture.updates.spreadVolumes((byte) 0, new Path[] {route});
                assertEquals(40, fixture.first.virtualLink.getCurrentVolume((byte) 0), 1e-12);
                fixture.updates.spreadVolumes((byte) 0, new Path[] {route});
                assertEquals(40, fixture.first.virtualLink.getCurrentVolume((byte) 0), 1e-12);
                fixture.first.virtualLink.addCell((byte) 0, new PathODCell(0, 50));
                fixture.updates.markPathEdge(fixture.first);
                fixture.updates.spreadVolumes((byte) 0, new Path[] {route});
                assertEquals(60, fixture.first.virtualLink.getCurrentVolume((byte) 0), 1e-12);
              }));
      tests.add(
          dynamicTest(
              name + "restoring weights retains Exact MF path marks",
              () -> {
                Fixture fixture = new Fixture(compact);
                fixture.updates.markPathEdge(fixture.first);
                fixture.updates.increaseCosts(3);
                fixture.updates.restoreWeights();
                assertTrue(fixture.first.inCurrentTree);
                fixture.updates.increaseCosts(2);
                assertEquals(4, fixture.first.edgeWeight, 1e-12);
                assertEquals(6, fixture.costToGoal(), 1e-12);
              }));
    }
    return tests;
  }

  private static final class Fixture {
    final VirtualNode[] nodes = new VirtualNode[4];
    final AdjacencyNode[] graph = new AdjacencyNode[4];
    final AdjacencyNode first;
    final AdjacencyNode second;
    final AdjacencyNode direct;
    final MultiFlowEdgeUpdates updates;
    final BinaryHeapDijkstra search;

    Fixture(boolean compact) {
      for (int i = 1; i <= 3; i++) {
        nodes[i] = new VirtualNode(i, i, 10, (byte) (i == 2 ? 1 : 2), (byte) 1, (short) 0, 0, 0);
        graph[i] = new AdjacencyNode(nodes[i]);
      }
      first = edge(1, 2, 2, VirtualLink.TYPE_LOAD);
      second = edge(2, 3, 2, VirtualLink.TYPE_LOAD);
      direct = edge(1, 3, 7, VirtualLink.TYPE_MOVE);
      CompactShortestPathGraph copy = compact ? new CompactShortestPathGraph(graph) : null;
      updates = new MultiFlowEdgeUpdates(graph, copy);
      search = new BinaryHeapDijkstra(graph, null, copy);
    }

    AdjacencyNode edge(int from, int to, double cost, byte type) {
      VirtualLink link = new VirtualLink(from * 10 + to, 0, 0, nodes[from], nodes[to], type);
      link.setNbGroups(1, 1);
      link.setCost((byte) 0, cost);
      AdjacencyNode edge = graph[from];
      while (edge.nextNode != null) {
        edge = edge.nextNode;
      }
      edge.setNext(new AdjacencyNode(nodes[to]), (byte) 0, link);
      return edge;
    }

    double costToGoal() {
      search.compute(1, 3);
      return search.getWeights()[3];
    }
  }
}
