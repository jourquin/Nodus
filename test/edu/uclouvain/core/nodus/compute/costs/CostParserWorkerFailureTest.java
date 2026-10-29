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

package edu.uclouvain.core.nodus.compute.costs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.AssignmentTestProject;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Faults in one real cost worker must cancel its peers, including a waiting peer. */
@ResourceLock("JDBCUtils")
@Timeout(20)
class CostParserWorkerFailureTest {
  @TempDir Path directory;

  @Test
  void invalidFormulaCancelsPeersAndPreservesTheError() throws Exception {
    exercise("formula");
  }

  @Test
  void uncheckedFailureCancelsPeersAndIsReported() throws Exception {
    exercise("exception");
  }

  @Test
  void fatalFailureCancelsPeersAndRemainsVisibleToTheUncaughtHandler() throws Exception {
    exercise("error");
  }

  @Test
  void interruptedCostCoordinatorRetainsWorkersUntilTheirCleanupFinishes() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      final VirtualNetwork network = new VirtualNetwork(project.parameters(1));
      final CountDownLatch entered = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
      project.setProgressHandler(
          text -> {
            if (Thread.currentThread() instanceof CostParserWorker) {
              entered.countDown();
              while (release.getCount() != 0) {
                try {
                  release.await();
                } catch (InterruptedException ignored) {
                  // Simulate an operation that needs time to complete after cancellation.
                }
              }
              return false;
            }
            return true;
          });
      final FutureTask<Boolean> result =
          new FutureTask<>(
              () -> {
                boolean success = network.computeCosts(0, 1, (byte) 0, 1);
                assertTrue(Thread.currentThread().isInterrupted());
                return success;
              });
      final Thread coordinator = new Thread(result, "test-cost-cleanup");
      try {
        assertTrue(network.generate());
        network.setGroups(new byte[] {1});
        network.setMaxClass((byte) 0);
        coordinator.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        coordinator.interrupt();
        assertThrows(TimeoutException.class, () -> result.get(1300, TimeUnit.MILLISECONDS));
        coordinator.interrupt();
        assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
        release.countDown();
        assertFalse(result.get(5, TimeUnit.SECONDS));
      } finally {
        release.countDown();
        coordinator.join(5000);
        assertFalse(coordinator.isAlive());
        project.setProgressHandler(null);
        network.dispose();
      }
    }
  }

  @Test
  void progressCancellationStopsPeersQuietly() throws Exception {
    exercise("cancel");
  }

  @TestFactory
  List<DynamicTest> coordinatorFailuresAreReportedAndCanBeRetried() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String fault : List.of("formula", "exception", "cancel")) {
      tests.add(
          dynamicTest(
              fault,
              () -> {
                try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
                  final AssignmentParameters parameters = project.parameters(2);
                  if (fault.equals("formula")) {
                    parameters
                        .getCostFunctions()
                        .setProperty("mv.1,1", "missing_cost_variable + 1");
                  }
                  final List<String> errors = new ArrayList<>();
                  final VirtualNetwork network =
                      new VirtualNetwork(parameters) {
                        @Override
                        protected void reportCostError(String message) {
                          errors.add(message);
                        }
                      };
                  try {
                    assertTrue(network.generate());
                    network.setGroups(new byte[] {1, 2});
                    network.setMaxClass((byte) 0);
                    final Set<Thread> observed = ConcurrentHashMap.newKeySet();
                    project.setProgressHandler(
                        text -> {
                          if (Thread.currentThread() instanceof CostParserWorker) {
                            observed.add(Thread.currentThread());
                            if (fault.equals("exception")) {
                              throw new IllegalStateException("Injected coordinator failure");
                            }
                            return !fault.equals("cancel");
                          }
                          return true;
                        });
                    assertFalse(network.computeCosts(0, 1, (byte) 0, 2));
                    assertFalse(observed.isEmpty());
                    for (Thread worker : observed) {
                      assertFalse(worker.isAlive());
                    }
                    assertEquals(fault.equals("cancel") ? 0 : 1, errors.size());
                    if (!errors.isEmpty()) {
                      assertTrue(
                          errors
                              .get(0)
                              .contains(
                                  fault.equals("formula")
                                      ? "missing_cost_variable"
                                      : "Injected coordinator failure"));
                    }
                    project.setProgressHandler(null);
                    parameters.getCostFunctions().setProperty("mv.1,1", "BASECOST");
                    assertTrue(network.computeCosts(1, 1, (byte) 0, 2));
                  } finally {
                    project.setProgressHandler(null);
                    network.dispose();
                  }
                }
              }));
    }
    return tests;
  }

  private void exercise(String fault) throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      final AssignmentParameters parameters = project.parameters(2);
      if (fault.equals("formula")) {
        parameters.getCostFunctions().setProperty("mv.1,1.1", "missing_cost_variable + 1");
      }
      final VirtualNetwork network = new VirtualNetwork(parameters);
      assertTrue(network.generate());
      network.setGroups(new byte[] {1, 2});
      network.setMaxClass((byte) 0);
      final WorkQueue firstQueue = new WorkQueue();
      final WorkQueue secondQueue = new WorkQueue();
      final CostParserWorker first = new CostParserWorker(firstQueue);
      final CostParserWorker peer = new CostParserWorker(secondQueue);
      final CostParserWorker[] workers = {first, peer};
      final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
      first.setUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
      peer.setUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
      final CountDownLatch peerEntered = new CountDownLatch(1);
      final CountDownLatch peerWait = new CountDownLatch(1);
      final AtomicBoolean peerInterrupted = new AtomicBoolean();
      project.setProgressHandler(
          text -> {
            if (Thread.currentThread() == peer) {
              peerEntered.countDown();
              try {
                peerWait.await(8, TimeUnit.SECONDS);
              } catch (InterruptedException expected) {
                peerInterrupted.set(true);
                Thread.currentThread().interrupt();
              }
              return false;
            }
            if (Thread.currentThread() == first) {
              try {
                assertTrue(peerEntered.await(5, TimeUnit.SECONDS));
              } catch (InterruptedException unexpected) {
                throw new IllegalStateException(unexpected);
              }
              if (fault.equals("exception")) {
                throw new IllegalStateException("Injected cost-worker failure");
              }
              if (fault.equals("error")) {
                throw new AssertionError("Injected cost-worker failure");
              }
              return !fault.equals("cancel");
            }
            return true;
          });
      firstQueue.addWork(
          new CostParserWorkerParameters(
              workers,
              project,
              1,
              (byte) 0,
              (byte) 0,
              network,
              new CostParser(parameters.getCostFunctions(), project, 1, (byte) 1, (byte) 0, -1)));
      secondQueue.addWork(
          new CostParserWorkerParameters(
              workers,
              project,
              1,
              (byte) 0,
              (byte) 1,
              network,
              new CostParser(parameters.getCostFunctions(), project, 1, (byte) 2, (byte) 0, -1)));
      firstQueue.addWork(WorkQueue.NO_MORE_WORK);
      secondQueue.addWork(WorkQueue.NO_MORE_WORK);
      try {
        peer.start();
        first.start();
        first.join(5000);
        peer.join(1000);
        assertFalse(first.isAlive(), "Failing worker must terminate");
        assertFalse(peer.isAlive(), "Cancellation must wake the peer promptly");
        assertTrue(first.isCancelled());
        assertTrue(peer.isCancelled());
        assertTrue(peerInterrupted.get());
        assertEquals(fault.equals("error") ? 1 : 0, uncaught.size(), uncaught.toString());
        if (fault.equals("cancel")) {
          assertNull(first.getErrorMessage());
          assertNull(peer.getErrorMessage());
        } else {
          final String expected =
              fault.equals("formula") ? "missing_cost_variable" : "Injected cost-worker failure";
          assertTrue(first.getErrorMessage().contains(expected), first.getErrorMessage());
          assertTrue(peer.getErrorMessage().contains(expected), peer.getErrorMessage());
        }
      } finally {
        peerWait.countDown();
        first.cancel(null);
        peer.cancel(null);
        first.interrupt();
        peer.interrupt();
        first.join(5000);
        peer.join(5000);
        project.setProgressHandler(null);
        network.dispose();
      }
    }
  }
}
