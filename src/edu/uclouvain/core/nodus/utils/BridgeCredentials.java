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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Properties;

/** Creates the credentials used by optional project Python and R bridges. */
public final class BridgeCredentials {

  static final String PY4J_TOKEN_PROPERTY = "bridge.py4j.token";
  static final String J4R_KEY_PROPERTY = "bridge.j4r.key";
  private static final String PREFERENCES_FILE = ".nodus9.properties";
  private static final String LOCK_FILE = ".nodus9.properties.lock";

  private static final SecureRandom RANDOM = new SecureRandom();

  private BridgeCredentials() {}

  /**
   * Reuses existing credentials or saves new ones before a Groovy hook can start a bridge. A
   * separate lock file coordinates simultaneous Nodus processes; the preferences file itself is
   * replaced during saving. In-memory credentials are updated only after a successful save.
   *
   * @param home directory containing the preferences file
   * @param properties preferences to update with the bridge credentials
   * @throws IOException if the preferences file cannot be saved or protected
   */
  public static synchronized void ensure(Path home, Properties properties) throws IOException {
    Path lockFile = home.resolve(LOCK_FILE);
    try (FileChannel channel =
            FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        FileLock ignored = channel.lock()) {
      if (Files.getFileStore(lockFile).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(
            lockFile,
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      }

      // The caller loaded preferences before acquiring the lock. Another process may have saved
      // credentials in the meantime, so only this reread can decide which values to use.
      Properties stored = NodusPreferences.load(home);
      String token = stored.getProperty(PY4J_TOKEN_PROPERTY);
      String key = stored.getProperty(J4R_KEY_PROPERTY);
      boolean needsSave =
          !Files.isRegularFile(home.resolve(PREFERENCES_FILE))
              || !isValidToken(token)
              || !isValidKey(key);

      if (needsSave) {
        if (!isValidToken(token)) {
          token = properties.getProperty(PY4J_TOKEN_PROPERTY);
          if (!isValidToken(token)) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
          }
        }
        if (!isValidKey(key)) {
          key = properties.getProperty(J4R_KEY_PROPERTY);
          if (!isValidKey(key)) {
            key = Integer.toString(RANDOM.nextInt(Integer.MAX_VALUE) + 1);
          }
        }

        Properties updated = new Properties();
        updated.putAll(stored);
        // Preserve settings supplied for a fresh installation without overwriting newer values
        // another Nodus instance has already saved.
        for (String name : properties.stringPropertyNames()) {
          if (!updated.containsKey(name)
              && !PY4J_TOKEN_PROPERTY.equals(name)
              && !J4R_KEY_PROPERTY.equals(name)) {
            updated.setProperty(name, properties.getProperty(name));
          }
        }
        updated.setProperty(PY4J_TOKEN_PROPERTY, token);
        updated.setProperty(J4R_KEY_PROPERTY, key);
        NodusPreferences.save(home, updated);
      } else {
        NodusPreferences.protect(home);
      }

      properties.setProperty(PY4J_TOKEN_PROPERTY, token);
      properties.setProperty(J4R_KEY_PROPERTY, key);
    }
  }

  private static boolean isValidToken(String token) {
    return token != null && token.matches("[A-Za-z0-9_-]{43}");
  }

  private static boolean isValidKey(String key) {
    if (key == null) {
      return false;
    }
    try {
      int value = Integer.parseInt(key);
      return value > 0 && Integer.toString(value).equals(key);
    } catch (NumberFormatException ex) {
      return false;
    }
  }
}
