/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * The DynamicTimeDependentAssignment assigns a time dependent OD matrix. It is based on an
 * All-Or-Nothing assignment. At the end of each period (time slice), the algorithm compute the
 * location of each route at the end of the period and relocated the associated demand to this
 * location. At the beginning of the following period, the assignment starts the assignment of the
 * demand that starts at that time, plus the "relocated" demands which are not yet at their final
 * destination. After each iteration (period), the costs on the network are recomputed. If the costs
 * are volume/capacity dependent, this allows for dynamic route changes occurring during the trip.
 *
 * @author Bart Jourquin
 */
public class DynamicTimeDependentAssignmentWorker extends AssignmentWorker {

  private BinaryHeapDijkstra shortestPath;
  float assignmentEndTime;
  float assignmentStartTime;
  byte currentTimeSlice;

  LinkedList<DemandToRelocate> demandsToRelocate;

  /**
   * Initializes an Assignment Worker.
   *
   * @param queue The WorkQueue the assignment will run in.
   */
  public DynamicTimeDependentAssignmentWorker(WorkQueue queue) {
    super(queue);
  }

  /**
   * Runs an Dynamic time dependent assignment.
   *
   * @return True on success.
   */
  @Override
  boolean doAssignment() {

    // Initialize the adjacency list for current group
    graph = virtualNet.generateAdjacencyList(groupIndex);
    shortestPath = new BinaryHeapDijkstra(graph, virtualNet);

    // List of OD pairs that will be relocated after the assignment of this time slice
    demandsToRelocate = new LinkedList<>();

    // Scan all the nodes
    for (int nodeIndex = 0; nodeIndex < virtualNet.getVirtualNodeLists().length; nodeIndex++) {

      // Update progress bar
      if (virtualNet.getVirtualNodeLists()[nodeIndex].hasDemandForGroup(
          virtualNet.getGroups()[groupIndex], odClass)) {
        String s = null;
        if (virtualNet.getNbODClasses() > 1) {
          s =
              MessageFormat.format(
                  i18n.get(
                      Assignment.class,
                      "Assignment_for_period_and_class",
                      "Assignment for period {0} and class {1}"),
                  currentTimeSlice,
                  odClass);
        } else {
          s =
              i18n.get(
                  Assignment.class,
                  "Assignment_for_period",
                  "Assignment for time period {0}",
                  currentTimeSlice);
        }

        if (!updateProgress(s)) {
          return false;
        }
      }

      // This worker could also have been stopped by another thread
      if (isCancelled()) {
        return false;
      }

      /*
       * In this assignment procedure, several demand lists can be associated to a same real node.
       * This is due to the relocation of the OD pairs during assignment.
       */

      int nbDemandLists = virtualNet.getVirtualNodeLists()[nodeIndex].getNbDemandLists();

      for (int demandListIndex = 0; demandListIndex < nbDemandLists; demandListIndex++) {

        // Get the demand associated to this node for the current group
        demandList =
            virtualNet.getVirtualNodeLists()[nodeIndex].getDemandForGroup(
                virtualNet.getGroups()[groupIndex], odClass);

        if (demandList != null) {
          // Compute all the shortest paths in the virtual network starting from here
          // (not a loading node)
          int beginNode = virtualNet.getVirtualNodeLists()[nodeIndex].getLoadingVirtualNodeId();
          shortestPath.compute(beginNode, demandList);

          // Build all the relevant detailed paths
          if (!readPaths(demandListIndex, nodeIndex)) {
            return false;
          }
        }
      }
    }

    // Relocate all the demands to move
    Iterator<DemandToRelocate> it = demandsToRelocate.iterator();
    while (it.hasNext()) {
      DemandToRelocate dtm = it.next();
      virtualNet.getVirtualNodeLists()[dtm.getToNodeIndex()].relocateDemand(
          dtm.getDemand(), dtm.getToVirtualNodeId());
      virtualNet.getVirtualNodeLists()[dtm.getFromNodeIndex()].removeDemand(
          dtm.getFromDemandListIndex(), dtm.getDemand());
    }

    return true;
  }

  private boolean readPaths(int demandListIndex, int nodeIndex) {

    int[] pi = shortestPath.getPredecessors();
    int beginNode = virtualNet.getVirtualNodeLists()[nodeIndex].getLoadingVirtualNodeId();

    // Scan the demand list
    Iterator<ODCell> it = demandList.iterator();

    while (it.hasNext()) {
      ODCell demand = it.next();

      // Not yet started ?
      if (demand.getStartingTime() > assignmentStartTime) {
        continue;
      }

      // Already arrived ?
      if (demand.getRelocatedOriginNodeId() == demand.getDestinationNodeId()) {
        continue;
      }

      /*
       * Give an index to this OD pair for the detailed paths if needed
       */
      int currentPathIndex = 0;
      if (pathWriter.isSavePaths()) {
        if (demand.getPathIndex() == -1) {
          demand.setPathIndex(getNewPathIndex());
        }
        currentPathIndex = demand.getPathIndex();
      }

      LinkedList<VirtualLink> orderedLinkList = new LinkedList<>();

      // Build path from end to begin node
      int destinationNodeIndex =
          virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true);
      int endNode =
          virtualNet.getVirtualNodeLists()[destinationNodeIndex].getUnloadingVirtualNodeId();

      int currentNode = endNode;
      boolean isPathFound = true;
      PathWeights pathCosts = new PathWeights();
      int nbTranshipments = 0;
      byte loadingMode = 0;
      byte loadingMeans = 0;
      byte unloadingMode = 0;
      byte unloadingMeans = 0;

      // Get starting time of route in seconds
      int odStartingTime = demand.getRelocatedStartingTime();
      int currentTime = odStartingTime;

      while (currentNode != beginNode) {
        // Predecessor
        int predecessor = pi[currentNode];
        if (predecessor == 0) {
          if (assignmentParameters.isLogLostPaths()) {
            System.out.println(
                "delete from "
                    + assignmentParameters.getODMatrix()
                    + " where "
                    + NodusC.DBF_GROUP
                    + "="
                    + currentGroup
                    + " and "
                    + NodusC.DBF_ORIGIN
                    + "="
                    + virtualNet.getVirtualNodeLists()[nodeIndex].getRealNodeId()
                    + " and "
                    + NodusC.DBF_DESTINATION
                    + "="
                    + virtualNet.getVirtualNodeLists()[destinationNodeIndex].getRealNodeId()
                    + ";");
          }
          isPathFound = false;

          break;
        } else {
          AdjacencyNode an = graph[predecessor];

          while (an.endVirtualNode != currentNode) {
            an = an.nextNode;
          }

          /*
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
      pathIterator = orderedLinkList.iterator();
      while (pathIterator.hasNext()) {

        VirtualLink vl = pathIterator.next();

        // Compute the arrival time at the end of this link

        if (vl.getType() == VirtualLink.TYPE_MOVE) {

          currentTime += (int) (vl.getLength() * 3600) / vl.getSpeed();

          // Stop if current time is out of time slice
          if (currentTime > assignmentEndTime) {

            // Relocate the demand to the new origin in the virtual network structure
            // for the next time slice
            int newNodeIndex =
                virtualNet.getNodeIndexInVirtualNodeList(
                    vl.getBeginVirtualNode().getRealNodeId(false), false);
            if (newNodeIndex != -1) {
              DemandToRelocate dtm =
                  new DemandToRelocate(
                      demandListIndex,
                      nodeIndex,
                      newNodeIndex,
                      vl.getBeginVirtualNode().getId(),
                      demand);
              demandsToRelocate.add(dtm);
            } else {
              System.err.println("This should never happen !");
            }
            break;
          }

          // Update the OD cell : this OD pair has at least reached this node
          demand.setRelocatedOriginNodeId(vl.getEndVirtualNode().getRealNodeId(false));
          demand.setRelocatedStartingTime(currentTime);

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
              nbTranshipments++;
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
        }
        vl.addVolume(groupIndex, currentTimeSlice, demand.getQuantity());
      }

      // The total cost of a path must be strictly positive
      if (isPathFound && pathCosts.getCost() == 0.0) {
        setErrorMessage(
            i18n.get(
                AssignmentWorker.class,
                "Cost_must_be_positive",
                "The total cost for all paths must be strictly positive"));
        return false;
      }

      // Save the header of this detailed path if needed
      if (isPathFound && pathWriter.isSavePaths()) {
        if (!pathWriter.savePathHeader(
            1,
            demand,
            demand.getQuantity(),
            pathCosts,
            loadingMode,
            loadingMeans,
            unloadingMode,
            unloadingMeans,
            nbTranshipments,
            demand.getPathIndex())) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Initializes the time parameters for a give period.
   *
   * @param currentTimeSlice Period concerned by these parameters.
   * @param sliceStartTime Starting time of the period, expressed in minutes after midnight.
   * @param sliceDuration Duration of the period, expressed in minutes.
   */
  public void setTimeParameters(byte currentTimeSlice, int sliceStartTime, int sliceDuration) {
    this.currentTimeSlice = currentTimeSlice;
    this.assignmentStartTime = sliceStartTime * 60;
    this.assignmentEndTime = this.assignmentStartTime + sliceDuration * 60;
  }

  private class DemandToRelocate {
    private ODCell demand;
    private int fromDemandListIndex;
    private int fromNodeIndex;
    private int toNodeIndex;
    private int toVirtualNodeId;

    DemandToRelocate(
        int fromDemandListIndex,
        int fromNodeIndex,
        int toNodeIndex,
        int toVirtualNodeId,
        ODCell demand) {
      this.fromDemandListIndex = fromDemandListIndex;
      this.fromNodeIndex = fromNodeIndex;
      this.toNodeIndex = toNodeIndex;
      this.demand = demand;
      this.toVirtualNodeId = toVirtualNodeId;
    }

    public ODCell getDemand() {
      return demand;
    }

    public int getFromDemandListIndex() {
      return fromDemandListIndex;
    }

    public int getFromNodeIndex() {
      return fromNodeIndex;
    }

    public int getToNodeIndex() {
      return toNodeIndex;
    }

    public int getToVirtualNodeId() {
      return toVirtualNodeId;
    }
  }
}
