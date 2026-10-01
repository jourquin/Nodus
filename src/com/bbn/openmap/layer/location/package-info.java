/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Labels derived from network-layer attributes or computed results.
 *
 * <p>{@link com.bbn.openmap.layer.location.NodusLocationHandler} supplies labels for one Nodus
 * shapefile layer, using a selected DBF field or attached result values. It manages label
 * filtering, appearance and refresh when the source data changes.
 *
 * <p>{@link com.bbn.openmap.layer.location.NodusLocationLayer} combines the handlers for the
 * project node and link layers. Field and filter selection is provided by {@link
 * com.bbn.openmap.layer.location.gui.LocationFieldChooserDlg}.
 */
package com.bbn.openmap.layer.location;
