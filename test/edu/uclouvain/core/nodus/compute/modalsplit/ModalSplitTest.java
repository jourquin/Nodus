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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Numerical checks that need neither a Nodus project nor an OD database. */
class ModalSplitTest {
  @Test
  void proportionalSharesFollowInverseCostsWithinAndBetweenModes() {
    PathsForMode road = mode(1, 10, 20);
    PathsForMode rail = mode(2, 20);
    List<PathsForMode> modes = Arrays.asList(road, rail);
    assertTrue(new Proportional(null).split(null, modes));
    assertEquals(2.0 / 3, road.marketShare, 1e-12);
    assertEquals(1.0 / 3, rail.marketShare, 1e-12);
    assertEquals(4.0 / 9, road.pathList.get(0).marketShare, 1e-12);
    assertEquals(2.0 / 9, road.pathList.get(1).marketShare, 1e-12);
    assertSharesSumToOne(modes);
  }

  @Test
  void proportionalFactorsAdjustModalSharesWithoutChangingPhysicalCostsOrRouteShares() {
    Properties costs = new Properties();
    costs.setProperty("proportional.reference.0", "1");
    costs.setProperty("proportional.costFactor.1.0", "1");
    costs.setProperty("proportional.costFactor.2.0", "0.25");
    Proportional model = proportional(costs);
    PathsForMode road = mode(1, 10, 20);
    PathsForMode rail = mode(2, 20);
    assertTrue(model.split(null, List.of(road, rail)));
    assertEquals(1.0 / 3, road.marketShare, 1e-12);
    assertEquals(2.0 / 3, rail.marketShare, 1e-12);
    assertEquals(2.0 / 9, road.pathList.get(0).marketShare, 1e-12);
    assertEquals(1.0 / 9, road.pathList.get(1).marketShare, 1e-12);
    assertEquals(10, road.cheapestPathWeights.getCost());
    assertEquals(20, rail.cheapestPathWeights.getCost());
    assertSharesSumToOne(List.of(road, rail));
    // An unavailable reference mode is simply absent from the choice set.
    assertTrue(model.split(null, List.of(rail)));
    assertEquals(1, rail.marketShare);
    // A group without calibration keeps the original behavior; factors must not leak across groups.
    model.initializeGroup(1);
    assertTrue(model.split(null, List.of(road, rail)));
    assertEquals(2.0 / 3, road.marketShare, 1e-12);
  }

  @Test
  void proportionalRejectsIncompleteOrInvalidCalibrations() {
    Properties costs = new Properties();
    costs.setProperty("proportional.reference.0", "1");
    costs.setProperty("proportional.costFactor.1.0", "1");
    assertThrows(
        IllegalArgumentException.class,
        () -> proportional(costs).split(null, List.of(mode(2, 10))));
    for (String factor : new String[] {"0", "-1", "NaN", "Infinity", "broken"}) {
      costs.setProperty("proportional.costFactor.2.0", factor);
      assertThrows(IllegalArgumentException.class, () -> proportional(costs));
    }
    costs.setProperty("proportional.costFactor.2.0", "1");
    costs.setProperty("proportional.costFactor.1.0", "2");
    assertThrows(IllegalArgumentException.class, () -> proportional(costs));
  }

  @Test
  void proportionalDoesNotOverflowAdjustedCosts() {
    Properties costs = new Properties();
    costs.setProperty("proportional.reference.0", "1");
    costs.setProperty("proportional.costFactor.1.0", "1");
    costs.setProperty("proportional.costFactor.2.0", "1e200");
    PathsForMode road = mode(1, 1e300);
    PathsForMode rail = mode(2, 1e300);
    assertTrue(proportional(costs).split(null, List.of(road, rail)));
    assertEquals(1e-200, rail.marketShare, 1e-212);
    assertSharesSumToOne(List.of(road, rail));
  }

  private static Proportional proportional(Properties costs) {
    AssignmentParameters parameters = new AssignmentParameters(null);
    parameters.setCostFunctions(costs);
    Proportional model = new Proportional(null);
    model.initialize(parameters);
    model.initializeGroup(0);
    return model;
  }

  @Test
  void logitSharesFollowEstimatedLogCostsAndInverseCostsWithinModes() {
    PathsForMode road = mode(1, 10, 30);
    PathsForMode rail = mode(2, 30);
    List<PathsForMode> modes = Arrays.asList(road, rail);
    assertTrue(logit(-1, 0).split(null, modes));
    assertEquals(0.75, road.marketShare, 1e-12);
    assertEquals(0.25, rail.marketShare, 1e-12);
    assertEquals(0.5625, road.pathList.get(0).marketShare, 1e-12);
    assertEquals(0.1875, road.pathList.get(1).marketShare, 1e-12);
    assertSharesSumToOne(modes);
  }

  @Test
  void logitDoesNotUnderflowWhenAllCostsAreLarge() {
    PathsForMode road = mode(1, 10000, 10000);
    PathsForMode rail = mode(2, 10000);
    List<PathsForMode> modes = Arrays.asList(road, rail);
    assertTrue(logit(-1, 0).split(null, modes));
    assertEquals(0.5, road.marketShare, 1e-12);
    assertEquals(0.5, rail.marketShare, 1e-12);
    assertEquals(0.25, road.pathList.get(0).marketShare, 1e-12);
    assertEquals(0.25, road.pathList.get(1).marketShare, 1e-12);
    assertSharesSumToOne(modes);
  }

  @Test
  void logitAppliesModeSpecificSlopesWithoutSubtractingModeSpecificUtilityShifts() {
    Properties costs = new Properties();
    costs.setProperty("log(cost).1.0", "-1");
    costs.setProperty("log(cost).2.0", "-2");
    costs.setProperty("(intercept).2.0", "0");
    MultinomialLogit model = logit(costs);
    PathsForMode road = mode(1, 2, 4);
    PathsForMode rail = mode(2, 2);
    for (List<PathsForMode> alternatives : List.of(List.of(road, rail), List.of(rail, road))) {
      model.split(null, alternatives);
      assertEquals(2.0 / 3, road.marketShare, 1e-12);
      assertEquals(1.0 / 3, rail.marketShare, 1e-12);
      assertEquals(4.0 / 9, road.pathList.get(0).marketShare, 1e-12);
      assertSharesSumToOne(alternatives);
    }
  }

  @Test
  void logitRejectsInvalidCostsAndMissingParameters() {
    for (double cost : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> logit(-1, 0).split(null, Collections.singletonList(mode(1, 5, cost))));
    }
    for (String partial : new String[] {"mnl.reference.0", "(intercept).2.0"}) {
      Properties costs = new Properties();
      costs.setProperty(partial, "1");
      assertThrows(IllegalArgumentException.class, () -> logit(costs));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> logit(-1, 0).split(null, Collections.singletonList(mode(3, 5))));
  }

  @Test
  void absentParametersRestoreLegacyNegativeCostAndExponentialRouteShares() {
    Properties costs = new Properties();
    costs.setProperty("probit.log(cost).1.0", "123");
    costs.setProperty("proportional.costFactor.1.0", "1");
    final Properties original = (Properties) costs.clone();
    MultinomialLogit model = logit(costs);
    PathsForMode road = mode(1, 10, 12);
    PathsForMode rail = mode(2, 11);
    road.pathList.get(0).intermodal = true;
    assertTrue(model.split(null, List.of(road, rail)));
    // Independent values for exp(-C), distinctly different from inverse cost or exp(+C).
    assertEquals(0.7310585786300049, road.marketShare, 1e-14);
    assertEquals(0.2689414213699951, rail.marketShare, 1e-14);
    assertEquals(road.marketShare * 0.8807970779778823, road.pathList.get(0).marketShare, 1e-14);
    assertEquals(road.marketShare * 0.1192029220221176, road.pathList.get(1).marketShare, 1e-14);
    assertSharesSumToOne(List.of(road, rail));
    assertEquals(original, costs);
    assertEquals(10, road.cheapestPathWeights.getCost());
    assertTrue(model.split(null, List.of(rail)));
    assertEquals(1, rail.marketShare);
  }

  @Test
  void defaultAndFittedGroupsDoNotLeakIntoEachOtherOrWorkerClones() throws Exception {
    MultinomialLogit model = logit(-1, 0);
    MultinomialLogit worker = (MultinomialLogit) model.clone();
    worker.initializeGroup(1);
    PathsForMode road = mode(1, 1);
    PathsForMode rail = mode(2, 2);
    worker.split(null, List.of(road, rail));
    assertEquals(0.7310585786300049, road.marketShare, 1e-14);
    model.split(null, List.of(road, rail));
    assertEquals(2.0 / 3, road.marketShare, 1e-14);
    worker.initializeGroup(0);
    worker.split(null, List.of(road, rail));
    assertEquals(2.0 / 3, road.marketShare, 1e-14);
  }

  @Test
  void defaultLogitAcceptsFiniteCostsAndStaysStableWithLargeCosts() {
    MultinomialLogit model = logit(new Properties());
    for (double cost : new double[] {-1, 0, 10000, Double.MAX_VALUE}) {
      PathsForMode road = mode(1, cost, cost);
      PathsForMode rail = mode(2, cost);
      model.split(null, List.of(road, rail));
      assertEquals(0.5, road.marketShare, 1e-14);
      assertEquals(0.25, road.pathList.get(0).marketShare, 1e-14);
      assertSharesSumToOne(List.of(road, rail));
    }
    for (double cost : new double[] {Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> model.split(null, List.of(mode(1, cost))));
    }
  }

  @Test
  void zeroParametersGiveEqualModeSharesRegardlessOfMeansAndCost() {
    PathsForMode road = mode(1, 1, 2, 3);
    PathsForMode rail = mode(2, 1000);
    assertTrue(logit(0, 0).split(null, Arrays.asList(road, rail)));
    assertEquals(0.5, road.marketShare, 1e-12);
    assertEquals(0.5, rail.marketShare, 1e-12);
    assertSharesSumToOne(Arrays.asList(road, rail));
  }

  @Test
  void interceptsShiftSharesAndExtremeUtilitiesStayFinite() {
    PathsForMode road = mode(1, 1e-200);
    PathsForMode rail = mode(2, 1e200);
    assertTrue(logit(-10, 1000).split(null, Arrays.asList(road, rail)));
    assertEquals(1, road.marketShare);
    assertEquals(0, rail.marketShare);
    road = mode(1, 10);
    rail = mode(2, 10);
    assertTrue(logit(-1, Math.log(3)).split(null, Arrays.asList(road, rail)));
    assertEquals(0.25, road.marketShare, 1e-12);
    assertEquals(0.75, rail.marketShare, 1e-12);
  }

  @Test
  void addingACheaperAlternativeUpdatesTheModalCostUsedForSplitting() {
    PathsForMode road = mode(1, 40, 10);
    PathsForMode rail = mode(2, 20);
    assertTrue(new Proportional(null).split(null, Arrays.asList(road, rail)));
    assertEquals(2.0 / 3, road.marketShare, 1e-12);
    assertEquals(1.0 / 3, rail.marketShare, 1e-12);
  }

  @Test
  void logitReturnsFalseWhenThereAreNoAlternatives() {
    assertFalse(new MultinomialLogit(null).split(null, Collections.emptyList()));
  }

  private static MultinomialLogit logit(double beta, double intercept) {
    Properties costs = new Properties();
    costs.setProperty("log(cost).1.0", Double.toString(beta));
    costs.setProperty("log(cost).2.0", Double.toString(beta));
    costs.setProperty("(intercept).2.0", Double.toString(intercept));
    return logit(costs);
  }

  private static MultinomialLogit logit(Properties costs) {
    AssignmentParameters parameters = new AssignmentParameters(null);
    parameters.setCostFunctions(costs);
    MultinomialLogit model = new MultinomialLogit(null);
    model.initialize(parameters);
    model.initializeGroup(0);
    return model;
  }

  private static PathsForMode mode(int id, double... costs) {
    PathsForMode result = new PathsForMode(path(id, costs[0]));
    for (int i = 1; i < costs.length; i++) {
      result.addPath(path(id, costs[i]));
    }
    return result;
  }

  private static Path path(int mode, double cost) {
    Path path = new Path();
    path.loadingMode = (byte) mode;
    path.weights = new PathWeights();
    path.weights.mvCost = cost;
    return path;
  }

  private static void assertSharesSumToOne(List<PathsForMode> modes) {
    double modalSum = 0;
    double pathSum = 0;
    for (PathsForMode mode : modes) {
      assertTrue(Double.isFinite(mode.marketShare));
      assertTrue(mode.marketShare >= 0 && mode.marketShare <= 1);
      modalSum += mode.marketShare;
      double withinMode = 0;
      for (Path path : mode.pathList) {
        assertTrue(Double.isFinite(path.marketShare));
        assertTrue(path.marketShare >= 0 && path.marketShare <= 1);
        withinMode += path.marketShare;
      }
      assertEquals(mode.marketShare, withinMode, 1e-12);
      pathSum += withinMode;
    }
    assertEquals(1, modalSum, 1e-12);
    assertEquals(1, pathSum, 1e-12);
  }
}
