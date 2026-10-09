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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ModeSpecificChoiceEstimatorTest {
  private static final double[][] COSTS = {
    {10, 20, 30},
    {30, 15, 25},
    {20, 35, 12},
    {8, 12, 18},
    {50, 25, 15},
    {18, 14, 22},
    {1, Double.NaN, 2},
    {0, 1, 3},
    {2, 3, -1}
  };

  @Test
  void recoversIndependentBinaryLogitAndProbitExamples() {
    double[][] costs = {{1, 1}, {2, 1}, {1, 2}, {4, 2}};
    LogCostChoiceEstimate logit =
        fit(false, costs, new double[][] {{2, 3}, {1, 3}, {8, 3}, {2, 3}}, 0, false);
    assertArrayEquals(new double[] {0, Math.log(1.5)}, logit.getIntercepts(), 1e-8);
    assertArrayEquals(new double[] {-1, -2}, logit.getCostCoefficients(), 1e-8);
    // Independent Phi(-1), Phi(0), Phi(2) values, with utility error difference variance 2.
    LogCostChoiceEstimate probit =
        fit(
            true,
            costs,
            new double[][] {
              {50, 50},
              {15.86552539314571, 84.13447460685429},
              {97.72498680518208, 2.27501319481792},
              {50, 50}
            },
            0,
            false);
    assertArrayEquals(new double[] {0, 0}, probit.getIntercepts(), 1e-8);
    double beta = -Math.sqrt(2) / Math.log(2);
    assertArrayEquals(new double[] {beta, 2 * beta}, probit.getCostCoefficients(), 1e-8);
  }

  @Test
  void recoversModeSpecificCoefficientsAndReferenceChangesWithUnavailableModes() throws Exception {
    double[] alpha = {0, 0.4, -0.2};
    double[] beta = {-0.7, -1.3, -2};
    for (boolean probit : new boolean[] {false, true}) {
      double[][] quantities = quantities(probit, alpha, beta);
      LogCostChoiceEstimate result = fit(probit, COSTS, quantities, 0, false);
      assertFalse(result.isConditional());
      assertArrayEquals(alpha, result.getIntercepts(), 2e-7);
      assertArrayEquals(beta, result.getCostCoefficients(), 2e-7);
      assertThrows(IllegalStateException.class, result::getCostCoefficient);
      assertThrows(IllegalStateException.class, result::getCostStandardError);
      assertTrue(Arrays.stream(result.getCostStandardErrors()).allMatch(e -> e > 0));
      LogCostChoiceEstimate other = fit(probit, COSTS, quantities, 2, false);
      assertArrayEquals(new double[] {0.2, 0.6, 0}, other.getIntercepts(), 2e-7);
      assertArrayEquals(beta, other.getCostCoefficients(), 2e-7);
      assertEquals(result.getLogLikelihood(), other.getLogLikelihood(), 1e-8);
      result.getCostCoefficients()[0] = 10;
      result.getCostStandardErrors()[0] = -10;
      assertEquals(beta[0], result.getCostCoefficients()[0], 2e-7);
      assertTrue(result.getCostStandardErrors()[0] > 0);
      Properties entries = new Properties();
      entries.load(new StringReader(result.toCostFileEntries(new int[] {1, 4, 9}, 7)));
      for (int mode = 0; mode < 3; mode++) {
        assertEquals(
            beta[mode],
            Double.parseDouble(
                entries.getProperty(
                    (probit ? "probit." : "") + "log(cost)." + new int[] {1, 4, 9}[mode] + ".7")),
            2e-7);
      }
      double[][] scaled =
          Arrays.stream(COSTS)
              .map(row -> Arrays.stream(row).map(cost -> cost > 0 ? cost * 1e100 : cost).toArray())
              .toArray(double[][]::new);
      LogCostChoiceEstimate changedUnits = fit(probit, scaled, quantities, 0, false);
      assertArrayEquals(beta, changedUnits.getCostCoefficients(), 2e-7);
      assertArrayEquals(result.getCostStandardErrors(), changedUnits.getCostStandardErrors(), 2e-7);
      assertEquals(result.getLogLikelihood(), changedUnits.getLogLikelihood(), 1e-8);
      for (int mode = 0; mode < 3; mode++) {
        assertEquals(
            alpha[mode] + (beta[0] - beta[mode]) * Math.log(1e100),
            changedUnits.getIntercepts()[mode],
            1e-5);
      }
    }
  }

  @Test
  void rejectsZeroAndPositiveCoefficientsWithoutClampingThem() {
    for (boolean probit : new boolean[] {false, true}) {
      for (double invalid : new double[] {0, 0.5}) {
        NonNegativeCostCoefficientException failure =
            assertThrows(
                NonNegativeCostCoefficientException.class,
                () ->
                    fit(
                        probit,
                        COSTS,
                        quantities(
                            probit, new double[] {0, 0.4, -0.2}, new double[] {-0.7, invalid, -2}),
                        0,
                        false));
        assertArrayEquals(new int[] {1}, failure.getModeColumns());
        assertTrue(failure.getMessage().contains("zero or positive"));
        assertTrue(
            NonNegativeCostCoefficientException.causedBy(
                new IllegalArgumentException("group failed", failure)));
      }
      for (double coefficient : new double[] {0, 1}) {
        double[][] quantities =
            quantities(
                probit,
                new double[] {0, 0.4, -0.2},
                new double[] {coefficient, coefficient, coefficient});
        assertThrows(
            NonNegativeCostCoefficientException.class,
            () -> fit(probit, COSTS, quantities, 0, true));
      }
    }
  }

  @Test
  void rejectsUnidentifiedModeSpecificSlopesAndSupportsCancellation() {
    for (boolean probit : new boolean[] {false, true}) {
      assertThrows(
          IllegalStateException.class,
          () ->
              fit(
                  probit,
                  new double[][] {{1, 2}, {2, 4}, {3, 6}},
                  new double[][] {{1, 1}, {1, 1}, {1, 1}},
                  0,
                  false));
      assertThrows(
          java.util.concurrent.CancellationException.class,
          () -> {
            double[][] quantities =
                quantities(probit, new double[] {0, 0.4, -0.2}, new double[] {-0.7, -1.3, -2});
            if (probit) {
              LogCostProbitEstimator.estimate(COSTS, quantities, 0, false, () -> false);
            } else {
              LogCostLogitEstimator.estimate(COSTS, quantities, 0, false, () -> false);
            }
          });
    }
  }

  @Test
  void pivotsUseTheModeSpecificBehavioralCoefficients() {
    for (boolean probit : new boolean[] {false, true}) {
      double[][] quantities =
          quantities(probit, new double[] {0, 0.4, -0.2}, new double[] {-0.7, -1.3, -2});
      LogCostChoiceEstimate result = fit(probit, COSTS, quantities, 0, false);
      Properties entries = new Properties();
      ModalPivotEstimator.estimate(
          List.of(new ModalPivotEstimator.Row(7, 1, 2, COSTS[0], quantities[0])),
          Map.of(7, result),
          new int[] {1, 4, 9},
          0,
          probit ? "MNP" : "MNL",
          8,
          entries,
          () -> true);
      for (String key : entries.stringPropertyNames()) {
        assertEquals(0, Double.parseDouble(entries.getProperty(key)), 1e-6);
      }
    }
  }

  @Test
  void formulaDisplaysTheSelectedCostSpecification() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(
        () -> {
          ModalChoiceFormula formula = new ModalChoiceFormula();
          for (String method : new String[] {"MNL", "MNP"}) {
            formula.setMethod(method, true, 4, false);
            assertTrue(formula.getText().contains("&#946;<sub>i</sub>"));
            assertTrue(formula.getText().contains("&#948;<sub>4,o,d,g</sub> = 0"));
            formula.setMethod(method, false, 4, true);
            assertFalse(formula.getText().contains("&#946;<sub>i</sub>"));
          }
          formula.setMethod("Proportional", false, 4, false);
          assertFalse(formula.getText().contains("&#946;"));
        });
  }

  private static LogCostChoiceEstimate fit(
      boolean probit, double[][] costs, double[][] quantities, int reference, boolean conditional) {
    return probit
        ? LogCostProbitEstimator.estimate(costs, quantities, reference, conditional, () -> true)
        : LogCostLogitEstimator.estimate(costs, quantities, reference, conditional, () -> true);
  }

  private static double[][] quantities(boolean probit, double[] alpha, double[] beta) {
    double[][] result = new double[COSTS.length][3];
    for (int row = 0; row < COSTS.length; row++) {
      double[] utility = new double[(int) Arrays.stream(COSTS[row]).filter(c -> c > 0).count()];
      int column = 0;
      for (int mode = 0; mode < 3; mode++) {
        if (COSTS[row][mode] > 0) {
          utility[column++] = alpha[mode] + beta[mode] * Math.log(COSTS[row][mode]);
        }
      }
      double[] probabilities;
      if (probit) {
        probabilities = ProbitProbabilities.probabilities(utility);
      } else {
        double denominator = Arrays.stream(utility).map(Math::exp).sum();
        probabilities = Arrays.stream(utility).map(u -> Math.exp(u) / denominator).toArray();
      }
      column = 0;
      for (int mode = 0; mode < 3; mode++) {
        if (COSTS[row][mode] > 0) {
          result[row][mode] = 100 * probabilities[column++];
        }
      }
    }
    return result;
  }
}
