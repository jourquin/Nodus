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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators;
import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators.Indicator;
import java.sql.DriverManager;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerformanceReportHtmlTest {
  @Test
  void escapesNamesAndShowsOnlySelectedIndicators() throws Exception {
    try (var connection =
            DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate("CREATE TABLE \"<road>&cargo\" (grp INT,org INT,dst INT,qty DOUBLE)");
      statement.executeUpdate(
          "CREATE TABLE \"paths<&>_header\" (grp INT,org INT,dst INT,ldmode INT,qty DOUBLE)");
      statement.executeUpdate("INSERT INTO \"<road>&cargo\" VALUES (0,1,2,5)");
      statement.executeUpdate("INSERT INTO \"paths<&>_header\" VALUES (0,1,2,1,4)");
      var report =
          PerformanceIndicators.compute(
              connection, Map.of(1, "<road>&cargo"), "paths<&>_header", false, () -> true);
      String html =
          PerformanceReportHtml.render(
              report, EnumSet.of(Indicator.RMSE), (key, fallback) -> fallback);
      assertTrue(html.contains("&lt;road&gt;&amp;cargo"));
      assertTrue(html.contains("paths&lt;&amp;&gt;_header"));
      assertFalse(html.contains("<road>"));
      assertTrue(html.contains("<th>RMSE (quantity units)</th>"));
      assertFalse(html.contains("<th>WAPE (%)</th>"));
      assertFalse(html.contains("<h3>OD modal distributions</h3>"));
      assertFalse(html.contains("<h3>Coverage and zero predictions</h3>"));
      String all =
          PerformanceReportHtml.render(
              report, EnumSet.allOf(Indicator.class), (key, fallback) -> fallback);
      assertTrue(all.contains("<h3>Coverage and zero predictions</h3>"));
      assertTrue(all.contains("<th>Median share error (%)</th>"));
      assertTrue(all.contains("<th>Jensen–Shannon divergence</th>"));
      assertFalse(all.contains("KL divergence"));
    }
  }

  @Test
  void rendersUndefinedScoresAndInfinitePenaltiesExplicitly() throws Exception {
    try (var connection =
            DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate("CREATE TABLE road (grp INT,org INT,dst INT,qty DOUBLE)");
      statement.executeUpdate("CREATE TABLE rail (grp INT,org INT,dst INT,qty DOUBLE)");
      statement.executeUpdate(
          "CREATE TABLE paths_header (grp INT,org INT,dst INT,ldmode INT,qty DOUBLE)");
      statement.executeUpdate("INSERT INTO road VALUES (0,1,2,10)");
      statement.executeUpdate("INSERT INTO rail VALUES (0,1,2,10)");
      statement.executeUpdate("INSERT INTO paths_header VALUES (0,1,2,2,20)");
      var report =
          PerformanceIndicators.compute(
              connection, Map.of(1, "road", 2, "rail"), "paths_header", true, () -> true);
      String html =
          PerformanceReportHtml.render(
              report,
              EnumSet.of(Indicator.CROSS_ENTROPY, Indicator.JENSEN_SHANNON),
              (key, fallback) -> fallback);
      assertTrue(html.contains("∞"));
      assertTrue(html.contains("Why cross-entropy is infinite"));
      assertTrue(html.contains("<th>Affected eligible ODs</th>"));
      assertTrue(html.contains("<th>Assigned OD total</th>"));
      assertFalse(html.contains("<h3>Coverage and zero predictions</h3>"));
      String jsOnly =
          PerformanceReportHtml.render(
              report, EnumSet.of(Indicator.JENSEN_SHANNON), (key, fallback) -> fallback);
      assertFalse(jsOnly.contains("∞"));
      assertFalse(jsOnly.contains("Why cross-entropy is infinite"));
      assertTrue(jsOnly.contains("ranges from 0 (matching shares) to 1 (disjoint shares)"));
      statement.executeUpdate("DELETE FROM paths_header");
      report =
          PerformanceIndicators.compute(
              connection, Map.of(1, "road", 2, "rail"), "paths_header", true, () -> true);
      html =
          PerformanceReportHtml.render(
              report, EnumSet.allOf(Indicator.class), (key, fallback) -> fallback);
      assertTrue(html.contains("Undefined"));
      assertTrue(html.contains("Observed ODs without assigned flow:"));
      assertFalse(html.contains("NaN"));
      assertFalse(html.contains("Why cross-entropy is infinite"));
    }
  }

  @Test
  void savedKlCheckboxMigratesWithoutChangingOtherSelections() {
    assertEquals(
        EnumSet.of(Indicator.JENSEN_SHANNON), PerformanceDlg.savedIndicators("KL_DIVERGENCE"));
    assertEquals(
        EnumSet.of(Indicator.WAPE, Indicator.JENSEN_SHANNON),
        PerformanceDlg.savedIndicators("WAPE,KL_DIVERGENCE"));
    assertEquals(
        EnumSet.of(Indicator.JENSEN_SHANNON), PerformanceDlg.savedIndicators("JENSEN_SHANNON"));
    assertEquals(EnumSet.of(Indicator.WAPE), PerformanceDlg.savedIndicators("WAPE,UNKNOWN"));
    assertEquals(EnumSet.of(Indicator.WAPE), PerformanceDlg.savedIndicators(""));
    assertEquals(EnumSet.of(Indicator.WAPE), PerformanceDlg.savedIndicators(null));
  }
}
