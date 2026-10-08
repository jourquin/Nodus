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

package edu.uclouvain.core.nodus.utils;

import edu.uclouvain.core.nodus.NodusProject;

/** Gives layer code in another package access to the project's save safety check. */
public final class ProjectConnectionStatus {
  private ProjectConnectionStatus() {}

  /**
   * Checks whether network edits can safely be saved to the project.
   *
   * @param project the project whose database connection is checked
   * @return true if the project connection is healthy; false if it has been lost
   */
  public static boolean canSaveNetworkEdits(NodusProject project) {
    return project.canSaveNetworkEdits();
  }
}
