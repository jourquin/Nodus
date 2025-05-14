/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.od;

import com.bbn.openmap.Environment;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.Vector;
import javax.swing.JOptionPane;

/**
 * Loads an origin destination matrix.
 *
 * @author Bart Jourquin
 */
public class ODReader {

  /** I18N mechanism. */
  private static I18n i18n = Environment.getI18n();

  /**
   * Returns a list containing all the valid OD tables found in the database.
   *
   * @param nodusProject The Nodus project.
   * @return A Vector containing table names corresponding to valid OD tables.
   */
  public static Vector<String> getValidODTables(NodusProject nodusProject) {

    Connection jdbcConnection = nodusProject.getMainJDBCConnection();
    DatabaseMetaData metaData;
    Vector<String> tables = new Vector<>();

    String shema = null;
    if (JDBCUtils.getDbEngine() == JDBCUtils.DB_ORACLE) {
      shema =
          JDBCUtils.getCompliantIdentifier(
              nodusProject.getLocalProperty(NodusC.PROP_JDBC_USERNAME, "null"));
    }
    
    String catalog = null;
    if (JDBCUtils.getDbEngine() == JDBCUtils.DB_MYSQL) {
      catalog = "";
    }

    String vnetTablePrefix =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    vnetTablePrefix =
        nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, vnetTablePrefix).toLowerCase();

    String pathTablePrefix =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_PATH;
    pathTablePrefix =
        nodusProject.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, pathTablePrefix).toLowerCase();

    String excTablePrefix =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
    excTablePrefix =
        nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, excTablePrefix).toLowerCase();

    String serviceTablePrefix =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_SERVICES;
    serviceTablePrefix =
        nodusProject
            .getLocalProperty(NodusC.PROP_SERVICES_TABLE_PREFIX, serviceTablePrefix)
            .toLowerCase();

    try {
      // Get metadata about user tables by building a vector of table names
      String[] usertables = {"TABLE"};
      metaData = jdbcConnection.getMetaData();
      ResultSet result = metaData.getTables(catalog, shema, null, usertables);

      while (result.next()) {
        // if (result.getString(3).indexOf("$") == -1) {
        // Keep only valid OD tables
        String tableName = result.getString(3);
        String s = tableName.toLowerCase();
        if (s.startsWith(vnetTablePrefix)) {
          continue;
        }
        if (s.startsWith(pathTablePrefix)) {
          continue;
        }
        if (s.startsWith(excTablePrefix)) {
          continue;
        }
        if (s.startsWith(serviceTablePrefix)) {
          continue;
        }
        if (nodusProject.getLayer(tableName) != null) {
          continue;
        }
        if (!ODReader.isValidBasicODTable(jdbcConnection, tableName)) {
          continue;
        }
        tables.addElement(tableName);
        // }
      }

      result.close();
    } catch (SQLException e) {
      e.printStackTrace();
      return tables;
    }

    return tables;
  }

  /**
   * Tests if a given table is a valid basic OD table.
   *
   * @param jdbcConnection A JDBC connection.
   * @param odTableName A Table name.
   * @return True if the table is a valid OD table.
   */
  private static boolean isValidBasicODTable(Connection jdbcConnection, String odTableName) {

    // Test the structure of the table
    try {
      // Create a result set
      Statement stmt = jdbcConnection.createStatement();

      // Select * but returns no records
      ResultSet rs =
          stmt.executeQuery(
              "SELECT * FROM "
                  + JDBCUtils.getQuotedCompliantIdentifier(odTableName)
                  + " WHERE 1 = 0");

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
        if (columnName.equalsIgnoreCase(NodusC.DBF_GROUP)) {
          grpField = true;
        }
        if (columnName.equalsIgnoreCase(NodusC.DBF_ORIGIN)) {
          orgField = true;
        }
        if (columnName.equalsIgnoreCase(NodusC.DBF_DESTINATION)) {
          dstField = true;
        }
        if (columnName.equalsIgnoreCase(NodusC.DBF_QUANTITY)) {
          qtyField = true;
        }
      }
      stmt.close();
      rs.close();

      if (!grpField || !orgField || !dstField || !qtyField) {
        return false;
      }

    } catch (SQLException e) {

      return false;
    }
    return true;
  }

  /* Will contain true in the cells for which there is a demand. */
  private boolean[] demandForGroup = new boolean[NodusC.MAXMM];

  private boolean hasClasses = false;

  private boolean isOk = true;

  private boolean isTimeDependent;

  private Connection jdbcConnection;

  private boolean limitedToHighligthedArea;

  private byte maxClass = 0;

  private int nbRecords;

  private NodusMapPanel nodusMapPanel;

  private String odTableName;

  private double totalQuantity = 0;

  private String whereStmt;

  /**
   * Initializes the OD reader.
   *
   * @param ap AssignmentParameters
   */
  public ODReader(AssignmentParameters ap) {
    nodusMapPanel = ap.getNodusProject().getNodusMapPanel();

    jdbcConnection = nodusMapPanel.getNodusProject().getMainJDBCConnection();

    odTableName = ap.getODMatrix();

    whereStmt = ap.getWhereStmt().trim();

    limitedToHighligthedArea = ap.isLimitedToHighlightedArea();

    isTimeDependent = ap.isTimeDependent();

    /* Does table exists? */
    if (!JDBCUtils.tableExists(odTableName)) {
      JOptionPane.showMessageDialog(
          null,
          MessageFormat.format(
              i18n.get(ODReader.class, "TableNotFound", "Table {0} not found"), odTableName),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
      isOk = false;

      return;
    }

    nodusMapPanel.setBusy(true);

    // Test the structure of the table
    try {
      // Create a result set
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs =
          stmt.executeQuery(
              "SELECT * FROM "
                  + JDBCUtils.getQuotedCompliantIdentifier(odTableName)
                  + " WHERE 1 = 0");

      // Get result set meta data
      ResultSetMetaData rsmd = rs.getMetaData();
      int numColumns = rsmd.getColumnCount();

      // Get the column names; column indices start from 1
      boolean grpField = false;
      boolean orgField = false;
      boolean dstField = false;
      boolean qtyField = false;
      boolean timeField = false;
      for (int i = 1; i < numColumns + 1; i++) {
        String columnName = rsmd.getColumnName(i);
        if (columnName.equalsIgnoreCase(NodusC.DBF_GROUP)) {
          grpField = true;
        }
        if (columnName.equalsIgnoreCase(NodusC.DBF_ORIGIN)) {
          orgField = true;
        }
        if (columnName.equalsIgnoreCase(NodusC.DBF_DESTINATION)) {
          dstField = true;
        }
        if (columnName.equalsIgnoreCase(NodusC.DBF_QUANTITY)) {
          qtyField = true;
        }
        if (columnName.equalsIgnoreCase(NodusC.DBF_CLASS)) {
          hasClasses = true;
        }
        if (columnName.equalsIgnoreCase(NodusC.DBF_TIME)) {
          timeField = true;
        }
      }
      stmt.close();
      rs.close();

      if (!grpField || !orgField || !dstField || !qtyField) {
        nodusMapPanel.setBusy(false);
        isOk = false;
        String msg = i18n.get(ODReader.class, "InvalidODStructure", "Invalid O-D table structure");
        JOptionPane.showMessageDialog(null, msg, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
        return;
      }

      /* Time dependent assignments needs temporal classes in OD matrix */
      if (isTimeDependent && !timeField) {
        nodusMapPanel.setBusy(false);
        isOk = false;
        String msg =
            i18n.get(
                ODReader.class,
                "Invalid Table",
                "Time dependent O-D table structure must contain times");
        JOptionPane.showMessageDialog(null, msg, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
        return;
      }
    } catch (SQLException e) {
      nodusMapPanel.setBusy(false);
      isOk = false;
      return;
    }

    // Fetch number of records to read
    nodusMapPanel.setText(
        i18n.get(ODReader.class, "GettingRows", "Getting number of rows. Please wait..."));

    String sql = "SELECT COUNT(*) FROM " + odTableName;

    if (whereStmt.length() > 0) {
      sql += " WHERE " + whereStmt;
    }

    try {
      // Open connection
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery(sql);

      rs.next();
      nbRecords = rs.getInt(1);
      rs.close();
      stmt.close();

    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          null, e.getMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      isOk = false;
    }

    nodusMapPanel.setBusy(false);
  }

  /**
   * Returns the total quantity contained in the OD matrix.
   *
   * @return The sum of the quantity (tons) of all OD cells.
   */
  public double getTotalQuantity() {
    return totalQuantity;
  }

  /**
   * Loads the demand into the virtual network.
   *
   * @param vnet The virtual network
   * @return boolean True on success.
   */
  public boolean loadDemand(VirtualNetwork vnet) {
    if (!isOk) {
      return false;
    }

    totalQuantity = 0;

    nodusMapPanel.setText(
        i18n.get(ODReader.class, "Querying", "Querying OD table. Please wait..."));

    // Sample: select grp, org, dst, qty from od
    String sqlStmt =
        "SELECT "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ORIGIN)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_DESTINATION)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY);

    // Time field
    if (isTimeDependent) {
      sqlStmt += ", " + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_TIME);
    }

    if (hasClasses) {
      sqlStmt += ", " + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_CLASS);
    }

    sqlStmt += " FROM " + JDBCUtils.getQuotedCompliantIdentifier(odTableName);

    // Add where statement if relevant
    if (whereStmt.length() > 0) {
      sqlStmt += " WHERE " + whereStmt;
    }

    // connect to database and execute query
    try {
      Statement stmt = jdbcConnection.createStatement();
      nodusMapPanel.setBusy(true);

      ResultSet rs = stmt.executeQuery(sqlStmt);
      nodusMapPanel.setBusy(false);

      nodusMapPanel.startProgress(nbRecords);

      VirtualNodeList[] vnl = vnet.getVirtualNodeLists();

      // Get the records
      while (rs.next()) {
        if (!nodusMapPanel.updateProgress(
            i18n.get(ODReader.class, "LoadingMatrix", "Loading O-D matrix"))) {

          return false;
        }

        int group = 0;
        int origin = 0;
        int destination = 0;
        double quantity = 0;
        byte odClass = 0;
        int time = 0;
        int nbFields = 4;

        if (isTimeDependent) {
          nbFields++;
        }

        if (hasClasses) {
          nbFields++;
        }

        for (int i = 1; i <= nbFields; i++) {
          Object obj = rs.getObject(i);

          switch (i) {
            case 1: // Group
              int value1 = JDBCUtils.getInt(obj);
              if (value1 == Integer.MIN_VALUE) {
                return false;
              }
              group = value1;
              demandForGroup[value1] = true;

              break;

            case 2: // org
              int value2 = JDBCUtils.getInt(obj);
              if (value2 == Integer.MIN_VALUE) {
                return false;
              }
              origin = value2;

              break;

            case 3: // dst
              int value3 = JDBCUtils.getInt(obj);
              if (value3 == Integer.MIN_VALUE) {
                return false;
              }
              destination = value3;

              break;

            case 4: // qty
              double value4 = JDBCUtils.getDouble(obj);
              if (value4 == Double.MIN_VALUE) {
                return false;
              }
              quantity = value4;
              break;

            case 5:
              if (isTimeDependent) { // time
                int value5 = JDBCUtils.getInt(obj);
                if (value5 == Integer.MIN_VALUE) {
                  return false;
                }
                time = value5;
              } else { // class
                byte value5 = JDBCUtils.getByte(obj);
                if (value5 == Byte.MIN_VALUE) {
                  return false;
                }
                odClass = value5;
                if (odClass > maxClass) {
                  maxClass = odClass;
                }
              }
              break;
            case 6: // class in Time dependent case
              byte value6 = JDBCUtils.getByte(obj);
              if (value6 == Byte.MIN_VALUE) {
                return false;
              }
              odClass = value6;
              if (odClass > maxClass) {
                maxClass = odClass;
              }
              break;
            default:
              break;
          }
        }

        // Do not consider OD cells from an origin to itself
        if (origin == destination) {
          continue;
        }

        // add the demand to the relevant list
        if (group >= 0 && group < NodusC.MAXMM && quantity > 0) {
          int orgIndex = vnet.getNodeIndexInVirtualNodeList(origin, true);
          int dstIndex = vnet.getNodeIndexInVirtualNodeList(destination, true);

          // Both nodes must exist
          if (orgIndex == -1 || dstIndex == -1) {
            continue;
          }

          // Is in highlighted area?
          if (limitedToHighligthedArea) {
            OMPoint graphic = vnl[orgIndex].getGraphic();
            RealNetworkObject rl = (RealNetworkObject) graphic.getAttribute(0);
            if (!rl.isInHighlightedArea()) {
              continue;
            }

            // Also test destination
            graphic = vnl[dstIndex].getGraphic();
            rl = (RealNetworkObject) graphic.getAttribute(0);
            if (!rl.isInHighlightedArea()) {
              continue;
            }
          }
          // Add the demand to the list

          ODCell odCell = null;
          if (!hasClasses) {
            if (isTimeDependent) {
              odCell = new ODCell(group, origin, destination, quantity, time);
            } else {
              odCell = new ODCell(group, origin, destination, quantity);
            }
          } else {
            if (isTimeDependent) {
              odCell = new ODCell(group, origin, destination, quantity, time, odClass);
            } else {
              odCell = new ODCell(group, origin, destination, quantity, odClass);
            }
          }
          vnl[orgIndex].addDemand(odCell);
          totalQuantity += quantity;
        }
      }
      rs.close();
      stmt.close();

    } catch (Exception e) {
      String msg = e.toString();

      if (e instanceof ArrayIndexOutOfBoundsException) {
        msg = i18n.get(ODReader.class, "InvalidRecordFound", "Invalid record found in O-D table");
      }

      nodusMapPanel.stopProgress();
      JOptionPane.showMessageDialog(null, msg, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);

      return false;
    }

    // Get an array of group numbers for which there is a demand
    int nbGroups = 0;

    for (byte i = 0; i < NodusC.MAXMM; i++) {
      if (demandForGroup[i]) {
        nbGroups++;
      }
    }

    byte[] groupsToAssign = new byte[nbGroups];
    int j = 0;

    for (byte i = 0; i < NodusC.MAXMM; i++) {
      if (demandForGroup[i]) {
        groupsToAssign[j++] = i;
      }
    }

    vnet.setGroups(groupsToAssign);

    // Set the classes
    vnet.setMaxClass(maxClass);

    nodusMapPanel.stopProgress();

    return true;
  }
}
