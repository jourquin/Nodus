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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
