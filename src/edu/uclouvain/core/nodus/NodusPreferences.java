/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/**
 * Stores application preferences in {@code .nodus9.properties} in the user's home directory.
 *
 * <p>On first use, an existing {@code .nodus8.properties} file is copied unchanged if the new file
 * does not exist. Subsequent loads and saves use only the new file, leaving Nodus 8 preferences
 * intact. An existing Nodus 9 file, including an empty one, always takes precedence.
 */
final class NodusPreferences {

  /** Current application preferences filename. */
  private static final String FILE_NAME = ".nodus9.properties";

  /** Bridge credentials in this file must be readable only by the current user on POSIX systems. */
  private static final Set<PosixFilePermission> PRIVATE_PERMISSIONS =
      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  /** Prevents instantiation of this persistence utility. */
  private NodusPreferences() {}

  /**
   * Loads preferences, copying the previous version's file when needed.
   *
   * @param home User's home directory
   * @return Saved preferences, or empty preferences when neither version's file exists
   * @throws IOException If the migration or reading an existing file fails
   */
  static Properties load(Path home) throws IOException {
    Path file = home.resolve(FILE_NAME);
    Path legacyFile = home.resolve(".nodus8.properties");
    if (Files.notExists(file) && Files.isRegularFile(legacyFile)) {
      try {
        Files.copy(legacyFile, file);
      } catch (FileAlreadyExistsException ex) {
        // Another Nodus instance created the file; use it without replacing its preferences.
      }
    }

    Properties properties = new Properties();
    if (Files.notExists(file)) {
      return properties;
    }
    try (InputStream in = Files.newInputStream(file)) {
      properties.load(in);
    }
    return properties;
  }

  /**
   * Saves application state to the Nodus 9 file without modifying the previous version's file.
   * Writes a replacement file so readers see complete preferences; its POSIX permissions are
   * owner-only because bridge credentials are stored here.
   *
   * @param home User's home directory
   * @param properties Current application preferences
   * @throws IOException If the preferences cannot be written
   */
  static void save(Path home, Properties properties) throws IOException {
    FileAttribute<?>[] attributes =
        Files.getFileStore(home).supportsFileAttributeView("posix")
            ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PRIVATE_PERMISSIONS)}
            : new FileAttribute<?>[0];
    Path temporary = Files.createTempFile(home, ".nodus9-", ".tmp", attributes);
    try {
      try (OutputStream out = Files.newOutputStream(temporary)) {
        properties.store(out, null);
      }
      Path destination = home.resolve(FILE_NAME);
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /** Restricts access to an existing preferences file without rewriting its contents. */
  static void protect(Path home) throws IOException {
    Path file = home.resolve(FILE_NAME);
    if (Files.exists(file) && Files.getFileStore(file).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(file, PRIVATE_PERMISSIONS);
    }
  }
}
