/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Independent normal-integral references and checks on likelihood derivatives. */
class ProbitProbabilitiesTest {
  @Test
  void binaryCaseUsesTheVarianceOfTheDifferenceAndRetainsRareChoices() {
    assertArrayEquals(
        new double[] {0.8413447460685429, 0.1586552539314571},
        ProbitProbabilities.probabilities(new double[] {Math.sqrt(2), 0}),
        2e-14);
    assertEquals(
        -27.89403672609738,
        ProbitProbabilities.evaluate(new double[] {-10, 0}, 0).logProbability,
        1e-11);
    ProbitProbabilities.Evaluation rare = ProbitProbabilities.evaluate(new double[] {-100, 0}, 0);
    assertTrue(Double.isFinite(rare.logProbability));
    assertTrue(rare.logProbability < -2500);
    assertTrue(rare.score[0] > 50);
    assertEquals(-0.5, rare.hessian[0][0], 0.0002);
  }

  @Test
  void matchesIndependentScipyQuadNormalIntegrals() {
    // scipy.integrate.quad of normal PDF times the product of normal CDFs, relative tolerance
    // 1e-12, with a smaller absolute tolerance for rare choices.
    assertArrayEquals(
        new double[] {0.2566057850424605, 0.5975752718063184, 0.14581894315122107},
        ProbitProbabilities.probabilities(new double[] {0, 0.7, -0.4}),
        2e-12);
    assertArrayEquals(
        new double[] {8.066932511640977e-5, 0.0786284072100317, 0.9212909234648521},
        ProbitProbabilities.probabilities(new double[] {-3, 0, 2}),
        2e-12);
    assertEquals(
        Math.log(7.602444443811362e-21),
        ProbitProbabilities.evaluate(new double[] {0, -10, 2}, 1).logProbability,
        1e-8);
  }

  @Test
  void probabilitiesAreSymmetricAndInvariantToUtilityShiftsAndOrdering() {
    for (int size : new int[] {1, 2, 3, 10, 80}) {
      double[] equal = ProbitProbabilities.probabilities(new double[size]);
      for (double probability : equal) {
        assertEquals(1.0 / size, probability, 1e-12);
      }
    }
    double[] original = ProbitProbabilities.probabilities(new double[] {0, 0.7, -0.4});
    assertArrayEquals(
        original, ProbitProbabilities.probabilities(new double[] {100, 100.7, 99.6}), 1e-13);
    double[] reordered = ProbitProbabilities.probabilities(new double[] {-0.4, 0, 0.7});
    assertArrayEquals(new double[] {original[2], original[0], original[1]}, reordered, 1e-13);
  }

  @Test
  void underflowingAlternativesDoNotPreventComputingTheRemainingShares() {
    double[] binary = ProbitProbabilities.probabilities(new double[] {-1, -2});
    assertArrayEquals(
        new double[] {binary[0], 0, binary[1]},
        ProbitProbabilities.probabilities(new double[] {-1, -1e9, -2}),
        1e-14);
    assertArrayEquals(
        new double[] {0, 0.5, 0.5},
        ProbitProbabilities.probabilities(
            new double[] {-Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}),
        1e-14);
    assertArrayEquals(
        new double[] {1, 0, 0},
        ProbitProbabilities.probabilities(new double[] {-10, -1000, -2000}),
        1e-14);
  }

  @Test
  void scoreAndHessianAgreeWithFiniteDifferencesIncludingRareChoices() {
    for (double[] utilities : new double[][] {{0, 0.7, -0.4}, {0, -10, 2}, {-20, 0}}) {
      for (int chosen = 0; chosen < utilities.length; chosen++) {
        ProbitProbabilities.Evaluation value = ProbitProbabilities.evaluate(utilities, chosen);
        double step = 1e-4;
        for (int j = 0; j < utilities.length; j++) {
          double[] plus = utilities.clone();
          double[] minus = utilities.clone();
          plus[j] += step;
          minus[j] -= step;
          ProbitProbabilities.Evaluation high = ProbitProbabilities.evaluate(plus, chosen);
          ProbitProbabilities.Evaluation low = ProbitProbabilities.evaluate(minus, chosen);
          assertEquals(
              (high.logProbability - low.logProbability) / (2 * step), value.score[j], 2e-8);
          for (int k = 0; k < utilities.length; k++) {
            assertEquals((high.score[k] - low.score[k]) / (2 * step), value.hessian[j][k], 2e-8);
          }
        }
        assertEquals(0, Arrays.stream(value.score).sum(), 1e-12);
      }
    }
  }
}
