/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Shortest-path searches and worker-local graph structures for assignment.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra} and {@link
 * edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapAStar} compute path weights and
 * predecessor information on the virtual-network adjacency structure. {@link
 * edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode} retains the connection to
 * virtual links for path reconstruction and volume updates.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.assign.shortestpath.CompactShortestPathGraph} and its
 * heap helper reduce per-search object traversal. Their topology and mutable cost state belong to a
 * worker job; cost changes must be copied before another search. {@link
 * edu.uclouvain.core.nodus.compute.assign.shortestpath.ReachabilityDijkstra} adds diagnostic
 * observations without changing the chosen paths.
 */
package edu.uclouvain.core.nodus.compute.assign.shortestpath;
