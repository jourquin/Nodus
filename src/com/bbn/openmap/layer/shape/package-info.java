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
