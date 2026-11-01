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

package edu.uclouvain.core.nodus.database.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.database.ImportProgress;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class CsvOptionsTest {
  @TestFactory
  List<DynamicTest> automaticDetectionRespectsQuotesAndReplaysTheSampleExactly() {
    List<DynamicTest> tests = new ArrayList<>();
    for (char delimiter : new char[] {',', ';', '\t', '|'}) {
      for (String encoding : List.of("UTF-8", "UTF-16", "UTF-16LE", "windows-1252")) {
        tests.add(
            dynamicTest(
                encoding + "/" + (int) delimiter,
                () -> {
                  String label = "Paris, France;\tbus|\"rail\"\nnext";
                  String csv =
                      "\uFEFFid"
                          + delimiter
                          + "label\r\n1"
                          + delimiter
                          + "\""
                          + label.replace("\"", "\"\"")
                          + "\"\r\n2"
                          + delimiter
                          + "road\r\n";
                  CsvOptions options = new CsvOptions(encoding, ',', true);
                  // A UTF BOM is meaningful only in Unicode encodings, not Windows-1252.
                  if (encoding.equals("windows-1252")) {
                    csv = csv.substring(1);
                  }
                  try (var input = new ByteArrayInputStream(csv.getBytes(options.getCharset()));
                      var reader = options.reader(input)) {
                    var format = options.format(reader, new ImportProgress());
                    assertEquals(delimiter, format.getDelimiterString().charAt(0));
                    try (var parser = format.parse(reader)) {
                      List<CSVRecord> rows = parser.getRecords();
                      assertEquals(3, rows.size());
                      assertEquals(List.of("id", "label"), rows.get(0).toList());
                      assertEquals(List.of("1", label), rows.get(1).toList());
                      assertEquals(List.of("2", "road"), rows.get(2).toList());
                    }
                  }
                }));
      }
    }
    return tests;
  }

  @Test
  void ambiguousSeparatorsFailAndSingleColumnsUseComma() throws Exception {
    CsvOptions options = new CsvOptions("UTF-8", ',', true);
    try (var reader =
        options.reader(
            new ByteArrayInputStream("a,b;c\nd,e;f\n".getBytes(StandardCharsets.UTF_8)))) {
      assertTrue(
          assertThrows(IOException.class, () -> options.format(reader, new ImportProgress()))
              .getMessage()
              .contains("Ambiguous"));
    }
    try (var reader =
        options.reader(
            new ByteArrayInputStream("name\nrail\nroad\n".getBytes(StandardCharsets.UTF_8)))) {
      assertEquals(",", options.format(reader, new ImportProgress()).getDelimiterString());
    }
  }

  @Test
  void detectionHasABoundedReadAndLeavesTheFullFileAvailable() throws Exception {
    String first = "id;label\n" + "1;rail\n".repeat(20000);
    byte[] data = first.getBytes(StandardCharsets.UTF_8);
    long[] read = {0};
    try (var input =
        new FilterInputStream(new ByteArrayInputStream(data)) {
          @Override
          public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = in.read(bytes, offset, length);
            if (count > 0) {
              read[0] += count;
            }
            return count;
          }
        }) {
      CsvOptions options = new CsvOptions("UTF-8", ',', true);
      try (var reader = options.reader(input)) {
        var format = options.format(reader, new ImportProgress());
        assertTrue(read[0] < 100000, "Detection must not read the entire source: " + read[0]);
        assertEquals(";", format.getDelimiterString());
        try (var parser = format.parse(reader)) {
          assertEquals(20001, parser.getRecords().size());
        }
      }
    }
  }

  @Test
  void longQuotedRecordAtTheSampleBoundaryDoesNotHideEarlierCompleteRows() throws Exception {
    String csv = "id;label\n1;road\n2;\"" + "x;\n".repeat(30000) + "\"\n";
    CsvOptions options = new CsvOptions("UTF-8", ',', true);
    try (var reader =
        options.reader(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)))) {
      var format = options.format(reader, new ImportProgress());
      assertEquals(";", format.getDelimiterString());
      try (var parser = format.parse(reader)) {
        assertEquals(3, parser.getRecords().size());
      }
    }
  }

  @Test
  void incompleteFirstRecordRequiresAnExplicitDelimiterAndMalformedBytesAreRejected()
      throws Exception {
    CsvOptions options = new CsvOptions("UTF-8", ',', true);
    String csv = "\"" + "x".repeat(100000) + "\";label\n";
    try (var reader =
        options.reader(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)))) {
      assertTrue(
          assertThrows(IOException.class, () -> options.format(reader, new ImportProgress()))
              .getMessage()
              .contains("specify DELIMITER"));
    }
    try (var input = new ByteArrayInputStream(new byte[] {(byte) 0xc3, 0x28})) {
      assertThrows(IOException.class, () -> new CsvOptions("UTF-8", ',', false).reader(input));
    }
  }
}
