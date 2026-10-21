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

package edu.uclouvain.core.nodus.compute.rules.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real H2/HSQLDB persistence through the editor's backend, with injected JDBC failures. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class NodeRulesPersistenceTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> failedReplacementsPreserveRulesPendingEditsAndEarlierWork() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean auto : new boolean[] {false, true}) {
        for (String phase :
            List.of(
                "prepare delete",
                "delete",
                "prepare insert",
                "insert",
                "insert close",
                "delete close",
                "savepoint",
                "release",
                "commit",
                "runtime insert",
                "insert+restore",
                "rollback")) {
          if (auto && phase.equals("release")
              || !auto
                  && (phase.equals("commit")
                      || phase.equals("insert+restore")
                      || phase.equals("rollback"))) {
            continue;
          }
          tests.add(
              dynamicTest(
                  hsql + "/auto=" + auto + "/" + phase, () -> checkFailure(hsql, auto, phase)));
        }
      }
    }
    return tests;
  }

  private void checkFailure(boolean hsql, boolean auto, String phase) throws Exception {
    try (Fixture f = new Fixture(hsql, true, "rules")) {
      final List<List<String>> original = f.rules();
      final DefaultTableModel edited = replacements();
      final List<List<Object>> pending = NodeRulesPersistence.snapshot(edited);
      final Savepoint earlier = f.pending(auto);
      f.faults.phase = phase;
      final Exception failure =
          assertThrows(
              Exception.class, () -> NodeRulesPersistence.save(f.project, f.table, 2, 7, edited));
      assertEquals(
          phase.equals("runtime insert") ? IllegalStateException.class : SQLException.class,
          failure.getClass());
      assertTrue(failure.getMessage().startsWith("Injected "), failure.toString());
      if (phase.equals("insert+restore") || phase.equals("rollback")) {
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("Injected insert", failure.getMessage());
      }
      assertEquals(original, f.rules());
      assertEquals(pending, NodeRulesPersistence.snapshot(edited));
      assertEquals(List.of(List.of("42")), f.rows("SELECT * FROM unrelated"));
      assertEquals(phase.equals("rollback") ? false : auto, f.connection.getAutoCommit());
      assertFalse(f.connection.isClosed());
      assertSame(f.connection, JDBCUtils.getConnection());
      f.faults.assertClosed();
      f.faults.phase = null;
      if (!auto) {
        f.connection.rollback(earlier);
        assertTrue(f.rows("SELECT * FROM unrelated").isEmpty());
      } else if (!f.connection.getAutoCommit()) {
        f.connection.rollback();
        f.connection.setAutoCommit(true);
      }
      assertFalse(NodeRulesPersistence.save(f.project, f.table, 2, 7, edited));
      assertEquals(2, f.editedRows().size());
      assertEquals(f.unrelated, f.otherRules());
      assertEquals(pending, NodeRulesPersistence.snapshot(edited));
      f.faults.assertClosed();
      if (!auto) {
        f.connection.rollback();
        assertEquals(original, f.rules(), "A successful edit belongs to the caller's transaction");
      }
    }
  }

  @TestFactory
  List<DynamicTest> successfulEditsPreserveOtherScopesAndTheCallersTransaction() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean auto : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                hsql + "/auto=" + auto,
                () -> {
                  try (Fixture f = new Fixture(hsql, true, "rules")) {
                    final List<List<String>> original = f.rules();
                    final Savepoint earlier = f.pending(auto);
                    assertFalse(
                        NodeRulesPersistence.save(f.project, f.table, 2, 7, replacements()));
                    assertEquals(2, f.editedRows().size());
                    assertEquals(f.unrelated, f.otherRules());
                    assertEquals(auto, f.connection.getAutoCommit());
                    if (!auto) {
                      assertEquals(0, f.faults.commits);
                      f.connection.rollback(earlier);
                      assertTrue(f.rows("SELECT * FROM unrelated").isEmpty());
                      assertEquals(original, f.rules());
                    } else {
                      assertEquals(1, f.faults.commits);
                    }
                    f.faults.assertClosed();
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> distinctTuplesSurviveConcatenationAndHashCollisions() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "HSQLDB=" + hsql,
              () -> {
                try (Fixture f = new Fixture(hsql, true, "rules")) {
                  final DefaultTableModel model = NodeRulesComparisonTest.model();
                  model.addRow(rule(-2, 7, 3, 1, 11, 2, 1, false));
                  model.addRow(rule(-2, 7, 3, 11, 1, 2, 1, false));
                  final List<Integer> firstHash = List.of(-2, 7, 3, 1, 32, 3, 1);
                  final List<Integer> secondHash = List.of(-2, 7, 3, 2, 1, 3, 1);
                  assertEquals(firstHash.hashCode(), secondHash.hashCode());
                  assertNotEquals(firstHash, secondHash);
                  model.addRow(rule(-2, 7, 3, 1, 32, 3, 1, false));
                  model.addRow(rule(-2, 7, 3, 2, 1, 3, 1, false));
                  model.addRow(rule(-2, 7, 3, 1, 11, 2, 1, false));
                  final Object[] numericalDuplicate = rule(-2, 7, 3, 1, 11, 2, 1, false);
                  numericalDuplicate[2] = "01";
                  numericalDuplicate[3] = "+11";
                  model.addRow(numericalDuplicate);
                  model.addRow(rule(2, 7, 3, 1, 11, 2, 1, false));
                  model.addRow(rule(-2, -1, 3, 1, 11, 2, 1, false));
                  model.addRow(rule(-2, 7, 4, 1, 11, 2, 1, false));
                  final List<List<Object>> pending = NodeRulesPersistence.snapshot(model);
                  assertTrue(NodeRulesPersistence.save(f.project, f.table, 2, 7, model));
                  assertEquals(7, f.editedRows().size());
                  assertEquals(pending, NodeRulesPersistence.snapshot(model));
                  assertEquals(f.unrelated, f.otherRules());
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> symmetricRulesCoverOnlyExactDirectionsWithTheSameScopeAndPolarity() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "HSQLDB=" + hsql,
              () -> {
                try (Fixture f = new Fixture(hsql, true, "rules")) {
                  final DefaultTableModel model = NodeRulesComparisonTest.model();
                  model.addRow(rule(-2, 7, 3, 1, 1, 2, 1, false));
                  model.addRow(rule(-2, 7, 3, 2, 1, 1, 1, true));
                  model.addRow(rule(-2, 7, 3, 2, 1, 1, 1, false));
                  model.addRow(rule(-2, 7, 3, 1, 1, 2, 1, true));
                  model.addRow(rule(2, 7, 3, 1, 1, 2, 1, false));
                  model.addRow(rule(-2, -1, 3, 1, 1, 2, 1, false));
                  model.addRow(rule(-2, 7, 4, 1, 1, 2, 1, false));
                  model.addRow(rule(-2, 7, 3, 1, -1, 2, 1, false));
                  assertTrue(NodeRulesPersistence.save(f.project, f.table, 2, 7, model));
                  assertEquals(5, f.editedRows().size());
                  assertEquals(
                      List.of(List.of("-2", "7", "3", "2", "1", "1", "1", "1")),
                      f.rows(
                          "SELECT * FROM rules WHERE symetry=1 AND ABS(num)=2"
                              + " AND scenario IN(-1,7)"));
                  assertEquals(f.unrelated, f.otherRules());
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> openingAMissingTableIsReadOnlyAndFirstSavesDoNotCommitEarlierWork() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean fail : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                hsql + "/fail=" + fail,
                () -> {
                  try (Fixture f = new Fixture(hsql, false, "rules")) {
                    final Savepoint earlier = f.pending(false);
                    final int sessions = f.sessions();
                    assertTrue(NodeRulesPersistence.load(f.project, f.table, 2, 7).isEmpty());
                    assertFalse(JDBCUtils.tableExists(f.table));
                    final DefaultTableModel model = replacements();
                    if (fail) {
                      f.faults.phase = "insert";
                      assertThrows(
                          SQLException.class,
                          () -> NodeRulesPersistence.save(f.project, f.table, 2, 7, model));
                      assertTrue(f.rules().isEmpty());
                      f.faults.phase = null;
                    } else {
                      assertFalse(NodeRulesPersistence.save(f.project, f.table, 2, 7, model));
                      assertEquals(2, f.rules().size());
                    }
                    assertEquals(sessions, f.sessions(), "The schema connection must close");
                    assertFalse(f.connection.getAutoCommit());
                    assertEquals(List.of(List.of("42")), f.rows("SELECT * FROM unrelated"));
                    f.connection.rollback(earlier);
                    assertTrue(f.rows("SELECT * FROM unrelated").isEmpty());
                    assertTrue(f.rules().isEmpty());
                    assertFalse(NodeRulesPersistence.save(f.project, f.table, 2, 7, model));
                    f.connection.commit();
                    assertEquals(2, f.rules().size());
                    f.faults.assertClosed();
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> emptyModelsClearOnlyThisNodesGenericAndCurrentScenarioRules() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "HSQLDB=" + hsql,
              () -> {
                try (Fixture f = new Fixture(hsql, true, "rules")) {
                  assertFalse(
                      NodeRulesPersistence.save(
                          f.project, f.table, 2, 7, NodeRulesComparisonTest.model()));
                  assertTrue(f.editedRows().isEmpty());
                  assertEquals(f.unrelated, f.rules());
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> malformedOrOutOfScopePendingRowsCannotDeleteSavedRules() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (String invalid : List.of("node", "scenario", "number", "null", "transit")) {
        tests.add(
            dynamicTest(
                hsql + "/" + invalid,
                () -> {
                  try (Fixture f = new Fixture(hsql, true, "rules")) {
                    final List<List<String>> original = f.rules();
                    final DefaultTableModel model = replacements();
                    if (invalid.equals("node")) {
                      model.setValueAt("3", 1, 7);
                    } else if (invalid.equals("scenario")) {
                      model.setValueAt("8", 1, 0);
                    } else if (invalid.equals("number")) {
                      model.setValueAt("not a number", 1, 2);
                    } else if (invalid.equals("null")) {
                      model.setValueAt(null, 1, 2);
                    } else {
                      model.setValueAt("3", 1, 2);
                    }
                    final List<List<Object>> pending = NodeRulesPersistence.snapshot(model);
                    assertThrows(
                        IllegalArgumentException.class,
                        () -> NodeRulesPersistence.save(f.project, f.table, 2, 7, model));
                    assertEquals(original, f.rules());
                    assertEquals(pending, NodeRulesPersistence.snapshot(model));
                    assertEquals(0, f.faults.createdStatements);
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> readFailuresCloseResourcesAndNeverPublishPartialRows() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (String phase : List.of("row read", "result close", "read close")) {
        tests.add(
            dynamicTest(
                hsql + "/" + phase,
                () -> {
                  try (Fixture f = new Fixture(hsql, true, "rules")) {
                    final Savepoint earlier = f.pending(false);
                    final List<List<String>> original = f.rules();
                    f.faults.phase = phase;
                    assertThrows(
                        SQLException.class,
                        () -> NodeRulesPersistence.load(f.project, f.table, 2, 7));
                    f.faults.assertClosed();
                    assertEquals(original, f.rules());
                    assertFalse(f.connection.getAutoCommit());
                    f.connection.rollback(earlier);
                    assertTrue(f.rows("SELECT * FROM unrelated").isEmpty());
                    f.faults.phase = null;
                    assertEquals(2, NodeRulesPersistence.load(f.project, f.table, 2, 7).size());
                    f.faults.assertClosed();
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> customTablesUseNamedColumnsAndPreserveTheirOtherFields() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "HSQLDB=" + hsql,
              () -> {
                try (Fixture f = new Fixture(hsql, false, "node rules_%")) {
                  f.execute(
                      "CREATE TABLE "
                          + f.quotedTable()
                          + " (extra VARCHAR(20) DEFAULT 'retained', symetry INTEGER,"
                          + " means2 INTEGER,"
                          + " mode2 INTEGER, means1 INTEGER, mode1 INTEGER, grp INTEGER,"
                          + " scenario INTEGER, num NUMERIC(11,0))");
                  f.execute(
                      "INSERT INTO " + f.quotedTable() + " VALUES('other node',0,1,2,1,1,3,7,-3)");
                  final DefaultTableModel model = replacements();
                  assertFalse(NodeRulesPersistence.save(f.project, f.table, 2, 7, model));
                  final List<String[]> loaded = NodeRulesPersistence.load(f.project, f.table, 2, 7);
                  assertEquals(2, loaded.size());
                  for (int row = 0; row < loaded.size(); row++) {
                    assertEquals(
                        NodeRulesPersistence.snapshot(model).get(row), List.of(loaded.get(row)));
                  }
                  assertEquals(
                      List.of(List.of("other node"), List.of("retained"), List.of("retained")),
                      f.rows("SELECT extra FROM " + f.quotedTable() + " ORDER BY extra"));
                  f.faults.assertClosed();
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> firstSaveUsesTheCurrentSchemaWithoutTouchingSameNamedTables() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean auto : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                hsql + "/auto=" + auto,
                () -> {
                  try (Fixture f = new Fixture(hsql, true, "rules")) {
                    final List<List<String>> original = f.rules();
                    f.execute("CREATE SCHEMA \"rule_space\" AUTHORIZATION SA");
                    f.execute("CREATE SCHEMA \"ruleXspace\" AUTHORIZATION SA");
                    f.execute("CREATE TABLE \"ruleXspace\".rules(marker INTEGER)");
                    f.execute("INSERT INTO \"ruleXspace\".rules VALUES(99)");
                    f.connection.setSchema("rule_space");
                    f.connection.setAutoCommit(auto);
                    final Savepoint earlier = auto ? null : f.connection.setSavepoint();
                    assertTrue(NodeRulesPersistence.load(f.project, f.table, 2, 7).isEmpty());
                    final int sessions = f.sessions();
                    assertFalse(
                        NodeRulesPersistence.save(f.project, f.table, 2, 7, replacements()));
                    assertEquals(2, f.rules().size());
                    assertEquals("rule_space", f.connection.getSchema());
                    assertEquals(sessions, f.sessions());
                    assertEquals(auto, f.connection.getAutoCommit());
                    if (!auto) {
                      f.connection.rollback(earlier);
                      assertTrue(f.rules().isEmpty());
                    }
                    assertEquals(
                        original, f.rows("SELECT * FROM PUBLIC.rules ORDER BY 1,2,3,4,5,6,7,8"));
                    assertEquals(
                        List.of(List.of("99")), f.rows("SELECT * FROM \"ruleXspace\".rules"));
                    f.faults.assertClosed();
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> failedFirstSaveConnectionsCannotCommitTheMainTransaction() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "HSQLDB=" + hsql,
              () -> {
                try (Fixture f = new Fixture(hsql, false, "rules")) {
                  final Savepoint earlier = f.pending(false);
                  final int sessions = f.sessions();
                  final NodusProject unavailable =
                      new NodusProject(null) {
                        @Override
                        public Connection getMainJDBCConnection() {
                          return f.faults.proxy;
                        }

                        @Override
                        public String getLocalProperty(String key, String fallback) {
                          return NodusC.PROP_JDBC_URL.equals(key)
                              ? "jdbc:unavailable:nodus"
                              : fallback;
                        }
                      };
                  final DefaultTableModel model = replacements();
                  final List<List<Object>> pending = NodeRulesPersistence.snapshot(model);
                  assertThrows(
                      SQLException.class,
                      () -> NodeRulesPersistence.save(unavailable, f.table, 2, 7, model));
                  assertFalse(JDBCUtils.tableExists(f.table));
                  assertEquals(sessions, f.sessions());
                  assertEquals(pending, NodeRulesPersistence.snapshot(model));
                  assertEquals(List.of(List.of("42")), f.rows("SELECT * FROM unrelated"));
                  assertFalse(f.connection.getAutoCommit());
                  f.connection.rollback(earlier);
                  assertTrue(f.rows("SELECT * FROM unrelated").isEmpty());
                  assertFalse(NodeRulesPersistence.save(f.project, f.table, 2, 7, model));
                  assertEquals(2, f.rules().size());
                  f.faults.assertClosed();
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> nullSymmetryCannotSilentlyChangeTheMeaningOfLoadedRules() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "HSQLDB=" + hsql,
              () -> {
                try (Fixture f = new Fixture(hsql, true, "rules")) {
                  f.execute("UPDATE rules SET symetry=NULL WHERE scenario=7 AND num=2");
                  final List<List<String>> original = f.rules();
                  assertThrows(
                      SQLException.class,
                      () -> NodeRulesPersistence.load(f.project, f.table, 2, 7));
                  assertEquals(original, f.rules());
                  f.faults.assertClosed();
                }
              }));
    }
    return tests;
  }

  private static DefaultTableModel replacements() {
    final DefaultTableModel model = NodeRulesComparisonTest.model();
    model.addRow(rule(2, -1, -1, 0, 0, 1, 1, false));
    model.addRow(rule(-2, 7, 3, 1, 1, 3, 1, true));
    return model;
  }

  private static Object[] rule(
      int node,
      int scenario,
      int group,
      int mode1,
      int means1,
      int mode2,
      int means2,
      boolean symmetric) {
    return new Object[] {
      String.valueOf(scenario),
      String.valueOf(group),
      String.valueOf(mode1),
      String.valueOf(means1),
      String.valueOf(mode2),
      String.valueOf(means2),
      symmetric ? NodeRulesPersistence.DOUBLE_ARROW : NodeRulesPersistence.SINGLE_ARROW,
      String.valueOf(node)
    };
  }

  private final class Fixture implements AutoCloseable {
    final DatabaseFixture database;
    final Connection connection;
    final String table;
    final boolean hsql;
    final Faults faults;
    final NodusProject project;
    final List<List<String>> unrelated;

    Fixture(boolean hsql, boolean existing, String table) throws Exception {
      this.hsql = hsql;
      this.table = table;
      database = new DatabaseFixture(directory, hsql);
      connection = database.connection;
      if (existing) {
        execute(
            "CREATE TABLE "
                + quotedTable()
                + " (num NUMERIC(11,0),scenario INTEGER,grp INTEGER,"
                + "mode1 INTEGER,means1 INTEGER,mode2 INTEGER,means2 INTEGER,symetry INTEGER)");
        execute(
            "INSERT INTO "
                + quotedTable()
                + " VALUES(-2,-1,-1,1,1,2,1,1),(2,7,3,1,1,3,1,0),"
                + "(-2,8,3,1,1,2,1,0),(-3,-1,-1,1,1,2,1,1),(3,7,3,1,1,2,1,0)");
      }
      execute("CREATE TABLE unrelated(marker INTEGER)");
      unrelated = existing ? otherRules() : List.of();
      faults = new Faults(connection);
      project =
          new NodusProject(null) {
            @Override
            public Connection getMainJDBCConnection() {
              return faults.proxy;
            }

            @Override
            public String getLocalProperty(String key, String fallback) {
              return database.project.getLocalProperty(key, fallback);
            }
          };
    }

    String quotedTable() {
      return JDBCUtils.getQuotedCompliantIdentifier(table);
    }

    Savepoint pending(boolean auto) throws SQLException {
      connection.setAutoCommit(auto);
      final Savepoint point = auto ? null : connection.setSavepoint();
      execute("INSERT INTO unrelated VALUES(42)");
      return point;
    }

    int sessions() throws SQLException {
      return Integer.parseInt(
          rows("SELECT COUNT(*) FROM INFORMATION_SCHEMA." + (hsql ? "SYSTEM_SESSIONS" : "SESSIONS"))
              .get(0)
              .get(0));
    }

    List<List<String>> rules() throws SQLException {
      return rows("SELECT * FROM " + quotedTable() + " ORDER BY 1,2,3,4,5,6,7,8");
    }

    List<List<String>> editedRows() throws SQLException {
      return rows(
          "SELECT * FROM "
              + quotedTable()
              + " WHERE ABS(num)=2 AND (scenario=-1 OR scenario=7) ORDER BY 1,2,3,4,5,6,7,8");
    }

    List<List<String>> otherRules() throws SQLException {
      return rows(
          "SELECT * FROM "
              + quotedTable()
              + " WHERE ABS(num)<>2 OR scenario NOT IN(-1,7) ORDER BY 1,2,3,4,5,6,7,8");
    }

    void execute(String sql) throws SQLException {
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate(sql);
      }
    }

    List<List<String>> rows(String sql) throws SQLException {
      final List<List<String>> rows = new ArrayList<>();
      try (Statement statement = connection.createStatement();
          ResultSet result = statement.executeQuery(sql)) {
        while (result.next()) {
          final List<String> row = new ArrayList<>();
          for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
            row.add(result.getString(column));
          }
          rows.add(row);
        }
      }
      return rows;
    }

    @Override
    public void close() throws SQLException {
      if (!connection.getAutoCommit()) {
        connection.rollback();
      }
      if (hsql) {
        execute("SHUTDOWN");
      }
      database.close();
    }
  }

  private static final class Faults {
    final Connection proxy;
    String phase;
    int inserts;
    int createdStatements;
    int closedStatements;
    int createdResults;
    int closedResults;
    int commits;

    Faults(Connection connection) {
      proxy =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (ignored, method, args) -> {
                    final String operation = method.getName();
                    if (operation.equals("setSavepoint") && "savepoint".equals(phase)
                        || operation.equals("releaseSavepoint") && "release".equals(phase)
                        || operation.equals("commit") && "commit".equals(phase)) {
                      throw failure(phase);
                    }
                    if (operation.equals("prepareStatement")) {
                      final String sql = (String) args[0];
                      if (sql.startsWith("DELETE") && "prepare delete".equals(phase)
                          || sql.startsWith("INSERT") && "prepare insert".equals(phase)) {
                        throw failure(phase);
                      }
                    }
                    final Object result = invoke(connection, method, args);
                    if (operation.equals("rollback") && "rollback".equals(phase)) {
                      throw failure("rollback");
                    }
                    if (operation.equals("setAutoCommit")
                        && Boolean.TRUE.equals(args[0])
                        && "insert+restore".equals(phase)) {
                      throw failure("restore");
                    }
                    if (operation.equals("commit")) {
                      commits++;
                    }
                    if (operation.equals("prepareStatement")) {
                      return statement((PreparedStatement) result, (String) args[0]);
                    }
                    return result;
                  });
    }

    private PreparedStatement statement(PreparedStatement statement, String sql) {
      createdStatements++;
      return (PreparedStatement)
          Proxy.newProxyInstance(
              PreparedStatement.class.getClassLoader(),
              new Class<?>[] {PreparedStatement.class},
              (ignored, method, args) -> {
                final Object result = invoke(statement, method, args);
                final String operation = method.getName();
                if (operation.equals("close")) {
                  closedStatements++;
                  if (sql.startsWith("INSERT") && "insert close".equals(phase)
                      || sql.startsWith("DELETE") && "delete close".equals(phase)
                      || sql.startsWith("SELECT") && "read close".equals(phase)) {
                    throw failure(phase);
                  }
                }
                if (operation.equals("executeUpdate")) {
                  if (sql.startsWith("DELETE") && "delete".equals(phase)) {
                    throw failure("delete");
                  }
                  if (sql.startsWith("INSERT") && ++inserts == 2) {
                    if ("runtime insert".equals(phase)) {
                      throw new IllegalStateException("Injected runtime insert");
                    }
                    if (List.of("insert", "insert+restore", "rollback")
                        .contains(phase == null ? "" : phase)) {
                      throw failure("insert");
                    }
                  }
                }
                if (operation.equals("executeQuery")) {
                  return result((ResultSet) result);
                }
                return result;
              });
    }

    private ResultSet result(ResultSet result) {
      createdResults++;
      final int[] read = {0};
      return (ResultSet)
          Proxy.newProxyInstance(
              ResultSet.class.getClassLoader(),
              new Class<?>[] {ResultSet.class},
              (ignored, method, args) -> {
                final Object value = invoke(result, method, args);
                if (method.getName().equals("next") && ++read[0] == 2 && "row read".equals(phase)) {
                  throw failure(phase);
                }
                if (method.getName().equals("close")) {
                  closedResults++;
                  if ("result close".equals(phase)) {
                    throw failure(phase);
                  }
                }
                return value;
              });
    }

    void assertClosed() {
      assertEquals(createdStatements, closedStatements);
      assertEquals(createdResults, closedResults);
    }

    private static SQLException failure(String phase) {
      return new SQLException("Injected " + phase);
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args)
        throws Throwable {
      try {
        return method.invoke(target, args);
      } catch (InvocationTargetException failure) {
        throw failure.getCause();
      }
    }
  }
}
