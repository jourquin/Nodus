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

package edu.uclouvain.core.nodus.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Exercises the shipped Groovy service guard against real service handlers and databases. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("JOptionPaneMetaClass")
class NetworkSimplifierTest {
  @TempDir Path directory;

  private static GroovyClassLoader scriptLoader;
  private static Method prepare;
  private static Method confirm;
  private static Constructor<?> constructor;

  /** Compiles the entire script without executing its GUI entry point. */
  @BeforeAll
  static void compileScript() throws Exception {
    Path location =
        Path.of(NodusProject.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path script = null;
    for (Path root = location; root != null; root = root.getParent()) {
      Path candidate = root.resolve("scripts/NetworkSimplifier.groovy");
      if (Files.isRegularFile(candidate)) {
        script = candidate;
        break;
      }
    }
    if (script == null) {
      throw new IllegalStateException("Cannot locate NetworkSimplifier.groovy above " + location);
    }
    scriptLoader = new GroovyClassLoader(NetworkSimplifierTest.class.getClassLoader());
    scriptLoader.parseClass(script.toFile());
    Class<?> type = scriptLoader.loadClass("SimplifyNetworks");
    prepare =
        type.getDeclaredMethod(
            "prepareServiceLines", NodusProject.class, boolean.class, Predicate.class);
    prepare.setAccessible(true);
    confirm = type.getDeclaredMethod("confirmServiceDeletion", List.class);
    confirm.setAccessible(true);
    constructor = type.getDeclaredConstructor(NodusMapPanel.class, boolean.class);
    constructor.setAccessible(true);
  }

  @AfterAll
  static void closeScriptLoader() throws Exception {
    if (scriptLoader != null) {
      scriptLoader.close();
    }
  }

  @TestFactory
  Stream<DynamicTest> serviceGuard() {
    return Stream.of(false, true)
        .flatMap(
            hsql -> {
              String engine = hsql ? "HSQLDB: " : "H2: ";
              return Stream.of(
                  DynamicTest.dynamicTest(
                      engine + "cancel", () -> decliningPreservesSavedAndUnsavedServices(hsql)),
                  DynamicTest.dynamicTest(
                      engine + "delete custom tables",
                      () -> acceptanceDropsCustomTablesAndCannotResaveDiscardedServices(hsql)),
                  DynamicTest.dynamicTest(
                      engine + "dry run", () -> dryRunDoesNotPromptOrChangeServices(hsql)),
                  DynamicTest.dynamicTest(
                      engine + "unsaved services", () -> unsavedServicesAlsoRequireConsent(hsql)),
                  DynamicTest.dynamicTest(
                      engine + "empty project and orphan table",
                      () -> emptyProjectDoesNotPromptAndAnOrphanTableDoes(hsql)),
                  DynamicTest.dynamicTest(
                      engine + "failed drop", () -> failedDropStopsBeforeDiscardingServices(hsql)));
            });
  }

  @TestFactory
  Stream<DynamicTest> serviceWarningDialog() {
    return Stream.of(false, true)
        .flatMap(
            saved ->
                Stream.of(0, 1, JOptionPane.CLOSED_OPTION)
                    .map(
                        answer ->
                            DynamicTest.dynamicTest(
                                (saved ? "Saved" : "Unsaved")
                                    + " services, dialog answer "
                                    + answer,
                                () -> actualWarningHonorsAnswer(saved, answer))));
  }

  /** Runs the real warning code; replaces only the Swing window with a captured user response. */
  private void actualWarningHonorsAnswer(boolean saved, int answer) throws Exception {
    try (NetworkTestProject project = project(false)) {
      ServiceHandler handler = project.getServiceHandler();
      TransportService service = createService(handler, saved);
      final List<List<String>> before = saved ? snapshot(project) : List.of();
      Object simplifier = constructor.newInstance(project.panel, false);
      List<Object[]> dialogs = new ArrayList<>();
      MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
      MetaClass original = registry.getMetaClass(JOptionPane.class);
      MetaClass capture =
          new DelegatingMetaClass(original) {
            @Override
            public Object invokeStaticMethod(Object object, String name, Object[] arguments) {
              if (!"showOptionDialog".equals(name)) {
                return super.invokeStaticMethod(object, name, arguments);
              }
              assertTrue(SwingUtilities.isEventDispatchThread());
              assertSame(service, handler.getService("Saved"));
              for (String table : tableNames(handler)) {
                assertEquals(saved, JDBCUtils.tableExists(table));
              }
              assertEquals(1, project.links.getModel().getRowCount());
              assertEquals(2, project.nodes.getModel().getRowCount());
              dialogs.add(arguments.clone());
              return answer;
            }
          };
      capture.initialize();
      registry.setMetaClass(JOptionPane.class, capture);
      try {
        assertEquals(
            answer == 0,
            prepare(
                project,
                false,
                tables -> {
                  try {
                    return (Boolean) confirm.invoke(simplifier, tables);
                  } catch (ReflectiveOperationException ex) {
                    throw new AssertionError("Cannot display the service warning", ex);
                  }
                }));
      } finally {
        registry.setMetaClass(JOptionPane.class, original);
      }
      assertEquals(1, dialogs.size());
      Object[] dialog = dialogs.get(0);
      assertSame(project.panel, dialog[0]);
      String message = (String) dialog[1];
      assertTrue(message.contains("Simplification can break their routes and stops"));
      assertTrue(
          message.contains("permanently delete ALL service lines, including unsaved changes"));
      assertTrue(message.contains("Back up the database first."));
      assertTrue(message.endsWith("Continue?"));
      if (saved) {
        assertTrue(message.contains(String.join("\n", tableNames(handler))));
      } else {
        assertTrue(message.contains("No saved tables; unsaved services will be discarded."));
      }
      assertEquals(NodusC.APPNAME, dialog[2]);
      assertEquals(JOptionPane.YES_NO_OPTION, dialog[3]);
      assertEquals(JOptionPane.WARNING_MESSAGE, dialog[4]);
      assertEquals(
          List.of("Delete service lines and continue", "Cancel"), List.of((Object[]) dialog[6]));
      assertEquals("Cancel", dialog[7]);
      if (answer == 0) {
        assertFalse(handler.getServiceNamesIterator().hasNext());
        assertNoServiceTables(handler);
      } else {
        assertSame(service, handler.getService("Saved"));
        if (saved) {
          assertEquals(before, snapshot(project));
        } else {
          assertNoServiceTables(handler);
        }
      }
      assertEquals(1, project.links.getModel().getRowCount());
      assertEquals(2, project.nodes.getModel().getRowCount());
    }
  }

  private void decliningPreservesSavedAndUnsavedServices(boolean hsql) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      ServiceHandler handler = project.getServiceHandler();
      TransportService saved = createService(handler, true);
      final List<List<String>> before = snapshot(project);
      saved.setFrequency(730);
      handler.mustBeSaved();
      assertFalse(
          prepare(
              project,
              false,
              tables -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                assertEquals(tableNames(handler), tables);
                return false;
              }));
      assertSame(saved, handler.getService("Saved"));
      assertEquals(730, saved.getFrequency());
      assertEquals(before, snapshot(project));
      assertTrue(handler.savePendingChanges());
      assertEquals("730", rows(project, handler.getServiceHeaderTableName()).get(0).get(4));
    }
  }

  private void acceptanceDropsCustomTablesAndCannotResaveDiscardedServices(boolean hsql)
      throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      project.setLocalProperty(NodusC.PROP_SERVICES_TABLE_PREFIX, "custom_lines");
      project.reloadNetwork();
      project.nodes.getModel().setValueAt(1.0, 0, NodusC.DBF_IDX_TRANSHIP);
      project.nodes.getModel().setValueAt(1.0, 1, NodusC.DBF_IDX_TRANSHIP);
      ServiceHandler handler = project.getServiceHandler();
      createService(handler, true);
      handler.mustBeSaved();
      execute(project, "CREATE TABLE unrelated(marker INTEGER)");
      execute(project, "INSERT INTO unrelated VALUES(42)");
      List<List<String>> prompts = new ArrayList<>();
      assertTrue(prepare(project, false, tables -> prompts.add(new ArrayList<>(tables))));
      assertEquals(List.of(tableNames(handler)), prompts);
      assertTrue(handler.getServiceHeaderTableName().equalsIgnoreCase("custom_lines_header"));
      assertFalse(handler.getServiceNamesIterator().hasNext());
      assertNull(handler.getCurrentService());
      assertTrue(handler.savePendingChanges());
      assertNoServiceTables(handler);
      assertEquals(1, project.links.getModel().getRowCount());
      assertEquals(2, project.nodes.getModel().getRowCount());
      assertEquals(List.of(List.of("42")), rows(project, "unrelated"));
      assertTrue(project.serviceErrors.isEmpty());
    }
  }

  private void dryRunDoesNotPromptOrChangeServices(boolean hsql) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      ServiceHandler handler = project.getServiceHandler();
      TransportService saved = createService(handler, true);
      List<List<String>> before = snapshot(project);
      assertTrue(
          prepare(project, true, tables -> fail("A dry run must not ask to delete services")));
      assertSame(saved, handler.getService("Saved"));
      assertEquals(before, snapshot(project));
    }
  }

  private void unsavedServicesAlsoRequireConsent(boolean hsql) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      ServiceHandler handler = project.getServiceHandler();
      TransportService unsaved = createService(handler, false);
      assertNoServiceTables(handler);
      assertFalse(prepare(project, false, tables -> false));
      assertSame(unsaved, handler.getService("Saved"));
      assertTrue(
          prepare(
              project,
              false,
              tables -> {
                assertTrue(tables.isEmpty());
                return true;
              }));
      assertFalse(handler.getServiceNamesIterator().hasNext());
      assertTrue(handler.savePendingChanges());
      assertNoServiceTables(handler);
    }
  }

  private void emptyProjectDoesNotPromptAndAnOrphanTableDoes(boolean hsql) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      assertTrue(prepare(project, false, tables -> fail("There are no services to delete")));
      String stops = project.getServiceHandler().getServiceStopDetailTableName();
      execute(project, "CREATE TABLE " + stops + "(id INTEGER, stop INTEGER)");
      assertFalse(prepare(project, false, tables -> false));
      assertTrue(JDBCUtils.tableExists(stops));
      assertTrue(
          prepare(
              project,
              false,
              tables -> {
                assertEquals(List.of(stops), tables);
                return true;
              }));
      assertNoServiceTables(project.getServiceHandler());
    }
  }

  private void failedDropStopsBeforeDiscardingServices(boolean hsql) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      ServiceHandler handler = project.getServiceHandler();
      final TransportService saved = createService(handler, true);
      String links = handler.getServiceLinkDetailTableName();
      execute(project, "ALTER TABLE " + links + " ADD CONSTRAINT route_key UNIQUE(id, pathidx)");
      execute(
          project,
          "CREATE TABLE dependent(id INTEGER, pathidx INTEGER, "
              + "FOREIGN KEY(id, pathidx) REFERENCES "
              + links
              + "(id, pathidx))");
      List<List<String>> before = snapshot(project);
      assertThrows(SQLException.class, () -> prepare(project, false, tables -> true));
      assertSame(saved, handler.getService("Saved"));
      assertEquals(before, snapshot(project));
      assertEquals(1, project.links.getModel().getRowCount());
      assertEquals(2, project.nodes.getModel().getRowCount());
    }
  }

  private NetworkTestProject project(boolean hsql) throws Exception {
    NetworkTestProject project =
        new NetworkTestProject(directory, new double[] {0, 0, 0, 10}, hsql);
    project.nodes.getModel().setValueAt(1.0, 0, NodusC.DBF_IDX_TRANSHIP);
    project.nodes.getModel().setValueAt(1.0, 1, NodusC.DBF_IDX_TRANSHIP);
    return project;
  }

  private static TransportService createService(ServiceHandler handler, boolean save) {
    return handler.createOrReplaceServiceFromLinkIds(
        1, "Saved", 3, 1, 365, List.of(11), List.of(1, 2), false, save);
  }

  private static boolean prepare(
      NodusProject project, boolean dryRun, Predicate<List<String>> confirm) throws Exception {
    try {
      return (Boolean) prepare.invoke(null, project, dryRun, confirm);
    } catch (InvocationTargetException ex) {
      if (ex.getCause() instanceof Exception) {
        throw (Exception) ex.getCause();
      }
      throw new AssertionError(ex.getCause());
    }
  }

  private static List<String> tableNames(ServiceHandler handler) {
    return List.of(
        handler.getServiceLinkDetailTableName(),
        handler.getServiceStopDetailTableName(),
        handler.getServiceHeaderTableName());
  }

  private static void assertNoServiceTables(ServiceHandler handler) {
    for (String name : tableNames(handler)) {
      assertFalse(JDBCUtils.tableExists(name), name);
    }
  }

  private static List<List<String>> snapshot(NetworkTestProject project) throws SQLException {
    List<List<String>> result = new ArrayList<>();
    for (String table : tableNames(project.getServiceHandler())) {
      result.addAll(rows(project, table));
    }
    return result;
  }

  /** Borrows the project connection; closes only the statement and result set. */
  private static List<List<String>> rows(NetworkTestProject project, String table)
      throws SQLException {
    List<List<String>> rows = new ArrayList<>();
    try (Statement statement = project.getMainJDBCConnection().createStatement();
        ResultSet result = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
      while (result.next()) {
        List<String> row = new ArrayList<>();
        for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
          row.add(result.getString(i));
        }
        rows.add(row);
      }
    }
    return rows;
  }

  /** Executes SQL without closing the connection owned by the project fixture. */
  private static void execute(NetworkTestProject project, String sql) throws SQLException {
    try (Statement statement = project.getMainJDBCConnection().createStatement()) {
      statement.executeUpdate(sql);
    }
  }
}
