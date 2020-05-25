/*
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

import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;

/**
 * A node that is used to build adjacency lists. All variables are public for performance reasons.
 *
 * @author Bart Jourquin
 */
public class AdjacencyNode {

  /** Weight from head Node to this Node, used during path computation. */
  public double edgeWeight;

  /**
   * Point the the virtual node located at the end of the virtual link this adjacency node refers
   * to.
   */
  public int endVirtualNode;

  /** Flag to test id this adjacency node is in current tree. */
  public boolean inCurrentTree = false;

  /**
   * Flag used in ensure that the cost is only increased one time in a Dijkstra tree. Used in the
   * FastMFAssignment.
   */
  public boolean isIncreased = false;

  /**
   * Set to true if this node is a final destination in the set of routes that is computed. Used to
   * improve the performances of the algorithm of Dijkstra, that now can stop once all the
   * destinations are reached.
   */
  public boolean isNodeToReach;

  /** Latitude of node. */
  public double latitude;

  /** Longitude of node. */
  public double longitude;

  /** Next adjacency node in the list. */
  public AdjacencyNode nextNode;

  /**
   * The first cost attached to this adjacency node. This is used in the multi-flow algorithms were
   * alternative paths are not computed on the base of the actual cost of the link.
   */
  public double originalEdgeWeight;

  /** Points to the virtual link this adjacency node refers to. */
  public VirtualLink virtualLink;

  /** Number of the virtual node this adjacency node refers to. */
  public int virtualNodeNum;

  /**
   * Initialize the adjacency node.
   *
   * @param vn VirtualNode
   */
  public AdjacencyNode(VirtualNode vn) {
    longitude = vn.getLongitude();
    latitude = vn.getLatitude();
    virtualNodeNum = vn.getId();
  }

  /**
   * Returns the straight-line distance to the goal (used by AStar).
   *
   * @return the straight-line distance to the goal
   */
  public double goalEst(AdjacencyNode goal) {
    return Math.sqrt(
        Math.pow(Math.abs(goal.longitude - longitude), 2)
            + Math.pow(Math.abs(goal.latitude - latitude), 2));
  }

  /**
   * Assigns the next node in the adjacency list.
   *
   * @param next AdjacencyNode The node that will be connected to this one
   * @param group byte Group The group this adjacency node is generated for
   * @param vl VirtualLink Virtual link this adjacency node refers to
   */
  public void setNext(AdjacencyNode next, byte group, VirtualLink vl) {
    nextNode = next;
    edgeWeight = vl.getCost(group);
    originalEdgeWeight = edgeWeight;
    virtualLink = vl;
    endVirtualNode = vl.getEndVirtualNode().getId();
  }
}
