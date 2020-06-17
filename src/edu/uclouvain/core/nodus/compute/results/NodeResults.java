/*
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.results;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.proj.coords.LatLonPoint;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.utils.FileUtils;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;
import javax.swing.JOptionPane;

/**
 * Performs the necessary queries and work to display the OD matrices.
 *
 * @author Bart Jourquin
 */
public class NodeResults implements ShapeConstants {

  /**
   * Returns the RealNode stored in one of the node layers, or null if it doesn't exists.
   *
   * @param layers NodusEsriLayer[]
   * @param realNodeId int
   * @return RealNetworkObject
   */
  private static RealNetworkObject getRealNode(NodusEsriLayer[] layers, int realNodeId) {
    for (NodusEsriLayer element : layers) {
      if (element.numExists(realNodeId)) {
        OMGraphic omg =
            element.getEsriGraphicList().getOMGraphicAt(element.getNumIndex(realNodeId));

        return (RealNetworkObject) omg.getAttribute(0);
      }
    }

    return null;
  }

  private float brLat;

  private float brLon;

  private boolean export;

  private NodusMapPanel nodusMapPanel;

  private NodusProject nodusProject;

  private boolean relativeToView;

  private float ulLat;

  private float ulLon;

  /**
   * Initializes the class.
   *
   * @param mapPanel The NodusMapPanel.
   * @param relativeToView If true, the max size of the nodes will be computed taking only into
   *     account the visible nodes.
   * @param export If true, the results are exported in a shapefile.
   */
  public NodeResults(NodusMapPanel mapPanel, boolean relativeToView, boolean export) {
    nodusMapPanel = mapPanel;
    nodusProject = nodusMapPanel.getNodusProject();
    this.relativeToView = relativeToView;
    this.export = export;

    /*
     * Get latitude/longitude of upper-left and bottom-right corners of the current view
     */
    LatLonPoint.Double llp = mapPanel.getMapBean().getProjection().inverse(0, 0);
    ulLat = llp.getLatitude();
    ulLon = llp.getLongitude();
    int width = nodusMapPanel.getMapBean().getWidth();
    int height = nodusMapPanel.getMapBean().getHeight();
    llp = nodusMapPanel.getMapBean().getProjection().inverse(width, height);
    brLat = llp.getLatitude();
    brLon = llp.getLongitude();
  }

  private boolean isNodeInView(OMPoint omPoint) {

    double lat = omPoint.getLat();
    double lon = omPoint.getLon();

    if (lat > ulLat || lat < brLat) {
      return false;
    }
    if (lon < ulLon || lon > brLon) {
      return false;
    }
    return true;
  }

  /**
   * Read the demand in the database by means of the passed SQL statement, and updates the nodes
   * attributes in order to display the demand on the map.
   *
   * @param sqlStmt The SQL query used to display this result.
   * @return boolean True on success.
   */
  public boolean readOD(String sqlStmt) {
    nodusMapPanel.setBusy(true);
    // resetResults();

    Connection jdbcConnection = nodusProject.getMainJDBCConnection();
    NodusEsriLayer[] nodeLayers = nodusProject.getNodeLayers();

    try {
      // connect to database and execute query
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery(sqlStmt);

      // Retrieve result of query
      while (rs.next()) {
        RealNetworkObject rn = getRealNode(nodeLayers, JDBCUtils.getInt(rs.getObject(1)));

        if (rn != null) {
          rn.setResult(JDBCUtils.getDouble(rs.getObject(2)));
        }
      }

      rs.close();
      stmt.close();

    } catch (Exception ex) {
      nodusMapPanel.setBusy(false);
      JOptionPane.showMessageDialog(null, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);

      return false;
    }

    /* Retain extreme values */
    double maxResult = Integer.MIN_VALUE;
    double minResult = Integer.MAX_VALUE;
    NodusEsriLayer[] layers = nodusProject.getNodeLayers();

    for (NodusEsriLayer element : layers) {
      /*
      // Delete old _results file
      String fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
              + element.getTableName()
              + NodusC.SUFFIX_RESULTS
              + NodusC.TYPE_DBF;
      File f = new File(fileName);
      if (f.exists()) {
        boolean ok = f.delete();
        if (!ok) {
          System.err.println("Unable to delete " + fileName);
        }
      }

      fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
              + element.getTableName()
              + NodusC.SUFFIX_RESULTS
              + NodusC.TYPE_SHP;
      f = new File(fileName);
      if (f.exists()) {
        boolean ok = f.delete();
        if (!ok) {
          System.err.println("Unable to delete " + fileName);
        }
      }

      fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
              + element.getTableName()
              + NodusC.SUFFIX_RESULTS
              + NodusC.TYPE_SHX;
      f = new File(fileName);
      if (f.exists()) {
        boolean ok = f.delete();
        if (!ok) {
          System.err.println("Unable to delete " + fileName);
        }
      }
      */
      // Only look into displayed layers
      if (element.isVisible()) {
        EsriGraphicList egl = element.getEsriGraphicList();
        Iterator<?> it = egl.iterator();

        while (it.hasNext()) {
          OMGraphic omg = (OMGraphic) it.next();

          if (!relativeToView || isNodeInView((OMPoint) omg)) {
            RealNetworkObject rn = (RealNetworkObject) omg.getAttribute(0);
            double result = rn.getResult();

            if (maxResult < result) {
              maxResult = result;
            }

            if (minResult > result) {
              minResult = result;
            }
          }
        }
      }
    }

    /* Set the radius of the nodes and update map */
    int maxRadius = nodusProject.getLocalProperty(NodusC.PROP_MAX_RADIUS, NodusC.MAX_RADIUS);
    DbfTableModel resultModel = null;

    for (int i = 0; i < layers.length; i++) {
      if (layers[i].isVisible()) {
        EsriGraphicList egl = layers[i].getEsriGraphicList();
        DbfTableModel tableModel = layers[i].getModel();

        // TODO Better way to export results ?
        if (export) {
          // Copy the .shp and .shx files
          String fromFile =
              nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + layers[i].getTableName()
                  + NodusC.TYPE_SHP;
          String toFile =
              nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + layers[i].getTableName()
                  + NodusC.SUFFIX_RESULTS
                  + NodusC.TYPE_SHP;
          FileUtils.copyFile(fromFile, toFile);

          fromFile =
              nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + layers[i].getTableName()
                  + NodusC.TYPE_SHX;
          toFile =
              nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + layers[i].getTableName()
                  + NodusC.SUFFIX_RESULTS
                  + NodusC.TYPE_SHX;
          FileUtils.copyFile(fromFile, toFile);

          // Clone dbfTable
          resultModel = new DbfTableModel(tableModel.getColumnCount() + 1);
          for (int j = 0; j < tableModel.getColumnCount(); j++) {
            resultModel.setColumnName(j, tableModel.getColumnName(j));
            resultModel.setType(j, tableModel.getType(j));
            resultModel.setLength(j, tableModel.getLength(j));
            resultModel.setDecimalCount(j, tableModel.getDecimalCount(j));
          }

          // Add new column
          resultModel.setColumnName(tableModel.getColumnCount(), NodusC.DBF_RESULT);
          resultModel.setType(tableModel.getColumnCount(), DBF_TYPE_NUMERIC);
          resultModel.setLength(tableModel.getColumnCount(), 12);
          resultModel.setDecimalCount(tableModel.getColumnCount(), (byte) 2);

          // Copy the values
          for (int j = 0; j < tableModel.getRowCount(); j++) {
            resultModel.addBlankRecord();
            for (int k = 0; k < tableModel.getColumnCount(); k++) {
              resultModel.setValueAt(tableModel.getValueAt(j, k), j, k);
            }
          }
        }

        Iterator<?> it = egl.iterator();
        int index = 0;
        double minRadius = maxRadius / 10;
        double ratio = (maxResult - minResult) / (maxRadius - minRadius);

        while (it.hasNext()) {
          OMGraphic omg = (OMGraphic) it.next();
          RealNetworkObject rn = (RealNetworkObject) omg.getAttribute(0);

          if (export) {
            // Set Result in last column
            resultModel.setValueAt(rn.getResult(), index, tableModel.getColumnCount());
          }

          if (omg.isVisible()) {

            if (rn.getResult() != 0) {

              double size = minRadius + (rn.getResult() - minResult) / ratio;
              rn.setSize((int) size);
            } else {
              rn.setSize(0);
            }
          }
          index++;
        }

        if (export) {
          ExportDBF.exportTable(
              nodusProject,
              layers[i].getTableName() + NodusC.SUFFIX_RESULTS + NodusC.TYPE_DBF,
              resultModel);
        }

        nodeLayers[i].setDisplayResults(true);
        nodeLayers[i].getLocationHandler().setDisplayResults(true);
        layers[i].attachStyles();
        layers[i].doPrepare();
      }
    }

    nodusProject.getLocationLayer().reloadData();
    nodusProject.getLocationLayer().doPrepare();
    nodusMapPanel.setBusy(false);

    return true;
  }
}
