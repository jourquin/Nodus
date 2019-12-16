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

import edu.uclouvain.core.nodus.compute.od.ODCell;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Univariate Logarithmic Logit with exponent set to -1. This method simply allocates a modal share
 * proportionally to the inverse of the cost of each alternative.
 *
 * @author Bart Jourquin
 */
public class Proportional extends ModalSplitMethod {

  private static I18n i18n = Environment.getI18n();

  @Override
  public String getName() {
    return "Proportional";
  }

  @Override
  public String getPrettyName() {
    return i18n.get(ModalSplitMethod.class, "Proportional", "Proportional");
  }

  @Override
  public boolean split(ODCell odCell, HashMap<Integer, AltPathsList> hm) {

    /*
     * Compute the market share for each mode
     */
    double denominator = 0.0;
    Iterator<AltPathsList> hmIt = hm.values().iterator();
    while (hmIt.hasNext()) {
      AltPathsList altPathsList = hmIt.next();
      denominator += Math.pow(altPathsList.cheapestPathTotalCost, -1);
    }

    // Compute the market share per mode
    hmIt = hm.values().iterator();
    while (hmIt.hasNext()) {
      AltPathsList altPathsList = hmIt.next();
      altPathsList.marketShare = Math.pow(altPathsList.cheapestPathTotalCost, -1) / denominator;
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
        denominator += Math.pow(path.weight, -1);
      }

      // Spread over each path of this mode
      it = altPathsList.alternativePaths.iterator();
      while (it.hasNext()) {
        Path path = it.next();
        path.weight = Math.pow(path.weight, -1) / denominator * altPathsList.marketShare;
      }
    }
    return true;
  }
}
