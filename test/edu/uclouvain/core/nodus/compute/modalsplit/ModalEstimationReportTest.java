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
import org.junit.jupiter.api.Test;

class ModalEstimationReportTest {
  @Test
  void formatsFitStatisticsAndEscapesDiagnosticsWithoutTestingFixedParameters() {
    LogCostChoiceEstimate result = new LogCostChoiceEstimate(
        new double[] {2, -1}, new double[] {.5, .25}, 0, -80, -100, 200, 20, 7, false);
    String group = ModalEstimationReport.group(5, "MNL", new int[] {1, 2}, 1, result);
    assertTrue(group.contains("164.00000")); // AIC: 2*2 - 2*(-80).
    assertTrue(group.contains("0.20000000")); // Rho-square.
    assertTrue(group.contains("4.0000000")); // Nonreference intercept t-statistic.
    assertTrue(group.contains("<td>—</td><td>—</td><td>Fixed reference"));
    String html = ModalEstimationReport.document(group, "<script>&\" bad");
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
}
