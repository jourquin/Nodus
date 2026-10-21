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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** Single-file publication, stream failures, replacement failures and existing file semantics. */
class TableExportTest {
  private static final byte[] ORIGINAL =
      "previous export\r\nété 東京".getBytes(StandardCharsets.UTF_8);
  private static final byte[] REPLACEMENT =
      "new export\n".repeat(500).getBytes(StandardCharsets.UTF_8);
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> failedIoPreservesDestinationsAndCleansStagingForRetry() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean existing : new boolean[] {false, true}) {
      for (String phase : List.of("open", "write", "flush", "close", "replace", "unsupported")) {
        tests.add(
            dynamicTest(
                existing + "/" + phase,
                () -> {
                  final Path target =
                      Files.createTempDirectory(directory, "io-").resolve("data.csv");
                  if (existing) {
                    Files.write(target, ORIGINAL);
                  }
                  final ExportFileFaults save = new ExportFileFaults(phase);
                  assertThrows(
                      IOException.class,
                      () ->
                          save.write(
                              target,
                              output -> {
                                output.write(REPLACEMENT);
                                output.flush();
                              }));
                  assertFalse(Files.exists(save.temporary));
                  assertEquals(!phase.equals("open"), save.closed);
                  if (existing) {
                    assertArrayEquals(ORIGINAL, Files.readAllBytes(target));
                  } else {
                    assertFalse(Files.exists(target));
                  }
                  new TableExport().write(target, output -> output.write(REPLACEMENT));
                  assertArrayEquals(REPLACEMENT, Files.readAllBytes(target));
                  assertEquals(List.of(target), ExportFileFaults.regularFiles(target.getParent()));
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> serializationExceptionsAndErrorsNeverPublishPartialFiles() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean existing : new boolean[] {false, true}) {
      for (boolean fatal : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                existing + "/error=" + fatal,
                () -> {
                  final Path target =
                      Files.createTempDirectory(directory, "serialization-").resolve("data.csv");
                  if (existing) {
                    Files.write(target, ORIGINAL);
                  }
                  final Throwable injected =
                      fatal
                          ? new AssertionError("Injected")
                          : new IllegalStateException("Injected");
                  final Throwable failure =
                      assertThrows(
                          Throwable.class,
                          () ->
                              new TableExport()
                                  .write(
                                      target,
                                      output -> {
                                        output.write(REPLACEMENT);
                                        if (fatal) {
                                          throw (AssertionError) injected;
                                        }
                                        throw (IllegalStateException) injected;
                                      }));
                  assertEquals(injected, failure);
                  assertEquals(
                      existing ? List.of(target) : List.of(),
                      ExportFileFaults.regularFiles(target.getParent()));
                  if (existing) {
                    assertArrayEquals(ORIGINAL, Files.readAllBytes(target));
                  }
                }));
      }
    }
    return tests;
  }

  @Test
  void destinationsCreatedWhileWritingAreRetained() throws Exception {
    final Path target = directory.resolve("data.csv");
    assertThrows(
        IOException.class,
        () ->
            new TableExport()
                .write(
                    target,
                    output -> {
                      output.write(REPLACEMENT);
                      Files.write(target, ORIGINAL);
                    }));
    assertArrayEquals(ORIGINAL, Files.readAllBytes(target));
    assertEquals(List.of(target), ExportFileFaults.regularFiles(directory));
  }

  @Test
  void destinationDirectoriesAreRetainedAndDoNotStartSerialization() throws Exception {
    final Path target = Files.createDirectory(directory.resolve("data.csv"));
    assertThrows(
        IOException.class,
        () ->
            new TableExport()
                .write(
                    target,
                    output -> {
                      throw new AssertionError("Must not serialize to a directory");
                    }));
    assertTrue(Files.isDirectory(target));
    assertTrue(ExportFileFaults.regularFiles(directory).isEmpty());
  }

  @Test
  void existingAccessPermissionsSurviveReplacement() throws Exception {
    final Path target = Files.write(directory.resolve("data.csv"), ORIGINAL);
    if (Files.getFileAttributeView(target, PosixFileAttributeView.class) == null) {
      return;
    }
    final var permissions = PosixFilePermissions.fromString("rw-r-----");
    Files.setPosixFilePermissions(target, permissions);
    new TableExport().write(target, output -> output.write(REPLACEMENT));
    assertArrayEquals(REPLACEMENT, Files.readAllBytes(target));
    assertEquals(permissions, Files.getPosixFilePermissions(target));
  }

  @Test
  void symbolicLinksAndTheirTargetsSurviveFailureAndSuccessfulRetry() throws Exception {
    final Path actual = Files.write(directory.resolve("actual.csv"), ORIGINAL);
    final Path target = directory.resolve("data.csv");
    try {
      Files.createSymbolicLink(target, actual.getFileName());
    } catch (UnsupportedOperationException | IOException unsupported) {
      return;
    }
    assertThrows(
        IOException.class,
        () -> new ExportFileFaults("close").write(target, output -> output.write(REPLACEMENT)));
    assertTrue(Files.isSymbolicLink(target));
    assertArrayEquals(ORIGINAL, Files.readAllBytes(actual));
    new TableExport().write(target, output -> output.write(REPLACEMENT));
    assertTrue(Files.isSymbolicLink(target));
    assertArrayEquals(REPLACEMENT, Files.readAllBytes(actual));
  }
}
