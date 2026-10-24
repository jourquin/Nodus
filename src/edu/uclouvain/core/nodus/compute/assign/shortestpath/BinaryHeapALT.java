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

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Directed ALT (A*, landmarks and triangle inequality) for Exact Multi-flow.
 *
 * <p>Preprocessing snapshots the worker's unpenalized, unrestricted costs once. All subsequent
 * costs must stay at or above this baseline; markups, blocked loading edges and restoration to
 * baseline are supported without rebuilding the tables. Tables are private to one worker job. Both
 * linked and compact searches read their current edge costs, never the snapshot.
 *
 * <p>For landmark L, the maximum of zero, d(L,t)-d(L,v), and d(v,L)-d(t,L) bounds the remaining
 * cost from v to t. Only finite differences are used. Missing distances in directed components can
 * make these admissible bounds inconsistent, so a cheaper label can reopen a settled node.
 * Conservative rounding guards protect cost differences across very different cost scales.
 *
 * <p>References:
 *
 * <ul>
 *   <li>A. V. Goldberg and C. Harrelson (2005).
 *       <a href="https://www.microsoft.com/en-us/research/wp-content/uploads/2005/01/soda05.pdf">
 *       Computing the Shortest Path: A* Search Meets Graph Theory</a>. <i>Proceedings of the 16th
 *       Annual ACM-SIAM Symposium on Discrete Algorithms (SODA)</i>, pp. 156-165. Introduces the
 *       directed landmark bounds used here.
 *   <li>D. Delling (2009).
 *       <a href="https://publikationen.bibliothek.kit.edu/1000011046">Engineering and Augmenting
 *       Route Planning Algorithms</a>. PhD thesis, Universit&auml;t Karlsruhe (TH), section 4.1.2.
 *       Describes reusing landmark potentials while costs remain at or above their baseline.
 * </ul>
 */
public class BinaryHeapALT extends BinaryHeapAStar {
  /** Maximum number of deterministic, farthest-first landmarks per worker job. */
  public static final int DEFAULT_LANDMARKS = 16;

  private final double[][] fromLandmarks;
  private final double[][] toLandmarks;
  private final double[] estimates;
  private final int[] estimateGoals;
  private int goal;

  /**
   * Creates landmark tables and uses linked graph/heap storage for queries.
   *
   * @param graph worker-owned graph with nonnegative costs
   */
  public BinaryHeapALT(AdjacencyNode[] graph) {
    this(graph, null);
  }

  /**
   * Creates landmark tables and selects the requested query storage.
   *
   * @param graph worker-owned graph with nonnegative costs
   * @param compactGraph synchronized compact graph, or null for linked queries
   */
  public BinaryHeapALT(AdjacencyNode[] graph, CompactShortestPathGraph compactGraph) {
    this(graph, compactGraph, DEFAULT_LANDMARKS, () -> false);
  }

  /**
   * Builds up to landmarkCount tables before any cost penalties or loading restrictions.
   *
   * @param graph worker-owned graph with nonnegative costs
   * @param compactGraph synchronized compact graph, or null for linked queries
   * @param landmarkCount maximum table count; zero retains safe Dijkstra ordering when costs may
   *     fall below their initial values
   * @param cancelled cancellation check during preprocessing
   */
  public BinaryHeapALT(
      AdjacencyNode[] graph,
      CompactShortestPathGraph compactGraph,
      int landmarkCount,
      BooleanSupplier cancelled) {
    super(graph, compactGraph, true);
    if (landmarkCount < 0) {
      throw new IllegalArgumentException("Landmark count must be nonnegative");
    }
    checkCancelled(cancelled);
    estimates = new double[graph.length];
    estimateGoals = new int[graph.length];
    double[][][] tables = preprocess(graph, compactGraph, landmarkCount, cancelled);
    fromLandmarks = tables[0];
    toLandmarks = tables[1];
  }

  /** Searches the current penalized graph, reusing the original landmark tables. */
  @Override
  public void compute(int source, int goal) {
    this.goal = goal;
    initializeSingleSource(source);
    int min = extractMin();
    while (min != -1 && minWeight != Double.MAX_VALUE) {
      weights[min] = minWeight;
      if (min == goal) {
        break;
      }
      if (compactGraph != null) {
        for (int edge = compactGraph.offsets[min]; edge < compactGraph.offsets[min + 1]; edge++) {
          relax(min, compactGraph.destinations[edge], compactGraph.costs[edge]);
        }
      } else {
        for (AdjacencyNode edge = graph[min]; edge.nextNode != null; edge = edge.nextNode) {
          relax(min, edge.nextNode.virtualNodeNum, edge.edgeWeight);
        }
      }
      min = extractMin();
    }
  }

  /** Uses cached goal bounds, allowing improved labels to reopen previously settled nodes. */
  @Override
  public void relax(int from, int to, double cost) {
    double candidate = weights[from] + cost;
    if (!(candidate < Double.MAX_VALUE)) {
      return;
    }
    if (nodePos[to] == -1) {
      if (!(candidate < weights[to])) {
        return;
      }
      reopen(to);
    }
    if (compactHeap != null) {
      if (compactHeap.needsEstimate(to)) {
        compactHeap.setEstimate(to, estimateRemainingCost(to, goal));
      }
      if (compactHeap.improve(to, candidate)) {
        pi[to] = from;
      }
    } else {
      BinaryHeapNode node = getHeapNode(getNodePosition(to));
      if (node.weight > candidate) {
        node.updateGoalEstWeight(estimateRemainingCost(to, goal));
        decreaseKey(to, candidate);
        pi[to] = from;
      }
    }
  }

  /** Number of selected landmarks; package-visible for correctness tests. */
  int landmarkCount() {
    return fromLandmarks.length;
  }

  /** Caches a node's bound across all alternatives and modes for the same destination. */
  double estimateRemainingCost(int node, int target) {
    if (estimateGoals[node] != target) {
      double estimate = 0;
      for (int i = 0; i < fromLandmarks.length; i++) {
        estimate =
            Math.max(estimate, lowerDifference(fromLandmarks[i][target], fromLandmarks[i][node]));
        estimate =
            Math.max(estimate, lowerDifference(toLandmarks[i][node], toLandmarks[i][target]));
      }
      estimates[node] = estimate;
      estimateGoals[node] = target;
    }
    return estimates[node];
  }

  /** Omits unavailable bounds and rounds differences down with an accumulated-error allowance. */
  private double lowerDifference(double first, double second) {
    if (first == Double.MAX_VALUE || second == Double.MAX_VALUE || first <= second) {
      return 0;
    }
    double roundoff = (4.0 * graph.length) * Math.ulp(first);
    return Math.max(0, first - second - roundoff);
  }

  /**
   * Builds both directions using temporary CSR topology and the existing primitive Dijkstra heap.
   * Only the landmark distance tables survive preprocessing; no reverse graph or cost copy is kept.
   */
  private static double[][][] preprocess(
      AdjacencyNode[] graph,
      CompactShortestPathGraph compact,
      int limit,
      BooleanSupplier cancelled) {
    if (limit == 0 || graph.length <= 1) {
      return new double[][][] {new double[0][], new double[0][]};
    }
    CompactShortestPathGraph baseline =
        compact == null ? new CompactShortestPathGraph(graph) : compact;
    double[] costs = baseline.costs;
    int[] reverseOffsets = new int[graph.length + 1];
    int[] outDegrees = new int[graph.length];
    int[] inDegrees = new int[graph.length];
    for (int node = 1; node < graph.length; node++) {
      for (int edge = baseline.offsets[node]; edge < baseline.offsets[node + 1]; edge++) {
        double cost = costs[edge];
        if (cost < 0) {
          throw new IllegalArgumentException("ALT requires nonnegative edge costs");
        }
        int next = baseline.destinations[edge];
        reverseOffsets[next + 1]++;
        if (cost < Double.MAX_VALUE) {
          outDegrees[node]++;
          inDegrees[next]++;
        }
      }
    }
    for (int node = 1; node < reverseOffsets.length; node++) {
      reverseOffsets[node] += reverseOffsets[node - 1];
    }
    int[] reverseNodes = new int[costs.length];
    int[] reverseEdges = new int[costs.length];
    int[] cursor = reverseOffsets.clone();
    for (int node = 1; node < graph.length; node++) {
      for (int edge = baseline.offsets[node]; edge < baseline.offsets[node + 1]; edge++) {
        int slot = cursor[baseline.destinations[edge]]++;
        reverseNodes[slot] = node;
        reverseEdges[slot] = edge;
      }
    }

    // Prefer interior transport nodes over source-only loading and sink-only unloading nodes.
    boolean hasInterior = false;
    for (int node = 1; node < graph.length; node++) {
      hasInterior |= outDegrees[node] > 0 && inDegrees[node] > 0;
    }
    boolean[] eligible = new boolean[graph.length];
    int count = 0;
    int landmark = 0;
    for (int node = 1; node < graph.length; node++) {
      eligible[node] =
          hasInterior
              ? outDegrees[node] > 0 && inDegrees[node] > 0
              : outDegrees[node] + inDegrees[node] > 0;
      if (eligible[node]) {
        count++;
        if (landmark == 0
            || outDegrees[node] + inDegrees[node] > outDegrees[landmark] + inDegrees[landmark]) {
          landmark = node;
        }
      }
    }
    count = Math.min(limit, count);
    double[][] from = new double[count][];
    double[][] to = new double[count][];
    double[] closest = new double[graph.length];
    Arrays.fill(closest, Double.MAX_VALUE);
    int[] predecessors = new int[graph.length];
    CompactShortestPathHeap heap = new CompactShortestPathHeap(new int[graph.length], false);
    for (int i = 0; i < count; i++) {
      checkCancelled(cancelled);
      from[i] =
          distances(
              landmark,
              baseline.offsets,
              baseline.destinations,
              null,
              costs,
              heap,
              predecessors,
              cancelled);
      to[i] =
          distances(
              landmark,
              reverseOffsets,
              reverseNodes,
              reverseEdges,
              costs,
              heap,
              predecessors,
              cancelled);
      eligible[landmark] = false;
      int next = 0;
      for (int node = 1; node < graph.length; node++) {
        closest[node] = Math.min(closest[node], Math.min(from[i][node], to[i][node]));
        if (eligible[node] && (next == 0 || closest[node] > closest[next])) {
          next = node;
        }
      }
      landmark = next;
    }
    return new double[][][] {from, to};
  }

  /** Runs a full directed baseline search; non-finite and overflowing routes remain unavailable. */
  private static double[] distances(
      int source,
      int[] offsets,
      int[] destinations,
      int[] edgeIds,
      double[] costs,
      CompactShortestPathHeap heap,
      int[] predecessors,
      BooleanSupplier cancelled) {
    double[] result = new double[predecessors.length];
    Arrays.fill(result, Double.MAX_VALUE);
    heap.initialize(source, predecessors, result);
    int settled = 0;
    int node;
    while ((node = heap.extractMin()) != -1 && heap.minWeight != Double.MAX_VALUE) {
      if ((settled++ & 1023) == 0) {
        checkCancelled(cancelled);
      }
      result[node] = heap.minWeight;
      for (int edge = offsets[node]; edge < offsets[node + 1]; edge++) {
        double candidate = heap.minWeight + costs[edgeIds == null ? edge : edgeIds[edge]];
        if (candidate < Double.MAX_VALUE) {
          heap.improve(destinations[edge], candidate);
        }
      }
    }
    return result;
  }

  private static void checkCancelled(BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean()) {
      throw new CancellationException("ALT preprocessing cancelled");
    }
  }
}
