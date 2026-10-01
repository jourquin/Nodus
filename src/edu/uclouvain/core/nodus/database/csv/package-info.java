/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * CSV import and export for project database tables.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.csv.ImportCSV} reads comma-separated records into an
 * existing table, with an option to skip a header row. {@link
 * edu.uclouvain.core.nodus.database.csv.ExportCSV} writes table contents with optional column
 * headers and escaping for delimiters, quotes and line breaks.
 *
 * <p>The importer uses the destination table metadata for conversion. CSV data does not itself
 * create the destination schema.
 */
package edu.uclouvain.core.nodus.database.csv;
