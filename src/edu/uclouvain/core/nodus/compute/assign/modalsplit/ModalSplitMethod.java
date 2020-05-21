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

package edu.uclouvain.core.nodus.compute.assign.modalsplit;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.HashMap;

public abstract class ModalSplitMethod {

  private AssignmentParameters assignmentParameters;

  private int group;

  private boolean enabled = true;

  private NodusProject nodusProject;

  /**
   * Default constructor.
   *
   * @exclude
   */
  public ModalSplitMethod() {

  }

  /**
   * Returns the assignment parameters.
   *
   * @return AssignmentParameters
   */
  public AssignmentParameters getAssignmentParameters() {
    return assignmentParameters;
  }

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
   * Initializes the method with the right parameters.
   *
   * @param group Group ID for the commodities
   * @param nodusProject Nodus project
   * @param assignmentParameters Assignment parameters
   */
  public void initialize(
      int group, NodusProject nodusProject, AssignmentParameters assignmentParameters) {
    this.group = group;
    this.assignmentParameters = assignmentParameters;
    this.nodusProject = nodusProject;
  }

  /**
   * If false, the method will not be displayed in the list of possible methods to use.
   *
   * @return boolean
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Controls if the modal split method will be included in the list of possible methods.
   *
   * @param enabled True or false
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Runs the modal split method algorithm.
   *
   * @param odCell The OD cell for which the modal split has to be performed.
   * @param hm The HashMap that contains the routes over which the flow must be spread.
   * 
   * @return True on success.
   */
  public abstract boolean split(ODCell odCell, HashMap<Integer, ModalPaths> hm);
}
