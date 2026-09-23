/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
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
import java.util.Arrays;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 * Stores the vehicle characteristics used to convert volumes (tons) into vehicle counts.
 *
 * <p>Each loaded commodity group has dense mode/means tables. Conversion can therefore look up
 * capacities and PCU factors without allocating string keys or accessing boxed values for every
 * link. Missing characteristics retain the default value of one.
 *
 * @author Bart Jourquin
 */
public class VehiclesParser {

  private static I18n i18n = Environment.getI18n();

  /** Average loads, indexed by commodity group and then mode * MAXMM + means. */
  private double[][] averageLoad;

  /** PCU factors with the same indexing; rows are allocated only for loaded groups. */
  private double[][] passengerCarUnits;

  private int scenario;

  /**
   * Initialize the data structure that will hold the vehicle characteristics for a scenario.
   *
   * @param scenario The scenario for which the vehicle characteristics must be loaded.
   */
  public VehiclesParser(int scenario) {
    averageLoad = new double[NodusC.MAXGROUPS][];
    passengerCarUnits = new double[NodusC.MAXGROUPS][];
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
    // Out-of-range combinations were absent from the old maps and also default to one.
    if (mode < 0 || mode >= NodusC.MAXMM || means < 0 || means >= NodusC.MAXMM) {
      return 1.0;
    }
    return passengerCarUnits[group][mode * NodusC.MAXMM + means];
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
    // Out-of-range combinations were absent from the old maps and also default to one.
    if (mode < 0 || mode >= NodusC.MAXMM || means < 0 || means >= NodusC.MAXMM) {
      return 1.0;
    }
    return averageLoad[group][mode * NodusC.MAXMM + means];
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

    averageLoad[group] = new double[NodusC.MAXMM * NodusC.MAXMM];
    passengerCarUnits[group] = new double[NodusC.MAXMM * NodusC.MAXMM];
    Arrays.fill(averageLoad[group], 1.0);
    Arrays.fill(passengerCarUnits[group], 1.0);

    // Load capacities
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int means = 0; means < NodusC.MAXMM; means++) {
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

          averageLoad[group][mode * NodusC.MAXMM + means] = value;
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

          averageLoad[group][mode * NodusC.MAXMM + means] = value;
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

          averageLoad[group][mode * NodusC.MAXMM + means] = value;
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

          averageLoad[group][mode * NodusC.MAXMM + means] = value;
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

          passengerCarUnits[group][mode * NodusC.MAXMM + means] = value;
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

          passengerCarUnits[group][mode * NodusC.MAXMM + means] = value;
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

          passengerCarUnits[group][mode * NodusC.MAXMM + means] = value;
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

          passengerCarUnits[group][mode * NodusC.MAXMM + means] = value;
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
