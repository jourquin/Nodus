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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Destination trees and penalized queries compared with independent Floyd-Warshall distances. */
class BinaryHeapDestinationAStarTest {
  @TestFactory
  List<DynamicTest> matchesReferenceAcrossScalesAndIndependentPenalties() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      for (double scale : new double[] {1e-200, 1e-9, 1, 1e9, 1e200}) {
        for (boolean limited : new boolean[] {false, true}) {
          tests.add(
              dynamicTest(
                  "compact=" + compact + "/scale=" + scale + "/earlyStop=" + limited,
                  () -> {
                    Graph graph = new Graph(25);
                    graph.edge(1, 2, 0);
                    graph.edge(2, 1, 0);
                    graph.edge(2, 3, 9 * scale);
                    graph.edge(2, 3, scale);
                    Random random = new Random(20261025);
                    for (int from = 1; from < 25; from++) {
                      for (int to = 1; to < 25; to++) {
                        if (from != to && random.nextInt(8) == 0) {
                          graph.edge(from, to, random.nextInt(10) * scale);
                        }
                      }
                    }
                    CompactShortestPathGraph copy =
                        compact ? new CompactShortestPathGraph(graph.heads) : null;
                    ReverseDijkstra reverse = new ReverseDijkstra(graph.heads, copy, () -> false);
                    ReverseDijkstra.Result[] trees = new ReverseDijkstra.Result[graph.heads.length];
                    for (int target = 1; target < trees.length; target++) {
                      ReverseDijkstra.Result full = reverse.compute(target, () -> false);
                      int[] required = {1, 2, 4, 7, 7, target};
                      trees[target] =
                          limited ? reverse.compute(target, required, () -> false) : full;
                      assertTrue(trees[target].getSettledNodeCount() <= full.getSettledNodeCount());
                      for (int source : required) {
                        assertEquals(full.isReachable(source), trees[target].isReachable(source));
                        assertEquals(full.distances[source], trees[target].distances[source]);
                      }
                    }
                    BinaryHeapDestinationAStar search =
                        new BinaryHeapDestinationAStar(graph.heads, copy, () -> false);
                    checkAllPairs(graph, search, trees, true);
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
                    checkAllPairs(graph, search, trees, false);
                    for (AdjacencyNode edge : graph.edges) {
                      edge.edgeWeight = edge.originalEdgeWeight;
                      if (copy != null) {
                        copy.copyWeight(edge);
                      }
                    }
                    checkAllPairs(graph, search, trees, true);
                  }));
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> handlesNonfiniteCostsAndOverflow() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "compact=" + compact,
              () -> {
                Graph graph = new Graph(7);
                graph.edge(1, 2, Double.NaN);
                graph.edge(1, 3, Double.POSITIVE_INFINITY);
                graph.edge(1, 4, 1e308);
                graph.edge(4, 5, 1e308);
                graph.edge(1, 5, 7);
                graph.edge(5, 6, 1e308);
                graph.edge(6, 7, 1e308);
                graph.edge(7, 1, 0);
                CompactShortestPathGraph copy =
                    compact ? new CompactShortestPathGraph(graph.heads) : null;
                ReverseDijkstra reverse = new ReverseDijkstra(graph.heads, copy, () -> false);
                ReverseDijkstra.Result[] trees = new ReverseDijkstra.Result[graph.heads.length];
                for (int target = 1; target < trees.length; target++) {
                  trees[target] = reverse.compute(target, () -> false);
                }
                checkAllPairs(
                    graph,
                    new BinaryHeapDestinationAStar(graph.heads, copy, () -> false),
                    trees,
                    true);
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> disablingBoundsAllowsCostsToFallBelowBaseline() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "compact=" + compact,
              () -> {
                Graph graph = new Graph(3);
                AdjacencyNode edge = graph.edge(1, 2, 10);
                graph.edge(2, 3, 1);
                graph.edge(1, 3, 5);
                CompactShortestPathGraph copy =
                    compact ? new CompactShortestPathGraph(graph.heads) : null;
                ReverseDijkstra.Result tree =
                    new ReverseDijkstra(graph.heads, copy, () -> false).compute(3, () -> false);
                BinaryHeapDestinationAStar search =
                    new BinaryHeapDestinationAStar(graph.heads, copy, () -> false);
                search.setBounds(tree, false);
                search.compute(1, 3);
                assertEquals(5, search.getWeights()[3]);
                edge.edgeWeight = 1;
                if (copy != null) {
                  copy.copyWeight(edge);
                }
                search.compute(1, 3);
                assertEquals(2, search.getWeights()[3]);
              }));
    }
    return tests;
  }

  @Test
  void checksGraphOwnershipDestinationAndCancellation() {
    Graph graph = new Graph(4);
    AdjacencyNode edge = graph.edge(1, 2, -1);
    assertThrows(
        IllegalArgumentException.class, () -> new ReverseDijkstra(graph.heads, null, () -> false));
    edge.edgeWeight = 1;
    graph.edge(2, 3, 1);
    assertThrows(
        CancellationException.class, () -> new ReverseDijkstra(graph.heads, null, () -> true));
    AtomicInteger checks = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () -> new ReverseDijkstra(graph.heads, null, () -> checks.incrementAndGet() >= 3));
    ReverseDijkstra reverse = new ReverseDijkstra(graph.heads, null, () -> false);
    checks.set(0);
    assertThrows(
        CancellationException.class, () -> reverse.compute(3, () -> checks.incrementAndGet() >= 2));
    ReverseDijkstra.Result tree = reverse.compute(3, () -> false);
    BinaryHeapDestinationAStar search =
        new BinaryHeapDestinationAStar(graph.heads, null, () -> false);
    assertThrows(IllegalArgumentException.class, () -> search.compute(1, 3));
    search.setBounds(tree, true);
    assertThrows(IllegalArgumentException.class, () -> search.compute(1, 2));
    Graph other = new Graph(4);
    ReverseDijkstra.Result wrong =
        new ReverseDijkstra(other.heads, null, () -> false).compute(3, () -> false);
    assertThrows(IllegalArgumentException.class, () -> search.setBounds(wrong, true));
    BinaryHeapDestinationAStar canceled =
        new BinaryHeapDestinationAStar(graph.heads, null, () -> true);
    canceled.setBounds(tree, true);
    assertThrows(CancellationException.class, () -> canceled.compute(1, 3));
    search.compute(1, 3);
    assertEquals(2, search.getWeights()[3]);
  }

  @Test
  void reverseBoundsAvoidSettlingIrrelevantBranches() {
    Graph graph = new Graph(102);
    graph.edge(1, 102, 20);
    for (int node = 2; node < 102; node++) {
      graph.edge(1, node, 1);
      graph.edge(node, 102, 100);
    }
    CompactShortestPathGraph compact = new CompactShortestPathGraph(graph.heads);
    ReverseDijkstra.Result bounds =
        new ReverseDijkstra(graph.heads, compact, () -> false).compute(102, () -> false);
    BinaryHeapDestinationAStar destination =
        new BinaryHeapDestinationAStar(graph.heads, compact, () -> false);
    destination.setBounds(bounds, true);
    BinaryHeapAStar improved = new BinaryHeapAStar(graph.heads, compact);
    improved.compute(1, 102);
    destination.compute(1, 102);
    assertEquals(improved.getWeights()[102], destination.getWeights()[102]);
    long improvedSettled =
        Arrays.stream(improved.getWeights()).filter(value -> value < Double.MAX_VALUE).count();
    long destinationSettled =
        Arrays.stream(destination.getWeights()).filter(value -> value < Double.MAX_VALUE).count();
    assertEquals(102, improvedSettled);
    assertEquals(2, destinationSettled);
    destination.clearBounds();
    assertThrows(IllegalArgumentException.class, () -> destination.compute(1, 102));
  }

  @TestFactory
  List<DynamicTest> earlyStoppingRetainsExactRoutesThroughUnexploredNodes() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "compact=" + compact,
              () -> {
                Graph graph = new Graph(103);
                final AdjacencyNode direct = graph.edge(1, 102, 20);
                for (int node = 2; node < 102; node++) {
                  graph.edge(1, node, 1);
                  graph.edge(node, 102, 100);
                }
                CompactShortestPathGraph copy =
                    compact ? new CompactShortestPathGraph(graph.heads) : null;
                ReverseDijkstra reverse = new ReverseDijkstra(graph.heads, copy, () -> false);
                ReverseDijkstra.Result full = reverse.compute(102, () -> false);
                ReverseDijkstra.Result partial =
                    reverse.compute(102, new int[] {1, 1, 102}, () -> false);
                assertTrue(partial.stoppedEarly());
                assertEquals(102, full.getSettledNodeCount());
                assertEquals(2, partial.getSettledNodeCount());
                assertTrue(partial.getScannedEdgeCount() < full.getScannedEdgeCount());
                assertEquals(20, partial.distances[1]);
                assertFalse(partial.isReachable(2));
                assertFalse(partial.isKnownUnreachable(2));
                assertTrue(partial.bounds[2] > 19.9 && partial.bounds[2] <= 20);
                int[] scratch = new int[graph.heads.length];
                partial.copyPredecessors(1, scratch);
                assertEquals(1, scratch[102]);
                BinaryHeapDestinationAStar search =
                    new BinaryHeapDestinationAStar(graph.heads, copy, () -> false);
                search.setBounds(partial, true);
                // Penalties force the alternative through nodes the reverse search never finalized.
                direct.edgeWeight = 200;
                if (copy != null) {
                  copy.copyWeight(direct);
                }
                search.compute(1, 102);
                assertEquals(101, search.getWeights()[102]);
                assertTrue(search.getPredecessors()[102] != 1);
                search.compute(2, 102);
                assertEquals(100, search.getWeights()[102]);
                search.compute(103, 102);
                assertEquals(Double.MAX_VALUE, search.getWeights()[102]);
                // A required unreachable origin forces full exploration, with proven unreachable
                // labels.
                ReverseDijkstra.Result exhausted =
                    reverse.compute(102, new int[] {1, 103}, () -> false);
                assertFalse(exhausted.stoppedEarly());
                assertTrue(exhausted.isKnownUnreachable(103));
                assertEquals(full.getSettledNodeCount(), exhausted.getSettledNodeCount());
                // Previously retained snapshots remain independent of later searches and current
                // costs.
                assertEquals(20, partial.distances[1]);
                partial.copyPredecessors(1, scratch);
                assertEquals(1, scratch[102]);
                ReverseDijkstra.Result empty = reverse.compute(102, new int[0], () -> false);
                assertTrue(empty.stoppedEarly());
                assertEquals(1, empty.getSettledNodeCount());
                assertEquals(0, empty.getScannedEdgeCount());
                assertEquals(0, empty.bounds[1]);
                search.setBounds(empty, true);
                search.compute(1, 102);
                assertEquals(101, search.getWeights()[102]);
                // Empty, duplicate and canceled request sets cannot leak into the next search.
                AtomicInteger checks = new AtomicInteger();
                assertThrows(
                    CancellationException.class,
                    () ->
                        reverse.compute(
                            102, new int[] {1, 2}, () -> checks.incrementAndGet() >= 2));
                ReverseDijkstra.Result again = reverse.compute(102, new int[] {1}, () -> false);
                assertTrue(again.isReachable(1));
                assertEquals(101, again.distances[1]);
                assertThrows(
                    IllegalArgumentException.class,
                    () -> reverse.compute(102, new int[] {0}, () -> false));
                assertThrows(
                    IllegalArgumentException.class,
                    () -> reverse.compute(0, new int[] {1}, () -> false));
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> reopensCheaperLabelsAndResetsThemBetweenQueries() {
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
                ReverseDijkstra.Result bounds =
                    new ReverseDijkstra(graph.heads, copy, () -> false).compute(4, () -> false);
                // Model admissible but inconsistent bounds, which rounding guards can produce:
                // node 2 is first settled at 3, then node 3 improves its label to 2.
                Arrays.fill(bounds.bounds, 0);
                bounds.bounds[3] = 10;
                BinaryHeapDestinationAStar search =
                    new BinaryHeapDestinationAStar(graph.heads, copy, () -> false);
                search.setBounds(bounds, true);
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

  private static void checkAllPairs(
      Graph graph,
      BinaryHeapDestinationAStar search,
      ReverseDijkstra.Result[] trees,
      boolean checkFirstRoutes) {
    int count = graph.heads.length;
    double[][] edges = new double[count][count];
    double[][] expected = new double[count][];
    for (int node = 1; node < count; node++) {
      Arrays.fill(edges[node], Double.MAX_VALUE);
      edges[node][node] = 0;
      for (AdjacencyNode edge = graph.heads[node]; edge.nextNode != null; edge = edge.nextNode) {
        int next = edge.nextNode.virtualNodeNum;
        if (edge.edgeWeight < edges[node][next]) {
          edges[node][next] = edge.edgeWeight;
        }
      }
      expected[node] = edges[node].clone();
    }
    for (int via = 1; via < count; via++) {
      for (int from = 1; from < count; from++) {
        for (int to = 1; to < count; to++) {
          expected[from][to] =
              Math.min(expected[from][to], expected[from][via] + expected[via][to]);
        }
      }
    }
    int[] scratch = new int[count];
    for (int to = 1; to < count; to++) {
      search.setBounds(trees[to], true);
      for (int from = count - 1; from >= 1; from--) {
        search.compute(from, to);
        double cost = expected[from][to];
        String context = from + " -> " + to;
        if (cost == Double.MAX_VALUE) {
          assertEquals(cost, search.getWeights()[to], context);
          assertEquals(0, search.getPredecessors()[to], context);
          if (checkFirstRoutes) {
            trees[to].copyPredecessors(from, scratch);
            assertFalse(trees[to].isReachable(from));
            assertEquals(0, scratch[to]);
          }
          continue;
        }
        double tolerance = Math.ulp(cost) * count * 8;
        assertEquals(cost, search.getWeights()[to], tolerance, context);
        checkPath(from, to, search.getPredecessors(), edges, cost, tolerance);
        if (checkFirstRoutes) {
          assertTrue(trees[to].bounds[from] <= cost + tolerance, context + ": admissible bound");
          if (trees[to].isReachable(from)) {
            assertEquals(cost, trees[to].distances[from], tolerance, context);
            trees[to].copyPredecessors(from, scratch);
            checkPath(from, to, scratch, edges, cost, tolerance);
          } else {
            assertTrue(
                trees[to].stoppedEarly(),
                context + ": only a partial tree can omit a reachable node");
          }
        }
      }
    }
  }

  private static void checkPath(
      int from, int to, int[] pi, double[][] edges, double cost, double tolerance) {
    int node = to;
    double actual = 0;
    int steps = 0;
    while (node != from) {
      assertTrue(++steps < edges.length, "Predecessor cycle");
      int previous = pi[node];
      assertTrue(previous > 0, "Missing predecessor");
      actual += edges[previous][node];
      node = previous;
    }
    assertEquals(cost, actual, tolerance, from + " -> " + to + ": reconstructed cost");
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
