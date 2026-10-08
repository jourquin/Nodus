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
 * Reusable Swing dialogs, table models, layouts and interface helpers.
 *
 * <p>{@link edu.uclouvain.core.nodus.swing.EscapeDialog} adds Escape-key closing to dialogs. {@link
 * edu.uclouvain.core.nodus.swing.GUIUtils} installs localized tooltips and keeps dialogs on screen.
 * Font selection, vertical layout and window-stacking helpers support the surrounding application
 * interfaces.
 *
 * <p>{@link edu.uclouvain.core.nodus.swing.GridSwing} supplies query-result data to a table, while
 * {@link edu.uclouvain.core.nodus.swing.TableSorter} maps sorted view rows onto an existing model.
 * {@link edu.uclouvain.core.nodus.swing.SingleInstanceMessagePane} avoids repeated task messages
 * until its state is reset.
 */
package edu.uclouvain.core.nodus.swing;
