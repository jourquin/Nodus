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

package edu.uclouvain.core.nodus.compute.costs;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;

/**
 * Convenient class that holds all the parameters used by a cost parser worker.
 *
 * @author Bart Jourquin
 */
public class CostParserWorkerParameters {

  private CostParser costParser;
  private CostParserWorker[] costWorkers;
  private byte groupIndex;
  private byte groupNum;
  private NodusProject nodusProject;
  private byte scenario;
  private byte odClass;
  private VirtualNetwork virtualnetwork;
  private boolean withFirstDerivative;
  
  /**
   * Sets the needed parameters.
   *
   * @param costWorkers The array of cost workers that is used.
   * @param project The Nodus project.
   * @param scenario Scenario.
   * @param odClass The OD class the costs must be computed for.
   * @param groupIndex The index of this OD group in VirtualNetwork.
   * @param vn The virtual network structure.
   * @param costParser The cost parser
   */
  public CostParserWorkerParameters(
      CostParserWorker[] costWorkers,
      NodusProject project,
      byte scenario,
      byte odClass,
      byte groupIndex,
      VirtualNetwork vn,
      CostParser costParser) {
    this(costWorkers, project, scenario, odClass, groupIndex, vn, costParser, false);
  }

  /**
   * Sets the needed parameters.
   *
   * @param costWorkers The array of cost workers that is used.
   * @param project The Nodus project.
   * @param scenario Scenario.
   * @param odClass The OD class the costs must be computed for.
   * @param groupIndex The index of this OD group in VirtualNetwork.
   * @param vn The virtual network structure.
   * @param costParser The cost parser.
   * @param withFirstDerivative True if the first derivative of the objective function must be
   *     computed (for Frank-Wolfe assignments only).
   */
  public CostParserWorkerParameters(
      CostParserWorker[] costWorkers,
      NodusProject project,
      byte scenario,
      byte odClass,
      byte groupIndex,
      VirtualNetwork vn,
      CostParser costParser,
      
      boolean withFirstDerivative) {
    this.nodusProject = project;
    this.scenario = scenario;
    this.odClass = odClass;
    this.groupIndex = groupIndex;
    this.virtualnetwork = vn;
    this.costWorkers = costWorkers;
    this.costParser = costParser;
    this.withFirstDerivative = withFirstDerivative;
    
    this.groupNum = this.virtualnetwork.getGroups()[groupIndex];
  }

  /**
   * Returns the cost parser.
   *
   * @return The cost parser.
   */
  public CostParser getCostParser() {
    return costParser;
  }

  /**
   * Returns the array of cost parser workers.
   *
   * @return The array of cost parser workers.
   */
  public CostParserWorker[] getCostWorkers() {
    return costWorkers;
  }

  /**
   * Returns the group index.
   *
   * @return The group index.
   */
  public byte getGroupIndex() {
    return groupIndex;
  }

  /**
   * Returns the group number.
   *
   * @return The group for which the costs are computed.
   */
  public byte getGroupNum() {
    return groupNum;
  }

  /**
   * Returns the Nodud project.
   *
   * @return The Nodus project.
   */
  public NodusProject getNodusProject() {
    return nodusProject;
  }

  /**
   * Returns the OD class.
   *
   * @return The OD class for which the costs are computed.
   */
  public byte getODClass() {
    return odClass;
  }

  
  /**
   * Returns the scenario ID.
   *
   * @return The scenario for which the costs are computed.
   */
  public byte getScenario() {
    return scenario;
  }
  
  /**
   * Returns the virtual network structure.
   *
   * @return The virtual network structure.
   */
  public VirtualNetwork getVirtualNetwork() {
    return virtualnetwork;
  }

  /**
   * Returns true if the first derivative of the objective function must be computed. Only for
   * Frank-Wolfe assignments.
   *
   * @return True if the first derivative must be computed.
   */
  public boolean isWithFirstDerivative() {
    return withFirstDerivative;
  }

}
