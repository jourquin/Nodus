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

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Destination-based A* with reverse-Dijkstra baseline distances as remaining-cost bounds.
 *
 * <p>Exact MF groups OD pairs by destination and loading mode. One reverse tree supplies their
 * first routes; its bounds guide subsequent, independently penalized OD searches. Each relaxation
 * uses one precomputed cost bound. Bounds remain valid when costs increase or edges are blocked.
 * Negative markups must disable bounds, retaining Dijkstra ordering. Trees and search storage
 * belong to one worker/group/cost-class job, never a global cache.
 *
 * <p>Bounds are rounded down to guard different forward/reverse floating-point accumulation.
 * Improved labels can reopen settled nodes because rounding can make the bounds inconsistent.
 * Reverse searches stop after reaching their required origins. Unexplored nodes retain a safe bound
 * at the stopping radius and can still participate in penalized detours. Only exhausted reverse
 * searches can prove a node unreachable. Equal-cost paths may differ from the origin-based
 * algorithms, affecting later penalties.
 *
 * <p>References:
 *
 * <ul>
 *   <li>P. E. Hart, N. J. Nilsson and B. Raphael (1968). <a
 *       href="https://doi.org/10.1109/TSSC.1968.300136">A Formal Basis for the Heuristic
 *       Determination of Minimum Cost Paths</a>. <i>IEEE Transactions on Systems Science and
 *       Cybernetics</i>, 4(2), pp. 100-107.
 *   <li>E. W. Dijkstra (1959). <a href="https://doi.org/10.1007/BF01386390">A note on two problems
 *       in connexion with graphs</a>. <i>Numerische Mathematik</i>, 1, pp. 269-271.
 *   <li>D. Delling (2009). <a href="https://publikationen.bibliothek.kit.edu/1000011046">
 *       Engineering and Augmenting Route Planning Algorithms</a>. PhD thesis, Universit&auml;t
 *       Karlsruhe (TH), section 4.1.2: reusing baseline potentials under cost increases.
 * </ul>
 */
public final class BinaryHeapDestinationAStar extends BinaryHeapAStar {
  private final BooleanSupplier cancelled;
  private ReverseDijkstra.Result bounds;
  private boolean useBounds;
  private double sourceRoundoff;

  /**
   * Creates reusable forward search storage without constructing any destination trees.
   *
   * @param graph worker-owned graph with nonnegative current costs
   * @param compact synchronized compact graph, or null for linked queries
   * @param cancelled cancellation check during queries
   */
  public BinaryHeapDestinationAStar(
      AdjacencyNode[] graph, CompactShortestPathGraph compact, BooleanSupplier cancelled) {
    super(graph, compact, true);
    this.cancelled = cancelled;
  }

  /**
   * Selects the original-cost tree for the current destination/loading-mode combination.
   *
   * @param bounds reverse tree constructed from this graph before OD-specific penalties
   * @param useBounds false if costs may fall below the tree's baseline
   */
  public void setBounds(ReverseDijkstra.Result bounds, boolean useBounds) {
    if (bounds.graph != graph) {
      throw new IllegalArgumentException("Destination bounds must belong to this adjacency list");
    }
    this.bounds = bounds;
    this.useBounds = useBounds;
  }

  /** Releases the previous destination's tables before building the next ones. */
  public void clearBounds() {
    bounds = null;
  }

  /** Searches current costs using the selected destination bounds. */
  @Override
  public void compute(int source, int goal) {
    if (bounds == null || bounds.goal != goal) {
      throw new IllegalArgumentException("Select bounds for this destination before searching");
    }
    checkCancelled();
    initializeSingleSource(source);
    sourceRoundoff =
        useBounds && !bounds.isKnownUnreachable(source)
            ? (4.0 * graph.length)
                * Math.ulp(bounds.isReachable(source) ? bounds.distances[source] : bounds.cutoff)
            : 0;
    if (useBounds && bounds.isKnownUnreachable(source)) {
      return;
    }
    int visited = 0;
    int min = extractMin();
    while (min != -1 && minWeight != Double.MAX_VALUE) {
      if ((visited++ & 1023) == 0) {
        checkCancelled();
      }
      weights[min] = minWeight;
      if (min == goal) {
        break;
      }
      if (compactGraph != null) {
        for (int edge = compactGraph.offsets[min]; edge < compactGraph.offsets[min + 1]; edge++) {
          if ((visited++ & 1023) == 0) {
            checkCancelled();
          }
          relax(min, compactGraph.destinations[edge], compactGraph.costs[edge]);
        }
      } else {
        for (AdjacencyNode edge = graph[min]; edge.nextNode != null; edge = edge.nextNode) {
          if ((visited++ & 1023) == 0) {
            checkCancelled();
          }
          relax(min, edge.nextNode.virtualNodeNum, edge.edgeWeight);
        }
      }
      min = extractMin();
    }
  }

  /** Relaxes current penalized costs; baseline-unreachable nodes cannot lead to this goal. */
  @Override
  public void relax(int from, int to, double cost) {
    double candidate = weights[from] + cost;
    if (!(candidate < Double.MAX_VALUE) || (useBounds && bounds.isKnownUnreachable(to))) {
      return;
    }
    if (nodePos[to] == -1) {
      if (!(candidate < weights[to])) {
        return;
      }
      reopen(to);
    }
    double estimate = useBounds ? Math.max(0, bounds.bounds[to] - sourceRoundoff) : 0;
    if (compactHeap != null) {
      if (compactHeap.improveWithEstimate(to, candidate, estimate)) {
        pi[to] = from;
      }
    } else {
      BinaryHeapNode node = getHeapNode(getNodePosition(to));
      if (candidate < node.weight) {
        node.updateGoalEstWeight(estimate);
        decreaseKey(to, candidate);
        pi[to] = from;
      }
    }
  }

  private void checkCancelled() {
    if (cancelled.getAsBoolean()) {
      throw new CancellationException("Destination A* cancelled");
    }
  }
}
