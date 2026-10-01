/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

import java.util.Arrays;

/**
 * Multinomial choice with independent standard-normal utility errors.
 *
 * <p>For alternative i, integrates phi(z) * product(j != i, Phi(z + Vi - Vj)). Binary choices use
 * Phi((Vi - Vj) / sqrt(2)) directly. Multinomial integrals are deterministic: Gauss-Legendre
 * quadrature about the log-integrand's maximum, with bounds in the tails and refinement checks on
 * probability, score and Hessian. No simulation draws or pairwise-probit normalization are used.
 * Only available alternatives may be passed to this class.
 *
 * <p>Derivatives are with respect to systematic utilities. Log probabilities and scaled integrands
 * avoid underflow for rare observed choices. The error variance is fixed at one; coefficients
 * therefore have a different scale from logit coefficients.
 *
 * <p>For likelihood evaluation with three or more alternatives, utility differences must be finite
 * and at most 1e6 in magnitude. The integration interval expands until each endpoint lies at least
 * 42 log-density units below the peak; 24-, 48- and 96-node rules refine each side of the peak.
 * Agreement must hold for the log probability and its first two derivatives, not only for total
 * probability. Numerical nonconvergence is reported to the estimator rather than silently accepting
 * a result.
 *
 * <p>All working arrays belong to the invocation. The precomputed quadrature rules are shared
 * read-only, so routing workers may call this helper concurrently. It performs no I/O and applies
 * no cost transformation; callers pass finite utilities for an already filtered choice set.
 */
final class ProbitProbabilities {
  private static final double LOG_SQRT_TWO_PI = 0.9189385332046727;
  private static final double SQRT_TWO = Math.sqrt(2);
  private static final double[][][] RULES = {rule(24), rule(48), rule(96)};

  private ProbitProbabilities() {}

  /**
   * Returns joint multinomial probabilities in the supplied available-alternative order.
   *
   * <p>Exponentiation may round extremely rare choices to zero; estimation uses log probabilities
   * directly through {@link #evaluate}. Renormalization removes only integration roundoff after
   * checking that the sum differs from one by no more than 1e-7. Alternatives more than 56 utility
   * units below the best are omitted from integration: even their binary probability of beating the
   * best, Phi(-56/sqrt(2)), underflows to zero. This permits raw-cost assignment utilities with
   * very large differences, without changing the likelihood evaluator's handling of rare observed
   * choices.
   *
   * @param utilities nonempty vector of finite systematic utilities
   * @return fresh array of shares summing to one
   * @throws IllegalStateException if integration fails or its probabilities do not sum consistently
   */
  static double[] probabilities(double[] utilities) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (double utility : utilities) {
      if (!Double.isFinite(utility)) {
        throw new IllegalArgumentException("Probit utilities must be finite");
      }
      maximum = Math.max(maximum, utility);
    }
    double[] retained = new double[utilities.length];
    int[] indices = new int[utilities.length];
    int size = 0;
    for (int i = 0; i < utilities.length; i++) {
      double shifted = utilities[i] - maximum;
      if (shifted >= -56) {
        retained[size] = shifted;
        indices[size++] = i;
      }
    }
    retained = Arrays.copyOf(retained, size);
    double[] probabilities = new double[utilities.length];
    double sum = 0;
    for (int i = 0; i < size; i++) {
      probabilities[indices[i]] = Math.exp(evaluate(retained, i).logProbability);
      sum += probabilities[indices[i]];
    }
    if (!(sum > 0) || Math.abs(sum - 1) > 1e-7) {
      throw new IllegalStateException("Probit probability integration failed");
    }
    // Remove only quadrature roundoff; these are already joint choice probabilities.
    for (int i = 0; i < probabilities.length; i++) {
      probabilities[i] /= sum;
    }
    return probabilities;
  }

  /**
   * Evaluates one choice's log probability and derivatives with respect to all utilities.
   *
   * @param utilities nonempty finite utility vector containing available alternatives only
   * @param chosen valid zero-based index of the chosen alternative
   * @return log probability, gradient and Hessian in utility coordinates; a singleton has log P=0
   * @throws IllegalArgumentException for nonfinite utilities or unsupported utility differences
   * @throws IllegalStateException if successive quadrature rules do not converge
   */
  static Evaluation evaluate(double[] utilities, int chosen) {
    for (double utility : utilities) {
      if (!Double.isFinite(utility)) {
        throw new IllegalArgumentException("Probit utilities must be finite");
      }
    }
    int size = utilities.length;
    Evaluation result = new Evaluation(size);
    if (size == 1) {
      return result;
    }
    if (size == 2) {
      int other = 1 - chosen;
      double difference = (utilities[chosen] - utilities[other]) / SQRT_TWO;
      Normal normal = normal(difference);
      result.logProbability = normal.logCdf;
      result.score[chosen] = normal.mills / SQRT_TWO;
      result.score[other] = -result.score[chosen];
      double curvature = normal.curvature / 2;
      result.hessian[chosen][chosen] = -curvature;
      result.hessian[other][other] = -curvature;
      result.hessian[chosen][other] = curvature;
      result.hessian[other][chosen] = curvature;
      return result;
    }
    double[] differences = new double[size];
    double upper = 1 + Math.sqrt(size);
    for (int j = 0; j < size; j++) {
      differences[j] = utilities[chosen] - utilities[j];
      if (!Double.isFinite(differences[j]) || Math.abs(differences[j]) > 1e6) {
        throw new IllegalArgumentException("Probit utility differences exceed the numeric range");
      }
      upper = Math.max(upper, -differences[j] + Math.sqrt(size));
    }
    // The derivative is strictly decreasing, so safeguarded Newton has a unique root.
    double lower = 0;
    double center = 0;
    double curvature = 1;
    for (int iteration = 0; iteration < 80; iteration++) {
      double derivative = -center;
      curvature = 1;
      for (int j = 0; j < size; j++) {
        if (j != chosen) {
          Normal normal = normal(center + differences[j]);
          derivative += normal.mills;
          curvature += normal.curvature;
        }
      }
      if (Math.abs(derivative) < 1e-11 * (1 + Math.abs(center))) {
        break;
      }
      if (derivative > 0) {
        lower = center;
      } else {
        upper = center;
      }
      double next = center + derivative / curvature;
      center = next > lower && next < upper ? next : (lower + upper) / 2;
    }
    double peak = logIntegrand(center, differences, chosen);
    double left = 8 / Math.sqrt(curvature);
    double right = left;
    while (logIntegrand(center - left, differences, chosen) > peak - 42) {
      left *= 2;
    }
    while (logIntegrand(center + right, differences, chosen) > peak - 42) {
      right *= 2;
    }
    Evaluation previous = null;
    for (double[][] rule : RULES) {
      result = integrate(differences, chosen, center, left, right, peak, rule);
      if (previous != null && agrees(previous, result)) {
        return result;
      }
      previous = result;
    }
    throw new IllegalStateException("Probit probability integration did not converge");
  }

  /**
   * Compares quadrature refinements using separate tolerances for log probability and derivatives.
   */
  private static boolean agrees(Evaluation a, Evaluation b) {
    if (Math.abs(a.logProbability - b.logProbability) > 1e-9) {
      return false;
    }
    for (int j = 0; j < a.score.length; j++) {
      if (Math.abs(a.score[j] - b.score[j]) > 1e-8 * (1 + Math.abs(b.score[j]))) {
        return false;
      }
      for (int k = 0; k < a.score.length; k++) {
        if (Math.abs(a.hessian[j][k] - b.hessian[j][k]) > 1e-7) {
          return false;
        }
      }
    }
    return true;
  }

  /** Returns ln(phi(z)) plus competing-mode log-CDF terms, omitting the chosen mode itself. */
  private static double logIntegrand(double z, double[] differences, int chosen) {
    double result = -z * z / 2 - LOG_SQRT_TWO_PI;
    for (int j = 0; j < differences.length; j++) {
      if (j != chosen) {
        result += normal(z + differences[j]).logCdf;
      }
    }
    return result;
  }

  /**
   * Integrates both sides of the peak using a Gauss-Legendre rule mapped onto each interval.
   *
   * <p>Subtracting the peak from every log weight avoids rare-choice underflow. Weighted scores and
   * second derivatives are accumulated with the same nodes; normalizing by mass and subtracting the
   * outer product of the mean score yields the Hessian of the log probability.
   */
  private static Evaluation integrate(
      double[] differences,
      int chosen,
      double center,
      double left,
      double right,
      double peak,
      double[][] rule) {
    int size = differences.length;
    Evaluation result = new Evaluation(size);
    double mass = 0;
    double[] score = new double[size];
    double[] curvature = new double[size];
    for (int half = 0; half < 2; half++) {
      double width = (half == 0 ? left : right) / 2;
      double middle = center + (half == 0 ? -width : width);
      for (int node = 0; node < rule[0].length; node++) {
        double z = middle + width * rule[0][node];
        double logWeight = -z * z / 2 - LOG_SQRT_TWO_PI;
        score[chosen] = 0;
        for (int j = 0; j < size; j++) {
          if (j != chosen) {
            Normal normal = normal(z + differences[j]);
            logWeight += normal.logCdf;
            score[j] = -normal.mills;
            score[chosen] += normal.mills;
            curvature[j] = normal.curvature;
          }
        }
        double weight = width * rule[1][node] * Math.exp(logWeight - peak);
        mass += weight;
        for (int j = 0; j < size; j++) {
          result.score[j] += weight * score[j];
          for (int k = 0; k < size; k++) {
            result.hessian[j][k] += weight * score[j] * score[k];
          }
          if (j != chosen) {
            double value = weight * curvature[j];
            result.hessian[j][j] -= value;
            result.hessian[chosen][chosen] -= value;
            result.hessian[j][chosen] += value;
            result.hessian[chosen][j] += value;
          }
        }
      }
    }
    result.logProbability = peak + Math.log(mass);
    for (int j = 0; j < size; j++) {
      result.score[j] /= mass;
    }
    for (int j = 0; j < size; j++) {
      for (int k = 0; k < size; k++) {
        result.hessian[j][k] = result.hessian[j][k] / mass - result.score[j] * result.score[k];
      }
    }
    return result;
  }

  /** Normal log-CDF and its first two derivatives, retaining precision in the negative tail. */
  private static Normal normal(double x) {
    double a = Math.abs(x);
    double logDensity = -a * a / 2 - LOG_SQRT_TWO_PI;
    double logTail;
    double tailMills;
    double tailCurvature;
    if (a >= 4) {
      // Laplace continued fraction for phi(a) / Phi(-a).
      double fraction = 0;
      for (int k = 64; k >= 1; k--) {
        fraction = k / (a + fraction);
      }
      tailMills = a + fraction;
      tailCurvature = tailMills * fraction;
      logTail = logDensity - Math.log(tailMills);
    } else {
      // Rational approximation to the normal tail; coefficients are in descending order.
      double numerator =
          ((((((0.0352624965998911 * a + 0.700383064443688) * a + 6.37396220353165) * a
                                      + 33.912866078383)
                                  * a
                              + 112.079291497871)
                          * a
                      + 221.213596169931)
                  * a
              + 220.206867912376);
      double denominator =
          (((((((0.0883883476483184 * a + 1.75566716318264) * a + 16.064177579207) * a
                                              + 86.7807322029461)
                                          * a
                                      + 296.564248779674)
                                  * a
                              + 637.333633378831)
                          * a
                      + 793.826512519948)
                  * a
              + 440.413735824752);
      logTail = -a * a / 2 + Math.log(numerator / denominator);
      tailMills = Math.exp(logDensity - logTail);
      tailCurvature = tailMills * (tailMills - a);
    }
    if (x <= 0) {
      return new Normal(logTail, tailMills, tailCurvature);
    }
    double logCdf = Math.log1p(-Math.exp(logTail));
    double mills = Math.exp(logDensity - logCdf);
    return new Normal(logCdf, mills, mills == 0 ? 0 : mills * (x + mills));
  }

  /** Builds a symmetric Gauss-Legendre rule on [-1,1]; row zero holds nodes and row one weights. */
  private static double[][] rule(int size) {
    double[][] rule = new double[2][size];
    for (int i = 0; i < (size + 1) / 2; i++) {
      double root = Math.cos(Math.PI * (i + 0.75) / (size + 0.5));
      double derivative = 0;
      for (int iteration = 0; iteration < 20; iteration++) {
        double previous = 0;
        double value = 1;
        for (int degree = 1; degree <= size; degree++) {
          double next = ((2 * degree - 1) * root * value - (degree - 1) * previous) / degree;
          previous = value;
          value = next;
        }
        derivative = size * (root * value - previous) / (root * root - 1);
        double change = value / derivative;
        root -= change;
        if (Math.abs(change) < 1e-15) {
          break;
        }
      }
      double weight = 2 / ((1 - root * root) * derivative * derivative);
      rule[0][i] = -root;
      rule[0][size - 1 - i] = root;
      rule[1][i] = weight;
      rule[1][size - 1 - i] = weight;
    }
    return rule;
  }

  /**
   * Log probability and its derivatives in the full available-alternative utility coordinates.
   *
   * <p>The score is the gradient of log P and the Hessian is its second derivative, not the
   * negative information matrix. Arrays are local mutable workspaces and must not be retained as
   * shared writable state. The zero initialization also represents a singleton choice set.
   */
  static final class Evaluation {
    double logProbability;
    final double[] score;
    final double[][] hessian;

    Evaluation(int size) {
      score = new double[size];
      hessian = new double[size][size];
    }
  }

  /**
   * Stable standard-normal log-CDF data for a single argument.
   *
   * <p>Mills is phi/Phi, the first derivative of ln(Phi); curvature is its nonnegative negative
   * second derivative. These quantities retain useful precision in the negative tail.
   */
  private static final class Normal {
    private final double logCdf;
    private final double mills;
    private final double curvature;

    private Normal(double logCdf, double mills, double curvature) {
      this.logCdf = logCdf;
      this.mills = mills;
      this.curvature = curvature;
    }
  }
}
