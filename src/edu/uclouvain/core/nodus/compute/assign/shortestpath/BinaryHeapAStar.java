/**
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

  /**
   * Initializes the data structures.
   *
   * @param graph AdjacencyNode[]
   */
  public BinaryHeapAStar(AdjacencyNode[] graph) {
    super(graph);
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
      if (min == -1) {
        System.err.println("Goal not reachable from source.");
        break;
      }

      weights[min] = minWeight;

      for (AdjacencyNode curNode = graph[min];
          curNode.nextNode != null;
          curNode = curNode.nextNode) {
        relax(graph[min].virtualNodeNum, curNode.nextNode.virtualNodeNum, curNode.edgeWeight);

        if (nodePos[curNode.nextNode.virtualNodeNum] != -1
            && upperBoundCosts[nodePos[curNode.nextNode.virtualNodeNum]].goalEstWeight == 0) {
          upperBoundCosts[nodePos[curNode.nextNode.virtualNodeNum]].updateGoalEstWeight(
              curNode.nextNode.goalEst(graph[goal]));
        }
      }

      min = extractMin();
    }

    weights[min] = minWeight;
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
    int y = nodePos[nodeNum];
    upperBoundCosts[y].updateWeight(newVal);

    int parent = y >> 1;

    while (parent != 0 && upperBoundCosts[y].keyWeight < upperBoundCosts[parent].keyWeight) {
      swap(y, parent);
      y = parent;
      parent = y >> 1;
    }
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
    int l = i << 1;

    int r = l + 1;
    int smallest;

    if (l <= heapSize && upperBoundCosts[l].keyWeight < upperBoundCosts[i].keyWeight) {
      smallest = l;
    } else {
      smallest = i;
    }

    if (r <= heapSize && upperBoundCosts[r].keyWeight < upperBoundCosts[smallest].keyWeight) {
      smallest = r;
    }

    if (smallest != i) {
      swap(i, smallest);
      heapify(smallest);
    }
  }

  /**
   * Initialize the shortest-path estimates and predecessor function. The predecessor function is
   * left at the default of all zeros. Because there is no zero node in the graph, zero denotes a
   * null value for a predecessor. The source node upperBoundCosts value is initially set to
   * cost zero and all other nodes are set to have cost equal to the maximum double floating point
   * precision value to represent infinity.
   *
   * @param graph the input graph in adjacency-list form
   * @param source the number identifier of the source node
   */
  public void initializeSingleSource(AdjacencyNode[] graph, int source) {
    for (int i = 0; i < upperBoundCosts.length; i++) {
      upperBoundCosts[i] = stock[i];
    }

    upperBoundCosts[1].init(source, 0);
    nodePos[source] = 1;

    // BHNodes greater than source
    int i = 2;

    for (int nextNodeNum = source + 1; nextNodeNum < graph.length; i++, nextNodeNum++) {
      upperBoundCosts[i].init(nextNodeNum, Double.MAX_VALUE);
      nodePos[nextNodeNum] = i;
    }

    // BHNodes less than source
    for (int nextNodeNum = 1; nextNodeNum < source; i++, nextNodeNum++) {
      upperBoundCosts[i].init(nextNodeNum, Double.MAX_VALUE);
      nodePos[nextNodeNum] = i;
    }

    heapSize = upperBoundCosts.length - 1;
  }
}
