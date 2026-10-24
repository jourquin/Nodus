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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/** Checks both search algorithms with linked and compact graph storage. */
class ShortestPathTest {

  @TestFactory
  List<DynamicTest> searches() {
    List<DynamicTest> tests = new ArrayList<>();
    for (SearchKind kind : SearchKind.values()) {
      add(
          tests,
          kind,
          "chooses the cheaper indirect route",
          () -> {
            Graph graph = new Graph(5);
            graph.edge(1, 2, 8);
            graph.edge(1, 3, 2);
            graph.edge(3, 2, 1);
            graph.edge(2, 4, 2);
            graph.edge(3, 4, 9);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 4);
            assertPath(search, 5, 1, 3, 2, 4);
          });
      add(
          tests,
          kind,
          "preserves the first predecessor on an equal-cost route",
          () -> {
            Graph graph = new Graph(4);
            graph.edge(1, 2, 1);
            graph.edge(1, 3, 1);
            graph.edge(2, 4, 1);
            graph.edge(3, 4, 1);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 4);
            assertPath(search, 2, 1, 2, 4);
          });
      add(
          tests,
          kind,
          "handles parallel edges and zero-cost cycles",
          () -> {
            Graph graph = new Graph(4);
            graph.edge(1, 2, 9);
            graph.edge(1, 2, 0);
            graph.edge(2, 1, 0);
            graph.edge(2, 3, 2);
            graph.edge(1, 3, 8);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 3);
            assertPath(search, 2, 1, 2, 3);
          });
      add(
          tests,
          kind,
          "clears results when the source changes after an early stop",
          () -> {
            Graph graph = new Graph(5);
            graph.edge(1, 2, 1);
            graph.edge(1, 3, 10);
            graph.edge(2, 3, 1);
            graph.edge(4, 5, 3);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 2);
            assertPath(search, 1, 1, 2);
            search.compute(4, 5);
            assertPath(search, 3, 4, 5);
            assertEquals(Double.MAX_VALUE, search.getWeights()[2]);
            assertEquals(0, search.getPredecessors()[2]);
            assertEquals(0, search.getPredecessors()[3]);
            search.compute(1, 3);
            assertPath(search, 2, 1, 2, 3);
          });
      add(
          tests,
          kind,
          "reports an unreachable goal after a successful search",
          () -> {
            Graph graph = new Graph(3);
            graph.edge(1, 2, 1);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 2);
            search.compute(3, 2);
            assertEquals(Double.MAX_VALUE, search.getWeights()[2]);
            assertEquals(0, search.getPredecessors()[2]);
          });
      add(
          tests,
          kind,
          "returns zero for a source that is also the goal",
          () -> {
            BinaryHeapDijkstra search = kind.create(new Graph(1));
            search.compute(1, 1);
            assertPath(search, 0, 1);
          });
      add(
          tests,
          kind,
          "uses updated edge costs on the next search",
          () -> {
            Graph graph = new Graph(3);
            final AdjacencyNode changed = graph.edge(1, 2, 1);
            graph.edge(2, 3, 1);
            graph.edge(1, 3, 5);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 3);
            assertPath(search, 2, 1, 2, 3);
            changed.edgeWeight = 10;
            if (search.compactGraph != null) {
              search.compactGraph.copyWeight(changed);
            }
            search.compute(1, 3);
            assertPath(search, 5, 1, 3);
          });
      add(
          tests,
          kind,
          "ignores non-finite and overflowing routes",
          () -> {
            Graph graph = new Graph(5);
            graph.edge(1, 2, Double.NaN);
            graph.edge(1, 3, Double.POSITIVE_INFINITY);
            graph.edge(1, 4, 1e308);
            graph.edge(4, 5, 1e308);
            graph.edge(1, 5, 7);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 5);
            assertPath(search, 7, 1, 5);
            search.compute(1, 3);
            assertEquals(Double.MAX_VALUE, search.getWeights()[3]);
            assertEquals(Double.MAX_VALUE, search.getWeights()[2]);
            assertEquals(0, search.getPredecessors()[3]);
          });
      add(
          tests,
          kind,
          "finds the optimum with nonzero coordinates",
          () -> {
            Graph graph = new Graph(4);
            graph.nodes[2] = node(2, 1, 1);
            graph.nodes[3] = node(3, 1, 0);
            graph.nodes[4] = node(4, 2, 0);
            graph.resetHeads();
            graph.edge(1, 2, 2);
            graph.edge(2, 4, 2);
            graph.edge(1, 3, 1);
            graph.edge(3, 4, 1);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 4);
            assertPath(search, 2, 1, 3, 4);
          });
      add(
          tests,
          kind,
          "finds the cheaper detour when coordinates overestimate transport costs",
          () -> {
            Graph graph = detourGraph();
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 3);
            assertPath(search, 0.2, 1, 2, 3);
          });
      add(
          tests,
          kind,
          "handles zero-cost movement and cycles between different coordinates",
          () -> {
            Graph graph = new Graph(4);
            graph.nodes[2] = node(2, -10, 5);
            graph.nodes[3] = node(3, 20, -5);
            graph.nodes[4] = node(4, 1, 0);
            graph.resetHeads();
            graph.edge(1, 4, 0.5);
            graph.edge(1, 2, 0);
            graph.edge(2, 3, 0);
            graph.edge(3, 2, 0);
            graph.edge(3, 4, 0);
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 4);
            assertPath(search, 0, 1, 2, 3, 4);
          });
      add(
          tests,
          kind,
          "reuses a search after cost decreases, restoration and goal changes",
          () -> {
            Graph graph = detourGraph();
            final AdjacencyNode first = graph.heads[1].nextNode;
            first.edgeWeight = 1;
            BinaryHeapDijkstra search = kind.create(graph);
            search.compute(1, 3);
            assertPath(search, 0.5, 1, 3);
            first.edgeWeight = 0.1;
            if (search.compactGraph != null) {
              search.compactGraph.copyWeight(first);
            }
            search.compute(1, 3);
            assertPath(search, 0.2, 1, 2, 3);
            search.compute(1, 2);
            assertPath(search, 0.1, 1, 2);
            search.compute(2, 3);
            assertPath(search, 0.1, 2, 3);
            first.edgeWeight = 1;
            if (search.compactGraph != null) {
              search.compactGraph.copyWeight(first);
            }
            search.compute(1, 3);
            assertPath(search, 0.5, 1, 3);
          });
      add(
          tests,
          kind,
          "keeps route choice independent of the chosen cost unit",
          () -> {
            for (double scale : new double[] {1e-200, 1e-9, 1, 1e9, 1e200}) {
              Graph graph = detourGraph();
              for (int from = 1; from < graph.heads.length; from++) {
                for (AdjacencyNode edge = graph.heads[from];
                    edge.nextNode != null;
                    edge = edge.nextNode) {
                  edge.edgeWeight *= scale;
                }
              }
              BinaryHeapDijkstra search = kind.create(graph);
              search.compute(1, 3);
              final double expected = 0.2 * scale;
              assertEquals(expected, search.getWeights()[3], Math.ulp(expected) * 4);
              assertEquals(2, search.getPredecessors()[3]);
              assertEquals(1, search.getPredecessors()[2]);
            }
          });
      add(
          tests,
          kind,
          "invalid or extreme coordinates cannot poison valid route costs",
          () -> {
            for (double coordinate : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1e308}) {
              Graph graph = detourGraph();
              graph.nodes[2] = node(2, coordinate, coordinate);
              graph.resetHeads();
              graph.edge(1, 3, 0.5);
              graph.edge(1, 2, 0.1);
              graph.edge(2, 3, 0.1);
              BinaryHeapDijkstra search = kind.create(graph);
              search.compute(1, 3);
              assertPath(search, 0.2, 1, 2, 3);
            }
          });
      add(
          tests,
          kind,
          "matches an independent all-pairs reference on seeded networks",
          () -> checkAgainstReference(kind, false));
      add(
          tests,
          kind,
          "matches the reference with nonzero coordinates and different cost units",
          () -> checkAgainstReference(kind, true));
    }
    return tests;
  }

  @Test
  void rejectsACompactGraphFromAnotherAdjacencyList() {
    Graph first = new Graph(2);
    Graph second = new Graph(2);
    CompactShortestPathGraph compact = new CompactShortestPathGraph(first.heads);
    assertThrows(
        IllegalArgumentException.class, () -> new BinaryHeapDijkstra(second.heads, null, compact));
    assertThrows(IllegalArgumentException.class, () -> new BinaryHeapAStar(second.heads, compact));
    assertThrows(
        IllegalArgumentException.class, () -> new LegacyBinaryHeapAStar(second.heads, compact));
    assertThrows(IllegalArgumentException.class, () -> new BinaryHeapALT(second.heads, compact));
  }

  @TestFactory
  List<DynamicTest> legacySearchRetainsThePreviousGeographicHeuristicAndHeapBehavior() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean compact : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "compact=" + compact,
              () -> {
                final Graph graph = detourGraph();
                final CompactShortestPathGraph copy =
                    compact ? new CompactShortestPathGraph(graph.heads) : null;
                final BinaryHeapAStar search = new LegacyBinaryHeapAStar(graph.heads, copy);
                // This deliberately preserves the old overestimate, for honest timing comparisons.
                search.compute(1, 3);
                assertPath(search, 0.5, 1, 3);
                final AdjacencyNode direct = graph.heads[1];
                direct.edgeWeight = Double.POSITIVE_INFINITY;
                if (copy != null) {
                  copy.copyWeight(direct);
                }
                search.compute(1, 3);
                assertPath(search, 0.2, 1, 2, 3);
                search.compute(1, 2);
                assertPath(search, 0.1, 1, 2);
                direct.edgeWeight = 0.5;
                if (copy != null) {
                  copy.copyWeight(direct);
                }
                search.compute(1, 3);
                assertPath(search, 0.5, 1, 3);
              }));
    }
    return tests;
  }

  private static void add(List<DynamicTest> tests, SearchKind kind, String name, Executable check) {
    tests.add(dynamicTest(kind + ": " + name, check));
  }

  private static void assertPath(BinaryHeapDijkstra search, double cost, int... nodes) {
    assertEquals(cost, search.getWeights()[nodes[nodes.length - 1]], 1e-12);
    assertEquals(0, search.getPredecessors()[nodes[0]]);
    for (int i = 1; i < nodes.length; i++) {
      assertEquals(nodes[i - 1], search.getPredecessors()[nodes[i]], "predecessor of " + nodes[i]);
    }
  }

  /** Floyd-Warshall is deliberately independent of both production search/heap implementations. */
  private static void checkAgainstReference(SearchKind kind, boolean geographic) {
    Random random = new Random(20260924L);
    for (int sample = 0; sample < 8; sample++) {
      int count = 7;
      Graph graph = new Graph(count);
      if (geographic) {
        for (int node = 1; node <= count; node++) {
          graph.nodes[node] =
              node(node, random.nextDouble() * 360 - 180, random.nextDouble() * 180 - 90);
        }
        graph.resetHeads();
      }
      final double scale = geographic ? Math.pow(10, sample - 4) : 1;
      double[][] distances = new double[count + 1][count + 1];
      for (int from = 1; from <= count; from++) {
        Arrays.fill(distances[from], Double.POSITIVE_INFINITY);
        distances[from][from] = 0;
        // A directed ring guarantees reachability while extra edges add ties and cycles.
        int next = from % count + 1;
        double cost = random.nextInt(6) * scale;
        graph.edge(from, next, cost);
        distances[from][next] = cost;
        for (int to = 1; to <= count; to++) {
          if (from != to && random.nextBoolean()) {
            cost = random.nextInt(10) * scale;
            graph.edge(from, to, cost);
            distances[from][to] = Math.min(distances[from][to], cost);
          }
        }
      }
      double[][] edgeCosts = new double[count + 1][];
      for (int i = 1; i <= count; i++) {
        edgeCosts[i] = distances[i].clone();
      }
      for (int via = 1; via <= count; via++) {
        for (int from = 1; from <= count; from++) {
          for (int to = 1; to <= count; to++) {
            distances[from][to] =
                Math.min(distances[from][to], distances[from][via] + distances[via][to]);
          }
        }
      }
      BinaryHeapDijkstra search = kind.create(graph);
      for (int from = count; from >= 1; from--) {
        for (int to = 1; to <= count; to++) {
          String context = kind + " sample " + sample + ", " + from + " -> " + to;
          search.compute(from, to);
          final double tolerance = Math.ulp(distances[from][to]) * 16;
          assertEquals(distances[from][to], search.getWeights()[to], tolerance, context);
          int current = to;
          int steps = 0;
          double pathCost = 0;
          while (current != from) {
            assertTrue(++steps <= count, context + ": predecessor cycle");
            int previous = search.getPredecessors()[current];
            assertTrue(previous > 0, context + ": missing predecessor");
            pathCost += edgeCosts[previous][current];
            current = previous;
          }
          assertEquals(distances[from][to], pathCost, tolerance, context + ": reconstructed cost");
        }
      }
    }
  }

  private static Graph detourGraph() {
    Graph graph = new Graph(3);
    graph.nodes[2] = node(2, -1, 0);
    graph.nodes[3] = node(3, 1, 0);
    graph.resetHeads();
    graph.edge(1, 3, 0.5);
    graph.edge(1, 2, 0.1);
    graph.edge(2, 3, 0.1);
    return graph;
  }

  private enum SearchKind {
    DIJKSTRA_LINKED(false, false),
    DIJKSTRA_COMPACT(false, true),
    ASTAR_LINKED(true, false),
    ASTAR_COMPACT(true, true);

    private final boolean astar;
    private final boolean compact;

    SearchKind(boolean astar, boolean compact) {
      this.astar = astar;
      this.compact = compact;
    }

    BinaryHeapDijkstra create(Graph graph) {
      CompactShortestPathGraph copy = compact ? new CompactShortestPathGraph(graph.heads) : null;
      return astar
          ? new BinaryHeapAStar(graph.heads, copy)
          : new BinaryHeapDijkstra(graph.heads, null, copy);
    }
  }

  private static VirtualNode node(int id, double longitude, double latitude) {
    return new VirtualNode(id, id, 0, (byte) 1, (byte) 1, (short) 0, latitude, longitude);
  }

  /** Builds the same sentinel-ended adjacency lists used by Nodus, without a database. */
  private static final class Graph {
    final VirtualNode[] nodes;
    final AdjacencyNode[] heads;

    Graph(int count) {
      nodes = new VirtualNode[count + 1];
      heads = new AdjacencyNode[count + 1];
      for (int i = 1; i <= count; i++) {
        nodes[i] = node(i, 0, 0);
      }
      resetHeads();
    }

    void resetHeads() {
      for (int i = 1; i < heads.length; i++) {
        heads[i] = new AdjacencyNode(nodes[i]);
      }
    }

    AdjacencyNode edge(int from, int to, double cost) {
      AdjacencyNode edge = heads[from];
      while (edge.nextNode != null) {
        edge = edge.nextNode;
      }
      edge.edgeWeight = cost;
      edge.nextNode = new AdjacencyNode(nodes[to]);
      return edge;
    }
  }
}
