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
import java.math.BigDecimal;
import java.sql.Connection;
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

/**
 * Coordinates standalone modal estimation from observed OD tables through guarded cost-file saving.
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
 * class-specific route costs. All groups must succeed before {@link LogitCostFile} updates the
 * selected output file. By default this is the source; saving under another name leaves the source
 * unchanged. Successful saving also refreshes the caller's in-memory cost properties. The caller's
 * assignment OD selection, scenario and routing controls remain unchanged. Exclusions apply only to
 * estimation and never filter a subsequent assignment's demand.
 *
 * <p>Use a fresh instance for one estimation and close it in a try-with-resources block. The class
 * owns its scratch table but borrows the project's JDBC connection, which it never closes. The
 * caller must serialize computations using the project's assignment resources; this workflow is not
 * reentrant. Routing workers share the observation map only while its keys are stable and own
 * distinct OD records. Fitting resumes after those workers have joined.
 *
 * <p>Preparation and fitting report indeterminate progress; routing reports its own progress.
 * Cancellation is propagated as {@link CancellationException}, not a failed statistical fit.
 * Optional terminal logging controls diagnostics only; a successful cost-file report is always
 * written. Closing after success, failure or cancellation removes the scratch demand table.
 */
public final class LogitCalibration implements AutoCloseable {
  private final AssignmentParameters parameters;
  private final LogitCalibrationSettings settings;
  private final Connection connection;
  private final Map<String, Observation> observations = new LinkedHashMap<>();
  private final int[] modes;
  private final String method;
  private final boolean logToTerminal;
  private String temporaryTable;

  /**
   * Creates a standalone estimator with terminal logging enabled for scripted callers.
   *
   * @param parameters cost file, modal method, detour and threads; scenario, routing iterations,
   *     markup and OD selection are not used
   * @param settings observed modal tables and reference mode
   */
  public LogitCalibration(AssignmentParameters parameters, LogitCalibrationSettings settings) {
    this(parameters, settings, true);
  }

  /**
   * Creates a standalone estimator; no assignment is launched and no scenario is replaced.
   *
   * <p>Terminal logging is independent of the report saved in the cost file. Disabling it
   * suppresses skipped-OD details, coverage summaries and fitted parameters on stdout, without
   * changing the fitted model, saved report or error handling.
   *
   * @param parameters cost file, modal method, detour and threads; scenario, routing iterations,
   *     markup and OD selection are not used
   * @param settings observed modal tables and reference mode
   * @param logToTerminal whether to print estimation diagnostics and results in the terminal
   */
  public LogitCalibration(
      AssignmentParameters parameters, LogitCalibrationSettings settings, boolean logToTerminal) {
    this.parameters = parameters;
    method = parameters.getModalSplitMethodName();
    this.settings = settings;
    this.logToTerminal = logToTerminal;
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
      log(
          method
              + " calibration: no usable OD observations remain. "
              + "No coefficients were saved.");
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
      for (Map.Entry<Integer, List<Observation>> entry : groups.entrySet()) {
        int group = entry.getKey();
        checkCancelled("Estimating " + method + " for group " + group);
        double[][] costs = entry.getValue().stream().map(row -> row.costs).toArray(double[][]::new);
        double[][] quantities =
            entry.getValue().stream().map(row -> row.quantities).toArray(double[][]::new);
        try {
          estimateGroup(group, costs, quantities, fitted, report);
        } catch (CancellationException cancelled) {
          // CancellationException is an IllegalStateException, but is not a failed fit.
          throw cancelled;
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
      checkCancelled("Saving " + method + " coefficients");
      Properties saved = LogitCostFile.save(target, original, fitted, report.toString(), method);
      parameters.getCostFunctions().clear();
      parameters.getCostFunctions().putAll(saved);
      log(report.toString());
    } finally {
      parameters.getNodusProject().getNodusMapPanel().stopProgress();
    }
    return true;
  }

  /**
   * Removes whole OD records having positive observed flow on at least one unroutable mode.
   *
   * <p>Coverage denominators use the original observations in each group/mode scope. Excluded
   * quantity includes both genuinely unroutable flow and otherwise routable flow discarded with the
   * same record. Modal record counts include positive modal demand only. The returned report is
   * preserved for the cost file even when terminal logging is disabled.
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
        if (logToTerminal) {
          log(
              method
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
                  + row.total());
        }
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
    log(diagnostics.toString());
    return diagnostics.toString();
  }

  /** Emits optional terminal output without affecting the report written to the cost file. */
  private void log(String message) {
    if (logToTerminal) {
      System.out.println(message);
    }
  }

  /** Allocates independent counters in the fixed ascending mode-column order. */
  private RouteStatistics[] newRouteStatistics() {
    RouteStatistics[] result = new RouteStatistics[modes.length];
    Arrays.setAll(result, mode -> new RouteStatistics());
    return result;
  }

  /**
   * Appends one scope's coverage line using the same original-demand denominators as the terminal.
   */
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
  private void estimateGroup(
      int group, double[][] costs, double[][] quantities, Properties fitted, StringBuilder report)
      throws Exception {
    java.util.function.BooleanSupplier proceed =
        () ->
            !Thread.currentThread().isInterrupted()
                && parameters
                    .getNodusProject()
                    .getNodusMapPanel()
                    .updateProgress("Estimating " + method + " for group " + group);
    int reference = Arrays.binarySearch(modes, settings.getReferenceMode());
    final LogCostChoiceEstimate result;
    final String referenceKey;
    if ("Proportional".equals(method)) {
      result = ProportionalEstimator.estimate(costs, quantities, reference, proceed);
      referenceKey = Proportional.REFERENCE_PREFIX;
    } else if ("MNP".equals(method)) {
      result = LogCostProbitEstimator.estimate(costs, quantities, reference, proceed);
      referenceKey = "probit.reference.";
    } else {
      result = LogCostLogitEstimator.estimate(costs, quantities, reference, proceed);
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
      return;
    }
    report
        .append("# Intercept SEs (modes ")
        .append(Arrays.toString(modes))
        .append("): ")
        .append(Arrays.toString(result.getInterceptStandardErrors()))
        .append('\n');
    report.append("# Log-cost SE=").append(result.getCostStandardError()).append('\n');
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
    temporaryTable = "nodus_mnl_" + UUID.randomUUID().toString().replace("-", "");
    String odClass = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_CLASS);
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE "
              + temporaryTable
              + " (grp INTEGER, org INTEGER, dst INTEGER, qty DOUBLE, "
              + odClass
              + " INTEGER)");
    }
    try (PreparedStatement insert =
        connection.prepareStatement("INSERT INTO " + temporaryTable + " VALUES (?,?,?,?,?)")) {
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
    if (temporaryTable != null) {
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("DROP TABLE IF EXISTS " + temporaryTable);
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
