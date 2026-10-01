/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

/**
 * Estimates positive modal cost factors in P(i) = (k(i) C(i))^-1 / sum((k(j) C(j))^-1).
 *
 * <p>The reference factor is exactly one. Internally, the logit utility is -ln(k(i)) - ln(C(i));
 * only the intercepts are fitted, with the cost coefficient fixed at -1. Factors therefore adjust
 * relative perceived cost without estimating a cost elasticity. They may absorb omitted modal
 * attributes as well as cost perception. Factors are fitted separately for each commodity group by
 * the caller and affect modal choice only.
 *
 * <p>NaN and nonpositive costs denote unavailable modes and require zero observed quantity;
 * infinite costs are invalid. Quantities are frequency weights. Zero-demand modes, disconnected
 * choice sets and separation cannot yield finite identified factors and are reported as failures.
 *
 * <p>Each call reads one group's {@code [OD row][mode column]} arrays without modifying them.
 * Reference arguments are column indices, not Nodus mode IDs. Unlike a logit that estimates its
 * cost coefficient, this constrained model can identify factors with equal costs or constant cost
 * ratios. Its baseline likelihood uses ordinary inverse-cost shares with all factors one.
 *
 * <p>{@link Result} converts fitted intercepts to factors using {@code k = exp(-intercept)} and
 * applies the delta method to their standard errors. Factors outside the positive finite double
 * range are rejected before export. No physical costs are rewritten. Database routing, per-group
 * iteration and atomic cost-file persistence belong to {@link LogitCalibration}; this API has no
 * file or project side effects. Cancellation uses the same synchronous callback and thread
 * interruption contract as {@link LogCostLogitEstimator}.
 */
public final class ProportionalEstimator {
  private ProportionalEstimator() {}

  /**
   * Fits using the first cost/quantity column as the reference mode.
   *
   * @param costs costs by OD row and mode
   * @param quantities observed quantities with the same shape
   * @return estimated factors and diagnostics
   */
  public static Result estimate(double[][] costs, double[][] quantities) {
    return estimate(costs, quantities, 0);
  }

  /**
   * Fits using a chosen reference mode.
   *
   * @param costs costs by OD row and mode
   * @param quantities observed quantities with the same shape
   * @param referenceMode zero-based reference column, whose factor is fixed at one
   * @return estimated factors and diagnostics
   */
  public static Result estimate(double[][] costs, double[][] quantities, int referenceMode) {
    return estimate(costs, quantities, referenceMode, () -> true);
  }

  /**
   * Fits factors with cooperative cancellation during preparation and likelihood evaluation.
   *
   * @param costs costs by OD row and mode
   * @param quantities observed quantities with the same shape
   * @param referenceMode zero-based reference column, whose factor is fixed at one
   * @param continueEstimation returns false to cancel
   * @return estimated factors and diagnostics
   * @throws IllegalArgumentException for invalid observations
   * @throws IllegalStateException for unidentified models, numerical failure or nonconvergence
   * @throws java.util.concurrent.CancellationException on callback cancellation or interruption
   */
  public static Result estimate(
      double[][] costs,
      double[][] quantities,
      int referenceMode,
      java.util.function.BooleanSupplier continueEstimation) {
    return new Result(
        LogCostChoiceEstimator.estimateProportional(
            costs, quantities, referenceMode, continueEstimation));
  }

  /**
   * Immutable factors and diagnostics. Inherited intercepts equal -ln(k); the inherited cost
   * coefficient and its standard error are fixed at -1 and zero respectively.
   */
  public static final class Result extends LogCostChoiceEstimate {
    private final double[] factors;
    private final double[] factorStandardErrors;

    private Result(LogCostChoiceEstimate source) {
      super(source);
      factors = getIntercepts();
      factorStandardErrors = getInterceptStandardErrors();
      for (int mode = 0; mode < factors.length; mode++) {
        factors[mode] = Math.exp(-factors[mode]);
        factorStandardErrors[mode] *= factors[mode];
        if (!Double.isFinite(factors[mode])
            || factors[mode] <= 0
            || !Double.isFinite(factorStandardErrors[mode])) {
          throw new IllegalStateException("Proportional cost factor exceeds the numeric range");
        }
      }
    }

    /**
     * Returns the multiplicative cost adjustments.
     *
     * @return positive factors in input column order, including one for the reference
     */
    public double[] getCostFactors() {
      return factors.clone();
    }

    /**
     * Returns approximate factor uncertainty, transformed from intercept uncertainty.
     *
     * @return delta-method standard errors k * SE(ln(k)); the reference error is zero
     */
    public double[] getCostFactorStandardErrors() {
      return factorStandardErrors.clone();
    }

    /**
     * Returns the likelihood of ordinary proportional splitting before modal adjustments.
     *
     * @return weighted log-likelihood with every cost factor fixed at one
     */
    @Override
    public double getNullLogLikelihood() {
      return super.getNullLogLikelihood();
    }

    /**
     * Formats positive cost factors under the proportional namespace.
     *
     * <p>The reference column is exported with factor one. The separate {@code
     * proportional.reference.group} property is supplied by {@link LogitCalibration}; callers using
     * this numerical API directly must add it themselves. This method returns text and does not
     * write a file or modify transport cost expressions.
     *
     * @param modeIds distinct positive Nodus mode IDs in input column order
     * @param group commodity group ID
     * @return one proportional.costFactor entry per input mode
     * @throws IllegalArgumentException for missing, duplicate or nonpositive mode IDs
     */
    @Override
    public String toCostFileEntries(int[] modeIds, int group) {
      validateModeIds(modeIds);
      StringBuilder text = new StringBuilder();
      for (int mode = 0; mode < modeIds.length; mode++) {
        text.append(Proportional.COST_FACTOR_PREFIX)
            .append(modeIds[mode])
            .append('.')
            .append(group)
            .append(" = ")
            .append(factors[mode])
            .append('\n');
      }
      return text.toString();
    }
  }
}
