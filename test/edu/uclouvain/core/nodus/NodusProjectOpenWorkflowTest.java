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

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.gui.NodusLayersPanel;
import com.bbn.openmap.gui.ToolPanel;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.layer.shape.PoliticalBoundariesLayer;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.ProjectionStack;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.tools.drawing.NodusOMDrawingTool;
import com.bbn.openmap.tools.drawing.NodusOMDrawingToolLauncher;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.ProjectFilesTools;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.mappanel.MapMenus;
import edu.uclouvain.core.nodus.mappanel.MapPluginManager;
import edu.uclouvain.core.nodus.project.ProjectLayerIds;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.testing.PluginTestJars;
import edu.uclouvain.core.nodus.testing.ProjectFilesFixture;
import edu.uclouvain.core.nodus.utils.ProjectLocker;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Exercises persisted project opening; only window/menu presentation is substituted. */
@ResourceLock("ProjectLocker")
@ResourceLock("JDBCUtils")
@ResourceLock("ModalSplitMethodsLoader")
@Timeout(40)
public class NodusProjectOpenWorkflowTest {
  @TempDir Path directory;

  @TestFactory
  @ResourceLock(Resources.SYSTEM_ERR)
  Stream<DynamicTest> faultyPluginsDoNotPreventOpeningAndReopeningWithUpdatedResources() {
    return Stream.of(false, true)
        .map(hsql -> DynamicTest.dynamicTest("HSQLDB=" + hsql, () -> checkPluginReopen(hsql)));
  }

  private void checkPluginReopen(boolean hsql) throws Exception {
    final PluginTestJars jars = new PluginTestJars(directory.resolve("compiled-plugins-" + hsql));
    final Path pluginJar = directory.resolve("plugins.jar");
    jars.write(pluginJar, "version-one", "HealthyLazy", "PropertiesFailure");
    final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
    final PrintStream previousError = System.err;
    try (PrintStream errors = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
      System.setErr(errors);
      try (Fixture fixture = new Fixture(hsql)) {
        fixture.panel.useRealPlugins();
        fixture.open();
        final NodusPlugin first =
            (NodusPlugin) fixture.panel.retrieveObject("instance.HealthyLazy");
        assertNotNull(first);
        assertNull(fixture.panel.retrieveObject("resource"));
        SwingUtilities.invokeAndWait(() -> fixture.panel.pluginItem().doClick(0));
        assertEquals("helper:version-one", fixture.panel.retrieveObject("resource"));
        final JMenuItem oldItem = fixture.panel.pluginItem();
        fixture.closeProject();
        assertNull(first.getNodusMapPanel());
        assertEquals(1, first.getClass().getField("disposals").getInt(first));
        assertEquals(0, oldItem.getActionListeners().length);
        assertEquals(0, fixture.panel.pluginMenus.getMenuCount());
        // Closing must release the JAR before an updated project plugin can replace it.
        Files.move(pluginJar, directory.resolve("previous-plugin-" + hsql + ".bak"));
        jars.write(pluginJar, "version-two", "HealthyLazy", "PropertiesFailure");
        fixture.panel.removeStoredObject("resource");
        fixture.open();
        final NodusPlugin second =
            (NodusPlugin) fixture.panel.retrieveObject("instance.HealthyLazy");
        assertNotSame(first.getClass().getClassLoader(), second.getClass().getClassLoader());
        assertSame(fixture.panel, second.getNodusMapPanel());
        assertNull(fixture.panel.retrieveObject("resource"));
        SwingUtilities.invokeAndWait(() -> fixture.panel.pluginItem().doClick(0));
        assertEquals("helper:version-two", fixture.panel.retrieveObject("resource"));
        final NodusPlugin rejected =
            (NodusPlugin) fixture.panel.retrieveObject("instance.PropertiesFailure");
        assertNull(rejected.getNodusMapPanel());
        assertEquals(1, rejected.getClass().getField("disposals").getInt(rejected));
        fixture.closeProject();
        assertNull(second.getNodusMapPanel());
        assertEquals(1, second.getClass().getField("disposals").getInt(second));
        assertTrue(fixture.project.errors.isEmpty());
      } finally {
        System.setErr(previousError);
      }
    }
    assertTrue(diagnostics.toString(StandardCharsets.UTF_8).contains("PropertiesFailure"));
  }

  @TestFactory
  Stream<DynamicTest> realProjectOpensClosesAndReimportsOnlyModifiedDbfs() {
    return Stream.of(false, true)
        .map(hsql -> DynamicTest.dynamicTest(hsql ? "HSQLDB" : "H2", () -> checkReopen(hsql)));
  }

  private void checkReopen(boolean hsql) throws Exception {
    try (Fixture fixture = new Fixture(hsql)) {
      fixture.open();
      assertTrue(fixture.project.isOpen());
      assertFalse(fixture.panel.busy);
      assertFalse(fixture.panel.fileMenuBusy);
      assertEquals("project value", fixture.project.getLocalProperty("precedence"));
      assertEquals("local value", fixture.project.getLocalProperty("onlyLocal"));
      assertEquals(fixture.url, fixture.project.getLocalProperty(NodusC.PROP_JDBC_URL));
      assertEquals("Nodes", fixture.project.getNodeLayers()[0].getName());
      assertEquals(2, fixture.project.getNodeLayers()[0].getModel().getRowCount());
      assertEquals(1, fixture.project.getLinkLayers()[0].getModel().getRowCount());
      assertEquals(
          "Route d'été 1", fixture.project.getLinkLayers()[0].getModel().getRecord(0).get(9));
      assertTrue(fixture.project.getNodeLayers()[0].numExists(1));
      assertTrue(fixture.project.getNodeLayers()[0].numExists(2));
      assertTrue(fixture.project.getLinkLayers()[0].numExists(1));
      assertTrue(fixture.project.isOtherObjectsLoaded());
      assertEquals(5, fixture.project.getNewNodeId());
      assertEquals(3, fixture.project.getNewLinkId());
      assertEquals(2, fixture.project.getOtherNodeNumbers().size());
      assertEquals(1, fixture.project.getOtherLinkNumbers().size());
      // An unchanged DBF must retain database-only edits when the project is reopened.
      try (Statement statement = fixture.project.getMainJDBCConnection().createStatement()) {
        statement.executeUpdate("UPDATE nodes SET label='database edit' WHERE num=1");
        statement.executeUpdate("UPDATE links SET speed=12 WHERE num=1");
      }
      fixture.project.getMainJDBCConnection().commit();
      final Connection firstConnection = fixture.project.getMainJDBCConnection();
      final NodusEsriLayer firstLayer = fixture.project.getNodeLayers()[0];
      fixture.closeProject();
      assertTrue(firstConnection.isClosed());
      assertFalse(firstLayer.isReady());
      assertFalse(fixture.project.isOpen());
      assertFalse(fixture.project.isOtherObjectsLoaded());
      assertDefaultMap(fixture.panel);
      assertLockReleased();
      final Path linksDbf = directory.resolve("links.dbf");
      final long previousTimestamp = Files.getLastModifiedTime(linksDbf).toMillis();
      Object[] changed = ProjectFilesFixture.row(false, false, 1, "Modified Liège 東京");
      changed[8] = 87.5;
      ProjectFilesFixture.writeDbf(
          directory,
          "links",
          ProjectFilesFixture.fields(false, false),
          java.nio.charset.StandardCharsets.UTF_8,
          List.<Object[]>of(changed));
      Files.setLastModifiedTime(linksDbf, FileTime.fromMillis(previousTimestamp + 10000));
      fixture.open();
      assertNotSame(firstLayer, fixture.project.getNodeLayers()[0]);
      assertNotSame(firstConnection, fixture.project.getMainJDBCConnection());
      try (Statement statement = fixture.project.getMainJDBCConnection().createStatement();
          ResultSet rows = statement.executeQuery("SELECT label FROM nodes WHERE num=1")) {
        assertTrue(rows.next());
        assertEquals("database edit", rows.getString(1));
      }
      try (Statement statement = fixture.project.getMainJDBCConnection().createStatement();
          ResultSet rows = statement.executeQuery("SELECT speed,label FROM links WHERE num=1")) {
        assertTrue(rows.next());
        assertEquals(87.5, rows.getDouble(1));
        assertEquals("Modified Liège 東京", rows.getString(2));
      }
      assertEquals(
          "Modified Liège 東京", fixture.project.getLinkLayers()[0].getModel().getRecord(0).get(9));
      assertEquals(5, fixture.project.getNewNodeId());
      assertEquals(3, fixture.project.getNewLinkId());
    }
  }

  @Test
  void projectIsReadyOnlyAfterItsRealOpenHookCompletes() throws Exception {
    try (Fixture fixture = new Fixture(false)) {
      Files.writeString(
          directory.resolve("project.groovy"),
          "if (openProject) { nodusMapPanel.awaitOpenHook() }\n");
      fixture.startOpen();
      assertTrue(fixture.panel.hookEntered.await(10, TimeUnit.SECONDS));
      assertFalse(fixture.project.isOpen());
      assertTrue(fixture.panel.busy);
      assertTrue(fixture.panel.fileMenuBusy);
      assertEquals(1, fixture.project.getNodeLayers().length);
      fixture.panel.hookRelease.countDown();
      fixture.awaitOpen();
      assertTrue(fixture.project.isOpen());
      assertFalse(fixture.panel.busy);
      assertTrue(fixture.panel.completionOnEdt);
    }
  }

  @Test
  void lateOpenFailureDisposesLoadedLayersClosesJdbcReleasesLockAndAllowsRetry() throws Exception {
    try (Fixture fixture = new Fixture(false)) {
      fixture.panel.failPluginLoad = true;
      fixture.startOpen();
      assertTrue(fixture.panel.finished.await(10, TimeUnit.SECONDS));
      assertEquals(1, fixture.project.errors.size());
      assertEquals("Injected late opening failure", fixture.project.errors.get(0).getMessage());
      assertFalse(fixture.project.isOpen());
      assertFalse(fixture.panel.busy);
      assertFalse(fixture.panel.fileMenuBusy);
      assertDefaultMap(fixture.panel);
      assertTrue(fixture.panel.failedConnection.isClosed());
      for (NodusEsriLayer layer : fixture.panel.failedLayers) {
        assertFalse(layer.isReady());
        assertNull(layer.getTableName());
        assertFalse(layer.numExists(1));
      }
      assertNull(fixture.project.getNodeLayers());
      assertNull(fixture.project.getLinkLayers());
      assertFalse(fixture.project.isOtherObjectsLoaded());
      assertLockReleased();
      fixture.panel.failPluginLoad = false;
      fixture.project.errors.clear();
      fixture.open();
      assertTrue(fixture.project.isOpen());
      assertEquals(5, fixture.project.getNewNodeId());
    }
  }

  @Test
  void failedBackgroundLayerLoadAlsoClosesThePartiallyOpenedProject() throws Exception {
    try (Fixture fixture = new Fixture(false)) {
      ProjectFilesFixture.writeDbf(
          directory,
          "nodes",
          ProjectFilesFixture.fields(true, false),
          java.nio.charset.StandardCharsets.UTF_8,
          List.<Object[]>of(ProjectFilesFixture.row(true, false, 1, "only one attribute row")));
      fixture.startOpen();
      assertTrue(fixture.panel.finished.await(10, TimeUnit.SECONDS));
      assertEquals(1, fixture.project.errors.size());
      assertTrue(fixture.project.errors.get(0).getMessage().contains("Cannot read DBF attributes"));
      assertTrue(fixture.project.failedConnection.isClosed());
      assertFalse(fixture.project.isOpen());
      assertFalse(fixture.panel.busy);
      assertNull(fixture.project.getNodeLayers());
      assertDefaultMap(fixture.panel);
      assertLockReleased();
    }
  }

  @Test
  void rescanningUnloadedLayersDropsStaleIdsAndIgnoresNonNodusLayers() throws Exception {
    try (Fixture fixture = new Fixture(false)) {
      fixture.open();
      final ProjectLayerIds ids = new ProjectLayerIds(fixture.project);
      ids.loadOtherLayersObjectNumbers(
          fixture.project.getNodeLayers(), fixture.project.getLinkLayers());
      assertEquals(java.util.Set.of(3, 4), ids.getOtherNodeNumbers().keySet());
      assertEquals(java.util.Set.of(2), ids.getOtherLinkNumbers().keySet());
      ProjectFilesFixture.writeLayer(directory, "outsideNodes", true, 6);
      for (String extension : List.of(".shp", ".shx", ".dbf", ".cpg")) {
        Files.delete(directory.resolve("outsideLinks" + extension));
      }
      ProjectFilesFixture.writeLayer(directory, "malformed", true, 3);
      Files.delete(directory.resolve("malformed.shx"));
      ids.loadOtherLayersObjectNumbers(
          fixture.project.getNodeLayers(), fixture.project.getLinkLayers());
      assertEquals(java.util.Set.of(6), ids.getOtherNodeNumbers().keySet());
      assertTrue(ids.getOtherLinkNumbers().isEmpty());
      // Point/link IDs have independent namespaces; loaded-layer IDs must not be cached here.
      assertFalse(ids.getOtherNodeNumbers().containsKey(1));
      assertTrue(ids.isOtherObjectsLoaded());
      ids.clear();
      assertFalse(ids.isOtherObjectsLoaded());
      assertTrue(ids.getOtherNodeNumbers().isEmpty());
      String projectPath = fixture.project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
      try {
        fixture.project.setLocalProperty(
            NodusC.PROP_PROJECT_DOTPATH, directory.resolve("missing").toString());
        assertThrows(
            IllegalStateException.class,
            () ->
                ids.loadOtherLayersObjectNumbers(
                    fixture.project.getNodeLayers(), fixture.project.getLinkLayers()));
        assertFalse(ids.isOtherObjectsLoaded());
      } finally {
        fixture.project.setLocalProperty(NodusC.PROP_PROJECT_DOTPATH, projectPath);
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> rejectedLayerFormatsRestoreTheDefaultMapAndAllowRetry() {
    return Stream.of(false, true)
        .map(
            repairable ->
                DynamicTest.dynamicTest(
                    repairable ? "declined format repair" : "incompatible field data",
                    () -> {
                      try (Fixture fixture = new Fixture(false)) {
                        final DBFField[] fields = ProjectFilesFixture.fields(false, false);
                        fields[8] = new DBFField("speed", repairable ? 'N' : 'C', 4, 0);
                        final Object[] row = ProjectFilesFixture.row(false, false, 1, "bad format");
                        row[8] = repairable ? Integer.valueOf(65) : "fast";
                        ProjectFilesFixture.writeDbf(
                            directory,
                            "links",
                            fields,
                            java.nio.charset.StandardCharsets.UTF_8,
                            List.<Object[]>of(row));
                        assertFalse(
                            ProjectFilesTools.isValidLayer(
                                directory.toString(),
                                "links",
                                com.bbn
                                    .openmap
                                    .dataAccess
                                    .shape
                                    .ShapeConstants
                                    .SHAPE_TYPE_POLYLINE));
                        fixture.project.headlessFileValidation = true;
                        fixture.panel.scenarios.setVisible(true);
                        fixture.panel.scenarioCaption.setVisible(true);
                        final byte[] original = Files.readAllBytes(directory.resolve("links.dbf"));
                        fixture.startOpen();
                        assertTrue(fixture.panel.finished.await(10, TimeUnit.SECONDS));
                        assertFalse(fixture.project.isOpen());
                        assertNull(fixture.project.getNodeLayers());
                        assertNull(fixture.project.getLinkLayers());
                        assertFalse(fixture.panel.busy);
                        assertFalse(fixture.panel.fileMenuBusy);
                        assertDefaultMap(fixture.panel);
                        assertLockReleased();
                        org.junit.jupiter.api.Assertions.assertArrayEquals(
                            original, Files.readAllBytes(directory.resolve("links.dbf")));
                        ProjectFilesFixture.writeLayer(directory, "links", false, 1);
                        fixture.open();
                        assertTrue(fixture.project.isOpen());
                        fixture.closeProject();
                        assertDefaultMap(fixture.panel);
                      }
                    }));
  }

  private static void assertDefaultMap(Panel panel) {
    final com.bbn.openmap.Layer[] layers = panel.layers.getLayers();
    assertEquals(1, layers.length);
    assertTrue(layers[0] instanceof PoliticalBoundariesLayer);
    assertTrue(layers[0].isVisible());
    assertTrue(panel.map.getProjection() instanceof Mercator);
    assertEquals(1000000, panel.map.getScale());
    assertEquals(50, panel.map.getCenter().getY(), 1e-6);
    assertEquals(4, panel.map.getCenter().getX(), 1e-6);
    assertFalse(panel.scenarios.isVisible());
    assertFalse(panel.scenarioCaption.isVisible());
    assertTrue(panel.completionOnEdt);
  }

  private void assertLockReleased() throws Exception {
    try (FileChannel channel =
            FileChannel.open(
                directory.resolve("project" + NodusC.TYPE_LOCK), StandardOpenOption.WRITE);
        FileLock lock = channel.tryLock()) {
      assertNotNull(lock);
    }
  }

  private final class Fixture implements AutoCloseable {
    final Logger previousLogger = Nodus.nodusLogger;
    final Panel panel = new Panel();
    final OpeningProject project = new OpeningProject(panel);
    final String url;

    Fixture(boolean hsql) throws Exception {
      Nodus.nodusLogger = Logger.getAnonymousLogger();
      panel.project = project;
      panel.displayPoliticalBoundaries(true, true);
      final Path db = directory.resolve(hsql ? "hsqldb" : "h2db");
      url = hsql ? "jdbc:hsqldb:file:" + db + ";shutdown=true" : "jdbc:h2:" + db;
      ProjectFilesFixture.writeLayer(directory, "nodes", true, 1, 2);
      ProjectFilesFixture.writeLayer(directory, "links", false, 1);
      ProjectFilesFixture.writeLayer(directory, "outsideNodes", true, 3, 4);
      ProjectFilesFixture.writeLayer(directory, "outsideLinks", false, 2);
      Properties properties = new Properties();
      properties.setProperty(NodusC.PROP_NETWORK_NODES, "nodes");
      properties.setProperty(NodusC.PROP_NETWORK_LINKS, "links");
      properties.setProperty(NodusC.PROP_JDBC_URL, url);
      properties.setProperty(
          NodusC.PROP_JDBC_DRIVER, hsql ? "org.hsqldb.jdbcDriver" : "org.h2.Driver");
      properties.setProperty(NodusC.PROP_JDBC_USERNAME, "sa");
      properties.setProperty(NodusC.PROP_JDBC_PASSWORD, "");
      properties.setProperty(
          NodusC.PROP_EMBEDDED_DB, "" + (hsql ? JDBCUtils.DB_HSQLDB : JDBCUtils.DB_H2));
      properties.setProperty("nodes.prettyName", "Nodes");
      properties.setProperty("precedence", "project value");
      store(directory.resolve("project.nodus"), properties);
      Properties local = new Properties();
      local.setProperty("precedence", "old local value");
      local.setProperty("onlyLocal", "local value");
      local.setProperty(NodusC.PROP_JDBC_URL, "jdbc:invalid:must-not-be-used");
      store(directory.resolve("project.nodus.local"), local);
    }

    void startOpen() throws Exception {
      panel.finished = new CountDownLatch(1);
      SwingUtilities.invokeAndWait(
          () -> project.openProject(directory.resolve("project.nodus").toString()));
    }

    void awaitOpen() throws Exception {
      assertTrue(panel.finished.await(10, TimeUnit.SECONDS), "Opening must finish");
      if (!project.errors.isEmpty()) {
        throw new AssertionError("Project opening failed", project.errors.get(0));
      }
      assertTrue(project.isOpen());
    }

    void open() throws Exception {
      startOpen();
      awaitOpen();
    }

    void closeProject() throws Exception {
      CountDownLatch closed = new CountDownLatch(1);
      SwingUtilities.invokeAndWait(() -> project.close(closed::countDown));
      assertTrue(closed.await(10, TimeUnit.SECONDS), "Closing must finish");
    }

    @Override
    public void close() throws Exception {
      panel.hookRelease.countDown();
      try {
        if (project.isOpen()) {
          closeProject();
        } else if (panel.busy) {
          assertTrue(panel.finished.await(10, TimeUnit.SECONDS));
          if (project.isOpen()) {
            closeProject();
          }
        }
      } finally {
        ProjectLocker.releaseLock();
        JDBCUtils.setConnection(null);
        Nodus.nodusLogger = previousLogger;
        SwingUtilities.invokeAndWait(panel.map::dispose);
      }
    }
  }

  private static void store(Path file, Properties properties) throws Exception {
    try (OutputStream output = Files.newOutputStream(file)) {
      properties.store(output, null);
    }
  }

  private static final class OpeningProject extends NodusProject {
    final List<Throwable> errors = new ArrayList<>();
    Connection failedConnection;
    boolean headlessFileValidation;

    OpeningProject(Panel panel) {
      super(panel);
    }

    @Override
    boolean validateProjectFiles(Properties properties) {
      if (!headlessFileValidation) {
        return super.validateProjectFiles(properties);
      }
      // Exercise actual DBF validation while simulating rejection without a modal dialog.
      final String path = properties.getProperty(NodusC.PROP_PROJECT_DOTPATH);
      return ProjectFilesTools.isValidLayer(
              path, "nodes", com.bbn.openmap.dataAccess.shape.ShapeConstants.SHAPE_TYPE_POINT)
          && ProjectFilesTools.isValidLayer(
              path, "links", com.bbn.openmap.dataAccess.shape.ShapeConstants.SHAPE_TYPE_POLYLINE);
    }

    @Override
    ServiceHandler createServiceHandler() {
      return new ServiceHandler(this, false);
    }

    @Override
    void reportProjectOpenError(Throwable error) {
      errors.add(error);
      failedConnection = getMainJDBCConnection();
    }
  }

  /** Headless presentation with real map, layer handler, drawing tool and projection stack. */
  public static final class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    final MapBean map = new MapBean(false);
    final LayerHandler layers = new LayerHandler();
    final NodusLayersPanel controls = new NodusLayersPanel(this);
    final NodusOMDrawingToolLauncher launcher = new NodusOMDrawingToolLauncher(this);
    final NodusOMDrawingTool drawing = new NodusOMDrawingTool(this, launcher);
    final ProjectionStack stack = new ProjectionStack();
    final ToolPanel tools = new ToolPanel();
    final javax.swing.JComboBox<String> scenarios = new javax.swing.JComboBox<>();
    final javax.swing.JLabel scenarioCaption = new javax.swing.JLabel("Scenario");
    final CountDownLatch hookEntered = new CountDownLatch(1);
    final CountDownLatch hookRelease = new CountDownLatch(1);
    volatile CountDownLatch finished = new CountDownLatch(1);
    volatile boolean busy;
    volatile boolean fileMenuBusy;
    volatile boolean completionOnEdt;
    boolean failPluginLoad;
    OpeningProject project;
    Connection failedConnection;
    NodusEsriLayer[] failedLayers;
    MapPluginManager realPlugins;
    JMenuBar pluginMenus;

    Panel() throws Exception {
      map.setProjection(new Mercator(new LatLonPoint.Double(50, 4), 1000000, 800, 600));
      setPanelField("mapBean", map);
      setPanelField("layerHandler", layers);
      setPanelField("nodusLayersPanel", controls);
      setPanelField("scenarioComboBox", scenarios);
      setPanelField("scenarioLabel", scenarioCaption);
    }

    private void setPanelField(String name, Object value) throws Exception {
      Field field = NodusMapPanel.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(this, value);
    }

    void useRealPlugins() throws Exception {
      Field manager = NodusMapPanel.class.getDeclaredField("pluginManager");
      manager.setAccessible(true);
      realPlugins = (MapPluginManager) manager.get(this);
      Field menus = NodusMapPanel.class.getDeclaredField("menus");
      menus.setAccessible(true);
      pluginMenus = ((MapMenus) menus.get(this)).nodusMenuBar;
    }

    JMenuItem pluginItem() {
      assertEquals(1, pluginMenus.getMenuCount());
      assertEquals(1, pluginMenus.getMenu(0).getItemCount());
      return pluginMenus.getMenu(0).getItem(0);
    }

    /** Called from the real Groovy open hook. */
    public void awaitOpenHook() throws InterruptedException {
      hookEntered.countDown();
      if (!hookRelease.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Hook was not released");
      }
    }

    @Override
    public NodusProject getNodusProject() {
      return project;
    }

    @Override
    public MapBean getMapBean() {
      return map;
    }

    @Override
    public LayerHandler getLayerHandler() {
      return layers;
    }

    @Override
    public NodusLayersPanel getNodusLayersPanel() {
      return controls;
    }

    @Override
    public NodusOMDrawingTool getNodusDrawingTool() {
      return drawing;
    }

    @Override
    public NodusOMDrawingToolLauncher getNodusDrawingToolLauncher() {
      return launcher;
    }

    @Override
    public ProjectionStack getProjectionStack() {
      return stack;
    }

    @Override
    public ToolPanel getToolPanel() {
      return tools;
    }

    @Override
    public java.awt.Frame getMainFrame() {
      return null;
    }

    @Override
    public String getActiveMouseMode() {
      return "Gestures";
    }

    @Override
    public void displayHighlightedAreaLayer(boolean added, boolean visible) {}

    @Override
    public void resetMap() {}

    @Override
    public void resetTitle() {}

    @Override
    public void resetText() {}

    @Override
    public void setText(String text) {}

    @Override
    public void removeProjectPlugins() {
      if (realPlugins != null) {
        super.removeProjectPlugins();
      }
    }

    @Override
    public void enableMenus(boolean enabled) {
      if (realPlugins != null) {
        realPlugins.enableMenus(enabled);
      }
    }

    @Override
    public void restoreMainFrameFocus() {}

    @Override
    public void setActiveMouseMode(String mode) {}

    @Override
    public void updateScenarioComboBox(boolean reset) {}

    @Override
    public void setRenderingScaleThreshold(float scale) {}

    @Override
    public void setFileMenuBusy(boolean value) {
      fileMenuBusy = value;
    }

    @Override
    public void setBusy(boolean value) {
      busy = value;
      if (!value) {
        completionOnEdt = SwingUtilities.isEventDispatchThread();
        finished.countDown();
      }
    }

    @Override
    public void loadPlugins(String path, boolean projectScoped, Collection<File> excludedJars) {
      if (failPluginLoad) {
        failedConnection = project.getMainJDBCConnection();
        failedLayers =
            new NodusEsriLayer[] {project.getNodeLayers()[0], project.getLinkLayers()[0]};
        throw new IllegalStateException("Injected late opening failure");
      }
      if (realPlugins != null) {
        super.loadPlugins(path, projectScoped, excludedJars);
      }
    }
  }
}
