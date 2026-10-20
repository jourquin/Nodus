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
 *
 * <p>Each successful query replaces the previous numeric values; repeated IDs accumulate and
 * unknown IDs are ignored. Values retain their precision for labels and export (DBF output has
 * two decimal places). Display sizes are bounded and proportional to magnitude, with their sign
 * selecting the existing alternative colors. SQL filters, layer visibility and the optional map
 * extent determine the scaling reference. Zero results remain hidden and can reappear on a later
 * query. A failed SQL read leaves the previous values intact.
 *
 * <p>Playback uses the selected scenario's cost file, a constant scale across complete assignment
 * periods and an exclusive end boundary. Timers, keyboard listeners and temporary label text are
 * released on completion, cancellation, query failure and project cleanup. Timer delivery and
 * completion callbacks run on the EDT; project cleanup waits for that UI cleanup before continuing.
 */
package edu.uclouvain.core.nodus.compute.results;
