/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
