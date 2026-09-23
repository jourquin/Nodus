package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.OutsidePhase;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.OutsideScope;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.Phase;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.ReachabilityMetric;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerPhase;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerTimes;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone regression checks for timing accumulation and parallel-worker accounting. */
public final class AssignmentComputingTimesTest {
  private static final AtomicLong now = new AtomicLong();

  private static void time(double seconds) {
    now.set((long) (seconds * 1.0e9));
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void checkTime(String report, String label, String seconds) {
    for (String line : report.split("\\R")) {
      if (line.stripLeading().startsWith(label + ":")) {
        check(line.endsWith(seconds + " s"), "Unexpected timing: " + line);
        return;
      }
    }
    throw new AssertionError("Missing timing: " + label);
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    check(latch.await(5, TimeUnit.SECONDS), "Worker synchronization timed out");
  }

  /** Checks local stage sums, including overlapping workers and complete path-output exclusions. */
  private static void checkWorkerStages(ByteArrayOutputStream output) throws Exception {
    output.reset();
    AssignmentComputingTimes audit = new AssignmentComputingTimes(now::get);
    time(0);
    audit.startAssignment();
    WorkerTimes first = audit.newWorkerTimes();
    check(first.isEnabled(), "Enabled worker audit is inactive");
    first.addReachability(ReachabilityMetric.SEARCHES, 2);
    first.addReachability(ReachabilityMetric.EDGES, 4);
    first.addReachability(ReachabilityMetric.AVOIDABLE_EDGES, 1);
    first.addReachability(ReachabilityMetric.SEARCH_TIME, 3_000_000_000L);
    first.addReachability(ReachabilityMetric.AVOIDABLE_TIME, 1_000_000_000L);
    first.addReachability(ReachabilityMetric.MIXED_DESTINATION_SEARCHES, 1);
    first.addReachability(ReachabilityMetric.TAIL_NODES, 3);
    first.addReachability(ReachabilityMetric.TAIL_EDGES, 1);
    first.addReachability(ReachabilityMetric.TAIL_TIME, 1_000_000_000L);
    first.addReachability(ReachabilityMetric.PREPROCESSING_AVOIDABLE_NODES, 3);
    first.addReachability(ReachabilityMetric.PREPROCESSING_AVOIDABLE_EDGES, 1);
    first.addReachability(ReachabilityMetric.PREPROCESSING_AVOIDABLE_TIME, 1_000_000_000L);
    time(1);
    long firstBefore = audit.getThreadDatabaseTime();
    long firstPath = audit.startPaths();
    first.startPhase(WorkerPhase.DIJKSTRA);
    CountDownLatch secondStarted = new CountDownLatch(1);
    CountDownLatch finishDijkstra = new CountDownLatch(1);
    CountDownLatch modalStarted = new CountDownLatch(1);
    CountDownLatch finishModal = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              try (WorkerTimes second = audit.newWorkerTimes()) {
                second.addReachability(ReachabilityMetric.SEARCHES, 3);
                second.addReachability(ReachabilityMetric.EDGES, 6);
                second.addReachability(ReachabilityMetric.AVOIDABLE_EDGES, 2);
                second.addReachability(ReachabilityMetric.SEARCH_TIME, 6_000_000_000L);
                second.addReachability(ReachabilityMetric.AVOIDABLE_TIME, 2_000_000_000L);
                second.addReachability(ReachabilityMetric.MIXED_DESTINATION_SEARCHES, 1);
                second.addReachability(ReachabilityMetric.NO_REACHABLE_DESTINATION_SEARCHES, 1);
                second.addReachability(ReachabilityMetric.TAIL_NODES, 2);
                second.addReachability(ReachabilityMetric.TAIL_EDGES, 1);
                second.addReachability(ReachabilityMetric.TAIL_TIME, 1_000_000_000L);
                second.addReachability(ReachabilityMetric.NO_REACHABLE_TIME, 2_000_000_000L);
                second.addReachability(ReachabilityMetric.PREPROCESSING_AVOIDABLE_NODES, 7);
                second.addReachability(ReachabilityMetric.PREPROCESSING_AVOIDABLE_EDGES, 4);
                second.addReachability(
                    ReachabilityMetric.PREPROCESSING_AVOIDABLE_TIME, 3_000_000_000L);
                long before = audit.getThreadDatabaseTime();
                long paths = audit.startPaths();
                second.startPhase(WorkerPhase.DIJKSTRA);
                secondStarted.countDown();
                await(finishDijkstra);
                second.endPhase();
                second.startPhase(WorkerPhase.MODAL_SPLITTING);
                modalStarted.countDown();
                await(finishModal);
                second.endPhase();
                audit.endPaths(paths, before);
              } catch (Throwable error) {
                failure.set(error);
                secondStarted.countDown();
                modalStarted.countDown();
              }
            });
    worker.setDaemon(true);
    worker.start();
    await(secondStarted);
    time(3);
    first.endPhase();
    first.startPhase(WorkerPhase.RECONSTRUCTION);
    time(4);
    long writing = first.startPathOutput();
    // Two seconds in the output call, only half a second attributed to DB work.
    // The remaining writer wait must also be excluded from reconstruction.
    time(4.5);
    long database = audit.start();
    time(5);
    audit.add(Phase.DATABASE, database);
    time(6);
    first.endPathOutput(writing);
    time(7);
    first.endPhase();
    finishDijkstra.countDown();
    await(modalStarted);
    first.startPhase(WorkerPhase.HEADER_MATCHING);
    time(8);
    writing = first.startPathOutput();
    time(8.5);
    database = audit.start();
    time(9);
    audit.add(Phase.DATABASE, database);
    time(10);
    first.endPathOutput(writing);
    time(11);
    first.endPhase();
    finishModal.countDown();
    worker.join(5000);
    check(!worker.isAlive(), "Worker did not finish");
    check(failure.get() == null, "Worker failed: " + failure.get());
    first.startPhase(WorkerPhase.MODAL_SPLITTING);
    time(13);
    first.endPhase();
    first.startPhase(WorkerPhase.VOLUME_DISTRIBUTION);
    time(14);
    first.endPhase();
    first.startPhase(WorkerPhase.DIJKSTRA); // Another alternative on the same worker.
    time(15);
    first.endPhase();
    first.close();
    first.close(); // Must not merge this job twice.
    audit.endPaths(firstPath, firstBefore);
    time(16);
    audit.finishAndPrint("FastMFAssignment", 26, 2, true);
    String report = output.toString("UTF-8");
    checkTime(report, "Dijkstra", "9.000");
    checkTime(report, "Observed Dijkstra time (worker sum)", "9.000");
    checkTime(report, "Potentially avoidable Dijkstra time (worker sum)", "3.000");
    checkTime(report, "Time after last reachable destination (worker sum)", "2.000");
    checkTime(report, "Time with no reachable destination (worker sum)", "2.000");
    checkTime(report, "Upper-bound avoidable Dijkstra time (worker sum)", "4.000");
    check(
        report.matches("(?s).*Upper-bound avoidable nodes:\\s+10\\R.*"),
        "Preprocessing counters were not merged exactly once");
    check(
        report.matches("(?s).*Upper-bound share of edge examinations:\\s+50\\.00 %\\R.*"),
        "Incorrect preprocessing edge share");
    check(
        report.matches("(?s).*Upper-bound share of observed Dijkstra time:\\s+44\\.44 %\\R.*"),
        "Incorrect preprocessing time share or locale");
    check(
        report.matches("(?s).*Completed Dijkstra searches:\\s+5\\R.*"),
        "Search counts were not merged exactly once across workers");
    check(
        report.matches("(?s).*Potentially avoidable edge examinations:\\s+30\\.00 %\\R.*"),
        "Wrong ratio or locale for reachability counters");
    checkTime(report, "Path reconstruction", "2.000");
    checkTime(report, "Header matching", "2.000");
    checkTime(report, "Modal splitting and path filtering", "6.000");
    checkTime(report, "Volume distribution", "1.000");
    checkTime(report, "Path output (includes DB calls and writer waits)", "4.000");
    checkTime(report, "Paths and flow assignment (wall, includes path writes)", "14.000");
    checkTime(report, "Paths and flow assignment (worker sum, excludes DB calls)", "23.000");
    checkTime(report, "Database writing (writer calls)", "1.000");

    output.reset();
    audit.startAssignment();
    try (WorkerTimes partial = audit.newWorkerTimes()) {
      partial.includePhases(
          WorkerPhase.DIJKSTRA,
          WorkerPhase.RECONSTRUCTION,
          WorkerPhase.HEADER_MATCHING,
          WorkerPhase.MODAL_SPLITTING,
          WorkerPhase.VOLUME_DISTRIBUTION);
      time(17);
      partial.startPhase(WorkerPhase.VOLUME_DISTRIBUTION);
      time(18);
      throw new IllegalStateException("Simulated assignment failure");
    } catch (IllegalStateException expected) {
      // Closing must retain the partial stage, even if its usual end call was not reached.
    }
    time(19);
    audit.finishAndPrint("FastMFAssignment", 27, 1, false);
    report = output.toString("UTF-8");
    check(report.contains("failed/cancelled"), "Failed worker run not identified");
    check(!report.contains("unreachable-destination diagnostic"), "Stale diagnostic after reset");
    check(!report.contains("Reachability preprocessing potential"), "Stale preprocessing estimate");
    checkTime(report, "Dijkstra", "0.000");
    checkTime(report, "Path reconstruction", "0.000");
    checkTime(report, "Header matching", "0.000");
    checkTime(report, "Modal splitting and path filtering", "0.000");
    checkTime(report, "Volume distribution", "1.000");
    checkTime(report, "Path output (includes DB calls and writer waits)", "0.000");

    output.reset();
    audit.startAssignment();
    time(20);
    try (WorkerTimes other = audit.newWorkerTimes()) {
      other.startPhase(WorkerPhase.RECONSTRUCTION_LOADING);
      time(21);
      long outer = other.startPathOutput();
      time(22);
      long inner = other.startPathOutput(); // Automatic flush within a header/detail call.
      time(24);
      other.endPathOutput(inner);
      time(25);
      other.endPathOutput(outer);
      time(26);
      other.endPhase();
    }
    time(27);
    audit.finishAndPrint("AllOrNothingAssignment", 28, 1, true);
    report = output.toString("UTF-8");
    checkTime(report, "Path reconstruction and volume loading", "2.000");
    checkTime(report, "Path output (includes DB calls and writer waits)", "4.000");
    check(!report.contains("Header matching:"), "Inapplicable stage leaked from previous run");
    check(!report.contains("Dijkstra:"), "Unused stage leaked from previous run");

    output.reset();
    audit.startAssignment();
    audit.finishAndPrint("OtherAssignment", 28, 1, true);
    check(
        !output.toString("UTF-8").contains("Assignment breakdown"),
        "Worker detail leaked into another assignment");
  }

  /** Checks an independently specified timeline with nested scopes and overlapping worker jobs. */
  private static void checkOutsideStages(ByteArrayOutputStream output) throws Exception {
    output.reset();
    time(0);
    NodusC.displayComputingTimes = true;
    AtomicLong clockReads = new AtomicLong();
    AssignmentComputingTimes audit =
        new AssignmentComputingTimes(
            () -> {
              clockReads.incrementAndGet();
              return now.get();
            });
    audit.startAssignment();
    time(1);
    try (OutsideScope network = audit.outside(OutsidePhase.NETWORK)) {
      time(2);
      try (OutsideScope demand = audit.outside(OutsidePhase.DEMAND)) {
        time(3);
      }
      time(4);
    }
    try (OutsideScope modal = audit.outside(OutsidePhase.MODAL_SETUP)) {
      time(5);
    }
    try (OutsideScope setup = audit.outside(OutsidePhase.PATH_SETUP)) {
      time(6);
      long first = audit.startPaths();
      time(7);
      long second = audit.startPaths();
      time(7.5);
      try (OutsideScope overlapped = audit.outside(OutsidePhase.NETWORK_OUTPUT)) {
        time(8);
        audit.endPaths(first, 0);
        time(8.5);
      }
      time(9);
      audit.endPaths(second, 0);
      time(10);
    }
    // A foreign thread cannot reclassify coordinator work or read the outside timer's clock.
    long beforeReads = clockReads.get();
    Thread foreign =
        new Thread(
            () -> {
              try (OutsideScope ignored = audit.outside(OutsidePhase.COSTS)) {
                audit.beginFinalization();
              }
            });
    foreign.start();
    foreign.join(5000);
    check(!foreign.isAlive(), "Foreign-thread check did not complete");
    check(clockReads.get() == beforeReads, "Worker scope read the coordinator clock");
    time(11);
    long third = audit.startPaths();
    time(12);
    audit.endPaths(third, 0);
    time(13);
    audit.beginFinalization();
    try (OutsideScope finalization = audit.outside(OutsidePhase.PATH_FINALIZATION)) {
      time(14);
      try (OutsideScope batches = audit.outside(OutsidePhase.PATH_BATCHES)) {
        time(15);
      }
      time(16);
      try (OutsideScope indexes = audit.outside(OutsidePhase.PATH_INDEXES)) {
        time(17);
      }
      time(18);
      try (OutsideScope commit = audit.outside(OutsidePhase.DATABASE_COMMIT)) {
        time(19);
      }
      time(20);
    }
    time(21);
    audit.finishAndPrint("OutsideTimeline", 1, 2, true);
    String report = output.toString("UTF-8");
    checkTime(report, "Total elapsed", "21.000");
    checkTime(report, "Paths and flow assignment (wall, includes path writes)", "4.000");
    checkTime(report, "Outside parallel assignment (wall)", "17.000");
    checkTime(report, "Network initialization and generation", "2.000");
    checkTime(report, "Demand loading and preparation", "1.000");
    checkTime(report, "Modal-split initialization", "1.000");
    checkTime(report, "Path-output initialization", "2.000");
    checkTime(report, "Virtual-network database output", "0.000");
    checkTime(report, "Path database batches", "1.000");
    checkTime(report, "Path database index creation", "1.000");
    checkTime(report, "Database commits", "1.000");
    checkTime(report, "Other path-output finalization", "4.000");
    checkTime(report, "Other assignment preparation", "1.000");
    checkTime(report, "Other coordination between worker jobs", "2.000");
    checkTime(report, "Other finalization", "1.000");
    check(!report.contains("Cost parsing and evaluation:"), "Foreign scope changed the report");

    // Exceptional scope exits and a report printed while a worker is still active.
    output.reset();
    time(30);
    audit.startAssignment();
    try (OutsideScope failed = audit.outside(OutsidePhase.VEHICLES)) {
      time(31);
      throw new IllegalStateException("Synthetic conversion failure");
    } catch (IllegalStateException expected) {
      // The partial conversion must be retained, with its scope restored for subsequent work.
    }
    OutsideScope stale = audit.outside(OutsidePhase.DEMAND);
    time(32);
    long unfinished = audit.startPaths();
    time(33);
    audit.finishAndPrint("InterruptedOutside", 2, 1, false);
    audit.endPaths(unfinished, 0); // Finishing after the report must not change it.
    report = output.toString("UTF-8");
    checkTime(report, "Total elapsed", "3.000");
    checkTime(report, "Outside parallel assignment (wall)", "2.000");
    checkTime(report, "Volume-to-vehicle conversion", "1.000");
    checkTime(report, "Demand loading and preparation", "1.000");
    checkTime(report, "Paths and flow assignment (wall, includes path writes)", "1.000");

    output.reset();
    time(40);
    audit.startAssignment();
    stale.close(); // A scope belonging to the preceding run must not restore its category.
    stale.close();
    time(41);
    audit.beginFinalization();
    time(42);
    audit.finishAndPrint("ResetOutside", 3, 1, true);
    report = output.toString("UTF-8");
    checkTime(report, "Outside parallel assignment (wall)", "2.000");
    checkTime(report, "Other assignment preparation", "1.000");
    checkTime(report, "Other finalization", "1.000");
    check(!report.contains("Demand loading and preparation:"), "Stale outside scope after reset");
    check(!report.contains("Volume-to-vehicle conversion:"), "Stale outside duration after reset");
  }

  public static void main(String[] args) throws Exception {
    boolean oldEnabled = NodusC.displayComputingTimes;
    PrintStream oldOut = System.out;
    Locale oldLocale = Locale.getDefault();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PrintStream captured = new PrintStream(output, true, "UTF-8")) {
      System.setOut(captured);
      Locale.setDefault(Locale.FRANCE);
      NodusC.displayComputingTimes = false;
      AssignmentComputingTimes disabled = new AssignmentComputingTimes(() -> {
        throw new AssertionError("Disabled audit read the clock");
      });
      disabled.startAssignment();
      for (OutsidePhase phase : OutsidePhase.values()) {
        try (OutsideScope ignored = disabled.outside(phase)) {
          disabled.beginFinalization();
        }
      }
      NodusC.displayComputingTimes = true; // Switching mid-run must have no effect.
      disabled.add(Phase.COSTS, disabled.start());
      disabled.add(Phase.DATABASE, disabled.start());
      disabled.endPaths(disabled.startPaths(), disabled.getThreadDatabaseTime());
      try (WorkerTimes details = disabled.newWorkerTimes()) {
        details.includePhases(WorkerPhase.values());
        check(details.startMeasurement() == 0, "Disabled diagnostic read its clock");
        for (ReachabilityMetric metric : ReachabilityMetric.values()) {
          details.addReachability(metric, 123);
        }
        check(!details.isEnabled(), "Disabled worker audit is active");
        for (WorkerPhase phase : WorkerPhase.values()) {
          details.startPhase(phase);
          details.endPathOutput(details.startPathOutput());
          details.endPhase();
        }
      }
      disabled.finishAndPrint("Disabled", 1, 1, true);
      check(output.size() == 0, "Disabled audit produced output");

      AssignmentComputingTimes audit = new AssignmentComputingTimes(now::get);
      time(0);
      audit.startAssignment();
      time(1);
      long started = audit.start();
      time(2);
      audit.add(Phase.NETWORK, started);
      started = audit.start();
      time(3);
      audit.add(Phase.COSTS, started);
      started = audit.start();
      time(5);
      audit.add(Phase.COSTS, started); // Repeated passes must accumulate.

      long databaseBefore = audit.getThreadDatabaseTime();
      long firstPath = audit.startPaths();
      CountDownLatch secondStarted = new CountDownLatch(1);
      CountDownLatch finishSecond = new CountDownLatch(1);
      AtomicReference<Throwable> workerFailure = new AtomicReference<>();
      time(6);
      Thread worker = new Thread(() -> {
        try {
          long before = audit.getThreadDatabaseTime();
          long secondPath = audit.startPaths();
          secondStarted.countDown();
          await(finishSecond);
          audit.endPaths(secondPath, before);
        } catch (Throwable failure) {
          workerFailure.set(failure);
          secondStarted.countDown();
        }
      });
      worker.setDaemon(true);
      worker.start();
      await(secondStarted);
      time(6.5);
      started = audit.start();
      time(7);
      audit.add(Phase.DATABASE, started);
      time(8);
      audit.endPaths(firstPath, databaseBefore);
      time(9);
      finishSecond.countDown();
      worker.join(5000);
      check(!worker.isAlive(), "Worker did not finish");
      check(workerFailure.get() == null, "Worker failed: " + workerFailure.get());
      started = audit.start();
      time(9.5);
      audit.add(Phase.DATABASE, started); // Final batch/index/commit outside the workers.
      time(10);
      NodusC.displayComputingTimes = false; // The enabled run must still print.
      audit.finishAndPrint("ParallelAssignment", 7, 2, true);
      String report = output.toString("UTF-8");
      check(report.contains("scenario 7, 2 threads, completed"), "Missing run identity");
      checkTime(report, "Total elapsed", "10.000");
      checkTime(report, "Virtual network generation (wall)", "1.000");
      checkTime(report, "Cost parser (wall)", "3.000");
      checkTime(report, "Paths and flow assignment (wall, includes path writes)", "4.000");
      checkTime(report, "Paths and flow assignment (worker sum, excludes DB calls)", "5.500");
      checkTime(report, "Database writing (writer calls)", "1.000");
      audit.finishAndPrint("Duplicate", 7, 2, true);
      check(output.toString("UTF-8").equals(report), "Summary printed twice");

      output.reset();
      NodusC.displayComputingTimes = true;
      audit.startAssignment(); // Reusing parameters must reset every counter.
      time(11);
      long path = audit.startPaths();
      time(12);
      audit.endPaths(path, audit.getThreadDatabaseTime());
      time(14);
      path = audit.startPaths();
      time(15);
      audit.endPaths(path, audit.getThreadDatabaseTime());
      time(16);
      audit.finishAndPrint("FailedAssignment", 8, 1, false);
      String failed = output.toString("UTF-8");
      check(failed.contains("failed/cancelled"), "Missing failed-run status");
      checkTime(failed, "Total elapsed", "6.000");
      checkTime(failed, "Cost parser (wall)", "0.000");
      checkTime(failed, "Database writing (writer calls)", "0.000");
      checkTime(failed, "Paths and flow assignment (wall, includes path writes)", "2.000");
      checkTime(failed, "Paths and flow assignment (worker sum, excludes DB calls)", "2.000");
      checkWorkerStages(output);
      checkOutsideStages(output);
    } finally {
      System.setOut(oldOut);
      Locale.setDefault(oldLocale);
      NodusC.displayComputingTimes = oldEnabled;
    }
    System.out.println("Assignment computing-time checks passed.");
  }
}
