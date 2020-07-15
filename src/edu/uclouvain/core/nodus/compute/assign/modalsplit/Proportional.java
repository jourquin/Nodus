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

package edu.uclouvain.core.nodus.compute.assign.modalsplit;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.Iterator;
import java.util.List;

/**
 * Univariate Logarithmic Logit with exponent set to -1. This method simply allocates a modal
 * marketShare proportionally to the inverse of the cost of each alternative.
 *
 * @author Bart Jourquin
 */
public class Proportional extends ModalSplitMethod {

  private static I18n i18n = Environment.getI18n();

  /**
   * Default constructor. Calls the super class.
   *
   * @param nodusProject Nodus project to associate to this method.
   */
  public Proportional(NodusProject nodusProject) {
    super(nodusProject);
  }

  @Override
  public String getName() {
    return "Proportional";
  }

  @Override
  public String getPrettyName() {
    return i18n.get(ModalSplitMethod.class, "Proportional", "Proportional");
  }

  @Override
  public boolean split(ODCell odCell, List<PathsForMode> pathsLists) {
    /*
     * Compute the market marketShare for each mode
     */
    double denominator = 0.0;
    Iterator<PathsForMode> plIt = pathsLists.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();
      denominator += Math.pow(modalPaths.cheapestPathWeights.getCost(), -1);
    }

    // Compute the market marketShare per mode
    plIt = pathsLists.iterator();
    while (plIt.hasNext()) {
      PathsForMode modalPaths = plIt.next();
      modalPaths.marketShare = Math.pow(modalPaths.cheapestPathWeights.getCost(), -1) / denominator;
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
        denominator += Math.pow(path.weights.getCost(), -1);
      }

      // Spread over each path of this mode
      it = modalPaths.pathList.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        path.marketShare =
            Math.pow(path.weights.getCost(), -1) / denominator * modalPaths.marketShare;
      }
    }
    return true;
  }
}
