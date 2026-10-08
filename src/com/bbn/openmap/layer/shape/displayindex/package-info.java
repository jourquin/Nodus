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
