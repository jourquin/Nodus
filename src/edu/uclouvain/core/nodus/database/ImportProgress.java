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

package edu.uclouvain.core.nodus.database;

import java.sql.SQLException;
import java.util.function.Consumer;

/** Per-import progress and cooperative cancellation, independent of Swing or the JDBC driver. */
public final class ImportProgress {
  private final Consumer<ImportProgress> listener;
  private final long started = System.nanoTime();
  private long notified;
  private volatile long records;
  private volatile long scanned;
  private volatile String phase = "Reading";
  private volatile boolean cancelled;
  private boolean finishing;

  /** Creates a monitor without progress notifications. */
  public ImportProgress() {
    this(null);
  }

  /**
   * Creates a monitor with throttled updates and immediate notifications when phases change.
   *
   * @param listener importing-thread callback, or null for no notifications
   */
  public ImportProgress(Consumer<ImportProgress> listener) {
    this.listener = listener;
  }

  /**
   * Requests cancellation until publication starts; never interrupts rollback or JDBC cleanup.
   *
   * @return whether the request was accepted before publication
   */
  public synchronized boolean cancel() {
    if (finishing) {
      return false;
    }
    cancelled = true;
    return true;
  }

  /**
   * Throws before more input or database work when cancellation has been requested.
   *
   * @throws SQLException when cancellation or thread interruption is pending
   */
  public void checkCancelled() throws SQLException {
    if (cancelled || Thread.currentThread().isInterrupted()) {
      throw new SQLException("Import cancelled.", "57014");
    }
  }

  /**
   * Changes the phase and immediately notifies the listener.
   *
   * @param value current import phase
   * @throws SQLException if cancellation is pending
   */
  public void phase(String value) throws SQLException {
    checkCancelled();
    phase = value;
    scanned = 0;
    notifyProgress(true);
    checkCancelled();
  }

  /**
   * Counts a source row inspected during schema inference.
   *
   * @throws SQLException if cancellation is pending
   */
  public void scanned() throws SQLException {
    checkCancelled();
    scanned++;
    notifyProgress(false);
    checkCancelled();
  }

  /**
   * Counts a data record submitted for insertion, excluding headers and deleted DBF records.
   *
   * @throws SQLException if cancellation is pending
   */
  public void loaded() throws SQLException {
    checkCancelled();
    records++;
    notifyProgress(false);
    checkCancelled();
  }

  /**
   * Stops accepting cancellation just before committing or publishing the completed import.
   *
   * @throws SQLException if cancellation was requested before publication
   */
  public void finishing() throws SQLException {
    synchronized (this) {
      checkCancelled();
      finishing = true;
      phase = "Finishing";
    }
    notifyProgress(true);
  }

  /**
   * Returns the current phase.
   *
   * @return phase displayed by progress consumers
   */
  public String getPhase() {
    return phase;
  }

  /**
   * Returns the number of submitted data records, which are not committed until success.
   *
   * @return record count excluding schema/header rows and deleted records
   */
  public long getRecords() {
    return records;
  }

  /**
   * Returns the number of rows inspected in the current inference phase.
   *
   * @return rows inspected during the current phase
   */
  public long getScanned() {
    return scanned;
  }

  /**
   * Returns elapsed seconds since this import started.
   *
   * @return monotonic elapsed time in seconds
   */
  public double getElapsedSeconds() {
    return (System.nanoTime() - started) / 1_000_000_000.0;
  }

  private void notifyProgress(boolean force) {
    long now = System.nanoTime();
    if (listener != null && (force || now - notified >= 1_000_000_000L)) {
      notified = now;
      listener.accept(this);
    }
  }
}
