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

import java.util.ArrayList;
import java.util.List;

/**
 * Shared numerical engine for quantity-weighted logit, independent-error probit and proportional
 * fits.
 *
 * <p>Each call fits one group using equally shaped {@code [OD row][mode column]} arrays. Positive
 * finite costs identify available modes; NaN and nonpositive finite costs identify unavailable
 * modes, which must have zero observed quantity. Infinite costs, negative/nonfinite quantities and
 * inconsistent dimensions are rejected. Zero-total rows are ignored. The reference is a column
 * index, not a Nodus mode ID. Inputs are read without modification.
 *
 * <p>The free parameter vector contains the nonreference intercepts in column order, followed by a
 * common log-cost coefficient or M mode-specific coefficients for logit/probit. Within each OD,
 * features are centered on the first available alternative; common utility shifts leave
 * probabilities unchanged. Log-cost features are scaled for optimization and
 * coefficients/uncertainty are transformed back before returning. Proportional choice instead fixes
 * the cost coefficient at -1 and puts the centered log cost in a utility offset, so its optimizer
 * has only the intercept parameters. Mode-specific log costs are also centered per mode, then
 * intercepts and their covariance are transformed back to the original cost units. Fitting starts
 * with negative slopes and rejects converged slopes that are nonnegative or zero at solver
 * precision.
 *
 * <p>The objective is negative log-likelihood divided by total quantity. Damped Newton steps use
 * analytic softmax derivatives or {@link ProbitProbabilities} derivatives. The information matrix's
 * lower triangle is accumulated and solved by a diagonally scaled Cholesky factorization. A line
 * search controls the step; convergence requires a small relative Newton step, not merely a small
 * change in likelihood. Uncertainty comes from the inverse information rescaled to original
 * frequency weights, without regularization or robust/clustered corrections.
 *
 * <p>A fit requires finite, identified parameters: unused modes, disconnected choice sets, singular
 * information, numerical failure and nonconvergence produce exceptions. These checks are not a
 * complete statistical separation test. Callbacks and interruption provide cooperative cancellation
 * between iterations and blocks of observations. Every invocation owns its working data; input
 * arrays must remain stable while that call is running.
 */
final class LogCostChoiceEstimator {
  private static final int MAX_ITERATIONS = 200;
  private static final double TOLERANCE = 1e-8;

  private LogCostChoiceEstimator() {}

  /**
   * Fits nonreference intercepts with fixed inverse-cost utility offsets.
   *
   * @param costs OD costs in input column order
   * @param quantities observed quantities with identical dimensions
   * @param referenceMode zero-based reference column
   * @param continueEstimation synchronous cancellation callback
   * @return a log-cost result with coefficient -1 and zero cost-coefficient uncertainty
   */
  static LogCostChoiceEstimate estimateProportional(
      double[][] costs,
      double[][] quantities,
      int referenceMode,
      java.util.function.BooleanSupplier continueEstimation) {
    return estimate(costs, quantities, referenceMode, continueEstimation, false, true, true);
  }

  /**
   * Fits modal intercepts and a common cost coefficient using the selected error distribution.
   *
   * @param costs OD costs in input column order
   * @param quantities observed quantities with identical dimensions
   * @param referenceMode zero-based reference column
   * @param continueEstimation synchronous cancellation callback
   * @param probit true for independent normal errors, false for logit
   * @return coefficients, original-weight likelihoods and uncertainty
   */
  static LogCostChoiceEstimate estimate(
      double[][] costs,
      double[][] quantities,
      int referenceMode,
      java.util.function.BooleanSupplier continueEstimation,
      boolean probit) {
    return estimate(costs, quantities, referenceMode, continueEstimation, probit, true);
  }

  /** Fits either a common coefficient or one coefficient for each input mode. */
  static LogCostChoiceEstimate estimate(
      double[][] costs,
      double[][] quantities,
      int referenceMode,
      java.util.function.BooleanSupplier continueEstimation,
      boolean probit,
      boolean conditional) {
    return estimate(
        costs, quantities, referenceMode, continueEstimation, probit, false, conditional);
  }

  /**
   * Validates observations, constructs centered features and runs a damped Newton fit.
   *
   * <p>All transformations use internal copies. Quantities are normalized for numerical stability,
   * then likelihoods and uncertainty are returned on the caller's original quantity scale. A fixed
   * proportional offset is excluded from the information matrix because its coefficient is not
   * fitted.
   */
  private static LogCostChoiceEstimate estimate(
      double[][] costs,
      double[][] quantities,
      int referenceMode,
      java.util.function.BooleanSupplier continueEstimation,
      boolean probit,
      boolean proportional,
      boolean conditional) {
    if (costs == null
        || quantities == null
        || costs.length == 0
        || costs.length != quantities.length
        || costs[0] == null
        || costs[0].length < 2) {
      throw new IllegalArgumentException(
          "Expected equally sized cost/quantity matrices with >=2 modes");
    }
    checkCancelled(continueEstimation);
    int modes = costs[0].length;
    int slopes = proportional ? 0 : conditional ? 1 : modes;
    int size = modes - 1 + slopes;
    if (referenceMode < 0 || referenceMode >= modes) {
      throw new IllegalArgumentException("Reference mode must be a zero-based column index");
    }
    List<Observation> observations = new ArrayList<>();
    double totalQuantity = 0;
    double[] costScales = new double[slopes];
    double[] costCenters = new double[modes];
    java.util.Arrays.fill(costCenters, Double.NaN);
    double[] modeQuantities = new double[modes];
    for (int row = 0; row < costs.length; row++) {
      if (row % 256 == 0) {
        checkCancelled(continueEstimation);
      }
      if (costs[row] == null
          || quantities[row] == null
          || costs[row].length != modes
          || quantities[row].length != modes) {
        throw new IllegalArgumentException("Inconsistent number of modes at row " + row);
      }
      double rowQuantity = 0;
      int available = 0;
      for (int mode = 0; mode < modes; mode++) {
        double cost = costs[row][mode];
        double quantity = quantities[row][mode];
        if (Double.isInfinite(cost)
            || !Double.isFinite(quantity)
            || quantity < 0
            || (quantity > 0 && !(cost > 0))) {
          throw new IllegalArgumentException(
              "Invalid cost/quantity at row " + row + ", mode " + mode);
        }
        if (cost > 0) {
          available++;
        }
        rowQuantity += quantity;
        modeQuantities[mode] += quantity;
      }
      totalQuantity += rowQuantity;
      if (!Double.isFinite(totalQuantity)) {
        throw new IllegalArgumentException("Total quantity exceeds the numeric range");
      }
      if (rowQuantity == 0) {
        continue;
      }
      double[][] features = new double[available][size];
      double[] offsets = new double[available];
      double[] observed = new double[available];
      int alternative = 0;
      double baseLogCost = 0;
      for (int mode = 0; mode < modes; mode++) {
        if (!(costs[row][mode] > 0)) {
          continue;
        }
        double logCost = Math.log(costs[row][mode]);
        if (alternative == 0) {
          baseLogCost = logCost;
        }
        if (mode != referenceMode) {
          features[alternative][mode < referenceMode ? mode : mode - 1] = 1;
        }
        // A common utility shift has no effect on probabilities; avoid subtracting huge utilities.
        double difference = logCost - baseLogCost;
        if (proportional) {
          offsets[alternative] = -difference;
        } else if (conditional) {
          features[alternative][modes - 1] = difference;
          costScales[0] = Math.max(costScales[0], Math.abs(difference));
        } else {
          if (Double.isNaN(costCenters[mode])) {
            costCenters[mode] = logCost;
          }
          double centered = logCost - costCenters[mode];
          features[alternative][modes - 1 + mode] = centered;
          costScales[mode] = Math.max(costScales[mode], Math.abs(centered));
        }
        observed[alternative++] = quantities[row][mode];
      }
      if (!proportional && !conditional) {
        // Subtract the complete first-alternative feature vector, a common utility shift.
        double[] base = features[0].clone();
        for (double[] feature : features) {
          for (int p = modes - 1; p < size; p++) {
            feature[p] -= base[p];
          }
        }
      }
      observations.add(new Observation(features, offsets, observed, rowQuantity));
    }
    if (totalQuantity == 0) {
      throw new IllegalArgumentException("No positive observed quantities");
    }
    for (int mode = 0; mode < modes; mode++) {
      if (modeQuantities[mode] == 0) {
        throw new IllegalStateException(
            "Mode " + mode + " has no observed quantity; no finite intercept");
      }
    }
    for (double scale : costScales) {
      if (scale == 0) {
        throw new IllegalStateException("Log-cost coefficient is unidentified: no cost variation");
      }
    }
    for (Observation observation : observations) {
      if (!proportional) {
        for (double[] feature : observation.features) {
          for (int slope = 0; slope < slopes; slope++) {
            feature[modes - 1 + slope] /= costScales[slope];
          }
        }
      }
      for (int mode = 0; mode < observation.quantities.length; mode++) {
        observation.quantities[mode] /= totalQuantity;
      }
      observation.weight /= totalQuantity;
    }

    double[] parameters = new double[size];
    Evaluation current = evaluate(observations, parameters, continueEstimation, probit);
    double nullLogLikelihood = -current.loss * totalQuantity;
    if (!Double.isFinite(nullLogLikelihood)) {
      throw new IllegalArgumentException(
          "Quantity weights make the log-likelihood exceed the numeric range");
    }
    if (!proportional) {
      // Seek the maximum likelihood estimate from a negative-cost starting point. Do not clamp
      // a nonnegative optimum to an arbitrary negative value: report it as a failed estimate.
      for (int slope = 0; slope < slopes; slope++) {
        parameters[modes - 1 + slope] = -1;
      }
      current = evaluate(observations, parameters, continueEstimation, probit);
    }
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      if (!continueEstimation.getAsBoolean() || Thread.currentThread().isInterrupted()) {
        throw new java.util.concurrent.CancellationException("Modal estimation canceled");
      }
      Cholesky information = new Cholesky(current.information);
      double[] step = information.solve(current.gradient);
      double relativeStep = 0;
      double improvement = 0;
      for (int p = 0; p < size; p++) {
        relativeStep = Math.max(relativeStep, Math.abs(step[p]) / (1 + Math.abs(parameters[p])));
        improvement += current.gradient[p] * step[p];
      }
      if (relativeStep < TOLERANCE) {
        if (!proportional) {
          List<Integer> invalid = new ArrayList<>();
          for (int mode = 0; mode < modes; mode++) {
            if (parameters[modes - 1 + (conditional ? 0 : mode)] >= -TOLERANCE) {
              invalid.add(mode);
            }
          }
          if (!invalid.isEmpty()) {
            throw new NonNegativeCostCoefficientException(
                invalid.stream().mapToInt(Integer::intValue).toArray());
          }
        }
        if (!conditional) {
          return modeSpecificResult(
              parameters,
              information,
              costCenters,
              costScales,
              referenceMode,
              -current.loss * totalQuantity,
              nullLogLikelihood,
              totalQuantity,
              observations.size(),
              iteration,
              probit);
        }
        double[] errors = new double[modes];
        for (int p = 0; p < size; p++) {
          double[] unit = new double[size];
          unit[p] = 1;
          errors[p] = Math.sqrt(information.solve(unit)[p] / totalQuantity);
          if (!Double.isFinite(errors[p])) {
            throw new IllegalStateException("Coefficient uncertainty exceeds the numeric range");
          }
        }
        parameters = java.util.Arrays.copyOf(parameters, modes);
        if (proportional) {
          parameters[modes - 1] = -1;
        } else {
          parameters[modes - 1] /= costScales[0];
          errors[modes - 1] /= costScales[0];
        }
        return new LogCostChoiceEstimate(
            parameters,
            errors,
            referenceMode,
            -current.loss * totalQuantity,
            nullLogLikelihood,
            totalQuantity,
            observations.size(),
            iteration,
            probit);
      }
      if (!Double.isFinite(improvement) || improvement <= 0) {
        throw new IllegalStateException(
            "Cannot find a finite Newton step; check model identification");
      }
      boolean accepted = false;
      double fraction = 1;
      for (int search = 0; search < 50; search++) {
        double[] candidate = new double[size];
        for (int p = 0; p < size; p++) {
          candidate[p] = parameters[p] - fraction * step[p];
        }
        Evaluation next = evaluate(observations, candidate, continueEstimation, probit);
        double roundoff = 1e-14 * Math.max(1, current.loss);
        if (Double.isFinite(next.loss)
            && next.loss <= current.loss - 1e-4 * fraction * improvement + roundoff) {
          parameters = candidate;
          current = next;
          accepted = true;
          break;
        }
        fraction *= 0.5;
      }
      if (!accepted) {
        throw new IllegalStateException("Modal line search failed; check scaling and separation");
      }
    }
    throw new IllegalStateException(
        "Modal estimation did not converge in "
            + MAX_ITERATIONS
            + " iterations; check separation or weak identification");
  }

  /** Removes per-mode centering/scaling and transforms uncertainty using the full covariance. */
  private static LogCostChoiceEstimate modeSpecificResult(
      double[] parameters,
      Cholesky information,
      double[] centers,
      double[] scales,
      int reference,
      double likelihood,
      double nullLikelihood,
      double quantity,
      int observations,
      int iterations,
      boolean probit) {
    int modes = centers.length;
    double[] intercepts = new double[modes];
    double[] interceptErrors = new double[modes];
    double[] coefficients = new double[modes];
    double[] coefficientErrors = new double[modes];
    for (int mode = 0; mode < modes; mode++) {
      coefficients[mode] = parameters[modes - 1 + mode] / scales[mode];
      double[] slope = new double[parameters.length];
      slope[modes - 1 + mode] = 1 / scales[mode];
      coefficientErrors[mode] = standardError(slope, information, quantity);
      if (mode != reference) {
        int index = mode < reference ? mode : mode - 1;
        intercepts[mode] =
            parameters[index]
                - coefficients[mode] * centers[mode]
                + parameters[modes - 1 + reference] / scales[reference] * centers[reference];
        double[] transform = new double[parameters.length];
        transform[index] = 1;
        transform[modes - 1 + mode] = -centers[mode] / scales[mode];
        transform[modes - 1 + reference] = centers[reference] / scales[reference];
        interceptErrors[mode] = standardError(transform, information, quantity);
      }
    }
    return new LogCostChoiceEstimate(
        intercepts,
        interceptErrors,
        coefficients,
        coefficientErrors,
        likelihood,
        nullLikelihood,
        quantity,
        observations,
        iterations,
        probit);
  }

  private static double standardError(double[] transform, Cholesky information, double quantity) {
    double[] covariance = information.solve(transform);
    double variance = 0;
    for (int p = 0; p < transform.length; p++) {
      variance += transform[p] * covariance[p];
    }
    double error = Math.sqrt(variance / quantity);
    if (!Double.isFinite(error)) {
      throw new IllegalStateException("Coefficient uncertainty exceeds the numeric range");
    }
    return error;
  }

  /**
   * Accumulates normalized negative log-likelihood, gradient and lower-triangular information.
   *
   * <p>Fixed offsets contribute to probabilities but not to utility derivatives. Pairwise feature
   * differences reduce cancellation in logit gradients and retain evidence of separation near
   * probabilities zero and one. Probit contributions differentiate each observed log probability.
   */
  private static Evaluation evaluate(
      List<Observation> observations,
      double[] parameters,
      java.util.function.BooleanSupplier continueEstimation,
      boolean probit) {
    int size = parameters.length;
    Evaluation evaluation = new Evaluation(size);
    int row = 0;
    for (Observation observation : observations) {
      if (row++ % 256 == 0) {
        checkCancelled(continueEstimation);
      }
      double[][] features = observation.features;
      double[] utilities = observation.offsets.clone();
      double largest = Double.NEGATIVE_INFINITY;
      for (int mode = 0; mode < features.length; mode++) {
        for (int p = 0; p < size; p++) {
          utilities[mode] += features[mode][p] * parameters[p];
        }
        largest = Math.max(largest, utilities[mode]);
      }
      if (probit) {
        accumulateProbit(evaluation, features, utilities, observation.quantities);
        continue;
      }
      double denominator = 0;
      double[] probabilities = new double[features.length];
      for (int mode = 0; mode < features.length; mode++) {
        probabilities[mode] = Math.exp(utilities[mode] - largest);
        denominator += probabilities[mode];
      }
      double logDenominator = Math.log(denominator);
      for (int mode = 0; mode < features.length; mode++) {
        probabilities[mode] /= denominator;
        if (observation.quantities[mode] > 0) {
          evaluation.loss +=
              observation.quantities[mode] * ((largest - utilities[mode]) + logDenominator);
        }
      }
      // Pairwise differences avoid subtracting nearly equal observed and predicted totals.
      // This matters for separation, where rounding a probability to 1 must not imply convergence.
      for (int mode = 0; mode < features.length; mode++) {
        for (int other = 0; other < mode; other++) {
          double residual =
              observation.quantities[other] * probabilities[mode]
                  - observation.quantities[mode] * probabilities[other];
          double weight = observation.weight * probabilities[mode] * probabilities[other];
          for (int p = 0; p < size; p++) {
            double difference = features[mode][p] - features[other][p];
            evaluation.gradient[p] += residual * difference;
            for (int q = 0; q <= p; q++) {
              evaluation.information[p][q] +=
                  weight * difference * (features[mode][q] - features[other][q]);
            }
          }
        }
      }
    }
    return evaluation;
  }

  /** Checks interruption and the caller's continuation callback without wrapping cancellation. */
  private static void checkCancelled(java.util.function.BooleanSupplier supplier) {
    if (Thread.currentThread().isInterrupted() || !supplier.getAsBoolean()) {
      throw new java.util.concurrent.CancellationException("Modal estimation canceled");
    }
  }

  /**
   * Applies the chain rule from log-probability utility derivatives to coefficient derivatives.
   *
   * <p>Each observed quantity weights a contribution to the negative log-likelihood information;
   * only its lower triangle is stored. Alternatives with zero observed quantity contribute nothing
   * to the chosen-alternative sum but still enter the probabilities of other alternatives.
   */
  private static void accumulateProbit(
      Evaluation result, double[][] features, double[] utilities, double[] quantities) {
    int size = result.gradient.length;
    for (int i = 0; i < utilities.length; i++) {
      if (quantities[i] == 0) {
        continue;
      }
      ProbitProbabilities.Evaluation probability = ProbitProbabilities.evaluate(utilities, i);
      result.loss -= quantities[i] * probability.logProbability;
      for (int p = 0; p < size; p++) {
        for (int j = 0; j < utilities.length; j++) {
          result.gradient[p] -= quantities[i] * probability.score[j] * features[j][p];
          for (int q = 0; q <= p; q++) {
            for (int k = 0; k < utilities.length; k++) {
              result.information[p][q] -=
                  quantities[i] * probability.hessian[j][k] * features[j][p] * features[k][q];
            }
          }
        }
      }
    }
  }

  /**
   * One retained OD row after removing unavailable alternatives.
   *
   * <p>Features have one column per free coefficient. Offsets hold fixed proportional log-cost
   * terms (zero for logit/probit). Quantities and their total weight are normalized in place once,
   * before optimization; they never alias the caller's quantity arrays.
   */
  private static final class Observation {
    private final double[][] features;
    private final double[] offsets;
    private final double[] quantities;
    private double weight;

    private Observation(double[][] features, double[] offsets, double[] quantities, double weight) {
      this.features = features;
      this.offsets = offsets;
      this.quantities = quantities;
      this.weight = weight;
    }
  }

  /**
   * Objective and derivative accumulator for one candidate parameter vector.
   *
   * <p>Loss and derivatives use total-quantity normalization. Only the lower information triangle
   * is populated; the Cholesky solver intentionally does not read the upper triangle.
   */
  private static final class Evaluation {
    private double loss;
    private final double[] gradient;
    private final double[][] information;

    private Evaluation(int size) {
      gradient = new double[size];
      information = new double[size][size];
    }
  }

  /**
   * Small dense positive-definite solve, scaled to detect relative rather than absolute rank loss.
   *
   * <p>The diagonal square roots scale the information matrix to correlation units before
   * factorization. Nonfinite/zero diagonals and pivots no larger than 1e-12 fail instead of adding
   * a ridge penalty. Solving rescales both sides, yielding steps or inverse-information columns in
   * the original parameter coordinates. The supplied matrix is read without modification.
   */
  private static final class Cholesky {
    private final double[][] lower;
    private final double[] scale;

    private Cholesky(double[][] matrix) {
      int size = matrix.length;
      lower = new double[size][size];
      scale = new double[size];
      for (int p = 0; p < size; p++) {
        scale[p] = Math.sqrt(matrix[p][p]);
        if (!Double.isFinite(scale[p]) || scale[p] == 0) {
          throw singularInformation();
        }
        for (int q = 0; q <= p; q++) {
          double value = matrix[p][q] / scale[p] / scale[q];
          for (int k = 0; k < q; k++) {
            value -= lower[p][k] * lower[q][k];
          }
          if (p == q) {
            if (!Double.isFinite(value) || value <= 1e-12) {
              throw singularInformation();
            }
            lower[p][q] = Math.sqrt(value);
          } else {
            lower[p][q] = value / lower[q][q];
          }
        }
      }
    }

    /**
     * Solves the information system for a gradient or unit vector without mutating the right side.
     */
    private double[] solve(double[] right) {
      double[] result = right.clone();
      for (int p = 0; p < result.length; p++) {
        result[p] /= scale[p];
        for (int q = 0; q < p; q++) {
          result[p] -= lower[p][q] * result[q];
        }
        result[p] /= lower[p][p];
      }
      for (int p = result.length - 1; p >= 0; p--) {
        for (int q = p + 1; q < result.length; q++) {
          result[p] -= lower[q][p] * result[q];
        }
        result[p] /= lower[p][p];
      }
      for (int p = 0; p < result.length; p++) {
        result[p] /= scale[p];
      }
      return result;
    }

    private static IllegalStateException singularInformation() {
      return new IllegalStateException(
          "Singular choice-model information: check collinear costs, disconnected"
              + " choice sets, unused modes or separation");
    }
  }
}
