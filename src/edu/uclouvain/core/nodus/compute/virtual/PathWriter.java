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

package edu.uclouvain.core.nodus.compute.virtual;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.database.JDBCField;
import edu.uclouvain.core.nodus.database.JDBCIndex;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.SingleInstanceMessagePane;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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

  private NodusProject nodusProject;

  private String pathDetailTableName;

  private String pathHeaderTableName;

  private PreparedStatement prepStmtDetails;

  private PreparedStatement prepStmtHeaders;

  private boolean saveDetailedPaths;

  private boolean savePaths;

  private int scenario;

  private static I18n i18n = Environment.getI18n();

  private int headerBatchSize = 0;

  private int detailsBatchSize = 0;

  private int maxBatchSize;

  private boolean hasBatchSupport = false;

  private boolean canceled = false;

  private boolean hasDurationFunctions = false;

  /**
   * Initializes the different tables needed to store the paths.
   *
   * @param assignmentParameters The assignment parameters.
   */
  public PathWriter(AssignmentParameters assignmentParameters) {
    nodusProject = assignmentParameters.getNodusProject();

    scenario = assignmentParameters.getScenario();
    savePaths = assignmentParameters.isSavePaths();
    saveDetailedPaths = assignmentParameters.isDetailedPaths();
    hasDurationFunctions = assignmentParameters.hasDurationFunctions();

    if (!savePaths) {
      saveDetailedPaths = false;
    }

    maxBatchSize =
        nodusProject.getLocalProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, NodusC.MAXBATCHSIZE);

    // Decimal format used in sql statements
    DecimalFormatSymbols dfs = new DecimalFormatSymbols();
    dfs.setDecimalSeparator('.');
    df = new DecimalFormat("0.000", dfs);

    con = nodusProject.getMainJDBCConnection();

    // Does the used DB support batch processing ?
    try {
      DatabaseMetaData dbmd = con.getMetaData();
      hasBatchSupport = false;
      if (dbmd != null) {
        if (dbmd.supportsBatchUpdates()) {
          hasBatchSupport = true;
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }

    // Prepare tables
    String defValue = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    String name = nodusProject.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, defValue);

    pathHeaderTableName = JDBCUtils.getCompliantIdentifier(name + scenario + NodusC.SUFFIX_HEADER);
    pathDetailTableName = JDBCUtils.getCompliantIdentifier(name + scenario + NodusC.SUFFIX_DETAIL);

    // Create new tables if needed
    if (isSavePaths()) {
      createPathsTables();
    } else {
      // Drop existing tables if they exist
      JDBCUtils.dropTable(pathHeaderTableName);
      JDBCUtils.dropTable(pathDetailTableName);
    }

    // Set the static index to 0 (used by multiflow assignments)
    AssignmentWorker.resetPathIndex();

    SingleInstanceMessagePane.reset();
  }

  /** Delete the paths table(s) in the database. */
  public void deletePathsTables() {
    if (savePaths) {
      JDBCUtils.dropTable(pathHeaderTableName);
      if (saveDetailedPaths) {
        JDBCUtils.dropTable(pathDetailTableName);
      }
    }
  }

  /** Save the SQL batches, create the indexes and close the connection to the database. */
  public void close() {

    if (!savePaths) {
      return;
    }

    nodusProject
        .getNodusMapPanel()
        .setText(i18n.get(PathWriter.class, "Creating_indexes", "Creating indexes..."));

    if (hasBatchSupport) {
      executeHeaderBatch(true);
    }

    // Create index on origin node
    JDBCIndex index =
        new JDBCIndex(pathHeaderTableName, NodusC.DBF_ORIGIN + scenario, NodusC.DBF_ORIGIN);
    JDBCUtils.createIndex(index);

    // Create index on path index
    index =
        new JDBCIndex(
            pathHeaderTableName, NodusC.DBF_PATH_INDEX + "H" + scenario, NodusC.DBF_PATH_INDEX);
    JDBCUtils.createIndex(index);

    if (saveDetailedPaths) {
      if (hasBatchSupport) {
        executeDetailsBatch(true);
      }

      // Create index on path index
      index =
          new JDBCIndex(
              pathDetailTableName, NodusC.DBF_PATH_INDEX + "D" + scenario, NodusC.DBF_PATH_INDEX);
      JDBCUtils.createIndex(index);
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
   * Execute batch if max batch size is reached.
   *
   * @param force If true, the batch is exectured even if its max size is not reached.
   * @return True on success
   */
  private boolean executeDetailsBatch(boolean force) {

    detailsBatchSize++;

    if (detailsBatchSize < maxBatchSize && !force) {
      return true;
    }

    try {
      final boolean oldAutoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        prepStmtDetails.executeBatch();
      } catch (Exception e) {
        // If not in batch mode because there is nothing to write in table
      }
      con.commit();
      con.setAutoCommit(oldAutoCommit);
    } catch (SQLException e) {
      nodusProject.getNodusMapPanel().stopProgress();
      SingleInstanceMessagePane.display(
          nodusProject.getNodusMapPanel(), e.getMessage(), JOptionPane.ERROR_MESSAGE);

      return false;
    }

    detailsBatchSize = 0;
    return true;
  }

  /**
   * Execute batch if max batch size is reached.
   *
   * @param force If true, the batch is exectured even if its max size is not reached.
   * @return True on success
   */
  private boolean executeHeaderBatch(boolean force) {

    headerBatchSize++;

    if (headerBatchSize < maxBatchSize && !force) {
      return true;
    }

    try {
      final boolean oldAutoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        prepStmtHeaders.executeBatch();
      } catch (Exception e) {
        // If not in batch mode because there is nothing to write in table
      }
      con.commit();
      con.setAutoCommit(oldAutoCommit);
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }

    headerBatchSize = 0;
    return true;
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
  private void createPathsTables() {

    int nbFields = 27;
    JDBCField[] fields = new JDBCField[nbFields];
    int idx = 0;
    fields[idx++] = new JDBCField(NodusC.DBF_GROUP, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_ORIGIN, "NUMERIC(10)");
    fields[idx++] = new JDBCField(NodusC.DBF_DESTINATION, "NUMERIC(10)");
    fields[idx++] = new JDBCField(NodusC.DBF_TIME, "NUMERIC(5)");
    fields[idx++] = new JDBCField(NodusC.DBF_ITERATION, "NUMERIC(3)");
    fields[idx++] = new JDBCField(NodusC.DBF_QUANTITY, "NUMERIC(13,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_LENGTH, "NUMERIC(8,3)");

    fields[idx++] = new JDBCField(NodusC.DBF_LDCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_ULCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_TRCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_TPCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_STCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_SWCOST, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_MVCOST, "NUMERIC(10,3)");

    fields[idx++] = new JDBCField(NodusC.DBF_LDDURATION, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_ULDURATION, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_TRDURATION, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_TPDURATION, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_STDURATION, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_SWDURATION, "NUMERIC(10,3)");
    fields[idx++] = new JDBCField(NodusC.DBF_MVDURATION, "NUMERIC(10,3)");

    fields[idx++] = new JDBCField(NodusC.DBF_LDMODE, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_LDMEANS, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_ULMODE, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_ULMEANS, "NUMERIC(2)");
    fields[idx++] = new JDBCField(NodusC.DBF_NBTRANS, "NUMERIC(3)");
    fields[idx++] = new JDBCField(NodusC.DBF_PATH_INDEX, "NUMERIC(8)");
    JDBCUtils.createTable(pathHeaderTableName, fields);

    // Use prepared statements to improve insert performances
    String sqlStmt =
        "INSERT INTO "
            + JDBCUtils.getCompliantIdentifier(pathHeaderTableName)
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

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
      JDBCUtils.createTable(pathDetailTableName, fields);

      // Use prepared statements to improve insert performances
      sqlStmt =
          "INSERT INTO "
              + JDBCUtils.getCompliantIdentifier(pathDetailTableName)
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

      if (hasBatchSupport) {
        prepStmtDetails.addBatch();
        executeDetailsBatch(false);
      } else {
        prepStmtDetails.executeUpdate();
      }

    } catch (Exception e) {
      System.err.println(e.toString());
    }
  }

  /**
   * Save the header for a path for a give O-D pair computed at a given iteration.
   *
   * @param iteration The iteration of the assignment.
   * @param odCell The OD cell associated to this path.
   * @param quantity The quantity to assign to this path. It can be different from the one stored in
   *     the OD cell.
   * @param weights The costs, durations and length relative to this path.
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
      PathWeights weights,
      byte ldMode,
      byte ldMeans,
      byte ulMode,
      byte ulMeans,
      int nbTranshipments) {

    if (canceled) {
      return false;
    }

    if (Double.isNaN(quantity)) {
      canceled = true;
      JOptionPane.showMessageDialog(
          null,
          i18n.get(PathWriter.class, "QuantityIsNan", "Quantity is NaN !"),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
      return false;
    }

    if (!savePathHeader(
        iteration,
        odCell,
        quantity,
        weights,
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
   * @param quantity The quantity to assign to this path. It can be different from the one stored in
   *     the OD cell.
   * @param detailedCosts The costs, durations and length relative to this path.
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
      PathWeights detailedCosts,
      byte ldMode,
      byte ldMeans,
      byte ulMode,
      byte ulMeans,
      int nbTranshipments,
      int pathIndex) {

    if (canceled) {
      return false;
    }

    // For backward compatibility with assignments from Nodus < 7.3
    if (!hasDurationFunctions) {
      detailedCosts.mvDuration = Math.round(detailedCosts.mvDuration);
    }

    if (Double.isNaN(quantity)) {
      canceled = true;
      JOptionPane.showMessageDialog(
          null,
          i18n.get(PathWriter.class, "QuantityIsNan", "Quantity is NaN !"),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
      return false;
    }

    try {

      int idx = 1;
      prepStmtHeaders.setInt(idx++, odCell.getGroup());
      prepStmtHeaders.setInt(idx++, odCell.getOriginNodeId());
      prepStmtHeaders.setInt(idx++, odCell.getDestinationNodeId());
      prepStmtHeaders.setInt(idx++, odCell.getStartingTime() / 60);
      prepStmtHeaders.setInt(idx++, iteration);
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(quantity)));
      prepStmtHeaders.setFloat(idx++, Float.parseFloat(df.format(detailedCosts.length)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.ldCost)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.ulCost)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.trCost)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.tpCost)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.stpCost)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.swCost)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.mvCost)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.ldDuration)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.ulDuration)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.trDuration)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.tpDuration)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.stpDuration)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.swDuration)));
      prepStmtHeaders.setDouble(idx++, Double.parseDouble(df.format(detailedCosts.mvDuration)));
      prepStmtHeaders.setInt(idx++, ldMode);
      prepStmtHeaders.setInt(idx++, ldMeans);
      prepStmtHeaders.setInt(idx++, ulMode);
      prepStmtHeaders.setInt(idx++, ldMeans);
      prepStmtHeaders.setInt(idx++, nbTranshipments);
      prepStmtHeaders.setInt(idx++, pathIndex);

      if (hasBatchSupport) {
        prepStmtHeaders.addBatch();
        executeHeaderBatch(false);
      } else {
        prepStmtHeaders.executeUpdate();
      }

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
   * Balance the volume between the previous saved detailed paths and those saved during the current
   * iteration. This is used in equilibrium assignment algorithms.
   *
   * @param iteration The iteration of the assignment.
   * @param lambda The balance factor : (1-lambda) * previous volume + lambda * current volume.
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
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
              + " = ROUND("
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
              + "*(1-"
              + lambda
              + "),3)"
              + " WHERE "
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ITERATION)
              + " < "
              + iteration;
      stmt.execute(sqlStmt);

      /*
       * Update records relative to current iterations Example: UPDATE HeaderTable set QTY =
       * ROUND(QTY*lambda,3) where iteration = it
       */
      sqlStmt =
          "UPDATE "
              + pathHeaderTableName
              + " SET "
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
              + " = ROUND("
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_QUANTITY)
              + "*"
              + lambda
              + ",3) WHERE "
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_ITERATION)
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
