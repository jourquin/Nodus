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

package edu.uclouvain.core.nodus.compute.assign.shortestpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Directed landmark queries compared with independent Floyd-Warshall costs and actual paths. */
class BinaryHeapALTTest {
  @TestFactory
  List<DynamicTest> matchesReferenceAcrossCostScalesPenaltiesRestrictionsAndRestoration() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      for (double scale : new double[] {1e-200, 1e-9, 1, 1e9, 1e200}) {
        tests.add(
            dynamicTest(
                "compact=" + compact + "/scale=" + scale,
                () -> {
                  final Random random = new Random(20261024);
                  Graph graph = new Graph(25);
                  // Asymmetric components, an isolated node, zero-cost cycles and parallel edges.
                  graph.edge(1, 2, 0);
                  graph.edge(2, 1, 0);
                  graph.edge(2, 3, 9 * scale);
                  graph.edge(2, 3, scale);
                  for (int from = 1; from < 25; from++) {
                    for (int to = 1; to < 25; to++) {
                      if (from != to && random.nextInt(8) == 0) {
                        graph.edge(from, to, random.nextInt(10) * scale);
                      }
                    }
                  }
                  CompactShortestPathGraph copy =
                      compact ? new CompactShortestPathGraph(graph.heads) : null;
                  BinaryHeapALT search = new BinaryHeapALT(graph.heads, copy);
                  assertEquals(BinaryHeapALT.DEFAULT_LANDMARKS, search.landmarkCount());
                  checkAllPairs(graph, search);
                  for (int i = 0; i < graph.edges.size(); i++) {
                    AdjacencyNode edge = graph.edges.get(i);
                    edge.edgeWeight *= 1 + random.nextInt(6);
                    if (i % 7 == 0) {
                      edge.edgeWeight = Double.POSITIVE_INFINITY;
                    }
                    if (copy != null) {
                      copy.copyWeight(edge);
                    }
                  }
                  checkAllPairs(graph, search);
                  for (AdjacencyNode edge : graph.edges) {
                    edge.edgeWeight = edge.originalEdgeWeight;
                    if (copy != null) {
                      copy.copyWeight(edge);
                    }
                  }
                  checkAllPairs(graph, search);
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> reopensImprovedLabelsAndResetsThemForTheNextQuery() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "compact=" + compact,
              () -> {
                Graph graph = new Graph(5);
                graph.edge(1, 2, 3);
                graph.edge(1, 3, 1);
                graph.edge(3, 2, 1);
                graph.edge(2, 4, 10);
                CompactShortestPathGraph copy =
                    compact ? new CompactShortestPathGraph(graph.heads) : null;
                BinaryHeapALT search =
                    new BinaryHeapALT(graph.heads, copy, 0, () -> false) {
                      @Override
                      double estimateRemainingCost(int node, int target) {
                        // Admissible but inconsistent: node 2 is settled at 3 before node 3
                        // improves it to 2.
                        return target == 4 && node == 3 ? 10 : 0;
                      }
                    };
                for (int repeat = 0; repeat < 4; repeat++) {
                  search.compute(1, 4);
                  assertEquals(12, search.getWeights()[4]);
                  assertEquals(3, search.getPredecessors()[2]);
                  search.compute(5, 4);
                  assertEquals(Double.MAX_VALUE, search.getWeights()[4]);
                  assertEquals(0, search.getPredecessors()[4]);
                  search.compute(3, 4);
                  assertEquals(11, search.getWeights()[4]);
                }
              }));
    }
    return tests;
  }

  @Test
  void boundsUseCostsRatherThanCoordinatesAndRemainUsefulWithSourceAndSinkNodes() {
    Graph graph = new Graph(8);
    for (int node = 1; node < 8; node++) {
      graph.edge(node, node + 1, 2);
    }
    BinaryHeapALT search = new BinaryHeapALT(graph.heads);
    assertEquals(6, search.landmarkCount());
    assertTrue(search.estimateRemainingCost(2, 8) > 11.9);
    assertEquals(0, search.estimateRemainingCost(8, 8));
    checkAllPairs(graph, search);
  }

  @TestFactory
  List<DynamicTest> handlesEmptyGraphsNonfiniteCostsAndOverflowingLandmarkDistances() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "compact=" + compact,
              () -> {
                Graph empty = new Graph(1);
                BinaryHeapALT search =
                    new BinaryHeapALT(
                        empty.heads, compact ? new CompactShortestPathGraph(empty.heads) : null);
                assertEquals(0, search.landmarkCount());
                search.compute(1, 1);
                assertEquals(0, search.getWeights()[1]);
                Graph graph = new Graph(7);
                graph.edge(1, 2, Double.NaN);
                graph.edge(1, 3, Double.POSITIVE_INFINITY);
                graph.edge(1, 4, 1e308);
                graph.edge(4, 5, 1e308);
                graph.edge(1, 5, 7);
                graph.edge(5, 6, 1e308);
                graph.edge(6, 7, 1e308);
                graph.edge(7, 1, 0);
                search =
                    new BinaryHeapALT(
                        graph.heads, compact ? new CompactShortestPathGraph(graph.heads) : null);
                checkAllPairs(graph, search);
              }));
    }
    return tests;
  }

  @Test
  void zeroLandmarksSupportsCostsDecreasingBelowTheInitialBaseline() {
    Graph graph = new Graph(3);
    AdjacencyNode edge = graph.edge(1, 2, 10);
    graph.edge(2, 3, 1);
    graph.edge(1, 3, 5);
    BinaryHeapALT search = new BinaryHeapALT(graph.heads, null, 0, () -> false);
    search.compute(1, 3);
    assertEquals(5, search.getWeights()[3]);
    edge.edgeWeight = 1;
    search.compute(1, 3);
    assertEquals(2, search.getWeights()[3]);
  }

  @Test
  void rejectsNegativeCostsAndCanCancelWhilePreprocessing() {
    Graph graph = new Graph(4);
    AdjacencyNode edge = graph.edge(1, 2, -1);
    assertThrows(IllegalArgumentException.class, () -> new BinaryHeapALT(graph.heads));
    edge.edgeWeight = 1;
    graph.edge(2, 3, 1);
    AtomicInteger checks = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () -> new BinaryHeapALT(graph.heads, null, 16, () -> checks.incrementAndGet() >= 3));
    assertTrue(checks.get() >= 3);
  }

  private static void checkAllPairs(Graph graph, BinaryHeapALT search) {
    int count = graph.heads.length;
    double[][] edges = new double[count][count];
    for (int node = 1; node < count; node++) {
      Arrays.fill(edges[node], Double.MAX_VALUE);
      edges[node][node] = 0;
      for (AdjacencyNode edge = graph.heads[node]; edge.nextNode != null; edge = edge.nextNode) {
        int next = edge.nextNode.virtualNodeNum;
        if (edge.edgeWeight < edges[node][next]) {
          edges[node][next] = edge.edgeWeight;
        }
      }
    }
    double[][] expected = new double[count][];
    for (int i = 1; i < count; i++) {
      expected[i] = edges[i].clone();
    }
    for (int via = 1; via < count; via++) {
      for (int from = 1; from < count; from++) {
        for (int to = 1; to < count; to++) {
          expected[from][to] =
              Math.min(expected[from][to], expected[from][via] + expected[via][to]);
        }
      }
    }
    for (int from = count - 1; from >= 1; from--) {
      for (int to = 1; to < count; to++) {
        search.compute(from, to);
        String context = from + " -> " + to;
        double cost = expected[from][to];
        if (cost == Double.MAX_VALUE) {
          assertEquals(cost, search.getWeights()[to], context);
          assertEquals(0, search.getPredecessors()[to], context);
          continue;
        }
        double tolerance = Math.ulp(cost) * count * 4;
        assertEquals(cost, search.getWeights()[to], tolerance, context);
        assertTrue(search.estimateRemainingCost(from, to) <= cost + tolerance, context);
        int node = to;
        double actual = 0;
        int steps = 0;
        while (node != from) {
          assertTrue(++steps < count, context + ": predecessor cycle");
          int previous = search.getPredecessors()[node];
          assertTrue(previous > 0, context + ": missing predecessor");
          actual += edges[previous][node];
          node = previous;
        }
        assertEquals(cost, actual, tolerance, context + ": reconstructed cost");
      }
    }
  }

  private static final class Graph {
    final AdjacencyNode[] heads;
    final VirtualNode[] nodes;
    final List<AdjacencyNode> edges = new ArrayList<>();

    Graph(int count) {
      heads = new AdjacencyNode[count + 1];
      nodes = new VirtualNode[count + 1];
      for (int i = 1; i <= count; i++) {
        // Coordinates deliberately have no relation to routing costs.
        nodes[i] = new VirtualNode(i, i, 0, (byte) 1, (byte) 1, (short) 0, i * 7, -i * 11);
        heads[i] = new AdjacencyNode(nodes[i]);
      }
    }

    AdjacencyNode edge(int from, int to, double cost) {
      AdjacencyNode edge = heads[from];
      while (edge.nextNode != null) {
        edge = edge.nextNode;
      }
      edge.edgeWeight = cost;
      edge.originalEdgeWeight = cost;
      edge.nextNode = new AdjacencyNode(nodes[to]);
      edges.add(edge);
      return edge;
    }
  }
}
