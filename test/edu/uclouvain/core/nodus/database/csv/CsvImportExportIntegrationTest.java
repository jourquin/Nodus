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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Verifies CSV contents and transaction boundaries against real files and an H2 database. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class CsvImportExportIntegrationTest {
  @TempDir Path directory;
  private DatabaseFixture database;
  private final List<String> errors = new ArrayList<>();

  @BeforeEach
  void openDatabase() throws Exception {
    database = new DatabaseFixture(directory);
    database.execute("CREATE TABLE data (id INTEGER, label VARCHAR(200), amount DECIMAL(25,6))");
    database.execute("INSERT INTO data VALUES (99, 'original', 7)");
  }

  @AfterEach
  void closeDatabase() throws Exception {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void exportEscapesHeadersTextAndLineBreaksAndKeepsExactDecimals() throws Exception {
    database.execute(
        "CREATE TABLE exports (\"label,quoted\" VARCHAR(200), "
            + "empty_value VARCHAR(200), amount DECIMAL(25,6))");
    try (PreparedStatement insert =
        database.connection.prepareStatement("INSERT INTO exports VALUES (?, ?, ?)")) {
      insert.setString(1, "Bruxelles, \"été\"\r\n東京");
      insert.setString(2, "");
      insert.setBigDecimal(3, new BigDecimal("1234567890123456789.123456"));
      insert.executeUpdate();
    }
    assertTrue(ExportCSV.exportTable(database.project, "exports", true));
    String newline = System.lineSeparator();
    assertEquals(
        "\"label,quoted\",EMPTY_VALUE,AMOUNT"
            + newline
            + "\"Bruxelles, \"\"été\"\"\r\n東京\",,1234567890123456789.123456"
            + newline,
        Files.readString(directory.resolve("exports.csv"), Charset.defaultCharset()));
    assertFalse(database.connection.isClosed());
  }

  @Test
  void nullsAndEmptyStringsHaveTheDocumentedEmptyFieldRepresentation() throws Exception {
    database.execute("DELETE FROM data");
    database.execute("INSERT INTO data VALUES (1, NULL, NULL), (2, '', 0)");
    assertTrue(ExportCSV.exportTable(database.project, "data", false));
    assertEquals(
        List.of("1,,", "2,,0.000000"),
        Files.readAllLines(directory.resolve("data.csv"), Charset.defaultCharset()));
  }

  @Test
  void importParsesQuotedFieldsUnicodeAndMultilineValuesAcrossBatches() throws Exception {
    writeCsv(
        "ID,LABEL,AMOUNT\r\n"
            + "1,\"Bruxelles, \"\"été\"\"\",1234567890123456789.123456\r\n"
            + "2,\"first\r\nsecond\",-12.340000\r\n"
            + "3,東京,0\r\n4,\" padded \",1.25\r\n5,,2.5\r\n");
    assertTrue(importCsv(true), errors.toString());
    assertEquals(
        List.of(
            List.of("1", "Bruxelles, \"été\"", "1234567890123456789.123456"),
            List.of("2", "first\r\nsecond", "-12.340000"),
            List.of("3", "東京", "0.000000"),
            List.of("4", " padded ", "1.250000"),
            List.of("5", "", "2.500000")),
        rows());
    assertTrue(errors.isEmpty());
    assertTrue(database.connection.getAutoCommit());
    assertFalse(database.connection.isClosed());
  }

  @Test
  void roundTripsWithAndWithoutHeadersPreserveTextAndDecimals() throws Exception {
    database.execute("DELETE FROM data");
    try (PreparedStatement insert =
        database.connection.prepareStatement("INSERT INTO data VALUES (?, ?, ?)")) {
      for (int i = 1; i <= 5; i++) {
        insert.setInt(1, i);
        insert.setString(2, i == 5 ? "" : "row " + i + ", \"été\"\n東京");
        insert.setBigDecimal(3, new BigDecimal("-1234567890123456789.123456"));
        insert.executeUpdate();
      }
    }
    List<List<String>> expected = rows();
    for (boolean header : new boolean[] {false, true}) {
      assertTrue(ExportCSV.exportTable(database.project, "data", header));
      database.execute("DELETE FROM data");
      assertTrue(importCsv(header), errors.toString());
      assertEquals(expected, rows());
    }
    assertTrue(errors.isEmpty());
  }

  @Test
  void emptyAndHeaderOnlyFilesReplaceTheTableWithNoRows() throws Exception {
    writeCsv("");
    assertTrue(importCsv(false), errors.toString());
    assertTrue(rows().isEmpty());
    database.execute("INSERT INTO data VALUES (99, 'original', 7)");
    writeCsv("ID,LABEL,AMOUNT\r\n");
    assertTrue(importCsv(true), errors.toString());
    assertTrue(rows().isEmpty());
    assertTrue(errors.isEmpty());
  }

  @Test
  void missingFileLeavesRowsAndTransactionModeUnchanged() throws Exception {
    List<List<String>> before = rows();
    assertFalse(importCsv(false));
    assertEquals(before, rows());
    assertTrue(database.connection.getAutoCommit());
    assertTrue(errors.isEmpty());
  }

  @Test
  void invalidNumericValueRollsBackAlreadyFlushedRowsAndRestoresAutoCommit() throws Exception {
    final List<List<String>> before = rows();
    writeCsv("1,first,1\n2,second,2\n3,bad,not-a-number\n");
    assertFalse(importCsv(false));
    assertEquals(1, errors.size());
    assertEquals(before, rows());
    assertTrue(database.connection.getAutoCommit());
    assertFalse(database.connection.isClosed());
  }

  @Test
  void failedImportPreservesEarlierUncommittedWorkOnTheSharedConnection() throws Exception {
    database.connection.setAutoCommit(false);
    database.execute("INSERT INTO data VALUES (100, 'pending', 8)");
    final List<List<String>> before = rows();
    writeCsv("1,first,1\n2,second,2\n3,bad,not-a-number\n");
    assertFalse(importCsv(false));
    assertEquals(1, errors.size());
    assertEquals(before, rows());
    assertFalse(database.connection.getAutoCommit());
    database.connection.rollback();
    assertEquals(List.of(List.of("99", "original", "7.000000")), rows());
  }

  @Test
  void successfulImportDoesNotCommitTheCallersTransaction() throws Exception {
    database.connection.setAutoCommit(false);
    database.execute("INSERT INTO data VALUES (100, 'pending', 8)");
    writeCsv("1,new,1\n");
    assertTrue(importCsv(false), errors.toString());
    assertEquals(List.of(List.of("1", "new", "1.000000")), rows());
    assertFalse(database.connection.getAutoCommit());
    database.connection.rollback();
    assertEquals(List.of(List.of("99", "original", "7.000000")), rows());
    assertTrue(errors.isEmpty());
  }

  @Test
  void shortRecordsRollBackTheWholeImport() throws Exception {
    assertInvalidRecord("3,missing amount\n");
  }

  @Test
  void extraFieldsAreRejectedInsteadOfSilentlyDiscarded() throws Exception {
    assertInvalidRecord("3,third,3,unexpected extra field\n");
  }

  @Test
  void unterminatedQuotedFieldRollsBackTheWholeImport() throws Exception {
    assertInvalidRecord("3,\"unterminated,3\n");
  }

  private void assertInvalidRecord(String invalidRow) throws Exception {
    final List<List<String>> before = rows();
    writeCsv("1,first,1\n2,second,2\n" + invalidRow);
    assertFalse(importCsv(false));
    assertEquals(1, errors.size());
    assertEquals(before, rows());
    assertTrue(database.connection.getAutoCommit());
  }

  private boolean importCsv(boolean header) {
    return ImportCSV.importTable(database.project, "data", header, errors::add);
  }

  private void writeCsv(String content) throws Exception {
    Files.writeString(directory.resolve("data.csv"), content, Charset.defaultCharset());
  }

  private List<List<String>> rows() throws Exception {
    List<List<String>> rows = new ArrayList<>();
    try (Statement statement = database.connection.createStatement();
        ResultSet result =
            statement.executeQuery("SELECT id, label, amount FROM data ORDER BY id")) {
      while (result.next()) {
        rows.add(List.of(result.getString(1), result.getString(2), result.getString(3)));
      }
    }
    return rows;
  }
}
