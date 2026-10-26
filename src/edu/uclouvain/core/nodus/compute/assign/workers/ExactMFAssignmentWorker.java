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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerPhase;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapAStar;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDestinationAStar;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.CompactShortestPathGraph;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.ReverseDijkstra;
import edu.uclouvain.core.nodus.compute.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.modalsplit.PathsForMode;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.PathODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * The exact multi-flow assignment computes one or several alternative routes for each mode/means
 * combination, with independent penalties for each OD pair to prevent the shared-tree side effects
 * described in <i> Jourquin B., A multi-flow multi-modal assignment procedure on large freight
 * transportation networks, Studies in Regional Science, Vol 35, n°4, pp. 929- 946, 2005.</i>.
 *
 * <p>The first, unpenalized routes share one Dijkstra search per origin and loading mode/means.
 * NodusC.exactMFAlgorithm selects safe zero-heuristic A* for subsequent alternatives. The
 * destination-based A* option instead shares reverse Dijkstra first routes and baseline bounds per
 * destination and loading mode/means. All later searches and penalties remain separate for each OD
 * pair. Tables belong to one group/cost-class worker job.
 *
 * @author Bart Jourquin
 */
public class ExactMFAssignmentWorker extends AssignmentWorker {

  private MultiFlowEdgeUpdates edgeUpdates;

  byte maxDetourReferenceMode = -1;

  /** This value is used when the "force modal split is used". */
  static final double forcedCostPerIteration = Integer.MAX_VALUE;

  int[] availableModeMeans;

  private BinaryHeapAStar shortestPath;

  private BinaryHeapDijkstra firstRouteSearch;

  /**
   * Two hash tables. The first will contain the OD pairs between which at least one path was found,
   * the second the pairs between which, during an iteration, no path was found. All the pairs which
   * are in the second and not in the first hash table will be considered as "lost paths"
   */
  HashMap<String, ODCell> foundPaths = new HashMap<>();

  HashMap<String, ODCell> potentialLostPaths = new HashMap<>();

  ModalSplitMethod modalSplitMethod;

  /** Array that will contain the weights of the paths for the successive iterations. */
  private Path[] paths;

  private final MultiFlowPathIdentity pathIdentity = new MultiFlowPathIdentity();

  /**
   * Initializes an Assignment Worker.
   *
   * @param queue The WorkQueue the assignment will run in.
   */
  public ExactMFAssignmentWorker(WorkQueue queue) {
    super(queue);
  }

  /**
   * Runs an exact multi-flow assignment.
   *
   * @return True on success.
   */
  @Override
  boolean doAssignment() {
    try {
      return assignPaths();
    } catch (CancellationException e) {
      return false;
    } finally {
      // Release destination trees and search storage before this worker picks up another job.
      shortestPath = null;
      firstRouteSearch = null;
      edgeUpdates = null;
      graph = null;
    }
  }

  /** Runs one group/class job with its own graph, penalties and optional destination trees. */
  private boolean assignPaths() {
    workerTimes.includePhases(
        WorkerPhase.RECONSTRUCTION,
        WorkerPhase.HEADER_MATCHING,
        WorkerPhase.MODAL_SPLITTING,
        WorkerPhase.VOLUME_DISTRIBUTION);

    // Initialize the adjacency list for current group
    graph = virtualNet.generateAdjacencyList(groupIndex);
    CompactShortestPathGraph compactGraph = createCompactGraph();
    edgeUpdates = new MultiFlowEdgeUpdates(graph, compactGraph);
    NodusC.ExactMFAlgorithm algorithm = NodusC.exactMFAlgorithm;
    boolean destinationBased = algorithm == NodusC.ExactMFAlgorithm.DESTINATION_ASTAR;
    if (!destinationBased) {
      firstRouteSearch = new BinaryHeapDijkstra(graph, virtualNet, compactGraph);
      workerTimes.includePhases(WorkerPhase.DIJKSTRA);
    } else {
      workerTimes.includePhases(WorkerPhase.REVERSE_REACHABILITY, WorkerPhase.REVERSE_DIJKSTRA);
    }

    WorkerPhase searchPhase = null;
    if (assignmentParameters.getNbIterations() > 1) {
      switch (algorithm) {
        case DESTINATION_ASTAR:
          shortestPath = new BinaryHeapDestinationAStar(graph, compactGraph, this::isCancelled);
          searchPhase = WorkerPhase.DESTINATION_ASTAR;
          break;
        case IMPROVED_ASTAR:
        default:
          shortestPath = new BinaryHeapAStar(graph, compactGraph);
          searchPhase = WorkerPhase.ASTAR;
          break;
      }
      workerTimes.includePhases(searchPhase);
    }
    availableModeMeans = virtualNet.getAvailableModeMeans(groupIndex);
    paths = new Path[assignmentParameters.getNbIterations() * availableModeMeans.length];

    maxDetourReferenceMode = assignmentParameters.getMaxDetourReferenceMode();

    // Use a copy of the already initialized method
    ModalSplitMethod msp = assignment.getModalSplitMethod();

    try {
      modalSplitMethod = (ModalSplitMethod) msp.clone();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
      return false;
    }

    if (modalSplitMethod == null) {
      return false;
    }
    modalSplitMethod.initializeGroup(currentGroup);

    if (destinationBased) {
      return assignDestinations(compactGraph, searchPhase);
    }

    for (int nodeIndex = 0; nodeIndex < virtualNet.getVirtualNodeLists().length; nodeIndex++) {

      if (!updateProgressBar(nodeIndex)) {
        return false;
      }

      // This worker could also have been stopped by another thread
      if (isCancelled()) {
        return false;
      }

      demandList =
          virtualNet.getVirtualNodeLists()[nodeIndex].getDemandForGroup(
              virtualNet.getGroups()[groupIndex], odClass);

      if (demandList != null) {
        // Compute all the n shortest paths in the virtual network starting from here
        int beginNode = virtualNet.getVirtualNodeLists()[nodeIndex].getLoadingVirtualNodeId();
        int[][] firstRoutePredecessors = computeFirstRoutes(nodeIndex, beginNode);
        if (firstRoutePredecessors == null) {
          return false;
        }

        Iterator<ODCell> it = demandList.iterator();
        while (it.hasNext()) {
          if (!assignOD(
              nodeIndex, it.next(), firstRoutePredecessors, null, null, null, searchPhase)) {
            return false;
          }
        } // end of the demand cell
      } // end of demand list
    } // Next node

    return true;
  }

  /** Uses identical reconstruction, modal split and OD-local penalties in both orientations. */
  private boolean assignOD(
      int nodeIndex,
      ODCell demand,
      int[][] firstRoutePredecessors,
      ReverseDijkstra.Result[] destinationBounds,
      boolean[] canLoad,
      int[] scratch,
      WorkerPhase searchPhase) {
    int beginNode = virtualNet.getVirtualNodeLists()[nodeIndex].getLoadingVirtualNodeId();
    pathIdentity.clear();
    int currentPathPropertiesIndex = 0;

    // Get demand and num of unloading virtual node
    int endNodeIndex =
        virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true);
    int endNode = virtualNet.getVirtualNodeLists()[endNodeIndex].getUnloadingVirtualNodeId();

    /*
     * Compute "iteration" alternative paths for each mode/means combination
     */
    LinkedList<MFPathHeader> pathHeaders = new LinkedList<>();
    for (int modeIndex = 0; modeIndex < availableModeMeans.length; modeIndex++) {
      int[] firstPredecessors;
      if (destinationBounds == null) {
        firstPredecessors = firstRoutePredecessors[modeIndex];
      } else if (canLoad[modeIndex] && destinationBounds[modeIndex] != null) {
        destinationBounds[modeIndex].copyPredecessors(beginNode, scratch);
        firstPredecessors = scratch;
        if (shortestPath != null) {
          ((BinaryHeapDestinationAStar) shortestPath)
              .setBounds(destinationBounds[modeIndex], assignmentParameters.getCostMarkup() >= 0);
        }
      } else {
        firstPredecessors = null;
      }
      // As in Fast MF, an unavailable loading mode contributes no candidate routes.
      if (firstPredecessors == null) {
        for (int alternativePath = 0;
            alternativePath < assignmentParameters.getNbIterations();
            alternativePath++) {
          paths[currentPathPropertiesIndex] = new Path();
          paths[currentPathPropertiesIndex++].isValid = false;
        }
        continue;
      }

      edgeUpdates.restrictLoading(availableModeMeans[modeIndex]);

      for (int alternativePath = 0;
          alternativePath < assignmentParameters.getNbIterations();
          alternativePath++) {
        if (isCancelled()) {
          return false;
        }
        int[] predecessors = firstPredecessors;
        if (alternativePath > 0) {
          // No path was marked for an unreachable first route, so penalties cannot
          // change this OD/mode's reachability in later alternatives.
          if (firstPredecessors[endNode] == 0) {
            paths[currentPathPropertiesIndex] = new Path();
            paths[currentPathPropertiesIndex++].isValid = false;
            continue;
          }
          workerTimes.startPhase(searchPhase);
          try {
            shortestPath.compute(beginNode, endNode);
          } finally {
            workerTimes.endPhase();
          }
          predecessors = shortestPath.getPredecessors();
        }

        // Mark the paths for all the destinations to reach and compute their costs
        workerTimes.startPhase(WorkerPhase.RECONSTRUCTION);
        try {
          paths[currentPathPropertiesIndex] =
              markPaths(
                  groupIndex,
                  nodeIndex,
                  predecessors,
                  currentPathPropertiesIndex,
                  demand,
                  pathHeaders);
        } finally {
          workerTimes.endPhase();
        }

        if (paths[currentPathPropertiesIndex] == null) {
          return false;
        }

        /*
         * Increase the costs in the adjacency list to make the already used links more
         *  expensive (not for last iteration).
         */
        double costMarkup = 1 + assignmentParameters.getCostMarkup();
        if (alternativePath < assignmentParameters.getNbIterations() - 1) {
          edgeUpdates.increaseCosts(costMarkup);
        }
        currentPathPropertiesIndex++;
      } // Next alternative path

      // Restore only weights changed for this mode/means combination.
      edgeUpdates.restoreWeights();
      edgeUpdates.clearPathMarks();
    } // Next mode/means

    // Log lost paths if needed
    if (assignmentParameters.isLogLostPaths()) {
      logLostPaths();
    }

    // Compute the fraction of the demand to be assigned to each alternative path
    // for this O-D cell
    workerTimes.startPhase(WorkerPhase.MODAL_SPLITTING);
    try {
      if (!modalSplit(demand)) {
        return false;
      }
    } finally {
      workerTimes.endPhase();
    }

    // Apply modal marketShare to path headers
    if (assignmentParameters.isSavePaths()) {

      workerTimes.startPhase(WorkerPhase.HEADER_MATCHING);
      try {
        // Update the path headers in memory to compute the quantity on each path
        Iterator<MFPathHeader> it3 = pathHeaders.iterator();
        while (it3.hasNext()) {
          MFPathHeader ph = it3.next();

          if (ph.demand.getOriginNodeId() == demand.getOriginNodeId()
              && ph.demand.getDestinationNodeId() == demand.getDestinationNodeId()) {
            if (paths[ph.iteration].isValid) {
              double q = demand.getQuantity() * paths[ph.iteration].marketShare;
              if (!pathBuffer.savePathHeader(
                  ph.iteration,
                  demand,
                  q,
                  ph.weights,
                  ph.loadingMode,
                  ph.loadingMeans,
                  ph.unloadingMode,
                  ph.unloadingMeans,
                  ph.nbTranshipments,
                  ph.index)) {
                return false;
              }
            }
          }
        }
        pathHeaders.clear();
      } finally {
        workerTimes.endPhase();
      }
    }

    // Apply the modal split only to links that received path demand.
    workerTimes.startPhase(WorkerPhase.VOLUME_DISTRIBUTION);
    try {
      edgeUpdates.spreadVolumes(groupIndex, paths);
    } finally {
      workerTimes.endPhase();
    }
    return true;
  }

  /** Holds only the current destination's mode trees; never caches trees across worker jobs. */
  private boolean assignDestinations(
      CompactShortestPathGraph compactGraph, WorkerPhase searchPhase) {
    Map<Integer, List<DestinationDemand>> destinations = new LinkedHashMap<>();
    for (int origin = 0; origin < virtualNet.getVirtualNodeLists().length; origin++) {
      if (isCancelled()) {
        return false;
      }
      LinkedList<ODCell> row =
          virtualNet.getVirtualNodeLists()[origin].getDemandForGroup(currentGroup, odClass);
      if (row == null || row.isEmpty()) {
        continue;
      }
      boolean[] canLoad = loadingModes(origin);
      for (ODCell demand : row) {
        int target = virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true);
        destinations
            .computeIfAbsent(target, key -> new ArrayList<>())
            .add(new DestinationDemand(origin, demand, canLoad));
      }
    }
    if (destinations.isEmpty()) {
      return true;
    }
    ReverseDijkstra reverse;
    workerTimes.startPhase(WorkerPhase.DESTINATION_SETUP);
    try {
      reverse = new ReverseDijkstra(graph, compactGraph, this::isCancelled);
    } finally {
      workerTimes.endPhase();
    }
    int[] scratch = new int[graph.length];
    for (Map.Entry<Integer, List<DestinationDemand>> entry : destinations.entrySet()) {
      if (isCancelled() || !updateProgress(assignmentProgressMessage())) {
        return false;
      }
      int goal = virtualNet.getVirtualNodeLists()[entry.getKey()].getUnloadingVirtualNodeId();
      ReverseDijkstra.Result[] trees = new ReverseDijkstra.Result[availableModeMeans.length];
      for (int modeIndex = 0; modeIndex < availableModeMeans.length; modeIndex++) {
        int requiredCount = 0;
        for (DestinationDemand cell : entry.getValue()) {
          if (cell.canLoad[modeIndex]) {
            requiredCount++;
          }
        }
        if (requiredCount == 0) {
          continue;
        }
        int[] requiredOrigins = new int[requiredCount];
        int requiredIndex = 0;
        for (DestinationDemand cell : entry.getValue()) {
          if (cell.canLoad[modeIndex]) {
            requiredOrigins[requiredIndex++] =
                virtualNet.getVirtualNodeLists()[cell.origin].getLoadingVirtualNodeId();
          }
        }
        edgeUpdates.restrictLoading(availableModeMeans[modeIndex]);
        try {
          ReverseDijkstra.Reachability reachability;
          workerTimes.startPhase(WorkerPhase.REVERSE_REACHABILITY);
          try {
            reachability = reverse.computeReachability(goal, requiredOrigins, this::isCancelled);
            if (reachability.getReachableOriginCount() == 0) {
              // Keep a tree result for normal invalid-path/lost-path handling, without heap work.
              trees[modeIndex] = reverse.compute(reachability, this::isCancelled);
            }
          } finally {
            workerTimes.endPhase();
          }
          workerTimes.addReversePrepass(
              reachability.getReachableOriginCount(), reachability.getUnreachableOriginCount(),
              reachability.getVisitedNodeCount(), reachability.getScannedEdgeCount());
          if (reachability.getReachableOriginCount() > 0) {
            workerTimes.startPhase(WorkerPhase.REVERSE_DIJKSTRA);
            try {
              trees[modeIndex] = reverse.compute(reachability, this::isCancelled);
            } finally {
              workerTimes.endPhase();
            }
            ReverseDijkstra.Result tree = trees[modeIndex];
            workerTimes.addReverseSearch(
                tree.stoppedEarly(), tree.getSettledNodeCount(), tree.getScannedEdgeCount());
          }
        } finally {
          edgeUpdates.restoreWeights();
        }
      }
      for (DestinationDemand cell : entry.getValue()) {
        if (!assignOD(cell.origin, cell.demand, null, trees, cell.canLoad, scratch, searchPhase)) {
          return false;
        }
      }
      if (shortestPath != null) {
        ((BinaryHeapDestinationAStar) shortestPath).clearBounds();
      }
    }
    return true;
  }

  private static final class DestinationDemand {
    final int origin;
    final ODCell demand;
    final boolean[] canLoad;

    DestinationDemand(int origin, ODCell demand, boolean[] canLoad) {
      this.origin = origin;
      this.demand = demand;
      this.canLoad = canLoad;
    }
  }

  private boolean[] loadingModes(int nodeIndex) {
    boolean[] result = new boolean[availableModeMeans.length];
    VirtualNode loadingNode =
        virtualNet.getVirtualNodeLists()[nodeIndex].getVirtualNodeList().getLast();
    for (VirtualLink link : loadingNode.getVirtualLinkList()) {
      int modeMeans = link.getEndVirtualNode().getModeMeansKey();
      for (int modeIndex = 0; modeIndex < availableModeMeans.length; modeIndex++) {
        result[modeIndex] |= availableModeMeans[modeIndex] == modeMeans;
      }
    }
    return result;
  }

  /**
   * Shares the original-cost trees only within this origin and group/class. Snapshots survive later
   * OD-specific searches but are discarded before processing the next origin. Null entries
   * represent modes/means that cannot be loaded here, never unrestricted fallback searches.
   */
  private int[][] computeFirstRoutes(int nodeIndex, int beginNode) {
    int[][] predecessors = new int[availableModeMeans.length][];
    boolean[] canLoad = loadingModes(nodeIndex);
    for (int modeIndex = 0; modeIndex < availableModeMeans.length; modeIndex++) {
      if (isCancelled()) {
        return null;
      }
      int modeMeans = availableModeMeans[modeIndex];
      if (!canLoad[modeIndex]) {
        continue;
      }
      edgeUpdates.restrictLoading(modeMeans);
      try {
        workerTimes.startPhase(WorkerPhase.DIJKSTRA);
        try {
          firstRouteSearch.compute(beginNode, demandList);
        } finally {
          workerTimes.endPhase();
        }
        predecessors[modeIndex] = firstRouteSearch.getPredecessors().clone();
      } finally {
        edgeUpdates.restoreWeights();
      }
    }
    return predecessors;
  }

  private HashMap<Integer, PathsForMode> getPaths() {

    HashMap<Integer, PathsForMode> hm = new HashMap<>();

    float cheapestPathLength = Float.MAX_VALUE;
    double cheapestIntermodalPathWeight = Double.MAX_VALUE;
    double cheapestPathWeight = Double.MAX_VALUE;
    double cheapestReferencePathWeight = Double.MAX_VALUE;

    // Clean unwanted paths
    for (int index = 0;
        index < assignmentParameters.getNbIterations() * availableModeMeans.length;
        index++) {

      if (paths[index].isValid) {

        /*
         * If the same path is found several times in the set of alternatives, just keep one
         * instance.
         */
        if (index > 0) {
          for (int j = 0; j < index; j++) {
            if (pathIdentity.sameRoute(paths[index], paths[j])) {
              // Mark cell
              paths[index].isValid = false;
            }
          }
        }

        /*
         * Keep the cheapest intermodal path, if any. Used later if intermodal solutions
         * have to be kept only if they are the cheapest transport solution.
         */
        if (paths[index].intermodal
            && paths[index].weights.getCost() < cheapestIntermodalPathWeight) {
          cheapestIntermodalPathWeight = paths[index].weights.getCost();
        }

        // Keep info about shortest and cheapest paths
        Path path = paths[index];
        cheapestPathWeight = Math.min(cheapestPathWeight, path.weights.getCost());
        boolean matchesReferenceMode =
            maxDetourReferenceMode == -1
                || (path.loadingMode == maxDetourReferenceMode && !path.intermodal);

        if (matchesReferenceMode && path.weights.getCost() < cheapestReferencePathWeight) {
          cheapestReferencePathWeight = path.weights.getCost();
          cheapestPathLength = path.weights.getLength();
        }
      }
    }

    // Compute the max length of the paths that will be retained
    double maxPathLength = cheapestPathLength * assignmentParameters.getMaxDetour();

    for (int index = 0;
        index < assignmentParameters.getNbIterations() * availableModeMeans.length;
        index++) {

      if (paths[index].isValid) {

        if (assignmentParameters.isKeepOnlyCheapestIntermodalPath()) {
          /*
           * If the intermodal paths have to be kept only if they are the cheapest solution,
           * remove the intermodal paths that are more expensive than any other mode.
           */
          if (paths[index].intermodal) {
            if (cheapestPathWeight < cheapestIntermodalPathWeight) {
              // Mark cell
              paths[index].isValid = false;
            } else {
              if (paths[index].weights.getCost() > cheapestIntermodalPathWeight) {
                // Mark cell
                paths[index].isValid = false;
              }
            }
          }
        }

        // Limit length of alternative paths to the length of the shortest path x maxDettour
        if (assignmentParameters.getMaxDetour() > 0) {
          if (paths[index].weights.getLength() > maxPathLength) {
            // Mark cell
            paths[index].isValid = false;
          }
        }

        // Zero length paths can appear when "inclusions" are used
        if (paths[index].weights.length == 0) {
          paths[index].isValid = false;
        }

        // Only retain valid paths
        if (paths[index].isValid) {
          // Get the list of paths for this mode or create one
          PathsForMode altPathsList = hm.get(paths[index].intermodalModeKey);
          if (altPathsList == null) {
            // Create new list of path for this mode
            altPathsList = new PathsForMode(paths[index]);
            hm.put(paths[index].intermodalModeKey, altPathsList);
          } else {
            // A list of paths for this mode already exists. Update it
            altPathsList.addPath(paths[index]);
          }
        }
      }
    }
    return hm;
  }

  /** Log the OD pairs between which no paths could be found. */
  private void logLostPaths() {
    /*
     * Go through all the potential lost paths and look if they could be reached by another
     * mode/means.
     */
    Iterator<String> it = potentialLostPaths.keySet().iterator();
    while (it.hasNext()) {
      // Get key
      String key = it.next();
      if (foundPaths.get(key) == null) {
        ODCell lostPath = potentialLostPaths.get(key);

        System.out.println(
            "delete from "
                + assignmentParameters.getODMatrix()
                + " where "
                + NodusC.DBF_GROUP
                + "="
                + lostPath.getGroup()
                + " and "
                + NodusC.DBF_ORIGIN
                + "="
                + lostPath.getOriginNodeId()
                + " and "
                + NodusC.DBF_DESTINATION
                + "="
                + lostPath.getDestinationNodeId()
                + ";");
      }
    }

    potentialLostPaths.clear();
    foundPaths.clear();
  }

  /** Retrieves the path associated to an OD cell. Returns a Path object. node. */
  private Path markPaths(
      byte groupIndex,
      int nodeIndex,
      int[] pi,
      int iteration,
      ODCell demand,
      LinkedList<MFPathHeader> pathHeaders) {
    VirtualNodeList[] virtualNodeList = virtualNet.getVirtualNodeLists();
    String key = null;

    // double weight = 0.0;
    // float length = 0;

    int currentPathIndex = 0;
    if (pathBuffer.isSavePaths()) {
      currentPathIndex = getNewPathIndex();
    }

    // Build path from end to begin node
    int destinationNodeIndex =
        virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true);
    virtualNet.getNodeIndexInVirtualNodeList(demand.getOriginNodeId(), true);

    int endNode = virtualNodeList[destinationNodeIndex].getUnloadingVirtualNodeId();

    int currentNode = endNode;
    boolean isPathFound = true;
    PathWeights pathCosts = new PathWeights();
    int nbTranshipments = 0;
    byte loadingMode = 0;
    byte loadingMeans = 0;
    byte unloadingMode = 0;
    byte unloadingMeans = 0;

    int intermodalModeKey = 1;

    if (assignmentParameters.isLogLostPaths()) {
      key = demand.getOriginNodeId() + "-" + demand.getDestinationNodeId();
    }

    int mode = -1;
    int beginNode = virtualNodeList[nodeIndex].getLoadingVirtualNodeId();
    Path path = new Path();
    List<Integer> linkIds = new ArrayList<>();
    while (currentNode != beginNode) {
      // Predecessor
      int predecessor = pi[currentNode];

      if (predecessor == 0) {

        if (assignmentParameters.isLogLostPaths()) {
          if (potentialLostPaths.get(key) == null) {
            potentialLostPaths.put(key, demand);
          }
        }
        // weight = Double.MAX_VALUE;
        path.isValid = false;
        isPathFound = false;

        break;
      } else {
        AdjacencyNode an = graph[predecessor];

        while (an.endVirtualNode != currentNode) {
          an = an.nextNode;
        }

        // Mark this link as being included in the path for this iteration
        edgeUpdates.markPathEdge(an);
        VirtualLink vl = an.virtualLink;

        vl.addCell(groupIndex, new PathODCell(iteration, demand.getQuantity()));

        if (vl.getType() == VirtualLink.TYPE_TRANSHIP) {
          intermodalModeKey *=
              NodusC.MAXMM * vl.getBeginVirtualNode().getMode() + vl.getEndVirtualNode().getMode();
          path.intermodal = true;
          nbTranshipments++;
        }

        switch (vl.getType()) {
          case VirtualLink.TYPE_LOAD:
            pathCosts.ldCost += vl.getCost(groupIndex);
            pathCosts.ldDuration += vl.getDuration(groupIndex);
            loadingMode = vl.getEndVirtualNode().getMode();
            loadingMeans = vl.getEndVirtualNode().getMeans();
            break;
          case VirtualLink.TYPE_UNLOAD:
            pathCosts.ulCost += vl.getCost(groupIndex);
            pathCosts.ulDuration += vl.getDuration(groupIndex);
            unloadingMode = vl.getBeginVirtualNode().getMode();
            unloadingMeans = vl.getBeginVirtualNode().getMeans();
            break;
          case VirtualLink.TYPE_TRANSIT:
            pathCosts.trCost += vl.getCost(groupIndex);
            pathCosts.trDuration += vl.getDuration(groupIndex);
            break;
          case VirtualLink.TYPE_TRANSHIP:
            pathCosts.tpCost += vl.getCost(groupIndex);
            pathCosts.tpDuration += vl.getDuration(groupIndex);
            break;
          case VirtualLink.TYPE_STOP:
            pathCosts.stpCost += vl.getCost(groupIndex);
            pathCosts.stpDuration += vl.getDuration(groupIndex);
            break;
          case VirtualLink.TYPE_SWITCH:
            pathCosts.swCost += vl.getCost(groupIndex);
            pathCosts.swDuration += vl.getDuration(groupIndex);
            break;
          case VirtualLink.TYPE_MOVE:
            mode = vl.getBeginVirtualNode().getMode();
            pathCosts.mvCost += vl.getCost(groupIndex);
            if (assignmentParameters.hasDurationFunctions()) {
              pathCosts.mvDuration += vl.getDuration(groupIndex);
            } else {
              pathCosts.mvDuration += vl.getDefaultDuration();
            }
            pathCosts.length += vl.getLength();
            pathBuffer.savePathLink(vl, currentPathIndex);
            break;
          default:
            break;
        }

        // Build the key that represents this path
        path.detailedPathKey += vl.getId();
        linkIds.add(vl.getId());

        currentNode = predecessor;
      }
    }

    path.intermodalModeKey = path.intermodal ? intermodalModeKey : mode;
    path.loadingMode = loadingMode;
    path.loadingMeans = loadingMeans;
    // path.weight = weight;
    path.weights = pathCosts;
    // path.length = length;

    // The total cost of a path must be strictly positive
    if (isPathFound && pathCosts.getCost() == 0.0) {
      setErrorMessage(
          i18n.get(
              AssignmentWorker.class,
              "Cost_must_be_positive",
              "The total cost for all paths must be strictly positive"));
      return null;
    }

    // Associate the key with the length to maximize chances to have a unique key
    path.detailedPathKey *= path.weights.getLength();

    // Save the header of this detailed path if needed
    if (isPathFound) {
      pathIdentity.record(path, linkIds);
      // Keep track of found path for logging
      if (assignmentParameters.isLogLostPaths()) {
        if (foundPaths.get(key) == null) {
          foundPaths.put(key, demand);
        }
      }

      if (pathBuffer.isSavePaths()) {
        // The quantity will be computed later
        pathHeaders.add(
            new MFPathHeader(
                iteration,
                demand,
                pathCosts,
                loadingMode,
                loadingMeans,
                unloadingMode,
                unloadingMeans,
                nbTranshipments,
                currentPathIndex));
      }
    }

    return path;
  }

  /** Computes the market marketShare of each mode/path. */
  private boolean modalSplit(ODCell odCell) {
    List<PathsForMode> list = new ArrayList<PathsForMode>(getPaths().values());
    return modalSplitMethod.split(odCell, list);
  }

  private boolean updateProgressBar(int nodeIndex) {
    if (virtualNet.getVirtualNodeLists()[nodeIndex].hasDemandForGroup(currentGroup, odClass)) {
      if (!updateProgress(assignmentProgressMessage())) {
        return false;
      }
    }
    return true;
  }

  private String assignmentProgressMessage() {
    if (virtualNet.getNbODClasses() > 1) {
      return MessageFormat.format(
          i18n.get(Assignment.class, "Assignment_for_class", "Assignment for class {0}"), odClass);
    }
    return i18n.get(Assignment.class, "Assignment", "Assignment");
  }
}
