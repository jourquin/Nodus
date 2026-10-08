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
 * Text editing for cost functions and Groovy scripts, with printing support.
 *
 * <p>{@link edu.uclouvain.core.nodus.tools.notepad.NotePad} provides the editor window. {@link
 * edu.uclouvain.core.nodus.tools.notepad.NotePadActions} implements file, search and clipboard
 * operations, and {@link edu.uclouvain.core.nodus.tools.notepad.Print} prints editor contents.
 *
 * <p>{@link edu.uclouvain.core.nodus.tools.notepad.NodusGroovyConsole} extends the editor with
 * script execution on a separate thread. These tools edit text or run scripts; cost-expression
 * evaluation itself is implemented in {@link edu.uclouvain.core.nodus.compute.costs}.
 */
package edu.uclouvain.core.nodus.tools.notepad;
