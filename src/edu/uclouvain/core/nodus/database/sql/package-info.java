/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * SQL syntax highlighting for the Nodus database console.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.sql.NodusSQLTokenMaker} produces RSyntaxTextArea
 * tokens for SQL and Nodus-specific console commands. It is derived from a JFlex scanner and
 * adapted to process editor line buffers.
 *
 * <p>This is a display lexer, not the SQL execution engine. Consult the token maker class
 * documentation before regenerating it, because the generated scanner requires local adaptations.
 */
package edu.uclouvain.core.nodus.database.sql;
