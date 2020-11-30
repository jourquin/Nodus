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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.PathsForMode;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * This modal split method uses parameters that are estimated using the R mnLogit package. The
 * parameters must be estimated using data that corresponds to the cheapest computed route for each
 * mode, whatever the means used.
 *
 * <p>See the MNLogit.R script for the estimation of the parameters.
 *
 * <p>Once the modal split computed, the quantity assigned to each mode is spread over the available
 * means proportionally to their relative costs.
 *
 * @author Bart Jourquin
 */
public class MLogit extends ModalSplitMethod {
	
  // Names of the estimated parameters (as found in the output of the R MLogit package.
  private String[] paramNames = {
    "(intercept)", "log(cost)",
  };

  // Estimated values for the parameters
  private double[][] paramValue;

  // The cost functions used in the Nodus project that call this modal split plugin.
  private Properties costFunctions;
  
  /**
   * Default constructor. Calls the super class.
   *
   * @param nodusProject Nodus project to associate to this method.
   */
  public MLogit(NodusProject nodusProject) {
    super(nodusProject);
  }


  /**
   * Initializes the method with the right parameters.
   *
   * @param currentGroup Group ID for the commodities
   * @param assignmentParameters Assignment parameters
   */
  @Override
  public void initializeGroup(int currentGroup) {
    super.initializeGroup(currentGroup);

    // Retrieve the cost functions
    costFunctions = getAssignmentParameters().getCostFunctions();

    // Retrieve the values of the estimated parameters
    paramValue = new double[NodusC.MAXMM][paramNames.length];
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int j = 0; j < paramNames.length; j++) {
        paramValue[mode][j] = getParameter(paramNames[j], mode);
      }
    }
  }

  /**
   * Returns the pretty name of the modal split method, as displayed in the Assignment Dialog Box.
   *
   * @return The pretty name of the modal split method.
   */
  @Override
  public String getPrettyName() {
    return "Use R estimators";
  }

  /**
   * Returns the short name of the modal split method.
   *
   * @return The short name of the modal split method.
   */
  @Override
  public String getName() {
    return "MLogit";
  }

  /**
   * Computes the utility for the cheapest path identified for a mode.
   *
   * @param modalPaths List of alternative routes for a mode.
   * @return PathsForMode The updated PathsForMode with its utility.
   */
  private PathsForMode computeUtility(PathsForMode modalPaths) {
    PathWeights weights = modalPaths.cheapestPathWeights;
    int mode = modalPaths.loadingMode;

    // Compute the total cost of the OD relation
    double cost = weights.getCost();

    // The model was estimated with logs
    double logCost = Math.log(cost);

    // Utility = intercept + log(cost)
    modalPaths.utility = paramValue[mode][0] + paramValue[mode][1] * logCost;

    return modalPaths;
  }

  /**
   * Runs the modal split method algorithm.
   *
   * @param odCell The OD cell for which the modal split has to be performed.
   * @param pathsList A list that contains the lists of routes for each mode.
   * @return True on success.
   */
  public boolean split(ODCell odCell, List<PathsForMode> pathsList) {

    /*
     * Compute the utility of each mode and the denominator of the logit
     */
    double denominator = 0.0;
    Iterator<PathsForMode> plIt = pathsList.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();
      modalPaths = computeUtility(modalPaths);
      denominator += Math.exp(modalPaths.utility);
    }

    // Compute the market share for each mode
    plIt = pathsList.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();
      modalPaths.marketShare = Math.exp(modalPaths.utility) / denominator;
    }

    // Compute the market share for each path of a mode (proportional)
    plIt = pathsList.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();

      // Denominator for this mode
      denominator = 0.0;
      Iterator<Path> it = modalPaths.pathList.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        denominator += Math.pow(path.weights.getCost(), -1);
      }

      // Spread over each path of this mode
      it = modalPaths.pathList.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        path.marketShare =
            (Math.pow(path.weights.getCost(), -1) / denominator) * modalPaths.marketShare;
      }
    }
    return true;
  }

  /**
   * Returns the value of a numeric parameter 'name' for a given mode and the current group or 0 if
   * parameter is not defined.
   *
   * @param name The name of the parameter to fetch
   * @param mode The mode for which the value must be retrieved
   * @return The value of the parameter
   */
  public double getParameter(String name, int mode) {

    double ret = 0.0;

    // Search for a mode and group specific value
    String propName = name + "." + mode + "." + getCurrentGroup();

    String doubleString = costFunctions.getProperty(propName);

    if (doubleString == null) {
      ret = 0.0;
    } else {
      try {
        ret = Double.parseDouble(doubleString.trim());

      } catch (NumberFormatException e) {
        ret = 0.0;
      }
    }
    return ret;
  }
}
