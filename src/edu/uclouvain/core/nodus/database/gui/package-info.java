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
 * The project SQL console, statistical query builder and chart dialogs.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.gui.SQLConsole} executes interactive queries and
 * command batches, presents text or grid results, and handles Nodus commands for table interchange
 * and scenario operations.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.gui.StatDlg} prepares queries for assignment
 * statistics. {@link edu.uclouvain.core.nodus.database.gui.StatPieDlg} displays the corresponding
 * pie charts. SQL syntax coloring is supplied by the token maker in the {@code database.sql}
 * package.
 */
package edu.uclouvain.core.nodus.database.gui;
