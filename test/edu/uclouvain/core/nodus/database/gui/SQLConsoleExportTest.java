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

package edu.uclouvain.core.nodus.database.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Runs the real console, exporters and files while replacing only the user dialog response. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class SQLConsoleExportTest {
  private static final byte[] ORIGINAL = "Keep this existing file".getBytes(StandardCharsets.UTF_8);

  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> overwriteScenarios() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Format format : Format.values()) {
      add(
          tests,
          format,
          "declined direct command preserves existing bytes",
          (console, output) -> {
            Files.write(output, ORIGINAL);
            assertFalse(console.direct(format.command()));
            assertEquals(1, console.prompts);
            assertTrue(console.message.contains(output.getFileName().toString()));
            assertArrayEquals(ORIGINAL, Files.readAllBytes(output));
          });
      add(
          tests,
          format,
          "closing the prompt cancels a command ending in a semicolon",
          (console, output) -> {
            Files.write(output, ORIGINAL);
            console.answer = JOptionPane.CLOSED_OPTION;
            assertFalse(console.direct(format.command() + ";"));
            assertEquals(1, console.prompts);
            assertArrayEquals(ORIGINAL, Files.readAllBytes(output));
          });
      add(
          tests,
          format,
          "accepted direct command replaces the file",
          (console, output) -> {
            Files.write(output, ORIGINAL);
            console.answer = JOptionPane.YES_OPTION;
            assertTrue(console.direct(format.command()));
            assertEquals(1, console.prompts);
            format.assertExport(output);
          });
      add(
          tests,
          format,
          "new output requires no confirmation",
          (console, output) -> {
            assertTrue(console.direct(format.command()));
            assertEquals(0, console.prompts);
            format.assertExport(output);
          });
      add(
          tests,
          format,
          "single-command runBatch overwrites silently",
          (console, output) -> {
            Files.write(output, ORIGINAL);
            assertTrue(console.runBatch(new String[] {format.command()}));
            assertEquals(0, console.prompts);
            format.assertExport(output);
          });
      add(
          tests,
          format,
          "loaded one-command script overwrites silently",
          (console, output) -> {
            Files.write(output, ORIGINAL);
            Path script = output.resolveSibling("export.sql");
            Files.writeString(script, format.command());
            console.loadScript(script.toFile());
            assertTrue(console.execute(false));
            assertEquals(0, console.prompts);
            format.assertExport(output);
          });
      add(
          tests,
          format,
          "pasted batch overwrites silently",
          (console, output) -> {
            Files.write(output, ORIGINAL);
            assertTrue(console.direct("DISABLEECHO;\n" + format.command() + ";"));
            assertEquals(0, console.prompts);
            format.assertExport(output);
          });
      add(
          tests,
          format,
          "variable definition plus one export is a script",
          (console, output) -> {
            Files.write(output, ORIGINAL);
            assertTrue(console.direct("@@table:=testdata;\n" + format.name() + " @@table;"));
            assertEquals(0, console.prompts);
            format.assertExport(output);
          });
      add(
          tests,
          format,
          "direct command after runBatch asks again",
          (console, output) -> {
            assertTrue(console.runBatch(new String[] {format.command()}));
            final byte[] exported = Files.readAllBytes(output);
            assertFalse(console.direct(format.command()));
            assertEquals(1, console.prompts);
            assertArrayEquals(exported, Files.readAllBytes(output));
          });
      add(
          tests,
          format,
          "resetting a loaded script restores confirmation",
          (console, output) -> {
            Path script = output.resolveSibling("export.sql");
            Files.writeString(script, format.command() + ";");
            console.loadScript(script.toFile());
            assertTrue(console.execute(false));
            final byte[] exported = Files.readAllBytes(output);
            console.resetScript();
            assertFalse(console.direct(format.command()));
            assertEquals(1, console.prompts);
            assertArrayEquals(exported, Files.readAllBytes(output));
          });
    }
    return tests;
  }

  @Test
  void batchWorksWithoutAWindowAndLeavesTheProjectConnectionOpen() throws Exception {
    try (DatabaseFixture database = new DatabaseFixture(directory)) {
      try (RecordingConsole console = new RecordingConsole(database.project)) {
        assertNull(console.getFrame());
        assertTrue(
            console.runBatch(
                new String[] {
                  "CREATE TABLE testdata (num INTEGER)", "INSERT INTO testdata VALUES (42)",
                  "SELECT * FROM testdata", "EXPORTCSV testdata"
                }));
        Format.EXPORTCSV.assertExport(directory.resolve("testdata.csv"));
        assertTrue(console.runBatch(new String[] {"EXPORTCSVH testdata"}));
        Format.EXPORTCSVH.assertExport(directory.resolve("testdata.csv"));
      }
      assertFalse(database.connection.isClosed());
    }
  }

  private void add(List<DynamicTest> tests, Format format, String name, ExportCheck check) {
    tests.add(
        dynamicTest(
            format + ": " + name,
            () -> {
              Path folder = Files.createTempDirectory(directory, "export-");
              try (DatabaseFixture database = new DatabaseFixture(folder);
                  RecordingConsole console = new RecordingConsole(database.project)) {
                database.execute("CREATE TABLE testdata (num INTEGER)");
                database.execute("INSERT INTO testdata VALUES (42)");
                check.run(console, folder.resolve("testdata" + format.extension));
                assertFalse(database.connection.isClosed());
              }
            }));
  }

  private interface ExportCheck {
    void run(RecordingConsole console, Path output) throws Exception;
  }

  private enum Format {
    EXPORTCSV(".csv"),
    EXPORTCSVH(".csv"),
    EXPORTDBF(".dbf"),
    EXPORTXLS(".xls"),
    EXPORTXLSX(".xlsx");

    private final String extension;

    Format(String extension) {
      this.extension = extension;
    }

    String command() {
      return name() + " testdata";
    }

    void assertExport(Path output) throws Exception {
      if (this == EXPORTCSV || this == EXPORTCSVH) {
        assertEquals(
            this == EXPORTCSVH ? List.of("NUM", "42") : List.of("42"), Files.readAllLines(output));
      } else if (this == EXPORTDBF) {
        try (DBFReader reader = new DBFReader(output.toString())) {
          assertEquals(1, reader.getFieldCount());
          assertTrue(reader.hasNextRecord());
          assertEquals(42, ((Number) reader.nextRecord()[0]).intValue());
          assertFalse(reader.hasNextRecord());
        }
      } else {
        try (InputStream input = Files.newInputStream(output);
            Workbook workbook = WorkbookFactory.create(input)) {
          assertEquals(1, workbook.getNumberOfSheets());
          assertEquals(1, workbook.getSheetAt(0).getLastRowNum());
          assertEquals(42, workbook.getSheetAt(0).getRow(1).getCell(0).getNumericCellValue());
        }
      }
    }
  }

  private static final class RecordingConsole extends SQLConsole implements AutoCloseable {
    private int prompts;
    private int answer = JOptionPane.NO_OPTION;
    private String message;

    RecordingConsole(NodusProject project) {
      super(project, false);
    }

    boolean direct(String command) {
      getSqlCommandArea().setText(command);
      return execute(false);
    }

    @Override
    protected int showOverwriteDialog(String text) {
      assertTrue(SwingUtilities.isEventDispatchThread());
      prompts++;
      message = text;
      return answer;
    }

    @Override
    public void close() {
      windowClosing(null);
    }
  }
}
