package edu.uclouvain.core.nodus.compute.assign;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.Phase;
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
    } finally {
      System.setOut(oldOut);
      Locale.setDefault(oldLocale);
      NodusC.displayComputingTimes = oldEnabled;
    }
    System.out.println("Assignment computing-time checks passed.");
  }
}
