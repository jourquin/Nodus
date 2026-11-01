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
import edu.uclouvain.core.nodus.database.ImportProgress;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.csv.ExportCSV;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.parquet.ExportParquet;
import edu.uclouvain.core.nodus.database.xls.ExportXLS;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real console imports exercise cancellation, source changes, summaries and transaction safety. */
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_ERR")
class ImportRobustnessTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> importsCountRecordsAndProtectDataOnCancellationOrSourceChange() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String format :
        List.of("csv", "csvh", "dbf", "parquet", "xls", "xlsx", "ods", "xlsh", "xlsxh", "odsh")) {
      for (boolean hsql : new boolean[] {false, true}) {
        for (boolean existing : new boolean[] {false, true}) {
          if (!existing && format.equals("csv")) {
            continue;
          }
          for (boolean transaction : new boolean[] {false, true}) {
            for (String mode : List.of("success", "cancel", "changed", "cancelInference")) {
              if (mode.equals("cancelInference") && (existing || !format.endsWith("h"))) {
                continue;
              }
              tests.add(
                  dynamicTest(
                      format + "/" + hsql + "/" + existing + "/" + transaction + "/" + mode,
                      () -> {
                        Path folder = Files.createTempDirectory(directory, "import-");
                        try (DatabaseFixture db = new DatabaseFixture(folder, hsql)) {
                          db.execute("CREATE TABLE data(id INTEGER,label VARCHAR(32))");
                          db.execute("INSERT INTO data VALUES(1,'rail'),(2,'road')");
                          final Path source = write(db, folder, format);
                          if (existing) {
                            db.execute("DELETE FROM data");
                            db.execute("INSERT INTO data VALUES(99,'original')");
                          } else {
                            db.execute("DROP TABLE data");
                          }
                          db.execute("CREATE TABLE earlier(id INTEGER)");
                          db.connection.setAutoCommit(!transaction);
                          db.execute("INSERT INTO earlier VALUES(7)");
                          try (Console console = new Console(db.project, source, mode)) {
                            PrintStream previous = System.err;
                            ByteArrayOutputStream errors = new ByteArrayOutputStream();
                            boolean success;
                            try (PrintStream capture = new PrintStream(errors)) {
                              System.setErr(capture);
                              String command =
                                  "IMPORT" + format.toUpperCase(java.util.Locale.ROOT) + " data";
                              success =
                                  console.runBatch(
                                      mode.equals("success")
                                          ? new String[] {command}
                                          : new String[] {
                                            command, "INSERT INTO earlier VALUES(100)"
                                          });
                            } finally {
                              System.setErr(previous);
                            }
                            assertEquals(mode.equals("success"), success, console.message);
                            assertFalse(
                                errors.toString(StandardCharsets.UTF_8).contains("\tat "),
                                errors.toString(StandardCharsets.UTF_8));
                            assertFalse(
                                console.cancelImport(), "Control must be released after cleanup");
                            assertTrue(
                                console.phases.contains(
                                    mode.equals("cancelInference") ? "Inferring" : "Loading"),
                                console.phases.toString());
                            if (success) {
                              assertEquals(2, console.records);
                              assertTrue(console.message.contains("2 "), console.message);
                              assertTrue(console.message.contains(" s."), console.message);
                              assertTrue(console.phases.contains("Finishing"));
                              assertEquals(2, count(db, "data"));
                              try (Statement s = db.connection.createStatement();
                                  ResultSet r = s.executeQuery("SELECT * FROM data ORDER BY 1")) {
                                assertTrue(r.next());
                                assertEquals(1, r.getInt(1));
                                assertEquals("rail", r.getString(2));
                                assertTrue(r.next());
                                assertEquals(2, r.getInt(1));
                                assertEquals("road", r.getString(2));
                              }
                            } else {
                              assertTrue(
                                  console.message.contains(
                                      mode.startsWith("cancel") ? "cancelled" : "changed"),
                                  console.message);
                              assertEquals(existing, JDBCUtils.tableExists(db.connection, "data"));
                              if (existing) {
                                assertEquals(1, count(db, "data"));
                                try (Statement s = db.connection.createStatement();
                                    ResultSet r = s.executeQuery("SELECT * FROM data")) {
                                  assertTrue(r.next());
                                  assertEquals(99, r.getInt(1));
                                  assertEquals("original", r.getString(2));
                                }
                              }
                            }
                            assertEquals(!transaction, db.connection.getAutoCommit());
                            assertEquals(1, count(db, "earlier"));
                            if (transaction) {
                              db.connection.rollback();
                              assertEquals(0, count(db, "earlier"));
                            }
                            try (ResultSet tables =
                                db.connection
                                    .getMetaData()
                                    .getTables(
                                        db.connection.getCatalog(),
                                        db.connection.getSchema(),
                                        null,
                                        new String[] {"TABLE"})) {
                              while (tables.next()) {
                                String name = tables.getString("TABLE_NAME");
                                assertFalse(
                                    name.startsWith("NDI_") || name.startsWith("NDB_"), name);
                              }
                            }
                          }
                        }
                      }));
            }
          }
        }
      }
    }
    return tests;
  }

  private static int count(DatabaseFixture db, String table) throws Exception {
    try (Statement s = db.connection.createStatement();
        ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
      assertTrue(r.next());
      return r.getInt(1);
    }
  }

  private static Path write(DatabaseFixture db, Path folder, String format) throws Exception {
    boolean header = format.endsWith("h");
    String extension = header ? format.substring(0, format.length() - 1) : format;
    Path source = folder.resolve("data." + extension);
    if (extension.equals("csv")) {
      assertTrue(ExportCSV.exportTable(db.project, "data", header));
    } else if (extension.equals("dbf")) {
      assertTrue(ExportDBF.exportTable(db.project, "data"));
    } else if (extension.equals("parquet")) {
      assertTrue(ExportParquet.exportTable(db.project, "data"));
    } else if (extension.equals("ods")) {
      if (!header) {
        assertTrue(ExportXLS.exportODS(db.project, "data"));
      } else {
        String office = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
        String table = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
        String text = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
        StringBuilder xml =
            new StringBuilder("<office:document-content xmlns:office=\"")
                .append(office)
                .append("\" xmlns:table=\"")
                .append(table)
                .append("\" xmlns:text=\"")
                .append(text)
                .append("\"><table:table>");
        for (String[] row : new String[][] {{"ID", "LABEL"}, {"1", "rail"}, {"2", "road"}}) {
          xml.append("<table:table-row>");
          for (String value : row) {
            boolean number = value.equals("1") || value.equals("2");
            xml.append("<table:table-cell office:value-type=\"")
                .append(number ? "float" : "string")
                .append("\"");
            if (number) {
              xml.append(" office:value=\"").append(value).append("\"");
            }
            xml.append("><text:p>").append(value).append("</text:p></table:table-cell>");
          }
          xml.append("</table:table-row>");
        }
        xml.append("</table:table></office:document-content>");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
          zip.putNextEntry(new ZipEntry("content.xml"));
          zip.write(xml.toString().getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      }
    } else if (header) {
      try (Workbook book = extension.equals("xlsx") ? new XSSFWorkbook() : new HSSFWorkbook();
          var out = Files.newOutputStream(source)) {
        var sheet = book.createSheet();
        var names = sheet.createRow(0);
        names.createCell(0).setCellValue("ID");
        names.createCell(1).setCellValue("LABEL");
        for (int i = 1; i <= 2; i++) {
          var row = sheet.createRow(i);
          row.createCell(0).setCellValue(i);
          row.createCell(1).setCellValue(i == 1 ? "rail" : "road");
        }
        book.write(out);
      }
    } else {
      assertTrue(ExportXLS.exportTable(db.project, "data", extension.equals("xlsx")));
    }
    return source;
  }

  private static final class Console extends SQLConsole implements AutoCloseable {
    private final Path source;
    private final String mode;
    private final List<String> phases = new ArrayList<>();
    private String message;
    private long records;
    private boolean acted;

    Console(NodusProject project, Path source, String mode) {
      super(project, false);
      this.source = source;
      this.mode = mode;
    }

    @Override
    protected void importProgressUpdated(String operation, String table, ImportProgress progress) {
      phases.add(progress.getPhase());
      records = progress.getRecords();
      if (mode.equals("cancelInference") && progress.getPhase().equals("Inferring")) {
        assertTrue(cancelImport());
      }
      if (!acted && progress.getPhase().equals("Loading")) {
        acted = true;
        if (mode.equals("cancel")) {
          assertTrue(cancelImport());
        } else if (mode.equals("changed")) {
          try {
            Files.setLastModifiedTime(
                source, FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis() + 10000));
          } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
          }
        }
      }
      if (progress.getPhase().equals("Finishing")) {
        assertFalse(cancelImport(), "Publication must complete once finishing starts");
      }
    }

    @Override
    void displayMessageInResult(String header, String text) {
      message = text;
      super.displayMessageInResult(header, text);
    }

    @Override
    public void close() {
      windowClosing(null);
    }
  }
}
