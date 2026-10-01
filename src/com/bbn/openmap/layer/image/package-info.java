/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
