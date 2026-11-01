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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Imports accept one rectangular table and report the first structural failure without a popup. */
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_ERR")
class SingleTableImportTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> spreadsheetLayouts() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String format : List.of("xls", "xlsx", "ods")) {
      for (boolean hsql : new boolean[] {false, true}) {
        for (boolean existing : new boolean[] {false, true}) {
          for (String scenario :
              List.of(
                  "twoSheets",
                  "hiddenSecond",
                  "corruptSecond",
                  "merged",
                  "wider",
                  "shorter",
                  "gap",
                  "mixed",
                  "repeatedHeader",
                  "firstMismatch",
                  "validNulls",
                  "validDecimals",
                  "validTrailing",
                  "validBareTrailing",
                  "validSchemaTrailing",
                  "schemaMismatch",
                  "bareShorter",
                  "bareMixed")) {
            tests.add(
                dynamicTest(
                    format + "/" + hsql + "/" + existing + "/" + scenario,
                    () -> {
                      Path folder = Files.createTempDirectory(directory, "sheet-");
                      try (DatabaseFixture db = new DatabaseFixture(folder, hsql);
                          Console console = new Console(db.project)) {
                        if (existing) {
                          db.execute(
                              "CREATE TABLE data(id "
                                  + (scenario.equals("validDecimals") ? "DECIMAL(8,3)" : "INTEGER")
                                  + ",label VARCHAR(16))");
                          db.execute("INSERT INTO data VALUES(99,'original')");
                        }
                        writeSheet(folder, format, scenario);
                        boolean header =
                            !scenario.startsWith("bare")
                                && !List.of(
                                        "schemaMismatch",
                                        "validBareTrailing",
                                        "validSchemaTrailing")
                                    .contains(scenario);
                        String command =
                            "IMPORT"
                                + format.toUpperCase(java.util.Locale.ROOT)
                                + (header ? "H" : "")
                                + " data";
                        boolean valid = scenario.startsWith("valid");
                        assertEquals(valid, execute(console, command), console.message);
                        if (valid) {
                          try (Statement s = db.connection.createStatement();
                              ResultSet r = s.executeQuery("SELECT * FROM data ORDER BY 1")) {
                            assertTrue(r.next());
                            assertEquals(1, r.getInt(1));
                            assertTrue(r.next());
                            if (scenario.equals("validNulls")) {
                              assertNull(r.getObject(2));
                            } else if (scenario.equals("validDecimals")) {
                              assertEquals(2.5, r.getDouble(1));
                            } else {
                              assertEquals(2, r.getInt(1));
                              assertEquals("road", r.getString(2));
                            }
                            assertFalse(r.next());
                          }
                        } else {
                          if (List.of(
                                  "wider",
                                  "shorter",
                                  "gap",
                                  "mixed",
                                  "repeatedHeader",
                                  "firstMismatch",
                                  "schemaMismatch")
                              .contains(scenario)) {
                            assertTrue(
                                console.message.toLowerCase().contains("row 3"), console.message);
                          }
                          if (List.of("twoSheets", "hiddenSecond", "corruptSecond")
                              .contains(scenario)) {
                            assertTrue(
                                console.message.contains("exactly one sheet"), console.message);
                          }
                          original(db, existing);
                        }
                        assertFalse(db.connection.isClosed());
                        assertTrue(db.connection.getAutoCommit());
                      }
                    }));
          }
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> csvLayouts() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean header : new boolean[] {false, true}) {
        for (boolean existing : new boolean[] {false, true}) {
          if (!header && !existing) {
            continue;
          }
          for (String scenario :
              List.of("shorter", "wider", "blank", "mixed", "repeatedHeader", "firstMismatch")) {
            tests.add(
                dynamicTest(
                    "csv/" + hsql + "/" + header + "/" + existing + "/" + scenario,
                    () -> {
                      Path folder = Files.createTempDirectory(directory, "csv-");
                      try (DatabaseFixture db = new DatabaseFixture(folder, hsql);
                          Console console = new Console(db.project)) {
                        if (existing) {
                          db.execute("CREATE TABLE data(id INTEGER,label VARCHAR(16))");
                          db.execute("INSERT INTO data VALUES(99,'original')");
                        }
                        String bad =
                            scenario.equals("shorter")
                                ? "2\n"
                                : scenario.equals("wider")
                                    ? "2,road,extra\n"
                                    : scenario.equals("blank")
                                        ? "\n"
                                        : scenario.equals("repeatedHeader")
                                            ? "id,label\n"
                                            : "wrong,road\n";
                        String tail = scenario.equals("firstMismatch") ? "\"unterminated\n" : "";
                        Files.writeString(
                            folder.resolve("data.csv"),
                            (header ? "id,label\n" : "") + "1,rail\n" + bad + tail);
                        assertFalse(
                            execute(console, (header ? "IMPORTCSVH" : "IMPORTCSV") + " data"));
                        assertTrue(
                            console.message.contains("record " + (header ? 3 : 2)),
                            console.message);
                        original(db, existing);
                      }
                    }));
          }
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> parquetAndDbfErrorsUseOnlyTheConsole() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (String format : List.of("nested", "parquetRow", "dbf")) {
        tests.add(
            dynamicTest(
                format + "/" + hsql,
                () -> {
                  Path folder = Files.createTempDirectory(directory, "typed-");
                  try (DatabaseFixture db = new DatabaseFixture(folder, hsql);
                      Console console = new Console(db.project)) {
                    db.execute("CREATE TABLE data(id INTEGER,label VARCHAR(16))");
                    db.execute("INSERT INTO data VALUES(99,'original')");
                    if (format.equals("dbf")) {
                      assertTrue(ExportDBF.exportTable(db.project, "data"));
                      byte[] bytes = Files.readAllBytes(folder.resolve("data.dbf"));
                      System.arraycopy(bytes, 32, bytes, 64, 32);
                      Files.write(folder.resolve("data.dbf"), bytes);
                    } else {
                      writeParquet(folder.resolve("data.parquet"), format.equals("nested"));
                    }
                    assertFalse(
                        execute(
                            console,
                            (format.equals("dbf") ? "IMPORTDBF" : "IMPORTPARQUET") + " data"));
                    if (format.equals("parquetRow")) {
                      assertTrue(console.message.contains("row 2"), console.message);
                    }
                    if (format.equals("nested")) {
                      assertTrue(console.message.contains("Nested/repeated"), console.message);
                    }
                    original(db, true);
                  }
                }));
      }
    }
    return tests;
  }

  private static boolean execute(Console console, String command) throws Exception {
    PrintStream previous = System.err;
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    boolean result;
    try (PrintStream capture = new PrintStream(errors)) {
      System.setErr(capture);
      result = console.runBatch(new String[] {command});
    } finally {
      System.setErr(previous);
    }
    assertFalse(errors.toString(StandardCharsets.UTF_8).contains("\tat "), errors.toString());
    assertNull(console.getFrame());
    return result;
  }

  private static void original(DatabaseFixture db, boolean existing) throws Exception {
    assertEquals(existing, JDBCUtils.tableExists(db.connection, "data"));
    if (existing) {
      try (Statement s = db.connection.createStatement();
          ResultSet r = s.executeQuery("SELECT * FROM data")) {
        assertTrue(r.next());
        assertEquals(99, r.getInt(1));
        assertEquals("original", r.getString(2));
        assertFalse(r.next());
      }
    }
  }

  private static void writeSheet(Path folder, String format, String scenario) throws Exception {
    boolean bare = scenario.startsWith("bare") || scenario.equals("validBareTrailing");
    Object[][] rows =
        bare
            ? new Object[][] {{1, "rail"}, {2, "road"}}
            : new Object[][] {{"id", "label"}, {1, "rail"}, {2, "road"}};
    int bad = rows.length - 1;
    switch (scenario) {
      case "shorter":
      case "bareShorter":
        rows[bad] = new Object[] {2};
        break;
      case "wider":
        rows[bad] = new Object[] {2, "road", "extra"};
        break;
      case "mixed":
      case "bareMixed":
      case "firstMismatch":
        rows[bad] = new Object[] {2, 42};
        break;
      case "repeatedHeader":
        rows[bad] = new Object[] {"id", "label"};
        break;
      case "gap":
        rows = new Object[][] {{"id", "label"}, {1, "rail"}, null, {2, "road"}};
        break;
      case "schemaMismatch":
        rows[0] = new Object[] {"id,N,8,0", "label,C,16"};
        rows[bad] = new Object[] {"wrong", "road"};
        break;
      case "validNulls":
        rows[bad] = new Object[] {2, null};
        break;
      case "validDecimals":
        rows[bad] = new Object[] {2.5, "road"};
        break;
      case "validSchemaTrailing":
        rows[0] = new Object[] {"id,N,8,0", "label,C,16"};
        break;
      default:
        break;
    }
    if (scenario.endsWith("Trailing")) {
      rows = java.util.Arrays.copyOf(rows, rows.length + 5);
      rows[bad + 1] = new Object[] {null, null};
      rows[bad + 2] = new Object[] {null, null};
      // The missing row, short blank row and formatted empty row are all outside the table.
      rows[bad + 4] = new Object[] {null};
      rows[bad + 5] = new Object[0];
    }
    boolean multiple = List.of("twoSheets", "hiddenSecond", "corruptSecond").contains(scenario);
    Path path = folder.resolve("data." + format);
    if (format.equals("ods")) {
      writeOds(path, rows, multiple, scenario.equals("merged"));
      return;
    }
    try (Workbook book = format.equals("xlsx") ? new XSSFWorkbook() : new HSSFWorkbook();
        OutputStream out = Files.newOutputStream(path)) {
      var sheet = book.createSheet("data");
      for (int r = 0; r < rows.length; r++) {
        if (rows[r] == null) {
          continue;
        }
        var row = sheet.createRow(r);
        for (int c = 0; c < rows[r].length; c++) {
          var cell = row.createCell(c);
          Object value = rows[r][c];
          if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
          } else if (value != null) {
            cell.setCellValue(value.toString());
          }
        }
      }
      if (scenario.equals("firstMismatch")) {
        sheet
            .createRow(99)
            .createCell(0)
            .setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.NA.getCode());
      }
      if (scenario.equals("merged")) {
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
      }
      if (multiple) {
        book.createSheet("other");
        if (scenario.equals("hiddenSecond")) {
          book.setSheetHidden(1, true);
        }
      }
      book.write(out);
    }
    if (format.equals("xlsx") && scenario.equals("corruptSecond")) {
      Path replacement = folder.resolve("corrupt.xlsx");
      try (ZipFile zip = new ZipFile(path.toFile());
          ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(replacement))) {
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          out.putNextEntry(new ZipEntry(entry.getName()));
          if (entry.getName().equals("xl/worksheets/sheet2.xml")) {
            out.write("not XML".getBytes(StandardCharsets.UTF_8));
          } else {
            try (var in = zip.getInputStream(entry)) {
              in.transferTo(out);
            }
          }
          out.closeEntry();
        }
      }
      Files.move(replacement, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void writeOds(Path path, Object[][] rows, boolean multiple, boolean merged)
      throws Exception {
    String office = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    String table = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    String text = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
      zip.putNextEntry(new ZipEntry("content.xml"));
      XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(zip, "UTF-8");
      xml.writeStartElement("office", "document-content", office);
      xml.writeNamespace("office", office);
      xml.writeNamespace("table", table);
      xml.writeNamespace("text", text);
      xml.writeStartElement("table", "table", table);
      for (int r = 0; r < rows.length; r++) {
        xml.writeStartElement("table", "table-row", table);
        if (rows[r] != null) {
          for (int c = 0; c < rows[r].length; c++) {
            Object value = rows[r][c];
            xml.writeStartElement("table", "table-cell", table);
            if (merged && r == 0 && c == 0) {
              xml.writeAttribute("table", table, "number-columns-spanned", "2");
            }
            if (value != null) {
              boolean number = value instanceof Number;
              xml.writeAttribute("office", office, "value-type", number ? "float" : "string");
              if (number) {
                xml.writeAttribute("office", office, "value", value.toString());
              }
              xml.writeStartElement("text", "p", text);
              xml.writeCharacters(value.toString());
              xml.writeEndElement();
            }
            xml.writeEndElement();
          }
        }
        xml.writeEndElement();
      }
      xml.writeEndElement();
      if (multiple) {
        xml.writeEmptyElement("table", "table", table);
      }
      xml.writeEndElement();
      xml.flush();
      xml.close();
      zip.closeEntry();
    }
  }

  private static void writeParquet(Path path, boolean nested) throws Exception {
    var schema =
        org.apache.parquet.schema.MessageTypeParser.parseMessageType(
            nested
                ? "message data { optional group details { optional int32 id; } }"
                : "message data { required int64 amount (DECIMAL(3,1)); }");
    try (var writer =
        org.apache.parquet.hadoop.example.ExampleParquetWriter.builder(
                new org.apache.parquet.io.LocalOutputFile(path))
            .withConf(new org.apache.parquet.conf.PlainParquetConfiguration())
            .withType(schema)
            .withCompressionCodec(
                org.apache.parquet.hadoop.metadata.CompressionCodecName.UNCOMPRESSED)
            .build()) {
      var factory = new org.apache.parquet.example.data.simple.SimpleGroupFactory(schema);
      if (nested) {
        var row = factory.newGroup();
        row.addGroup("details").append("id", 1);
        writer.write(row);
      } else {
        writer.write(factory.newGroup().append("amount", 10L));
        writer.write(factory.newGroup().append("amount", 1000L));
      }
    }
  }

  private static final class Console extends SQLConsole implements AutoCloseable {
    String message;

    Console(NodusProject project) {
      super(project, false);
    }

    @Override
    public void displayMessageInResult(String head, String text) {
      message = text;
      super.displayMessageInResult(head, text);
    }

    @Override
    public void close() {
      windowClosing(null);
    }
  }
}
