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

package edu.uclouvain.core.nodus.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.Nodus;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.dbf.DbfFiles;
import edu.uclouvain.core.nodus.database.parquet.ExportParquet;
import edu.uclouvain.core.nodus.testing.MasterODScriptHarness;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Small end-to-end cases: real Parquet files, JDBC staging, loaded centroid models and Groovy. */
@ResourceLock("JDBCUtils")
class ODMatrixExtractorTest {
  @TempDir Path directory;
  private final List<Map<String, Object>> reports = new ArrayList<>();
  private Map<String, Object> catalog;

  private static MasterODScriptHarness compiledScript;

  private static synchronized MasterODScriptHarness scriptEngine() throws Exception {
    if (compiledScript == null) {
      compiledScript = new MasterODScriptHarness(Path.of("scripts", "ExtractMasterOD.groovy"));
    }
    return compiledScript;
  }

  @Test
  void odImplementationIsScriptLocalAndAbsentFromCore() throws Exception {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("edu.uclouvain.core.nodus.utils.ODMatrixExtractor"));
    assertEquals("ODMatrixExtractor", scriptEngine().getEngineClass().getName());
    assertEquals("", scriptEngine().getEngineClass().getPackageName());
  }

  @Test
  void separatelyCompiledScriptsSharePanelExecutionFlag() throws Exception {
    try (DatabaseFixture db = new DatabaseFixture(directory)) {
      Panel panel = new Panel(sampleProject(db));
      java.util.concurrent.atomic.AtomicBoolean active =
          new java.util.concurrent.atomic.AtomicBoolean(true);
      panel.storeObject("ExtractMasterOD.running", active);
      NodusGroovyShell first = new NodusGroovyShell();
      first.setVariable("nodusMapPanel", panel);
      IllegalStateException busy =
          assertThrows(
              IllegalStateException.class,
              () -> first.evaluate(Path.of("scripts", "ExtractMasterOD.groovy").toFile()));
      assertTrue(busy.getMessage().contains("already running"));
      assertTrue(active.get());
      active.set(false);
      Path sample = Path.of("scripts", "master-od-sample");
      Files.copy(sample.resolve("catalog.json"), directory.resolve("catalog.json"));
      Files.copy(sample.resolve("sample_road.parquet"), directory.resolve("sample_road.parquet"));
      NodusGroovyShell second = new NodusGroovyShell();
      second.setVariable("nodusMapPanel", panel);
      second.evaluate(Path.of("scripts", "ExtractMasterOD.groovy").toFile());
      assertFalse(active.get());
      assertEquals(expectedCells(3, "NSTR"), cells(db, "SAMPLEOD_L3_NSTR_ROAD"));
    }
  }

  @Test
  void nativeConsoleInterruptChecksWaitForWorkerCleanup() throws Exception {
    org.codehaus.groovy.control.CompilerConfiguration configuration =
        new org.codehaus.groovy.control.CompilerConfiguration();
    configuration.addCompilationCustomizers(
        new org.codehaus.groovy.control.customizers.ASTTransformationCustomizer(
            groovy.transform.ThreadInterrupt.class));
    String boundary = "    try {\n      for (; ; ) {";
    CountDownLatch workerStarted = new CountDownLatch(1);
    CountDownLatch workerMayFinish = new CountDownLatch(1);
    CountDownLatch atWait = new CountDownLatch(1);
    MasterODScriptHarness.setWaitHook(
        () -> {
          try {
            if (!workerStarted.await(10, TimeUnit.SECONDS)) {
              throw new AssertionError("Worker did not start");
            }
          } catch (InterruptedException e) {
            throw new AssertionError(e);
          }
          Thread.currentThread().interrupt();
          atWait.countDown();
        });
    try (DatabaseFixture db = fixture(false);
        MasterODScriptHarness console =
            new MasterODScriptHarness(
                Path.of("scripts", "ExtractMasterOD.groovy"),
                configuration,
                source -> {
                  assertTrue(source.contains(boundary));
                  return source.replace(
                      boundary,
                      "    try {\n"
                          + "      edu.uclouvain.core.nodus.testing."
                          + "MasterODScriptHarness.beforeWorkerWait();\n"
                          + "      for (; ; ) {");
                })) {
      sentinel(db);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread caller =
          new Thread(
              () -> {
                try {
                  console.extract(
                      project(db),
                      options(Map.of()),
                      message -> {
                        if (message.startsWith("Extracting")) {
                          workerStarted.countDown();
                          try {
                            if (!workerMayFinish.await(10, TimeUnit.SECONDS)) {
                              throw new AssertionError("cleanup timeout");
                            }
                          } catch (InterruptedException e) {
                            throw new AssertionError("Worker was interrupted", e);
                          }
                        }
                      },
                      reports::add);
                } catch (Throwable e) {
                  failure.set(e);
                }
              });
      caller.start();
      try {
        assertTrue(atWait.await(10, TimeUnit.SECONDS));
        caller.join(100);
        assertTrue(caller.isAlive(), "The interrupted caller must wait for its active worker");
      } finally {
        workerMayFinish.countDown();
        caller.join(10000);
      }
      assertFalse(caller.isAlive());
      assertInstanceOf(InterruptedException.class, failure.get());
      assertFalse(caller.isInterrupted());
      assertEquals("CANCELLED", reports.get(0).get("status"));
      assertSentinel(db);
      assertNoStagingTables(db);
    } finally {
      MasterODScriptHarness.setWaitHook(() -> {});
    }
  }

  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> resolutionsForBothEngines() {
    return java.util.stream.Stream.of(false, true)
        .flatMap(
            hsql ->
                java.util.stream.Stream.of(3, 2)
                    .flatMap(
                        level ->
                            java.util.stream.Stream.of("NSTR", "CHAPTER")
                                .map(
                                    split ->
                                        org.junit.jupiter.api.DynamicTest.dynamicTest(
                                            hsql + ":" + level + ":" + split,
                                            () ->
                                                conservesEveryCellForBothEmbeddedEngines(
                                                    hsql,
                                                    level,
                                                    split,
                                                    level == 3
                                                        ? (split.equals("NSTR") ? 6 : 5)
                                                        : (split.equals("NSTR") ? 3 : 2))))));
  }

  void conservesEveryCellForBothEmbeddedEngines(boolean hsql, int level, String split, int rows)
      throws Exception {
    try (DatabaseFixture db = fixture(hsql)) {
      final List<Map<String, Object>> result =
          extract(db, Map.of("nutsLevel", level, "commodityLevel", split));
      String table = "OD2031_L" + level + "_" + split + "_ROAD";
      Map<String, Double> actual = cells(db, table);
      Map<String, Double> expected = expectedCells(level, split);
      assertEquals(expected, actual);
      assertEquals(rows, actual.size());
      Map<String, Object> report = reports.get(0);
      assertEquals("PUBLISHED", report.get("status"));
      assertEquals(26.0, report.get("exported_available_tonnes"));
      assertEquals(3L, report.get("selected_unestimated_components"));
      assertEquals(1L, report.get("source_rows_without_available_quantity"));
      assertEquals(1L, report.get("zero_available_source_rows"));
      assertEquals(2.75, report.get("selected_proxy_available_tonnes"));
      assertEquals(2.75, report.get("selected_spatial_review_available_tonnes"));
      assertEquals(List.of(99), report.get("source_absent_detailed_nstr_groups_in_selection"));
      assertEquals(List.of(table), ODReader.getValidODTables(project(db)));
      assertFalse(db.connection.isClosed());
      assertThrows(UnsupportedOperationException.class, () -> result.add(Map.of()));
      assertThrows(UnsupportedOperationException.class, () -> result.get(0).put("x", 1));
      assertThrows(UnsupportedOperationException.class, () -> report.put("x", 1));
      assertNoStagingTables(db);
    }
  }

  @Test
  void filtersChapterZeroAndReportsAbsentGroupWithoutInventingRows() throws Exception {
    try (DatabaseFixture db = fixture(false)) {
      extract(db, Map.of("commodityLevel", "CHAPTER", "groups", List.of(0)));
      assertEquals(13.0, reports.get(0).get("exported_available_tonnes"));
      assertEquals(
          List.of(), reports.get(0).get("source_absent_detailed_nstr_groups_in_selection"));
      extract(db, Map.of("groups", List.of(99)));
      assertEquals(Map.of(), cells(db, "OD2031_L3_NSTR_ROAD"));
      assertEquals(
          List.of(99), reports.get(1).get("source_absent_detailed_nstr_groups_in_selection"));
      assertEquals(0.0, reports.get(1).get("exported_available_tonnes"));
    }
  }

  @Test
  void strictPolicyAcceptsCompleteSelectionAndRejectsMissingAndAbsentSelections() throws Exception {
    try (DatabaseFixture db = fixture(false)) {
      extract(db, Map.of("groups", List.of(10), "missingPolicy", "REQUIRE_COMPLETE"));
      Map<String, Double> before = cells(db, "OD2031_L3_NSTR_ROAD");
      for (int group : new int[] {0, 1, 99}) {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                extract(db, Map.of("groups", List.of(group), "missingPolicy", "REQUIRE_COMPLETE")));
        assertEquals(before, cells(db, "OD2031_L3_NSTR_ROAD"));
      }
      assertEquals(1, reports.size());
    }
  }

  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> failuresForBothEngines() {
    return java.util.stream.Stream.of(false, true)
        .map(
            hsql ->
                org.junit.jupiter.api.DynamicTest.dynamicTest(
                    "staged recovery:" + hsql,
                    () -> lateNegativeOrUnorderedSourceLeavesExistingTableIntact(hsql)));
  }

  void lateNegativeOrUnorderedSourceLeavesExistingTableIntact(boolean hsql) throws Exception {
    try (DatabaseFixture db = fixture(hsql)) {
      sentinel(db);
      for (String bad : List.of("negative", "unsorted", "schema", "parent", "required")) {
        if (bad.equals("negative")) {
          db.execute("UPDATE master SET QTY_AVAILABLE_PART=-1 WHERE GRP=10 AND ORG=102");
        }
        if (bad.equals("unsorted")) {
          db.execute("UPDATE master SET GRP=1,CHAPTER=0 WHERE GRP=10 AND ORG=102");
        }
        if (bad.equals("schema")) {
          db.execute("ALTER TABLE master DROP COLUMN SPATIAL_REVIEW");
        }
        if (bad.equals("parent")) {
          db.execute("UPDATE master SET ORG_L2=20 WHERE ORG=101");
        }
        if (bad.equals("required")) {
          db.execute("UPDATE master SET GRP=NULL WHERE GRP=0 AND ORG=101 AND DST=101");
        }
        export(db, false);
        assertThrows(Exception.class, () -> extract(db, Map.of()));
        assertSentinel(db);
        assertNoStagingTables(db);
        restoreMaster(db);
      }
      assertEquals(5, reports.size());
      assertTrue(reports.stream().allMatch(r -> r.get("status").equals("FAILED")));
    }
  }

  @Test
  void detectsSourceReplacementAndAllModeHashesBeforePublication() throws Exception {
    try (DatabaseFixture db = fixture(false)) {
      sentinel(db);
      Map<String, Object> config = options(Map.of());
      assertThrows(
          Exception.class,
          () ->
              scriptEngine()
                  .extract(
                      project(db),
                      config,
                      message -> {
                        if (message.startsWith("Extracting")) {
                          try {
                            Files.setLastModifiedTime(
                                directory.resolve("master.parquet"), FileTime.fromMillis(1));
                          } catch (IOException e) {
                            throw new RuntimeException(e);
                          }
                        }
                      },
                      reports::add));
      assertSentinel(db);
      Map<String, Object> mode = new LinkedHashMap<>(modeInfo());
      mode.put("sha256", "0".repeat(64));
      catalog.put("modes", Map.of("road", modeInfo(), "rail", mode));
      writeCatalog();
      assertThrows(IOException.class, () -> extract(db, Map.of("modes", List.of("road", "rail"))));
      assertSentinel(db);
      assertEquals(1, reports.size());
    }
  }

  @Test
  void checksLoadedCentroidCodeParentAndLevelIncludingZeroRows() throws Exception {
    try (DatabaseFixture db = fixture(false)) {
      sentinel(db);
      for (int field : new int[] {1, 2, 3}) {
        NodusProject p = project(db);
        p.getNodeLayers()[0].getModel().setValueAt(field == 2 ? "wrong" : 99, 4, field);
        assertThrows(
            IllegalArgumentException.class,
            () ->
                scriptEngine()
                    .extract(p, options(Map.of("groups", List.of(0))), s -> {}, reports::add));
        assertSentinel(db);
      }
    }
  }

  @Test
  void rejectsInvalidOptionsAndCatalogWithClearErrors() throws Exception {
    try (DatabaseFixture db = fixture(false)) {
      for (Map<String, Object> invalid :
          List.<Map<String, Object>>of(
              Map.of("nutsLevel", 2.5),
              Map.of("nutsLevel", 1),
              Map.of("groups", List.of(0.1)),
              Map.of("groups", List.of(2)),
              Map.of("groups", "0"),
              Map.of("modes", List.of("road", "road")),
              Map.of("modes", List.of()),
              Map.of("modes", List.of("sea")),
              Map.of("verifyHashes", "false"),
              Map.of("batchSize", 0),
              Map.of("tablePrefix", "DROP;"),
              Map.of("commodityLevel", "NST2007"),
              Map.of("missingPolicy", "ZERO"),
              Map.of("typo", true))) {
        assertThrows(
            IllegalArgumentException.class, () -> extract(db, invalid), invalid.toString());
      }
      assertThrows(
          IllegalArgumentException.class,
          () -> scriptEngine().extract(project(db), Map.of(), s -> {}, reports::add));
      catalog.put("schema_version", 2);
      writeCatalog();
      assertThrows(IllegalArgumentException.class, () -> extract(db, Map.of()));
      assertTrue(reports.isEmpty());
    }
  }

  @Test
  void cancellationAndConcurrentCallLeaveDatabaseUsable() throws Exception {
    try (DatabaseFixture db = fixture(false)) {
      sentinel(db);
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch resume = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread caller =
          new Thread(
              () -> {
                try {
                  scriptEngine()
                      .extract(
                          project(db),
                          options(Map.of()),
                          message -> {
                            if (message.startsWith("Extracting")) {
                              started.countDown();
                              try {
                                if (!resume.await(10, TimeUnit.SECONDS)) {
                                  throw new AssertionError("timeout");
                                }
                              } catch (InterruptedException e) {
                                throw new AssertionError("JDBC worker interrupted", e);
                              }
                            }
                          },
                          reports::add);
                } catch (Throwable e) {
                  failure.set(e);
                }
              });
      caller.start();
      try {
        assertTrue(started.await(10, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, () -> extract(db, Map.of()));
        caller.interrupt();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (caller.isInterrupted() && System.nanoTime() < deadline) {
          Thread.yield();
        }
        assertFalse(caller.isInterrupted(), "Caller must consume interruption cooperatively");
      } finally {
        resume.countDown();
        caller.join(10000);
      }
      assertFalse(caller.isAlive());
      assertInstanceOf(InterruptedIOException.class, failure.get());
      assertEquals("CANCELLED", reports.get(0).get("status"));
      assertSentinel(db);
      assertNoStagingTables(db);
      extract(db, Map.of("groups", List.of(10)));
      assertEquals(2, cells(db, "OD2031_L3_NSTR_ROAD").size());
    }
  }

  @Test
  void reportFailurePreservesOriginalErrorOrPublishedTable() throws Exception {
    try (DatabaseFixture db = fixture(false)) {
      Consumer<Map<String, Object>> brokenSink =
          r -> {
            throw new IllegalStateException("report failure");
          };
      assertThrows(
          IllegalStateException.class,
          () -> scriptEngine().extract(project(db), options(Map.of()), s -> {}, brokenSink));
      assertEquals(6, cells(db, "OD2031_L3_NSTR_ROAD").size());
      db.execute("UPDATE master SET QTY_AVAILABLE_PART=-1 WHERE GRP=10 AND ORG=102");
      export(db, false);
      Exception error =
          assertThrows(
              Exception.class,
              () -> scriptEngine().extract(project(db), options(Map.of()), s -> {}, brokenSink));
      assertTrue(error.getMessage().contains("negative"));
      assertEquals("report failure", error.getSuppressed()[0].getMessage());
      assertEquals(6, cells(db, "OD2031_L3_NSTR_ROAD").size());
    }
  }

  @Test
  void suppliedGroovyScriptUsesCoreApiAndWritesDefaultReports() throws Exception {
    try (DatabaseFixture db = fixture(false)) {
      NodusGroovyShell shell = new NodusGroovyShell();
      shell.setVariable("nodusMapPanel", new Panel(project(db)));
      shell.setVariable(
          "odExtractionConfig",
          options(Map.of("commodityLevel", "CHAPTER", "nutsLevel", 2, "tablePrefix", "OD2031")));
      runScriptWithOverrides(shell);
      assertEquals(Map.of("0:10:10", 13.0, "1:10:20", 13.0), cells(db, "OD2031_L2_CHAPTER_ROAD"));
      try (java.util.stream.Stream<Path> files =
          Files.list(directory.resolve("ODExtractionReports"))) {
        List<Path> paths = files.collect(java.util.stream.Collectors.toList());
        assertEquals(1, paths.size());
        Map<?, ?> report = (Map<?, ?>) new JsonSlurper().parse(paths.get(0).toFile(), "UTF-8");
        assertEquals("PUBLISHED", report.get("status"));
        assertEquals(2031, report.get("year"));
        assertEquals(List.of(99), report.get("source_absent_detailed_nstr_groups_in_selection"));
      }
    }
  }

  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> bundledSampleResolutions() {
    return java.util.stream.Stream.of(3, 2)
        .flatMap(
            level ->
                java.util.stream.Stream.of("NSTR", "CHAPTER")
                    .map(
                        split ->
                            org.junit.jupiter.api.DynamicTest.dynamicTest(
                                "bundled sample:" + level + ":" + split,
                                () -> {
                                  try (DatabaseFixture db = new DatabaseFixture(directory)) {
                                    Map<String, Object> config = new LinkedHashMap<>();
                                    config.put(
                                        "masterDirectory",
                                        Path.of("scripts", "master-od-sample").toString());
                                    config.put("tablePrefix", "SAMPLE");
                                    config.put("nutsLevel", level);
                                    config.put("commodityLevel", split);
                                    List<Map<String, Object>> result =
                                        scriptEngine()
                                            .extract(
                                                sampleProject(db),
                                                config,
                                                message -> {},
                                                report -> {});
                                    assertEquals(
                                        expectedCells(level, split),
                                        cells(db, "SAMPLE_L" + level + "_" + split + "_ROAD"));
                                    assertEquals(
                                        26.0, result.get(0).get("exported_available_tonnes"));
                                    assertEquals(
                                        3L, result.get(0).get("selected_unestimated_components"));
                                    assertEquals(
                                        List.of(99),
                                        result
                                            .get(0)
                                            .get(
                                                "source_absent_detailed_nstr_groups_in_selection"));
                                  }
                                })));
  }

  @Test
  void javaStyleScriptUsesOpenProjectDirectoryWithoutOverrides() throws Exception {
    final Path projectDirectory = Files.createDirectory(directory.resolve("project with spaces é"));
    Path sample = Path.of("scripts", "master-od-sample");
    Files.copy(
        sample.resolve("sample_road.parquet"), projectDirectory.resolve("sample_road.parquet"));
    Map<String, Object> localCatalog =
        new LinkedHashMap<>(readJson(sample.resolve("catalog.json")));
    localCatalog.put("release", "copied_project_sample");
    Files.writeString(projectDirectory.resolve("catalog.json"), JsonOutput.toJson(localCatalog));
    try (DatabaseFixture db = new DatabaseFixture(projectDirectory)) {
      NodusGroovyShell shell = new NodusGroovyShell();
      shell.setVariable("nodusMapPanel", new Panel(sampleProject(db)));
      String source = Files.readString(Path.of("scripts", "ExtractMasterOD.groovy"));
      assertTrue(source.stripTrailing().endsWith("new ExtractMasterOD(nodusMapPanel);"));
      shell.evaluate(Path.of("scripts", "ExtractMasterOD.groovy").toFile());
      assertEquals(expectedCells(3, "NSTR"), cells(db, "SAMPLEOD_L3_NSTR_ROAD"));
      try (java.util.stream.Stream<Path> files =
          Files.list(projectDirectory.resolve("ODExtractionReports"))) {
        Path file = files.findFirst().orElseThrow();
        Map<?, ?> report = readJson(file);
        assertEquals(2024, report.get("year"));
        assertEquals("copied_project_sample", report.get("master_release"));
        assertEquals(
            projectDirectory.resolve("sample_road.parquet").toAbsolutePath().normalize().toString(),
            report.get("source_file"));
      }
    }
  }

  @Test
  void missingProjectDirectoryRequiresExplicitOverride() throws Exception {
    try (DatabaseFixture db = new DatabaseFixture(directory)) {
      NodusProject delegate = sampleProject(db);
      NodusProject project =
          new NodusProject(null) {
            @Override
            public boolean isOpen() {
              return true;
            }

            @Override
            public Connection getMainJDBCConnection() {
              return db.connection;
            }

            @Override
            public NodusEsriLayer[] getNodeLayers() {
              return delegate.getNodeLayers();
            }

            @Override
            public String getLocalProperty(String key) {
              return NodusC.PROP_PROJECT_DOTPATH.equals(key)
                  ? null
                  : delegate.getLocalProperty(key);
            }

            @Override
            public String getLocalProperty(String key, String fallback) {
              return NodusC.PROP_PROJECT_DOTPATH.equals(key)
                  ? null
                  : delegate.getLocalProperty(key, fallback);
            }
          };
      NodusGroovyShell shell = new NodusGroovyShell();
      shell.setVariable("nodusMapPanel", new Panel(project));
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> shell.evaluate(Path.of("scripts", "ExtractMasterOD.groovy").toFile()));
      assertTrue(failure.getMessage().contains("no directory"));
      shell.setVariable(
          "odExtractionConfig",
          Map.of(
              "masterDirectory",
              Path.of("scripts", "master-od-sample").toAbsolutePath().toString(),
              "reportDirectory",
              directory.resolve("reports").toString()));
      runScriptWithOverrides(shell);
      assertEquals(expectedCells(3, "NSTR"), cells(db, "SAMPLEOD_L3_NSTR_ROAD"));
    }
  }

  @Test
  void mapPanelEntryPointRequiresOpenProject() throws Exception {
    NodusProject closed =
        new NodusProject(null) {
          @Override
          public boolean isOpen() {
            return false;
          }
        };
    for (NodusProject project : new NodusProject[] {null, closed}) {
      NodusGroovyShell shell = new NodusGroovyShell();
      shell.setVariable("nodusMapPanel", new Panel(project));
      assertThrows(
          IllegalStateException.class,
          () -> shell.evaluate(Path.of("scripts", "ExtractMasterOD.groovy").toFile()));
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJson(Path file) {
    return (Map<String, Object>) new JsonSlurper().parse(file.toFile(), "UTF-8");
  }

  private void runScriptWithOverrides(NodusGroovyShell shell) throws Exception {
    String source = Files.readString(Path.of("scripts", "ExtractMasterOD.groovy"));
    assertTrue(source.stripTrailing().endsWith("new ExtractMasterOD(nodusMapPanel);"));
    String explicitCall =
        source.replace(
            "new ExtractMasterOD(nodusMapPanel);",
            "new ExtractMasterOD(nodusMapPanel, odExtractionConfig);");
    shell.run(explicitCall, "ExtractMasterOD.groovy", new String[0]);
  }

  private NodusProject sampleProject(DatabaseFixture db) throws Exception {
    NodusEsriLayer[] layers = new NodusEsriLayer[2];
    for (int i = 0; i < layers.length; i++) {
      final DbfTableModel model =
          DbfFiles.readModel(
                  Path.of("scripts", "master-od-sample", "centroidsl" + (i + 2) + ".dbf"), null)
              .model;
      layers[i] =
          new NodusEsriLayer() {
            @Override
            public DbfTableModel getModel() {
              return model;
            }
          };
    }
    return project(db, layers);
  }

  @Test
  void worksWithInstalledRuntimeLibrariesOnly() throws Exception {
    List<String> cp = new ArrayList<>();
    cp.add(
        Path.of(Nodus.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString());
    cp.add(
        Path.of(
                ODMatrixExtractorTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .toString());
    try (java.util.stream.Stream<Path> jars = Files.walk(Path.of("lib"))) {
      jars.filter(p -> p.toString().endsWith(".jar"))
          .sorted()
          .forEach(p -> cp.add(p.toAbsolutePath().toString()));
    }
    Path log = directory.resolve("runtime.log");
    Process child =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true",
                "-cp",
                String.join(File.pathSeparator, cp),
                ODMatrixExtractorTest.class.getName(),
                directory.toString())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    try {
      assertTrue(child.waitFor(40, TimeUnit.SECONDS), "Runtime extraction timed out");
      assertEquals(0, child.exitValue(), Files.readString(log));
    } finally {
      if (child.isAlive()) {
        child.destroyForcibly();
      }
    }
  }

  /**
   * Runs the real Groovy example with installed libraries, without a GUI or live project.
   *
   * @param args temporary test directory
   * @throws Exception on extraction failure
   */
  public static void main(String[] args) throws Exception {
    for (String absent :
        List.of(
            "org.apache.hadoop.conf.Configuration",
            "org.duckdb.DuckDBDriver",
            "edu.uclouvain.core.nodus.utils.ODMatrixExtractor")) {
      assertThrows(ClassNotFoundException.class, () -> Class.forName(absent));
    }
    ODMatrixExtractorTest test = new ODMatrixExtractorTest();
    test.directory = Path.of(args[0]);
    test.suppliedGroovyScriptUsesCoreApiAndWritesDefaultReports();
    test.directory = Files.createDirectory(test.directory.resolve("bundled_sample"));
    test.javaStyleScriptUsesOpenProjectDirectoryWithoutOverrides();
  }

  /** Groovy-visible panel substitute, with no GUI or live project. */
  public static final class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    private final NodusProject project;

    /** Wraps the isolated test project for the script binding. */
    public Panel(NodusProject project) {
      this.project = project;
    }

    /** Returns the isolated project without opening a GUI. */
    @Override
    public NodusProject getNodusProject() {
      return project;
    }
  }

  private DatabaseFixture fixture(boolean hsql) throws Exception {
    reports.clear();
    DatabaseFixture db = new DatabaseFixture(directory, hsql);
    restoreMaster(db);
    catalog = new LinkedHashMap<>();
    catalog.put("schema_version", 1);
    catalog.put("year", 2031);
    catalog.put("release", "test");
    catalog.put("source_release", "synthetic");
    catalog.put("unit", "annual tonnes");
    catalog.put("supported_nuts_levels", List.of(2, 3));
    catalog.put("classifications", List.of("NSTR", "CHAPTER"));
    catalog.put(
        "commodities",
        List.of(
            Map.of("id", 0, "chapter", 0),
            Map.of("id", 1, "chapter", 0),
            Map.of("id", 10, "chapter", 1),
            Map.of("id", 99, "chapter", 9)));
    List<Map<String, Object>> zones = new ArrayList<>();
    for (Object[] z : zoneRows()) {
      zones.add(Map.of("id", z[0], "level", z[1], "code", z[2], "parent", z[3]));
    }
    catalog.put("zones", zones);
    export(db, true);
    return db;
  }

  private void restoreMaster(DatabaseFixture db) throws Exception {
    db.execute("DROP VIEW IF EXISTS ordered_master");
    db.execute("DROP TABLE IF EXISTS master");
    db.execute(
        "CREATE TABLE master (GRP INTEGER, ORG BIGINT,DST BIGINT,ORG_L2 BIGINT,DST_L2 BIGINT,"
            + "CHAPTER INTEGER,QTY_AVAILABLE_PART DOUBLE,UNESTIMATED_CELLS INTEGER,"
            + "PROXY_APPLIED BOOLEAN,"
            + "PROXY_QTY DOUBLE,SPATIAL_REVIEW BOOLEAN)");
    db.execute(
        "INSERT INTO master VALUES (0,101,101,10,10,0,1.25,0,FALSE,0,FALSE),"
            + "(0,101,102,10,10,0,2.75,0,TRUE,2.75,TRUE),"
            + "(0,102,201,10,20,0,NULL,1,FALSE,NULL,TRUE),"
            + "(0,201,101,20,10,0,0,0,FALSE,0,FALSE),(1,101,102,10,10,0,4,0,FALSE,0,FALSE),"
            + "(1,102,101,10,10,0,5,2,FALSE,0,FALSE),(10,101,201,10,20,1,6,0,FALSE,0,FALSE),"
            + "(10,102,201,10,20,1,7,0,FALSE,0,FALSE)");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> modeInfo() {
    return (Map<String, Object>) ((Map<?, ?>) catalog.get("modes")).get("road");
  }

  private void export(DatabaseFixture db, boolean sorted) throws Exception {
    // ExportParquet preserves SELECT order from this view. Bad-key tests deliberately preserve
    // input order.
    db.execute("DROP VIEW IF EXISTS ordered_master");
    db.execute(
        "CREATE VIEW ordered_master AS SELECT * FROM master"
            + (sorted ? " ORDER BY GRP,ORG,DST" : ""));
    assertTrue(ExportParquet.exportTable(db.project, "ordered_master"));
    Files.move(
        directory.resolve("ordered_master.parquet"),
        directory.resolve("master.parquet"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    Map<String, Object> mode = new LinkedHashMap<>();
    mode.put("file", "master.parquet");
    mode.put("bytes", Files.size(directory.resolve("master.parquet")));
    mode.put(
        "sha256",
        String.format(
            java.util.Locale.ROOT,
            "%064x",
            new java.math.BigInteger(
                1,
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(directory.resolve("master.parquet"))))));
    mode.put(
        "groups",
        List.of(
            Map.of("id", 0, "unestimated_components", 1, "unavailable_quantity_rows", 1),
            Map.of("id", 1, "unestimated_components", 2, "unavailable_quantity_rows", 0),
            Map.of("id", 10, "unestimated_components", 0, "unavailable_quantity_rows", 0)));
    mode.put("source_absent_nstr_groups", List.of(99));
    catalog.put("modes", new LinkedHashMap<>(Map.of("road", mode)));
    writeCatalog();
  }

  private void writeCatalog() throws IOException {
    Files.writeString(directory.resolve("catalog.json"), JsonOutput.toJson(catalog));
  }

  private List<Object[]> zoneRows() {
    return List.of(
        new Object[] {10L, 2, "AA01", 0L},
        new Object[] {20L, 2, "AA02", 0L},
        new Object[] {101L, 3, "AA011", 10L},
        new Object[] {102L, 3, "AA012", 10L},
        new Object[] {201L, 3, "AA021", 20L});
  }

  private NodusProject project(DatabaseFixture db) {
    DbfTableModel model = new DbfTableModel(4);
    String[] names = {"NUM", "LEVEL", "ZONE_CODE", "PARENT_ID"};
    for (int i = 0; i < names.length; i++) {
      model.setColumnName(i, names[i]);
    }
    for (Object[] row : zoneRows()) {
      model.addRecord(new ArrayList<>(List.of(row)));
    }
    NodusEsriLayer layer =
        new NodusEsriLayer() {
          @Override
          public DbfTableModel getModel() {
            return model;
          }
        };
    return project(db, new NodusEsriLayer[] {layer});
  }

  private NodusProject project(DatabaseFixture db, NodusEsriLayer[] layers) {
    return new NodusProject(null) {
      @Override
      public boolean isOpen() {
        return true;
      }

      @Override
      public Connection getMainJDBCConnection() {
        return db.connection;
      }

      @Override
      public NodusEsriLayer[] getNodeLayers() {
        return layers;
      }

      @Override
      public String getProject() {
        throw new AssertionError("Must not open project chooser");
      }

      @Override
      public String getLocalProperty(String key) {
        return db.project.getLocalProperty(key);
      }

      @Override
      public String getLocalProperty(String key, String fallback) {
        return db.project.getLocalProperty(key, fallback);
      }
    };
  }

  private Map<String, Double> expectedCells(int level, String split) {
    Map<String, Double> expected = new LinkedHashMap<>();
    if (level == 3 && split.equals("NSTR")) {
      expected.put("0:101:101", 1.25);
      expected.put("0:101:102", 2.75);
      expected.put("1:101:102", 4.0);
      expected.put("1:102:101", 5.0);
      expected.put("10:101:201", 6.0);
      expected.put("10:102:201", 7.0);
    } else if (level == 2 && split.equals("NSTR")) {
      expected.put("0:10:10", 4.0);
      expected.put("1:10:10", 9.0);
      expected.put("10:10:20", 13.0);
    } else if (level == 3) {
      expected.put("0:101:101", 1.25);
      expected.put("0:101:102", 6.75);
      expected.put("0:102:101", 5.0);
      expected.put("1:101:201", 6.0);
      expected.put("1:102:201", 7.0);
    } else {
      expected.put("0:10:10", 13.0);
      expected.put("1:10:20", 13.0);
    }
    return expected;
  }

  private Map<String, Object> options(Map<String, Object> extra) {
    Map<String, Object> options = new LinkedHashMap<>();
    options.put("masterDirectory", directory.toString());
    options.putAll(extra);
    return options;
  }

  private List<Map<String, Object>> extract(DatabaseFixture db, Map<String, Object> extra)
      throws Exception {
    return scriptEngine().extract(project(db), options(extra), s -> {}, reports::add);
  }

  private Map<String, Double> cells(DatabaseFixture db, String table) throws Exception {
    Map<String, Double> values = new LinkedHashMap<>();
    try (Statement st = db.connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT GRP,ORG,DST,QTY FROM " + table)) {
      while (rs.next()) {
        assertNull(
            values.put(rs.getInt(1) + ":" + rs.getLong(2) + ":" + rs.getLong(3), rs.getDouble(4)));
      }
    }
    return values;
  }

  private void sentinel(DatabaseFixture db) throws Exception {
    db.execute("CREATE TABLE OD2031_L3_NSTR_ROAD (GRP SMALLINT,ORG BIGINT,DST BIGINT,QTY DOUBLE)");
    db.execute("INSERT INTO OD2031_L3_NSTR_ROAD VALUES(9,101,201,123)");
  }

  private void assertSentinel(DatabaseFixture db) throws Exception {
    assertEquals(Map.of("9:101:201", 123.0), cells(db, "OD2031_L3_NSTR_ROAD"));
    assertFalse(db.connection.isClosed());
  }

  private void assertNoStagingTables(DatabaseFixture db) throws Exception {
    try (ResultSet rs =
        db.connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
      while (rs.next()) {
        String name = rs.getString("TABLE_NAME");
        assertFalse(name.startsWith("NDI_") || name.startsWith("NDB_"), name);
      }
    }
  }
}
