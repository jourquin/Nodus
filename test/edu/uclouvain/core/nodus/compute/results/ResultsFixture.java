/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.results;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.layer.LabelLayer;
import com.bbn.openmap.layer.location.NodusLocationHandler;
import com.bbn.openmap.layer.location.NodusLocationLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.NodusOMGraphic;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.coords.LatLonPoint;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.real.RealNode;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Real embedded SQL and OpenMap graphics; substitutes dialogs, painting and timer delivery. */
final class ResultsFixture implements AutoCloseable {
  final Connection raw;
  final Connection connection;
  final Properties properties = new Properties();
  final Panel panel = new Panel();
  final NodusProject project;
  final List<String> queries = new ArrayList<>();
  final List<Statement> statements = new ArrayList<>();
  final NodusEsriLayer[] links;
  final NodusEsriLayer[] nodes;
  final RecordingLabel label = new RecordingLabel();
  final NodusLocationLayer locations =
      new NodusLocationLayer() {
        private static final long serialVersionUID = 1L;

        @Override
        public void reloadData() {}

        @Override
        public void doPrepare() {}
      };
  String failQuery;
  boolean open = true;
  int closedQueries;
  int connectionRequests;
  final Path directory;

  ResultsFixture(Path directory, boolean hsql) throws Exception {
    this.directory = directory;
    raw =
        DriverManager.getConnection(
            (hsql ? "jdbc:hsqldb:mem:" : "jdbc:h2:mem:") + "results_" + UUID.randomUUID(),
            "sa",
            "");
    connection =
        (Connection)
            Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (ignored, method, args) -> {
                  try {
                    if (method.getName().equals("createStatement")) {
                      final Statement statement = raw.createStatement();
                      statements.add(statement);
                      return Proxy.newProxyInstance(
                          Statement.class.getClassLoader(),
                          new Class<?>[] {Statement.class},
                          (unused, sqlMethod, sqlArgs) -> {
                            try {
                              if (sqlMethod.getName().equals("executeQuery")) {
                                String sql = (String) sqlArgs[0];
                                queries.add(sql);
                                if (!open || raw.isClosed()) {
                                  closedQueries++;
                                  throw new SQLException("Query after project close");
                                }
                                if (failQuery != null && sql.contains(failQuery)) {
                                  throw new SQLException("Injected result query failure");
                                }
                              }
                              return sqlMethod.invoke(statement, sqlArgs);
                            } catch (InvocationTargetException failure) {
                              throw failure.getCause();
                            }
                          });
                    }
                    return method.invoke(raw, args);
                  } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                  }
                });
    assertTrue(JDBCUtils.setConnection(connection));
    properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, directory + java.io.File.separator);
    properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "results");
    properties.setProperty(NodusC.PROP_MAX_WIDTH, "10");
    properties.setProperty(NodusC.PROP_MAX_RADIUS, "20");
    project =
        new NodusProject(panel) {
          @Override
          public Connection getMainJDBCConnection() {
            connectionRequests++;
            return connection;
          }

          @Override
          public NodusEsriLayer[] getLinkLayers() {
            return links;
          }

          @Override
          public NodusEsriLayer[] getNodeLayers() {
            return nodes;
          }

          @Override
          public NodusLocationLayer getLocationLayer() {
            return locations;
          }

          @Override
          public boolean isOpen() {
            return open;
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
          public void setLocalProperty(String key, String value) {
            properties.setProperty(key, value);
          }

          @Override
          public void setLocalProperty(String key, boolean value) {
            properties.setProperty(key, Boolean.toString(value));
          }
        };
    panel.project = project;
    panel.map.setSize(800, 600);
    panel.map.setProjection(new Mercator(new LatLonPoint.Double(0, 0), 10000000, 800, 600));
    label.setLabelText("original label");
    panel.layers.setLayers(new Layer[] {label});
    links = layers(true);
    nodes = layers(false);
    execute(
        "CREATE TABLE results (id INTEGER, qty DECIMAL(12,3), veh DECIMAL(12,3), "
            + "grp INTEGER, mode INTEGER, means INTEGER, time INTEGER)");
    costs("0", "60", "30");
  }

  private NodusEsriLayer[] layers(boolean link) throws Exception {
    return new NodusEsriLayer[] {
      new TestLayer(link, "a", 30, 10),
      new TestLayer(link, "b", 20, 40),
      new TestLayer(link, "hidden", 50)
    };
  }

  void execute(String sql) throws SQLException {
    try (Statement statement = raw.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  void add(int id, double quantity, double vehicles, int group, int time) throws SQLException {
    execute(
        "INSERT INTO results VALUES ("
            + id
            + ","
            + quantity
            + ","
            + vehicles
            + ","
            + group
            + ",1,1,"
            + time
            + ")");
  }

  void costs(String start, String end, String duration) throws Exception {
    Files.writeString(
        directory.resolve("results.costs"),
        NodusC.VARNAME_STARTTIME
            + "="
            + start
            + "\n"
            + NodusC.VARNAME_ENDTIME
            + "="
            + end
            + "\n"
            + NodusC.VARNAME_TIMESLICE
            + "="
            + duration
            + "\nAVGLOAD.1,1=10\nAVGLOAD.1,1.1=10\nAVGLOAD.1,1.2=4\n");
  }

  OMGraphic graphic(boolean link, int id) {
    for (NodusEsriLayer layer : link ? links : nodes) {
      for (int i = 0; i < layer.getModel().getRowCount(); i++) {
        if (((Number) layer.getModel().getValueAt(i, 0)).intValue() == id) {
          return layer.getEsriGraphicList().getOMGraphicAt(i);
        }
      }
    }
    throw new AssertionError("Unknown fixture ID " + id);
  }

  RealNetworkObject object(boolean link, int id) {
    return (RealNetworkObject) graphic(link, id).getAttribute(0);
  }

  void key(int key) {
    final KeyEvent event =
        new KeyEvent(panel.map, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED);
    for (KeyListener listener : panel.map.getKeyListeners()) {
      listener.keyPressed(event);
    }
  }

  void assertClean() throws Exception {
    assertFalse(panel.busy);
    for (Statement statement : statements) {
      assertTrue(statement.isClosed(), "Leaked query statement");
    }
  }

  void closeProject() throws Exception {
    final CountDownLatch done = new CountDownLatch(1);
    SwingUtilities.invokeAndWait(() -> project.close(done::countDown));
    assertTrue(done.await(5, TimeUnit.SECONDS));
    open = false;
    raw.close();
  }

  @Override
  public void close() throws Exception {
    if (open) {
      closeProject();
    }
    panel.map.dispose();
    JDBCUtils.setConnection(null);
  }

  private final class TestLayer extends NodusEsriLayer {
    private static final long serialVersionUID = 1L;
    final DbfTableModel model = new DbfTableModel(1);
    final EsriGraphicList graphics;
    final NodusOMGraphic style = new NodusOMGraphic();
    final String table;

    TestLayer(boolean link, String name, int... ids) throws Exception {
      table = (link ? "links_" : "nodes_") + name;
      Field owner = NodusEsriLayer.class.getDeclaredField("nodusProject");
      owner.setAccessible(true);
      owner.set(this, project);
      Field tableField = NodusEsriLayer.class.getDeclaredField("tableName");
      tableField.setAccessible(true);
      tableField.set(this, table);
      model.setColumnName(0, "NUM");
      model.setType(0, DbfTableModel.TYPE_NUMERIC);
      model.setLength(0, 10);
      model.setDecimalCount(0, (byte) 0);
      graphics = link ? new EsriPolylineList() : new EsriPointList();
      execute("CREATE TABLE " + table + " (num INTEGER)");
      for (int id : ids) {
        model.addRecord(new ArrayList<>(List.of((double) id)));
        final double longitude = id == 40 ? 120 : id / 100.0;
        final OMGraphic graphic =
            link
                ? new EsriPolyline(
                    new double[] {0, longitude, 0.1, longitude + 0.1},
                    OMGraphic.DECIMAL_DEGREES,
                    OMGraphic.LINETYPE_STRAIGHT)
                : new EsriPoint(0, longitude);
        graphic.putAttribute(0, link ? new RealLink() : new RealNode());
        graphics.add(graphic);
        execute("INSERT INTO " + table + " VALUES (" + id + ")");
      }
      style.setStroke(new BasicStroke(2));
      style.setLinePaint(Color.BLUE);
      style.setAltLinePaint(Color.RED);
      style.setFillPaint(Color.GREEN);
      style.setAltFillPaint(Color.YELLOW);
      style.setRadius(5);
      setLocationHandler(new NodusLocationHandler(this));
      setStyleRendering(true);
      setVisible(!name.equals("hidden"));
    }

    @Override
    public EsriGraphicList getEsriGraphicList() {
      return graphics;
    }

    @Override
    public DbfTableModel getModel() {
      return model;
    }

    @Override
    public NodusOMGraphic getStyle(OMGraphic graphic, int index) {
      return style;
    }

    @Override
    public String getTableName() {
      return table;
    }

    @Override
    public void doPrepare() {}
  }

  static final class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    final MapBean map = new MapBean(false);
    final LayerHandler layers = new LayerHandler();
    NodusProject project;
    boolean busy;
    int resets;

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
    public float getRenderingScaleThreshold() {
      return -1;
    }

    @Override
    public void setBusy(boolean value) {
      busy = value;
    }

    @Override
    public void resetText() {
      resets++;
    }

    @Override
    public void enableMenus(boolean enabled) {}

    @Override
    public void setFileMenuBusy(boolean busy) {}

    @Override
    public void restoreMainFrameFocus() {}
  }

  static final class RecordingLabel extends LabelLayer {
    private static final long serialVersionUID = 1L;

    @Override
    public void doPrepare() {}
  }

  static final class Links extends LinkResults {
    Integer interval = 500;
    Integer units = 0;
    int completed;
    final List<String> errors = new ArrayList<>();
    final List<ManualTimer> timers = new ArrayList<>();

    Links(ResultsFixture fixture, boolean relative, boolean export) {
      super(fixture.panel, relative, export);
    }

    @Override
    Integer askForDisplayInterval() {
      return interval;
    }

    @Override
    Integer askForDisplayUnits() {
      return units;
    }

    @Override
    void showError(String message) {
      errors.add(message);
    }

    @Override
    void showPlaybackComplete() {
      completed++;
    }

    @Override
    Timer createPlaybackTimer(int delay, Runnable step) {
      ManualTimer timer = new ManualTimer(delay, step);
      timers.add(timer);
      return timer;
    }

    void tick() {
      timers.get(timers.size() - 1).fire();
    }
  }

  static final class Nodes extends NodeResults {
    final List<String> errors = new ArrayList<>();

    Nodes(ResultsFixture fixture, boolean relative, boolean export) {
      super(fixture.panel, relative, export);
    }

    @Override
    void showError(String message) {
      errors.add(message);
    }
  }

  static final class ManualTimer extends Timer {
    private static final long serialVersionUID = 1L;
    boolean running;

    ManualTimer(int delay, Runnable step) {
      super(delay, event -> step.run());
    }

    @Override
    public void start() {
      running = true;
    }

    @Override
    public void stop() {
      running = false;
    }

    @Override
    public boolean isRunning() {
      return running;
    }

    void fire() {
      for (java.awt.event.ActionListener listener : getActionListeners()) {
        listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "tick"));
      }
    }
  }
}
