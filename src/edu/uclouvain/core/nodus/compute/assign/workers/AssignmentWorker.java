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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.util.LinkedList;

/**
 * An AssignmentWorker is an assignment that runs it its own thread. For each assignment class
 * (AllOrNothingAssignment for instance), an Assignment worker is defined
 * (AllOrNothingAssignmentWorker for instance). Depending on the number of threads defined by the
 * user, the Assignment will create and launch one or several Workers.
 *
 * @author Bart Jourquin
 */
public abstract class AssignmentWorker extends Thread {

  private static int currentPathIndex = 0;

  /** I18N mechanism. */
  static I18n i18n = Environment.getI18n();

  /** Reset the index of the (detailed) path to 0. Used by multi-flow assignments */
  public static void resetPathIndex() {
    currentPathIndex = 0;
  }

  AssignmentParameters assignmentParameters;

  /** Set to true if thread is canceled on error or by the user. */
  boolean canceled = false;

  /** The num of the current group. */
  int currentGroup;

  /** List of the demands to be assigned. */
  LinkedList<ODCell> demandList;

  /** The graph on which the shortest paths are computed. */
  AdjacencyNode[] graph;

  /** The index of the group to assign. */
  byte groupIndex;

  /** The iteration in equilibrium assignments. */
  int iteration;

  /** The percentage of the OD cell to assign. */
  double loadFactor;

  /** Used to update the progress bar. */
  private NodusMapPanel nodusMapPanel;

  /** Made available to the assignment algorithms. */
  NodusProject nodusProject;

  /** The odClass to assign. */
  byte odClass;

  /** Used the save the detailed paths if asked. */
  PathWriter pathWriter;

  WorkQueue workQueue;

  /** Virtual network that will be generated before the assignment. */
  VirtualNetwork virtualNet;

  Assignment assignment;

  /**
   * Initializes an Assignment Worker.
   *
   * @param queue The WorkQueue the assignment will run in.
   */
  public AssignmentWorker(WorkQueue queue) {
    this.workQueue = queue;
  }

  /** Give the signal to this work to stop. */
  private void cancel() {
    canceled = true;
  }

  /**
   * Sets an error message that will be displayed once the assignment canceled.
   * @param msg The message to display.
   */
  public void setErrorMessage(String msg) {
    assignment.setErrorMessage(msg);
  }

  /**
   * Main work must be done here. Must be overridden in a sub-class.
   *
   * @return True on success
   */
  abstract boolean doAssignment();

  /**
   * Returns a new path index. Used by multi-flow assignments. Is thread save.
   *
   * @return New path index.
   */
  public synchronized int getNewPathIndex() {
    return currentPathIndex++;
  }

  /**
   * Returns true if this thread was canceled.
   *
   * @return True if canceled
   */
  public boolean isCancelled() {
    return canceled;
  }

  /**
   * Main entry point of the worker. Fetches the assignment parameters and runs it. It also cancels
   * all the assignment workers of the assignment if the Esc key was pressed on the MapBean.
   */
  @Override
  public void run() {
    try {
      while (true) {
        // Retrieve some work; block if the queue is empty
        Object x = workQueue.getWork();

        // Terminate if the end-of-stream marker was retrieved
        if (x == WorkQueue.NO_MORE_WORK) {
          break;
        }
        AssignmentWorkerParameters awp = (AssignmentWorkerParameters) x;

        assignment = awp.getAssignment();

        virtualNet = assignment.getVNet();

        nodusProject = assignment.getNodusProjectl();
        nodusMapPanel = nodusProject.getNodusMapPanel();
        pathWriter = assignment.getPathWriter();
        assignmentParameters = assignment.getAssignmentParameters();

        iteration = awp.getIteration();
        loadFactor = awp.getLoadFactor();
        groupIndex = awp.getGroupIndex();
        odClass = awp.getODClass();

        currentGroup = virtualNet.getGroups()[groupIndex];

        // Start the real work
        if (!doAssignment()) {
          // Cancel all workers
          for (AssignmentWorker element : assignment.getAssignmentWorkers()) {
            element.cancel();
          }
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Updates the progress bar and tests if the Esc key was pressed.
   *
   * @param message Message to display near the progress bar.
   * @return False if the assignment was canceled.
   */
  boolean updateProgress(String message) {
    if (!nodusMapPanel.updateProgress(message)) {
      cancel();
      return false;
    }
    return true;
  }
}
