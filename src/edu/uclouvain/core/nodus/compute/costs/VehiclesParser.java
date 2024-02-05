/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.utils.StringUtils;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 * Parser and place holder used to converts volumes (tons) into a number of vehicles.
 *
 * @author Bart Jourquin
 */
public class VehiclesParser {

  private static I18n i18n = Environment.getI18n();

  /** Average load per mode/means for each group. */
  private HashMap<String, Double>[] averageLoad;

  /** PCU per mode/means for each group. */
  private HashMap<String, Double>[] passengerCarUnits;

  private int scenario;

  /**
   * Initialize the data structure that will hold the vehicle characteristics for a scenario.
   *
   * @param scenario The scenario for which the vehicle characteristics must be loaded.
   */
  @SuppressWarnings("unchecked")
  public VehiclesParser(int scenario) {
    averageLoad = new HashMap[NodusC.MAXGROUPS];
    passengerCarUnits = new HashMap[NodusC.MAXGROUPS];
    this.scenario = scenario;
  }

  /**
   * Returns the number of PCU's for a vehicle of a given mode-means combination.
   *
   * @param group The group of commodities.
   * @param mode The transportation mode.
   * @param means The transportation means.
   * @return The Personal car units ratio.
   */
  public double getPassengerCarUnits(int group, int mode, int means) {
    String key = mode + "-" + means;
    Double value = passengerCarUnits[group].get(key);
    if (value == null) {
      return 1.0;
    } else {
      return value.doubleValue();
    }
  }

  /**
   * Returns the average load of a vehicle of a given mode-means combination.
   *
   * @param group The group of commodities.
   * @param mode The transportation mode.
   * @param means The transportation means.
   * @return double The average load for the vehicle.
   */
  public double getAverageLoad(int group, int mode, int means) {
    String key = mode + "-" + means;
    Double value = averageLoad[group].get(key);
    if (value == null) {
      return 1.0;
    } else {
      return value.doubleValue();
    }
  }

  /**
   * Loads the capacities and PCU's for all the vehicles for a given group of commodities. If a
   * capacity for a mode-means combination is not defined, it is supposed to be equal to 1. The same
   * is true for its PCU.
   *
   * @param costFunctions The properties that contain the cost functions.
   * @param group The group of commodities for which this information must be loaded from the cost
   *     functions file.
   * @return True on success.
   */
  public boolean loadVehicleCharacteristics(Properties costFunctions, byte group) {

    // If already initialized, don't load again
    if (averageLoad[group] != null) {
      return true;
    }

    averageLoad[group] = new HashMap<String, Double>();
    passengerCarUnits[group] = new HashMap<String, Double>();

    String key;

    // Load capacities
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int means = 0; means < NodusC.MAXMM; means++) {
        // averageLoad[mode][means] = 1;

        String core = NodusC.VARNAME_AVERAGELOAD + "." + mode + "," + means;

        // Is there a specific value for this scenario and group?
        String varName = scenario + "." + core + "." + group;
        String s = costFunctions.getProperty(varName, null);
        if (s != null) {
          if (!StringUtils.isNumeric(s)) {
            displayError(varName, s);
            return false;
          }
          double value = Double.parseDouble(s);

          key = mode + "-" + means;
          averageLoad[group].put(key, Double.valueOf(value));
          continue;
        }

        // Is there a specific value for this scenario?
        varName = scenario + "." + core;
        s = costFunctions.getProperty(varName, null);
        if (s != null) {
          if (!StringUtils.isNumeric(s)) {
            displayError(varName, s);
            return false;
          }
          double value = Double.parseDouble(s);

          key = mode + "-" + means;
          averageLoad[group].put(key, Double.valueOf(value));
          continue;
        }

        // Is there a specific value for this group?
        varName = core + "." + group;
        s = costFunctions.getProperty(varName, null);
        if (s != null) {
          if (!StringUtils.isNumeric(s)) {
            displayError(varName, s);
            return false;
          }
          double value = Double.parseDouble(s);

          key = mode + "-" + means;
          averageLoad[group].put(key, Double.valueOf(value));
          continue;
        }

        // Is there a generic value ?
        varName = core;
        s = costFunctions.getProperty(varName, null);
        if (s != null) {
          if (!StringUtils.isNumeric(s)) {
            displayError(varName, s);
            return false;
          }
          double value = Double.parseDouble(s);

          key = mode + "-" + means;
          averageLoad[group].put(key, Double.valueOf(value));
          continue;
        }
      }
    }

    // Load equivalent standard vehicles ratios
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int means = 0; means < NodusC.MAXMM; means++) {

        String core = NodusC.VARNAME_PCU + "." + mode + "," + means;

        // Is there a specific value for this scenario and group?
        String varName = scenario + "." + core + "." + group;
        String s = costFunctions.getProperty(varName, null);
        if (s != null) {
          if (!StringUtils.isNumeric(s)) {
            displayError(varName, s);
            return false;
          }
          double value = Double.parseDouble(s);

          key = mode + "-" + means;
          passengerCarUnits[group].put(key, Double.valueOf(value));
          continue;
        }

        // Is there a specific value for this scenario?
        varName = scenario + "." + core;
        s = costFunctions.getProperty(varName, null);
        if (s != null) {
          if (!StringUtils.isNumeric(s)) {
            displayError(varName, s);
            return false;
          }
          double value = Double.parseDouble(s);

          key = mode + "-" + means;
          passengerCarUnits[group].put(key, Double.valueOf(value));
          continue;
        }

        // Is there a specific value for this group?
        varName = core + "." + group;
        s = costFunctions.getProperty(varName, null);
        if (s != null) {
          if (!StringUtils.isNumeric(s)) {
            displayError(varName, s);
            return false;
          }
          double value = Double.parseDouble(s);

          key = mode + "-" + means;
          passengerCarUnits[group].put(key, Double.valueOf(value));
          continue;
        }

        // Is there a generic value ?
        varName = core;
        s = costFunctions.getProperty(varName, null);
        if (s != null) {
          if (!StringUtils.isNumeric(s)) {
            displayError(varName, s);
            return false;
          }
          double value = Double.parseDouble(s);

          key = mode + "-" + means;
          passengerCarUnits[group].put(key, Double.valueOf(value));
          continue;
        }
      }
    }
    return true;
  }

  private void displayError(String varName, String value) {
    String errorMessage =
        MessageFormat.format(
            i18n.get(
                VehiclesParser.class,
                "is_not_a_valid_number",
                "''{0} = {1}'' is not a valid number"),
            varName,
            value);
    JOptionPane.showMessageDialog(null, errorMessage, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
  }
}
