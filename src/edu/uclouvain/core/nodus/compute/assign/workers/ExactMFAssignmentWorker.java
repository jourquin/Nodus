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

package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.PathsForMode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapAStar;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.PathODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.utils.ModalSplitMethodsLoader;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * The exact multi-flow assignment computes one or several alternative routes for each mode/means
 * combination. It is based on the AStar shortest path algorithm instead of the algorithm of
 * Dijkstra in order to prevent some side effects described in <i> Jourquin B., A multi-flow
 * multi-modal assignment procedure on large freight transportation networks, Studies in Regional
 * Science, Vol 35, n°4, pp. 929- 946, 2005.</i>.
 *
 * <p>Even if the AStar algorithm is faster than Dijkstra if the shortest path between a single
 * origin-destination pair must be computed, running AStar for each pair of a row in the OD matrix
 * is slower than running Dijkstra, which computes, in a single run, all the routes for the this row
 * of OD pairs. The exact multi-flow assignment is therefore (much) slower than the fast multi-fmow
 * assignment.
 *
 * @author Bart Jourquin
 */
public class ExactMFAssignmentWorker extends AssignmentWorker {

  /** This value is used when the "force modal split is used". */
  static final double forcedCostPerIteration = Integer.MAX_VALUE;

  int[] availableModeMeans;

  private BinaryHeapAStar shortestPath;

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

    double costMarkup = 1 + assignmentParameters.getCostMarkup();

    LinkedList<MFPathHeader> pathHeaders = new LinkedList<>();

    // Initialize the adjacency list for current group
    graph = virtualNet.generateAdjacencyList(groupIndex);

    shortestPath = new BinaryHeapAStar(graph);
    availableModeMeans = virtualNet.getAvailableModeMeans(groupIndex);
    paths = new Path[assignmentParameters.getNbIterations() * availableModeMeans.length];

    // Use a copy of the already initialized method
    ModalSplitMethod msp =
        ModalSplitMethodsLoader.getModalSplitMethod(assignmentParameters.getModalSplitMethodName());

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

        Iterator<ODCell> it = demandList.iterator();
        while (it.hasNext()) {
          int currentPathPropertiesIndex = 0;

          // Get demand and num of unloading virtual node
          ODCell demand = it.next();
          int endNodeIndex =
              virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true);
          int endNode = virtualNet.getVirtualNodeLists()[endNodeIndex].getUnloadingVirtualNodeId();

          /*
           * Compute "iteration" alternative paths for each mode/means combination
           */
          for (int availableModeMean : availableModeMeans) {

            /*
             * Performance tip : only try to compute paths from here if there is a loading
             *  possibility to the current mode/means combination (loading virtual links are
             *   put at the end of the virtual node list.
             */
            VirtualNode vn =
                virtualNet.getVirtualNodeLists()[nodeIndex].getVirtualNodeList().getLast();
            LinkedList<VirtualLink> ll = vn.getVirtualLinkList();
            boolean canLoadToCurrentModeMeansFromThisNode = false;
            Iterator<VirtualLink> it2 = ll.iterator();
            while (it2.hasNext()) {
              VirtualLink vl = it2.next();
              if (vl.getEndVirtualNode().getModeMeansKey() == availableModeMean) {
                canLoadToCurrentModeMeansFromThisNode = true;
                break;
              }
            }

            /*
             * Only open graph to current mode/means combination
             */
            if (canLoadToCurrentModeMeansFromThisNode) {
              for (int i = 1; i < graph.length; i++) {
                AdjacencyNode current = graph[i];
                while (current != null && current.virtualLink != null) {
                  if (current.virtualLink.getType() == VirtualLink.TYPE_LOAD) {
                    if (current.virtualLink.getEndVirtualNode().getModeMeansKey()
                        != availableModeMean) {
                      current.edgeWeight = Double.POSITIVE_INFINITY;
                    }
                  }
                  current = current.nextNode;
                }
              }
            }

            for (int alternativePath = 0;
                alternativePath < assignmentParameters.getNbIterations();
                alternativePath++) {
              // Compute shortest path tree
              shortestPath.compute(beginNode, endNode);

              // Mark the paths for all the destinations to reach and compute their costs
              paths[currentPathPropertiesIndex] =
                  markPaths(
                      groupIndex,
                      nodeIndex,
                      shortestPath,
                      currentPathPropertiesIndex,
                      demand,
                      pathHeaders);

              /*
               * Increase the costs in the adjacency list to make the already used links more
               *  expensive (not for last iteration).
               */
              if (alternativePath < assignmentParameters.getNbIterations() - 1) {
                for (int i = 1; i < graph.length; i++) {
                  AdjacencyNode current = graph[i];
                  while (current != null) {

                    if (current.inCurrentTree) {
                      // Increase cost on used link if not yet done
                      current.edgeWeight *= costMarkup;
                    }
                    current = current.nextNode;
                  }
                }
              }
              currentPathPropertiesIndex++;
            } // Next alternative path

            // Reset the original weights of the links to prepare next OD matrix cell
            for (int i = 1; i < graph.length; i++) {
              AdjacencyNode current = graph[i];

              while (current != null) {
                current.edgeWeight = current.originalEdgeWeight;
                current.inCurrentTree = false;
                current = current.nextNode;
              }
            }
          } // Next mode/means

          // Log lost paths if needed
          if (assignmentParameters.isLogLostPaths()) {
            logLostPaths();
          }

          // Compute the fraction of the demand to be assigned to each alternative path
          // for this O-D cell
          if (!modalSplit(demand)) {
            return false;
          }

          // Apply modal marketShare to path headers
          if (assignmentParameters.isSavePaths()) {

            // Update the path headers in memory to compute the quantity on each path
            Iterator<MFPathHeader> it3 = pathHeaders.iterator();
            while (it3.hasNext()) {
              MFPathHeader ph = it3.next();

              if (ph.demand.getOriginNodeId() == demand.getOriginNodeId()
                  && ph.demand.getDestinationNodeId() == demand.getDestinationNodeId()) {
                if (paths[ph.iteration].isValid) {
                  double q = demand.getQuantity() * paths[ph.iteration].marketShare;
                  if (!pathWriter.savePathHeader(
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
          }

          // Update flow on virtual links
          for (int i = 1; i < graph.length; i++) {
            AdjacencyNode current = graph[i];

            while (current != null) {
              VirtualLink vl = current.virtualLink;
              if (vl != null) {
                vl.spreadFlowOverPaths(groupIndex, paths);
              }

              current = current.nextNode;
            }
          }
        } // end of the demand cell
      } // end of demand list
    } // Next node

    graph = null;

    return true;
  }

  private HashMap<Integer, PathsForMode> getPaths() {

    HashMap<Integer, PathsForMode> hm = new HashMap<>();

    float cheapestPathLength = Float.MAX_VALUE;
    double cheapestIntermodalPathWeight = Double.MAX_VALUE;
    double cheapestPathWeight = Double.MAX_VALUE;

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
            if (paths[index].detailedPathKey == paths[j].detailedPathKey) {
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
        if (paths[index].weights.getCost() < cheapestPathWeight) {
          cheapestPathWeight = paths[index].weights.getCost();
          cheapestPathLength = paths[index].weights.getLength();
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
      BinaryHeapAStar shortestPath,
      int iteration,
      ODCell demand,
      LinkedList<MFPathHeader> pathHeaders) {
    VirtualNodeList[] virtualNodeList = virtualNet.getVirtualNodeLists();
    int[] pi = shortestPath.getPredecessors();

    String key = null;

    Path path = new Path();

    int mode = -1;

    PathODCell pathODCell = new PathODCell(iteration, demand.getQuantity());
    // double weight = 0.0;
    // float length = 0;

    int currentPathIndex = 0;
    if (pathWriter.isSavePaths()) {
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

    int beginNode = virtualNodeList[nodeIndex].getLoadingVirtualNodeId();
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
        an.inCurrentTree = true;
        VirtualLink vl = an.virtualLink;

        /*
         * Performance issue: all the used virtual links are put in a list in order not to need a
         * browse through the complete virtual network to find them when needed
         */
        vl.addCell(groupIndex, pathODCell);

        if (vl.getType() == VirtualLink.TYPE_TRANSHIP) {
          intermodalModeKey *=
              NodusC.MAXMM * vl.getBeginVirtualNode().getMode() + vl.getEndVirtualNode().getMode();
          mode = intermodalModeKey;
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
            pathWriter.savePathLink(vl, currentPathIndex);
            break;
          default:
            break;
        }

        // Build the key that represents this path
        path.detailedPathKey += vl.getId();

        currentNode = predecessor;
      }
    }

    path.intermodalModeKey = mode;
    path.loadingMode = loadingMode;
    path.loadingMeans = loadingMeans;
    // path.weight = weight;
    path.weights = pathCosts;
    // path.length = length;

    // Associate the key with the length to maximize chances to have a unique key
    path.detailedPathKey *= path.weights.getLength();

    // Save the header of this detailed path if needed
    if (isPathFound) {
      // Keep track of found path for logging
      if (assignmentParameters.isLogLostPaths()) {
        if (foundPaths.get(key) == null) {
          foundPaths.put(key, demand);
        }
      }

      if (pathWriter.isSavePaths()) {
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
      String s = null;
      if (virtualNet.getNbODClasses() > 1) {
        s =
            MessageFormat.format(
                i18n.get(Assignment.class, "Assignment_for_class", "Assignment for class {0}"),
                odClass);
      } else {
        s = i18n.get(Assignment.class, "Assignment", "Assignment");
      }

      if (!updateProgress(s)) {
        return false;
      }
    }
    return true;
  }
}
