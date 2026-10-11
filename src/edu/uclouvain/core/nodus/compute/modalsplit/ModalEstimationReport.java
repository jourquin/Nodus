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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Standalone HTML presentation of behavioral estimates and workflow diagnostics. */
final class ModalEstimationReport {
  private ModalEstimationReport() {}

  static String document(String groups, String diagnostics) {
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
        + "<p>Behavioral estimates before optional OD/group pivot calibration. Standard errors "
        + "use observed quantities as frequency weights; they are not robust or clustered. "
        + "Changing the quantity unit changes likelihoods and uncertainty. T-statistics are "
        + "asymptotic estimate/SE ratios for a zero coefficient, not finite-sample Student tests.</p>"
        + groups + "<h2>Workflow diagnostics and saved parameters</h2><pre>"
        + escape(diagnostics) + "</pre><p>Robust errors, coefficient correlations, bootstrap "
        + "statistics and optimizer gradient diagnostics are not available in this report. "
        + "BIC is omitted because aggregate flow quantities do not define an unambiguous "
        + "independent sample size.</p></body></html>";
  }

  static String group(int group, String method, int[] modes, int reference,
      LogCostChoiceEstimate estimate) {
    boolean proportional = estimate instanceof ProportionalEstimator.Result;
    int count = modes.length - 1 + (proportional ? 0 : estimate.isConditional() ? 1 : modes.length);
    double ll = estimate.getLogLikelihood();
    double baseline = estimate.getNullLogLikelihood();
    StringBuilder html = new StringBuilder("<h2>Commodity group " + group + " — "
        + escape(method) + "</h2><table><tr><th>Measurement</th><th>Value</th></tr>");
    metric(html, "Estimated behavioral parameters", count);
    metric(html, "Included OD rows", estimate.getObservations());
    metric(html, "Total observed quantity (frequency weight)", estimate.getTotalQuantity());
    metric(html, "Iterations", estimate.getIterations());
    metric(html, "Final log-likelihood", ll);
    metric(html, proportional ? "Unadjusted proportional log-likelihood" : "Zero-parameter log-likelihood", baseline);
    metric(html, "Twice log-likelihood improvement (no p-value)", 2 * (ll - baseline));
    if (baseline != 0) {
      metric(html, "Rho-square against baseline", 1 - ll / baseline);
      metric(html, "Adjusted rho-square against baseline", 1 - (ll - count) / baseline);
    }
    metric(html, "AIC (behavioral model, frequency-weight scale)", 2 * count - 2 * ll);
    html.append("</table><table><tr><th>Parameter</th><th>Estimate</th><th>Standard error</th>"
        + "<th>T-statistic (zero null)</th><th>Status</th></tr>");
    double[] values = proportional ? ((ProportionalEstimator.Result) estimate).getCostFactors()
        : estimate.getIntercepts();
    double[] errors = proportional ? ((ProportionalEstimator.Result) estimate).getCostFactorStandardErrors()
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
        parameter(html, estimate.isConditional() ? "Common log-cost coefficient"
            : "Log-cost coefficient, mode " + modes[i], values[i], errors[i],
            boundary ? "At zero upper bound; curvature SE only" : "Estimated", !boundary);
      }
    }
    return html.append("</table>").toString();
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
    return Double.isFinite(value) ? String.format(Locale.ROOT, "%.8g", value) : "Unavailable";
  }

  static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }
}
