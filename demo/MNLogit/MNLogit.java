/*******************************************************************************
 * Copyright (c) 1991-2018 Université catholique de Louvain, 
 * Center for Operations Research and Econometrics (CORE)
 * http://www.uclouvain.be
 * 
 * This file is part of Nodus.
 * 
 * Nodus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.AltPathsList;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.assign.workers.PathDetailedCosts;
import edu.uclouvain.core.nodus.compute.od.ODCell;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

/**
 * This modal split method uses parameters that are estimated using the R mnLogit package. The
 * parameters must be estimated using date that corresponds to the cheapest computed route for each
 * mode, whatever the means used. 
 * 
 * <p>See the MNLogit.R script for the estimation of the parameters.
 *
 * <p>Once the modal split computed, the quantity assigned to each mode is spread over the available
 * means proportionally to their relative costs.
 *
 * @author Bart Jourquin
 */
public class MNLogit extends ModalSplitMethod {

  // If true, prints the parameters
  boolean debug = false;

  // Estimated parameters names and values
  String[] paramNames = {
    "(Intercept)", "log(cost)", "log(duration)",
  };

  double[][] paramValue;

  Properties costFunctions;

  /** The constructor is a good place to tell if this method will be available or not. */
  public MNLogit() {
    setEnabled(true);
  }

  /**
   * Initializes the method with the right parameters.
   *
   * @param currentGroup Group ID for the commodities
   * @param nodusProject Nodus project
   * @param assignmentParameters Assignment parameters
   */
  public void initialize(
      int currentGroup, NodusProject nodusProject, AssignmentParameters assignmentParameters) {
    super.initialize(currentGroup, nodusProject, assignmentParameters);

    costFunctions = assignmentParameters.getCostFunctions();

    // Retrieve the values of the estimated parameters
    paramValue = new double[NodusC.MAXMM][paramNames.length];
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int j = 0; j < paramNames.length; j++) {
        paramValue[mode][j] = getParameter(paramNames[j], mode);
      }
    }
  }

  @Override
  public String getPrettyName() {
    return "Use R estimators";
  }

  @Override
  public String getName() {
    return "MNLogit";
  }

  /**
   * Compute the utility for the cheapest path of a mode.
   *
   * @param altPathList List of alternative paths
   * @return AltPathsList The updated list
   */
  private AltPathsList computeUtility(AltPathsList altPathList) {
    PathDetailedCosts c = altPathList.cheapestPathDetailedCosts;
    int mode = altPathList.loadingMode;

    // Compute the total cost of the OD relation
    double cost = c.ldCosts + c.tpCosts + c.trCosts + c.ulCosts + c.mvCosts;

    // Get the travel duration, expressed in  hours
    double duration = altPathList.cheapestPathDuration / 3600;

    // The model was estimated with logs
    double logCost = Math.log(cost);
    double logDuration = Math.log(duration);

    // Utility = intercept + log(cost) + log(duration)
    altPathList.utility =
        paramValue[mode][0] + paramValue[mode][1] * logCost + paramValue[mode][2] * logDuration;
    return altPathList;
  }

  /**
   * Runs the modal split method algorithm.
   *
   * @param odCell The OD cell for which the modal split has to be performed.
   * @param hm The HashMap that contains the routes over which the flow must be spread.
   */
  public void split(ODCell odCell, HashMap<Integer, AltPathsList> hm) {

    /*
     * Compute the market share for each mode, based on the estimated utilities
     */
    double denominator = 0.0;
    Iterator<AltPathsList> hmIt = hm.values().iterator();
    while (hmIt.hasNext()) {
      AltPathsList pathList = hmIt.next();
      pathList = computeUtility(pathList);
      denominator += Math.exp(pathList.utility);
    }

    // Compute the market share per mode
    hmIt = hm.values().iterator();
    while (hmIt.hasNext()) {
      AltPathsList pathsList = hmIt.next();
      pathsList.marketShare = Math.exp(pathsList.utility) / denominator;
    }

    // Compute the market share per path for each mode (proportional)
    hmIt = hm.values().iterator();
    while (hmIt.hasNext()) {
      AltPathsList pathList = hmIt.next();

      // Denominator for this mode
      denominator = 0.0;
      Iterator<Path> it = pathList.alternativePaths.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        denominator += Math.pow(path.weight, -1);
      }

      // Spread over each path of this mode
      it = pathList.alternativePaths.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        path.weight = (Math.pow(path.weight, -1) / denominator) * pathList.marketShare;
      }
    }
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

    if (debug) {
      System.out.println(propName + " = " + ret);
    }

    return ret;
  }
}
