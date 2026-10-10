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

package edu.uclouvain.core.nodus.compute.results;

import edu.uclouvain.core.nodus.NodusC;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Read-only comparisons of reference modal quantities with saved assignment path headers. */
public final class PerformanceIndicators {
  private PerformanceIndicators() {}

  /** Independently selectable report sections. */
  public enum Indicator {
    /** Absolute quantity errors relative to total observed quantity. */
    WAPE("WAPE", "WAPE (%)"),
    /** Signed total quantity error relative to total observed quantity. */
    BIAS("Bias", "Relative bias (%)"),
    /** Root mean square cell error in quantity units. */
    RMSE("RMSE", "RMSE (quantity units)"),
    /** Observed and assigned modal shares of aggregate quantities. */
    MODAL_SHARES("ModalShares", "Aggregate modal shares"),
    /** Median and 90th percentile of OD modal-share errors. */
    OD_SHARE_ERROR("ODShareError", "Median / P90 OD share error"),
    /** Observed-quantity-weighted cross-entropy using natural logarithms. */
    CROSS_ENTROPY("CrossEntropy", "Cross-entropy"),
    /** Observed-quantity-weighted Jensen–Shannon divergence using base-2 logarithms. */
    JENSEN_SHANNON("JS", "Jensen–Shannon divergence"),
    /** Observed OD coverage, quantity shortfalls/excesses and zero predictions. */
    COVERAGE("Coverage", "Coverage and zero predictions");

    /** Resource key used to translate the section label. */
    public final String key;
    /** English fallback label for the indicator selector. */
    public final String label;

    Indicator(String key, String label) {
      this.key = key;
      this.label = label;
    }
  }

  /** A volume comparison for a mode, commodity group, their intersection, or the whole sample. */
  public static final class Volumes {
    /** Number of mode/group/OD cells, including cells that are zero on both sides. */
    public long cells;
    /** Sum of observed quantities across the comparison cells. */
    public double observed;
    /** Sum of assigned quantities across the comparison cells. */
    public double assigned;
    /** Sum of absolute assigned-minus-observed cell errors. */
    public double absoluteError;

    private double errorNorm;
    /** Number of observed-positive cells with zero assigned quantity. */
    public long zeroPredictions;
    /** Observed quantity in the cells counted by {@link #zeroPredictions}. */
    public double zeroPredictionQuantity;

    /** Creates an empty volume accumulator with zero counts and quantities. */
    public Volumes() {}

    private void add(double actual, double prediction) {
      cells++;
      observed += actual;
      assigned += prediction;
      double error = prediction - actual;
      absoluteError += Math.abs(error);
      errorNorm = Math.hypot(errorNorm, error);
      if (actual > 0 && prediction == 0) {
        zeroPredictions++;
        zeroPredictionQuantity += actual;
      }
      if (!Double.isFinite(observed)
          || !Double.isFinite(assigned)
          || !Double.isFinite(absoluteError)
          || !Double.isFinite(errorNorm)) {
        throw new IllegalArgumentException("Quantity totals exceed the numeric range");
      }
    }

    /**
     * Returns the weighted absolute percentage error.
     *
     * @return absolute error as a percentage of observed quantity, or NaN if that quantity is zero
     */
    public double wape() {
      return observed == 0 ? Double.NaN : 100 * (absoluteError / observed);
    }

    /**
     * Returns the relative quantity bias; positive values indicate overprediction.
     *
     * @return signed total error as a percentage of observed quantity, or NaN if it is zero
     */
    public double bias() {
      return observed == 0 ? Double.NaN : 100 * ((assigned - observed) / observed);
    }

    /**
     * Returns the root mean square error over the complete comparison grid.
     *
     * @return error in quantity units, or NaN without comparison cells
     */
    public double rmse() {
      return cells == 0 ? Double.NaN : errorNorm / Math.sqrt(cells);
    }
  }

  /** Modal distribution scores and coverage for OD/group totals. */
  public static final class Choices {
    /** Number of OD/group records with a positive observed selected-mode total. */
    public long observedODs;
    /** Number of OD/group records with positive observed and assigned selected-mode totals. */
    public long eligibleODs;
    /** Number of observed-positive OD/group records with no assigned selected-mode flow. */
    public long missingODs;
    /** Number of assigned-positive OD/group records with no observed selected-mode flow. */
    public long extraODs;
    /** Total observed quantity across all OD/group records. */
    public double observedQuantity;
    /** Observed quantity in the OD/group records counted by {@link #missingODs}. */
    public double missingQuantity;
    /** Sum of positive observed-minus-assigned OD/group total differences. */
    public double shortfall;
    /** Sum of positive assigned-minus-observed OD/group total differences. */
    public double excess;
    /** Eligible OD/group records containing at least one observed-positive, assigned-zero mode. */
    public long zeroShareODs;
    /** Observed-positive, assigned-zero modal cells within eligible OD/group records. */
    public long zeroShareCells;
    /** Observed quantity in the cells counted by {@link #zeroShareCells}. */
    public double zeroShareQuantity;

    private double eligibleQuantity;
    private double crossEntropy;
    private double jensenShannon;
    private boolean impossiblePrediction;
    private final List<Double> distances = new ArrayList<>();

    /** Creates an empty modal-distribution and coverage accumulator. */
    public Choices() {}

    private void add(Row row, boolean keepDistances) {
      double actual = sum(row.observed);
      double prediction = sum(row.assigned);
      observedQuantity += actual;
      shortfall += Math.max(0, actual - prediction);
      excess += Math.max(0, prediction - actual);
      if (actual == 0) {
        if (prediction > 0) {
          extraODs++;
        }
        return;
      }
      observedODs++;
      if (prediction == 0) {
        missingODs++;
        missingQuantity += actual;
        return;
      }
      eligibleODs++;
      double distance = 0;
      double entropy = 0;
      double js = 0;
      boolean zeroShare = false;
      for (int mode = 0; mode < row.observed.length; mode++) {
        double p = row.observed[mode] / actual;
        double estimated = row.assigned[mode] / prediction;
        distance += Math.abs(estimated - p);
        double mixture = (p + estimated) / 2;
        // Zero-share terms contribute zero. The mixture is positive for either positive share,
        // including when the two distributions have disjoint support. This is divergence, not
        // the square-root distance; convert the natural-log sum to base 2 below.
        if (p == 0 || estimated == 0) {
          // Avoid forming a half-subnormal mixture when only one share is positive.
          js += 0.5 * (p + estimated) * Math.log(2);
        } else {
          js +=
              0.5
                  * (p * (Math.log(p) - Math.log(mixture))
                      + estimated * (Math.log(estimated) - Math.log(mixture)));
        }
        if (row.observed[mode] > 0) {
          if (row.assigned[mode] == 0) {
            impossiblePrediction = true;
            zeroShare = true;
            zeroShareCells++;
            zeroShareQuantity += row.observed[mode];
          } else {
            // Log differences avoid underflow when a positive modal share is extremely small.
            double logEstimated = Math.log(row.assigned[mode]) - Math.log(prediction);
            entropy -= p * logEstimated;
          }
        }
      }
      if (zeroShare) {
        zeroShareODs++;
      }
      if (keepDistances) {
        distances.add(50 * distance);
      }
      double newWeight = eligibleQuantity + actual;
      crossEntropy += (entropy - crossEntropy) * (actual / newWeight);
      double boundedJS = Math.max(0, Math.min(1, js / Math.log(2)));
      jensenShannon += (boundedJS - jensenShannon) * (actual / newWeight);
      eligibleQuantity = newWeight;
    }

    /**
     * Linearly interpolates sorted OD share errors; every eligible OD/group has equal weight.
     *
     * @param fraction percentile fraction from zero to one; use 0.5 for the median and 0.9 for P90
     * @return share error from zero to 100 percent, or NaN if no OD distances were retained
     */
    public double percentile(double fraction) {
      if (distances.isEmpty()) {
        return Double.NaN;
      }
      List<Double> sorted = new ArrayList<>(distances);
      Collections.sort(sorted);
      double position = fraction * (sorted.size() - 1);
      int lower = (int) Math.floor(position);
      int upper = (int) Math.ceil(position);
      return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (position - lower);
    }

    /**
     * Returns the observed-quantity-weighted cross-entropy in natural-log units.
     *
     * @return NaN without eligible ODs, or infinity for an observed-positive, assigned-zero share
     */
    public double crossEntropy() {
      return eligibleODs == 0
          ? Double.NaN
          : impossiblePrediction ? Double.POSITIVE_INFINITY : crossEntropy;
    }

    /**
     * Returns the observed-quantity-weighted mean of base-2 Jensen–Shannon divergences.
     *
     * @return divergence from zero to one, or NaN without eligible OD/group records
     */
    public double jensenShannonDivergence() {
      return eligibleODs == 0 ? Double.NaN : jensenShannon;
    }

    /**
     * Returns the share of observed quantity on ODs with any assigned selected-mode flow.
     *
     * @return coverage from zero to 100 percent, or NaN without observed quantity
     */
    public double coveredPercent() {
      return observedQuantity == 0 ? Double.NaN : 100 * (1 - missingQuantity / observedQuantity);
    }
  }

  /** An eligible OD with an observed-positive, assigned-zero modal cell. */
  public static final class ZeroShareCell {
    /** Commodity group ID. */
    public final int group;
    /** Network mode ID of the zero-prediction cell. */
    public final int mode;
    /** Origin node ID. */
    public final long origin;
    /** Destination node ID. */
    public final long destination;
    /** Observed quantity for this mode/group/OD cell. */
    public final double observed;
    /** Positive assigned total over all selected modes for the same group/OD. */
    public final double assignedODTotal;

    private ZeroShareCell(Key key, int mode, double observed, double assignedODTotal) {
      group = key.group;
      origin = key.origin;
      destination = key.destination;
      this.mode = mode;
      this.observed = observed;
      this.assignedODTotal = assignedODTotal;
    }
  }

  /** Results use the same comparison cells for all selected indicators. */
  public static final class Report {
    /** Resolved assignment path-header table name. */
    public final String pathTable;
    /** Immutable mapping from selected network mode IDs to reference OD table names. */
    public final Map<Integer, String> referenceTables;
    /** Volume errors over all selected modes and commodity groups. */
    public final Volumes total = new Volumes();
    /** Volume errors by selected network mode ID. */
    public final Map<Integer, Volumes> modes = new TreeMap<>();
    /** Volume errors by commodity group ID. */
    public final Map<Integer, Volumes> groups = new TreeMap<>();
    /** Volume errors indexed first by commodity group ID, then by network mode ID. */
    public final Map<Integer, Map<Integer, Volumes>> groupModes = new TreeMap<>();
    /** Modal-distribution scores and coverage over all commodity groups. */
    public final Choices choices = new Choices();
    /** Modal-distribution scores and coverage by commodity group ID. */
    public final Map<Integer, Choices> groupChoices = new TreeMap<>();
    /** Assigned quantities excluded from comparison, indexed by unselected network mode ID. */
    public final Map<Integer, Double> omittedModes = new TreeMap<>();

    private final java.util.PriorityQueue<ZeroShareCell> zeroShareExamples =
        new java.util.PriorityQueue<>(java.util.Comparator.comparingDouble(cell -> cell.observed));

    private Report(String pathTable, Map<Integer, String> references) {
      this.pathTable = pathTable;
      referenceTables = Collections.unmodifiableMap(new TreeMap<>(references));
      references.keySet().forEach(mode -> modes.put(mode, new Volumes()));
    }

    private void addZeroShareExample(Key key, int mode, double observed, double assignedODTotal) {
      if (zeroShareExamples.size() < 20) {
        zeroShareExamples.add(new ZeroShareCell(key, mode, observed, assignedODTotal));
      } else if (observed > zeroShareExamples.peek().observed) {
        zeroShareExamples.remove();
        zeroShareExamples.add(new ZeroShareCell(key, mode, observed, assignedODTotal));
      }
    }

    /**
     * Returns at most twenty zero-prediction cells in decreasing observed-quantity order.
     *
     * @return an immutable list of examples from eligible OD/group records
     */
    public List<ZeroShareCell> zeroShareExamples() {
      List<ZeroShareCell> result = new ArrayList<>(zeroShareExamples);
      result.sort(
          java.util.Comparator.comparingDouble((ZeroShareCell cell) -> cell.observed)
              .reversed()
              .thenComparingInt(cell -> cell.group)
              .thenComparingInt(cell -> cell.mode)
              .thenComparingLong(cell -> cell.origin)
              .thenComparingLong(cell -> cell.destination));
      return Collections.unmodifiableList(result);
    }
  }

  private static final class Key {
    final int group;
    final long origin;
    final long destination;

    Key(int group, long origin, long destination) {
      this.group = group;
      this.origin = origin;
      this.destination = destination;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Key)) {
        return false;
      }
      Key key = (Key) other;
      return group == key.group && origin == key.origin && destination == key.destination;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(group, origin, destination);
    }
  }

  private static final class Row {
    final double[] observed;
    final double[] assigned;

    Row(int modes) {
      observed = new double[modes];
      assigned = new double[modes];
    }
  }

  /**
   * Lists actual header tables in the current schema, using both their suffix and required fields.
   *
   * @param connection project connection whose current catalog and schema are inspected
   * @return table names ending in {@code _header} with grp, org, dst, qty and ldmode columns,
   *     sorted without regard to case
   * @throws SQLException if database metadata or table columns cannot be read
   */
  public static List<String> pathHeaderTables(Connection connection) throws SQLException {
    List<String> result = new ArrayList<>();
    for (String name : tableNames(connection)) {
      if (name.toLowerCase(java.util.Locale.ROOT).endsWith("_header")) {
        Map<String, String> columns = columns(connection, name);
        if (columns.keySet().containsAll(List.of("grp", "org", "dst", "qty", "ldmode"))) {
          result.add(name);
        }
      }
    }
    result.sort(String.CASE_INSENSITIVE_ORDER);
    return result;
  }

  /**
   * Aggregates all paths, classes and departure times by mode/group/OD.
   * Null quantities are zero; negative/nonfinite quantities and null/nonintegral OD keys are
   * errors. Tables present on one side only are retained. RMSE uses the complete selected-mode grid
   * over the union of OD/group keys. Share scores require positive totals on both sides and are
   * reported with their coverage; unmapped assignment modes are explicitly excluded.
   *
   * @param connection project connection used to read reference and assignment tables
   * @param references selected network mode IDs mapped to observed OD table names
   * @param pathTable assignment path-header table name
   * @param keepODDistances whether to retain OD share errors for median and percentile calculation
   * @param proceed returns false to cancel the computation
   * @return aggregated quantity errors, modal-distribution scores and coverage diagnostics
   * @throws SQLException if tables or required columns are missing or database reads fail
   * @throws IllegalArgumentException for an empty or invalid mapping, invalid data or numeric
   *     overflow
   * @throws CancellationException on callback cancellation or interruption
   */
  public static Report compute(
      Connection connection,
      Map<Integer, String> references,
      String pathTable,
      boolean keepODDistances,
      BooleanSupplier proceed)
      throws SQLException {
    if (references == null || references.isEmpty()) {
      throw new IllegalArgumentException("Select at least one reference modal OD matrix");
    }
    Map<Integer, String> tables = new TreeMap<>(references);
    for (Map.Entry<Integer, String> entry : tables.entrySet()) {
      if (entry.getKey() <= 0
          || entry.getKey() >= NodusC.MAXMM
          || entry.getValue() == null
          || entry.getValue().isBlank()) {
        throw new IllegalArgumentException(
            "Each reference matrix requires a valid mode ID and table");
      }
    }
    checkCancelled(proceed);
    String header = resolve(connection, pathTable);
    Map<String, String> pathColumns = requireColumns(connection, header, true);
    Report report = new Report(header, tables);
    int[] modeIds = tables.keySet().stream().mapToInt(Integer::intValue).toArray();
    Map<Key, Row> rows = new LinkedHashMap<>();
    int index = 0;
    for (Map.Entry<Integer, String> entry : tables.entrySet()) {
      checkCancelled(proceed);
      String name = resolve(connection, entry.getValue());
      Map<String, String> fields = requireColumns(connection, name, false);
      read(connection, name, fields, false, index++, modeIds, rows, report, proceed);
    }
    read(connection, header, pathColumns, true, -1, modeIds, rows, report, proceed);
    int checked = 0;
    for (Map.Entry<Key, Row> entry : rows.entrySet()) {
      if (checked++ % 256 == 0) {
        checkCancelled(proceed);
      }
      int group = entry.getKey().group;
      Row row = entry.getValue();
      double assignedODTotal = sum(row.assigned);
      Volumes groupTotal = report.groups.computeIfAbsent(group, key -> new Volumes());
      Map<Integer, Volumes> modalTotals =
          report.groupModes.computeIfAbsent(group, key -> new TreeMap<>());
      for (int column = 0; column < modeIds.length; column++) {
        double actual = row.observed[column];
        double assigned = row.assigned[column];
        report.total.add(actual, assigned);
        report.modes.get(modeIds[column]).add(actual, assigned);
        groupTotal.add(actual, assigned);
        modalTotals.computeIfAbsent(modeIds[column], key -> new Volumes()).add(actual, assigned);
        if (actual > 0 && assigned == 0 && assignedODTotal > 0) {
          report.addZeroShareExample(entry.getKey(), modeIds[column], actual, assignedODTotal);
        }
      }
      report.choices.add(row, keepODDistances);
      report.groupChoices.computeIfAbsent(group, key -> new Choices()).add(row, keepODDistances);
    }
    checkCancelled(proceed);
    return report;
  }

  private static void read(
      Connection connection,
      String table,
      Map<String, String> fields,
      boolean paths,
      int column,
      int[] modes,
      Map<Key, Row> cells,
      Report report,
      BooleanSupplier proceed)
      throws SQLException {
    List<String> keys =
        new ArrayList<>(List.of(fields.get("grp"), fields.get("org"), fields.get("dst")));
    if (paths) {
      keys.add(fields.get("ldmode"));
    }
    String qty = fields.get("qty");
    String sql =
        "SELECT "
            + String.join(",", keys)
            + ", SUM(COALESCE("
            + qty
            + ",0)), MIN("
            + qty
            + ") FROM "
            + quote(connection, table)
            + " GROUP BY "
            + String.join(",", keys);
    checkCancelled(proceed);
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      int checked = 0;
      while (result.next()) {
        if (checked++ % 256 == 0) {
          checkCancelled(proceed);
        }
        int quantityColumn = keys.size() + 1;
        double quantity = result.getDouble(quantityColumn);
        double minimum = result.getDouble(quantityColumn + 1);
        if (!Double.isFinite(quantity)
            || !Double.isFinite(minimum)
            || minimum < 0
            || quantity < 0) {
          throw new IllegalArgumentException("Negative or nonfinite quantity in " + table);
        }
        int target = column;
        if (paths) {
          int mode = integer(result, 4, table);
          target = java.util.Arrays.binarySearch(modes, mode);
          if (target < 0) {
            report.omittedModes.merge(mode, quantity, Double::sum);
            continue;
          }
        }
        Key key =
            new Key(
                integer(result, 1, table), integral(result, 2, table), integral(result, 3, table));
        Row row = cells.computeIfAbsent(key, ignored -> new Row(modes.length));
        (paths ? row.assigned : row.observed)[target] += quantity;
      }
    }
  }

  private static int integer(ResultSet rows, int column, String table) throws SQLException {
    try {
      return Math.toIntExact(integral(rows, column, table));
    } catch (ArithmeticException failure) {
      throw new IllegalArgumentException("Invalid integer ID in " + table, failure);
    }
  }

  private static long integral(ResultSet rows, int column, String table) throws SQLException {
    java.math.BigDecimal value = rows.getBigDecimal(column);
    if (value == null) {
      throw new IllegalArgumentException("Null OD/mode ID in " + table);
    }
    try {
      return value.longValueExact();
    } catch (ArithmeticException failure) {
      throw new IllegalArgumentException("Invalid integer ID in " + table, failure);
    }
  }

  private static double sum(double[] values) {
    double sum = 0;
    for (double value : values) {
      sum += value;
    }
    if (!Double.isFinite(sum)) {
      throw new IllegalArgumentException("Quantity totals exceed the numeric range");
    }
    return sum;
  }

  private static void checkCancelled(BooleanSupplier proceed) {
    if (Thread.currentThread().isInterrupted() || !proceed.getAsBoolean()) {
      throw new CancellationException("Performance computation cancelled");
    }
  }

  private static List<String> tableNames(Connection connection) throws SQLException {
    List<String> result = new ArrayList<>();
    try (ResultSet tables =
        connection
            .getMetaData()
            .getTables(connection.getCatalog(), connection.getSchema(), "%", null)) {
      while (tables.next()) {
        String type = tables.getString("TABLE_TYPE");
        if ("TABLE".equals(type) || "BASE TABLE".equals(type)) {
          result.add(tables.getString("TABLE_NAME"));
        }
      }
    }
    return result;
  }

  private static String resolve(Connection connection, String requested) throws SQLException {
    if (requested == null || requested.isBlank()) {
      throw new IllegalArgumentException("Select an assignment path-header table");
    }
    String match = null;
    for (String actual : tableNames(connection)) {
      if (actual.equals(requested)) {
        return actual;
      }
      if (actual.equalsIgnoreCase(requested)) {
        if (match != null) {
          throw new SQLException("Ambiguous table name: " + requested);
        }
        match = actual;
      }
    }
    if (match == null) {
      throw new SQLException("Table not found: " + requested);
    }
    return match;
  }

  private static Map<String, String> requireColumns(
      Connection connection, String name, boolean paths) throws SQLException {
    Map<String, String> fields = columns(connection, name);
    List<String> required = new ArrayList<>(List.of("grp", "org", "dst", "qty"));
    if (paths) {
      required.add("ldmode");
    }
    if (!fields.keySet().containsAll(required)) {
      throw new SQLException("Missing required columns in " + name + ": " + required);
    }
    return fields;
  }

  private static Map<String, String> columns(Connection connection, String table)
      throws SQLException {
    Map<String, String> fields = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery("SELECT * FROM " + quote(connection, table) + " WHERE 1=0")) {
      for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
        String name = result.getMetaData().getColumnName(i);
        fields.put(name, quote(connection, name));
      }
    }
    return fields;
  }

  private static String quote(Connection connection, String name) throws SQLException {
    String quote = connection.getMetaData().getIdentifierQuoteString();
    if (quote == null || quote.isBlank()) {
      throw new SQLException("The database cannot quote table identifiers");
    }
    return quote + name.replace(quote, quote + quote) + quote;
  }
}
