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
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Properties;

/**
 * Writes settings to a sibling temporary file and installs only a fully closed replacement.
 * Existing files require atomic replacement; unsupported filesystems fail without removing them.
 * Caller-owned Properties are retained on every outcome. Existing symbolic links are followed, and
 * supported access permissions are preserved. This does not provide a power-loss guarantee.
 */
public class PropertiesFileSave {
  /** Default constructor. */
  public PropertiesFileSave() {}

  /**
   * Saves all entries before replacing the original settings file.
   *
   * @param file destination settings file
   * @param properties current settings
   * @throws IOException if writing, flushing, closing or replacement fails
   */
  public void save(Path file, Properties properties) throws IOException {
    final Path absolute = file.toAbsolutePath();
    final boolean existing = Files.exists(absolute, LinkOption.NOFOLLOW_LINKS);
    final Path target = existing ? absolute.toRealPath() : absolute;
    if (existing && (!Files.isRegularFile(target) || !Files.isWritable(target))) {
      throw new AccessDeniedException(target.toString());
    }
    final Path temporary = Files.createTempFile(target.getParent(), ".nodus-properties-", ".tmp");
    try {
      try (OutputStream output = open(temporary)) {
        properties.store(output, null);
      }
      if (existing) {
        copyPermissions(target, temporary);
      }
      replace(temporary, target, existing);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /**
   * Opens the staged file; kept separate to exercise genuine stream failures in tests.
   *
   * @param temporary staged file
   * @return owned output stream
   * @throws IOException if opening fails
   */
  protected OutputStream open(Path temporary) throws IOException {
    return Files.newOutputStream(temporary);
  }

  /**
   * Installs a complete file. A failed replacement must leave an existing target untouched.
   *
   * @param temporary completely written and closed staged file
   * @param target destination file
   * @param existing whether the destination existed at the start of this save
   * @throws IOException if installation fails
   */
  protected void replace(Path temporary, Path target, boolean existing) throws IOException {
    if (existing) {
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } else {
      // Do not replace a file created by another writer during serialization.
      Files.move(temporary, target);
    }
  }

  private static void copyPermissions(Path original, Path temporary) throws IOException {
    if (Files.getFileAttributeView(original, PosixFileAttributeView.class) != null) {
      Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(original));
    }
    final AclFileAttributeView acl =
        Files.getFileAttributeView(original, AclFileAttributeView.class);
    if (acl != null) {
      Files.getFileAttributeView(temporary, AclFileAttributeView.class).setAcl(acl.getAcl());
    }
  }
}
