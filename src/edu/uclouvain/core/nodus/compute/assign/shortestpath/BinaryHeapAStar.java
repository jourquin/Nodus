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
 * Exact multi-flow uses shared Dijkstra first routes, then searches and penalizes subsequent
 * alternatives separately for each OD pair.
 *
 * <p>The "improved A*" option is Nodus's zero-heuristic specialization of A*. Its algorithmic
 * references are therefore the original A* and Dijkstra papers:
 *
 * <ul>
 *   <li>P. E. Hart, N. J. Nilsson and B. Raphael (1968). <a
 *       href="https://doi.org/10.1109/TSSC.1968.300136">A Formal Basis for the Heuristic
 *       Determination of Minimum Cost Paths</a>. <i>IEEE Transactions on Systems Science and
 *       Cybernetics</i>, 4(2), pp. 100-107.
 *   <li>E. W. Dijkstra (1959). <a href="https://doi.org/10.1007/BF01386390">A note on two problems
 *       in connexion with graphs</a>. <i>Numerische Mathematik</i>, 1, pp. 269-271.
 * </ul>
 *
 * @author Bart Jourquin
 */
public class BinaryHeapAStar extends BinaryHeapDijkstra {
  /** Whether heap keys include a remaining-cost estimate. */
  private final boolean useHeuristic;

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
    this(graph, compactGraph, false);
  }

  /**
   * Allows destination-based searches to use heuristic heap keys.
   *
   * @param graph worker-owned adjacency list
   * @param compactGraph synchronized compact copy, or null for linked storage
   * @param useHeuristic whether heap keys include a remaining-cost estimate
   */
  protected BinaryHeapAStar(
      AdjacencyNode[] graph, CompactShortestPathGraph compactGraph, boolean useHeuristic) {
    super(graph, null, compactGraph, useHeuristic);
    this.useHeuristic = useHeuristic;
  }

  /** {@inheritDoc} */
  @Override
  public void compute(int source, int goal) {
    super.compute(source, goal);
  }

  /**
   * This method lowers the value of a node in the binary heap and restores the heap property. The
   * node is found in the heap using the nodePos array.
   *
   * @param nodeNum the number of the node to have its key decreased
   * @param newVal the new key value
   */
  @Override
  public void decreaseKey(int nodeNum, double newVal) {
    if (!useHeuristic || compactHeap != null) {
      super.decreaseKey(nodeNum, newVal);
      return;
    }
    int position = getNodePosition(nodeNum);
    BinaryHeapNode node = getHeapNode(position);
    node.updateWeight(newVal);
    int parent = position >> 1;
    while (parent != 0) {
      BinaryHeapNode parentNode = getHeapNode(parent);
      if (!(node.keyWeight < parentNode.keyWeight)) {
        break;
      }
      upperBoundCosts[position] = parentNode;
      nodePos[parentNode.id] = position;
      position = parent;
      parent = position >> 1;
    }
    upperBoundCosts[position] = node;
    nodePos[nodeNum] = position;
    // Preserve A*'s additional repair after updating the estimated total cost.
    heapify(position);
  }

  /**
   * Heapify is a process that causes an input node to be swapped with the smaller of its children
   * continually until either it reaches the bottom of the tree or is less than or equal to its
   * children. Once the node "runs down" the tree, the heap property is restored for that node with
   * respect to the node original position and its new children, if any.
   *
   * @param i the index in the binary heap array of the node to perform heapify on
   */
  @Override
  public void heapify(int i) {
    if (!useHeuristic || compactHeap != null) {
      super.heapify(i);
      return;
    }
    if (i > heapSize) {
      return;
    }
    BinaryHeapNode node = getHeapNode(i);
    while ((i << 1) <= heapSize) {
      int left = i << 1;
      int child = i;
      BinaryHeapNode childNode = node;
      BinaryHeapNode leftNode = getHeapNode(left);
      // Keep the original strict comparisons, including ties and non-finite A* estimates.
      if (leftNode.keyWeight < childNode.keyWeight) {
        child = left;
        childNode = leftNode;
      }
      if (left + 1 <= heapSize) {
        BinaryHeapNode rightNode = getHeapNode(left + 1);
        if (rightNode.keyWeight < childNode.keyWeight) {
          child = left + 1;
          childNode = rightNode;
        }
      }
      if (child == i) {
        break;
      }
      upperBoundCosts[i] = childNode;
      nodePos[childNode.id] = i;
      i = child;
    }
    upperBoundCosts[i] = node;
    nodePos[node.id] = i;
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
