/*
 * Copyright (c) 1991-2022 Université catholique de Louvain
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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 * Univariate logarithmic Logit (law of Abraham), which exponent can be defined in the cost
 * functions file.
 *
 * @author Bart Jourquin
 */
public class Abraham extends ModalSplitMethod {

  private static I18n i18n = Environment.getI18n();

  private Properties costFunctions;

  private double exponent;

  /**
   * Default constructor. Calls the super class.
   *
   * @param nodusProject Nodus project to associate to this method.
   */
  public Abraham(NodusProject nodusProject) {
    super(nodusProject);
  }

  /**
   * Reads the Abraham exponent in the cost functions file.
   *
   * @return double The Abraham exponent found in the cost functions, or -10 by default.
   */
  private double getExponent() {

    double value =
        PropUtils.doubleFromProperties(
            costFunctions, NodusC.VARNAME_ABRAHAM + "." + getCurrentGroup(), Double.NaN);

    if (Double.isNaN(value)) {
      // Is there a generic value ?
      value = PropUtils.doubleFromProperties(costFunctions, NodusC.VARNAME_ABRAHAM, Double.NaN);
    }

    // Default = -10
    if (Double.isNaN(value)) {
      value = -10;
    } else {
      if (value >= 0) {
        String s =
            i18n.get(
                Assignment.class,
                "Abraham_exponent_error",
                "The exponent for the Abraham method must be a strict negative value");
        JOptionPane.showMessageDialog(null, s, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      }
    }

    return value;
  }

  @Override
  public String getName() {
    return "Abraham";
  }

  @Override
  public String getPrettyName() {
    return i18n.get(ModalSplitMethod.class, "Abraham", "Abraham (Logarithmic logit)");
  }

  @Override
  public void initializeGroup(int currentGroup) {
    super.initializeGroup(currentGroup);

    costFunctions = getAssignmentParameters().getCostFunctions();
    exponent = getExponent();
  }

  @Override
  public boolean split(ODCell odCell, List<PathsForMode> pathsLists) {

    if (exponent >= 0) {
      return false;
    }

    /*
     * Compute the market marketShare for each mode
     */
    double denominator = 0.0;
    Iterator<PathsForMode> plIt = pathsLists.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();
      denominator += Math.pow(modalPaths.cheapestPathWeights.getCost(), exponent);
    }

    // Compute the market marketShare per mode
    plIt = pathsLists.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();
      modalPaths.marketShare =
          Math.pow(modalPaths.cheapestPathWeights.getCost(), exponent) / denominator;
    }

    // Compute the market marketShare per path for each mode
    plIt = pathsLists.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();

      // Denominator for this mode
      denominator = 0.0;
      Iterator<Path> it = modalPaths.pathList.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        denominator += Math.pow(path.weights.getCost(), exponent);
      }

      // Spread over each path of this mode
      it = modalPaths.pathList.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        path.marketShare =
            Math.pow(path.weights.getCost(), exponent) / denominator * modalPaths.marketShare;
      }
    }
    return true;
  }
}
