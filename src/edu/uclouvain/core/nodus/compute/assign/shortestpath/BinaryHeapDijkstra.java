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

import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import java.util.Arrays;
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

  /** Heap nodes allocated on first use and reused across shortest-path computations. */
  BinaryHeapNode[] stock;

  /** Heap slots materialized during the previous search, for sparse reset. */
  private final int[] touchedHeapSlots;

  private int nbTouchedHeapSlots;

  /** Nodes whose positions/results were materialized during the previous search. */
  private final int[] touchedNodes;

  private int nbTouchedNodes;

  /** Source determines the original cyclic ordering of nodes in the heap. */
  private int initialSource;

  /** Destinations marked by the previous OD row. */
  private final int[] markedDestinations;

  /** Search-local flags keep independent search objects from sharing destination state. */
  private final boolean[] nodesToReach;

  private int nbMarkedDestinations;

  /** Virtual network used for the assignment. */
  private VirtualNetwork virtualNet;

  /**
   * Initializes the needed data structures.
   *
   * @param graph An array of adjacency nodes
   */
  public BinaryHeapDijkstra(AdjacencyNode[] graph) {
    this.graph = graph;
    weights = new double[graph.length];

    Arrays.fill(weights, Double.MAX_VALUE);

    pi = new int[graph.length];

    stock = new BinaryHeapNode[graph.length];
    upperBoundCosts = new BinaryHeapNode[graph.length];

    nodePos = new int[graph.length];
    touchedHeapSlots = new int[graph.length];
    touchedNodes = new int[graph.length];
    markedDestinations = new int[graph.length];
    nodesToReach = new boolean[graph.length];
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

    while (min != -1 && minWeight != Double.MAX_VALUE) {
      weights[min] = minWeight;

      // Speed-Up test
      if (nodesToReach[min]) {
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
   * Runs Dijkstra until the given goal is reached.
   *
   * @param source Source vertex number.
   * @param goal Goal vertex number.
   */
  public void compute(int source, int goal) {
    initializeSingleSource(source);

    int min = extractMin();

    while (min != -1 && minWeight != Double.MAX_VALUE) {
      weights[min] = minWeight;
      if (min == goal) {
        break;
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
    int position = getNodePosition(nodeNum);
    BinaryHeapNode node = getHeapNode(position);
    node.weight = newVal;
    int parent = position >> 1;
    while (parent != 0) {
      BinaryHeapNode parentNode = getHeapNode(parent);
      if (!(node.weight < parentNode.weight)) {
        break;
      }
      upperBoundCosts[position] = parentNode;
      nodePos[parentNode.id] = position;
      position = parent;
      parent = position >> 1;
    }
    upperBoundCosts[position] = node;
    nodePos[nodeNum] = position;
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

    min = getHeapNode(1);
    nodePos[min.id] = -1;
    if (heapSize > 1) {
      upperBoundCosts[1] = getHeapNode(heapSize);
      nodePos[upperBoundCosts[1].id] = 1;
    }
    upperBoundCosts[heapSize] = null;
    heapSize--;
    if (heapSize > 0) {
      heapify(1);
    }
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
   * Returns the shortest-path weights from the last computation.
   *
   * @return double[]
   */
  public double[] getWeights() {
    return weights;
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
      if (leftNode.weight < childNode.weight) {
        child = left;
        childNode = leftNode;
      }
      if (left + 1 <= heapSize) {
        BinaryHeapNode rightNode = getHeapNode(left + 1);
        if (rightNode.weight < childNode.weight) {
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
   * @param source The number identifier of the source node
   */
  public void initializeSingleSource(int source) {
    for (int i = 0; i < nbTouchedNodes; i++) {
      int node = touchedNodes[i];
      pi[node] = 0;
      weights[node] = Double.MAX_VALUE;
      nodePos[node] = 0;
    }
    for (int i = 0; i < nbTouchedHeapSlots; i++) {
      upperBoundCosts[touchedHeapSlots[i]] = null;
    }
    nbTouchedNodes = 0;
    nbTouchedHeapSlots = 0;
    initialSource = source;
    heapSize = graph.length - 1;
    if (heapSize > 0) {
      getHeapNode(1);
    }
  }

  /**
   * Materializes an initial heap slot on first access. Keeping the full logical heap and its
   * original source-first ordering preserves the choice between equal-cost routes.
   */
  final BinaryHeapNode getHeapNode(int position) {
    BinaryHeapNode node = upperBoundCosts[position];
    return node != null ? node : initializeHeapNode(position);
  }

  /** Kept separate so accesses to already initialized heap slots stay cheap. */
  private BinaryHeapNode initializeHeapNode(int position) {
    BinaryHeapNode node = stock[position];
    if (node == null) {
      node = new BinaryHeapNode();
      stock[position] = node;
    }
    int tailLength = graph.length - initialSource;
    int id = position <= tailLength ? initialSource + position - 1 : position - tailLength;
    node.init(id, position == 1 ? 0 : Double.MAX_VALUE);
    upperBoundCosts[position] = node;
    touchedHeapSlots[nbTouchedHeapSlots++] = position;
    // A node must be materialized before it can move from its initial heap slot.
    touchedNodes[nbTouchedNodes++] = id;
    nodePos[id] = position;
    return node;
  }

  /** An unmaterialized node still occupies its implicit initial heap position. */
  final int getNodePosition(int node) {
    int position = nodePos[node];
    if (position == 0) {
      return node >= initialSource ? node - initialSource + 1 : node + graph.length - initialSource;
    }
    return position;
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
    double candidateWeight = weights[u] + w;
    // Infinite/overflowed costs cannot improve any label. Do not materialize their heap slots.
    if (!(candidateWeight < Double.MAX_VALUE)) {
      return;
    }
    int position = getNodePosition(v);
    if (position == -1) {
      return;
    }

    if (getHeapNode(position).weight > candidateWeight) {
      decreaseKey(v, candidateWeight);
      pi[v] = u;
      // decreaseKey already restores Dijkstra's heap order by moving the node upward.
    }
  }

  /**
   * Mark the destinations to reach from the current source.
   *
   * @param demandList OD matrix row from current source
   */
  private void setNodesToReach(LinkedList<ODCell> demandList) {
    for (int i = 0; i < nbMarkedDestinations; i++) {
      graph[markedDestinations[i]].isNodeToReach = false;
      nodesToReach[markedDestinations[i]] = false;
    }
    nbMarkedDestinations = 0;

    Iterator<ODCell> it = demandList.iterator();

    nbNodesToReach = 0;

    while (it.hasNext()) {
      ODCell demand = it.next();
      int index =
          virtualNet
              .getVirtualNodeLists()[
              virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true)]
              .getUnloadingVirtualNodeId();
      if (!nodesToReach[index]) {
        nodesToReach[index] = true;
        graph[index].isNodeToReach = true;
        markedDestinations[nbMarkedDestinations++] = index;
        nbNodesToReach++;
      }
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
    BinaryHeapNode temp = getHeapNode(a);
    upperBoundCosts[a] = getHeapNode(b);
    upperBoundCosts[b] = temp;

    nodePos[upperBoundCosts[a].id] = a;
    nodePos[upperBoundCosts[b].id] = b;
  }
}
