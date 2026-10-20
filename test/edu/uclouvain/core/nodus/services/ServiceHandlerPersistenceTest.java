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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Saves through the service manager and reloads using actual database readers. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class ServiceHandlerPersistenceTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> importsServiceTablesAfterProjectOpened() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    hsql ? "HSQLDB late service import" : "H2 late service import",
                    () -> {
                      try (NetworkTestProject project = project(hsql)) {
                        ServiceHandler handler = project.getServiceHandler();
                        assertTrue(names(handler).isEmpty());
                        handler.loadServicesForEditor();
                        assertTrue(names(handler).isEmpty());

                        handler.resetServicesTables();
                        Connection connection = project.getMainJDBCConnection();
                        execute(
                            connection,
                            "INSERT INTO "
                                + handler.getServiceHeaderTableName()
                                + " (id,name,mode,means,frequency)"
                                + " VALUES (7,'Imported',3,1,365)");
                        execute(
                            connection,
                            "INSERT INTO "
                                + handler.getServiceLinkDetailTableName()
                                + " (id,pathidx,link) VALUES (7,0,11)");
                        execute(
                            connection,
                            "INSERT INTO "
                                + handler.getServiceStopDetailTableName()
                                + " (id,stop) VALUES (7,1),(7,2)");

                        handler.loadServicesForEditor();
                        TransportService imported = handler.getService("Imported");
                        assertEquals(List.of("Imported"), names(handler));
                        assertEquals(365, imported.getFrequency());
                        assertEquals(1, imported.getLinks().size());
                        assertEquals(List.of(1, 2), imported.getStopNodes());
                        handler.loadServicesForEditor();
                        assertSame(imported, handler.getService("Imported"));

                        handler.removeService("Imported");
                        handler.loadServicesForEditor();
                        assertTrue(names(handler).isEmpty(), "Pending deletion must not be undone");
                        handler.discardPendingChanges();
                        assertEquals(List.of("Imported"), names(handler));
                        assertTrue(project.serviceErrors.isEmpty());
                      }
                    }));
  }

  @Test
  void changedNamesStopsAndFrequencyReplaceOldRowsWithoutDuplicates() throws Exception {
    try (NetworkTestProject project = project()) {
      assertTrue(project.nodes.addRecord(new EsriPoint(0, 5), 3, false));
      project.nodes.getModel().setValueAt(1.0, 2, NodusC.DBF_IDX_TRANSHIP);
      project.links.getModel().setValueAt(3.0, 0, NodusC.DBF_IDX_NODE2);
      ((OMPoly) project.links.getEsriGraphicList().getOMGraphicAt(0))
          .setLocation(new double[] {0, 0, 0, 5}, OMGraphic.DECIMAL_DEGREES);
      assertTrue(
          project.links.addRecord(
              new EsriPolyline(
                  new double[] {0, 5, 0, 10},
                  OMGraphic.DECIMAL_DEGREES,
                  OMGraphic.LINETYPE_STRAIGHT),
              12,
              3,
              2,
              false));
      project.links.getModel().setValueAt(3.0, 1, NodusC.DBF_IDX_MODE);
      project.links.getModel().setValueAt(1.0, 1, NodusC.DBF_IDX_MEANS);
      final ServiceHandler handler = project.getServiceHandler();
      handler.createOrReplaceServiceFromLinkIds(
          1, "Original", 3, 1, 365, List.of(11, 12), List.of(1, 2), false, true);
      final TransportService changed =
          handler.createOrReplaceServiceFromLinkIds(
              1, "L'été – 東京", 3, 1, 730, List.of(11, 12), List.of(1, 3, 2), true, true);
      List<List<String>> saved = snapshot(project);
      assertTrue(handler.savePendingChanges());
      assertEquals(saved, snapshot(project));
      try (ManagerClose reloaded = new ManagerClose(project.newServiceHandler())) {
        TransportService actual = reloaded.handler.getService("L'été – 東京");
        assertNotSame(changed, actual);
        assertEquals(730, actual.getFrequency());
        assertEquals(changed.getLinks(), actual.getLinks());
        assertEquals(List.of(1, 3, 2), actual.getStopNodes());
        assertEquals(List.of("L'été – 東京"), names(reloaded.handler));
      }
      handler.removeService("L'été – 東京");
      assertTrue(handler.savePendingChanges());
      assertTrue(snapshot(project).isEmpty());
      try (ManagerClose empty = new ManagerClose(project.newServiceHandler())) {
        assertTrue(names(empty.handler).isEmpty());
      }
      assertTrue(project.serviceErrors.isEmpty());
    }
  }

  @TestFactory
  Stream<DynamicTest> failedReplacementPreservesSavedDataAndCanBeRetried() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(false, true)
                    .map(
                        autoCommit ->
                            DynamicTest.dynamicTest(
                                (hsql ? "HSQLDB" : "H2") + ", autoCommit=" + autoCommit,
                                () -> {
                                  try (NetworkTestProject project = project(hsql)) {
                                    final ServiceHandler handler = project.getServiceHandler();
                                    final TransportService existing =
                                        handler.createOrReplaceServiceFromLinkIds(
                                            1,
                                            "Saved",
                                            3,
                                            1,
                                            365,
                                            List.of(11),
                                            List.of(1, 2),
                                            false,
                                            true);
                                    final List<List<String>> before = snapshot(project);
                                    final Connection connection = project.getMainJDBCConnection();
                                    execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
                                    connection.setAutoCommit(autoCommit);
                                    execute(connection, "INSERT INTO unrelated VALUES(42)");
                                    existing.setFrequency(730);
                                    final String tooLong = "Z".repeat(40);
                                    TransportService invalid =
                                        new TransportService(2, tooLong, (byte) 3, (byte) 1, 365);
                                    invalid.addChunk(existing.getLinks().getFirst());
                                    invalid.addStop(1);
                                    invalid.addStop(2);
                                    handler.saveService(invalid);
                                    handler.mustBeSaved();
                                    project.setLocalProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "1");
                                    project.captureServiceErrors = true;
                                    assertFalse(handler.savePendingChanges());
                                    assertEquals(1, project.serviceErrors.size());
                                    assertEquals(
                                        before,
                                        snapshot(project),
                                        "A failed replacement must retain the last saved services");
                                    assertEquals(autoCommit, connection.getAutoCommit());
                                    assertEquals(
                                        List.of(List.of("42")),
                                        rows(connection, "SELECT * FROM unrelated"));
                                    if (!autoCommit) {
                                      connection.rollback();
                                      assertTrue(
                                          rows(connection, "SELECT * FROM unrelated").isEmpty(),
                                          "Saving must not commit earlier work before failing");
                                    }
                                    handler.removeService(tooLong);
                                    assertTrue(handler.savePendingChanges());
                                    try (ManagerClose reloaded =
                                        new ManagerClose(project.newServiceHandler())) {
                                      assertEquals(List.of("Saved"), names(reloaded.handler));
                                      assertEquals(
                                          730, reloaded.handler.getService("Saved").getFrequency());
                                    }
                                    assertEquals(1, project.serviceErrors.size());
                                  }
                                })));
  }

  @Test
  void failedLoadDoesNotPublishHeadersWithoutTheirDetails() throws Exception {
    try (NetworkTestProject project = project()) {
      final ServiceHandler handler = project.getServiceHandler();
      handler.createOrReplaceServiceFromLinkIds(
          1, "Saved", 3, 1, 365, List.of(11), List.of(1, 2), false, true);
      final Connection connection = project.getMainJDBCConnection();
      final String stops = handler.getServiceStopDetailTableName();
      execute(connection, "ALTER TABLE " + stops + " RENAME TO held_stops");
      project.captureServiceErrors = true;
      try (ManagerClose failed = new ManagerClose(project.newServiceHandler())) {
        assertTrue(names(failed.handler).isEmpty());
      }
      assertEquals(1, project.serviceErrors.size());
      execute(connection, "ALTER TABLE held_stops RENAME TO " + stops);
      try (ManagerClose retry = new ManagerClose(project.newServiceHandler())) {
        assertEquals(List.of("Saved"), names(retry.handler));
        assertEquals(List.of(1, 2), retry.handler.getService("Saved").getStopNodes());
      }
      assertEquals(1, project.serviceErrors.size());
      assertFalse(connection.isClosed());
    }
  }

  @TestFactory
  Stream<DynamicTest> failedFirstSavesAndSchemaMigrationsDoNotCommitEarlierWork() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of("missing", "partial", "legacy")
                    .flatMap(
                        schema ->
                            Stream.of(false, true)
                                .map(
                                    autoCommit ->
                                        DynamicTest.dynamicTest(
                                            hsql + "/" + schema + "/" + autoCommit,
                                            () ->
                                                checkSchemaSaveFailure(
                                                    hsql, schema, autoCommit)))));
  }

  private void checkSchemaSaveFailure(boolean hsql, String schema, boolean autoCommit)
      throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      final ServiceHandler handler = project.getServiceHandler();
      prepareSchema(project, schema);
      final Connection connection = project.getMainJDBCConnection();
      final List<List<String>> before = existingServiceRows(project);
      execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
      connection.setAutoCommit(autoCommit);
      final Savepoint earlier = autoCommit ? null : connection.setSavepoint();
      execute(connection, "INSERT INTO unrelated VALUES(42)");
      final String tooLong = "Z".repeat(40);
      final TransportService invalid = new TransportService(2, tooLong, (byte) 3, (byte) 1, 365);
      invalid.addChunk(project.links.getEsriGraphicList().getOMGraphicAt(0));
      invalid.addStop(1);
      handler.saveService(invalid);
      handler.mustBeSaved();
      project.setLocalProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "1");
      project.captureServiceErrors = true;
      assertFalse(handler.savePendingChanges());
      assertEquals(1, project.serviceErrors.size());
      assertEquals(autoCommit, connection.getAutoCommit());
      assertSame(connection, JDBCUtils.getConnection());
      assertFalse(connection.isClosed());
      assertEquals(before, existingServiceRows(project), "Previously saved rows must survive");
      assertEquals(List.of(List.of("42")), rows(connection, "SELECT * FROM unrelated"));
      if (!autoCommit) {
        connection.rollback(earlier);
        assertTrue(
            rows(connection, "SELECT * FROM unrelated").isEmpty(),
            "Failed schema saves must preserve earlier work and savepoints");
      }
      handler.removeService(tooLong);
      assertTrue(handler.savePendingChanges());
      try (ManagerClose reloaded = new ManagerClose(project.newServiceHandler())) {
        assertEquals(List.of("Saved"), names(reloaded.handler));
        assertEquals(365, reloaded.handler.getService("Saved").getFrequency());
      }
      assertEquals(1, project.serviceErrors.size());
    }
  }

  @TestFactory
  Stream<DynamicTest> successfulFirstSavesAndSchemaMigrationsKeepEarlierWorkPending() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of("missing", "partial", "legacy")
                    .map(
                        schema ->
                            DynamicTest.dynamicTest(
                                hsql + "/" + schema,
                                () -> {
                                  try (NetworkTestProject project = project(hsql)) {
                                    prepareSchema(project, schema);
                                    final Connection connection = project.getMainJDBCConnection();
                                    execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
                                    connection.setAutoCommit(false);
                                    final Savepoint earlier = connection.setSavepoint();
                                    execute(connection, "INSERT INTO unrelated VALUES(42)");
                                    final ServiceHandler handler = project.getServiceHandler();
                                    handler.getService("Saved").setFrequency(730);
                                    handler.mustBeSaved();
                                    assertTrue(handler.savePendingChanges());
                                    assertFalse(connection.getAutoCommit());
                                    connection.rollback(earlier);
                                    assertTrue(
                                        rows(connection, "SELECT * FROM unrelated").isEmpty());
                                    try (ManagerClose reloaded =
                                        new ManagerClose(project.newServiceHandler())) {
                                      assertEquals(
                                          730, reloaded.handler.getService("Saved").getFrequency());
                                      assertEquals(
                                          1,
                                          reloaded.handler.getService("Saved").getLinks().size());
                                      assertEquals(
                                          List.of(1, 2),
                                          reloaded.handler.getService("Saved").getStopNodes());
                                    }
                                    assertTrue(project.serviceErrors.isEmpty());
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> automaticLegacyMigrationDoesNotCommitEarlierWork() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    hsql ? "HSQLDB" : "H2",
                    () -> {
                      try (NetworkTestProject project = project(hsql)) {
                        prepareSchema(project, "legacy");
                        final Connection connection = project.getMainJDBCConnection();
                        execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
                        connection.setAutoCommit(false);
                        final Savepoint earlier = connection.setSavepoint();
                        execute(connection, "INSERT INTO unrelated VALUES(42)");
                        try (ManagerClose migrated =
                            new ManagerClose(project.newServiceHandler())) {
                          assertEquals(List.of("Saved"), names(migrated.handler));
                          assertEquals(
                              List.of(List.of("0")),
                              rows(
                                  connection,
                                  "SELECT pathidx FROM "
                                      + migrated.handler.getServiceLinkDetailTableName()));
                        }
                        connection.rollback(earlier);
                        assertTrue(rows(connection, "SELECT * FROM unrelated").isEmpty());
                        assertFalse(connection.getAutoCommit());
                        assertTrue(project.serviceErrors.isEmpty());
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> isolatedSaveFailuresCloseOwnedConnectionsAndAllowRetry() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of("missing", "legacy")
                    .flatMap(
                        schema ->
                            Stream.of("connect", "setSchema", "schema", "insert", "commit")
                                .map(
                                    phase ->
                                        DynamicTest.dynamicTest(
                                            hsql + "/" + schema + "/" + phase,
                                            () -> checkIsolatedFailure(hsql, schema, phase)))));
  }

  private void checkIsolatedFailure(boolean hsql, String schema, String phase) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      prepareSchema(project, schema);
      final ServiceHandler handler = project.getServiceHandler();
      final Connection connection = project.getMainJDBCConnection();
      final List<List<String>> before = existingServiceRows(project);
      execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
      connection.setAutoCommit(false);
      final Savepoint earlier = connection.setSavepoint();
      execute(connection, "INSERT INTO unrelated VALUES(42)");
      handler.getService("Saved").setFrequency(730);
      handler.mustBeSaved();
      project.captureServiceErrors = true;
      try (SchemaDriver driver = new SchemaDriver(project, phase)) {
        assertFalse(handler.savePendingChanges());
        assertEquals(1, project.serviceErrors.size());
        assertEquals("Injected " + phase, project.serviceErrors.get(0).getMessage());
        assertEquals(driver.opened, driver.closed, "Only owned connections must close");
        assertSame(connection, JDBCUtils.getConnection());
        assertFalse(connection.isClosed());
        assertFalse(connection.getAutoCommit());
        assertEquals(before, existingServiceRows(project));
        assertEquals(730, handler.getService("Saved").getFrequency());
        assertEquals(List.of(List.of("42")), rows(connection, "SELECT * FROM unrelated"));
        connection.rollback(earlier);
        assertTrue(rows(connection, "SELECT * FROM unrelated").isEmpty());
        driver.phase = null;
        assertTrue(handler.savePendingChanges());
        assertEquals(driver.opened, driver.closed);
        assertEquals(
            List.of(List.of("730")),
            rows(connection, "SELECT frequency FROM " + handler.getServiceHeaderTableName()));
        assertEquals(1, project.serviceErrors.size());
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> failedAutomaticMigrationRetainsPendingRouteOrderForRetry() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (NetworkTestProject project = project(hsql)) {
                        prepareSchema(project, "legacy");
                        final Connection connection = project.getMainJDBCConnection();
                        execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
                        connection.setAutoCommit(false);
                        final Savepoint earlier = connection.setSavepoint();
                        execute(connection, "INSERT INTO unrelated VALUES(42)");
                        project.captureServiceErrors = true;
                        try (SchemaDriver driver = new SchemaDriver(project, "commit");
                            ManagerClose loaded = new ManagerClose(project.newServiceHandler())) {
                          assertEquals(1, project.serviceErrors.size());
                          assertEquals(List.of("Saved"), names(loaded.handler));
                          assertEquals(driver.opened, driver.closed);
                          connection.rollback(earlier);
                          assertTrue(rows(connection, "SELECT * FROM unrelated").isEmpty());
                          driver.phase = null;
                          assertTrue(loaded.handler.savePendingChanges());
                          assertEquals(
                              List.of(List.of("0")),
                              rows(
                                  connection,
                                  "SELECT pathidx FROM "
                                      + loaded.handler.getServiceLinkDetailTableName()));
                          assertEquals(1, project.serviceErrors.size());
                        }
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> schemaConnectionUsesTheProjectsCurrentSchema() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (NetworkTestProject project = project(hsql)) {
                        final Connection connection = project.getMainJDBCConnection();
                        final ServiceHandler handler = project.getServiceHandler();
                        // Same-name tables in PUBLIC must not hide missing tables in the selected
                        // schema.
                        handler.resetServicesTables();
                        execute(connection, "CREATE SCHEMA \"Service Space\"");
                        connection.setSchema("Service Space");
                        execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
                        connection.setAutoCommit(false);
                        final Savepoint earlier = connection.setSavepoint();
                        execute(connection, "INSERT INTO unrelated VALUES(42)");
                        try (SchemaDriver driver = new SchemaDriver(project, null)) {
                          handler.createOrReplaceServiceFromLinkIds(
                              1, "Saved", 3, 1, 365, List.of(11), List.of(1, 2), false, true);
                          assertEquals(1, driver.opened);
                          assertEquals(driver.opened, driver.closed);
                          connection.rollback(earlier);
                          assertTrue(rows(connection, "SELECT * FROM unrelated").isEmpty());
                          assertEquals(
                              List.of(List.of("Saved")),
                              rows(
                                  connection,
                                  "SELECT name FROM " + handler.getServiceHeaderTableName()));
                          assertTrue(
                              rows(
                                      connection,
                                      "SELECT * FROM PUBLIC." + handler.getServiceHeaderTableName())
                                  .isEmpty());
                          assertEquals("Service Space", connection.getSchema());
                          assertSame(connection, JDBCUtils.getConnection());
                        }
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> ordinarySavesReuseTheBorrowedProjectConnection() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (NetworkTestProject project = project(hsql)) {
                        prepareSchema(project, "complete");
                        final ServiceHandler handler = project.getServiceHandler();
                        handler.getService("Saved").setFrequency(730);
                        handler.mustBeSaved();
                        try (SchemaDriver driver = new SchemaDriver(project, "connect")) {
                          assertTrue(handler.savePendingChanges());
                          assertEquals(0, driver.opened);
                          assertEquals(
                              List.of(List.of("730")),
                              rows(
                                  project.getMainJDBCConnection(),
                                  "SELECT frequency FROM " + handler.getServiceHeaderTableName()));
                          assertTrue(project.serviceErrors.isEmpty());
                        }
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> explicitTableResetDoesNotCommitEarlierWork() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (NetworkTestProject project = project(hsql)) {
                        prepareSchema(project, "complete");
                        final Connection connection = project.getMainJDBCConnection();
                        execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
                        connection.setAutoCommit(false);
                        final Savepoint earlier = connection.setSavepoint();
                        execute(connection, "INSERT INTO unrelated VALUES(42)");
                        try (SchemaDriver driver = new SchemaDriver(project, null)) {
                          project.getServiceHandler().resetServicesTables();
                          assertEquals(1, driver.opened);
                          assertEquals(driver.opened, driver.closed);
                          connection.rollback(earlier);
                          assertTrue(rows(connection, "SELECT * FROM unrelated").isEmpty());
                          assertTrue(snapshot(project).isEmpty());
                          assertFalse(connection.getAutoCommit());
                          assertTrue(project.serviceErrors.isEmpty());
                        }
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> deletingInvalidLegacyServicesDoesNotSaveTwiceOrLoseFailedEdits() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(false, true)
                    .map(
                        fail ->
                            DynamicTest.dynamicTest(
                                hsql + "/fail=" + fail,
                                () -> {
                                  try (NetworkTestProject project = project(hsql)) {
                                    prepareSchema(project, "legacy");
                                    final Connection connection = project.getMainJDBCConnection();
                                    execute(
                                        connection,
                                        "UPDATE "
                                            + project
                                                .getServiceHandler()
                                                .getServiceHeaderTableName()
                                            + " SET mode=4");
                                    execute(connection, "CREATE TABLE unrelated(marker INTEGER)");
                                    connection.setAutoCommit(false);
                                    final Savepoint earlier = connection.setSavepoint();
                                    execute(connection, "INSERT INTO unrelated VALUES(42)");
                                    final AtomicInteger confirmations = new AtomicInteger();
                                    try (SchemaDriver driver =
                                            new SchemaDriver(project, fail ? "commit" : null);
                                        ManagerClose loaded =
                                            new ManagerClose(
                                                new ServiceHandler(project, false) {
                                                  @Override
                                                  protected void reportDatabaseError(
                                                      Exception failure) {
                                                    project.serviceErrors.add(failure);
                                                  }

                                                  @Override
                                                  protected boolean confirmInvalidServicesDeletion(
                                                      String details) {
                                                    confirmations.incrementAndGet();
                                                    return true;
                                                  }
                                                })) {
                                      assertEquals(1, confirmations.get());
                                      assertEquals(fail ? 1 : 0, project.serviceErrors.size());
                                      assertEquals(driver.opened, driver.closed);
                                      connection.rollback(earlier);
                                      assertTrue(
                                          rows(connection, "SELECT * FROM unrelated").isEmpty());
                                      if (fail) {
                                        assertFalse(existingServiceRows(project).isEmpty());
                                        driver.phase = null;
                                        assertTrue(loaded.handler.savePendingChanges());
                                      }
                                      assertTrue(existingServiceRows(project).isEmpty());
                                      assertTrue(names(loaded.handler).isEmpty());
                                    }
                                  }
                                })));
  }

  private static void prepareSchema(NetworkTestProject project, String schema) throws Exception {
    final ServiceHandler handler = project.getServiceHandler();
    handler.createOrReplaceServiceFromLinkIds(
        1, "Saved", 3, 1, 365, List.of(11), List.of(1, 2), false, !schema.equals("missing"));
    if (schema.equals("partial")) {
      execute(
          project.getMainJDBCConnection(), "DROP TABLE " + handler.getServiceStopDetailTableName());
    } else if (schema.equals("legacy")) {
      execute(
          project.getMainJDBCConnection(),
          "ALTER TABLE " + handler.getServiceLinkDetailTableName() + " DROP COLUMN pathidx");
    }
  }

  private static List<List<String>> existingServiceRows(NetworkTestProject project)
      throws Exception {
    final ServiceHandler handler = project.getServiceHandler();
    final List<List<String>> result = new ArrayList<>();
    final String[] tables = {
      handler.getServiceHeaderTableName(),
      handler.getServiceLinkDetailTableName(),
      handler.getServiceStopDetailTableName()
    };
    final String[] fields = {"id,name,mode,means,frequency", "id,link", "id,stop"};
    for (int i = 0; i < tables.length; i++) {
      if (JDBCUtils.tableExists(tables[i])) {
        result.addAll(
            rows(
                project.getMainJDBCConnection(),
                "SELECT " + fields[i] + " FROM " + tables[i] + " ORDER BY 1,2"));
      }
    }
    return result;
  }

  @TestFactory
  Stream<DynamicTest> legacyRouteTablesAreMigratedAndRemainWritable() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    hsql ? "HSQLDB" : "H2",
                    () -> {
                      try (NetworkTestProject project = project(hsql)) {
                        final ServiceHandler handler = project.getServiceHandler();
                        handler.createOrReplaceServiceFromLinkIds(
                            1, "Legacy", 3, 1, 365, List.of(11), List.of(1, 2), false, true);
                        final Connection connection = project.getMainJDBCConnection();
                        execute(
                            connection,
                            "ALTER TABLE "
                                + handler.getServiceLinkDetailTableName()
                                + " DROP COLUMN pathidx");
                        try (ManagerClose migrated =
                            new ManagerClose(project.newServiceHandler())) {
                          assertEquals(List.of("Legacy"), names(migrated.handler));
                          assertEquals(
                              List.of(List.of("1", "11", "0")),
                              rows(
                                  connection,
                                  "SELECT id,link,pathidx FROM "
                                      + handler.getServiceLinkDetailTableName()));
                          migrated.handler.getService("Legacy").setFrequency(730);
                          migrated.handler.mustBeSaved();
                          assertTrue(migrated.handler.savePendingChanges());
                        }
                        assertEquals(
                            List.of(List.of("730")),
                            rows(
                                connection,
                                "SELECT frequency FROM " + handler.getServiceHeaderTableName()));
                        assertTrue(project.serviceErrors.isEmpty());
                      }
                    }));
  }

  private NetworkTestProject project() throws Exception {
    return project(false);
  }

  private NetworkTestProject project(boolean hsql) throws Exception {
    NetworkTestProject project =
        new NetworkTestProject(directory, new double[] {0, 0, 0, 10}, hsql);
    project.nodes.getModel().setValueAt(1.0, 0, NodusC.DBF_IDX_TRANSHIP);
    project.nodes.getModel().setValueAt(1.0, 1, NodusC.DBF_IDX_TRANSHIP);
    return project;
  }

  private static List<String> names(ServiceHandler handler) {
    List<String> names = new ArrayList<>();
    handler.getServiceNamesIterator().forEachRemaining(names::add);
    return names;
  }

  private static List<List<String>> snapshot(NetworkTestProject project) throws Exception {
    ServiceHandler handler = project.getServiceHandler();
    Connection connection = project.getMainJDBCConnection();
    List<List<String>> rows =
        new ArrayList<>(
            rows(
                connection,
                "SELECT * FROM " + handler.getServiceHeaderTableName() + " ORDER BY id"));
    rows.addAll(
        rows(
            connection,
            "SELECT * FROM " + handler.getServiceLinkDetailTableName() + " ORDER BY id,pathidx"));
    rows.addAll(
        rows(
            connection,
            "SELECT * FROM " + handler.getServiceStopDetailTableName() + " ORDER BY id,stop"));
    return rows;
  }

  private static void execute(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static List<List<String>> rows(Connection connection, String sql) throws Exception {
    List<List<String>> rows = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
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

  /** Delegates to the real engine, injecting failures only on newly opened schema connections. */
  private static final class SchemaDriver implements Driver, AutoCloseable {
    final String url = "jdbc:nodus-service-test:" + UUID.randomUUID();
    final String target;
    String phase;
    int opened;
    int closed;
    int batches;

    SchemaDriver(NetworkTestProject project, String phase) throws SQLException {
      this.phase = phase;
      target = project.getMainJDBCConnection().getMetaData().getURL();
      DriverManager.registerDriver(this);
      project.setLocalProperty(NodusC.PROP_JDBC_URL, url);
    }

    @Override
    public Connection connect(String requested, Properties properties) throws SQLException {
      if (!acceptsURL(requested)) {
        return null;
      }
      if ("connect".equals(phase)) {
        throw new SQLException("Injected connect");
      }
      final Connection delegate = DriverManager.getConnection(target, properties);
      opened++;
      return (Connection)
          Proxy.newProxyInstance(
              Connection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, arguments) -> {
                final String operation = method.getName();
                if (operation.equals(phase)) {
                  throw new SQLException("Injected " + phase);
                }
                try {
                  final Object result = method.invoke(delegate, arguments);
                  if (operation.equals("close")) {
                    closed++;
                  } else if (operation.equals("createStatement")) {
                    return statement(Statement.class, (Statement) result);
                  } else if (operation.equals("prepareStatement")) {
                    return statement(PreparedStatement.class, (PreparedStatement) result);
                  }
                  return result;
                } catch (InvocationTargetException failure) {
                  throw failure.getCause();
                }
              });
    }

    private <T extends Statement> T statement(Class<T> type, T delegate) {
      return type.cast(
          Proxy.newProxyInstance(
              type.getClassLoader(),
              new Class<?>[] {type},
              (proxy, method, arguments) -> {
                try {
                  final Object result = method.invoke(delegate, arguments);
                  if (method.getName().equals("executeUpdate") && "schema".equals(phase)) {
                    final String sql = (String) arguments[0];
                    if (sql.startsWith("CREATE TABLE ") || sql.startsWith("ALTER TABLE ")) {
                      throw new SQLException("Injected schema");
                    }
                  } else if (method.getName().equals("executeBatch")
                      && ++batches == 2
                      && "insert".equals(phase)) {
                    throw new SQLException("Injected insert");
                  }
                  return result;
                } catch (InvocationTargetException failure) {
                  throw failure.getCause();
                }
              }));
    }

    @Override
    public boolean acceptsURL(String requested) {
      return url.equals(requested);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String requested, Properties properties) {
      return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
      return 1;
    }

    @Override
    public int getMinorVersion() {
      return 0;
    }

    @Override
    public boolean jdbcCompliant() {
      return false;
    }

    @Override
    public Logger getParentLogger() {
      return Logger.getAnonymousLogger();
    }

    @Override
    public void close() throws SQLException {
      DriverManager.deregisterDriver(this);
    }
  }

  private static final class ManagerClose implements AutoCloseable {
    final ServiceHandler handler;

    ManagerClose(ServiceHandler handler) {
      this.handler = handler;
    }

    @Override
    public void close() {
      handler.dispose();
    }
  }
}
