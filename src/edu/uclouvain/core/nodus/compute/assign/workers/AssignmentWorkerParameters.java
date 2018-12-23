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

package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.compute.assign.Assignment;

/**
 * The worker (thread) specific assignment parameters.
 *
 * @author Bart Jourquin
 */
public class AssignmentWorkerParameters {

  private Assignment assignment;
  private byte groupIndex;
  private byte iteration = 0;
  private double loadFactor;
  private byte odClass;

  /**
   * Initializes the AssignmentWorker specific parameters.
   *
   * @param assignment The Assignment object these parameters belong to.
   * @param groupIndex The index of the group that will be assigned by this worker.
   * @param odClass The OD class thatwill be assigned by this worker.
   */
  public AssignmentWorkerParameters(Assignment assignment, byte groupIndex, byte odClass) {
    this(assignment, groupIndex, odClass, (byte) 0, 0.0);
  }

  /**
   * Initializes the AssignmentWorker specific parameters.
   *
   * @param assignment The Assignment object these parameters belong to.
   * @param groupIndex The index of the group that will be assigned by this worker.
   * @param odClass The OD class that will be assigned by this worker.
   * @param iteration The iteration of the equilibrium assignment algorithm the worker will run.
   */
  public AssignmentWorkerParameters(
      Assignment assignment, byte groupIndex, byte odClass, byte iteration) {
    this(assignment, groupIndex, odClass, iteration, 0.0);
  }

  /**
   * Initializes the AssignmentWorker specific parameters.
   *
   * @param assignment The Assignment object these parameters belong to.
   * @param groupIndex The index of the group that will be assigned by this worker.
   * @param odClass The OD class that will be assigned by this worker.
   * @param iteration The iteration of the equilibrium assignment algorithm the worker will run.
   * @param loadFactor The incremental load factor that is will be applied (Incremental and
   *     IncFrankWolfe algorithms only).
   */
  public AssignmentWorkerParameters(
      Assignment assignment, byte groupIndex, byte odClass, byte iteration, double loadFactor) {
    this.assignment = assignment;
    this.groupIndex = groupIndex;
    this.odClass = odClass;
    this.iteration = iteration;
    this.loadFactor = loadFactor;
  }

  Assignment getAssignment() {
    return assignment;
  }

  byte getGroupIndex() {
    return groupIndex;
  }

  byte getIteration() {
    return iteration;
  }

  double getLoadFactor() {
    return loadFactor;
  }

  byte getODClass() {
    return odClass;
  }
}
