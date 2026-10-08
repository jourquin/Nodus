/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/** Creates the credentials used by optional project Python and R bridges. */
final class BridgeCredentials {

  static final String PY4J_TOKEN_PROPERTY = "bridge.py4j.token";
  static final String J4R_KEY_PROPERTY = "bridge.j4r.key";

  private static final SecureRandom RANDOM = new SecureRandom();

  private BridgeCredentials() {}

  /**
   * Reuses existing credentials or saves new ones before a Groovy hook can start a bridge.
   * In-memory preferences are updated only after the file has been written successfully.
   */
  static void ensure(Path home, Properties properties) throws IOException {
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
