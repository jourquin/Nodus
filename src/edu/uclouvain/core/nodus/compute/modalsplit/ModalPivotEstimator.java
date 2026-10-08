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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;

/** Fits bounded residual utilities over OD and commodity cells after behavioral estimation. */
final class ModalPivotEstimator {
  private static final double DAMPING = 0.7;
  private static final double MAX_STEP = 2;
  private static final double EPSILON_TONNES = 1e-6;
  private static final int MAX_ITERATIONS = 80;

  static final class Row {
    final int group;
    final int origin;
    final int destination;
    final double[] costs;
    final double[] quantities;

    Row(int group, int origin, int destination, double[] costs, double[] quantities) {
      this.group = group;
      this.origin = origin;
      this.destination = destination;
      this.costs = costs;
      this.quantities = quantities;
    }
  }

  private ModalPivotEstimator() {}

  static int estimate(
      List<Row> rows,
      Map<Integer, LogCostChoiceEstimate> fitted,
      int[] modes,
      int reference,
      String method,
      double maxAbs,
      Properties output,
      BooleanSupplier proceed) {
    ModalParameterTable.validatePivotMaxAbs(maxAbs);
    Map<String, List<Row>> cells = new HashMap<>();
    for (Row row : rows) {
      cells
          .computeIfAbsent(
              row.group + ":" + row.origin + ":" + row.destination, ignored -> new ArrayList<>())
          .add(row);
    }
    int saved = 0;
    int checked = 0;
    for (List<Row> cell : cells.values()) {
      if (checked++ % 128 == 0
          && (!proceed.getAsBoolean() || Thread.currentThread().isInterrupted())) {
        throw new java.util.concurrent.CancellationException("Modal pivot estimation canceled");
      }
      Row first = cell.get(0);
      LogCostChoiceEstimate behavioral = fitted.get(first.group);
      double[] intercepts = behavioral.getIntercepts();
      double beta = behavioral.getCostCoefficient();
      double[] correction = new double[modes.length];
      double[] observed = new double[modes.length];
      for (Row row : cell) {
        for (int m = 0; m < modes.length; m++) {
          observed[m] += row.quantities[m];
        }
      }
      double epsilon = EPSILON_TONNES;
      for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
        double[] predicted = new double[modes.length];
        for (Row row : cell) {
          double[] utilities = new double[modes.length];
          for (int m = 0; m < modes.length; m++) {
            utilities[m] =
                Double.isFinite(row.costs[m]) && row.costs[m] > 0
                    ? intercepts[m] + beta * Math.log(row.costs[m]) + correction[m]
                    : Double.NEGATIVE_INFINITY;
          }
          double[] probability = probabilities(utilities, method);
          double quantity = 0;
          for (double value : row.quantities) {
            quantity += value;
          }
          for (int m = 0; m < modes.length; m++) {
            predicted[m] += quantity * probability[m];
          }
        }
        double largestChange = 0;
        for (int m = 0; m < modes.length; m++) {
          if (m == reference || !available(cell, m)) {
            continue;
          }
          double step = Math.log((observed[m] + epsilon) / (predicted[m] + epsilon));
          if (observed[m] <= epsilon && predicted[m] <= epsilon) {
            continue;
          }
          step = Math.max(-MAX_STEP, Math.min(MAX_STEP, step));
          double updated = Math.max(-maxAbs, Math.min(maxAbs, correction[m] + DAMPING * step));
          largestChange = Math.max(largestChange, Math.abs(updated - correction[m]));
          correction[m] = updated;
        }
        if (largestChange < 1e-5) {
          break;
        }
      }
      for (int m = 0; m < modes.length; m++) {
        if (m != reference && correction[m] != 0) {
          output.setProperty(
              ModalParameterTable.pivotKey(modes[m], first.origin, first.destination, first.group),
              Double.toString(correction[m]));
          saved++;
        }
      }
    }
    return saved;
  }

  private static boolean available(List<Row> cell, int column) {
    for (Row row : cell) {
      if (Double.isFinite(row.costs[column]) && row.costs[column] > 0) {
        return true;
      }
    }
    return false;
  }

  private static double[] probabilities(double[] utilities, String method) {
    if ("MNP".equals(method)) {
      int count = 0;
      for (double utility : utilities) {
        if (Double.isFinite(utility)) {
          count++;
        }
      }
      double[] active = new double[count];
      int[] indexes = new int[count];
      int next = 0;
      for (int m = 0; m < utilities.length; m++) {
        if (Double.isFinite(utilities[m])) {
          indexes[next] = m;
          active[next++] = utilities[m];
        }
      }
      double[] result = new double[utilities.length];
      if (count > 0) {
        double[] shares = ProbitProbabilities.probabilities(active);
        for (int m = 0; m < count; m++) {
          result[indexes[m]] = shares[m];
        }
      }
      return result;
    }
    double maximum = Double.NEGATIVE_INFINITY;
    for (double utility : utilities) {
      maximum = Math.max(maximum, utility);
    }
    double[] result = new double[utilities.length];
    double denominator = 0;
    for (int m = 0; m < utilities.length; m++) {
      if (Double.isFinite(utilities[m])) {
        result[m] = Math.exp(utilities[m] - maximum);
        denominator += result[m];
      }
    }
    for (int m = 0; m < result.length; m++) {
      result[m] /= denominator;
    }
    return result;
  }
}
