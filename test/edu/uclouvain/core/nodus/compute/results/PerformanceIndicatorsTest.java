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

package edu.uclouvain.core.nodus.compute.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PerformanceIndicatorsTest {
  @Test
  void matchesKnownQuantityAndShareErrorsOnBothDatabases() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      try (Connection connection = fixture(hsql)) {
        PerformanceIndicators.Report report = compute(connection);
        assertEquals(6, report.total.cells);
        assertEquals(300, report.total.observed);
        assertEquals(300, report.total.assigned);
        assertEquals(60, report.total.absoluteError);
        assertEquals(20, report.total.wape(), 1e-12);
        assertEquals(0, report.total.bias());
        assertEquals(Math.sqrt(1000.0 / 6), report.total.rmse(), 1e-12);
        assertEquals(110, report.modes.get(1).observed);
        assertEquals(120, report.modes.get(1).assigned);
        assertEquals(100 * 10.0 / 110, report.modes.get(1).bias(), 1e-12);
        assertEquals(30, report.groupModes.get(1).get(1).observed);
        assertEquals(30, report.groups.get(0).wape(), 1e-12);
        assertEquals(10, report.choices.percentile(0.5), 1e-12);
        assertEquals(18, report.choices.percentile(0.9), 1e-12);
        assertEquals(15, report.groupChoices.get(0).percentile(0.5), 1e-12);
        double entropy =
            -(Math.log(0.5)
                    + 0.2 * Math.log(0.4)
                    + 0.8 * Math.log(0.6)
                    + 0.3 * Math.log(0.3)
                    + 0.7 * Math.log(0.7))
                / 3;
        // Independently evaluated base-2 Jensen–Shannon divergences for the three ODs.
        double divergence = (0.007299156760473963 + 0.03485155455967723) / 3;
        assertEquals(entropy, report.choices.crossEntropy(), 1e-12);
        assertEquals(divergence, report.choices.jensenShannonDivergence(), 1e-12);
        assertEquals(0, report.groupChoices.get(1).jensenShannonDivergence(), 1e-12);
        assertEquals(100, report.choices.coveredPercent());
        assertEquals(3, report.choices.eligibleODs);
        assertEquals(0, report.choices.shortfall);
        assertEquals(0, report.choices.excess);
        assertEquals(0, report.total.zeroPredictions);
        assertEquals(8, count(connection, "assignment_header"));
        assertEquals(5, count(connection, "road"));
      }
    }
  }

  @Test
  void retainsMissingAndExtraODsAndReportsUnmappedModes() throws Exception {
    try (Connection connection = fixture(false)) {
      execute(connection, "INSERT INTO road VALUES (3,7,8,30)");
      execute(connection, "INSERT INTO rail VALUES (3,7,8,10)");
      execute(connection, "INSERT INTO assignment_header VALUES (2,9,10,1,40),(4,11,12,9,123)");
      PerformanceIndicators.Report report = compute(connection);
      assertEquals(10, report.total.cells);
      assertEquals(340, report.total.observed);
      assertEquals(340, report.total.assigned);
      assertEquals(140, report.total.absoluteError);
      assertEquals(0, report.total.bias());
      assertEquals(1, report.choices.missingODs);
      assertEquals(1, report.choices.extraODs);
      assertEquals(40, report.choices.missingQuantity);
      assertEquals(40, report.choices.shortfall);
      assertEquals(40, report.choices.excess);
      assertEquals(100 * 300.0 / 340, report.choices.coveredPercent(), 1e-12);
      assertEquals(2, report.total.zeroPredictions);
      assertEquals(40, report.total.zeroPredictionQuantity);
      assertEquals(Map.of(9, 123.0), report.omittedModes);
      assertEquals(3, report.choices.eligibleODs);
      assertTrue(Double.isFinite(report.choices.crossEntropy()));
      assertTrue(Double.isNaN(report.groupChoices.get(3).crossEntropy()));
      assertEquals(0, report.choices.zeroShareODs);
      assertEquals(0, report.groupChoices.get(3).zeroShareCells);
      assertTrue(report.zeroShareExamples().isEmpty());
    }
  }

  @Test
  void zeroPredictedModalSharesGiveFiniteJSAndInfiniteCrossEntropy() throws Exception {
    try (Connection connection = fixture(false)) {
      execute(connection, "UPDATE assignment_header SET qty=0 WHERE grp=1 AND ldmode=1");
      execute(connection, "UPDATE assignment_header SET qty=100 WHERE grp=1 AND ldmode=2");
      PerformanceIndicators.Report report = compute(connection);
      assertEquals(Double.POSITIVE_INFINITY, report.choices.crossEntropy());
      assertEquals(
          0.16919485510105411, report.groupChoices.get(1).jensenShannonDivergence(), 1e-12);
      assertTrue(Double.isFinite(report.choices.jensenShannonDivergence()));
      assertTrue(Double.isFinite(report.groupChoices.get(0).crossEntropy()));
      assertEquals(1, report.total.zeroPredictions);
      assertEquals(30, report.total.zeroPredictionQuantity);
      assertEquals(1, report.choices.zeroShareODs);
      assertEquals(1, report.choices.zeroShareCells);
      assertEquals(30, report.choices.zeroShareQuantity);
      assertEquals(0, report.groupChoices.get(0).zeroShareODs);
      assertEquals(1, report.groupChoices.get(1).zeroShareODs);
      var cell = report.zeroShareExamples().get(0);
      assertEquals(1, cell.group);
      assertEquals(1, cell.mode);
      assertEquals(5, cell.origin);
      assertEquals(6, cell.destination);
      assertEquals(30, cell.observed);
      assertEquals(100, cell.assignedODTotal);
    }
  }

  @Test
  void jensenShannonHandlesStructuralZerosAndDisjointSharesSymmetrically() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      try (Connection connection = fixture(hsql)) {
        execute(connection, "CREATE TABLE water (grp INT,org INT,dst INT,qty DOUBLE)");
        for (String table : List.of("road", "rail", "assignment_header")) {
          execute(connection, "DELETE FROM " + table);
        }
        execute(connection, "INSERT INTO road VALUES (0,1,2,3)");
        execute(connection, "INSERT INTO rail VALUES (0,1,2,7)");
        execute(connection, "INSERT INTO water VALUES (0,1,2,0)");
        execute(connection, "INSERT INTO assignment_header VALUES (0,1,2,2,10),(0,1,2,3,0)");
        Map<Integer, String> references = Map.of(1, "road", 2, "rail", 3, "water");
        var report =
            PerformanceIndicators.compute(
                connection, references, "assignment_header", true, () -> true);
        assertEquals(0.16919485510105411, report.choices.jensenShannonDivergence(), 1e-12);
        execute(connection, "UPDATE road SET qty=0");
        execute(connection, "UPDATE rail SET qty=10");
        execute(connection, "DELETE FROM assignment_header");
        execute(connection, "INSERT INTO assignment_header VALUES (0,1,2,1,3),(0,1,2,2,7)");
        var reversed =
            PerformanceIndicators.compute(
                connection, references, "assignment_header", true, () -> true);
        assertEquals(
            report.choices.jensenShannonDivergence(),
            reversed.choices.jensenShannonDivergence(),
            1e-12);
        execute(connection, "UPDATE road SET qty=10");
        execute(connection, "UPDATE rail SET qty=0");
        execute(connection, "DELETE FROM assignment_header");
        execute(connection, "INSERT INTO assignment_header VALUES (0,1,2,2,10)");
        var disjoint =
            PerformanceIndicators.compute(
                connection, references, "assignment_header", true, () -> true);
        assertEquals(1, disjoint.choices.jensenShannonDivergence(), 1e-12);
        execute(connection, "UPDATE assignment_header SET ldmode=1");
        var matching =
            PerformanceIndicators.compute(
                connection, references, "assignment_header", true, () -> true);
        assertEquals(0, matching.choices.jensenShannonDivergence(), 1e-12);
      }
    }
  }

  @Test
  void jsAveragesODDivergencesUsingObservedQuantityWeights() throws Exception {
    try (Connection connection = fixture(false)) {
      execute(connection, "UPDATE road SET qty=60 WHERE org=3");
      execute(connection, "UPDATE rail SET qty=240 WHERE org=3");
      var report = compute(connection);
      assertEquals(
          (0.007299156760473963 + 3 * 0.03485155455967723) / 5,
          report.choices.jensenShannonDivergence(),
          1e-12);
      assertEquals(
          (0.007299156760473963 + 3 * 0.03485155455967723) / 4,
          report.groupChoices.get(0).jensenShannonDivergence(),
          1e-12);
    }
  }

  @Test
  void zeroShareExamplesAreBoundedAndPrioritizeTheLargestObservedFlows() throws Exception {
    try (Connection connection = fixture(false)) {
      for (int i = 1; i <= 25; i++) {
        execute(connection, "INSERT INTO road VALUES (0," + (100 + i) + ",999," + (100 * i) + ")");
        execute(
            connection,
            "INSERT INTO assignment_header VALUES (0," + (100 + i) + ",999,2," + (100 * i) + ")");
      }
      var report = compute(connection);
      assertEquals(25, report.choices.zeroShareODs);
      assertEquals(25, report.choices.zeroShareCells);
      assertEquals(32500, report.choices.zeroShareQuantity);
      var examples = report.zeroShareExamples();
      assertEquals(20, examples.size());
      assertEquals(2500, examples.get(0).observed);
      assertEquals(600, examples.get(19).observed);
      assertThrows(UnsupportedOperationException.class, examples::clear);
    }
  }

  @Test
  void zeroObservedTotalsAndEmptyTablesAreExplicitlyUndefined() throws Exception {
    try (Connection connection = fixture(false)) {
      for (String table : List.of("road", "rail", "assignment_header")) {
        execute(connection, "DELETE FROM " + table);
      }
      PerformanceIndicators.Report empty = compute(connection);
      assertEquals(0, empty.total.cells);
      assertTrue(Double.isNaN(empty.total.wape()));
      assertTrue(Double.isNaN(empty.total.rmse()));
      assertTrue(Double.isNaN(empty.choices.jensenShannonDivergence()));
      execute(connection, "INSERT INTO road VALUES (0,1,2,NULL)");
      execute(connection, "INSERT INTO assignment_header VALUES (0,1,2,1,20)");
      PerformanceIndicators.Report report = compute(connection);
      assertTrue(Double.isNaN(report.total.wape()));
      assertTrue(Double.isNaN(report.total.bias()));
      assertTrue(Double.isNaN(report.choices.crossEntropy()));
      assertTrue(Double.isNaN(report.choices.jensenShannonDivergence()));
      assertTrue(Double.isNaN(report.choices.coveredPercent()));
      assertEquals(1, report.choices.extraODs);
      assertEquals(20, report.choices.excess);
    }
  }

  @Test
  void validatesRawQuantitiesKeysSchemasAndIdentifiers() throws Exception {
    try (Connection connection = fixture(false)) {
      execute(connection, "INSERT INTO road VALUES (0,1,2,-1)");
      assertThrows(IllegalArgumentException.class, () -> compute(connection));
      execute(connection, "DELETE FROM road WHERE qty<0");
      execute(connection, "INSERT INTO rail VALUES (NULL,1,2,1)");
      assertThrows(IllegalArgumentException.class, () -> compute(connection));
      execute(connection, "DELETE FROM rail WHERE grp IS NULL");
      assertThrows(
          java.sql.SQLException.class,
          () ->
              PerformanceIndicators.compute(
                  connection,
                  Map.of(1, "road; DROP TABLE rail"),
                  "assignment_header",
                  true,
                  () -> true));
      assertEquals(3, count(connection, "rail"));
      execute(connection, "CREATE TABLE invalid_header (grp INT)");
      assertThrows(
          java.sql.SQLException.class,
          () ->
              PerformanceIndicators.compute(
                  connection, Map.of(1, "road"), "invalid_header", true, () -> true));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              PerformanceIndicators.compute(
                  connection, Map.of(0, "road"), "assignment_header", true, () -> true));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              PerformanceIndicators.compute(
                  connection, Map.of(), "assignment_header", true, () -> true));
    }
  }

  @Test
  void detectsOnlyValidHeadersAndSupportsQuotedTableNames() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      try (Connection connection = fixture(hsql)) {
        execute(connection, "CREATE TABLE not_header (qty DOUBLE)");
        execute(
            connection, "CREATE TABLE unrelated (grp INT,org INT,dst INT,ldmode INT,qty DOUBLE)");
        execute(
            connection,
            "CREATE TABLE \"Odd & Mixed_header\" (grp INT,org INT,dst INT,ldmode INT,qty DOUBLE)");
        List<String> headers = PerformanceIndicators.pathHeaderTables(connection);
        assertEquals(2, headers.size());
        assertTrue(headers.contains("Odd & Mixed_header"));
        assertTrue(headers.stream().anyMatch(name -> name.equalsIgnoreCase("assignment_header")));
        PerformanceIndicators.Report report =
            PerformanceIndicators.compute(
                connection, Map.of(1, "road"), "Odd & Mixed_header", true, () -> true);
        assertEquals(110, report.total.observed);
        assertEquals(110, report.total.absoluteError);
      }
    }
  }

  @Test
  void cancellationAndReadingDoNotCommitOrAlterInputs() throws Exception {
    try (Connection connection = fixture(false)) {
      connection.setAutoCommit(false);
      execute(connection, "INSERT INTO road VALUES (0,5,6,9)");
      assertEquals(119, compute(connection).modes.get(1).observed);
      assertFalse(connection.getAutoCommit());
      AtomicInteger calls = new AtomicInteger();
      assertThrows(
          CancellationException.class,
          () ->
              PerformanceIndicators.compute(
                  connection,
                  Map.of(1, "road", 2, "rail"),
                  "assignment_header",
                  true,
                  () -> calls.incrementAndGet() < 4));
      assertEquals(6, count(connection, "road"));
      connection.rollback();
      assertEquals(5, count(connection, "road"));
      PerformanceIndicators.Report noDistances =
          PerformanceIndicators.compute(
              connection, Map.of(1, "road", 2, "rail"), "assignment_header", false, () -> true);
      assertTrue(Double.isNaN(noDistances.choices.percentile(0.5)));
      assertTrue(Double.isFinite(noDistances.choices.crossEntropy()));
    }
  }

  private static Connection fixture(boolean hsql) throws Exception {
    Connection connection =
        DriverManager.getConnection(
            (hsql ? "jdbc:hsqldb:mem:" : "jdbc:h2:mem:") + UUID.randomUUID(), "sa", "");
    execute(connection, "CREATE TABLE road (grp INT,org INT,dst INT,qty DOUBLE)");
    execute(connection, "CREATE TABLE rail (grp INT,org INT,dst INT,qty DOUBLE)");
    execute(
        connection,
        "CREATE TABLE assignment_header (grp INT,org INT,dst INT,ldmode INT,qty DOUBLE)");
    execute(
        connection,
        "INSERT INTO road VALUES (0,1,2,20),(0,1,2,40),(0,1,2,NULL),(0,3,4,20),(1,5,6,30)");
    execute(connection, "INSERT INTO rail VALUES (0,1,2,40),(0,3,4,80),(1,5,6,70)");
    execute(
        connection,
        "INSERT INTO assignment_header VALUES (0,1,2,1,30),(0,1,2,1,20),"
            + "(0,1,2,2,50),(0,3,4,1,15),(0,3,4,1,25),(0,3,4,2,60),(1,5,6,1,30),(1,5,6,2,70)");
    return connection;
  }

  private static PerformanceIndicators.Report compute(Connection connection) throws Exception {
    return PerformanceIndicators.compute(
        connection, Map.of(1, "road", 2, "rail"), "assignment_header", true, () -> true);
  }

  private static long count(Connection connection, String table) throws Exception {
    try (Statement statement = connection.createStatement();
        var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static void execute(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }
}
