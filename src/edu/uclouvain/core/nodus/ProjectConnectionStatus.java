/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus;

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
