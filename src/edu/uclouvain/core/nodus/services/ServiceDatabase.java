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

package edu.uclouvain.core.nodus.services;

import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Reads and writes the three tables that describe transport services: headers, ordered route links
 * and stop nodes.
 *
 * <p>Loading uses one query per table instead of querying links and stops separately for every
 * service. Saving reuses three prepared statements and groups their rows into bounded JDBC batches
 * when supported by the driver.
 *
 * <p>This helper owns only its statements and result sets. The caller supplies the connection,
 * prepares the tables, and handles commits and rollbacks. Failures propagate to the caller after
 * JDBC resources are closed; this class does not close the connection or change auto-commit mode.
 * Graphic lookups are supplied by the caller so persistence does not depend on map-layer access.
 */
final class ServiceDatabase {
  private final Connection connection;
  private final String headerTable;
  private final String linksTable;
  private final String stopsTable;

  /**
   * Binds persistence to the project's existing service tables.
   *
   * @param connection The project connection, whose metadata must already be configured in {@link
   *     JDBCUtils} for identifier quoting and case conversion.
   * @param headerTable Unquoted name of the table containing service properties.
   * @param linksTable Unquoted name of the table containing route link occurrences.
   * @param stopsTable Unquoted name of the table containing service stop nodes.
   */
  ServiceDatabase(Connection connection, String headerTable, String linksTable, String stopsTable) {
    this.connection = connection;
    this.headerTable = quote(headerTable);
    this.linksTable = quote(linksTable);
    this.stopsTable = quote(stopsTable);
  }

  /**
   * Loads service headers, then attaches their links and stops through an in-memory ID lookup.
   *
   * <p>The detail tables are read separately: joining both to the headers would multiply link rows
   * by stop rows for each service. Only headers are read when there are no services. Otherwise,
   * exactly three data queries are used, regardless of the number of services.
   *
   * @param linksHavePathIndex Whether the link table contains the route-position column. Legacy
   *     tables without it are read in the order returned by the database, without an added sort.
   * @param resolveLink Resolves a stored link ID to its map graphic; returning {@code null} skips a
   *     link that is no longer present in the network.
   * @return Fully populated services in the order returned by the header query. Nothing is returned
   *     if a read fails, so the caller can publish the services only after loading succeeds.
   * @throws SQLException If any table cannot be read.
   */
  List<TransportService> load(boolean linksHavePathIndex, IntFunction<OMGraphic> resolveLink)
      throws SQLException {
    List<TransportService> services = new ArrayList<>();
    // Keep header order separately from the lookup used to attach detail rows.
    Map<Integer, List<TransportService>> byId = new HashMap<>();
    String headersSql =
        "SELECT "
            + columns(
                NodusC.DBF_ID,
                NodusC.DBF_SERVICE_NAME,
                NodusC.DBF_MODE,
                NodusC.DBF_MEANS,
                NodusC.DBF_FREQUENCY)
            + " FROM "
            + headerTable;
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(headersSql)) {
      while (rows.next()) {
        int id = JDBCUtils.getInt(rows.getObject(1));
        TransportService service =
            new TransportService(
                id,
                rows.getString(2),
                (byte) JDBCUtils.getInt(rows.getObject(3)),
                (byte) JDBCUtils.getInt(rows.getObject(4)),
                JDBCUtils.getInt(rows.getObject(5)));
        services.add(service);
        // Legacy data can contain multiple headers with one ID. Each receives the same details.
        byId.computeIfAbsent(id, key -> new ArrayList<>(1)).add(service);
      }
    }
    if (services.isEmpty()) {
      return services;
    }

    String linksSql = "SELECT " + columns(NodusC.DBF_ID, NodusC.DBF_LINK) + " FROM " + linksTable;
    if (linksHavePathIndex) {
      // Group by service and preserve the order of occurrences within each route.
      linksSql += " ORDER BY " + columns(NodusC.DBF_ID, NodusC.DBF_PATH_INDEX);
    }
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(linksSql)) {
      while (rows.next()) {
        List<TransportService> matches = byId.get(JDBCUtils.getInt(rows.getObject(1)));
        if (matches == null) {
          // Ignore orphan details, which the former per-service queries did not retrieve.
          continue;
        }
        OMGraphic link = resolveLink.apply(JDBCUtils.getInt(rows.getObject(2)));
        if (link != null) {
          for (TransportService service : matches) {
            // A route may traverse a link more than once: append every stored occurrence.
            service.addChunk(link);
          }
        }
      }
    }

    String stopsSql = "SELECT " + columns(NodusC.DBF_ID, NodusC.DBF_STOP) + " FROM " + stopsTable;
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(stopsSql)) {
      while (rows.next()) {
        List<TransportService> matches = byId.get(JDBCUtils.getInt(rows.getObject(1)));
        if (matches != null) {
          int stop = JDBCUtils.getInt(rows.getObject(2));
          for (TransportService service : matches) {
            // addStop keeps stop membership unique even if the table contains duplicate rows.
            service.addStop(stop);
          }
        }
      }
    }
    return services;
  }

  /**
   * Inserts all supplied services into tables already prepared by the caller.
   *
   * <p>Headers are written first so every service exists before its links or stops are inserted.
   * Each table has its own batch, which can span multiple services and is flushed at the configured
   * limit. Remaining rows are flushed before returning. Drivers without batch support execute the
   * same prepared inserts individually.
   *
   * <p>This method does not delete existing rows or commit the writes. If insertion fails, earlier
   * batches may already have executed; the caller is responsible for rolling back the transaction.
   *
   * @param services Services keyed by the names to save. The registry key takes precedence over the
   *     name stored inside the service object, as required when editing or renaming services.
   * @param resolveLinkId Resolves a map graphic to its database link ID; returning {@code -1} skips
   *     that occurrence while retaining its position in the route's path-index numbering.
   * @param hasBatchSupport Whether the JDBC driver supports batch updates.
   * @param maxBatchSize Maximum number of queued rows per statement; values below one use one.
   * @throws SQLException If preparing or executing an insert fails, including a failed row reported
   *     in a batch result.
   */
  void insert(
      Map<String, TransportService> services,
      ToIntFunction<OMGraphic> resolveLinkId,
      boolean hasBatchSupport,
      int maxBatchSize)
      throws SQLException {
    String headersSql =
        "INSERT INTO "
            + headerTable
            + " ("
            + columns(
                NodusC.DBF_ID,
                NodusC.DBF_SERVICE_NAME,
                NodusC.DBF_MODE,
                NodusC.DBF_MEANS,
                NodusC.DBF_FREQUENCY)
            + ") VALUES (?,?,?,?,?)";
    String linksSql =
        "INSERT INTO "
            + linksTable
            + " ("
            + columns(NodusC.DBF_ID, NodusC.DBF_PATH_INDEX, NodusC.DBF_LINK)
            + ") VALUES (?,?,?)";
    String stopsSql =
        "INSERT INTO "
            + stopsTable
            + " ("
            + columns(NodusC.DBF_ID, NodusC.DBF_STOP)
            + ") VALUES (?,?)";
    try (PreparedStatement headers = connection.prepareStatement(headersSql);
        PreparedStatement links = connection.prepareStatement(linksSql);
        PreparedStatement stops = connection.prepareStatement(stopsSql)) {
      Batch headerBatch = new Batch(headers, hasBatchSupport, maxBatchSize);
      Batch linkBatch = new Batch(links, hasBatchSupport, maxBatchSize);
      Batch stopBatch = new Batch(stops, hasBatchSupport, maxBatchSize);

      for (Map.Entry<String, TransportService> entry : services.entrySet()) {
        TransportService service = entry.getValue();
        headers.setInt(1, service.getId());
        // The editor's registry key is authoritative, including services being renamed.
        headers.setString(2, entry.getKey());
        headers.setInt(3, service.getMode());
        headers.setInt(4, service.getMeans());
        headers.setInt(5, service.getFrequency());
        headerBatch.add();
      }
      // Flush even a partial header batch before details, also supporting foreign-key constraints.
      headerBatch.flush();

      for (TransportService service : services.values()) {
        for (Integer stop : service.getStopNodes()) {
          stops.setInt(1, service.getId());
          stops.setInt(2, stop);
          stopBatch.add();
        }
        int pathIndex = 0;
        for (OMGraphic link : service.getLinks()) {
          int id = resolveLinkId.applyAsInt(link);
          if (id != -1) {
            links.setInt(1, service.getId());
            links.setInt(2, pathIndex);
            links.setInt(3, id);
            linkBatch.add();
          }
          // Count missing graphics too, preserving the existing route-position numbering.
          pathIndex++;
        }
      }
      // The last batch for each detail table may be smaller than the configured limit.
      stopBatch.flush();
      linkBatch.flush();
    }
  }

  /** Applies the active database's identifier case and quoting rules to a table or column name. */
  private static String quote(String identifier) {
    return JDBCUtils.getQuotedCompliantIdentifier(identifier);
  }

  /** Builds a comma-separated column list, quoting each identifier independently. */
  private static String columns(String... identifiers) {
    String[] quoted = new String[identifiers.length];
    for (int i = 0; i < identifiers.length; i++) {
      quoted[i] = quote(identifiers[i]);
    }
    return String.join(",", quoted);
  }

  /**
   * Tracks queued rows for one prepared statement without buffering a second copy in Java.
   *
   * <p>The enclosing method owns the statement and must flush once more after adding the last row.
   * This class limits queued rows, not transaction size: flushing never commits the connection.
   */
  private static final class Batch {
    private final PreparedStatement statement;
    private final boolean supported;
    private final int limit;
    /** Rows queued since the last successful flush; always zero when batching is unsupported. */
    private int pending;

    /** Uses at least one row per batch, including for a nonpositive configured limit. */
    Batch(PreparedStatement statement, boolean supported, int limit) {
      this.statement = statement;
      this.supported = supported;
      this.limit = Math.max(1, limit);
    }

    /** Queues the currently bound parameters, or executes them immediately in fallback mode. */
    void add() throws SQLException {
      if (!supported) {
        statement.executeUpdate();
        return;
      }
      statement.addBatch();
      if (++pending >= limit) {
        flush();
      }
    }

    /** Executes a nonempty batch and propagates failures for the caller to handle by rollback. */
    void flush() throws SQLException {
      if (pending == 0) {
        return;
      }
      int[] counts = statement.executeBatch();
      for (int count : counts) {
        // SUCCESS_NO_INFO is also successful; only EXECUTE_FAILED indicates a failed batch row.
        if (count == Statement.EXECUTE_FAILED) {
          throw new SQLException("A service batch row could not be saved.");
        }
      }
      statement.clearBatch();
      pending = 0;
    }
  }
}
