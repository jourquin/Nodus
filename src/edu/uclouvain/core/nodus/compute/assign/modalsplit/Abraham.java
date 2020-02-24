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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.HashMap;
import java.util.Iterator;
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
  public void initialize(
      int currentGroup, NodusProject nodusProject, AssignmentParameters assignmentParameters) {
    super.initialize(currentGroup, nodusProject, assignmentParameters);

    costFunctions = assignmentParameters.getCostFunctions();
    exponent = getExponent();
  }

  @Override
  public boolean split(ODCell odCell, HashMap<Integer, AltPathsList> hm) {

    if (exponent >= 0) {
      return false;
    }

    /*
     * Compute the market share for each mode
     */
    double denominator = 0.0;
    Iterator<AltPathsList> hmIt = hm.values().iterator();
    while (hmIt.hasNext()) {
      AltPathsList altPathsList = hmIt.next();
      denominator += Math.pow(altPathsList.cheapestPathTotalCost, exponent);
    }

    // Compute the market share per mode
    hmIt = hm.values().iterator();
    while (hmIt.hasNext()) {
      AltPathsList altPathsList = hmIt.next();
      altPathsList.marketShare =
          Math.pow(altPathsList.cheapestPathTotalCost, exponent) / denominator;
    }

    // Compute the market share per path for each mode
    hmIt = hm.values().iterator();
    while (hmIt.hasNext()) {
      AltPathsList altPathsList = hmIt.next();

      // Denominator for this mode
      denominator = 0.0;
      Iterator<Path> it = altPathsList.alternativePaths.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        denominator += Math.pow(path.weight, exponent);
      }

      // Spread over each path of this mode
      it = altPathsList.alternativePaths.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        path.weight = Math.pow(path.weight, exponent) / denominator * altPathsList.marketShare;
      }
    }
    return true;
  }
}
