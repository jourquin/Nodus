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

package edu.uclouvain.core.nodus.compute.modalsplit;

import edu.uclouvain.core.nodus.NodusC;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Standalone HTML presentation of behavioral estimates and workflow diagnostics. */
final class ModalEstimationReport {
  private ModalEstimationReport() {}

  static String document(String groups, String diagnostics) {
    return document(groups, diagnostics, NodusC.includeEstimationWorkflowDiagnostics);
  }

  static String document(String groups, String diagnostics, boolean includeWorkflowDiagnostics) {
    String workflow = includeWorkflowDiagnostics
        ? "<h2>Workflow diagnostics and saved parameters</h2><pre>" + escape(diagnostics) + "</pre>"
        : "";
    String generatedAt = ZonedDateTime.now().format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX '['VV']'", Locale.ROOT));
    return "<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<title>Nodus modal split estimation</title><style>"
        + "body{font:16px system-ui,sans-serif;max-width:1100px;margin:32px auto;padding:0 20px;"
        + "color:#243044}table{border-collapse:collapse;width:100%;margin:16px 0 28px}"
        + "th,td{border:1px solid #dce2ea;padding:8px;text-align:left}th{background:#edf2f7}"
        + "pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f5f7fa;padding:16px}"
        + "@media print{body{max-width:none;margin:0}tr{break-inside:avoid}}</style></head><body>"
        + "<h1>Nodus modal split estimation</h1>"
        + "<p>Generated at: " + escape(generatedAt) + "</p>"
        + groups + workflow + "<p>Robust errors, bootstrap "
        + "statistics and optimizer gradient diagnostics are not available in this report. "
        + "BIC is omitted because aggregate flow quantities do not define an unambiguous "
        + "independent sample size.</p></body></html>";
  }

  /** Keeps all behavioral group estimates ahead of the separate pivot-calibration layer. */
  static String layers(String behavioralGroups, String pivotGroups) {
    return "<section id='behavioral-layer'><h2>Behavioral layer</h2>"
        + "<p>Model parameters estimated before optional OD/group pivot calibration. Standard "
        + "errors use observed quantities as frequency weights; they are not robust or clustered. "
        + "Changing the quantity unit changes likelihoods and uncertainty. T-statistics are "
        + "asymptotic estimate/SE ratios for a zero coefficient, "
        + "not finite-sample Student tests.</p>"
        + behavioralGroups
        + "</section><section id='pivot-calibration'><h2>Pivot calibration</h2>"
        + (pivotGroups.isEmpty()
            ? "<p>Pivot calibration was not requested.</p>"
            : "<p>Bounded base-year utility corrections fitted after the behavioral model. "
                + "These corrections are separate from the behavioral parameters.</p>"
                + pivotGroups)
        + "</section>";
  }

  static String group(int group, String method, int[] modes, int reference,
      LogCostChoiceEstimate estimate) {
    boolean proportional = estimate instanceof ProportionalEstimator.Result;
    int count = modes.length - 1 + (proportional ? 0 : estimate.isConditional() ? 1 : modes.length);
    final double ll = estimate.getLogLikelihood();
    final double baseline = estimate.getNullLogLikelihood();
    StringBuilder html = new StringBuilder("<h3>Commodity group " + group + " — "
        + escape(method) + "</h3><table><tr><th>Measurement</th><th>Value</th></tr>");
    metric(html, "Estimated behavioral parameters", count);
    metric(html, "Included OD rows", estimate.getObservations());
    metric(html, "Total observed quantity (frequency weight)", estimate.getTotalQuantity());
    metric(html, "Iterations", estimate.getIterations());
    metric(html, "Final log-likelihood", ll);
    metric(
        html,
        proportional ? "Unadjusted proportional log-likelihood" : "Zero-parameter log-likelihood",
        baseline);
    metric(html, "Twice log-likelihood improvement (no p-value)", 2 * (ll - baseline));
    if (baseline != 0) {
      metric(html, "Rho-square against baseline", 1 - ll / baseline);
      metric(html, "Adjusted rho-square against baseline", 1 - (ll - count) / baseline);
    }
    metric(html, "AIC (behavioral model, frequency-weight scale)", 2 * count - 2 * ll);
    html.append("</table><table><tr><th>Parameter</th><th>Estimate</th><th>Standard error</th>"
        + "<th>T-statistic (zero null)</th><th>Status</th></tr>");
    double[] values =
        proportional
            ? ((ProportionalEstimator.Result) estimate).getCostFactors()
            : estimate.getIntercepts();
    double[] errors =
        proportional
            ? ((ProportionalEstimator.Result) estimate).getCostFactorStandardErrors()
            : estimate.getInterceptStandardErrors();
    for (int i = 0; i < modes.length; i++) {
      parameter(html, (proportional ? "Cost factor, mode " : "Intercept, mode ") + modes[i],
          values[i], errors[i], modes[i] == reference ? "Fixed reference" : "Estimated",
          !proportional && modes[i] != reference);
    }
    if (!proportional) {
      values = estimate.getCostCoefficients();
      errors = estimate.getCostStandardErrors();
      for (int i = 0; i < (estimate.isConditional() ? 1 : modes.length); i++) {
        boolean boundary = values[i] == 0;
        parameter(html, estimate.isConditional() ? "Common log-cost coef."
            : "Log-cost coef., mode " + modes[i], values[i], errors[i],
            boundary ? "At zero upper bound; curvature SE only" : "Estimated", !boundary);
      }
    }
    html.append("</table>");
    appendCorrelations(html, modes, reference, estimate);
    return html.toString();
  }

  /** Pivot diagnostics belong to the commodity group immediately preceding this section. */
  static String pivots(ModalPivotEstimator.GroupDiagnostics diagnostics) {
    StringBuilder html = new StringBuilder("<h4>Pivot-constant distribution</h4><p>");
    html.append("Mode–OD–commodity-group cells; implicit zero corrections and unavailable "
        + "alternatives are included in the unweighted distribution. Available cells and stored "
        + "nonzero corrections are counted separately. The reference mode is excluded. "
        + "Weighted values use observed quantities of each mode; N/A means no observed modal "
        + "quantity. Weighted median is the first correction reaching "
        + "50% of cumulative weight.</p>");
    String bound = Double.toString(diagnostics.maxAbs);
    String distributionHeadings = "<th>Median</th><th>% at −" + bound
        + "</th><th>% at +" + bound + "</th>";
    html.append("<table><tr><th rowspan='2'>Mode</th><th rowspan='2'>OD/group cells</th>"
        + "<th rowspan='2'>Available</th><th rowspan='2'>Stored nonzero</th>"
        + "<th colspan='3'>Unweighted</th><th colspan='3'>Weighted by observed modal quantity</th>"
        + "</tr><tr>" + distributionHeadings + distributionHeadings + "</tr>");
    for (var entry : diagnostics.distributions.entrySet()) {
      ModalPivotEstimator.Distribution distribution = entry.getValue();
      html.append("<tr><td>").append(entry.getKey()).append("</td><td>")
          .append(distribution.values.size()).append("</td><td>")
          .append(distribution.available).append("</td><td>").append(distribution.stored)
          .append("</td>");
      for (boolean weighted : new boolean[] {false, true}) {
        html.append("<td>")
            .append(pivotNumber(distribution.median(weighted)))
            .append("</td><td>")
            .append(pivotPercentage(distribution.boundPercentage(false, weighted)))
            .append("</td><td>")
            .append(pivotPercentage(distribution.boundPercentage(true, weighted)))
            .append("</td>");
      }
      html.append("</tr>");
    }
    html.append("</table><h4>Pivot numerical completion</h4><p>Counts refer to OD/group cells. "
        + "A small update can result from clipping at a bound; it does not establish exact "
        + "reproduction of observed quantities.</p>"
        + "<table><tr><th>Measurement</th><th>Value</th></tr>");
    metric(html, "OD/group cells", diagnostics.cells);
    metric(html, "Cells without an adjustable nonreference mode", diagnostics.noAdjustableModes);
    metric(html, "Cells stopped on update tolerance", diagnostics.toleranceStops);
    metric(html, "Cells stopped at iteration limit", diagnostics.iterationLimitStops);
    metric(html, "Iteration limit", ModalPivotEstimator.MAX_ITERATIONS);
    metric(html, "Update tolerance", ModalPivotEstimator.UPDATE_TOLERANCE);
    if (!diagnostics.iterations.isEmpty()) {
      List<Integer> sorted = new ArrayList<>(diagnostics.iterations);
      sorted.sort(Integer::compare);
      int middle = sorted.size() / 2;
      double median = sorted.size() % 2 == 0
          ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0 : sorted.get(middle);
      metric(html, "Iterations per adjustable cell: minimum", sorted.get(0));
      metric(html, "Iterations per adjustable cell: median", median);
      metric(html, "Iterations per adjustable cell: maximum", sorted.get(sorted.size() - 1));
    }
    metric(html, "Maximum final applied update", diagnostics.maxFinalChange);
    metric(
        html, "Maximum remaining absolute raw log-ratio update", diagnostics.maxRemainingLogRatio);
    metric(
        html,
        "OD/group cells with an outward update blocked by a bound",
        diagnostics.boundBlockedCells);
    int boundBlocked = diagnostics.distributions.values().stream()
        .mapToInt(distribution -> distribution.boundBlocked).sum();
    metric(html, "Modal corrections with an outward update blocked by a bound", boundBlocked);
    return html.append("</table><p>Remaining raw updates are recomputed with the final constants "
        + "and the calibration epsilon. Bound-blocked counts require an outward raw log-ratio "
        + "larger than the update tolerance in magnitude.</p>").toString();
  }

  private static String pivotPercentage(double value) {
    return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "N/A";
  }

  private static String pivotNumber(double value) {
    return Double.isFinite(value) ? number(value) : "N/A";
  }

  /** Lists each distinct pair once; fixed reference and fixed cost parameters are omitted. */
  private static void appendCorrelations(
      StringBuilder html, int[] modes, int reference, LogCostChoiceEstimate estimate) {
    boolean proportional = estimate instanceof ProportionalEstimator.Result;
    double[][] covariance = proportional
        ? ((ProportionalEstimator.Result) estimate).getCostFactorCovariance()
        : estimate.getCoefficientCovariance();
    html.append("<h4>Correlation of coefficients</h4><p>")
        .append(
            proportional
                ? "Delta-method covariance of cost factors derived from conventional "
                    + "inverse information"
                : "Conventional inverse-information covariance")
        .append(
            " with quantity frequency weights; not robust or clustered. "
                + "Fixed parameters are omitted. Boundary entries describe curvature only.</p>");
    if (covariance == null) {
      html.append("<p>Full covariance is unavailable for this estimate.</p>");
      return;
    }
    List<String> names = new ArrayList<>();
    List<Integer> indexes = new ArrayList<>();
    List<Double> errors = new ArrayList<>();
    List<Boolean> boundaries = new ArrayList<>();
    double[] interceptErrors = proportional
        ? ((ProportionalEstimator.Result) estimate).getCostFactorStandardErrors()
        : estimate.getInterceptStandardErrors();
    for (int mode = 0; mode < modes.length; mode++) {
      if (modes[mode] != reference) {
        names.add((proportional ? "Cost factor, mode " : "Intercept, mode ") + modes[mode]);
        indexes.add(mode);
        errors.add(interceptErrors[mode]);
        boundaries.add(false);
      }
    }
    if (!proportional) {
      double[] coefficients = estimate.getCostCoefficients();
      double[] coefficientErrors = estimate.getCostStandardErrors();
      for (int mode = 0; mode < (estimate.isConditional() ? 1 : modes.length); mode++) {
        names.add(estimate.isConditional() ? "Common log-cost coef."
            : "Log-cost coef., mode " + modes[mode]);
        indexes.add(modes.length + mode);
        errors.add(coefficientErrors[mode]);
        boundaries.add(coefficients[mode] == 0);
      }
    }
    if (names.size() < 2) {
      html.append("<p>Fewer than two estimated coefficients; no coefficient pairs.</p>");
      return;
    }
    html.append("<table><tr><th>Coefficient 1</th><th>Coefficient 2</th>"
        + "<th>Covariance</th><th>Correlation</th><th>Status</th></tr>");
    for (int first = 0; first < names.size(); first++) {
      for (int second = first + 1; second < names.size(); second++) {
        double value = covariance[indexes.get(first)][indexes.get(second)];
        double correlation = errors.get(first) > 0 && errors.get(second) > 0
            ? value / errors.get(first) / errors.get(second) : Double.NaN;
        // Remove only tiny roundoff outside the mathematical [-1,1] range.
        if (Double.isFinite(correlation) && Math.abs(correlation) <= 1 + 1e-12) {
          correlation = Math.max(-1, Math.min(1, correlation));
        } else {
          correlation = Double.NaN;
        }
        html.append("<tr><td>").append(escape(names.get(first))).append("</td><td>")
            .append(escape(names.get(second))).append("</td><td>").append(number(value))
            .append("</td><td>").append(number(correlation)).append("</td><td>")
            .append(boundaries.get(first) || boundaries.get(second)
                ? "Includes coefficient at zero upper bound; curvature only" : "Estimated")
            .append("</td></tr>");
      }
    }
    html.append("</table>");
  }

  private static void metric(StringBuilder html, String label, double value) {
    html.append("<tr><td>").append(escape(label)).append("</td><td>")
        .append(number(value)).append("</td></tr>");
  }

  private static void parameter(StringBuilder html, String name, double value, double error,
      String status, boolean test) {
    html.append("<tr><td>").append(escape(name)).append("</td><td>").append(number(value))
        .append("</td><td>").append(status.startsWith("Fixed") ? "—" : number(error))
        .append("</td><td>").append(test && error > 0 ? number(value / error) : "—")
        .append("</td><td>").append(escape(status)).append("</td></tr>");
  }

  private static String number(double value) {
    if (!Double.isFinite(value)) {
      return "Unavailable";
    }
    if (value == Math.rint(value) && value >= Long.MIN_VALUE && value < Long.MAX_VALUE) {
      return Long.toString((long) value);
    }
    return String.format(Locale.ROOT, "%.8g", value);
  }

  static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }
}
