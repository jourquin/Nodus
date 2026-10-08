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
 * Background map layers for remote tiles and Web Map Service imagery.
 *
 * <p>{@link com.bbn.openmap.layer.image.NodusMapTileLayer} adds cache management and transparency
 * controls to the OpenMap tile layer. {@link com.bbn.openmap.layer.image.NodusGoogleTileLayer} adds
 * a map-type selector.
 *
 * <p>{@link com.bbn.openmap.layer.image.NodusWMSLayer} retrieves server capabilities and opens a
 * chooser for the layers included in image requests. The related {@code capabilities} and {@code
 * gui} subpackages hold its parser and selection dialog. {@link
 * com.bbn.openmap.layer.image.NetUtils} provides a server reachability check.
 */
package com.bbn.openmap.layer.image;
