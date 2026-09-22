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

/**
 * A worker-local, compact copy of a fixed adjacency-list topology for Dijkstra and A* searches.
 *
 * <p>Each node's outgoing edges occupy a contiguous range of primitive destination and cost arrays.
 * The ranges preserve the linked list's order, including parallel edges, so strict relaxations
 * choose the same equal-cost paths. Searches no longer follow adjacency objects for each edge. The
 * original list remains available for reconstruction and virtual-link volume updates.
 *
 * <p>Build once after generating the worker's graph. Topology must then remain fixed. Every cost
 * change must be copied with {@link #copyWeight(AdjacencyNode)} before the next search; both
 * multi-flow workers do this in their affected-edge update passes. Other assignment workers keep
 * costs fixed within a job and rebuild after the coordinator recomputes costs for an iteration or
 * time slice. Never share mutable costs between worker jobs or rebuild during a search.
 */
public final class CompactShortestPathGraph {
  /** Original worker graph, also used to reject accidental pairing with another graph. */
  final AdjacencyNode[] graph;

  /** Edges of node n occupy [offsets[n], offsets[n + 1]). Node zero is unused. */
  final int[] offsets;

  /** Destination node number for each edge, in adjacency-list order. */
  final int[] destinations;

  /** Current costs, kept synchronized by the worker's affected-edge updates. */
  final double[] costs;

  /**
   * Copies topology and current weights without retaining one object or map entry per edge.
   *
   * @param graph Worker-owned adjacency list with node numbers matching its array indices.
   */
  public CompactShortestPathGraph(AdjacencyNode[] graph) {
    this.graph = graph;
    offsets = new int[graph.length + 1];
    int count = 0;
    for (int node = 1; node < graph.length; node++) {
      offsets[node] = count;
      for (AdjacencyNode edge = graph[node];
          edge != null && edge.nextNode != null;
          edge = edge.nextNode) {
        count = Math.addExact(count, 1);
      }
    }
    offsets[graph.length] = count;
    destinations = new int[count];
    costs = new double[count];
    int index = 0;
    for (int node = 1; node < graph.length; node++) {
      for (AdjacencyNode edge = graph[node];
          edge != null && edge.nextNode != null;
          edge = edge.nextNode) {
        edge.compactEdgeIndex = index;
        destinations[index] = edge.nextNode.virtualNodeNum;
        costs[index++] = edge.edgeWeight;
      }
    }
  }

  /**
   * Copies one modified weight; no graph scan is needed between searches.
   *
   * @param edge An outgoing edge belonging to the original graph, with its updated weight.
   */
  public void copyWeight(AdjacencyNode edge) {
    costs[edge.compactEdgeIndex] = edge.edgeWeight;
  }
}
