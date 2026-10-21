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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.csv.ExportCSV;
import edu.uclouvain.core.nodus.database.xls.ExportXLS;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Late JDBC errors through real exporters cannot replace old files or publish partial new files.
 */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_OUT")
@ResourceLock("SYSTEM_ERR")
@ResourceLock("POI_TEMP_FILES")
@Timeout(60)
class TableExportIntegrationTest {
  private static final byte[] ORIGINAL =
      "previous export\r\nété 東京".getBytes(StandardCharsets.UTF_8);
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> jdbcFailuresPreserveFilesTransactionsAndResourcesAndAllowRetry() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean existing : new boolean[] {false, true}) {
        for (Format format : Format.values()) {
          for (String phase :
              List.of(
                  "statement",
                  "query",
                  "metadata",
                  "row",
                  "cell",
                  "result close",
                  "statement close",
                  "metadata close")) {
            if (phase.equals("metadata close") && format.csv) {
              continue;
            }
            tests.add(
                dynamicTest(
                    hsql + "/existing=" + existing + "/" + format + "/" + phase,
                    () -> checkFailure(hsql, existing, format, phase)));
          }
        }
      }
    }
    return tests;
  }

  private void checkFailure(boolean hsql, boolean existing, Format format, String phase)
      throws Exception {
    final Path folder = Files.createTempDirectory(directory, "export-");
    try (DatabaseFixture f = new DatabaseFixture(folder, hsql)) {
      f.execute("CREATE TABLE data(id INTEGER,label VARCHAR(300))");
      f.execute("CREATE TABLE earlier(marker INTEGER)");
      try (PreparedStatement insert =
          f.connection.prepareStatement("INSERT INTO data VALUES(?,?)")) {
        for (int row = 1; row <= 300; row++) {
          insert.setInt(1, row);
          insert.setString(2, row == 2 ? null : label(row));
          insert.addBatch();
        }
        insert.executeBatch();
      }
      final boolean auto = !existing;
      f.connection.setAutoCommit(auto);
      final Savepoint earlier = auto ? null : f.connection.setSavepoint();
      f.execute("INSERT INTO earlier VALUES(42)");
      final TrackedJdbc tracked = new TrackedJdbc(f.connection);
      assertTrue(JDBCUtils.setConnection(tracked.connection));
      final NodusProject project =
          new NodusProject(null) {
            @Override
            public Connection getMainJDBCConnection() {
              return tracked.connection;
            }

            @Override
            public String getLocalProperty(String key) {
              return f.project.getLocalProperty(key);
            }
          };
      final Path target = folder.resolve("data" + format.extension);
      if (existing) {
        Files.write(target, ORIGINAL);
      }
      final Path spill = Files.createDirectory(folder.resolve("poi"));
      TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy(spill.toFile()));
      try {
        tracked.phase = phase;
        assertFalse(ExportFileFaults.quiet(() -> format.export(project)));
        if (existing) {
          assertArrayEquals(ORIGINAL, Files.readAllBytes(target));
        } else {
          assertFalse(Files.exists(target));
        }
        assertEquals(existing ? List.of(target) : List.of(), ExportFileFaults.regularFiles(folder));
        assertEquals(tracked.statements, tracked.closedStatements);
        assertEquals(tracked.results, tracked.closedResults);
        assertEquals(0, tracked.transactionChanges);
        assertSame(tracked.connection, JDBCUtils.getConnection());
        assertFalse(f.connection.isClosed());
        assertEquals(auto, f.connection.getAutoCommit());
        assertMarker(f.connection, 1);
        tracked.phase = null;
        assertTrue(format.export(project));
        format.assertContents(target);
        assertEquals(List.of(target), ExportFileFaults.regularFiles(folder));
        assertEquals(tracked.statements, tracked.closedStatements);
        assertEquals(tracked.results, tracked.closedResults);
        assertEquals(0, tracked.transactionChanges);
        assertMarker(f.connection, 1);
        if (!auto) {
          f.connection.rollback(earlier);
          assertMarker(f.connection, 0);
        }
        try (Statement statement = f.connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT COUNT(*),SUM(id) FROM data")) {
          assertTrue(rows.next());
          assertEquals(300, rows.getInt(1));
          assertEquals(45150, rows.getInt(2));
        }
      } finally {
        TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy());
        if (!auto) {
          f.connection.rollback();
        }
      }
    }
  }

  private static void assertMarker(Connection connection, int count) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM earlier")) {
      assertTrue(rows.next());
      assertEquals(count, rows.getInt(1));
    }
  }

  private static String label(int row) {
    return "row " + row + ", \"été\"\n東京";
  }

  private enum Format {
    CSV(".csv", true),
    CSVH(".csv", true),
    XLS(".xls", false),
    XLSX(".xlsx", false);
    final String extension;
    final boolean csv;

    Format(String extension, boolean csv) {
      this.extension = extension;
      this.csv = csv;
    }

    boolean export(NodusProject project) {
      return csv
          ? ExportCSV.exportTable(project, "data", this == CSVH)
          : ExportXLS.exportTable(project, "data", this == XLSX);
    }

    void assertContents(Path file) throws Exception {
      if (csv) {
        final String newline = System.lineSeparator();
        final StringBuilder expected = new StringBuilder(this == CSVH ? "ID,LABEL" + newline : "");
        for (int row = 1; row <= 300; row++) {
          expected.append(row).append(',');
          if (row != 2) {
            expected.append('"').append("row ").append(row).append(", \"\"été\"\"\n東京\"");
          }
          expected.append(newline);
        }
        assertEquals(expected.toString(), Files.readString(file, Charset.defaultCharset()));
      } else {
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
          final var sheet = workbook.getSheetAt(0);
          assertEquals(301, sheet.getPhysicalNumberOfRows());
          for (int row = 1; row <= 300; row++) {
            assertEquals(row, sheet.getRow(row).getCell(0).getNumericCellValue());
            if (row == 2) {
              assertEquals(CellType.BLANK, sheet.getRow(row).getCell(1).getCellType());
            } else {
              assertEquals(label(row), sheet.getRow(row).getCell(1).getStringCellValue());
            }
          }
        }
      }
    }
  }

  private static final class TrackedJdbc {
    final Connection connection;
    String phase;
    int statements;
    int closedStatements;
    int results;
    int closedResults;
    int transactionChanges;

    TrackedJdbc(Connection real) {
      connection =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (ignored, method, args) -> {
                    final String name = method.getName();
                    if (List.of("setAutoCommit", "commit", "rollback", "close").contains(name)) {
                      transactionChanges++;
                    }
                    if (name.equals("createStatement") && "statement".equals(phase)) {
                      throw failure();
                    }
                    final Object result = invoke(real, method, args);
                    if (name.equals("getMetaData")) {
                      return Proxy.newProxyInstance(
                          DatabaseMetaData.class.getClassLoader(),
                          new Class<?>[] {DatabaseMetaData.class},
                          (ignoredMeta, metaMethod, metaArgs) -> {
                            final Object value = invoke(result, metaMethod, metaArgs);
                            return metaMethod.getName().equals("getColumns")
                                ? rows((ResultSet) value, true)
                                : value;
                          });
                    }
                    if (name.equals("createStatement")) {
                      return statement((Statement) result);
                    }
                    return result;
                  });
    }

    private Statement statement(Statement real) {
      statements++;
      return (Statement)
          Proxy.newProxyInstance(
              Statement.class.getClassLoader(),
              new Class<?>[] {Statement.class},
              (ignored, method, args) -> {
                final String name = method.getName();
                if (name.equals("executeQuery") && "query".equals(phase)) {
                  throw failure();
                }
                final Object value = invoke(real, method, args);
                if (name.equals("close")) {
                  closedStatements++;
                  if ("statement close".equals(phase)) {
                    throw failure();
                  }
                }
                return name.equals("executeQuery") ? rows((ResultSet) value, false) : value;
              });
    }

    private ResultSet rows(ResultSet real, boolean metadata) {
      results++;
      final int[] row = {0};
      return (ResultSet)
          Proxy.newProxyInstance(
              ResultSet.class.getClassLoader(),
              new Class<?>[] {ResultSet.class},
              (ignored, method, args) -> {
                final String name = method.getName();
                final Object value = invoke(real, method, args);
                if (name.equals("next") && Boolean.TRUE.equals(value)) {
                  row[0]++;
                  if (metadata && row[0] == 2 && "metadata".equals(phase)
                      || !metadata && row[0] == 250 && "row".equals(phase)) {
                    throw failure();
                  }
                }
                if (!metadata
                    && row[0] == 250
                    && (name.equals("getObject") || name.equals("getString"))
                    && Integer.valueOf(2).equals(args[0])
                    && "cell".equals(phase)) {
                  throw failure();
                }
                if (name.equals("close")) {
                  closedResults++;
                  if ((metadata ? "metadata close" : "result close").equals(phase)) {
                    throw failure();
                  }
                }
                if (name.equals("getMetaData")) {
                  return Proxy.newProxyInstance(
                      ResultSetMetaData.class.getClassLoader(),
                      new Class<?>[] {ResultSetMetaData.class},
                      (ignoredMeta, metaMethod, metaArgs) -> {
                        final Object result = invoke(value, metaMethod, metaArgs);
                        if (metaMethod.getName().equals("getColumnCount")
                            && "metadata".equals(phase)) {
                          throw failure();
                        }
                        return result;
                      });
                }
                return value;
              });
    }

    private SQLException failure() {
      return new SQLException("Injected export " + phase + " failure");
    }

    private static Object invoke(Object real, Method method, Object[] args) throws Throwable {
      try {
        return method.invoke(real, args);
      } catch (InvocationTargetException failure) {
        throw failure.getCause();
      }
    }
  }
}
