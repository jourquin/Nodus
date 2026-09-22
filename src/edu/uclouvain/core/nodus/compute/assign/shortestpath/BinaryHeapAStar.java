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
 * An implementation of the AStar shortest path algorithm using a binary heap.
 *
 * @author Bart Jourquin
 */
public class BinaryHeapAStar extends BinaryHeapDijkstra {

  /** Fixed coordinates for compact searches, absent with the original object representation. */
  private final double[] latitudes;

  private final double[] longitudes;

  /**
   * Initializes the data structures.
   *
   * @param graph AdjacencyNode[]
   */
  public BinaryHeapAStar(AdjacencyNode[] graph) {
    this(graph, null);
  }

  /**
   * Selects compact graph/heap storage while preserving the original A* heuristic and tie order.
   *
   * @param graph Worker adjacency list with consistent node coordinates in all adjacency entries.
   * @param compactGraph Synchronized compact copy, or null for the original implementation.
   */
  public BinaryHeapAStar(AdjacencyNode[] graph, CompactShortestPathGraph compactGraph) {
    super(graph, null, compactGraph, true);
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
   * @param source Loading virtual node num
   * @param goal Unloading virtual node num
   */
  public void compute(int source, int goal) {
    this.initializeSingleSource(graph, source);

    int min = extractMin();

    while (min != goal) {
      if (min == -1 || minWeight == Double.MAX_VALUE) {
        System.err.println("Goal not reachable from source.");
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
   * This method lowers the value of a node in the binary heap and restores the heap property. The
   * node is found in the heap using the nodePos array.
   *
   * @param nodeNum the number of the node to have its key decreased
   * @param newVal the new key value
   */
  @Override
  public void decreaseKey(int nodeNum, double newVal) {
    if (compactHeap != null) {
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
    if (compactHeap != null) {
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
