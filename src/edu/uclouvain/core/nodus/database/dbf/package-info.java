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
 * Interchange between DBF files, project database tables and shapefile attributes.
 *
 * <p>{@link edu.uclouvain.core.nodus.database.dbf.ImportDBF} builds database columns from DBF field
 * definitions and imports the file records. {@link edu.uclouvain.core.nodus.database.dbf.ExportDBF}
 * writes database results or an OpenMap DBF table model back to DBF format.
 *
 * <p>This package provides persistence and format conversion. Interactive editing of DBF records
 * and field definitions is handled by the Nodus OpenMap extensions.
 */
package edu.uclouvain.core.nodus.database.dbf;
