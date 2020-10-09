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

package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.compute.assign.workers.AllOrNothingAssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorkerParameters;
import edu.uclouvain.core.nodus.compute.exclusions.ExclusionReader;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetworkWriter;
import edu.uclouvain.core.nodus.utils.GarbageCollectionRunner;
import edu.uclouvain.core.nodus.utils.WorkQueue;

/**
 * The simple All Or Nothing assignment procedure.
 *
 * @author Bart Jourquin
 */
public class AllOrNothingAssignment extends Assignment {

  /**
   * Initializes the assignment procedure.
   *
   * @param ap AssignmentParameters
   */
  public AllOrNothingAssignment(AssignmentParameters ap) {
    super(ap);
  }

  /**
   * Launches the computation threads that does the real assignment work.
   *
   * @return True on success.
   */
  @Override
  public boolean assign() {

    // Test if cost functions contain deprecated XX_Duration variables
    if (costsContainDeprecatedDurations()) {
      return false;
    }

    // Mention if the cost functions file contain duration functions
    if (hasDurationFunctions()) {
      assignmentParameters.setDurationFunctions(true);
    }

    // Test if scenario already exists
    if (!VirtualNetworkWriter.acceptScenario(
        nodusProject, assignmentParameters.getScenario(), assignmentParameters.isConfirmDelete())) {
      return false;
    }

    // long start = System.currentTimeMillis();

    // Generate a virtual network
    virtualNet = new VirtualNetwork(assignmentParameters);

    if (!virtualNet.generate()) {
      return false;
    }

    byte scenario = assignmentParameters.getScenario();

    // Read the exclusions
    ExclusionReader er = new ExclusionReader(virtualNet, scenario);

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

    // Assign per class
    for (byte odClass = 0; odClass < virtualNet.getNbODClasses(); odClass++) {

      if (!virtualNet.odClassHasDemand(odClass)) {
        continue;
      }

      // Must paths be saved
      boolean withPaths = assignmentParameters.isSavePaths();

      // Get the number of threads
      int threads = assignmentParameters.getThreads();

      // Compute costs
      if (!virtualNet.computeCosts(0, scenario, odClass, withPaths, threads)) {
        return false;
      }

      // Create the work queue
      WorkQueue queue = new WorkQueue();

      // Create a set of worker threads
      assignmentWorkers = new AssignmentWorker[threads];
      for (int i = 0; i < assignmentWorkers.length; i++) {
        assignmentWorkers[i] = new AllOrNothingAssignmentWorker(queue);
        assignmentWorkers[i].start();
      }

      // Add the jobs to the queue
      for (byte groupIndex = 0; groupIndex < (byte) virtualNet.getGroups().length; groupIndex++) {
        AssignmentWorkerParameters awp = new AssignmentWorkerParameters(this, groupIndex, odClass);
        queue.addWork(awp);
      }

      // Add special end-of-stream markers to terminate the workers
      for (int i = 0; i < assignmentWorkers.length; i++) {
        queue.addWork(WorkQueue.NO_MORE_WORK);
      }

      // Initialize a progress monitor with the number of OD matrix rows to assign
      int lengthOfTask = 0;
      for (byte groupIndex = 0; groupIndex < (byte) virtualNet.getGroups().length; groupIndex++) {
        for (int nodeIndex = 0; nodeIndex < virtualNet.getVirtualNodeLists().length; nodeIndex++) {
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

    gcr.stop();

    // Close the detailed path writer
    pathWriter.close();

    // long end = System.currentTimeMillis();
    // System.out.println("Duration : " + ((end - start) / 1000));

    // Transform the flows in vehicles
    virtualNet.flowsToVehicles();

    // Now save virtual network
    VirtualNetworkWriter vnw = new VirtualNetworkWriter(assignmentParameters, virtualNet);
    return vnw.save();
  }
}
