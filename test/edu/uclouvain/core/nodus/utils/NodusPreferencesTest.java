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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks preference migration and version isolation using a temporary home directory. */
class NodusPreferencesTest {
  @TempDir Path home;

  @Test
  void legacyPreferencesAreCopiedExactlyAndFutureSavesLeaveTheOriginalIntact() throws Exception {
    Path legacy = home.resolve(".nodus8.properties");
    byte[] original =
        "# Saved settings\r\nlocale=fr_FR\r\ncustom.value=café\r\n"
            .getBytes(StandardCharsets.ISO_8859_1);
    Files.write(legacy, original);

    Properties preferences = NodusPreferences.load(home);
    assertEquals("fr_FR", preferences.getProperty("locale"));
    assertEquals("café", preferences.getProperty("custom.value"));
    assertArrayEquals(original, Files.readAllBytes(home.resolve(".nodus9.properties")));
    assertArrayEquals(original, Files.readAllBytes(legacy));

    preferences.setProperty("locale", "en");
    NodusPreferences.save(home, preferences);
    assertEquals("en", NodusPreferences.load(home).getProperty("locale"));
    assertArrayEquals(original, Files.readAllBytes(legacy));
  }

  @Test
  void existingNodus9PreferencesTakePrecedenceWithoutImportingLegacyKeys() throws Exception {
    Files.writeString(home.resolve(".nodus8.properties"), "locale=fr_FR\nlegacy.only=true\n");
    Path current = home.resolve(".nodus9.properties");
    String saved = "# Current settings\nlocale=en\n";
    Files.writeString(current, saved);

    Properties preferences = NodusPreferences.load(home);
    assertEquals("en", preferences.getProperty("locale"));
    assertFalse(preferences.containsKey("legacy.only"));
    assertEquals(saved, Files.readString(current));
  }

  @Test
  void emptyNodus9FileIsNotReplacedByLegacyPreferences() throws Exception {
    Files.writeString(home.resolve(".nodus8.properties"), "locale=fr_FR\n");
    Path current = Files.createFile(home.resolve(".nodus9.properties"));

    assertTrue(NodusPreferences.load(home).isEmpty());
    assertEquals(0, Files.size(current));
  }

  @Test
  void freshInstallationStartsWithDefaultsAndSavesOnlyNodus9Preferences() throws Exception {
    Properties preferences = NodusPreferences.load(home);
    assertTrue(preferences.isEmpty());
    preferences.setProperty("locale", "en");
    NodusPreferences.save(home, preferences);

    assertEquals(preferences, NodusPreferences.load(home));
    assertTrue(Files.isRegularFile(home.resolve(".nodus9.properties")));
    assertFalse(Files.exists(home.resolve(".nodus8.properties")));
  }
}
