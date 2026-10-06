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
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Allocates modal demand in proportion to 1 / (k(mode, group) * cheapest modal cost).
 *
 * <p>Groups with no proportional parameters retain ordinary inverse-cost splitting (all k = 1). A
 * calibrated group requires a positive finite factor for every available mode and a reference
 * factor of one. Factors apply only to modal choice; physical route costs and inverse-cost shares
 * among routes of the same mode remain unchanged. Calibrated groups require unimodal alternatives,
 * matching the estimator's cost collection. Legacy uncalibrated intermodal splitting is supported.
 *
 * <p>The estimated keys are {@code proportional.costFactor.mode.group} and {@code
 * proportional.reference.group}. They are read from the table named by {@code @paramTable}, or from
 * the cost file for an older project. Any entry for a group makes it calibrated: missing factors for
 * available modes then fail instead of defaulting to one. A group with no entries starts with all
 * factors one, independently of factors loaded for the previous group. The reference mode may be
 * unavailable for a particular OD; its factor only establishes the overall cost scale.
 *
 * <p>Assignment initializes and clones the method before workers call {@link #initializeGroup}.
 * Each group load allocates a new array of log factors, so clones do not modify each other's group
 * parameters. {@link #split} changes only modal/path shares and scratch utilities. Log-domain
 * evaluation avoids forming potentially overflowing products {@code k*C}. Estimation is performed
 * by {@link ProportionalEstimator}, not by assignment-time split calls.
 */
public class Proportional extends ModalSplitMethod {
  static final String COST_FACTOR_PREFIX = "proportional.costFactor.";
  static final String REFERENCE_PREFIX = "proportional.reference.";
  private static final I18n i18n = Environment.getI18n();
  private double[] logFactors = new double[NodusC.MAXMM];
  private boolean calibrated;
  private Properties choiceParameters;
  private boolean usePivots;
  private double pivotMaxAbs;

  /**
   * Associates this modal split with a project.
   *
   * @param nodusProject project to associate with this method
   */
  public Proportional(NodusProject nodusProject) {
    super(nodusProject);
  }

  @Override
  public String getName() {
    return "Proportional";
  }

  @Override
  public String getPrettyName() {
    return i18n.get(ModalSplitMethod.class, "Proportional", "Proportional");
  }

  @Override
  public void initialize(AssignmentParameters assignmentParameters) {
    super.initialize(assignmentParameters);
    choiceParameters = ModalParameterTable.load(assignmentParameters, getName());
    usePivots = Boolean.parseBoolean(
        choiceParameters.getProperty(ModalParameterTable.PIVOTS, "false"));
    pivotMaxAbs = usePivots ? ModalParameterTable.pivotMaxAbs(choiceParameters)
        : ModalParameterTable.DEFAULT_PIVOT_MAX_ABS;
  }

  /**
   * Loads positive cost factors for a group or restores all factors to one for an uncalibrated
   * group.
   *
   * @param group commodity group whose proportional keys are read
   * @throws IllegalArgumentException for malformed factors or a missing/nonunit reference factor
   */
  @Override
  public void initializeGroup(int group) {
    super.initializeGroup(group);
    Properties costs = choiceParameters;
    logFactors = new double[NodusC.MAXMM];
    Arrays.fill(logFactors, Double.NaN);
    String suffix = "." + group;
    calibrated = costs.containsKey(REFERENCE_PREFIX + group);
    for (String key : costs.stringPropertyNames()) {
      if (key.startsWith(COST_FACTOR_PREFIX) && key.endsWith(suffix)) {
        calibrated = true;
        try {
          int mode =
              Integer.parseInt(
                  key.substring(COST_FACTOR_PREFIX.length(), key.length() - suffix.length()));
          double factor = Double.parseDouble(costs.getProperty(key));
          if (mode <= 0 || mode >= NodusC.MAXMM || !Double.isFinite(factor) || factor <= 0) {
            throw new NumberFormatException();
          }
          logFactors[mode] = Math.log(factor);
        } catch (NumberFormatException exception) {
          throw new IllegalArgumentException("Invalid proportional cost factor: " + key, exception);
        }
      }
    }
    if (!calibrated) {
      Arrays.fill(logFactors, 0);
      return;
    }
    try {
      int reference = Integer.parseInt(costs.getProperty(REFERENCE_PREFIX + group, ""));
      if (reference <= 0 || reference >= NodusC.MAXMM || logFactors[reference] != 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Proportional group " + group + " requires a reference mode with cost factor 1",
          exception);
    }
  }

  /**
   * Applies multiplicative modal cost factors while retaining inverse-cost allocation among routes.
   *
   * @param odCell routed OD record; the supplied alternatives carry the costs used here
   * @param modes mutable feasible mode/route alternatives for this OD
   * @return false for an empty choice set, true after assigning normalized shares
   * @throws IllegalArgumentException for missing factors, invalid costs or calibrated intermodal
   *     paths
   */
  @Override
  public boolean split(ODCell odCell, List<PathsForMode> modes) {
    if (modes.isEmpty()) {
      return false;
    }
    double maximum = Double.NEGATIVE_INFINITY;
    for (PathsForMode mode : modes) {
      int id = mode.loadingMode;
      if (id <= 0 || id >= logFactors.length || !Double.isFinite(logFactors[id])) {
        throw new IllegalArgumentException(
            "No proportional cost factor for mode " + id + ", group " + getCurrentGroup());
      }
      for (Path path : mode.pathList) {
        if (calibrated && path.intermodal) {
          throw new IllegalArgumentException(
              "Calibrated proportional choice requires unimodal routes");
        }
        if (!Double.isFinite(path.weights.getCost()) || path.weights.getCost() <= 0) {
          throw new IllegalArgumentException(
              "Proportional choice requires finite, positive route costs");
        }
      }
      // Work in logs so the product k*C and reciprocal costs cannot overflow.
      mode.utility = -logFactors[id] - Math.log(mode.cheapestPathWeights.getCost());
      if (calibrated && usePivots && odCell != null) {
        mode.utility += ModalParameterTable.pivot(
            choiceParameters, id, odCell.getOriginNodeId(), odCell.getDestinationNodeId(),
            odCell.getGroup(), pivotMaxAbs);
      }
      maximum = Math.max(maximum, mode.utility);
    }
    double denominator = 0;
    for (PathsForMode mode : modes) {
      mode.marketShare = Math.exp(mode.utility - maximum);
      denominator += mode.marketShare;
    }
    for (PathsForMode mode : modes) {
      mode.marketShare /= denominator;
      double minimum = mode.cheapestPathWeights.getCost();
      double routeDenominator = 0;
      for (Path path : mode.pathList) {
        routeDenominator += minimum / path.weights.getCost();
      }
      for (Path path : mode.pathList) {
        path.marketShare = mode.marketShare * (minimum / path.weights.getCost()) / routeDenominator;
      }
    }
    return true;
  }
}
