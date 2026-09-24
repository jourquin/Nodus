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

import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Checks which numeric parameter an assignment receives for its scenario, group and OD class. */
class CostParameterTest {
  @Test
  void increasinglyGenericParametersAreUsedOnlyWhenMoreSpecificOnesAreAbsent() {
    final String[] precedence = {
      "7.VALUE.3-2", "7.VALUE.3", "7.VALUE-2", "7.VALUE",
      "VALUE.3-2", "VALUE-2", "VALUE.3", "VALUE"
    };
    final Properties properties = new Properties();
    for (int i = 0; i < precedence.length; i++) {
      properties.setProperty(precedence[i], Double.toString(i + 0.5));
    }
    for (int i = 0; i < precedence.length; i++) {
      assertEquals(
          i + 0.5,
          CostParser.getValue(properties, "VALUE", 7, (byte) 3, (byte) 2),
          1e-12,
          "Expected parameter " + precedence[i]);
      properties.remove(precedence[i]);
    }
    assertTrue(Double.isNaN(CostParser.getValue(properties, "VALUE", 7, (byte) 3, (byte) 2)));
  }

  @Test
  void valuesDoNotLeakIntoAnotherScenarioGroupClassOrVariable() {
    Properties properties = new Properties();
    properties.setProperty("7.VALUE.3-2", "12.5");
    properties.setProperty("VALUE", "1.25");
    assertEquals(12.5, CostParser.getValue(properties, "VALUE", 7, (byte) 3, (byte) 2), 1e-12);
    assertEquals(1.25, CostParser.getValue(properties, "VALUE", 8, (byte) 3, (byte) 2), 1e-12);
    assertEquals(1.25, CostParser.getValue(properties, "VALUE", 7, (byte) 4, (byte) 2), 1e-12);
    assertEquals(1.25, CostParser.getValue(properties, "VALUE", 7, (byte) 3, (byte) 4), 1e-12);
    assertTrue(Double.isNaN(CostParser.getValue(properties, "OTHER", 7, (byte) 3, (byte) 2)));
  }

  @Test
  void zeroAndNegativeOverridesAreValuesRatherThanMissingParameters() {
    Properties properties = new Properties();
    properties.setProperty("VALUE", "99");
    properties.setProperty("7.VALUE.3-2", "0");
    assertEquals(0, CostParser.getValue(properties, "VALUE", 7, (byte) 3, (byte) 2), 1e-12);
    properties.setProperty("7.VALUE.3-2", "-2.5e-3");
    assertEquals(-0.0025, CostParser.getValue(properties, "VALUE", 7, (byte) 3, (byte) 2), 1e-12);
  }

  @Test
  void nanOverridesFallBackAndInheritedPropertiesRemainAvailable() {
    final Properties defaults = new Properties();
    defaults.setProperty("VALUE", "2.5");
    Properties properties = new Properties(defaults);
    properties.setProperty("7.VALUE.3-2", "NaN");
    assertEquals(2.5, CostParser.getValue(properties, "VALUE", 7, (byte) 3, (byte) 2), 1e-12);
    properties.setProperty("7.VALUE", "3.5");
    assertEquals(3.5, CostParser.getValue(properties, "VALUE", 7, (byte) 3, (byte) 2), 1e-12);
  }
}
