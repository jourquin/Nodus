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

package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusC;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Optional elapsed-time audit for one assignment run, including all iterations and time slices.
 *
 * <p>Network and cost phases are timed on the coordinating thread. Path wall time is the union of
 * active assignment-worker intervals, rather than the sum of parallel jobs. Database time covers
 * writer calls, including row preparation, batches, indexes and commits. It sums preparation time
 * across workers, which can now overlap, and overlaps path wall time when workers save paths.
 * Worker time excluding database calls is also reported; it includes time waiting for the writer
 * lock and is elapsed worker time, not CPU time.
 */
public final class AssignmentComputingTimes {

  /** The sequential phases and database writer calls recorded by this audit. */
  public enum Phase {
    /** Network initialization and generation. */
    NETWORK,
    /** Cost and duration evaluation passes, including line searches. */
    COSTS,
    /** Database output, including preparation and finalization. */
    DATABASE
  }

  private final LongSupplier clock;
  private final long[] elapsed = new long[Phase.values().length];
  private final ThreadLocal<long[]> threadDatabaseTime = ThreadLocal.withInitial(() -> new long[1]);
  private volatile boolean enabled;
  private long assignmentStarted;
  private long pathsStarted;
  private long pathsWallTime;
  private long pathsWorkerTime;
  private int activePathWorkers;

  /** Creates an audit using the monotonic system clock. */
  public AssignmentComputingTimes() {
    this(System::nanoTime);
  }

  /** Allows deterministic timing checks without sleeping. */
  AssignmentComputingTimes(LongSupplier clock) {
    this.clock = clock;
  }

  /** Resets the audit and samples the global switch once for this run. */
  public synchronized void startAssignment() {
    enabled = NodusC.displayComputingTimes;
    for (int i = 0; i < elapsed.length; i++) {
      elapsed[i] = 0;
    }
    pathsWallTime = 0;
    pathsWorkerTime = 0;
    activePathWorkers = 0;
    threadDatabaseTime.remove();
    assignmentStarted = start();
  }

  /**
   * Starts a measurement without reading the clock when auditing is disabled.
   *
   * @return The start timestamp, or zero when disabled.
   */
  public long start() {
    return enabled ? clock.getAsLong() : 0;
  }

  /**
   * Accumulates a completed measurement. Call in a finally block to include failed work.
   *
   * @param phase The measured phase.
   * @param started The timestamp returned by start().
   */
  public void add(Phase phase, long started) {
    if (!enabled) {
      return;
    }
    long duration = clock.getAsLong() - started;
    if (phase == Phase.DATABASE) {
      threadDatabaseTime.get()[0] += duration;
    }
    synchronized (this) {
      elapsed[phase.ordinal()] += duration;
    }
  }

  /**
   * Returns this thread's accumulated database writer time.
   *
   * @return Elapsed nanoseconds spent in writer calls on the current thread.
   */
  public long getThreadDatabaseTime() {
    return enabled ? threadDatabaseTime.get()[0] : 0;
  }

  /**
   * Starts an assignment-worker job, merging overlapping jobs for the wall-time measurement.
   *
   * @return The job's start timestamp.
   */
  public long startPaths() {
    if (!enabled) {
      return 0;
    }
    synchronized (this) {
      long started = clock.getAsLong();
      if (activePathWorkers++ == 0) {
        pathsStarted = started;
      }
      return started;
    }
  }

  /**
   * Finishes a worker job, excluding its own database writer calls from summed worker time.
   *
   * @param started The timestamp returned by startPaths().
   * @param databaseBefore The thread's database time before the job.
   */
  public void endPaths(long started, long databaseBefore) {
    if (!enabled) {
      return;
    }
    long databaseTime = getThreadDatabaseTime() - databaseBefore;
    synchronized (this) {
      long finished = clock.getAsLong();
      pathsWorkerTime += Math.max(0, finished - started - databaseTime);
      if (--activePathWorkers == 0) {
        pathsWallTime += finished - pathsStarted;
      }
    }
  }

  /**
   * Prints one summary and stops measuring, before post-assignment scripts and completion dialogs.
   * Failed runs contain measurements up to the failure, not subsequent error handling.
   *
   * @param assignment The assignment implementation's name.
   * @param scenario The scenario ID.
   * @param threads The configured worker count.
   * @param success True if computation and saving completed successfully.
   */
  public synchronized void finishAndPrint(
      String assignment, int scenario, int threads, boolean success) {
    if (!enabled) {
      return;
    }
    long finished = clock.getAsLong();
    enabled = false;
    if (activePathWorkers > 0) {
      pathsWallTime += finished - pathsStarted;
    }
    StringBuilder report = new StringBuilder();
    report.append(
        String.format(
            Locale.ROOT,
            "%nComputing times: %s (scenario %d, %d threads, %s)%n",
            assignment,
            scenario,
            threads,
            success ? "completed" : "failed/cancelled"));
    appendTime(report, "Total elapsed", finished - assignmentStarted);
    appendTime(report, "Virtual network generation (wall)", elapsed[Phase.NETWORK.ordinal()]);
    appendTime(report, "Cost parser (wall)", elapsed[Phase.COSTS.ordinal()]);
    appendTime(report, "Paths and flow assignment (wall, includes path writes)", pathsWallTime);
    appendTime(
        report, "Paths and flow assignment (worker sum, excludes DB calls)", pathsWorkerTime);
    appendTime(report, "Database writing (writer calls)", elapsed[Phase.DATABASE.ordinal()]);
    report.append("  Parallel work and path writes overlap; these rows are not additive.\n");
    System.out.print(report);
    threadDatabaseTime.remove();
  }

  private static void appendTime(StringBuilder report, String label, long nanos) {
    report.append(String.format(Locale.ROOT, "  %-58s %10.3f s%n", label + ":", nanos / 1.0e9));
  }
}
