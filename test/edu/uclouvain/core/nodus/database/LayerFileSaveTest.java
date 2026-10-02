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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LayerFileSaveTest {
  @TempDir Path directory;

  @Test
  void interruptedReplacementIsRestoredOnNextOpen() throws Exception {
    for (String name : List.of("test.shp", "test.shx", "test.dbf")) {
      Files.writeString(directory.resolve(name), "original " + name);
    }
    LayerFileSave save =
        new LayerFileSave(directory, "test") {
          int installed;

          @Override
          protected void install(Path staged, Path target) throws IOException {
            if (++installed == 2) {
              throw new IOException("Interrupted after first replacement");
            }
            super.install(staged, target);
          }
        };
    for (String name : List.of("test.shp", "test.shx", "test.dbf")) {
      Files.writeString(save.stage(name), "new " + name);
    }
    assertThrows(IOException.class, save::commit);
    assertEquals("new test.shp", Files.readString(directory.resolve("test.shp")));
    // Simulate interruption before close()/rollback runs; the next load performs recovery.
    LayerFileSave.recover(directory, "test");
    for (String name : List.of("test.shp", "test.shx", "test.dbf")) {
      assertEquals("original " + name, Files.readString(directory.resolve(name)));
    }
    assertFalse(Files.exists(directory.resolve(".test.nodus-save")));
  }

  @Test
  void committedJournalIsCleanedWithoutUndoingSuccessfulSave() throws Exception {
    Files.writeString(directory.resolve("test.dbf"), "old");
    // Deliberately skip close() to simulate interruption after commit but before journal cleanup.
    // recover() below performs cleanup; closing here would remove the journal under test.
    @SuppressWarnings("resource")
    LayerFileSave save = new LayerFileSave(directory, "test");
    Files.writeString(save.stage("test.dbf"), "new");
    save.commit();
    LayerFileSave.recover(directory, "test");
    assertEquals("new", Files.readString(directory.resolve("test.dbf")));
    assertFalse(Files.exists(directory.resolve(".test.nodus-save")));
  }

  @Test
  void failedRecoveryRetainsBackupForRetry() throws Exception {
    Path target = directory.resolve("test.dbf");
    Files.writeString(target, "original");
    // close() is tested through assertThrows below; recover() cleans up after that expected
    // failure.
    @SuppressWarnings("resource")
    LayerFileSave save =
        new LayerFileSave(directory, "test") {
          @Override
          protected void install(Path staged, Path destination) throws IOException {
            super.install(staged, destination);
            throw new IOException("Interrupted before commit marker");
          }
        };
    Files.writeString(save.stage("test.dbf"), "new");
    assertThrows(IOException.class, save::commit);
    Files.delete(target);
    Files.createDirectory(target);
    Path obstruction = Files.writeString(target.resolve("obstruction"), "blocked");
    assertThrows(IOException.class, save::close);
    assertEquals("original", Files.readString(directory.resolve(".test.nodus-save/test.dbf.bak")));
    Files.delete(obstruction);
    Files.delete(target);
    LayerFileSave.recover(directory, "test");
    assertEquals("original", Files.readString(target));
    assertFalse(Files.exists(directory.resolve(".test.nodus-save")));
  }

  @Test
  void failedInstallationRemovesNewFilesThatHadNoOriginal() throws Exception {
    try (LayerFileSave save =
        new LayerFileSave(directory, "test") {
          int installed;

          @Override
          protected void install(Path source, Path target) throws IOException {
            if (++installed == 2) {
              throw new IOException("Second file failure");
            }
            super.install(source, target);
          }
        }) {
      Files.writeString(save.stage("test.shp"), "new shape");
      Files.writeString(save.stage("test.shx"), "new index");
      assertThrows(IOException.class, save::commit);
    }
    assertFalse(Files.exists(directory.resolve("test.shp")));
    assertFalse(Files.exists(directory.resolve("test.shx")));
  }
}
