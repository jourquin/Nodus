/*******************************************************************************
 * Copyright (c) 1991-2020 Université catholique de Louvain, 
 * Center for Operations Research and Econometrics (CORE)
 * http://www.uclouvain.be
 * 
 * This file is part of Nodus.
 * 
 * Nodus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.dataAccess.shape.EsriShapeExport;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.proj.coords.LatLonPoint;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.database.JDBCField;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

public class ExtractView_ {

  float ulLat, ulLon, brLat, brLon;

  boolean isInScreen(double lat, double lon) {
    if (lat > ulLat || lat < brLat) {
      return false;
    }
    if (lon < ulLon || lon > brLon) {
      return false;
    }
    return true;
  }

  public ExtractView_(NodusMapPanel nodusMapPanel) {

    NodusProject nodusProject = nodusMapPanel.getNodusProject();

    if (nodusProject.isOpen()) {

      new NodusConsole();

      /*
       * Get latitude/longitude of upper-left and bottom-right corners of the current view
       */

      LatLonPoint.Double llp = nodusMapPanel.getMapBean().getProjection().inverse(0, 0);
      ulLat = llp.getLatitude();
      ulLon = llp.getLongitude();
      int width = nodusMapPanel.getMapBean().getWidth();
      int height = nodusMapPanel.getMapBean().getHeight();
      llp = nodusMapPanel.getMapBean().getProjection().inverse(width, height);
      brLat = llp.getLatitude();
      brLon = llp.getLongitude();

      /*
       * Scan all the link layers and keep all the links that have at their end points in the current display
       */
      NodusEsriLayer[] layer = nodusProject.getLinkLayers();
      for (int i = 0; i < layer.length; i++) {
        System.out.println("Processing layer : " + layer[i].getName());

        // Create an EsriGraphic list that will hold the shapes to save
        EsriGraphicList egl = new EsriPolylineList();
        // Create a dbfTableModel for the shapes to save
        DbfTableModel tableModel = layer[i].getModel().headerClone();

        // Go through the list of graphics of the current layer
        EsriGraphicList list = layer[i].getEsriGraphicList();
        Iterator it = list.iterator();
        boolean extractedLayersIsEmpty = true;
        int index = 0;
        while (it.hasNext()) {
          OMPoly omPoly = (OMPoly) it.next();

          // Get the coordinates of the end points of the current link
          double[] pts = omPoly.getLatLonArray();

          // Transform from radians to degrees!
          double orgLat = ProjMath.radToDeg(pts[0]);
          double orgLon = ProjMath.radToDeg(pts[1]);
          double dstLat = ProjMath.radToDeg(pts[pts.length - 2]);
          double dstLon = ProjMath.radToDeg(pts[pts.length - 1]);

          // Keep this link if its end-nodes are in screen
          if (isInScreen(orgLat, orgLon) && isInScreen(dstLat, dstLon)) {
            extractedLayersIsEmpty = false;
            egl.add(omPoly);
            tableModel.addRecord((List<Object>) layer[i].getModel().getRecord(index));
          }

          index++;
        }

        // Save the extracted layer
        if (!extractedLayersIsEmpty) {

          // Save the extracted shapes, but save dbf file separately
          // as openMap routine is broken...
          String layerName = layer[i].getTableName() + "_extract";
          String tablePath = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
          EsriShapeExport exporter =
              new EsriShapeExport(egl, (DbfTableModel) null, tablePath + layerName);
          exporter.setWriteDBF(false);
          exporter.export();
          egl.clear();

          // Save the dbf file
          ExportDBF.exportTable(nodusProject, layerName + NodusC.TYPE_DBF, tableModel);
        }
      }

      // Now process the node layers
      HashMap<Integer, Integer> centroidsHashMap = new HashMap<Integer, Integer>();

      layer = nodusProject.getNodeLayers();
      for (int i = 0; i < layer.length; i++) {
        System.out.println("Processing layer : " + layer[i].getName());

        // Create an EsriGraphic list that will hold the shapes to save
        EsriGraphicList egl = new EsriPointList();
        // Create a dbfTableModel for the shapes to save
        DbfTableModel tableModel = layer[i].getModel().headerClone();

        // Go through the list of graphics of the current layer
        EsriGraphicList list = layer[i].getEsriGraphicList();
        Iterator it = list.iterator();
        boolean extractedLayersIsEmpty = true;
        int index = 0;
        while (it.hasNext()) {
          OMPoint omPoint = (OMPoint) it.next();

          double lat = omPoint.getLat();
          double lon = omPoint.getLon();

          if (isInScreen(lat, lon)) {
            extractedLayersIsEmpty = false;
            egl.add(omPoint);
            tableModel.addRecord((List<Object>) layer[i].getModel().getRecord(index));

            // Also keep the potential centroids for later use
            if (JDBCUtils.getInt(layer[i].getModel().getValueAt(index, NodusC.DBF_IDX_TRANSHIP))
                > 0) {
              Integer num =
                  new Integer(
                      JDBCUtils.getInt(layer[i].getModel().getValueAt(index, NodusC.DBF_IDX_NUM)));
              centroidsHashMap.put(num, num);
            }
          }
          index++;
        }

        // Save the extracted layer
        if (!extractedLayersIsEmpty) {

          // Save the extracted shapes, but save dbf file separately
          // as openMap routine is broken...
          String layerName = layer[i].getTableName() + "_extract";
          String tablePath = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
          EsriShapeExport exporter =
              new EsriShapeExport(egl, (DbfTableModel) null, tablePath + layerName);
          exporter.setWriteDBF(false);
          exporter.export();
          egl.clear();

          // Save the dbf file
          ExportDBF.exportTable(nodusProject, layerName + NodusC.TYPE_DBF, tableModel);
        }
      }

      // Get all the od tables of the project
      Connection jdbcConnection = nodusProject.getMainJDBCConnection();
      JDBCUtils jdbcUtils = new JDBCUtils(jdbcConnection);
      Vector odTables = ODReader.getValidODTables(nodusMapPanel.getNodusProject());

      boolean hasClasses = false;
      try {
        Statement stmt = jdbcConnection.createStatement();
        Statement stmt2 = jdbcConnection.createStatement();

        Iterator<String> it = odTables.iterator();
        while (it.hasNext()) {
          String odTableName = it.next();
          System.out.println("Processing OD table : " + odTableName);

          odTableName = jdbcUtils.getCompliantIdentifier(odTableName);
          ResultSet rs = stmt.executeQuery("SELECT * FROM " + odTableName);

          // Get result set meta data
          ResultSetMetaData rsmd = rs.getMetaData();
          int numColumns = rsmd.getColumnCount();

          // Get the column names; column indices start from 1
          boolean grpField = false;
          boolean orgField = false;
          boolean dstField = false;
          boolean qtyField = false;
          for (int i = 1; i < numColumns + 1; i++) {
            String columnName = rsmd.getColumnName(i);
            if (columnName.equalsIgnoreCase(NodusC.DBF_GROUP)) grpField = true;
            if (columnName.equalsIgnoreCase(NodusC.DBF_ORIGIN)) orgField = true;
            if (columnName.equalsIgnoreCase(NodusC.DBF_DESTINATION)) dstField = true;
            if (columnName.equalsIgnoreCase(NodusC.DBF_QUANTITY)) qtyField = true;
            if (columnName.equalsIgnoreCase(NodusC.DBF_CLASS)) hasClasses = true;
          }

          if (!grpField || !orgField || !dstField || !qtyField) {
            System.err.println("Invalid O-D table structure");
            return;
          }

          // Create table for extracted data
          String extractedOD = jdbcUtils.getCompliantIdentifier(odTableName + "_extract");
          JDBCField[] field;
          if (hasClasses) {
            field = new JDBCField[5];
          } else {
            field = new JDBCField[4];
          }
          field[0] = new JDBCField(NodusC.DBF_GROUP, "NUMERIC(2,0)");
          field[1] = new JDBCField(NodusC.DBF_ORIGIN, "NUMERIC(10,0)");
          field[2] = new JDBCField(NodusC.DBF_DESTINATION, "NUMERIC(10,0)");
          field[3] = new JDBCField(NodusC.DBF_QUANTITY, "NUMERIC(10,0)");
          if (hasClasses) {
            field[4] = new JDBCField(NodusC.DBF_CLASS, "NUMERIC(8,3)");
          }
          jdbcUtils.createTable(extractedOD, field);

          // Fill the table
          String sqlStmt =
              "SELECT " + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP) + ", ";
          sqlStmt += jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN) + ", ";
          sqlStmt += jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_DESTINATION) + ", ";
          sqlStmt += jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY);

          if (hasClasses) {
            sqlStmt += ", " + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_CLASS);
          }

          sqlStmt += " FROM " + jdbcUtils.getQuotedCompliantIdentifier(odTableName);

          rs = stmt.executeQuery(sqlStmt);

          while (rs.next()) {

            int group = 0;
            int origin = 0;
            int destination = 0;
            double quantity = 0;
            float distance = 0;
            int nbFields = 4;
            if (hasClasses) nbFields++;

            for (int i = 1; i <= nbFields; i++) {
              Object obj = rs.getObject(i);

              switch (i) {
                case 1:
                  int value1 = JDBCUtils.getInt(obj);
                  if (value1 == Integer.MIN_VALUE) {
                    System.err.println("Invalid field in OD table");
                    return;
                  }
                  group = value1;

                  break;

                case 2:
                  int value2 = JDBCUtils.getInt(obj);
                  if (value2 == Integer.MIN_VALUE) {
                    System.err.println("Invalid field in OD table");
                    return;
                  }
                  origin = value2;

                  break;

                case 3:
                  int value3 = JDBCUtils.getInt(obj);
                  if (value3 == Integer.MIN_VALUE) {
                    System.err.println("Invalid field in OD table");
                    return;
                  }
                  destination = value3;

                  break;

                case 4:
                  double value4 = JDBCUtils.getDouble(obj);
                  if (value4 == Double.MIN_VALUE) {
                    System.err.println("Invalid field in OD table");
                    return;
                  }
                  quantity = value4;
                  break;

                case 5:
                  float value5 = JDBCUtils.getFloat(obj);
                  if (value5 == Float.MIN_VALUE) {
                    System.err.println("Invalid field in OD table");
                    return;
                  }
                  distance = value5;
              }
            }

            // Save if in view if origin and destination are in view
            if (centroidsHashMap.get(new Integer(origin)) != null
                && centroidsHashMap.get(new Integer(destination)) != null) {
              sqlStmt = "INSERT INTO " + extractedOD + " VALUES (" + group + ", " + origin + ", ";
              sqlStmt += destination + ", " + quantity;

              if (hasClasses) {
                sqlStmt += ", " + distance;
              }
              sqlStmt += ")";

              stmt2.executeUpdate(sqlStmt);
            }
          }

          rs.close();
        }
        stmt.close();
        stmt2.close();
        if (!jdbcConnection.getAutoCommit()) {
          jdbcConnection.commit();
        }
      } catch (SQLException e) {
        e.printStackTrace();
        return;
      }

      System.out.println("Done.");
    }
  }
}

new ExtractView_(nodusMapPanel);
