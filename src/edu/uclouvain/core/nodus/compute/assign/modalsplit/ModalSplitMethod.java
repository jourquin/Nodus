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

package edu.uclouvain.core.nodus.compute.assign.modalsplit;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.List;

/**
 * Modal split method skeleton.
 *
 * @author Bart Jourquin
 */
public abstract class ModalSplitMethod implements Cloneable {

  private AssignmentParameters assignmentParameters;

  private int group;

  private NodusProject nodusProject;

  /**
   * Default constructor. Associates a Nodus project to the modal split method. It is called when a
   * project is loaded, not at assignment time. In a user defined modal split method, this
   * constructor can be used to initialize some stuff that will not be modified before assignment
   * time. Generally, it is better to override the initialize or setGroup method.
   *
   * @param nodusProject The Nodus project to associate to the method.
   */
  public ModalSplitMethod(NodusProject nodusProject) {
    this.nodusProject = nodusProject;
  }

  /**
   * Returns a copy of this object. Needed a the ModalSpiltMethod is initialized at the assignment
   * level, but also at the assignment worker (thread) level. An instance of the ModalSplitMethod
   * must exist in each worker.
   *
   * @return a clone of the ModalSplitMethod.
   */
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  /**
   * Returns the assignment parameters.
   *
   * @return AssignmentParameters
   */
  public AssignmentParameters getAssignmentParameters() {
    return assignmentParameters;
  }

  /**
   * Returns the group of commodities associated to this modal split method.
   *
   * @return The ID of the group of commodities.
   */
  public int getCurrentGroup() {
    return group;
  }

  /**
   * Returns the short name of the modal split method.
   *
   * @return The short name of the modal split method.
   */
  public abstract String getName();

  /**
   * Returns the Nodus project this assignment is related to.
   *
   * @return NodusProject
   */
  public NodusProject getNodusProject() {
    return nodusProject;
  }

  /**
   * Returns the pretty name of the modal split method, as displayed in the Assignment Dialog Box.
   *
   * @return The pretty name of the modal split method.
   */
  public abstract String getPrettyName();

  /**
   * Initializes the method at the group level. This is called by the doAssignment() method of the
   * multiflow assignment workers. Therefore, if this method is overridden by a user defined modal
   * split method, the code must be thread safe.
   *
   * @param group Group ID for the commodities
   */
  public void initializeGroup(int group) {
    this.group = group;
  }

  /**
   * Initializes the method with the right parameters at the assignment level . This is called by
   * the assign() method of the multiflow assignment.
   *
   * @param assignmentParameters Assignment parameters
   */
  public void initialize(AssignmentParameters assignmentParameters) {
    this.assignmentParameters = assignmentParameters;
  }

  /**
   * Runs the modal split method algorithm. This is called by the the multiflow assignment workers.
   * Therefore, this method must be thread safe.
   *
   * @param odCell The OD cell for which the modal split has to be performed.
   * @param pathsLists A list that contains the lists of routes for each mode.
   * @return True on success.
   */
  public abstract boolean split(ODCell odCell, List<PathsForMode> pathsLists);
}
