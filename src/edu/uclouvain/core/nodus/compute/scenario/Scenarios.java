/**
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

package edu.uclouvain.core.nodus.compute.scenario;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetworkWriter;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Performs various operations on scenarios.
 *
 * @author Bart Jourquin
 */
public class Scenarios {

  /** Placeholder for virtual link records that must be compared between two scenarios. */
  private class VnetRecord {

    public double[] cost;

    public double length;

    public int line1;

    public int line2;

    public int link1;

    public int link2;

    public int means1;

    public int means2;

    public int mode1;

    public int mode2;

    public int node1;

    public int node2;

    public double[] qty;

    public int time;

    public int[] vehicles;

    public VnetRecord(int nbGroups) {
      qty = new double[nbGroups];
      cost = new double[nbGroups];
      vehicles = new int[nbGroups];
      line1 = line2 = 0;
    }

    /**
     * Create a unique key from the virtual link label elements.
     *
     * @return key
     */
    public String getKey() {
      NumberFormat format8 = new DecimalFormat("00000000");
      NumberFormat format5 = new DecimalFormat("00000");
      NumberFormat format4 = new DecimalFormat("0000");
      NumberFormat format2 = new DecimalFormat("00");
      String node1 = format8.format(this.node1);

      if (this.node1 > 0) {
        node1 = '+' + node1;
      }

      String node2 = format8.format(this.node2);

      if (this.node2 > 0) {
        node2 = '+' + node2;
      }

      return node1
          + format8.format(link1)
          + format2.format(mode1)
          + format2.format(means1)
          + format4.format(line1)
          + node2
          + format8.format(link2)
          + format2.format(mode2)
          + format2.format(means2)
          + format4.format(line2)
          + format5.format(time);
    }
  }

  private JDBCUtils jdbcUtils;

  private NodusProject nodusProject;

  /**
   * Initializes the class.
   *
   * @param nodusProject The Nodus project the scenario(weights) belong to.
   */
  public Scenarios(NodusProject nodusProject) {
    this.nodusProject = nodusProject;
    jdbcUtils = new JDBCUtils(nodusProject.getMainJDBCConnection());
  }

  /**
   * Compares two existing scenarios, creating a result scenario. The comparison can be filtered by
   * means of a SQL statement.
   *
   * @param referenceScenario ID of the reference scenario.
   * @param scenarioToCompare ID of the scenario that must be compared to the reference.
   * @param resultScenario ID of the resulting scenario.
   * @param whereString SQL where statement used as filter.
   */
  public void compare(
      int referenceScenario, int scenarioToCompare, int resultScenario, String whereString) {
    plusOrMinus(referenceScenario, scenarioToCompare, resultScenario, whereString, true);
  }

  /**
   * Compares two existing scenarios, creating a result scenario. The comparison can be filtered by
   * means of a SQL statement.
   *
   * @param referenceScenario ID of the reference scenario.
   * @param scenarioToCompare ID of the scenario that must be compared to the reference.
   * @param resultScenario ID of the resulting scenario.
   * @param whereString SQL where statement used as filter. @ param compare True to compare
   *     scenarios, false to sum them up.
   */
  private void plusOrMinus(
      int referenceScenario,
      int scenarioToCompare,
      int resultScenario,
      String whereString,
      boolean compare) {

    /*
     * The tables to compare don't have to have the same structure, as the number of groups and/or
     * group numbers can be different. We have to make a list of groups which merges the two
     * structures
     */
    LinkedList<Byte> groupList1 = new LinkedList<>();
    LinkedList<Byte> groupList2 = new LinkedList<>();

    Connection con = nodusProject.getMainJDBCConnection();

    try {
      DatabaseMetaData metaData = con.getMetaData();

      // First table
      String tableName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
      tableName =
          nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + referenceScenario;
      tableName = jdbcUtils.getCompliantIdentifier(tableName);

      ResultSet col = metaData.getColumns(null, null, tableName, null);

      while (col.next()) {
        String c = col.getString(4).toLowerCase();

        // Get the group number
        if (c.startsWith(NodusC.DBF_QUANTITY)) {
          String num = c.substring(NodusC.DBF_QUANTITY.length(), c.length());

          if (num.length() > 0) {
            groupList1.add(Byte.valueOf(num));
          }
        }
      }

      // Second table
      tableName = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
      tableName =
          nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + scenarioToCompare;
      tableName = jdbcUtils.getCompliantIdentifier(tableName);
      col = metaData.getColumns(null, null, tableName, null);

      while (col.next()) {
        String c = col.getString(4).toLowerCase();

        if (c.startsWith(NodusC.DBF_QUANTITY)) {
          String num = c.substring(NodusC.DBF_QUANTITY.length(), c.length());

          if (num.length() > 0) {
            groupList2.add(Byte.valueOf(num));
          }
        }
      }

      col.close();
    } catch (SQLException ex) {
      System.err.println(ex.toString());
      return;
    }

    /* Create a new sorted array that merges the 2 others */
    LinkedList<Byte> groupList = new LinkedList<>(groupList1);
    Iterator<Byte> lit = groupList2.iterator();

    while (lit.hasNext()) {
      Byte b = lit.next();

      if (!groupList.contains(b)) {
        groupList.add(b);
      }
    }

    Byte[] gir = groupList.toArray(new Byte[groupList.size()]);
    byte[] groupsInResults = new byte[gir.length];
    for (int i = 0; i < gir.length; i++) {
      groupsInResults[i] = gir[i].byteValue();
    }

    Arrays.sort(groupsInResults);

    /* Now keep the indexes of the groups in the merged table */
    byte[] indexesForTable1 = new byte[groupList1.size()];
    lit = groupList1.iterator();

    int j = 0;

    while (lit.hasNext()) {
      Byte b = lit.next();

      for (byte i = 0; i < groupsInResults.length; i++) {
        if (b.byteValue() == groupsInResults[i]) {
          indexesForTable1[j] = i;

          break;
        }
      }

      j++;
    }

    byte[] indexesForTable2 = new byte[groupList2.size()];
    lit = groupList2.iterator();
    j = 0;

    while (lit.hasNext()) {
      Byte b = lit.next();

      for (byte i = 0; i < groupsInResults.length; i++) {
        if (b.byteValue() == groupsInResults[i]) {
          indexesForTable2[j] = i;

          break;
        }
      }

      j++;
    }

    /* Read the first table and create an hashtable with all the records */
    HashMap<String, VnetRecord> hashMap = null;

    String tableName =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName =
        nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + referenceScenario;
    tableName = jdbcUtils.getCompliantIdentifier(tableName);

    String sqlStmt = "SELECT * from " + tableName;

    if (whereString.length() > 0) {
      sqlStmt += " WHERE " + whereString;
    }

    try {
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sqlStmt);

      // Set initial capacity of the hash table to the number of records
      // fetch + 10%
      hashMap = new HashMap<>((int) (rs.getFetchSize() * 1.1));

      // Retrieve result of query
      while (rs.next()) {
        VnetRecord vnr = new VnetRecord(groupsInResults.length);

        // Fetch basic elements

        vnr.node1 = JDBCUtils.getInt(rs.getObject(1));
        vnr.link1 = JDBCUtils.getInt(rs.getObject(2));
        vnr.mode1 = JDBCUtils.getInt(rs.getObject(3));
        vnr.means1 = JDBCUtils.getInt(rs.getObject(4));
        vnr.line1 = JDBCUtils.getInt(rs.getObject(5));
        vnr.node2 = JDBCUtils.getInt(rs.getObject(6));
        vnr.link2 = JDBCUtils.getInt(rs.getObject(7));
        vnr.mode2 = JDBCUtils.getInt(rs.getObject(8));
        vnr.means2 = JDBCUtils.getInt(rs.getObject(9));
        vnr.line2 = JDBCUtils.getInt(rs.getObject(10));
        vnr.time = JDBCUtils.getInt(rs.getObject(11));
        vnr.length = JDBCUtils.getDouble(rs.getObject(12));

        // Fetch group related results
        int offset = 13;

        for (int i = 0; i < indexesForTable1.length; i++) {
          int index = indexesForTable1[i];
          vnr.cost[index] = JDBCUtils.getDouble(rs.getObject(offset + 3 * i));
          vnr.qty[index] = JDBCUtils.getDouble(rs.getObject(offset + 3 * i + 1));
          vnr.vehicles[index] = JDBCUtils.getInt(rs.getObject(offset + 3 * i + 2));
        }

        // Put this record in the hash table
        hashMap.put(vnr.getKey(), vnr);
      }

      rs.close();
      stmt.close();

    } catch (SQLException ex) {
      System.err.println(ex.toString());
    }

    /* Read the second table and update the hashtable with all the records */
    tableName = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName =
        nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + scenarioToCompare;
    tableName = jdbcUtils.getCompliantIdentifier(tableName);

    sqlStmt = "SELECT * from " + tableName;

    if (whereString.length() > 0) {
      sqlStmt += " WHERE " + whereString;
    }

    try {
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sqlStmt);

      // Retrieve result of query
      while (rs.next()) {
        VnetRecord vnr = new VnetRecord(groupsInResults.length);

        // Fetch basic elements

        vnr.node1 = JDBCUtils.getInt(rs.getObject(1));
        vnr.link1 = JDBCUtils.getInt(rs.getObject(2));
        vnr.mode1 = JDBCUtils.getInt(rs.getObject(3));
        vnr.means1 = JDBCUtils.getInt(rs.getObject(4));
        vnr.line1 = JDBCUtils.getInt(rs.getObject(5));
        vnr.node2 = JDBCUtils.getInt(rs.getObject(6));
        vnr.link2 = JDBCUtils.getInt(rs.getObject(7));
        vnr.mode2 = JDBCUtils.getInt(rs.getObject(8));
        vnr.means2 = JDBCUtils.getInt(rs.getObject(9));
        vnr.line2 = JDBCUtils.getInt(rs.getObject(10));
        vnr.time = JDBCUtils.getInt(rs.getObject(11));
        vnr.length = JDBCUtils.getDouble(rs.getObject(12));

        // Look if this virtual links already exists
        String key = vnr.getKey();
        VnetRecord vnr2 = hashMap.get(key);

        if (vnr2 != null) {
          vnr = vnr2;
        } else {
          hashMap.put(key, vnr);
        }

        // Fetch group related results and substract values from the
        // scenario 1
        int offset = 13;

        for (int i = 0; i < indexesForTable2.length; i++) {
          int index = indexesForTable2[i];
          if (compare) {
            vnr.cost[index] -= JDBCUtils.getDouble(rs.getObject(offset + 3 * i));
            vnr.qty[index] -= JDBCUtils.getDouble(rs.getObject(offset + 3 * i + 1));
            vnr.vehicles[index] -= JDBCUtils.getInt(rs.getObject(offset + 3 * i + 2));
          } else {
            vnr.cost[index] += JDBCUtils.getDouble(rs.getObject(offset + 3 * i));
            vnr.qty[index] += JDBCUtils.getDouble(rs.getObject(offset + 3 * i + 1));
            vnr.vehicles[index] += JDBCUtils.getInt(rs.getObject(offset + 3 * i + 2));
          }
        }
      }

      rs.close();
      stmt.close();

    } catch (SQLException ex) {
      System.err.println(ex.toString());
      return;
    }

    /* Now create the result table and save the data into it */
    try {

      // Create result table
      if (!VirtualNetworkWriter.initTable(nodusProject, resultScenario, groupsInResults)) {
        return;
      }

      /* Prepared different statement for the different version of Virtual Network */
      sqlStmt =
          "INSERT INTO "
              + VirtualNetworkWriter.getTableName()
              + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,";

      for (byte k = 0; k < (byte) groupsInResults.length; k++) {
        sqlStmt += "?,?,?,";
      }
      sqlStmt += "?,?,?)";
      PreparedStatement prepStmt = con.prepareStatement(sqlStmt);

      // Save the data from the hashtable.
      Statement stmt = con.createStatement();

      Iterator<VnetRecord> it = hashMap.values().iterator();
      while (it.hasNext()) {
        VnetRecord vnr = it.next();

        int idx = 1;
        /*
         * With the virtual network 3, insert in the table the line origin and the line
         * destination. With the virtual network 2, don't make any change.
         */
        prepStmt.setInt(idx++, vnr.node1);
        prepStmt.setInt(idx++, vnr.link1);
        prepStmt.setInt(idx++, vnr.mode1);
        prepStmt.setInt(idx++, vnr.means1);
        prepStmt.setInt(idx++, vnr.line1);
        prepStmt.setInt(idx++, vnr.node2);
        prepStmt.setInt(idx++, vnr.link2);
        prepStmt.setInt(idx++, vnr.mode2);
        prepStmt.setInt(idx++, vnr.means2);
        prepStmt.setInt(idx++, vnr.line2);
        prepStmt.setInt(idx++, vnr.time);
        prepStmt.setDouble(idx++, vnr.length);

        // double totalCost = 0.0;
        double totalQty = 0.0;
        double averageWeight = 0.0;
        int totalVehicles = 0;

        for (byte k = 0; k < (byte) groupsInResults.length; k++) {
          // totalCost += vnr.cost[k];
          totalQty += vnr.qty[k];
          averageWeight += vnr.qty[k] * vnr.cost[k];
          totalVehicles += vnr.vehicles[k];

          prepStmt.setDouble(idx++, vnr.cost[k]);
          prepStmt.setDouble(idx++, vnr.qty[k]);
          prepStmt.setInt(idx++, vnr.vehicles[k]);
        }

        if (totalQty > 0) {
          averageWeight /= totalQty;
        }

        prepStmt.setDouble(idx++, averageWeight);
        prepStmt.setDouble(idx++, totalQty);
        prepStmt.setInt(idx++, totalVehicles);

        prepStmt.executeUpdate();
      }

      stmt.close();
      if (!con.getAutoCommit()) {
        con.commit();
      }
    } catch (Exception e) {
      System.err.println(e.toString());
      return;
    }
  }

  /**
   * Removes a scenario, dropping all the relevant tables in the database and its local properties.
   *
   * @param scenarioId ID of the scenario to delete.
   */
  public void remove(int scenarioId) {
    nodusProject.removeScenario(scenarioId);
  }

  /**
   * Rename a scenario, including all its related entries in the properties.
   *
   * @param currentId Current ID of the scenario.
   * @param newId New ID of the scenario.
   */
  public void rename(int currentId, int newId) {
    nodusProject.renameScenario(currentId, newId);
  }

  /**
   * Sums-up the results of two scenarios. Can be useful to consolidate the flows of two separate
   * assignments.
   *
   * @param scenario1 ID of the first scenario.
   * @param scenario2 ID of the second scenario.
   * @param resultScenario ID of the resulting scenario.
   * @param whereString SQL where statement used as filter.
   */
  public void sum(int scenario1, int scenario2, int resultScenario, String whereString) {
    plusOrMinus(scenario1, scenario2, resultScenario, whereString, false);
  }
}
