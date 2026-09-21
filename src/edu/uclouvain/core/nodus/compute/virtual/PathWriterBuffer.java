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

import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerTimes;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepares path rows on one assignment worker before handing a bounded block to the shared writer.
 *
 * <p>Obtain a buffer from {@link PathWriter#newBuffer()} for each worker job. It belongs
 * exclusively to that worker and is not thread-safe. Different workers can resolve link attributes
 * and format header values concurrently. The buffer stores numeric snapshots, never references to
 * mutable demands, weights or network links; subsequent input changes cannot alter queued rows.
 *
 * <p>Headers and link details share one row limit, capped at 1,000 to bound memory per worker.
 * Adding the row that reaches this limit triggers a synchronous handoff to the writer. The writer
 * locks once for the whole block, binds its headers and details to their separate prepared
 * statements, and applies the existing JDBC batch limit. A worker block can span several paths, and
 * a long path can span several blocks. Repeated link occurrences and the order of detail rows are
 * retained.
 *
 * <p>Successful jobs must call {@link #flush()} before completion so their final partial block is
 * available to the coordinator before an equilibrium split, a new time slice or finalization.
 * Failed or cancelled jobs call {@link #clear()} and abandon the buffer. Clearing cannot undo rows
 * already handed to JDBC; {@link PathWriter} owns database finalization and discarding output.
 *
 * <p>Automatic path IDs stay attached to one worker's path until its header is accepted. Multi-flow
 * and dynamic assignments instead supply their own explicit IDs, which this buffer copies as-is.
 * Row preparation is recorded as database time on the worker; JDBC time is recorded separately by
 * the shared writer, excluding the wait to acquire its lock. When a worker breakdown is supplied,
 * header/detail preparation and flushes also count as path-output time, including lock waits.
 * Nested automatic flushes count once; this time is excluded from the enclosing computation stage.
 */
public final class PathWriterBuffer {
  /** Shared owner of the JDBC connection, statements, path IDs and output failure state. */
  private final PathWriter writer;

  /** The assignment's audit, also used by the writer for the actual JDBC work. */
  private final AssignmentComputingTimes computingTimes;

  /** Optional worker-local output timer; null avoids clock reads when detailed auditing is off. */
  private final WorkerTimes workerTimes;

  /**
   * Whether headers are enabled; false makes all path output a no-op while the writer is active.
   */
  private final boolean savePaths;

  /** Whether moving-link rows are enabled in addition to headers. */
  private final boolean saveDetails;

  /** Selects fractional durations or the legacy whole-number moving-duration convention. */
  private final boolean hasDurationFunctions;

  /** Maximum combined number of header and detail rows waiting for a handoff. */
  private final int limit;

  /** Lazily created, worker-local formatter; DecimalFormat must not be shared across workers. */
  private DecimalFormat format;

  /** Prepared headers, exposed to PathWriter only for the synchronous handoff on this worker. */
  final List<Header> headers = new ArrayList<>();

  /**
   * Flat detail-row storage: each four-int group contains path ID, signed real-link ID, mode and
   * means. Allocated on the first saved link and reused after flushing to avoid a per-link object.
   */
  int[] details;

  /** Number of populated four-int rows in details; unused array entries are ignored. */
  int detailCount;

  /**
   * ID of this worker's unfinished implicit-index path, or -1 if none is reserved. It survives a
   * flush partway through a path, preventing other workers' headers from changing the links' owner.
   */
  private int currentPathIndex = -1;

  /**
   * Captures the writer's output settings for one worker job; no JDBC work is done here.
   *
   * @param writer The shared writer receiving completed blocks.
   * @param computingTimes The assignment audit used to attribute local row preparation.
   * @param savePaths Whether path headers are enabled.
   * @param saveDetails Whether link details are enabled; the writer sets this false if saving paths
   *     is disabled.
   * @param hasDurationFunctions Whether to retain fractional moving durations.
   * @param maxBatchSize The configured JDBC batch limit; the local block limit is clamped to
   *     1–1,000 without changing the writer's own JDBC batch limit.
   * @param workerTimes Worker-local stage counters, or null when no detailed audit is needed.
   */
  PathWriterBuffer(
      PathWriter writer,
      AssignmentComputingTimes computingTimes,
      boolean savePaths,
      boolean saveDetails,
      boolean hasDurationFunctions,
      int maxBatchSize,
      WorkerTimes workerTimes) {
    this.writer = writer;
    this.computingTimes = computingTimes;
    this.workerTimes = workerTimes != null && workerTimes.isEnabled() ? workerTimes : null;
    this.savePaths = savePaths;
    this.saveDetails = saveDetails;
    this.hasDurationFunctions = hasDurationFunctions;
    // Bound worker memory even when the JDBC batch limit is configured unusually high.
    limit = Math.max(1, Math.min(maxBatchSize, 1000));
  }

  /**
   * Returns the assignment's output setting, independently of the writer's failure/closed state.
   *
   * @return True if path headers are enabled.
   */
  public boolean isSavePaths() {
    return savePaths;
  }

  /**
   * Reserves an implicit path ID on first use, without acquiring the JDBC writer lock. Header-only
   * paths reserve it when saving the header; detailed paths reserve it when saving their first
   * link.
   */
  private int pathIndex() {
    if (currentPathIndex == -1) {
      currentPathIndex = writer.reservePathIndex();
    }
    return currentPathIndex;
  }

  /**
   * Adds a moving link to the path completed by the next implicit-index header.
   *
   * <p>Disabled details or a stopped writer cause an immediate return, without reserving an ID or
   * inspecting the link. Otherwise, all links up to that header share this worker's reserved ID.
   *
   * @param link The moving virtual link whose direction, real-link ID, mode and means are saved.
   */
  public void savePathLink(VirtualLink link) {
    if (saveDetails && writer.isAcceptingRows()) {
      savePathLink(link, pathIndex());
    }
  }

  /**
   * Snapshots a moving link with an explicit multi-flow or dynamic-assignment path ID.
   *
   * <p>Every call appends an occurrence, even if that link already appears in the path. A full
   * block is handed to the writer before returning. A database write failure stops the shared
   * writer and is reported by a subsequent header save or {@link #flush()}.
   *
   * @param link The moving virtual link to resolve now; the buffer does not retain it.
   * @param pathIndex The caller-assigned path ID, copied without changing the implicit path ID.
   */
  public void savePathLink(VirtualLink link, int pathIndex) {
    if (!saveDetails || !writer.isAcceptingRows()) {
      return;
    }
    long outputStarted = workerTimes == null ? 0 : workerTimes.startPathOutput();
    try {
      long started = computingTimes.start();
      try {
        if (details == null) {
          details = new int[limit * 4];
        }
        VirtualNode begin = link.getBeginVirtualNode();
        // Match the existing database convention: negative link IDs denote decreasing real-node
        // IDs.
        int direction =
            begin.getRealNodeId(false) > link.getEndVirtualNode().getRealNodeId(false) ? -1 : 1;
        int offset = detailCount * 4;
        details[offset] = pathIndex;
        details[offset + 1] = direction * begin.getRealLinkId();
        details[offset + 2] = begin.getMode();
        details[offset + 3] = begin.getMeans();
        detailCount++;
      } finally {
        computingTimes.add(AssignmentComputingTimes.Phase.DATABASE, started);
      }
      // End the preparation measurement before the writer starts its separate JDBC measurement.
      flushIfFull();
    } finally {
      if (workerTimes != null) {
        workerTimes.endPathOutput(outputStarted);
      }
    }
  }

  /**
   * Completes this worker's current implicit-index path by queuing its header.
   *
   * <p>The header receives the same ID as preceding implicit-index links. Once accepted, it
   * releases that ID locally so the next path gets a new one. Acceptance can mean only that the
   * header is buffered; it does not imply that JDBC has executed or committed it.
   *
   * @param iteration The assignment iteration associated with this path.
   * @param demand The demand supplying group, origin, destination and departure time.
   * @param quantity The assigned quantity, which may differ from the demand's total quantity.
   * @param weights Path length, costs and durations; legacy mode rounds its moving duration in
   *     place.
   * @param ldMode The mode used at the origin.
   * @param ldMeans The means used at the origin.
   * @param ulMode The mode used at the destination.
   * @param ulMeans The means used at the destination.
   * @param nbTranshipments The number of transhipment operations along the path.
   * @return True if accepted or saving is disabled; false if a failure or stopped writer is
   *     observed.
   */
  public boolean savePathHeader(
      int iteration,
      ODCell demand,
      double quantity,
      PathWeights weights,
      byte ldMode,
      byte ldMeans,
      byte ulMode,
      byte ulMeans,
      int nbTranshipments) {
    if (!savePaths || !writer.isAcceptingRows()) {
      return writer.isAcceptingRows();
    }
    boolean saved =
        savePathHeader(
            iteration,
            demand,
            quantity,
            weights,
            ldMode,
            ldMeans,
            ulMode,
            ulMeans,
            nbTranshipments,
            pathIndex());
    if (saved) {
      currentPathIndex = -1;
    }
    return saved;
  }

  /**
   * Snapshots a header with an explicit path ID, leaving the implicit path state untouched.
   *
   * <p>All formatting and numeric conversion happen here, before entering the shared writer lock.
   * Invalid header values mark the writer as failed, so other workers stop submitting output too.
   * The header may remain in memory until the block fills or the worker calls {@link #flush()}.
   *
   * @param iteration The assignment iteration associated with this path.
   * @param demand The demand supplying group, origin, destination and departure time.
   * @param quantity The quantity assigned to this path, rather than necessarily the whole demand.
   * @param weights Path length, costs and durations; legacy mode rounds its moving duration in
   *     place.
   * @param ldMode The mode used at the origin.
   * @param ldMeans The means used at the origin.
   * @param ulMode The mode used at the destination.
   * @param ulMeans The means used at the destination.
   * @param nbTranshipments The number of transhipment operations along the path.
   * @param pathIndex The caller's path ID; repeated IDs are allowed, for example across time
   *     slices.
   * @return True if accepted or saving is disabled; false if preparation or writing fails, or the
   *     writer has stopped. A successful return does not imply a database commit.
   */
  public boolean savePathHeader(
      int iteration,
      ODCell demand,
      double quantity,
      PathWeights weights,
      byte ldMode,
      byte ldMeans,
      byte ulMode,
      byte ulMeans,
      int nbTranshipments,
      int pathIndex) {
    if (!savePaths || !writer.isAcceptingRows()) {
      return writer.isAcceptingRows();
    }
    long outputStarted = workerTimes == null ? 0 : workerTimes.startPathOutput();
    try {
      long started = computingTimes.start();
      try {
        if (format == null) {
          format = newFormat();
        }
        headers.add(
            Header.prepare(
                format,
                hasDurationFunctions,
                iteration,
                demand,
                quantity,
                weights,
                ldMode,
                ldMeans,
                ulMode,
                ulMeans,
                nbTranshipments,
                pathIndex));
      } catch (RuntimeException e) {
        writer.fail(e);
        return false;
      } finally {
        computingTimes.add(AssignmentComputingTimes.Phase.DATABASE, started);
      }
      // JDBC work is timed separately, so a full block does not double-count its flush time.
      return flushIfFull();
    } finally {
      if (workerTimes != null) {
        workerTimes.endPathOutput(outputStarted);
      }
    }
  }

  /** Checks the combined row count: the limit is per block, not separately per table or path. */
  private boolean flushIfFull() {
    return headers.size() + detailCount < limit || flush();
  }

  /**
   * Hands all queued rows to JDBC synchronously under one writer lock.
   *
   * <p>This drains the worker buffer into the writer's prepared statements. JDBC batches execute
   * when their own limit is reached; partial header batches also execute before an equilibrium
   * split, and final close executes all remaining batches. This method does not commit.
   *
   * <p>Local snapshots are cleared whether the handoff succeeds or fails, so this buffer must not
   * be retried after a failed write. Previously submitted rows remain the writer's responsibility.
   * An empty buffer simply checks the writer's state without taking its JDBC lock.
   *
   * @return True if the rows were accepted and the writer has not failed or closed.
   */
  public boolean flush() {
    if (headers.isEmpty() && detailCount == 0) {
      return writer.isAcceptingRows();
    }
    long outputStarted = workerTimes == null ? 0 : workerTimes.startPathOutput();
    try {
      return writer.writeBuffer(this);
    } finally {
      clear();
      if (workerTimes != null) {
        workerTimes.endPathOutput(outputStarted);
      }
    }
  }

  /**
   * Discards queued snapshots without writing them; already submitted JDBC rows are unaffected.
   *
   * <p>The detail array, list capacity and formatter remain reusable. The implicit path ID also
   * remains reserved because a normal flush can occur midway through a path. Only an accepted
   * implicit-index header releases it; a failed job abandons this buffer entirely.
   */
  public void clear() {
    headers.clear();
    detailCount = 0;
  }

  /**
   * Creates the existing three-decimal formatter with its default half-even rounding and a decimal
   * point suitable for Float/Double parsing. Each worker creates its own instance on first use.
   * Keeping the format-then-parse conversion preserves the previous handling of rounding
   * boundaries.
   */
  static DecimalFormat newFormat() {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols();
    symbols.setDecimalSeparator('.');
    return new DecimalFormat("0.000", symbols);
  }

  /**
   * Numeric snapshot of the 27 existing header columns, ready to bind without further formatting.
   *
   * <p>The SQL layout interleaves two numeric types: five integer fields, sixteen fractional
   * fields, then six integer fields. Separate primitive arrays avoid boxing while retaining the
   * existing column order and JDBC setter types. Both arrays are created during preparation and
   * kept private.
   */
  static final class Header {
    /**
     * Group, origin, destination, departure minute, iteration, then modes/means, transfers and ID.
     */
    private final int[] identifiers;

    /** Quantity, length, seven costs and seven durations; length retains float precision. */
    private final double[] values;

    /** Takes ownership of the freshly prepared arrays, which are never subsequently modified. */
    private Header(int[] identifiers, double[] values) {
      this.identifiers = identifiers;
      this.values = values;
    }

    /**
     * Copies all header inputs and applies the legacy numeric conversions on the calling thread.
     * Departure time is converted from seconds to minutes; quantity, length, costs and durations
     * are formatted to three decimals. Length is parsed as a float and the other values as doubles.
     *
     * <p>Without duration functions, moving duration is first rounded to a whole number, also
     * updating the supplied weights as the original writer did. NaN quantities are rejected
     * explicitly; other values that cannot survive the format/parse conversion propagate an error
     * to the caller. No JDBC statement is touched while preparing the snapshot.
     */
    static Header prepare(
        DecimalFormat format,
        boolean hasDurationFunctions,
        int iteration,
        ODCell demand,
        double quantity,
        PathWeights weights,
        byte ldMode,
        byte ldMeans,
        byte ulMode,
        byte ulMeans,
        int nbTranshipments,
        int pathIndex) {
      // Preserve the pre-7.3 duration convention, including the update to the caller's weights.
      if (!hasDurationFunctions) {
        weights.mvDuration = Math.round(weights.mvDuration);
      }
      if (Double.isNaN(quantity)) {
        throw new IllegalArgumentException("Quantity is NaN !");
      }
      int[] identifiers = {
        demand.getGroup(),
        demand.getOriginNodeId(),
        demand.getDestinationNodeId(),
        demand.getStartingTime() / 60,
        iteration,
        ldMode,
        ldMeans,
        ulMode,
        ulMeans,
        nbTranshipments,
        pathIndex
      };
      double[] values = {
        quantity,
        weights.length,
        weights.ldCost,
        weights.ulCost,
        weights.trCost,
        weights.tpCost,
        weights.stpCost,
        weights.swCost,
        weights.mvCost,
        weights.ldDuration,
        weights.ulDuration,
        weights.trDuration,
        weights.tpDuration,
        weights.stpDuration,
        weights.swDuration,
        weights.mvDuration
      };
      for (int i = 0; i < values.length; i++) {
        String rounded = format.format(values[i]);
        // A float length is widened for storage here, then bound with setFloat in bind().
        values[i] = i == 1 ? Float.parseFloat(rounded) : Double.parseDouble(rounded);
      }
      return new Header(identifiers, values);
    }

    /**
     * Binds the snapshot to the existing header schema; the caller must hold the writer lock.
     * Binding neither queues a JDBC batch nor executes it: PathWriter handles those steps.
     *
     * @param statement The shared prepared statement for the 27-column path-header table.
     * @throws SQLException If a JDBC parameter cannot be bound.
     */
    void bind(PreparedStatement statement) throws SQLException {
      int column = 1;
      // Columns 1–5: demand identifiers, departure minute and assignment iteration.
      for (int i = 0; i < 5; i++) {
        statement.setInt(column++, identifiers[i]);
      }
      // Columns 6–21: quantity, float length, costs and durations.
      for (int i = 0; i < values.length; i++) {
        if (i == 1) {
          statement.setFloat(column++, (float) values[i]);
        } else {
          statement.setDouble(column++, values[i]);
        }
      }
      // Columns 22–27: loading/unloading mode and means, transhipment count and path ID.
      for (int i = 5; i < identifiers.length; i++) {
        statement.setInt(column++, identifiers[i]);
      }
    }
  }
}
