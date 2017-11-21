/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.compute.virtual;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.PathDetailedCosts;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.database.JDBCField;
import edu.uclouvain.core.nodus.database.JDBCIndex;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.SingleInstanceMessagePane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.JOptionPane;

/**
 * Writes the path headers and detail tables in the database.
 *
 * @author Bart Jourquin
 */
public class PathWriter {

  private Connection con;

  private int currentPathIndex = 1;

  private DecimalFormat df;

  private JDBCUtils jdbcUtils;

  private NodusProject nodusProject;

  private String pathDetailTableName;

  private String pathHeaderTableName;

  private PreparedStatement prepStmtDetails;

  private PreparedStatement prepStmtHeaders;

  private boolean saveDetailedPaths;

  private boolean savePaths;

  private int scenario;

  private static I18n i18n = Environment.getI18n();

  /**
   * Initializes the different tables needed to store the paths.
   *
   * @param assignmentParameters The assignment parameters.
   */
  public PathWriter(AssignmentParameters assignmentParameters) {
    nodusProject = assignmentParameters.getNodusProject();
    jdbcUtils = new JDBCUtils(nodusProject.getMainJDBCConnection());

    scenario = assignmentParameters.getScenario();
    savePaths = assignmentParameters.isSavePaths();
    saveDetailedPaths = assignmentParameters.isDetailedPaths();

    // Decimal format used in sql statements
    DecimalFormatSymbols dfs = new DecimalFormatSymbols();
    dfs.setDecimalSeparator('.');
    df = new DecimalFormat("0.000", dfs);

    con = nodusProject.getMainJDBCConnection();

    // Prepare tables
    String defValue = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    String name = nodusProject.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, defValue);

    pathHeaderTableName = jdbcUtils.getCompliantIdentifier(name + scenario + NodusC.SUFFIX_HEADER);
    pathDetailTableName = jdbcUtils.getCompliantIdentifier(name + scenario + NodusC.SUFFIX_DETAIL);

    // Create new tables if needed
    if (isSavePaths()) {
      resetPathsTables();
    } else {
      // Drop existing tables if they exist
      jdbcUtils.dropTable(pathHeaderTableName);
      jdbcUtils.dropTable(pathDetailTableName);
    }

    // Set the static index to 0 (used by multiflow assignments)
    AssignmentWorker.resetPathIndex();

    SingleInstanceMessagePane.reset();
  }

  /** Closes the connection to the database. */
  public void close() {

    if (!savePaths) {
      return;
    }

    try {
      if (!con.getAutoCommit()) {
        con.commit();
      }

      prepStmtHeaders.close();
      if (saveDetailedPaths) {
        prepStmtDetails.close();
      }

    } catch (SQLException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Returns true if the detailed paths must be stored in the database.
   *
   * @return boolean True if the paths must be saved.
   */
  public boolean isSavePaths() {
    return savePaths;
  }

  /** Creates empty tables to store the paths. */
  private void resetPathsTables() {

    int nbFields = 19;
    JDBCField[] fields = new JDBCField[nbFields];
    int idx = 0;
    fields[idx++] = new JDBCField(NodusC.DBF_GROUP, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_ORIGIN, "NUMERIC(10)");
    fields[idx++] = new JDBCField(NodusC.DBF_DESTINATION, "NUMERIC(10)");
    fields[idx++] = new JDBCField(NodusC.DBF_TIME, "NUMERIC(5)");
    fields[idx++] = new JDBCField(NodusC.DBF_ITERATION, "NUMERIC(3)");
    fields[idx++] = new JDBCField(NodusC.DBF_QUANTITY, "NUMERIC(13,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_LENGTH, "NUMERIC(8,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_DURATION, "NUMERIC(7,0)");
    fields[idx++] = new JDBCField(NodusC.DBF_LDCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_ULCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_TRCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_TPCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_MVCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_LDMODE, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_LDMEANS, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_ULMODE, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_ULMEANS, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_NBTRANS, "NUMERIC(3)");
    fields[idx++] = new JDBCField(NodusC.DBF_PATH_INDEX, "NUMERIC(8)");

    JDBCIndex[] indexes = new JDBCIndex[2];

    // Create index on origin node
    indexes[0] =
        new JDBCIndex(pathHeaderTableName, NodusC.DBF_ORIGIN + scenario, NodusC.DBF_ORIGIN);
    // Create index on path index
    indexes[1] =
        new JDBCIndex(
            pathHeaderTableName, NodusC.DBF_PATH_INDEX + "H" + scenario, NodusC.DBF_PATH_INDEX);

    jdbcUtils.createTable(pathHeaderTableName, fields, indexes);

    // Use prepared statements to improve insert performances
    String sqlStmt =
        "INSERT INTO "
            + jdbcUtils.getCompliantIdentifier(pathHeaderTableName)
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    try {
      prepStmtHeaders = con.prepareStatement(sqlStmt);
    } catch (SQLException e) {
      e.printStackTrace();
    }

    if (saveDetailedPaths) {
      fields = new JDBCField[4];
      idx = 0;
      fields[idx++] = new JDBCField(NodusC.DBF_PATH_INDEX, "NUMERIC(8)");
      fields[idx++] = new JDBCField(NodusC.DBF_LINK, "NUMERIC(10)");
      fields[idx++] = new JDBCField(NodusC.DBF_MODE, "NUMERIC(2)");
      fields[idx++] = new JDBCField(NodusC.DBF_MEANS, "NUMERIC(2)");

      // Create index on path index
      indexes = new JDBCIndex[1];
      indexes[0] =
          new JDBCIndex(
              pathDetailTableName, NodusC.DBF_PATH_INDEX + "D" + scenario, NodusC.DBF_PATH_INDEX);
      jdbcUtils.createTable(pathDetailTableName, fields, indexes);

      // Use prepared statements to improve insert performances
      sqlStmt =
          "INSERT INTO "
              + jdbcUtils.getCompliantIdentifier(pathDetailTableName)
              + " VALUES (?,?,?,?)";
      try {
        prepStmtDetails = con.prepareStatement(sqlStmt);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Save a link and its associated quantity in the detailed path.
   *
   * @param virtualLink The virtual link to save.
   */
  public synchronized void savePathLink(VirtualLink virtualLink) {
    savePathLink(virtualLink, currentPathIndex);
  }

  /**
   * Save a link and its associated quantity in the detailed path.
   *
   * @param virtualLink The virtual link to save.
   * @param pathIndex The index of the path.
   */
  public synchronized void savePathLink(VirtualLink virtualLink, int pathIndex) {
    if (!saveDetailedPaths) {
      return;
    }

    // Up or down flow?
    int up = 1;
    if (virtualLink.getBeginVirtualNode().getRealNodeId(false)
        > virtualLink.getEndVirtualNode().getRealNodeId(false)) {
      up = -1;
    }

    // Set values
    try {
      int idx = 1;
      prepStmtDetails.setInt(idx++, pathIndex);
      prepStmtDetails.setInt(idx++, up * virtualLink.getBeginVirtualNode().getRealLinkId());
      prepStmtDetails.setInt(idx++, virtualLink.getBeginVirtualNode().getMode());
      prepStmtDetails.setInt(idx++, virtualLink.getBeginVirtualNode().getMeans());
      prepStmtDetails.executeUpdate();
    } catch (Exception e) {
      System.err.println(e.toString());
    }
  }

  /**
   * Save the header for a path for a give O-D pair computed at a given iteration.
   *
   * @param iteration The iteration of the assignment.
   * @param odCell The OD cell associated to this path.
   * @param quantity The quantity to assign to this path.
   * @param length The length of this path.
   * @param duration The travel duration (in seconds).
   * @param pc The costs of the different operations (loading, moving, ...) relative to this path.
   * @param ldMode The ID of the mode used at the origin.
   * @param ldMeans The ID of the means used at the origin.
   * @param ulMode The ID of the mode used at the destination.
   * @param ulMeans The ID of the means used at the destination.
   * @param nbTranshipments The number of transhipment operations along the path.
   * @return True on success.
   */
  public synchronized boolean savePathHeader(
      int iteration,
      ODCell odCell,
      double quantity,
      float length,
      float duration,
      PathDetailedCosts pc,
      byte ldMode,
      byte ldMeans,
      byte ulMode,
      byte ulMeans,
      int nbTranshipments) {
    if (!savePathHeader(
        iteration,
        odCell,
        quantity,
        length,
        duration,
        pc,
        ldMode,
        ldMeans,
        ulMode,
        ulMeans,
        nbTranshipments,
        currentPathIndex)) {
      return false;
    }
    currentPathIndex++;
    return true;
  }

  /**
   * Save the header for a path for a give O-D pair computed at a given iteration.
   *
   * @param iteration The iteration of the assignment.
   * @param odCell The OD cell associated to this path.
   * @param quantity The quantity to assign to this path.
   * @param length The length of this path.
   * @param duration The travel duration (in seconds).
   * @param pc The costs of the different operations (loading, moving, ...) relative to this path.
   * @param ldMode The ID of the mode used at the origin.
   * @param ldMeans The ID of the means used at the origin.
   * @param ulMode The ID of the mode used at the destination.
   * @param ulMeans The ID of the means used at the destination.
   * @param nbTranshipments The number of transhipment operations along the path.
   * @param pathIndex The index of the path.
   * @return True on success.
   */
  public synchronized boolean savePathHeader(
      int iteration,
      ODCell odCell,
      double quantity,
      float length,
      float duration,
      PathDetailedCosts pc,
      byte ldMode,
      byte ldMeans,
      byte ulMode,
      byte ulMeans,
      int nbTranshipments,
      int pathIndex) {
    try {

      int idx = 1;
      prepStmtHeaders.setInt(idx++, odCell.getGroup());
      prepStmtHeaders.setInt(idx++, odCell.getOriginNodeId());
      prepStmtHeaders.setInt(idx++, odCell.getDestinationNodeId());
      prepStmtHeaders.setInt(idx++, odCell.getStartingTime() / 60);
      prepStmtHeaders.setInt(idx++, iteration);
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(quantity)));
      prepStmtHeaders.setFloat(idx++, Float.parseFloat(df.format(length)));
      prepStmtHeaders.setFloat(idx++, Float.parseFloat(df.format(duration)));
      prepStmtHeaders.setDouble(idx++, pc.ldCosts);
      prepStmtHeaders.setDouble(idx++, pc.ulCosts);
      prepStmtHeaders.setDouble(idx++, pc.trCosts);
      prepStmtHeaders.setDouble(idx++, pc.tpCosts);
      prepStmtHeaders.setDouble(idx++, pc.mvCosts);
      prepStmtHeaders.setInt(idx++, ldMode);
      prepStmtHeaders.setInt(idx++, ldMeans);
      prepStmtHeaders.setInt(idx++, ulMode);
      prepStmtHeaders.setInt(idx++, ldMeans);
      prepStmtHeaders.setInt(idx++, nbTranshipments);
      prepStmtHeaders.setInt(idx++, pathIndex);

      prepStmtHeaders.executeUpdate();
    } catch (Exception e) {
      nodusProject.getNodusMapPanel().stopProgress();
      SingleInstanceMessagePane.display(
          nodusProject.getNodusMapPanel(),
          i18n.get(
              PathWriter.class,
              "Invalid_value",
              "Invalid value in header fields. See Stack Trace."),
          JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Balance the flow between the previous saved detailed paths and those saved during the current
   * iteration. This is used in equilibrium assignment algorithms.
   *
   * @param iteration The iteration of the assignment.
   * @param lambda The balance factor : (1-lambda) * previous flow + lambda * current flow.
   */
  public void splitPaths(int iteration, double lambda) {
    if (iteration <= 1) {
      return;
    }

    try {

      Statement stmt = con.createStatement();

      /*
       * Update records relative to previous iterations Example: UPDATE HeaderTable set QTY =
       * ROUND(QTY*(1-lambda),3) where iteration < it
       */
      String sqlStmt =
          "UPDATE "
              + pathHeaderTableName
              + " SET "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
              + " = ROUND("
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
              + "*(1-"
              + lambda
              + "),3)"
              + " WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_ITERATION)
              + " < "
              + iteration;
      stmt.execute(sqlStmt);

      /**
       * Update records relative to current iterations Example: UPDATE HeaderTable set QTY =
       * ROUND(QTY*lambda,3) where iteration = it
       */
      sqlStmt =
          "UPDATE "
              + pathHeaderTableName
              + " SET "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
              + " = ROUND("
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
              + "*"
              + lambda
              + ",3) WHERE "
              + jdbcUtils.getQuotedCompliantIdentifier(NodusC.DBF_ITERATION)
              + " = "
              + iteration;
      stmt.execute(sqlStmt);

      stmt.close();

      if (!con.getAutoCommit()) {
        con.commit();
      }
    } catch (Exception e) {
      System.err.println(e.toString());
    }
  }
}
