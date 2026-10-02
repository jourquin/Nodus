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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import edu.uclouvain.core.nodus.database.xls.ImportXLS;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_ERR")
@ResourceLock("SYSTEM_OUT")
@Timeout(40)
class TableImportTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> invalidInputPreservesOriginalAndEarlierTransaction() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean transaction : new boolean[] {false, true}) {
        for (String kind :
            List.of(
                "dbf",
                "xls-schema",
                "xlsx-schema",
                "xls-data",
                "xlsx-data",
                "xls-no-schema",
                "xlsx-no-schema")) {
          tests.add(
              DynamicTest.dynamicTest(
                  hsql + "/" + transaction + "/" + kind,
                  () -> {
                    try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                      prepare(f);
                      if (kind.equals("dbf")) {
                        assertTrue(ExportDBF.exportTable(f.project, "original"));
                        Path file = directory.resolve("original.dbf");
                        byte[] bytes = Files.readAllBytes(file);
                        System.arraycopy(bytes, 32, bytes, 64, 11);
                        Files.write(file, bytes);
                      } else if (kind.endsWith("no-schema")) {
                        writeExcelWithoutSchema(kind.startsWith("xlsx"));
                      } else {
                        writeExcel(kind.startsWith("xlsx"), kind.endsWith("schema"), true);
                      }
                      f.connection.setAutoCommit(!transaction);
                      f.execute("INSERT INTO earlier VALUES (99)");
                      PrintStream error = System.err;
                      PrintStream output = System.out;
                      ByteArrayOutputStream messages = new ByteArrayOutputStream();
                      try (PrintStream capture = new PrintStream(messages)) {
                        System.setErr(capture);
                        System.setOut(capture);
                        assertFalse(
                            kind.equals("dbf")
                                ? ImportDBF.importTable(f.project, "original")
                                : ImportXLS.importTable(
                                    f.project, "original", kind.startsWith("xlsx")));
                      } finally {
                        System.setErr(error);
                        System.setOut(output);
                      }
                      assertTrue(messages.size() > 0);
                      assertEquals(
                          List.of("17:23"), rows(f.connection, "SELECT a,b FROM original"));
                      assertEquals(!transaction, f.connection.getAutoCommit());
                      assertEquals(1, count(f.connection, "earlier"));
                      if (transaction) {
                        f.connection.rollback();
                        assertEquals(0, count(f.connection, "earlier"));
                      }
                      assertNoStagingTables(f.connection);
                    }
                  }));
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> successfulImportInstallsNewSchemaAndLeavesEarlierWorkUncommitted() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          DynamicTest.dynamicTest(
              "schema replacement " + hsql,
              () -> {
                try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                  prepare(f);
                  writeExcel(false, false, false);
                  f.connection.setAutoCommit(false);
                  f.execute("INSERT INTO earlier VALUES (99)");
                  assertTrue(ImportXLS.importTable(f.project, "original", false));
                  assertEquals(4, count(f.connection, "original"));
                  try (Statement s = f.connection.createStatement();
                      ResultSet r = s.executeQuery("SELECT label FROM original")) {
                    assertTrue(r.next());
                    assertEquals("new", r.getString(1));
                  }
                  f.connection.rollback();
                  assertEquals(0, count(f.connection, "earlier"));
                  assertNoStagingTables(f.connection);
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> installationFailureRestoresOriginalTableAndCleansStage() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (String point : List.of("install", "drop backup")) {
        tests.add(
            DynamicTest.dynamicTest(
                hsql + "/" + point,
                () -> {
                  try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                    prepare(f);
                    boolean[] injected = {false};
                    Connection failing =
                        (Connection)
                            java.lang.reflect.Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                  try {
                                    Object result = method.invoke(f.connection, args);
                                    if (method.getName().equals("createStatement")) {
                                      Statement delegate = (Statement) result;
                                      return java.lang.reflect.Proxy.newProxyInstance(
                                          Statement.class.getClassLoader(),
                                          new Class<?>[] {Statement.class},
                                          (p, m, a) -> {
                                            if (m.getName().equals("executeUpdate")
                                                && a[0] instanceof String) {
                                              String sql = (String) a[0];
                                              boolean trigger =
                                                  point.equals("install")
                                                      ? sql.startsWith("ALTER TABLE \"NDI_")
                                                          && sql.contains("RENAME TO")
                                                      : sql.startsWith("DROP TABLE \"NDB_");
                                              if (trigger && !injected[0]) {
                                                injected[0] = true;
                                                throw new java.sql.SQLException(
                                                    "Injected installation failure");
                                              }
                                            }
                                            try {
                                              return m.invoke(delegate, a);
                                            } catch (InvocationTargetException e) {
                                              throw e.getCause();
                                            }
                                          });
                                    }
                                    return result;
                                  } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                  }
                                });
                    assertThrows(
                        java.sql.SQLException.class,
                        () ->
                            TableImport.replace(
                                failing,
                                "original",
                                (c, staged) -> {
                                  try (Statement statement = c.createStatement()) {
                                    statement.execute(
                                        "CREATE TABLE " + staged + " (new_column INTEGER)");
                                    statement.execute("INSERT INTO " + staged + " VALUES (42)");
                                  }
                                },
                                true));
                    assertTrue(injected[0]);
                    assertEquals(List.of("17:23"), rows(f.connection, "SELECT a,b FROM original"));
                    assertNoStagingTables(f.connection);
                  }
                }));
      }
    }
    return tests;
  }

  private void writeExcel(boolean xlsx, boolean duplicate, boolean badData) throws Exception {
    try (Workbook book = xlsx ? new XSSFWorkbook() : new HSSFWorkbook()) {
      var sheet = book.createSheet();
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("label,C,10");
      header.createCell(1).setCellValue(duplicate ? "label,C,10" : "value,N,6,0");
      for (int i = 1; i <= 4; i++) {
        var row = sheet.createRow(i);
        row.createCell(0).setCellValue("new");
        if (badData && i == 4) {
          row.createCell(1).setCellValue("invalid numeric cell");
        } else {
          row.createCell(1).setCellValue(i);
        }
      }
      try (var out =
          Files.newOutputStream(directory.resolve("original." + (xlsx ? "xlsx" : "xls")))) {
        book.write(out);
      }
    }
  }

  @TestFactory
  List<DynamicTest> transactionalDdlRespectsCallerTransaction() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean autoCommit : new boolean[] {false, true}) {
      for (boolean fail : new boolean[] {false, true}) {
        tests.add(
            DynamicTest.dynamicTest(
                "SQLite/" + autoCommit + "/" + fail,
                () -> {
                  try (Connection connection =
                      java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
                    assertTrue(JDBCUtils.setConnection(connection));
                    var project =
                        new edu.uclouvain.core.nodus.NodusProject(null) {
                          @Override
                          public Connection getMainJDBCConnection() {
                            return connection;
                          }
                        };
                    try (Statement s = connection.createStatement()) {
                      s.execute("CREATE TABLE original (a INTEGER, b INTEGER)");
                      s.execute("INSERT INTO original VALUES (17,23)");
                      s.execute("CREATE TABLE earlier (id INTEGER)");
                      connection.setAutoCommit(autoCommit);
                      s.execute("INSERT INTO earlier VALUES (99)");
                    }
                    TableImport.Loader loader =
                        (c, staged) -> {
                          try (Statement s = c.createStatement()) {
                            s.execute("CREATE TABLE " + staged + " (a INTEGER, b INTEGER)");
                            s.execute("INSERT INTO " + staged + " VALUES (42,43)");
                          }
                          if (fail) {
                            throw new java.sql.SQLException("Invalid input");
                          }
                        };
                    if (fail) {
                      assertThrows(
                          java.sql.SQLException.class,
                          () -> TableImport.replace(project, "original", loader));
                    } else {
                      TableImport.replace(project, "original", loader);
                    }
                    assertEquals(
                        List.of(fail ? "17:23" : "42:43"),
                        rows(connection, "SELECT a,b FROM original"));
                    assertEquals(autoCommit, connection.getAutoCommit());
                    assertEquals(1, count(connection, "earlier"));
                    if (!autoCommit) {
                      connection.rollback();
                      assertEquals(List.of("17:23"), rows(connection, "SELECT a,b FROM original"));
                      assertEquals(0, count(connection, "earlier"));
                    }
                    assertNoStagingTables(connection);
                  } finally {
                    JDBCUtils.setConnection(null);
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> failedNewTableImportLeavesNoPartialTable() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          DynamicTest.dynamicTest(
              "new table/" + hsql,
              () -> {
                try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                  assertThrows(
                      java.sql.SQLException.class,
                      () ->
                          TableImport.replace(
                              f.project,
                              "original",
                              (c, staged) -> {
                                try (Statement s = c.createStatement()) {
                                  s.execute("CREATE TABLE " + staged + " (a INTEGER)");
                                  s.execute("INSERT INTO " + staged + " VALUES (42)");
                                }
                                throw new java.sql.SQLException("Invalid input after partial load");
                              }));
                  assertFalse(JDBCUtils.tableExists("original"));
                  assertNoStagingTables(f.connection);
                }
              }));
    }
    return tests;
  }

  @Test
  void failedRollbackDoesNotImplicitlyCommitFailedImport() throws Exception {
    try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
      assertTrue(JDBCUtils.setConnection(connection));
      Connection failing =
          (Connection)
              java.lang.reflect.Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("rollback")) {
                      throw new java.sql.SQLException("Injected rollback failure");
                    }
                    try {
                      return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                      throw e.getCause();
                    }
                  });
      java.sql.SQLException failure =
          assertThrows(
              java.sql.SQLException.class,
              () ->
                  TableImport.replace(
                      failing,
                      "original",
                      (c, staged) -> {
                        try (Statement statement = c.createStatement()) {
                          statement.execute("CREATE TABLE " + staged + " (a INTEGER)");
                          statement.execute("INSERT INTO " + staged + " VALUES (42)");
                        }
                        throw new java.sql.SQLException("Invalid input");
                      },
                      false));
      assertEquals("Invalid input", failure.getMessage());
      assertEquals(1, failure.getSuppressed().length);
      assertFalse(
          connection.getAutoCommit(), "Do not commit the failed import after rollback fails");
      connection.rollback();
      assertNoStagingTables(connection);
    } finally {
      JDBCUtils.setConnection(null);
    }
  }

  private void writeExcelWithoutSchema(boolean xlsx) throws Exception {
    try (Workbook book = xlsx ? new XSSFWorkbook() : new HSSFWorkbook()) {
      var sheet = book.createSheet();
      for (int i = 0; i < 4; i++) {
        var row = sheet.createRow(i);
        row.createCell(0).setCellValue(i);
        if (i == 3) {
          row.createCell(1).setCellValue("invalid numeric cell");
        } else {
          row.createCell(1).setCellValue(i);
        }
      }
      try (var out =
          Files.newOutputStream(directory.resolve("original." + (xlsx ? "xlsx" : "xls")))) {
        book.write(out);
      }
    }
  }

  private static void prepare(DatabaseFixture f) throws Exception {
    f.execute("CREATE TABLE original (a NUMERIC(6), b NUMERIC(6))");
    f.execute("INSERT INTO original VALUES (17,23)");
    f.execute("CREATE TABLE earlier (id INTEGER)");
  }

  private static List<String> rows(Connection c, String sql) throws Exception {
    List<String> values = new ArrayList<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery(sql)) {
      while (r.next()) {
        values.add(r.getString(1) + ":" + r.getString(2));
      }
    }
    return values;
  }

  private static int count(Connection c, String table) throws Exception {
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
      r.next();
      return r.getInt(1);
    }
  }

  private static void assertNoStagingTables(Connection c) throws Exception {
    try (ResultSet tables =
        c.getMetaData().getTables(null, c.getSchema(), null, new String[] {"TABLE"})) {
      while (tables.next()) {
        String name = tables.getString("TABLE_NAME");
        assertFalse(name.startsWith("NDI_") || name.startsWith("NDB_"), name);
      }
    }
  }
}
