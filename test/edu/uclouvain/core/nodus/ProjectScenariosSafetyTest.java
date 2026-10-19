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

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.compute.rules.NodeRulesReader;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Scenario schema changes must not silently detach their results from their saved settings. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(20)
class ProjectScenariosSafetyTest {
  @TempDir Path directory;

  @Test
  void absentPropertiesAndIdenticalPropertyNamesAreHarmless() throws Exception {
    final NodusProject project = new NodusProject(null);
    final Properties properties = new Properties();
    final Field field = NodusProject.class.getDeclaredField("localProperties");
    field.setAccessible(true);
    field.set(project, properties);
    properties.setProperty("existing", "value");
    project.renameLocalProperty("absent", "existing");
    assertEquals("value", properties.getProperty("existing"));
    project.renameLocalProperty("existing", "existing");
    assertEquals("value", properties.getProperty("existing"));
  }

  @TestFactory
  List<DynamicTest> scenarioChanges() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      final String engine = hsql ? "HSQLDB " : "H2 ";
      for (boolean custom : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                engine + "sparse settings and prefixes " + custom,
                () -> checkSparseRename(hsql, custom)));
        for (int table = 0; table < 3; table++) {
          final int occupied = table;
          tests.add(
              dynamicTest(
                  engine + "collision " + custom + "/" + table,
                  () -> checkCollision(hsql, custom, occupied)));
        }
      }
      tests.add(
          dynamicTest(
              engine + "settings-only destination is occupied",
              () -> {
                try (Fixture f = new Fixture(hsql, true)) {
                  f.createScenario(1);
                  f.properties.setProperty(
                      NodusC.PROP_ASSIGNMENT_DESCRIPTION + "2", "Existing target");
                  final Properties before = f.settings();
                  final Map<String, Integer> rows = f.rows();
                  assertThrows(IllegalStateException.class, () -> f.project.renameScenario(1, 2));
                  f.assertUnchanged(before, rows);
                }
              }));
      tests.add(
          dynamicTest(
              engine + "partial source cannot merge into an occupied destination",
              () -> {
                try (Fixture f = new Fixture(hsql, true)) {
                  f.createTable(f.tables(1)[0], 100);
                  f.createTable(f.tables(2)[1], 201);
                  final Properties before = f.settings();
                  final Map<String, Integer> rows = f.rows();
                  assertThrows(IllegalStateException.class, () -> f.project.renameScenario(1, 2));
                  f.assertUnchanged(before, rows);
                }
              }));
      tests.add(
          dynamicTest(
              engine + "identical scenario ID is a no-op",
              () -> {
                try (Fixture f = new Fixture(hsql, false)) {
                  f.createScenario(1);
                  final Properties before = f.settings();
                  final Map<String, Integer> rows = f.rows();
                  f.project.renameScenario(1, 1);
                  f.assertUnchanged(before, rows);
                }
              }));
      tests.add(
          dynamicTest(
              engine + "settings-only source can be renamed",
              () -> {
                try (Fixture f = new Fixture(hsql, false)) {
                  f.project.renameScenario(1, 2);
                  assertNull(f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "1"));
                  assertEquals(
                      "one.costs", f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "2"));
                  assertEquals(2, f.project.getCurrentScenario());
                  assertEquals(1, f.refreshed.get());
                }
              }));
      for (boolean autoCommit : new boolean[] {false, true}) {
        for (int step = 1; step <= 3; step++) {
          final int failed = step;
          tests.add(
              dynamicTest(
                  engine + "rename failure " + step + "/autocommit=" + autoCommit,
                  () -> checkRenameFailure(hsql, autoCommit, failed)));
        }
      }
      for (int step = 1; step <= 3; step++) {
        final int failed = step;
        tests.add(
            dynamicTest(
                engine + "deletion failure " + step + " can be retried",
                () -> checkRemoveFailure(hsql, failed)));
      }
      tests.add(
          dynamicTest(
              engine + "metadata failure is not mistaken for absent tables",
              () -> {
                try (Fixture f = new Fixture(hsql, true)) {
                  f.createScenario(1);
                  final Properties before = f.settings();
                  final Map<String, Integer> rows = f.rows();
                  f.failMetadata();
                  assertThrows(IllegalStateException.class, () -> f.project.renameScenario(1, 2));
                  assertThrows(IllegalStateException.class, () -> f.project.removeScenario(1));
                  f.restoreConnection();
                  f.assertUnchanged(before, rows);
                }
              }));
      tests.add(
          dynamicTest(
              engine + "unsuccessful compensation reports the remaining table",
              () -> {
                try (Fixture f = new Fixture(hsql, true)) {
                  f.createScenario(1);
                  final Properties before = f.settings();
                  f.failSql("ALTER TABLE", 2, true);
                  final IllegalStateException failure =
                      assertThrows(
                          IllegalStateException.class, () -> f.project.renameScenario(1, 2));
                  assertTrue(
                      failure
                          .getMessage()
                          .contains(JDBCUtils.getCompliantIdentifier(f.tables(2)[0])),
                      failure.getMessage());
                  assertNotNull(failure.getCause());
                  assertTrue(failure.getCause().getSuppressed().length > 0);
                  assertEquals(before, f.settings());
                  assertEquals(0, f.refreshed.get());
                  assertEquals(0, f.propertyChanges.get());
                  f.restoreConnection();
                  assertTrue(JDBCUtils.tableExists(f.tables(2)[0]));
                  assertFalse(JDBCUtils.tableExists(f.tables(1)[0]));
                  assertEquals(100, f.marker(f.tables(2)[0]));
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> scenarioRulesFollowScenarioChanges() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean custom : new boolean[] {false, true}) {
        for (boolean autoCommit : new boolean[] {false, true}) {
          tests.add(
              dynamicTest(
                  hsql + "/rules/" + custom + "/autocommit=" + autoCommit,
                  () -> {
                    try (Fixture f = new Fixture(hsql, custom)) {
                      f.createScenario(1);
                      f.createRules();
                      f.database.connection.setAutoCommit(autoCommit);
                      f.project.renameScenario(1, 2);
                      assertEquals(f.expectedRules(2), f.rulesRows());
                      assertEquals(autoCommit, f.database.connection.getAutoCommit());
                      f.assertCommittedRules(2, 2);
                      f.project.removeScenario(2);
                      assertEquals(List.of(f.genericRule(), f.unrelatedRule()), f.rulesRows());
                      f.assertCommittedRules(2, 0);
                      assertEquals(autoCommit, f.database.connection.getAutoCommit());
                      assertNull(f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "2"));
                    }
                  }));
        }
        tests.add(
            dynamicTest(
                hsql + "/destination occupied only by rules/" + custom,
                () -> {
                  try (Fixture f = new Fixture(hsql, custom)) {
                    f.createScenario(1);
                    f.createRules();
                    f.database.execute(
                        "UPDATE " + f.quotedRules() + " SET scenario=2 WHERE scenario=9");
                    final Properties settings = f.settings();
                    final Map<String, Integer> outputs = f.rows();
                    final List<List<Integer>> rules = f.rulesRows();
                    assertThrows(IllegalStateException.class, () -> f.project.renameScenario(1, 2));
                    f.assertUnchanged(settings, outputs);
                    assertEquals(rules, f.rulesRows());
                  }
                }));
      }
      tests.add(
          dynamicTest(
              hsql + "/rules without output tables",
              () -> {
                try (Fixture f = new Fixture(hsql, false)) {
                  f.createRules();
                  f.database.connection.setAutoCommit(false);
                  f.project.renameScenario(1, 2);
                  assertEquals(f.expectedRules(2), f.rulesRows());
                  f.assertCommittedRules(2, 2);
                  assertTrue(f.rows().isEmpty());
                  f.project.removeScenario(2);
                  assertEquals(List.of(f.genericRule(), f.unrelatedRule()), f.rulesRows());
                  f.assertCommittedRules(2, 0);
                  assertFalse(f.database.connection.getAutoCommit());
                }
              }));
      tests.add(
          dynamicTest(
              hsql + "/legacy rules without a scenario column",
              () -> {
                try (Fixture f = new Fixture(hsql, false)) {
                  f.createScenario(1);
                  f.database.execute(
                      "CREATE TABLE "
                          + f.quotedRules()
                          + " (num INTEGER, grp INTEGER, mode1 INTEGER, means1 INTEGER,"
                          + " mode2 INTEGER, means2 INTEGER)");
                  f.database.execute("INSERT INTO " + f.quotedRules() + " VALUES (-10,-1,1,1,2,1)");
                  f.project.renameScenario(1, 2);
                  f.project.removeScenario(2);
                  try (Statement statement = f.database.connection.createStatement();
                      ResultSet result =
                          statement.executeQuery("SELECT * FROM " + f.quotedRules())) {
                    assertTrue(result.next());
                    assertEquals(-10, result.getInt(1));
                    assertEquals(-1, result.getInt(2));
                    assertEquals(1, result.getInt(3));
                    assertEquals(1, result.getInt(4));
                    assertEquals(2, result.getInt(5));
                    assertEquals(1, result.getInt(6));
                    assertFalse(result.next());
                  }
                }
              }));
      for (boolean autoCommit : new boolean[] {false, true}) {
        for (boolean afterWrite : new boolean[] {false, true}) {
          for (boolean deleting : new boolean[] {false, true}) {
            tests.add(
                dynamicTest(
                    hsql
                        + "/rule failure/"
                        + deleting
                        + "/after="
                        + afterWrite
                        + "/autocommit="
                        + autoCommit,
                    () -> {
                      try (Fixture f = new Fixture(hsql, true)) {
                        f.createScenario(1);
                        f.createRules();
                        f.database.connection.setAutoCommit(autoCommit);
                        final Properties settings = f.settings();
                        final Map<String, Integer> outputs = f.rows();
                        final List<List<Integer>> rules = f.rulesRows();
                        f.failRuleWrite(deleting, afterWrite, false);
                        assertThrows(
                            IllegalStateException.class,
                            () -> {
                              if (deleting) {
                                f.project.removeScenario(1);
                              } else {
                                f.project.renameScenario(1, 2);
                              }
                            });
                        assertTrue(f.injected.get() > 0);
                        f.restoreConnection();
                        assertEquals(settings, f.settings());
                        assertEquals(rules, f.rulesRows());
                        assertEquals(0, f.refreshed.get());
                        assertEquals(0, f.propertyChanges.get());
                        assertEquals(autoCommit, f.database.connection.getAutoCommit());
                        if (deleting) {
                          f.project.removeScenario(1);
                          assertEquals(List.of(f.genericRule(), f.unrelatedRule()), f.rulesRows());
                        } else {
                          assertEquals(outputs, f.rows(), "Output renames must also be reversed");
                          f.project.renameScenario(1, 2);
                          assertEquals(f.expectedRules(2), f.rulesRows());
                        }
                      }
                    }));
          }
        }
      }
      tests.add(
          dynamicTest(
              hsql + "/rule commit failure reverses output renames",
              () -> {
                try (Fixture f = new Fixture(hsql, false)) {
                  f.createScenario(1);
                  f.createRules();
                  f.database.connection.setAutoCommit(false);
                  final Properties settings = f.settings();
                  final Map<String, Integer> outputs = f.rows();
                  final List<List<Integer>> rules = f.rulesRows();
                  f.failRuleWrite(false, false, true);
                  assertThrows(IllegalStateException.class, () -> f.project.renameScenario(1, 2));
                  assertTrue(f.injected.get() > 0);
                  f.restoreConnection();
                  f.assertUnchanged(settings, outputs);
                  assertEquals(rules, f.rulesRows());
                }
              }));
      tests.add(
          dynamicTest(
              hsql + "/failed output deletion retains all rules",
              () -> {
                try (Fixture f = new Fixture(hsql, false)) {
                  f.createScenario(1);
                  f.createRules();
                  f.failSql("DROP TABLE", 2, false);
                  assertThrows(IllegalStateException.class, () -> f.project.removeScenario(1));
                  f.restoreConnection();
                  assertEquals(f.expectedRules(1), f.rulesRows());
                  assertEquals(
                      "one.costs", f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "1"));
                }
              }));
      tests.add(
          dynamicTest(
              hsql + "/generic rules cannot become a scenario",
              () -> {
                try (Fixture f = new Fixture(hsql, false)) {
                  f.createRules();
                  final Properties settings = f.settings();
                  final List<List<Integer>> rules = f.rulesRows();
                  assertThrows(IllegalStateException.class, () -> f.project.renameScenario(-1, 2));
                  assertThrows(IllegalStateException.class, () -> f.project.renameScenario(1, -1));
                  assertThrows(IllegalStateException.class, () -> f.project.removeScenario(-1));
                  assertEquals(settings, f.settings());
                  assertEquals(rules, f.rulesRows());
                  assertEquals(0, f.refreshed.get());
                }
              }));
    }
    return tests;
  }

  private void checkSparseRename(boolean hsql, boolean custom) throws Exception {
    try (Fixture f = new Fixture(hsql, custom)) {
      f.createScenario(1);
      f.createScenario(9);
      f.properties.setProperty(NodusC.PROP_OD_TABLE, "global_od");
      f.properties.setProperty(NodusC.PROP_PATH_QUERY + "1", "SELECT * FROM custom_paths");
      // An underscore in a table name must not behave like a metadata LIKE wildcard.
      final String unrelated = f.tables(2)[0].replace('_', 'x');
      f.createTable(unrelated, 900);
      f.project.renameScenario(1, 2);
      for (int i = 0; i < 3; i++) {
        assertFalse(JDBCUtils.tableExists(f.tables(1)[i]));
        assertEquals(100 + i, f.marker(f.tables(2)[i]));
        assertEquals(900 + i, f.marker(f.tables(9)[i]));
      }
      assertNull(f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "1"));
      assertEquals("one.costs", f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "2"));
      assertNull(f.project.getLocalProperty(NodusC.PROP_OD_TABLE + "2"));
      assertEquals("global_od", f.project.getLocalProperty(NodusC.PROP_OD_TABLE));
      assertNull(f.project.getLocalProperty(NodusC.PROP_PATH_QUERY + "1"));
      assertEquals(
          "SELECT * FROM custom_paths", f.project.getLocalProperty(NodusC.PROP_PATH_QUERY + "2"));
      assertEquals("nine.costs", f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "9"));
      assertEquals(2, f.project.getCurrentScenario());
      assertEquals(1, f.refreshed.get());
      f.project.removeScenario(2);
      for (String table : f.tables(2)) {
        assertFalse(f.rows().containsKey(JDBCUtils.getCompliantIdentifier(table)));
      }
      assertEquals(900, f.marker(unrelated));
      assertNull(f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "2"));
      assertNull(f.project.getLocalProperty(NodusC.PROP_PATH_QUERY + "2"));
      assertEquals("nine.costs", f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "9"));
      assertEquals(2, f.refreshed.get());
    }
  }

  private void checkCollision(boolean hsql, boolean custom, int occupied) throws Exception {
    try (Fixture f = new Fixture(hsql, custom)) {
      f.createScenario(1);
      f.createTable(f.tables(2)[occupied], 200 + occupied);
      final Properties before = f.settings();
      final Map<String, Integer> rows = f.rows();
      assertThrows(IllegalStateException.class, () -> f.project.renameScenario(1, 2));
      f.assertUnchanged(before, rows);
    }
  }

  private void checkRenameFailure(boolean hsql, boolean autoCommit, int failed) throws Exception {
    try (Fixture f = new Fixture(hsql, true)) {
      f.createScenario(1);
      f.createScenario(9);
      f.database.connection.setAutoCommit(autoCommit);
      final Properties before = f.settings();
      final Map<String, Integer> rows = f.rows();
      f.failSql("ALTER TABLE", failed, false);
      final IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> f.project.renameScenario(1, 2));
      assertTrue(failure.getCause() instanceof SQLException);
      assertTrue(f.injected.get() > 0);
      f.restoreConnection();
      f.assertUnchanged(before, rows);
      assertEquals(autoCommit, f.database.connection.getAutoCommit());
      assertFalse(f.database.connection.isClosed());
      f.project.renameScenario(1, 2);
      for (int i = 0; i < 3; i++) {
        assertEquals(100 + i, f.marker(f.tables(2)[i]));
      }
    }
  }

  private void checkRemoveFailure(boolean hsql, int failed) throws Exception {
    try (Fixture f = new Fixture(hsql, true)) {
      f.createScenario(1);
      f.createScenario(9);
      final Properties before = f.settings();
      f.failSql("DROP TABLE", failed, false);
      assertThrows(IllegalStateException.class, () -> f.project.removeScenario(1));
      assertEquals(before, f.settings());
      assertEquals(0, f.refreshed.get());
      assertEquals(0, f.propertyChanges.get());
      f.restoreConnection();
      // DDL may commit: earlier requested drops can persist, but remaining outputs and settings
      // stay available for a retry. No unrelated table is removed.
      for (int i = failed - 1; i < 3; i++) {
        assertEquals(100 + i, f.marker(f.tables(1)[i]));
      }
      for (int i = 0; i < 3; i++) {
        assertEquals(900 + i, f.marker(f.tables(9)[i]));
      }
      f.project.removeScenario(1);
      for (String table : f.tables(1)) {
        assertFalse(JDBCUtils.tableExists(table));
      }
      assertNull(f.project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "1"));
      assertEquals(1, f.refreshed.get());
    }
  }

  private final class Fixture implements AutoCloseable {
    final DatabaseFixture database;
    final Properties properties = new Properties();
    final AtomicInteger refreshed = new AtomicInteger();
    final AtomicInteger propertyChanges = new AtomicInteger();
    final AtomicInteger injected = new AtomicInteger();
    final String network;
    final String paths;
    final String rules;
    final NodusMapPanel panel;
    final NodusProject project;
    Connection activeConnection;

    Fixture(boolean hsql, boolean custom) throws Exception {
      database = new DatabaseFixture(directory, hsql);
      activeConnection = database.connection;
      network = custom ? "net works_" : "mini" + NodusC.SUFFIX_VNET;
      paths = custom ? "path data_" : "mini";
      rules = custom ? "custom_node_rules" : "mini" + NodusC.SUFFIX_EXC;
      properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "mini");
      properties.setProperty(NodusC.PROP_SCENARIO, "1");
      properties.setProperty(NodusC.PROP_COST_FUNCTIONS + "1", "one.costs");
      properties.setProperty(NodusC.PROP_COST_FUNCTIONS + "9", "nine.costs");
      if (custom) {
        properties.setProperty(NodusC.PROP_VNET_TABLE, network);
        properties.setProperty(NodusC.PROP_PATH_TABLE_PREFIX, paths);
        properties.setProperty(NodusC.PROP_EXC_TABLE, rules);
      }
      panel =
          new NodusMapPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            public void updateScenarioComboBox(boolean force) {
              assertFalse(force);
              refreshed.incrementAndGet();
            }
          };
      project =
          new NodusProject(panel) {
            @Override
            public Connection getMainJDBCConnection() {
              return activeConnection;
            }

            @Override
            public void renameLocalProperty(String oldKey, String newKey) {
              propertyChanges.incrementAndGet();
              super.renameLocalProperty(oldKey, newKey);
            }

            @Override
            public void removeLocalProperty(String key) {
              propertyChanges.incrementAndGet();
              super.removeLocalProperty(key);
            }
          };
      final Field local = NodusProject.class.getDeclaredField("localProperties");
      local.setAccessible(true);
      local.set(project, properties);
    }

    String[] tables(int scenario) {
      return new String[] {
        network + scenario,
        paths + scenario + NodusC.SUFFIX_HEADER,
        paths + scenario + NodusC.SUFFIX_DETAIL
      };
    }

    void createScenario(int scenario) throws SQLException {
      final String[] names = tables(scenario);
      for (int i = 0; i < names.length; i++) {
        createTable(names[i], scenario * 100 + i);
      }
    }

    void createTable(String name, int marker) throws SQLException {
      database.execute(
          "CREATE TABLE " + JDBCUtils.getQuotedCompliantIdentifier(name) + " (marker INTEGER)");
      database.execute(
          "INSERT INTO "
              + JDBCUtils.getQuotedCompliantIdentifier(name)
              + " VALUES ("
              + marker
              + ")");
    }

    int marker(String name) throws SQLException {
      try (Statement s = database.connection.createStatement();
          ResultSet r =
              s.executeQuery(
                  "SELECT marker FROM " + JDBCUtils.getQuotedCompliantIdentifier(name))) {
        assertTrue(r.next());
        return r.getInt(1);
      }
    }

    Map<String, Integer> rows() throws SQLException {
      final List<String> names = new ArrayList<>();
      try (ResultSet result = JDBCUtils.getTables()) {
        while (result.next()) {
          final String name = result.getString("TABLE_NAME");
          if (!name.equals(JDBCUtils.getCompliantIdentifier(rules))) {
            names.add(name);
          }
        }
      }
      final Map<String, Integer> result = new LinkedHashMap<>();
      names.sort(String::compareTo);
      for (String name : names) {
        result.put(name, marker(name));
      }
      return result;
    }

    Properties settings() {
      return (Properties) properties.clone();
    }

    void assertUnchanged(Properties before, Map<String, Integer> rows) throws SQLException {
      assertEquals(before, settings());
      assertEquals(rows, rows());
      assertEquals(0, refreshed.get());
      assertEquals(0, propertyChanges.get());
    }

    void failSql(String verb, int position, boolean failRestore) {
      final AtomicInteger calls = new AtomicInteger();
      activeConnection =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, args) -> {
                    try {
                      final Object result = method.invoke(database.connection, args);
                      if (!method.getName().equals("createStatement")) {
                        return result;
                      }
                      final Statement delegate = (Statement) result;
                      return Proxy.newProxyInstance(
                          Statement.class.getClassLoader(),
                          new Class<?>[] {Statement.class},
                          (p, m, a) -> {
                            if ((m.getName().equals("execute")
                                    || m.getName().equals("executeUpdate"))
                                && a[0] instanceof String
                                && ((String) a[0]).startsWith(verb)
                                && (calls.incrementAndGet() == position
                                    || failRestore && injected.get() > 0)) {
                              injected.incrementAndGet();
                              throw new SQLException("Injected scenario DDL failure");
                            }
                            try {
                              return m.invoke(delegate, a);
                            } catch (InvocationTargetException failure) {
                              throw failure.getCause();
                            }
                          });
                    } catch (InvocationTargetException failure) {
                      throw failure.getCause();
                    }
                  });
      assertTrue(JDBCUtils.setConnection(activeConnection));
    }

    void failMetadata() {
      activeConnection =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, args) -> {
                    try {
                      final Object result = method.invoke(database.connection, args);
                      if (!method.getName().equals("getMetaData")) {
                        return result;
                      }
                      return Proxy.newProxyInstance(
                          DatabaseMetaData.class.getClassLoader(),
                          new Class<?>[] {DatabaseMetaData.class},
                          (p, m, a) -> {
                            if (m.getName().equals("getTables")) {
                              throw new SQLException("Injected scenario metadata failure");
                            }
                            try {
                              return m.invoke(result, a);
                            } catch (InvocationTargetException failure) {
                              throw failure.getCause();
                            }
                          });
                    } catch (InvocationTargetException failure) {
                      throw failure.getCause();
                    }
                  });
      assertTrue(JDBCUtils.setConnection(activeConnection));
    }

    String quotedRules() {
      return JDBCUtils.getQuotedCompliantIdentifier(rules);
    }

    void createRules() throws SQLException {
      assertTrue(NodeRulesReader.createExclusionsTable(rules));
      database.execute(
          "INSERT INTO "
              + quotedRules()
              + " VALUES "
              + "(-10,-1,-1,1,1,2,1,1),(20,1,3,1,1,2,1,0),"
              + "(-20,1,4,2,1,1,1,1),(-30,9,-1,1,1,1,2,0)");
      if (!database.connection.getAutoCommit()) {
        database.connection.commit();
      }
    }

    List<Integer> genericRule() {
      return List.of(-10, -1, -1, 1, 1, 2, 1, 1);
    }

    List<Integer> unrelatedRule() {
      return List.of(-30, 9, -1, 1, 1, 1, 2, 0);
    }

    List<List<Integer>> expectedRules(int scenario) {
      return List.of(
          genericRule(),
          unrelatedRule(),
          List.of(-20, scenario, 4, 2, 1, 1, 1, 1),
          List.of(20, scenario, 3, 1, 1, 2, 1, 0));
    }

    List<List<Integer>> rulesRows() throws SQLException {
      final List<List<Integer>> rows = new ArrayList<>();
      try (Statement statement = database.connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT * FROM "
                      + quotedRules()
                      + " ORDER BY "
                      + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM))) {
        while (result.next()) {
          final List<Integer> row = new ArrayList<>();
          for (int column = 1; column <= 8; column++) {
            row.add(result.getInt(column));
          }
          rows.add(row);
        }
      }
      // Keep the independent reference order stable: generic, unrelated, scenario rules.
      rows.sort((a, b) -> Integer.compare(ruleOrder(a.get(0)), ruleOrder(b.get(0))));
      return rows;
    }

    int ruleOrder(int num) {
      return num == -10 ? 0 : num == -30 ? 1 : num == -20 ? 2 : 3;
    }

    void assertCommittedRules(int scenario, int count) throws SQLException {
      try (Connection observer =
              DriverManager.getConnection(database.connection.getMetaData().getURL(), "sa", "");
          Statement statement = observer.createStatement()) {
        statement.setQueryTimeout(2);
        try (ResultSet result =
            statement.executeQuery(
                "SELECT COUNT(*) FROM " + quotedRules() + " WHERE scenario=" + scenario)) {
          assertTrue(result.next());
          assertEquals(count, result.getInt(1));
        }
      }
    }

    void failRuleWrite(boolean deleting, boolean afterWrite, boolean failCommit) {
      final AtomicBoolean failed = new AtomicBoolean();
      final String verb = deleting ? "DELETE FROM" : "UPDATE";
      activeConnection =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, args) -> {
                    try {
                      if (failCommit
                          && method.getName().equals("commit")
                          && failed.compareAndSet(false, true)) {
                        injected.incrementAndGet();
                        throw new SQLException("Injected rule commit failure");
                      }
                      final Object value = method.invoke(database.connection, args);
                      if (!method.getName().equals("prepareStatement")
                          || failCommit
                          || !((String) args[0]).startsWith(verb)) {
                        return value;
                      }
                      final PreparedStatement delegate = (PreparedStatement) value;
                      return Proxy.newProxyInstance(
                          PreparedStatement.class.getClassLoader(),
                          new Class<?>[] {PreparedStatement.class},
                          (p, m, a) -> {
                            try {
                              if (m.getName().equals("executeUpdate")
                                  && failed.compareAndSet(false, true)) {
                                if (afterWrite) {
                                  m.invoke(delegate, a);
                                }
                                injected.incrementAndGet();
                                throw new SQLException("Injected node-rule write failure");
                              }
                              return m.invoke(delegate, a);
                            } catch (InvocationTargetException failure) {
                              throw failure.getCause();
                            }
                          });
                    } catch (InvocationTargetException failure) {
                      throw failure.getCause();
                    }
                  });
      assertTrue(JDBCUtils.setConnection(activeConnection));
    }

    void restoreConnection() {
      activeConnection = database.connection;
      assertTrue(JDBCUtils.setConnection(activeConnection));
    }

    @Override
    public void close() throws Exception {
      panel.dispose();
      database.close();
    }
  }
}
