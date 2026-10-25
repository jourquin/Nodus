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
 * Previous geographic A* implementation, retained for controlled performance comparisons.
 *
 * <p>The algorithm, coordinate estimate and heap tie order match the implementation preceding the
 * zero-heuristic correction. With arbitrary user-defined transport costs, its geographic estimate
 * can overestimate remaining costs and produce a nonoptimal route. Exact Multi-flow selects it when
 * NodusC.exactMFAlgorithm is LEGACY_ASTAR; its searches use the optional worker timing log.
 *
 * <p>Algorithm reference (the geographic estimate above is specific to this implementation):
 *
 * <ul>
 *   <li>P. E. Hart, N. J. Nilsson and B. Raphael (1968). <a
 *       href="https://doi.org/10.1109/TSSC.1968.300136">A Formal Basis for the Heuristic
 *       Determination of Minimum Cost Paths</a>. <i>IEEE Transactions on Systems Science and
 *       Cybernetics</i>, 4(2), pp. 100-107.
 * </ul>
 *
 * @author Bart Jourquin
 */
public class LegacyBinaryHeapAStar extends BinaryHeapAStar {

  /** Fixed coordinates for compact searches, absent with the original object representation. */
  private final double[] latitudes;

  private final double[] longitudes;

  /**
   * Initializes the data structures.
   *
   * @param graph AdjacencyNode[]
   */
  public LegacyBinaryHeapAStar(AdjacencyNode[] graph) {
    this(graph, null);
  }

  /**
   * Selects compact graph/heap storage while preserving the original A* heuristic and tie order.
   *
   * @param graph Worker adjacency list with consistent node coordinates in all adjacency entries.
   * @param compactGraph Synchronized compact copy, or null for the original implementation.
   */
  public LegacyBinaryHeapAStar(AdjacencyNode[] graph, CompactShortestPathGraph compactGraph) {
    super(graph, compactGraph, true);
    latitudes = compactGraph == null ? null : new double[graph.length];
    longitudes = compactGraph == null ? null : new double[graph.length];
    if (compactGraph != null) {
      for (int node = 1; node < graph.length; node++) {
        latitudes[node] = graph[node].latitude;
        longitudes[node] = graph[node].longitude;
      }
    }
  }

  /**
   * Runs the AStar algorithm to completion.
   *
   * <p>An unreachable OD/mode combination is a normal result. The goal keeps its infinite cost and
   * zero predecessor; the assignment worker handles optional lost-path reporting.
   *
   * @param source Loading virtual node num
   * @param goal Unloading virtual node num
   */
  public void compute(int source, int goal) {
    this.initializeSingleSource(graph, source);

    int min = extractMin();

    while (min != goal) {
      if (min == -1 || minWeight == Double.MAX_VALUE) {
        return;
      }

      weights[min] = minWeight;

      if (compactGraph != null) {
        relaxCompactEdges(min, goal);
      } else {
        for (AdjacencyNode curNode = graph[min];
            curNode.nextNode != null;
            curNode = curNode.nextNode) {
          int nextNodeNum = curNode.nextNode.virtualNodeNum;
          int position = getNodePosition(nextNodeNum);
          if (position != -1 && getHeapNode(position).goalEstWeight == 0) {
            getHeapNode(position).updateGoalEstWeight(curNode.nextNode.goalEst(graph[goal]));
          }

          relax(graph[min].virtualNodeNum, nextNodeNum, curNode.edgeWeight);
        }
      }

      min = extractMin();
    }

    if (min != -1) {
      weights[min] = minWeight;
    }
  }

  /**
   * Initialize the shortest-path estimates and predecessor function. The predecessor function is
   * left at the default of all zeros. Because there is no zero node in the graph, zero denotes a
   * null value for a predecessor. The source node upperBoundCosts value is initially set to cost
   * zero and all other nodes are set to have cost equal to the maximum double floating point
   * precision value to represent infinity.
   *
   * @param graph the input graph in adjacency-list form
   * @param source the number identifier of the source node
   */
  public void initializeSingleSource(AdjacencyNode[] graph, int source) {
    super.initializeSingleSource(source);
  }

  /** Uses primitive coordinates and exactly the same arithmetic as AdjacencyNode.goalEst. */
  private void relaxCompactEdges(int node, int goal) {
    int end = compactGraph.offsets[node + 1];
    for (int edge = compactGraph.offsets[node]; edge < end; edge++) {
      int next = compactGraph.destinations[edge];
      if (compactHeap.needsEstimate(next)) {
        double estimate =
            Math.sqrt(
                Math.pow(Math.abs(longitudes[goal] - longitudes[next]), 2)
                    + Math.pow(Math.abs(latitudes[goal] - latitudes[next]), 2));
        compactHeap.setEstimate(next, estimate);
      }
      relax(node, next, compactGraph.costs[edge]);
    }
  }
}
