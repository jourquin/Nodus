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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.omGraphics.OMPoint;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerTimes;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.CompactShortestPathGraph;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.ReachabilityDijkstra;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Read-only observation is compared with ordinary Dijkstra on both graph representations. */
@ResourceLock("JDBCUtils")
@ResourceLock("NodusC.displayComputingTimes")
@ResourceLock(Resources.SYSTEM_OUT)
class ReachabilityDijkstraTest {
  private static final String KNOWN_SEARCHES =
      "Searches with previously known unreachable destinations";
  private static final String MIXED_SEARCHES =
      "Searches with mixed reachable/unreachable destinations";
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> mixedAndEntirelyUnreachableSearchesHaveExactIndependentEstimates() {
    return storage(
        compact -> {
          try (Harness h = new Harness(compact, true, 1)) {
            h.observer.startSequence(2);
            h.search(1, 2, 7, 7);
            h.search(1, 2, 7, 7);
            h.search(1, 7, 7);
            String report = h.report();
            assertEquals(3, count(report, "Completed Dijkstra searches"));
            assertEquals(3, count(report, "Searches ending with unreachable destinations"));
            assertEquals(2, count(report, KNOWN_SEARCHES));
            assertEquals(1, count(report, "Potentially shortenable searches"));
            assertEquals(1, count(report, "Potentially entirely skippable searches"));
            assertEquals(0, count(report, "Searches excluded from reuse estimates"));
            assertEquals(18, count(report, "Nodes settled"));
            assertEquals(24, count(report, "Edges examined"));
            assertEquals(10, count(report, "Potentially avoidable nodes"));
            assertEquals(12, count(report, "Potentially avoidable edges"));
            assertEquals(5, seconds(report, "Observed Dijkstra time (worker sum)"));
            assertEquals(2, seconds(report, "Potentially avoidable Dijkstra time (worker sum)"));
            assertEquals(2, count(report, MIXED_SEARCHES));
            assertEquals(1, count(report, "Searches with no reachable destination"));
            assertEquals(8, count(report, "Nodes after last reachable destination"));
            assertEquals(8, count(report, "Edges after last reachable destination"));
            assertEquals(14, count(report, "Upper-bound avoidable nodes"));
            assertEquals(16, count(report, "Upper-bound avoidable edges"));
            assertEquals(2, seconds(report, "Time after last reachable destination (worker sum)"));
            assertEquals(1, seconds(report, "Time with no reachable destination (worker sum)"));
            assertEquals(3, seconds(report, "Upper-bound avoidable Dijkstra time (worker sum)"));
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> reachedEmptyAndSourceDestinationsDoNotCreateAvoidableWork() {
    return Stream.of(false, true)
        .flatMap(
            compact ->
                Stream.of("reached", "empty", "source")
                    .map(
                        kind ->
                            dynamicTest(
                                "compact=" + compact + ", " + kind,
                                () -> {
                                  try (Harness h = new Harness(compact, true, 1)) {
                                    h.observer.startSequence(1);
                                    int[] destinations =
                                        kind.equals("reached")
                                            ? new int[] {2, 2}
                                            : kind.equals("source") ? new int[] {1} : new int[0];
                                    h.search(1, destinations);
                                    String report = h.report();
                                    assertEquals(
                                        kind.equals("empty") ? 6 : kind.equals("source") ? 1 : 2,
                                        count(report, "Nodes settled"));
                                    assertEquals(0, count(report, "Potentially avoidable nodes"));
                                    assertEquals(0, count(report, "Upper-bound avoidable nodes"));
                                    assertEquals(0, count(report, "Upper-bound avoidable edges"));
                                    assertEquals(
                                        0, count(report, "Searches with no reachable destination"));
                                    assertEquals(0, count(report, MIXED_SEARCHES));
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> changingTheSourceDiscardsEarlierUnreachableKnowledge() {
    return storage(
        compact -> {
          try (Harness h = new Harness(compact, true, 1)) {
            h.observer.startSequence(1);
            h.search(1, 7);
            h.search(7, 7);
            h.search(7, 2);
            h.search(1, 2);
            String report = h.report();
            assertEquals(4, count(report, "Completed Dijkstra searches"));
            assertEquals(0, count(report, KNOWN_SEARCHES));
            assertEquals(0, count(report, "Searches excluded from reuse estimates"));
            assertEquals(0, count(report, "Potentially entirely skippable searches"));
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> newRestrictionSequenceDiscardsEarlierKnowledgeForTheSameSource() {
    return storage(
        compact -> {
          try (Harness h = new Harness(compact, true, 1)) {
            h.observer.startSequence(1);
            h.search(1, 7);
            h.unblockDestination();
            h.observer.startSequence(1);
            h.search(1, 2, 7);
            assertEquals(2, h.observer.getWeights()[7]);
            String report = h.report();
            assertEquals(0, count(report, KNOWN_SEARCHES));
            assertEquals(0, count(report, "Searches excluded from reuse estimates"));
            assertEquals(0, count(report, "Potentially shortenable searches"));
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> unsafeCostMultipliersDisableReuseButRetainHindsightCounts() {
    return Stream.of(false, true)
        .flatMap(
            compact ->
                Stream.of(
                        -1.0,
                        0.0,
                        0.5,
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY)
                    .map(
                        multiplier ->
                            dynamicTest(
                                "compact=" + compact + ", factor=" + multiplier,
                                () -> {
                                  try (Harness h = new Harness(compact, true, 1)) {
                                    h.observer.startSequence(multiplier);
                                    h.search(1, 2, 7);
                                    h.search(1, 2, 7);
                                    String report = h.report();
                                    assertEquals(
                                        2, count(report, "Searches excluded from reuse estimates"));
                                    assertEquals(0, count(report, KNOWN_SEARCHES));
                                    assertEquals(0, count(report, "Potentially avoidable nodes"));
                                    assertEquals(8, count(report, "Upper-bound avoidable nodes"));
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> negativeOrNanStartingCostsExcludeReuse() {
    return Stream.of(false, true)
        .flatMap(
            compact ->
                Stream.of(-0.5, Double.NaN)
                    .map(
                        cost ->
                            dynamicTest(
                                "compact=" + compact + ", initial=" + cost,
                                () -> {
                                  try (Harness h = new Harness(compact, true, cost)) {
                                    h.observer.startSequence(2);
                                    h.search(1, 2, 7);
                                    h.search(1, 2, 7);
                                    String report = h.report();
                                    assertEquals(
                                        2, count(report, "Searches excluded from reuse estimates"));
                                    assertEquals(0, count(report, KNOWN_SEARCHES));
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> unexpectedImprovementInvalidatesReuseWithoutChangingTheComputedRoute() {
    return storage(
        compact -> {
          try (Harness h = new Harness(compact, true, 1)) {
            h.observer.startSequence(1);
            h.search(1, 2, 7);
            h.unblockDestination(); // Deliberately violates the documented sequence protocol.
            h.search(1, 2, 7);
            assertEquals(2, h.observer.getWeights()[7]);
            h.search(1, 2, 7);
            String report = h.report();
            assertEquals(2, count(report, "Searches excluded from reuse estimates"));
            assertEquals(1, count(report, KNOWN_SEARCHES));
            assertEquals(0, count(report, "Potentially shortenable searches"));
            assertEquals(0, count(report, "Potentially avoidable nodes"));
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> increasedCostsPreserveKnowledgeAndSynchronizeBothGraphCopies() {
    return storage(
        compact -> {
          try (Harness h = new Harness(compact, true, 1)) {
            h.observer.startSequence(2);
            h.search(1, 2, 7);
            h.expected.change(h.expected.heads[1], 8);
            h.actual.change(h.actual.heads[1], 8);
            h.search(1, 2, 7);
            assertEquals(4, h.observer.getWeights()[2]); // The unmarked parallel edge now wins.
            String report = h.report();
            assertEquals(1, count(report, KNOWN_SEARCHES));
            assertEquals(1, count(report, "Potentially shortenable searches"));
            assertEquals(0, count(report, "Searches excluded from reuse estimates"));
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> overflowingPathsRemainObservationsWithoutChangingInfinitySemantics() {
    return storage(
        compact -> {
          try (Harness h = new Harness(compact, true, 1)) {
            // Both individual edges are finite; their sum overflows to positive infinity.
            h.expected.change(h.expected.branch, Double.MAX_VALUE * 0.75);
            h.actual.change(h.actual.branch, Double.MAX_VALUE * 0.75);
            h.expected.change(h.expected.heads[5], Double.MAX_VALUE * 0.75);
            h.actual.change(h.actual.heads[5], Double.MAX_VALUE * 0.75);
            h.observer.startSequence(2);
            h.search(1, 2, 6);
            h.search(1, 2, 6);
            assertEquals(Double.MAX_VALUE, h.observer.getWeights()[6]);
            String report = h.report();
            assertEquals(1, count(report, "Potentially shortenable searches"));
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> failedCallsAreNotRecordedAndDoNotPoisonTheNextSearch() {
    return storage(
        compact -> {
          try (Harness h = new Harness(compact, true, 1)) {
            h.observer.startSequence(1);
            assertThrows(
                IndexOutOfBoundsException.class, () -> h.observer.compute(1, demands(2, 99)));
            h.search(1, 2, 7);
            String report = h.report();
            assertEquals(1, count(report, "Completed Dijkstra searches"));
            assertEquals(6, count(report, "Nodes settled"));
            assertEquals(8, count(report, "Edges examined"));
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> disabledWorkerCountersDoNotReadAClockWhileSearchResultsStayIdentical() {
    return storage(
        compact -> {
          try (Harness h = new Harness(compact, false, 1)) {
            h.observer.startSequence(1);
            h.search(1, 2, 7);
            h.search(1, 7);
            assertEquals("", h.report());
            assertEquals(0, h.fixture.clock.reads());
          }
        });
  }

  private static Stream<DynamicTest> storage(StorageCase action) {
    return Stream.of(false, true)
        .map(compact -> dynamicTest("compact=" + compact, () -> action.run(compact)));
  }

  private interface StorageCase {
    void run(boolean compact) throws Exception;
  }

  private static LinkedList<ODCell> demands(int... destinations) {
    LinkedList<ODCell> result = new LinkedList<>();
    for (int destination : destinations) {
      result.add(new ODCell(1, 1, destination, 1));
    }
    return result;
  }

  private final class Harness implements AutoCloseable {
    final AssignmentTestProject project;
    final ComputingTimesTestSupport fixture;
    final WorkerTimes worker;
    final Graph expected;
    final Graph actual;
    final BinaryHeapDijkstra ordinary;
    final ReachabilityDijkstra observer;

    Harness(boolean compact, boolean enabled, double initialCost) throws Exception {
      project = new AssignmentTestProject(directory, 7, new double[][] {{11, 1, 2, 1, 0}});
      fixture = new ComputingTimesTestSupport(enabled);
      fixture.clock.stepEachRead();
      worker = fixture.audit.newWorkerTimes();
      VirtualNetwork network = new MappingNetwork(project);
      expected = new Graph(compact, initialCost);
      actual = new Graph(compact, initialCost);
      ordinary = new BinaryHeapDijkstra(expected.heads, network, expected.copy);
      observer = new ReachabilityDijkstra(actual.heads, network, worker, actual.copy);
    }

    void search(int source, int... destinations) {
      ordinary.compute(source, demands(destinations));
      observer.compute(source, demands(destinations));
      assertArrayEquals(ordinary.getWeights(), observer.getWeights());
      assertArrayEquals(ordinary.getPredecessors(), observer.getPredecessors());
      assertEquals(expected.state(), actual.state());
    }

    void unblockDestination() {
      expected.change(expected.blocked, 2);
      actual.change(actual.blocked, 2);
    }

    String report() {
      worker.close();
      return fixture.report(true);
    }

    @Override
    public void close() throws Exception {
      worker.close();
      try {
        project.close();
      } finally {
        fixture.close();
      }
    }
  }

  /**
   * Supplies only the real-to-virtual destination mapping; the graph is independently specified.
   */
  private static final class MappingNetwork extends VirtualNetwork {
    private final VirtualNodeList[] lists = new VirtualNodeList[7];

    MappingNetwork(AssignmentTestProject project) {
      super(project.parameters(1));
      for (int i = 0; i < lists.length; i++) {
        lists[i] = new VirtualNodeList(i + 1, NodusC.HANDLING_LOAD_UNLOAD, new OMPoint(0, 0));
        lists[i].setUnloadingVirtualNodeId(i + 1);
      }
    }

    @Override
    public VirtualNodeList[] getVirtualNodeLists() {
      return lists;
    }

    @Override
    public int getNodeIndexInVirtualNodeList(int realNodeId, boolean loading) {
      return realNodeId - 1;
    }
  }

  private static final class Graph {
    final AdjacencyNode[] heads = new AdjacencyNode[8];
    final CompactShortestPathGraph copy;
    final AdjacencyNode blocked;
    final AdjacencyNode branch;

    Graph(boolean compact, double initialCost) {
      for (int i = 1; i < heads.length; i++) {
        heads[i] = node(i);
      }
      edge(1, 2, 1);
      edge(1, 2, 4);
      branch = edge(1, 5, 10);
      blocked = edge(1, 7, Double.MAX_VALUE);
      edge(2, 3, 1);
      edge(2, 1, 0);
      edge(3, 4, initialCost);
      edge(5, 6, 1);
      copy = compact ? new CompactShortestPathGraph(heads) : null;
    }

    private static AdjacencyNode node(int id) {
      return new AdjacencyNode(new VirtualNode(id, id, 0, (byte) 1, (byte) 1, (short) 0, 0, 0));
    }

    private AdjacencyNode edge(int from, int to, double cost) {
      AdjacencyNode edge = heads[from];
      while (edge.nextNode != null) {
        edge = edge.nextNode;
      }
      edge.edgeWeight = edge.originalEdgeWeight = cost;
      edge.endVirtualNode = to;
      edge.nextNode = node(to);
      return edge;
    }

    void change(AdjacencyNode edge, double cost) {
      edge.edgeWeight = cost;
      if (copy != null) {
        copy.copyWeight(edge);
      }
    }

    List<String> state() {
      List<String> result = new ArrayList<>();
      for (AdjacencyNode head : heads) {
        for (AdjacencyNode edge = head; edge != null; edge = edge.nextNode) {
          result.add(
              edge.virtualNodeNum
                  + ":"
                  + edge.edgeWeight
                  + ":"
                  + edge.originalEdgeWeight
                  + ":"
                  + edge.inCurrentTree
                  + ":"
                  + edge.isIncreased
                  + ":"
                  + edge.isNodeToReach);
        }
      }
      return result;
    }
  }
}
