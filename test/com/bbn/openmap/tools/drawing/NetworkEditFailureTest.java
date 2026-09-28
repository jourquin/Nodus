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

package com.bbn.openmap.tools.drawing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.services.TransportService;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Database constraints exercise actual failed writes, rather than mocking a failure return. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class NetworkEditFailureTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> rejectedInsertionsLeaveNoGeometryOrRecords() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of("node", "link", "complete link")
                    .map(
                        kind ->
                            DynamicTest.dynamicTest(
                                (hsql ? "HSQLDB " : "H2 ") + kind,
                                () -> checkInsertionFailure(hsql, kind))));
  }

  @TestFactory
  Stream<DynamicTest> splitFailuresRollBackAllLayersAndPreserveServicesAndEarlierWork() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(false, true)
                    .flatMap(
                        transaction ->
                            Stream.of("node", "second fragment", "first fragment")
                                .map(
                                    stage ->
                                        DynamicTest.dynamicTest(
                                            (hsql ? "HSQLDB " : "H2 ")
                                                + stage
                                                + " transaction="
                                                + transaction,
                                            () -> checkSplitFailure(hsql, transaction, stage)))));
  }

  private void checkInsertionFailure(boolean hsql, String kind) throws Exception {

    try (NetworkTestProject p = project(hsql)) {
      final Snapshot before = new Snapshot(p);
      sql(
          p,
          "ALTER TABLE "
              + (kind.equals("node") ? "nodes" : "links")
              + " ADD CONSTRAINT reject_new CHECK (NUM <> "
              + (kind.equals("node") ? 3 : 12)
              + ")");
      if (kind.equals("node")) {
        assertFalse(p.nodes.addRecord(new EsriPoint(0, 5), 3, false));
      } else if (kind.equals("link")) {
        assertFalse(p.links.addRecord(line(), 12, 1, 2, false));
      } else {
        List<Object> row = new ArrayList<>(p.links.getModel().getRecord(0));
        row.set(0, 12.0);
        p.links.addRecord(line(), row);
      }
      before.assertUnchanged(p);
      assertConstraintError(p);
      sql(
          p,
          "ALTER TABLE "
              + (kind.equals("node") ? "nodes" : "links")
              + " DROP CONSTRAINT reject_new");
      assertTrue(p.nodes.addRecord(new EsriPoint(0, 5), 3, false));
      assertTrue(p.links.addRecord(line(), 12, 3, 2, false));
      assertEquals(Map.of(1, 0, 2, 1, 3, 2), p.nodes.getIndex());
      assertEquals(Map.of(11, 0, 12, 1), p.links.getIndex());
      assertEquals(
          List.of(List.of("12", "3", "2")),
          rows(p, "SELECT NUM,NODE1,NODE2 FROM links WHERE NUM=12"));
    }
  }

  private void checkSplitFailure(boolean hsql, boolean transaction, String stage) throws Exception {

    try (NetworkTestProject p = project(hsql)) {
      final OMGraphic original = p.links.getEsriGraphicList().getOMGraphicAt(0);
      final TransportService service = new TransportService(1);
      service.setName("Repeated");
      service.addStop(1);
      service.addStop(2);
      service.addChunk(original);
      service.addChunk(original);
      p.getServiceHandler().saveService(service);
      sql(
          p,
          "ALTER TABLE "
              + (stage.equals("node") ? "nodes" : "links")
              + " ADD CONSTRAINT reject_split CHECK ("
              + (stage.equals("node")
                  ? "NUM <> 3"
                  : stage.equals("second fragment") ? "NUM <> 12" : "NODE2 <> 3")
              + ")");
      sql(p, "CREATE TABLE earlier_work (ID INTEGER)");
      p.getMainJDBCConnection().setAutoCommit(!transaction);
      sql(p, "INSERT INTO earlier_work VALUES (7)");
      if (transaction) {
        p.links.getModel().setValueAt("Earlier edit", 0, 9);
        p.links.setDirtyDbf(true);
        sql(p, "UPDATE links SET LABEL='Earlier edit' WHERE NUM=11");
      }
      final Snapshot before = new Snapshot(p);
      final NodusOMDrawingTool tool =
          new NodusOMDrawingTool(p.panel, null) {
            @Override
            protected void reportSplitError(Exception error) {
              throw new AssertionError("Unexpected transaction failure", error);
            }
          };
      tool.setProjection(p.panel.map.getProjection());
      tool.setNodusLayers(p.getNodeLayers(), p.getLinkLayers());
      tool.splitLink(new OMPoint(1.0, 5.0), original, 0);
      before.assertUnchanged(p);
      assertEquals(List.of(original, original), service.getLinks());
      assertEquals(List.of(1, 2), service.getStopNodes());
      assertEquals(List.of("Repeated"), p.getServiceHandler().getServiceNamesForLink(11));
      assertConstraintError(p);
      assertEquals(!transaction, p.getMainJDBCConnection().getAutoCommit());
      assertEquals(List.of(List.of("7")), rows(p, "SELECT ID FROM earlier_work"));
      if (transaction) {
        p.getMainJDBCConnection().rollback();
        assertTrue(
            rows(p, "SELECT ID FROM earlier_work").isEmpty(),
            "A failed split must not commit earlier work");
        p.getMainJDBCConnection().setAutoCommit(true);
      }
      sql(
          p,
          "ALTER TABLE "
              + (stage.equals("node") ? "nodes" : "links")
              + " DROP CONSTRAINT reject_split");
      tool.splitLink(new OMPoint(1.0, 5.0), original, 0);
      assertEquals(3, p.nodes.getModel().getRowCount());
      assertEquals(2, p.links.getModel().getRowCount());
      assertEquals(
          List.of(List.of("11", "1", "3"), List.of("12", "3", "2")),
          rows(p, "SELECT NUM,NODE1,NODE2 FROM links ORDER BY NUM"));
      assertEquals(4, service.getLinks().size());
    }
  }

  private static void assertConstraintError(NetworkTestProject p) {
    List<Exception> errors = new ArrayList<>(p.nodes.editErrors);
    errors.addAll(p.links.editErrors);
    assertEquals(1, errors.size());
    assertTrue(((java.sql.SQLException) errors.get(0)).getSQLState().startsWith("23"));
  }

  private NetworkTestProject project(boolean hsql) throws Exception {
    return new NetworkTestProject(directory, new double[] {0, 0, 0, 10}, hsql);
  }

  private static EsriPolyline line() {
    return new EsriPolyline(
        new double[] {0, 0, 0, 10}, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
  }

  private static void sql(NetworkTestProject p, String sql) throws Exception {
    try (Statement statement = p.getMainJDBCConnection().createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private static List<List<String>> rows(NetworkTestProject p, String sql) throws Exception {
    List<List<String>> rows = new ArrayList<>();
    try (Statement statement = p.getMainJDBCConnection().createStatement();
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

  private static final class Snapshot {
    final List<List<String>> nodes;
    final List<List<String>> links;
    final List<Object> record;
    final OMGraphic graphic;
    final double[] coordinates;
    final boolean nodesDirty;
    final boolean linksDirty;

    Snapshot(NetworkTestProject p) throws Exception {
      nodesDirty = p.nodes.isDirty();
      linksDirty = p.links.isDirty();
      nodes = rows(p, "SELECT * FROM nodes ORDER BY NUM");
      links = rows(p, "SELECT * FROM links ORDER BY NUM");
      record = new ArrayList<>(p.links.getModel().getRecord(0));
      graphic = p.links.getEsriGraphicList().getOMGraphicAt(0);
      coordinates = ((OMPoly) graphic).getLatLonArrayCopy();
    }

    void assertUnchanged(NetworkTestProject p) throws Exception {
      assertEquals(nodes, rows(p, "SELECT * FROM nodes ORDER BY NUM"));
      assertEquals(links, rows(p, "SELECT * FROM links ORDER BY NUM"));
      assertEquals(2, p.nodes.getModel().getRowCount());
      assertEquals(2, p.nodes.getEsriGraphicList().size());
      assertEquals(1, p.links.getModel().getRowCount());
      assertEquals(1, p.links.getEsriGraphicList().size());
      assertEquals(Map.of(1, 0, 2, 1), p.nodes.getIndex());
      assertEquals(Map.of(11, 0), p.links.getIndex());
      assertEquals(record, p.links.getModel().getRecord(0));
      assertSame(graphic, p.links.getEsriGraphicList().getOMGraphicAt(0));
      assertArrayEquals(coordinates, ((OMPoly) graphic).getLatLonArrayCopy());
      assertEquals(nodesDirty, p.nodes.isDirty());
      assertEquals(linksDirty, p.links.isDirty());
    }
  }
}
