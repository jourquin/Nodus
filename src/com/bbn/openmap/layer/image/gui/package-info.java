/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Selection of WMS background layers from a server capabilities tree.
 *
 * <p>{@link com.bbn.openmap.layer.image.gui.WmsLayersChooserDlg} displays the available server
 * layers and the current selection for a {@link com.bbn.openmap.layer.image.NodusWMSLayer}. It uses
 * the parsed capabilities tree to update the requested imagery layers. Server access and map
 * rendering remain responsibilities of the WMS layer.
 */
package com.bbn.openmap.layer.image.gui;
