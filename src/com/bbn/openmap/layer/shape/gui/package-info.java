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
 * Dialogs for editing network attributes and inspecting virtual-network results.
 *
 * <p>{@link com.bbn.openmap.layer.shape.gui.DbfEditDlg} edits one shape record, while {@link
 * com.bbn.openmap.layer.shape.gui.SelectPropertiesDlg} controls filtering, styles and result
 * display for a layer. {@link com.bbn.openmap.layer.shape.gui.ServicesAtShapeDlg} edits service
 * stops at a node.
 *
 * <p>{@link com.bbn.openmap.layer.shape.gui.VirtualNetworkViewerDlg} queries virtual links
 * associated with a real node or link and can open the graphical viewer in {@link
 * com.bbn.openmap.layer.shape.jung.gui}.
 */
package com.bbn.openmap.layer.shape.gui;
