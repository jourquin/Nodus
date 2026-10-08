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
