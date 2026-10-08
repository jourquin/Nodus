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
 */
package edu.uclouvain.core.nodus.database;
