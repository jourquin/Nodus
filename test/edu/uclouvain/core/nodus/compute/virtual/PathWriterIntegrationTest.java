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

package edu.uclouvain.core.nodus.compute.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Exercises real SQL output using a fresh, private in-memory database for every test. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class PathWriterIntegrationTest {
  private Connection connection;
  private String databaseUrl;
  private RecordingPathWriter writer;

  @BeforeEach
  void openDatabase() throws SQLException {
    databaseUrl = "jdbc:h2:mem:nodus_paths_" + UUID.randomUUID();
    connection = DriverManager.getConnection(databaseUrl, "sa", "");
    connection.setAutoCommit(false);
    assertTrue(JDBCUtils.setConnection(connection));
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    try {
      if (writer != null) {
        writer.discard();
      }
    } finally {
      JDBCUtils.setConnection(null);
      if (connection != null) {
        connection.close();
      }
    }
  }

  @Test
  void pendingRowsKeepNumericSnapshotsAndCloseCommitsAllColumns() throws SQLException {
    createWriter(100, true);
    final PathWriterBuffer buffer = writer.newBuffer();
    final ODCell demand = new ODCell(3, 101, 202, 999, 90);
    final PathWeights weights = new PathWeights();
    weights.length = 12.345f;
    weights.ldCost = 1.125;
    weights.ulCost = 2.25;
    weights.trCost = 3.375;
    weights.tpCost = 4.5;
    weights.stpCost = 5.625;
    weights.swCost = 6.75;
    weights.mvCost = 7.875;
    weights.ldDuration = 11.125f;
    weights.ulDuration = 12.25f;
    weights.trDuration = 13.375f;
    weights.tpDuration = 14.5f;
    weights.stpDuration = 15.625f;
    weights.swDuration = 16.75f;
    weights.mvDuration = 17.875f;
    assertTrue(
        buffer.savePathHeader(
            2, demand, 42.1236, weights, (byte) 1, (byte) 2, (byte) 3, (byte) 4, 5, 77));
    weights.length = 999;
    weights.mvCost = 999;
    weights.mvDuration = 999;
    demand.addQuantity(100);
    assertTrue(buffer.flush());
    assertEquals(0, scalar("SELECT COUNT(*) FROM unit_paths7_header"));

    assertTrue(writer.close());
    assertTrue(writer.close());
    assertNull(writer.failure);
    try (Connection observer = DriverManager.getConnection(databaseUrl, "sa", "");
        Statement statement = observer.createStatement();
        ResultSet row = statement.executeQuery("SELECT * FROM unit_paths7_header")) {
      assertTrue(row.next());
      assertEquals(3, row.getInt(NodusC.DBF_GROUP));
      assertEquals(101, row.getInt(NodusC.DBF_ORIGIN));
      assertEquals(202, row.getInt(NodusC.DBF_DESTINATION));
      assertEquals(90, row.getInt(NodusC.DBF_TIME));
      assertEquals(2, row.getInt(NodusC.DBF_ITERATION));
      assertEquals(42.124, row.getDouble(NodusC.DBF_QUANTITY), 1e-9);
      assertEquals(12.345, row.getDouble(NodusC.DBF_LENGTH), 1e-6);
      assertEquals(1.125, row.getDouble(NodusC.DBF_LDCOST), 1e-9);
      assertEquals(2.25, row.getDouble(NodusC.DBF_ULCOST), 1e-9);
      assertEquals(3.375, row.getDouble(NodusC.DBF_TRCOST), 1e-9);
      assertEquals(4.5, row.getDouble(NodusC.DBF_TPCOST), 1e-9);
      assertEquals(5.625, row.getDouble(NodusC.DBF_STCOST), 1e-9);
      assertEquals(6.75, row.getDouble(NodusC.DBF_SWCOST), 1e-9);
      assertEquals(7.875, row.getDouble(NodusC.DBF_MVCOST), 1e-9);
      assertEquals(11.125, row.getDouble(NodusC.DBF_LDDURATION), 1e-9);
      assertEquals(12.25, row.getDouble(NodusC.DBF_ULDURATION), 1e-9);
      assertEquals(13.375, row.getDouble(NodusC.DBF_TRDURATION), 1e-9);
      assertEquals(14.5, row.getDouble(NodusC.DBF_TPDURATION), 1e-9);
      assertEquals(15.625, row.getDouble(NodusC.DBF_STDURATION), 1e-9);
      assertEquals(16.75, row.getDouble(NodusC.DBF_SWDURATION), 1e-9);
      assertEquals(17.875, row.getDouble(NodusC.DBF_MVDURATION), 1e-9);
      assertEquals(1, row.getInt(NodusC.DBF_LDMODE));
      assertEquals(2, row.getInt(NodusC.DBF_LDMEANS));
      assertEquals(3, row.getInt(NodusC.DBF_ULMODE));
      assertEquals(4, row.getInt(NodusC.DBF_ULMEANS));
      assertEquals(5, row.getInt(NodusC.DBF_NBTRANS));
      assertEquals(77, row.getInt(NodusC.DBF_PATH_INDEX));
      assertFalse(row.next());
    }
  }

  @Test
  void automaticFlushKeepsRepeatedAndReverseLinksWithTheirPath() throws SQLException {
    createWriter(2, true);
    final VirtualLink forward = movingLink(10, 20);
    final PathWriterBuffer buffer = writer.newBuffer();
    buffer.savePathLink(forward);
    buffer.savePathLink(forward); // Automatic handoff in the middle of a path.
    buffer.savePathLink(movingLink(20, 10));
    assertTrue(saveHeader(buffer, 1, 25));
    buffer.savePathLink(forward);
    assertTrue(saveHeader(buffer, 1, 50));
    assertTrue(buffer.flush());
    assertTrue(writer.close());

    assertEquals(2, scalar("SELECT COUNT(*) FROM unit_paths7_header"));
    assertEquals(4, scalar("SELECT COUNT(*) FROM unit_paths7_detail"));
    assertEquals(2, scalar("SELECT COUNT(*) FROM unit_paths7_detail WHERE pathidx=1 AND link=17"));
    assertEquals(1, scalar("SELECT COUNT(*) FROM unit_paths7_detail WHERE pathidx=1 AND link=-17"));
    assertEquals(1, scalar("SELECT COUNT(*) FROM unit_paths7_detail WHERE pathidx=2 AND link=17"));
    assertEquals(4, scalar("SELECT COUNT(*) FROM unit_paths7_detail WHERE mode=2 AND means=3"));
  }

  @Test
  void explicitPathIdsLeaveAnUnfinishedImplicitPathIntact() throws SQLException {
    createWriter(2, true);
    final PathWriterBuffer buffer = writer.newBuffer();
    buffer.savePathLink(movingLink(10, 20));
    buffer.savePathLink(movingLink(20, 10), 90);
    assertTrue(
        buffer.savePathHeader(
            1,
            new ODCell(1, 10, 20, 60),
            60,
            new PathWeights(),
            (byte) 2,
            (byte) 3,
            (byte) 2,
            (byte) 3,
            0,
            90));
    assertTrue(saveHeader(buffer, 1, 30));
    assertTrue(buffer.flush());
    assertTrue(writer.close());

    assertEquals(30, scalar("SELECT qty FROM unit_paths7_header WHERE pathidx=1"));
    assertEquals(60, scalar("SELECT qty FROM unit_paths7_header WHERE pathidx=90"));
    assertEquals(17, scalar("SELECT link FROM unit_paths7_detail WHERE pathidx=1"));
    assertEquals(-17, scalar("SELECT link FROM unit_paths7_detail WHERE pathidx=90"));
  }

  @Test
  void equilibriumSplitIncludesPendingJdbcBatches() throws SQLException {
    createWriter(100, true);
    final PathWriterBuffer buffer = writer.newBuffer();
    assertTrue(saveHeader(buffer, 1, 100));
    assertTrue(saveHeader(buffer, 2, 80));
    assertTrue(buffer.flush());
    writer.splitPaths(2, 0.25);
    assertTrue(writer.close());

    assertEquals(75, scalar("SELECT qty FROM unit_paths7_header WHERE iteration=1"));
    assertEquals(20, scalar("SELECT qty FROM unit_paths7_header WHERE iteration=2"));
  }

  @Test
  void legacyModeRoundsMovingDurationBeforeSaving() throws SQLException {
    createWriter(100, false);
    final PathWeights weights = new PathWeights();
    weights.mvDuration = 17.875f;
    final PathWriterBuffer buffer = writer.newBuffer();
    assertTrue(
        buffer.savePathHeader(
            1, new ODCell(1, 10, 20, 1), 1, weights, (byte) 2, (byte) 3, (byte) 2, (byte) 3, 0));
    assertTrue(buffer.flush());
    assertTrue(writer.close());
    assertEquals(18, weights.mvDuration);
    assertEquals(18, scalar("SELECT " + NodusC.DBF_MVDURATION + " FROM unit_paths7_header"));
  }

  @Test
  void clearingAbandonsLocalRowsAndDiscardRemovesAlreadyWrittenOutput() throws SQLException {
    createWriter(3, true);
    final PathWriterBuffer buffer = writer.newBuffer();
    buffer.savePathLink(movingLink(10, 20));
    assertTrue(saveHeader(buffer, 1, 123));
    buffer.clear();
    assertTrue(buffer.flush());
    assertTrue(writer.close());
    assertEquals(0, scalar("SELECT COUNT(*) FROM unit_paths7_header"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM unit_paths7_detail"));
    writer.discard();

    createWriter(1, true);
    final PathWriterBuffer accepted = writer.newBuffer();
    accepted.savePathLink(movingLink(10, 20));
    assertTrue(saveHeader(accepted, 1, 45));
    assertEquals(1, scalar("SELECT COUNT(*) FROM unit_paths7_header"));
    writer.discard();
    assertFalse(JDBCUtils.tableExists("unit_paths7_header"));
    assertFalse(JDBCUtils.tableExists("unit_paths7_detail"));
    assertFalse(saveHeader(accepted, 1, 1));
    assertFalse(accepted.flush());
  }

  @Test
  void invalidQuantityStopsOtherWorkersAndRejectsFinalization() throws SQLException {
    createWriter(100, true);
    final PathWriterBuffer waitingWorker = writer.newBuffer();
    assertTrue(saveHeader(waitingWorker, 1, 50));
    final PathWriterBuffer failingWorker = writer.newBuffer();
    assertFalse(saveHeader(failingWorker, 1, Double.NaN));
    assertInstanceOf(IllegalArgumentException.class, writer.failure);
    assertFalse(waitingWorker.flush());
    assertFalse(saveHeader(waitingWorker, 1, 25));
    assertFalse(writer.close());
    assertEquals(0, scalar("SELECT COUNT(*) FROM unit_paths7_header"));
  }

  @Test
  void databaseBatchFailureStopsTheSharedWriter() throws SQLException {
    createWriter(1, true);
    final PathWriterBuffer buffer = writer.newBuffer();
    // This finite value exceeds the quantity column's NUMERIC(13,3) capacity.
    assertFalse(saveHeader(buffer, 1, 1e20));
    assertInstanceOf(SQLException.class, writer.failure);
    assertFalse(writer.newBuffer().flush());
    assertFalse(writer.close());
  }

  @Test
  void concurrentBuffersSaveEveryPathWithUniqueIdsAndMatchingDetails() throws Exception {
    createWriter(3, true);
    final int workerCount = 4;
    final int pathsPerWorker = 20;
    final CountDownLatch ready = new CountDownLatch(workerCount);
    final CountDownLatch start = new CountDownLatch(1);
    final ExecutorService executor = Executors.newFixedThreadPool(workerCount);
    try {
      final List<Future<Void>> results = new ArrayList<>();
      for (int worker = 0; worker < workerCount; worker++) {
        final int workerId = worker + 1;
        results.add(
            executor.submit(
                () -> {
                  final PathWriterBuffer buffer = writer.newBuffer();
                  final VirtualLink link = movingLink(10, 20, workerId);
                  ready.countDown();
                  assertTrue(start.await(10, TimeUnit.SECONDS));
                  for (int path = 0; path < pathsPerWorker; path++) {
                    buffer.savePathLink(link);
                    buffer.savePathLink(link);
                    assertTrue(saveHeader(buffer, 1, workerId));
                  }
                  assertTrue(buffer.flush());
                  return null;
                }));
      }
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      for (Future<Void> result : results) {
        result.get(10, TimeUnit.SECONDS);
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
    assertTrue(writer.close());
    assertNull(writer.failure);
    assertEquals(80, scalar("SELECT COUNT(*) FROM unit_paths7_header"));
    assertEquals(80, scalar("SELECT COUNT(DISTINCT pathidx) FROM unit_paths7_header"));
    assertEquals(200, scalar("SELECT SUM(qty) FROM unit_paths7_header"));
    assertEquals(160, scalar("SELECT COUNT(*) FROM unit_paths7_detail"));
    assertEquals(
        0,
        scalar(
            "SELECT COUNT(*) FROM unit_paths7_header h WHERE "
                + "(SELECT COUNT(*) FROM unit_paths7_detail d "
                + "WHERE d.pathidx=h.pathidx AND d.link=h.qty) <> 2"));
  }

  private void createWriter(int batchSize, boolean durationFunctions) {
    final AssignmentParameters parameters =
        new AssignmentParameters(new HeadlessProject(connection, batchSize));
    parameters.setScenario(7);
    parameters.setSavePaths(true);
    parameters.setDetailedPaths(true);
    parameters.setDurationFunctions(durationFunctions);
    writer = new RecordingPathWriter(parameters);
  }

  private static boolean saveHeader(PathWriterBuffer buffer, int iteration, double quantity) {
    return buffer.savePathHeader(
        iteration,
        new ODCell(1, 10, 20, 100),
        quantity,
        new PathWeights(),
        (byte) 2,
        (byte) 3,
        (byte) 2,
        (byte) 3,
        0);
  }

  private double scalar(String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      assertTrue(rows.next());
      double value = rows.getDouble(1);
      assertFalse(rows.next());
      return value;
    }
  }

  private static VirtualLink movingLink(int from, int to) {
    return movingLink(from, to, 17);
  }

  private static VirtualLink movingLink(int from, int to, int linkId) {
    final VirtualNode begin = new VirtualNode(1, from, linkId, (byte) 2, (byte) 3, (short) 0, 0, 0);
    final VirtualNode end = new VirtualNode(2, to, linkId, (byte) 2, (byte) 3, (short) 0, 0, 0);
    return new VirtualLink(1, 0, 0, begin, end, new RealLink());
  }

  /** Supplies only output settings and an in-memory connection; opens no GUI or project files. */
  private static final class HeadlessProject extends NodusProject {
    private final Connection connection;
    private final int batchSize;

    HeadlessProject(Connection connection, int batchSize) {
      super(null);
      this.connection = connection;
      this.batchSize = batchSize;
    }

    @Override
    public Connection getMainJDBCConnection() {
      return connection;
    }

    @Override
    public String getLocalProperty(String key) {
      return getLocalProperty(key, (String) null);
    }

    @Override
    public String getLocalProperty(String key, String defaultValue) {
      return NodusC.PROP_PROJECT_DOTNAME.equals(key) || NodusC.PROP_PATH_TABLE_PREFIX.equals(key)
          ? "unit_paths"
          : defaultValue;
    }

    @Override
    public int getLocalProperty(String key, int defaultValue) {
      return NodusC.PROP_MAX_SQL_BATCH_SIZE.equals(key) ? batchSize : defaultValue;
    }
  }

  /** Records failures instead of opening the production error dialog. */
  private static final class RecordingPathWriter extends PathWriter {
    private Exception failure;

    RecordingPathWriter(AssignmentParameters parameters) {
      super(parameters);
    }

    @Override
    void showWriteError(Exception error) {
      failure = error;
    }
  }
}
