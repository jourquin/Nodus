/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
