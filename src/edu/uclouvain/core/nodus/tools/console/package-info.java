/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * A desktop console for Nodus standard output and error messages.
 *
 * <p>{@link edu.uclouvain.core.nodus.tools.console.NodusConsole} redirects {@code System.out} and
 * {@code System.err} to a text window with clear and save controls. Its cleanup releases the
 * console resources and restores the previous streams when they are still owned by this console.
 *
 * <p>The console displays program output. SQL command execution belongs to {@link
 * edu.uclouvain.core.nodus.database.gui.SQLConsole}.
 */
package edu.uclouvain.core.nodus.tools.console;
