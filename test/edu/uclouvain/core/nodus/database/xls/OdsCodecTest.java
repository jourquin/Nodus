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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class OdsCodecTest {
  private static final String PREFIX =
      "<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" "
          + "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" "
          + "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">"
          + "<office:body><office:spreadsheet><table:table table:name=\"Sheet1\">";
  private static final String SUFFIX =
      "</table:table></office:spreadsheet></office:body></office:document-content>";

  private InputStream ods(String xml) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("content.xml"));
      zip.write(xml.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return new ByteArrayInputStream(bytes.toByteArray());
  }

  @Test
  void readsRepeatedRowsCellsWhitespaceAndCachedFormulas() throws Exception {
    String xml =
        PREFIX
            + "<table:table-row table:number-rows-repeated=\"2\">"
            + ("<table:table-cell office:value-type=\"string\"><text:p>été<t"
                + "ext:s text:c=\"3\"/>rail<text:tab/>x<text:line-break/>y</tex"
                + "t:p></table:table-cell>")
            + ("<table:table-cell table:number-columns-repeated=\"2\" office"
                + ":value-type=\"float\" office:value=\"42\" table:formula=\"of"
                + ":=21*2\"/>")
            + "</table:table-row>"
            + ("<table:table-row table:number-rows-repeated=\"1048574\"><tab"
                + "le:table-cell table:number-columns-repeated=\"16384\"/></tab"
                + "le:table-row>")
            + SUFFIX;
    try (Workbook book = new XSSFWorkbook();
        InputStream input = ods(xml)) {
      OdsCodec.read(input, book);
      assertEquals(1, book.getSheetAt(0).getLastRowNum());
      assertEquals(
          "été   rail\tx\ny", book.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
      assertEquals(42, book.getSheetAt(0).getRow(1).getCell(2).getNumericCellValue());
    }
  }

  @Test
  void retainsExplicitEmptyDataRows() throws Exception {
    String xml = PREFIX + "<table:table-row><table:table-cell/></table:table-row>" + SUFFIX;
    try (Workbook book = new XSSFWorkbook();
        InputStream input = ods(xml)) {
      OdsCodec.read(input, book);
      assertEquals(1, book.getSheetAt(0).getPhysicalNumberOfRows());
    }
  }

  @Test
  void rejectsExternalEntitiesAndUncachedFormulas() throws Exception {
    try (Workbook book = new XSSFWorkbook()) {
      assertThrows(
          Exception.class,
          () ->
              OdsCodec.read(
                  ods(
                      ("<!DOCTYPE office:document-content [<!ENTITY xxe SYSTEM 'file"
                              + ":///etc/passwd'>]>")
                          + PREFIX
                          + SUFFIX),
                  book));
    }
    try (Workbook book = new XSSFWorkbook()) {
      assertThrows(
          IOException.class,
          () ->
              OdsCodec.read(
                  ods(
                      PREFIX
                          + "<table:table-row><table:table-cell table:formula=\"of:=1+1\"><text:p/>"
                          + "</table:table-cell></table:table-row>"
                          + SUFFIX),
                  book));
    }
  }
}
