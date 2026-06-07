/*
 * SimplifyNetworkForNodus_Java11Style_AllLineLayers.groovy
 *
 * Nodus/Groovy implementation of the Network Shapefile Simplifier.
 *
 * Purpose
 * -------
 * This script simplifies one or more loaded Nodus link layers, or all loaded link 
 * layers in scan mode, by removing intermediate transit nodes and merging the two 
 * polylines that meet at such a node. It works directly on the Nodus/OpenMap 
 * objects already loaded in the current project, rather than reading shapefiles
 * through GeoTools.
 *
 * The script is intended to be launched from the Nodus Groovy scripting system.
 * Nodus provides the variable `nodusMapPanel`; the final line of this file uses
 * that variable to instantiate the class and start processing.
 *
 * Java-like style
 * ---------------
 * The body is intentionally written in a Java 11 compatible style: explicit
 * imports, explicit types, semicolons, no `def`, no Groovy closures, no Groovy
 * list/map literals in the core logic, no records, no switch arrows, and no
 * Java 16+ syntax. Apart from the last script-launch line, the class body can be
 * moved into a .java file if you later decide to integrate it directly into
 * Nodus.
 *
 * Processing summary
 * ------------------
 * For every configured target link layer, or for every loaded target link layer when
 * scanAllLinkLayers is true, the script:
 *
 *   1. resolves the target line layer;
 *   2. resolves or auto-detects the point layer(s) containing its NODE1/NODE2
 *      endpoint IDs;
 *   3. resolves or auto-detects connector/blocking line layers;
 *   4. repeatedly finds removable transit nodes;
 *   5. merges eligible polyline pairs;
 *   6. removes the merged-away link and the intermediate point;
 *   7. writes a conflict CSV;
 *   8. backs up modified shapefiles and asks Nodus to save dirty ESRI layers.
 *
 * Important assumptions
 * ---------------------
 * - Link layers have NUM, NODE1 and NODE2 fields.
 * - Point layers have NUM and Tranship fields.
 * - Field matching is case-insensitive.
 * - Geometry topology is driven by NODE1/NODE2 attributes, not by geometric
 *   intersection alone.
 * - Connector layers are used as blockers: if a node is touched by a connector,
 *   the node is not removed unless the two touching objects are both in the
 *   target line layer.
 *
 * Safety notes
 * ------------
 * Run first with dryRun = true on a copy of a project. When dryRun = false, the
 * script modifies loaded layer objects in memory and then asks Nodus to save
 * them in the current project directory. Backups are created before saving when
 * createBackups = true.
 */

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicConstants;
import com.bbn.openmap.proj.ProjMath;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.JOptionPane;

/**
 * Main simplifier class.
 *
 * <p>The constructor is the entry point used by the Groovy script runner. It stores the {@link
 * NodusMapPanel}, obtains the current {@link NodusProject}, and immediately calls {@link #run()}.
 *
 * <p>The class stores global user settings at the top, followed by operational state such as
 * conflict rows and modified layers. Counters are reset for each target layer processed.
 */
public final class SimplifyNetworks {

  // ------------------------------------------------------------------
  // USER SETTINGS
  // ------------------------------------------------------------------

  /**
   * If true, the script builds one simplification job for every loaded Nodus link/polyline layer in
   * the current project.
   *
   * <p>This is the most portable mode: no layer prefix and no fixed list of line layers are
   * required. For each loaded line layer, the script attempts to auto-detect the referenced point
   * layer(s) and connector/blocking layers. Layers that cannot be resolved can either be skipped or
   * treated as errors, depending on {@link #skipUnresolvableScannedLayers}.
   *
   * <p>Set this to {@code false} when you want to use the explicit {@link #jobsToRun} list below.
   */
  private boolean scanAllLinkLayers = true;

  /**
   * If true, only visible link layers are considered when scanAllLinkLayers is enabled. If false,
   * all loaded link layers are considered.
   */
  private boolean scanOnlyVisibleLinkLayers = false;

  /**
   * If true, layers that cannot be resolved during an automatic all-layer scan are reported and
   * skipped. If false, the first unresolved layer stops the script.
   */
  private boolean skipUnresolvableScannedLayers = true;

  /**
   * Exact layer names to exclude when scanAllLinkLayers is enabled. Matching is case-insensitive
   * and accepts both the Nodus display name and table name.
   */
  private List<String> scanExcludedLayerNames = new ArrayList<String>();

  /**
   * Case-insensitive name fragments to exclude when scanAllLinkLayers is enabled. For example, add
   * {@code "conl"} if connector layers should never be simplified as target layers during a full
   * scan.
   */
  private List<String> scanExcludedNameHints = new ArrayList<String>();

  /**
   * Explicit simplification jobs to execute when scanAllLinkLayers is false.
   *
   * <p>The simplest and recommended explicit form is {@code JobSpec.line("layer_name")}. In that
   * mode the script auto-detects the point layer(s) and connector layers. More explicit
   * constructors are available when automatic detection is ambiguous or when you want full control.
   *
   * <p>The layer names are complete Nodus layer/table names, not prefixes. For example, use {@code
   * "iww_polylines"}, not {@code "iww"}.
   */
  private final List<JobSpec> jobsToRun =
      new ArrayList<JobSpec>(
          Arrays.asList(
              JobSpec.line("iww_polylines"),
              JobSpec.line("road_polylines"),
              JobSpec.line("rail_polylines")));

  /**
   * Advanced examples:
   *
   * <p>jobsToRun.add(JobSpec.line("iww_polylines"));
   * jobsToRun.add(JobSpec.withPointLayer("road_polylines", "road_points"));
   * jobsToRun.add(JobSpec.withPointLayers("multimodal_polylines", Arrays.asList("iww_points",
   * "rail_points"))); jobsToRun.add(JobSpec.withPointLayersAndConnectors("rail_polylines",
   * Arrays.asList("rail_points", "station_points"), Arrays.asList("rail_conl2", "rail_conl3")));
   * jobsToRun.add(JobSpec.withPointLayersAndConnectors("custom_links",
   * Arrays.asList("custom_nodes"), new ArrayList<String>())); // explicit no connectors
   */

  /**
   * If true, infer point layer(s) when a job does not explicitly list them.
   *
   * <p>Detection is based on the {@code NODE1}/{@code NODE2} values used by the target line layer.
   * A node layer is relevant when its {@code NUM} values cover those endpoint IDs.
   */
  private boolean autoDetectPointLayer = true;

  /**
   * If true, automatic point-layer detection may select several point layers.
   *
   * <p>This is useful for networks whose line layer references nodes stored in more than one point
   * layer. When false, a single point layer must cover all endpoints.
   */
  private boolean allowMultiplePointLayers = true;

  /** If true, infer connector/blocking layers when a job does not explicitly list them. */
  private boolean autoDetectConnectorLayers = true;

  /**
   * Connector auto-detection mode. true = every other link layer touching at least one endpoint
   * node of the target layer blocks merges; false = only connector-like layer names are used,
   * according to autoConnectorNameHints.
   */
  private boolean autoConnectorsAreAllTouchingLinkLayers = true;

  /**
   * Used only when autoConnectorsAreAllTouchingLinkLayers is false. Case-insensitive substrings.
   */
  private List<String> autoConnectorNameHints =
      new ArrayList<String>(Arrays.asList("con", "connector", "conl"));

  /** If true, ambiguous automatic point-layer detection stops the script. */
  private boolean failOnAmbiguousPointLayer = true;

  /** If true, missing explicitly listed connector layers stop the script. */
  private boolean requireConnectors = false;

  /**
   * Attribute conflict policy. Accepted values are:
   *
   * <ul>
   *   <li>{@code skip}: do not merge and log the differing attributes;
   *   <li>{@code prompt}: ask the user at each conflict;
   *   <li>{@code keep-left}: merge and keep non-key attributes from the first line;
   *   <li>{@code keep-right}: merge and keep non-key attributes from the second line.
   * </ul>
   */
  private String onConflict = "skip";

  /**
   * Policy deciding which original {@code NUM} value remains on the merged line. Accepted values
   * are {@code left}, {@code right}, {@code min-num}, and {@code max-num}.
   */
  private String retainId = "left";

  /**
   * If true, evaluate candidates and write conflict CSV files, but do not modify geometries,
   * records, or project files. This is the safest first mode to use on a project.
   */
  private boolean dryRun = false;

  /** Create .bak backups of the original modified layer files before saving. */
  private boolean createBackups = true;

  /** Add a timestamp before .bak to avoid overwriting older backups. */
  private boolean timestampBackups = true;

  /** Save modified ESRI layers and project properties at the end. */
  private boolean saveProject = true;

  /** Open the Nodus console before printing progress. */
  private boolean openConsole = true;

  /** Ask a final confirmation before applying non-dry-run changes. */
  private boolean askBeforeApplying = true;

  // ------------------------------------------------------------------
  // Internal state
  // ------------------------------------------------------------------

  private final Set<String> keyNames = new HashSet<String>(Arrays.asList("num", "node1", "node2"));

  private final NodusMapPanel nodusMapPanel;
  private final NodusProject nodusProject;

  private List<ConflictRow> conflictLog = new ArrayList<ConflictRow>();
  private Set<String> skippedKeys = new LinkedHashSet<String>();
  private Set<Long> deletedNodes = new LinkedHashSet<Long>();
  private int merges = 0;
  private int autoMerges = 0;
  private int conflictMerges = 0;

  private final Set<String> modifiedLayerTables = new LinkedHashSet<String>();
  private final Map<String, NodusEsriLayer> modifiedLayersByTable =
      new LinkedHashMap<String, NodusEsriLayer>();

  /**
   * Creates and runs the simplifier.
   *
   * @param nodusMapPanel map panel supplied by the Nodus Groovy scripting environment; it gives
   *     access to the currently open project.
   */
  public SimplifyNetworks(NodusMapPanel nodusMapPanel) {
    this.nodusMapPanel = nodusMapPanel;
    this.nodusProject = nodusMapPanel == null ? null : nodusMapPanel.getNodusProject();

    try {
      run();
    } catch (Throwable ex) {
      ex.printStackTrace();
      JOptionPane.showMessageDialog(
          nodusMapPanel,
          "Network simplification failed:\n\n" + ex.getMessage(),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Executes the full workflow: validate settings, resolve jobs, optionally ask for confirmation,
   * run all simplifications, save modified layers, and show a summary.
   */
  private void run() throws Exception {
    if (openConsole) {
      try {
        new NodusConsole();
      } catch (Throwable ignored) {
        // Console opening is helpful but not essential.
      }
    }

    if (nodusProject == null || !nodusProject.isOpen()) {
      JOptionPane.showMessageDialog(
          nodusMapPanel,
          "No Nodus project is currently open.",
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    validateSettings();

    List<JobSpec> effectiveJobs = buildEffectiveJobs();

    System.out.println("Network simplification for Nodus");
    System.out.println("  scan all link layers: " + scanAllLinkLayers);
    System.out.println("  jobs: " + jobNames(effectiveJobs));
    System.out.println("  dry run: " + dryRun);

    List<ResolvedJob> resolvedJobs = new ArrayList<ResolvedJob>();
    for (JobSpec job : effectiveJobs) {
      try {
        resolvedJobs.add(resolveJob(job));
      } catch (RuntimeException ex) {
        if (scanAllLinkLayers && skipUnresolvableScannedLayers) {
          System.out.println("Skipping " + job.lineLayerName + ": " + ex.getMessage());
        } else {
          throw ex;
        }
      }
    }

    if (resolvedJobs.isEmpty()) {
      throw new IllegalStateException("No line layer could be resolved for simplification.");
    }

    printResolvedJobs(resolvedJobs);

    if (!dryRun && askBeforeApplying) {
      StringBuilder msg = new StringBuilder();
      msg.append("This will simplify the following loaded Nodus link layers in memory ");
      msg.append("and save them in the current project directory:\n\n");
      for (ResolvedJob job : resolvedJobs) {
        msg.append("• ").append(layerLabel(job.lineLayer));
        msg.append("  using node layers ").append(layerLabels(job.pointLayers));
        msg.append("  and blockers ").append(layerLabels(job.connectorLayers));
        msg.append("\n");
      }
      msg.append("\nOriginal modified layer files will be backed up before saving. Continue?");

      int answer =
          JOptionPane.showConfirmDialog(
              nodusMapPanel, msg.toString(), NodusC.APPNAME, JOptionPane.YES_NO_OPTION);
      if (answer != JOptionPane.YES_OPTION) {
        System.out.println("Canceled by user.");
        return;
      }
    }

    List<JobStats> summaries = new ArrayList<JobStats>();
    for (ResolvedJob job : resolvedJobs) {
      summaries.add(simplifyJob(job));
    }

    if (!dryRun && saveProject) {
      Path projectDir = projectDirectory();
      if (createBackups) {
        for (String tableName : modifiedLayerTables) {
          backupLayerFiles(projectDir, tableName);
        }
      }

      // Modified rows/geometries are already in the Nodus/OpenMap layer objects.
      // saveEsriLayers() writes dirty SHP/SHX/DBF files to the project directory.
      nodusProject.saveEsriLayers();
      try {
        nodusProject.saveProperties();
      } catch (Throwable ignored) {
        // Some Nodus versions may not expose this or may not need it.
      }

      for (NodusEsriLayer layer : modifiedLayersByTable.values()) {
        refreshLayer(layer);
      }
    }

    String summary = buildGlobalSummary(summaries);
    System.out.println(summary);

    String message = summary;
    if (!dryRun) {
      message = message + "\nThe project layers have been saved in the current project directory. " + "Please reload the project before running assignments or other analyses.";
    } else {
      message = message + "\nDry run only: no layer was modified.";
    }
    JOptionPane.showMessageDialog(
        nodusMapPanel, message, NodusC.APPNAME, JOptionPane.INFORMATION_MESSAGE);
  }

  /**
   * Prints the resolved line/point/connector layers for each job to the Nodus console. This is
   * especially useful when layer detection is automatic.
   */
  private void printResolvedJobs(List<ResolvedJob> resolvedJobs) {
    System.out.println("Resolved simplification jobs:");
    for (ResolvedJob job : resolvedJobs) {
      System.out.println("  line layer: " + layerLabel(job.lineLayer));
      System.out.println(
          "    point layers: " + layerLabels(job.pointLayers) + (job.pointLayersDetected ? " (detected)" : " (explicit)"));
      System.out.println(
          "    connector layers: " + layerLabels(job.connectorLayers) + (job.connectorLayersDetected ? " (detected)" : " (explicit)"));
      if (!job.missingConnectorLayers.isEmpty()) {
        System.out.println("    missing connector layers: " + job.missingConnectorLayers);
      }
    }
  }

  /**
   * Simplifies one target line layer.
   *
   * <p>The method works in repeated topology passes. In each pass, it first computes all current
   * candidate nodes, then selects a batch of candidates that do not share any target-line row and
   * can therefore be merged independently. The selected merge operations are applied as one batch,
   * and topology is recomputed only once for the next pass.
   *
   * <p>This is significantly faster than recomputing incidence after every single merge, while
   * preserving safety: adjacent candidates sharing a line are never merged in the same pass.
   *
   * @param job fully resolved line layer, point layers and connector layers
   * @return processing statistics for this line layer
   */
  private JobStats simplifyJob(ResolvedJob job) throws Exception {
    resetCounters();

    NodusEsriLayer lineLayer = job.lineLayer;
    List<NodusEsriLayer> pointLayers = job.pointLayers;
    List<NodusEsriLayer> connectorLayers = job.connectorLayers;

    String lineName = lineLayer.getTableName();
    System.out.println("\nSimplifying layer: " + layerLabel(lineLayer));

    int inputLines = lineLayer.getModel().getRowCount();
    int inputPoints = totalRowCount(pointLayers);
    int pass = 0;

    while (true) {
      pass++;
      List<Candidate> pairs = candidatePairs(lineLayer, connectorLayers, pointLayers);
      List<MergeOperation> operations = selectMergeOperationsForPass(lineLayer, pointLayers, pairs, lineName);

      if (dryRun || operations.isEmpty()) {
        break;
      }

      applyMergeOperationsForPass(lineLayer, pointLayers, operations);
      System.out.println("  pass " + pass + ": applied " + operations.size() + " merge(s)");
    }

    int outputLines = lineLayer.getModel().getRowCount();
    int outputPoints = totalRowCount(pointLayers);
    int remainingCandidates = candidatePairs(lineLayer, connectorLayers, pointLayers).size();

    Path conflictCsv = projectDirectory().resolve(lineName + "_merge_conflicts.csv");
    writeConflictCsv(conflictCsv);

    JobStats stats = new JobStats();
    stats.lineLayer = lineName;
    stats.pointLayers = layerTableNames(pointLayers);
    stats.connectorLayers = layerTableNames(connectorLayers);
    stats.inputLines = inputLines;
    stats.inputPoints = inputPoints;
    stats.outputLines = outputLines;
    stats.outputPoints = outputPoints;
    stats.merges = merges;
    stats.autoMerges = autoMerges;
    stats.conflictMerges = conflictMerges;
    stats.deletedNodes = deletedNodes.size();
    stats.remainingCandidates = remainingCandidates;
    stats.conflictRowsWritten = conflictLog.size();
    stats.conflictCsv = conflictCsv.toString();

    System.out.println(buildJobSummary(stats));
    return stats;
  }

  /**
   * Selects a batch of merge operations that can be applied during the same topology pass.
   *
   * <p>A candidate is selected only if neither of its two target-line rows has already been selected
   * by another candidate in the same pass. This prevents adjacent line segments from being merged
   * simultaneously with stale geometry or row indices.
   */
  private List<MergeOperation> selectMergeOperationsForPass(
      NodusEsriLayer lineLayer,
      List<NodusEsriLayer> pointLayers,
      List<Candidate> pairs,
      String lineName) {

    List<MergeOperation> operations = new ArrayList<MergeOperation>();
    Set<Integer> usedLineRows = new LinkedHashSet<Integer>();
    Set<Long> usedMiddleNodes = new LinkedHashSet<Long>();

    DbfTableModel lineModel = lineLayer.getModel();
    int numCol = colIndex(lineModel, "num");
    List<Integer> zCols = zColumns(lineModel);

    for (Candidate candidate : pairs) {
      int i = candidate.leftIndex;
      int j = candidate.rightIndex;
      long middleNode = candidate.middleNode;

      if (i >= lineModel.getRowCount() || j >= lineModel.getRowCount()) {
        continue;
      }
      if (usedLineRows.contains(Integer.valueOf(i)) || usedLineRows.contains(Integer.valueOf(j))) {
        continue;
      }
      if (usedMiddleNodes.contains(Long.valueOf(middleNode))) {
        continue;
      }

      List<Object> recordI = copyRecord(lineModel.getRecord(i));
      List<Object> recordJ = copyRecord(lineModel.getRecord(j));
      Object numI = recordI.get(numCol);
      Object numJ = recordJ.get(numCol);
      String skipKey = skipKey(middleNode, numI, numJ);
      if (skippedKeys.contains(skipKey)) {
        continue;
      }

      List<Diff> diffs = diffZAttributes(lineModel, recordI, recordJ, zCols);
      if (dryRun) {
        if (!diffs.isEmpty()) {
          addDiffs(middleNode, numI, numJ, diffs);
        }
        continue;
      }

      String attrChoice = chooseConflictAction(numI, numJ, middleNode, diffs, lineName);
      if (attrChoice == null) {
        skippedKeys.add(skipKey);
        addDiffs(middleNode, numI, numJ, diffs);
        continue;
      }

      CombinedLine combined;
      try {
        combined = combineLines(lineLayer, i, j, middleNode);
      } catch (Throwable ex) {
        skippedKeys.add(skipKey);
        conflictLog.add(new ConflictRow(middleNode, numI, numJ, "__geometry_error__", "", ex.getMessage()));
        continue;
      }

      MergeOperation operation = buildMergeOperation(i, j, middleNode, recordI, recordJ, numI, numJ, attrChoice, combined, diffs, zCols, lineModel);
      operations.add(operation);
      usedLineRows.add(Integer.valueOf(i));
      usedLineRows.add(Integer.valueOf(j));
      usedMiddleNodes.add(Long.valueOf(middleNode));
    }

    sortMergeOperationsForPass(operations);
    return operations;
  }

  /** Builds the immutable data needed to apply one merge later in the current pass. */
  private MergeOperation buildMergeOperation(
      int i,
      int j,
      long middleNode,
      List<Object> recordI,
      List<Object> recordJ,
      Object numI,
      Object numJ,
      String attrChoice,
      CombinedLine combined,
      List<Diff> diffs,
      List<Integer> zCols,
      DbfTableModel lineModel) {

    int numCol = colIndex(lineModel, "num");
    int keepIdx;
    int dropIdx;

    if ("left".equals(retainId)) {
      keepIdx = i;
      dropIdx = j;
    } else if ("right".equals(retainId)) {
      keepIdx = j;
      dropIdx = i;
    } else if ("min-num".equals(retainId)) {
      if (nodeKey(numI) <= nodeKey(numJ)) {
        keepIdx = i;
        dropIdx = j;
      } else {
        keepIdx = j;
        dropIdx = i;
      }
    } else if ("max-num".equals(retainId)) {
      if (nodeKey(numI) >= nodeKey(numJ)) {
        keepIdx = i;
        dropIdx = j;
      } else {
        keepIdx = j;
        dropIdx = i;
      }
    } else {
      throw new IllegalStateException("Unexpected retainId: " + retainId);
    }

    List<Object> keepRecord = keepIdx == i ? recordI : recordJ;
    List<Object> attrSource = "left".equals(attrChoice) ? recordI : recordJ;
    Object keptNum = keepRecord.get(numCol);

    MergeOperation operation = new MergeOperation();
    operation.originalKeepIdx = keepIdx;
    operation.originalDropIdx = dropIdx;
    operation.middleNode = middleNode;
    operation.combined = combined;
    operation.attrSource = attrSource;
    operation.keptNum = keptNum;
    operation.zCols = new ArrayList<Integer>(zCols);
    operation.hasAttributeConflict = !diffs.isEmpty();
    return operation;
  }

  /**
   * Applies a selected batch of independent merge operations.
   *
   * <p>Operations store row indices from the start of the pass. After each deletion, later row
   * indices shift. The helper {@link #currentIndexAfterDeleted(int, List)} maps the original pass
   * index to the current layer index before each operation is applied.
   */
  private void applyMergeOperationsForPass(
      NodusEsriLayer lineLayer, List<NodusEsriLayer> pointLayers, List<MergeOperation> operations) {

    List<Integer> deletedOriginalIndices = new ArrayList<Integer>();

    for (MergeOperation operation : operations) {
      int keepIdx = currentIndexAfterDeleted(operation.originalKeepIdx, deletedOriginalIndices);
      int dropIdx = currentIndexAfterDeleted(operation.originalDropIdx, deletedOriginalIndices);

      applyMerge(lineLayer, pointLayers, keepIdx, dropIdx, operation.middleNode, operation.combined, operation.attrSource, operation.keptNum, operation.zCols);

      deletedOriginalIndices.add(Integer.valueOf(operation.originalDropIdx));
      merges++;
      if (operation.hasAttributeConflict) {
        conflictMerges++;
      } else {
        autoMerges++;
      }
      deletedNodes.add(Long.valueOf(operation.middleNode));
    }
  }

  /** Converts an index from the beginning of the pass to the current index after prior deletions. */
  private int currentIndexAfterDeleted(int originalIndex, List<Integer> deletedOriginalIndices) {
    int shift = 0;
    for (Integer deleted : deletedOriginalIndices) {
      if (deleted.intValue() < originalIndex) {
        shift++;
      }
    }
    return originalIndex - shift;
  }

  /**
   * Sorts operations in a stable descending order of their largest affected row index.
   *
   * <p>The index remapping logic makes the algorithm correct regardless of order, but descending
   * order usually minimizes index shifts and is easier to reason about when reading the console log.
   */
  private void sortMergeOperationsForPass(List<MergeOperation> operations) {
    for (int i = 1; i < operations.size(); i++) {
      MergeOperation key = operations.get(i);
      int j = i - 1;
      while (j >= 0 && mergeOperationSortKey(operations.get(j)) < mergeOperationSortKey(key)) {
        operations.set(j + 1, operations.get(j));
        j--;
      }
      operations.set(j + 1, key);
    }
  }

  private int mergeOperationSortKey(MergeOperation operation) {
    return Math.max(operation.originalKeepIdx, operation.originalDropIdx);
  }

  /** Resets per-job counters and logs. Called before each target line layer is processed. */
  private void resetCounters() {
    conflictLog = new ArrayList<ConflictRow>();
    skippedKeys = new LinkedHashSet<String>();
    deletedNodes = new LinkedHashSet<Long>();
    merges = 0;
    autoMerges = 0;
    conflictMerges = 0;
  }

  /**
   * Builds the effective list of simplification jobs.
   *
   * <p>When {@link #scanAllLinkLayers} is enabled, this method inspects the loaded Nodus link
   * layers and creates one {@link JobSpec} per candidate layer. The point and connector layers are
   * not assigned here; they are still resolved by {@link #resolveJob(JobSpec)} so that the same
   * detection logic is used for explicit and automatic jobs.
   *
   * <p>When {@link #scanAllLinkLayers} is disabled, the user-provided {@link #jobsToRun} list is
   * returned unchanged.
   *
   * @return effective simplification jobs to resolve and run.
   */
  private List<JobSpec> buildEffectiveJobs() {
    if (!scanAllLinkLayers) {
      return jobsToRun;
    }

    List<JobSpec> jobs = new ArrayList<JobSpec>();
    for (NodusEsriLayer layer : allLinkLayers()) {
      if (layer == null) {
        continue;
      }
      if (scanOnlyVisibleLinkLayers && !layer.isVisible()) {
        continue;
      }
      if (isExcludedFromFullScan(layer)) {
        System.out.println("Excluding from full scan: " + layerLabel(layer));
        continue;
      }
      if (!hasColumns(layer, Arrays.asList("num", "node1", "node2"))) {
        System.out.println("Skipping line layer without NUM/NODE1/NODE2: " + layerLabel(layer));
        continue;
      }
      jobs.add(JobSpec.line(layer.getTableName()));
    }
    return jobs;
  }

  /**
   * Returns true when a loaded link layer should not be used as a target layer in full-scan mode.
   */
  private boolean isExcludedFromFullScan(NodusEsriLayer layer) {
    String tableName = layer.getTableName() == null ? "" : layer.getTableName();
    String displayName = layer.getName() == null ? "" : layer.getName();
    String tableLower = tableName.toLowerCase(Locale.ROOT);
    String displayLower = displayName.toLowerCase(Locale.ROOT);

    for (String excluded : scanExcludedLayerNames) {
      if (excluded == null) {
        continue;
      }
      String value = excluded.trim().toLowerCase(Locale.ROOT);
      if (value.length() == 0) {
        continue;
      }
      if (tableLower.equals(value) || displayLower.equals(value)) {
        return true;
      }
    }

    for (String hint : scanExcludedNameHints) {
      if (hint == null) {
        continue;
      }
      String value = hint.trim().toLowerCase(Locale.ROOT);
      if (value.length() == 0) {
        continue;
      }
      if (tableLower.contains(value) || displayLower.contains(value)) {
        return true;
      }
    }
    return false;
  }

  // ------------------------------------------------------------------
  // Job resolution and automatic layer detection
  // ------------------------------------------------------------------

  /**
   * Converts a user-facing {@link JobSpec} into concrete loaded Nodus layers.
   *
   * <p>This method validates required fields, auto-detects point layers and connectors when
   * requested, and records whether those layers were explicit or detected.
   */
  private ResolvedJob resolveJob(JobSpec spec) {
    String lineLayerName = spec.lineLayerName;
    NodusEsriLayer lineLayer = requireLayer(lineLayerName);
    if (!isLinkLayer(lineLayer)) {
      throw new IllegalArgumentException("Layer is not a link/polyline layer: " + lineLayerName);
    }
    requireColumns(lineLayer, Arrays.asList("num", "node1", "node2"));

    boolean pointDetected = false;
    List<NodusEsriLayer> pointLayers;
    if (spec.pointLayerNames != null && !spec.pointLayerNames.isEmpty()) {
      pointLayers = new ArrayList<NodusEsriLayer>();
      for (String pointLayerName : spec.pointLayerNames) {
        if (pointLayerName != null && pointLayerName.trim().length() > 0) {
          pointLayers.add(requireLayer(pointLayerName));
        }
      }
    } else if (autoDetectPointLayer) {
      pointLayers = detectPointLayers(lineLayer);
      pointDetected = true;
    } else {
      throw new IllegalArgumentException(
          "No pointLayer(s) provided for " + lineLayerName + " and autoDetectPointLayer is false.");
    }
    pointLayers = uniqueLayers(pointLayers);
    if (pointLayers.isEmpty()) {
      throw new IllegalArgumentException("No point layer resolved for " + lineLayerName + ".");
    }
    for (NodusEsriLayer pointLayer : pointLayers) {
      if (!isNodeLayer(pointLayer)) {
        throw new IllegalArgumentException(
            "Layer is not a node/point layer: " + layerLabel(pointLayer));
      }
      requireColumns(pointLayer, Arrays.asList("num", "tranship"));
    }

    boolean connectorsDetected = false;
    List<NodusEsriLayer> connectorLayers = new ArrayList<NodusEsriLayer>();
    List<String> missingConnectorLayers = new ArrayList<String>();

    if (spec.connectorLayerNames != null) {
      for (String name : spec.connectorLayerNames) {
        NodusEsriLayer layer = nodusProject.getLayer(name);
        if (layer == null) {
          missingConnectorLayers.add(name);
        } else if (!sameLayer(layer, lineLayer)) {
          connectorLayers.add(layer);
        }
      }
      connectorLayers = uniqueLayers(connectorLayers);
      if (requireConnectors && !missingConnectorLayers.isEmpty()) {
        throw new IllegalStateException(
            "Missing required connector layers for " + lineLayerName + ": " + missingConnectorLayers);
      }
    } else if (autoDetectConnectorLayers) {
      connectorLayers = detectConnectorLayers(lineLayer);
      connectorsDetected = true;
    }

    for (NodusEsriLayer connector : connectorLayers) {
      if (!isLinkLayer(connector)) {
        throw new IllegalArgumentException(
            "Connector layer is not a link/polyline layer: " + layerLabel(connector));
      }
      requireColumns(connector, Arrays.asList("node1", "node2"));
    }

    ResolvedJob job = new ResolvedJob();
    job.lineLayer = lineLayer;
    job.pointLayers = pointLayers;
    job.connectorLayers = connectorLayers;
    job.pointLayersDetected = pointDetected;
    job.connectorLayersDetected = connectorsDetected;
    job.missingConnectorLayers = missingConnectorLayers;
    return job;
  }

  /**
   * Finds the point layer or set of point layers referenced by a target line layer.
   *
   * <p>The algorithm collects all endpoint IDs from {@code NODE1}/{@code NODE2} and checks loaded
   * node layers whose {@code NUM} values cover those IDs. When several point layers are allowed, a
   * greedy cover is used to cover all endpoint IDs.
   */
  private List<NodusEsriLayer> detectPointLayers(NodusEsriLayer lineLayer) {
    Set<Long> endpoints = endpointNodeIds(lineLayer);
    if (endpoints.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot auto-detect point layers: " + layerLabel(lineLayer) + " has no endpoints.");
    }

    List<PointLayerCandidate> candidates = new ArrayList<PointLayerCandidate>();
    for (NodusEsriLayer nodeLayer : allNodeLayers()) {
      if (hasColumns(nodeLayer, Arrays.asList("num", "tranship"))) {
        Set<Long> nodeIds = nodeIds(nodeLayer);
        int covered = countCovered(endpoints, nodeIds);
        if (covered > 0) {
          PointLayerCandidate candidate = new PointLayerCandidate();
          candidate.layer = nodeLayer;
          candidate.score = nameAffinityScore(lineLayer, nodeLayer);
          candidate.covered = covered;
          candidates.add(candidate);
        }
      }
    }

    if (candidates.isEmpty()) {
      throw new IllegalStateException(
          "Cannot auto-detect point layers referenced by " + layerLabel(lineLayer) + ". No node layer contains any NODE1/NODE2 value.");
    }

    sortPointLayerCandidates(candidates);

    if (!allowMultiplePointLayers) {
      List<PointLayerCandidate> singleCover = new ArrayList<PointLayerCandidate>();
      for (PointLayerCandidate candidate : candidates) {
        if (candidate.covered == endpoints.size()) {
          singleCover.add(candidate);
        }
      }
      if (singleCover.isEmpty()) {
        throw new IllegalStateException(
            "No single point layer contains all NODE1/NODE2 values for " + layerLabel(lineLayer) + ". Enable allowMultiplePointLayers or specify several point layers explicitly.");
      }
      sortPointLayerCandidates(singleCover);
      if (singleCover.size() > 1 && failOnAmbiguousPointLayer) {
        int bestScore = singleCover.get(0).score;
        List<String> tied = new ArrayList<String>();
        for (PointLayerCandidate candidate : singleCover) {
          if (candidate.score == bestScore) {
            tied.add(layerLabel(candidate.layer));
          }
        }
        if (tied.size() > 1) {
          throw new IllegalStateException(
              "Ambiguous point-layer auto-detection for " + layerLabel(lineLayer) + ". Candidates: " + tied + ". Add pointLayer(s) explicitly in jobsToRun.");
        }
      }
      return new ArrayList<NodusEsriLayer>(Arrays.asList(singleCover.get(0).layer));
    }

    List<NodusEsriLayer> selected = choosePointLayerCover(lineLayer, endpoints, candidates);
    Set<Long> coveredNodes = nodeIds(selected);
    int covered = countCovered(endpoints, coveredNodes);
    if (covered != endpoints.size()) {
      List<String> coverage = new ArrayList<String>();
      for (PointLayerCandidate candidate : candidates) {
        coverage.add(
            layerLabel(candidate.layer) + ": " + candidate.covered + "/" + endpoints.size());
      }
      throw new IllegalStateException(
          "Cannot auto-detect enough point layers for " + layerLabel(lineLayer) + ". Covered " + covered + "/" + endpoints.size() + " endpoint nodes. Coverage: " + coverage);
    }
    return selected;
  }

  /**
   * Greedily chooses point layers that together cover all endpoint node IDs.
   *
   * <p>At each step the layer covering the largest number of still-uncovered nodes is selected.
   * Name affinity is used only as a tie-breaker.
   */
  private List<NodusEsriLayer> choosePointLayerCover(
      NodusEsriLayer lineLayer, Set<Long> endpoints, List<PointLayerCandidate> candidates) {

    List<NodusEsriLayer> selected = new ArrayList<NodusEsriLayer>();
    Set<Long> remaining = new LinkedHashSet<Long>(endpoints);

    while (!remaining.isEmpty()) {
      PointLayerCandidate best = null;
      int bestAdditionalCoverage = 0;
      for (PointLayerCandidate candidate : candidates) {
        if (containsLayer(selected, candidate.layer)) {
          continue;
        }
        Set<Long> ids = nodeIds(candidate.layer);
        int additional = countCovered(remaining, ids);
        if (additional > bestAdditionalCoverage) {
          best = candidate;
          bestAdditionalCoverage = additional;
        } else if (additional == bestAdditionalCoverage && additional > 0 && best != null) {
          int cmp = comparePointLayerCandidates(candidate, best);
          if (cmp < 0) {
            best = candidate;
          }
        }
      }

      if (best == null || bestAdditionalCoverage == 0) {
        break;
      }

      selected.add(best.layer);
      Set<Long> bestIds = nodeIds(best.layer);
      Set<Long> stillRemaining = new LinkedHashSet<Long>();
      for (Long node : remaining) {
        if (!bestIds.contains(node)) {
          stillRemaining.add(node);
        }
      }
      remaining = stillRemaining;
    }

    return selected;
  }

  /**
   * Sorts point-layer candidates in descending preference order.
   *
   * <p>An insertion sort is used deliberately to keep the code Java-11-style and avoid Groovy
   * closures/comparators in this script.
   */
  private void sortPointLayerCandidates(List<PointLayerCandidate> candidates) {
    // Small lists: insertion sort avoids relying on Groovy closures/comparators.
    for (int i = 1; i < candidates.size(); i++) {
      PointLayerCandidate key = candidates.get(i);
      int j = i - 1;
      while (j >= 0 && comparePointLayerCandidates(candidates.get(j), key) > 0) {
        candidates.set(j + 1, candidates.get(j));
        j--;
      }
      candidates.set(j + 1, key);
    }
  }

  /** Compares point-layer candidates by name affinity score, then by layer name. */
  private int comparePointLayerCandidates(PointLayerCandidate a, PointLayerCandidate b) {
    if (a.score != b.score) {
      return b.score - a.score;
    }
    return layerLabel(a.layer).compareToIgnoreCase(layerLabel(b.layer));
  }

  /**
   * Auto-detects connector/blocking line layers.
   *
   * <p>When {@code autoConnectorsAreAllTouchingLinkLayers} is true, every other link layer touching
   * at least one endpoint node of the target layer is used as a blocker. Otherwise, only layers
   * with connector-like names are accepted.
   */
  private List<NodusEsriLayer> detectConnectorLayers(NodusEsriLayer lineLayer) {
    Set<Long> targetEndpointNodes = endpointNodeIds(lineLayer);
    List<NodusEsriLayer> connectors = new ArrayList<NodusEsriLayer>();

    for (NodusEsriLayer candidate : allLinkLayers()) {
      if (!sameLayer(candidate, lineLayer)
          && hasColumns(candidate, Arrays.asList("node1", "node2"))
          && (autoConnectorsAreAllTouchingLinkLayers || isConnectorLikeName(candidate))
          && layerTouchesAnyNode(candidate, targetEndpointNodes)) {
        connectors.add(candidate);
      }
    }

    return uniqueLayers(connectors);
  }

  /** Returns true when a line layer has at least one endpoint in the supplied node-id set. */
  private boolean layerTouchesAnyNode(NodusEsriLayer layer, Set<Long> nodes) {
    DbfTableModel model = layer.getModel();
    int node1Col = colIndex(model, "node1");
    int node2Col = colIndex(model, "node2");
    for (int r = 0; r < model.getRowCount(); r++) {
      if (nodes.contains(Long.valueOf(nodeKey(model.getValueAt(r, node1Col))))) {
        return true;
      }
      if (nodes.contains(Long.valueOf(nodeKey(model.getValueAt(r, node2Col))))) {
        return true;
      }
    }
    return false;
  }

  private boolean isConnectorLikeName(NodusEsriLayer layer) {
    String name = layerLabel(layer).toLowerCase(Locale.ROOT);
    for (String hint : autoConnectorNameHints) {
      if (name.contains(hint.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Computes a heuristic score indicating how likely a node layer belongs to a target line layer.
   * Exact conventional matches such as {@code iww_polylines} / {@code iww_points} receive high
   * scores.
   */
  private int nameAffinityScore(NodusEsriLayer lineLayer, NodusEsriLayer nodeLayer) {
    String line = lineLayer.getTableName().toLowerCase(Locale.ROOT);
    String node = nodeLayer.getTableName().toLowerCase(Locale.ROOT);
    String base = stripSuffixIgnoreCase(line, "_polylines");
    base = stripSuffixIgnoreCase(base, "_polyline");
    base = stripSuffixIgnoreCase(base, "_links");
    base = stripSuffixIgnoreCase(base, "_link");

    if (node.equals(base + "_points")) {
      return 100;
    }
    if (node.equals(base + "_nodes")) {
      return 95;
    }
    if (node.startsWith(base + "_")) {
      return 80;
    }
    if (node.contains(base)) {
      return 60;
    }
    return commonPrefixLength(line, node);
  }

  private String stripSuffixIgnoreCase(String value, String suffix) {
    if (value.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
      return value.substring(0, value.length() - suffix.length());
    }
    return value;
  }

  private int commonPrefixLength(String a, String b) {
    int n = Math.min(a.length(), b.length());
    int i = 0;
    while (i < n && a.charAt(i) == b.charAt(i)) {
      i++;
    }
    return i;
  }

  /** Removes duplicate layer references while preserving the first occurrence. */
  private List<NodusEsriLayer> uniqueLayers(List<NodusEsriLayer> layers) {
    Map<String, NodusEsriLayer> byTable = new LinkedHashMap<String, NodusEsriLayer>();
    for (NodusEsriLayer layer : layers) {
      byTable.put(layer.getTableName().toLowerCase(Locale.ROOT), layer);
    }
    return new ArrayList<NodusEsriLayer>(byTable.values());
  }

  private boolean sameLayer(NodusEsriLayer a, NodusEsriLayer b) {
    if (a == null || b == null) {
      return false;
    }
    return a == b || a.getTableName().equalsIgnoreCase(b.getTableName());
  }

  private List<NodusEsriLayer> allLinkLayers() {
    NodusEsriLayer[] layers = nodusProject.getLinkLayers();
    List<NodusEsriLayer> result = new ArrayList<NodusEsriLayer>();
    if (layers != null) {
      for (NodusEsriLayer layer : layers) {
        result.add(layer);
      }
    }
    return result;
  }

  private List<NodusEsriLayer> allNodeLayers() {
    NodusEsriLayer[] layers = nodusProject.getNodeLayers();
    List<NodusEsriLayer> result = new ArrayList<NodusEsriLayer>();
    if (layers != null) {
      for (NodusEsriLayer layer : layers) {
        result.add(layer);
      }
    }
    return result;
  }

  private boolean isLinkLayer(NodusEsriLayer layer) {
    for (NodusEsriLayer candidate : allLinkLayers()) {
      if (sameLayer(candidate, layer)) {
        return true;
      }
    }
    return false;
  }

  private boolean isNodeLayer(NodusEsriLayer layer) {
    for (NodusEsriLayer candidate : allNodeLayers()) {
      if (sameLayer(candidate, layer)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a loaded Nodus layer by name, or throws a clear error if the layer is absent from the
   * current project.
   */
  private NodusEsriLayer requireLayer(String name) {
    NodusEsriLayer layer = nodusProject.getLayer(name);
    if (layer == null) {
      throw new IllegalStateException("Layer not found in the open project: " + name);
    }
    return layer;
  }

  private String layerLabel(NodusEsriLayer layer) {
    if (layer == null) {
      return "<null>";
    }
    try {
      return layer.getTableName();
    } catch (Throwable ignored) {
      return layer.getName();
    }
  }

  private List<String> layerLabels(List<NodusEsriLayer> layers) {
    List<String> names = new ArrayList<String>();
    for (NodusEsriLayer layer : layers) {
      names.add(layerLabel(layer));
    }
    return names;
  }

  private List<String> layerTableNames(List<NodusEsriLayer> layers) {
    List<String> names = new ArrayList<String>();
    for (NodusEsriLayer layer : layers) {
      names.add(layer.getTableName());
    }
    return names;
  }

  private void requireColumns(NodusEsriLayer layer, List<String> names) {
    for (String name : names) {
      colIndex(layer.getModel(), name);
    }
  }

  private boolean hasColumns(NodusEsriLayer layer, List<String> names) {
    try {
      requireColumns(layer, names);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  /**
   * Collects all distinct node IDs referenced by {@code NODE1} and {@code NODE2} in a line layer.
   */
  private Set<Long> endpointNodeIds(NodusEsriLayer lineLayer) {
    Set<Long> ids = new LinkedHashSet<Long>();
    DbfTableModel model = lineLayer.getModel();
    int node1Col = colIndex(model, "node1");
    int node2Col = colIndex(model, "node2");
    for (int r = 0; r < model.getRowCount(); r++) {
      ids.add(Long.valueOf(nodeKey(model.getValueAt(r, node1Col))));
      ids.add(Long.valueOf(nodeKey(model.getValueAt(r, node2Col))));
    }
    return ids;
  }

  /** Collects all distinct {@code NUM} identifiers from one point layer. */
  private Set<Long> nodeIds(NodusEsriLayer pointLayer) {
    Set<Long> ids = new LinkedHashSet<Long>();
    DbfTableModel model = pointLayer.getModel();
    int numCol = colIndex(model, "num");
    for (int r = 0; r < model.getRowCount(); r++) {
      ids.add(Long.valueOf(nodeKey(model.getValueAt(r, numCol))));
    }
    return ids;
  }

  /** Collects all distinct {@code NUM} identifiers from several point layers. */
  private Set<Long> nodeIds(List<NodusEsriLayer> pointLayers) {
    Set<Long> ids = new LinkedHashSet<Long>();
    for (NodusEsriLayer pointLayer : pointLayers) {
      ids.addAll(nodeIds(pointLayer));
    }
    return ids;
  }

  /**
   * Builds a lookup table from node ID to {@code Tranship} value using all relevant point layers.
   * If the same node appears more than once, the first encountered value is kept.
   */
  private Map<Long, Object> transhipByNode(List<NodusEsriLayer> pointLayers) {
    Map<Long, Object> transhipByNode = new LinkedHashMap<Long, Object>();
    for (NodusEsriLayer pointLayer : pointLayers) {
      DbfTableModel pointModel = pointLayer.getModel();
      int pointNumCol = colIndex(pointModel, "num");
      int transhipCol = colIndex(pointModel, "tranship");
      for (int r = 0; r < pointModel.getRowCount(); r++) {
        Long node = Long.valueOf(nodeKey(pointModel.getValueAt(r, pointNumCol)));
        if (!transhipByNode.containsKey(node)) {
          transhipByNode.put(node, pointModel.getValueAt(r, transhipCol));
        }
      }
    }
    return transhipByNode;
  }

  private boolean containsLayer(List<NodusEsriLayer> layers, NodusEsriLayer searched) {
    for (NodusEsriLayer layer : layers) {
      if (sameLayer(layer, searched)) {
        return true;
      }
    }
    return false;
  }

  private int totalRowCount(List<NodusEsriLayer> layers) {
    int total = 0;
    for (NodusEsriLayer layer : layers) {
      total += layer.getModel().getRowCount();
    }
    return total;
  }

  /**
   * Finds the point layer that currently contains the node to delete after a successful merge. This
   * is required when several point layers are relevant to one line layer.
   */
  private NodusEsriLayer findPointLayerContainingNode(
      List<NodusEsriLayer> pointLayers, long nodeId) {
    for (NodusEsriLayer pointLayer : pointLayers) {
      if (findRowByNum(pointLayer, nodeId) >= 0) {
        return pointLayer;
      }
    }
    return null;
  }

  private int countCovered(Set<Long> required, Set<Long> available) {
    int count = 0;
    for (Long value : required) {
      if (available.contains(value)) {
        count++;
      }
    }
    return count;
  }

  // ------------------------------------------------------------------
  // Simplification core
  // ------------------------------------------------------------------

  /**
   * Finds all candidate merge pairs for the current topology.
   *
   * <p>A node is a candidate only when it is present in the relevant point-layer lookup, has {@code
   * Tranship == 0}, is touched by exactly two arcs across the target line layer plus connector
   * layers, and both arcs belong to the target line layer.
   */
  private List<Candidate> candidatePairs(
      NodusEsriLayer lines,
      List<NodusEsriLayer> connectorLayers,
      List<NodusEsriLayer> pointLayers) {

    Map<Long, Object> transhipByNode = transhipByNode(pointLayers);

    Map<Long, List<Touch>> incidence = buildIncidence(lines, connectorLayers);
    List<Candidate> pairs = new ArrayList<Candidate>();
    for (Map.Entry<Long, List<Touch>> entry : incidence.entrySet()) {
      Long node = entry.getKey();
      Object tranship = transhipByNode.get(node);
      List<Touch> touching = entry.getValue();
      if (tranship != null
          && isZero(tranship)
          && touching.size() == 2
          && "A".equals(touching.get(0).kind)
          && "A".equals(touching.get(1).kind)) {
        int i = touching.get(0).index;
        int j = touching.get(1).index;
        if (i != j) {
          pairs.add(new Candidate(node.longValue(), i, j));
        }
      }
    }
    return pairs;
  }

  /**
   * Builds endpoint incidence for the target line layer and all connector layers. The target layer
   * is tagged as {@code A}; connectors are tagged as {@code C}.
   */
  private Map<Long, List<Touch>> buildIncidence(
      NodusEsriLayer lines, List<NodusEsriLayer> connectorLayers) {

    Map<Long, List<Touch>> incidence = new LinkedHashMap<Long, List<Touch>>();
    addLayerIncidence(incidence, lines, "A");
    for (NodusEsriLayer layer : connectorLayers) {
      addLayerIncidence(incidence, layer, "C");
    }
    return incidence;
  }

  /** Adds all {@code NODE1}/{@code NODE2} endpoint touches of one layer to the incidence map. */
  private void addLayerIncidence(
      Map<Long, List<Touch>> incidence, NodusEsriLayer layer, String kind) {
    DbfTableModel model = layer.getModel();
    int node1Col = colIndex(model, "node1");
    int node2Col = colIndex(model, "node2");
    for (int r = 0; r < model.getRowCount(); r++) {
      addTouch(incidence, nodeKey(model.getValueAt(r, node1Col)), new Touch(kind, r));
      addTouch(incidence, nodeKey(model.getValueAt(r, node2Col)), new Touch(kind, r));
    }
  }

  private void addTouch(Map<Long, List<Touch>> incidence, long node, Touch touch) {
    Long key = Long.valueOf(node);
    List<Touch> list = incidence.get(key);
    if (list == null) {
      list = new ArrayList<Touch>();
      incidence.put(key, list);
    }
    list.add(touch);
  }

  /**
   * Returns the non-key DBF columns whose values must be compared before a merge. The key columns
   * are {@code NUM}, {@code NODE1}, and {@code NODE2}.
   */
  private List<Integer> zColumns(DbfTableModel model) {
    List<Integer> cols = new ArrayList<Integer>();
    for (int c = 0; c < model.getColumnCount(); c++) {
      String name = model.getColumnName(c);
      if (!keyNames.contains(name.toLowerCase(Locale.ROOT))) {
        cols.add(Integer.valueOf(c));
      }
    }
    return cols;
  }

  /**
   * Compares all non-key attributes of two line records and returns the list of differing fields.
   */
  private List<Diff> diffZAttributes(
      DbfTableModel model, List<Object> recordA, List<Object> recordB, List<Integer> zCols) {

    List<Diff> diffs = new ArrayList<Diff>();
    for (Integer colObject : zCols) {
      int col = colObject.intValue();
      Object a = recordA.get(col);
      Object b = recordB.get(col);
      if (!sameValue(a, b)) {
        diffs.add(new Diff(model.getColumnName(col), a, b));
      }
    }
    return diffs;
  }

  /**
   * Applies the configured conflict policy and returns which record supplies non-key attributes for
   * the merged line: {@code "left"}, {@code "right"}, or {@code null} to skip the merge.
   */
  private String chooseConflictAction(
      Object numA, Object numB, long middleNode, List<Diff> diffs, String lineLayerName) {

    if (diffs.isEmpty()) {
      return "left";
    }
    if ("skip".equals(onConflict)) {
      return null;
    }
    if ("keep-left".equals(onConflict)) {
      return "left";
    }
    if ("keep-right".equals(onConflict)) {
      return "right";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Attribute conflict in layer ")
        .append(lineLayerName)
        .append(" at node ")
        .append(middleNode)
        .append("\n");
    sb.append("Line 1 NUM = ").append(numA).append("\n");
    sb.append("Line 2 NUM = ").append(numB).append("\n\n");
    for (Diff diff : diffs) {
      sb.append(diff.attribute)
          .append(": ")
          .append(diff.leftValue)
          .append(" <> ")
          .append(diff.rightValue)
          .append("\n");
    }
    sb.append("\nMerge these two lines?");

    int answer =
        JOptionPane.showConfirmDialog(
            nodusMapPanel, sb.toString(), NodusC.APPNAME, JOptionPane.YES_NO_OPTION);
    if (answer != JOptionPane.YES_OPTION) {
      return null;
    }

    Object[] choices = new Object[] {"line 1", "line 2"};
    Object selected =
        JOptionPane.showInputDialog(
            nodusMapPanel,
            "Keep attributes from:",
            NodusC.APPNAME,
            JOptionPane.QUESTION_MESSAGE,
            null,
            choices,
            choices[0]);
    return "line 2".equals(selected) ? "right" : "left";
  }

  private void addDiffs(long middleNode, Object numI, Object numJ, List<Diff> diffs) {
    for (Diff diff : diffs) {
      conflictLog.add(
          new ConflictRow(middleNode, numI, numJ, diff.attribute, diff.leftValue, diff.rightValue));
    }
  }

  /**
   * Concatenates the two polylines around a removable middle node.
   *
   * <p>The method uses {@code NODE1}/{@code NODE2} to orient each geometry so that the output path
   * is outer-node A -> middle node -> outer-node B. The duplicated middle coordinate is removed.
   */
  private CombinedLine combineLines(NodusEsriLayer lineLayer, int i, int j, long middleNode) {
    DbfTableModel model = lineLayer.getModel();
    int node1Col = colIndex(model, "node1");
    int node2Col = colIndex(model, "node2");

    long aN1 = nodeKey(model.getValueAt(i, node1Col));
    long aN2 = nodeKey(model.getValueAt(i, node2Col));
    long bN1 = nodeKey(model.getValueAt(j, node1Col));
    long bN2 = nodeKey(model.getValueAt(j, node2Col));

    long aOuter = (aN1 == middleNode) ? aN2 : aN1;
    long bOuter = (bN1 == middleNode) ? bN2 : bN1;

    EsriPolyline lineA = (EsriPolyline) lineLayer.getEsriGraphicList().getOMGraphicAt(i);
    EsriPolyline lineB = (EsriPolyline) lineLayer.getEsriGraphicList().getOMGraphicAt(j);

    double[] coordsA = orientCoordsByNodes(lineA, aN1, aN2, aOuter, middleNode);
    double[] coordsB = orientCoordsByNodes(lineB, bN1, bN2, middleNode, bOuter);

    double[] merged = new double[coordsA.length + coordsB.length - 2];
    int p = 0;
    for (int k = 0; k < coordsA.length - 2; k++) {
      merged[p++] = coordsA[k];
    }
    for (int k = 0; k < coordsB.length; k++) {
      merged[p++] = coordsB[k];
    }

    EsriPolyline newPolyline =
        new EsriPolyline(
            merged, OMGraphicConstants.DECIMAL_DEGREES, OMGraphicConstants.LINETYPE_STRAIGHT);
    return new CombinedLine(newPolyline, aOuter, bOuter);
  }

  /**
   * Returns polyline coordinates oriented from {@code fromNode} to {@code toNode}. If the DBF node
   * order is opposite, coordinate pairs are reversed.
   */
  private double[] orientCoordsByNodes(
      EsriPolyline polyline, long node1, long node2, long fromNode, long toNode) {

    double[] coords = latLonDegrees(polyline);
    if (node1 == fromNode && node2 == toNode) {
      return coords;
    }
    if (node1 == toNode && node2 == fromNode) {
      return reversePairs(coords);
    }
    throw new IllegalArgumentException(
        "Line does not connect expected nodes " + fromNode + " -> " + toNode + "; row has " + node1 + " -> " + node2);
  }

  /**
   * Returns a copy of an OpenMap polyline coordinate array in decimal degrees. OpenMap stores
   * {@code EsriPolyline} coordinates in radians internally.
   */
  private double[] latLonDegrees(EsriPolyline polyline) {
    double[] pts = polyline.getLatLonArrayCopy();
    double[] out = new double[pts.length];
    for (int i = 0; i < pts.length; i++) {
      out[i] = ProjMath.radToDeg(pts[i]);
    }
    return out;
  }

  private double[] reversePairs(double[] pts) {
    double[] out = new double[pts.length];
    int p = 0;
    for (int i = pts.length - 2; i >= 0; i -= 2) {
      out[p++] = pts[i];
      out[p++] = pts[i + 1];
    }
    return out;
  }

  /**
   * Applies a completed merge to the loaded Nodus layers.
   *
   * <p>This removes the dropped line, replaces the kept line geometry, updates {@code NUM}/{@code
   * NODE1}/{@code NODE2}, copies selected non-key attributes, deletes the intermediate point from
   * the correct point layer, marks modified layers dirty, and records them for backup/save.
   */
  private void applyMerge(
      NodusEsriLayer lineLayer,
      List<NodusEsriLayer> pointLayers,
      int keepIdx,
      int dropIdx,
      long middleNode,
      CombinedLine combined,
      List<Object> attrSource,
      Object keptNum,
      List<Integer> zCols) {

    DbfTableModel lineModel = lineLayer.getModel();
    int node1Col = colIndex(lineModel, "node1");
    int node2Col = colIndex(lineModel, "node2");
    int numCol = colIndex(lineModel, "num");

    // Remove the dropped line first; update the retained row at its possibly shifted index.
    lineLayer.removeRecord(dropIdx);
    int keepIdx2 = dropIdx < keepIdx ? keepIdx - 1 : keepIdx;

    EsriPolyline newPolyline = combined.geometry;
    OMGraphic oldGraphic = lineLayer.getEsriGraphicList().getOMGraphicAt(keepIdx2);
    try {
      newPolyline.putAttribute(0, oldGraphic.getAttribute(0));
    } catch (Throwable ignored) {
      // Not essential.
    }

    lineLayer.getEsriGraphicList().setOMGraphicAt(newPolyline, keepIdx2);
    lineLayer.attachStyle(newPolyline, keepIdx2);

    for (Integer colObject : zCols) {
      int col = colObject.intValue();
      lineModel.setValueAt(attrSource.get(col), keepIdx2, col);
    }
    lineModel.setValueAt(keptNum, keepIdx2, numCol);
    lineModel.setValueAt(Double.valueOf((double) combined.node1), keepIdx2, node1Col);
    lineModel.setValueAt(Double.valueOf((double) combined.node2), keepIdx2, node2Col);

    NodusEsriLayer pointLayer = findPointLayerContainingNode(pointLayers, middleNode);
    if (pointLayer != null) {
      int pointIdx = findRowByNum(pointLayer, middleNode);
      if (pointIdx >= 0) {
        pointLayer.removeRecord(pointIdx);
        pointLayer.setDirtyShp(true);
        pointLayer.setDirtyDbf(true);
        markModified(pointLayer);
      }
    }

    lineLayer.setDirtyShp(true);
    lineLayer.setDirtyDbf(true);

    markModified(lineLayer);
  }

  /**
   * Records a layer as modified so it can be backed up, saved, and refreshed at the end of the run.
   */
  private void markModified(NodusEsriLayer layer) {
    String table = layer.getTableName();
    modifiedLayerTables.add(table);
    modifiedLayersByTable.put(table, layer);
  }

  /**
   * Finds the row index whose {@code NUM} field equals the requested value, or returns {@code -1}.
   */
  private int findRowByNum(NodusEsriLayer layer, long num) {
    DbfTableModel model = layer.getModel();
    int numCol = colIndex(model, "num");
    for (int r = 0; r < model.getRowCount(); r++) {
      if (nodeKey(model.getValueAt(r, numCol)) == num) {
        return r;
      }
    }
    return -1;
  }

  // ------------------------------------------------------------------
  // Generic helpers
  // ------------------------------------------------------------------

  /**
   * Case-insensitive DBF column lookup. Throws an explicit error listing available columns when the
   * requested field is missing.
   */
  private int colIndex(DbfTableModel model, String wanted) {
    for (int c = 0; c < model.getColumnCount(); c++) {
      if (model.getColumnName(c).equalsIgnoreCase(wanted)) {
        return c;
      }
    }
    throw new IllegalArgumentException(
        "Column " + wanted + " not found in table. Available columns: " + columnNames(model));
  }

  private List<String> columnNames(DbfTableModel model) {
    List<String> names = new ArrayList<String>();
    for (int c = 0; c < model.getColumnCount(); c++) {
      names.add(model.getColumnName(c));
    }
    return names;
  }

  private List<Object> copyRecord(List<Object> source) {
    return new ArrayList<Object>(source);
  }

  /** Normalizes DBF numeric/string node identifiers to a {@code long}. */
  private long nodeKey(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Node id is null");
    }
    if (value instanceof Number) {
      double d = ((Number) value).doubleValue();
      if (Double.isNaN(d)) {
        throw new IllegalArgumentException("Node id is NaN");
      }
      return Math.round(d);
    }
    return Long.parseLong(value.toString().trim());
  }

  /** Tests whether a DBF value represents numeric zero. Used for {@code Tranship}. */
  private boolean isZero(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Number) {
      return BigDecimal.valueOf(((Number) value).doubleValue()).compareTo(BigDecimal.ZERO) == 0;
    }
    return "0".equals(value.toString().trim());
  }

  /**
   * Compares DBF values, treating numerically equal values as equal even when their Java numeric
   * classes differ.
   */
  private boolean sameValue(Object a, Object b) {
    if (a == null && b == null) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a instanceof Number && b instanceof Number) {
      return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
    }
    return a.equals(b);
  }

  private String skipKey(long middleNode, Object numI, Object numJ) {
    long a = nodeKey(numI);
    long b = nodeKey(numJ);
    return middleNode + ":" + Math.min(a, b) + ":" + Math.max(a, b);
  }

  /**
   * Returns the current Nodus project directory, falling back to the working directory if the
   * property cannot be read.
   */
  private Path projectDirectory() {
    String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
    if (path == null || path.trim().length() == 0) {
      return Paths.get(".");
    }
    return Paths.get(path);
  }

  /** Validates user-editable settings before any layer is modified. */
  private void validateSettings() {
    if (!Arrays.asList("skip", "prompt", "keep-left", "keep-right").contains(onConflict)) {
      throw new IllegalArgumentException("Invalid onConflict: " + onConflict);
    }
    if (!Arrays.asList("left", "right", "min-num", "max-num").contains(retainId)) {
      throw new IllegalArgumentException("Invalid retainId: " + retainId);
    }
    if (autoConnectorNameHints == null) {
      autoConnectorNameHints = new ArrayList<String>();
    }
    if (scanExcludedLayerNames == null) {
      scanExcludedLayerNames = new ArrayList<String>();
    }
    if (scanExcludedNameHints == null) {
      scanExcludedNameHints = new ArrayList<String>();
    }
    if (!scanAllLinkLayers && (jobsToRun == null || jobsToRun.isEmpty())) {
      throw new IllegalArgumentException(
          "jobsToRun must contain at least one job when scanAllLinkLayers is false.");
    }
  }

  /**
   * Writes all attribute conflicts for the current job to a CSV file in the project directory. Dry
   * runs also write this file.
   */
  private void writeConflictCsv(Path csvPath) throws IOException {
    Files.createDirectories(csvPath.getParent());
    BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8);
    try {
      writer.write("middle_node,num_left,num_right,attribute,left_value,right_value");
      writer.newLine();
      for (ConflictRow row : conflictLog) {
        writer.write(csv(Long.valueOf(row.middleNode)));
        writer.write(',');
        writer.write(csv(row.numLeft));
        writer.write(',');
        writer.write(csv(row.numRight));
        writer.write(',');
        writer.write(csv(row.attribute));
        writer.write(',');
        writer.write(csv(row.leftValue));
        writer.write(',');
        writer.write(csv(row.rightValue));
        writer.newLine();
      }
    } finally {
      writer.close();
    }
  }

  private String csv(Object value) {
    String s = value == null ? "" : value.toString();
    if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }

  /**
   * Creates backups of the shapefile sidecars that belong to a modified layer.
   *
   * <p>{@code .shp}, {@code .shx}, and {@code .dbf} are moved out of the way so Nodus can recreate
   * them on save. {@code .prj} and {@code .cpg} are copied but left in place.
   */
  private void backupLayerFiles(Path projectDir, String tableName) throws IOException {
    String stamp =
        timestampBackups ? "." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) : "";
    String[] moveExtensions = new String[] {".shp", ".shx", ".dbf"};
    String[] copyExtensions = new String[] {".prj", ".cpg"};

    for (String ext : moveExtensions) {
      Path src = projectDir.resolve(tableName + ext);
      if (Files.exists(src)) {
        Path dst = projectDir.resolve(tableName + ext + stamp + ".bak");
        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Backup: " + src.getFileName() + " -> " + dst.getFileName());
      }
    }

    // Keep projection/codepage files in place, but back them up if present.
    for (String ext : copyExtensions) {
      Path src = projectDir.resolve(tableName + ext);
      if (Files.exists(src)) {
        Path dst = projectDir.resolve(tableName + ext + stamp + ".bak");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Backup: " + src.getFileName() + " -> " + dst.getFileName());
      }
    }
  }

  /**
   * Refreshes labels, styles, and prepared graphics for a modified layer after saving. Failures are
   * ignored because they should not mask a successful save.
   */
  private void refreshLayer(NodusEsriLayer layer) {
    try {
      layer.reloadLabels();
      layer.attachStyles();
      layer.doPrepare();
    } catch (Throwable ignored) {
      // Refresh is useful but should not mask a successful save.
    }
  }

  /** Builds a human-readable summary for one processed line layer. */
  private String buildJobSummary(JobStats stats) {
    StringBuilder sb = new StringBuilder();
    sb.append("\nSimplification summary for ").append(stats.lineLayer).append("\n");
    sb.append("  point_layers: ").append(stats.pointLayers).append("\n");
    sb.append("  connector_layers: ").append(stats.connectorLayers).append("\n");
    sb.append("  input_lines: ").append(stats.inputLines).append("\n");
    sb.append("  input_points: ").append(stats.inputPoints).append("\n");
    sb.append("  output_lines: ").append(stats.outputLines).append("\n");
    sb.append("  output_points: ").append(stats.outputPoints).append("\n");
    sb.append("  merges: ").append(stats.merges).append("\n");
    sb.append("  auto_merges: ").append(stats.autoMerges).append("\n");
    sb.append("  conflict_merges: ").append(stats.conflictMerges).append("\n");
    sb.append("  deleted_nodes: ").append(stats.deletedNodes).append("\n");
    sb.append("  remaining_candidates: ").append(stats.remainingCandidates).append("\n");
    sb.append("  conflict_rows_written: ").append(stats.conflictRowsWritten).append("\n");
    sb.append("  conflict_csv: ").append(stats.conflictCsv).append("\n");
    return sb.toString();
  }

  /** Builds the final summary shown in the console and in the completion dialog. */
  private String buildGlobalSummary(List<JobStats> summaries) {
    int totalMerges = 0;
    int totalDeleted = 0;
    int totalConflicts = 0;
    StringBuilder details = new StringBuilder();
    for (JobStats stats : summaries) {
      totalMerges += stats.merges;
      totalDeleted += stats.deletedNodes;
      totalConflicts += stats.conflictRowsWritten;
      details.append("  ").append(stats.lineLayer).append(": ");
      details.append(stats.inputLines).append(" -> ").append(stats.outputLines);
      details.append(" lines, merges=").append(stats.merges);
      details.append(", remaining_candidates=").append(stats.remainingCandidates);
      details.append("\n");
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Global simplification summary\n");
    sb.append(details);
    sb.append("\nTotal merges: ").append(totalMerges).append("\n");
    sb.append("Total deleted nodes: ").append(totalDeleted).append("\n");
    sb.append("Total conflict rows written: ").append(totalConflicts).append("\n");
    sb.append("Modified layers: ").append(modifiedLayerTables).append("\n");
    return sb.toString();
  }

  private List<String> jobNames(List<JobSpec> jobs) {
    List<String> names = new ArrayList<String>();
    for (JobSpec job : jobs) {
      names.add(job.lineLayerName);
    }
    return names;
  }

  // ------------------------------------------------------------------
  // Data classes
  // ------------------------------------------------------------------

  /**
   * User-facing configuration for one simplification job.
   *
   * <p>Use factory methods instead of the constructor. A {@code null} point-layer list means
   * "auto-detect point layers". A {@code null} connector-layer list means "auto-detect connectors".
   * An empty connector list means "explicitly use no connectors".
   */
  public static final class JobSpec {
    public final String lineLayerName;
    public final List<String> pointLayerNames;
    public final List<String> connectorLayerNames;

    private JobSpec(
        String lineLayerName, List<String> pointLayerNames, List<String> connectorLayerNames) {
      this.lineLayerName = lineLayerName;
      this.pointLayerNames =
          pointLayerNames == null ? null : new ArrayList<String>(pointLayerNames);
      this.connectorLayerNames =
          connectorLayerNames == null ? null : new ArrayList<String>(connectorLayerNames);
    }

    public static JobSpec line(String lineLayerName) {
      return new JobSpec(lineLayerName, null, null);
    }

    public static JobSpec withPointLayer(String lineLayerName, String pointLayerName) {
      return new JobSpec(lineLayerName, new ArrayList<String>(Arrays.asList(pointLayerName)), null);
    }

    public static JobSpec withPointLayers(String lineLayerName, List<String> pointLayerNames) {
      return new JobSpec(lineLayerName, pointLayerNames, null);
    }

    public static JobSpec withPointAndConnectors(
        String lineLayerName, String pointLayerName, List<String> connectorLayerNames) {
      return new JobSpec(
          lineLayerName, new ArrayList<String>(Arrays.asList(pointLayerName)), connectorLayerNames);
    }

    public static JobSpec withPointLayersAndConnectors(
        String lineLayerName, List<String> pointLayerNames, List<String> connectorLayerNames) {
      return new JobSpec(lineLayerName, pointLayerNames, connectorLayerNames);
    }
  }

  /** Internal form of a job after layer names have been resolved to loaded Nodus layers. */
  private static final class ResolvedJob {
    NodusEsriLayer lineLayer;
    List<NodusEsriLayer> pointLayers = new ArrayList<NodusEsriLayer>();
    List<NodusEsriLayer> connectorLayers = new ArrayList<NodusEsriLayer>();
    boolean pointLayersDetected;
    boolean connectorLayersDetected;
    List<String> missingConnectorLayers = new ArrayList<String>();
  }

  /** Candidate node layer during automatic point-layer detection. */
  private static final class PointLayerCandidate {
    NodusEsriLayer layer;
    int score;
    int covered;
  }

  /**
   * One endpoint incidence entry. {@code kind} is {@code A} for the target line layer or {@code C}
   * for a connector/blocking layer.
   */
  private static final class Touch {
    final String kind;
    final int index;

    Touch(String kind, int index) {
      this.kind = kind;
      this.index = index;
    }
  }

  /**
   * A possible merge operation: two rows of the target line layer meeting at a removable middle
   * node.
   */
  private static final class Candidate {
    final long middleNode;
    final int leftIndex;
    final int rightIndex;

    Candidate(long middleNode, int leftIndex, int rightIndex) {
      this.middleNode = middleNode;
      this.leftIndex = leftIndex;
      this.rightIndex = rightIndex;
    }
  }

  /** One non-key attribute difference between two candidate line records. */
  private static final class Diff {
    final String attribute;
    final Object leftValue;
    final Object rightValue;

    Diff(String attribute, Object leftValue, Object rightValue) {
      this.attribute = attribute;
      this.leftValue = leftValue;
      this.rightValue = rightValue;
    }
  }

  /** Result of a geometric merge: new polyline geometry and updated endpoint IDs. */
  private static final class CombinedLine {
    final EsriPolyline geometry;
    final long node1;
    final long node2;

    CombinedLine(EsriPolyline geometry, long node1, long node2) {
      this.geometry = geometry;
      this.node1 = node1;
      this.node2 = node2;
    }
  }

  /**
   * Fully prepared merge selected for the current topology pass.
   *
   * <p>The row indices are the indices that were valid at the beginning of the pass. They are
   * remapped just before application because previous operations in the same batch may already have
   * deleted rows with lower indices.
   */
  private static final class MergeOperation {
    int originalKeepIdx;
    int originalDropIdx;
    long middleNode;
    CombinedLine combined;
    List<Object> attrSource;
    Object keptNum;
    List<Integer> zCols;
    boolean hasAttributeConflict;
  }

  /** Row written to the conflict CSV. */
  private static final class ConflictRow {
    final long middleNode;
    final Object numLeft;
    final Object numRight;
    final String attribute;
    final Object leftValue;
    final Object rightValue;

    ConflictRow(
        long middleNode,
        Object numLeft,
        Object numRight,
        String attribute,
        Object leftValue,
        Object rightValue) {
      this.middleNode = middleNode;
      this.numLeft = numLeft;
      this.numRight = numRight;
      this.attribute = attribute;
      this.leftValue = leftValue;
      this.rightValue = rightValue;
    }
  }

  /** Summary counters for one processed simplification job. */
  private static final class JobStats {
    String lineLayer;
    List<String> pointLayers = new ArrayList<String>();
    List<String> connectorLayers = new ArrayList<String>();
    int inputLines;
    int inputPoints;
    int outputLines;
    int outputPoints;
    int merges;
    int autoMerges;
    int conflictMerges;
    int deletedNodes;
    int remainingCandidates;
    int conflictRowsWritten;
    String conflictCsv;
  }
}

/*
 * Nodus Groovy script entry point.
 *
 * The variable `nodusMapPanel` is injected by Nodus when the script is run from
 * the application. Remove this line if the class is moved into the Java source
 * tree and called directly from Nodus code.
 */
new SimplifyNetworks(nodusMapPanel);