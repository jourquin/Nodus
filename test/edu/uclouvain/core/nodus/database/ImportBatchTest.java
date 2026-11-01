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

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ImportBatchTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> batchFailuresRetainActualSourcePositions() {
    List<DynamicTest> tests = new ArrayList<>();
    int[][] counts = {
      {1, Statement.EXECUTE_FAILED, 1},
      {1},
      {Statement.SUCCESS_NO_INFO, 1},
      {},
      {Statement.EXECUTE_FAILED, 1, Statement.EXECUTE_FAILED},
      {-99},
      {1, 1, 1}
    };
    String[] positions = {"8", "8", "11", "3–11", "3–11", "3–11", "3–11"};
    for (int n = 0; n < counts.length; n++) {
      int index = n;
      tests.add(
          dynamicTest(
              "driver update counts " + n,
              () -> {
                SQLException cause =
                    new BatchUpdateException("constraint failed", "23505", 7, counts[index]);
                ImportProgress progress = new ImportProgress();
                ImportBatch batch = batch(statement(true, cause), 3, "CSV record", progress);
                batch.add(3);
                batch.add(8);
                SQLException error = assertThrows(SQLException.class, () -> batch.add(11));
                assertTrue(
                    error.getMessage().startsWith("CSV record " + positions[index] + ":"),
                    error.getMessage());
                assertEquals("23505", error.getSQLState());
                assertEquals(7, error.getErrorCode());
                assertSame(cause, error.getCause());
              }));
    }
    return tests;
  }

  @Test
  void unknownBatchFailureReportsRangeAndNonBatchedFailureReportsExactRow() throws Exception {
    SQLException cause = new SQLException("connection failed");
    ImportBatch batch = batch(statement(true, cause), 2, "DBF record", new ImportProgress());
    batch.add(4);
    assertTrue(
        assertThrows(SQLException.class, () -> batch.add(7))
            .getMessage()
            .startsWith("DBF record 4–7:"));
    ImportBatch single = batch(statement(false, cause), 2, "Spreadsheet row", new ImportProgress());
    assertTrue(
        assertThrows(SQLException.class, () -> single.add(19))
            .getMessage()
            .startsWith("Spreadsheet row 19:"));
  }

  @Test
  void cancellationIsCooperativeAndStopsBeforePublication() throws Exception {
    ImportProgress progress = new ImportProgress();
    progress.phase("Loading");
    progress.loaded();
    assertEquals(1, progress.getRecords());
    assertTrue(progress.cancel());
    assertEquals("57014", assertThrows(SQLException.class, progress::checkCancelled).getSQLState());
    assertThrows(SQLException.class, progress::finishing);
    try (var input = ImportSource.cancellable(new ByteArrayInputStream(new byte[] {1}), progress)) {
      assertThrows(IOException.class, input::read);
    }
    ImportProgress completed = new ImportProgress();
    completed.finishing();
    assertFalse(completed.cancel());
    completed.checkCancelled();
  }

  @Test
  void changesAndMissingSourceAreDetectedWithoutReadingContents() throws Exception {
    Path source = directory.resolve("data.csv");
    Files.writeString(source, "1,rail");
    ImportSource stable = new ImportSource(source);
    stable.verify();
    Files.setLastModifiedTime(
        source, FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis() + 10000));
    assertTrue(assertThrows(SQLException.class, stable::verify).getMessage().contains("changed"));
    ImportSource missing = new ImportSource(source);
    Files.delete(source);
    assertTrue(
        assertThrows(SQLException.class, missing::verify).getMessage().contains("unavailable"));
  }

  private static ImportBatch batch(
      PreparedStatement statement, int limit, String position, ImportProgress progress)
      throws SQLException {
    return new ImportBatch(statement.getConnection(), statement, limit, position, progress);
  }

  private static PreparedStatement statement(boolean batching, SQLException failure) {
    DatabaseMetaData metadata =
        (DatabaseMetaData)
            Proxy.newProxyInstance(
                ImportBatchTest.class.getClassLoader(),
                new Class<?>[] {DatabaseMetaData.class},
                (proxy, method, args) ->
                    method.getName().equals("supportsBatchUpdates") ? batching : null);
    Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                ImportBatchTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> method.getName().equals("getMetaData") ? metadata : null);
    return (PreparedStatement)
        Proxy.newProxyInstance(
            ImportBatchTest.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getConnection")) {
                return connection;
              }
              if (method.getName().equals("executeBatch")
                  || method.getName().equals("executeUpdate")) {
                throw failure;
              }
              return null;
            });
  }
}
