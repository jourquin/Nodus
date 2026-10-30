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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** First-sheet ODS interchange using the JDK ZIP/XML APIs and the existing Excel table model. */
final class OdsCodec {
  private static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
  private static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
  private static final String TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
  private static final byte[] MIME =
      "application/vnd.oasis.opendocument.spreadsheet"
          .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  private OdsCodec() {}

  static void write(Workbook book, OutputStream output) throws Exception {
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      ZipEntry mime = new ZipEntry("mimetype");
      mime.setMethod(ZipEntry.STORED);
      mime.setSize(MIME.length);
      CRC32 crc = new CRC32();
      crc.update(MIME);
      mime.setCrc(crc.getValue());
      zip.putNextEntry(mime);
      zip.write(MIME);
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
      zip.write(
          ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                  + "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\">"
                  + "<manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.spreadsheet\"/>"
                  + "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>"
                  + "</manifest:manifest>")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("content.xml"));
      XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(zip, "UTF-8");
      xml.writeStartDocument("UTF-8", "1.0");
      xml.writeStartElement("office", "document-content", OFFICE);
      xml.writeNamespace("office", OFFICE);
      xml.writeNamespace("table", TABLE);
      xml.writeNamespace("text", TEXT);
      xml.writeAttribute("office", OFFICE, "version", "1.2");
      xml.writeStartElement("office", "body", OFFICE);
      xml.writeStartElement("office", "spreadsheet", OFFICE);
      xml.writeStartElement("table", "table", TABLE);
      xml.writeAttribute("table", TABLE, "name", book.getSheetName(0));
      for (Row row : book.getSheetAt(0)) {
        xml.writeStartElement("table", "table-row", TABLE);
        for (int i = 0; i < row.getLastCellNum(); i++) {
          Cell cell = row.getCell(i);
          xml.writeStartElement("table", "table-cell", TABLE);
          if (cell != null && cell.getCellType() != CellType.BLANK) {
            boolean numeric = cell.getCellType() == CellType.NUMERIC;
            xml.writeAttribute("office", OFFICE, "value-type", numeric ? "float" : "string");
            if (numeric)
              xml.writeAttribute(
                  "office", OFFICE, "value", Double.toString(cell.getNumericCellValue()));
            xml.writeStartElement("text", "p", TEXT);
            if (numeric) xml.writeCharacters(Double.toString(cell.getNumericCellValue()));
            else {
              // Explicit spaces preserve leading, repeated and trailing whitespace in ODF readers.
              String value = cell.getStringCellValue();
              for (int j = 0; j < value.length(); j++) {
                char ch = value.charAt(j);
                if (ch == ' ') xml.writeEmptyElement("text", "s", TEXT);
                else if (ch == '\t') xml.writeEmptyElement("text", "tab", TEXT);
                else if (ch == '\n') xml.writeEmptyElement("text", "line-break", TEXT);
                else xml.writeCharacters(String.valueOf(ch));
              }
            }
            xml.writeEndElement();
          }
          xml.writeEndElement();
        }
        xml.writeEndElement();
      }
      xml.writeEndElement();
      xml.writeEndElement();
      xml.writeEndElement();
      xml.writeEndElement();
      xml.writeEndDocument();
      xml.flush();
      zip.closeEntry();
    }
  }

  static Workbook read(InputStream input) throws Exception {
    Document document = null;
    try (ZipInputStream zip = new ZipInputStream(input)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!entry.getName().equals("content.xml")) continue;
        // Bound decompressed XML before DOM allocation; never resolve external entities.
        byte[] xml = zip.readNBytes(64 * 1024 * 1024 + 1);
        if (xml.length > 64 * 1024 * 1024) throw new IOException("ODS content exceeds 64 MiB");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
        break;
      }
    }
    if (document == null) throw new IOException("ODS has no content.xml");
    NodeList tables = document.getElementsByTagNameNS(TABLE, "table");
    if (tables.getLength() == 0) throw new IOException("ODS has no sheet");
    Workbook book = new XSSFWorkbook();
    try {
      Sheet sheet = book.createSheet("data");
      NodeList rows = ((Element) tables.item(0)).getElementsByTagNameNS(TABLE, "table-row");
      int lastDataRow = rows.getLength() - 1;
      while (lastDataRow >= 0) {
        Element row = (Element) rows.item(lastDataRow);
        if (hasValue(row)
            || row.getElementsByTagNameNS(TEXT, "p").getLength() > 0
            || repeat(row, "number-rows-repeated", 1048576) == 1) break;
        lastDataRow--;
      }
      int rowIndex = 0;
      long cells = 0;
      for (int r = 0; r <= lastDataRow; r++) {
        Element source = (Element) rows.item(r);
        int repeatRows = repeat(source, "number-rows-repeated", 1048576);
        if ((long) rowIndex + repeatRows > 1048576) throw new IOException("Too many ODS rows");
        for (int n = 0; n < repeatRows; n++) {
          Row row = sheet.createRow(rowIndex++);
          int col = 0;
          for (Node node = source.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element) || !TABLE.equals(node.getNamespaceURI())) continue;
            Element cell = (Element) node;
            if (!cell.getLocalName().equals("table-cell")
                && !cell.getLocalName().equals("covered-table-cell")) continue;
            int count = repeat(cell, "number-columns-repeated", 16384);
            if ((long) col + count > 16384) throw new IOException("Too many ODS columns");
            String type = cell.getAttributeNS(OFFICE, "value-type");
            if (type.isEmpty()) {
              if (cell.hasAttributeNS(TABLE, "formula"))
                throw new IOException("ODS formula has no cached result");
              col += count;
              continue;
            }
            cells += count;
            if (cells > 2000000) throw new IOException("ODS exceeds two million populated cells");
            for (int c = 0; c < count; c++) {
              Cell target = row.createCell(col++);
              if (type.equals("float") || type.equals("currency") || type.equals("percentage")) {
                target.setCellValue(Double.parseDouble(cell.getAttributeNS(OFFICE, "value")));
              } else if (type.equals("boolean")) {
                target.setCellValue(cell.getAttributeNS(OFFICE, "boolean-value"));
              } else if (type.equals("date") || type.equals("time")) {
                target.setCellValue(cell.getAttributeNS(OFFICE, type + "-value"));
              } else {
                StringBuilder value = new StringBuilder();
                NodeList paragraphs = cell.getElementsByTagNameNS(TEXT, "p");
                for (int p = 0; p < paragraphs.getLength(); p++) {
                  if (p > 0) value.append('\n');
                  appendText(paragraphs.item(p), value);
                }
                target.setCellValue(
                    paragraphs.getLength() == 0
                        ? cell.getAttributeNS(OFFICE, "string-value")
                        : value.toString());
              }
            }
          }
        }
      }
      return book;
    } catch (Exception failure) {
      book.close();
      throw failure;
    }
  }

  private static boolean hasValue(Element row) {
    NodeList cells = row.getElementsByTagNameNS(TABLE, "table-cell");
    for (int i = 0; i < cells.getLength(); i++)
      if (((Element) cells.item(i)).hasAttributeNS(OFFICE, "value-type")
          || ((Element) cells.item(i)).hasAttributeNS(TABLE, "formula")) return true;
    return false;
  }

  private static int repeat(Element element, String attribute, int maximum) throws IOException {
    String value = element.getAttributeNS(attribute.equals("c") ? TEXT : TABLE, attribute);
    int count = value.isEmpty() ? 1 : Integer.parseInt(value);
    if (count < 1 || count > maximum) throw new IOException("Invalid ODS repetition");
    return count;
  }

  private static void appendText(Node node, StringBuilder value) throws IOException {
    if (node.getNodeType() == Node.TEXT_NODE) {
      value.append(node.getNodeValue());
      return;
    }
    if (TEXT.equals(node.getNamespaceURI())) {
      switch (node.getLocalName()) {
        case "s":
          value.append(" ".repeat(repeat((Element) node, "c", 32767)));
          return;
        case "tab":
          value.append('\t');
          return;
        case "line-break":
          value.append('\n');
          return;
        default:
          break;
      }
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
      appendText(child, value);
  }
}
