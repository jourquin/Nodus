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
 * Nodus graphic styles and scale-dependent display detail for map polylines.
 *
 * <p>{@link com.bbn.openmap.omGraphics.NodusDrawingAttributes} and {@link
 * com.bbn.openmap.omGraphics.NodusOMGraphic} store node size/shape and alternative colors for
 * displaying positive and negative results. The latter is a style holder rather than an
 * independently rendered network object.
 *
 * <p>{@link com.bbn.openmap.omGraphics.MapPolylineDetail} caches simplified display coordinates for
 * supported projections while retaining the original geographic geometry and object identity. Its
 * cache belongs to a layer preparation worker and must be discarded after geometry edits.
 */
package com.bbn.openmap.omGraphics;
