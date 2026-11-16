/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusC;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.TreeSet;

/** One assignment's distinct unreachable OD pairs and their optional cleanup script. */
final class AssignmentLostPaths {
  private final Set<String> statements = new TreeSet<>();
  private final Path directory;
  private final String filePrefix;
  private final String table;
  private final StringBuilder header = new StringBuilder();

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

  /** Serializes concurrent workers and suppresses repeated reports from later iterations. */
  synchronized void record(int group, int origin, int destination) {
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
      System.out.println(
          "No path: origin=" + origin + ", destination=" + destination + ", group=" + group);
    }
  }

  /** Saves only runs with lost paths, preserving scripts from earlier assignment attempts. */
  synchronized Path save(boolean completed) throws IOException {
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
        writer.write("-- Problematic pairs: " + statements.size() + "\n\n");
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
