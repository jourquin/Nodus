/*
 * Copyright (c) 1991-2022 Université catholique de Louvain
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

import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A Binary Heap implementation of the algorithm of Dijkstra. The Binary Heap implementation has
 * been proven to be the most effective for the class of problems to be resolved by means of virtual
 * networks.
 *
 * @author Bart Jourquin
 */
public class BinaryHeapDijkstra {

  /**
   * Array of BHNodes containing information on the upper bounds of the cost of a shortest path from
   * source weights to a node in upperBoundCosts.
   */
  BinaryHeapNode[] upperBoundCosts;

  /** Graph in which shortest paths must be computed. */
  AdjacencyNode[] graph;

  /** Heap size. */
  int heapSize;

  /** Weight of the shortest path to the vertex last extracted from the priority queue. */
  double minWeight;

  /** Used to stop the search when all the nodes that have to be reached are reached. */
  int nbNodesToReach;

  /** Positions of nodes in the heap -- needed for decreaseKey. */
  int[] nodePos;

  /** Set of predecessors. */
  int[] pi;

  /** Set of weights of the shortest paths. */
  double[] weights;

  /**
   * Array of BHNodes that will be allocated at the initialization of the class. Used to feed
   * "upperBoundCosts" at each new shortest path tree computation.
   */
  BinaryHeapNode[] stock;

  /** Virtual network used for the assignment. */
  private VirtualNetwork virtualNet;

  /**
   * Initializes the needed data structures.
   *
   * @param graph An array of adjacency nodes
   */
  BinaryHeapDijkstra(AdjacencyNode[] graph) {
    this.graph = graph;
    weights = new double[graph.length];

    for (int i = 0; i < graph.length; i++) {
      weights[i] = Double.MAX_VALUE;
    }

    pi = new int[graph.length];

    stock = new BinaryHeapNode[graph.length];
    upperBoundCosts = new BinaryHeapNode[graph.length];

    // Initialization of binary heap specific implementation
    for (int i = 0; i < graph.length; i++) {
      stock[i] = new BinaryHeapNode();
    }

    nodePos = new int[graph.length];
  }

  /**
   * Initializes the data structures.
   *
   * @param graph AdjacencyNode[]
   * @param virtualNet VirtualNetwork
   */
  public BinaryHeapDijkstra(AdjacencyNode[] graph, VirtualNetwork virtualNet) {
    this(graph);
    this.virtualNet = virtualNet;
  }

  /**
   * Runs the Dijkstra algorithm to completion.
   *
   * <p>In order to improve the performances of the algorithm, its stops once all the destinations
   * are reached. This trick improves the performances of the algorithm by about 50%.
   *
   * @param source Loading virtual node num used as source vertex
   * @param demandList The row of the OD matrix having source as starting vertex
   */
  public void compute(int source, LinkedList<ODCell> demandList) {
    setNodesToReach(demandList);
    initializeSingleSource(source);

    int min = extractMin();

    while (min != -1) {
      weights[min] = minWeight;

      // Speed-Up test
      if (graph[min].isNodeToReach) {
        nbNodesToReach--;

        if (nbNodesToReach == 0) {
          break;
        }
      }

      for (AdjacencyNode cursor = graph[min]; cursor.nextNode != null; cursor = cursor.nextNode) {
        relax(graph[min].virtualNodeNum, cursor.nextNode.virtualNodeNum, cursor.edgeWeight);
      }

      min = extractMin();
    }
  }

  /**
   * This method lowers the value of a node in the binary heap and restores the heap property. The
   * node is found in the heap using the nodePos array.
   *
   * @param nodeNum int The number of the node to have its key decreased
   * @param newVal double The new key value
   */
  public void decreaseKey(int nodeNum, double newVal) {

    int y = nodePos[nodeNum];
    upperBoundCosts[y].weight = newVal;

    int parent = y >> 1;

    while (parent != 0 && upperBoundCosts[y].weight < upperBoundCosts[parent].weight) {
      swap(y, parent);
      y = parent;
      parent = y >> 1;
    }
  }

  /**
   * This method removes the minimum valued node form the binary heap, shifts the node at the bottom
   * of the heap to the top and calls heapify on that node. If the heap is empty, -1 is returned.
   *
   * @return int The node number with the minimum shortest path estimate
   */
  public int extractMin() {
    BinaryHeapNode min;

    if (heapSize < 1) {
      return -1;
    }

    min = upperBoundCosts[1];
    nodePos[upperBoundCosts[1].id] = -1; // node removed from node positions
    upperBoundCosts[1] = upperBoundCosts[heapSize];
    nodePos[upperBoundCosts[1].id] = 1; // largest value node now in position
    // one
    upperBoundCosts[heapSize] = null; // remove node
    heapSize--;
    heapify(1);
    minWeight = min.weight;

    return min.id;
  }

  /**
   * Returns the set of predecessors.
   *
   * @return int[]
   */
  public int[] getPredecessors() {
    return pi;
  }

  /**
   * Heapify is a process that causes an input node to be swapped with the smaller of its children
   * continually until either it reaches the bottom of the tree or is less than or equal to its
   * children. Once the node "runs down" the tree, the heap property is restored for that node with
   * respect to the node original position and its new children, if any.
   *
   * @param i int the index in the binary heap array of the node to perform heapify on
   */
  public void heapify(int i) {
    int l = i << 1;

    int r = l + 1;
    int smallest;

    if (l <= heapSize && upperBoundCosts[l].weight < upperBoundCosts[i].weight) {
      smallest = l;
    } else {
      smallest = i;
    }

    if (r <= heapSize && upperBoundCosts[r].weight < upperBoundCosts[smallest].weight) {
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
   * @param source The number identifier of the source node
   */
  public void initializeSingleSource(int source) {
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

  /**
   * Test if shortest path to v can be improved by going through u, and if so, updating
   * upperBoundCosts[v] and pi[v]. w is the cost from u to v. if nodePos[v] is -1, then the shortest
   * path to v has already been found and there is no need to do the relaxation.
   *
   * @param u Vertex relaxing from
   * @param v Vertex relaxing to
   * @param w double The cost from u to v
   */
  public void relax(int u, int v, double w) {

    if (nodePos[v] == -1) {
      return;
    }

    if (upperBoundCosts[nodePos[v]].weight > weights[u] + w) {
      decreaseKey(v, weights[u] + w);
      pi[v] = u;

      heapify(nodePos[v]);
    }
  }

  /**
   * Mark the destinations to reach from the current source.
   *
   * @param demandList OD matrix row from current source
   */
  private void setNodesToReach(LinkedList<ODCell> demandList) {
    for (int i = 1; i < graph.length; i++) {
      graph[i].isNodeToReach = false;
    }

    Iterator<ODCell> it = demandList.iterator();

    nbNodesToReach = demandList.size();

    while (it.hasNext()) {
      ODCell demand = it.next();
      int index =
          virtualNet
              .getVirtualNodeLists()[
              virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true)]
              .getUnloadingVirtualNodeId();
      graph[index].isNodeToReach = true;
    }
  }

  /**
   * The method is used by heapify and decreaseKey to switch the positions of two nodes in the
   * binary heap.
   *
   * @param a int Index of node to be swapped in the binary heap array
   * @param b int Index of other node to be swapped in the binary heap array
   */
  public void swap(int a, int b) {
    BinaryHeapNode temp = upperBoundCosts[a];
    upperBoundCosts[a] = upperBoundCosts[b];
    upperBoundCosts[b] = temp;

    int tempInt = nodePos[upperBoundCosts[a].id];
    nodePos[upperBoundCosts[a].id] = nodePos[upperBoundCosts[b].id];
    nodePos[upperBoundCosts[b].id] = tempInt;
  }
}
