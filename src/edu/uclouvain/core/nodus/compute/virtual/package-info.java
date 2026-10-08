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
 * Virtual transport-network construction, mutable flows and assignment result writing.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork} expands physical node and link
 * layers into a graph that distinguishes transport modes, means, services and node operations.
 * {@link edu.uclouvain.core.nodus.compute.virtual.VirtualNode} and {@link
 * edu.uclouvain.core.nodus.compute.virtual.VirtualLink} carry its connectivity, costs, durations
 * and flows. {@link edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList} associates virtual
 * nodes with real-node demand and rules.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.virtual.VirtualNetworkWriter} stores network results.
 * {@link edu.uclouvain.core.nodus.compute.virtual.PathWriter} stores optional path headers and
 * details, accepting batches prepared by worker-owned {@link
 * edu.uclouvain.core.nodus.compute.virtual.PathWriterBuffer} instances. The writer owns database
 * finalization; clearing a worker buffer only discards rows that have not yet been handed to it.
 */
package edu.uclouvain.core.nodus.compute.virtual;
