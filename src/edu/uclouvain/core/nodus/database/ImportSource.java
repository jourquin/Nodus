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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.Objects;

/** Inexpensive file-identity checks; no extra pass over the source's contents. */
public final class ImportSource {
  private final Path path;
  private final BasicFileAttributes before;

  /**
   * Captures a readable regular source file before any import work.
   *
   * @param path source file to monitor
   * @throws IOException if the source is missing, unreadable or not a regular file
   */
  public ImportSource(Path path) throws IOException {
    this.path = path;
    before = Files.readAttributes(path, BasicFileAttributes.class);
    if (!before.isRegularFile() || !Files.isReadable(path)) {
      throw new IOException("Source file is not a readable regular file: " + path);
    }
  }

  /**
   * Rejects replacement or modification before imported data is published.
   *
   * @throws SQLException if file identity, size or modification time changed, or the file vanished
   */
  public void verify() throws SQLException {
    try {
      BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
      if (after.isRegularFile()
          && before.size() == after.size()
          && before.lastModifiedTime().equals(after.lastModifiedTime())
          && Objects.equals(before.fileKey(), after.fileKey())) {
        return;
      }
    } catch (IOException error) {
      throw new SQLException(
          "Source file changed or became unavailable during import: " + path, error);
    }
    throw new SQLException(
        "Source file changed during import; retry with a stable source file: " + path);
  }

  /**
   * Wraps a parser's source so cancellation is also checked while decoding large workbooks.
   *
   * @param input source owned by the returned wrapper
   * @param progress import cancellation control
   * @return stream that checks cancellation while reading; the caller must close it
   */
  public static InputStream cancellable(InputStream input, ImportProgress progress) {
    return new FilterInputStream(input) {
      private void check() throws IOException {
        try {
          progress.checkCancelled();
        } catch (SQLException cancelled) {
          throw new IOException(cancelled.getMessage(), cancelled);
        }
      }

      @Override
      public int read() throws IOException {
        check();
        return super.read();
      }

      @Override
      public int read(byte[] bytes, int offset, int length) throws IOException {
        check();
        return in.read(bytes, offset, length);
      }

      @Override
      public long skip(long length) throws IOException {
        check();
        return in.skip(length);
      }
    };
  }
}
