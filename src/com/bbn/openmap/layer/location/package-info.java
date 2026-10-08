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
