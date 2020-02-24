/**
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

package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorkerParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.MSAAssignmentWorker;
import edu.uclouvain.core.nodus.compute.exclusions.ExclusionReader;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetworkWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.utils.GarbageCollectionRunner;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.util.Iterator;

/**
 * A equilibrium assignment algorithm based on the Method of Successive Averages (MSA). See <i>
 * Jourquin B. and Limbourg S., Equilibrium Traffic Assignment on Large Virtual Networks,
 * Implementation issues and limits for Multi-Modal Freight Transport, European Journal of Transport
 * and Infrastructure Research, Vol 6, n°3, pp. 205-228, 2006. </i>
 *
 * @author Bart Jourquin
 */
public class MSAAssignment extends Assignment {

  /**
   * Initializes the assignment procedure.
   *
   * @param ap AssignmentParameters
   */
  public MSAAssignment(AssignmentParameters ap) {
    super(ap);
  }

  /** Computation thread that does the real assignment work. */
  @Override
  public boolean assign() {
    // Test if scenario already exists
    if (!VirtualNetworkWriter.acceptScenario(
        nodusProject, assignmentParameters.getScenario(), assignmentParameters.isConfirmDelete())) {
      return false;
    }

    // long Start = System.currentTimeMillis();

    // Generate a virtual network
    virtualNet = new VirtualNetwork(assignmentParameters);

    if (!virtualNet.generate()) {
      return false;
    }

    // Read the exclusions
    ExclusionReader er = new ExclusionReader(virtualNet);

    if (er.hasExclusions()) {
      if (!er.loadExclusions()) {
        return false;
      }
    }

    // Read the O-D matrixes
    ODReader odr = new ODReader(assignmentParameters);

    if (!odr.loadDemand(virtualNet)) {
      return false;
    }

    // Create a path writer
    pathWriter = new PathWriter(assignmentParameters);

    // Display console if needed
    if (assignmentParameters.isLogLostPaths()) {
      displayConsoleIfNeeded();
      /*new NodusConsole(nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH));
      System.out.println(i18n.get(Assignment.class, "Lost_paths", "Lost paths:"));
      System.out.println(
          NodusC.DBF_GROUP
              + ", "
              + NodusC.DBF_ORIGIN
              + ", "
              + NodusC.DBF_DESTINATION
              + ", "
              + NodusC.DBF_QUANTITY);*/
    }

    // Force Garbage collector?
    NodusMapPanel nodusMapPanel = nodusProject.getNodusMapPanel();
    int gcInterval = nodusMapPanel.getGarbageCollectorInterval();
    GarbageCollectionRunner gcr = new GarbageCollectionRunner(gcInterval);

    for (byte iteration = 1; iteration < assignmentParameters.getNbIterations() + 1; iteration++) {
      double split = 1.0 / iteration;

      for (byte odClass = 0; odClass < virtualNet.getNbODClasses(); odClass++) {

        if (!virtualNet.odClassHasDemand(odClass)) {
          continue;
        }

        // Get the number of threads
        int threads = assignmentParameters.getThreads();

        // (re)Compute costs
        if (!virtualNet.computeCosts(iteration, odClass, threads)) {
          nodusMapPanel.stopProgress();
          return false;
        }

        // Create the work queue
        WorkQueue queue = new WorkQueue();

        // Create a set of worker threads
        assignmentWorkers = new AssignmentWorker[threads];
        for (int i = 0; i < assignmentWorkers.length; i++) {
          assignmentWorkers[i] = new MSAAssignmentWorker(queue);
          assignmentWorkers[i].start();
        }

        // Add the jobs to the queue
        for (byte groupIndex = 0; groupIndex < (byte) virtualNet.getGroups().length; groupIndex++) {
          AssignmentWorkerParameters awp =
              new AssignmentWorkerParameters(this, groupIndex, odClass, iteration);
          queue.addWork(awp);
        }

        // Add special end-of-stream markers to terminate the workers
        for (int i = 0; i < assignmentWorkers.length; i++) {
          queue.addWork(WorkQueue.NO_MORE_WORK);
        }

        // Initialize a progress monitor with the number of OD matrix rows to assign
        int lengthOfTask = 0;

        for (byte groupIndex = 0; groupIndex < (byte) virtualNet.getGroups().length; groupIndex++) {
          for (int nodeIndex = 0;
              nodeIndex < virtualNet.getVirtualNodeLists().length;
              nodeIndex++) {
            // Get the demand associated to this node and group
            if (virtualNet.getVirtualNodeLists()[nodeIndex].hasDemandForGroup(
                virtualNet.getGroups()[groupIndex], odClass)) {
              lengthOfTask++;
            }
          }
        }

        nodusMapPanel.startProgress(lengthOfTask);

        // Wait until all the works are completed
        for (int i = 0; i < threads; i++) {
          try {
            assignmentWorkers[i].join();
          } catch (InterruptedException ex) {
            ex.printStackTrace();
          }
        }

        nodusMapPanel.stopProgress();

        // Test if everything was OK
        for (int i = 0; i < threads; i++) {
          if (assignmentWorkers[i].isCancelled()) {
            return false;
          }
        }
      } // Next od class

      // Now combine the auxiliary flows with the current flow
      splitFlows(split);

      if (assignmentParameters.isSavePaths()) {
        pathWriter.splitPaths(iteration, split);
      }

      // Test if the stop rule is satisfied
      if (stopRule(iteration, assignmentParameters.getPrecision())) {
        break;
      }
    }

    // Close the path writer
    pathWriter.close();

    gcr.stop();

    // Save the flows
    VirtualNetworkWriter vnw = new VirtualNetworkWriter(assignmentParameters, virtualNet);
    // long end = System.currentTimeMillis();
    // System.out.println("Duration : " + (end - Start) / 1000);

    return vnw.save();
  }

  /**
   * Updates the flows, combining the current flow and the auxiliarry flow.
   *
   * <p>New current flow = (1-lambda) x current flow + lambda x auxilliary flow
   *
   * @param lambda double
   */
  public void splitFlows(double lambda) {
    // Update current flows on virtual links
    VirtualNodeList[] vnl = virtualNet.getVirtualNodeLists();

    for (VirtualNodeList element : vnl) {
      // Iterate through all the virtual nodes generated for this real
      // node
      Iterator<VirtualNode> nodeLit = element.getVirtualNodeList().iterator();

      while (nodeLit.hasNext()) {
        VirtualNode vn = nodeLit.next();

        // Iterate through all the virtual links that start from this
        // virtual node
        Iterator<VirtualLink> linkLit = vn.getVirtualLinkList().iterator();

        while (linkLit.hasNext()) {
          VirtualLink vl = linkLit.next();

          byte[] groups = virtualNet.getGroups();

          for (byte k = 0; k < (byte) groups.length; k++) {
            vl.combineFlows(k, lambda);
          }
        }
      }
    }

    // Transform the flows in vehicles
    virtualNet.flowsToVehicles();
  }

  /**
   * Returns true if max of allowed iterations is reached, or if the maximum gap in the computed
   * flows between two successive iterations doesn't vary more than the expected precision.
   *
   * @param iteration int
   * @param precision double
   * @return boolean
   */
  public boolean stopRule(int iteration, double precision) {
    double currentGap = 0.0;
    double numerator = 0.0;
    double denominator = 0.0;
    double maxGap = 0.0;

    if (iteration > 1) {
      // Update current flows on virtual links
      VirtualNodeList[] vnl = virtualNet.getVirtualNodeLists();

      for (VirtualNodeList element : vnl) {
        // Iterate through all the virtual nodes generated for this real node
        Iterator<VirtualNode> nodeLit = element.getVirtualNodeList().iterator();

        while (nodeLit.hasNext()) {
          VirtualNode vn = nodeLit.next();

          // Iterate through all the virtual links that start from this virtual node
          Iterator<VirtualLink> linkLit = vn.getVirtualLinkList().iterator();

          while (linkLit.hasNext()) {
            VirtualLink vl = linkLit.next();
            byte[] groups = virtualNet.getGroups();

            for (byte k = 0; k < (byte) groups.length; k++) {
              numerator += Math.abs(vl.getCurrentFlow(k) - vl.getPreviousFlow(k));
              denominator += vl.getCurrentFlow(k);
            }
          }

          currentGap = numerator / denominator;

          if (currentGap > maxGap) {
            maxGap = currentGap;
          }
        }
      }

      if (maxGap < precision) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }
}
