/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Computation and display data attached to physical network nodes and links.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.real.RealNetworkObject} associates a network graphic
 * with its source record, label, displayed result and size. {@link
 * edu.uclouvain.core.nodus.compute.real.RealNode} represents a node, while {@link
 * edu.uclouvain.core.nodus.compute.real.RealLink} adds physical length, speed, service membership
 * and vehicle-flow information.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.real.RealLinkInitializer} initializes link data from
 * shape records and geometry. The physical network remains distinct from the
 * mode/means/service-specific graph in {@link edu.uclouvain.core.nodus.compute.virtual}.
 */
package edu.uclouvain.core.nodus.compute.real;
