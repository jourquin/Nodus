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

package edu.uclouvain.core.nodus.compute.assign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.real.RealNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Complete assignments through real demand, virtual-network, cost, worker and SQL output code.
 * Nodes A=1, B=2, C=3 and D=4 form two bidirectional routes: A-B-D costs 2+3, A-C-D costs 4+4.
 * Demand is 100 from A to D (group 1) and 40 from B to D (group 2).
 */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class AllOrNothingAssignmentIntegrationTest {
  @TempDir Path directory;

  @Test
  void fourNodeAssignmentMatchesHandCalculatedPathsFlowsAndCosts() throws Exception {
    try (ReferenceProject project = new ReferenceProject(directory)) {
      project.runAssignment(1);
      assertBaseline(project);
    }
  }

  @Test
  void concurrentWorkersProduceTheSameResultsAsOneWorker() throws Exception {
    try (ReferenceProject project = new ReferenceProject(directory)) {
      project.runAssignment(1);
      assertBaseline(project);
      final List<List<String>> serial = project.snapshot();
      project.runAssignment(4);
      assertBaseline(project);
      assertEquals(2, project.panel.workers.size(), "Both commodity jobs must run concurrently");
      assertEquals(serial, project.snapshot());
    }
  }

  @Test
  void closingBToDReroutesBothDemands() throws Exception {
    try (ReferenceProject project = new ReferenceProject(directory)) {
      project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
      project.runAssignment(4);
      assertEquals(2, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
      assertEquals(8, project.number("SELECT mvcost FROM mini_paths1_header WHERE grp=1"));
      assertEquals(10, project.number("SELECT mvcost FROM mini_paths1_header WHERE grp=2"));
      assertEquals(
          List.of(
              List.of("1", "1", "4", "13", "1", "1"),
              List.of("1", "1", "4", "14", "1", "1"),
              List.of("2", "2", "4", "-11", "1", "1"),
              List.of("2", "2", "4", "13", "1", "1"),
              List.of("2", "2", "4", "14", "1", "1")),
          project.pathDetails());
      assertFlow(project, 11, 0, 0, 2, true);
      assertFlow(project, 11, 0, 40, 2, false);
      assertFlow(project, 13, 100, 40, 4);
      assertFlow(project, 14, 100, 40, 4);
      assertEquals(
          0,
          project.number(
              "SELECT COUNT(*) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_MOVE
                  + " AND link1=12"));
      assertConservation(project, 140, 1200);
    }
  }

  @Test
  void isolatingBLeavesItsDemandUnassignedWhileAReroutes() throws Exception {
    try (ReferenceProject project = new ReferenceProject(directory)) {
      project.links.getModel().setValueAt(0.0, 0, NodusC.DBF_IDX_ENABLED);
      project.links.getModel().setValueAt(0.0, 1, NodusC.DBF_IDX_ENABLED);
      project.runAssignment(4);
      assertEquals(1, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
      assertEquals(100, project.number("SELECT SUM(qty) FROM mini_paths1_header"));
      assertEquals(8, project.number("SELECT mvcost FROM mini_paths1_header WHERE grp=1"));
      assertEquals(0, project.number("SELECT COUNT(*) FROM mini_paths1_header WHERE grp=2"));
      assertEquals(
          List.of(List.of("1", "1", "4", "13", "1", "1"), List.of("1", "1", "4", "14", "1", "1")),
          project.pathDetails());
      assertFlow(project, 13, 100, 0, 4);
      assertFlow(project, 14, 100, 0, 4);
      assertEquals(
          0,
          project.number(
              "SELECT COUNT(*) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_MOVE
                  + " AND link1 IN (11,12)"));
      assertConservation(project, 100, 800);
      assertEquals(140, project.number("SELECT SUM(qty) FROM mini_od"));
    }
  }

  @Test
  void repeatingTheSameAssignmentReplacesOutputsAndDoesNotAccumulateFlow() throws Exception {
    try (ReferenceProject project = new ReferenceProject(directory)) {
      project.runAssignment(4);
      assertBaseline(project);
      final List<List<String>> first = project.snapshot();
      project.runAssignment(4);
      assertBaseline(project);
      assertEquals(first, project.snapshot());
    }
  }

  private static void assertBaseline(ReferenceProject project) throws Exception {
    assertEquals(
        List.of(
            List.of("1", "1", "4", "100.000", "5.000"), List.of("2", "2", "4", "40.000", "3.000")),
        project.rows("SELECT grp, org, dst, qty, mvcost FROM mini_paths1_header ORDER BY grp"));
    assertEquals(
        List.of(
            List.of("1", "1", "4", "11", "1", "1"),
            List.of("1", "1", "4", "12", "1", "1"),
            List.of("2", "2", "4", "12", "1", "1")),
        project.pathDetails());
    assertEquals(
        0,
        project.number(
            "SELECT SUM(ldcost+ulcost+trcost+tpcost+stcost+swcost) " + "FROM mini_paths1_header"));
    assertFlow(project, 11, 100, 0, 2);
    assertFlow(project, 12, 100, 40, 3);
    assertFlow(project, 13, 0, 0, 4);
    assertFlow(project, 14, 0, 0, 4);
    assertConservation(project, 140, 620);
    assertEquals(140, project.number("SELECT SUM(qty) FROM mini_od"));
  }

  private static void assertFlow(
      ReferenceProject project, int link, double group1, double group2, double cost)
      throws Exception {
    assertFlow(project, link, group1, group2, cost, true);
    assertFlow(project, link, 0, 0, cost, false);
  }

  private static void assertFlow(
      ReferenceProject project,
      int link,
      double group1,
      double group2,
      double cost,
      boolean forward)
      throws Exception {
    String moving =
        " FROM mini_vnet1 WHERE vtype="
            + VirtualLink.TYPE_MOVE
            + " AND link1="
            + link
            + (forward ? " AND ABS(node1)<ABS(node2)" : " AND ABS(node1)>ABS(node2)");
    assertEquals(1, project.number("SELECT COUNT(*)" + moving));
    assertEquals(group1, project.number("SELECT qty1" + moving));
    assertEquals(group2, project.number("SELECT qty2" + moving));
    assertEquals(group1 + group2, project.number("SELECT qty" + moving));
    assertEquals((group1 + group2) / 10, project.number("SELECT veh" + moving));
    assertEquals(cost, project.number("SELECT ucost1" + moving));
    assertEquals(cost, project.number("SELECT ucost2" + moving));
  }

  private static void assertConservation(ReferenceProject project, double demand, double cost)
      throws Exception {
    assertEquals(
        demand,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_LOAD));
    assertEquals(
        demand,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_UNLOAD));
    assertEquals(
        cost,
        project.number(
            "SELECT SUM(qty*ucost) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_MOVE));
    assertEquals(
        cost,
        project.number(
            "SELECT SUM(qty*(ldcost+ulcost+trcost+tpcost+stcost+swcost+mvcost)) "
                + "FROM mini_paths1_header"));
    // Every internal node conserves flow after accounting for loading and unloading there.
    for (int node = 1; node <= 4; node++) {
      String at = " FROM mini_vnet1 WHERE ABS(node1)=" + node + " AND vtype=";
      double departing = project.number("SELECT SUM(qty)" + at + VirtualLink.TYPE_MOVE);
      double arriving =
          project.number(
              "SELECT SUM(qty) FROM mini_vnet1 WHERE ABS(node2)="
                  + node
                  + " AND vtype="
                  + VirtualLink.TYPE_MOVE);
      double loaded = project.number("SELECT SUM(qty)" + at + VirtualLink.TYPE_LOAD);
      double unloaded = project.number("SELECT SUM(qty)" + at + VirtualLink.TYPE_UNLOAD);
      assertEquals(arriving + loaded, departing + unloaded, "Flow balance at node " + node);
    }
  }

  private static final class ReferenceProject extends NodusProject implements AutoCloseable {
    private final Connection connection;
    private final Properties properties = new Properties();
    private final ReferenceLayer nodes = nodes();
    private final ReferenceLayer links = links();
    private final HeadlessPanel panel;
    private final ServiceHandler services;

    ReferenceProject(Path directory) throws Exception {
      super(null);
      connection =
          DriverManager.getConnection("jdbc:h2:mem:assignment_" + UUID.randomUUID(), "sa", "");
      assertTrue(JDBCUtils.setConnection(connection));
      properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, directory + File.separator);
      properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "mini");
      properties.setProperty(NodusC.PROP_PATH_TABLE_PREFIX, "mini_paths");
      properties.setProperty(NodusC.PROP_SAVE_ALL_VN, "true");
      properties.setProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, "2");
      panel = new HeadlessPanel(this);
      services = new ServiceHandler(this, false);
      execute("CREATE TABLE mini_od (grp INTEGER, org INTEGER, dst INTEGER, qty NUMERIC(12,3))");
      execute("INSERT INTO mini_od VALUES (1,1,4,100), (2,2,4,40)");
      connection.setAutoCommit(false);
    }

    void runAssignment(int threads) {
      final AssignmentParameters parameters = new AssignmentParameters(this);
      final Properties costs = new Properties();
      costs.setProperty("ld.1,1", "0");
      costs.setProperty("ul.1,1", "0");
      costs.setProperty("tr.1,1", "0");
      costs.setProperty("mv.1,1", "BASECOST");
      costs.setProperty("AVGLOAD.1,1", "10");
      costs.setProperty("PCU.1,1", "1");
      parameters.setCostFunctions(costs);
      parameters.setScenario(1);
      parameters.setODMatrix("mini_od");
      parameters.setWhereStmt("");
      parameters.setThreads(threads);
      parameters.setSavePaths(true);
      parameters.setDetailedPaths(true);
      panel.prepareRun(threads);
      new AllOrNothingAssignment(parameters).run();
      assertEquals(SoundPlayer.SOUND_OK, panel.completionSound, "The assignment must succeed");
      assertEquals(Math.min(threads, 2), panel.workers.size());
      for (Thread worker : panel.workers) {
        assertFalse(worker.isAlive(), "Worker still running after assignment completion");
      }
      assertTrue(panel.getAssignmentMenuItem().isEnabled());
    }

    List<List<String>> snapshot() throws Exception {
      List<List<String>> result =
          new ArrayList<>(
              rows("SELECT * FROM mini_vnet1 " + "ORDER BY node1,node2,link1,link2,vtype"));
      result.addAll(
          rows(
              "SELECT grp,org,dst,qty,ldcost,ulcost,trcost,tpcost,stcost,swcost,mvcost "
                  + "FROM mini_paths1_header ORDER BY grp,org,dst"));
      result.addAll(pathDetails());
      return result;
    }

    List<List<String>> pathDetails() throws Exception {
      return rows(
          "SELECT h.grp,h.org,h.dst,d.link,d.mode,d.means FROM mini_paths1_header h "
              + "JOIN mini_paths1_detail d ON h.pathidx=d.pathidx "
              + "ORDER BY h.grp,h.org,h.dst,d.link");
    }

    List<List<String>> rows(String sql) throws Exception {
      List<List<String>> result = new ArrayList<>();
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery(sql)) {
        while (rows.next()) {
          List<String> row = new ArrayList<>();
          for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
            row.add(rows.getString(i));
          }
          result.add(row);
        }
      }
      return result;
    }

    double number(String sql) throws Exception {
      try (Statement statement = connection.createStatement();
          ResultSet result = statement.executeQuery(sql)) {
        assertTrue(result.next(), sql);
        return result.getDouble(1);
      }
    }

    private void execute(String sql) throws Exception {
      try (Statement statement = connection.createStatement()) {
        statement.execute(sql);
      }
    }

    @Override
    public NodusMapPanel getNodusMapPanel() {
      return panel;
    }

    @Override
    public Connection getMainJDBCConnection() {
      return connection;
    }

    @Override
    public NodusEsriLayer[] getNodeLayers() {
      return new NodusEsriLayer[] {nodes};
    }

    @Override
    public NodusEsriLayer[] getLinkLayers() {
      return new NodusEsriLayer[] {links};
    }

    @Override
    public ServiceHandler getServiceHandler() {
      return services;
    }

    @Override
    public String getLocalProperty(String key) {
      return properties.getProperty(key);
    }

    @Override
    public String getLocalProperty(String key, String fallback) {
      return properties.getProperty(key, fallback);
    }

    @Override
    public int getLocalProperty(String key, int fallback) {
      return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
    }

    @Override
    public void close() {
      JDBCUtils.setConnection(null);
      try {
        connection.close();
      } catch (java.sql.SQLException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static ReferenceLayer nodes() {
    final DbfTableModel model = model("NUM", "STYLE", "TRANSHIP");
    final EsriPointList graphics = new EsriPointList();
    for (int node = 1; node <= 4; node++) {
      model.addRecord(record(node, 0, NodusC.HANDLING_LOAD_UNLOAD));
      EsriPoint point = new EsriPoint(0, node * 0.01);
      point.putAttribute(0, new RealNode());
      graphics.add(point);
    }
    return new ReferenceLayer("nodes", model, graphics);
  }

  private static ReferenceLayer links() {
    final DbfTableModel model =
        model(
            "NUM",
            "STYLE",
            "ENABLED",
            "NODE1",
            "NODE2",
            "MODE",
            "MEANS",
            "CAPACITY",
            "SPEED",
            "BASECOST");
    final EsriPolylineList graphics = new EsriPolylineList();
    // Link ID, first node, second node, and cost in either direction.
    int[][] links = {{11, 1, 2, 2}, {12, 2, 4, 3}, {13, 1, 3, 4}, {14, 3, 4, 4}};
    for (int[] link : links) {
      model.addRecord(record(link[0], 0, 1, link[1], link[2], 1, 1, 1000, 50, link[3]));
      graphics.add(
          new EsriPolyline(
              new double[] {0, link[1] * 0.01, 0, link[2] * 0.01},
              OMGraphic.DECIMAL_DEGREES,
              OMGraphic.LINETYPE_STRAIGHT));
    }
    return new ReferenceLayer("links", model, graphics);
  }

  private static DbfTableModel model(String... columns) {
    DbfTableModel model = new DbfTableModel(columns.length);
    for (int i = 0; i < columns.length; i++) {
      model.setColumnName(i, columns[i]);
      model.setType(i, DbfTableModel.TYPE_NUMERIC);
      model.setLength(i, 12);
      model.setDecimalCount(i, (byte) 0);
    }
    return model;
  }

  private static List<Object> record(double... values) {
    List<Object> row = new ArrayList<>();
    for (double value : values) {
      row.add(value);
    }
    return row;
  }

  private static final class ReferenceLayer extends NodusEsriLayer {
    private static final long serialVersionUID = 1L;
    private final EsriGraphicList graphics;
    private final DbfTableModel model;
    private final String variableName;

    ReferenceLayer(String variableName, DbfTableModel model, EsriGraphicList graphics) {
      this.variableName = variableName;
      this.model = model;
      this.graphics = graphics;
    }

    @Override
    public DbfTableModel getModel() {
      return model;
    }

    @Override
    public String getLayerVariableName() {
      return variableName;
    }

    @Override
    public EsriGraphicList getEsriGraphicList() {
      return graphics;
    }
  }

  /** Replaces presentation only; every computational callback continues into production code. */
  private static final class HeadlessPanel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    private final ReferenceProject project;
    private final Set<Thread> workers = ConcurrentHashMap.newKeySet();
    private CountDownLatch started;
    private int completionSound;
    private final SoundPlayer sounds =
        new SoundPlayer(false) {
          @Override
          public void play(int sound) {
            completionSound = sound;
          }
        };

    HeadlessPanel(ReferenceProject project) {
      super();
      this.project = project;
    }

    void prepareRun(int threads) {
      workers.clear();
      started = new CountDownLatch(Math.min(threads, 2));
      completionSound = 0;
    }

    @Override
    public NodusProject getNodusProject() {
      return project;
    }

    @Override
    public SoundPlayer getSoundPlayer() {
      return sounds;
    }

    @Override
    public void setBusy(boolean busy) {}

    @Override
    public void setText(String text) {}

    @Override
    public void resetText() {}

    @Override
    public void updateScenarioComboBox(boolean reset) {}

    @Override
    public void startProgress(int length) {}

    @Override
    public void stopProgress() {}

    @Override
    public boolean updateProgress(String text) {
      return updateProgress(text, 1);
    }

    @Override
    public boolean updateProgress(String text, int interval) {
      Thread worker = Thread.currentThread();
      if (worker instanceof AssignmentWorker && workers.add(worker)) {
        // Hold the first job until the second starts, guaranteeing actual concurrent workers.
        started.countDown();
        try {
          if (!started.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Assignment workers did not start both commodity jobs");
          }
        } catch (InterruptedException e) {
          worker.interrupt();
          return false;
        }
      }
      return !worker.isInterrupted();
    }
  }
}
