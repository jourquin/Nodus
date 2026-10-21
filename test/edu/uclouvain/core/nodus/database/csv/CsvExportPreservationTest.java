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

package edu.uclouvain.core.nodus.database.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.ExportFileFaults;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real CSV serialization must not publish partial output on late file failures. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_OUT")
@ResourceLock("SYSTEM_ERR")
class CsvExportPreservationTest {
  private static final byte[] ORIGINAL =
      "previous export\r\nété 東京".getBytes(StandardCharsets.UTF_8);
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> failedStreamsAndPublicationLeavePreviousExportsAndPermitRetry() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean existing : new boolean[] {false, true}) {
      for (boolean header : new boolean[] {false, true}) {
        for (String phase : List.of("open", "write", "flush", "close", "replace", "unsupported")) {
          tests.add(
              dynamicTest(
                  existing + "/header=" + header + "/" + phase,
                  () -> {
                    final Path folder = Files.createTempDirectory(directory, "csv-");
                    try (DatabaseFixture f = new DatabaseFixture(folder)) {
                      f.execute("CREATE TABLE data(id INTEGER,label VARCHAR(30000))");
                      final String label = "été 東京".repeat(3000);
                      try (PreparedStatement insert =
                          f.connection.prepareStatement("INSERT INTO data VALUES(?,?)")) {
                        for (int row = 1; row <= 3; row++) {
                          insert.setInt(1, row);
                          insert.setString(2, label);
                          insert.executeUpdate();
                        }
                      }
                      final Path target = folder.resolve("data.csv");
                      if (existing) {
                        Files.write(target, ORIGINAL);
                      }
                      final ExportFileFaults files = new ExportFileFaults(phase);
                      assertFalse(
                          ExportFileFaults.quiet(
                              () -> ExportCSV.exportTable(f.project, "data", header, files)));
                      assertEquals(!phase.equals("open"), files.closed);
                      assertFalse(Files.exists(files.temporary));
                      if (!phase.equals("open")) {
                        assertTrue(files.bytes > 0, "Failure must follow actual output");
                      }
                      if (existing) {
                        assertArrayEquals(ORIGINAL, Files.readAllBytes(target));
                      } else {
                        assertFalse(Files.exists(target));
                      }
                      assertTrue(ExportCSV.exportTable(f.project, "data", header));
                      final String newline = System.lineSeparator();
                      final String expected =
                          (header ? "ID,LABEL" + newline : "")
                              + "1,"
                              + label
                              + newline
                              + "2,"
                              + label
                              + newline
                              + "3,"
                              + label
                              + newline;
                      assertEquals(expected, Files.readString(target, Charset.defaultCharset()));
                      assertEquals(List.of(target), ExportFileFaults.regularFiles(folder));
                      assertFalse(f.connection.isClosed());
                    }
                  }));
        }
      }
    }
    return tests;
  }
}
