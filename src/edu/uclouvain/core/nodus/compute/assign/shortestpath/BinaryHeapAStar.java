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
 * Single-goal A* search with a zero heuristic for arbitrary nonnegative transport costs.
 *
 * <p>Geographic distance has no guaranteed relationship to a user-defined cost function. Using it
 * as a remaining-cost estimate can overestimate a cheap route, including one with zero-cost
 * movement, and stop at a more expensive goal. A zero estimate is always admissible and consistent;
 * heap ordering therefore follows Dijkstra's algorithm, independent of coordinates and cost units.
 *
 * <p>The search reuses the linked or compact heap without scanning all edges before each OD pair.
 * Exact multi-flow still searches and penalizes alternatives separately for each OD pair.
 *
 * @author Bart Jourquin
 */
public class BinaryHeapAStar extends BinaryHeapDijkstra {
  /**
   * Initializes a single-goal search using the original adjacency-list storage.
   *
   * @param graph worker-owned graph with nonnegative edge costs
   */
  public BinaryHeapAStar(AdjacencyNode[] graph) {
    this(graph, null);
  }

  /**
   * Selects the optional compact graph and heap while retaining the same route ordering.
   *
   * @param graph worker-owned graph with nonnegative edge costs
   * @param compactGraph synchronized compact copy, or null for the original implementation
   */
  public BinaryHeapAStar(AdjacencyNode[] graph, CompactShortestPathGraph compactGraph) {
    super(graph, null, compactGraph);
  }

  /** {@inheritDoc} */
  @Override
  public void compute(int source, int goal) {
    super.compute(source, goal);
  }

  /** {@inheritDoc} */
  @Override
  public void decreaseKey(int nodeNum, double newVal) {
    super.decreaseKey(nodeNum, newVal);
  }

  /** {@inheritDoc} */
  @Override
  public void heapify(int i) {
    super.heapify(i);
  }

  /**
   * Retains the original initialization overload for callers of the A* entry point.
   *
   * @param graph original adjacency list, already bound at construction
   * @param source source vertex number
   */
  public void initializeSingleSource(AdjacencyNode[] graph, int source) {
    super.initializeSingleSource(source);
  }
}
