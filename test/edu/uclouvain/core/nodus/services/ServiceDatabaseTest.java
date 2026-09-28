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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real H2 persistence with independent SQL expectations and explicit transaction ownership. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class ServiceDatabaseTest {
  @TempDir Path directory;
  private final OMGraphic first = new OMPoint(0.0, 0.0);
  private final OMGraphic second = new OMPoint(1.0, 1.0);

  @TestFactory
  Stream<DynamicTest> writesExactRowsWithAndWithoutBatching() {
    return Stream.of(false, true)
        .flatMap(
            batch ->
                Stream.of(-2, 1, 2, 100)
                    .map(
                        size ->
                            DynamicTest.dynamicTest(
                                "batch=" + batch + ", size=" + size,
                                () -> {
                                  try (DatabaseFixture fixture = fixture()) {
                                    final TransportService route =
                                        service(7, "old name", first, second, first);
                                    route.addStop(10);
                                    route.addStop(30);
                                    final Map<String, TransportService> services =
                                        new LinkedHashMap<>();
                                    services.put("L'été – 東京", route);
                                    services.put("Empty", service(8, "Empty"));
                                    services.put("Other", service(9, "Other", second));
                                    ServiceDatabase database = database(fixture.connection);
                                    database.insert(services, this::id, batch, size);
                                    assertEquals(
                                        List.of(
                                            List.of("7", "L'été – 東京", "3", "2", "365"),
                                            List.of("8", "Empty", "3", "2", "365"),
                                            List.of("9", "Other", "3", "2", "365")),
                                        rows(
                                            fixture.connection,
                                            "SELECT * FROM headers ORDER BY id"));
                                    assertEquals(
                                        List.of(
                                            List.of("7", "0", "11"),
                                            List.of("7", "1", "12"),
                                            List.of("7", "2", "11"),
                                            List.of("9", "0", "12")),
                                        rows(
                                            fixture.connection,
                                            "SELECT * FROM links ORDER BY id,pathidx"));
                                    assertEquals(
                                        List.of(List.of("7", "10"), List.of("7", "30")),
                                        rows(
                                            fixture.connection,
                                            "SELECT * FROM stops ORDER BY id,stop"));
                                    List<TransportService> loaded =
                                        database.load(true, this::graphic);
                                    TransportService actual =
                                        loaded
                                            .stream()
                                            .filter(s -> s.getId() == 7)
                                            .findFirst()
                                            .orElseThrow();
                                    assertEquals("L'été – 東京", actual.getName());
                                    assertEquals(List.of(first, second, first), actual.getLinks());
                                    assertEquals(List.of(10, 30), actual.getStopNodes());
                                    assertEquals(3, actual.getMode());
                                    assertEquals(2, actual.getMeans());
                                    assertEquals(365, actual.getFrequency());
                                    assertEquals(
                                        0,
                                        loaded
                                            .stream()
                                            .filter(s -> s.getId() == 8)
                                            .findFirst()
                                            .orElseThrow()
                                            .getNbLinks());
                                    assertTrue(fixture.connection.getAutoCommit());
                                    assertFalse(fixture.connection.isClosed());
                                  }
                                })));
  }

  @Test
  void loadUsesPathPositionsAndIgnoresOrphansMissingGraphicsAndDuplicateStops() throws Exception {
    try (DatabaseFixture fixture = fixture()) {
      fixture.execute("INSERT INTO headers VALUES(7,'Route',3,2,365),(8,'Empty',3,2,10)");
      fixture.execute("INSERT INTO links VALUES(7,3,11),(99,0,11),(7,1,12),(7,0,11),(7,2,999)");
      fixture.execute("INSERT INTO stops VALUES(7,10),(99,55),(7,30),(7,10)");
      List<TransportService> loaded = database(fixture.connection).load(true, this::graphic);
      TransportService route =
          loaded.stream().filter(s -> s.getId() == 7).findFirst().orElseThrow();
      assertEquals(2, loaded.size());
      assertEquals(List.of(first, second, first), route.getLinks());
      assertEquals(List.of(10, 30), route.getStopNodes());
    }
  }

  @Test
  void missingGraphicsKeepGapsInSavedPathPositions() throws Exception {
    try (DatabaseFixture fixture = fixture()) {
      TransportService route = service(7, "Route", first, new OMPoint(), second);
      database(fixture.connection).insert(Map.of("Route", route), this::id, true, 2);
      assertEquals(
          List.of(List.of("0", "11"), List.of("2", "12")),
          rows(fixture.connection, "SELECT pathidx,link FROM links ORDER BY pathidx"));
    }
  }

  @Test
  void duplicateLegacyHeadersReceiveIndependentDetailLists() throws Exception {
    try (DatabaseFixture fixture = fixture()) {
      fixture.execute("INSERT INTO headers VALUES(7,'One',3,2,365),(7,'Two',3,2,10)");
      fixture.execute("INSERT INTO links VALUES(7,0,11),(7,1,12)");
      fixture.execute("INSERT INTO stops VALUES(7,10),(7,30)");
      List<TransportService> loaded = database(fixture.connection).load(true, this::graphic);
      assertEquals(2, loaded.size());
      for (TransportService service : loaded) {
        assertEquals(List.of(first, second), service.getLinks());
        assertEquals(List.of(10, 30), service.getStopNodes());
      }
      loaded.get(0).getLinks().clear();
      loaded.get(0).getStopNodes().clear();
      assertEquals(List.of(first, second), loaded.get(1).getLinks());
      assertEquals(List.of(10, 30), loaded.get(1).getStopNodes());
    }
  }

  @Test
  void legacyTablesWithoutPathIndexRemainReadable() throws Exception {
    try (DatabaseFixture fixture = fixture()) {
      fixture.execute("ALTER TABLE links DROP COLUMN pathidx");
      fixture.execute("INSERT INTO headers VALUES(7,'Route',3,2,365)");
      fixture.execute("INSERT INTO links VALUES(7,11)");
      assertEquals(
          List.of(first),
          database(fixture.connection).load(false, this::graphic).get(0).getLinks());
    }
  }

  @Test
  void emptyDatabaseLoadsWithoutReadingAbsentDetailTables() throws Exception {
    try (DatabaseFixture fixture = fixture()) {
      fixture.execute("DROP TABLE links");
      fixture.execute("DROP TABLE stops");
      assertTrue(database(fixture.connection).load(true, this::graphic).isEmpty());
    }
  }

  @Test
  void failedReadPropagatesAndAllowsACompleteRetry() throws Exception {
    try (DatabaseFixture fixture = fixture()) {
      fixture.execute("INSERT INTO headers VALUES(7,'Route',3,2,365)");
      fixture.execute("INSERT INTO links VALUES(7,0,11)");
      fixture.execute("DROP TABLE stops");
      final ServiceDatabase database = database(fixture.connection);
      assertThrows(SQLException.class, () -> database.load(true, this::graphic));
      assertFalse(fixture.connection.isClosed());
      fixture.execute("CREATE TABLE stops(id INTEGER,stop INTEGER)");
      fixture.execute("INSERT INTO stops VALUES(7,10)");
      List<TransportService> loaded = database.load(true, this::graphic);
      assertEquals(1, loaded.size());
      assertEquals(List.of(first), loaded.get(0).getLinks());
      assertEquals(List.of(10), loaded.get(0).getStopNodes());
    }
  }

  @TestFactory
  Stream<DynamicTest> failedWritesCanBeRolledBackWithoutLosingEarlierWork() {
    return Stream.of(false, true)
        .map(
            batch ->
                DynamicTest.dynamicTest(
                    "batch=" + batch,
                    () -> {
                      try (DatabaseFixture fixture = fixture()) {
                        fixture.execute("INSERT INTO headers VALUES(1,'Saved',3,2,10)");
                        fixture.execute("CREATE TABLE unrelated(marker INTEGER)");
                        fixture.connection.setAutoCommit(false);
                        fixture.execute("INSERT INTO unrelated VALUES(42)");
                        final Savepoint before = fixture.connection.setSavepoint();
                        fixture.execute("DELETE FROM headers");
                        final TransportService route = service(7, "New", first, second);
                        final ServiceDatabase database = database(fixture.connection);
                        assertThrows(
                            SQLException.class,
                            () ->
                                database.insert(
                                    Map.of("New", route), g -> g == first ? 11 : -2, batch, 1));
                        fixture.connection.rollback(before);
                        assertEquals(
                            List.of(List.of("1", "Saved", "3", "2", "10")),
                            rows(fixture.connection, "SELECT * FROM headers"));
                        assertTrue(rows(fixture.connection, "SELECT * FROM links").isEmpty());
                        assertEquals(
                            List.of(List.of("42")),
                            rows(fixture.connection, "SELECT * FROM unrelated"));
                        assertFalse(fixture.connection.getAutoCommit());
                        fixture.connection.rollback();
                        assertTrue(rows(fixture.connection, "SELECT * FROM unrelated").isEmpty());
                      }
                    }));
  }

  @Test
  void insertDoesNotCommitItsCallersTransaction() throws Exception {
    try (DatabaseFixture fixture = fixture();
        Connection observer =
            DriverManager.getConnection(fixture.connection.getMetaData().getURL(), "sa", "")) {
      fixture.connection.setAutoCommit(false);
      database(fixture.connection)
          .insert(Map.of("Route", service(7, "Route", first)), this::id, true, 1);
      assertEquals(1, rows(fixture.connection, "SELECT * FROM headers").size());
      assertTrue(rows(observer, "SELECT * FROM headers").isEmpty());
      fixture.connection.commit();
      assertEquals(1, rows(observer, "SELECT * FROM headers").size());
      assertFalse(fixture.connection.getAutoCommit());
    }
  }

  private DatabaseFixture fixture() throws Exception {
    DatabaseFixture fixture = new DatabaseFixture(directory);
    fixture.execute(
        "CREATE TABLE headers(id INTEGER,name VARCHAR(30),"
            + "mode INTEGER,means INTEGER,frequency INTEGER)");
    fixture.execute("CREATE TABLE links(id INTEGER,pathidx INTEGER,link INTEGER CHECK(link>0))");
    fixture.execute("CREATE TABLE stops(id INTEGER,stop INTEGER)");
    return fixture;
  }

  private static ServiceDatabase database(Connection connection) {
    return new ServiceDatabase(connection, "headers", "links", "stops");
  }

  private static TransportService service(int id, String name, OMGraphic... links) {
    TransportService service = new TransportService(id, name, (byte) 3, (byte) 2, 365);
    for (OMGraphic link : links) {
      service.addChunk(link);
    }
    return service;
  }

  private int id(OMGraphic graphic) {
    return graphic == first ? 11 : graphic == second ? 12 : -1;
  }

  private OMGraphic graphic(int id) {
    return id == 11 ? first : id == 12 ? second : null;
  }

  private static List<List<String>> rows(Connection connection, String sql) throws SQLException {
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
}
