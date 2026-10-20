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
 * Database access, project layer files and network integrity checks.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.JDBCUtils} provides table, field and index
 * operations, database metadata and value conversions. {@link
 * edu.uclouvain.core.nodus.database.JDBCField} and {@link
 * edu.uclouvain.core.nodus.database.JDBCIndex} describe schema elements used by those operations.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.ProjectFilesTools} creates and checks project layer
 * files. {@link edu.uclouvain.core.nodus.database.ShapeIntegrityTester} checks network identifier
 * uniqueness and link endpoint references. Format-specific table interchange and SQL/statistics
 * dialogs live in the subpackages.
 *
 * <p>Project opening can repair mandatory numeric DBF formats after checking every physical row for
 * exact representability, then obtaining one confirmation per affected layer. Numeric and
 * unambiguous numeric-text fields can change type, width or scale without rounding or truncation.
 * Other columns, row order, deletion flags, encoding metadata and geometry remain intact. Repairs
 * are staged and keep the original bytes in a permanent {@code <layer>.dbf.bak}; existing backups
 * are preserved by choosing numbered {@code .bak} filenames. Declining cancels project opening, and
 * non-convertible schemas or data retain the normal validation error.
 */
package edu.uclouvain.core.nodus.database;
