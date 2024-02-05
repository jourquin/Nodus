/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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
import edu.uclouvain.core.nodus.compute.assign.workers.FrankWolfeAssignmentWorker;
import edu.uclouvain.core.nodus.compute.costs.VehiclesParser;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.compute.rules.NodeRulesReader;
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
 * Frank-Wolfe equilibrium assignment algorithm. See <i> Frank M. and Wolfe P.‚An algorithm for
 * quadratic programming, 1956, Naval Research Logistics Quarterly, Vol 3 , pp. 95-110.</i> and
 * <i>Jourquin B. and Limbourg S., Equilibrium Traffic Assignment on Large Virtual Networks,
 * Implementation issues and limits for Multi-Modal Freight Transport, European Journal of Transport
 * and Infrastructure Research, Vol 6, n°3, pp. 205-228, 2006.</i>
 *
 * <p>Doesn't work (anymore). Is disabled since Nodus 8.0.
 *
 * @author Bart Jourquin
 */
public class FrankWolfeAssignment extends Assignment {

  /**
   * Initializes the assignment procedure.
   *
   * @param ap AssignmentParameters
   */
  public FrankWolfeAssignment(AssignmentParameters ap) {
    super(ap);
  }

  /** Computation thread that does the real assignment work. */
  @Override
  public boolean assign() {

    // Test if cost functions contain deprecated XX_Duration variables
    if (costsContainDeprecatedVariables()) {
      return false;
    }

    // Mention if the cost functions file contain duration functions
    if (hasDurationFunctions()) {
      assignmentParameters.setDurationFunctions(true);
    }

    // Generate a virtual network
    virtualNet = new VirtualNetwork(assignmentParameters);

    if (!virtualNet.generate()) {
      return false;
    }

    int scenario = assignmentParameters.getScenario();

    // Read the exclusions
    NodeRulesReader er = new NodeRulesReader(virtualNet, scenario);

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

    // Initialize the vehicles parser and load the vehicle characteristics for all groups.
    vehiclesParser = new VehiclesParser(assignmentParameters.getScenario());
    for (byte groupIndex = 0; groupIndex < (byte) virtualNet.getGroups().length; groupIndex++) {
      if (!vehiclesParser.loadVehicleCharacteristics(
          assignmentParameters.getCostFunctions(), virtualNet.getGroups()[groupIndex])) {
        return false;
      }
    }

    // long Start=System.currentTimeMillis() ;

    // Variables that are used to split current and auxiliary volumes
    double lambda = 1.0;
    double lambdaPrecisionThreshold = 0.01;

    // Create a path writer
    pathWriter = new PathWriter(assignmentParameters);

    // Display console if needed
    if (assignmentParameters.isLogLostPaths()) {
      displayConsoleIfNeeded();
    }

    // Force Garbage collector?
    NodusMapPanel nodusMapPanel = nodusProject.getNodusMapPanel();
    int gcInterval = nodusMapPanel.getGarbageCollectorInterval();
    GarbageCollectionRunner gcr = new GarbageCollectionRunner(gcInterval);

    // Get the number of threads
    int threads = assignmentParameters.getThreads();

    /*
     * Enter into an iterative process that can be stopped before NbIterations if the stopping rule
     * succeeds
     */
    for (byte iteration = 1; iteration < assignmentParameters.getNbIterations() + 1; iteration++) {
      // -- Assign all od classes
      for (byte odClass = 0; odClass < virtualNet.getNbODClasses(); odClass++) {

        if (!virtualNet.odClassHasDemand(odClass)) {
          continue;
        }

        if (!virtualNet.computeCosts(iteration - 1, scenario, odClass, threads)) {
          nodusMapPanel.stopProgress();
          return false;
        }

        // Create the work queue
        WorkQueue queue = new WorkQueue();

        // Create a set of worker threads
        assignmentWorkers = new AssignmentWorker[threads];
        for (int i = 0; i < assignmentWorkers.length; i++) {
          assignmentWorkers[i] = new FrankWolfeAssignmentWorker(queue);
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

      // Compute optimal Lambda
      if (iteration > 1) {
        // Transform the volumes into vehicles
        if (!virtualNet.volumesToVehicles(vehiclesParser)) {
          return false;
        }

        double li = 0.0;
        double ls = 1.0;
        double m = 0.5;
        double firstDerivativeValue = 0.0;
        double currentLambdaPrecision = ls;

        while (currentLambdaPrecision > lambdaPrecisionThreshold) {
          firstDerivativeValue = virtualNet.objectiveFunctionFirstDerivative(iteration, m, threads);

          // If task was aborted
          if (Double.isNaN(firstDerivativeValue)) {
            return false;
          }

          if (firstDerivativeValue == 0.0) {
            lambda = m;
            break;
          }

          if (firstDerivativeValue < 0.0) {
            li = m;
          }

          if (firstDerivativeValue > 0.0) {
            ls = m;
          }

          m = (li + ls) / 2.0f;
          currentLambdaPrecision = ls - li;
        }

        lambda = m;

        // Now combine the auxiliary volumes with the current volume
        splitVolumes(lambda);

        if (assignmentParameters.isSavePaths()) {
          pathWriter.splitPaths(iteration, lambda);
        }
      } else { // If first iteration

        // Now combine the auxiliary volumes with the current volume
        // In this case, current volume will simply be the AoN result...
        splitVolumes(lambda);

        // Transform the volumes in vehicles
        if (!virtualNet.volumesToVehicles(vehiclesParser)) {
          return false;
        }
      }

      // Test if the stop rule is satisfied
      if (stopRule(iteration, assignmentParameters.getPrecision())) {
        break;
      }
    }

    // Close the path writer
    pathWriter.close();

    gcr.stop();

    // Save the volumes
    VirtualNetworkWriter vnw = new VirtualNetworkWriter(assignmentParameters, virtualNet);
    return vnw.save();
  }

  /**
   * Updates the volumes, combining the current volume and the auxiliarry volume.
   *
   * <p>New current volume = (1-lambda) x current volume + lambda x auxilliary volume
   *
   * @param lambda double
   */
  private void splitVolumes(double lambda) {

    byte[] groups = virtualNet.getGroups();

    // Update current volumes on virtual links
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

          for (byte k = 0; k < (byte) groups.length; k++) {
            vl.combineVolumes(k, lambda);
          }
        }
      }
    }
  }

  /**
   * Returns true if max of allowed iterations is reached, or if the maximum gap in the computed
   * volumes between two successive iterations doesn't vary more than the expected precision.
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
      // Update current volumes on virtual links
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
              numerator += Math.abs(vl.getCurrentVolume(k) - vl.getPreviousVolume(k));
              denominator += vl.getCurrentVolume(k);
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
