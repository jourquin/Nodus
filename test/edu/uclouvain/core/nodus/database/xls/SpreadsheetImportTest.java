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

package edu.uclouvain.core.nodus.database.xls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real imports exercise schema inference, strict validation and preservation of existing data. */
@ResourceLock("JDBCUtils")
class SpreadsheetImportTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> formatsAndDatabases() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String format : List.of("xls", "xlsx", "ods")) {
      for (boolean hsql : new boolean[] {false, true}) {
        for (String scenario :
            List.of(
                "infer",
                "unnamed",
                "reorder",
                "extra",
                "short",
                "wrongtype",
                "notnull",
                "toolong",
                "fraction",
                "duplicate",
                "missing",
                "latecolumn",
                "invalidschema",
                "empty",
                "headersOnly",
                "constraints",
                "schema")) {
          tests.add(
              dynamicTest(
                  format + "/" + hsql + "/" + scenario,
                  () -> {
                    try (DatabaseFixture db = new DatabaseFixture(directory, hsql)) {
                      check(db, format, scenario);
                    }
                  }));
        }
      }
    }
    return tests;
  }

  private void check(DatabaseFixture db, String format, String scenario) throws Exception {
    List<Exception> errors = new ArrayList<>();
    if (scenario.equals("infer")) {
      write(
          format,
          new Object[][] {
            {"code", "amount", "mixed", "flag", "shipdate", "stamp", "clock", "empty"},
            {
              "00123",
              1,
              2,
              true,
              LocalDate.of(2026, 10, 31),
              LocalDateTime.of(2026, 10, 31, 12, 30),
              LocalTime.of(12, 30),
              null
            },
            {"00124", null, "rail", false, null, null, null, null},
            {"a much longer identifier", 123.456, "", null, null, null, null, null}
          });
      assertTrue(importFile(db, format, true, errors), errors.toString());
      try (Statement s = db.connection.createStatement();
          ResultSet r = s.executeQuery("SELECT * FROM data ORDER BY code")) {
        assertEquals(8, r.getMetaData().getColumnCount());
        assertEquals(Types.DECIMAL, r.getMetaData().getColumnType(2));
        assertEquals(3, r.getMetaData().getScale(2));
        assertEquals(Types.DATE, r.getMetaData().getColumnType(5));
        assertTrue(r.next());
        assertEquals("00123", r.getString(1));
        assertEquals("2.0", r.getString(3));
        assertTrue(r.getBoolean(4));
        assertEquals(LocalDate.of(2026, 10, 31), r.getObject(5, LocalDate.class));
        assertEquals(LocalTime.of(12, 30), r.getObject(7, LocalTime.class));
        assertNull(r.getObject(8));
        assertTrue(r.next());
        assertNull(r.getObject(2));
        assertTrue(r.next());
        assertEquals(123.456, r.getDouble(2));
        assertFalse(r.next());
      }
      return;
    }
    if (scenario.equals("unnamed")) {
      write(format, new Object[][] {{"00123", 42}, {"abc", 43}});
      assertTrue(importFile(db, format, false, errors), errors.toString());
      assertEquals("00123", scalar(db, "SELECT col1 FROM data WHERE col2=42"));
      return;
    }
    if (scenario.equals("headersOnly")) {
      write(format, new Object[][] {{"first", "second"}});
      assertTrue(importFile(db, format, true, errors), errors.toString());
      assertEquals("0", scalar(db, "SELECT COUNT(*) FROM data"));
      return;
    }
    if (scenario.equals("empty")) {
      write(format, new Object[0][]);
      assertFalse(importFile(db, format, false, errors));
      assertFalse(JDBCUtils.tableExists(db.connection, "data"));
      return;
    }
    db.execute("CREATE TABLE data (id INTEGER PRIMARY KEY, label VARCHAR(8))");
    db.execute("INSERT INTO data VALUES (99, 'original')");
    if (scenario.equals("reorder")) {
      write(format, new Object[][] {{"LABEL", "ID"}, {"new", 42}, {null, 43}});
      assertTrue(importFile(db, format, true, errors), errors.toString());
      assertEquals("new", scalar(db, "SELECT label FROM data WHERE id=42"));
      assertNull(scalar(db, "SELECT label FROM data WHERE id=43"));
      assertThrows(
          java.sql.SQLException.class, () -> db.execute("INSERT INTO data VALUES (42,'x')"));
      return;
    }
    if (scenario.equals("schema")) {
      write(format, new Object[][] {{"newname,C,12", "quantity,N,8,2"}, {"rail", 1.25}});
      assertTrue(importFile(db, format, false, errors), errors.toString());
      assertEquals("rail", scalar(db, "SELECT newname FROM data"));
      return;
    }
    Object[][] rows;
    boolean header = false;
    switch (scenario) {
      case "extra":
        rows = new Object[][] {{42, "new", "extra"}};
        break;
      case "short":
        rows = new Object[][] {{42}};
        break;
      case "notnull":
        rows = new Object[][] {{null, "new"}};
        break;
      case "wrongtype":
        rows = new Object[][] {{"42", "new"}};
        break;
      case "toolong":
        rows = new Object[][] {{42, "much too long"}};
        break;
      case "fraction":
        rows = new Object[][] {{42.5, "new"}};
        break;
      case "duplicate":
        header = true;
        rows = new Object[][] {{"id", "ID"}, {42, "new"}};
        break;
      case "missing":
        header = true;
        rows = new Object[][] {{"id", "other"}, {42, "new"}};
        break;
      case "latecolumn":
        rows = new Object[][] {{42, "new"}, {43, "new", "extra"}};
        break;
      case "invalidschema":
        rows = new Object[][] {{"id,N,8,0", "label,C,no"}, {42, "new"}};
        break;
      case "constraints":
        rows = new Object[][] {{42, "new"}, {43, "new"}, {42, "new"}};
        break;
      default:
        throw new IllegalArgumentException(scenario);
    }
    write(format, rows);
    db.execute("CREATE TABLE earlier (id INTEGER)");
    db.connection.setAutoCommit(false);
    db.execute("INSERT INTO earlier VALUES (7)");
    assertFalse(importFile(db, format, header, errors));
    assertFalse(errors.isEmpty());
    if (List.of("wrongtype", "toolong", "fraction", "duplicate", "invalidschema")
        .contains(scenario)) {
      assertTrue(errors.get(0).getMessage().contains("row 1, column"), errors.toString());
    }
    assertEquals("original", scalar(db, "SELECT label FROM data WHERE id=99"));
    assertEquals("1", scalar(db, "SELECT COUNT(*) FROM data"));
    assertEquals("1", scalar(db, "SELECT COUNT(*) FROM earlier"));
    assertFalse(db.connection.getAutoCommit());
    db.connection.rollback();
    assertEquals("0", scalar(db, "SELECT COUNT(*) FROM earlier"));
  }

  private static String scalar(DatabaseFixture db, String sql) throws Exception {
    try (Statement s = db.connection.createStatement();
        ResultSet r = s.executeQuery(sql)) {
      assertTrue(r.next());
      return r.getString(1);
    }
  }

  private boolean importFile(
      DatabaseFixture db, String format, boolean header, List<Exception> errors) {
    return format.equals("ods")
        ? ImportXLS.importODS(db.project, "data", header, errors::add)
        : ImportXLS.importTable(db.project, "data", format.equals("xlsx"), header, errors::add);
  }

  private void write(String format, Object[][] values) throws Exception {
    Path path = directory.resolve("data." + format);
    if (format.equals("ods")) {
      writeOds(path, values);
      return;
    }
    try (Workbook book = format.equals("xlsx") ? new XSSFWorkbook() : new HSSFWorkbook();
        OutputStream output = Files.newOutputStream(path)) {
      var sheet = book.createSheet();
      for (int r = 0; r < values.length; r++) {
        var row = sheet.createRow(r);
        for (int c = 0; c < values[r].length; c++) {
          Object value = values[r][c];
          Cell cell = row.createCell(c);
          if (value == null) {
            continue;
          }
          if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
          } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
          } else if (value instanceof LocalDate
              || value instanceof LocalDateTime
              || value instanceof LocalTime) {
            var style = book.createCellStyle();
            String pattern =
                value instanceof LocalDate
                    ? "yyyy-mm-dd"
                    : value instanceof LocalTime ? "hh:mm:ss" : "yyyy-mm-dd hh:mm:ss";
            style.setDataFormat(book.createDataFormat().getFormat(pattern));
            cell.setCellStyle(style);
            if (value instanceof LocalDate) {
              cell.setCellValue((LocalDate) value);
            } else if (value instanceof LocalDateTime) {
              cell.setCellValue((LocalDateTime) value);
            } else {
              cell.setCellValue(((LocalTime) value).toSecondOfDay() / 86400.0);
            }
          } else {
            cell.setCellValue(value.toString());
          }
        }
      }
      book.write(output);
    }
  }

  private static void writeOds(Path path, Object[][] rows) throws Exception {
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
      for (Object[] row : rows) {
        xml.writeStartElement("table", "table-row", table);
        for (Object value : row) {
          xml.writeStartElement("table", "table-cell", table);
          if (value != null) {
            String type =
                value instanceof Number
                    ? "float"
                    : value instanceof Boolean
                        ? "boolean"
                        : value instanceof LocalDate || value instanceof LocalDateTime
                            ? "date"
                            : value instanceof LocalTime ? "time" : "string";
            xml.writeAttribute("office", office, "value-type", type);
            String attribute = type.equals("float") ? "value" : type + "-value";
            String data =
                value instanceof LocalTime
                    ? java.time.Duration.ofSeconds(((LocalTime) value).toSecondOfDay()).toString()
                    : value.toString();
            xml.writeAttribute("office", office, attribute, data);
            xml.writeStartElement("text", "p", text);
            xml.writeCharacters(value.toString());
            xml.writeEndElement();
          }
          xml.writeEndElement();
        }
        xml.writeEndElement();
      }
      xml.writeEndElement();
      xml.writeEndElement();
      xml.flush();
      xml.close();
      zip.closeEntry();
    }
  }
}
