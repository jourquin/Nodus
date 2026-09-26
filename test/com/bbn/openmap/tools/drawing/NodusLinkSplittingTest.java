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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.services.TransportService;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real link splitting, including geometry, DBF attributes and service membership. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class NodusLinkSplittingTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> splitsAtTheNearestPointAndPreservesAttributesAndServices() {
    return Stream.of(
        split(
            "horizontal",
            new double[] {0, 0, 0, 10},
            2,
            4,
            new double[] {0, 0, 0, 4},
            new double[] {0, 4, 0, 10}),
        split(
            "vertical",
            new double[] {0, 2, 10, 2},
            4,
            5,
            new double[] {0, 2, 4, 2},
            new double[] {4, 2, 10, 2}),
        split(
            "bent",
            new double[] {0, 0, 0, 4, 4, 4},
            2,
            5,
            new double[] {0, 0, 0, 4, 2, 4},
            new double[] {2, 4, 4, 4}),
        split(
            "nearest segment, not nearest vertex",
            new double[] {0, 0, 0, 10, 1, 10, 1, 4},
            .1,
            5,
            new double[] {0, 0, 0, 5},
            new double[] {0, 5, 0, 10, 1, 10, 1, 4}),
        split(
            "double precision",
            new double[] {0, 0, 0, 10},
            1,
            4.123456789,
            new double[] {0, 0, 0, 4.123456789},
            new double[] {0, 4.123456789, 0, 10}),
        split(
            "repeated vertex",
            new double[] {0, 0, 0, 0, 0, 10},
            1,
            4,
            new double[] {0, 0, 0, 0, 0, 4},
            new double[] {0, 4, 0, 10}));
  }

  private DynamicTest split(
      String name, double[] source, double lat, double lon, double[] first, double[] second) {
    return DynamicTest.dynamicTest(
        name,
        () -> {
          try (NetworkTestProject project = new NetworkTestProject(directory, source)) {
            final List<Object> original = new ArrayList<>(project.links.getModel().getRecord(0));
            final OMGraphic graphic = project.links.getEsriGraphicList().getOMGraphicAt(0);
            TransportService service = new TransportService(1);
            service.setName("Route");
            service.addStop(1);
            service.addStop(2);
            service.addChunk(graphic);
            project.getServiceHandler().saveService(service);
            tool(project).splitLink(new OMPoint(lat, lon), graphic, 0);
            assertEquals(3, project.nodes.getModel().getRowCount());
            assertEquals(2, project.links.getModel().getRowCount());
            assertEquals(2, project.links.getEsriGraphicList().size());
            assertGeometry(first, (OMPoly) project.links.getEsriGraphicList().getOMGraphicAt(0));
            assertGeometry(second, (OMPoly) project.links.getEsriGraphicList().getOMGraphicAt(1));
            EsriPoint inserted = (EsriPoint) project.nodes.getEsriGraphicList().getOMGraphicAt(2);
            assertEquals(first[first.length - 2], inserted.getLat(), 1e-10);
            assertEquals(first[first.length - 1], inserted.getLon(), 1e-10);
            assertEquals(1, number(project, 0, NodusC.DBF_IDX_NODE1));
            assertEquals(3, number(project, 0, NodusC.DBF_IDX_NODE2));
            assertEquals(3, number(project, 1, NodusC.DBF_IDX_NODE1));
            assertEquals(2, number(project, 1, NodusC.DBF_IDX_NODE2));
            assertEquals(11, number(project, 0, NodusC.DBF_IDX_NUM));
            assertEquals(12, number(project, 1, NodusC.DBF_IDX_NUM));
            for (int column : new int[] {1, 2, 5, 6, 7, 8, 9}) {
              assertEquals(original.get(column), project.links.getModel().getValueAt(0, column));
              assertEquals(original.get(column), project.links.getModel().getValueAt(1, column));
            }
            assertEquals(List.of("Route"), project.getServiceHandler().getServiceNamesForLink(11));
            assertEquals(List.of("Route"), project.getServiceHandler().getServiceNamesForLink(12));
            assertEquals(
                List.of(
                    project.links.getEsriGraphicList().getOMGraphicAt(0),
                    project.links.getEsriGraphicList().getOMGraphicAt(1)),
                service.getLinks(),
                "The old graphic must be replaced, not retained in the service route");
            assertEquals(
                List.of(1, 2), service.getStopNodes(), "Splitting must not add a service stop");
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> endpointsAndDegenerateLinesDoNotCreateDisconnectedFragments() {
    return Stream.of(
        unchanged("origin", new double[] {0, 0, 0, 10}, 0, 0, true),
        unchanged("destination", new double[] {0, 0, 0, 10}, 0, 10, true),
        unchanged("degenerate", new double[] {1, 2, 1, 2}, 3, 4, true),
        unchanged("cancelled node", new double[] {0, 0, 0, 10}, 1, 5, false));
  }

  private DynamicTest unchanged(
      String name, double[] coordinates, double lat, double lon, boolean accept) {
    return DynamicTest.dynamicTest(
        name,
        () -> {
          try (NetworkTestProject project = new NetworkTestProject(directory, coordinates)) {
            project.nodes.acceptNode = accept;
            final OMGraphic original = project.links.getEsriGraphicList().getOMGraphicAt(0);
            final List<Object> record = new ArrayList<>(project.links.getModel().getRecord(0));
            tool(project).splitLink(new OMPoint(lat, lon), original, 0);
            assertEquals(2, project.nodes.getModel().getRowCount());
            assertEquals(1, project.links.getModel().getRowCount());
            assertSame(original, project.links.getEsriGraphicList().getOMGraphicAt(0));
            assertEquals(record, project.links.getModel().getRecord(0));
            assertGeometry(coordinates, (OMPoly) original);
          }
        });
  }

  @Test
  void repeatedAndReverseServiceTraversalsKeepTheirOrder() throws Exception {
    try (NetworkTestProject project =
        new NetworkTestProject(directory, new double[] {0, 0, 0, 10})) {
      final OMGraphic original = project.links.getEsriGraphicList().getOMGraphicAt(0);
      final EsriPolyline other =
          new EsriPolyline(
              new double[] {0, 10, 0, 20}, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
      assertTrue(project.links.addRecord(other, 21, 2, 4, false));
      final TransportService service = new TransportService(1);
      service.setName("Repeated");
      service.addChunk(other);
      service.addChunk(original);
      service.addChunk(original);
      service.addChunk(other);
      project.getServiceHandler().saveService(service);
      final TransportService unrelated = new TransportService(2);
      unrelated.setName("Unrelated");
      unrelated.addChunk(other);
      project.getServiceHandler().saveService(unrelated);
      tool(project).splitLink(new OMPoint(1.0, 5.0), original, 0);
      final OMGraphic first = project.links.getEsriGraphicList().getOMGraphicAt(0);
      final OMGraphic second = project.links.getEsriGraphicList().getOMGraphicAt(2);
      assertEquals(List.of(other, second, first, first, second, other), service.getLinks());
      assertEquals(List.of(other), unrelated.getLinks());
    }
  }

  private static NodusOMDrawingTool tool(NetworkTestProject project) {
    NodusOMDrawingTool tool = new NodusOMDrawingTool(project.panel, null);
    tool.setProjection(project.panel.map.getProjection());
    tool.setNodusLayers(project.getNodeLayers(), project.getLinkLayers());
    return tool;
  }

  private static int number(NetworkTestProject project, int row, int column) {
    return JDBCUtils.getInt(project.links.getModel().getValueAt(row, column));
  }

  private static void assertGeometry(double[] expected, OMPoly line) {
    double[] actual = line.getLatLonArrayCopy();
    for (int i = 0; i < actual.length; i++) {
      actual[i] = Math.toDegrees(actual[i]);
    }
    assertArrayEquals(expected, actual, 1e-10);
  }
}
