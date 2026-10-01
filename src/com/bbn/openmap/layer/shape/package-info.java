/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Shapefile layers for transport-network editing, rendering and result display.
 *
 * <p>{@link com.bbn.openmap.layer.shape.NodusEsriLayer} connects node and link geometry to DBF
 * records, project database tables, styles, labels and displayed results. It provides the record
 * and geometry operations used by the map editor.
 *
 * <p>{@link com.bbn.openmap.layer.shape.FastEsriLayer} limits preparation to visible geometry using
 * a display spatial index and rendering caches. {@link
 * com.bbn.openmap.layer.shape.PoliticalBoundariesLayer} supplies the bundled background boundaries.
 * The {@code gui} and {@code jung} subpackages support attribute editing and virtual-network
 * inspection.
 */
package com.bbn.openmap.layer.shape;
