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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Coordinates standalone modal estimation from observed OD tables.
 *
 * <p>Despite its historical name, this workflow supports logit, probit and proportional cost
 * factors. {@link ModalChoiceEstimationDlg} supplies the selected method, cost file, observed-table
 * mapping and routing controls. The workflow reads the union of observed {@code (group, origin,
 * destination, class)} records, sums duplicate quantities within each mode, and uses a uniquely
 * named scratch OD table to request modal costs from the normal routing code. No path or volume
 * result tables are published and no assignment scripts run.
 *
 * <p>The selected file supplies base costs, with commodity/class overrides; numbered scenario
 * overrides are ignored. Routing makes one search per mode/means without markup and records the
 * cheapest admissible unimodal cost. Temporary equal shares ensure every feasible alternative is
 * visited, independently of its observed quantity. Costs remain in memory and are never averaged
 * over routes. Missing modes with zero observed flow are valid; positive observed flow without a
 * route excludes the entire OD record from the fit, preserving the retained observations' shares.
 *
 * <p>Each commodity group is fitted separately, pooling its OD classes while retaining their
 * class-specific route costs. The dialog stores successful estimates in a database parameter table,
 * writes diagnostics to a sibling file named {@code <cost-file-stem>_params.txt}, and adds only
 * {@code @paramTable} to the selected cost file. The earlier cost-file overloads remain available
 * for existing scripted callers. Successful saving also refreshes the caller's in-memory cost
 * properties. The caller's assignment OD selection, scenario and routing controls remain unchanged.
 * Exclusions apply only to estimation and never filter a subsequent assignment's demand.
 *
 * <p>Use a fresh instance for one estimation and close it in a try-with-resources block. The class
 * owns its scratch table but borrows the project's JDBC connection, which it never closes. It uses
 * isolated connections for scratch-table DDL and parameter saving so database DDL cannot commit
 * unrelated project changes. The caller must serialize computations using the project's assignment
 * resources; this workflow is not reentrant. Routing workers share the observation map only while
 * its keys are stable and own distinct OD records. After routing, independent commodity groups fit
 * on a bounded worker pool. Workers share no JDBC connection; the coordinating thread saves the
 * completed result once.
 *
 * <p>Preparation and fitting report indeterminate progress; routing reports its own progress.
 * Cancellation is propagated as {@link CancellationException}, not a failed statistical fit.
 * Closing after success, failure or cancellation removes the scratch demand table.
 */
public final class LogitCalibration implements AutoCloseable {
  private final AssignmentParameters parameters;
  private final LogitCalibrationSettings settings;
  private final Connection connection;
  private final Map<String, Observation> observations = new LinkedHashMap<>();
  private final int[] modes;
  private final String method;
  private String outputTable;
  private String mergedTable = "";
  private boolean overwriteMergedTable;
  private boolean estimatePivots;
  private boolean conditional = true;
  private double pivotMaxAbs = ModalParameterTable.DEFAULT_PIVOT_MAX_ABS;
  private final StringBuilder skippedDetails = new StringBuilder();
  private String temporaryTable;
  private Connection scratchConnection;

  /**
   * Creates a standalone estimator.
   *
   * @param parameters cost file, modal method, detour and threads; scenario, routing iterations,
   *     markup and OD selection are not used
   * @param settings observed modal tables and reference mode
   */
  public LogitCalibration(AssignmentParameters parameters, LogitCalibrationSettings settings) {
    this(parameters, settings, false);
  }

  /**
   * Selects common or mode-specific cost coefficients before starting estimation.
   *
   * @param conditional true for a common coefficient, false for mode-specific logit/probit slopes
   */
  public void setConditional(boolean conditional) {
    this.conditional = conditional;
  }

  /**
   * Creates a standalone estimator; no assignment is launched and no scenario is replaced.
   *
   * <p>The former terminal-logging argument is ignored; diagnostics are saved with the estimate.
   *
   * @param parameters cost file, modal method, detour and threads; scenario, routing iterations,
   *     markup and OD selection are not used
   * @param settings observed modal tables and reference mode
   * @param logToTerminal retained for binary compatibility; ignored
   */
  @Deprecated
  public LogitCalibration(
      AssignmentParameters parameters, LogitCalibrationSettings settings, boolean logToTerminal) {
    this.parameters = parameters;
    method = parameters.getModalSplitMethodName();
    this.settings = settings;
    connection = parameters.getNodusProject().getMainJDBCConnection();
    modes = settings.getTables().keySet().stream().mapToInt(Integer::intValue).toArray();
  }

  /**
   * Fits and saves coefficients using a temporary sum of the observed modal matrices.
   *
   * <p>Routing only discovers feasible alternatives and their costs. This operation does not
   * publish path or volume results, run assignment scripts, or alter the selected assignment OD
   * matrix. Callers must close the estimator to remove its temporary demand table, including on
   * failure or cancellation. All groups must fit before the cost file is updated.
   *
   * @param exact whether to use exact multi-flow routing
   * @return false when every observed OD record was skipped because of missing routes
   * @throws CancellationException when interruption or the progress control cancels the operation
   * @throws Exception if data validation, routing, estimation or saving fails
   */
  public boolean estimate(boolean exact) throws Exception {
    return estimate(exact, parameters.getCostFunctionsPath());
  }

  /**
   * Fits using the selected source cost file and saves its updated contents to another file.
   *
   * <p>Calling this overload authorizes replacement of an existing output. Interactive callers must
   * confirm that replacement first. The source and destination are checked for intervening edits
   * before saving; failure or cancellation leaves both untouched. Routing always uses the source,
   * and the caller's selected source path remains unchanged.
   *
   * @param exact Whether to use exact multi-flow routing
   * @param output File to create or replace with source costs and estimated parameters
   * @return False when every observed OD record was skipped because of missing routes
   * @throws Exception If validation, routing, estimation or saving fails, or on cancellation
   */
  public boolean estimate(boolean exact, java.nio.file.Path output) throws Exception {
    return estimate(exact, LogitCostFile.target(parameters.getCostFunctionsPath(), output));
  }

  /** Runs a dialog-approved save using the destination snapshot captured before confirmation. */
  boolean estimate(boolean exact, LogitCostFile.Target target) throws Exception {
    if (!LogitCalibrationSettings.supportsMethod(method)) {
      throw new IllegalArgumentException("Unsupported calibration method: " + method);
    }
    settings.validate();
    target.checkUnchanged();
    final byte[] original = target.original;
    Properties sourceCosts = new Properties();
    sourceCosts.load(new ByteArrayInputStream(original));
    parameters.getNodusProject().getNodusMapPanel().startProgress(0);
    try {
      for (int column = 0; column < modes.length; column++) {
        readTable(settings.getTables().get(modes[column]), column);
      }
      observations.values().removeIf(row -> row.total() == 0);
      if (observations.isEmpty()) {
        throw new IllegalArgumentException(
            "The observed modal matrices contain no positive demand");
      }
      createDemandTable();
    } finally {
      parameters.getNodusProject().getNodusMapPanel().stopProgress();
    }
    AssignmentParameters preliminary = preliminaryParameters(sourceCosts);
    parameters.getNodusProject().getNodusMapPanel().startProgress(0);
    try {
      checkCancelled("Computing " + method + " calibration costs");
      Assignment.computeModalChoiceCosts(preliminary, exact, new Collector());
    } finally {
      parameters.getNodusProject().getNodusMapPanel().stopProgress();
    }

    String skippedSummary;
    parameters.getNodusProject().getNodusMapPanel().startProgress(0);
    try {
      skippedSummary = skipUnroutableObservations();
    } finally {
      parameters.getNodusProject().getNodusMapPanel().stopProgress();
    }
    if (observations.isEmpty()) {
      if (outputTable != null) {
        writeReport(
            method
                + " calibration: no usable OD observations remain. No coefficients were saved.\n"
                + skippedSummary
                + skippedDetails);
      }
      return false;
    }
    Map<Integer, List<Observation>> groups = new TreeMap<>();
    for (Observation row : observations.values()) {
      groups.computeIfAbsent(row.group, key -> new ArrayList<>()).add(row);
    }
    final Properties fitted = new Properties();
    StringBuilder report = new StringBuilder("# Estimated by Nodus at " + Instant.now() + "\n");
    report.append("# Observed matrices: ").append(settings.encode()).append('\n');
    report.append("# Model: ").append(method).append('\n');
    if (!"Proportional".equals(method)) {
      report
          .append("# Cost coefficients: ")
          .append(conditional ? "conditional (common)" : "mode-specific")
          .append("; negative coefficients required.\n");
    }
    report
        .append("# Routing: ")
        .append(exact ? "Exact multi-flow" : "Fast multi-flow")
        .append(", base costs (no numbered scenario overrides), one search per mode/means")
        .append(", no cost markup")
        .append(", max detour=")
        .append(parameters.getMaxDetour())
        .append('\n');
    skippedSummary.lines().forEach(line -> report.append("# ").append(line).append('\n'));
    report.append("# Reference mode: ").append(settings.getReferenceMode()).append('\n');
    if ("MNP".equals(method)) {
      report.append("# Probit errors: independent N(0,1); deterministic normal integration.\n");
    } else if ("Proportional".equals(method)) {
      report.append("# Modal shares proportional to 1/(k*C); reference factor k=1.\n");
      report.append("# Positive modal cost factors only; log-cost coefficient fixed at -1.\n");
    }
    report.append("# Cost per mode: cheapest admissible route; no averaging across routes.\n");
    report.append("# SEs use original quantities as frequency weights; not robust/clustered.\n");
    parameters.getNodusProject().getNodusMapPanel().startProgress(0);
    try {
      Map<Integer, GroupFit> results = fitGroups(groups, fitted);
      int pivotCount = 0;
      for (GroupFit result : results.values()) {
        report.append(result.report);
        pivotCount += result.pivots;
      }
      checkCancelled("Saving " + method + " coefficients");
      report.append(skippedDetails);
      if (outputTable != null) {
        fitted.setProperty(ModalParameterTable.METHOD, method);
        fitted.setProperty(ModalParameterTable.PIVOTS, Boolean.toString(estimatePivots));
        if (estimatePivots) {
          fitted.setProperty(ModalParameterTable.PIVOT_MAX_ABS, Double.toString(pivotMaxAbs));
        }
        report
            .append("# Pivot calibration: ")
            .append(
                estimatePivots
                    ? "mode_od_group, 80 iterations maximum, damping=0.7, "
                        + "maximum step=2, absolute bound="
                        + pivotMaxAbs
                        + ", epsilon=1e-6 tonnes"
                    : "disabled")
            .append('\n');
        if (estimatePivots) {
          report
              .append("# Estimated bounded OD/group pivot constants: ")
              .append(pivotCount)
              .append('\n');
        }
        java.util.TreeSet<String> reportedKeys = new java.util.TreeSet<>();
        for (String key : fitted.stringPropertyNames()) {
          if (!key.startsWith("pivot.")) {
            reportedKeys.add(key);
          }
        }
        for (String key : reportedKeys) {
          report.append(key).append('=').append(fitted.getProperty(key)).append('\n');
        }
        try (Connection parameterConnection = openParameterConnection();
            ModalMatrixMerge.Prepared merge =
                mergedTable.isEmpty()
                    ? null
                    : ModalMatrixMerge.prepare(
                        parameterConnection,
                        connection,
                        mergedTable,
                        settings.getTables().values(),
                        outputTable,
                        overwriteMergedTable)) {
          Properties saved =
              saveTableOutputs(
                  parameterConnection,
                  outputTable,
                  fitted,
                  target,
                  reportPath(),
                  report,
                  () ->
                      !Thread.currentThread().isInterrupted()
                          && parameters
                              .getNodusProject()
                              .getNodusMapPanel()
                              .updateProgress("Saving modal parameters"),
                  merge == null ? () -> {} : merge::write);
          if (merge != null) {
            merge.complete();
          }
          parameters.getCostFunctions().clear();
          parameters.getCostFunctions().putAll(saved);
        }
      } else {
        Properties saved = LogitCostFile.save(target, original, fitted, report.toString(), method);
        parameters.getCostFunctions().clear();
        parameters.getCostFunctions().putAll(saved);
      }
    } finally {
      parameters.getNodusProject().getNodusMapPanel().stopProgress();
    }
    return true;
  }

  /**
   * Fits the behavioral model and optional OD/group pivots into a named parameter table.
   *
   * @param exact whether to use exact multi-flow routing
   * @param table name of the parameter table to create or replace
   * @param pivots whether to estimate OD/group pivot constants
   * @return false when every observed OD record was skipped because of missing routes
   * @throws Exception if validation, routing, estimation or saving fails, or on cancellation
   */
  public boolean estimateToTable(boolean exact, String table, boolean pivots) throws Exception {
    return estimateToTable(exact, table, pivots, ModalParameterTable.DEFAULT_PIVOT_MAX_ABS);
  }

  /**
   * Fits the selected model with an optional user-chosen bound on OD/group pivots.
   *
   * @param exact whether to use exact multi-flow routing
   * @param table name of the parameter table to create or replace
   * @param pivots whether to estimate OD/group pivot constants
   * @param maxAbs maximum absolute value of an estimated pivot
   * @return false when every observed OD record was skipped because of missing routes
   * @throws Exception if validation, routing, estimation or saving fails, or on cancellation
   */
  public boolean estimateToTable(boolean exact, String table, boolean pivots, double maxAbs)
      throws Exception {
    return estimateToTable(exact, table, pivots, maxAbs, "", false);
  }

  /**
   * Saves an optional merged assignment matrix in the same transaction as the parameters.
   *
   * @param exact whether to use exact multi-flow routing
   * @param table name of the parameter table to create or replace
   * @param pivots whether to estimate OD/group pivot constants
   * @param maxAbs maximum absolute value of an estimated pivot
   * @param mergedMatrix name of the optional merged assignment matrix
   * @param overwrite whether an existing merged matrix may be replaced
   * @return false when every observed OD record was skipped because of missing routes
   * @throws Exception if validation, routing, estimation or saving fails, or on cancellation
   */
  public boolean estimateToTable(
      boolean exact,
      String table,
      boolean pivots,
      double maxAbs,
      String mergedMatrix,
      boolean overwrite)
      throws Exception {
    outputTable = ModalParameterTable.validateName(table);
    mergedTable = ModalMatrixMerge.validateName(mergedMatrix, settings.getTables().values(), table);
    overwriteMergedTable = overwrite;
    estimatePivots = pivots;
    pivotMaxAbs = ModalParameterTable.validatePivotMaxAbs(maxAbs);
    java.nio.file.Path report = reportPath();
    if (Files.exists(report) && (!Files.isRegularFile(report) || !Files.isWritable(report))) {
      throw new java.io.IOException("Cannot replace estimation report: " + report);
    }
    return estimate(
        exact,
        LogitCostFile.target(parameters.getCostFunctionsPath(), parameters.getCostFunctionsPath()));
  }

  /**
   * Removes whole OD records having positive observed flow on at least one unroutable mode.
   *
   * <p>Coverage denominators use the original observations in each group/mode scope. Excluded
   * quantity includes both genuinely unroutable flow and otherwise routable flow discarded with the
   * same record. Modal record counts include positive modal demand only. The returned report is
   * preserved for the estimation report.
   */
  private String skipUnroutableObservations() throws Exception {
    final int total = observations.size();
    List<Observation> skipped = new ArrayList<>();
    RouteStatistics overall = new RouteStatistics();
    RouteStatistics[] byMode = newRouteStatistics();
    Map<Integer, RouteStatistics> byGroup = new TreeMap<>();
    Map<Integer, RouteStatistics[]> byGroupAndMode = new TreeMap<>();
    int checked = 0;
    Iterator<Observation> iterator = observations.values().iterator();
    while (iterator.hasNext()) {
      if (checked++ % 256 == 0) {
        checkCancelled("Checking calibration routes");
      }
      Observation row = iterator.next();
      List<String> missing = new ArrayList<>();
      double unroutableQuantity = 0;
      for (int mode = 0; mode < modes.length; mode++) {
        if (row.quantities[mode] > 0 && !Double.isFinite(row.costs[mode])) {
          missing.add(modes[mode] + " (observed quantity=" + row.quantities[mode] + ")");
          unroutableQuantity += row.quantities[mode];
        }
      }
      final boolean excluded = !missing.isEmpty();
      overall.add(row.total(), unroutableQuantity, excluded);
      byGroup
          .computeIfAbsent(row.group, key -> new RouteStatistics())
          .add(row.total(), unroutableQuantity, excluded);
      RouteStatistics[] groupModes =
          byGroupAndMode.computeIfAbsent(row.group, key -> newRouteStatistics());
      for (int mode = 0; mode < modes.length; mode++) {
        double missingQuantity = Double.isFinite(row.costs[mode]) ? 0 : row.quantities[mode];
        byMode[mode].add(row.quantities[mode], missingQuantity, excluded);
        groupModes[mode].add(row.quantities[mode], missingQuantity, excluded);
      }
      if (excluded) {
        skippedDetails.append(
            "# "
                + method
                + " calibration: skipping OD record: group="
                + row.group
                + ", org="
                + row.origin
                + ", dst="
                + row.destination
                + ", class="
                + row.odClass
                + "; no admissible route for mode(s) "
                + String.join(", ", missing)
                + "; excluded quantity across all modes="
                + row.total()
                + "\n");
        skipped.add(row);
        iterator.remove();
      }
    }
    String summary =
        method
            + " calibration: skipped "
            + skipped.size()
            + " of "
            + total
            + " OD records (group/origin/destination/class), excluded quantity="
            + overall.excludedQuantity
            + "; retained "
            + observations.size()
            + ".";
    StringBuilder diagnostics = new StringBuilder(summary).append('\n');
    diagnostics.append(
        "Coverage percentages use observed demand before exclusions within each "
            + "listed group/mode; mode record counts include positive modal demand only.\n");
    diagnostics.append(
        "Unroutable quantity has no admissible route. Other excluded quantity has "
            + "a route but is removed from estimation with the same OD record.\n");
    diagnostics.append("Exclusions apply only to estimation; assignment demand is unchanged.\n");
    appendCoverage(diagnostics, "all groups/modes", overall);
    for (Map.Entry<Integer, RouteStatistics> group : byGroup.entrySet()) {
      appendCoverage(diagnostics, "group=" + group.getKey(), group.getValue());
    }
    for (int mode = 0; mode < modes.length; mode++) {
      appendCoverage(diagnostics, "mode=" + modes[mode], byMode[mode]);
    }
    for (Map.Entry<Integer, RouteStatistics[]> group : byGroupAndMode.entrySet()) {
      for (int mode = 0; mode < modes.length; mode++) {
        appendCoverage(
            diagnostics,
            "group=" + group.getKey() + ", mode=" + modes[mode],
            group.getValue()[mode]);
      }
    }
    return diagnostics.toString();
  }

  /** Allocates independent counters in the fixed ascending mode-column order. */
  private RouteStatistics[] newRouteStatistics() {
    RouteStatistics[] result = new RouteStatistics[modes.length];
    Arrays.setAll(result, mode -> new RouteStatistics());
    return result;
  }

  /** Fits each independent commodity group, including its optional pivots, on the worker pool. */
  private Map<Integer, GroupFit> fitGroups(
      Map<Integer, List<Observation>> groups, Properties fitted) throws Exception {
    int workers = Math.max(1, Math.min(parameters.getThreads(), groups.size()));
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    ExecutorCompletionService<GroupFit> completed = new ExecutorCompletionService<>(pool);
    AtomicBoolean active = new AtomicBoolean(true);
    Map<Integer, GroupFit> results = new TreeMap<>();
    try {
      for (Map.Entry<Integer, List<Observation>> entry : groups.entrySet()) {
        checkCancelled("Estimating " + method + " for group " + entry.getKey());
        final int group = entry.getKey();
        final List<Observation> rows = entry.getValue();
        completed.submit(
            () ->
                fitGroup(
                    group,
                    rows,
                    fitted,
                    () -> active.get() && !Thread.currentThread().isInterrupted()));
      }
      for (int remaining = groups.size(); remaining > 0; remaining--) {
        Future<GroupFit> future;
        do {
          checkCancelled("Estimating " + method + " for group parameters");
          future = completed.poll(200, TimeUnit.MILLISECONDS);
        } while (future == null);
        GroupFit result = future.get();
        results.put(result.group, result);
      }
      return results;
    } catch (ExecutionException failed) {
      Throwable cause = failed.getCause();
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw new IllegalStateException(cause);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new CancellationException(method + " calibration canceled");
    } finally {
      active.set(false);
      pool.shutdownNow();
      try {
        pool.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Each worker writes only its own group's keys and returns its report for ordered assembly. */
  private GroupFit fitGroup(
      int group, List<Observation> rows, Properties fitted, BooleanSupplier proceed)
      throws Exception {
    if (!proceed.getAsBoolean()) {
      throw new CancellationException(method + " calibration canceled");
    }
    double[][] costs = rows.stream().map(row -> row.costs).toArray(double[][]::new);
    double[][] quantities = rows.stream().map(row -> row.quantities).toArray(double[][]::new);
    StringBuilder groupReport = new StringBuilder();
    Properties groupCoefficients = new Properties();
    try {
      LogCostChoiceEstimate behavioral =
          estimateGroup(group, costs, quantities, groupCoefficients, groupReport, proceed);
      for (String key : groupCoefficients.stringPropertyNames()) {
        fitted.setProperty(key, groupCoefficients.getProperty(key));
      }
      int pivots = 0;
      if (outputTable != null && estimatePivots) {
        List<ModalPivotEstimator.Row> pivotRows = new ArrayList<>(rows.size());
        for (Observation row : rows) {
          pivotRows.add(
              new ModalPivotEstimator.Row(
                  row.group, row.origin, row.destination, row.costs, row.quantities));
        }
        pivots =
            ModalPivotEstimator.estimate(
                pivotRows,
                Map.of(group, behavioral),
                modes,
                Arrays.binarySearch(modes, settings.getReferenceMode()),
                method,
                pivotMaxAbs,
                fitted,
                proceed);
      }
      return new GroupFit(group, groupReport.toString(), pivots);
    } catch (CancellationException cancelled) {
      throw cancelled;
    } catch (NonNegativeCostCoefficientException failure) {
      int[] invalidModes =
          Arrays.stream(failure.getModeColumns()).map(column -> modes[column]).toArray();
      throw new IllegalArgumentException(
          java.text.MessageFormat.format(
              com.bbn.openmap.Environment.getI18n()
                  .get(
                      LogitCalibration.class,
                      "NonNegativeCostCoefficients",
                      "{0} estimation failed for commodity group {1}: cost coefficients are zero or"
                          + " positive for modes {2}. All cost coefficients must be negative.\n"
                          + "No estimated coefficients have been saved."),
              method,
              group,
              Arrays.toString(invalidModes)),
          failure);
    } catch (IllegalArgumentException | IllegalStateException failure) {
      throw new IllegalArgumentException(
          method
              + " group "
              + group
              + ": "
              + failure.getMessage()
              + "\nNo estimated coefficients have been saved.",
          failure);
    }
  }

  private static final class GroupFit {
    final int group;
    final String report;
    final int pivots;

    GroupFit(int group, String report, int pivots) {
      this.group = group;
      this.report = report;
      this.pivots = pivots;
    }
  }

  /** Appends one scope's coverage line using the original-demand denominators. */
  private void appendCoverage(StringBuilder report, String scope, RouteStatistics statistics) {
    report
        .append(method)
        .append(" calibration coverage [")
        .append(scope)
        .append("]: ")
        .append(statistics.describe())
        .append('\n');
  }

  /**
   * Fits one group's retained OD/class rows and stages coefficients without writing the cost file.
   *
   * <p>Array columns follow the sorted Nodus mode IDs; the stored reference ID is translated to a
   * column index. The enclosing workflow adds model/group context to errors while propagating
   * cancellation separately. Model-specific export keeps namespaces separate and reports
   * uncertainty on the displayed scale.
   */
  private LogCostChoiceEstimate estimateGroup(
      int group,
      double[][] costs,
      double[][] quantities,
      Properties fitted,
      StringBuilder report,
      BooleanSupplier proceed)
      throws Exception {
    int reference = Arrays.binarySearch(modes, settings.getReferenceMode());
    final LogCostChoiceEstimate result;
    final String referenceKey;
    if ("Proportional".equals(method)) {
      result = ProportionalEstimator.estimate(costs, quantities, reference, proceed);
      referenceKey = Proportional.REFERENCE_PREFIX;
    } else if ("MNP".equals(method)) {
      result = LogCostProbitEstimator.estimate(costs, quantities, reference, conditional, proceed);
      referenceKey = "probit.reference.";
    } else {
      result = LogCostLogitEstimator.estimate(costs, quantities, reference, conditional, proceed);
      referenceKey = "mnl.reference.";
    }
    fitted.load(new java.io.StringReader(result.toCostFileEntries(modes, group)));
    fitted.setProperty(referenceKey + group, Integer.toString(settings.getReferenceMode()));
    report
        .append("# Group ")
        .append(group)
        .append(": OD rows=")
        .append(result.getObservations())
        .append(", quantity=")
        .append(result.getTotalQuantity())
        .append(", iterations=")
        .append(result.getIterations())
        .append('\n');
    report
        .append("# Log-likelihood=")
        .append(result.getLogLikelihood())
        .append(
            "Proportional".equals(method)
                ? ", unadjusted proportional log-likelihood="
                : ", zero-parameter log-likelihood=")
        .append(result.getNullLogLikelihood())
        .append('\n');
    if (result instanceof ProportionalEstimator.Result) {
      ProportionalEstimator.Result proportional = (ProportionalEstimator.Result) result;
      report
          .append("# Cost factors (modes ")
          .append(Arrays.toString(modes))
          .append("): ")
          .append(Arrays.toString(proportional.getCostFactors()))
          .append('\n');
      report
          .append("# Cost-factor SEs (delta method): ")
          .append(Arrays.toString(proportional.getCostFactorStandardErrors()))
          .append('\n');
      return result;
    }
    report
        .append("# Intercept SEs (modes ")
        .append(Arrays.toString(modes))
        .append("): ")
        .append(Arrays.toString(result.getInterceptStandardErrors()))
        .append('\n');
    if (result.isConditional()) {
      report.append("# Log-cost SE=").append(result.getCostStandardError()).append('\n');
    } else {
      report
          .append("# Log-cost coefficients (modes ")
          .append(Arrays.toString(modes))
          .append("): ")
          .append(Arrays.toString(result.getCostCoefficients()))
          .append('\n');
      report
          .append("# Log-cost SEs: ")
          .append(Arrays.toString(result.getCostStandardErrors()))
          .append('\n');
    }
    return result;
  }

  private java.nio.file.Path reportPath() {
    java.nio.file.Path costFile = parameters.getCostFunctionsPath().toAbsolutePath();
    String name = costFile.getFileName().toString();
    String stem =
        name.endsWith(".costs") ? name.substring(0, name.length() - ".costs".length()) : name;
    return costFile.resolveSibling(stem + "_params.txt");
  }

  private void writeReport(String contents) throws Exception {
    writeReport(reportPath(), contents);
  }

  private static void writeReport(java.nio.file.Path destination, String contents)
      throws Exception {
    java.nio.file.Path directory = destination.getParent();
    java.nio.file.Path temporary = Files.createTempFile(directory, ".nodus-params-", ".tmp");
    try {
      Files.writeString(temporary, contents, StandardCharsets.UTF_8);
      copyPosixPermissions(destination, temporary);
      Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      if (!Files.isRegularFile(destination)
          || !contents.equals(Files.readString(destination, StandardCharsets.UTF_8))) {
        throw new java.io.IOException("Estimation report was not saved: " + destination);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /** Opens a connection owned solely by the parameter save and its commit. */
  private Connection openParameterConnection() throws Exception {
    var project = parameters.getNodusProject();
    Connection separate =
        DriverManager.getConnection(
            project.getLocalProperty(NodusC.PROP_JDBC_URL, connection.getMetaData().getURL()),
            project.getLocalProperty(
                NodusC.PROP_JDBC_USERNAME, connection.getMetaData().getUserName()),
            project.getLocalProperty(NodusC.PROP_JDBC_PASSWORD, ""));
    try {
      String schema = connection.getSchema();
      if (schema != null) {
        separate.setSchema(schema);
      }
      return separate;
    } catch (Exception failure) {
      try {
        separate.close();
      } catch (Exception closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  /** Coordinates the database commit with recoverable report and cost-file replacements. */
  static Properties saveTableOutputs(
      Connection parameterConnection,
      String table,
      Properties values,
      LogitCostFile.Target target,
      java.nio.file.Path reportFile,
      StringBuilder report,
      BooleanSupplier proceed)
      throws Exception {
    return saveTableOutputs(
        parameterConnection, table, values, target, reportFile, report, proceed, () -> {});
  }

  static Properties saveTableOutputs(
      Connection parameterConnection,
      String table,
      Properties values,
      LogitCostFile.Target target,
      java.nio.file.Path reportFile,
      StringBuilder report,
      BooleanSupplier proceed,
      ModalParameterTable.SaveAction saveMergedMatrix)
      throws Exception {
    target.checkUnchanged();
    byte[] previousReport = Files.exists(reportFile) ? Files.readAllBytes(reportFile) : null;
    byte[][] installedCost = {null};
    boolean[] reportAttempted = {false};
    Properties[] saved = {null};
    long saveStarted = System.nanoTime();
    ModalParameterTable.save(
        parameterConnection,
        table,
        values,
        proceed,
        () -> {
          saveMergedMatrix.run();
          target.checkUnchanged();
          if (!sameFileContents(reportFile, previousReport)) {
            throw new IOException("Estimation report changed during saving: " + reportFile);
          }
          report
              .append("# Saved ")
              .append(values.size())
              .append(" parameter rows in ")
              .append(String.format(Locale.ROOT, "%.3f", (System.nanoTime() - saveStarted) / 1e9))
              .append(" seconds.\n");
          reportAttempted[0] = true;
          writeReport(reportFile, report.toString());
          saved[0] = LogitCostFile.saveParameterTable(target, table);
          installedCost[0] = Files.readAllBytes(target.file);
        },
        () -> {
          IOException recoveryFailure = null;
          try {
            if (installedCost[0] != null
                && Arrays.equals(installedCost[0], Files.readAllBytes(target.file))) {
              restoreFile(target.file, target.previous);
            }
          } catch (IOException failure) {
            recoveryFailure = failure;
          }
          try {
            if (reportAttempted[0]
                && Files.isRegularFile(reportFile)
                && Arrays.equals(
                    report.toString().getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(reportFile))) {
              restoreFile(reportFile, previousReport);
            }
          } catch (IOException failure) {
            if (recoveryFailure == null) {
              recoveryFailure = failure;
            } else {
              recoveryFailure.addSuppressed(failure);
            }
          }
          if (recoveryFailure != null) {
            throw recoveryFailure;
          }
        });
    return saved[0];
  }

  private static boolean sameFileContents(java.nio.file.Path file, byte[] expected)
      throws IOException {
    return expected == null
        ? Files.notExists(file)
        : Files.isRegularFile(file) && Arrays.equals(expected, Files.readAllBytes(file));
  }

  private static void restoreFile(java.nio.file.Path file, byte[] previous) throws IOException {
    if (previous == null) {
      Files.deleteIfExists(file);
      return;
    }
    java.nio.file.Path temporary =
        Files.createTempFile(file.getParent(), ".nodus-restore-", ".tmp");
    try {
      Files.write(temporary, previous);
      copyPosixPermissions(file, temporary);
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void copyPosixPermissions(java.nio.file.Path source, java.nio.file.Path target)
      throws IOException {
    if (!Files.isRegularFile(source)) {
      return;
    }
    try {
      Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source));
    } catch (UnsupportedOperationException ignored) {
      // Windows and other filesystems without POSIX permissions use their defaults.
    }
  }

  /**
   * Builds isolated cost-routing controls from the calibration inputs.
   *
   * <p>Scenario -1 means base definitions. The scratch demand table replaces only this private
   * routing pass's OD selection; assignment SQL filters, alternative-route iterations and markup
   * are not copied. The chosen detour and worker count still apply.
   */
  private AssignmentParameters preliminaryParameters(Properties sourceCosts) {
    AssignmentParameters copy = new AssignmentParameters(parameters.getNodusProject());
    // Routing and the saved output use the same source snapshot, never the old output file.
    copy.setCostFunctions(sourceCosts);
    // -1 selects base definitions and generic node rules, independent of assignment scenarios.
    copy.setScenario(-1);
    copy.setODMatrix(temporaryTable);
    // Observed tables define the calibration population. Assignment-only filters do not apply.
    copy.setWhereStmt("");
    copy.setThreads(parameters.getThreads());
    copy.setNbIterations(1);
    copy.setCostMarkup(0);
    copy.setMaxDetourRatio(parameters.getMaxDetour());
    copy.setKeepOnlyCheapestIntermodalPath(parameters.isKeepOnlyCheapestIntermodalPath());
    copy.setModalSplitMethodName(method);
    return copy;
  }

  /**
   * Adds one mode's observed table into the shared union of OD/group/class records.
   *
   * <p>Field names are matched case-insensitively and quoted as database identifiers. Group,
   * origin, destination and quantity are required; an absent class column supplies zero. Missing
   * rows and SQL NULL quantities are zero, duplicate rows are summed, and identifiers must be exact
   * integers. Negative/nonfinite quantities, invalid group/class ranges and aggregate overflow are
   * rejected.
   */
  private void readTable(String name, int column) throws Exception {
    checkCancelled("Reading observed OD matrix " + name);
    String table = JDBCUtils.getQuotedCompliantIdentifier(name);
    Map<String, String> fields = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
      for (int field = 1; field <= result.getMetaData().getColumnCount(); field++) {
        String actual = result.getMetaData().getColumnName(field);
        fields.put(actual, JDBCUtils.getQuotedCompliantIdentifier(actual));
      }
    }
    String[] names = {
      NodusC.DBF_GROUP, NodusC.DBF_ORIGIN, NodusC.DBF_DESTINATION, NodusC.DBF_QUANTITY
    };
    List<String> columns = new ArrayList<>();
    for (String required : names) {
      if (!fields.containsKey(required)) {
        throw new IllegalArgumentException(
            "Observed table " + name + " has no " + required + " column");
      }
      columns.add(fields.get(required));
    }
    columns.add(fields.getOrDefault(NodusC.DBF_CLASS, "0"));
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery("SELECT " + String.join(",", columns) + " FROM " + table)) {
      int count = 0;
      while (rows.next()) {
        if (++count % 256 == 0) {
          checkCancelled("Reading observed OD matrix " + name);
        }
        int group = integer(rows, 1);
        int origin = integer(rows, 2);
        int destination = integer(rows, 3);
        double quantity = rows.getDouble(4); // SQL NULL quantities are zero, as in the demo.
        int odClass = integer(rows, 5);
        if (group < 0
            || group >= NodusC.MAXGROUPS
            || odClass < 0
            || odClass >= NodusC.MAXMM
            || !Double.isFinite(quantity)
            || quantity < 0) {
          throw new IllegalArgumentException("Invalid observation in " + name + ", row " + count);
        }
        String key = key(group, origin, destination, odClass);
        Observation row =
            observations.computeIfAbsent(
                key, value -> new Observation(group, origin, destination, odClass, modes.length));
        row.quantities[column] += quantity;
        if (!Double.isFinite(row.total())) {
          throw new IllegalArgumentException("Observed quantities overflow in " + name);
        }
      }
    }
  }

  /** Reads an exact integer identifier, rejecting SQL NULL, fractions and out-of-range values. */
  private static int integer(ResultSet rows, int index) throws Exception {
    BigDecimal value = rows.getBigDecimal(index);
    if (value == null) {
      throw new IllegalArgumentException(
          "Missing group, node identifier or OD class in observed data");
    }
    return value.intValueExact();
  }

  /**
   * Creates a uniquely named scratch OD table containing total observed demand per record.
   *
   * <p>This is an ordinary database table for portability, explicitly dropped by {@link #close()}.
   * It is not a scenario result table; its temporary role depends on the caller closing the
   * workflow.
   */
  private void createDemandTable() throws Exception {
    scratchConnection = openParameterConnection();
    Connection writer = scratchConnection;
    temporaryTable = "nodus_mnl_" + UUID.randomUUID().toString().replace("-", "");
    String odClass = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_CLASS);
    try (Statement statement = writer.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE "
              + temporaryTable
              + " (grp INTEGER, org INTEGER, dst INTEGER, qty DOUBLE, "
              + odClass
              + " INTEGER)");
    }
    writer.setAutoCommit(false);
    try (PreparedStatement insert =
        writer.prepareStatement("INSERT INTO " + temporaryTable + " VALUES (?,?,?,?,?)")) {
      int batch = 0;
      for (Observation row : observations.values()) {
        insert.setInt(1, row.group);
        insert.setInt(2, row.origin);
        insert.setInt(3, row.destination);
        insert.setDouble(4, row.total());
        insert.setInt(5, row.odClass);
        insert.addBatch();
        if (++batch % 1000 == 0) {
          checkCancelled("Preparing calibration demand");
          insert.executeBatch();
        }
      }
      insert.executeBatch();
      writer.commit();
    } catch (Exception failure) {
      try {
        writer.rollback();
      } catch (Exception rollbackFailure) {
        failure.addSuppressed(rollbackFailure);
      }
      throw failure;
    } finally {
      writer.setAutoCommit(true);
    }
  }

  /**
   * Updates progress and converts confirmed cancellation or interruption into
   * CancellationException.
   */
  private void checkCancelled(String message) {
    if (Thread.currentThread().isInterrupted()
        || !parameters.getNodusProject().getNodusMapPanel().updateProgress(message)) {
      throw new CancellationException(method + " calibration canceled");
    }
  }

  /** Builds an unambiguous lookup key from the integer group, endpoints and OD class. */
  private static String key(int group, int origin, int destination, int odClass) {
    return group + ":" + origin + ":" + destination + ":" + odClass;
  }

  /**
   * Drops the owned scratch demand table, retaining the borrowed project connection.
   *
   * @throws Exception if database cleanup fails
   */
  @Override
  public void close() throws Exception {
    try {
      if (temporaryTable != null) {
        Connection writer = scratchConnection == null ? connection : scratchConnection;
        try (Statement statement = writer.createStatement()) {
          statement.executeUpdate("DROP TABLE IF EXISTS " + temporaryTable);
        }
      }
    } finally {
      if (scratchConnection != null) {
        scratchConnection.close();
        scratchConnection = null;
      }
    }
  }

  /**
   * Coverage counters for all observations, one group, one mode, or a group/mode intersection.
   *
   * <p>A positive-demand record is counted once in its scope even when several modes have no route.
   * Excluded quantity covers the entire scoped demand of removed records; unroutable quantity
   * covers only the missing-route portion. Their difference measures feasible demand removed solely
   * to preserve complete observed modal shares.
   */
  private static final class RouteStatistics {
    private int records;
    private int excludedRecords;
    private int unroutableRecords;
    private double observedQuantity;
    private double excludedQuantity;
    private double unroutableQuantity;

    private void add(double quantity, double missingQuantity, boolean excluded) {
      if (quantity == 0) {
        return;
      }
      records++;
      observedQuantity += quantity;
      if (excluded) {
        excludedRecords++;
        excludedQuantity += quantity;
      }
      if (missingQuantity > 0) {
        unroutableRecords++;
        unroutableQuantity += missingQuantity;
      }
    }

    private String describe() {
      return "skipped records="
          + excludedRecords
          + "/"
          + records
          + " ("
          + percentage(excludedRecords, records)
          + "); missing-route records="
          + unroutableRecords
          + "; observed quantity="
          + observedQuantity
          + "; excluded quantity="
          + excludedQuantity
          + " ("
          + percentage(excludedQuantity, observedQuantity)
          + ")"
          + "; unroutable quantity="
          + unroutableQuantity
          + " ("
          + percentage(unroutableQuantity, observedQuantity)
          + ")"
          + "; other excluded quantity="
          + Math.max(0, excludedQuantity - unroutableQuantity);
    }

    private static String percentage(double part, double total) {
      return total == 0 ? "n/a" : String.format(Locale.ROOT, "%.3f%%", 100 * (part / total));
    }
  }

  /**
   * Merged observed quantities and discovered costs for one group/origin/destination/class key.
   *
   * <p>Arrays follow the enclosing workflow's ascending mode IDs. A NaN cost means no route was
   * collected; quantities remain in their original units. Costs are written only by the routing job
   * that owns this OD record, and read for fitting after all routing workers have joined.
   */
  private static final class Observation {
    private final int group;
    private final int origin;
    private final int destination;
    private final int odClass;
    private final double[] quantities;
    private final double[] costs;

    private Observation(int group, int origin, int destination, int odClass, int alternatives) {
      this.group = group;
      this.origin = origin;
      this.destination = destination;
      this.odClass = odClass;
      quantities = new double[alternatives];
      costs = new double[alternatives];
      Arrays.fill(costs, Double.NaN);
    }

    private double total() {
      return Arrays.stream(quantities).sum();
    }
  }

  /**
   * Routing adapter that records feasible modal costs before any calibrated split is applied.
   *
   * <p>Worker clones share the stable observation map; routing jobs own distinct OD records. Every
   * discovered mode must be mapped to an observed table, even if its observed quantity is zero.
   * Valid routes receive uniform modal and within-mode shares solely to keep discovery independent
   * of previous coefficients. These shares are not saved as assignment results.
   */
  private final class Collector extends ModalSplitMethod {
    private Collector() {
      super(parameters.getNodusProject());
    }

    @Override
    public String getName() {
      return method + " calibration";
    }

    @Override
    public String getPrettyName() {
      return getName();
    }

    @Override
    public boolean split(ODCell demand, List<PathsForMode> paths) {
      Observation row =
          observations.get(
              key(
                  demand.getGroup(),
                  demand.getOriginNodeId(),
                  demand.getDestinationNodeId(),
                  demand.getODClass()));
      if (row == null) {
        throw new IllegalStateException("Cannot match routed OD to its calibration observation");
      }
      for (PathsForMode alternative : paths) {
        int column = Arrays.binarySearch(modes, alternative.loadingMode);
        if (column < 0) {
          throw new IllegalArgumentException(
              "Select an observed OD table for available mode " + alternative.loadingMode);
        }
        double cost = alternative.cheapestPathWeights.getCost();
        if (!Double.isFinite(cost) || cost <= 0) {
          throw new IllegalArgumentException(
              method
                  + " calibration requires positive finite costs, group "
                  + row.group
                  + ", OD "
                  + row.origin
                  + " -> "
                  + row.destination);
        }
        row.costs[column] = cost;
        alternative.marketShare = 1.0 / paths.size();
        for (Path path : alternative.pathList) {
          if (path.intermodal) {
            throw new IllegalArgumentException(
                method + " calibration currently requires unimodal routes");
          }
          path.marketShare = alternative.marketShare / alternative.pathList.size();
        }
      }
      return true;
    }
  }
}
