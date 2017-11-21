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
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.utils.WorkQueue;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A static All-Or-Nothing time dependent assignment algorithm. In this algorithm, each link of the
 * path is assigned to a given time period. To achieve this, each computed path is "scanned" from
 * the origin to the destination, and the arrival time at the end of the successive links are
 * computed.
 *
 * @author Bart Jourquin
 */
public class StaticAoNTimeDependentAssignmentWorker extends AssignmentWorker {

  private BinaryHeapDijkstra shortestPath;

  float assignmentEndTime;
  float assignmentStartTime;
  int nbTimeSlices;
  float timeSliceDuration;

  /**
   * Initializes an Assignment Worker.
   *
   * @param queue The WorkQueue the assignment will run in.
   */
  public StaticAoNTimeDependentAssignmentWorker(WorkQueue queue) {
    super(queue);
  }

  /**
   * Runs a static All-Or-Nothing time dependent assignment algorithm.
   *
   * @return True on success.
   */
  @Override
  boolean doAssignment() {

    // Initialize the adjacency list for current group
    graph = virtualNet.generateAdjacencyList(groupIndex);
    shortestPath = new BinaryHeapDijkstra(graph, virtualNet);

    // Scan all the nodes
    for (int nodeIndex = 0; nodeIndex < virtualNet.getVirtualNodeLists().length; nodeIndex++) {

      // Update progress bar
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

    return true;
  }

  /** Build all the paths starting from the origin node. */
  private boolean readPaths(int nodeIndex) {
    int[] pi = shortestPath.getPredecessors();
    int beginNode = virtualNet.getVirtualNodeLists()[nodeIndex].getLoadingVirtualNodeId();

    // Scan the demand list
    Iterator<ODCell> it = demandList.iterator();

    while (it.hasNext()) {
      ODCell demand = it.next();

      LinkedList<VirtualLink> orderedLinkList = new LinkedList<>();

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

      // Get starting time of route in seconds
      int odStartingTime = demand.getStartingTime();
      int currentTime = odStartingTime;

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

          /**
           * As the path is build in the reverse order (starting from destination), the used links
           * must be saved in order to compute the time of arrival in each link later
           */
          orderedLinkList.addFirst(an.virtualLink);

          // Go to next chunk in the path
          currentNode = predecessor;
        }
      }

      // Now, rebuild the path in the correct order
      Iterator<VirtualLink> pathIterator = orderedLinkList.iterator();
      while (pathIterator.hasNext()) {
        VirtualLink vl = pathIterator.next();

        // Compute the arrival time at the end of this link
        byte timeSlice = 0;

        if (vl.getType() == VirtualLink.TYPE_MOVE) {
          currentTime += (int) (vl.getLength() * 3600) / vl.getSpeed();

          if (currentTime >= assignmentStartTime && currentTime <= assignmentEndTime) {
            timeSlice = (byte) Math.floor((currentTime - assignmentStartTime) / timeSliceDuration);

            // Stop if out of time frame
            if (timeSlice == nbTimeSlices) {
              break;
            }
          } else {
            System.out.println(
                demand.getOriginNodeId()
                    + "-"
                    + demand.getDestinationNodeId()
                    + " starting at "
                    + demand.getStartingTime() / 60
                    + " minutes after midnight is out of time frame");
          }

          // Save this link in the detailed path table if needed
          if (pathWriter.isSavePaths()) {

            switch (vl.getType()) {
              case VirtualLink.TYPE_LOAD:
                pathCosts.ldCosts += vl.getWeight(groupIndex);
                loadingMode = vl.getEndVirtualNode().getMode();
                loadingMeans = vl.getEndVirtualNode().getMeans();
                break;
              case VirtualLink.TYPE_UNLOAD:
                pathCosts.ulCosts += vl.getWeight(groupIndex);
                unloadingMode = vl.getBeginVirtualNode().getMode();
                unloadingMeans = vl.getBeginVirtualNode().getMeans();
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
        }
        vl.addFlow(groupIndex, timeSlice, demand.getQuantity());
      }

      // Save the header of this detailed path if needed
      if (isPathFound && pathWriter.isSavePaths()) {
        if (!pathWriter.savePathHeader(
            1,
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

  /**
   * Initializes the time parameters.
   *
   * @param assignmentStartTime Start time of the assignment, expressed in minutes after midnight.
   * @param assignmentEndTime End time of the assignment, expressed in minutes after midnight.
   * @param timeSliceDuration Duration of a period (time slice), expressed in minutes.
   */
  public void setTimeParameters(
      int assignmentStartTime, int assignmentEndTime, int timeSliceDuration) {
    // Transform minutes into seconds
    this.assignmentStartTime = assignmentStartTime * 60;
    this.assignmentEndTime = assignmentEndTime * 60;
    this.timeSliceDuration = timeSliceDuration * 60;

    nbTimeSlices = (assignmentEndTime - assignmentStartTime) / timeSliceDuration;
  }
}
