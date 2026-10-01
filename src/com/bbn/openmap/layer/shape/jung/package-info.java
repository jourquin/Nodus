/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Node and edge data for graphical inspection of the virtual transport network.
 *
 * <p>{@link com.bbn.openmap.layer.shape.jung.JungVirtualNode} identifies a displayed node by its
 * real-network location, mode, means and service. {@link
 * com.bbn.openmap.layer.shape.jung.JungVirtualLink} carries edge attributes such as quantity, unit
 * cost, vehicles, time and operation type.
 *
 * <p>The JUNG viewer uses these objects to represent queried network results. The assignment graph
 * itself is defined in {@link edu.uclouvain.core.nodus.compute.virtual}.
 */
package com.bbn.openmap.layer.shape.jung;
