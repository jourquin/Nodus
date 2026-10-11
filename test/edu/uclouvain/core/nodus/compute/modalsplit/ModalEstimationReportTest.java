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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import edu.uclouvain.core.nodus.NodusC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

class ModalEstimationReportTest {
  @Test
  void formatsFitStatisticsAndEscapesDiagnosticsWithoutTestingFixedParameters() {
    LogCostChoiceEstimate result = new LogCostChoiceEstimate(
        new double[] {2, -1}, new double[] {.5, .25}, 0, -80, -100, 200, 20, 7, false);
    String group = ModalEstimationReport.group(5, "MNL", new int[] {1, 2}, 1, result);
    assertTrue(group.contains("<td>164</td>")); // AIC: 2*2 - 2*(-80).
    assertTrue(group.contains("0.20000000")); // Rho-square.
    assertTrue(group.contains("<td>4</td>")); // Nonreference intercept t-statistic.
    assertTrue(group.contains("<td>—</td><td>—</td><td>Fixed reference"));
    String html = ModalEstimationReport.document(group, "<script>&\" bad", true);
    assertTrue(html.contains("&lt;script&gt;&amp;&quot;"));
    assertFalse(html.contains("<script>"));
  }
  @Test
  void boundaryCoefficientDoesNotReceiveAnOrdinaryTStatistic() {
    LogCostChoiceEstimate result = new LogCostChoiceEstimate(
        new double[] {2, 0}, new double[] {.5, .25}, 0, -80, -100, 200, 20, 7, false);
    String html = ModalEstimationReport.group(5, "MNL", new int[] {1, 2}, 1, result);
    assertTrue(html.contains("<td>—</td><td>At zero upper bound; curvature SE only"));
  }
  @Test
  void reportsEachEstimatedPairOnceWithConventionalCovariance() {
    LogCostChoiceEstimate result = LogCostLogitEstimator.estimate(
        new double[][] {{1, 1}, {1, 2}}, new double[][] {{2, 3}, {8, 3}});
    String html = ModalEstimationReport.group(5, "MNL", new int[] {1, 2}, 1, result);
    String correlations = html.substring(html.indexOf("<h4>Correlation of coefficients"));
    assertTrue(correlations.contains("<th>Covariance</th><th>Correlation</th>"));
    assertTrue(correlations.contains("<td>-1.2022459</td><td>-0.80321933</td>"));
    assertFalse(correlations.contains("<td>Intercept, mode 1</td>"));
    assertFalse(correlations.contains("t-test"));
    assertFalse(ModalEstimationReport.document(html, "").contains("coefficient correlations, bootstrap"));
  }

  @Test
  void flagsBoundaryPairsAndReportsProportionalFactors() {
    LogCostChoiceEstimate boundary = LogCostLogitEstimator.estimate(
        new double[][] {{1, 1}, {1, 2}}, new double[][] {{1, 1}, {1, 2}});
    String html = ModalEstimationReport.group(1, "MNL", new int[] {1, 2}, 1, boundary);
    assertTrue(html.contains("Includes coefficient at zero upper bound; curvature only"));
    ProportionalEstimator.Result factors = ProportionalEstimator.estimate(
        new double[][] {{10, 10, 10}}, new double[][] {{40, 20, 10}});
    html = ModalEstimationReport.group(1, "Proportional", new int[] {1, 2, 3}, 1, factors);
    String correlations = html.substring(html.indexOf("<h4>Correlation of coefficients"));
    assertTrue(correlations.contains("<td>Cost factor, mode 2</td><td>Cost factor, mode 3</td>"));
    assertTrue(correlations.contains("<td>0.20000000</td><td>0.25819889</td>"));
    assertFalse(correlations.contains("Common log-cost coef."));
  }
  @Test
  @ResourceLock("NodusC.includeEstimationWorkflowDiagnostics")
  void workflowSectionIsDisabledByDefaultAndCanBeEnabled() {
    String diagnostics = "private saved parameters";
    String html = ModalEstimationReport.document("<h2>Group</h2>", diagnostics);
    assertFalse(html.contains("Workflow diagnostics and saved parameters"));
    assertFalse(html.contains(diagnostics));
    assertTrue(html.contains("<h2>Group</h2>"));
    boolean previous = NodusC.includeEstimationWorkflowDiagnostics;
    try {
      NodusC.includeEstimationWorkflowDiagnostics = true;
      html = ModalEstimationReport.document("<h2>Group</h2>", diagnostics);
      assertTrue(html.contains("Workflow diagnostics and saved parameters"));
      assertTrue(html.contains(diagnostics));
    } finally {
      NodusC.includeEstimationWorkflowDiagnostics = previous;
    }
  }
}
