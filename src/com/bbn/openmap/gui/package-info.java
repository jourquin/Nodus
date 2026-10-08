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
 * OpenMap navigation and layer controls adapted to Nodus projects.
 *
 * <p>{@link com.bbn.openmap.gui.NodusOMControlPanel} assembles the navigation controls and the
 * Nodus layer panel. {@link com.bbn.openmap.gui.NodusLayersPanel} protects required network layers
 * and hides the internal drawing layer from the layer list.
 *
 * <p>{@link com.bbn.openmap.gui.NodusEditLayersDlg} manages project node and link layers, including
 * adding existing shapefiles and creating empty layers from a template.
 */
package com.bbn.openmap.gui;
