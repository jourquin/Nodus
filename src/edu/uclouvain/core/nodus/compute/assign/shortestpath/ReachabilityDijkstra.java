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

package edu.uclouvain.core.nodus.compute.assign.shortestpath;

import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.ReachabilityMetric;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerTimes;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import java.util.BitSet;
import java.util.LinkedList;

/**
 * Observes the work that reusing unreachable destinations could avoid in Fast multi-flow searches.
 *
 * <p>The inherited algorithm still runs to its normal stopping point. The observer only counts
 * finite node extractions and edge examinations, and timestamps the point at which all destinations
 * not previously proven unreachable have been settled. Whole-search estimates include destination
 * setup and heap initialization; partial estimates begin just after the last required extraction.
 * No predecessor, distance, target flag, edge cost or heap ordering is changed by the observer.
 *
 * <p>One instance belongs to one worker job. Call startSequence for each origin/mode/means block,
 * after restricting loading and before its first alternative. Knowledge can be reused only with
 * nonnegative, non-NaN starting costs and a finite multiplier of at least one. Changing
 * restrictions or reducing costs requires a new sequence. Changing the source also clears knowledge
 * defensively.
 *
 * <p>Counts include completed searches only. The ordinary worker-stage timer still includes partial
 * searches interrupted by an exception. All counters are local; clocks are read only at search
 * boundaries and at most one hypothetical stopping point per search, never for every edge.
 */
public final class ReachabilityDijkstra extends BinaryHeapDijkstra {
  private final WorkerTimes workerTimes;
  private final BitSet knownUnreachable = new BitSet();
  private final boolean nonnegativeCosts;
  private boolean reuseAllowed;
  private int previousSource = -1;

  private boolean observing;
  private boolean prepared;
  private boolean stoppingPointReached;
  private boolean entirelySkippable;
  private boolean knowledgeInvalidated;
  private int knownAtStart;
  private int remainingToProve;
  private long nodes;
  private long edges;
  private long nodesAtStop;
  private long edgesAtStop;
  private long searchStarted;
  private long stoppingTime;

  /**
   * Creates an observer for an enabled assignment audit, scanning initial cost validity once.
   *
   * @param graph This worker's graph, whose costs may increase between alternatives.
   * @param virtualNet Network used to resolve requested destinations.
   * @param workerTimes Worker-local counters and clock for this assignment job.
   */
  public ReachabilityDijkstra(
      AdjacencyNode[] graph, VirtualNetwork virtualNet, WorkerTimes workerTimes) {
    super(graph, virtualNet);
    this.workerTimes = workerTimes;
    boolean valid = true;
    for (AdjacencyNode head : graph) {
      for (AdjacencyNode edge = head; edge != null && edge.nextNode != null; edge = edge.nextNode) {
        if (!(edge.edgeWeight >= 0)) {
          valid = false;
        }
      }
    }
    nonnegativeCosts = valid;
  }

  /**
   * Starts independent observations for an origin and a fixed set of loading restrictions.
   *
   * @param costMultiplier Factor applied between alternatives; values below one or non-finite
   *     values disable reuse estimates, while retaining total search/work counts.
   */
  public void startSequence(double costMultiplier) {
    knownUnreachable.clear();
    previousSource = -1;
    reuseAllowed = nonnegativeCosts && Double.isFinite(costMultiplier) && costMultiplier >= 1;
  }

  /** Observes a complete search without changing its stopping condition or its results. */
  @Override
  public void compute(int source, LinkedList<ODCell> demandList) {
    if (source != previousSource || !reuseAllowed) {
      knownUnreachable.clear();
    }
    previousSource = source;
    prepared = false;
    stoppingPointReached = false;
    entirelySkippable = false;
    knowledgeInvalidated = false;
    nodes = 0;
    edges = 0;
    knownAtStart = 0;
    searchStarted = workerTimes.startMeasurement();
    observing = true;
    try {
      super.compute(source, demandList);
    } finally {
      observing = false;
    }
    long finished = workerTimes.startMeasurement();
    recordSearch(finished);

    // Only a completed exhaustive search proves that remaining targets cannot be reached.
    // We inspect only the unique destinations already prepared by the inherited algorithm.
    if (reuseAllowed && nbNodesToReach > 0) {
      for (int i = 0; i < nbMarkedDestinations; i++) {
        int target = markedDestinations[i];
        if (weights[target] == Double.MAX_VALUE) {
          knownUnreachable.set(target);
        }
      }
    }
  }

  /** Counts finite extractions and observes when the last still-possible destination is settled. */
  @Override
  public int extractMin() {
    if (!observing) {
      return super.extractMin();
    }
    if (!prepared) {
      prepareSearch();
    }
    int node = super.extractMin();
    if (node != -1 && minWeight != Double.MAX_VALUE) {
      nodes++;
      if (nodesToReach[node]) {
        if (knownUnreachable.get(node)) {
          // A caller changed the graph outside the declared sequence rules. Discard its estimate.
          knowledgeInvalidated = true;
        } else if (--remainingToProve == 0 && knownAtStart > 0) {
          stoppingPointReached = true;
          nodesAtStop = nodes;
          edgesAtStop = edges;
          stoppingTime = workerTimes.startMeasurement();
        }
      }
    }
    return node;
  }

  /**
   * Counts each examined edge, including blocked or non-improving edges, before normal relaxation.
   */
  @Override
  public void relax(int from, int to, double weight) {
    if (observing) {
      edges++;
    }
    super.relax(from, to, weight);
  }

  /** Uses the inherited destination set after its setup and before the first extraction. */
  private void prepareSearch() {
    prepared = true;
    for (int i = 0; i < nbMarkedDestinations; i++) {
      if (knownUnreachable.get(markedDestinations[i])) {
        knownAtStart++;
      }
    }
    remainingToProve = nbMarkedDestinations - knownAtStart;
    if (knownAtStart > 0 && remainingToProve == 0) {
      entirelySkippable = true;
      stoppingPointReached = true;
      nodesAtStop = 0;
      edgesAtStop = 0;
      stoppingTime = searchStarted;
    }
  }

  /**
   * Adds one completed search to the worker's totals, with no shared lock or per-edge allocation.
   */
  private void recordSearch(long finished) {
    if (knowledgeInvalidated) {
      reuseAllowed = false;
      knownUnreachable.clear();
    }
    workerTimes.addReachability(ReachabilityMetric.SEARCHES, 1);
    workerTimes.addReachability(ReachabilityMetric.SEARCH_TIME, finished - searchStarted);
    workerTimes.addReachability(ReachabilityMetric.NODES, nodes);
    workerTimes.addReachability(ReachabilityMetric.EDGES, edges);
    if (nbNodesToReach > 0) {
      workerTimes.addReachability(ReachabilityMetric.EXHAUSTED_SEARCHES, 1);
    }
    if (knownAtStart > 0) {
      workerTimes.addReachability(ReachabilityMetric.KNOWN_UNREACHABLE_SEARCHES, 1);
    }
    if (!reuseAllowed) {
      workerTimes.addReachability(ReachabilityMetric.EXCLUDED_SEARCHES, 1);
    } else if (stoppingPointReached) {
      workerTimes.addReachability(
          entirelySkippable
              ? ReachabilityMetric.SKIPPABLE_SEARCHES
              : ReachabilityMetric.SHORTENABLE_SEARCHES,
          1);
      workerTimes.addReachability(ReachabilityMetric.AVOIDABLE_NODES, nodes - nodesAtStop);
      workerTimes.addReachability(ReachabilityMetric.AVOIDABLE_EDGES, edges - edgesAtStop);
      workerTimes.addReachability(ReachabilityMetric.AVOIDABLE_TIME, finished - stoppingTime);
    }
  }
}
