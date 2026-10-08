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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks bridge credential generation, persistence, and reuse. */
class BridgeCredentialsTest {
  @TempDir Path home;

  @Test
  void generatesCredentialsAndPreservesOtherPreferences() throws Exception {
    Properties properties = new Properties();
    properties.setProperty("locale", "fr_FR");

    BridgeCredentials.ensure(home, properties);

    assertTrue(
        properties
            .getProperty(BridgeCredentials.PY4J_TOKEN_PROPERTY)
            .matches("[A-Za-z0-9_-]{43}"));
    int key = Integer.parseInt(properties.getProperty(BridgeCredentials.J4R_KEY_PROPERTY));
    assertTrue(key > 0);
    assertEquals(properties, NodusPreferences.load(home));
    assertEquals("fr_FR", properties.getProperty("locale"));
    if (Files.getFileStore(home).supportsFileAttributeView("posix")) {
      assertEquals(
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(home.resolve(".nodus9.properties")));
    }
  }

  @Test
  void reusesStoredCredentialsAndRestrictsExistingFile() throws Exception {
    Properties properties = new Properties();
    BridgeCredentials.ensure(home, properties);
    String token = properties.getProperty(BridgeCredentials.PY4J_TOKEN_PROPERTY);
    String key = properties.getProperty(BridgeCredentials.J4R_KEY_PROPERTY);
    Path file = home.resolve(".nodus9.properties");
    if (Files.getFileStore(home).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(
          file,
          EnumSet.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ));
    }

    Properties reloaded = NodusPreferences.load(home);
    BridgeCredentials.ensure(home, reloaded);

    assertEquals(token, reloaded.getProperty(BridgeCredentials.PY4J_TOKEN_PROPERTY));
    assertEquals(key, reloaded.getProperty(BridgeCredentials.J4R_KEY_PROPERTY));
    if (Files.getFileStore(home).supportsFileAttributeView("posix")) {
      assertEquals(
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(file));
    }
  }

  @Test
  void replacesOnlyInvalidValues() throws Exception {
    Properties properties = new Properties();
    BridgeCredentials.ensure(home, properties);
    String token = properties.getProperty(BridgeCredentials.PY4J_TOKEN_PROPERTY);
    properties.setProperty(BridgeCredentials.J4R_KEY_PROPERTY, "-1");

    BridgeCredentials.ensure(home, properties);

    assertEquals(token, properties.getProperty(BridgeCredentials.PY4J_TOKEN_PROPERTY));
    assertNotEquals("-1", properties.getProperty(BridgeCredentials.J4R_KEY_PROPERTY));
    assertEquals(properties, NodusPreferences.load(home));
  }

  @Test
  void failedSaveDoesNotPublishUnsavedCredentialsInMemory() {
    Properties properties = new Properties();
    Path missingHome = home.resolve("missing");

    assertThrows(IOException.class, () -> BridgeCredentials.ensure(missingHome, properties));

    assertTrue(properties.isEmpty());
  }
}
