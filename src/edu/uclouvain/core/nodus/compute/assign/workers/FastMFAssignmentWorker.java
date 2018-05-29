/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.AltPathsList;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.costs.TransitTimesParser;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.PathODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.utils.WorkQueue;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * The fast multi-flow assignment computes one or several alternative routes for each mode/means
 * combination. It is based on the algorithm of Dijkstra, but this has some side effects described
 * in <i> Jourquin B., A multi-flow multi-modal assignment procedure on large freight transportation
 * networks, Studies in Regional Science, Vol 35, n°4, pp. 929- 946, 2005.</i>.
 *
 * <p>The exact multi-flow assignment algorithm can be used to avoid this problem, but it is much
 * slower.
 *
 * @author Bart Jourquin
 */
public class FastMFAssignmentWorker extends AssignmentWorker {

  private BinaryHeapDijkstra shortestPath;

  private int[] availableModeMeans;

  private ODCell demand;


  /**
   * Two hash tables. The first will contain the OD pairs between which at least one path was found,
   * the second the pairs between which, during an iteration, no path was found. All the pairs which
   * are in the second and not in the first hash table will be considered as "lost paths"
   */
  HashMap<String, ODCell> foundPaths = new HashMap<>();

  HashMap<String, ODCell> potentialLostPaths = new HashMap<>();

  ModalSplitMethod modalSplitMethod;

  /** Array that wall contain the weights of the paths at the successive iterations. */
  private Path[][] paths;

  /**
   * Initializes an Assignment Worker.
   *
   * @param queue The WorkQueue the assignment will run in.
   */
  public FastMFAssignmentWorker(WorkQueue queue) {
    super(queue);
  }

  /**
   * Runs a fast multi-flow assignment.
   *
   * @return True on success.
   */
  @Override
  boolean doAssignment() {

    LinkedList<PathHeader> pathHeaders = new LinkedList<>();

    double costMarkup = 1 + assignmentParameters.getCostMarkup();

    // Initialize
    graph = virtualNet.generateAdjacencyList(groupIndex);
    shortestPath = new BinaryHeapDijkstra(graph, virtualNet);
    availableModeMeans = virtualNet.getAvailableModeMeans(groupIndex);

    // Load the transit times for this group
    transitTimes =
        new TransitTimesParser(
            assignmentParameters.getCostFunctions(),
            assignmentParameters.getScenario(),
            currentGroup,
            virtualNet.getAvailableModeMeans(groupIndex));

    paths = new Path[assignmentParameters.getNbIterations() * availableModeMeans.length][];

    // Initialize the modal split method from the name found in the assignment parameters
    modalSplitMethod = getModalSplitMethod(assignmentParameters.getModalSplitMethodName());
    if (modalSplitMethod == null) {
      return false;
    }
    modalSplitMethod.initialize(currentGroup, nodusProject, assignmentParameters);

    // Get the structure of the virtual network
    VirtualNodeList[] vnl = virtualNet.getVirtualNodeLists();

    // Go through all the nodes
    for (int nodeIndex = 0; nodeIndex < vnl.length; nodeIndex++) {
      if (!updateProgressBar(nodeIndex)) {
        return false;
      }

      // This worker could also have been stopped by another thread
      if (isCancelled()) {
        return false;
      }

      demandList = vnl[nodeIndex].getDemandForGroup(currentGroup, odClass);

      /*
       * Handle only the demand nodes
       */
      if (demandList != null) {
        /*
         * Compute all the shortest paths (number of mode/means combinations * nb iterations)
         * in the virtual network starting from here
         */
        int beginNode = vnl[nodeIndex].getLoadingVirtualNodeId();
        int currentPathPropertiesIndex = 0;

        /*
         * Compute "iteration" alternative paths for each mode/means combination
         */
        for (int availableModeMean : availableModeMeans) {

          /*
           * Performance tip: only try to compute paths from here if there is a loading possibility
           * to the current  mode/means combination (loading virtual links are put at the end of
           * the virtual node list.
           */
          VirtualNode vn =
              virtualNet.getVirtualNodeLists()[nodeIndex].getVirtualNodeList().getLast();
          LinkedList<VirtualLink> ll = vn.getVirtualLinkList();
          boolean canLoadToCurrentModeMeansFromThisNode = false;
          Iterator<VirtualLink> it = ll.iterator();
          while (it.hasNext()) {
            VirtualLink vl = it.next();
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

          /*
           * Compute the paths for the current mode/means combination
           */
          for (int alternativePath = 0;
              alternativePath < assignmentParameters.getNbIterations();
              alternativePath++) {
            if (!canLoadToCurrentModeMeansFromThisNode) {
              paths[currentPathPropertiesIndex] = new Path[demandList.size()];
              for (int i = 0; i < demandList.size(); i++) {
                paths[currentPathPropertiesIndex][i] = new Path();
                paths[currentPathPropertiesIndex][i].weight = Double.MAX_VALUE;
              }
            } else {
              /*
               * Compute shortest path tree
               */
              shortestPath.compute(beginNode, demandList);

              /*
               * Mark the paths for all the destinations to reach and compute their costs
               */
              paths[currentPathPropertiesIndex] =
                  markPaths(nodeIndex, demandList.size(), currentPathPropertiesIndex, pathHeaders);

              /*
               * Increase the costs in the adjacency list to make the already used links more
               * expensive (not for last iteration).
               */
              if (alternativePath < assignmentParameters.getNbIterations() - 1) {
                for (int i = 1; i < graph.length; i++) {
                  AdjacencyNode current = graph[i];
                  while (current != null) {

                    if (current.inCurrentTree) {

                      /*
                       * Increase cost on used link if not yet done
                       */
                      if (!current.isIncreased) {
                        current.edgeWeight *= costMarkup;
                        current.isIncreased = true;
                      }
                    }
                    current = current.nextNode;
                  } // Next adjacency node for graph[i]
                } // next i

                /*
                 * Reset the "increased" flag for next iteration
                 */
                for (int i = 1; i < graph.length; i++) {
                  AdjacencyNode current = graph[i];
                  while (current != null) {
                    current.isIncreased = false;
                    current.inCurrentTree = false;
                    current = current.nextNode;
                  }
                }
              } // End if not last iteration (cost increase)
            }
            currentPathPropertiesIndex++;
          } // end of iteration

          // Reset the original weights on the links to prepare next mode/means combination
          for (int i = 1; i < graph.length; i++) {
            AdjacencyNode current = graph[i];
            while (current != null) {
              current.edgeWeight = current.originalEdgeWeight;
              current = current.nextNode;
            }
          }
        } // end of iteration for current mode/means combination

        // Log lost paths if needed
        if (assignmentParameters.isLogLostPaths()) {
          logLostPaths();
        }

        // Compute the fraction of the demand to be assigned to each alternative path
        // for this O-D line
        int currentPath = 0;
        Iterator<ODCell> it = demandList.iterator();
        while (it.hasNext()) {
          demand = it.next();

          if (!modalSplit(demand, currentPath)) {
            return false;
          }

          // Apply modal share to path headers
          if (assignmentParameters.isSavePaths()) {

            /* Update the path headers in memory to compute the quantity on each path */
            Iterator<PathHeader> it2 = pathHeaders.iterator();
            while (it2.hasNext()) {
              PathHeader ph = it2.next();

              if (ph.demand.getOriginNodeId() == demand.getOriginNodeId()
                  && ph.demand.getDestinationNodeId() == demand.getDestinationNodeId()) {
                if (paths[ph.iteration][currentPath].weight != Double.MAX_VALUE) {
                  double q = demand.getQuantity() * paths[ph.iteration][currentPath].weight;

                  if (!pathWriter.savePathHeader(
                      ph.iteration,
                      demand,
                      q,
                      ph.length,
                      ph.duration,
                      ph.pathCosts,
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
          }

          currentPath++;
        }
        pathHeaders.clear();

        // Now update the flow on the virtual links, using the just computed weights
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
      } // end of demand list
    } // Next node

    return true;
  }

  private HashMap<Integer, AltPathsList> getPathList(int currentPath) {

    HashMap<Integer, AltPathsList> hm = new HashMap<>();

    float cheapestPathLength = Float.MAX_VALUE;
    double cheapestIntermodalPathWeight = Double.MAX_VALUE;
    double cheapestPathWeight = Double.MAX_VALUE;

    // Clean unwanted paths
    for (int index = 0;
        index < assignmentParameters.getNbIterations() * availableModeMeans.length;
        index++) {

      if (paths[index][currentPath].weight != Double.MAX_VALUE) {

        /*
         * If the same path is found several times in the set of alternatives, just keep one
         * instance. To achieve this, just put an infinite cost on the additional instance.
         */
        if (index > 0) {
          for (int j = 0; j < index; j++) {
            if (paths[index][currentPath].detailedPathKey
                == paths[j][currentPath].detailedPathKey) {
              // Mark cell
              paths[index][currentPath].weight = Double.MAX_VALUE;
            }
          }
        }

        /* Keep the cheapest intermodal path, if any. Used later if intermodal solutions
         * have to be kept only if they are the cheapest transport solution.
         */
        if (paths[index][currentPath].intermodal
            && paths[index][currentPath].weight < cheapestIntermodalPathWeight) {
          cheapestIntermodalPathWeight = paths[index][currentPath].weight;
        }

        // Keep info about shortest and cheapest paths
        if (paths[index][currentPath].weight < cheapestPathWeight) {
          cheapestPathWeight = paths[index][currentPath].weight;
          cheapestPathLength = paths[index][currentPath].length;
        }
      }
    }

    // Compute the max length of the paths that will be retained
    double maxPathLength = cheapestPathLength * assignmentParameters.getMaxDetour();

    for (int index = 0;
        index < assignmentParameters.getNbIterations() * availableModeMeans.length;
        index++) {

      if (paths[index][currentPath].weight != Double.MAX_VALUE) {

        if (assignmentParameters.isKeepOnlyCheapestIntermodalPath()) {
          /*
           * If the intermodal paths have to be kept only if they are the cheapest solution,
           * remove the intermodal paths that are more expensive than any other mode
           */
          if (paths[index][currentPath].intermodal) {
            if (cheapestPathWeight < cheapestIntermodalPathWeight) {
              // Mark cell
              paths[index][currentPath].weight = Double.MAX_VALUE;
            } else {
              if (paths[index][currentPath].weight > cheapestIntermodalPathWeight) {
                // Mark cell
                paths[index][currentPath].weight = Double.MAX_VALUE;
              }
            }
          }
        }

        // Limit length of alternative paths to the length of the shortest path x maxDettour
        if (assignmentParameters.getMaxDetour() > 0) {
          if (paths[index][currentPath].length > maxPathLength) {
            // Mark cell
            paths[index][currentPath].weight = Double.MAX_VALUE;
          }
        }

        // Only retain valid paths
        if (paths[index][currentPath].weight != Double.MAX_VALUE) {
          // Get the list of paths for this mode or create one
          AltPathsList altPathsList = hm.get(paths[index][currentPath].intermodalModeKey);

          if (altPathsList == null) {
            // Create new list of path for this mode
            altPathsList = new AltPathsList(paths[index][currentPath]);
            hm.put(paths[index][currentPath].intermodalModeKey, altPathsList);
          } else {
            // A list of paths for this mode already exists. Update it
            altPathsList.addPath(paths[index][currentPath]);
          }
        }
      }
    }
    return hm;
  }

  /** Log the OD pairs between which no paths could be found. */
  private void logLostPaths() {
    /* Go through all the potential lost paths and look if they could be reached
     * by another mode/means
     */
    Iterator<String> it = potentialLostPaths.keySet().iterator();
    while (it.hasNext()) {
      // Get key
      String key = it.next();
      if (foundPaths.get(key) == null) {
        ODCell lostPath = potentialLostPaths.get(key);
        System.out.println(
            lostPath.getGroup()
                + ", "
                + lostPath.getOriginNodeId()
                + ", "
                + lostPath.getDestinationNodeId()
                + ", "
                + lostPath.getQuantity());
      }
    }

    potentialLostPaths.clear();
    foundPaths.clear();
  }

  /**
   * Read all the paths associated to the demand from a given node. Returns a vector with the costs
   * of all the paths from this node.
   */
  private Path[] markPaths(
      int nodeIndex, int nbOD, int iteration, LinkedList<PathHeader> pathHeaders) {
    VirtualNodeList[] virtualNodeList = virtualNet.getVirtualNodeLists();
    int[] pi = shortestPath.getPredecessors();
    int beginNode = virtualNodeList[nodeIndex].getLoadingVirtualNodeId();
    byte[] groups = virtualNet.getGroups();
    int indexInODRow = 0;
    Path[] paths = new Path[nbOD];
    String key = null;

    int mode = -1;

    for (int i = 0; i < nbOD; i++) {
      paths[i] = new Path();
    }

    // Scan the demand list
    Iterator<ODCell> it =
        virtualNodeList[nodeIndex].getDemandForGroup(groups[groupIndex], odClass).iterator();

    while (it.hasNext()) {
      ODCell demand = it.next();
      PathODCell pathODCell = new PathODCell(iteration, indexInODRow, demand.getQuantity());
      double weight = 0.0;
      int intermodalModeKey = 1;

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
      float pathLength = 0;
      float pathDuration = 0;
      PathDetailedCosts pathCosts = new PathDetailedCosts();

      int nbTranshipments = 0;
      byte loadingMode = 0;
      byte loadingMeans = 0;
      byte unloadingMode = 0;
      byte unloadingMeans = 0;

      if (assignmentParameters.isLogLostPaths()) {
        key = demand.getOriginNodeId() + "-" + demand.getDestinationNodeId();
      }

      while (currentNode != beginNode) {
        // Predecessor
        int predecessor = pi[currentNode];

        if (predecessor == 0) {

          if (assignmentParameters.isLogLostPaths()) {
            if (potentialLostPaths.get(key) == null) {
              potentialLostPaths.put(key, demand);
            }
          }
          weight = Double.MAX_VALUE;
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

          vl.addCell(groupIndex, pathODCell);

          // Add the real cost to the total cost
          weight += vl.getWeight(groupIndex);

          // Which mode and means is used on the path?
          if (vl.getType() == VirtualLink.TYPE_MOVE) {
            mode = vl.getBeginVirtualNode().getMode();
            pathLength += vl.getLength();
            pathDuration += vl.getDuration();
          }

          // Detect if this is an intermodal path
          if (vl.getType() == VirtualLink.TYPE_TRANSHIP) {
            intermodalModeKey *=
                NodusC.MAXMM * vl.getBeginVirtualNode().getMode()
                    + vl.getEndVirtualNode().getMode();
            paths[indexInODRow].intermodal = true;
            nbTranshipments++;
          }

          switch (vl.getType()) {
            case VirtualLink.TYPE_LOAD:
              pathCosts.ldCosts += vl.getWeight(groupIndex);
              loadingMode = vl.getEndVirtualNode().getMode();
              loadingMeans = vl.getEndVirtualNode().getMeans();
              pathDuration += transitTimes.getLoadingDuration(loadingMode, loadingMeans);
              break;
            case VirtualLink.TYPE_UNLOAD:
              pathCosts.ulCosts += vl.getWeight(groupIndex);
              unloadingMode = vl.getBeginVirtualNode().getMode();
              unloadingMeans = vl.getBeginVirtualNode().getMeans();
              pathDuration += transitTimes.getUnloadingDuration(unloadingMode, unloadingMeans);
              break;
            case VirtualLink.TYPE_TRANSIT:
              pathCosts.trCosts += vl.getWeight(groupIndex);
              break;
            case VirtualLink.TYPE_TRANSHIP:
              pathCosts.tpCosts += vl.getWeight(groupIndex);
              break;
            case VirtualLink.TYPE_MOVE:
              pathCosts.mvCosts += vl.getWeight(groupIndex);

              // Save detailed path info if needed
              if (pathWriter.isSavePaths()) {
                pathWriter.savePathLink(vl, currentPathIndex);
              }

              break;
            default:
              break;
          }

          // Build the key that represents this path
          paths[indexInODRow].detailedPathKey += vl.getId();

          currentNode = predecessor;
        }
      }

      // Save the properties of the path. Will be used to split the flow over the paths
      if (paths[indexInODRow].intermodal) {
        mode = intermodalModeKey;
      }

      paths[indexInODRow].intermodalModeKey = mode;
      paths[indexInODRow].loadingMode = loadingMode;
      paths[indexInODRow].loadingMeans = loadingMeans;
      paths[indexInODRow].weight = weight;
      paths[indexInODRow].pathDetailedCosts = pathCosts;
      paths[indexInODRow].length = pathLength;
      paths[indexInODRow].duration = pathDuration;

      // Associate the key with the length to maximize chances to have a unique key
      paths[indexInODRow].detailedPathKey *= pathLength;

      indexInODRow++;

      if (isPathFound) {
        // Keep track of found path for logging
        if (assignmentParameters.isLogLostPaths()) {
          if (foundPaths.get(key) == null) {
            foundPaths.put(key, demand);
          }
        }

        // Save the header of this detailed path if needed
        if (pathWriter.isSavePaths()) {
          // The quantity assigned to this path will be computed later
          pathHeaders.add(
              new PathHeader(
                  iteration,
                  demand,
                  pathLength,
                  pathDuration,
                  pathCosts,
                  loadingMode,
                  loadingMeans,
                  unloadingMode,
                  unloadingMeans,
                  nbTranshipments,
                  currentPathIndex));
        }
      }
    }

    return paths;
  }

  /**
   * Compute the market share of each mode/path.
   *
   * @param currentPath : the path to handle in the result set
   * @param msp = the parameters used for the modal split model
   * @return True on success
   */
  private boolean modalSplit(ODCell odCell, int currentPath) {

    // Retrieve all the valid routes
    HashMap<Integer, AltPathsList> hm = getPathList(currentPath);

    return modalSplitMethod.split(odCell, hm);
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
