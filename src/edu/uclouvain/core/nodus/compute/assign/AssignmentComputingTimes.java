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

  /** Computation stages measured separately inside an assignment worker. */
  public enum WorkerPhase {
    /** The complete shortest-path search, including its initialization. */
    DIJKSTRA("Dijkstra"),
    /** Exact multi-flow shortest-path search, including its initialization. */
    ASTAR("A*"),
    /** Reconstructing and loading routes in workers that perform both in one traversal. */
    RECONSTRUCTION_LOADING("Path reconstruction and volume loading"),
    /** Moving dynamic demands to their starting nodes for the next time slice. */
    DEMAND_RELOCATION("Demand relocation"),
    /** Reconstructing routes, collecting their weights and marking their edges. */
    RECONSTRUCTION("Path reconstruction"),
    /** Finding the headers belonging to each OD cell and applying its path shares. */
    HEADER_MATCHING("Header matching"),
    /** Filtering alternatives and invoking the configured modal-split method. */
    MODAL_SPLITTING("Modal splitting and path filtering"),
    /** Applying path shares to virtual-link volumes. */
    VOLUME_DISTRIBUTION("Volume distribution");

    private final String label;

    /** Associates a stage with its terminal label. */
    WorkerPhase(String label) {
      this.label = label;
    }
  }

  private final LongSupplier clock;
  private final long[] elapsed = new long[Phase.values().length];
  private final ThreadLocal<long[]> threadDatabaseTime = ThreadLocal.withInitial(() -> new long[1]);
  private final long[] workerElapsed = new long[WorkerPhase.values().length];
  private final boolean[] workerPhases = new boolean[WorkerPhase.values().length];
  private long workerPathOutputTime;
  private boolean hasWorkerDetails;
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
    for (int i = 0; i < workerElapsed.length; i++) {
      workerElapsed[i] = 0;
      workerPhases[i] = false;
    }
    workerPathOutputTime = 0;
    hasWorkerDetails = false;
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
   * Creates local counters for one assignment-worker job.
   *
   * @return Counters to close in a finally block or try-with-resources statement.
   */
  public WorkerTimes newWorkerTimes() {
    return new WorkerTimes();
  }

  /**
   * Accumulates a worker breakdown without locking the shared audit in its inner loops.
   *
   * <p>One instance belongs to one worker job. Stages must not nest: pair startPhase/endPhase in a
   * try/finally block. Path-output calls may occur within a stage; their complete elapsed time,
   * including writer-lock waits, is recorded separately and subtracted from that stage. These are
   * elapsed worker times, not CPU times. Setup, cost markups and other uninstrumented work remain
   * covered by the existing overall worker timer.
   *
   * <p>Closing merges counters once, including on failure. Disabled auditing never reads the clock.
   */
  public final class WorkerTimes implements AutoCloseable {
    private final boolean recording = enabled;
    private final long[] durations = new long[WorkerPhase.values().length];
    private final boolean[] phases = new boolean[WorkerPhase.values().length];
    private WorkerPhase currentPhase;
    private int pathOutputDepth;
    private long phaseStarted;
    private long outputBeforePhase;
    private long pathOutputTime;
    private boolean closed;

    /** Creates counters using the audit setting sampled at assignment start. */
    private WorkerTimes() {}

    /**
     * Returns whether this worker's breakdown is being recorded.
     *
     * @return True when auditing was enabled for this assignment.
     */
    public boolean isEnabled() {
      return recording;
    }

    /**
     * Includes the stages applicable to this worker, even if cancellation leaves them unstarted.
     *
     * @param applicable Stages to show in the report for this algorithm.
     */
    public void includePhases(WorkerPhase... applicable) {
      if (recording) {
        for (WorkerPhase phase : applicable) {
          phases[phase.ordinal()] = true;
        }
      }
    }

    /**
     * Starts one computation stage on the owning worker.
     *
     * @param phase Stage to measure; finish it before starting another stage.
     */
    public void startPhase(WorkerPhase phase) {
      if (recording) {
        phases[phase.ordinal()] = true;
        currentPhase = phase;
        outputBeforePhase = pathOutputTime;
        phaseStarted = clock.getAsLong();
      }
    }

    /** Finishes the current stage, excluding its path-output calls on this worker. */
    public void endPhase() {
      if (recording && currentPhase != null) {
        durations[currentPhase.ordinal()] +=
            Math.max(0, clock.getAsLong() - phaseStarted - (pathOutputTime - outputBeforePhase));
        currentPhase = null;
      }
    }

    /**
     * Starts a complete path-output call, including preparation, JDBC work and writer-lock waits.
     * Nested calls (such as automatic flushes) are included once in the outer call.
     *
     * @return Start timestamp, or zero when auditing is disabled or the call is nested.
     */
    public long startPathOutput() {
      return recording && pathOutputDepth++ == 0 ? clock.getAsLong() : 0;
    }

    /**
     * Finishes a path-output call without altering the existing database timing counters.
     *
     * @param started Timestamp from startPathOutput().
     */
    public void endPathOutput(long started) {
      if (recording && --pathOutputDepth == 0) {
        pathOutputTime += clock.getAsLong() - started;
      }
    }

    /** Merges this worker's completed stages once, including a stage interrupted by failure. */
    @Override
    public void close() {
      if (!recording || closed) {
        return;
      }
      endPhase();
      closed = true;
      synchronized (AssignmentComputingTimes.this) {
        if (enabled) {
          for (int i = 0; i < durations.length; i++) {
            workerElapsed[i] += durations[i];
            workerPhases[i] |= phases[i];
          }
          workerPathOutputTime += pathOutputTime;
          hasWorkerDetails = true;
        }
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
    if (hasWorkerDetails) {
      report.append("  Assignment breakdown (worker elapsed sums, not CPU time):\n");
      for (WorkerPhase phase : WorkerPhase.values()) {
        if (workerPhases[phase.ordinal()]) {
          appendTime(report, "  " + phase.label, workerElapsed[phase.ordinal()]);
        }
      }
      appendTime(
          report, "  Path output (includes DB calls and writer waits)", workerPathOutputTime);
      report.append("  Computation stages exclude path-output calls.\n");
      report.append("  Setup, cost-markup updates and coordinator work are not itemized.\n");
    }
    report.append("  Parallel work and path writes overlap; these rows are not additive.\n");
    System.out.print(report);
    threadDatabaseTime.remove();
  }

  private static void appendTime(StringBuilder report, String label, long nanos) {
    report.append(String.format(Locale.ROOT, "  %-58s %10.3f s%n", label + ":", nanos / 1.0e9));
  }
}
