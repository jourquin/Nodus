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
 * Dijkstra/A* binary heap stored as primitive node IDs and costs instead of heap objects.
 *
 * <p>The logical heap initially contains every node in the legacy source-first cyclic order.
 * Untouched slots are implicit, and only slots/nodes materialized by the previous search are reset.
 * Retaining that ordering, strict comparisons, and left-before-right heap repair is necessary: a
 * frontier-only heap could return different predecessor trees when costs tie.
 *
 * <p>One instance belongs to one search object. Node zero and heap slot zero are unused; a zero
 * slot denotes an implicit entry, a zero position an unmoved implicit node, and position -1 a
 * removed node. Costs are indexed by node, so moving IDs within the heap never copies doubles.
 * Dijkstra keys alias its distance array; only A* allocates separate estimates and combined keys.
 */
final class CompactShortestPathHeap {
  private final int[] heap;
  private final double[] costs;
  private final double[] keys;
  private final double[] estimates;
  private final int[] positions;
  private final int[] touchedSlots;
  private final int[] touchedNodes;
  private int touchedCount;
  private int initialSource;

  /** Number of nodes still in the logical heap, including implicit infinite entries. */
  int size;

  /** Cost of the most recently extracted node. */
  double minWeight;

  /** Creates primitive storage; the shared positions remain available to the search object. */
  CompactShortestPathHeap(int[] positions, boolean aStar) {
    this.positions = positions;
    heap = new int[positions.length];
    costs = new double[positions.length];
    keys = aStar ? new double[positions.length] : costs;
    estimates = aStar ? new double[positions.length] : null;
    touchedSlots = new int[positions.length];
    touchedNodes = new int[positions.length];
  }

  /** Clears only previously materialized entries and restores the implicit cyclic ordering. */
  void initialize(int source, int[] predecessors, double[] weights) {
    for (int i = 0; i < touchedCount; i++) {
      int node = touchedNodes[i];
      positions[node] = 0;
      predecessors[node] = 0;
      weights[node] = Double.MAX_VALUE;
      heap[touchedSlots[i]] = 0;
    }
    touchedCount = 0;
    initialSource = source;
    size = heap.length - 1;
    if (size > 0) {
      nodeAt(1);
    }
  }

  /** Materializes an implicit entry only when first read or moved. */
  private int nodeAt(int slot) {
    int node = heap[slot];
    return node != 0 ? node : initializeSlot(slot);
  }

  /** Keeps the common, already materialized access small enough for inlining. */
  private int initializeSlot(int slot) {
    int tailLength = heap.length - initialSource;
    int node = slot <= tailLength ? initialSource + slot - 1 : slot - tailLength;
    heap[slot] = node;
    costs[node] = slot == 1 ? 0 : Double.MAX_VALUE;
    if (estimates != null) {
      estimates[node] = 0;
      keys[node] = costs[node];
    }
    positions[node] = slot;
    touchedSlots[touchedCount] = slot;
    touchedNodes[touchedCount++] = node;
    return node;
  }

  /** Finds a node's current slot, or its implicit initial slot if it has not moved yet. */
  private int positionOf(int node) {
    int position = positions[node];
    if (position == 0) {
      return node >= initialSource ? node - initialSource + 1 : node + heap.length - initialSource;
    }
    return position;
  }

  /** Lowers a finite label if it improves an unsettled node, returning whether it changed. */
  boolean improve(int node, double candidate) {
    int position = positionOf(node);
    if (position == -1 || !(costs[nodeAt(position)] > candidate)) {
      return false;
    }
    moveUp(node, position, candidate);
    return true;
  }

  /** Implements the search object's public decrease-key operation. */
  void decreaseKey(int node, double cost) {
    int position = positionOf(node);
    nodeAt(position);
    moveUp(node, position, cost);
  }

  /** Moves only strictly cheaper nodes upward, preserving the existing treatment of ties. */
  private void moveUp(int node, int position, double cost) {
    costs[node] = cost;
    if (estimates != null) {
      keys[node] = cost + estimates[node];
    }
    double key = keys[node];
    int parent = position >> 1;
    while (parent != 0) {
      int parentNode = nodeAt(parent);
      if (!(key < keys[parentNode])) {
        break;
      }
      heap[position] = parentNode;
      positions[parentNode] = position;
      position = parent;
      parent = position >> 1;
    }
    heap[position] = node;
    positions[node] = position;
    if (estimates != null) {
      // A* also repairs downward after an estimate changes, even for non-finite keys.
      heapify(position);
    }
  }

  /** Tests A*'s original zero-estimate sentinel, materializing an unsettled node if needed. */
  boolean needsEstimate(int node) {
    int position = positionOf(node);
    return position != -1 && estimates[nodeAt(position)] == 0;
  }

  /** Sets an A* estimate without repairing the heap until a distance relaxation improves it. */
  void setEstimate(int node, double estimate) {
    estimates[node] = estimate;
    keys[node] = costs[node] + estimate;
  }

  /** Removes the root, replacing it by the last logical entry before repairing the heap. */
  int extractMin() {
    if (size < 1) {
      return -1;
    }
    int min = nodeAt(1);
    positions[min] = -1;
    if (size > 1) {
      heap[1] = nodeAt(size);
      positions[heap[1]] = 1;
    }
    heap[size--] = 0;
    if (size > 0) {
      heapify(1);
    }
    minWeight = costs[min];
    return min;
  }

  /** Repairs downward with exactly the original strict, left-before-right comparisons. */
  void heapify(int position) {
    if (position > size) {
      return;
    }
    int node = nodeAt(position);
    double cost = keys[node];
    while ((position << 1) <= size) {
      int left = position << 1;
      int child = position;
      int childNode = node;
      double childCost = cost;
      int leftNode = nodeAt(left);
      if (keys[leftNode] < childCost) {
        child = left;
        childNode = leftNode;
        childCost = keys[leftNode];
      }
      if (left + 1 <= size) {
        int rightNode = nodeAt(left + 1);
        if (keys[rightNode] < childCost) {
          child = left + 1;
          childNode = rightNode;
        }
      }
      if (child == position) {
        break;
      }
      heap[position] = childNode;
      positions[childNode] = position;
      position = child;
    }
    heap[position] = node;
    positions[node] = position;
  }

  /** Implements the search object's public slot-swap operation. */
  void swap(int first, int second) {
    int node = nodeAt(first);
    heap[first] = nodeAt(second);
    heap[second] = node;
    positions[heap[first]] = first;
    positions[node] = second;
  }
}
