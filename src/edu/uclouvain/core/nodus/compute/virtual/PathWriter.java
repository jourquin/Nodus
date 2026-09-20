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

package edu.uclouvain.core.nodus.compute.virtual;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
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
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JOptionPane;

/**
 * Writes the path headers and detail tables in the database.
 *
 * @author Bart Jourquin
 */
public class PathWriter {

  private final AssignmentComputingTimes computingTimes;

  private Connection con;

  private final AtomicInteger currentPathIndex = new AtomicInteger(1);

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

  private volatile boolean canceled = false;

  private boolean hasDurationFunctions = false;

  /** True once this writer has been finalized or discarded. */
  private volatile boolean closed = false;

  /**
   * Initializes the different tables needed to store the paths.
   *
   * @param assignmentParameters The assignment parameters.
   */
  public PathWriter(AssignmentParameters assignmentParameters) {
    computingTimes = assignmentParameters.getComputingTimes();
    long started = computingTimes.start();
    try {
      initialize(assignmentParameters);
    } finally {
      computingTimes.add(AssignmentComputingTimes.Phase.DATABASE, started);
    }
  }

  /** Initializes the writer and creates its tables within the database timing scope. */
  private void initialize(AssignmentParameters assignmentParameters) {
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
    df = PathWriterBuffer.newFormat();

    con = nodusProject.getMainJDBCConnection();

    // Does the used DB support batch processing ?
    hasBatchSupport = JDBCUtils.hasBatchSupport();

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

  /** Closes the prepared statements used by this writer. */
  private void closePreparedStatements() {
    if (prepStmtHeaders != null) {
      try {
        prepStmtHeaders.close();
      } catch (SQLException ex) {
        ex.printStackTrace();
      } finally {
        prepStmtHeaders = null;
      }
    }

    if (prepStmtDetails != null) {
      try {
        prepStmtDetails.close();
      } catch (SQLException ex) {
        ex.printStackTrace();
      } finally {
        prepStmtDetails = null;
      }
    }
  }

  /**
   * Commits the current JDBC connection if it is not in auto-commit mode.
   *
   * @return True on success.
   */
  private boolean commitIfNeeded() {
    try {
      if (con != null && !con.getAutoCommit()) {
        con.commit();
      }
      return true;
    } catch (SQLException ex) {
      ex.printStackTrace();
      return false;
    }
  }

  /**
   * Saves the SQL batches, creates the indexes and closes the prepared statements.
   *
   * @return True if all pending path data was saved.
   */
  public synchronized boolean close() {

    if (closed) {
      return !canceled;
    }

    long started = computingTimes.start();
    try {
      if (canceled) {
        return false;
      }
      if (savePaths) {
        if (nodusProject.getNodusMapPanel() != null) {
          nodusProject
              .getNodusMapPanel()
              .setText(i18n.get(PathWriter.class, "Creating_indexes", "Creating indexes..."));
        }

        if (hasBatchSupport) {
          if (!executeHeaderBatch(true)) {
            return false;
          }
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
            if (!executeDetailsBatch(true)) {
              return false;
            }
          }

          // Create index on path index
          index =
              new JDBCIndex(
                  pathDetailTableName,
                  NodusC.DBF_PATH_INDEX + "D" + scenario,
                  NodusC.DBF_PATH_INDEX);
          JDBCUtils.createIndex(index);
        }

        if (!commitIfNeeded()) {
          canceled = true;
          return false;
        }
      }
      return !canceled;
    } finally {
      closePreparedStatements();
      closed = true;
      computingTimes.add(AssignmentComputingTimes.Phase.DATABASE, started);
    }
  }

  /**
   * Abandons path output after a failed or cancelled assignment.
   *
   * <p>This method closes the prepared statements without flushing or indexing pending rows, then
   * drops the path tables created for this assignment scenario.
   */
  public synchronized void discard() {

    if (closed) {
      deletePathsTables();
      return;
    }

    try {
      closePreparedStatements();
      deletePathsTables();
      commitIfNeeded();
    } finally {
      closed = true;
    }
  }

  /**
   * Execute batch if max batch size is reached.
   *
   * @param force If true, the batch is executed even if its max size is not reached.
   * @return True on success
   */
  private boolean executeDetailsBatch(boolean force) {

    if (!force) {
      detailsBatchSize++;
    }

    if (detailsBatchSize == 0 || (detailsBatchSize < maxBatchSize && !force)) {
      return true;
    }

    try {
      checkBatchResult(prepStmtDetails.executeBatch());
      prepStmtDetails.clearBatch();
      detailsBatchSize = 0;
    } catch (SQLException e) {
      fail(e);
      return false;
    }

    return true;
  }

  /**
   * Execute batch if max batch size is reached.
   *
   * @param force If true, the batch is executed even if its max size is not reached.
   * @return True on success
   */
  private boolean executeHeaderBatch(boolean force) {

    if (!force) {
      headerBatchSize++;
    }

    if (headerBatchSize == 0 || (headerBatchSize < maxBatchSize && !force)) {
      return true;
    }

    try {
      checkBatchResult(prepStmtHeaders.executeBatch());
      prepStmtHeaders.clearBatch();
      headerBatchSize = 0;
    } catch (SQLException e) {
      fail(e);
      return false;
    }

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
    String quotedPathHeaderTableName = JDBCUtils.getQuotedCompliantIdentifier(pathHeaderTableName);
    String sqlStmt =
        "INSERT INTO "
            + quotedPathHeaderTableName
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
      String quotedPathDetailTableName =
          JDBCUtils.getQuotedCompliantIdentifier(pathDetailTableName);
      sqlStmt = "INSERT INTO " + quotedPathDetailTableName + " VALUES (?,?,?,?)";
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
    savePathLink(virtualLink, currentPathIndex.get());
  }

  /**
   * Save a link and its associated quantity in the detailed path.
   *
   * @param virtualLink The virtual link to save.
   * @param pathIndex The index of the path.
   */
  public synchronized void savePathLink(VirtualLink virtualLink, int pathIndex) {
    if (closed || canceled || !saveDetailedPaths) {
      return;
    }

    // Up or down flow?
    int up = 1;
    if (virtualLink.getBeginVirtualNode().getRealNodeId(false)
        > virtualLink.getEndVirtualNode().getRealNodeId(false)) {
      up = -1;
    }

    // Set values
    long started = computingTimes.start();
    try {
      writeDetail(
          pathIndex,
          up * virtualLink.getBeginVirtualNode().getRealLinkId(),
          virtualLink.getBeginVirtualNode().getMode(),
          virtualLink.getBeginVirtualNode().getMeans());
    } catch (Exception e) {
      fail(e);
    } finally {
      computingTimes.add(AssignmentComputingTimes.Phase.DATABASE, started);
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

    if (closed || canceled) {
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
        currentPathIndex.get())) {
      return false;
    }
    currentPathIndex.incrementAndGet();
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

    if (closed || canceled) {
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

    long started = computingTimes.start();
    try {

      PathWriterBuffer.Header header =
          PathWriterBuffer.Header.prepare(
              df,
              hasDurationFunctions,
              iteration,
              odCell,
              quantity,
              detailedCosts,
              ldMode,
              ldMeans,
              ulMode,
              ulMeans,
              nbTranshipments,
              pathIndex);
      return writeHeader(header);
    } catch (Exception e) {
      fail(e);
      return false;
    } finally {
      computingTimes.add(AssignmentComputingTimes.Phase.DATABASE, started);
    }
  }

  /**
   * Creates a buffer owned by one assignment-worker job. The worker must flush successful jobs
   * before they finish and clear failed jobs, so equilibrium splits and close see all accepted
   * rows.
   */
  public PathWriterBuffer newBuffer() {
    return new PathWriterBuffer(
        this, computingTimes, savePaths, saveDetailedPaths, hasDurationFunctions, maxBatchSize);
  }

  /** Reserves an implicit path ID once, before its first row, without taking the JDBC lock. */
  int reservePathIndex() {
    return currentPathIndex.getAndIncrement();
  }

  /** Volatile state lets workers avoid the shared lock when output is disabled or has stopped. */
  boolean isAcceptingRows() {
    return !closed && !canceled;
  }

  /**
   * Writes an entire worker block under one lock. Buffers remain worker-owned during this
   * synchronous handoff; the JDBC connection and prepared statements are never accessed
   * concurrently.
   */
  synchronized boolean writeBuffer(PathWriterBuffer buffer) {
    if (!isAcceptingRows()) {
      return false;
    }
    long started = computingTimes.start();
    try {
      for (PathWriterBuffer.Header header : buffer.headers) {
        if (!writeHeader(header)) {
          return false;
        }
      }
      for (int row = 0; row < buffer.detailCount; row++) {
        int offset = row * 4;
        if (!writeDetail(
            buffer.details[offset],
            buffer.details[offset + 1],
            buffer.details[offset + 2],
            buffer.details[offset + 3])) {
          return false;
        }
      }
      return true;
    } catch (SQLException e) {
      fail(e);
      return false;
    } finally {
      computingTimes.add(AssignmentComputingTimes.Phase.DATABASE, started);
    }
  }

  /** Binds a snapshot while holding the writer lock; batching remains shared across workers. */
  private boolean writeHeader(PathWriterBuffer.Header header) throws SQLException {
    header.bind(prepStmtHeaders);
    if (hasBatchSupport) {
      prepStmtHeaders.addBatch();
      return executeHeaderBatch(false);
    }
    prepStmtHeaders.executeUpdate();
    return true;
  }

  /** Appends one already resolved link, preserving its signed ID and mode/means columns. */
  private boolean writeDetail(int pathIndex, int link, int mode, int means) throws SQLException {
    prepStmtDetails.setInt(1, pathIndex);
    prepStmtDetails.setInt(2, link);
    prepStmtDetails.setInt(3, mode);
    prepStmtDetails.setInt(4, means);
    if (hasBatchSupport) {
      prepStmtDetails.addBatch();
      return executeDetailsBatch(false);
    }
    prepStmtDetails.executeUpdate();
    return true;
  }

  /** Accepts SUCCESS_NO_INFO, but treats an explicit failed row as a failed assignment write. */
  private static void checkBatchResult(int[] counts) throws SQLException {
    for (int count : counts) {
      if (count == Statement.EXECUTE_FAILED) {
        throw new SQLException("A path batch row could not be saved.");
      }
    }
  }

  /**
   * Stops further writes, including buffered rows submitted by other workers after this failure.
   */
  synchronized void fail(Exception error) {
    if (canceled || closed) {
      return;
    }
    canceled = true;
    showWriteError(error);
  }

  /** Reports a failed row or batch; kept separate from persistence for headless checks. */
  void showWriteError(Exception error) {
    nodusProject.getNodusMapPanel().stopProgress();
    SingleInstanceMessagePane.display(
        nodusProject.getNodusMapPanel(), error.getMessage(), JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Balance the volume between the previous saved detailed paths and those saved during the current
   * iteration. This is used in equilibrium assignment algorithms.
   *
   * @param iteration The iteration of the assignment.
   * @param lambda The balance factor : (1-lambda) * previous volume + lambda * current volume.
   */
  public synchronized void splitPaths(int iteration, double lambda) {
    long started = computingTimes.start();
    try {
      updatePathQuantities(iteration, lambda);
    } finally {
      computingTimes.add(AssignmentComputingTimes.Phase.DATABASE, started);
    }
  }

  /** Updates equilibrium path quantities, including pending batches and the commit. */
  private void updatePathQuantities(int iteration, double lambda) {
    if (closed || canceled || iteration <= 1) {
      return;
    }

    // Be sure header table is updated
    if (hasBatchSupport) {
      if (!executeHeaderBatch(true)) {
        return;
      }
    }

    try (Statement stmt = con.createStatement()) {

      /*
       * Update records relative to previous iterations Example: UPDATE HeaderTable set QTY =
       * ROUND(QTY*(1-lambda),3) where iteration < it
       */
      String sqlStmt =
          "UPDATE "
              + JDBCUtils.getQuotedCompliantIdentifier(pathHeaderTableName)
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
      stmt.executeUpdate(sqlStmt);

      /*
       * Update records relative to current iterations Example: UPDATE HeaderTable set QTY =
       * ROUND(QTY*lambda,3) where iteration = it
       */
      sqlStmt =
          "UPDATE "
              + JDBCUtils.getQuotedCompliantIdentifier(pathHeaderTableName)
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
      stmt.executeUpdate(sqlStmt);

      commitIfNeeded();
    } catch (Exception e) {
      System.err.println(e.toString());
    }
  }
}
