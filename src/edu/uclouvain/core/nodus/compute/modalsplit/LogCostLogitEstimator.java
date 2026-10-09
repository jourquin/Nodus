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

/**
 * Estimates V(i,m) = intercept(m) + beta * log(cost(i,m)) using multinomial logit.
 *
 * <p>One intercept is fixed at zero. NaN or nonpositive costs denote unavailable modes, which must
 * have zero observed quantity; infinite costs are rejected. Observations with zero total quantity
 * are ignored. Fits start with negative cost coefficients; a zero or positive fitted coefficient is
 * reported as a failed estimate rather than clamped or regularized. Standard errors treat
 * quantities as frequency weights, not independent survey observations. The logit error scale
 * follows the usual standard Gumbel convention.
 *
 * <p>Each call fits one commodity group from arrays shaped as {@code [OD row][mode column]}. The
 * reference argument is a column index, while Nodus mode IDs are supplied separately when
 * exporting. Costs and quantities are read without modification. Availability may vary by row,
 * including rows where the reference mode is unavailable, provided relative intercepts remain
 * identified through the combined choice sets. Equal costs or constant relative costs cannot
 * identify both a common slope and all modal constants.
 *
 * <p>The calculation has no project, database or file side effects. Use {@link LogitCalibration} to
 * compute route costs from a Nodus network and persist an entire estimation run. A cancellation
 * callback runs synchronously on the fitting thread and must return promptly; returning false or
 * interrupting that thread throws {@link java.util.concurrent.CancellationException}.
 */
public final class LogCostLogitEstimator {
  private LogCostLogitEstimator() {}

  /**
   * Fits with the first input column as reference.
   *
   * @param costs costs by OD row and mode
   * @param quantities observed quantities with the same shape
   * @return fitted coefficients and likelihood diagnostics
   */
  public static Result estimate(double[][] costs, double[][] quantities) {
    return estimate(costs, quantities, 0);
  }

  /**
   * Fits with a chosen reference mode.
   *
   * @param costs costs by OD row and mode
   * @param quantities observed quantities with the same shape
   * @param referenceMode zero-based reference column
   * @return fitted coefficients and likelihood diagnostics
   */
  public static Result estimate(double[][] costs, double[][] quantities, int referenceMode) {
    return estimate(costs, quantities, referenceMode, () -> true);
  }

  /**
   * Fits with cooperative cancellation during preparation and likelihood evaluation.
   *
   * @param costs costs by OD row and mode
   * @param quantities observed quantities with the same shape
   * @param referenceMode zero-based reference column
   * @param continueEstimation returns false to cancel
   * @return fitted coefficients and likelihood diagnostics
   * @throws IllegalArgumentException for invalid observations
   * @throws IllegalStateException for unidentified models, numerical failure or nonconvergence
   * @throws java.util.concurrent.CancellationException on callback cancellation or interruption
   */
  public static Result estimate(
      double[][] costs,
      double[][] quantities,
      int referenceMode,
      java.util.function.BooleanSupplier continueEstimation) {
    return estimate(costs, quantities, referenceMode, true, continueEstimation);
  }

  /**
   * Fits a common log-cost coefficient when conditional, or one coefficient per mode otherwise. All
   * fitted cost coefficients must be strictly negative.
   */
  public static Result estimate(
      double[][] costs,
      double[][] quantities,
      int referenceMode,
      boolean conditional,
      java.util.function.BooleanSupplier continueEstimation) {
    return new Result(
        LogCostChoiceEstimator.estimate(
            costs, quantities, referenceMode, continueEstimation, false, conditional));
  }

  /** Immutable logit result, retaining the original estimator API. */
  public static final class Result extends LogCostChoiceEstimate {
    private Result(LogCostChoiceEstimate source) {
      super(source);
    }
  }
}
