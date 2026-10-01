/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Database queries that turn assignment and demand data into map displays.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.results.LinkResults} retrieves assigned volumes,
 * individual paths and time-dependent flows for link rendering. {@link
 * edu.uclouvain.core.nodus.compute.results.NodeResults} retrieves OD quantities for node-based
 * display.
 *
 * <p>These classes connect query results to the current project layers and support view-limited or
 * export workflows. Selection of the desired result is handled by {@link
 * edu.uclouvain.core.nodus.compute.results.gui.ResultsDlg}; assignment and database result writing
 * occur in other computation packages.
 */
package edu.uclouvain.core.nodus.compute.results;
