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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Checks update decisions across release tags without making network requests. */
class GitHubReleaseTest {

  @Test
  void olderReleaseIsNotAnUpdate() {
    assertTrue(GitHubRelease.compareVersions("v8.6", "9.0") < 0);
    assertTrue(GitHubRelease.compareVersions("9.0.1", "9.1") < 0);
  }

  @Test
  void newerReleaseIsAnUpdate() {
    assertTrue(GitHubRelease.compareVersions("v9.1", "9.0") > 0);
    assertTrue(GitHubRelease.compareVersions("10.0", "9.99") > 0);
  }

  @Test
  void equivalentNumericTagsUseBuildComparison() {
    assertEquals(0, GitHubRelease.compareVersions("v9.0.0", "9.0"));
    assertEquals(0, GitHubRelease.compareVersions("V09.00", "9.0"));
  }

  @Test
  void unrecognizedTagsAreNotSilentlyOrdered() {
    assertThrows(
        IllegalArgumentException.class,
        () -> GitHubRelease.compareVersions("release-nine", "9.0"));
  }
}
