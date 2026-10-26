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

import static edu.uclouvain.core.nodus.compute.assign.ComputingTimesTestSupport.count;
import static edu.uclouvain.core.nodus.compute.assign.ComputingTimesTestSupport.seconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.OutsidePhase;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.Phase;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.ReachabilityMetric;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerPhase;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerTimes;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Elapsed-time accounting uses explicit timestamps, never wall-clock performance limits. */
@ResourceLock("NodusC.displayComputingTimes")
@ResourceLock(Resources.SYSTEM_OUT)
@Timeout(20)
class AssignmentComputingTimesTest {
  @Test
  void disabledAuditDoesNotReadTheClockOrPrintEvenIfTheGlobalSwitchChanges() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(false)) {
      AssignmentComputingTimes audit = fixture.audit;
      NodusC.displayComputingTimes = true;
      assertEquals(0, audit.start());
      assertEquals(0, audit.startPaths());
      audit.add(Phase.DATABASE, 0);
      audit.endPaths(0, 0);
      audit.beginFinalization();
      try (var scope = audit.outside(OutsidePhase.NETWORK);
          WorkerTimes worker = audit.newWorkerTimes()) {
        assertFalse(worker.isEnabled());
        assertEquals(0, worker.startMeasurement());
        worker.includePhases(WorkerPhase.DIJKSTRA);
        worker.startPhase(WorkerPhase.DIJKSTRA);
        final long output = worker.startPathOutput();
        worker.endPathOutput(output);
        worker.endPhase();
        worker.addReachability(ReachabilityMetric.SEARCHES, 1);
        worker.addReverseSearch(true, 10, 20);
        worker.addReversePrepass(1, 2, 10, 20);
      }
      assertEquals(0, audit.getThreadDatabaseTime());
      assertEquals("", fixture.report(true));
      assertEquals(0, fixture.clock.reads());
    }
  }

  @Test
  void nestedOutsideScopesExcludeActiveJobsAndPartitionTheWholeRun() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      AssignmentComputingTimes audit = fixture.audit;
      NodusC.displayComputingTimes = false; // The enabled setting was sampled at run start.
      fixture.clock.at(1);
      final var network = audit.outside(OutsidePhase.NETWORK);
      fixture.clock.at(3);
      final var costs = audit.outside(OutsidePhase.COSTS);
      fixture.clock.at(5);
      final long job = audit.startPaths();
      fixture.clock.at(7);
      costs.close();
      fixture.clock.at(8);
      audit.endPaths(job, 0);
      fixture.clock.at(10);
      network.close();
      fixture.clock.at(11);
      audit.beginFinalization();
      fixture.clock.at(12);
      final var finalization = audit.outside(OutsidePhase.PATH_FINALIZATION);
      fixture.clock.at(13);
      final var commit = audit.outside(OutsidePhase.DATABASE_COMMIT);
      fixture.clock.at(15);
      commit.close();
      fixture.clock.at(16);
      finalization.close();
      finalization.close();
      fixture.clock.at(18);
      String report = fixture.report(true);
      assertEquals(18, seconds(report, "Total elapsed"));
      assertEquals(3, seconds(report, "Paths and flow assignment (wall, includes path writes)"));
      assertEquals(15, seconds(report, "Outside parallel assignment (wall)"));
      assertEquals(1, seconds(report, "Other assignment preparation"));
      assertEquals(4, seconds(report, "Network initialization and generation"));
      assertEquals(2, seconds(report, "Cost parsing and evaluation"));
      assertEquals(1, seconds(report, "Other coordination between worker jobs"));
      assertEquals(3, seconds(report, "Other finalization"));
      assertEquals(2, seconds(report, "Other path-output finalization"));
      assertEquals(2, seconds(report, "Database commits"));
    }
  }

  @Test
  void overlappingWorkersHaveUnionWallTimeAndSubtractOnlyTheirOwnDatabaseCalls() throws Exception {
    ExecutorService first = Executors.newSingleThreadExecutor();
    ExecutorService second = Executors.newSingleThreadExecutor();
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      AssignmentComputingTimes audit = fixture.audit;
      // Database time before the first job must not be subtracted from that job again.
      final long prior = on(first, audit::start);
      fixture.clock.at(1);
      on(
          first,
          () -> {
            audit.add(Phase.DATABASE, prior);
            return null;
          });
      fixture.clock.at(2);
      final long firstBefore = on(first, audit::getThreadDatabaseTime);
      final long firstJob = on(first, audit::startPaths);
      fixture.clock.at(4);
      final long secondBefore = on(second, audit::getThreadDatabaseTime);
      final long secondJob = on(second, audit::startPaths);
      final long reads = fixture.clock.reads();
      on(
          second,
          () -> {
            try (var ignored = audit.outside(OutsidePhase.DEMAND)) {
              audit.beginFinalization(); // A worker cannot change coordinator attribution.
            }
            return null;
          });
      assertEquals(reads, fixture.clock.reads());
      fixture.clock.at(5);
      final long firstWrite = on(first, audit::start);
      fixture.clock.at(7);
      on(
          first,
          () -> {
            audit.add(Phase.DATABASE, firstWrite);
            return null;
          });
      fixture.clock.at(8);
      final long secondWrite = on(second, audit::start);
      fixture.clock.at(9);
      on(
          first,
          () -> {
            audit.endPaths(firstJob, firstBefore);
            return null;
          });
      fixture.clock.at(10);
      on(
          second,
          () -> {
            audit.add(Phase.DATABASE, secondWrite);
            return null;
          });
      fixture.clock.at(12);
      on(
          second,
          () -> {
            audit.endPaths(secondJob, secondBefore);
            return null;
          });
      fixture.clock.at(13);
      final var commit = audit.outside(OutsidePhase.DATABASE_COMMIT);
      final long coordinatorWrite = audit.start();
      fixture.clock.at(14);
      audit.add(Phase.DATABASE, coordinatorWrite);
      commit.close();
      audit.beginFinalization();
      fixture.clock.at(15);
      assertEquals(1_000_000_000L, audit.getThreadDatabaseTime());
      String report = fixture.report(true);
      assertEquals(10, seconds(report, "Paths and flow assignment (wall, includes path writes)"));
      assertEquals(
          11, seconds(report, "Paths and flow assignment (worker sum, excludes DB calls)"));
      assertEquals(6, seconds(report, "Database writing (writer calls)"));
      assertEquals(5, seconds(report, "Outside parallel assignment (wall)"));
      assertEquals(15, seconds(report, "Total elapsed"));
      assertFalse(report.contains("Demand loading and preparation:"));
    } finally {
      first.shutdownNow();
      second.shutdownNow();
      assertTrue(first.awaitTermination(5, TimeUnit.SECONDS));
      assertTrue(second.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void nestedPathOutputCountsOnceAndFailedStagesAreMergedOnce() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      WorkerTimes worker = fixture.audit.newWorkerTimes();
      assertThrows(
          IllegalStateException.class,
          () -> {
            try (WorkerTimes ignored = worker) {
              worker.includePhases(WorkerPhase.ASTAR);
              fixture.clock.at(1);
              worker.startPhase(WorkerPhase.RECONSTRUCTION);
              fixture.clock.at(3);
              final long output = worker.startPathOutput();
              fixture.clock.at(4);
              final long nested = worker.startPathOutput();
              assertEquals(0, nested);
              fixture.clock.at(6);
              worker.endPathOutput(nested);
              fixture.clock.at(7);
              worker.endPathOutput(output);
              fixture.clock.at(9);
              worker.endPhase();
              fixture.clock.at(10);
              worker.startPhase(WorkerPhase.MODAL_SPLITTING);
              fixture.clock.at(12);
              final long otherOutput = worker.startPathOutput();
              fixture.clock.at(13);
              worker.endPathOutput(otherOutput);
              fixture.clock.at(15);
              throw new IllegalStateException("Failed modal split");
            }
          });
      worker.close();
      String report = fixture.report(false);
      assertTrue(report.contains("failed/cancelled"));
      assertEquals(4, seconds(report, "Path reconstruction"));
      assertEquals(4, seconds(report, "Modal splitting and path filtering"));
      assertEquals(5, seconds(report, "Path output (includes DB calls and writer waits)"));
      assertEquals(0, seconds(report, "Improved A*"));
      assertFalse(report.contains("Dijkstra:"));
    }
  }

  @Test
  void sequentialMeasurementsIncludeFinallyBlocksAfterFailure() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      AssignmentComputingTimes audit = fixture.audit;
      fixture.clock.at(1);
      final long network = audit.start();
      fixture.clock.at(4);
      audit.add(Phase.NETWORK, network);
      final long costs = audit.start();
      fixture.clock.at(7);
      audit.add(Phase.COSTS, costs);
      fixture.clock.at(8);
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            final long failedCosts = audit.start();
            try {
              fixture.clock.at(10);
              throw new IllegalArgumentException("bad cost");
            } finally {
              audit.add(Phase.COSTS, failedCosts);
            }
          });
      fixture.clock.at(11);
      String report = fixture.report(false);
      assertEquals(3, seconds(report, "Virtual network generation (wall)"));
      assertEquals(5, seconds(report, "Cost parser (wall)"));
      assertEquals(11, seconds(report, "Total elapsed"));
    }
  }

  @Test
  void finishingAnActiveRunCountsItsPartialWallTimeAndSavesOnlyOnce() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      fixture.clock.at(2);
      final long job = fixture.audit.startPaths();
      fixture.clock.at(7);
      String report = fixture.report(false);
      assertEquals(5, seconds(report, "Paths and flow assignment (wall, includes path writes)"));
      assertEquals(2, seconds(report, "Outside parallel assignment (wall)"));
      final long reads = fixture.clock.reads();
      fixture.audit.endPaths(job, 0);
      fixture.audit.add(Phase.COSTS, 0);
      assertEquals("", fixture.report(false));
      assertEquals(reads, fixture.clock.reads());
    }
  }

  @Test
  void restartingClearsAllTotalsAndRejectsScopesAndWorkersFromThePreviousRun() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      AssignmentComputingTimes audit = fixture.audit;
      final long database = audit.start();
      fixture.clock.at(1);
      audit.add(Phase.DATABASE, database);
      final var oldScope = audit.outside(OutsidePhase.NETWORK);
      WorkerTimes oldWorker = audit.newWorkerTimes();
      oldWorker.startPhase(WorkerPhase.DIJKSTRA);
      oldWorker.addReachability(ReachabilityMetric.SEARCHES, 10);
      oldWorker.addReverseSearch(true, 100, 200);
      oldWorker.addReversePrepass(10, 20, 100, 200);
      fixture.clock.at(10);
      audit.startAssignment();
      final var currentScope = audit.outside(OutsidePhase.COSTS);
      fixture.clock.at(11);
      oldScope.close();
      oldWorker.close();
      WorkerTimes currentWorker = audit.newWorkerTimes();
      currentWorker.startPhase(WorkerPhase.ASTAR);
      currentWorker.addReachability(ReachabilityMetric.SEARCHES, 2);
      currentWorker.addReverseSearch(false, 5, 7);
      currentWorker.addReversePrepass(2, 3, 5, 7);
      fixture.clock.at(12);
      currentWorker.close();
      currentScope.close();
      assertEquals(0, audit.getThreadDatabaseTime());
      fixture.clock.at(13);
      String report = fixture.report(true);
      assertEquals(3, seconds(report, "Total elapsed"));
      assertEquals(2, seconds(report, "Cost parsing and evaluation"));
      assertEquals(0, seconds(report, "Database writing (writer calls)"));
      assertFalse(report.contains("Dijkstra:"), report);
      assertEquals(1, seconds(report, "Improved A*"));
      assertEquals(1, count(report, "Improved A* searches"));
      assertFalse(report.contains("<td>Dijkstra searches:</td>"), report);
      assertEquals(2, count(report, "Completed Dijkstra searches"));
      assertEquals(1, count(report, "Completed reverse searches"));
      assertEquals(0, count(report, "Stopped after reaching required origins"));
      assertEquals(5, count(report, "Reverse nodes settled"));
      assertEquals(7, count(report, "Reverse edges scanned"));
      assertEquals(1, count(report, "Completed reachability prepasses"));
      assertEquals(5, count(report, "Required origins checked"));
      assertEquals(3, count(report, "Required origins proven unreachable"));
      assertEquals(0, count(report, "Reverse searches skipped after prepass"));
      assertFalse(report.contains("Network initialization and generation:"), report);
    }
  }

  @Test
  void workerSearchCountersAggregateOnceAndZeroDenominatorsPrintAsUnavailable() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      WorkerTimes first = fixture.audit.newWorkerTimes();
      WorkerTimes second = fixture.audit.newWorkerTimes();
      first.addReachability(ReachabilityMetric.SEARCHES, 2);
      second.addReachability(ReachabilityMetric.SEARCHES, 3);
      first.startPhase(WorkerPhase.DIJKSTRA);
      first.endPhase();
      first.startPhase(WorkerPhase.DIJKSTRA);
      first.endPhase();
      second.startPhase(WorkerPhase.DIJKSTRA);
      first.close();
      second.close();
      first.close();
      String report = fixture.report(true);
      assertEquals(5, count(report, "Completed Dijkstra searches"));
      assertEquals(3, count(report, "Dijkstra searches"));
      assertTrue(report.contains("n/a"));
      assertFalse(report.contains("NaN"));
      assertFalse(report.contains("Infinity"));
      fixture.clock.at(10);
      fixture.audit.startAssignment();
      fixture.clock.at(11);
      String next = fixture.report(true);
      assertFalse(next.contains("unreachable-destination diagnostic"));
      assertFalse(next.contains("Assignment breakdown"));
    }
  }

  @Test
  void reverseWorkCountersMergeOnceAndResetBetweenAssignments() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      WorkerTimes first = fixture.audit.newWorkerTimes();
      WorkerTimes second = fixture.audit.newWorkerTimes();
      first.addReverseSearch(true, 10, 20);
      first.addReverseSearch(false, 100, 200);
      second.addReverseSearch(true, 5, 6);
      first.close();
      second.close();
      first.close();
      String report = fixture.report(true);
      assertEquals(3, count(report, "Completed reverse searches"));
      assertEquals(2, count(report, "Stopped after reaching required origins"));
      assertEquals(1, count(report, "Exhausted finite frontier"));
      assertEquals(115, count(report, "Reverse nodes settled"));
      assertEquals(226, count(report, "Reverse edges scanned"));
      fixture.audit.startAssignment();
      assertFalse(fixture.report(true).contains("Reverse Dijkstra work"));
    }
  }

  @Test
  void prepassCountersMergeOnceIncludingEntirelySkippedWeightedSearches() {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
      WorkerTimes first = fixture.audit.newWorkerTimes();
      WorkerTimes second = fixture.audit.newWorkerTimes();
      first.addReversePrepass(2, 3, 10, 20);
      second.addReversePrepass(0, 4, 30, 40);
      first.close();
      second.close();
      first.close();
      String report = fixture.report(true);
      assertEquals(2, count(report, "Completed reachability prepasses"));
      assertEquals(9, count(report, "Required origins checked"));
      assertEquals(7, count(report, "Required origins proven unreachable"));
      assertEquals(1, count(report, "Reverse searches skipped after prepass"));
      assertEquals(40, count(report, "Prepass nodes visited"));
      assertEquals(60, count(report, "Prepass edges scanned"));
      assertEquals(0, count(report, "Completed reverse searches"));
      fixture.audit.startAssignment();
      assertFalse(fixture.report(true).contains("Reverse reachability prepass"));
    }
  }

  private static <T> T on(ExecutorService worker, Callable<T> action) throws Exception {
    return worker.submit(action).get(5, TimeUnit.SECONDS);
  }
}
