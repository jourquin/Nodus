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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import groovy.lang.GroovyClassLoader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Executes complete topology passes against real layers, including shuffled input row order. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(40)
class NetworkSimplifierMergeTest {
  @TempDir Path directory;
  private static GroovyClassLoader loader;
  private static Class<?> simplifierClass;

  @BeforeAll
  static void compileScript() throws Exception {
    Path root =
        Path.of(NodusProject.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    while (root != null && !Files.exists(root.resolve("scripts/NetworkSimplifier.groovy"))) {
      root = root.getParent();
    }
    if (root == null) {
      throw new IllegalStateException("Cannot locate NetworkSimplifier.groovy");
    }
    loader = new GroovyClassLoader(NetworkSimplifierMergeTest.class.getClassLoader());
    loader.parseClass(root.resolve("scripts/NetworkSimplifier.groovy").toFile());
    simplifierClass = loader.loadClass("SimplifyNetworks");
  }

  @AfterAll
  static void closeLoader() throws Exception {
    if (loader != null) {
      loader.close();
    }
  }

  @TestFactory
  Stream<DynamicTest> retainedIdentifiersAndGeometrySurviveCompaction() {
    Map<String, Integer> expected =
        Map.of("left", 104, "right", 105, "min-num", 101, "max-num", 108);
    return expected
        .entrySet()
        .stream()
        .map(
            entry ->
                DynamicTest.dynamicTest(
                    entry.getKey(),
                    () -> {
                      try (NetworkTestProject project =
                          chain(directory, 8, new int[] {3, 0, 7, 2, 5, 1, 6, 4})) {
                        Object simplifier = simplifier(simplifierClass, project, entry.getKey());
                        runPasses(simplifier, project);
                        assertEquals(1, project.links.getModel().getRowCount());
                        assertEquals(Map.of(entry.getValue(), 0), project.links.getIndex());
                        assertEquals(Map.of(1, 0, 9, 1), project.nodes.getIndex());
                        assertEquals(
                            List.of(1, 9),
                            List.of(
                                    ((Number)
                                            project
                                                .links
                                                .getModel()
                                                .getValueAt(0, NodusC.DBF_IDX_NODE1))
                                        .intValue(),
                                    ((Number)
                                            project
                                                .links
                                                .getModel()
                                                .getValueAt(0, NodusC.DBF_IDX_NODE2))
                                        .intValue())
                                .stream()
                                .sorted()
                                .collect(java.util.stream.Collectors.toList()));
                        EsriPolyline line =
                            (EsriPolyline) project.links.getEsriGraphicList().getOMGraphicAt(0);
                        double[] coords = line.getLatLonArrayCopy();
                        assertEquals(18, coords.length);
                        double length = 0;
                        for (int i = 3; i < coords.length; i += 2) {
                          length += Math.abs(coords[i] - coords[i - 2]);
                        }
                        assertEquals(Math.toRadians(0.008), length, 1e-10);
                        assertTrue(project.links.saveChanges());
                        assertTrue(project.nodes.saveChanges());
                        project.reloadNetwork();
                        assertEquals(Map.of(entry.getValue(), 0), project.links.getIndex());
                        assertEquals(2, project.nodes.getModel().getRowCount());
                      }
                    }));
  }

  @Test
  void conflictBoundaryRemainsAndDryRunDoesNotApplyMerges() throws Exception {
    try (NetworkTestProject project = chain(directory, 8, null)) {
      Object simplifier = simplifier(simplifierClass, project, "left");
      set(simplifier, "dryRun", true);
      assertEquals(0, runPasses(simplifier, project));
      assertEquals(8, project.links.getModel().getRowCount());
      assertEquals(9, project.nodes.getModel().getRowCount());
      set(simplifier, "dryRun", false);
      for (int row = 4; row < 8; row++) {
        project.links.getModel().setValueAt("different", row, 9);
      }
      runPasses(simplifier, project);
      assertEquals(2, project.links.getModel().getRowCount());
      assertEquals(Map.of(1, 0, 5, 1, 9, 2), project.nodes.getIndex());
    }
  }

  @Test
  void longCorridorRefreshesOncePerPassRatherThanOncePerMerge() throws Exception {
    try (NetworkTestProject project = chain(directory, 256, null)) {
      Object simplifier = simplifier(simplifierClass, project, "left");
      int passes = runPasses(simplifier, project);
      assertEquals(8, passes);
      assertEquals(passes, project.links.labelRefreshes);
      assertEquals(passes, project.nodes.labelRefreshes);
      assertEquals(1, project.links.getModel().getRowCount());
      assertEquals(2, project.nodes.getModel().getRowCount());
    }
  }

  /** Creates the normal two-layer fixture, then installs a corridor in one SQL/model pass. */
  static NetworkTestProject chain(Path directory, int count, int[] order) throws Exception {
    NetworkTestProject project = new NetworkTestProject(directory, new double[] {0, 0, 0, 0.001});
    DbfTableModel nodes = project.nodes.getModel();
    DbfTableModel links = project.links.getModel();
    final List<Object> template = new ArrayList<>(links.getRecord(0));
    while (nodes.getRowCount() > 0) {
      nodes.remove(nodes.getRowCount() - 1);
    }
    while (links.getRowCount() > 0) {
      links.remove(links.getRowCount() - 1);
    }
    project.nodes.getEsriGraphicList().clear();
    project.links.getEsriGraphicList().clear();
    try (Statement s = project.getMainJDBCConnection().createStatement()) {
      s.executeUpdate("DELETE FROM nodes");
      s.executeUpdate("DELETE FROM links");
    }
    try (PreparedStatement s =
        project.getMainJDBCConnection().prepareStatement("INSERT INTO nodes VALUES(?,?,?)")) {
      for (int i = 0; i <= count; i++) {
        List<Object> row = new ArrayList<>(List.of((double) (i + 1), 0.0, 0.0));
        nodes.addRecord(row);
        project.nodes.getEsriGraphicList().add(new EsriPoint(0, i * 0.001));
        for (int c = 0; c < row.size(); c++) {
          s.setObject(c + 1, row.get(c));
        }
        s.addBatch();
      }
      s.executeBatch();
    }
    try (PreparedStatement s =
        project
            .getMainJDBCConnection()
            .prepareStatement("INSERT INTO links VALUES(?,?,?,?,?,?,?,?,?,?)")) {
      for (int r = 0; r < count; r++) {
        int i = order == null ? r : order[r];
        List<Object> row = new ArrayList<>(template);
        row.set(NodusC.DBF_IDX_NUM, (double) (101 + i));
        row.set(NodusC.DBF_IDX_NODE1, (double) (i + 1));
        row.set(NodusC.DBF_IDX_NODE2, (double) (i + 2));
        links.addRecord(row);
        project
            .links
            .getEsriGraphicList()
            .add(
                new EsriPolyline(
                    new double[] {0, i * 0.001, 0, (i + 1) * 0.001},
                    OMGraphic.DECIMAL_DEGREES,
                    OMGraphic.LINETYPE_STRAIGHT));
        for (int c = 0; c < row.size(); c++) {
          s.setObject(c + 1, row.get(c));
        }
        s.addBatch();
      }
      s.executeBatch();
    }
    project.nodes.getIndex().clear();
    project.links.getIndex().clear();
    project.nodes.getNumIndex(1);
    project.links.getNumIndex(101);
    project.nodes.labelRefreshes = 0;
    project.links.labelRefreshes = 0;
    return project;
  }

  static Object simplifier(Class<?> type, NetworkTestProject project, String retain)
      throws Exception {
    Constructor<?> constructor = type.getDeclaredConstructor(NodusMapPanel.class, boolean.class);
    constructor.setAccessible(true);
    Object instance = constructor.newInstance(project.panel, false);
    set(instance, "dryRun", false);
    set(instance, "retainId", retain);
    return instance;
  }

  /**
   * Runs the same candidate/selection/application loop as simplifyJob, without its final dialog.
   */
  static int runPasses(Object simplifier, NetworkTestProject project) throws Exception {
    int passes = 0;
    while (true) {
      Object pairs =
          call(simplifier, "candidatePairs", project.links, List.of(), List.of(project.nodes));
      List<?> operations =
          (List<?>)
              call(
                  simplifier,
                  "selectMergeOperationsForPass",
                  project.links,
                  List.of(project.nodes),
                  pairs,
                  "links");
      if (operations.isEmpty()) {
        return passes;
      }
      call(
          simplifier,
          "applyMergeOperationsForPass",
          project.links,
          List.of(project.nodes),
          operations);
      passes++;
    }
  }

  private static Object call(Object object, String name, Object... args) throws Exception {
    for (Method method : object.getClass().getDeclaredMethods()) {
      if (method.getName().equals(name)) {
        method.setAccessible(true);
        try {
          return method.invoke(object, args);
        } catch (InvocationTargetException error) {
          throw new IllegalStateException(error.getCause());
        }
      }
    }
    throw new NoSuchMethodException(name);
  }

  private static void set(Object object, String name, Object value) throws Exception {
    Field field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(object, value);
  }
}
