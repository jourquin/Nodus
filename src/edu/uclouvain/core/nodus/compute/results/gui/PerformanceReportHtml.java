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

package edu.uclouvain.core.nodus.compute.results.gui;

import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators.Choices;
import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators.Indicator;
import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators.Report;
import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators.Volumes;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** Escaped, localized HTML for read-only modal performance results. */
final class PerformanceReportHtml {
  private final BiFunction<String, String, String> labels;
  private final NumberFormat numbers = NumberFormat.getNumberInstance();
  private final StringBuilder html = new StringBuilder();

  private PerformanceReportHtml(BiFunction<String, String, String> labels) {
    this.labels = labels;
    numbers.setMaximumFractionDigits(4);
  }

  static String render(
      Report report, Set<Indicator> selected, BiFunction<String, String, String> labels) {
    PerformanceReportHtml renderer = new PerformanceReportHtml(labels);
    return renderer.render(report, selected);
  }

  private String render(Report report, Set<Indicator> selected) {
    html.append(
        "<html><head><style>body{font-family:sans-serif;margin:12px;}"
            + "table{border-collapse:collapse;margin-bottom:14px;}"
            + "th,td{border:1px solid #b0b0b0;padding:5px;}th{background:#ededed;}"
            + "td{text-align:right;}td:first-child{text-align:left;}</style></head><body>");
    html.append("<h2>").append(text("Title", "Model performance")).append("</h2>");
    html.append("<p>")
        .append(text("PathHeader", "Assignment path-header table:"))
        .append(' ')
        .append(escape(report.pathTable))
        .append("</p><ul>");
    report.referenceTables.forEach(
        (mode, table) ->
            html.append("<li>")
                .append(escape(modeLabel(mode)))
                .append(": ")
                .append(escape(table))
                .append("</li>"));
    html.append("</ul><p>")
        .append(
            text(
                "ComparisonScope",
                "Comparison is limited to the selected modes. Paths, duplicate"
                    + " matrix rows, classes and"
                    + " departure times are summed by mode, commodity group, origin and"
                    + " destination."
                    + " Cells use the complete selected-mode grid over the union of"
                    + " observed and assigned ODs."))
        .append("</p>");
    if (!report.omittedModes.isEmpty()) {
      html.append("<p><b>")
          .append(text("OmittedModes", "Assignment modes excluded from this comparison:"))
          .append("</b> ");
      report.omittedModes.forEach(
          (mode, quantity) ->
              html.append(escape(modeLabel(mode)))
                  .append(" (")
                  .append(value(quantity))
                  .append(") "));
      html.append("</p>");
    }
    if (selected.contains(Indicator.WAPE)
        || selected.contains(Indicator.BIAS)
        || selected.contains(Indicator.RMSE)) {
      html.append("<h3>").append(text("VolumeErrors", "Quantity errors")).append("</h3>");
      List<String> headers =
          new ArrayList<>(
              List.of(
                  text("Scope", "Scope"),
                  text("Cells", "Cells"),
                  text("Observed", "Observed quantity"),
                  text("Assigned", "Assigned quantity")));
      for (Indicator indicator : List.of(Indicator.WAPE, Indicator.BIAS, Indicator.RMSE)) {
        if (selected.contains(indicator)) {
          headers.add(text(indicator.key, indicator.label));
        }
      }
      startTable(headers);
      volumeRow(labels.apply("AllModes", "All selected modes"), report.total, selected);
      report.modes.forEach((mode, volumes) -> volumeRow(modeLabel(mode), volumes, selected));
      report.groups.forEach(
          (group, volumes) -> {
            volumeRow(groupLabel(group), volumes, selected);
            report
                .groupModes
                .get(group)
                .forEach(
                    (mode, modal) ->
                        volumeRow(groupLabel(group) + " / " + modeLabel(mode), modal, selected));
          });
      endTable();
      html.append("<p>")
          .append(
              text(
                  "VolumeNotes",
                  "WAPE and relative bias use total observed quantity as"
                      + " denominator; they are undefined"
                      + " when it is zero. Positive bias means overprediction. RMSE is in"
                      + " quantity units."
                      + " Use the same matrices, modes and evaluation ODs when comparing models."))
          .append("</p>");
    }
    if (selected.contains(Indicator.MODAL_SHARES)) {
      html.append("<h3>").append(text("ModalShares", "Aggregate modal shares")).append("</h3>");
      startTable(
          List.of(
              text("Scope", "Scope"),
              text("Mode", "Mode"),
              text("ObservedShare", "Observed share (%)"),
              text("AssignedShare", "Assigned share (%)"),
              text("ShareDifference", "Difference (percentage points)")));
      shareRows(labels.apply("AllGroups", "All commodity groups"), report.total, report.modes);
      report.groups.forEach(
          (group, volumes) -> shareRows(groupLabel(group), volumes, report.groupModes.get(group)));
      endTable();
    }
    boolean choices =
        selected.contains(Indicator.OD_SHARE_ERROR)
            || selected.contains(Indicator.CROSS_ENTROPY)
            || selected.contains(Indicator.JENSEN_SHANNON);
    if (choices) {
      html.append("<h3>")
          .append(text("ModalDistribution", "OD modal distributions"))
          .append("</h3>");
      List<String> headers =
          new ArrayList<>(List.of(text("Scope", "Scope"), text("EligibleODs", "Eligible ODs")));
      if (selected.contains(Indicator.OD_SHARE_ERROR)) {
        headers.add(text("MedianOD", "Median share error (%)"));
        headers.add(text("P90OD", "P90 share error (%)"));
      }
      if (selected.contains(Indicator.CROSS_ENTROPY)) {
        headers.add(text("CrossEntropy", "Cross-entropy"));
      }
      if (selected.contains(Indicator.JENSEN_SHANNON)) {
        headers.add(text("JS", "Jensen–Shannon divergence"));
      }
      startTable(headers);
      choiceRow(labels.apply("AllGroups", "All commodity groups"), report.choices, selected);
      report.groupChoices.forEach(
          (group, scores) -> choiceRow(groupLabel(group), scores, selected));
      endTable();
      if (selected.contains(Indicator.CROSS_ENTROPY) && report.choices.zeroShareODs > 0) {
        zeroShareDiagnostics(report);
      }
      html.append("<p>")
          .append(
              text(
                  "ShareNotes",
                  "Modal distribution scores require positive observed and assigned OD totals."
                      + " Missing assigned ODs are excluded from these scores and reported"
                      + " separately below."
                      + " OD share error is half the sum of absolute modal-share"
                      + " differences (0 to 100%)."
                      + " Median/P90 give equal weight to each eligible OD/group. Cross-entropy and"
                      + " Jensen–Shannon divergence use observed quantities as weights;"
                      + " lower is better."
                      + " Jensen–Shannon uses base-2 logarithms and ranges from 0 (matching shares)"
                      + " to 1 (disjoint shares); zero shares are valid. Cross-entropy uses natural"
                      + " logarithms, can be infinite for an observed-positive modal cell with zero"
                      + " predicted share, and need not be zero for matching shares."))
          .append("</p>");
      html.append("<p>")
          .append(text("MissingODs", "Observed ODs without assigned flow:"))
          .append(' ')
          .append(report.choices.missingODs)
          .append("; ")
          .append(text("MissingQuantity", "observed quantity:"))
          .append(' ')
          .append(value(report.choices.missingQuantity))
          .append(".</p>");
    }
    if (selected.contains(Indicator.COVERAGE)) {
      html.append("<h3>").append(text("Coverage", "Coverage and zero predictions")).append("</h3>");
      startTable(
          List.of(
              text("Scope", "Scope"),
              text("ObservedODs", "Observed ODs"),
              text("Covered", "Observed quantity on ODs with assigned flow (%)"),
              text("MissingODsShort", "ODs without assigned flow"),
              text("Shortfall", "OD quantity shortfall"),
              text("Excess", "OD quantity excess"),
              text("ExtraODs", "Assigned-only ODs")));
      coverageRow(labels.apply("AllGroups", "All commodity groups"), report.choices);
      report.groupChoices.forEach((group, scores) -> coverageRow(groupLabel(group), scores));
      endTable();
      startTable(
          List.of(
              text("Scope", "Scope"),
              text("ZeroCells", "Observed-positive / assigned-zero cells"),
              text("ZeroQuantity", "Observed quantity in these cells")));
      zeroRow(labels.apply("AllModes", "All selected modes"), report.total);
      report.modes.forEach((mode, volumes) -> zeroRow(modeLabel(mode), volumes));
      report.groups.forEach(
          (group, volumes) -> {
            zeroRow(groupLabel(group), volumes);
            report
                .groupModes
                .get(group)
                .forEach(
                    (mode, modal) -> zeroRow(groupLabel(group) + " / " + modeLabel(mode), modal));
          });
      endTable();
      html.append("<p>")
          .append(
              text(
                  "CoverageNotes",
                  "Coverage compares assignment with the reference matrices, not"
                      + " with its original input"
                      + " demand. It does not establish the assignment's unassigned-demand"
                      + " rate when those"
                      + " totals differ. Shortfall/excess sum positive OD total"
                      + " differences separately."
                      + " Coverage means some assigned flow exists on the OD; partial"
                      + " shortfalls are"
                      + " shown in the shortfall column."))
          .append("</p>");
    }
    html.append("<p>")
        .append(
            text(
                "ValidationNotes",
                "Predictive validation requires ODs or periods not used for"
                    + " parameter estimation or pivot"
                    + " calibration. Tonnage weights are descriptive; tonnes are not"
                    + " independent choices."))
        .append("</p></body></html>");
    return html.toString();
  }

  private void volumeRow(String scope, Volumes volumes, Set<Indicator> selected) {
    List<String> cells =
        new ArrayList<>(
            List.of(
                scope,
                Long.toString(volumes.cells),
                value(volumes.observed),
                value(volumes.assigned)));
    if (selected.contains(Indicator.WAPE)) {
      cells.add(value(volumes.wape()));
    }
    if (selected.contains(Indicator.BIAS)) {
      cells.add(value(volumes.bias()));
    }
    if (selected.contains(Indicator.RMSE)) {
      cells.add(value(volumes.rmse()));
    }
    row(cells);
  }

  private void shareRows(String scope, Volumes total, Map<Integer, Volumes> modes) {
    modes.forEach(
        (mode, volumes) -> {
          double observed =
              total.observed == 0 ? Double.NaN : 100 * (volumes.observed / total.observed);
          double assigned =
              total.assigned == 0 ? Double.NaN : 100 * (volumes.assigned / total.assigned);
          row(
              List.of(
                  scope,
                  modeLabel(mode),
                  value(observed),
                  value(assigned),
                  value(assigned - observed)));
        });
  }

  private void choiceRow(String scope, Choices scores, Set<Indicator> selected) {
    List<String> cells = new ArrayList<>(List.of(scope, Long.toString(scores.eligibleODs)));
    if (selected.contains(Indicator.OD_SHARE_ERROR)) {
      cells.add(value(scores.percentile(0.5)));
      cells.add(value(scores.percentile(0.9)));
    }
    if (selected.contains(Indicator.CROSS_ENTROPY)) {
      cells.add(value(scores.crossEntropy()));
    }
    if (selected.contains(Indicator.JENSEN_SHANNON)) {
      cells.add(value(scores.jensenShannonDivergence()));
    }
    row(cells);
  }

  private void zeroShareDiagnostics(Report report) {
    html.append("<h3>")
        .append(text("InfiniteScores", "Why cross-entropy is infinite"))
        .append("</h3><p>")
        .append(
            text(
                "InfiniteScoreNotes",
                "One observed-positive mode with zero assigned flow makes the whole group's"
                    + " cross-entropy infinite. Eligible ODs have positive total flow on"
                    + " both sides;"
                    + " this does not require every observed mode to have assigned flow. The counts"
                    + " below concern these eligible ODs only. An infinite group score"
                    + " does not mean"
                    + " that every OD is poorly predicted."))
        .append("</p>");
    startTable(
        List.of(
            text("Scope", "Scope"),
            text("ZeroShareODs", "Affected eligible ODs"),
            text("ZeroShareCells", "Affected modal cells"),
            text("ZeroShareQuantity", "Observed quantity in affected cells")));
    zeroShareRow(labels.apply("AllGroups", "All commodity groups"), report.choices);
    report.groupChoices.forEach((group, scores) -> zeroShareRow(groupLabel(group), scores));
    endTable();
    html.append("<p>")
        .append(
            text(
                "ZeroShareExamples",
                "Up to 20 affected modal cells with the largest observed quantities:"))
        .append("</p>");
    startTable(
        List.of(
            text("Group", "Commodity group"),
            text("Mode", "Mode"),
            text("Origin", "Origin"),
            text("Destination", "Destination"),
            text("Observed", "Observed quantity"),
            text("Assigned", "Assigned quantity"),
            text("AssignedODTotal", "Assigned OD total")));
    for (var cell : report.zeroShareExamples()) {
      row(
          List.of(
              Integer.toString(cell.group),
              Integer.toString(cell.mode),
              Long.toString(cell.origin),
              Long.toString(cell.destination),
              value(cell.observed),
              "0",
              value(cell.assignedODTotal)));
    }
    endTable();
    html.append("<p>")
        .append(
            text(
                "StoredZeroNotes",
                "Stored assignment quantities can be zero because of unavailable"
                    + " modes, assignment rules"
                    + " or rounding of small flows. These scores compare saved"
                    + " quantities, not the model's"
                    + " internal probabilities. No smoothing or probability floor is applied."))
        .append("</p>");
  }

  private void zeroShareRow(String scope, Choices scores) {
    row(
        List.of(
            scope,
            Long.toString(scores.zeroShareODs),
            Long.toString(scores.zeroShareCells),
            value(scores.zeroShareQuantity)));
  }

  private void coverageRow(String scope, Choices scores) {
    row(
        List.of(
            scope,
            Long.toString(scores.observedODs),
            value(scores.coveredPercent()),
            Long.toString(scores.missingODs),
            value(scores.shortfall),
            value(scores.excess),
            Long.toString(scores.extraODs)));
  }

  private void zeroRow(String scope, Volumes volumes) {
    row(
        List.of(
            scope, Long.toString(volumes.zeroPredictions), value(volumes.zeroPredictionQuantity)));
  }

  private void startTable(List<String> headers) {
    html.append("<table><tr>");
    for (String header : headers) {
      html.append("<th>").append(header).append("</th>");
    }
    html.append("</tr>");
  }

  private void row(List<String> cells) {
    html.append("<tr>");
    for (String cell : cells) {
      html.append("<td>").append(escape(cell)).append("</td>");
    }
    html.append("</tr>");
  }

  private void endTable() {
    html.append("</table>");
  }

  private String modeLabel(int mode) {
    return labels.apply("Mode", "Mode") + " " + mode;
  }

  private String groupLabel(int group) {
    return labels.apply("Group", "Commodity group") + " " + group;
  }

  private String value(double number) {
    if (Double.isNaN(number)) {
      return labels.apply("Undefined", "Undefined");
    }
    return Double.isInfinite(number) ? "∞" : numbers.format(number);
  }

  private String text(String key, String fallback) {
    return escape(labels.apply(key, fallback));
  }

  static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
