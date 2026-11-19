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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.parquet.example.data.Group;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Generic projection checks, without commodity, centroid or OD column assumptions. */
@ResourceLock("JDBCUtils")
class ParquetFilesTest {
  @TempDir Path directory;

  @Test
  void projectsRequestedOrderAndPreservesNullsAndExactIntegers() throws Exception {
    Path file = fixture(false);
    Group first;
    try (ParquetFiles.ProjectedReader reader =
        ParquetFiles.openProjectedReader(file, List.of("LABEL", "ID"))) {
      assertEquals(
          List.of("LABEL", "ID"),
          reader
              .getSchema()
              .getFields()
              .stream()
              .map(type -> type.getName())
              .collect(java.util.stream.Collectors.toList()));
      first = reader.read();
      assertEquals("café 東京", first.getString(0, 0));
      assertEquals(9007199254740993L, first.getLong(1, 0));
      Group second = reader.read();
      assertEquals(0, second.getFieldRepetitionCount(0));
      assertEquals(0, second.getFieldRepetitionCount(1));
      assertNull(reader.read());
      assertNull(reader.read());
    }
    assertEquals("café 東京", first.getString(0, 0));
  }

  @Test
  void validatesProjectionAndCanBeClosedRepeatedly() throws Exception {
    Path file = fixture(false);
    for (List<String> names : List.of(List.<String>of(), List.of("MISSING"), List.of("ID", "ID"))) {
      assertThrows(
          IllegalArgumentException.class, () -> ParquetFiles.openProjectedReader(file, names));
    }
    ParquetFiles.ProjectedReader reader = ParquetFiles.openProjectedReader(file, List.of("ID"));
    reader.close();
    reader.close();
    assertThrows(IOException.class, reader::read);
    Files.delete(file);
    assertTrue(Files.notExists(file));
  }

  @Test
  void keepsSchemaForEmptyFiles() throws Exception {
    Path file = fixture(true);
    try (ParquetFiles.ProjectedReader reader =
        ParquetFiles.openProjectedReader(file, List.of("LABEL"))) {
      assertEquals(1, reader.getSchema().getFieldCount());
      assertEquals("LABEL", reader.getSchema().getFieldName(0));
      assertNull(reader.read());
    }
  }

  private Path fixture(boolean empty) throws Exception {
    try (DatabaseFixture db = new DatabaseFixture(directory)) {
      db.execute("CREATE TABLE genericdata (ID BIGINT, LABEL VARCHAR(30), FLAG BOOLEAN)");
      if (!empty) {
        db.execute("INSERT INTO genericdata VALUES(9007199254740993,'café 東京',TRUE)");
        db.execute("INSERT INTO genericdata VALUES(NULL,NULL,NULL)");
      }
      assertTrue(ExportParquet.exportTable(db.project, "genericdata"));
    }
    return directory.resolve("genericdata.parquet");
  }
}
