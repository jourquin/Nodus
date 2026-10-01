/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
