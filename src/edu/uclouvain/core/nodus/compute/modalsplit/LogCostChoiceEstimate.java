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
 * Immutable reporting snapshot for one successfully fitted commodity group's modal model.
 *
 * <p>Intercepts and their standard errors follow the estimator's input column order, including an
 * explicit zero at the reference column. Cost coefficients are common or mode-specific. For
 * proportional estimation, intercepts are the internal values {@code -ln(k)}, the coefficient is
 * fixed at -1 and its standard error is zero; {@link ProportionalEstimator.Result} exposes the
 * positive factors and their transformed uncertainty.
 *
 * <p>Likelihoods and standard errors use the original observed quantities as frequency weights.
 * They are not robust or clustered survey errors. Changing the quantity unit changes likelihoods
 * and standard errors even though the fitted modal shares remain the same. Array getters return
 * copies, so a result may be retained or shared independently of subsequent assignments.
 *
 * <p>Export methods produce property text only. They do not update a file, select an assignment
 * method or persist the reference mode; {@link LogitCalibration} performs those bookkeeping steps.
 *
 * @see LogCostLogitEstimator
 * @see LogCostProbitEstimator
 * @see ProportionalEstimator
 */
public class LogCostChoiceEstimate {
  private final double[] intercepts;
  private final double[] interceptStandardErrors;
  private final double[] costCoefficients;
  private final double[] costStandardErrors;
  private final boolean conditional;
  private final double logLikelihood;
  private final double nullLogLikelihood;
  private final double totalQuantity;
  private final int observations;
  private final int iterations;
  private final boolean probit;

  /**
   * Expands the optimizer's packed parameters into one entry per input mode.
   *
   * <p>For M modes, the first M-1 slots contain nonreference intercepts, ordered by input column
   * with the reference omitted. The final slot is the common cost coefficient, including the fixed
   * -1 for proportional choice. Errors use the same layout; fixed parameters have zero error.
   *
   * @param parameters fitted values already transformed back from optimizer scaling
   * @param errors standard errors on the same coefficient scale
   * @param referenceMode zero-based reference column
   * @param logLikelihood likelihood with original quantity weights
   * @param nullLogLikelihood baseline likelihood on the same quantity scale
   * @param totalQuantity sum of original observed quantities
   * @param observations number of positive-total rows retained by the estimator
   * @param iterations accepted Newton updates before convergence
   * @param probit whether default property export uses the probit namespace
   */
  LogCostChoiceEstimate(
      double[] parameters,
      double[] errors,
      int referenceMode,
      double logLikelihood,
      double nullLogLikelihood,
      double totalQuantity,
      int observations,
      int iterations,
      boolean probit) {
    intercepts = new double[parameters.length];
    interceptStandardErrors = new double[parameters.length];
    for (int mode = 0; mode < parameters.length; mode++) {
      if (mode != referenceMode) {
        int index = mode < referenceMode ? mode : mode - 1;
        intercepts[mode] = parameters[index];
        interceptStandardErrors[mode] = errors[index];
      }
    }
    costCoefficients = new double[parameters.length];
    costStandardErrors = new double[parameters.length];
    java.util.Arrays.fill(costCoefficients, parameters[parameters.length - 1]);
    java.util.Arrays.fill(costStandardErrors, errors[errors.length - 1]);
    conditional = true;
    this.logLikelihood = logLikelihood;
    this.nullLogLikelihood = nullLogLikelihood;
    this.totalQuantity = totalQuantity;
    this.observations = observations;
    this.iterations = iterations;
    this.probit = probit;
  }

  /** Creates a mode-specific result with parameters already on the original log-cost scale. */
  LogCostChoiceEstimate(
      double[] intercepts,
      double[] interceptStandardErrors,
      double[] costCoefficients,
      double[] costStandardErrors,
      double logLikelihood,
      double nullLogLikelihood,
      double totalQuantity,
      int observations,
      int iterations,
      boolean probit) {
    this.intercepts = intercepts.clone();
    this.interceptStandardErrors = interceptStandardErrors.clone();
    this.costCoefficients = costCoefficients.clone();
    this.costStandardErrors = costStandardErrors.clone();
    conditional = false;
    this.logLikelihood = logLikelihood;
    this.nullLogLikelihood = nullLogLikelihood;
    this.totalQuantity = totalQuantity;
    this.observations = observations;
    this.iterations = iterations;
    this.probit = probit;
  }

  /**
   * Copies the reporting data into a model-specific result, retaining defensive array ownership.
   *
   * @param source successful estimate from the shared numerical engine
   */
  protected LogCostChoiceEstimate(LogCostChoiceEstimate source) {
    intercepts = source.intercepts.clone();
    interceptStandardErrors = source.interceptStandardErrors.clone();
    costCoefficients = source.costCoefficients.clone();
    costStandardErrors = source.costStandardErrors.clone();
    conditional = source.conditional;
    logLikelihood = source.logLikelihood;
    nullLogLikelihood = source.nullLogLikelihood;
    totalQuantity = source.totalQuantity;
    observations = source.observations;
    iterations = source.iterations;
    probit = source.probit;
  }

  /**
   * Returns a copy of the estimated intercepts.
   *
   * @return intercepts in input column order, including the fixed reference intercept
   */
  public double[] getIntercepts() {
    return intercepts.clone();
  }

  /**
   * Returns a copy of the intercept standard errors.
   *
   * @return standard errors for the intercepts in input column order
   */
  public double[] getInterceptStandardErrors() {
    return interceptStandardErrors.clone();
  }

  /**
   * Returns the common log-cost coefficient, estimated for logit/probit and fixed for proportional.
   *
   * @return common coefficient of log(cost), or -1 for proportional choice
   * @throws IllegalStateException for a mode-specific result; use {@link #getCostCoefficients()}
   */
  public double getCostCoefficient() {
    requireConditional();
    return costCoefficients[0];
  }

  /**
   * Returns the standard error of the shared log-cost coefficient.
   *
   * @return conventional standard error, or zero when the cost coefficient is fixed
   * @throws IllegalStateException for a mode-specific result; use {@link #getCostStandardErrors()}
   */
  public double getCostStandardError() {
    requireConditional();
    return costStandardErrors[0];
  }

  /**
   * Whether the result has a single common log-cost coefficient.
   *
   * @return true for a common coefficient, false for mode-specific coefficients
   */
  public boolean isConditional() {
    return conditional;
  }

  /**
   * Returns log-cost coefficients in input mode order, repeating a common coefficient.
   *
   * @return a defensive copy of the coefficients
   */
  public double[] getCostCoefficients() {
    return costCoefficients.clone();
  }

  /**
   * Returns log-cost coefficient standard errors in input mode order.
   *
   * @return a defensive copy of the conventional standard errors
   */
  public double[] getCostStandardErrors() {
    return costStandardErrors.clone();
  }

  private void requireConditional() {
    if (!conditional) {
      throw new IllegalStateException("This estimate has mode-specific cost coefficients");
    }
  }

  /**
   * Returns the fitted model's weighted log-likelihood.
   *
   * @return log-likelihood using the original quantity weights
   */
  public double getLogLikelihood() {
    return logLikelihood;
  }

  /**
   * Returns the weighted log-likelihood before fitting the free parameters.
   *
   * <p>Logit/probit start with all coefficients zero, giving equal shares among available modes.
   * Proportional starts with all factors one and a fixed cost coefficient of -1, giving ordinary
   * inverse-cost shares. This baseline is not a separately estimated intercept-only model.
   *
   * @return baseline likelihood using the original quantity weights
   */
  public double getNullLogLikelihood() {
    return nullLogLikelihood;
  }

  /**
   * Returns the total observed quantity used in estimation.
   *
   * @return sum of the input quantities
   */
  public double getTotalQuantity() {
    return totalQuantity;
  }

  /**
   * Returns the number of observations used in estimation.
   *
   * @return number of OD rows with positive total quantity
   */
  public int getObservations() {
    return observations;
  }

  /**
   * Returns the number of iterations needed to fit the model.
   *
   * @return number of Newton updates before convergence
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Formats coefficients for the fitted model; probit entries use a separate probit. prefix.
   *
   * <p>Every mode receives an intercept and slope entry, including a zero reference intercept. The
   * reference-mode property is not included: standalone callers must persist the chosen reference
   * ID separately, especially when it is not the smallest mode ID. The calibration workflow
   * supplies that property and performs the actual file update.
   *
   * @param modeIds Nodus mode IDs corresponding to the input columns (distinct positive integers)
   * @param group commodity group ID
   * @return cost-file entries, including an explicit zero for the reference intercept
   */
  public String toCostFileEntries(int[] modeIds, int group) {
    validateModeIds(modeIds);
    StringBuilder text = new StringBuilder();
    for (int mode = 0; mode < modeIds.length; mode++) {
      String suffix = "." + modeIds[mode] + "." + group + " = ";
      text.append(probit ? "probit.(intercept)" : "(intercept)")
          .append(suffix)
          .append(intercepts[mode])
          .append('\n');
      text.append(probit ? "probit.log(cost)" : "log(cost)")
          .append(suffix)
          .append(costCoefficients[mode])
          .append('\n');
    }
    return text.toString();
  }

  /**
   * Checks the mapping used to export fitted coefficients into a Nodus cost file.
   *
   * @param modeIds distinct positive mode IDs in input column order
   */
  protected final void validateModeIds(int[] modeIds) {
    if (modeIds == null || modeIds.length != intercepts.length) {
      throw new IllegalArgumentException("One mode ID is required per input column");
    }
    for (int mode = 0; mode < modeIds.length; mode++) {
      if (modeIds[mode] <= 0) {
        throw new IllegalArgumentException("Mode IDs must be positive");
      }
      for (int previous = 0; previous < mode; previous++) {
        if (modeIds[previous] == modeIds[mode]) {
          throw new IllegalArgumentException("Mode IDs must be distinct");
        }
      }
    }
  }
}
