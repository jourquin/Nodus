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

/**
 * Element of a binary heap structure. The binary heap implementation of the algorithm of Dijkstra
 * has been proven to be the most effective for the problems to be solved with virtual networks.
 *
 * <p>Variables are public for performance reasons.
 *
 * @author Bart Jourquin
 */
public class BinaryHeapNode {

  /** Distance estimation from this node to the goal. */
  double goalEstWeight;

  /** Node number. */
  public int id;

  /** Shortest path estimate plus the estimate of the distance to the goal. */
  double keyWeight;

  /** Value to be associated to this node. */
  double weight;

  /** The constructor initializes a node in the heap. */
  public BinaryHeapNode() {

  }

  /**
   * Initializes the BHNode.
   *
   * @param id Node number
   * @param weight Associated weight
   */
  public void init(int id, double weight) {
    this.id = id;
    this.weight = weight;
    keyWeight = weight;
  }

  /**
   * Updates the estimated weight to the goal. Used by AStar.
   *
   * @param goalEstWeight The goalEstVal to set.
   */
  public void updateGoalEstWeight(double goalEstWeight) {
    this.goalEstWeight = goalEstWeight;
    keyWeight = weight + this.goalEstWeight;
  }

  /**
   * Updates the weight.
   *
   * @param weight The value to set.
   */
  public void updateWeight(double weight) {
    this.weight = weight;
    keyWeight = this.weight + goalEstWeight;
  }
}
