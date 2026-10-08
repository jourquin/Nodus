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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Recovery, identification and independently fitted probit reference values. */
class LogCostProbitEstimatorTest {
  private static final double[][] COSTS = {
    {10, 20, 30}, {30, 15, 25}, {20, 35, 12}, {8, 12, 18}, {50, 25, 15}, {18, 14, 22}
  };
  private static final double[][] QUANTITIES = {
    {40, 15, 5}, {8, 30, 12}, {12, 6, 32}, {50, 25, 10}, {4, 16, 30}, {20, 22, 8}
  };

  @Test
  void matchesIndependentScipyWeightedLikelihoodAndInformation() {
    // scipy quad + BFGS and central numerical Hessian; independent of Java quadrature/derivatives.
    LogCostChoiceEstimate result = LogCostProbitEstimator.estimate(COSTS, QUANTITIES);
    assertArrayEquals(new double[] {0, 0.01188595, -0.10095402}, result.getIntercepts(), 2e-6);
    assertEquals(-1.34714429, result.getCostCoefficient(), 2e-6);
    assertEquals(-315.3265183859541, result.getLogLikelihood(), 1e-8);
    assertArrayEquals(
        new double[] {0, 0.10961883, 0.11730755}, result.getInterceptStandardErrors(), 2e-7);
    assertEquals(0.12968813, result.getCostStandardError(), 2e-7);
    assertEquals(-345 * Math.log(3), result.getNullLogLikelihood(), 1e-10);
  }

  @Test
  void recoversKnownBinaryModelAndHandlesUnavailableAlternatives() {
    // Phi(0), Phi(1), Phi(2), plus a singleton and an empty row.
    double[][] costs = {{1, 1}, {1, 2}, {1, 4}, {Double.NaN, 5}, {0, 0}};
    double[][] quantities = {
      {50, 50},
      {84.13447460685429, 15.86552539314571},
      {97.72498680518208, 2.27501319481792},
      {0, 27},
      {0, 0}
    };
    LogCostChoiceEstimate fit = LogCostProbitEstimator.estimate(costs, quantities);
    assertArrayEquals(new double[] {0, 0}, fit.getIntercepts(), 1e-8);
    assertEquals(-Math.sqrt(2) / Math.log(2), fit.getCostCoefficient(), 1e-8);
    assertEquals(4, fit.getObservations());
    assertEquals(327, fit.getTotalQuantity());
  }

  @Test
  void recoversThreeModeModelWithChangingAvailability() {
    double[][] costs = {{1, 2, 3}, {3, 1, 2}, {2, 3, 1}, {1, Double.NaN, 4}, {0, 1, 2}};
    double[] alpha = {0, 0.4, -0.3};
    double[][] quantities = new double[costs.length][3];
    for (int row = 0; row < costs.length; row++) {
      int count = 0;
      for (double cost : costs[row]) {
        if (cost > 0) {
          count++;
        }
      }
      double[] utility = new double[count];
      int column = 0;
      for (int mode = 0; mode < 3; mode++) {
        if (costs[row][mode] > 0) {
          utility[column++] = alpha[mode] - 1.2 * Math.log(costs[row][mode]);
        }
      }
      double[] probabilities = ProbitProbabilities.probabilities(utility);
      column = 0;
      for (int mode = 0; mode < 3; mode++) {
        if (costs[row][mode] > 0) {
          quantities[row][mode] = 100 * probabilities[column++];
        }
      }
    }
    LogCostChoiceEstimate fit = LogCostProbitEstimator.estimate(costs, quantities);
    assertArrayEquals(alpha, fit.getIntercepts(), 1e-8);
    assertEquals(-1.2, fit.getCostCoefficient(), 1e-8);
    LogCostChoiceEstimate other = LogCostProbitEstimator.estimate(costs, quantities, 2);
    assertArrayEquals(new double[] {0.3, 0.7, 0}, other.getIntercepts(), 1e-8);
    assertEquals(fit.getLogLikelihood(), other.getLogLikelihood(), 1e-10);
  }

  @Test
  void costAndWeightScalingPreservesTheFitAndExportsOnlyProbitKeys() throws Exception {
    LogCostChoiceEstimate fit = LogCostProbitEstimator.estimate(COSTS, QUANTITIES);
    double[][] costs = new double[COSTS.length][];
    double[][] quantities = new double[COSTS.length][];
    for (int row = 0; row < COSTS.length; row++) {
      costs[row] = COSTS[row].clone();
      quantities[row] = QUANTITIES[row].clone();
      for (int mode = 0; mode < 3; mode++) {
        costs[row][mode] *= 1e100;
        quantities[row][mode] *= 100;
      }
    }
    LogCostChoiceEstimate scaled = LogCostProbitEstimator.estimate(costs, quantities);
    assertArrayEquals(fit.getIntercepts(), scaled.getIntercepts(), 1e-8);
    assertEquals(fit.getCostCoefficient(), scaled.getCostCoefficient(), 1e-8);
    assertEquals(fit.getCostStandardError(), 10 * scaled.getCostStandardError(), 1e-8);
    Properties entries = new Properties();
    entries.load(new StringReader(fit.toCostFileEntries(new int[] {1, 4, 9}, 7)));
    assertEquals(6, entries.size());
    assertTrue(entries.stringPropertyNames().stream().allMatch(key -> key.startsWith("probit.")));
    assertEquals("0.0", entries.getProperty("probit.(intercept).1.7"));
  }

  @Test
  void rejectsImpossibleObservationsAndUnidentifiedModelsAndSupportsCancellation() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LogCostProbitEstimator.estimate(
                new double[][] {{1, Double.NaN}}, new double[][] {{1, 1}}));
    assertThrows(
        IllegalStateException.class,
        () ->
            LogCostProbitEstimator.estimate(
                new double[][] {{1, 2}, {1, 4}}, new double[][] {{1, 0}, {1, 0}}));
    assertThrows(
        IllegalStateException.class,
        () ->
            LogCostProbitEstimator.estimate(
                new double[][] {{1, 2}, {2, 4}}, new double[][] {{1, 1}, {1, 1}}));
    AtomicInteger calls = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () ->
            LogCostProbitEstimator.estimate(
                COSTS, QUANTITIES, 0, () -> calls.incrementAndGet() < 5));
  }
}
