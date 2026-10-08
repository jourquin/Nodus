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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.xls.ExportXLS;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real console imports and database contents; only the confirmation response is replaced. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class SQLConsoleImportTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> overwriteScenarios() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Format format : Format.values()) {
      for (int answer : new int[] {JOptionPane.NO_OPTION, JOptionPane.CLOSED_OPTION}) {
        add(
            tests,
            format,
            "declining or closing preserves rows and schema: " + answer,
            (console, database, folder) -> {
              console.answer = answer;
              assertFalse(console.direct(format.command().toLowerCase() + ";"));
              assertEquals(1, console.prompts);
              assertTrue(console.message.contains("testdata"));
              assertRows(database, 7, "Keep this row");
              try (Statement statement = database.connection.createStatement();
                  ResultSet rows = statement.executeQuery("SELECT * FROM testdata")) {
                assertEquals(Types.INTEGER, rows.getMetaData().getColumnType(1));
                assertEquals(80, rows.getMetaData().getPrecision(2));
              }
            });
      }
      add(
          tests,
          format,
          "accepting replaces existing contents",
          (console, database, folder) -> {
            console.answer = JOptionPane.YES_OPTION;
            assertTrue(console.direct(format.command()));
            assertEquals(1, console.prompts);
            assertRows(database, 42, "Imported");
          });
      add(
          tests,
          format,
          "runBatch imports silently and subsequent direct execution asks again",
          (console, database, folder) -> {
            assertTrue(console.runBatch(new String[] {format.command()}));
            assertEquals(0, console.prompts);
            assertRows(database, 42, "Imported");
            assertFalse(console.direct(format.command()));
            assertEquals(1, console.prompts);
            assertRows(database, 42, "Imported");
          });
      add(
          tests,
          format,
          "loaded one-command script imports silently until reset",
          (console, database, folder) -> {
            Path script = folder.resolve("import.sql");
            Files.writeString(script, format.command() + ";");
            console.loadScript(script.toFile());
            assertTrue(console.execute(false));
            assertEquals(0, console.prompts);
            assertRows(database, 42, "Imported");
            console.resetScript();
            assertFalse(console.direct(format.command()));
            assertEquals(1, console.prompts);
          });
      add(
          tests,
          format,
          "pasted script with a variable imports silently",
          (console, database, folder) -> {
            assertTrue(console.direct("@@table:=testdata;\n" + format.operation + " @@table;"));
            assertEquals(0, console.prompts);
            assertRows(database, 42, "Imported");
          });
      add(
          tests,
          format,
          "existing empty tables also require approval",
          (console, database, folder) -> {
            database.execute("DELETE FROM testdata");
            assertFalse(console.direct(format.command()));
            assertEquals(1, console.prompts);
            try (Statement statement = database.connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT * FROM testdata")) {
              assertFalse(rows.next());
            }
          });
      if (format == Format.DBF || format == Format.XLS || format == Format.XLSX) {
        add(
            tests,
            format,
            "new tables require no confirmation",
            (console, database, folder) -> {
              database.execute("DROP TABLE testdata");
              assertTrue(console.direct(format.command()));
              assertEquals(0, console.prompts);
              assertRows(database, 42, "Imported");
            });
      }
    }
    return tests;
  }

  private void add(List<DynamicTest> tests, Format format, String name, ImportCheck check) {
    tests.add(
        dynamicTest(
            format + ": " + name,
            () -> {
              Path folder = Files.createTempDirectory(directory, "import-");
              try (DatabaseFixture database = new DatabaseFixture(folder);
                  RecordingConsole console = new RecordingConsole(database.project)) {
                database.execute("CREATE TABLE testdata (num INTEGER, label VARCHAR(80))");
                database.execute("INSERT INTO testdata VALUES (42, 'Imported')");
                format.writeFile(database, folder);
                database.execute("DELETE FROM testdata");
                database.execute("INSERT INTO testdata VALUES (7, 'Keep this row')");
                check.run(console, database, folder);
                assertFalse(database.connection.isClosed());
                assertTrue(database.connection.getAutoCommit());
              }
            }));
  }

  private static void assertRows(DatabaseFixture database, int number, String label)
      throws Exception {
    try (Statement statement = database.connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT * FROM testdata")) {
      assertEquals(2, rows.getMetaData().getColumnCount());
      assertTrue(rows.next());
      assertEquals(number, rows.getInt(1));
      assertEquals(label, rows.getString(2));
      assertFalse(rows.next());
    }
  }

  private interface ImportCheck {
    void run(RecordingConsole console, DatabaseFixture database, Path folder) throws Exception;
  }

  private enum Format {
    CSV("IMPORTCSV"),
    CSVH("IMPORTCSVH"),
    DBF("IMPORTDBF"),
    XLS("IMPORTXLS"),
    XLSX("IMPORTXLSX"),
    XLS_WITHOUT_SCHEMA("IMPORTXLS"),
    XLSX_WITHOUT_SCHEMA("IMPORTXLSX");

    private final String operation;

    Format(String operation) {
      this.operation = operation;
    }

    String command() {
      return operation + " testdata";
    }

    void writeFile(DatabaseFixture database, Path folder) throws Exception {
      if (this == CSV || this == CSVH) {
        Files.writeString(
            folder.resolve("testdata.csv"), (this == CSVH ? "NUM,LABEL\n" : "") + "42,Imported\n");
      } else if (this == DBF) {
        assertTrue(ExportDBF.exportTable(database.project, "testdata"));
      } else if (this == XLS || this == XLSX) {
        assertTrue(ExportXLS.exportTable(database.project, "testdata", this == XLSX));
      } else {
        boolean xlsx = this == XLSX_WITHOUT_SCHEMA;
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
            OutputStream output =
                Files.newOutputStream(folder.resolve("testdata" + (xlsx ? ".xlsx" : ".xls")))) {
          Sheet sheet = workbook.createSheet();
          sheet.createRow(0).createCell(0).setCellValue(42);
          sheet.getRow(0).createCell(1).setCellValue("Imported");
          workbook.write(output);
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
