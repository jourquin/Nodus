/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
