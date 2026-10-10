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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class LogCostLogitEstimatorTest {
  private static final double[][] COSTS = {
    {10, 20, 30}, {30, 15, 25}, {20, 35, 12}, {8, 12, 18}, {50, 25, 15}, {18, 14, 22}
  };
  private static final double[][] QUANTITIES = {
    {40, 15, 5}, {8, 30, 12}, {12, 6, 32}, {50, 25, 10}, {4, 16, 30}, {20, 22, 8}
  };

  @Test
  void matchesIndependentRWeightedLikelihood() {
    LogCostLogitEstimator.Result result = LogCostLogitEstimator.estimate(COSTS, QUANTITIES);
    // R mlogit with formula mode ~ log(cost) | 1 | 1, weights=tons; see fixture notes in README.
    assertArrayEquals(
        new double[] {0, 0.030331521099273, -0.137361127842608}, result.getIntercepts(), 2e-8);
    assertEquals(-1.723605031702693, result.getCostCoefficient(), 2e-8);
    assertEquals(-315.4739273229093, result.getLogLikelihood(), 1e-8);
    // Independent R optim numerical Hessian, with ORIGINAL frequency weights (sum=345).
    assertArrayEquals(
        new double[] {0, 0.139371197, 0.153995484}, result.getInterceptStandardErrors(), 2e-7);
    assertEquals(0.170582132, result.getCostStandardError(), 2e-7);
    assertEquals(-345 * Math.log(3), result.getNullLogLikelihood(), 1e-10);
    assertEquals(345, result.getTotalQuantity());
    assertEquals(6, result.getObservations());
    assertTrue(result.getIterations() > 0 && result.getIterations() < 200);
  }

  @Test
  void recoversAnExactlyKnownBinaryModel() {
    // Odds for mode 2 are 3/2 and 3/8: alpha=log(1.5), beta=-2 with costs 1 and 2.
    double[][] costs = {{1, 1}, {1, 2}};
    double[][] quantities = {{2, 3}, {8, 3}};
    LogCostLogitEstimator.Result result = LogCostLogitEstimator.estimate(costs, quantities);
    assertEquals(Math.log(1.5), result.getIntercepts()[1], 1e-8);
    assertEquals(-2, result.getCostCoefficient(), 1e-8);
  }

  @Test
  void fitsAZeroBoundWhenCostDoesNotReduceDemand() {
    double[][] costs = {{1, 1}, {1, 2}};
    LogCostLogitEstimator.Result flat =
        LogCostLogitEstimator.estimate(costs, new double[][] {{1, 1}, {1, 1}});
    assertEquals(0, flat.getCostCoefficient(), 1e-8);
    assertEquals(0, flat.getIntercepts()[1], 1e-8);

    LogCostLogitEstimator.Result increasing =
        LogCostLogitEstimator.estimate(costs, new double[][] {{1, 1}, {1, 2}});
    assertEquals(0, increasing.getCostCoefficient(), 1e-8);
    assertEquals(Math.log(1.5), increasing.getIntercepts()[1], 1e-8);
  }

  @Test
  void recoversSharedSlopeWithUnavailableModesAndFractionalFlows() {
    // With beta=-1 and exp(intercepts)=[1,2,3], unnormalised shares are [1/c1,2/c2,3/c3].
    double[][] costs = {{1, 2, 3}, {2, 1, 3}, {3, 2, 1}, {1, Double.NaN, 2}, {0, 1, 3}, {2, 3, -1}};
    double[][] quantities = {
      {1, 1, 1}, {0.5, 2, 1}, {1.0 / 3, 1, 3}, {1, 0, 1.5}, {0, 2, 1}, {0.5, 2.0 / 3, 0}
    };
    LogCostLogitEstimator.Result result = LogCostLogitEstimator.estimate(costs, quantities);
    assertArrayEquals(new double[] {0, Math.log(2), Math.log(3)}, result.getIntercepts(), 1e-8);
    assertEquals(-1, result.getCostCoefficient(), 1e-8);
    // A different reference changes only the utility origin.
    LogCostLogitEstimator.Result other = LogCostLogitEstimator.estimate(costs, quantities, 2);
    assertArrayEquals(
        new double[] {-Math.log(3), Math.log(2.0 / 3), 0}, other.getIntercepts(), 1e-8);
    assertEquals(-1, other.getCostCoefficient(), 1e-8);
    assertEquals(result.getLogLikelihood(), other.getLogLikelihood(), 1e-10);
  }

  @Test
  void scalingCostsAndFlowsPreservesCoefficientsButRescalesUncertainty() {
    LogCostLogitEstimator.Result original = LogCostLogitEstimator.estimate(COSTS, QUANTITIES);
    for (double factor : new double[] {1e-8, 1000, 1e8}) {
      LogCostLogitEstimator.Result scaled =
          LogCostLogitEstimator.estimate(scale(COSTS, 1e100), scale(QUANTITIES, factor));
      assertArrayEquals(original.getIntercepts(), scaled.getIntercepts(), 1e-8);
      assertEquals(original.getCostCoefficient(), scaled.getCostCoefficient(), 1e-8);
      assertEquals(original.getLogLikelihood(), scaled.getLogLikelihood() / factor, 1e-8);
      assertEquals(
          original.getCostStandardError(), scaled.getCostStandardError() * Math.sqrt(factor), 1e-8);
    }
  }

  @Test
  void estimatesRareButNonzeroChoicesWithoutMistakingThemForSeparation() {
    LogCostLogitEstimator.Result result =
        LogCostLogitEstimator.estimate(
            new double[][] {{1, 1}, {1, 2}}, new double[][] {{1, 1e-20}, {1, 1e-30}});
    assertEquals(Math.log(1e-20), result.getIntercepts()[1], 1e-7);
    assertEquals(Math.log(1e-10) / Math.log(2), result.getCostCoefficient(), 1e-7);
  }

  @Test
  void supportsExtremeFiniteCostsWithoutFormingOverflowingCostRatios() {
    double high = Math.pow(10, 0.3);
    double low = Math.pow(10, -0.3);
    LogCostLogitEstimator.Result result =
        LogCostLogitEstimator.estimate(
            new double[][] {{1e-300, 1e300, 1}, {1e300, 1e-300, 1}, {1, 1, 1e-300}},
            new double[][] {{high, 2 * low, 3}, {low, 2 * high, 3}, {1, 2, 3 * high}});
    assertArrayEquals(new double[] {0, Math.log(2), Math.log(3)}, result.getIntercepts(), 1e-8);
    assertEquals(-0.001, result.getCostCoefficient(), 1e-10);
  }

  @Test
  void acceptsZeroCellsAndIgnoresEmptyRows() {
    double[][] quantities = {{0, 10, 20}, {30, 0, 10}, {10, 20, 0}};
    double[][] costs = {{3, 2, 1}, {1, 3, 2}, {2, 1, 3}};
    LogCostLogitEstimator.Result original = LogCostLogitEstimator.estimate(costs, quantities);
    double[][] extendedCosts = {costs[0], costs[1], costs[2], {Double.NaN, 0, -1}};
    double[][] extendedQuantities = {quantities[0], quantities[1], quantities[2], {0, 0, 0}};
    LogCostLogitEstimator.Result extended =
        LogCostLogitEstimator.estimate(extendedCosts, extendedQuantities);
    assertEquals(3, extended.getObservations());
    assertArrayEquals(original.getIntercepts(), extended.getIntercepts(), 1e-12);
    assertEquals(original.getCostCoefficient(), extended.getCostCoefficient(), 1e-12);
  }

  @Test
  void exportsAllModesWithAnExplicitReferenceAndDoesNotExposeMutableArrays() throws Exception {
    LogCostLogitEstimator.Result result = LogCostLogitEstimator.estimate(COSTS, QUANTITIES);
    result.getIntercepts()[1] = 100;
    result.getInterceptStandardErrors()[1] = 100;
    Properties entries = new Properties();
    entries.load(new StringReader(result.toCostFileEntries(new int[] {1, 4, 9}, 7)));
    assertEquals(6, entries.size());
    assertEquals("0.0", entries.getProperty("(intercept).1.7"));
    assertEquals(
        result.getIntercepts()[1], Double.parseDouble(entries.getProperty("(intercept).4.7")));
    assertTrue(result.getInterceptStandardErrors()[1] < 1);
    for (int mode : new int[] {1, 4, 9}) {
      assertEquals(
          result.getCostCoefficient(),
          Double.parseDouble(entries.getProperty("log(cost)." + mode + ".7")));
    }
    assertThrows(
        IllegalArgumentException.class, () -> result.toCostFileEntries(new int[] {1, 1, 3}, 0));
    assertThrows(
        IllegalArgumentException.class, () -> result.toCostFileEntries(new int[] {0, 2, 3}, 0));
    assertThrows(
        IllegalArgumentException.class, () -> result.toCostFileEntries(new int[] {1, 2}, 0));
  }

  @Test
  void rejectsInvalidInput() {
    assertThrows(
        IllegalArgumentException.class, () -> LogCostLogitEstimator.estimate(null, QUANTITIES));
    assertThrows(
        IllegalArgumentException.class,
        () -> LogCostLogitEstimator.estimate(new double[0][], new double[0][]));
    assertThrows(
        IllegalArgumentException.class, () -> LogCostLogitEstimator.estimate(COSTS, QUANTITIES, 3));
    assertThrows(
        IllegalArgumentException.class,
        () -> LogCostLogitEstimator.estimate(new double[][] {{1}}, new double[][] {{1}}));
    assertThrows(
        IllegalArgumentException.class,
        () -> LogCostLogitEstimator.estimate(COSTS, new double[][] {{1, 1}}));
    for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> LogCostLogitEstimator.estimate(new double[][] {{1, 2}}, new double[][] {{1, bad}}));
    }
    for (double unavailable : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              LogCostLogitEstimator.estimate(
                  new double[][] {{1, unavailable}}, new double[][] {{1, 1}}));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> LogCostLogitEstimator.estimate(new double[][] {{1, 2}}, new double[][] {{0, 0}}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LogCostLogitEstimator.estimate(
                new double[][] {{1, 2}}, new double[][] {{Double.MAX_VALUE, Double.MAX_VALUE}}));
  }

  @Test
  void rejectsUnidentifiedModelsAndSeparation() {
    // Identical costs within each row, or a constant cost ratio confounded with intercepts.
    for (double[][] costs : new double[][][] {{{1, 1}, {2, 2}}, {{1, 2}, {2, 4}}}) {
      assertThrows(
          IllegalStateException.class,
          () -> LogCostLogitEstimator.estimate(costs, new double[][] {{1, 1}, {1, 1}}));
    }
    assertThrows(
        IllegalStateException.class,
        () ->
            LogCostLogitEstimator.estimate(
                new double[][] {{1, 2}, {2, 1}}, new double[][] {{1, 0}, {1, 0}}));
    // Both modes are chosen, but the cheaper one always wins: no finite maximum.
    assertThrows(
        IllegalStateException.class,
        () ->
            LogCostLogitEstimator.estimate(
                new double[][] {{1, 2}, {2, 1}}, new double[][] {{1, 0}, {0, 1}}));
    // Two disconnected choice sets cannot identify all relative intercepts.
    assertThrows(
        IllegalStateException.class,
        () ->
            LogCostLogitEstimator.estimate(
                new double[][] {{1, 2, 0, 0}, {2, 1, 0, 0}, {0, 0, 1, 2}, {0, 0, 2, 1}},
                new double[][] {{1, 2, 0, 0}, {2, 1, 0, 0}, {0, 0, 1, 2}, {0, 0, 2, 1}}));
  }

  private static double[][] scale(double[][] values, double factor) {
    double[][] result = new double[values.length][];
    for (int i = 0; i < values.length; i++) {
      result[i] = values[i].clone();
      for (int j = 0; j < result[i].length; j++) {
        result[i][j] *= factor;
      }
    }
    return result;
  }
}
