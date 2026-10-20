/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.results;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.proj.coords.LatLonPoint;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shared SQL value validation and signed, bounded map sizing. */
final class ResultDisplay {
  private final NodusProject project;
  private final boolean relativeToView;
  private final double north;
  private final double south;
  private final double west;
  private final double east;

  ResultDisplay(NodusMapPanel panel, boolean relativeToView) {
    project = panel.getNodusProject();
    this.relativeToView = relativeToView;
    LatLonPoint upper = panel.getMapBean().getProjection().inverse(0, 0);
    LatLonPoint lower =
        panel
            .getMapBean()
            .getProjection()
            .inverse(panel.getMapBean().getWidth(), panel.getMapBean().getHeight());
    north = upper.getLatitude();
    west = upper.getLongitude();
    south = lower.getLatitude();
    east = lower.getLongitude();
  }

  static Map<Integer, RealNetworkObject> objects(NodusEsriLayer[] layers) {
    Map<Integer, RealNetworkObject> result = new LinkedHashMap<>();
    for (NodusEsriLayer layer : layers) {
      int index = 0;
      for (OMGraphic graphic : layer.getEsriGraphicList()) {
        Object object = graphic.getAttribute(0);
        if (object instanceof RealNetworkObject) {
          result.putIfAbsent(
              JDBCUtils.getInt(layer.getModel().getValueAt(index, 0)), (RealNetworkObject) object);
        }
        index++;
      }
    }
    return result;
  }

  Set<RealNetworkObject> scalingObjects(NodusEsriLayer[] layers) {
    Set<RealNetworkObject> objects = new HashSet<>();
    for (NodusEsriLayer layer : layers) {
      if (!layer.isVisible()) {
        continue;
      }
      boolean previous = layer.isDisplayResults();
      try {
        layer.setDisplayResults(false);
        layer.attachStyles();
        for (OMGraphic graphic : layer.getEsriGraphicList()) {
          if (graphic.isVisible() && (!relativeToView || inView(graphic))) {
            objects.add((RealNetworkObject) graphic.getAttribute(0));
          }
        }
      } finally {
        layer.setDisplayResults(previous);
        layer.attachStyles();
      }
    }
    return objects;
  }

  static double value(ResultSet rows, int column) throws SQLException {
    double value = rows.getDouble(column);
    if (rows.wasNull() || !Double.isFinite(value)) {
      throw new SQLException("Result values must be finite and non-null");
    }
    return value;
  }

  static void add(Map<RealNetworkObject, Double> values, RealNetworkObject object, double value)
      throws SQLException {
    double total = values.getOrDefault(object, 0.0) + value;
    if (!Double.isFinite(total)) {
      throw new SQLException("Result total is too large");
    }
    values.put(object, total);
  }

  boolean apply(
      NodusEsriLayer[] layers,
      Map<RealNetworkObject, Double> values,
      boolean links,
      boolean export,
      double fixedMagnitude) {
    for (NodusEsriLayer layer : layers) {
      for (OMGraphic graphic : layer.getEsriGraphicList()) {
        RealNetworkObject object = (RealNetworkObject) graphic.getAttribute(0);
        if (object != null) {
          object.setResult(values.getOrDefault(object, 0.0));
          object.setSize((float) Math.signum(object.getResult()));
        }
      }
      if (layer.isVisible()) {
        // Resolve SQL visibility and recover graphics hidden by a previous zero result.
        layer.setDisplayResults(true);
        layer.attachStyles();
      }
    }
    double magnitude = fixedMagnitude;
    if (magnitude < 0) {
      magnitude = 0;
      for (NodusEsriLayer layer : layers) {
        if (layer.isVisible()) {
          for (OMGraphic graphic : layer.getEsriGraphicList()) {
            if (graphic.isVisible() && (!relativeToView || inView(graphic))) {
              RealNetworkObject object = (RealNetworkObject) graphic.getAttribute(0);
              magnitude = Math.max(magnitude, Math.abs(object.getResult()));
            }
          }
        }
      }
    }
    boolean saved = true;
    for (NodusEsriLayer layer : layers) {
      if (!layer.isVisible()) {
        continue;
      }
      final int maximum =
          Math.max(
              1,
              links
                  ? project.getLocalProperty(NodusC.PROP_MAX_WIDTH, NodusC.MAX_WIDTH)
                  : project.getLocalProperty(NodusC.PROP_MAX_RADIUS, NodusC.MAX_RADIUS));
      DbfTableModel output = export ? exportModel(layer) : null;
      int index = 0;
      for (OMGraphic graphic : layer.getEsriGraphicList()) {
        RealNetworkObject object = (RealNetworkObject) graphic.getAttribute(0);
        if (object != null) {
          double value = object.getResult();
          double ratio = magnitude > 0 ? Math.min(1, Math.abs(value) / magnitude) : 1;
          double size =
              links
                  ? 1 + Math.round(maximum * ratio)
                  : Math.max(Math.max(1, maximum / 10.0), Math.round(maximum * ratio));
          object.setSize(value == 0 ? 0 : (float) Math.copySign(size, value));
          if (export) {
            output.setValueAt(value, index, 1);
          }
        }
        index++;
      }
      if (export) {
        saved &=
            ExportDBF.exportTable(
                project, layer.getTableName() + NodusC.SUFFIX_RESULTS + NodusC.TYPE_DBF, output);
      }
      layer.getLocationHandler().setDisplayResults(true);
      layer.attachStyles();
      layer.doPrepare();
    }
    project.getLocationLayer().reloadData();
    project.getLocationLayer().doPrepare();
    return saved;
  }

  private static DbfTableModel exportModel(NodusEsriLayer layer) {
    DbfTableModel model = new DbfTableModel(2);
    model.setColumnName(0, NodusC.DBF_NUM);
    model.setColumnName(1, NodusC.DBF_RESULT);
    for (int column = 0; column < 2; column++) {
      model.setType(column, DbfTableModel.TYPE_NUMERIC);
      model.setLength(column, column == 0 ? 10 : 12);
      model.setDecimalCount(column, (byte) (column == 0 ? 0 : 2));
    }
    for (int row = 0; row < layer.getModel().getRowCount(); row++) {
      model.addBlankRecord();
      model.setValueAt(layer.getModel().getValueAt(row, 0), row, 0);
      model.setValueAt(0.0, row, 1);
    }
    return model;
  }

  private boolean inView(OMGraphic graphic) {
    if (graphic instanceof OMPoint) {
      OMPoint point = (OMPoint) graphic;
      return inView(point.getLat(), point.getLon());
    }
    double[] points = ((OMPoly) graphic).getLatLonArray();
    for (int i = 0; i < points.length; i += 2) {
      if (inView(ProjMath.radToDeg(points[i]), ProjMath.radToDeg(points[i + 1]))) {
        return true;
      }
    }
    return false;
  }

  private boolean inView(double latitude, double longitude) {
    return latitude <= north
        && latitude >= south
        && (west <= east
            ? longitude >= west && longitude <= east
            : longitude >= west || longitude <= east);
  }
}
