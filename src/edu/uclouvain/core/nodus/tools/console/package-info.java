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
 * A desktop console for Nodus standard output and error messages.
 *
 * <p>{@link edu.uclouvain.core.nodus.tools.console.NodusConsole} redirects {@code System.out} and
 * {@code System.err} to a text window with clear and save controls. Its cleanup releases the
 * console resources and restores the previous streams when they are still owned by this console.
 * Output is buffered without reader threads or blocking pipes, then displayed in bounded batches
 * on Swing's event thread. Clear also discards pending messages; Save includes the current backlog.
 *
 * <p>The console displays program output. SQL command execution belongs to {@link
 * edu.uclouvain.core.nodus.database.gui.SQLConsole}.
 */
package edu.uclouvain.core.nodus.tools.console;
