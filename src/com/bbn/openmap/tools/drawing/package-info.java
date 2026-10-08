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
 * Interactive editing of transport-network nodes, links and service routes.
 *
 * <p>{@link com.bbn.openmap.tools.drawing.NodusOMDrawingTool} adapts the OpenMap drawing workflow
 * to network constraints: links connect existing nodes, geometry edits update the associated layer
 * records, and operations include moving, deleting, transferring and splitting network objects.
 *
 * <p>{@link com.bbn.openmap.tools.drawing.NodusOMDrawingToolLauncher} presents the node and link
 * tools, with loaders that provide Nodus-specific names. Internal location and splitting helpers
 * retain the record and service information needed while applying an edit.
 */
package com.bbn.openmap.tools.drawing;
