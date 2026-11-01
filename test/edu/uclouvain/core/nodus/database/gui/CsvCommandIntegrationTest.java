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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real SQL commands, byte encodings, CSV quoting and preservation after rejected input/output. */
@ResourceLock("JDBCUtils")
@ResourceLock("SYSTEM_ERR")
class CsvCommandIntegrationTest {
  private static final String LABEL = "L'été; \"rail\", bus\tpipe|line\nnext";
  private static final byte[] ORIGINAL = "keep previous export".getBytes(StandardCharsets.UTF_8);
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> exportsAndImportsRoundTripUsingEachCommandsOwnOptions() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean header : new boolean[] {false, true}) {
        for (String encoding : List.of("UTF-8", "windows-1252", "UTF-16LE", "UTF-16")) {
          for (char delimiter : new char[] {',', ';', '\t', '|', ' ', '\'', '§'}) {
            tests.add(
                dynamicTest(
                    hsql + "/" + header + "/" + encoding + "/" + (int) delimiter,
                    () -> {
                      Path folder = Files.createTempDirectory(directory, "roundtrip-");
                      try (DatabaseFixture db = new DatabaseFixture(folder, hsql);
                          Console console = new Console(db.project)) {
                        String table = "traffic  flows";
                        String quoted = JDBCUtils.getQuotedCompliantIdentifier(table);
                        db.execute("CREATE TABLE " + quoted + "(id INTEGER,label VARCHAR(300))");
                        try (PreparedStatement insert =
                            db.connection.prepareStatement(
                                "INSERT INTO " + quoted + " VALUES(?,?)")) {
                          for (int id = 1; id <= 2; id++) {
                            insert.setInt(1, id);
                            insert.setString(2, LABEL + id);
                            insert.executeUpdate();
                          }
                        }
                        String delimiterClause =
                            delimiter == '\t'
                                ? "TAB"
                                : "'" + String.valueOf(delimiter).replace("'", "''") + "'";
                        String options = " DELIMITER " + delimiterClause + " ENCODING " + encoding;
                        String suffix = header ? "H" : "";
                        assertTrue(
                            execute(console, "EXPORTCSV" + suffix + " \"" + table + "\"" + options),
                            console.message);
                        Path file = folder.resolve(table + ".csv");
                        String text = Files.readString(file, Charset.forName(encoding));
                        var format = CSVFormat.RFC4180.builder().setDelimiter(delimiter).get();
                        try (var parser = format.parse(new java.io.StringReader(text))) {
                          var rows = parser.getRecords();
                          assertEquals(header ? 3 : 2, rows.size());
                          assertEquals(LABEL + 1, rows.get(header ? 1 : 0).get(1));
                        }
                        db.execute("DELETE FROM " + quoted);
                        db.execute("INSERT INTO " + quoted + " VALUES(99,'original')");
                        assertTrue(
                            execute(
                                console,
                                "IMPORTCSV"
                                    + suffix
                                    + " \""
                                    + table
                                    + "\""
                                    + " ENCODING "
                                    + encoding
                                    + " DELIMITER "
                                    + delimiterClause),
                            console.message);
                        try (Statement s = db.connection.createStatement();
                            ResultSet rows =
                                s.executeQuery("SELECT * FROM " + quoted + " ORDER BY id")) {
                          for (int id = 1; id <= 2; id++) {
                            assertTrue(rows.next());
                            assertEquals(id, rows.getInt(1));
                            assertEquals(LABEL + id, rows.getString(2));
                          }
                          assertFalse(rows.next());
                        }
                      }
                    }));
          }
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> autoImportsWorkWithHeadersOrExistingTablesAndEachEncoding() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean header : new boolean[] {false, true}) {
        for (char delimiter : new char[] {',', ';', '\t', '|'}) {
          tests.add(
              dynamicTest(
                  hsql + "/" + header + "/" + (int) delimiter,
                  () -> {
                    Path folder = Files.createTempDirectory(directory, "auto-");
                    try (DatabaseFixture db = new DatabaseFixture(folder, hsql);
                        Console console = new Console(db.project)) {
                      if (!header) {
                        db.execute("CREATE TABLE data(id INTEGER,label VARCHAR(32))");
                        db.execute("INSERT INTO data VALUES(99,'original')");
                      }
                      String csv =
                          (header ? "id" + delimiter + "label\n" : "")
                              + "1"
                              + delimiter
                              + "été\n2"
                              + delimiter
                              + "route\n";
                      Files.writeString(
                          folder.resolve("data.csv"), csv, Charset.forName("windows-1252"));
                      assertTrue(
                          execute(
                              console,
                              "IMPORTCSV"
                                  + (header ? "H" : "")
                                  + " data ENCODING windows-1252 DELIMITER AUTO"),
                          console.message);
                      try (Statement s = db.connection.createStatement();
                          ResultSet rows = s.executeQuery("SELECT label FROM data WHERE id=1")) {
                        assertTrue(rows.next());
                        assertEquals("été", rows.getString(1));
                      }
                    }
                  }));
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> optionErrorsPrecedeOverwritePromptsAndLeaveTablesAndFilesUntouched() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String operation : List.of("IMPORTCSV", "IMPORTCSVH", "EXPORTCSV", "EXPORTCSVH")) {
      for (String options :
          List.of(
              "ENCODING unknown-charset",
              "DELIMITER '||'",
              "ENCODING UTF-8 ENCODING UTF-16",
              "DELIMITER TAB DELIMITER ';'",
              "DELIMITER")) {
        tests.add(
            dynamicTest(
                operation + "/" + options,
                () -> {
                  Path folder = Files.createTempDirectory(directory, "invalid-");
                  try (DatabaseFixture db = new DatabaseFixture(folder);
                      Console console = new Console(db.project)) {
                    db.execute("CREATE TABLE data(id INTEGER,label VARCHAR(32))");
                    db.execute("INSERT INTO data VALUES(99,'original')");
                    Files.write(folder.resolve("data.csv"), ORIGINAL);
                    console.getSqlCommandArea().setText(operation + " data " + options);
                    assertFalse(console.execute(false));
                    assertEquals(0, console.prompts);
                    assertArrayEquals(ORIGINAL, Files.readAllBytes(folder.resolve("data.csv")));
                    assertOriginal(db);
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> decodingAmbiguityAndLateRowFailuresPreserveEarlierWork() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (String failure : List.of("bytes", "ambiguous", "lateRow")) {
        tests.add(
            dynamicTest(
                hsql + "/" + failure,
                () -> {
                  Path folder = Files.createTempDirectory(directory, "failure-");
                  try (DatabaseFixture db = new DatabaseFixture(folder, hsql);
                      Console console = new Console(db.project)) {
                    db.execute("CREATE TABLE data(id INTEGER,label VARCHAR(32))");
                    db.execute("INSERT INTO data VALUES(99,'original')");
                    Path file = folder.resolve("data.csv");
                    if (failure.equals("bytes")) {
                      byte[] prefix = "id;label\n1;".getBytes(StandardCharsets.UTF_8);
                      byte[] bad = java.util.Arrays.copyOf(prefix, prefix.length + 2);
                      bad[prefix.length] = (byte) 0xc3;
                      bad[prefix.length + 1] = 0x28;
                      Files.write(file, bad);
                    } else if (failure.equals("ambiguous")) {
                      Files.writeString(file, "id,label;x\n1,road;y\n");
                    } else {
                      Files.writeString(
                          file, "id;label\n" + "1;rail\n".repeat(30) + "2;road;extra\n");
                    }
                    db.execute("CREATE TABLE earlier(id INTEGER)");
                    db.connection.setAutoCommit(false);
                    db.execute("INSERT INTO earlier VALUES(7)");
                    assertFalse(execute(console, "IMPORTCSVH data ENCODING UTF-8 DELIMITER AUTO"));
                    assertTrue(
                        console.message.contains(
                            failure.equals("bytes")
                                ? "UTF-8"
                                : failure.equals("ambiguous") ? "Ambiguous" : "record 32"),
                        console.message);
                    assertOriginal(db);
                    assertFalse(db.connection.getAutoCommit());
                    db.connection.rollback();
                    try (Statement s = db.connection.createStatement();
                        ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM earlier")) {
                      assertTrue(rows.next());
                      assertEquals(0, rows.getInt(1));
                    }
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> unrepresentableExportsPreserveExistingFilesAndCleanTemporaryFiles() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean existing : new boolean[] {false, true}) {
      for (boolean header : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                existing + "/" + header,
                () -> {
                  Path folder = Files.createTempDirectory(directory, "export-");
                  try (DatabaseFixture db = new DatabaseFixture(folder);
                      Console console = new Console(db.project)) {
                    db.execute("CREATE TABLE data(id INTEGER,label VARCHAR(32))");
                    db.execute("INSERT INTO data VALUES(1,'Tokyo 東京')");
                    Path file = folder.resolve("data.csv");
                    if (existing) {
                      Files.write(file, ORIGINAL);
                    }
                    assertFalse(
                        execute(
                            console,
                            "EXPORTCSV"
                                + (header ? "H" : "")
                                + " data ENCODING windows-1252 DELIMITER ';'"));
                    assertTrue(console.message.contains("windows-1252"), console.message);
                    if (existing) {
                      assertArrayEquals(ORIGINAL, Files.readAllBytes(file));
                    } else {
                      assertFalse(Files.exists(file));
                    }
                    try (var files = Files.list(folder)) {
                      assertEquals(
                          existing ? List.of(file) : List.of(),
                          files.collect(java.util.stream.Collectors.toList()));
                    }
                  }
                }));
      }
    }
    return tests;
  }

  @Test
  void oneScriptCanReadDifferentEncodingsAndReuseTheDelimiterAsAVariable() throws Exception {
    try (DatabaseFixture db = new DatabaseFixture(directory);
        Console console = new Console(db.project)) {
      Files.writeString(
          directory.resolve("utf.csv"), "label;id\nété 東京;1\n", StandardCharsets.UTF_8);
      Files.writeString(
          directory.resolve("latin.csv"),
          "label|id\nété; café|2\n",
          Charset.forName("windows-1252"));
      assertTrue(
          console.runBatch(
              new String[] {
                "@@sep:=';';",
                "IMPORTCSVH utf DELIMITER @@sep ENCODING UTF-8",
                "IMPORTCSVH latin ENCODING windows-1252 DELIMITER '|'"
              }),
          console.message);
      try (Statement s = db.connection.createStatement();
          ResultSet rows =
              s.executeQuery(
                  "SELECT id,label FROM utf UNION ALL SELECT id,label FROM latin ORDER BY id")) {
        assertTrue(rows.next());
        assertEquals("été 東京", rows.getString(2));
        assertTrue(rows.next());
        assertEquals("été; café", rows.getString(2));
        assertFalse(rows.next());
      }
    }
  }

  private static boolean execute(Console console, String command) throws Exception {
    PrintStream previous = System.err;
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(errors)) {
      System.setErr(capture);
      boolean result = console.runBatch(new String[] {command});
      assertFalse(
          errors.toString(StandardCharsets.UTF_8).contains("\tat "),
          errors.toString(StandardCharsets.UTF_8));
      return result;
    } finally {
      System.setErr(previous);
    }
  }

  private static void assertOriginal(DatabaseFixture db) throws Exception {
    try (Statement s = db.connection.createStatement();
        ResultSet rows = s.executeQuery("SELECT id,label FROM data")) {
      assertTrue(rows.next());
      assertEquals(99, rows.getInt(1));
      assertEquals("original", rows.getString(2));
      assertFalse(rows.next());
    }
  }

  private static final class Console extends SQLConsole implements AutoCloseable {
    private String message;
    private int prompts;

    Console(NodusProject project) {
      super(project, false);
    }

    @Override
    void displayMessageInResult(String head, String text) {
      message = text;
      super.displayMessageInResult(head, text);
    }

    @Override
    protected int showOverwriteDialog(String text) {
      prompts++;
      return JOptionPane.YES_OPTION;
    }

    @Override
    public void close() {
      windowClosing(null);
    }
  }
}
