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
 *
 */

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.ImportSource;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableImport;
import edu.uclouvain.core.nodus.database.parquet.ParquetFiles;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import groovy.transform.CompileStatic;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;

/**
 * Selects geography and commodities from a catalogued master OD dataset.
 *
 * <p>Edit the typed settings below and run this script inside Nodus with the matching project open.
 * By default, catalog.json and the Parquet masters are read from the open project directory. Set
 * masterDirectory explicitly only when the dataset is elsewhere. See ODMatrixExtractor.md for the
 * configuration and dataset contract. No separate extractor JAR is required.
 */
@CompileStatic
public final class ExtractMasterOD {

  // Editable settings. Empty modes/groups lists mean all catalogue modes/commodity groups.

  /**
   * Empty uses the open project directory; otherwise specify the folder containing catalog.json.
   */
  private String masterDirectory = "";

  /** Mode names from the catalogue, for example Arrays.asList("road", "rail", "iww"). */
  private List<String> modes = new ArrayList<String>();

  /** NUTS level 2 or 3; the corresponding centroid layer must be loaded. */
  private int nutsLevel = 3;

  /** Detailed NST/R codes (NSTR) or main chapters (CHAPTER). */
  private String commodityLevel = "NSTR";

  /** Integer codes at commodityLevel, for example Arrays.asList(0, 1, 51) for NST/R. */
  private List<Integer> groups = new ArrayList<Integer>();

  /** Prefix for generated tables. Change this when using your actual master dataset. */
  private String tablePrefix = "SAMPLEOD";

  /** AVAILABLE_WITH_REPORT retains known quantities; REQUIRE_COMPLETE rejects selected gaps. */
  private String missingPolicy = "AVAILABLE_WITH_REPORT";

  /** Verify all selected master file hashes before replacing any output table. */
  private boolean verifyHashes = true;

  /** Number of inserts per JDBC batch (the embedded helper clamps this to 100..10000). */
  private int batchSize = 5000;

  /** Empty uses the open project's ODExtractionReports folder. */
  private String reportDirectory = "";

  /**
   * Runs with the editable settings above.
   *
   * @param nodusMapPanel the map panel supplied by Nodus to Groovy
   * @throws Exception if validation, import, report writing or cooperative cancellation fails
   */
  public ExtractMasterOD(NodusMapPanel nodusMapPanel) throws Exception {
    this(nodusMapPanel, Collections.<String, Object>emptyMap());
  }

  /**
   * Runs with optional settings overrides, principally for automation and regression tests.
   *
   * @param nodusMapPanel the map panel supplied by Nodus to Groovy
   * @param overrides configuration entries to replace after constructing the editable settings
   * @throws Exception if validation, import, report writing or cooperative cancellation fails
   */
  public ExtractMasterOD(NodusMapPanel nodusMapPanel, Map<String, ?> overrides) throws Exception {
    if (nodusMapPanel == null) {
      throw new IllegalStateException("Run this script with the Nodus map panel.");
    }
    NodusProject project = nodusMapPanel.getNodusProject();
    if (project == null || !project.isOpen()) {
      throw new IllegalStateException(
          "Open the relevant Nodus project before running this script.");
    }
    Map<String, Object> settings = new LinkedHashMap<String, Object>();
    settings.put(
        "masterDirectory",
        masterDirectory.isBlank()
            ? project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
            : masterDirectory);
    if (!modes.isEmpty()) {
      settings.put("modes", modes);
    }
    settings.put("nutsLevel", nutsLevel);
    settings.put("commodityLevel", commodityLevel);
    settings.put("groups", groups);
    settings.put("tablePrefix", tablePrefix);
    settings.put("missingPolicy", missingPolicy);
    settings.put("verifyHashes", verifyHashes);
    settings.put("batchSize", batchSize);
    if (!reportDirectory.isEmpty()) {
      settings.put("reportDirectory", reportDirectory);
    }
    settings.putAll(overrides);
    Object directory = settings.get("masterDirectory");
    if (directory == null || directory.toString().isBlank()) {
      throw new IllegalStateException(
          "The open project has no directory; set masterDirectory explicitly.");
    }
    AtomicBoolean active = runningFlag(nodusMapPanel);
    if (!active.compareAndSet(false, true)) {
      throw new IllegalStateException(
          "Another OD extraction is already running in this Nodus panel.");
    }
    try {
      List<Map<String, Object>> reports = ODMatrixExtractor.extract(project, settings);
      System.out.println(
          "Finished "
              + reports.size()
              + " mode table(s). Select the generated OD table in the assignment dialog.");
    } finally {
      active.set(false);
    }
  }

  /** Shares a JDK flag across separate script compilations through Nodus's generic object store. */
  private static AtomicBoolean runningFlag(NodusMapPanel panel) {
    synchronized (panel) {
      String key = "ExtractMasterOD.running";
      Object existing = panel.retrieveObject(key);
      if (existing == null) {
        existing = new AtomicBoolean();
        panel.storeObject(key, existing);
      }
      if (!(existing instanceof AtomicBoolean)) {
        throw new IllegalStateException("Unexpected OD script execution-state object.");
      }
      return (AtomicBoolean) existing;
    }
  }
}

/**
 * Extracts assignment-ready OD tables from a catalogued, sorted Parquet master dataset.
 *
 * <p>This script-local helper accepts a Groovy-friendly options map. The catalogue is read from
 * {@code masterDirectory/catalog.json}; no helper JAR, Hadoop installation or external database
 * process is needed. See {@code scripts/ODMatrixExtractor.md} for the complete version-1 catalogue,
 * Parquet schema, options and report contracts. This class is not part of Nodus core. The dataset
 * year comes from the catalogue.
 *
 * <p>Each selected mode replaces one table named {@code PREFIX_L<level>_<NSTR|CHAPTER>_<MODE>} with
 * numeric {@code GRP, ORG, DST, QTY} columns. Only positive available quantities are emitted.
 * Direction, intrazonal flows and proxy quantities are retained. Missing components and
 * source-absent commodities remain explicitly reported; they are never inferred to be observed zero
 * demand. No cost or reachability filter is applied.
 *
 * <p>All selected files and the strict missing-data policy are checked before the first import.
 * Publication uses {@link TableImport}: recovery is per table, not across modes. Earlier published
 * modes remain published if a later mode fails. Aggregation buffers one output commodity group at a
 * time; detailed NUTS3/NST/R extraction streams without an OD aggregation map.
 *
 * <p>Call from a background script thread, with the project and centroid layers kept open and
 * unchanged. Do not run assignments, edit the database/layers or switch projects during extraction.
 * The extractor borrows the project's JDBC connection and never closes it. Simultaneous extractor
 * calls are rejected because Nodus also uses process-wide JDBC metadata.
 *
 * <p>Interrupting the <em>calling</em> thread requests cooperative cancellation. A private worker
 * performs JDBC operations without being interrupted, then completes staging recovery before the
 * caller returns. The caller's interruption is consumed. Do not interrupt the JDBC worker or close
 * its connection: interrupting file-backed JDBC I/O can damage embedded database files. A mode
 * already past its final cancellation check may finish publication. Callbacks execute on that
 * worker and must not block, interrupt it, edit the project or directly access Swing components.
 */
@CompileStatic
final class ODMatrixExtractor {
  private static final ReentrantLock EXTRACTION_LOCK = new ReentrantLock();

  /**
   * Extracts selected modes and saves one UTF-8 JSON reconciliation report per started mode.
   *
   * <p>Progress is printed to standard output. Reports go to {@code reportDirectory}, or {@code
   * ODExtractionReports} beneath the open project's directory. Report filenames include a unique
   * run identifier and the destination table. A report-writing failure is propagated; it does not
   * undo a table that has already been published. Validation failures before any mode starts do not
   * create a mode report.
   *
   * @param project open project whose loaded centroids must match the catalogue
   * @param options extraction options; see the four-argument overload
   * @return unmodifiable reports for all successfully published modes
   * @throws Exception on invalid input, cancellation, file/report I/O or database failure
   * @see #extract(NodusProject, Map, Consumer, Consumer)
   */
  public static List<Map<String, Object>> extract(NodusProject project, Map<String, ?> options)
      throws Exception {
    Objects.requireNonNull(project, "project");
    Objects.requireNonNull(options, "options");
    Object explicit = options.get("reportDirectory");
    String projectDirectory = project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
    if (explicit == null && projectDirectory == null) {
      throw new IllegalArgumentException(
          "Set reportDirectory: the project has no directory property.");
    }
    Path folder =
        explicit == null
            ? Path.of(projectDirectory, "ODExtractionReports")
            : Path.of(explicit.toString());
    Files.createDirectories(folder);
    String runId = System.currentTimeMillis() + "_" + UUID.randomUUID();
    return extract(
        project,
        options,
        System.out::println,
        report -> {
          Path file = folder.resolve(runId + "_" + report.get("table") + ".json");
          try {
            Files.writeString(
                file,
                JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n",
                java.nio.file.StandardOpenOption.CREATE_NEW);
          } catch (IOException failure) {
            throw new java.io.UncheckedIOException(
                "Cannot write OD extraction report: " + file, failure);
          }
          System.out.println("Reconciliation report: " + file);
        });
  }

  /**
   * Extracts selected modes with caller-supplied progress and reconciliation callbacks.
   *
   * <p>This overload writes no report files itself. {@code reportSink} receives an unmodifiable
   * snapshot once per started mode, including failures/cancellation; it should persist that report.
   * If both extraction and reporting fail, the reporting error is suppressed on the extraction
   * exception. If reporting fails after publication, the table remains published and the exception
   * propagates. Exceptions before a mode starts are propagated without a mode report.
   *
   * <p>Supported options (unknown keys are rejected):
   *
   * <ul>
   *   <li>{@code masterDirectory}: required local directory containing {@code catalog.json}.
   *   <li>{@code modes}: list of catalogue mode names, default all catalogue modes.
   *   <li>{@code nutsLevel}: integer 2 or 3, default 3.
   *   <li>{@code commodityLevel}: {@code NSTR} or {@code CHAPTER}, default {@code NSTR}.
   *   <li>{@code groups}: list of integer codes at that commodity level, default/empty means all.
   *   <li>{@code tablePrefix}: SQL identifier prefix, default {@code OD} followed by catalogue
   *       year.
   *   <li>{@code missingPolicy}: {@code AVAILABLE_WITH_REPORT} (default) or {@code
   *       REQUIRE_COMPLETE}. Strict mode rejects unavailable quantities, unestimated components and
   *       source-absent groups.
   *   <li>{@code verifyHashes}: boolean, default true. Disabling skips SHA-256, not size/identity
   *       checks.
   *   <li>{@code batchSize}: positive integer, default 5000, clamped to 100..10000.
   *   <li>{@code reportDirectory}: used only by the two-argument overload.
   * </ul>
   *
   * @param project open project; its connection remains owned by Nodus
   * @param options configuration copied before extraction; do not mutate nested lists during a call
   * @param log progress callback, never null; runs on the JDBC worker
   * @param reportSink reconciliation callback, never null; runs on the JDBC worker
   * @return unmodifiable list of unmodifiable successful reports, in selected mode order
   * @throws IllegalArgumentException if configuration, catalogue or centroids are incompatible
   * @throws IllegalStateException if another extraction is running
   * @throws InterruptedIOException if cooperative cancellation is observed
   * @throws Exception on source, database or callback failure
   */
  public static List<Map<String, Object>> extract(
      NodusProject project,
      Map<String, ?> options,
      Consumer<String> log,
      Consumer<Map<String, Object>> reportSink)
      throws Exception {
    Objects.requireNonNull(project, "project");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(log, "log");
    Objects.requireNonNull(reportSink, "reportSink");
    cancelled();
    if (!EXTRACTION_LOCK.tryLock()) {
      throw new IllegalStateException("Another OD extraction is already running.");
    }
    try {
      if (!options.containsKey("masterDirectory")
          || options.get("masterDirectory") == null
          || options.get("masterDirectory").toString().isBlank()) {
        throw new IllegalArgumentException("masterDirectory is required.");
      }
      return run(project, new LinkedHashMap<String, Object>(options), log, reportSink);
    } finally {
      EXTRACTION_LOCK.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readCatalog(Path directory) throws IOException {
    Path file = directory.resolve("catalog.json");
    if (!Files.isRegularFile(file)) {
      throw new IOException("Missing OD master catalogue: " + file);
    }
    Object value = new JsonSlurper().parse(file.toFile(), "UTF-8");
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("OD master catalogue must be a JSON object.");
    }
    return (Map<String, Object>) value;
  }

  private static void validateOptions(Map<String, Object> config, Map<String, Object> catalog) {
    Set<String> keys =
        Set.of(
            "masterDirectory",
            "modes",
            "nutsLevel",
            "commodityLevel",
            "groups",
            "tablePrefix",
            "missingPolicy",
            "verifyHashes",
            "batchSize",
            "reportDirectory");
    for (String key : config.keySet()) {
      if (!keys.contains(key)) {
        throw new IllegalArgumentException("Unknown extraction option: " + key);
      }
      if (config.get(key) == null) {
        throw new IllegalArgumentException("Null extraction option: " + key);
      }
    }
    integer(config.getOrDefault("nutsLevel", 3), "nutsLevel", 2, 3);
    integer(config.getOrDefault("batchSize", 5000), "batchSize", 1, Integer.MAX_VALUE);
    if (config.containsKey("verifyHashes") && !(config.get("verifyHashes") instanceof Boolean)) {
      throw new IllegalArgumentException("verifyHashes must be a boolean.");
    }
    if (config.containsKey("modes")) {
      list(config.get("modes"), "modes");
    }
    if (config.containsKey("groups")) {
      list(config.get("groups"), "groups");
    }
    String prefix =
        config.getOrDefault("tablePrefix", "OD" + number(catalog.get("year"))).toString();
    if (!prefix.matches("[A-Za-z][A-Za-z0-9_]{0,59}")) {
      throw new IllegalArgumentException("tablePrefix must be a 1..60 character SQL identifier.");
    }
  }

  private static long integer(Object value, String label, long min, long max) {
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException(label + " must be an integer.");
    }
    double d = ((Number) value).doubleValue();
    long n = ((Number) value).longValue();
    if (!Double.isFinite(d) || d != n || n < min || n > max) {
      throw new IllegalArgumentException("Invalid " + label + ": " + value);
    }
    return n;
  }

  private static List<?> list(Object value, String label) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(label + " must be a list.");
    }
    return (List<?>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value, String label) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException(label + " must be an object.");
    }
    return (Map<String, Object>) value;
  }

  private static String text(Object value, String label) {
    if (!(value instanceof String) || ((String) value).isBlank()) {
      throw new IllegalArgumentException(label + " must be a nonempty string.");
    }
    return (String) value;
  }

  private static void nonnegative(Object value, String label) {
    if (!(value instanceof Number)
        || !Double.isFinite(((Number) value).doubleValue())
        || ((Number) value).doubleValue() < 0) {
      throw new IllegalArgumentException(label + " must be a finite nonnegative number.");
    }
  }

  private static void validateCatalog(Map<String, Object> catalog) {
    integer(catalog.get("schema_version"), "schema_version", 1, 1);
    integer(catalog.get("year"), "year", 1, 9999);
    if (!"annual tonnes".equals(catalog.get("unit"))) {
      throw new IllegalArgumentException("Catalogue unit must be annual tonnes.");
    }
    text(catalog.get("release"), "release");
    text(catalog.get("source_release"), "source_release");
    List<?> levels = list(catalog.get("supported_nuts_levels"), "supported_nuts_levels");
    if (!levels.contains(2) || !levels.contains(3)) {
      throw new IllegalArgumentException("Catalogue must support NUTS levels 2 and 3.");
    }
    List<?> classifications = list(catalog.get("classifications"), "classifications");
    if (!classifications.containsAll(List.of("NSTR", "CHAPTER"))) {
      throw new IllegalArgumentException(
          "Catalogue must declare NSTR and CHAPTER classifications.");
    }
    Map<Integer, Integer> chapters = new java.util.TreeMap<>();
    for (Object value : list(catalog.get("commodities"), "commodities")) {
      Map<String, Object> group = object(value, "commodity");
      int id = (int) integer(group.get("id"), "commodity id", 0, 99);
      int chapter = (int) integer(group.get("chapter"), "chapter", 0, 9);
      if (chapters.put(id, chapter) != null) {
        throw new IllegalArgumentException("Duplicate commodity id: " + id);
      }
    }
    if (chapters.isEmpty()) {
      throw new IllegalArgumentException("Catalogue has no commodities.");
    }
    int previous = -1;
    for (int chapter : chapters.values()) {
      if (chapter < previous) {
        throw new IllegalArgumentException("Chapters must follow NST/R order.");
      }
      previous = chapter;
    }
    Map<Long, Map<String, Object>> zones = new HashMap<>();
    for (Object value : list(catalog.get("zones"), "zones")) {
      Map<String, Object> zone = object(value, "zone");
      final long id = integer(zone.get("id"), "zone id", 1, Integer.MAX_VALUE);
      integer(zone.get("level"), "zone level", 2, 3);
      integer(zone.get("parent"), "zone parent", 0, Integer.MAX_VALUE);
      text(zone.get("code"), "zone code");
      if (zones.put(id, zone) != null) {
        throw new IllegalArgumentException("Duplicate zone id: " + id);
      }
    }
    if (zones.isEmpty()) {
      throw new IllegalArgumentException("Catalogue has no zones.");
    }
    for (Map<String, Object> zone : zones.values()) {
      if (number(zone.get("level")) == 3) {
        Map<String, Object> parent = zones.get(number(zone.get("parent")));
        if (parent == null || number(parent.get("level")) != 2) {
          throw new IllegalArgumentException("NUTS3 zone lacks a catalogued NUTS2 parent.");
        }
      }
    }
    Map<String, Object> modes = object(catalog.get("modes"), "modes");
    if (modes.isEmpty()) {
      throw new IllegalArgumentException("Catalogue has no modes.");
    }
    for (Map.Entry<String, Object> mode : modes.entrySet()) {
      if (!mode.getKey().matches("[a-z][a-z0-9_]{0,19}")) {
        throw new IllegalArgumentException("Invalid mode name: " + mode.getKey());
      }
      Map<String, Object> info = object(mode.getValue(), "mode");
      text(info.get("file"), "mode file");
      if (!text(info.get("sha256"), "sha256").matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("Invalid SHA-256 for " + mode.getKey());
      }
      integer(info.get("bytes"), "file bytes", 0, Long.MAX_VALUE);
      Set<Integer> present = new HashSet<>();
      for (Object value : list(info.get("groups"), "mode groups")) {
        Map<String, Object> group = object(value, "mode group");
        int id = (int) integer(group.get("id"), "mode group id", 0, 99);
        if (!chapters.containsKey(id) || !present.add(id)) {
          throw new IllegalArgumentException("Unknown or duplicate mode group: " + id);
        }
        integer(group.get("unestimated_components"), "unestimated_components", 0, Long.MAX_VALUE);
        integer(
            group.get("unavailable_quantity_rows"), "unavailable_quantity_rows", 0, Long.MAX_VALUE);
      }
      Set<Integer> absent = new HashSet<>();
      for (Object id : list(info.get("source_absent_nstr_groups"), "source_absent_nstr_groups")) {
        int group = (int) integer(id, "absent group", 0, 99);
        if (!chapters.containsKey(group) || present.contains(group) || !absent.add(group)) {
          throw new IllegalArgumentException(
              "Unknown, present or duplicate source-absent group: " + id);
        }
      }
      Set<Integer> declared = new HashSet<>(present);
      declared.addAll(absent);
      if (!declared.equals(chapters.keySet())) {
        throw new IllegalArgumentException(
            "Mode must declare every commodity present or source-absent.");
      }
    }
    if (catalog.containsKey("unlocated_iww_tonnes")) {
      nonnegative(catalog.get("unlocated_iww_tonnes"), "unlocated_iww_tonnes");
    }
  }

  private static List<Integer> absentGroups(
      Map<String, Object> info,
      String classification,
      Set<Integer> chosen,
      Map<Integer, Integer> chapters) {
    List<Integer> absent = new ArrayList<>();
    for (Object value : (List<?>) info.get("source_absent_nstr_groups")) {
      int id = ((Number) value).intValue();
      if (chosen.isEmpty()
          || chosen.contains(classification.equals("NSTR") ? id : chapters.get(id))) {
        absent.add(id);
      }
    }
    Collections.sort(absent);
    return Collections.unmodifiableList(absent);
  }

  private ODMatrixExtractor() {}

  /** Compensated summation limits drift across millions of source rows. */
  private static final class Sum {
    double value;
    double correction;

    void add(double x) {
      double y = x - correction;
      double t = value + y;
      correction = (t - value) - y;
      value = t;
    }
  }

  /** Identity and hierarchy only: changing centroid coordinates does not invalidate a master. */
  private static final class Zone {
    final long id;
    final long parent;
    final int level;
    final String code;

    Zone(long i, int l, String c, long p) {
      id = i;
      level = l;
      code = c;
      parent = p;
    }
  }

  /** Quantities and quality counts for the current mode, retained when staging fails. */
  private static final class State {
    long scanned;
    long selected;
    long positive;
    long zero;
    long unavailable;
    long pending;
    long partial;
    long proxyRows;
    long spatialRows;
    long written;
    long maxBuffered;
    final Sum selectedQty = new Sum();
    final Sum exportedQty = new Sum();
    final Sum proxyQty = new Sum();
    final Sum spatialQty = new Sum();
  }

  private static final class Cell {
    final Sum qty = new Sum();
  }

  private static String ident(String s) {
    if (s == null || !s.matches("[A-Za-z][A-Za-z0-9_]{0,100}")) {
      throw new IllegalArgumentException("Invalid generated table identifier: " + s);
    }
    return JDBCUtils.getQuotedCompliantIdentifier(s);
  }

  private static long number(Object v) {
    return ((Number) v).longValue();
  }

  private static double real(Object v) {
    return v == null ? 0 : ((Number) v).doubleValue();
  }

  private static final ThreadLocal<AtomicBoolean> CANCELLATION = new ThreadLocal<>();
  private static final int[] REQUIRED_COLUMNS = new int[] {0, 1, 2, 3, 4, 5, 7, 8, 10};

  private static void cancelled() throws InterruptedIOException {
    AtomicBoolean token = CANCELLATION.get();
    boolean interrupted = Thread.interrupted();
    if (interrupted || (token != null && token.get())) {
      throw new InterruptedIOException("OD extraction cancelled");
    }
  }

  static String sha256(Path path) throws Exception {
    MessageDigest h = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(path)) {
      byte[] bytes = new byte[4 * 1024 * 1024];
      int n;
      while ((n = in.read(bytes)) >= 0) {
        cancelled();
        h.update(bytes, 0, n);
      }
    }
    StringBuilder hex = new StringBuilder(64);
    for (byte value : h.digest()) {
      hex.append(String.format(Locale.ROOT, "%02x", value & 255));
    }
    return hex.toString();
  }

  /** Snapshots centroid attributes before reading any source file. */
  private static Map<Long, Zone> loadedZones(NodusProject project) {
    Map<Long, Zone> out = new HashMap<>();
    NodusEsriLayer[] layers = project.getNodeLayers();
    if (layers == null) {
      throw new IllegalArgumentException("The open project has no centroid layers.");
    }
    for (NodusEsriLayer layer : layers) {
      DbfTableModel m = layer.getModel();
      if (m == null) {
        continue;
      }
      Map<String, Integer> f = new HashMap<>();
      for (int j = 0; j < m.getColumnCount(); j++) {
        f.put(m.getColumnName(j).toUpperCase(Locale.ROOT), j);
      }
      if (!f.containsKey("LEVEL") || !f.containsKey("ZONE_CODE")) {
        continue;
      }
      if (!f.containsKey("NUM") || !f.containsKey("PARENT_ID")) {
        throw new IllegalArgumentException(
            "Centroid layer needs NUM, LEVEL, ZONE_CODE and PARENT_ID.");
      }
      for (int r = 0; r < m.getRowCount(); r++) {
        Zone z =
            new Zone(
                integer(m.getValueAt(r, f.get("NUM")), "loaded centroid NUM", 1, Integer.MAX_VALUE),
                (int) integer(m.getValueAt(r, f.get("LEVEL")), "loaded centroid LEVEL", 2, 3),
                String.valueOf(m.getValueAt(r, f.get("ZONE_CODE"))).trim(),
                integer(
                    m.getValueAt(r, f.get("PARENT_ID")),
                    "loaded centroid PARENT_ID",
                    0,
                    Integer.MAX_VALUE));
        Zone old = out.put(z.id, z);
        if (old != null
            && (old.level != z.level || !old.code.equals(z.code) || old.parent != z.parent)) {
          throw new IllegalArgumentException("Inconsistent duplicate centroid ID " + z.id);
        }
      }
    }
    return out;
  }

  private static void checkZone(
      long id, int level, Map<Long, Zone> expected, Map<Long, Zone> loaded, Set<Long> checked) {
    if (checked.contains(id)) {
      return;
    }
    Zone a = expected.get(id);
    Zone b = loaded.get(id);
    if (a == null
        || b == null
        || a.level != level
        || b.level != level
        || !a.code.equals(b.code)
        || a.parent != b.parent) {
      throw new IllegalArgumentException(
          "Centroid "
              + id
              + " at level "
              + level
              + " does not match the open project's ID, zone code or parent. "
              + "Open the relevant new network/centroid layers.");
    }
    checked.add(id);
  }

  /** Positive signed-32-bit IDs preserve lexicographic order in a packed positive long. */
  private static long pairKey(long org, long dst) {
    if (org <= 0 || dst <= 0 || org > Integer.MAX_VALUE || dst > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Centroid ID outside Nodus signed integer range.");
    }
    return (org << 32) | (dst & 0xffffffffL);
  }

  private static int intValue(Group r, int col) {
    return r.getInteger(col, 0);
  }

  private static double doubleValue(Group r, int col) {
    return r.getFieldRepetitionCount(col) == 0 ? 0 : r.getDouble(col, 0);
  }

  private static boolean boolValue(Group r, int col) {
    return r.getFieldRepetitionCount(col) > 0 && r.getBoolean(col, 0);
  }

  /** Waits for cooperative worker cleanup; never cancels a JDBC operation by interruption. */
  private static List<Map<String, Object>> run(
      NodusProject project,
      Map<String, Object> config,
      Consumer<String> log,
      Consumer<Map<String, Object>> reportSink)
      throws Exception {
    cancelled();
    AtomicBoolean token = new AtomicBoolean();
    ExecutorService executor =
        Executors.newSingleThreadExecutor(
            task -> {
              Thread thread = new Thread(task, "OD-master-extraction");
              thread.setDaemon(true);
              return thread;
            });
    CompletableFuture<Void> finished = new CompletableFuture<Void>();
    Future<List<Map<String, Object>>> future =
        executor.submit(
            () -> {
              CANCELLATION.set(token);
              try {
                return runInternal(project, config, log, reportSink);
              } finally {
                CANCELLATION.remove();
                finished.complete(null);
              }
            });
    try {
      for (; ; ) {
        try {
          return future.get();
        } catch (InterruptedException requested) {
          token.set(true); /* Never interrupt the JDBC worker or close its NIO file channel. */
        } catch (ExecutionException failed) {
          Throwable cause = failed.getCause();
          if (cause instanceof Exception) {
            throw (Exception) cause;
          }
          if (cause instanceof Error) {
            throw (Error) cause;
          }
          throw new RuntimeException(cause);
        }
      }
    } finally {
      // Native-console AST interrupt checks may throw before Future.get is entered.
      // A Java join is uninterruptible and contains no injected Groovy loop/method checks.
      token.set(true);
      finished.join();
      Thread.interrupted();
      executor.shutdown();
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> runInternal(
      NodusProject project,
      Map<String, Object> config,
      Consumer<String> log,
      Consumer<Map<String, Object>> reportSink)
      throws Exception {
    Path directory =
        Path.of(String.valueOf(config.get("masterDirectory"))).toAbsolutePath().normalize();
    Map<String, Object> catalog = readCatalog(directory);
    validateCatalog(catalog);
    validateOptions(config, catalog);

    if (project == null
        || project.getMainJDBCConnection() == null
        || project.getMainJDBCConnection().isClosed()) {
      throw new IllegalArgumentException("Open a Nodus project before running the extractor.");
    }
    final Path root =
        Path.of(String.valueOf(config.get("masterDirectory"))).toAbsolutePath().normalize();
    int level = ((Number) config.getOrDefault("nutsLevel", 3)).intValue();
    if (level != 2 && level != 3) {
      throw new IllegalArgumentException("Only levels 2 and 3 have supported centroid mappings.");
    }
    String classification =
        String.valueOf(config.getOrDefault("commodityLevel", "NSTR")).toUpperCase(Locale.ROOT);
    if (!classification.equals("NSTR") && !classification.equals("CHAPTER")) {
      throw new IllegalArgumentException("commodityLevel must be NSTR or CHAPTER.");
    }
    String prefix =
        String.valueOf(config.getOrDefault("tablePrefix", "OD" + number(catalog.get("year"))))
            .toUpperCase(Locale.ROOT);
    ident(prefix);
    String missing =
        String.valueOf(config.getOrDefault("missingPolicy", "AVAILABLE_WITH_REPORT"))
            .toUpperCase(Locale.ROOT);
    if (!Set.of("AVAILABLE_WITH_REPORT", "REQUIRE_COMPLETE").contains(missing)) {
      throw new IllegalArgumentException("Unknown missingPolicy.");
    }
    final boolean hashes = !Boolean.FALSE.equals(config.get("verifyHashes"));
    final int batchSize =
        Math.max(
            100, Math.min(10000, ((Number) config.getOrDefault("batchSize", 5000)).intValue()));
    Set<Integer> allowed = new HashSet<>();
    Map<Integer, Integer> chapters = new HashMap<>();
    for (Map<String, Object> g : (List<Map<String, Object>>) catalog.get("commodities")) {
      int id = ((Number) g.get("id")).intValue();
      int chapter = ((Number) g.get("chapter")).intValue();
      chapters.put(id, chapter);
      allowed.add(classification.equals("NSTR") ? id : chapter);
    }
    Set<Integer> chosen = new HashSet<>();
    for (Object g : (List<?>) config.getOrDefault("groups", List.of())) {
      if (!(g instanceof Number)
          || ((Number) g).doubleValue() != ((Number) g).intValue()
          || !allowed.contains(((Number) g).intValue())) {
        throw new IllegalArgumentException("Unknown/non-integer " + classification + " group " + g);
      }
      chosen.add(((Number) g).intValue());
    }
    List<String> modes = new ArrayList<>();
    for (Object m :
        (List<?>)
            config.getOrDefault(
                "modes", new ArrayList<>(((Map<?, ?>) catalog.get("modes")).keySet()))) {
      String mode = String.valueOf(m).toLowerCase(Locale.ROOT);
      if (!((Map<?, ?>) catalog.get("modes")).containsKey(mode) || modes.contains(mode)) {
        throw new IllegalArgumentException("Unknown or duplicate mode: " + mode);
      }
      modes.add(mode);
    }
    if (modes.isEmpty()) {
      throw new IllegalArgumentException("Select at least one mode.");
    }

    Map<Long, Zone> expected = new HashMap<>();
    for (Map<String, Object> z : (List<Map<String, Object>>) catalog.get("zones")) {
      Zone value =
          new Zone(
              number(z.get("id")),
              ((Number) z.get("level")).intValue(),
              String.valueOf(z.get("code")),
              number(z.get("parent")));
      expected.put(value.id, value);
    }
    Map<Long, Zone> loaded = loadedZones(project);
    Map<String, Object> modeCatalog = (Map<String, Object>) catalog.get("modes");
    Map<String, ImportSource> identities = new HashMap<>();
    // Validate every selected source/hash and strict coverage policy before publishing any mode.
    for (String mode : modes) {
      cancelled();
      Map<String, Object> info = (Map<String, Object>) modeCatalog.get(mode);
      final Path file = root.resolve(String.valueOf(info.get("file"))).toRealPath();
      if (!file.startsWith(root.toRealPath())) {
        throw new IllegalArgumentException("Master file is outside its package.");
      }
      ImportSource identity = new ImportSource(file);
      if (Files.size(file) != number(info.get("bytes"))) {
        throw new IOException("Master size differs from catalog: " + mode);
      }
      if (hashes && !sha256(file).equals(info.get("sha256"))) {
        throw new IOException("Master SHA256 differs from catalog: " + mode);
      }
      identity.verify();
      identities.put(mode, identity);
      if (missing.equals("REQUIRE_COMPLETE")
          && !absentGroups(info, classification, chosen, chapters).isEmpty()) {
        throw new IllegalArgumentException(
            "Selected "
                + mode
                + " has source-absent NST/R groups; completeness cannot be established.");
      }
      if (missing.equals("REQUIRE_COMPLETE")) {
        for (Map<String, Object> g : (List<Map<String, Object>>) info.get("groups")) {
          int code = ((Number) g.get("id")).intValue();
          int output = classification.equals("NSTR") ? code : chapters.get(code);
          if ((chosen.isEmpty() || chosen.contains(output))
              && (number(g.get("unestimated_components")) > 0
                  || number(g.get("unavailable_quantity_rows")) > 0)) {
            throw new IllegalArgumentException(
                "Selected "
                    + mode
                    + " demand contains unestimated components. "
                    + "REQUIRE_COMPLETE leaves all destination tables unchanged. "
                    + "Use AVAILABLE_WITH_REPORT explicitly to extract known "
                    + "available quantities.");
          }
        }
      }
    }
    List<Map<String, Object>> results = new ArrayList<>();
    for (String mode : modes) {
      Map<String, Object> info = (Map<String, Object>) modeCatalog.get(mode);
      final Path file = root.resolve(String.valueOf(info.get("file")));
      String target =
          prefix + "_L" + level + "_" + classification + "_" + mode.toUpperCase(Locale.ROOT);
      ident(target);
      final State state = new State();
      final long started = System.nanoTime();
      Map<String, Object> report = new LinkedHashMap<>();
      report.put("mode", mode);
      report.put("table", target);
      report.put("nuts_level", level);
      report.put("commodity_level", classification);
      report.put(
          "selected_groups", Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(chosen))));
      report.put("missing_policy", missing);
      report.put("year", catalog.get("year"));
      report.put("unit", catalog.get("unit"));
      report.put(
          "source_absent_detailed_nstr_groups_in_selection",
          absentGroups(info, classification, chosen, chapters));
      report.put("source_absence_is_not_observed_zero", true);
      report.put("source_release", catalog.get("source_release"));
      report.put("master_release", catalog.get("release"));
      report.put("source_file", file.toString());
      report.put("sha256_verified", hashes);
      report.put("source_sha256", info.get("sha256"));
      report.put(
          "unlocated_iww_all_goods_tonnes_not_assigned",
          mode.equals("iww") ? real(catalog.get("unlocated_iww_tonnes")) : 0.0d);
      report.put(
          "unlocated_scope", "All-goods ledger; no commodity-specific allocation is inferred.");
      Exception failure = null;
      try {
        log.accept("Extracting " + mode + " to " + target + " (" + missing + ")");
        TableImport.replace(
            project,
            target,
            (connection, stage) ->
                load(
                    connection,
                    stage,
                    file,
                    level,
                    classification,
                    chosen,
                    chapters,
                    expected,
                    loaded,
                    missing,
                    batchSize,
                    identities.get(mode),
                    state,
                    log));
        report.put("status", "PUBLISHED");
        long count;
        try (Statement stmt = project.getMainJDBCConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + ident(target))) {
          rs.next();
          count = rs.getLong(1);
        }
        if (count != state.written) {
          throw new SQLException("Published row count differs from the checked staging count.");
        }
        results.add(Collections.unmodifiableMap(report));
        log.accept(
            "Published "
                + target
                + ": "
                + count
                + " rows, "
                + state.exportedQty.value
                + " available tonnes; unestimated components="
                + state.pending);
      } catch (Exception e) {
        failure = e;
        AtomicBoolean token = CANCELLATION.get();
        if (!"PUBLISHED".equals(report.get("status"))) {
          report.put("status", token != null && token.get() ? "CANCELLED" : "FAILED");
        }
        report.put("error", String.valueOf(e.getMessage()));
        throw e;
      } finally {
        report.put("source_rows_scanned", state.scanned);
        report.put("selected_source_rows", state.selected);
        report.put("positive_source_rows", state.positive);
        report.put("zero_available_source_rows", state.zero);
        report.put("source_rows_without_available_quantity", state.unavailable);
        report.put("selected_unestimated_components", state.pending);
        report.put("selected_incomplete_source_rows", state.partial);
        report.put("selected_proxy_source_rows", state.proxyRows);
        report.put("selected_proxy_available_tonnes", state.proxyQty.value);
        report.put("selected_spatial_review_source_rows", state.spatialRows);
        report.put("selected_spatial_review_available_tonnes", state.spatialQty.value);
        report.put("selected_available_tonnes", state.selectedQty.value);
        report.put("exported_rows", state.written);
        report.put("exported_available_tonnes", state.exportedQty.value);
        report.put("quantity_difference_tonnes", state.exportedQty.value - state.selectedQty.value);
        report.put("maximum_buffered_output_pairs_in_one_commodity_group", state.maxBuffered);
        report.put("elapsed_seconds", (System.nanoTime() - started) / 1e9d);
        report.put(
            "quantity_scope",
            "Available estimates only; missing/absent demand is not an observed zero. "
                + "Proxy/spatial flags overlap and their totals must not be added.");
        Map<String, Object> snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(report));
        try {
          reportSink.accept(snapshot);
        } catch (RuntimeException reportFailure) {
          if (failure != null) {
            failure.addSuppressed(reportFailure);
          } else {
            throw reportFailure;
          }
        }
      }
    }
    return Collections.unmodifiableList(results);
  }

  /** Streams projected fields, validates ordered keys and stages conserved available quantities. */
  private static void load(
      Connection con,
      String stage,
      Path file,
      int level,
      String classification,
      Set<Integer> chosen,
      Map<Integer, Integer> chapters,
      Map<Long, Zone> expected,
      Map<Long, Zone> loaded,
      String missing,
      int batchSize,
      ImportSource identity,
      State state,
      Consumer<String> log)
      throws Exception {
    try (Statement st = con.createStatement()) {
      st.execute(
          "CREATE TABLE "
              + ident(stage)
              + " (GRP SMALLINT, ORG BIGINT, DST BIGINT, QTY DOUBLE PRECISION)");
    }
    // Match TableImport: only its isolated DDL connection may commit intermediate staging batches.
    boolean chunks =
        !con.getMetaData().supportsDataDefinitionAndDataManipulationTransactions()
            || con.getMetaData().dataDefinitionCausesTransactionCommit();
    Set<Long> checked = new HashSet<>();
    // One output commodity group at a time bounds memory even for continental masters.
    HashMap<Long, Cell> cells = new HashMap<>();
    int bufferedGroup = -1;
    int previousNstr = -1;
    long previousPair = -1;
    int[] pendingBatch = new int[] {0};
    String[] fields =
        new String[] {
          "GRP",
          "ORG",
          "DST",
          "ORG_L2",
          "DST_L2",
          "CHAPTER",
          "QTY_AVAILABLE_PART",
          "UNESTIMATED_CELLS",
          "PROXY_APPLIED",
          "PROXY_QTY",
          "SPATIAL_REVIEW"
        };
    try (PreparedStatement insert =
            con.prepareStatement("INSERT INTO " + ident(stage) + " VALUES(?,?,?,?)");
        ParquetFiles.ProjectedReader reader =
            ParquetFiles.openProjectedReader(file, Arrays.asList(fields))) {
      MessageType source = reader.getSchema();
      List<Type> wanted = new ArrayList<>();
      for (String field : fields) {
        if (!source.containsField(field)) {
          throw new IOException("Master schema missing " + field);
        }
        Type type = source.getType(field);
        int index = wanted.size();
        PrimitiveTypeName expectedType =
            index == 0 || index == 5 || index == 7
                ? PrimitiveTypeName.INT32
                : index >= 1 && index <= 4
                    ? PrimitiveTypeName.INT64
                    : index == 8 || index == 10
                        ? PrimitiveTypeName.BOOLEAN
                        : PrimitiveTypeName.DOUBLE;
        if (!type.isPrimitive()
            || type.isRepetition(Type.Repetition.REPEATED)
            || type.asPrimitiveType().getPrimitiveTypeName() != expectedType) {
          throw new IOException(
              "Master schema has invalid type for " + field + "; expected flat " + expectedType);
        }
        wanted.add(type);
      }
      MessageType projection = source;
      Group row;
      while ((row = reader.read()) != null) {
        if ((state.scanned & 8191) == 0) {
          cancelled();
        }
        Group r = row;
        state.scanned++;
        for (int required : REQUIRED_COLUMNS) {
          if (r.getFieldRepetitionCount(required) != 1) {
            throw new IOException(
                "Missing required master value at row "
                    + state.scanned
                    + ", field "
                    + projection.getFieldName(required));
          }
        }
        int nstr = intValue(r, 0);
        int chapter = intValue(r, 5);
        long org = r.getLong(1, 0);
        long dst = r.getLong(2, 0);
        long pair = pairKey(org, dst);
        if (!chapters.containsKey(nstr)
            || chapters.get(nstr) != chapter
            || nstr < previousNstr
            || (nstr == previousNstr && pair <= previousPair)) {
          throw new IOException(
              "Master must contain unique keys sorted by GRP, ORG, DST "
                  + "and the declared chapter mapping (row "
                  + state.scanned
                  + ").");
        }
        previousNstr = nstr;
        previousPair = pair;
        Zone fineOrg = expected.get(org);
        Zone fineDst = expected.get(dst);
        if (fineOrg == null
            || fineDst == null
            || fineOrg.level != 3
            || fineDst.level != 3
            || fineOrg.parent != r.getLong(3, 0)
            || fineDst.parent != r.getLong(4, 0)) {
          throw new IOException(
              "Master centroid/parent mapping differs from catalog at row " + state.scanned);
        }
        int group = classification.equals("NSTR") ? nstr : chapter;
        if (!chosen.isEmpty() && !chosen.contains(group)) {
          continue;
        }
        state.selected++;
        if (bufferedGroup != -1 && group != bufferedGroup) {
          if (group < bufferedGroup) {
            throw new IOException("Output commodity groups are not ordered.");
          }
          flush(cells, bufferedGroup, insert, con, chunks, batchSize, pendingBatch, state);
          cells.clear();
        }
        bufferedGroup = group;
        long pending = r.getFieldRepetitionCount(7) == 0 ? 0 : r.getInteger(7, 0);
        if (pending < 0) {
          throw new IOException("Negative missing-component count.");
        }
        state.pending += pending;
        if (pending > 0) {
          state.partial++;
        }
        boolean available = r.getFieldRepetitionCount(6) > 0;
        double qty = doubleValue(r, 6);
        double proxy = doubleValue(r, 9);
        if (!Double.isFinite(qty) || qty < 0 || !Double.isFinite(proxy) || proxy < 0) {
          throw new IOException("Nonfinite or negative quantity at row " + state.scanned);
        }
        if (!available) {
          state.unavailable++;
          if (missing.equals("REQUIRE_COMPLETE")) {
            throw new IOException("Unavailable selected quantity.");
          }
        }
        if (pending > 0 && missing.equals("REQUIRE_COMPLETE")) {
          throw new IOException("Unestimated selected source components.");
        }
        state.selectedQty.add(qty);
        if (boolValue(r, 8)) {
          state.proxyRows++;
          state.proxyQty.add(proxy);
        }
        if (boolValue(r, 10)) {
          state.spatialRows++;
          state.spatialQty.add(qty);
        }
        if (level == 2) {
          org = r.getLong(3, 0);
          dst = r.getLong(4, 0);
        }
        checkZone(org, level, expected, loaded, checked);
        checkZone(dst, level, expected, loaded, checked);
        final long key = pairKey(org, dst);

        if (!available) {
          continue;
        }
        if (qty == 0) {
          state.zero++;
          continue;
        }
        state.positive++;
        if (level == 3 && classification.equals("NSTR")) {
          emit(insert, con, chunks, batchSize, pendingBatch, state, group, org, dst, qty);
        } else {
          Cell cell = cells.get(key);
          if (cell == null) {
            cell = new Cell();
            cells.put(key, cell);
          }
          cell.qty.add(qty);
          state.maxBuffered = Math.max(state.maxBuffered, cells.size());
        }
        if (state.scanned % 1000000 == 0) {
          log.accept(
              "Read "
                  + state.scanned
                  + " master rows; selected="
                  + state.selected
                  + ", staged output="
                  + state.written);
        }
      }
      flush(cells, bufferedGroup, insert, con, chunks, batchSize, pendingBatch, state);
      if (pendingBatch[0] > 0) {
        insert.executeBatch();
      }
    }
    identity.verify();
    cancelled();
    if (Math.abs(state.exportedQty.value - state.selectedQty.value) > 0.05d) {
      throw new SQLException("Available tonnes were not conserved during extraction.");
    }
    try (Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + ident(stage))) {
      rs.next();
      if (rs.getLong(1) != state.written) {
        throw new SQLException("Staging row count mismatch.");
      }
    }
  }

  /** Sorts one aggregated commodity group before batching it, then the caller clears the map. */
  private static void flush(
      HashMap<Long, Cell> cells,
      int group,
      PreparedStatement insert,
      Connection con,
      boolean chunks,
      int batchSize,
      int[] pendingBatch,
      State state)
      throws Exception {
    if (cells.isEmpty()) {
      return;
    }
    long[] keys = new long[cells.size()];
    int i = 0;
    for (long key : cells.keySet()) {
      keys[i++] = key;
    }
    Arrays.sort(keys);
    for (long key : keys) {
      if ((state.written & 8191) == 0) {
        cancelled();
      }
      emit(
          insert,
          con,
          chunks,
          batchSize,
          pendingBatch,
          state,
          group,
          key >>> 32,
          key & 0xffffffffL,
          cells.get(key).qty.value);
    }
  }

  private static void emit(
      PreparedStatement insert,
      Connection con,
      boolean chunks,
      int batchSize,
      int[] pendingBatch,
      State state,
      int group,
      long org,
      long dst,
      double qty)
      throws SQLException {
    if (!Double.isFinite(qty) || qty <= 0) {
      throw new SQLException("Invalid output quantity.");
    }
    insert.setShort(1, (short) group);
    insert.setLong(2, org);
    insert.setLong(3, dst);
    insert.setDouble(4, qty);
    insert.addBatch();
    state.written++;
    state.exportedQty.add(qty);
    if (++pendingBatch[0] >= batchSize) {
      insert.executeBatch();
      pendingBatch[0] = 0;
      if (chunks && state.written % 100000 == 0) {
        con.commit();
      }
    }
  }
}

// Nodus injects the current application panel into the Groovy script.
new ExtractMasterOD(nodusMapPanel);
