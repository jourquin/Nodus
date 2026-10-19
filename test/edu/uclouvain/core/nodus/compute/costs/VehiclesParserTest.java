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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Protects capacity and PCU selection before volumes are converted into vehicle counts. */
class VehiclesParserTest {
  @Test
  void mostSpecificValueWinsForBothCapacityAndPassengerCarUnits() {
    final Properties properties = new Properties();
    addValues(properties, "", "", "10", "1.5");
    addValues(properties, "", ".3", "20", "2.5");
    addValues(properties, "7.", "", "30", "3.5");
    addValues(properties, "7.", ".3", "40", "4.5");
    VehiclesParser parser = new VehiclesParser(7);
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
    assertEquals(40, parser.getAverageLoad(3, 1, 2), 1e-12);
    assertEquals(4.5, parser.getPassengerCarUnits(3, 1, 2), 1e-12);
  }

  @Test
  void scenarioDefaultTakesPrecedenceOverGroupDefault() {
    final Properties properties = new Properties();
    addValues(properties, "", "", "10", "1.5");
    addValues(properties, "", ".3", "20", "2.5");
    addValues(properties, "7.", "", "30", "3.5");
    VehiclesParser parser = new VehiclesParser(7);
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
    assertEquals(30, parser.getAverageLoad(3, 1, 2), 1e-12);
    assertEquals(3.5, parser.getPassengerCarUnits(3, 1, 2), 1e-12);
  }

  @Test
  void groupAndGenericDefaultsDoNotLeakAcrossScenariosGroupsOrModeMeans() {
    final Properties properties = new Properties();
    addValues(properties, "", "", "10", "1.5");
    addValues(properties, "", ".3", "20", "2.5");
    addValues(properties, "7.", "", "30", "3.5");
    VehiclesParser parser = new VehiclesParser(8);
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 4));
    assertEquals(20, parser.getAverageLoad(3, 1, 2), 1e-12);
    assertEquals(2.5, parser.getPassengerCarUnits(3, 1, 2), 1e-12);
    assertEquals(10, parser.getAverageLoad(4, 1, 2), 1e-12);
    assertEquals(1.5, parser.getPassengerCarUnits(4, 1, 2), 1e-12);
    assertEquals(1, parser.getAverageLoad(3, 2, 1), 1e-12);
    assertEquals(1, parser.getPassengerCarUnits(3, 2, 1), 1e-12);
  }

  @Test
  void missingAndOutOfRangeModeMeansUseUnitDefaults() {
    VehiclesParser parser = new VehiclesParser(7);
    assertTrue(parser.loadVehicleCharacteristics(new Properties(), (byte) 3));
    int[][] combinations = {{0, 0}, {1, 2}, {-1, 2}, {1, -1}, {NodusC.MAXMM, 2}, {1, NodusC.MAXMM}};
    for (int[] combination : combinations) {
      assertEquals(1, parser.getAverageLoad(3, combination[0], combination[1]), 1e-12);
      assertEquals(1, parser.getPassengerCarUnits(3, combination[0], combination[1]), 1e-12);
    }
  }

  @Test
  void fractionalValuesArePreservedAndReloadDoesNotOverwriteAnInitializedGroup() {
    final Properties properties = new Properties();
    addValues(properties, "", "", "12.5", "0.75");
    final VehiclesParser parser = new VehiclesParser(7);
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
    addValues(properties, "", "", "99", "9");
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 4));
    assertEquals(12.5, parser.getAverageLoad(3, 1, 2), 1e-12);
    assertEquals(0.75, parser.getPassengerCarUnits(3, 1, 2), 1e-12);
    assertEquals(99, parser.getAverageLoad(4, 1, 2), 1e-12);
    assertEquals(9, parser.getPassengerCarUnits(4, 1, 2), 1e-12);
  }

  @TestFactory
  List<DynamicTest> invalidCharacteristicsAreRejectedAtEveryPrecedenceAndCanBeRetried() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String prefix : new String[] {"", "7."}) {
      for (String suffix : new String[] {"", ".3"}) {
        for (boolean capacity : new boolean[] {false, true}) {
          final String characteristic = capacity ? NodusC.VARNAME_AVERAGELOAD : NodusC.VARNAME_PCU;
          final String[] invalid =
              capacity
                  ? new String[] {
                    "0", "-0.0", "-1", "NaN", "Infinity", "-Infinity", "1e309", "1e-999", "bad", ""
                  }
                  : new String[] {"-1", "NaN", "Infinity", "-Infinity", "1e309", "bad", ""};
          for (String value : invalid) {
            final String key = prefix + characteristic + ".1,2" + suffix;
            tests.add(
                dynamicTest(
                    key + "=" + value,
                    () -> {
                      final List<List<String>> errors = new ArrayList<>();
                      final VehiclesParser parser =
                          new VehiclesParser(
                              7, (name, rejected) -> errors.add(List.of(name, rejected)));
                      final Properties properties = new Properties();
                      addValues(properties, "", ".4", "12", "2");
                      assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 4));
                      properties.setProperty(key, value);
                      assertFalse(parser.loadVehicleCharacteristics(properties, (byte) 3));
                      assertEquals(List.of(List.of(key, value)), errors);

                      // A retry must reload even values processed before the rejected property.
                      properties.setProperty(key, capacity ? "2.5" : "0.75");
                      properties.setProperty(NodusC.VARNAME_AVERAGELOAD + ".0,0", "6.5");
                      properties.setProperty(NodusC.VARNAME_PCU + ".0,0", "0.25");
                      assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
                      assertEquals(capacity ? 2.5 : 1, parser.getAverageLoad(3, 1, 2));
                      assertEquals(capacity ? 1 : 0.75, parser.getPassengerCarUnits(3, 1, 2));
                      assertEquals(6.5, parser.getAverageLoad(3, 0, 0));
                      assertEquals(0.25, parser.getPassengerCarUnits(3, 0, 0));
                      assertEquals(1, parser.getAverageLoad(3, 2, 1));
                      assertEquals(1, parser.getPassengerCarUnits(3, 2, 1));
                      assertEquals(12, parser.getAverageLoad(4, 1, 2));
                      assertEquals(2, parser.getPassengerCarUnits(4, 1, 2));
                      assertEquals(1, errors.size());
                    }));
          }
        }
      }
    }
    return tests;
  }

  @Test
  void zeroPcuAndFinitePositiveCapacitiesAreAllowed() {
    final VehiclesParser parser =
        new VehiclesParser(
            7,
            (name, value) -> {
              throw new AssertionError(name + "=" + value);
            });
    final Properties properties = new Properties();
    addValues(properties, "", "", "2.5", "0");
    properties.setProperty(NodusC.VARNAME_AVERAGELOAD + ".2,1", Double.toString(Double.MIN_VALUE));
    properties.setProperty(NodusC.VARNAME_AVERAGELOAD + ".3,1", Double.toString(Double.MAX_VALUE));
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
    assertEquals(2.5, parser.getAverageLoad(3, 1, 2));
    assertEquals(0, parser.getPassengerCarUnits(3, 1, 2));
    assertEquals(Double.MIN_VALUE, parser.getAverageLoad(3, 2, 1));
    assertEquals(Double.MAX_VALUE, parser.getAverageLoad(3, 3, 1));
  }

  @Test
  void overriddenInvalidDefaultsDoNotRejectValidSelectedCharacteristics() {
    final VehiclesParser parser =
        new VehiclesParser(
            7,
            (name, value) -> {
              throw new AssertionError(name + "=" + value);
            });
    final Properties properties = new Properties();
    addValues(properties, "", "", "0", "NaN");
    addValues(properties, "", ".3", "-1", "bad");
    addValues(properties, "7.", "", "Infinity", "-1");
    addValues(properties, "7.", ".3", "2.5", "0.75");
    assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
    assertEquals(2.5, parser.getAverageLoad(3, 1, 2));
    assertEquals(0.75, parser.getPassengerCarUnits(3, 1, 2));
  }

  private static void addValues(
      Properties properties, String scenario, String group, String load, String pcu) {
    properties.setProperty(scenario + NodusC.VARNAME_AVERAGELOAD + ".1,2" + group, load);
    properties.setProperty(scenario + NodusC.VARNAME_PCU + ".1,2" + group, pcu);
  }
}
