/*
 * Copyright (c) 1991-2022 Université catholique de Louvain
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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.database.JDBCField;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.SingleInstanceMessagePane;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.util.Iterator;
import javax.swing.JOptionPane;

/**
 * Writes the result of an assignment in a database table.
 *
 * @author Bart Jourquin
 */
public class VirtualNetworkWriter {

  private static I18n i18n = Environment.getI18n();

  private NodusMapPanel nodusMapPanel;

  /**
   * Initializes the database table used to store the virtual network that contains an assignment.
   *
   * @param nodusProject The Nodus project.
   * @param scenario The ID of the scenario for which the table must be created.
   * @param groups An array that contains the numbers of the groups of commodities to assign.
   * @return Return true on success.
   */
  public static boolean initTable(NodusProject nodusProject, int scenario, byte[] groups) {

    vNetTableName = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    vNetTableName = nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, vNetTableName) + scenario;
    vNetTableName = JDBCUtils.getCompliantIdentifier(vNetTableName);

    // Virtual network table
    JDBCUtils.dropTable(vNetTableName);

    JDBCField[] field = null;

    field = new JDBCField[12 + 3 * (groups.length + 1)];

    int idx = 0;
    /*
     * With the virtual network 3, create a table with line origin and the line destination.
     * With the virtual network 2, don't make any change.
     */
    field[idx++] = new JDBCField(NodusC.DBF_NODE1, "NUMERIC(11)");
    field[idx++] = new JDBCField(NodusC.DBF_LINK1, "NUMERIC(10)");
    field[idx++] = new JDBCField(NodusC.DBF_MODE1, "NUMERIC(2)");
    field[idx++] = new JDBCField(NodusC.DBF_MEANS1, "NUMERIC(2)");
    field[idx++] = new JDBCField(NodusC.DBF_SERVICE1, "NUMERIC(4)");
    field[idx++] = new JDBCField(NodusC.DBF_NODE2, "NUMERIC(11)");
    field[idx++] = new JDBCField(NodusC.DBF_LINK2, "NUMERIC(10)");
    field[idx++] = new JDBCField(NodusC.DBF_MODE2, "NUMERIC(2)");
    field[idx++] = new JDBCField(NodusC.DBF_MEANS2, "NUMERIC(2)");
    field[idx++] = new JDBCField(NodusC.DBF_SERVICE2, "NUMERIC(4)");
    field[idx++] = new JDBCField(NodusC.DBF_TIME, "NUMERIC(5,0)");
    field[idx++] = new JDBCField(NodusC.DBF_LENGTH, "NUMERIC(8,3)");

    // Detailed data per group
    for (byte element : groups) {
      field[idx++] = new JDBCField(NodusC.DBF_UNITCOST + element, "NUMERIC(13,3)");
      field[idx++] = new JDBCField(NodusC.DBF_QUANTITY + element, "NUMERIC(13,3)");
      field[idx++] = new JDBCField(NodusC.DBF_VEHICLES + element, "NUMERIC(10)");
    }

    // Consolidated data
    field[idx++] = new JDBCField(NodusC.DBF_UNITCOST, "NUMERIC(13,3)");
    field[idx++] = new JDBCField(NodusC.DBF_QUANTITY, "NUMERIC(13,3)");
    field[idx++] = new JDBCField(NodusC.DBF_VEHICLES, "NUMERIC(10)");

    if (!JDBCUtils.createTable(vNetTableName, field)) {
      return false;
    }

    return true;
  }

  private NodusProject nodusProject;

  private int scenario;

  private boolean tablesAreReady = false;

  private VirtualNetwork virtualNet;

  private static String vNetTableName;

  /**
   * Initializes a writer for a given main frame, a set of assignment parameters and a virtual
   * network.
   *
   * @param ap AssignmentParameters
   * @param vnet VirtualNetwork
   */
  public VirtualNetworkWriter(AssignmentParameters ap, VirtualNetwork vnet) {
    virtualNet = vnet;
    nodusProject = ap.getNodusProject();
    nodusMapPanel = nodusProject.getNodusMapPanel();
    scenario = ap.getScenario();
    SingleInstanceMessagePane.reset();
  }

  /**
   * Creates a new empty table in the database.
   *
   * @return True on success.
   */
  private boolean initTable() {

    if (tablesAreReady) {
      return true;
    }

    boolean result = initTable(nodusProject, scenario, virtualNet.getGroups());

    if (result) {
      tablesAreReady = true;
    }
    return result;
  }

  /**
   * Saves the content of the assignment in a database table.
   *
   * @return True on success.
   */
  public boolean save() {

    // Create or clear table at first iteration
    if (!initTable()) {
      return false;
    }

    int nbTimeSlices = virtualNet.getNbTimeSlices();
    int timeSliceDuration = virtualNet.getTimeSliceDuration();
    int assignmentStarTime = virtualNet.getAssignmentStartTime();

    int maxBatchSize =
        nodusProject.getLocalProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, NodusC.MAXBATCHSIZE);

    // Reduce max batch size as a vnet record is much larger that a path header / detail record
    if (maxBatchSize > NodusC.MAXBATCHSIZE) {
      maxBatchSize /= 5;
    }

    boolean saveCompleteVirtualNetwork =
        Boolean.parseBoolean(nodusProject.getLocalProperty(NodusC.PROP_SAVE_ALL_VN));

    // long start = System.currentTimeMillis();

    try {
      // Fill it
      Connection jdbcConnection = nodusProject.getMainJDBCConnection();

      DatabaseMetaData dbmd = jdbcConnection.getMetaData();
      boolean hasBatchSupport = false;
      if (dbmd != null) {
        if (dbmd.supportsBatchUpdates()) {
          hasBatchSupport = true;
        }
      }

      /* Prepared statement */
      String sqlStmt = "INSERT INTO " + vNetTableName + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,";

      byte[] groups = virtualNet.getGroups();
      for (byte k = 0; k < (byte) groups.length; k++) {
        sqlStmt += "?,?,?,";
      }
      sqlStmt += "?,?,?)";
      PreparedStatement prepStmt = jdbcConnection.prepareStatement(sqlStmt);

      nodusMapPanel.startProgress(virtualNet.getNbVirtualLinks());

      int batchSize = 0;

      VirtualNodeList[] vnl = virtualNet.getVirtualNodeLists();
      for (VirtualNodeList element : vnl) {
        // Iterate through all the virtual nodes generated for this real node
        Iterator<VirtualNode> nodeLit = element.getVirtualNodeList().iterator();

        while (nodeLit.hasNext()) {
          VirtualNode vn = nodeLit.next();

          // Iterate through all the virtual links that start from
          // this virtual node
          Iterator<VirtualLink> linkLit = vn.getVirtualLinkList().iterator();

          while (linkLit.hasNext()) {
            if (!nodusMapPanel.updateProgress(
                i18n.get(
                    VirtualNetworkWriter.class,
                    "Saving_virtual_network",
                    "Saving virtual network"))) {

              prepStmt.close();

              return false;
            }

            VirtualLink vl = linkLit.next();

            // Only saves virtual links on which a volume was assigned
            for (byte timeSlice = 0; timeSlice < nbTimeSlices; timeSlice++) {
              int currentTime = assignmentStarTime + timeSlice * timeSliceDuration;

              if (vl.hasVolume(timeSlice) || saveCompleteVirtualNetwork) {

                int idx = 1;
                /*
                 * With the virtual network 3, insert in the table the line origin and the line
                 * destination. With the virtual network 2, don't make any change.
                 */
                prepStmt.setInt(idx++, vl.getBeginVirtualNode().getRealNodeId(true));
                prepStmt.setInt(idx++, vl.getBeginVirtualNode().getRealLinkId());
                prepStmt.setInt(idx++, vl.getBeginVirtualNode().getMode());
                prepStmt.setInt(idx++, vl.getBeginVirtualNode().getMeans());
                prepStmt.setInt(idx++, vl.getBeginVirtualNode().getService());
                prepStmt.setInt(idx++, vl.getEndVirtualNode().getRealNodeId(true));
                prepStmt.setInt(idx++, vl.getEndVirtualNode().getRealLinkId());
                prepStmt.setInt(idx++, vl.getEndVirtualNode().getMode());
                prepStmt.setInt(idx++, vl.getEndVirtualNode().getMeans());
                prepStmt.setInt(idx++, vl.getEndVirtualNode().getService());
                prepStmt.setInt(idx++, currentTime);
                prepStmt.setDouble(idx++, vl.getLength());

                double totalQty = 0.0;
                double averageWeight = 0.0;
                int totalVehicles = 0;

                for (byte k = 0; k < (byte) groups.length; k++) {

                  totalQty += vl.getCurrentVolume(k, timeSlice);
                  averageWeight += vl.getCurrentVolume(k, timeSlice) * vl.getCost(k);
                  totalVehicles += vl.getCurrentVehicles(k, timeSlice);

                  prepStmt.setDouble(idx++, vl.getCost(k));
                  prepStmt.setDouble(idx++, vl.getCurrentVolume(k, timeSlice));
                  prepStmt.setInt(idx++, vl.getCurrentVehicles(k, timeSlice));
                }

                if (totalQty > 0) {
                  averageWeight /= totalQty;
                }

                prepStmt.setDouble(idx++, averageWeight);
                prepStmt.setDouble(idx++, totalQty);
                prepStmt.setInt(idx++, totalVehicles);

                // Save into table according to batch policy;
                if (hasBatchSupport) {
                  batchSize++;
                  prepStmt.addBatch();
                  if (batchSize >= maxBatchSize) {
                    final boolean oldAutoCommit = jdbcConnection.getAutoCommit();
                    jdbcConnection.setAutoCommit(false);
                    prepStmt.executeBatch();
                    jdbcConnection.commit();
                    jdbcConnection.setAutoCommit(oldAutoCommit);
                    batchSize = 0;
                  }
                } else {
                  prepStmt.executeUpdate();
                }
              }
            }
          }
        }
      }

      // Flush remaining records in batch
      if (hasBatchSupport) {
        final boolean oldAutoCommit = jdbcConnection.getAutoCommit();
        jdbcConnection.setAutoCommit(false);
        try {
          prepStmt.executeBatch();
        } catch (Exception e) {
          // If not in batch mode because there is nothing to write in table
        }
        jdbcConnection.commit();
        jdbcConnection.setAutoCommit(oldAutoCommit);
      }

      prepStmt.close();
      if (!jdbcConnection.getAutoCommit()) {
        jdbcConnection.commit();
      }

    } catch (Exception e) {
      nodusMapPanel.stopProgress();
      SingleInstanceMessagePane.display(
          nodusProject.getNodusMapPanel(),
          i18n.get(
              VirtualNetworkWriter.class,
              "Invalid_value",
              "Invalid value in VNET fields. See Stack Trace."),
          JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
      return false;
    }

    // long end = System.currentTimeMillis();
    // System.out.println("Duration : " + (end - start) / 1000);

    nodusMapPanel.stopProgress();

    return true;
  }

  /**
   * Returns the name of the virtual network table.
   *
   * @return The name of the table.
   */
  public static String getTableName() {
    return vNetTableName;
  }
}
