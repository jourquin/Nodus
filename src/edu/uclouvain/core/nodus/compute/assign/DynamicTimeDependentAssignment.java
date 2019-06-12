/**
 * Copyright (c) 1991-2019 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorkerParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.DynamicTimeDependentAssignmentWorker;
import edu.uclouvain.core.nodus.compute.exclusions.ExclusionReader;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetworkWriter;
import edu.uclouvain.core.nodus.utils.GarbageCollectionRunner;
import edu.uclouvain.core.nodus.utils.WorkQueue;

import java.util.Properties;

import javax.swing.JOptionPane;

/**
 * The DynamicTimeDependentAssignment assigns a time dependent OD matrix. It is based on an
 * All-Or-Nothing assignment. At the end of each period (time slice), the algorithm compute the
 * location of each route at the end of the period and relocated the associated demand to this
 * location. At the beginning of the following period, the assignment starts the assignment of the
 * demand that starts at that time, plus the "relocated" demands which are not yet at their final
 * destination. After each iteration (period), the costs on the network are recomputed. If the costs
 * are flow/capacity dependent, this allows for dynamic route changes occurring during the trip.
 *
 * @author Bart Jourquin
 */
public class DynamicTimeDependentAssignment extends Assignment {

  /**
   * Initializes the assignment procedure.
   *
   * @param ap AssignmentParameters
   */
  public DynamicTimeDependentAssignment(AssignmentParameters ap) {
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

    // long start = System.currentTimeMillis();

    // Get the information about the time windows in the cost function
    Properties costFunctions = assignmentParameters.getCostFunctions();

    int assignmentStartTime =
        Integer.parseInt(costFunctions.getProperty(NodusC.VARNAME_STARTTIME, "-1"));
    int assignmentEndTime =
        Integer.parseInt(costFunctions.getProperty(NodusC.VARNAME_ENDTIME, "-1"));
    int timeSliceDuration =
        Integer.parseInt(costFunctions.getProperty(NodusC.VARNAME_TIMESLICE, "-1"));

    final int nbTimeSlices = (assignmentEndTime - assignmentStartTime) / timeSliceDuration;

    if (assignmentEndTime == -1 || assignmentStartTime == -1 || timeSliceDuration == -1) {
      JOptionPane.showMessageDialog(
          null,
          i18n.get(
              Assignment.class,
              "Time_related_variables_not_found",
              "Time related variables not found in cost functions"),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
      return false;
    }

    // Generate a virtual network
    virtualNet = new VirtualNetwork(assignmentParameters);
    virtualNet.setAssignmentTimeParameters(
        assignmentStartTime, assignmentEndTime, timeSliceDuration);

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

    // Assign each time slice
    for (byte currentTimeSlice = 0; currentTimeSlice < nbTimeSlices; currentTimeSlice++) {

      int sliceStartTime = assignmentStartTime + currentTimeSlice * timeSliceDuration;

      // Assign per class
      for (byte odClass = 0; odClass < virtualNet.getNbODClasses(); odClass++) {

        if (!virtualNet.odClassHasDemand(odClass)) {
          continue;
        }

        // Get the number of threads
        int threads = assignmentParameters.getThreads();

        // Compute costs
        if (!virtualNet.computeCosts(0, odClass, currentTimeSlice, threads)) {
          return false;
        }

        // Create the work queue
        WorkQueue queue = new WorkQueue();

        // Create a set of worker threads
        assignmentWorkers = new AssignmentWorker[threads];
        for (int i = 0; i < assignmentWorkers.length; i++) {
          DynamicTimeDependentAssignmentWorker aw = new DynamicTimeDependentAssignmentWorker(queue);
          aw.setTimeParameters(currentTimeSlice, sliceStartTime, timeSliceDuration);
          assignmentWorkers[i] = aw;
          assignmentWorkers[i].start();
        }

        // Add the jobs to the queue
        for (byte groupIndex = 0; groupIndex < (byte) virtualNet.getGroups().length; groupIndex++) {
          AssignmentWorkerParameters awp =
              new AssignmentWorkerParameters(this, groupIndex, odClass);
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
            // Get the demand associated to this node, group and class
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
      } // Next odClass
    } // Next time slice

    gcr.stop();

    // long end = System.currentTimeMillis();
    // System.out.println("Duration : " + ((end - start) / 1000));

    // Close the detailed path writer
    pathWriter.close();

    // Transform the flows in vehicles
    for (byte i = 0; i < virtualNet.getNbTimeSlices(); i++) {
      virtualNet.flowsToVehicles(i);
    }

    // Now save virtual network
    VirtualNetworkWriter vnw = new VirtualNetworkWriter(assignmentParameters, virtualNet);
    return vnw.save();
  }
}
