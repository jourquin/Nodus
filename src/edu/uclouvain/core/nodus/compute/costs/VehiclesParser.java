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
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.BiConsumer;
import javax.swing.JOptionPane;

/**
 * Stores the vehicle characteristics used to convert volumes (tons) into vehicle counts.
 *
 * <p>Each loaded commodity group has dense mode/means tables. Conversion can therefore look up
 * capacities and PCU factors without allocating string keys or accessing boxed values for every
 * link. Missing characteristics retain the default value of one. Average loads must be finite and
 * strictly positive. PCU factors must be finite and nonnegative; zero excludes the vehicle from
 * equivalent road traffic while preserving its vehicle count.
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

  private final BiConsumer<String, String> errorReporter;

  /**
   * Initialize the data structure that will hold the vehicle characteristics for a scenario.
   *
   * @param scenario The scenario for which the vehicle characteristics must be loaded.
   */
  public VehiclesParser(int scenario) {
    this(scenario, VehiclesParser::displayError);
  }

  /** Creates a parser with a caller-supplied error reporter. */
  VehiclesParser(int scenario, BiConsumer<String, String> errorReporter) {
    averageLoad = new double[NodusC.MAXGROUPS][];
    passengerCarUnits = new double[NodusC.MAXGROUPS][];
    this.scenario = scenario;
    this.errorReporter = errorReporter;
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
   * is true for its PCU. Invalid values leave the group uninitialized so a corrected load can be
   * retried. Only the selected value is validated, following scenario/group precedence.
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

    final double[] loads = new double[NodusC.MAXMM * NodusC.MAXMM];
    final double[] pcu = new double[NodusC.MAXMM * NodusC.MAXMM];
    Arrays.fill(loads, 1.0);
    Arrays.fill(pcu, 1.0);
    if (!loadValues(costFunctions, group, NodusC.VARNAME_AVERAGELOAD, loads, true)
        || !loadValues(costFunctions, group, NodusC.VARNAME_PCU, pcu, false)) {
      return false;
    }

    // Publish both complete rows together; a failed load must remain retryable.
    averageLoad[group] = loads;
    passengerCarUnits[group] = pcu;
    return true;
  }

  private boolean loadValues(
      Properties costFunctions,
      byte group,
      String characteristic,
      double[] values,
      boolean capacity) {
    for (int mode = 0; mode < NodusC.MAXMM; mode++) {
      for (int means = 0; means < NodusC.MAXMM; means++) {
        final String core = characteristic + "." + mode + "," + means;
        final String[] keys = {
          scenario + "." + core + "." + group, scenario + "." + core, core + "." + group, core
        };
        for (String key : keys) {
          final String text = costFunctions.getProperty(key);
          if (text == null) {
            continue;
          }
          final double value;
          try {
            value = Double.parseDouble(text);
          } catch (NumberFormatException failure) {
            errorReporter.accept(key, text);
            return false;
          }
          if (!Double.isFinite(value) || (capacity ? value <= 0 : value < 0)) {
            errorReporter.accept(key, text);
            return false;
          }
          values[mode * NodusC.MAXMM + means] = value;
          break;
        }
      }
    }
    return true;
  }

  private static void displayError(String varName, String value) {
    String errorMessage =
        MessageFormat.format(
            i18n.get(
                VehiclesParser.class,
                "Invalid_vehicle_characteristic",
                "''{0} = {1}'' is invalid. Average load must be finite and greater than zero; "
                    + "PCU must be finite and nonnegative."),
            varName,
            value);
    JOptionPane.showMessageDialog(null, errorMessage, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
  }
}
