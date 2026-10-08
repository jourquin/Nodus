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

package edu.uclouvain.core.nodus.compute.modalsplit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModalParameterTableTest {
  @TempDir Path directory;

  @Test
  void costFileStemDeterminesTheParameterTableName() {
    assertEquals(
        "NodusEstimated_params", ModalParameterTable.nameForCostFile("NodusEstimated.costs"));
    assertEquals(
        "lineas - Copy_params", ModalParameterTable.nameForCostFile("lineas - Copy.costs"));
    assertEquals(
        "MoreBoxCox-best_params", ModalParameterTable.nameForCostFile("MoreBoxCox-best.costs"));
    assertEquals("123_params", ModalParameterTable.nameForCostFile("123.costs"));
    assertThrows(
        IllegalArgumentException.class, () -> ModalParameterTable.nameForCostFile("model.csv"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ModalParameterTable.nameForCostFile("model.v2.costs"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ModalParameterTable.nameForCostFile("x".repeat(58) + ".costs"));
  }

  @Test
  void costFileMethodLookupUsesTheLinkedTable() throws Exception {
    Path costs = directory.resolve("choice.costs");
    for (String jdbc : List.of("jdbc:h2:mem:", "jdbc:hsqldb:mem:")) {
      try (Connection connection =
          DriverManager.getConnection(jdbc + UUID.randomUUID(), "sa", "")) {
        Files.writeString(costs, "mv.1,1=BASECOST\n");
        assertNull(ModalParameterTable.methodForCostFile(connection, costs));

        String table = "choice params";
        Properties values = new Properties();
        values.setProperty(ModalParameterTable.METHOD, "MNP");
        ModalParameterTable.save(connection, table, values);
        Files.writeString(costs, "mv.1,1=BASECOST\n@paramTable=" + table + "\n");
        assertEquals("MNP", ModalParameterTable.methodForCostFile(connection, costs));

        values.remove(ModalParameterTable.METHOD);
        ModalParameterTable.save(connection, table, values);
        assertThrows(
            SQLException.class, () -> ModalParameterTable.methodForCostFile(connection, costs));
      }
    }
  }

  @Test
  void pivotsAreBoundedAndRestoreOppositeObservedSharesForAllEmbeddedModels() {
    for (String method : List.of("MNL", "MNP", "Proportional")) {
      LogCostChoiceEstimate behavioral =
          new LogCostChoiceEstimate(
              new double[] {0, -1}, new double[] {0, 0}, 0, 0, 0, 200, 2, 0, "MNP".equals(method));
      Properties output = new Properties();
      int count =
          ModalPivotEstimator.estimate(
              List.of(
                  new ModalPivotEstimator.Row(
                      0, 10, 20, new double[] {10, 10}, new double[] {90, 10}),
                  new ModalPivotEstimator.Row(
                      0, 30, 40, new double[] {10, 10}, new double[] {10, 90})),
              Map.of(0, behavioral),
              new int[] {1, 2},
              0,
              method,
              8,
              output,
              () -> true);
      assertEquals(2, count);
      double low = ModalParameterTable.pivot(output, 2, 10, 20, 0, 8);
      double high = ModalParameterTable.pivot(output, 2, 30, 40, 0, 8);
      assertTrue(low < 0 && low >= -8, method);
      assertTrue(high > 0 && high <= 8, method);
      assertEquals(0, ModalParameterTable.pivot(output, 1, 10, 20, 0, 8));
      assertEquals(0, ModalParameterTable.pivot(output, 2, 99, 99, 0, 8));
    }
  }

  @Test
  void pivotEstimationHonorsCancellationBeforeFitting() {
    LogCostChoiceEstimate behavioral =
        new LogCostChoiceEstimate(
            new double[] {0, -1}, new double[] {0, 0}, 0, 0, 0, 100, 1, 0, false);
    assertThrows(
        java.util.concurrent.CancellationException.class,
        () ->
            ModalPivotEstimator.estimate(
                List.of(
                    new ModalPivotEstimator.Row(
                        0, 10, 20, new double[] {10, 10}, new double[] {90, 10})),
                Map.of(0, behavioral),
                new int[] {1, 2},
                0,
                "MNL",
                8,
                new Properties(),
                () -> false));
  }

  @Test
  void chosenPivotBoundIsAppliedAndValidated() {
    LogCostChoiceEstimate behavioral =
        new LogCostChoiceEstimate(
            new double[] {0, 0}, new double[] {0, 0}, 0, 0, 0, 100, 1, 0, false);
    Properties output = new Properties();
    ModalPivotEstimator.estimate(
        List.of(
            new ModalPivotEstimator.Row(0, 10, 20, new double[] {10, 10}, new double[] {99, 1})),
        Map.of(0, behavioral),
        new int[] {1, 2},
        0,
        "MNL",
        0.5,
        output,
        () -> true);
    assertEquals("pivot.2.10.20.0", ModalParameterTable.pivotKey(2, 10, 20, 0));
    assertEquals(-0.5, ModalParameterTable.pivot(output, 2, 10, 20, 0, 0.5));
    output.setProperty(ModalParameterTable.PIVOT_MAX_ABS, "0.5");
    assertEquals(0.5, ModalParameterTable.pivotMaxAbs(output));
    assertThrows(
        IllegalArgumentException.class,
        () -> ModalParameterTable.pivot(output, 2, 10, 20, 0, 0.25));
    assertThrows(IllegalArgumentException.class, () -> ModalParameterTable.validatePivotMaxAbs(0));
  }

  @Test
  void tableReplacesItsRowsOnBothSupportedEmbeddedDatabases() throws Exception {
    for (String jdbc : List.of("jdbc:h2:mem:", "jdbc:hsqldb:mem:")) {
      try (Connection connection =
          DriverManager.getConnection(jdbc + UUID.randomUUID(), "sa", "")) {
        Properties values = new Properties();
        values.setProperty(ModalParameterTable.METHOD, "MNL");
        values.setProperty("mnl.reference.0", "1");
        values.setProperty(ModalParameterTable.pivotKey(2, 10, 20, 0), "-2");
        ModalParameterTable.save(connection, "modal_params", values);
        assertTrue(ModalParameterTable.exists(connection, "modal_params"));
        try (ResultSet columns =
            connection.getMetaData().getColumns(null, null, "MODAL_PARAMS", "PARAM_KEY")) {
          assertTrue(columns.next());
          assertEquals(191, columns.getInt("COLUMN_SIZE"));
        }
        values.remove(ModalParameterTable.pivotKey(2, 10, 20, 0));
        ModalParameterTable.save(connection, "modal_params", values);
        try (Statement statement = connection.createStatement();
            ResultSet rows =
                statement.executeQuery(
                    "SELECT COUNT(*) FROM modal_params WHERE param_type='calibration'")) {
          rows.next();
          assertEquals(0, rows.getInt(1));
        }
      }
    }
  }

  @Test
  void costFileNamesWithSpacesAndHyphensWorkOnBothDatabases() throws Exception {
    String table = ModalParameterTable.nameForCostFile("lineas - Copy.costs");
    for (String jdbc : List.of("jdbc:h2:mem:", "jdbc:hsqldb:mem:")) {
      try (Connection connection =
          DriverManager.getConnection(jdbc + UUID.randomUUID(), "sa", "")) {
        Properties values = new Properties();
        values.setProperty(ModalParameterTable.METHOD, "MNL");
        ModalParameterTable.save(connection, table, values);
        assertTrue(ModalParameterTable.exists(connection, table));
        ModalParameterTable.checkSchema(connection, table);
        values.setProperty("mnl.reference.0", "1");
        ModalParameterTable.save(connection, table, values);
        String quote = connection.getMetaData().getIdentifierQuoteString();
        try (Statement statement = connection.createStatement();
            ResultSet rows =
                statement.executeQuery("SELECT COUNT(*) FROM " + quote + table + quote)) {
          assertTrue(rows.next());
          assertEquals(2, rows.getInt(1));
        }
      }
    }
  }

  @Test
  void oversizedParameterKeyIsRejectedBeforeCreatingTable() throws Exception {
    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")) {
      Properties values = new Properties();
      values.setProperty("x".repeat(192), "1");
      assertThrows(
          IllegalArgumentException.class,
          () -> ModalParameterTable.save(connection, "modal_params", values));
      assertFalse(ModalParameterTable.exists(connection, "modal_params"));
    }
  }

  @Test
  void largeParameterSetIsSavedAcrossBulkChunks() throws Exception {
    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")) {
      Properties values = new Properties();
      values.setProperty(ModalParameterTable.METHOD, "MNL");
      for (int destination = 1; destination <= 6000; destination++) {
        values.setProperty(ModalParameterTable.pivotKey(2, 1, destination, 0), "0.5");
      }
      ModalParameterTable.save(connection, "modal_params", values);
      try (Statement statement = connection.createStatement();
          ResultSet rows =
              statement.executeQuery(
                  "SELECT COUNT(*) FROM modal_params WHERE param_type='calibration'")) {
        assertTrue(rows.next());
        assertEquals(6000, rows.getInt(1));
      }
    }
  }

  @Test
  void cancellationRetainsPreviousTableContents() throws Exception {
    for (String jdbc : List.of("jdbc:h2:mem:", "jdbc:hsqldb:mem:")) {
      try (Connection connection =
          DriverManager.getConnection(jdbc + UUID.randomUUID(), "sa", "")) {
        Properties old = new Properties();
        old.setProperty(ModalParameterTable.METHOD, "MNL");
        ModalParameterTable.save(connection, "modal_params", old);
        Properties replacement = new Properties();
        replacement.setProperty(ModalParameterTable.METHOD, "MNP");
        assertThrows(
            java.util.concurrent.CancellationException.class,
            () -> ModalParameterTable.save(connection, "modal_params", replacement, () -> false));
        try (Statement statement = connection.createStatement();
            ResultSet rows =
                statement.executeQuery(
                    "SELECT param_value FROM modal_params WHERE param_key='@nodus.method'")) {
          assertTrue(rows.next());
          assertEquals("MNL", rows.getString(1));
        }
      }
    }
  }

  @Test
  void successfulSaveDoesNotCommitCallerTransaction() throws Exception {
    for (String jdbc : List.of("jdbc:h2:mem:", "jdbc:hsqldb:mem:")) {
      try (Connection connection =
          DriverManager.getConnection(jdbc + UUID.randomUUID(), "sa", "")) {
        Properties old = new Properties();
        old.setProperty(ModalParameterTable.METHOD, "MNL");
        ModalParameterTable.save(connection, "modal_params", old);
        try (Statement statement = connection.createStatement()) {
          statement.executeUpdate("CREATE TABLE pending (id INT)");
        }
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          statement.executeUpdate("INSERT INTO pending VALUES (1)");
        }
        Properties replacement = new Properties();
        replacement.setProperty(ModalParameterTable.METHOD, "MNP");
        ModalParameterTable.save(connection, "modal_params", replacement);
        assertFalse(connection.getAutoCommit());
        connection.rollback();
        try (Statement statement = connection.createStatement();
            ResultSet pending = statement.executeQuery("SELECT COUNT(*) FROM pending")) {
          assertTrue(pending.next());
          assertEquals(0, pending.getInt(1));
        }
        try (Statement statement = connection.createStatement();
            ResultSet parameter =
                statement.executeQuery(
                    "SELECT param_value FROM modal_params WHERE param_key='@nodus.method'")) {
          assertTrue(parameter.next());
          assertEquals("MNL", parameter.getString(1));
        }
      }
    }
  }

  @Test
  void nontransactionalDdlCannotCommitCallerWork() throws Exception {
    for (String jdbc : List.of("jdbc:h2:mem:", "jdbc:hsqldb:mem:")) {
      try (Connection connection =
          DriverManager.getConnection(jdbc + UUID.randomUUID(), "sa", "")) {
        try (Statement statement = connection.createStatement()) {
          statement.executeUpdate("CREATE TABLE pending (id INT)");
        }
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          statement.executeUpdate("INSERT INTO pending VALUES (1)");
        }
        Properties values = new Properties();
        values.setProperty(ModalParameterTable.METHOD, "MNL");
        assertThrows(
            java.sql.SQLException.class,
            () -> ModalParameterTable.save(connection, "new_params", values));
        connection.rollback();
        assertFalse(ModalParameterTable.exists(connection, "new_params"));
        try (Statement statement = connection.createStatement();
            ResultSet pending = statement.executeQuery("SELECT COUNT(*) FROM pending")) {
          assertTrue(pending.next());
          assertEquals(0, pending.getInt(1));
        }
      }
    }
  }

  @Test
  void fileFailureRollsBackParameterRowsAndRestoresEarlierOutput() throws Exception {
    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")) {
      Properties old = new Properties();
      old.setProperty(ModalParameterTable.METHOD, "MNL");
      ModalParameterTable.save(connection, "modal_params", old);
      Path costFile = directory.resolve("model.costs");
      String original = "mv.1,1=BASECOST\n@paramTable=modal_params\n";
      Files.writeString(costFile, original);
      LogitCostFile.Target target = LogitCostFile.target(costFile, costFile);
      Properties replacement = new Properties();
      replacement.setProperty(ModalParameterTable.METHOD, "MNP");
      // Reusing the cost-file path as the report destination makes the report write
      // succeed, then forces the guarded cost-file replacement to fail.
      assertThrows(
          java.io.IOException.class,
          () ->
              LogitCalibration.saveTableOutputs(
                  connection,
                  "modal_params",
                  replacement,
                  target,
                  costFile,
                  new StringBuilder("# report\n"),
                  () -> true));
      assertEquals(original, Files.readString(costFile));
      try (Statement statement = connection.createStatement();
          ResultSet parameter =
              statement.executeQuery(
                  "SELECT param_value FROM modal_params WHERE param_key='@nodus.method'")) {
        assertTrue(parameter.next());
        assertEquals("MNL", parameter.getString(1));
      }
    }
  }

  @Test
  void cancellationAfterFileInstallationRestoresFilesAndParameterRows() throws Exception {
    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")) {
      Properties old = new Properties();
      old.setProperty(ModalParameterTable.METHOD, "MNL");
      ModalParameterTable.save(connection, "modal_params", old);
      Path costFile = directory.resolve("model.costs");
      Path reportFile = directory.resolve("model_params.txt");
      String originalCost = "mv.1,1=BASECOST\n@paramTable=modal_params\n";
      String originalReport = "previous estimation\n";
      Files.writeString(costFile, originalCost);
      Files.writeString(reportFile, originalReport);
      LogitCostFile.Target target = LogitCostFile.target(costFile, costFile);
      Properties replacement = new Properties();
      replacement.setProperty(ModalParameterTable.METHOD, "MNP");
      int[] checks = {0};
      assertThrows(
          java.util.concurrent.CancellationException.class,
          () ->
              LogitCalibration.saveTableOutputs(
                  connection,
                  "modal_params",
                  replacement,
                  target,
                  reportFile,
                  new StringBuilder("# new report\n"),
                  () -> ++checks[0] == 1));
      assertEquals(originalCost, Files.readString(costFile));
      assertEquals(originalReport, Files.readString(reportFile));
      try (Statement statement = connection.createStatement();
          ResultSet parameter =
              statement.executeQuery(
                  "SELECT param_value FROM modal_params WHERE param_key='@nodus.method'")) {
        assertTrue(parameter.next());
        assertEquals("MNL", parameter.getString(1));
      }
    }
  }

  @Test
  void unrelatedTableCannotBeReplaced() throws Exception {
    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")) {
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("CREATE TABLE observed_od (grp INT, qty DOUBLE)");
        statement.executeUpdate("INSERT INTO observed_od VALUES (0, 12)");
      }
      Properties values = new Properties();
      values.setProperty(ModalParameterTable.METHOD, "MNL");
      assertThrows(
          java.sql.SQLException.class,
          () -> ModalParameterTable.save(connection, "observed_od", values));
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery("SELECT qty FROM observed_od")) {
        assertTrue(rows.next());
        assertEquals(12, rows.getDouble(1));
      }
    }
  }

  @Test
  void costFileKeepsTransportFormulasAndOnlyPointsToParameterTable() throws Exception {
    Path file = directory.resolve("model.costs");
    Files.writeString(
        file,
        "# transport cost\nmv.1,1 = BASECOST\nlog(cost).1.0 = -2\n"
            + "probit.log(cost).1.0 = -3\nproportional.costFactor.1.0 = 1\n");
    Properties saved =
        LogitCostFile.saveParameterTable(LogitCostFile.target(file, file), "modal_params");
    String text = Files.readString(file);
    assertTrue(text.contains("# transport cost\n"));
    assertEquals("BASECOST", saved.getProperty("mv.1,1"));
    assertEquals("modal_params", saved.getProperty("@paramTable"));
    assertFalse(text.contains("log(cost).1.0"));
    assertFalse(text.contains("proportional.costFactor.1.0"));
  }
}
