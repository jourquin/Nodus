/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.compute.costs;

import com.bbn.openmap.util.PropUtils;

import edu.uclouvain.core.nodus.NodusC;

import java.util.HashMap;
import java.util.Properties;

/**
 * Parser used to converts quantities into a number of vehicles.
 *
 * @author Bart Jourquin
 */
public class VehiclesParser {

  /** Average load per mode/means vehicle combination. */
  private static HashMap<String, Double> averageLoad = new HashMap<>();

  /** Equivalent standard vehicle ration for each mode/means combinations. */
  private static HashMap<String, Double> equivalentStandardVehicleRatio = new HashMap<>();

  /**
   * Returns the number of standard vehicles for a transportation means of a given mode-means
   * combination. This information must previously be loaded by loadVehicleCharacteristics(int
   * scenario, int group).
   *
   * @param mode The transportation mode.
   * @param means The transportation means.
   * @return The equivalent standard vehicle ratio.
   */
  public static double getEquivalentStandardVehicleRatio(int mode, int means) {
    String key = mode + "-" + means;
    Double value = equivalentStandardVehicleRatio.get(key);
    if (value == null) {
      return 1.0;
    } else {
      return value.doubleValue();
    }
  }

  /**
   * Returns the capacity of a vehicle of a given mode-means combination.
   *
   * @param mode The transportation mode.
   * @param means The transportation means.
   * @return double The average load for the vehicle.
   */
  public static double getVehicleAverageLoad(int mode, int means) {
    String key = mode + "-" + means;
    Double value = averageLoad.get(key);
    if (value == null) {
      return 1.0;
    } else {
      return value.doubleValue();
    }
  }

  /**
   * Loads the capacities and equivalent standard vehicles for all the vehicles for a given group of
   * commodities. If a capacity for a mode-means combination is not defined, it is supposed to be
   * equal to 1. The same is true for the equivalent standard vehicles.
   *
   * @param group The group of commodities for which this information must be loaded from the cost
   *     functions file.
   */
  public static void loadVehicleCharacteristics(
      Properties costFunctions, int scenario, byte group) {
    averageLoad.clear();
    equivalentStandardVehicleRatio.clear();
    String key;

    // Load capacities
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int means = 0; means < NodusC.MAXMM; means++) {
        // averageLoad[mode][means] = 1;

        String core = NodusC.VARNAME_AVERAGELOAD + "." + mode + "," + means;

        // Is there a specific value for this scenario and group?
        double value =
            PropUtils.doubleFromProperties(costFunctions, scenario + "." + core + "." + group, -1);

        if (value != -1) {
          key = mode + "-" + means;
          averageLoad.put(key, Double.valueOf(value));
          continue;
        }

        // Is there a specific value for this scenario?
        value = PropUtils.doubleFromProperties(costFunctions, scenario + "." + core, -1);

        if (value != -1) {
          key = mode + "-" + means;
          averageLoad.put(key, Double.valueOf(value));

          continue;
        }

        // Is there a specific value for this group?
        value = PropUtils.doubleFromProperties(costFunctions, core + "." + group, -1);

        if (value != -1) {
          key = mode + "-" + means;
          averageLoad.put(key, Double.valueOf(value));

          continue;
        }

        // Is there a generic value ?
        value = PropUtils.doubleFromProperties(costFunctions, core, -1);
        if (value != -1) {
          key = mode + "-" + means;
          averageLoad.put(key, Double.valueOf(value));
        }
      }
    }

    // Load equivalent standard vehicles ratios
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int means = 0; means < NodusC.MAXMM; means++) {
        // equivalentStandardVehicleRatio[mode][means] = 1;

        String core = NodusC.VARNAME_ESV + "." + mode + "," + means;

        // Is there a specific value for this scenario and group?
        double value =
            PropUtils.doubleFromProperties(costFunctions, scenario + "." + core + "." + group, -1);

        if (value != -1) {
          key = mode + "-" + means;
          equivalentStandardVehicleRatio.put(key, Double.valueOf(value));

          continue;
        }

        // Is there a specific value for this scenario?
        value = PropUtils.doubleFromProperties(costFunctions, scenario + "." + core, -1);

        if (value != -1) {
          key = mode + "-" + means;
          equivalentStandardVehicleRatio.put(key, Double.valueOf(value));

          continue;
        }

        // Is there a specific value for this group?
        value = PropUtils.doubleFromProperties(costFunctions, core + "." + group, -1);

        if (value != -1) {
          key = mode + "-" + means;
          equivalentStandardVehicleRatio.put(key, Double.valueOf(value));

          continue;
        }

        // Is there a generic value ?
        value = PropUtils.doubleFromProperties(costFunctions, core, -1);

        if (value != -1) {
          key = mode + "-" + means;
          equivalentStandardVehicleRatio.put(key, Double.valueOf(value));
        }
      }
    }
  }
}
