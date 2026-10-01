/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * The editor for transport-service routes, stops and operating characteristics.
 *
 * <p>{@link edu.uclouvain.core.nodus.services.gui.ServicesDlg} presents service selection,
 * mode/means settings and route-editing controls. It tracks pending changes and displays the state
 * of shortest-path route selection.
 *
 * <p>Map interactions, route construction and persistence are coordinated by {@link
 * edu.uclouvain.core.nodus.services.ServiceHandler}.
 */
package edu.uclouvain.core.nodus.services.gui;
