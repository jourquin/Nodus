/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus;

/** Gives layer code in another package access to the project's save safety check. */
public final class ProjectConnectionStatus {
  private ProjectConnectionStatus() {}

  /** Returns false once the project has lost its MySQL/MariaDB session. */
  public static boolean canSaveNetworkEdits(NodusProject project) {
    return project.canSaveNetworkEdits();
  }
}
