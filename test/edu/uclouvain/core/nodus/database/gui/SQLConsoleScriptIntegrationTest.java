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

package edu.uclouvain.core.nodus.database.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.ExportFileFaults;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Real headless console execution must keep literal data and validate scripts before side effects.
 */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_OUT")
@ResourceLock("SYSTEM_ERR")
@Timeout(30)
class SQLConsoleScriptIntegrationTest {
  @TempDir Path directory;

  @Test
  void nativeH2DollarStringsKeepQuotesCommentsAndSemicolonsAsData() throws Exception {
    try (DatabaseFixture f = new DatabaseFixture(directory);
        Console console = new Console(f.project)) {
      f.execute("CREATE TABLE data(id INTEGER,label VARCHAR(80))");
      assertTrue(
          ExportFileFaults.quiet(
              () ->
                  console.direct(
                      "INSERT INTO data VALUES(1,$$it's; -- # /*kept*/$$);"
                          + " INSERT INTO data VALUES(2,'after')")));
      assertEquals(List.of(List.of("1", "it's; -- # /*kept*/"), List.of("2", "after")), rows(f));
    }
  }

  @TestFactory
  List<DynamicTest> everyEntryPointKeepsLiteralValuesAndQuotedIdentifiers() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (Entry entry : Entry.values()) {
        tests.add(
            dynamicTest(
                hsql + "/" + entry,
                () -> {
                  try (DatabaseFixture f = new DatabaseFixture(directory, hsql);
                      Console console = new Console(f.project)) {
                    final String table = "\"table;--#/*\"\"quoted\"";
                    final List<String> values =
                        List.of(
                            "semi; -- # /* literal */",
                            "it's; -- /* # */",
                            "first\r\n-- inside literal;\r# inside literal\n"
                                + "/* inside literal */last",
                            "C:\\temp\\",
                            "",
                            "STOP; DELETE FROM anything;",
                            "été 東京");
                    final List<String> commands = new ArrayList<>();
                    commands.add(
                        "CREATE\r\nTABLE "
                            + table
                            + " (\"id;--#\" INTEGER,\"label;/*\" VARCHAR(300))");
                    for (int row = 0; row < values.size(); row++) {
                      commands.add(
                          "INSERT INTO "
                              + table
                              + " VALUES("
                              + (row + 1)
                              + ", '"
                              + values.get(row).replace("'", "''")
                              + "')");
                    }
                    assertTrue(
                        ExportFileFaults.quiet(() -> entry.execute(console, commands, directory)));
                    try (Statement statement = f.connection.createStatement();
                        ResultSet rows =
                            statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                      for (int row = 0; row < values.size(); row++) {
                        assertTrue(rows.next());
                        assertEquals(row + 1, rows.getInt(1));
                        assertEquals(values.get(row), rows.getString(2));
                      }
                      assertFalse(rows.next());
                    }
                    assertFalse(f.connection.isClosed());
                    assertSame(f.connection, JDBCUtils.getConnection());
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> variablesAndStopCommandsStillWorkWithTrailingComments() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (Entry entry : Entry.values()) {
        tests.add(
            dynamicTest(
                hsql + "/" + entry,
                () -> {
                  try (DatabaseFixture f = new DatabaseFixture(directory, hsql);
                      Console console = new Console(f.project)) {
                    f.execute("CREATE TABLE data(id INTEGER,label VARCHAR(80))");
                    final List<String> commands =
                        List.of(
                            "@@id := 7",
                            "@@idLong := 8",
                            "@@label:='it''s;--/*#*/'",
                            "INSERT INTO data VALUES(@@id,@@label)",
                            "INSERT INTO data VALUES(@@idLong,'STOP; DELETE FROM data;')",
                            "STOP",
                            "DELETE FROM data");
                    assertTrue(
                        ExportFileFaults.quiet(() -> entry.execute(console, commands, directory)));
                    assertEquals(
                        List.of(
                            List.of("7", "it's;--/*#*/"), List.of("8", "STOP; DELETE FROM data;")),
                        rows(f));
                    assertFalse(f.connection.isClosed());
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> malformedScriptsExecuteNothingAndPreservePendingTransactionsForRetry() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (Entry entry : Entry.values()) {
        for (String invalid :
            List.of(
                "SELECT 'unfinished",
                "SELECT \"unfinished",
                "SELECT `unfinished",
                "SELECT $$unfinished",
                "SELECT $tag$unfinished",
                "/*unfinished",
                "/*outer /*nested*/ unfinished")) {
          tests.add(
              dynamicTest(
                  hsql + "/" + entry + "/" + invalid,
                  () -> {
                    try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                      f.execute("CREATE TABLE data(id INTEGER,label VARCHAR(80))");
                      f.execute("INSERT INTO data VALUES(1,'original')");
                      f.connection.setAutoCommit(false);
                      final Savepoint earlier = f.connection.setSavepoint();
                      f.execute("INSERT INTO data VALUES(2,'pending')");
                      final TrackedJdbc tracked = new TrackedJdbc(f.connection);
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
                      try (Console console = new Console(project)) {
                        assertFalse(
                            ExportFileFaults.quiet(
                                () ->
                                    entry.execute(
                                        console, List.of("DELETE FROM data", invalid), directory)));
                        assertEquals(0, tracked.executions);
                        assertEquals(tracked.opened, tracked.closed);
                        assertEquals(
                            List.of(List.of("1", "original"), List.of("2", "pending")), rows(f));
                        assertFalse(f.connection.getAutoCommit());
                        assertFalse(f.connection.isClosed());
                        assertSame(f.connection, JDBCUtils.getConnection());
                        f.connection.rollback(earlier);
                        assertEquals(List.of(List.of("1", "original")), rows(f));
                        assertTrue(
                            ExportFileFaults.quiet(
                                () ->
                                    entry.execute(
                                        console,
                                        List.of("INSERT INTO data VALUES(3,'retry;--#/*kept*/')"),
                                        directory)));
                        assertEquals(
                            List.of(List.of("1", "original"), List.of("3", "retry;--#/*kept*/")),
                            rows(f));
                        assertEquals(1, tracked.executions);
                      }
                      assertEquals(tracked.opened, tracked.closed);
                    }
                  }));
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> commentsAndEmptyBatchesDoNotCommitEarlierWork() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "HSQLDB=" + hsql,
              () -> {
                try (DatabaseFixture f = new DatabaseFixture(directory, hsql);
                    Console console = new Console(f.project)) {
                  f.execute("CREATE TABLE data(id INTEGER,label VARCHAR(80))");
                  f.connection.setAutoCommit(false);
                  final Savepoint earlier = f.connection.setSavepoint();
                  f.execute("INSERT INTO data VALUES(1,'pending')");
                  for (String text : List.of("", "; ; --ignored\n;", "/* ' ; */ # \" ;\n-- ;")) {
                    assertTrue(ExportFileFaults.quiet(() -> console.direct(text)));
                  }
                  assertTrue(
                      ExportFileFaults.quiet(
                          () ->
                              console.runBatch(
                                  new String[] {"--comment", "#comment", "/*comment*/"})));
                  assertTrue(ExportFileFaults.quiet(() -> console.runBatch(new String[0])));
                  assertFalse(f.connection.getAutoCommit());
                  f.connection.rollback(earlier);
                  assertTrue(rows(f).isEmpty());
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> demoSqlBatchesRemainExecutableWithTheirExistingLayouts() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "HSQLDB=" + hsql,
              () -> {
                try (DatabaseFixture f = new DatabaseFixture(directory, hsql);
                    Console console = new Console(f.project)) {
                  for (String table : List.of("od_road", "od_iww", "od_rail")) {
                    f.execute(
                        "CREATE TABLE "
                            + table
                            + "(grp INTEGER,org INTEGER,dst INTEGER,qty DOUBLE)");
                    f.execute("INSERT INTO " + table + " VALUES(1,2,3,4)");
                  }
                  console.loadScript(Path.of("demo/MergeODs.sql").toFile());
                  assertTrue(ExportFileFaults.quiet(() -> console.execute(false)));
                  try (Statement statement = f.connection.createStatement();
                      ResultSet rows = statement.executeQuery("SELECT * FROM od")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                    assertEquals(2, rows.getInt(2));
                    assertEquals(3, rows.getInt(3));
                    assertEquals(12, rows.getDouble(4));
                    assertFalse(rows.next());
                  }
                }
              }));
    }
    return tests;
  }

  private static List<List<String>> rows(DatabaseFixture f) throws Exception {
    final List<List<String>> rows = new ArrayList<>();
    try (Statement statement = f.connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT * FROM data ORDER BY id")) {
      while (result.next()) {
        rows.add(List.of(result.getString(1), result.getString(2)));
      }
    }
    return rows;
  }

  private enum Entry {
    DIRECT,
    LOADED,
    RUN_BATCH;

    boolean execute(Console console, List<String> commands, Path directory) throws Exception {
      if (this == RUN_BATCH) {
        final List<String> elements = new ArrayList<>();
        elements.add("--ignored ; '");
        elements.add("#ignored /*");
        for (String command : commands) {
          elements.add(command + " -- trailing comment;");
        }
        return console.runBatch(elements.toArray(new String[0]));
      }
      // Adjacent commands on one physical line; the final command intentionally has no delimiter.
      final String text = String.join("; ", commands);
      if (this == LOADED) {
        final Path file = directory.resolve("script.sql");
        Files.writeString(file, text, Charset.defaultCharset());
        console.loadScript(file.toFile());
        return console.execute(false);
      }
      return console.direct(text);
    }
  }

  private static final class Console extends SQLConsole implements AutoCloseable {
    Console(NodusProject project) {
      super(project, false);
    }

    boolean direct(String text) {
      getSqlCommandArea().setText(text);
      return execute(false);
    }

    @Override
    public void close() {
      windowClosing(null);
    }
  }

  private static final class TrackedJdbc {
    final Connection connection;
    int opened;
    int closed;
    int executions;

    TrackedJdbc(Connection real) {
      connection =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (ignored, method, args) -> {
                    final Object value = invoke(real, method, args);
                    if (!method.getName().equals("createStatement")) {
                      return value;
                    }
                    opened++;
                    return Proxy.newProxyInstance(
                        Statement.class.getClassLoader(),
                        new Class<?>[] {Statement.class},
                        (ignoredStatement, statementMethod, statementArgs) -> {
                          final Object result = invoke(value, statementMethod, statementArgs);
                          if (statementMethod.getName().equals("close")) {
                            closed++;
                          } else if (statementMethod.getName().equals("execute")) {
                            executions++;
                          }
                          return result;
                        });
                  });
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
