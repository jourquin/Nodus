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
 * A geographic rectangle for highlighting and limiting selected Nodus operations.
 *
 * <p>{@link com.bbn.openmap.layer.highlightedarea.HighlightedAreaLayer} stores and draws a
 * rectangle defined by latitude and longitude bounds. The bounds can be entered in its controls or
 * taken from the current map view.
 *
 * <p>The layer also exposes a coordinate-membership test. Assignment and display options decide
 * whether to use the highlighted area as a filter.
 */
package com.bbn.openmap.layer.highlightedarea;
