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

package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Tracks the edges used by one multi-flow worker. The graph is scanned once to index loading edges;
 * subsequent weight and volume updates visit only the relevant edges.
 */
final class MultiFlowEdgeUpdates {
  private final List<AdjacencyNode> loadingEdges = new ArrayList<>();
  private final List<AdjacencyNode> markedEdges = new ArrayList<>();
  private final Set<AdjacencyNode> changedWeights =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<VirtualLink> linksWithDemand =
      Collections.newSetFromMap(new IdentityHashMap<>());

  MultiFlowEdgeUpdates(AdjacencyNode[] graph) {
    for (int i = 1; i < graph.length; i++) {
      for (AdjacencyNode edge = graph[i];
          edge != null && edge.virtualLink != null;
          edge = edge.nextNode) {
        if (edge.virtualLink.getType() == VirtualLink.TYPE_LOAD) {
          loadingEdges.add(edge);
        }
      }
    }
  }

  /** Restricts loading throughout the network, including loading at intermediate nodes. */
  void restrictLoading(int modeMeans) {
    for (AdjacencyNode edge : loadingEdges) {
      if (edge.virtualLink.getEndVirtualNode().getModeMeansKey() != modeMeans) {
        changedWeights.add(edge);
        edge.edgeWeight = Double.POSITIVE_INFINITY;
      }
    }
  }

  /**
   * Called for each traversed path edge, before its demand cell is attached to the virtual link.
   */
  void markPathEdge(AdjacencyNode edge) {
    if (!edge.inCurrentTree) {
      edge.inCurrentTree = true;
      markedEdges.add(edge);
    }
    linksWithDemand.add(edge.virtualLink);
  }

  /** Shared edges are increased once per pass, even when several destinations use them. */
  void increaseCosts(double multiplier) {
    for (AdjacencyNode edge : markedEdges) {
      changedWeights.add(edge);
      edge.edgeWeight *= multiplier;
    }
  }

  /**
   * Fast MF clears marks after each cost increase; Exact MF retains them across alternatives until
   * the mode/means combination is finished.
   */
  void clearPathMarks() {
    for (AdjacencyNode edge : markedEdges) {
      edge.inCurrentTree = false;
      edge.isIncreased = false;
    }
    markedEdges.clear();
  }

  /** Restores weights separately from marks, matching the different Fast and Exact lifetimes. */
  void restoreWeights() {
    for (AdjacencyNode edge : changedWeights) {
      edge.edgeWeight = edge.originalEdgeWeight;
    }
    changedWeights.clear();
  }

  /** Applies the modal split for one OD cell, then forgets the links whose cells were consumed. */
  void spreadVolumes(byte groupIndex, Path[] paths) {
    for (VirtualLink link : linksWithDemand) {
      link.spreadFlowOverPaths(groupIndex, paths);
    }
    linksWithDemand.clear();
  }

  /** Applies the modal split for one origin's OD row. */
  void spreadVolumes(byte groupIndex, Path[][] paths) {
    for (VirtualLink link : linksWithDemand) {
      link.spreadVolumeOverPaths(groupIndex, paths);
    }
    linksWithDemand.clear();
  }
}
