/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * Center for Operations Research and Econometrics (CORE)
 * http://www.uclouvain.be
 *
 * This file is part of Nodus.
 * Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

/** A converged fit whose cost coefficients are nonnegative (or zero at solver precision). */
final class NonNegativeCostCoefficientException extends IllegalStateException {
  private static final long serialVersionUID = 1L;
  private final int[] modeColumns;

  NonNegativeCostCoefficientException(int[] modeColumns) {
    super(
        "Estimation failed: at least one cost coefficient is zero or positive."
            + " All cost coefficients must be negative.");
    this.modeColumns = modeColumns.clone();
  }

  int[] getModeColumns() {
    return modeColumns.clone();
  }

  static boolean causedBy(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof NonNegativeCostCoefficientException) {
        return true;
      }
    }
    return false;
  }
}
