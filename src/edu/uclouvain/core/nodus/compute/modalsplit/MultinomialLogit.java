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
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Assignment-time conditional logit using a modal constant and a shared log-cost coefficient.
 *
 * <p>For every available mode, {@code V = intercept + beta * ln(C)}, where {@code C} is its
 * cheapest admissible route cost; modal probabilities are the softmax of these utilities. A common
 * utility shift avoids exponential overflow. Unavailable modes are omitted from the denominator,
 * and a single available mode receives all demand. Within a mode, its flow is split in proportion
 * to inverse route cost; this second-stage rule is not estimated from modal OD matrices.
 *
 * <p>When {@code @paramTable} names a database table, coefficients and optional pivots are read
 * from its {@code param_key,param_value} rows. Otherwise legacy cost-file coefficients are read per
 * commodity group from {@code (intercept).mode.group}, {@code log(cost).mode.group} and {@code
 * mnl.reference.group}. All modes must share the same beta, which may have either sign or be zero.
 * The reference intercept must be zero; for older R demo files, the smallest mode with a slope is
 * the default reference and its missing intercept defaults to zero. Incomplete or invalid saved
 * coefficients are errors.
 *
 * <p>If this model has no parameters at all for a group, assignment warns once and uses {@code V =
 * -C}: cost factor one and zero modal constants. This preserves the former built-in MNL, including
 * exponential allocation among a mode's routes. Defaults are local to the assignment; they are
 * never written to the cost file. Parameters for other models or groups do not disable this
 * fallback.
 *
 * <p>Assignment calls {@link #initialize} and then {@link #initializeGroup} for each group. Workers
 * clone the modal method and load their own group arrays; split calls mutate only their supplied
 * {@link PathsForMode} and {@link Path} shares/utilities, never physical costs or coefficients. All
 * calibrated alternatives need initialized parameters and positive finite route costs. Calibrated
 * intermodal routes are rejected because the embedded estimation model uses unimodal alternatives.
 * The default model accepts intermodal routes and any finite cost, as no logarithm is needed.
 *
 * <p>{@link MultinomialProbit} reuses this lifecycle and route allocation, overriding probability
 * calculation and coefficient namespaces. These classes apply saved parameters; they never trigger
 * estimation or write cost files during assignment.
 *
 * @see LogCostLogitEstimator
 * @see LogitCalibration
 */
public class MultinomialLogit extends ModalSplitMethod {
  private double[] intercepts;
  private double beta;
  private boolean calibrated;
  private Properties choiceParameters;
  private boolean usePivots;
  private double pivotMaxAbs;
  // Shallow worker clones share this flag, but initialize starts a new assignment's warning state.
  private AtomicBoolean defaultsWarningShown = new AtomicBoolean();

  /**
   * Associates this modal split with a project.
   *
   * @param project the Nodus project
   */
  public MultinomialLogit(NodusProject project) {
    super(project);
  }

  @Override
  public String getName() {
    return "MNL";
  }

  @Override
  public String getPrettyName() {
    return Environment.getI18n().get(ModalSplitMethod.class, "MNL", "Multinomial logit");
  }

  /**
   * Starts an assignment and resets the warning shared by its worker clones.
   *
   * @param assignmentParameters costs and routing controls for this assignment
   */
  @Override
  public void initialize(AssignmentParameters assignmentParameters) {
    super.initialize(assignmentParameters);
    intercepts = null;
    calibrated = false;
    choiceParameters = ModalParameterTable.load(assignmentParameters, getName());
    usePivots =
        Boolean.parseBoolean(choiceParameters.getProperty(ModalParameterTable.PIVOTS, "false"));
    pivotMaxAbs =
        usePivots
            ? ModalParameterTable.pivotMaxAbs(choiceParameters)
            : ModalParameterTable.DEFAULT_PIVOT_MAX_ABS;
    defaultsWarningShown = new AtomicBoolean();
  }

  /**
   * Loads and validates this group's saved coefficients before its OD records are split.
   *
   * <p>Allocates a fresh intercept array, marking unconfigured modes as NaN. Validates all
   * configured slopes against the common coefficient and checks the reference normalization. The
   * probit subclass supplies different property prefixes through the protected namespace hooks.
   *
   * <p>A group without any entries in this model's namespace uses the legacy {@code -C} utility. A
   * reference or intercept entry without slopes is an incomplete calibration, not a default.
   *
   * @param group commodity group whose coefficients must be loaded
   * @throws IllegalArgumentException for incomplete, malformed or inconsistent saved coefficients
   */
  @Override
  public void initializeGroup(int group) {
    super.initializeGroup(group);
    Properties costs = choiceParameters;
    intercepts = null;
    TreeSet<Integer> modes = new TreeSet<>();
    String suffix = "." + group;
    calibrated = costs.containsKey(referenceKey() + group);
    for (String key : costs.stringPropertyNames()) {
      if (key.startsWith(coefficientPrefix() + "(intercept).") && key.endsWith(suffix)) {
        calibrated = true;
      }
      if (key.startsWith(coefficientPrefix() + "log(cost).") && key.endsWith(suffix)) {
        calibrated = true;
        String mode =
            key.substring(
                (coefficientPrefix() + "log(cost).").length(), key.length() - suffix.length());
        try {
          int id = Integer.parseInt(mode);
          if (id <= 0 || id >= NodusC.MAXMM) {
            throw new NumberFormatException();
          }
          modes.add(id);
        } catch (NumberFormatException exception) {
          throw new IllegalArgumentException(
              "Invalid " + getName() + " parameter: " + key, exception);
        }
      }
    }
    if (!calibrated) {
      intercepts = new double[NodusC.MAXMM];
      beta = -1;
      warnAboutDefaults();
      return;
    }
    if (modes.isEmpty()) {
      throw new IllegalArgumentException(
          "Incomplete "
              + getName()
              + " coefficients for group "
              + group
              + ": log-cost coefficients are missing. Use Project > Modal choice estimation"
              + " or supply log-cost coefficients.");
    }
    // Existing R demo files omit the intercept of their first (reference) mode.
    int reference =
        Integer.parseInt(
            costs.getProperty(referenceKey() + group, Integer.toString(modes.first())));
    if (!modes.contains(reference)) {
      throw new IllegalArgumentException(
          getName() + " reference mode has no coefficient for group " + group);
    }
    intercepts = new double[NodusC.MAXMM];
    java.util.Arrays.fill(intercepts, Double.NaN);
    beta = parameter(costs, coefficientPrefix() + "log(cost)." + modes.first() + suffix, null);
    for (int mode : modes) {
      double slope = parameter(costs, coefficientPrefix() + "log(cost)." + mode + suffix, null);
      if (Math.abs(slope - beta) > 1e-10 * Math.max(1, Math.abs(beta))) {
        throw new IllegalArgumentException(
            getName() + " log-cost coefficients must agree within group " + group);
      }
      intercepts[mode] =
          parameter(
              costs,
              coefficientPrefix() + "(intercept)." + mode + suffix,
              mode == reference ? "0" : null);
    }
    if (intercepts[reference] != 0) {
      throw new IllegalArgumentException(
          getName() + " reference intercept must be zero for group " + group);
    }
  }

  /** Shows one localized warning per assignment, including when several workers need defaults. */
  private void warnAboutDefaults() {
    NodusProject project = getAssignmentParameters().getNodusProject();
    if (project == null
        || project.getNodusMapPanel() == null
        || !defaultsWarningShown.compareAndSet(false, true)) {
      return;
    }
    String message =
        MessageFormat.format(
            Environment.getI18n()
                .get(
                    MultinomialLogit.class,
                    "DefaultParameters",
                    "Some commodity groups have no {0} modal-choice parameters.\n"
                        + "For these groups, assignment will use a cost factor of 1 and modal"
                        + " constants of 0 (V = -C).\n"
                        + "The cost functions file will not be modified."),
            getPrettyName());
    Runnable warning =
        () ->
            project.getNodusMapPanel().showAssignmentMessage(message, JOptionPane.WARNING_MESSAGE);
    if (SwingUtilities.isEventDispatchThread()) {
      warning.run();
    } else {
      try {
        SwingUtilities.invokeAndWait(warning);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new CancellationException("Assignment cancelled");
      } catch (InvocationTargetException exception) {
        throw new IllegalStateException("Unable to display the modal-choice warning", exception);
      }
    }
  }

  /**
   * Reads a finite coefficient, using a fallback only for explicitly permitted reference defaults.
   */
  private double parameter(Properties costs, String key, String fallback) {
    String value = costs.getProperty(key, fallback);
    try {
      double number = Double.parseDouble(value);
      if (Double.isFinite(number)) {
        return number;
      }
    } catch (NumberFormatException | NullPointerException exception) {
      // Report the offending cost-file entry rather than silently substituting a coefficient.
    }
    throw new IllegalArgumentException("Missing or invalid " + getName() + " parameter: " + key);
  }

  /**
   * Returns the coefficient namespace used in the cost file.
   *
   * @return empty for the R-compatible logit coefficients
   */
  protected String coefficientPrefix() {
    return "";
  }

  /**
   * Returns the reference-mode property prefix.
   *
   * @return model-specific reference key, without the group ID
   */
  protected String referenceKey() {
    return "mnl.reference.";
  }

  /**
   * Computes probabilities on the available set, in its current order.
   *
   * @param utilities finite systematic utilities
   * @return probabilities summing to one
   */
  protected double[] probabilities(double[] utilities) {
    double maximum = java.util.Arrays.stream(utilities).max().orElseThrow();
    double[] shares = new double[utilities.length];
    double sum = 0;
    for (int i = 0; i < shares.length; i++) {
      shares[i] = Math.exp(utilities[i] - maximum);
      sum += shares[i];
    }
    for (int i = 0; i < shares.length; i++) {
      shares[i] /= sum;
    }
    return shares;
  }

  /**
   * Computes shares for the feasible alternatives of one OD record using the initialized group.
   *
   * @param demand routed OD record; availability and costs are supplied by the alternatives
   * @param modes mutable available alternatives, each containing its admissible routes
   * @return false for an empty choice set, true after assigning normalized modal and route shares
   * @throws IllegalStateException if no group coefficients have been initialized
   * @throws IllegalArgumentException for missing modal coefficients, invalid costs or intermodal
   *     paths
   */
  @Override
  public boolean split(ODCell demand, List<PathsForMode> modes) {
    if (modes.isEmpty()) {
      return false;
    }
    if (intercepts == null) {
      throw new IllegalStateException(
          getName() + " parameters must be initialized before assignment");
    }
    double[] utilities = new double[modes.size()];
    int index = 0;
    double baseLogCost = calibrated ? Math.log(modes.get(0).cheapestPathWeights.getCost()) : 0;
    for (PathsForMode mode : modes) {
      int id = mode.loadingMode;
      if (id <= 0 || id >= intercepts.length || !Double.isFinite(intercepts[id])) {
        throw new IllegalArgumentException(
            "No " + getName() + " parameters for mode " + id + ", group " + getCurrentGroup());
      }
      for (Path path : mode.pathList) {
        if (calibrated && path.intermodal) {
          throw new IllegalArgumentException(
              getName() + " currently requires unimodal alternatives");
        }
        if (!Double.isFinite(path.weights.getCost())
            || (calibrated && path.weights.getCost() <= 0)) {
          throw new IllegalArgumentException(
              getName()
                  + (calibrated
                      ? " requires finite, positive route costs"
                      : " requires finite route costs"));
        }
      }
      mode.utility =
          calibrated
              ? intercepts[id] + beta * (Math.log(mode.cheapestPathWeights.getCost()) - baseLogCost)
              : -mode.cheapestPathWeights.getCost();
      if (calibrated && usePivots && demand != null) {
        mode.utility +=
            ModalParameterTable.pivot(
                choiceParameters,
                id,
                demand.getOriginNodeId(),
                demand.getDestinationNodeId(),
                demand.getGroup(),
                pivotMaxAbs);
      }
      if (!Double.isFinite(mode.utility)) {
        throw new IllegalArgumentException(getName() + " utility exceeds the numeric range");
      }
      utilities[index++] = mode.utility;
    }
    double[] shares = probabilities(utilities);
    index = 0;
    for (PathsForMode mode : modes) {
      mode.marketShare = shares[index++];
      double minimum = mode.cheapestPathWeights.getCost();
      double routeDenominator = 0;
      for (Path path : mode.pathList) {
        routeDenominator += routeWeight(minimum, path.weights.getCost());
      }
      for (Path path : mode.pathList) {
        path.marketShare =
            mode.marketShare * routeWeight(minimum, path.weights.getCost()) / routeDenominator;
      }
    }
    return true;
  }

  /** Uses the legacy exponential route split for defaults and inverse costs for fitted models. */
  private double routeWeight(double minimum, double cost) {
    return calibrated ? minimum / cost : Math.exp(minimum - cost);
  }
}
