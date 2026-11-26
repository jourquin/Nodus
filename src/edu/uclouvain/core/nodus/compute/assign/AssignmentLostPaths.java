/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.assign;

import com.bbn.openmap.Environment;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** One assignment's distinct unreachable OD pairs and their optional cleanup script. */
final class AssignmentLostPaths {
  private final Set<String> statements = new TreeSet<>();
  private final Set<Long> affectedPairs = new HashSet<>();
  // Each loaded row is a separate cell, even when all its field values are identical.
  private final Set<ODCell> affectedRecords = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<Integer> modes = new TreeSet<>();
  private final Path directory;
  private final String filePrefix;
  private final String table;
  private final StringBuilder header = new StringBuilder();
  private long loadedRecords = -1;
  private double loadedQuantity;
  private boolean hasReportsWithoutQuantity;
  private double unassignedQuantity;

  AssignmentLostPaths(AssignmentParameters parameters, String method) {
    String project = parameters.getNodusProject().getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    directory =
        Paths.get(parameters.getNodusProject().getLocalProperty(NodusC.PROP_PROJECT_DOTPATH));
    filePrefix =
        (project + "_scenario" + parameters.getScenario() + "_" + method + "_lost_paths_")
            .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    table = parameters.getODMatrix();
    comment("Nodus lost paths - SQL cleanup script");
    comment("Assignment started: " + ZonedDateTime.now());
    comment("Project: " + project);
    comment("Scenario: " + parameters.getScenario());
    comment("Description: " + parameters.getScenarioDescription());
    comment("Assignment method: " + method);
    if ("ExactMFAssignment".equals(method)) {
      comment("Exact MF algorithm: " + NodusC.exactMFAlgorithm);
    }
    comment("OD table: " + table);
    comment("OD filter: " + parameters.getWhereStmt());
    comment(
        "Cost functions: "
            + (parameters.getCostFunctionsPath() == null
                ? "(in-memory)"
                : parameters.getCostFunctionsPath()));
    comment("Threads: " + parameters.getThreads());
    comment("Iterations / alternative routes: " + parameters.getNbIterations());
    comment("Precision: " + parameters.getPrecision());
    comment("Modal split method: " + parameters.getModalSplitMethodName());
    comment("Cost markup: " + parameters.getCostMarkup());
    comment("Maximum detour: " + parameters.getMaxDetour());
    comment("Limited to highlighted area: " + parameters.isLimitedToHighlightedArea());
    if (parameters.isTimeDependent() && parameters.getCostFunctions() != null) {
      for (String variable :
          new String[] {
            NodusC.VARNAME_STARTTIME, NodusC.VARNAME_ENDTIME, NodusC.VARNAME_TIMESLICE
          }) {
        comment(variable + ": " + parameters.getCostFunctions().getProperty(variable));
      }
    }
    comment(
        "Each DELETE matches the group, origin and destination, across classes/departure times.");
  }

  private void comment(String text) {
    header.append("-- ").append(text.replaceAll("\\R", "\n-- ")).append('\n');
  }

  synchronized void setLoadedDemand(long records, double quantity) {
    loadedRecords = records;
    loadedQuantity = quantity;
  }

  synchronized void recordModes(int[] modeMeans) {
    for (int combination : modeMeans) {
      modes.add(combination / NodusC.MAXMM);
    }
  }

  synchronized void recordWithoutQuantity(int group, int origin, int destination) {
    hasReportsWithoutQuantity = true;
    recordPair(group, origin, destination);
  }

  private void recordPair(int group, int origin, int destination) {
    String statement =
        "delete from "
            + table
            + " where "
            + NodusC.DBF_GROUP
            + "="
            + group
            + " and "
            + NodusC.DBF_ORIGIN
            + "="
            + origin
            + " and "
            + NodusC.DBF_DESTINATION
            + "="
            + destination
            + ";";
    if (statements.add(statement)) {
      affectedPairs.add(((long) origin << 32) | Integer.toUnsignedLong(destination));
    }
  }

  /** Counts each OD record once, even when several routing passes report it. */
  synchronized void recordUnassignedDemand(ODCell demand) {
    if (affectedRecords.add(demand)) {
      unassignedQuantity += demand.getQuantity();
      recordPair(demand.getGroup(), demand.getOriginNodeId(), demand.getDestinationNodeId());
    }
  }

  /** Describes the same counts and quantities in the console and in SQL comments. */
  synchronized String statistics(boolean completed) {
    if (loadedRecords < 0 && statements.isEmpty()) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    text.append(label("Title", "Lost-path statistics")).append('\n');
    if (!completed) {
      text.append(label("Partial", "Partial results from a failed or cancelled assignment."))
          .append('\n');
    }
    if (!modes.isEmpty()) {
      String ids = modes.stream().map(String::valueOf).collect(Collectors.joining(", "));
      text.append(modes.size() == 1 ? label("Mode", "Mode") : label("Modes", "Modes (combined)"))
          .append(": ")
          .append(ids)
          .append('\n');
    }
    append(text, label("Pairs", "Affected directed OD pairs"), affectedPairs.size());
    append(text, label("Keys", "OD/group cleanup keys"), statements.size());
    NumberFormat quantity = NumberFormat.getNumberInstance();
    quantity.setMinimumFractionDigits(1);
    quantity.setMaximumFractionDigits(6);
    if (loadedRecords >= 0) {
      append(text, label("Loaded_records", "Loaded OD records"), loadedRecords);
      append(
          text,
          label("Loaded_quantity", "Loaded quantity (OD units)"),
          quantity.format(loadedQuantity));
    }
    append(text, label("Unassigned_records", "Unassigned OD records"), affectedRecords.size());
    append(
        text,
        label("Unassigned_quantity", "Unassigned quantity (OD units)"),
        quantity.format(unassignedQuantity));
    if (loadedQuantity > 0 && Double.isFinite(loadedQuantity) && !hasReportsWithoutQuantity) {
      NumberFormat percent = NumberFormat.getPercentInstance();
      percent.setMaximumFractionDigits(4);
      append(
          text,
          label("Share", "Share of loaded quantity"),
          percent.format(unassignedQuantity / loadedQuantity));
    }
    if (!affectedRecords.isEmpty()) {
      text.append(
              label(
                  "Counting",
                  "Each loaded record with a missing path is counted once across routing passes."))
          .append('\n');
    }
    if (hasReportsWithoutQuantity) {
      text.append(label("Incomplete", "Some loss reports lack quantities.")).append('\n');
    }
    return text.toString();
  }

  private static String label(String key, String fallback) {
    return Environment.getI18n().get(AssignmentLostPaths.class, key, fallback);
  }

  private static void append(StringBuilder text, String label, Object value) {
    text.append(label).append(": ").append(value).append('\n');
  }

  /** Saves only runs with lost paths, preserving scripts from earlier assignment attempts. */
  synchronized Path save(boolean completed, String statistics) throws IOException {
    if (statements.isEmpty()) {
      return null;
    }
    Path script = Files.createTempFile(directory, filePrefix, ".sql").toAbsolutePath();
    boolean saved = false;
    try {
      try (BufferedWriter writer = Files.newBufferedWriter(script, StandardCharsets.UTF_8)) {
        writer.write(header.toString());
        writer.write(
            "-- Assignment status: " + (completed ? "completed" : "failed/cancelled") + "\n");
        for (String line : statistics.split("\n")) {
          writer.write("-- ");
          writer.write(line);
          writer.newLine();
        }
        writer.newLine();
        for (String statement : statements) {
          writer.write(statement);
          writer.newLine();
        }
      }
      saved = true;
      return script;
    } finally {
      if (!saved) {
        Files.deleteIfExists(script);
      }
    }
  }
}
