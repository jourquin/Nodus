/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Excel workbook import and export for project database tables.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.xls.ImportXLS} and {@link
 * edu.uclouvain.core.nodus.database.xls.ExportXLS} use Apache POI to exchange table data in XLS or
 * XLSX format.
 *
 * <p>Imports use an existing table unless the first worksheet row supplies the DBF-style field
 * definitions needed to create one. The classes implement database-table interchange rather than a
 * general spreadsheet editing API.
 */
package edu.uclouvain.core.nodus.database.xls;
