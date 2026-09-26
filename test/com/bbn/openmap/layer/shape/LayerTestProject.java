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

package com.bbn.openmap.layer.shape;

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
import com.bbn.openmap.layer.location.NodusLocationHandler;
import com.bbn.openmap.omGraphics.NodusOMGraphic;
import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Real temporary shapefiles and H2 tables; only presentation callbacks are replaced. */
final class LayerTestProject extends NodusProject implements AutoCloseable {
  final Connection connection;
  final Properties properties = new Properties();
  final Panel panel = new Panel(this);
  final TestLayer layer = new TestLayer();
  final NodusOMGraphic style = new NodusOMGraphic();
  int labelRefreshes;

  LayerTestProject(Path directory, boolean links) throws Exception {
    super(null);
    connection = DriverManager.getConnection("jdbc:h2:mem:layer_" + UUID.randomUUID(), "sa", "");
    assertTrue(JDBCUtils.setConnection(connection));
    properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, directory + File.separator);
    properties.setProperty("features" + NodusC.PROP_NAME, "features");
    style.setLinePaint(Color.BLUE);
    style.setFillPaint(Color.GREEN);
    style.setMattingPaint(Color.WHITE);
    style.setDefaultLinePaint(Color.GRAY);
    style.setAltLinePaint(Color.RED);
    style.setAltFillPaint(Color.YELLOW);
    style.setAltMattingPaint(Color.BLACK);
    style.setStroke(
        new BasicStroke(
            2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 10, new float[] {4, 2}, 1));
    style.setMatted(true);
    style.setOval(true);
    style.setRadius(7);
    DbfTableModel model = model(links);
    EsriGraphicList graphics = links ? new EsriPolylineList() : new EsriPointList();
    for (int num : new int[] {30, 10, 20}) {
      List<Object> row = new ArrayList<>();
      for (double value :
          links
              ? new double[] {num, 0, 1, num, num + 1, 1, 1, 1000, 50}
              : new double[] {num, 0, 0}) {
        row.add(value);
      }
      row.add("original " + num);
      row.add(num + 0.125);
      row.add("20200102");
      model.addRecord(row);
      graphics.add(links ? line(num) : new EsriPoint(0, num * 0.01));
    }
    try (OutputStream shp = Files.newOutputStream(directory.resolve("features.shp"));
        OutputStream shx = Files.newOutputStream(directory.resolve("features.shx"))) {
      int[][] index = new ShpOutputStream(shp).writeGeometry(graphics);
      new ShxOutputStream(shx).writeIndex(index, graphics.getType());
    }
    assertTrue(ExportDBF.exportTable(this, "features.dbf", model));
    layer.setProject(this, "features");
    layer.setProjection(panel.map.getProjection());
    layer.setLocationHandler(
        new NodusLocationHandler(layer) {
          @Override
          public synchronized void reloadData() {
            labelRefreshes++;
          }
        });
  }

  static EsriPolyline line(int num) {
    return new EsriPolyline(
        new double[] {0, num * 0.01, 0, (num + 1) * 0.01},
        OMGraphic.DECIMAL_DEGREES,
        OMGraphic.LINETYPE_STRAIGHT);
  }

  private static DbfTableModel model(boolean links) {
    String[] names =
        links
            ? new String[] {
              "NUM",
              "STYLE",
              "ENABLED",
              "NODE1",
              "NODE2",
              "MODE",
              "MEANS",
              "CAPACITY",
              "SPEED",
              "LABEL",
              "AMOUNT",
              "DAY"
            }
            : new String[] {"NUM", "STYLE", "TRANSHIP", "LABEL", "AMOUNT", "DAY"};
    DbfTableModel model = new DbfTableModel(names.length);
    for (int i = 0; i < names.length; i++) {
      model.setColumnName(i, names[i]);
      model.setType(i, DbfTableModel.TYPE_NUMERIC);
      model.setLength(i, 10);
      model.setDecimalCount(i, (byte) 0);
    }
    model.setType(names.length - 3, DbfTableModel.TYPE_CHARACTER);
    model.setLength(names.length - 3, 20);
    model.setDecimalCount(names.length - 2, (byte) 3);
    model.setType(names.length - 1, DbfTableModel.TYPE_DATE);
    model.setLength(names.length - 1, 8);
    return model;
  }

  void execute(String sql) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  List<List<String>> rows(String sql) throws Exception {
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

  List<List<Object>> records() {
    List<List<Object>> rows = new ArrayList<>();
    for (List<Object> record : layer.getModel()) {
      rows.add(new ArrayList<>(record));
    }
    return rows;
  }

  OMGraphic graphic(int num) {
    return layer.getEsriGraphicList().getOMGraphicAt(layer.getNumIndex(num));
  }

  @Override
  public Connection getMainJDBCConnection() {
    return connection;
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
    layer.dispose();
    panel.map.dispose();
    JDBCUtils.setConnection(null);
    try {
      connection.close();
    } catch (java.sql.SQLException ex) {
      throw new IllegalStateException(ex);
    }
  }

  static final class TestLayer extends NodusEsriLayer {
    private static final long serialVersionUID = 1L;
    int preparations;

    @Override
    public void doPrepare() {
      preparations++;
    }

    @Override
    public void reloadLabels() {}
  }

  static final class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    final MapBean map = new MapBean(false);
    final NodusProject project;
    float threshold = -1;

    Panel(NodusProject project) {
      super();
      this.project = project;
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
      return threshold;
    }

    @Override
    public void setBusy(boolean busy) {}

    @Override
    public void setText(String text) {}

    @Override
    public void resetText() {}
  }
}
