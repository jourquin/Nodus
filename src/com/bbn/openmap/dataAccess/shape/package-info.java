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
 * DBF table models and schema editors adapted to Nodus shapefile layers.
 *
 * <p>{@link com.bbn.openmap.dataAccess.shape.NodusDbfTableModel} connects the DBF editor to a Nodus
 * layer. {@link com.bbn.openmap.dataAccess.shape.NodusMetaDbfTableModel} controls structural edits,
 * including field-name uniqueness, existing field types and date-field dimensions. Its save
 * controls track whether the structure has changed.
 *
 * <p>These are Nodus extensions to the OpenMap table models. Database import and export are handled
 * separately by {@link edu.uclouvain.core.nodus.database.dbf}.
 */
package com.bbn.openmap.dataAccess.shape;
