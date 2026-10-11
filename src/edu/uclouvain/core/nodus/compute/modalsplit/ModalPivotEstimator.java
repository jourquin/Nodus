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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Properties;
import java.util.function.BooleanSupplier;

/** Fits bounded residual utilities over OD and commodity cells after behavioral estimation. */
final class ModalPivotEstimator {
  private static final double DAMPING = 0.7;
  private static final double MAX_STEP = 2;
  private static final double EPSILON_TONNES = 1e-6;
  static final int MAX_ITERATIONS = 80;
  static final double UPDATE_TOLERANCE = 1e-5;

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
    return estimateWithDiagnostics(rows, fitted, modes, reference, method, maxAbs, output, proceed)
        .saved;
  }

  static Result estimateWithDiagnostics(
      List<Row> rows, Map<Integer, LogCostChoiceEstimate> fitted, int[] modes, int reference,
      String method, double maxAbs, Properties output, BooleanSupplier proceed) {
    ModalParameterTable.validatePivotMaxAbs(maxAbs);
    Map<String, List<Row>> cells = new HashMap<>();
    for (Row row : rows) {
      cells
          .computeIfAbsent(
              row.group + ":" + row.origin + ":" + row.destination, ignored -> new ArrayList<>())
          .add(row);
    }
    Result result = new Result();
    int checked = 0;
    for (List<Row> cell : cells.values()) {
      if (checked++ % 128 == 0
          && (!proceed.getAsBoolean() || Thread.currentThread().isInterrupted())) {
        throw new java.util.concurrent.CancellationException("Modal pivot estimation canceled");
      }
      Row first = cell.get(0);
      LogCostChoiceEstimate behavioral = fitted.get(first.group);
      double[] intercepts = behavioral.getIntercepts();
      double[] coefficients = behavioral.getCostCoefficients();
      double[] correction = new double[modes.length];
      double[] observed = new double[modes.length];
      for (Row row : cell) {
        for (int m = 0; m < modes.length; m++) {
          observed[m] += row.quantities[m];
        }
      }
      GroupDiagnostics diagnostics = result.groups.computeIfAbsent(first.group,
          ignored -> new GroupDiagnostics(maxAbs));
      diagnostics.cells++;
      boolean[] availableModes = new boolean[modes.length];
      boolean adjustable = false;
      for (int mode = 0; mode < modes.length; mode++) {
        availableModes[mode] = available(cell, mode);
        adjustable |= mode != reference && availableModes[mode];
      }
      boolean stoppedOnTolerance = false;
      int iterations = 0;
      double largestChange = 0;
      double epsilon = EPSILON_TONNES;
      for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
        double[] predicted = predict(cell, intercepts, coefficients, correction, method);
        largestChange = 0;
        iterations = iteration + 1;
        for (int m = 0; m < modes.length; m++) {
          if (m == reference || !availableModes[m]) {
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
        if (largestChange < UPDATE_TOLERANCE) {
          stoppedOnTolerance = true;
          break;
        }
      }
      if (!adjustable) {
        diagnostics.noAdjustableModes++;
      } else {
        diagnostics.iterations.add(iterations);
        if (stoppedOnTolerance) {
          diagnostics.toleranceStops++;
        } else {
          diagnostics.iterationLimitStops++;
        }
        diagnostics.maxFinalChange = Math.max(diagnostics.maxFinalChange, largestChange);
      }
      // Re-evaluate once with the final corrections, rather than the previous iteration's values.
      double[] finalPrediction = predict(cell, intercepts, coefficients, correction, method);
      boolean boundBlocked = false;
      for (int m = 0; m < modes.length; m++) {
        if (m == reference) {
          continue;
        }
        Distribution distribution = diagnostics.distributions.computeIfAbsent(modes[m],
            ignored -> new Distribution(maxAbs));
        distribution.values.add(new PivotValue(correction[m], observed[m]));
        if (availableModes[m]) {
          distribution.available++;
          double rawStep = observed[m] <= epsilon && finalPrediction[m] <= epsilon
              ? 0 : Math.log((observed[m] + epsilon) / (finalPrediction[m] + epsilon));
          diagnostics.maxRemainingLogRatio = Math.max(
              diagnostics.maxRemainingLogRatio, Math.abs(rawStep));
          if ((correction[m] == -maxAbs && rawStep < -UPDATE_TOLERANCE)
              || (correction[m] == maxAbs && rawStep > UPDATE_TOLERANCE)) {
            distribution.boundBlocked++;
            boundBlocked = true;
          }
        }
        if (correction[m] != 0) {
          output.setProperty(
              ModalParameterTable.pivotKey(modes[m], first.origin, first.destination, first.group),
              Double.toString(correction[m]));
          distribution.stored++;
          result.saved++;
        }
      }
      if (boundBlocked) {
        diagnostics.boundBlockedCells++;
      }
    }
    return result;
  }

  static final class Result {
    int saved;
    final Map<Integer, GroupDiagnostics> groups = new TreeMap<>();
  }

  /** Completion counts refer to OD/group cells, not to individual modal corrections. */
  static final class GroupDiagnostics {
    final double maxAbs;
    final Map<Integer, Distribution> distributions = new TreeMap<>();
    final List<Integer> iterations = new ArrayList<>();
    int cells;
    int noAdjustableModes;
    int toleranceStops;
    int iterationLimitStops;
    int boundBlockedCells;
    double maxFinalChange;
    double maxRemainingLogRatio;

    GroupDiagnostics(double maxAbs) {
      this.maxAbs = maxAbs;
    }
  }

  /** All retained mode/OD/group cells, including implicit zeros and inactive alternatives. */
  static final class Distribution {
    final double maxAbs;
    final List<PivotValue> values = new ArrayList<>();
    int available;
    int stored;
    int boundBlocked;

    Distribution(double maxAbs) {
      this.maxAbs = maxAbs;
    }

    double median(boolean weighted) {
      List<PivotValue> sorted = new ArrayList<>(values);
      sorted.sort(Comparator.comparingDouble(value -> value.correction));
      if (sorted.isEmpty()) {
        return Double.NaN;
      }
      if (!weighted) {
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
            ? (sorted.get(middle - 1).correction + sorted.get(middle).correction) / 2
            : sorted.get(middle).correction;
      }
      double total = sorted.stream().mapToDouble(value -> value.quantity).sum();
      if (total == 0) {
        return Double.NaN;
      }
      double cumulative = 0;
      double lastPositive = Double.NaN;
      for (PivotValue value : sorted) {
        if (value.quantity > 0) {
          lastPositive = value.correction;
          cumulative += value.quantity;
          if (cumulative >= total / 2) {
            return value.correction;
          }
        }
      }
      return lastPositive;
    }

    double boundPercentage(boolean upper, boolean weighted) {
      double total = 0;
      double atBound = 0;
      for (PivotValue value : values) {
        double weight = weighted ? value.quantity : 1;
        total += weight;
        if (value.correction == (upper ? maxAbs : -maxAbs)) {
          atBound += weight;
        }
      }
      return total == 0 ? Double.NaN : 100 * (atBound / total);
    }
  }

  static final class PivotValue {
    final double correction;
    final double quantity;

    PivotValue(double correction, double quantity) {
      this.correction = correction;
      this.quantity = quantity;
    }
  }

  private static double[] predict(List<Row> cell, double[] intercepts, double[] coefficients,
      double[] correction, String method) {
    double[] predicted = new double[correction.length];
    for (Row row : cell) {
      double[] utilities = new double[correction.length];
      for (int mode = 0; mode < correction.length; mode++) {
        utilities[mode] = Double.isFinite(row.costs[mode]) && row.costs[mode] > 0
            ? intercepts[mode] + coefficients[mode] * Math.log(row.costs[mode]) + correction[mode]
            : Double.NEGATIVE_INFINITY;
      }
      double[] probability = probabilities(utilities, method);
      double quantity = 0;
      for (double value : row.quantities) {
        quantity += value;
      }
      for (int mode = 0; mode < correction.length; mode++) {
        predicted[mode] += quantity * probability[mode];
      }
    }
    return predicted;
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
