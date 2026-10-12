/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * Center for Operations Research and Econometrics (CORE)
 * http://www.uclouvain.be
 *
 * This file is part of Nodus.
 * Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ModalPivotDiagnosticsTest {
  private static LogCostChoiceEstimate behavior() {
    return new LogCostChoiceEstimate(new double[] {0, -1}, new double[] {1, 1},
        0, -1, -1, 1, 1, 1, false);
  }

  @Test
  void distributionsIncludeImplicitZerosAndWeightByObservedModalQuantityPerGroup() {
    List<ModalPivotEstimator.Row> rows = List.of(
        row(7, 1, new double[] {1, 1}, new double[] {100, 0}),
        row(7, 2, new double[] {1, 1}, new double[] {0, 100}),
        row(7, 3, new double[] {1, 1}, new double[] {10, 10}),
        row(7, 4, new double[] {1, Double.NaN}, new double[] {100, 0}),
        row(9, 1, new double[] {1, 1}, new double[] {10, 10}));
    Properties parameters = new Properties();
    ModalPivotEstimator.Result result = ModalPivotEstimator.estimateWithDiagnostics(
        rows, Map.of(7, behavior(), 9, behavior()), new int[] {1, 2}, 0,
        "MNL", 2, parameters, () -> true);
    ModalPivotEstimator.GroupDiagnostics group = result.groups.get(7);
    ModalPivotEstimator.Distribution distribution = group.distributions.get(2);
    assertEquals(4, distribution.values.size());
    assertEquals(3, distribution.available);
    assertEquals(2, distribution.stored);
    assertEquals(2, result.saved);
    assertEquals(0, distribution.median(false));
    assertEquals(2, distribution.median(true));
    assertEquals(25, distribution.boundPercentage(false, false));
    assertEquals(25, distribution.boundPercentage(true, false));
    assertEquals(0, distribution.boundPercentage(false, true));
    assertEquals(10000.0 / 110, distribution.boundPercentage(true, true), 1e-10);
    assertEquals(3, group.toleranceStops);
    assertEquals(0, group.iterationLimitStops);
    assertEquals(1, group.noAdjustableModes);
    assertEquals(2, group.boundBlockedCells);
    assertEquals(2, distribution.boundBlocked);
    assertTrue(group.maxRemainingLogRatio > 1);
    assertEquals(1, result.groups.get(9).cells);
    assertEquals(0, result.groups.get(9).distributions.get(2).median(true));
    assertFalse(group.distributions.containsKey(1));
    assertFalse(parameters.containsKey(ModalParameterTable.pivotKey(2, 3, 99, 7)));
    assertFalse(parameters.containsKey(ModalParameterTable.pivotKey(2, 4, 99, 7)));
    String html = ModalEstimationReport.pivots(group);
    assertTrue(html.contains("Pivot-constant distribution"));
    assertTrue(html.contains("Weighted by observed modal quantity"));
    assertTrue(html.contains("<th>% at −2.0</th>"));
    assertTrue(html.contains("<td>90.91</td>"));
    assertTrue(html.contains("<td>25.00</td>"));
    assertFalse(html.contains("K = "));
    assertTrue(html.contains("Pivot numerical completion"));
    assertFalse(html.contains("WAPE"));
  }

  @Test
  void identifiesIterationLimitSeparatelyFromSmallUpdatesAndHandlesZeroModalWeight() {
    ModalPivotEstimator.Result result = ModalPivotEstimator.estimateWithDiagnostics(
        List.of(row(7, 1, new double[] {1, 1}, new double[] {0, 100})),
        Map.of(7, behavior()), new int[] {1, 2}, 0, "MNL", 100,
        new Properties(), () -> true);
    ModalPivotEstimator.GroupDiagnostics group = result.groups.get(7);
    assertEquals(1, group.iterationLimitStops);
    assertEquals(0, group.toleranceStops);
    assertEquals(List.of(80), group.iterations);
    assertEquals(0, group.boundBlockedCells);
    assertTrue(group.maxFinalChange >= ModalPivotEstimator.UPDATE_TOLERANCE);
    result = ModalPivotEstimator.estimateWithDiagnostics(
        List.of(row(7, 1, new double[] {1, 1}, new double[] {100, 0})),
        Map.of(7, behavior()), new int[] {1, 2}, 0, "MNL", 2,
        new Properties(), () -> true);
    ModalPivotEstimator.Distribution distribution = result.groups.get(7).distributions.get(2);
    assertTrue(Double.isNaN(distribution.median(true)));
    assertTrue(Double.isNaN(distribution.boundPercentage(false, true)));
    assertTrue(ModalEstimationReport.pivots(result.groups.get(7)).contains("<td>N/A</td>"));
  }

  @Test
  void combinesClassesIntoOneOdGroupPivotCell() {
    ModalPivotEstimator.Result result = ModalPivotEstimator.estimateWithDiagnostics(
        List.of(row(7, 1, new double[] {1, 1}, new double[] {10, 0}),
            row(7, 1, new double[] {1, 1}, new double[] {0, 10})),
        Map.of(7, behavior()), new int[] {1, 2}, 0, "MNL", 2,
        new Properties(), () -> true);
    assertEquals(1, result.groups.get(7).cells);
    assertEquals(1, result.groups.get(7).distributions.get(2).values.size());
    assertEquals(0, result.saved);
  }

  @Test
  void negligibleFlowsDoNotReportAnUpdateThatTheAlgorithmSkips() {
    ModalPivotEstimator.Result result = ModalPivotEstimator.estimateWithDiagnostics(
        List.of(row(7, 1, new double[] {1, 1}, new double[] {1e-7, 0})),
        Map.of(7, behavior()), new int[] {1, 2}, 0, "MNL", 2,
        new Properties(), () -> true);
    assertEquals(0, result.saved);
    assertEquals(0, result.groups.get(7).maxRemainingLogRatio);
    assertEquals(1, result.groups.get(7).toleranceStops);
  }

  private static ModalPivotEstimator.Row row(int group, int origin, double[] costs,
      double[] quantities) {
    return new ModalPivotEstimator.Row(group, origin, 99, costs, quantities);
  }
}
