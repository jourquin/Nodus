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

package edu.uclouvain.core.nodus.compute.assign.modalsplit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Checks the Abraham model against independently calculated inverse-power shares. */
class AbrahamTest {
  @Test
  void inverseSquareCostsDistributeDemandBetweenModesAndTheirPaths() {
    final Abraham method = method("-2");
    final PathsForMode road = mode(1, 10, 20);
    final PathsForMode rail = mode(2, 20);
    List<PathsForMode> alternatives = List.of(road, rail);
    assertTrue(method.split(null, alternatives));
    assertEquals(0.8, road.marketShare, 1e-12);
    assertEquals(0.2, rail.marketShare, 1e-12);
    assertEquals(0.64, road.pathList.get(0).marketShare, 1e-12);
    assertEquals(0.16, road.pathList.get(1).marketShare, 1e-12);
    assertConservedShares(alternatives);
  }

  @Test
  void missingExponentUsesMinusTen() {
    final Abraham method = initialized(new Properties());
    final PathsForMode road = mode(1, 1);
    final PathsForMode rail = mode(2, 2);
    assertTrue(method.split(null, List.of(road, rail)));
    assertEquals(1024.0 / 1025, road.marketShare, 1e-12);
    assertEquals(1.0 / 1025, rail.marketShare, 1e-12);
  }

  @Test
  void switchingGroupsSelectsSpecificExponentThenReturnsToGenericValue() {
    final Properties properties = new Properties();
    properties.setProperty(NodusC.VARNAME_ABRAHAM, "-1");
    properties.setProperty(NodusC.VARNAME_ABRAHAM + ".3", "-2");
    final Abraham method = initialized(properties);
    final PathsForMode road = mode(1, 10);
    final PathsForMode rail = mode(2, 20);
    final List<PathsForMode> alternatives = List.of(road, rail);
    method.initializeGroup(3);
    assertTrue(method.split(null, alternatives));
    assertEquals(0.8, road.marketShare, 1e-12);
    method.initializeGroup(4);
    assertTrue(method.split(null, alternatives));
    assertEquals(2.0 / 3, road.marketShare, 1e-12);
    assertConservedShares(alternatives);
  }

  @Test
  void workerClonesCanInitializeDifferentGroupsWithoutChangingOneAnother()
      throws CloneNotSupportedException {
    final Properties properties = new Properties();
    properties.setProperty(NodusC.VARNAME_ABRAHAM, "-1");
    properties.setProperty(NodusC.VARNAME_ABRAHAM + ".3", "-2");
    final Abraham original = initialized(properties);
    final Abraham worker = (Abraham) original.clone();
    worker.initializeGroup(3);
    final PathsForMode originalRoad = mode(1, 10);
    final PathsForMode workerRoad = mode(1, 10);
    assertTrue(original.split(null, List.of(originalRoad, mode(2, 20))));
    assertTrue(worker.split(null, List.of(workerRoad, mode(2, 20))));
    assertEquals(2.0 / 3, originalRoad.marketShare, 1e-12);
    assertEquals(0.8, workerRoad.marketShare, 1e-12);
  }

  @Test
  void steepExponentAndLargeCostsStillProduceNormalizedShares() {
    final Abraham method = method("-100");
    final PathsForMode road = mode(1, 10000, 10000);
    final PathsForMode rail = mode(2, 10000);
    List<PathsForMode> alternatives = List.of(road, rail);
    assertTrue(method.split(null, alternatives));
    assertEquals(0.5, road.marketShare, 1e-12);
    assertEquals(0.5, rail.marketShare, 1e-12);
    assertEquals(0.25, road.pathList.get(0).marketShare, 1e-12);
    assertEquals(0.25, road.pathList.get(1).marketShare, 1e-12);
    assertConservedShares(alternatives);
  }

  @Test
  void rescalingCostUnitsDoesNotChangeSharesOrLoseDemand() {
    final Abraham method = method("-2");
    for (double scale : new double[] {1e-200, 1, 1e200}) {
      final PathsForMode road = mode(1, 10 * scale, 20 * scale);
      final PathsForMode rail = mode(2, 20 * scale);
      List<PathsForMode> alternatives = List.of(road, rail);
      assertTrue(method.split(null, alternatives));
      assertEquals(0.8, road.marketShare, 1e-12, "Cost scale " + scale);
      assertEquals(0.2, rail.marketShare, 1e-12, "Cost scale " + scale);
      assertConservedShares(alternatives);
    }
  }

  @Test
  void shallowExponentRetainsNonzeroSharesAcrossAnExtremeCostRange() {
    final Abraham method = method("-0.001");
    final PathsForMode road = mode(1, 1e-200, 1e200);
    final PathsForMode rail = mode(2, 1e200);
    final List<PathsForMode> alternatives = List.of(road, rail);
    assertTrue(method.split(null, alternatives));
    // (10^200 / 10^-200)^-0.001 = 10^-0.4, even though that ratio overflows a double.
    final double expensiveWeight = Math.pow(10, -0.4);
    final double cheapShare = 1 / (1 + expensiveWeight);
    assertEquals(cheapShare, road.marketShare, 1e-12);
    assertEquals(1 - cheapShare, rail.marketShare, 1e-12);
    assertEquals(cheapShare * (1 - cheapShare), road.pathList.get(1).marketShare, 1e-12);
    assertConservedShares(alternatives);
  }

  private static Abraham method(String exponent) {
    Properties properties = new Properties();
    properties.setProperty(NodusC.VARNAME_ABRAHAM, exponent);
    return initialized(properties);
  }

  private static Abraham initialized(Properties properties) {
    final AssignmentParameters parameters = new AssignmentParameters(null);
    parameters.setCostFunctions(properties);
    Abraham method = new Abraham(null);
    method.initialize(parameters);
    method.initializeGroup(1);
    return method;
  }

  private static PathsForMode mode(int mode, double... costs) {
    PathsForMode alternatives = new PathsForMode(path(mode, costs[0]));
    for (int i = 1; i < costs.length; i++) {
      alternatives.addPath(path(mode, costs[i]));
    }
    return alternatives;
  }

  private static Path path(int mode, double cost) {
    Path path = new Path();
    path.loadingMode = (byte) mode;
    path.weights = new PathWeights();
    path.weights.mvCost = cost;
    return path;
  }

  private static void assertConservedShares(List<PathsForMode> alternatives) {
    double total = 0;
    for (PathsForMode mode : alternatives) {
      double withinMode = 0;
      for (Path path : mode.pathList) {
        assertTrue(Double.isFinite(path.marketShare));
        assertTrue(path.marketShare >= 0 && path.marketShare <= 1);
        withinMode += path.marketShare;
      }
      assertEquals(mode.marketShare, withinMode, 1e-12);
      total += withinMode;
    }
    assertEquals(1, total, 1e-12);
  }
}
