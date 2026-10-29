/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.virtual.AssignmentOutputTables;
import edu.uclouvain.core.nodus.testing.GroupedRenameJdbc;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Real embedded SQL with a grouped-rename transport adapter; native SQL is checked separately. */
@ResourceLock("JDBCUtils")
@ResourceLock(Resources.SYSTEM_ERR)
class GroupedTablePublicationTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> outputSetsPublishAndRecoverAsOneGroup() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(
                        "success",
                        "before",
                        "after",
                        "commit",
                        "recovery",
                        "first-after",
                        "header-only",
                        "cleanup")
                    .map(
                        mode ->
                            DynamicTest.dynamicTest(
                                "HSQLDB=" + hsql + ": " + mode,
                                () -> {
                                  try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                                    GroupedRenameJdbc jdbc = new GroupedRenameJdbc(f.connection);
                                    JDBCUtils.setConnection(jdbc.connection);
                                    f.connection.setAutoCommit(false);
                                    AssignmentParameters parameters =
                                        new AssignmentParameters(project(jdbc.connection));
                                    parameters.setScenario(1);
                                    parameters.setDetailedPaths(!mode.equals("header-only"));
                                    List<String> destinations =
                                        List.of(
                                            "mini_vnet1",
                                            "mini_paths1_header",
                                            "mini_paths1_detail");
                                    boolean first = mode.equals("first-after");
                                    if (!first) {
                                      for (String name : destinations) {
                                        table(f, name, 1);
                                      }
                                    }
                                    try (AssignmentOutputTables output =
                                        new AssignmentOutputTables(parameters, true)) {
                                      table(f, output.virtualNetwork(), 2);
                                      table(f, output.pathHeaders(), 2);
                                      if (!mode.equals("header-only")) {
                                        table(f, output.pathDetails(), 2);
                                      }
                                      f.connection.commit();
                                      jdbc.fault = first ? "after" : mode;
                                      jdbc.failCommit = mode.equals("commit") ? 1 : 0;
                                      if (List.of(
                                              "before",
                                              "after",
                                              "commit",
                                              "recovery",
                                              "first-after")
                                          .contains(mode)) {
                                        assertThrows(SQLException.class, output::publish);
                                        assertTrue(jdbc.triggered);
                                      } else {
                                        output.publish();
                                      }
                                      assertTrue(
                                          jdbc.renames.get(0).contains(", "),
                                          "The complete output set uses one statement");
                                    }
                                    for (String name : destinations) {
                                      if (first
                                          || (mode.equals("header-only")
                                              && name.endsWith("detail"))) {
                                        assertFalse(JDBCUtils.tableExists(name));
                                      } else {
                                        int expected =
                                            List.of("success", "header-only", "cleanup", "recovery")
                                                    .contains(mode)
                                                ? 2
                                                : 1;
                                        assertEquals(expected, marker(f.connection, name));
                                      }
                                    }
                                    assertEquals(
                                        mode.equals("recovery") || mode.equals("cleanup") ? 3 : 0,
                                        privateTables(f.connection, "NB_"));
                                    assertEquals(0, privateTables(f.connection, "NO_"));
                                    assertEquals(
                                        mode.equals("before")
                                                || mode.equals("success")
                                                || mode.equals("header-only")
                                                || mode.equals("cleanup")
                                            ? 1
                                            : 2,
                                        jdbc.renames.size());
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> importsUseGroupedPublicationAndRestoreOnFailure() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of("success", "before", "after", "commit", "cleanup")
                    .map(
                        mode ->
                            DynamicTest.dynamicTest(
                                "HSQLDB=" + hsql + ": " + mode,
                                () -> {
                                  try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                                    table(f, "original", 1);
                                    GroupedRenameJdbc jdbc = new GroupedRenameJdbc(f.connection);
                                    JDBCUtils.setConnection(jdbc.connection);
                                    jdbc.fault = mode;
                                    jdbc.failCommit = mode.equals("commit") ? 2 : 0;
                                    TableImport.Loader loader =
                                        (connection, staged) -> table(f, staged, 2);
                                    if (List.of("before", "after", "commit").contains(mode)) {
                                      assertThrows(
                                          SQLException.class,
                                          () ->
                                              TableImport.replace(
                                                  jdbc.connection, "original", loader, true));
                                      assertTrue(jdbc.triggered);
                                      assertEquals(1, marker(f.connection, "original"));
                                    } else {
                                      TableImport.replace(
                                          jdbc.connection, "original", loader, true);
                                      assertEquals(2, marker(f.connection, "original"));
                                    }
                                    assertTrue(jdbc.renames.get(0).contains(", "));
                                    assertEquals(0, privateTables(f.connection, "NDI_"));
                                    assertEquals(
                                        mode.equals("cleanup") ? 1 : 0,
                                        privateTables(f.connection, "NDB_"));
                                  }
                                })));
  }

  private static NodusProject project(Connection connection) {
    return new NodusProject(null) {
      @Override
      public Connection getMainJDBCConnection() {
        return connection;
      }

      @Override
      public String getLocalProperty(String key) {
        return getLocalProperty(key, (String) null);
      }

      @Override
      public String getLocalProperty(String key, String fallback) {
        if (key.equals(NodusC.PROP_PROJECT_DOTNAME)) {
          return "mini";
        }
        if (key.equals(NodusC.PROP_PATH_TABLE_PREFIX)) {
          return "mini_paths";
        }
        return fallback;
      }
    };
  }

  private static void table(DatabaseFixture f, String name, int value) throws SQLException {
    f.execute("CREATE TABLE " + JDBCUtils.getQuotedCompliantIdentifier(name) + "(marker INTEGER)");
    f.execute(
        "INSERT INTO " + JDBCUtils.getQuotedCompliantIdentifier(name) + " VALUES(" + value + ")");
  }

  private static int marker(Connection connection, String name) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT marker FROM " + JDBCUtils.getQuotedCompliantIdentifier(name))) {
      assertTrue(rows.next());
      return rows.getInt(1);
    }
  }

  private static int privateTables(Connection connection, String prefix) throws SQLException {
    int count = 0;
    try (ResultSet rows =
        connection
            .getMetaData()
            .getTables(
                connection.getCatalog(), connection.getSchema(), null, new String[] {"TABLE"})) {
      while (rows.next()) {
        if (rows.getString("TABLE_NAME").startsWith(prefix)) {
          count++;
        }
      }
    }
    return count;
  }
}
