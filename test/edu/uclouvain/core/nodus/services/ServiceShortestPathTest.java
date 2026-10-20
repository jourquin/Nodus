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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Physical service routes on equatorial links with independently known angular lengths. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class ServiceShortestPathTest {
  private static final int ORIGIN = 1;
  private static final int VIA = 1007;
  private static final int DETOUR = 200003;
  private static final int DESTINATION = 900007;
  private static final int ISOLATED = 700001;
  private static final double KM_PER_DEGREE = 6378.137 * Math.PI / 180;

  @TempDir Path directory;

  @Test
  void minimizesLengthRatherThanLinkCountAndReconstructsBothDirectionsWithSparseIds()
      throws Exception {
    try (NetworkTestProject project = project(false)) {
      ServiceHandler handler = project.getServiceHandler();
      addLink(project, 18, ORIGIN, DESTINATION, 3, 3, 0, 0, 0, -3, 0, 4);
      // A-B-D is 1+3 degrees; A-C-D is 4+1; direct A-D is 10.
      for (int repeat = 0; repeat < 3; repeat++) {
        assertEquals(List.of(11, 12), handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
        assertEquals(List.of(12, 11), handler.findShortestServicePath(DESTINATION, ORIGIN, 3, 1));
      }
      assertEquals(
          4 * KM_PER_DEGREE,
          realLink(project, 11).getLength() + realLink(project, 12).getLength(),
          0.001);
      assertTrue(names(handler).isEmpty(), "Finding a path must not create a service");
      assertTrue(project.serviceErrors.isEmpty());
    }
  }

  @TestFactory
  Stream<DynamicTest> modeAndMeansChooseOnlyCompatibleLinks() {
    return Stream.of(1, 2, 3, TransportService.ALL_MEANS)
        .map(
            means ->
                dynamicTest(
                    "means=" + means,
                    () -> {
                      try (NetworkTestProject project = project(false)) {
                        ServiceHandler handler = project.getServiceHandler();
                        List<Integer> expected = means == 3 ? List.of(13, 14) : List.of(11, 12);
                        assertEquals(
                            expected,
                            handler.findShortestServicePath(ORIGIN, DESTINATION, 3, means));
                        assertEquals(
                            List.of(16),
                            handler.findShortestServicePath(ORIGIN, DESTINATION, 4, means));
                        assertThrows(
                            IllegalArgumentException.class,
                            () -> handler.findShortestServicePath(ORIGIN, DESTINATION, 5, means));
                      }
                    }));
  }

  @Test
  void disabledAndInsufficientMeansLinksAreExcludedFromEveryNewGraph() throws Exception {
    try (NetworkTestProject project = project(false)) {
      ServiceHandler handler = project.getServiceHandler();
      setLink(project, 11, NodusC.DBF_IDX_ENABLED, 0);
      assertEquals(List.of(13, 14), handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
      setLink(project, 14, NodusC.DBF_IDX_ENABLED, 0);
      assertEquals(List.of(15, 12), handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
      setLink(project, 15, NodusC.DBF_IDX_MEANS, 0);
      assertThrows(
          IllegalArgumentException.class,
          () -> handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
      setLink(project, 11, NodusC.DBF_IDX_ENABLED, 1);
      assertEquals(List.of(11, 12), handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
      assertThrows(
          IllegalArgumentException.class,
          () -> handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 4));
    }
  }

  @Test
  void geometryAndSpeedEditsRefreshWeightsAndReconstructTheCorrectParallelLink() throws Exception {
    try (NetworkTestProject project = project(false)) {
      ServiceHandler handler = project.getServiceHandler();
      assertEquals(List.of(11, 12), handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
      final RealLink original = realLink(project, 11);
      setLink(project, 11, NodusC.DBF_IDX_SPEED, 1);
      setLink(project, 12, NodusC.DBF_IDX_SPEED, 1);
      setLink(project, 13, NodusC.DBF_IDX_SPEED, 9999);
      setLink(project, 14, NodusC.DBF_IDX_SPEED, 9999);
      assertEquals(List.of(11, 12), handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
      assertEquals(1, original.getSpeed());
      geometry(project, 11, 0, 0, 0, -3, 0, 1); // Seven degrees instead of one.
      assertEquals(List.of(13, 14), handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
      assertSame(original, realLink(project, 11));
      assertEquals(7 * KM_PER_DEGREE, original.getLength(), 0.001);
      // Link 15 is stored B-A. Its shorter reverse edge follows the longer link 11 in adjacency.
      geometry(project, 15, 0, 1, 0, 0);
      assertEquals(List.of(15, 12), handler.findShortestServicePath(ORIGIN, DESTINATION, 3, 1));
      assertEquals(List.of(12, 15), handler.findShortestServicePath(DESTINATION, ORIGIN, 3, 1));
    }
  }

  @Test
  void waypointsAreVisitedInSelectedOrderIncludingRepeatedPhysicalLinks() throws Exception {
    try (NetworkTestProject project = project(false)) {
      ServiceHandler handler = project.getServiceHandler();
      assertEquals(
          List.of(13, 14),
          handler.findShortestServicePath(List.of(ORIGIN, DETOUR, DESTINATION), 3, 1));
      assertEquals(
          List.of(14, 13),
          handler.findShortestServicePath(List.of(DESTINATION, DETOUR, ORIGIN), 3, 1));
      assertEquals(
          List.of(13, 14, 12, 12),
          handler.findShortestServicePath(List.of(ORIGIN, DETOUR, VIA, DESTINATION), 3, 1));
      assertEquals(
          List.of(12, 12, 14, 13),
          handler.findShortestServicePath(List.of(DESTINATION, VIA, DETOUR, ORIGIN), 3, 1));
      assertThrows(
          IllegalArgumentException.class,
          () -> handler.findShortestServicePath(List.of(ORIGIN, DESTINATION, 2), 3, 1));
      assertThrows(
          IllegalArgumentException.class,
          () -> handler.findShortestServicePath(ORIGIN, ISOLATED, 3, 1));
    }
  }

  @TestFactory
  Stream<DynamicTest> invalidNodeSelectionsAreRejectedBeforeAServiceIsCreated() {
    final List<List<Integer>> selections =
        Arrays.asList(
            null,
            List.of(),
            List.of(ORIGIN),
            List.of(ORIGIN, ORIGIN),
            List.of(ORIGIN, VIA, ORIGIN, DESTINATION),
            Arrays.asList(ORIGIN, null, DESTINATION),
            List.of(ORIGIN, 999999, DESTINATION));
    return selections
        .stream()
        .map(
            selection ->
                dynamicTest(
                    String.valueOf(selection),
                    () -> {
                      try (NetworkTestProject project = project(false)) {
                        ServiceHandler handler = project.getServiceHandler();
                        assertThrows(
                            IllegalArgumentException.class,
                            () -> handler.findShortestServicePath(selection, 3, 1));
                        assertTrue(names(handler).isEmpty());
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> generatedServicesRoundTripOrderedLinksAndWildcardMeans() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(false, true)
                    .flatMap(
                        reverse ->
                            Stream.of(1, TransportService.ALL_MEANS)
                                .map(
                                    means ->
                                        dynamicTest(
                                            "HSQLDB="
                                                + hsql
                                                + ", reverse="
                                                + reverse
                                                + ", means="
                                                + means,
                                            () -> checkRoundTrip(hsql, reverse, means)))));
  }

  private void checkRoundTrip(boolean hsql, boolean reverse, int means) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      final ServiceHandler handler = project.getServiceHandler();
      final TransportService generated =
          handler.createShortestPathService(
              7, reverse ? DESTINATION : ORIGIN, reverse ? ORIGIN : DESTINATION, 3, means, 365);
      final List<Integer> route = reverse ? List.of(12, 11) : List.of(11, 12);
      assertEquals(route, linkIds(project, generated));
      assertEquals(Set.of(ORIGIN, DESTINATION), Set.copyOf(generated.getStopNodes()));
      assertSaved(project, 7, "Service 7", means, 365, route);
      final ServiceHandler reloaded = project.newServiceHandler();
      try {
        TransportService actual = reloaded.getService("Service 7");
        assertNotSame(generated, actual);
        assertEquals(route, linkIds(project, actual));
        assertEquals(365, actual.getFrequency());
        assertEquals(3, actual.getMode());
        assertEquals(means, actual.getMeans());
        assertEquals(Set.of(ORIGIN, DESTINATION), Set.copyOf(actual.getStopNodes()));
        assertTrue(reloaded.serviceSupportsModeMeans(7, 3, 1));
        assertEquals(
            means == TransportService.ALL_MEANS, reloaded.serviceSupportsModeMeans(7, 3, 2));
        assertFalse(reloaded.serviceSupportsModeMeans(7, 3, 3));
        assertFalse(reloaded.serviceSupportsModeMeans(7, 4, 1));
        assertNull(reloaded.getServiceLineValidationMessage(actual));
      } finally {
        reloaded.dispose();
      }
      assertTrue(project.serviceErrors.isEmpty());
    }
  }

  @TestFactory
  Stream<DynamicTest> generatedWaypointServiceKeepsRepeatedAccessLinksWhenReloaded() {
    return Stream.of(false, true)
        .map(hsql -> dynamicTest("HSQLDB=" + hsql, () -> checkWaypointRoundTrip(hsql)));
  }

  private void checkWaypointRoundTrip(boolean hsql) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      final int stop = 800004;
      addNode(project, stop, 2);
      project
          .nodes
          .getModel()
          .setValueAt(1.0, project.nodes.getNumIndex(stop), NodusC.DBF_IDX_TRANSHIP);
      addLink(project, 18, VIA, stop, 3, 3, 0, 1, 0, 2);
      final ServiceHandler handler = project.getServiceHandler();
      final List<Integer> route =
          handler.findShortestServicePath(List.of(ORIGIN, stop, DESTINATION), 3, 1);
      assertEquals(List.of(11, 18, 18, 12), route);
      final TransportService generated =
          handler.createOrReplaceServiceFromLinkIds(
              7, "Waypoint", 3, 1, 365, route, List.of(ORIGIN, stop, DESTINATION), false, true);
      assertEquals(route, linkIds(project, generated));
      assertEquals(
          List.of(List.of("0", "11"), List.of("1", "18"), List.of("2", "18"), List.of("3", "12")),
          rows(
              project,
              "SELECT pathidx,link FROM "
                  + handler.getServiceLinkDetailTableName()
                  + " ORDER BY pathidx"));
      final ServiceHandler reloaded = project.newServiceHandler();
      try {
        TransportService actual = reloaded.getService("Waypoint");
        assertEquals(route, linkIds(project, actual));
        assertEquals(Set.of(ORIGIN, stop, DESTINATION), Set.copyOf(actual.getStopNodes()));
        assertNull(reloaded.getServiceLineValidationMessage(actual));
      } finally {
        reloaded.dispose();
      }
      assertTrue(project.serviceErrors.isEmpty());
    }
  }

  @TestFactory
  Stream<DynamicTest> failedRouteConstructionKeepsExistingServicesAndSavedRows() {
    return Stream.of("disconnected", "isolated", "endpoint operations", "mode", "later waypoint")
        .map(failure -> dynamicTest(failure, () -> checkFailedConstruction(failure)));
  }

  private void checkFailedConstruction(String failure) throws Exception {
    try (NetworkTestProject project = project(false)) {
      final ServiceHandler handler = project.getServiceHandler();
      final TransportService existing =
          handler.createShortestPathService(7, ORIGIN, DESTINATION, 3, 1, 365);
      final List<List<String>> before = snapshot(project);
      if (failure.equals("endpoint operations")) {
        project
            .nodes
            .getModel()
            .setValueAt(0.0, project.nodes.getNumIndex(DESTINATION), NodusC.DBF_IDX_TRANSHIP);
      }
      if (failure.equals("later waypoint")) {
        assertThrows(
            IllegalArgumentException.class,
            () -> handler.findShortestServicePath(List.of(ORIGIN, DESTINATION, 2), 3, 1));
      } else {
        int destination =
            failure.equals("disconnected")
                ? 2
                : failure.equals("isolated") ? ISOLATED : DESTINATION;
        int mode = failure.equals("mode") ? 5 : 3;
        assertThrows(
            IllegalArgumentException.class,
            () ->
                handler.createShortestPathService(
                    7, "Replacement", ORIGIN, destination, mode, 1, 730, true, true));
      }
      assertSame(existing, handler.getService("Service 7"));
      assertEquals(List.of("Service 7"), names(handler));
      assertEquals(List.of(11, 12), linkIds(project, existing));
      assertEquals(365, existing.getFrequency());
      assertEquals(before, snapshot(project));
      assertTrue(handler.savePendingChanges());
      assertEquals(before, snapshot(project));
      assertTrue(project.serviceErrors.isEmpty());
    }
  }

  @TestFactory
  Stream<DynamicTest> replacementNeedsExplicitPermissionAndKeepsUnrelatedServices() {
    return Stream.of(false, true)
        .map(hsql -> dynamicTest("HSQLDB=" + hsql, () -> checkReplacement(hsql)));
  }

  private void checkReplacement(boolean hsql) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      final ServiceHandler handler = project.getServiceHandler();
      final TransportService existing =
          handler.createShortestPathService(7, ORIGIN, DESTINATION, 3, 1, 365);
      final TransportService other =
          handler.createShortestPathService(9, ORIGIN, DESTINATION, 4, 1, 365);
      final List<List<String>> before = snapshot(project);
      setLink(project, 11, NodusC.DBF_IDX_ENABLED, 0);
      assertThrows(
          IllegalArgumentException.class,
          () ->
              handler.createShortestPathService(
                  7, "Replacement", ORIGIN, DESTINATION, 3, 1, 730, false, true));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              handler.createShortestPathService(
                  8, "Service 7", ORIGIN, DESTINATION, 3, 1, 730, false, true));
      assertSame(existing, handler.getService("Service 7"));
      assertEquals(before, snapshot(project));
      TransportService replacement =
          handler.createShortestPathService(
              7, "Replacement", ORIGIN, DESTINATION, 3, 1, 730, true, false);
      assertEquals(List.of(13, 14), linkIds(project, replacement));
      assertEquals(before, snapshot(project), "Deferred replacement must wait for saving");
      assertTrue(handler.savePendingChanges());
      assertNull(handler.getService("Service 7"));
      assertSame(other, handler.getService("Service 9"));
      assertEquals(
          List.of(List.of("7", "Replacement"), List.of("9", "Service 9")),
          rows(
              project,
              "SELECT id,name FROM " + handler.getServiceHeaderTableName() + " ORDER BY id"));
      handler.createShortestPathService(
          8, "Replacement", ORIGIN, DESTINATION, 3, 1, 100, true, true);
      assertEquals(
          List.of(List.of("8", "Replacement"), List.of("9", "Service 9")),
          rows(
              project,
              "SELECT id,name FROM " + handler.getServiceHeaderTableName() + " ORDER BY id"));
      assertSame(other, handler.getService("Service 9"));
      assertEquals(List.of("Replacement", "Service 9"), names(handler));
      assertTrue(project.serviceErrors.isEmpty());
    }
  }

  @TestFactory
  Stream<DynamicTest> failedGeneratedServiceSaveRetainsDatabaseAndCanRetryPendingReplacement() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(false, true)
                    .map(
                        autoCommit ->
                            dynamicTest(
                                "HSQLDB=" + hsql + ", autoCommit=" + autoCommit,
                                () -> checkFailedSave(hsql, autoCommit))));
  }

  private void checkFailedSave(boolean hsql, boolean autoCommit) throws Exception {
    try (NetworkTestProject project = project(hsql)) {
      final ServiceHandler handler = project.getServiceHandler();
      handler.createShortestPathService(7, ORIGIN, DESTINATION, 3, 1, 365);
      final List<List<String>> before = snapshot(project);
      final String links = handler.getServiceLinkDetailTableName();
      execute(project, "ALTER TABLE " + links + " ADD CONSTRAINT rejected_link CHECK(link<>14)");
      project.getMainJDBCConnection().setAutoCommit(autoCommit);
      project.setLocalProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "1");
      project.captureServiceErrors = true;
      setLink(project, 11, NodusC.DBF_IDX_ENABLED, 0);
      assertThrows(
          IllegalStateException.class,
          () ->
              handler.createShortestPathService(
                  7, "Replacement", ORIGIN, DESTINATION, 3, 1, 730, true, true));
      assertEquals(before, snapshot(project));
      assertEquals(autoCommit, project.getMainJDBCConnection().getAutoCommit());
      assertEquals(1, project.serviceErrors.size());
      // A failed save retains the edit as pending, while the database
      // keeps the old route.
      TransportService pending = handler.getService("Replacement");
      assertEquals(List.of(13, 14), linkIds(project, pending));
      assertEquals(730, pending.getFrequency());
      execute(project, "ALTER TABLE " + links + " DROP CONSTRAINT rejected_link");
      assertTrue(handler.savePendingChanges());
      assertSaved(project, 7, "Replacement", 1, 730, List.of(13, 14));
      ServiceHandler reloaded = project.newServiceHandler();
      try {
        assertEquals(List.of("Replacement"), names(reloaded));
        assertEquals(List.of(13, 14), linkIds(project, reloaded.getService("Replacement")));
      } finally {
        reloaded.dispose();
      }
      assertEquals(1, project.serviceErrors.size());
    }
  }

  /** All geometry lies on the equator: link lengths are exact sums of longitude differences. */
  private NetworkTestProject project(boolean hsql) throws Exception {
    NetworkTestProject project =
        new NetworkTestProject(directory, new double[] {0, 0, 0, 10}, hsql);
    addNode(project, VIA, 1);
    addNode(project, DETOUR, 3);
    addNode(project, DESTINATION, 4);
    addNode(project, 800003, 11);
    addNode(project, ISOLATED, 12);
    for (int row = 0; row < project.nodes.getModel().getRowCount(); row++) {
      project.nodes.getModel().setValueAt(1.0, row, NodusC.DBF_IDX_TRANSHIP);
    }
    setLink(project, 11, NodusC.DBF_IDX_NODE2, VIA);
    setLink(project, 11, NodusC.DBF_IDX_MEANS, 2);
    geometry(project, 11, 0, 0, 0, 1);
    addLink(project, 12, VIA, DESTINATION, 3, 3, 0, 1, 0, 4);
    addLink(project, 13, ORIGIN, DETOUR, 3, 3, 0, 0, 0, -0.5, 0, 3);
    addLink(project, 14, DETOUR, DESTINATION, 3, 3, 0, 3, 0, 4);
    addLink(project, 15, VIA, ORIGIN, 3, 2, 0, 1, 0, -2, 0, 0);
    addLink(project, 16, ORIGIN, DESTINATION, 4, 4, 0, 0, 0, -3, 0, 4);
    addLink(project, 17, 2, 800003, 3, 3, 0, 10, 0, 11);
    return project;
  }

  private static void addNode(NetworkTestProject project, int id, double longitude) {
    assertTrue(project.nodes.addRecord(new EsriPoint(0, longitude), id, false));
  }

  private static void addLink(
      NetworkTestProject project,
      int id,
      int origin,
      int destination,
      int mode,
      int means,
      double... coordinates) {
    assertTrue(
        project.links.addRecord(
            new EsriPolyline(coordinates, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT),
            id,
            origin,
            destination,
            false));
    setLink(project, id, NodusC.DBF_IDX_MODE, mode);
    setLink(project, id, NodusC.DBF_IDX_MEANS, means);
    setLink(project, id, NodusC.DBF_IDX_SPEED, 65);
  }

  private static void setLink(NetworkTestProject project, int id, int column, double value) {
    project.links.getModel().setValueAt(value, project.links.getNumIndex(id), column);
  }

  private static void geometry(NetworkTestProject project, int id, double... coordinates) {
    ((OMPoly) graphic(project, id)).setLocation(coordinates, OMGraphic.DECIMAL_DEGREES);
  }

  private static OMGraphic graphic(NetworkTestProject project, int id) {
    return project.links.getEsriGraphicList().getOMGraphicAt(project.links.getNumIndex(id));
  }

  private static RealLink realLink(NetworkTestProject project, int id) {
    return (RealLink) graphic(project, id).getAttribute(0);
  }

  private static List<Integer> linkIds(NetworkTestProject project, TransportService service) {
    List<Integer> ids = new ArrayList<>();
    for (OMGraphic graphic : service.getLinks()) {
      int index = project.links.getEsriGraphicList().indexOf(graphic);
      ids.add(((Number) project.links.getModel().getValueAt(index, NodusC.DBF_IDX_NUM)).intValue());
    }
    return ids;
  }

  private static List<String> names(ServiceHandler handler) {
    List<String> names = new ArrayList<>();
    handler.getServiceNamesIterator().forEachRemaining(names::add);
    return names;
  }

  private static void assertSaved(
      NetworkTestProject project,
      int id,
      String name,
      int means,
      int frequency,
      List<Integer> route)
      throws Exception {
    ServiceHandler handler = project.getServiceHandler();
    assertEquals(
        List.of(
            List.of(
                Integer.toString(id),
                name,
                "3",
                Integer.toString(means),
                Integer.toString(frequency))),
        rows(
            project,
            "SELECT id,name,mode,means,frequency FROM " + handler.getServiceHeaderTableName()));
    assertEquals(
        List.of(
            List.of(Integer.toString(id), "0", route.get(0).toString()),
            List.of(Integer.toString(id), "1", route.get(1).toString())),
        rows(
            project,
            "SELECT id,pathidx,link FROM "
                + handler.getServiceLinkDetailTableName()
                + " ORDER BY pathidx"));
    assertEquals(
        List.of(
            List.of(Integer.toString(id), Integer.toString(ORIGIN)),
            List.of(Integer.toString(id), Integer.toString(DESTINATION))),
        rows(
            project,
            "SELECT id,stop FROM " + handler.getServiceStopDetailTableName() + " ORDER BY stop"));
  }

  private static List<List<String>> snapshot(NetworkTestProject project) throws Exception {
    ServiceHandler handler = project.getServiceHandler();
    List<List<String>> snapshot =
        rows(project, "SELECT * FROM " + handler.getServiceHeaderTableName() + " ORDER BY id");
    snapshot.addAll(
        rows(
            project,
            "SELECT * FROM " + handler.getServiceLinkDetailTableName() + " ORDER BY id,pathidx"));
    snapshot.addAll(
        rows(
            project,
            "SELECT * FROM " + handler.getServiceStopDetailTableName() + " ORDER BY id,stop"));
    return snapshot;
  }

  private static List<List<String>> rows(NetworkTestProject project, String sql) throws Exception {
    List<List<String>> rows = new ArrayList<>();
    try (Statement statement = project.getMainJDBCConnection().createStatement();
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

  private static void execute(NetworkTestProject project, String sql) throws Exception {
    try (Statement statement = project.getMainJDBCConnection().createStatement()) {
      statement.execute(sql);
    }
  }
}
