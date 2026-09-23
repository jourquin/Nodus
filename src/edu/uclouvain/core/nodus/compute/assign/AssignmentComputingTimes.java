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
import java.util.EnumSet;
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
 *
 * <p>The outside-phase breakdown partitions the complement of assignment-worker activity. Only
 * scopes opened on the thread that started the audit are classified. Nested scopes are exclusive,
 * and job starts/ends suspend/resume outside attribution even while a coordinator scope stays open.
 * Unclassified intervals remain visible as preparation, coordination or finalization. The union of
 * path intervals plus the outside categories therefore accounts for the complete audited run.
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

  /** Mutually exclusive wall-time categories when no assignment worker job is active. */
  public enum OutsidePhase {
    /** Virtual-network construction and generation. */
    NETWORK("Network initialization and generation"),
    /** Cost-parser passes, including their own cost-worker threads and line searches. */
    COSTS("Cost parsing and evaluation"),
    /** OD table validation, row counting, reading and preparation of demand. */
    DEMAND("Demand loading and preparation"),
    /** Global modal-split initialization, including model parameter loading. */
    MODAL_SETUP("Modal-split initialization"),
    /** Creation or removal of path-output tables and writer initialization. */
    PATH_SETUP("Path-output initialization"),
    /** Conversion of assigned volumes to vehicles and passenger-car units. */
    VEHICLES("Volume-to-vehicle conversion"),
    /** Equilibrium volume blending and convergence checks outside worker jobs. */
    VOLUME_UPDATES("Volume blending and convergence checks"),
    /** Virtual-network table output, excluding explicitly timed database commits. */
    NETWORK_OUTPUT("Virtual-network database output"),
    /** Pending path-header/detail batches executed on the coordinator. */
    PATH_BATCHES("Path database batches"),
    /** Equilibrium path-quantity updates, excluding batches and explicit commits. */
    PATH_UPDATES("Path database quantity updates"),
    /** Path-table index creation after assignment. */
    PATH_INDEXES("Path database index creation"),
    /** Explicit commits in the path and virtual-network writers. */
    DATABASE_COMMIT("Database commits"),
    /** Path-writer finalization excluding its batches, indexes and commits. */
    PATH_FINALIZATION("Other path-output finalization"),
    /** Uninstrumented work before the first assignment worker job starts. */
    PREPARATION("Other assignment preparation"),
    /** Uninstrumented work between worker intervals, including scheduling and waits. */
    COORDINATION("Other coordination between worker jobs"),
    /** Uninstrumented work after assign() returns, up to the existing audit cutoff. */
    FINALIZATION("Other finalization");

    private final String label;

    /** Associates an exclusive outside phase with its terminal label. */
    OutsidePhase(String label) {
      this.label = label;
    }
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

  /** Counters for completed Fast multi-flow searches observed without changing their behavior. */
  public enum ReachabilityMetric {
    /** Every completed search, including first alternatives. */
    SEARCHES("Completed Dijkstra searches"),
    /** Searches which exhausted their finite frontier with requested destinations still missing. */
    EXHAUSTED_SEARCHES("Searches ending with unreachable destinations"),
    /** Searches requesting at least one destination proven unreachable earlier in this sequence. */
    KNOWN_UNREACHABLE_SEARCHES("Searches with previously known unreachable destinations"),
    /** Searches reaching a later stopping point after settling all destinations still in doubt. */
    SHORTENABLE_SEARCHES("Potentially shortenable searches"),
    /** Searches whose entire destination set was already proven unreachable. */
    SKIPPABLE_SEARCHES("Potentially entirely skippable searches"),
    /** Searches whose cost conditions do not justify reusing unreachable destinations. */
    EXCLUDED_SEARCHES("Searches excluded from reuse estimates"),
    /** Finite-cost nodes extracted, including the last requested destination. */
    NODES("Nodes settled"),
    /** Calls to relax an edge, including edges blocked by infinite costs. */
    EDGES("Edges examined"),
    /** Finite-cost nodes extracted after the hypothetical stopping point. */
    AVOIDABLE_NODES("Potentially avoidable nodes"),
    /** Edges examined after the hypothetical stopping point. */
    AVOIDABLE_EDGES("Potentially avoidable edges"),
    /** Elapsed worker nanoseconds inside the observed compute calls. */
    SEARCH_TIME("Observed Dijkstra time (worker sum)"),
    /** Elapsed worker nanoseconds after the hypothetical stopping point, including whole skips. */
    AVOIDABLE_TIME("Potentially avoidable Dijkstra time (worker sum)"),
    /** Completed searches containing both reached and unreached requested destinations. */
    MIXED_DESTINATION_SEARCHES("Searches with mixed reachable/unreachable destinations"),
    /** Nonempty destination sets for which no requested destination was reached. */
    NO_REACHABLE_DESTINATION_SEARCHES("Searches with no reachable destination"),
    /** Nodes settled after the final reached destination in mixed-destination searches. */
    TAIL_NODES("Nodes after last reachable destination"),
    /** Edges examined after the final reached destination, including its outgoing edges. */
    TAIL_EDGES("Edges after last reachable destination"),
    /** Nodes in mixed-search tails plus all nodes in searches with no reachable destination. */
    PREPROCESSING_AVOIDABLE_NODES("Upper-bound avoidable nodes"),
    /** Edges in mixed-search tails plus all edges in searches with no reachable destination. */
    PREPROCESSING_AVOIDABLE_EDGES("Upper-bound avoidable edges"),
    /** Elapsed worker time after the final reached destination in mixed searches. */
    TAIL_TIME("Time after last reachable destination (worker sum)"),
    /** Entire compute-call time when no requested destination was reachable. */
    NO_REACHABLE_TIME("Time with no reachable destination (worker sum)"),
    /** Sum of mixed-search tail time and whole-search time with no reachable destination. */
    PREPROCESSING_AVOIDABLE_TIME("Upper-bound avoidable Dijkstra time (worker sum)");

    private final String label;

    /** Associates a counter with its terminal label. */
    ReachabilityMetric(String label) {
      this.label = label;
    }
  }

  private final LongSupplier clock;
  private final long[] elapsed = new long[Phase.values().length];
  private final ThreadLocal<long[]> threadDatabaseTime = ThreadLocal.withInitial(() -> new long[1]);
  private final long[] workerElapsed = new long[WorkerPhase.values().length];
  private final boolean[] workerPhases = new boolean[WorkerPhase.values().length];
  private final long[] reachability = new long[ReachabilityMetric.values().length];
  private boolean hasReachabilityDetails;
  private long workerPathOutputTime;
  private boolean hasWorkerDetails;
  private volatile boolean enabled;
  private long assignmentStarted;
  private long pathsStarted;
  private long pathsWallTime;
  private long pathsWorkerTime;
  private int activePathWorkers;

  private final long[] outsideElapsed = new long[OutsidePhase.values().length];
  private final boolean[] outsidePhases = new boolean[OutsidePhase.values().length];
  private Thread coordinatorThread;
  private OutsidePhase outsidePhase;
  private boolean pathsHaveStarted;
  private boolean finalizing;
  private long outsideCheckpoint;
  private long runSequence;

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
    for (int i = 0; i < reachability.length; i++) {
      reachability[i] = 0;
    }
    hasReachabilityDetails = false;
    workerPathOutputTime = 0;
    hasWorkerDetails = false;
    threadDatabaseTime.remove();
    assignmentStarted = start();
    coordinatorThread = Thread.currentThread();
    outsidePhase = null;
    pathsHaveStarted = false;
    finalizing = false;
    outsideCheckpoint = assignmentStarted;
    runSequence++;
    for (int i = 0; i < outsideElapsed.length; i++) {
      outsideElapsed[i] = 0;
      outsidePhases[i] = false;
    }
  }

  /**
   * Times a coordinator operation, excluding intervals with any active assignment worker job.
   * Nested scopes attribute each interval only to the innermost operation. Calls on worker threads
   * and calls with auditing disabled return a shared no-op scope without reading the clock.
   *
   * @param phase Operation to identify while no assignment worker is active.
   * @return Scope to close on the coordinator in reverse opening order, preferably with
   *     try-with-resources.
   */
  public OutsideScope outside(OutsidePhase phase) {
    if (!enabled || Thread.currentThread() != coordinatorThread) {
      return OutsideScope.DISABLED;
    }
    synchronized (this) {
      accountOutsideUntil(clock.getAsLong());
      OutsideScope scope = new OutsideScope(this, outsidePhase, runSequence);
      outsidePhase = phase;
      outsidePhases[phase.ordinal()] = true;
      return scope;
    }
  }

  /** Marks the existing post-computation interval, before disposal and path-writer finalization. */
  public void beginFinalization() {
    if (!enabled || Thread.currentThread() != coordinatorThread) {
      return;
    }
    synchronized (this) {
      accountOutsideUntil(clock.getAsLong());
      finalizing = true;
    }
  }

  /**
   * Accounts the interval preceding a scope or worker-activity transition. Caller holds this
   * audit's monitor. No interval is charged twice; worker-active intervals belong to path wall
   * time.
   */
  private void accountOutsideUntil(long now) {
    if (activePathWorkers == 0) {
      OutsidePhase phase = outsidePhase;
      if (phase == null) {
        phase =
            finalizing
                ? OutsidePhase.FINALIZATION
                : pathsHaveStarted ? OutsidePhase.COORDINATION : OutsidePhase.PREPARATION;
      }
      outsideElapsed[phase.ordinal()] += now - outsideCheckpoint;
    }
    outsideCheckpoint = now;
  }

  /** Restores a nested coordinator operation once, including exceptional exits. */
  public static final class OutsideScope implements AutoCloseable {
    private static final OutsideScope DISABLED = new OutsideScope(null, null, 0);
    private final AssignmentComputingTimes audit;
    private final OutsidePhase previous;
    private final long run;
    private boolean closed;

    /** Captures the previous category and assignment generation for a scoped measurement. */
    private OutsideScope(AssignmentComputingTimes audit, OutsidePhase previous, long run) {
      this.audit = audit;
      this.previous = previous;
      this.run = run;
    }

    /** Finishes this scope without changing a later assignment or an already printed report. */
    @Override
    public void close() {
      if (audit == null || Thread.currentThread() != audit.coordinatorThread) {
        return;
      }
      synchronized (audit) {
        if (!closed && audit.enabled && run == audit.runSequence) {
          audit.accountOutsideUntil(audit.clock.getAsLong());
          audit.outsidePhase = previous;
        }
        closed = true;
      }
    }
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
      accountOutsideUntil(started);
      pathsHaveStarted = true;
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
      accountOutsideUntil(finished);
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
    private final long[] searchMeasurements =
        recording ? new long[ReachabilityMetric.values().length] : null;
    private boolean hasSearchMeasurements;
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
     * Reads the worker's clock for an optional detailed measurement.
     *
     * @return Monotonic timestamp, or zero without a clock read when auditing is disabled.
     */
    public long startMeasurement() {
      return recording ? clock.getAsLong() : 0;
    }

    /**
     * Adds a completed search's observation to local counters, without locking the shared audit.
     *
     * @param metric Counter to update; time metrics use nanoseconds.
     * @param value Nonnegative measured count or elapsed time.
     */
    public void addReachability(ReachabilityMetric metric, long value) {
      if (recording) {
        searchMeasurements[metric.ordinal()] += value;
        hasSearchMeasurements = true;
      }
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
          for (int i = 0; i < searchMeasurements.length; i++) {
            reachability[i] += searchMeasurements[i];
          }
          hasReachabilityDetails |= hasSearchMeasurements;
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
    accountOutsideUntil(finished);
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
    appendOutside(report);
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
      report.append(
          "  Worker setup and cost-markup updates are not itemized in the worker breakdown.\n");
    }
    if (hasReachabilityDetails) {
      appendReachability(report);
    }
    report.append("  Worker sums and database writer calls overlap the wall-time breakdown.\n");
    System.out.print(report);
    threadDatabaseTime.remove();
  }

  /** Prints a partition of elapsed time outside the union of all assignment worker intervals. */
  private void appendOutside(StringBuilder report) {
    long total = 0;
    for (long duration : outsideElapsed) {
      total += duration;
    }
    appendTime(report, "Outside parallel assignment (wall)", total);
    report.append("  Outside-phase breakdown (exclusive wall times):\n");
    for (OutsidePhase phase : OutsidePhase.values()) {
      long duration = outsideElapsed[phase.ordinal()];
      if (outsidePhases[phase.ordinal()] || duration > 0) {
        appendTime(report, "  " + phase.label, duration);
      }
    }
    report.append(
        "  Outside rows sum to outside time; outside + path wall = total (before rounding).\n");
    report.append(
        "  Nested phases count once; intervals with active assignment jobs are excluded.\n");
  }

  /**
   * Prints observations separately from the existing stage timings; no work was actually skipped.
   */
  private void appendReachability(StringBuilder report) {
    report.append("  Fast multi-flow unreachable-destination diagnostic:\n");
    for (ReachabilityMetric metric :
        EnumSet.range(ReachabilityMetric.SEARCHES, ReachabilityMetric.AVOIDABLE_TIME)) {
      long value = reachability[metric.ordinal()];
      if (metric == ReachabilityMetric.SEARCH_TIME || metric == ReachabilityMetric.AVOIDABLE_TIME) {
        appendTime(report, "  " + metric.label, value);
      } else {
        report.append(String.format(Locale.ROOT, "    %-56s %12d%n", metric.label + ":", value));
      }
    }
    appendShare(
        report,
        "Potentially avoidable edge examinations",
        ReachabilityMetric.AVOIDABLE_EDGES,
        ReachabilityMetric.EDGES);
    appendShare(
        report,
        "Potentially avoidable share of observed Dijkstra time",
        ReachabilityMetric.AVOIDABLE_TIME,
        ReachabilityMetric.SEARCH_TIME);
    report.append("  Shortenable and entirely skippable searches are separate counts.\n");
    report.append("  The preceding reuse estimates require earlier alternatives.\n");
    report.append("  Reachability preprocessing potential (includes first routes):\n");
    for (ReachabilityMetric metric :
        EnumSet.range(
            ReachabilityMetric.MIXED_DESTINATION_SEARCHES,
            ReachabilityMetric.PREPROCESSING_AVOIDABLE_EDGES)) {
      report.append(
          String.format(
              Locale.ROOT, "    %-56s %12d%n", metric.label + ":", reachability[metric.ordinal()]));
    }
    for (ReachabilityMetric metric :
        EnumSet.range(
            ReachabilityMetric.TAIL_TIME, ReachabilityMetric.PREPROCESSING_AVOIDABLE_TIME)) {
      appendTime(report, "  " + metric.label, reachability[metric.ordinal()]);
    }
    appendShare(
        report,
        "Upper-bound share of edge examinations",
        ReachabilityMetric.PREPROCESSING_AVOIDABLE_EDGES,
        ReachabilityMetric.EDGES);
    appendShare(
        report,
        "Upper-bound share of observed Dijkstra time",
        ReachabilityMetric.PREPROCESSING_AVOIDABLE_TIME,
        ReachabilityMetric.SEARCH_TIME);
    report.append("  Upper bound assumes missing destinations are known before each search.\n");
    report.append("  Preprocessing and lookup costs are not measured or deducted.\n");
    report.append("  Reuse and preprocessing estimates overlap; do not add them together.\n");
    report.append(
        "  Searches ran in full; these are diagnostic observations, not wall-time savings.\n");
    report.append(
        "  Diagnostic counters add overhead; their times are already included in Dijkstra.\n");
  }

  /** Shows a measured proportion, using n/a when no denominator was observed. */
  private void appendShare(
      StringBuilder report, String label, ReachabilityMetric part, ReachabilityMetric whole) {
    long total = reachability[whole.ordinal()];
    String share =
        total == 0
            ? "n/a"
            : String.format(Locale.ROOT, "%.2f %%", 100.0 * reachability[part.ordinal()] / total);
    report.append(String.format(Locale.ROOT, "    %-56s %12s%n", label + ":", share));
  }

  private static void appendTime(StringBuilder report, String label, long nanos) {
    report.append(String.format(Locale.ROOT, "  %-58s %10.3f s%n", label + ":", nanos / 1.0e9));
  }
}
