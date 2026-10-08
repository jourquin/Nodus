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
import java.util.concurrent.TimeUnit;
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
      assertEquals(
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(home.resolve(".nodus9.properties.lock")));
    }
  }

  @Test
  void simultaneousFirstLaunchesAdoptTheSameSavedCredentials() throws Exception {
    Path start = home.resolve("start");
    Path firstReady = home.resolve("first.ready");
    Path secondReady = home.resolve("second.ready");
    Path firstResult = home.resolve("first.result");
    Path secondResult = home.resolve("second.result");
    Path firstOutput = home.resolve("first.log");
    Path secondOutput = home.resolve("second.log");
    Process first = startProbe(firstReady, start, firstResult, firstOutput);
    Process second = null;
    try {
      second = startProbe(secondReady, start, secondResult, secondOutput);
      awaitFile(firstReady);
      awaitFile(secondReady);
      assertTrue(Files.notExists(home.resolve(".nodus9.properties")));
      Files.createFile(start);

      assertTrue(first.waitFor(30, TimeUnit.SECONDS), "First Nodus process timed out");
      assertTrue(second.waitFor(30, TimeUnit.SECONDS), "Second Nodus process timed out");
      assertEquals(0, first.exitValue(), Files.readString(firstOutput));
      assertEquals(0, second.exitValue(), Files.readString(secondOutput));

      String credentials = Files.readString(firstResult);
      assertEquals(credentials, Files.readString(secondResult));
      Properties saved = NodusPreferences.load(home);
      assertEquals(
          saved.getProperty(BridgeCredentials.PY4J_TOKEN_PROPERTY)
              + "\n"
              + saved.getProperty(BridgeCredentials.J4R_KEY_PROPERTY),
          credentials);
    } finally {
      first.destroyForcibly();
      if (second != null) {
        second.destroyForcibly();
      }
    }
  }

  private Process startProbe(Path ready, Path start, Path result, Path output) throws IOException {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return new ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            FirstLaunchProbe.class.getName(),
            home.toString(),
            ready.toString(),
            start.toString(),
            result.toString())
        .redirectErrorStream(true)
        .redirectOutput(output.toFile())
        .start();
  }

  private static void awaitFile(Path file) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (Files.notExists(file) && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertTrue(Files.exists(file), "Timed out waiting for " + file);
  }

  /** Models the Nodus startup load followed by a deliberately synchronized first launch. */
  public static final class FirstLaunchProbe {
    private FirstLaunchProbe() {}

    /**
     * Loads preferences before the start signal, then saves and reports bridge credentials.
     *
     * @param args home directory, ready file, start file, result file
     * @throws Exception if startup or credential initialization fails
     */
    public static void main(String[] args) throws Exception {
      Files.createFile(Path.of(args[1]));
      Path start = Path.of(args[2]);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      while (Files.notExists(start) && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      if (Files.notExists(start)) {
        throw new IllegalStateException("First-launch start signal timed out");
      }
      Path home = Path.of(args[0]);
      Properties properties = NodusPreferences.load(home);
      BridgeCredentials.ensure(home, properties);
      Files.writeString(
          Path.of(args[3]),
          properties.getProperty(BridgeCredentials.PY4J_TOKEN_PROPERTY)
              + "\n"
              + properties.getProperty(BridgeCredentials.J4R_KEY_PROPERTY));
    }
  }

  @Test
  void reusesStoredCredentialsAndRestrictsExistingFile() throws Exception {
    Properties properties = new Properties();
    BridgeCredentials.ensure(home, properties);
    String token = properties.getProperty(BridgeCredentials.PY4J_TOKEN_PROPERTY);
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

    String key = properties.getProperty(BridgeCredentials.J4R_KEY_PROPERTY);
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
