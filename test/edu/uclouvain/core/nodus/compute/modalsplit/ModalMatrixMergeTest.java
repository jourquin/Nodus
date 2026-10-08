/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class ModalMatrixMergeTest {
  @Test
  void destinationIsOptionalAndMustDifferFromSourcesAndParameters() {
    assertEquals("", ModalMatrixMerge.validateName("  ", List.of("road"), "params"));
    assertEquals("total", ModalMatrixMerge.validateName(" total ", List.of("road"), "params"));
    for (String name : List.of("ROAD", "PARAMS", "x; DROP TABLE road", "x".repeat(65))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> ModalMatrixMerge.validateName(name, List.of("road"), "params"));
    }
  }

  @Test
  void mergeSumsDuplicateAndUnmatchedCellsAcrossClassesAndKeepsSources() throws Exception {
    for (String jdbc : List.of("jdbc:h2:mem:", "jdbc:hsqldb:mem:")) {
      try (Connection connection = database(jdbc)) {
        try (ModalMatrixMerge.Prepared merge =
            ModalMatrixMerge.prepare(
                connection, "total", List.of("road", "rail"), "params", false)) {
          ModalParameterTable.save(
              connection, "params", new Properties(), () -> true, merge::write, () -> {});
          merge.complete();
        }
        assertTrue(ModalMatrixMerge.exists(connection, "total"));
        try (Statement s = connection.createStatement();
            ResultSet rows =
                s.executeQuery("SELECT grp, org, dst, qty FROM total ORDER BY grp, org, dst")) {
          assertTrue(rows.next());
          assertEquals(1, rows.getInt(1));
          assertEquals(10, rows.getInt(2));
          assertEquals(20, rows.getInt(3));
          assertEquals(Types.DECIMAL, rows.getMetaData().getColumnType(4));
          assertEquals(38, rows.getMetaData().getPrecision(4));
          assertEquals(12, rows.getMetaData().getScale(4));
          assertEquals(new BigDecimal("60.750000000001"), rows.getBigDecimal(4));
          assertTrue(rows.next());
          assertEquals(30, rows.getInt(3));
          assertEquals(4, rows.getDouble(4));
          assertTrue(rows.next());
          assertEquals(2, rows.getInt(1));
          assertEquals(0, rows.getDouble(4));
          assertFalse(rows.next());
        }
        try (Statement s = connection.createStatement();
            ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM road")) {
          rows.next();
          assertEquals(3, rows.getInt(1));
        }
        assertThrows(
            SQLException.class,
            () ->
                ModalMatrixMerge.prepare(
                    connection, "total", List.of("road", "rail"), "params", false));
      }
    }
  }

  @Test
  void failedOrCanceledSaveRestoresExistingMatrixAndRemovesNewMatrix() throws Exception {
    for (String jdbc : List.of("jdbc:h2:mem:", "jdbc:hsqldb:mem:")) {
      try (Connection connection = database(jdbc)) {
        try (Statement s = connection.createStatement()) {
          s.executeUpdate(
              "CREATE TABLE total (grp INTEGER, org INTEGER, dst INTEGER, qty DOUBLE, class INTEGER NOT NULL)");
          s.executeUpdate("INSERT INTO total VALUES (9,8,7,6,5)");
        }
        ModalParameterTable.save(connection, "params", new Properties());
        try (ModalMatrixMerge.Prepared merge =
            ModalMatrixMerge.prepare(
                connection, "total", List.of("road", "rail"), "params", true)) {
          assertThrows(
              SQLException.class,
              () ->
                  ModalParameterTable.save(
                      connection,
                      "params",
                      new Properties(),
                      () -> true,
                      () -> {
                        merge.write();
                        throw new SQLException("Injected file save failure");
                      },
                      () -> {}));
        }
        try (Statement s = connection.createStatement();
            ResultSet rows = s.executeQuery("SELECT * FROM total")) {
          assertTrue(rows.next());
          assertEquals(9, rows.getInt(1));
          assertEquals(6, rows.getDouble(4));
          assertEquals(5, rows.getInt(5));
          assertFalse(rows.next());
        }
        try (ModalMatrixMerge.Prepared merge =
            ModalMatrixMerge.prepare(
                connection, "new_total", List.of("road", "rail"), "params", false)) {
          assertThrows(
              CancellationException.class,
              () ->
                  ModalParameterTable.save(
                      connection,
                      "params",
                      new Properties(),
                      () -> true,
                      () -> {
                        merge.write();
                        throw new CancellationException("Canceled");
                      },
                      () -> {}));
        }
        assertFalse(ModalMatrixMerge.exists(connection, "new_total"));
        try (Statement s = connection.createStatement()) {
          s.executeUpdate("CREATE TABLE other (param_key VARCHAR(10))");
        }
        assertThrows(SQLException.class, () -> ModalMatrixMerge.exists(connection, "other"));
      }
    }
  }

  private Connection database(String jdbc) throws Exception {
    Connection connection = DriverManager.getConnection(jdbc + UUID.randomUUID(), "sa", "");
    try (Statement s = connection.createStatement()) {
      s.executeUpdate(
          "CREATE TABLE road (grp INTEGER, org INTEGER, dst INTEGER, qty DECIMAL(38,12), class INTEGER)");
      s.executeUpdate("CREATE TABLE rail (grp INTEGER, org INTEGER, dst INTEGER, qty DECIMAL(38,12))");
      s.executeUpdate("INSERT INTO road VALUES (1,10,20,10.250000000001,0),(1,10,20,20,1),(2,10,20,NULL,0)");
      s.executeUpdate("INSERT INTO rail VALUES (1,10,20,30.5),(1,10,30,4)");
    }
    return connection;
  }
}
