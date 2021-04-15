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

package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Simple holder for the different assignment parameters selected in the Assignment dialog box.
 *
 * @author Bart Jourquin
 */
public class AssignmentParameters {

  /* If true, ask before deleting old assignments. */
  // private boolean confirmDelete = true;

  /** Cost functions file. */
  private Properties costFunctions;

  /** Cost markup to be used to compute alternative paths. */
  private double costMarkup;

  /** If true, the details of each path are saved in a specific table. */
  private boolean detailedPaths;

  /** For dynamic assignment procedures. */
  private boolean isTimeDependent = false;

  /**
   * Set to true if all the intermodal paths have to be kept in the MF methods, and not only if they
   * are cheaper than any monomodal path.
   */
  private boolean keepOnlyCheapestIntermodalPath = true;

  /** Set to true if the assignment should be limited by the highlighted area on the map. */
  private boolean limitedToHighlightedArea;

  /** Set to true to send the list of "lost" paths to stdout. */
  private boolean logLostPaths;

  /**
   * If > 0, only the alternative paths that are shorter than this ration x the length of the
   * cheapest alternative are kept during a multi-flow assignment.
   */
  private double maxDetour = 0;

  /** Modal split method name. */
  private String modalSplitMethodName;

  /** Maximum number of iterations to perform before the assignment procedure. stops */
  private int nbIterations = 1;

  /** Nodus project. */
  private NodusProject nodusProject;

  /** OD matrix table. */
  private String odMatrix;

  /** File name of the post-assignment script (relative to project dir). */
  private String postAssignmentScript = null;

  /** Threshold used as a stopping rule for the equilibrium algorithms. */
  private double precision;

  /** Set to true if the post-assignent script must be executed. */
  private boolean runPostAssignmentScript;

  /** Set to true of the detailed paths must be saved in the database. */
  private boolean savePaths;

  /** Scenario to assign. */
  private int scenario = -1;

  /** The number of parallel threads during assignment. */
  private int threads = 1;

  /** SQL statement that can be given to filter the O-D matrix. */
  private String whereStmt;

  /** Set to true of duration functions are defined. */
  private boolean withDurationFunction = false;

  /**
   * Initializes the assignment parameters.
   *
   * @param nodusProject NodusProject
   */
  public AssignmentParameters(NodusProject nodusProject) {
    this.nodusProject = nodusProject;
  }

  /**
   * Returns the cost functions.
   *
   * @return Properties
   */
  public Properties getCostFunctions() {
    return costFunctions;
  }

  /**
   * Returns the cost mark-up used in multi-flow assignments. This value represents a multiplicative
   * factor that will be applied to the costs of the links along a computed path in order to try to
   * avoid the same path being computed again when looking for an alternative route.
   *
   * @return double
   */
  public double getCostMarkup() {
    return costMarkup;
  }

  /**
   * Returns the max detour ratio used in multi-flow assignments. Any path between an origin and a
   * destination that is longer than the shortest path between these two locations, multiplied by
   * the max detour ratio, will not be considered as valid alternative routes.
   *
   * @return the double
   */
  public double getMaxDetour() {
    return maxDetour;
  }

  /**
   * Returns the name of the modal split method (used in multi-flow assignments).
   *
   * @return the String
   */
  public String getModalSplitMethodName() {
    return modalSplitMethodName;
  }

  /**
   * The value returned by this method can have two different meanings: <br>
   * - The (max) number of iterations in equilibrium assignments.<br>
   * - The number of alternative routes computed for each mode in multi-flow assignments. <br>
   *
   * @return Returns the nbIterations.
   */
  public int getNbIterations() {
    return nbIterations;
  }

  /**
   * Returns the project the assignment is launched from. This gives access to the whole Nodus API.
   *
   * @return The NodusProject
   */
  public NodusProject getNodusProject() {
    return nodusProject;
  }

  /**
   * Returns the name of the database table that contains the OD matrix.
   *
   * @return String
   */
  public String getODMatrix() {
    return odMatrix;
  }

  /**
   * Returns the name of the post-assignment script, or null if not defined.
   *
   * @return String
   */
  public String getPostAssignmentScript() {
    return postAssignmentScript;
  }

  /**
   * Returns the threshold that must be reached to stop an equilibrium assignment. This value
   * represents the difference, expressed as a percentage, in the total cost of all the transport
   * tasks embedded in the OD matrix computed between two successive iterations.
   *
   * @return Returns the precision.
   */
  public double getPrecision() {
    return precision;
  }

  /**
   * Returns the num of the assigned scenario.
   *
   * @return int.
   */
  public int getScenario() {
    return scenario;
  }

  /**
   * Returns the description of the scenario, if any.
   *
   * @return The description string
   */
  public String getScenarioDescription() {
    return nodusProject.getLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + scenario, "");
  }

  /**
   * Returns the number of workers (threads) that will be launched for the assignment.
   *
   * @return int
   */
  public int getThreads() {
    return threads;
  }

  /**
   * Returns the SQL WHERE statement used to filter the OD matrix.
   *
   * @return String.
   */
  public String getWhereStmt() {
    return whereStmt;
  }

  /**
   * Returns true if the cost functions contain duration functions.
   *
   * @return True if duration functions are present.
   */
  public boolean hasDurationFunctions() {
    return withDurationFunction;
  }

  /*
   * Returns true if the user must confirm before an existing scenario is overwritten.
   *
   * @return boolean
   */
  /*public boolean isConfirmDelete() {
    return confirmDelete;
  }*/

  /**
   * Returns true if the details of each path (the list of used network links) must be saved.
   *
   * @return boolean.
   */
  public boolean isDetailedPaths() {
    return detailedPaths;
  }

  /**
   * Returns true if intermodal routes must be kept only if they are cheaper than the unimodal
   * alternatives.
   *
   * @return boolean.
   */
  public boolean isKeepOnlyCheapestIntermodalPath() {
    return keepOnlyCheapestIntermodalPath;
  }

  /**
   * Returns true if the assignment must be limited to the highlighted area.
   *
   * @return boolean.
   */
  public boolean isLimitedToHighlightedArea() {
    return limitedToHighlightedArea;
  }

  /**
   * Returns true if the lost paths (no route found between the origin and the destination) must be
   * logged.
   *
   * @return boolean.
   */
  public boolean isLogLostPaths() {
    return logLostPaths;
  }

  /**
   * Returns true if a post assignment script must be run.
   *
   * @return boolean.
   */
  public boolean isRunPostAssignmentScript() {
    return runPostAssignmentScript;
  }

  /**
   * Returns true if the paths must be saved along with the virtual network table.
   *
   * @return boolean.
   */
  public boolean isSavePaths() {
    return savePaths;
  }

  /**
   * Returns true for time dependent assignments.
   *
   * @return boolean.
   */
  public boolean isTimeDependent() {
    return isTimeDependent;
  }

  /*
   * If true, the user will be asked to confirm before overwritting an exiting assignment.
   *
   * @param confirmDelete boolean.
   */
  /*public void setConfirmDelete(boolean confirmDelete) {
    this.confirmDelete = confirmDelete;
  }*/

  /**
   * Sets the cost functions to use with the assignment. The cost functions can be: <br>
   * - A Properties object in which the cost functions are already stored. - The name of the cost
   * functions file.
   *
   * @param costFunctions Object
   */
  public void setCostFunctions(Object costFunctions) {

    // Be sure the costs functions are stored as properties
    if (!(costFunctions instanceof Properties)) {

      String costFunctionsFileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + (String) costFunctions;
      this.costFunctions = new Properties();
      try {
        this.costFunctions.load(new FileInputStream(costFunctionsFileName.trim()));
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    } else {
      this.costFunctions = (Properties) costFunctions;
    }
  }

  /**
   * Set the cost mark-up to use. This value represents a multiplicative factor that will be applied
   * to the costs of the links along a computed path in order to try to avoid the same path being
   * computed again when looking for an alternative route.
   *
   * @param costMarkup double
   */
  public void setCostMarkup(double costMarkup) {
    this.costMarkup = costMarkup;
  }

  /**
   * If true, the list of network links used by each path will be saved.
   *
   * @param detailedPaths boolean.
   */
  public void setDetailedPaths(boolean detailedPaths) {
    this.detailedPaths = detailedPaths;
  }

  /**
   * Mention if the cost functions file contain duration functions.
   *
   * @param withDurationFunctions True to mention that the cost functions file contain duration
   *     functions.
   */
  public void setDurationFunctions(boolean withDurationFunctions) {
    this.withDurationFunction = withDurationFunctions;
  }
  
  /**
   * If true, all the computed intermodal routes will be used, even if they are more expensive than
   * unimodal alternatives.
   *
   * @param keepOnlyCheapestIntermodalPath boolean.
   */
  public void setKeepOnlyCheapestIntermodalPath(boolean keepOnlyCheapestIntermodalPath) {
    this.keepOnlyCheapestIntermodalPath = keepOnlyCheapestIntermodalPath;
  }

  /**
   * If true, the assignment will be limited to the OD pairs that are located in the highlighted
   * area.
   *
   * @param limitToHighlightedArea boolean.
   */
  public void setLimitedToHighlightedArea(boolean limitToHighlightedArea) {
    this.limitedToHighlightedArea = limitToHighlightedArea;
  }

  /**
   * If true, the lost paths (no route found between an origin an a destination) will be logged.
   *
   * @param logLostPaths boolean.
   */
  public void setLogLostPaths(boolean logLostPaths) {
    this.logLostPaths = logLostPaths;
  }

  /**
   * Specifies the max detour ratio. Any path between an origin and a destination that is longer
   * than the shortest path between these two locations, multiplied by this ratio, will not be
   * considered as valid alternative routes.
   *
   * @param maxDetourRatio boolean.
   */
  public void setMaxDetourRatio(double maxDetourRatio) {
    this.maxDetour = maxDetourRatio;
  }

  /**
   * Sets the name of the modal split method to use.
   *
   * @param modalSplitMethodName String
   */
  public void setModalSplitMethodName(String modalSplitMethodName) {
    this.modalSplitMethodName = modalSplitMethodName;
  }

  /**
   * Sets the number of iterations. Thi value can have two different meanings: <br>
   * - The (max) number of iterations in equilibrium assignments.<br>
   * - The number of alternative routes computed for each mode in multi-flow assignments. <br>
   *
   * @param nbIterations int
   */
  public void setNbIterations(int nbIterations) {
    this.nbIterations = nbIterations;
  }

  /**
   * Sets the name of the database table that contains the OD matrix.
   *
   * @param odMatrix String.
   */
  public void setODMatrix(String odMatrix) {
    this.odMatrix = odMatrix;
  }

  /**
   * Sets the name of the script that will be run one the assignment successfully completed.
   *
   * @param postAssignmentScript String
   */
  public void setPostAssignmentScript(String postAssignmentScript) {

    postAssignmentScript = postAssignmentScript.trim();
    if (postAssignmentScript.equals("")) {
      postAssignmentScript = null;
    }
    this.postAssignmentScript = postAssignmentScript;
  }

  /**
   * Sets the precision threshold that must be reached to stop an equilibrium assignment. This value
   * represents the difference, expressed as a percentage, in the total cost of all the transport
   * tasks embedded in the OD matrix computed between two successive iterations.
   *
   * @param precision double.
   */
  public void setPrecision(double precision) {
    this.precision = precision;
  }

  /**
   * If true, a post assignment script will be run after the assignment.
   *
   * @param runPostAssignmentScript boolean.
   */
  public void setRunPostAssignmentScript(boolean runPostAssignmentScript) {
    this.runPostAssignmentScript = runPostAssignmentScript;
  }

  /**
   * If true, the (detailed or not) paths will be saved along with the Virtual Network.
   *
   * @param savePaths boolean.
   */
  public void setSavePaths(boolean savePaths) {
    this.savePaths = savePaths;
  }

  /**
   * Sets the num of the scenario corresponding to this assignment.
   *
   * @param scenario int
   */
  public void setScenario(int scenario) {
    this.scenario = scenario;
  }

  /**
   * Associate a description string to this scenario.
   *
   * @param description The description string.
   */
  public void setScenarioDescription(String description) {
    nodusProject.setLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + scenario, description);
  }

  /**
   * Sets the number of workers (threads) that will be launched for the assignment.
   *
   * @param threads int
   */
  public void setThreads(int threads) {
    this.threads = threads;
  }

  /**
   * Use true to indicate that the assignment is time dependent.
   *
   * @param isTimeDependent boolean
   */
  public void setTimeDependent(boolean isTimeDependent) {
    this.isTimeDependent = isTimeDependent;
  }

  /**
   * Sets the SQL WHERE statement used to filter the OD matrix.
   *
   * @param whereStmt String
   */
  public void setWhereStmt(String whereStmt) {
    this.whereStmt = whereStmt;
  }

  /**
   * Returns a description of values of the assignment parameters.
   *
   * @return String
   */
  @Override
  public String toString() {
    String s = "\nAssignment paremeters :\n";
    s += "- Scenario : " + scenario + "\n";
    s += "- OD table : " + odMatrix + "\n";
    s += "- Cost functions : " + costFunctions + "\n";
    s += "- Alterantive paths : " + nbIterations + "\n";
    s += "- Max detour : " + maxDetour + "\n";
    s += "- Cost mark-up : " + costMarkup + "\n";
    s += "- SQL filter : " + whereStmt + "\n";
    s += "- Highlighted area : " + limitedToHighlightedArea + "\n";
    s += "- Save path : " + savePaths + "\n";
    s += "\n";

    return s;
  }
}
