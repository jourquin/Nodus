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
 * existing table by position, or maps columns by header name and infers missing tables. {@link
 * edu.uclouvain.core.nodus.database.csv.ExportCSV} writes table contents with optional column
 * headers and escaping for delimiters, quotes and line breaks. Per-command {@link
 * edu.uclouvain.core.nodus.database.csv.CsvOptions} select the charset and delimiter. Legacy calls
 * retain the Java default charset and comma; decoders and encoders reject invalid or
 * unrepresentable characters. An initial Unicode BOM is removed for both import modes. Optional
 * delimiter detection samples at most 64 Ki characters and 20 records, then replays the buffered
 * prefix without another file pass. Ambiguous samples require an explicit separator; complete row
 * validation continues through the remaining input.
 *
 * <p>Imports into existing tables validate records while loading a single pass. Creating a table
 * from headers needs one schema pass: first non-empty values establish the types, and incompatible
 * later values or field counts stop at the first offending record. Text lengths and numeric
 * precision can grow within the same type. Dates and leading-zero identifiers remain text; empty
 * fields in header imports become SQL NULL. Failed imports preserve existing data and transactions.
 */
package edu.uclouvain.core.nodus.database.csv;
