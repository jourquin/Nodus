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

package edu.uclouvain.core.nodus.database.xls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real Excel round trips, bounded JDBC batches, format limits and streaming-file cleanup. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_ERR")
@ResourceLock("POI_TEMP_FILES")
@Timeout(60)
class ExcelIoTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> importBatchesAndFallback() {
    return Stream.of(false, true)
        .flatMap(
            xlsx ->
                Stream.of(false, true)
                    .map(
                        batch ->
                            DynamicTest.dynamicTest(
                                "xlsx=" + xlsx + ", batch=" + batch,
                                () -> {
                                  try (DatabaseFixture f = new DatabaseFixture(directory)) {
                                    f.execute(
                                        "CREATE TABLE data(id NUMERIC(6), label VARCHAR(12))");
                                    writeInput(xlsx, false);
                                    BatchCounts counts = new BatchCounts();
                                    Connection tracked = track(f.connection, batch, counts);
                                    NodusProject project = project(f, tracked);
                                    assertTrue(ImportXLS.importTable(project, "data", xlsx));
                                    assertEquals(
                                        batch ? List.of(2, 2, 1) : List.of(), counts.sizes);
                                    assertEquals(batch ? 0 : 5, counts.updates);
                                    assertEquals(1, counts.closed);
                                    assertEquals(
                                        List.of(
                                            "1:L'été 東京",
                                            "2:",
                                            "3:L'été 東京",
                                            "4:L'été 東京",
                                            "5:L'été 東京"),
                                        rows(f.connection));
                                    assertFalse(f.connection.isClosed());
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> batchSqlFailureRollsBackEarlierBatches() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                        f.execute("CREATE TABLE data(id NUMERIC(6), label VARCHAR(12))");
                        f.execute("INSERT INTO data VALUES(99, 'original')");
                        f.execute("CREATE TABLE earlier(id INTEGER)");
                        writeInput(true, true);
                        f.connection.setAutoCommit(false);
                        f.execute("INSERT INTO earlier VALUES(42)");
                        assertFalse(quiet(() -> ImportXLS.importTable(f.project, "data", true)));
                        assertEquals(List.of("99:original"), rows(f.connection));
                        assertFalse(f.connection.getAutoCommit());
                        try (Statement s = f.connection.createStatement();
                            ResultSet r = s.executeQuery("SELECT COUNT(*) FROM earlier")) {
                          assertTrue(r.next());
                          assertEquals(1, r.getInt(1));
                        }
                        f.connection.rollback();
                        try (Statement s = f.connection.createStatement();
                            ResultSet r = s.executeQuery("SELECT COUNT(*) FROM earlier")) {
                          assertTrue(r.next());
                          assertEquals(0, r.getInt(1));
                        }
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> exportRoundTripsBeyondTheStreamingWindow() {
    return Stream.of(false, true)
        .flatMap(
            xlsx ->
                Stream.of(false, true)
                    .map(
                        hsql ->
                            DynamicTest.dynamicTest(
                                "xlsx=" + xlsx + ", HSQLDB=" + hsql,
                                () -> {
                                  try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                                    createExportData(f, false);
                                    Path spill = Files.createTempDirectory(directory, "poi-");
                                    TempFile.setTempFileCreationStrategy(
                                        new DefaultTempFileCreationStrategy(spill.toFile()));
                                    try {
                                      assertTrue(ExportXLS.exportTable(f.project, "data", xlsx));
                                      assertNoTemporaryFiles(spill);
                                      try (Workbook book =
                                          WorkbookFactory.create(
                                              directory
                                                  .resolve("data." + (xlsx ? "xlsx" : "xls"))
                                                  .toFile())) {
                                        var sheet = book.getSheetAt(0);
                                        assertEquals(301, sheet.getPhysicalNumberOfRows());
                                        assertTrue(
                                            sheet
                                                .getRow(0)
                                                .getCell(0)
                                                .getStringCellValue()
                                                .contains(",N,"));
                                        assertTrue(
                                            sheet
                                                .getRow(0)
                                                .getCell(1)
                                                .getStringCellValue()
                                                .contains(",C,"));
                                        for (int row = 1; row <= 300; row++) {
                                          assertEquals(
                                              row,
                                              sheet.getRow(row).getCell(0).getNumericCellValue());
                                          if (row != 2) {
                                            assertEquals(
                                                "L'été 東京 " + row,
                                                sheet.getRow(row).getCell(1).getStringCellValue());
                                          }
                                        }
                                      }
                                      assertFalse(f.connection.isClosed());
                                      assertTrue(ImportXLS.importTable(f.project, "data", xlsx));
                                      assertEquals(300, rows(f.connection).size());
                                      assertEquals("300:L'été 東京 300", rows(f.connection).get(299));
                                    } finally {
                                      TempFile.setTempFileCreationStrategy(
                                          new DefaultTempFileCreationStrategy());
                                    }
                                  }
                                })));
  }

  @Test
  void failedStreamingExportCleansTemporaryFilesAndPreservesExistingFile() throws Exception {
    try (DatabaseFixture f = new DatabaseFixture(directory)) {
      createExportData(f, true);
      Path target = directory.resolve("data.xlsx");
      Files.writeString(target, "previous export");
      Path spill = Files.createTempDirectory(directory, "poi-failure-");
      TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy(spill.toFile()));
      try {
        assertFalse(quiet(() -> ExportXLS.exportTable(f.project, "data", true)));
        assertNoTemporaryFiles(spill);
        assertEquals("previous export", Files.readString(target));
        assertFalse(f.connection.isClosed());
      } finally {
        TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy());
      }
    }
  }

  @Test
  void legacyRowLimitIncludesTheSchemaRow() throws Exception {
    try (DatabaseFixture f = new DatabaseFixture(directory)) {
      f.execute("CREATE TABLE data AS SELECT X AS id FROM SYSTEM_RANGE(1, 65536)");
      Path target = directory.resolve("data.xls");
      Files.writeString(target, "previous export");
      assertFalse(quiet(() -> ExportXLS.exportTable(f.project, "data", false)));
      assertEquals("previous export", Files.readString(target));
    }
  }

  @Test
  void legacyColumnLimitRejectsOversizedTables() throws Exception {
    try (DatabaseFixture f = new DatabaseFixture(directory)) {
      List<String> fields = new ArrayList<>();
      for (int i = 0; i < 257; i++) {
        fields.add("col" + i + " INTEGER");
      }
      f.execute("CREATE TABLE data(" + String.join(",", fields) + ")");
      assertFalse(quiet(() -> ExportXLS.exportTable(f.project, "data", false)));
      assertFalse(Files.exists(directory.resolve("data.xls")));
    }
  }

  private void writeInput(boolean xlsx, boolean badLastRow) throws Exception {
    try (Workbook book = xlsx ? new XSSFWorkbook() : new HSSFWorkbook()) {
      var sheet = book.createSheet();
      for (int i = 0; i < 5; i++) {
        var row = sheet.createRow(i);
        row.createCell(0).setCellValue(i + 1);
        if (i != 1) {
          row.createCell(1).setCellValue(badLastRow && i == 4 ? "x".repeat(20) : "L'été 東京");
        }
      }
      try (var out = Files.newOutputStream(directory.resolve("data." + (xlsx ? "xlsx" : "xls")))) {
        book.write(out);
      }
    }
  }

  private static void createExportData(DatabaseFixture f, boolean badLastRow) throws Exception {
    f.execute("CREATE TABLE data(id NUMERIC(6), label VARCHAR(40000))");
    try (PreparedStatement s = f.connection.prepareStatement("INSERT INTO data VALUES(?,?)")) {
      for (int i = 1; i <= 300; i++) {
        s.setInt(1, i);
        s.setString(
            2, i == 2 ? null : badLastRow && i == 300 ? "x".repeat(33000) : "L'été 東京 " + i);
        s.addBatch();
      }
      s.executeBatch();
    }
  }

  private static List<String> rows(Connection connection) throws Exception {
    List<String> result = new ArrayList<>();
    try (Statement s = connection.createStatement();
        ResultSet r = s.executeQuery("SELECT id,label FROM data ORDER BY id")) {
      while (r.next()) {
        result.add(r.getInt(1) + ":" + r.getString(2));
      }
    }
    return result;
  }

  private static void assertNoTemporaryFiles(Path directory) throws Exception {
    try (Stream<Path> files = Files.walk(directory)) {
      assertEquals(0, files.filter(Files::isRegularFile).count());
    }
  }

  private static boolean quiet(Callable<Boolean> action) throws Exception {
    PrintStream previous = System.err;
    try (PrintStream capture = new PrintStream(new ByteArrayOutputStream())) {
      System.setErr(capture);
      return action.call();
    } finally {
      System.setErr(previous);
    }
  }

  private static NodusProject project(DatabaseFixture f, Connection connection) {
    return new NodusProject(null) {
      @Override
      public Connection getMainJDBCConnection() {
        return connection;
      }

      @Override
      public String getLocalProperty(String key) {
        return f.project.getLocalProperty(key);
      }

      @Override
      public int getLocalProperty(String key, int fallback) {
        return f.project.getLocalProperty(key, fallback);
      }
    };
  }

  /** Records actual driver calls while delegating every SQL operation to the real database. */
  private static Connection track(Connection delegate, boolean batch, BatchCounts counts) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              Object value = invoke(method, delegate, args);
              if (method.getName().equals("getMetaData")) {
                return Proxy.newProxyInstance(
                    DatabaseMetaData.class.getClassLoader(),
                    new Class<?>[] {DatabaseMetaData.class},
                    (p, m, a) ->
                        m.getName().equals("supportsBatchUpdates") ? batch : invoke(m, value, a));
              }
              if (method.getName().equals("prepareStatement")) {
                return Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    (p, m, a) -> {
                      switch (m.getName()) {
                        case "addBatch":
                          counts.pending++;
                          break;
                        case "executeBatch":
                          counts.sizes.add(counts.pending);
                          break;
                        case "clearBatch":
                          counts.pending = 0;
                          break;
                        case "executeUpdate":
                          counts.updates++;
                          break;
                        case "close":
                          counts.closed++;
                          break;
                        default:
                          break;
                      }
                      return invoke(m, value, a);
                    });
              }
              return value;
            });
  }

  private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException error) {
      throw error.getCause();
    }
  }

  private static final class BatchCounts {
    final List<Integer> sizes = new ArrayList<>();
    int pending;
    int updates;
    int closed;
  }
}
