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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.ExportFileFaults;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Verifies ODS streaming, spreadsheet compatibility and atomic failure handling. */
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_ERR")
class OdsStreamingTest {
  private static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
  private static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
  @TempDir Path directory;

  @Test
  void streamsNineHundredThousandCellsWith64MiBHeap() throws Exception {
    Path output = directory.resolve("stream.ods");
    Path log = directory.resolve("stream.log");
    Process child =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx64m",
                "-Djava.awt.headless=true",
                "-cp",
                System.getProperty("java.class.path"),
                OdsStreamingProbe.class.getName(),
                output.toString())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    try {
      assertTrue(child.waitFor(60, TimeUnit.SECONDS), "ODS streaming timed out");
      assertEquals(0, child.exitValue(), Files.readString(log));
    } finally {
      if (child.isAlive()) {
        child.destroyForcibly();
      }
    }
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    try (ZipFile zip = new ZipFile(output.toFile());
        InputStream input = zip.getInputStream(zip.getEntry("content.xml"))) {
      assertEquals("mimetype", zip.entries().nextElement().getName());
      XMLStreamReader reader = factory.createXMLStreamReader(input);
      long rows = 0;
      long numbers = 0;
      double last = 0;
      try {
        while (reader.hasNext()) {
          if (reader.next() != XMLStreamConstants.START_ELEMENT
              || !TABLE.equals(reader.getNamespaceURI())) {
            continue;
          }
          if (reader.getLocalName().equals("table-row")) {
            rows++;
          } else if (reader.getLocalName().equals("table-cell")) {
            String value = reader.getAttributeValue(OFFICE, "value");
            if (value != null) {
              numbers++;
              last = Double.parseDouble(value);
            }
          }
        }
      } finally {
        reader.close();
      }
      assertEquals(OdsStreamingProbe.ROWS + 1, rows);
      assertEquals((long) OdsStreamingProbe.ROWS * OdsStreamingProbe.COLUMNS, numbers);
      assertEquals(OdsStreamingProbe.ROWS + OdsStreamingProbe.COLUMNS, last);
    }
  }

  @Test
  void preservesExcelSchemaAndUnicodeText() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      Path folder = Files.createTempDirectory(directory, "schema-");
      try (DatabaseFixture database = new DatabaseFixture(folder, hsql)) {
        database.execute(
            "CREATE TABLE data (id INTEGER, label VARCHAR(80),"
                + " amount DECIMAL(12,3), shipdate DATE)");
        String label = "  été 🚚 & <rail>\tstop\nend  ";
        try (java.sql.PreparedStatement insert =
            database.connection.prepareStatement("INSERT INTO data VALUES (?, ?, ?, ?)")) {
          insert.setInt(1, 42);
          insert.setString(2, label);
          insert.setBigDecimal(3, new java.math.BigDecimal("123.456"));
          insert.setDate(4, java.sql.Date.valueOf("2026-10-31"));
          insert.executeUpdate();
        }
        assertTrue(ExportXLS.exportODS(database.project, "data"));
        assertTrue(ExportXLS.exportTable(database.project, "data", true));
        try (Workbook ods = new XSSFWorkbook();
            InputStream odsInput = Files.newInputStream(folder.resolve("data.ods"));
            InputStream xlsxInput = Files.newInputStream(folder.resolve("data.xlsx"));
            Workbook xlsx = WorkbookFactory.create(xlsxInput)) {
          OdsCodec.read(odsInput, ods);
          for (int c = 0; c < 4; c++) {
            assertEquals(
                xlsx.getSheetAt(0).getRow(0).getCell(c).getStringCellValue(),
                ods.getSheetAt(0).getRow(0).getCell(c).getStringCellValue());
          }
          assertEquals(label, ods.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
          assertEquals(123.456, ods.getSheetAt(0).getRow(1).getCell(2).getNumericCellValue());
        }
      }
    }
  }

  @TestFactory
  List<DynamicTest> failedWritesPreserveExistingExports() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String phase : List.of("open", "write", "flush", "close", "replace", "unsupported")) {
      tests.add(
          dynamicTest(
              phase,
              () -> {
                Path folder = Files.createTempDirectory(directory, "failure-");
                try (DatabaseFixture database = new DatabaseFixture(folder)) {
                  database.execute("CREATE TABLE data (id INTEGER)");
                  database.execute("INSERT INTO data VALUES (42)");
                  Path output = folder.resolve("data.ods");
                  Files.writeString(output, "original", StandardCharsets.UTF_8);
                  assertFalse(
                      ExportFileFaults.quiet(
                          () ->
                              ExportODS.exportTable(
                                  database.project, "data", new ExportFileFaults(phase))));
                  assertEquals("original", Files.readString(output));
                  assertEquals(List.of(output), ExportFileFaults.regularFiles(folder));
                  assertFalse(database.connection.isClosed());
                }
              }));
    }
    return tests;
  }
}
