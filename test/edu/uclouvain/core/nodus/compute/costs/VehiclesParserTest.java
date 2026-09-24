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
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import java.util.Properties;
import org.junit.jupiter.api.Test;

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

  private static void addValues(
      Properties properties, String scenario, String group, String load, String pcu) {
    properties.setProperty(scenario + NodusC.VARNAME_AVERAGELOAD + ".1,2" + group, load);
    properties.setProperty(scenario + NodusC.VARNAME_PCU + ".1,2" + group, pcu);
  }
}
