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

import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.costs.TransitTimesParser;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.utils.WorkQueue;

import java.text.MessageFormat;
import java.util.Iterator;

/**
 * Frank-Wolfe equilibrium assignment algorithm. See <i> Frank M. and Wolfe P.‚An algorithm for
 * quadratic programming, 1956, Naval Research Logistics Quarterly, Vol 3 , pp. 95-110.</i> and
 * <i>Jourquin B. and Limbourg S., Equilibrium Traffic Assignment on Large Virtual Networks,
 * Implementation issues and limits for Multi-Modal Freight Transport, European Journal of Transport
 * and Infrastructure Research, Vol 6, n°3, pp. 205-228, 2006.</i>
 *
 * @author Bart Jourquin
 */
public class FrankWolfeAssignmentWorker extends AssignmentWorker {

  private BinaryHeapDijkstra shortestPath;

  /**
   * Initializes an Assignment Worker.
   *
   * @param queue The WorkQueue the assignment will run in.
   */
  public FrankWolfeAssignmentWorker(WorkQueue queue) {
    super(queue);
  }

  /**
   * Runs a Frank-Wolfe assignment algorithm.
   *
   * @return True on success.
   */
  @Override
  boolean doAssignment() {
    // Initialize the adjacency list for current group
    graph = virtualNet.generateAdjacencyList(groupIndex);
    shortestPath = new BinaryHeapDijkstra(graph, virtualNet);
    
    // Load the transit times for this group
    transitTimes =
        new TransitTimesParser(
            assignmentParameters.getCostFunctions(),
            assignmentParameters.getScenario(),
            currentGroup,
            virtualNet.getAvailableModeMeans(groupIndex));

    // Scan all the nodes
    for (int nodeIndex = 0; nodeIndex < virtualNet.getVirtualNodeLists().length; nodeIndex++) {
      if (virtualNet.getVirtualNodeLists()[nodeIndex].hasDemandForGroup(
          virtualNet.getGroups()[groupIndex], odClass)) {
        String s = null;
        if (virtualNet.getNbODClasses() > 1) {
          s =
              MessageFormat.format(
                  i18n.get(Assignment.class, "Assignment_for_class", "Assignment for class {0}"),
                  odClass);
        } else {
          s = i18n.get(Assignment.class, "Assignment", "Assignment");
        }
        /**
         * @param nodeIndex
         * @return boolean
         */
        if (!updateProgress(s)) {
          return false;
        }
      }

      // This worker could also have been stopped by another thread
      if (isCancelled()) {
        return false;
      }

      // Get the demand associated to this node for the current group
      demandList =
          virtualNet.getVirtualNodeLists()[nodeIndex].getDemandForGroup(
              virtualNet.getGroups()[groupIndex], odClass);

      if (demandList != null) {
        // Compute all the shortest paths in the virtual network starting from here
        int beginNode = virtualNet.getVirtualNodeLists()[nodeIndex].getLoadingVirtualNodeId();
        shortestPath.compute(beginNode, demandList);

        // Build all the relevant detailed paths
        if (!readPaths(nodeIndex)) {
          return false;
        }
      }
    }

    graph = null;

    return true;
  }

  /**
   * Build all the paths starting from the origin node, loading each used link with of the demand.
   * The quantity is added to the auxiliary flow. It will later combined with the current flow
   *
   * @param nodeIndex int
   * @return True on success.
   */
  private boolean readPaths(int nodeIndex) {

    int[] pi = shortestPath.getPredecessors();
    int beginNode = virtualNet.getVirtualNodeLists()[nodeIndex].getLoadingVirtualNodeId();

    // Scan the demand list
    Iterator<ODCell> it = demandList.iterator();

    while (it.hasNext()) {
      ODCell demand = it.next();

      // Build path from end to begin node
      int destinationNodeIndex =
          virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true);
      int endNode =
          virtualNet.getVirtualNodeLists()[destinationNodeIndex].getUnloadingVirtualNodeId();

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

      while (currentNode != beginNode) {
        // Predecessor
        int predecessor = pi[currentNode];

        if (predecessor == 0) {
          if (assignmentParameters.isLogLostPaths()) {
            System.out.println(
                currentGroup
                    + ", "
                    + virtualNet.getVirtualNodeLists()[nodeIndex].getRealNodeId()
                    + ", "
                    + virtualNet.getVirtualNodeLists()[destinationNodeIndex].getRealNodeId()
                    + ", "
                    + demand.getQuantity());
          }
          isPathFound = false;

          break;
        } else {
          AdjacencyNode an = graph[predecessor];

          while (an.endVirtualNode != currentNode) {
            an = an.nextNode;
          }

          an.virtualLink.addAuxiliaryFlow(groupIndex, demand.getQuantity());

          // Save this link in the detailed path table if needed
          if (pathWriter.isSavePaths()) {
            VirtualLink vl = an.virtualLink;

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
                nbTranshipments++;
                break;
              case VirtualLink.TYPE_MOVE:
                pathCosts.mvCosts += vl.getWeight(groupIndex);
                pathLength += vl.getLength();
                pathDuration += vl.getDuration();
                pathWriter.savePathLink(vl);
                break;
              default:
                break;
            }
          }

          // Go to next chunk in the path
          currentNode = predecessor;
        }
      }

      // Save the header of this detailed path if needed
      if (isPathFound && pathWriter.isSavePaths()) {
        if (!pathWriter.savePathHeader(
            iteration,
            demand,
            demand.getQuantity(),
            pathLength,
            pathDuration,
            pathCosts,
            loadingMode,
            loadingMeans,
            unloadingMode,
            unloadingMeans,
            nbTranshipments)) {
          return false;
        }
      }
    }
    return true;
  }
}
