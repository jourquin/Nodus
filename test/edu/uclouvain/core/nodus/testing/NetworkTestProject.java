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

package edu.uclouvain.core.nodus.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.MapBean;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.dataAccess.shape.output.ShpOutputStream;
import com.bbn.openmap.dataAccess.shape.output.ShxOutputStream;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.NodusOMGraphic;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.coords.LatLonPoint;
import edu.uclouvain.core.nodus.Nodus;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Temporary two-layer network using real shapefile loading, editing and service membership. */
public final class NetworkTestProject extends NodusProject implements AutoCloseable {
  public final TestLayer nodes;
  public final TestLayer links;
  public final Panel panel = new Panel(this);
  public boolean otherObjectsLoaded = true;
  private final java.util.logging.Logger previousLogger = Nodus.nodusLogger;
  private final Properties properties = new Properties();
  private final Connection connection;
  private final NodusOMGraphic style = new NodusOMGraphic();
  private final ServiceHandler services;

  /** Creates nodes 1 and 2 and link 11, with editable numeric and text attributes. */
  public NetworkTestProject(Path directory, double[] coordinates) throws Exception {
    super(null);
    Nodus.nodusLogger = java.util.logging.Logger.getAnonymousLogger();
    Nodus.nodusLogger.setUseParentHandlers(false);
    connection = DriverManager.getConnection("jdbc:h2:mem:network_" + UUID.randomUUID(), "sa", "");
    assertTrue(JDBCUtils.setConnection(connection));
    properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, directory + File.separator);
    properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "network");
    DbfTableModel nodeModel = model("NUM", "STYLE", "TRANSHIP");
    nodeModel.addRecord(row(1, 0, 0));
    nodeModel.addRecord(row(2, 0, 0));
    EsriPointList points = new EsriPointList();
    points.add(new EsriPoint(coordinates[0], coordinates[1]));
    points.add(
        new EsriPoint(coordinates[coordinates.length - 2], coordinates[coordinates.length - 1]));
    nodes = load(directory, "nodes", nodeModel, points);
    DbfTableModel linkModel =
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
            "LABEL");
    linkModel.setType(9, DbfTableModel.TYPE_CHARACTER);
    linkModel.setLength(9, 30);
    List<Object> record = row(11, 0, 1, 1, 2, 3, 4, 1200, 65);
    record.add("Route d'été");
    linkModel.addRecord(record);
    EsriPolylineList lines = new EsriPolylineList();
    lines.add(
        new EsriPolyline(
            coordinates.clone(), OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT));
    links = load(directory, "links", linkModel, lines);
    services = new ServiceHandler(this, false);
  }

  private TestLayer load(Path directory, String name, DbfTableModel model, EsriGraphicList graphics)
      throws Exception {
    properties.setProperty(name + NodusC.PROP_NAME, name);
    try (OutputStream shp = Files.newOutputStream(directory.resolve(name + ".shp"));
        OutputStream shx = Files.newOutputStream(directory.resolve(name + ".shx"))) {
      int[][] index = new ShpOutputStream(shp).writeGeometry(graphics);
      new ShxOutputStream(shx).writeIndex(index, graphics.getType());
    }
    assertTrue(ExportDBF.exportTable(this, name + ".dbf", model));
    TestLayer layer = new TestLayer();
    layer.setName(name);
    layer.setProject(this, name);
    layer.setProjection(panel.map.getProjection());
    layer.setVisible(true);
    return layer;
  }

  private static DbfTableModel model(String... names) {
    DbfTableModel model = new DbfTableModel(names.length);
    for (int i = 0; i < names.length; i++) {
      model.setColumnName(i, names[i]);
      model.setType(i, DbfTableModel.TYPE_NUMERIC);
      model.setLength(i, 12);
      model.setDecimalCount(i, (byte) 0);
    }
    return model;
  }

  private static List<Object> row(double... values) {
    List<Object> row = new ArrayList<>();
    for (double value : values) {
      row.add(value);
    }
    return row;
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
  public int getNewNodeId() {
    return 3;
  }

  @Override
  public int getNewLinkId() {
    return 12;
  }

  @Override
  public boolean isOtherObjectsLoaded() {
    return otherObjectsLoaded;
  }

  @Override
  public Connection getMainJDBCConnection() {
    return connection;
  }

  @Override
  public ServiceHandler getServiceHandler() {
    return services;
  }

  @Override
  public NodusMapPanel getNodusMapPanel() {
    return panel;
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

  @Override
  public int getNbStyles(OMGraphic graphic) {
    return 1;
  }

  @Override
  public NodusOMGraphic getStyle(OMGraphic graphic, int index) {
    return style;
  }

  @Override
  public void close() {
    Nodus.nodusLogger = previousLogger;
    services.dispose();
    nodes.dispose();
    links.dispose();
    panel.map.dispose();
    JDBCUtils.setConnection(null);
    try {
      connection.close();
    } catch (java.sql.SQLException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Keeps editing/storage real while replacing the attribute editor and repaint callbacks. */
  public static final class TestLayer extends NodusEsriLayer {
    private static final long serialVersionUID = 1L;
    public boolean acceptNode = true;
    public boolean ready = true;

    @Override
    public boolean addRecord(EsriPoint point, int id, boolean displayGUI) {
      return acceptNode && super.addRecord(point, id, false);
    }

    @Override
    public boolean isReady() {
      return ready;
    }

    @Override
    public void doPrepare() {}

    @Override
    public void reloadLabels() {}
  }

  /** Supplies a real projection without creating a window. */
  public static final class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    public final MapBean map = new MapBean(false);
    private final NodusProject project;

    Panel(NodusProject project) {
      super();
      this.project = project;
      map.setProjection(new Mercator(new LatLonPoint.Double(0, 0), 1000000, 800, 600));
    }

    @Override
    public MapBean getMapBean() {
      return map;
    }

    @Override
    public NodusProject getNodusProject() {
      return project;
    }

    @Override
    public float getRenderingScaleThreshold() {
      return -1;
    }

    @Override
    public void setBusy(boolean busy) {}

    @Override
    public void setText(String text) {}

    @Override
    public void resetText() {}
  }
}
