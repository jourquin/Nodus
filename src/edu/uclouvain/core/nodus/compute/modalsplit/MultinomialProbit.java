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

import com.bbn.openmap.Environment;
import edu.uclouvain.core.nodus.NodusProject;

/**
 * Assignment-time probit with modal constants and common or mode-specific log-cost coefficients.
 *
 * <p>The systematic utility is {@code V = intercept + beta * ln(C)}, using the cheapest admissible
 * modal cost. Independent utility errors have distribution N(0,1); the probability of selecting a
 * mode is the joint probability that its random utility exceeds all other available utilities.
 * {@link ProbitProbabilities} evaluates this event deterministically, with an exact normal-CDF
 * expression for two alternatives and numerical integration for larger choice sets.
 *
 * <p>Parameters use {@code probit.(intercept).mode.group}, {@code probit.log(cost).mode.group} and
 * {@code probit.reference.group}. One reference intercept is fixed at zero. The error variance is
 * fixed, not estimated, so logit coefficients cannot be reused even though the systematic utility
 * has the same form. No cross-mode error covariance or mode-specific variance is fitted.
 *
 * <p>Without any probit parameters for a group, the inherited fallback warns once per assignment
 * and uses {@code V = -C}: cost factor one and zero constants. Normal errors retain variance one.
 * Saved logit or proportional parameters do not change this fallback, and no defaults are saved.
 *
 * <p>The inherited {@link MultinomialLogit} lifecycle validates group coefficients, requires
 * positive finite unimodal costs for calibrated groups and distributes their modal flow among
 * routes by inverse cost. Uncalibrated groups accept finite costs and intermodal paths, using the
 * legacy exponential route allocation. Only the modal probability calculation and property keys
 * differ; estimation remains separate through {@link LogCostProbitEstimator} or {@link
 * LogitCalibration}.
 */
public final class MultinomialProbit extends MultinomialLogit {
  /**
   * Associates this modal method with a project.
   *
   * @param project the Nodus project
   */
  public MultinomialProbit(NodusProject project) {
    super(project);
  }

  @Override
  public String getName() {
    return "MNP";
  }

  @Override
  public String getPrettyName() {
    return Environment.getI18n().get(ModalSplitMethod.class, "MNP", "Multinomial probit");
  }

  @Override
  protected String coefficientPrefix() {
    return "probit.";
  }

  @Override
  protected String referenceKey() {
    return "probit.reference.";
  }

  @Override
  protected double[] probabilities(double[] utilities) {
    return ProbitProbabilities.probabilities(utilities);
  }
}
