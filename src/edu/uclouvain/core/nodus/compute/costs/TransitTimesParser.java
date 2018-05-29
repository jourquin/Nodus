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
 * Reads the optional loading and unloading durations for the different types of vehicles from the
 * cost functions file. The durations must be expressed in hours, but are converted in seconds. If
 * set, the loading and unloading durations are added to the travel duration (movement) in the
 * detailed path header table.
 *
 * @author Bart Jourquin
 */
public class TransitTimesParser {

  /**
   * Convenient class to store transit durations for transport operations.
   *
   * @author Bart Jourquin
   */
  private class TransitTimes {
    private double loadingDuration = 0;
    private double unloadingDuration = 0;

    /**
     * Returns the loading duration in seconds.
     *
     * @return The loading duration expressed in seconds.
     */
    public double getLoadingDuration() {
      return loadingDuration;
    }

    /**
     * Returns the unloading duration in seconds.
     *
     * @return The unloading duration expressed in seconds.
     */
    public double getUnloadingDuration() {
      return unloadingDuration;
    }

    /**
     * Stores the loading duration converted in seconds.
     *
     * @param loadingDuration Loading duration in hours.
     */
    public void setLoadingDuration(double loadingDuration) {
      this.loadingDuration = loadingDuration * 3600;
    }

    /**
     * Stores the loading duration convderted in seconds.
     *
     * @param loadingDuration Loading duration in hours.
     */
    public void setUnloadingDuration(double unloadingDuration) {
      this.unloadingDuration = unloadingDuration * 3600;
    }
  }

  static final byte LOADING_DURATION = 0;
  static final byte UNLOADING_DURATION = 1;

  private Properties costFunctions;

  private int group;
  private int scenario;
  
  /** Average load per mode/means vehicle combination. */
  private HashMap<Long, TransitTimes> transitTimes = new HashMap<>();

  /**
   * Loads the different transit times (loading, unloading, transhipment) for all the vehicles for a
   * given group. If a transit time for a mode-means combination is not defined, it is set to 0.
   *
   * @param costFunctions The properties that contain the cost functions.
   * @param scenario The scenario currently being computed.
   * @param group The group of commodities for which this information must be loaded from the cost
   *     functions file.
   * @param availableModeMeans Array of available mode and means (as defined in VirtualNetwork)
   */
  public TransitTimesParser(
      Properties costFunctions, int scenario, int group, int[] availableModeMeans) {
    this.costFunctions = costFunctions;
    this.scenario = scenario;
    this.group = group;
    loadTransitTimes(availableModeMeans);
  }

  /**
   * Returns the loading duration (in seconds) for a give vehicle.
   *
   * @param mode The transportation mode.
   * @param means The transportation means (type of vehicle).
   * @return The loading duration in seconds.
   */
  public double getLoadingDuration(int mode, int means) {
    long key = mode * NodusC.MAXMM + means;
    TransitTimes tt = transitTimes.get(key);
    if (tt == null) {
      return 0;
    } else {
      return tt.getLoadingDuration();
    }
  }

  /**
   * Returns the unloading duration (in seconds) for a give vehicle.
   *
   * @param mode The transportation mode.
   * @param means The transportation means (type of vehicle).
   * @return The unloading duration in seconds.
   */
  public double getUnloadingDuration(int mode, int means) {
    long key = mode * NodusC.MAXMM + means;
    TransitTimes tt = transitTimes.get(key);
    if (tt == null) {
      return 0;
    } else {
      return tt.getUnloadingDuration();
    }
  }

  /**
   * Loads the different transit times (loading, unloading) for all the possible modes and means. If
   * a transit time for a mode-means combination is not defined, it is set to 0.
   *
   * @param availableModeMeans Array of available mode and means (as defined in VirtualNetwork)
   */
  private void loadTransitTimes(int[] availabelModeMeans) {
    transitTimes.clear();

    for (int availabelModeMean : availabelModeMeans) {
      int mode = availabelModeMean / NodusC.MAXMM;
      int means = availabelModeMean - mode * NodusC.MAXMM;
      parseDuration(NodusC.VARNAME_LOADING_DURATION, mode, means);
      parseDuration(NodusC.VARNAME_UNLOADING_DURATION, mode, means);
    }
  }

  /**
   * Find the (un)loading duration for the given mode and means.
   *
   * @param varName VARNAME_LOADING_DURATION or VARNAME_UNLOADING_DURATION
   * @param mode The transportation mode
   * @param means The transportation means.
   */
  private void parseDuration(String varName, int mode, int means) {

    byte operation = LOADING_DURATION;
    if (varName.equals(NodusC.VARNAME_UNLOADING_DURATION)) {
      operation = UNLOADING_DURATION;
    }

    long key = mode * NodusC.MAXMM + means;

    String core = varName + "." + mode + "," + means;

    // Is there a specific value for this scenario and group?
    double value =
        PropUtils.doubleFromProperties(costFunctions, scenario + "." + core + "." + group, -1);

    if (value != -1) {
      TransitTimes tt = transitTimes.get(key);
      if (tt == null) {
        tt = new TransitTimes();
        transitTimes.put(key, tt);
      }

      if (operation == LOADING_DURATION) {
        tt.setLoadingDuration(value);
      } else {
        tt.setUnloadingDuration(value);
      }

      return;
    }

    // Is there a specific value for this scenario?
    value = PropUtils.doubleFromProperties(costFunctions, scenario + "." + core, -1);

    if (value != -1) {
      TransitTimes tt = transitTimes.get(key);
      if (tt == null) {
        tt = new TransitTimes();
        transitTimes.put(key, tt);
      }

      if (operation == LOADING_DURATION) {
        tt.setLoadingDuration(value);
      } else {
        tt.setUnloadingDuration(value);
      }

      return;
    }

    // Is there a specific value for this group?
    value = PropUtils.doubleFromProperties(costFunctions, core + "." + group, -1);

    if (value != -1) {
      TransitTimes tt = transitTimes.get(key);
      if (tt == null) {
        tt = new TransitTimes();
        transitTimes.put(key, tt);
      }

      if (operation == LOADING_DURATION) {
        tt.setLoadingDuration(value);
      } else {
        tt.setUnloadingDuration(value);
      }

      return;
    }

    // Is there a generic value ?
    value = PropUtils.doubleFromProperties(costFunctions, core, -1);
    if (value != -1) {
      TransitTimes tt = transitTimes.get(key);
      if (tt == null) {
        tt = new TransitTimes();
        transitTimes.put(key, tt);
      }

      if (operation == LOADING_DURATION) {
        tt.setLoadingDuration(value);
      } else {
        tt.setUnloadingDuration(value);
      }
    }
  }

  // TODO Handle transhipment durations.
  // TODO Handle class specific values. 

}
