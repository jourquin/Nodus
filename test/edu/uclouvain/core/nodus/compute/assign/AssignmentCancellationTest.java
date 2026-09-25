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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.compute.assign.workers.AllOrNothingAssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorkerParameters;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Checks that waiting workers terminate and coordinator cancellation does not leave them running.
 */
@ResourceLock(Resources.SYSTEM_ERR)
class AssignmentCancellationTest {
  private final ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
  private PrintStream originalError;
  private PrintStream capturedError;

  @BeforeEach
  void captureWorkerDiagnostics() {
    originalError = System.err;
    capturedError = new PrintStream(errorOutput, true, StandardCharsets.UTF_8);
    System.setErr(capturedError);
  }

  @AfterEach
  void restoreErrorStream() {
    System.setErr(originalError);
    capturedError.close();
  }

  @Test
  void endMarkersFinishEveryWorkerBeforeTheCoordinatorReportsCompletion() throws Exception {
    final ObservedQueue queue = new ObservedQueue(2);
    final AssignmentWorker first = new AllOrNothingAssignmentWorker(queue);
    final AssignmentWorker second = new AllOrNothingAssignmentWorker(queue);
    final Coordinator coordinator = new Coordinator(first, null, second);
    final FutureTask<Boolean> result = new FutureTask<>(coordinator::waitForAssignmentWorkers);
    final Thread waiting = new Thread(result, "test-assignment-coordinator");
    try {
      first.start();
      second.start();
      assertTrue(queue.entered.await(5, TimeUnit.SECONDS));
      waiting.start();
      assertFalse(result.isDone());
      queue.addWork(WorkQueue.NO_MORE_WORK);
      queue.addWork(WorkQueue.NO_MORE_WORK);
      assertTrue(result.get(5, TimeUnit.SECONDS));
      assertFalse(first.isAlive());
      assertFalse(second.isAlive());
      assertFalse(first.isCancelled());
      assertFalse(second.isCancelled());
    } finally {
      stopAndJoin(first, second, waiting);
    }
    assertEquals("", errorOutput.toString(StandardCharsets.UTF_8));
  }

  @Test
  void interruptedCoordinatorCancelsAndJoinsAllWorkersAndRetainsInterruptStatus() throws Exception {
    final ObservedQueue queue = new ObservedQueue(2);
    final AssignmentWorker first = new AllOrNothingAssignmentWorker(queue);
    final AssignmentWorker second = new AllOrNothingAssignmentWorker(queue);
    final Coordinator coordinator = new Coordinator(first, null, second);
    final FutureTask<Boolean> result =
        new FutureTask<>(
            () -> {
              Thread.currentThread().interrupt();
              boolean completed = coordinator.waitForAssignmentWorkers();
              assertTrue(Thread.currentThread().isInterrupted());
              return completed;
            });
    final Thread waiting = new Thread(result, "test-cancelled-coordinator");
    try {
      first.start();
      second.start();
      assertTrue(queue.entered.await(5, TimeUnit.SECONDS));
      waiting.start();
      assertFalse(result.get(5, TimeUnit.SECONDS));
      assertFalse(first.isAlive());
      assertFalse(second.isAlive());
      assertTrue(first.isCancelled());
      assertTrue(second.isCancelled());
    } finally {
      stopAndJoin(first, second, waiting);
    }
    assertEquals("", errorOutput.toString(StandardCharsets.UTF_8));
  }

  @Test
  void interruptingAWorkerWaitingForItsFirstJobCancelsItWithoutAnAssignment() throws Exception {
    final ObservedQueue queue = new ObservedQueue(1);
    final AssignmentWorker worker = new AllOrNothingAssignmentWorker(queue);
    try {
      worker.start();
      assertTrue(queue.entered.await(5, TimeUnit.SECONDS));
      worker.interrupt();
      worker.join(5000);
      assertFalse(worker.isAlive());
      assertTrue(worker.isCancelled());
    } finally {
      stopAndJoin(worker);
    }
    assertEquals("", errorOutput.toString(StandardCharsets.UTF_8));
  }

  @Test
  void malformedQueuedWorkCancelsTheWorkerInsteadOfLeavingItWaiting() throws Exception {
    final WorkQueue queue = new WorkQueue();
    queue.addWork("not assignment parameters");
    final AssignmentWorker worker = new AllOrNothingAssignmentWorker(queue);
    try {
      worker.start();
      worker.join(5000);
      assertFalse(worker.isAlive());
      assertTrue(worker.isCancelled());
    } finally {
      stopAndJoin(worker);
    }
    String diagnostic = errorOutput.toString(StandardCharsets.UTF_8);
    assertTrue(diagnostic.startsWith(ClassCastException.class.getName() + ":"), diagnostic);
    assertTrue(diagnostic.contains(AssignmentWorkerParameters.class.getName()), diagnostic);
  }

  private static void stopAndJoin(Thread... threads) throws InterruptedException {
    for (Thread thread : threads) {
      thread.interrupt();
    }
    for (Thread thread : threads) {
      thread.join(5000);
      assertFalse(thread.isAlive(), "Test thread did not terminate: " + thread.getName());
    }
  }

  private static final class ObservedQueue extends WorkQueue {
    final CountDownLatch entered;

    ObservedQueue(int workers) {
      entered = new CountDownLatch(workers);
    }

    @Override
    public synchronized Object getWork() throws InterruptedException {
      entered.countDown();
      return super.getWork();
    }
  }

  private static final class Coordinator extends Assignment {
    Coordinator(AssignmentWorker... workers) {
      super(new AssignmentParameters(null));
      assignmentWorkers = workers;
    }

    @Override
    public boolean assign() {
      throw new UnsupportedOperationException("Only the coordinator's worker lifecycle is tested");
    }
  }
}
