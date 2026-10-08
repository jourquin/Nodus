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
