/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
