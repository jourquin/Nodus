/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
