package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.Phase;
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
      NodusC.displayComputingTimes = true; // Switching mid-run must have no effect.
      disabled.add(Phase.COSTS, disabled.start());
      disabled.add(Phase.DATABASE, disabled.start());
      disabled.endPaths(disabled.startPaths(), disabled.getThreadDatabaseTime());
      try (WorkerTimes details = disabled.newWorkerTimes()) {
        details.includePhases(WorkerPhase.values());
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
    } finally {
      System.setOut(oldOut);
      Locale.setDefault(oldLocale);
      NodusC.displayComputingTimes = oldEnabled;
    }
    System.out.println("Assignment computing-time checks passed.");
  }
}
