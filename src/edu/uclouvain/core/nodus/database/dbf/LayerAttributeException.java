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

package edu.uclouvain.core.nodus.database.dbf;

/** Invalid network attributes that should be reported as an actionable project-opening error. */
public final class LayerAttributeException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  /** Creates a data error with the layer, physical record and field in its user-facing message. */
  public LayerAttributeException(String message) {
    super(message);
  }

  /** Retains the underlying DBF decoding failure for diagnostics. */
  public LayerAttributeException(String message, Throwable cause) {
    super(message, cause);
  }
}
