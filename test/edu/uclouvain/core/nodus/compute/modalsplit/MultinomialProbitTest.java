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

package edu.uclouvain.core.nodus.compute.modalsplit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Assignment uses the same joint probit probabilities and available-mode set as estimation. */
class MultinomialProbitTest {
  @Test
  void cheapestRouteDefinesModalCostAndOtherRoutesOnlyShareItsFlow() {
    MultinomialProbit model = model(-Math.sqrt(2) / Math.log(2), 0, 0);
    PathsForMode road = mode(1, 10, 12);
    PathsForMode rail = mode(2, 20);
    assertTrue(model.split(null, List.of(road, rail)));
    assertEquals(0.8413447460685429, road.marketShare, 2e-13);
    assertEquals(road.marketShare * 12 / 22, road.pathList.get(0).marketShare, 1e-13);
    assertEquals(road.marketShare * 10 / 22, road.pathList.get(1).marketShare, 1e-13);
    PathsForMode onlyCheapest = mode(1, 10);
    model.split(null, List.of(onlyCheapest, mode(2, 20)));
    assertEquals(road.marketShare, onlyCheapest.marketShare, 1e-13);
  }

  @Test
  void threeModeProbabilitiesAndUnavailableModesFollowJointNormalChoice() {
    MultinomialProbit model = model(0, 0.7, -0.4);
    PathsForMode road = mode(1, 10);
    PathsForMode rail = mode(2, 10);
    PathsForMode water = mode(3, 10);
    model.split(null, List.of(road, rail, water));
    assertEquals(0.2566057850424605, road.marketShare, 2e-12);
    assertEquals(0.5975752718063184, rail.marketShare, 2e-12);
    assertEquals(0.14581894315122107, water.marketShare, 2e-12);
    assertTrue(model.split(null, List.of(water)));
    assertEquals(1, water.marketShare);
    assertFalse(model.split(null, List.of()));
    model.split(null, List.of(road, rail));
    // Removing a third alternative requires recomputing a binary probability.
    assertEquals(0.3103089732188449, road.marketShare, 1e-12);
  }

  @Test
  void ignoresOtherModelsParametersAndRejectsIncompleteOrInvalidCalibrations() {
    Properties entries = new Properties();
    entries.setProperty("log(cost).1.0", "-1");
    AssignmentParameters parameters = new AssignmentParameters(null);
    parameters.setCostFunctions(entries);
    MultinomialProbit model = new MultinomialProbit(null);
    model.initialize(parameters);
    model.initializeGroup(0);
    PathsForMode road = mode(1, 10);
    model.split(null, List.of(road, mode(2, 10 + Math.sqrt(2))));
    assertEquals(0.8413447460685429, road.marketShare, 2e-13);
    for (String partial : new String[] {"probit.reference.0", "probit.(intercept).2.0"}) {
      entries.setProperty(partial, "1");
      assertThrows(IllegalArgumentException.class, () -> model.initializeGroup(0));
      entries.remove(partial);
    }
    MultinomialProbit initialized = model(-1, 0, 0);
    assertThrows(
        IllegalArgumentException.class, () -> initialized.split(null, List.of(mode(4, 2))));
    for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(
          IllegalArgumentException.class, () -> initialized.split(null, List.of(mode(1, bad))));
    }
  }

  @Test
  void missingGroupUsesRawCostsZeroConstantsAndAvailableAlternatives() {
    MultinomialProbit model = model(0, 0.7, -0.4);
    model.initializeGroup(1);
    PathsForMode road = mode(1, 10, 12);
    PathsForMode rail = mode(2, 9.3);
    PathsForMode water = mode(3, 10.4);
    model.split(null, List.of(road, rail, water));
    assertEquals(0.2566057850424605, road.marketShare, 2e-12);
    assertEquals(0.5975752718063184, rail.marketShare, 2e-12);
    assertEquals(0.14581894315122107, water.marketShare, 2e-12);
    assertEquals(road.marketShare * 0.8807970779778823, road.pathList.get(0).marketShare, 1e-14);
    model.split(null, List.of(road));
    assertEquals(1, road.marketShare);
    // Reloading a fitted group restores its intercepts and log-cost utility.
    model.initializeGroup(0);
    road = mode(1, 10);
    model.split(null, List.of(road, mode(2, 10), mode(3, 10)));
    assertEquals(0.2566057850424605, road.marketShare, 2e-12);
  }

  @Test
  void defaultProbitHandlesLargeCostsAndDifferencesWithoutQuadratureFailures() {
    MultinomialProbit model = model(0, 0, 0);
    model.initializeGroup(1);
    PathsForMode road = mode(1, 10000);
    PathsForMode rail = mode(2, 10000);
    PathsForMode water = mode(3, 1e9);
    model.split(null, List.of(road, rail, water));
    assertEquals(0.5, road.marketShare, 1e-14);
    assertEquals(0.5, rail.marketShare, 1e-14);
    assertEquals(0, water.marketShare);
  }

  private MultinomialProbit model(double beta, double railIntercept, double waterIntercept) {
    Properties entries = new Properties();
    for (int id = 1; id <= 3; id++) {
      entries.setProperty("probit.log(cost)." + id + ".0", Double.toString(beta));
    }
    entries.setProperty("probit.(intercept).2.0", Double.toString(railIntercept));
    entries.setProperty("probit.(intercept).3.0", Double.toString(waterIntercept));
    entries.setProperty("probit.reference.0", "1");
    AssignmentParameters parameters = new AssignmentParameters(null);
    parameters.setCostFunctions(entries);
    MultinomialProbit model = new MultinomialProbit(null);
    model.initialize(parameters);
    model.initializeGroup(0);
    return model;
  }

  private PathsForMode mode(int id, double... costs) {
    PathsForMode result = null;
    for (double cost : costs) {
      Path path = new Path();
      path.loadingMode = (byte) id;
      path.weights = new PathWeights();
      path.weights.mvCost = cost;
      if (result == null) {
        result = new PathsForMode(path);
      } else {
        result.addPath(path);
      }
    }
    return result;
  }
}
