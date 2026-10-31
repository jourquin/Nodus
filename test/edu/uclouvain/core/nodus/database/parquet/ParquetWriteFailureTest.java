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

package edu.uclouvain.core.nodus.database.parquet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.TableExport;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Exercises a real dictionary-page write failure, retaining its wrapped IO cause and old export.
 */
@ResourceLock("JDBCUtils")
class ParquetWriteFailureTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> wrappedWriteFailuresKeepCauseAndPreserveDestination() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean existing : new boolean[] {false, true}) {
      tests.add(
          DynamicTest.dynamicTest(
              "existing export=" + existing,
              () -> {
                Path folder = Files.createTempDirectory(directory, "wrapped-write-");
                try (DatabaseFixture database = new DatabaseFixture(folder)) {
                  database.execute("CREATE TABLE data (id DECIMAL(11), quantity DECIMAL(13,3))");
                  database.execute("INSERT INTO data VALUES (123456, 123.456), (654321, 654.321)");
                  Path target = folder.resolve("data.parquet");
                  byte[] original = "Keep the previous export".getBytes(StandardCharsets.UTF_8);
                  if (existing) {
                    Files.write(target, original);
                  }
                  TableExport files =
                      new TableExport() {
                        @Override
                        protected OutputStream open(Path temporary) throws IOException {
                          return new FilterOutputStream(super.open(temporary)) {
                            private int written;
                            private boolean failed;

                            @Override
                            public void write(int value) throws IOException {
                              // Simulate an underlying IO failure wrapped by the serializer.
                              if (written >= 4 && !failed) {
                                failed = true;
                                throw new IOException(
                                    "Parquet output failed",
                                    new IOException("Injected disk write failure"));
                              }
                              out.write(value);
                              written++;
                            }

                            @Override
                            public void write(byte[] bytes, int offset, int length)
                                throws IOException {
                              for (int i = 0; i < length; i++) {
                                write(bytes[offset + i]);
                              }
                            }
                          };
                        }
                      };
                  PrintStream previous = System.err;
                  ByteArrayOutputStream errors = new ByteArrayOutputStream();
                  try (PrintStream capture =
                      new PrintStream(errors, true, StandardCharsets.UTF_8)) {
                    System.setErr(capture);
                    assertFalse(ExportParquet.exportTable(database.project, "data", files));
                  } finally {
                    System.setErr(previous);
                  }
                  String message = errors.toString(StandardCharsets.UTF_8);
                  assertTrue(message.contains("Parquet output failed"), message);
                  assertTrue(message.contains("Injected disk write failure"), message);
                  assertFalse(message.contains("\tat "), message);
                  if (existing) {
                    assertArrayEquals(original, Files.readAllBytes(target));
                  } else {
                    assertFalse(Files.exists(target));
                  }
                  try (Stream<Path> remaining = Files.list(folder)) {
                    assertEquals(existing ? 1 : 0, remaining.count());
                  }
                }
              }));
    }
    return tests;
  }
}
