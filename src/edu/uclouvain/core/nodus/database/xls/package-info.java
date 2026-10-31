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
 * Spreadsheet import and export for project database tables.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.xls.ImportXLS} and {@link
 * edu.uclouvain.core.nodus.database.xls.ExportXLS} use Apache POI to exchange table data in XLS or
 * XLSX format, with a ZIP/XML codec for ODS.
 *
 * <p>Imports honor DBF-style schema rows, validate against existing tables, or infer a new table
 * from the complete sheet. Explicit header modes use column names and map them to existing tables;
 * headerless imports use column positions, with COL1, COL2, etc. for new tables. Blank cells become
 * SQL NULL. The classes implement database-table interchange rather than general spreadsheet
 * editing.
 */
package edu.uclouvain.core.nodus.database.xls;
