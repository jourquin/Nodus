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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** Real temporary files and injected I/O faults through the production staging pipeline. */
class PropertiesFileSaveTest {
  @TempDir Path directory;
  private static final byte[] ORIGINAL =
      "# Saved settings\r\nkey=café\r\n".getBytes(StandardCharsets.ISO_8859_1);

  @TestFactory
  List<DynamicTest> writeFlushCloseAndReplacementFailuresPreserveFilesAndPermitRetry() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean existing : new boolean[] {false, true}) {
      for (String phase :
          List.of(
              "open",
              "first write",
              "later write",
              "flush",
              "close",
              "replace",
              "unsupported atomic move")) {
        if (!existing && phase.equals("unsupported atomic move")) {
          continue;
        }
        tests.add(
            dynamicTest(
                "existing=" + existing + "/" + phase,
                () -> {
                  final Path file =
                      Files.createTempDirectory(directory, "save-").resolve("model.nodus");
                  if (existing) {
                    Files.write(file, ORIGINAL);
                  }
                  final CommentedProperties properties = new CommentedProperties();
                  properties.setProperty("key", "replacement" + "x".repeat(30000));
                  final FailingSave save = new FailingSave(phase);
                  assertThrows(IOException.class, () -> save.save(file, properties));
                  assertTrue(save.temporary != null);
                  assertFalse(Files.exists(save.temporary));
                  if (existing) {
                    assertArrayEquals(ORIGINAL, Files.readAllBytes(file));
                  } else {
                    assertFalse(Files.exists(file));
                  }
                  assertEquals(1, properties.size());
                  assertEquals(!phase.equals("open"), save.closed);
                  new PropertiesFileSave().save(file, properties);
                  assertEquals(properties, read(file));
                  assertOnly(file);
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> completeFilesAreInstalledWithCommentsAndNewSettings() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean existing : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "existing=" + existing,
              () -> {
                final Path file =
                    Files.createTempDirectory(directory, "save-").resolve("model.nodus");
                if (existing) {
                  Files.write(file, ORIGINAL);
                }
                final CommentedProperties properties = new CommentedProperties();
                properties.load(new java.io.ByteArrayInputStream(ORIGINAL));
                properties.setProperty("key", "new café 東京\nline");
                properties.setProperty("new setting", "added");
                new PropertiesFileSave().save(file, properties);
                assertEquals(properties, read(file));
                assertTrue(
                    Files.readString(file, StandardCharsets.ISO_8859_1)
                            .startsWith("# Saved settings\n")
                        || Files.readString(file, StandardCharsets.ISO_8859_1)
                            .startsWith("# Saved settings\r\n"));
                assertOnly(file);
              }));
    }
    return tests;
  }

  @Test
  void serializationRuntimeErrorsAlsoLeaveOriginalFilesAndNoTemporaryOutput() throws Exception {
    final Path file = directory.resolve("model.nodus");
    Files.write(file, ORIGINAL);
    final CommentedProperties properties =
        new CommentedProperties() {
          private static final long serialVersionUID = 1L;

          @Override
          public void store(OutputStream output, String header) throws IOException {
            output.write("partial".getBytes(StandardCharsets.ISO_8859_1));
            throw new IllegalStateException("serialization failed");
          }
        };
    assertThrows(
        IllegalStateException.class, () -> new PropertiesFileSave().save(file, properties));
    assertArrayEquals(ORIGINAL, Files.readAllBytes(file));
    assertOnly(file);
  }

  @Test
  void destinationCreatedDuringSerializationIsNotReplaced() throws Exception {
    final Path file = directory.resolve("model.nodus");
    final CommentedProperties properties =
        new CommentedProperties() {
          private static final long serialVersionUID = 1L;

          @Override
          public void store(OutputStream output, String header) throws IOException {
            super.store(output, header);
            Files.write(file, ORIGINAL);
          }
        };
    properties.setProperty("key", "mine");
    assertThrows(IOException.class, () -> new PropertiesFileSave().save(file, properties));
    assertArrayEquals(ORIGINAL, Files.readAllBytes(file));
    assertOnly(file);
  }

  @Test
  void directoryIsNeverReplacedEvenIfItIsEmpty() throws Exception {
    final Path file = Files.createDirectory(directory.resolve("model.nodus"));
    assertThrows(IOException.class, () -> new PropertiesFileSave().save(file, new Properties()));
    assertTrue(Files.isDirectory(file));
    assertOnly(file);
  }

  @Test
  void existingPosixPermissionsArePreserved() throws Exception {
    final Path file = Files.write(directory.resolve("model.nodus"), ORIGINAL);
    if (Files.getFileAttributeView(file, PosixFileAttributeView.class) == null) {
      return;
    }
    final java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions =
        PosixFilePermissions.fromString("rw-r-----");
    Files.setPosixFilePermissions(file, permissions);
    new PropertiesFileSave().save(file, new Properties());
    assertEquals(permissions, Files.getPosixFilePermissions(file));
    assertOnly(file);
  }

  @Test
  void savingThroughASymbolicLinkPreservesTheLinkAndUpdatesItsTarget() throws Exception {
    final Path target = Files.write(directory.resolve("actual.nodus"), ORIGINAL);
    final Path link = directory.resolve("model.nodus");
    // Windows installations without symlink privileges do not support this optional capability.
    try {
      Files.createSymbolicLink(link, target.getFileName());
    } catch (UnsupportedOperationException | IOException unsupported) {
      return;
    }
    final Properties properties = new Properties();
    properties.setProperty("key", "updated");
    new PropertiesFileSave().save(link, properties);
    assertTrue(Files.isSymbolicLink(link));
    assertEquals(properties, read(target));
    assertEquals(properties, read(link));
  }

  private static Properties read(Path file) throws IOException {
    final Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    }
    return properties;
  }

  private void assertOnly(Path file) throws IOException {
    try (java.util.stream.Stream<Path> files = Files.list(file.getParent())) {
      assertEquals(List.of(file), files.collect(java.util.stream.Collectors.toList()));
    }
  }

  private static final class FailingSave extends PropertiesFileSave {
    final String phase;
    Path temporary;
    boolean closed;
    int written;

    FailingSave(String phase) {
      this.phase = phase;
    }

    @Override
    protected OutputStream open(Path file) throws IOException {
      temporary = file;
      if (phase.equals("open")) {
        throw new IOException(phase);
      }
      return new FilterOutputStream(super.open(file)) {
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
          final int allowed =
              phase.equals("first write")
                  ? 0
                  : phase.equals("later write")
                      ? Math.min(length, Math.max(0, 9000 - written))
                      : length;
          out.write(bytes, offset, allowed);
          written += allowed;
          if (allowed < length) {
            throw new IOException(phase);
          }
        }

        @Override
        public void write(int value) throws IOException {
          write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void flush() throws IOException {
          if (phase.equals("flush")) {
            throw new IOException(phase);
          }
          super.flush();
        }

        @Override
        public void close() throws IOException {
          try {
            super.close();
          } finally {
            closed = true;
          }
          if (phase.equals("close")) {
            throw new IOException(phase);
          }
        }
      };
    }

    @Override
    protected void replace(Path staged, Path target, boolean existing) throws IOException {
      assertTrue(closed, "The complete output must close before installation");
      assertTrue(read(staged).getProperty("key").startsWith("replacement"));
      if (phase.equals("replace")) {
        throw new IOException(phase);
      }
      if (phase.equals("unsupported atomic move")) {
        throw new AtomicMoveNotSupportedException(staged.toString(), target.toString(), phase);
      }
      super.replace(staged, target, existing);
    }
  }
}
