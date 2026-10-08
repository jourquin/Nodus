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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/** Creates the credentials used by optional project Python and R bridges. */
public final class BridgeCredentials {

  static final String PY4J_TOKEN_PROPERTY = "bridge.py4j.token";
  static final String J4R_KEY_PROPERTY = "bridge.j4r.key";

  private static final SecureRandom RANDOM = new SecureRandom();

  private BridgeCredentials() {}

  /**
   * Reuses existing credentials or saves new ones before a Groovy hook can start a bridge.
   * In-memory preferences are updated only after the file has been written successfully.
   */
  public static void ensure(Path home, Properties properties) throws IOException {
    String token = properties.getProperty(PY4J_TOKEN_PROPERTY);
    String key = properties.getProperty(J4R_KEY_PROPERTY);
    boolean newToken = !isValidToken(token);
    boolean newKey = !isValidKey(key);

    if (!newToken && !newKey && Files.isRegularFile(home.resolve(".nodus9.properties"))) {
      NodusPreferences.protect(home);
      return;
    }

    if (newToken) {
      byte[] bytes = new byte[32];
      RANDOM.nextBytes(bytes);
      token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    if (newKey) {
      key = Integer.toString(RANDOM.nextInt(Integer.MAX_VALUE) + 1);
    }

    Properties updated = new Properties();
    updated.putAll(properties);
    updated.setProperty(PY4J_TOKEN_PROPERTY, token);
    updated.setProperty(J4R_KEY_PROPERTY, key);
    NodusPreferences.save(home, updated);
    properties.setProperty(PY4J_TOKEN_PROPERTY, token);
    properties.setProperty(J4R_KEY_PROPERTY, key);
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
