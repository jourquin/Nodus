/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * In-memory spatial indexes for selecting shapefile graphics within a map viewport.
 *
 * <p>{@link com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndex} defines the
 * geometry-query contract. Shared implementations retain geometry references and bounds, with
 * linear and tree-based selection strategies.
 *
 * <p>{@link com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndexFactory} currently creates
 * a {@link com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndexTree}. The tree preserves
 * source drawing order and represents one geometry revision; the owning layer must invalidate its
 * index when geometry changes.
 */
package com.bbn.openmap.layer.shape.displayindex;
