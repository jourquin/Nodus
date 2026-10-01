/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
