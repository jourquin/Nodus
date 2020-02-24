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

package edu.uclouvain.core.nodus.compute.costs;

import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.NodusC;
import java.util.Properties;
import java.util.Set;

/**
 * Reads the optional loading and unloading durations for the different types of vehicles from the
 * cost functions file. The durations must be expressed in hours, but are converted in seconds. If
 * set, the loading and unloading durations are added to the travel duration (movement) in the
 * detailed path header table.
 *
 * @author Bart Jourquin
 */
public class TransitTimesParser {

  private Properties costFunctions;

  private int group;
  private int scenario;

  private boolean withLoadingDuration = false;
  private boolean withUnloadingDuration = false;
  private boolean withTranshipmentDuration = false;

  /**
   * Loads the different transit times (loading, unloading, transhipment) for all the vehicles for a
   * given group. If a transit time for a mode-means combination is not defined, it is set to 0.
   *
   * @param costFunctions The properties that contain the cost functions.
   * @param scenario The scenario currently being computed.
   * @param group The group of commodities for which this information must be loaded from the cost
   *     functions file.
   */
  public TransitTimesParser(Properties costFunctions, int scenario, int group) {
    this.costFunctions = costFunctions;
    this.scenario = scenario;
    this.group = group;

    // Scan the cots function to detect the presence of durations
    Set<Object> keys = costFunctions.keySet();
    for (Object key : keys) {
      if (((String) key).contains(NodusC.VARNAME_LOADING_DURATION)) {
        withLoadingDuration = true;
        continue;
      }
      if (((String) key).contains(NodusC.VARNAME_UNLOADING_DURATION)) {
        withUnloadingDuration = true;
        continue;
      }
      if (((String) key).contains(NodusC.VARNAME_TRANSHIP_DURATION)) {
        withTranshipmentDuration = true;
        continue;
      }
    }
  }

  /**
   * Returns the loading duration (in seconds) for a given vehicle.
   *
   * @param mode The transportation mode.
   * @param means The transportation means (type of vehicle).
   * @return The loading duration in seconds.
   */
  public double getLoadingDuration(int mode, int means) {
    if (!withLoadingDuration) {
      return 0;
    }

    String coreKey = NodusC.VARNAME_LOADING_DURATION + "." + mode + "," + means;
    return parseDuration(coreKey) * 3600;
  }

  /**
   * Returns the unloading duration (in seconds) for a given vehicle.
   *
   * @param mode The transportation mode.
   * @param means The transportation means (type of vehicle).
   * @return The unloading duration in seconds.
   */
  public double getUnloadingDuration(int mode, int means) {
    if (!withUnloadingDuration) {
      return 0;
    }

    String coreKey = NodusC.VARNAME_UNLOADING_DURATION + "." + mode + "," + means;
    return parseDuration(coreKey) * 3600;
  }

  /**
   * Returns the transhipment duration (in seconds) for a given vehicle combination.
   *
   * @param mode1 The origin transportation mode.
   * @param means1 The origin transportation means (type of vehicle).
   * @param mode2 The destination transportation mode.
   * @param means2 The destination transportation means (type of vehicle).
   * @return The transhipment duration in seconds.
   */
  public double getTranshipmentDuration(int mode1, int means1, int mode2, int means2) {
    if (!withTranshipmentDuration) {
      return 0;
    }

    String coreKey =
        NodusC.VARNAME_TRANSHIP_DURATION + "." + mode1 + "," + means1 + "-" + mode2 + "," + means2;
    return parseDuration(coreKey) * 3600;
  }

  /**
   * Fetch the relevant duration value from the cost functions.
   *
   * @param key The core key to look for (varname + mode/means combination)
   * @return The duration value or 0 if not found
   */
  private double parseDuration(String key) {

    // Is there a specific value for this scenario and group?
    double value =
        PropUtils.doubleFromProperties(
            costFunctions, scenario + "." + key + "." + group, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this scenario?
    value = PropUtils.doubleFromProperties(costFunctions, scenario + "." + key, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this group?
    value = PropUtils.doubleFromProperties(costFunctions, key + "." + group, Double.NaN);

    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a generic value ?
    value = PropUtils.doubleFromProperties(costFunctions, key, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
    }

    return 0;
  }
}
