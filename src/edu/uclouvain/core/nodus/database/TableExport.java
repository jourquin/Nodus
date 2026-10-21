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

package edu.uclouvain.core.nodus.database;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;

/**
 * Publishes a single export only after serialization and resource closure succeed. Staging uses a
 * sibling file, and replacing an existing destination requires an atomic move. Unsupported
 * filesystems fail without removing the previous export. Symbolic links are followed and supported
 * destination access permissions are retained. This does not provide a power-loss guarantee.
 */
public class TableExport {
  /** Default constructor. */
  public TableExport() {}

  /** Fully serializes an export and closes its format/JDBC resources before returning. */
  @FunctionalInterface
  public interface Writer {
    /**
     * Writes the complete export; the enclosing save also closes the supplied stream.
     *
     * @param output staged output, never the destination file
     * @throws Exception if serialization or any required resource closure fails
     */
    void write(OutputStream output) throws Exception;
  }

  /**
   * Writes and closes a staged export before publishing it at the destination.
   *
   * @param file export destination
   * @param writer format-specific serialization, including required resource closure
   * @throws Exception if writing, resource closure, permission copying or publication fails
   */
  public void write(Path file, Writer writer) throws Exception {
    final Path absolute = file.toAbsolutePath();
    final boolean existing = Files.exists(absolute, LinkOption.NOFOLLOW_LINKS);
    final Path target = existing ? absolute.toRealPath() : absolute;
    if (existing && (!Files.isRegularFile(target) || !Files.isWritable(target))) {
      throw new AccessDeniedException(target.toString());
    }
    final Path temporary = Files.createTempFile(target.getParent(), ".nodus-export-", ".tmp");
    try {
      try (OutputStream output = open(temporary)) {
        writer.write(output);
      }
      if (existing) {
        copyPermissions(target, temporary);
      }
      replace(temporary, target, existing);
    } catch (Exception | Error failure) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  /**
   * Opens the staged output, allowing deterministic stream-failure tests.
   *
   * @param temporary sibling staging file
   * @return owned output stream
   * @throws IOException if opening fails
   */
  protected OutputStream open(Path temporary) throws IOException {
    return Files.newOutputStream(temporary);
  }

  /**
   * Publishes a complete export; a failure must leave an existing target untouched.
   *
   * @param temporary fully written and closed staging file
   * @param target export destination
   * @param existing whether the destination existed when serialization began
   * @throws IOException if publication fails
   */
  protected void replace(Path temporary, Path target, boolean existing) throws IOException {
    if (existing) {
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } else {
      // Preserve a file created by another writer while this export was being serialized.
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
