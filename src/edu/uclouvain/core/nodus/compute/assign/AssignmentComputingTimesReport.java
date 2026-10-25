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

package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusC.ExactMFAlgorithm;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Standalone UTF-8 assignment timing tables, styled like the modal-choice estimation report. */
final class AssignmentComputingTimesReport {
  private final StringBuilder html = new StringBuilder();
  private boolean tableOpen;

  AssignmentComputingTimesReport(
      String project,
      String assignment,
      int scenario,
      int threads,
      boolean success,
      ExactMFAlgorithm algorithm) {
    html.append(
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>Nodus assignment computing times</title><style>"
            + "body{font:16px system-ui,sans-serif;max-width:1100px;margin:32px auto;padding:0 20px;"
            + "color:#243044}table{border-collapse:collapse;width:100%;margin:16px 0 28px}"
            + "th,td{border:1px solid #dce2ea;padding:8px;text-align:left}th{background:#edf2f7}"
            + "td:last-child{text-align:right;font-variant-numeric:tabular-nums}"
            + "@media print{body{max-width:none;margin:0}tr{break-inside:avoid}}"
            + "</style></head><body><h1>Nodus assignment computing times</h1>\n");
    paragraph(
        "Generated at: "
            + ZonedDateTime.now()
                .format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX '['VV']'", Locale.ROOT)));
    table("Assignment", "Value");
    row("Project", project);
    row("Assignment method", assignment);
    row("Scenario", Integer.toString(scenario));
    row("Threads", Integer.toString(threads));
    row("Status", success ? "completed" : "failed/cancelled");
    if ("ExactMFAssignment".equals(assignment)) {
      row("Exact MF selector", algorithm.name());
    }
    section("Overall elapsed times", "Measurement", "Elapsed time");
  }

  void section(String title, String label, String value) {
    endTable();
    html.append("<h2>").append(escape(title)).append("</h2>\n");
    table(label, value);
  }

  void time(String label, long nanos) {
    row(label, String.format(Locale.ROOT, "%.3f s", nanos / 1.0e9));
  }

  void count(String label, long count) {
    row(label, Long.toString(count));
  }

  void share(String label, long part, long whole) {
    row(label, whole == 0 ? "n/a" : String.format(Locale.ROOT, "%.2f %%", 100.0 * part / whole));
  }

  void paragraph(String text) {
    endTable();
    html.append("<p>").append(escape(text)).append("</p>\n");
  }

  private void table(String label, String value) {
    html.append("<table><thead><tr><th>")
        .append(escape(label))
        .append("</th><th>")
        .append(escape(value))
        .append("</th></tr></thead><tbody>\n");
    tableOpen = true;
  }

  private void row(String label, String value) {
    html.append("<tr><td>")
        .append(escape(label.strip() + ":"))
        .append("</td><td>")
        .append(escape(value))
        .append("</td></tr>\n");
  }

  private void endTable() {
    if (tableOpen) {
      html.append("</tbody></table>\n");
      tableOpen = false;
    }
  }

  String document() {
    endTable();
    return html.toString() + "</body></html>";
  }

  /** Replaces only this scenario/method/selector's report, retaining other benchmark variants. */
  static Path reportPath(
      Path directory, String project, String assignment, int scenario, ExactMFAlgorithm algorithm) {
    String variant = "ExactMFAssignment".equals(assignment) ? "_" + algorithm.name() : "";
    String name =
        project + "_scenario" + scenario + "_" + assignment + variant + "_computing_times.html";
    return directory.resolve(name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")).toAbsolutePath();
  }

  /** Stages the full document before replacing a previous report, as for the KPI HTML output. */
  static Path save(Path destination, String contents) throws IOException {
    Path temporary =
        Files.createTempFile(destination.getParent(), ".nodus-computing-times-", ".html");
    try {
      Files.writeString(temporary, contents, StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
      return destination;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
