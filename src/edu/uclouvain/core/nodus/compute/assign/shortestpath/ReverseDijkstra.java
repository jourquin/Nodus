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
 * Reverse Dijkstra trees for one worker's fixed directed topology and current costs.
 *
 * <p>The reversed edges reference live forward costs, including loading restrictions. Each result
 * owns its distances and next-hop tree, so trees for several loading modes can coexist. A result
 * supplies first routes and lower bounds for later searches whose costs only increase. Rebuild it
 * after changing the destination, group or cost class; never build it from OD-specific penalties.
 *
 * <p>Searches can stop as soon as all requested origins have been settled. If R is the last settled
 * distance, every unsettled node has distance at least R. Its bound is therefore R, never a
 * tentative distance or an unreachable flag. This is the admissible capped potential
 * min(d(v,goal),R); full searches retain their original distance bounds. Requested origins retain
 * complete first routes in either case. Unreachable requested origins require exhausting the finite
 * frontier.
 *
 * <p>Reference: E. W. Dijkstra (1959). <a href="https://doi.org/10.1007/BF01386390">A note on two
 * problems in connexion with graphs</a>. <i>Numerische Mathematik</i>, 1, pp. 269-271. Applying the
 * algorithm to reversed directed edges gives distances to the destination in the original graph.
 */
public final class ReverseDijkstra {
  private final AdjacencyNode[] graph;
  private final CompactShortestPathGraph compact;
  private final int[] offsets;
  private final int[] sources;
  private final int[] edgeIds;
  private final AdjacencyNode[] links;
  private final CompactShortestPathHeap heap;
  private final int[] requested;
  private int requestGeneration;

  /**
   * Indexes reverse topology once, without copying mutable edge costs.
   *
   * @param graph worker-owned graph with nonnegative costs; node zero is unused
   * @param compact synchronized compact graph, or null for linked costs
   * @param cancelled cancellation check during construction
   */
  public ReverseDijkstra(
      AdjacencyNode[] graph, CompactShortestPathGraph compact, BooleanSupplier cancelled) {
    if (compact != null && compact.graph != graph) {
      throw new IllegalArgumentException("Compact graph must belong to this adjacency list");
    }
    this.graph = graph;
    this.compact = compact;
    offsets = new int[graph.length + 1];
    checkCancelled(cancelled);
    int visited = 0;
    for (int node = 1; node < graph.length; node++) {
      if ((node & 1023) == 0) {
        checkCancelled(cancelled);
      }
      for (AdjacencyNode edge = graph[node];
          edge != null && edge.nextNode != null;
          edge = edge.nextNode) {
        if ((visited++ & 1023) == 0) {
          checkCancelled(cancelled);
        }
        requireNonnegative(edge.edgeWeight);
        offsets[edge.nextNode.virtualNodeNum + 1]++;
      }
    }
    for (int node = 1; node < offsets.length; node++) {
      offsets[node] = Math.addExact(offsets[node], offsets[node - 1]);
    }
    int count = offsets[graph.length];
    sources = new int[count];
    edgeIds = compact == null ? null : new int[count];
    links = compact == null ? new AdjacencyNode[count] : null;
    int[] cursor = offsets.clone();
    visited = 0;
    for (int node = 1; node < graph.length; node++) {
      if ((node & 1023) == 0) {
        checkCancelled(cancelled);
      }
      for (AdjacencyNode edge = graph[node];
          edge != null && edge.nextNode != null;
          edge = edge.nextNode) {
        if ((visited++ & 1023) == 0) {
          checkCancelled(cancelled);
        }
        int slot = cursor[edge.nextNode.virtualNodeNum]++;
        sources[slot] = node;
        if (compact == null) {
          links[slot] = edge;
        } else {
          edgeIds[slot] = edge.compactEdgeIndex;
        }
      }
    }
    heap = new CompactShortestPathHeap(new int[graph.length], false);
    requested = new int[graph.length];
  }

  /**
   * Computes all distances to a destination using current, unpenalized loading-mode costs.
   *
   * @param goal destination virtual node
   * @param cancelled cancellation check during the search
   * @return independent first-route tree and baseline bounds
   */
  public Result compute(int goal, BooleanSupplier cancelled) {
    return compute(goal, null, cancelled);
  }

  /**
   * Stops once all distinct required origins have final distances and first routes.
   *
   * @param goal destination virtual node
   * @param requiredSources required loading virtual nodes; null computes a full tree, and an empty
   *     array stops at the goal with zero bounds elsewhere
   * @param cancelled cancellation check during the search
   * @return independent tree with exact settled distances and safe bounds elsewhere
   */
  public Result compute(int goal, int[] requiredSources, BooleanSupplier cancelled) {
    checkCancelled(cancelled);
    if (goal <= 0 || goal >= graph.length) {
      throw new IllegalArgumentException("Invalid destination virtual node");
    }
    if (requestGeneration == Integer.MAX_VALUE) {
      Arrays.fill(requested, 0);
      requestGeneration = 0;
    }
    requestGeneration++;
    int remaining = 0;
    if (requiredSources != null) {
      for (int source : requiredSources) {
        if (source <= 0 || source >= graph.length) {
          throw new IllegalArgumentException("Invalid origin virtual node");
        }
        if (requested[source] != requestGeneration) {
          requested[source] = requestGeneration;
          remaining++;
        }
      }
    }
    double[] distances = new double[graph.length];
    Arrays.fill(distances, Double.MAX_VALUE);
    int[] next = new int[graph.length];
    heap.initialize(goal, next, distances);
    int visited = 0;
    int settled = 0;
    long scanned = 0;
    double cutoff = Double.MAX_VALUE;
    int node;
    while ((node = heap.extractMin()) != -1 && heap.minWeight != Double.MAX_VALUE) {
      if ((visited++ & 1023) == 0) {
        checkCancelled(cancelled);
      }
      distances[node] = heap.minWeight;
      settled++;
      if (requiredSources != null) {
        if (requested[node] == requestGeneration) {
          remaining--;
        }
        if (remaining == 0) {
          // No incoming edge can lead to a distance below this radius, even if it has not
          // been relaxed yet. Nodes outside the settled tree remain available to forward A*.
          cutoff = heap.minWeight;
          break;
        }
      }
      for (int edge = offsets[node]; edge < offsets[node + 1]; edge++) {
        scanned++;
        if ((visited++ & 1023) == 0) {
          checkCancelled(cancelled);
        }
        double cost = compact == null ? links[edge].edgeWeight : compact.costs[edgeIds[edge]];
        requireNonnegative(cost);
        double candidate = heap.minWeight + cost;
        if (candidate < Double.MAX_VALUE && heap.improve(sources[edge], candidate)) {
          next[sources[edge]] = node;
        }
      }
    }
    checkCancelled(cancelled);
    return new Result(graph, goal, distances, next, cutoff, settled, scanned);
  }

  private static void requireNonnegative(double cost) {
    if (cost < 0) {
      throw new IllegalArgumentException("Reverse Dijkstra requires nonnegative edge costs");
    }
  }

  private static void checkCancelled(BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean()) {
      throw new CancellationException("Reverse Dijkstra cancelled");
    }
  }

  /** Immutable finalized distances, next hops and safe bounds for one destination/loading mode. */
  public static final class Result {
    final AdjacencyNode[] graph;
    final int goal;
    final double[] distances;
    final double[] bounds;
    final double cutoff;
    private final int[] next;
    private final int settled;
    private final long scanned;

    private Result(
        AdjacencyNode[] graph,
        int goal,
        double[] distances,
        int[] next,
        double cutoff,
        int settled,
        long scanned) {
      this.graph = graph;
      this.goal = goal;
      this.distances = distances;
      this.next = next;
      this.cutoff = cutoff;
      this.settled = settled;
      this.scanned = scanned;
      bounds = new double[distances.length];
      // The unexplored portion shares one bound: avoid repeating its rounding calculation
      // for every vertex when an early stop leaves most of the network untouched.
      double lowerCutoff = stoppedEarly() ? roundedBound(cutoff) : 0;
      for (int node = 1; node < distances.length; node++) {
        // Reverse and forward accumulation can round differently, especially across cost scales.
        double distance = distances[node];
        bounds[node] = distance == Double.MAX_VALUE ? lowerCutoff : roundedBound(distance);
      }
    }

    private double roundedBound(double distance) {
      return Math.max(0, distance - (4.0 * graph.length) * Math.ulp(distance));
    }

    /**
     * Returns whether a finite baseline route was finalized. All required origins are resolved;
     * false for an unrequested, unexplored node does not prove it unreachable.
     *
     * @param source origin virtual node
     * @return true if the tree contains a finalized route from this origin
     */
    public boolean isReachable(int source) {
      return distances[source] != Double.MAX_VALUE;
    }

    /**
     * Whether the search stopped after settling the requested origins.
     *
     * @return true when the remaining graph was left unexplored
     */
    public boolean stoppedEarly() {
      return cutoff != Double.MAX_VALUE;
    }

    /**
     * Number of finalized reverse distances.
     *
     * @return settled node count
     */
    public int getSettledNodeCount() {
      return settled;
    }

    /**
     * Number of reverse edges examined.
     *
     * @return scanned edge count
     */
    public long getScannedEdgeCount() {
      return scanned;
    }

    /** An unexplored node is unreachable only when the finite frontier was exhausted. */
    boolean isKnownUnreachable(int node) {
      return !stoppedEarly() && !isReachable(node);
    }

    /**
     * Copies one tree route into forward predecessors in time proportional to its length.
     *
     * <p>Other entries of the caller's scratch array are untouched. An unreachable route clears the
     * destination predecessor; callers must consume this route before copying another one.
     *
     * @param source origin virtual node
     * @param predecessors caller-owned scratch array of graph length
     */
    public void copyPredecessors(int source, int[] predecessors) {
      predecessors[goal] = 0;
      if (!isReachable(source)) {
        return;
      }
      int node = source;
      while (node != goal) {
        int successor = next[node];
        predecessors[successor] = node;
        node = successor;
      }
      predecessors[source] = 0;
    }
  }
}
