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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
  void logitSharesFollowCostDifferencesWithinAndBetweenModes() {
    PathsForMode road = mode(1, 10, 10 + Math.log(3));
    PathsForMode rail = mode(2, 10 + Math.log(3));
    List<PathsForMode> modes = Arrays.asList(road, rail);
    assertTrue(new MultinomialLogit(null).split(null, modes));
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
    assertTrue(new MultinomialLogit(null).split(null, modes));
    assertEquals(0.5, road.marketShare, 1e-12);
    assertEquals(0.5, rail.marketShare, 1e-12);
    assertEquals(0.25, road.pathList.get(0).marketShare, 1e-12);
    assertEquals(0.25, road.pathList.get(1).marketShare, 1e-12);
    assertSharesSumToOne(modes);
  }

  @Test
  void logitIgnoresNonFiniteAlternativesWhenAValidPathExists() {
    PathsForMode road = mode(1, 5, Double.POSITIVE_INFINITY, Double.NaN);
    List<PathsForMode> modes = Collections.singletonList(road);
    assertTrue(new MultinomialLogit(null).split(null, modes));
    assertEquals(1, road.pathList.get(0).marketShare, 1e-12);
    assertEquals(0, road.pathList.get(1).marketShare, 1e-12);
    assertEquals(0, road.pathList.get(2).marketShare, 1e-12);
    assertSharesSumToOne(modes);
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
