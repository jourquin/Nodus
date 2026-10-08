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
 * Selection of network results and demand values to display on the map.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.results.gui.ResultsDlg} gathers result-display options
 * and delegates queries to {@link edu.uclouvain.core.nodus.compute.results.LinkResults} or {@link
 * edu.uclouvain.core.nodus.compute.results.NodeResults}. It also provides a way to reset the layer
 * display after showing results.
 */
package edu.uclouvain.core.nodus.compute.results.gui;
