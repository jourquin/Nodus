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

/** Independent analytical checks for fixed-exponent proportional calibration. */
class ProportionalEstimatorTest {
  @Test
  void equalCostsIdentifyFactorsAndMatchAnalyticalUncertainty() {
    ProportionalEstimator.Result result =
        ProportionalEstimator.estimate(new double[][] {{10, 10}}, new double[][] {{40, 20}});
    assertArrayEquals(new double[] {1, 2}, result.getCostFactors(), 1e-8);
    assertArrayEquals(
        new double[] {0, 2 * Math.sqrt(1.0 / 40 + 1.0 / 20)},
        result.getCostFactorStandardErrors(),
        1e-8);
    assertEquals(-1, result.getCostCoefficient());
    assertEquals(0, result.getCostStandardError());
    assertEquals(40 * Math.log(2.0 / 3) + 20 * Math.log(1.0 / 3), result.getLogLikelihood(), 1e-10);
    assertEquals(-60 * Math.log(2), result.getNullLogLikelihood(), 1e-10);
  }

  @Test
  void constantCostRatiosIdentifyFactorsAndReferenceRescalesThem() {
    double[][] costs = {{10, 20}, {20, 40}};
    double[][] quantities = {{20, 20}, {40, 40}};
    ProportionalEstimator.Result result = ProportionalEstimator.estimate(costs, quantities);
    assertArrayEquals(new double[] {1, 0.5}, result.getCostFactors(), 1e-8);
    ProportionalEstimator.Result other = ProportionalEstimator.estimate(costs, quantities, 1);
    assertArrayEquals(new double[] {2, 1}, other.getCostFactors(), 1e-8);
    assertEquals(result.getLogLikelihood(), other.getLogLikelihood(), 1e-10);
    assertEquals(
        60 * Math.log(2.0 / 3) + 60 * Math.log(1.0 / 3), result.getNullLogLikelihood(), 1e-10);
  }

  @Test
  void recoversThreeFactorsFromChangingAvailabilityIncludingAbsentReference() throws Exception {
    double[][] costs = {{1, 2, 3}, {2, 1, 3}, {3, 2, 1}, {1, Double.NaN, 2}, {0, 1, 3}, {2, 3, -1}};
    // k=[1,2,0.5], quantities proportional to 1/(k*C) within every row.
    double[][] quantities = {
      {1, 0.25, 2.0 / 3}, {0.5, 0.5, 2.0 / 3}, {1.0 / 3, 0.25, 2},
      {1, 0, 1}, {0, 0.5, 2.0 / 3}, {0.5, 1.0 / 6, 0}
    };
    ProportionalEstimator.Result result = ProportionalEstimator.estimate(costs, quantities);
    assertArrayEquals(new double[] {1, 2, 0.5}, result.getCostFactors(), 1e-8);
    Properties entries = new Properties();
    entries.load(new StringReader(result.toCostFileEntries(new int[] {1, 7, 9}, 3)));
    assertEquals(3, entries.size());
    assertEquals("1.0", entries.getProperty("proportional.costFactor.1.3"));
    assertEquals(0.5, Double.parseDouble(entries.getProperty("proportional.costFactor.9.3")), 1e-8);
    result.getCostFactors()[1] = 99;
    result.getCostFactorStandardErrors()[0] = 99;
    assertEquals(2, result.getCostFactors()[1], 1e-8);
    assertEquals(0, result.getCostFactorStandardErrors()[0]);
    assertThrows(
        IllegalArgumentException.class, () -> result.toCostFileEntries(new int[] {1, 1, 2}, 0));
  }

  @Test
  void scalingCostsOrFrequencyWeightsPreservesFactors() {
    ProportionalEstimator.Result original =
        ProportionalEstimator.estimate(new double[][] {{1, 2}}, new double[][] {{20, 20}});
    ProportionalEstimator.Result scaled =
        ProportionalEstimator.estimate(
            new double[][] {{1e100, 2e100}}, new double[][] {{2000, 2000}});
    assertArrayEquals(original.getCostFactors(), scaled.getCostFactors(), 1e-8);
    assertEquals(
        original.getCostFactorStandardErrors()[1],
        10 * scaled.getCostFactorStandardErrors()[1],
        1e-8);
    assertEquals(original.getLogLikelihood(), scaled.getLogLikelihood() / 100, 1e-8);
  }

  @Test
  void zeroChoiceCellsNeedNotCauseSeparationWithTheSlopeFixed() {
    ProportionalEstimator.Result result =
        ProportionalEstimator.estimate(
            new double[][] {{1, 2}, {2, 1}, {0, 0}}, new double[][] {{10, 0}, {0, 10}, {0, 0}});
    assertArrayEquals(new double[] {1, 1}, result.getCostFactors(), 1e-8);
    assertEquals(2, result.getObservations());
  }

  @Test
  void rejectsInvalidObservationsAndUnidentifiedOrInfiniteFactors() {
    assertThrows(IllegalArgumentException.class, () -> ProportionalEstimator.estimate(null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ProportionalEstimator.estimate(
                new double[][] {{1, Double.NaN}}, new double[][] {{1, 1}}));
    assertThrows(
        IllegalArgumentException.class,
        () -> ProportionalEstimator.estimate(new double[][] {{1, 1}}, new double[][] {{1, -1}}));
    assertThrows(
        IllegalStateException.class,
        () -> ProportionalEstimator.estimate(new double[][] {{1, 1}}, new double[][] {{1, 0}}));
    // Mode 2 is observed only when it is the sole available alternative: k2 tends to infinity.
    assertThrows(
        IllegalStateException.class,
        () ->
            ProportionalEstimator.estimate(
                new double[][] {{1, 1}, {0, 1}}, new double[][] {{10, 0}, {0, 10}}));
    assertThrows(
        IllegalStateException.class,
        () ->
            ProportionalEstimator.estimate(
                new double[][] {{1, 2, 0, 0}, {0, 0, 1, 2}},
                new double[][] {{1, 1, 0, 0}, {0, 0, 1, 1}}));
  }

  @Test
  void cancellationPropagatesFromInsideLikelihoodEvaluation() {
    AtomicInteger checks = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () ->
            ProportionalEstimator.estimate(
                new double[][] {{10, 10}},
                new double[][] {{40, 20}},
                0,
                () -> checks.incrementAndGet() < 4));
    assertTrue(checks.get() >= 4);
  }
}
