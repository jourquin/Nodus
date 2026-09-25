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

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import edu.uclouvain.core.nodus.database.xls.ExportXLS;
import edu.uclouvain.core.nodus.database.xls.ImportXLS;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Reads exported DBF/Excel files independently, then imports them back into a real database. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class TabularFileIntegrationTest {
  @TempDir Path directory;

  @Test
  void dbfRoundTripPreservesRowsDatesAndDecimalScale() throws Exception {
    try (DatabaseFixture database = new DatabaseFixture(directory)) {
      database.execute(
          "CREATE TABLE data (id INTEGER, label VARCHAR(80), "
              + "amount DECIMAL(12,4), shipdate DATE)");
      for (int i = 1; i <= 5; i++) {
        database.execute(
            "INSERT INTO data VALUES (" + i + ", 'Bruxelles', -12345.6789, DATE '2026-09-25')");
      }
      assertTrue(ExportDBF.exportTable(database.project, "data"));
      try (DBFReader reader = new DBFReader(directory.resolve("data.dbf").toString())) {
        assertEquals(4, reader.getFieldCount());
        assertEquals('N', reader.getField(2).getType());
        assertEquals(4, reader.getField(2).getDecimalCount());
        int count = 0;
        while (reader.hasNextRecord()) {
          Object[] row = reader.nextRecord();
          assertEquals(++count, ((Number) row[0]).intValue());
          assertEquals("Bruxelles", row[1]);
          assertEquals(0, new BigDecimal("-12345.6789").compareTo((BigDecimal) row[2]));
        }
        assertEquals(5, count);
      }
      database.execute("DROP TABLE data");
      assertTrue(ImportDBF.importTable(database.project, "data"));
      try (Statement statement = database.connection.createStatement();
          ResultSet result = statement.executeQuery("SELECT * FROM data ORDER BY id")) {
        for (int i = 1; i <= 5; i++) {
          assertTrue(result.next());
          assertEquals(i, result.getInt("id"));
          assertEquals("Bruxelles", result.getString("label"));
          assertEquals(new BigDecimal("-12345.6789"), result.getBigDecimal("amount"));
          assertEquals(java.sql.Date.valueOf("2026-09-25"), result.getDate("shipdate"));
        }
        assertFalse(result.next());
      }
      assertTrue(database.connection.getAutoCommit());
      assertFalse(database.connection.isClosed());
    }
  }

  @TestFactory
  List<DynamicTest> excelFiles() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean xlsx : new boolean[] {false, true}) {
      String extension = xlsx ? ".xlsx" : ".xls";
      tests.add(
          dynamicTest(
              extension + " text, numbers and schema survive a round trip",
              () -> {
                try (DatabaseFixture database = new DatabaseFixture(directory)) {
                  database.execute(
                      "CREATE TABLE data (id INTEGER, label VARCHAR(200), "
                          + "amount DECIMAL(12,4))");
                  try (PreparedStatement insert =
                      database.connection.prepareStatement("INSERT INTO data VALUES (?, ?, ?)")) {
                    for (int i = 1; i <= 5; i++) {
                      insert.setInt(1, i);
                      insert.setString(2, "row " + i + ", \"été\"\n東京");
                      insert.setBigDecimal(3, new BigDecimal("-12345.6789"));
                      insert.executeUpdate();
                    }
                  }
                  assertTrue(ExportXLS.exportTable(database.project, "data", xlsx));
                  try (InputStream input =
                          Files.newInputStream(directory.resolve("data" + extension));
                      Workbook workbook = WorkbookFactory.create(input)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    assertEquals(5, sheet.getLastRowNum());
                    assertEquals("LABEL,C,200", sheet.getRow(0).getCell(1).getStringCellValue());
                    for (int i = 1; i <= 5; i++) {
                      assertEquals(i, sheet.getRow(i).getCell(0).getNumericCellValue());
                      assertEquals(
                          "row " + i + ", \"été\"\n東京",
                          sheet.getRow(i).getCell(1).getStringCellValue());
                      assertEquals(
                          -12345.6789, sheet.getRow(i).getCell(2).getNumericCellValue(), 1e-9);
                    }
                  }
                  database.execute("DROP TABLE data");
                  assertTrue(ImportXLS.importTable(database.project, "data", xlsx));
                  try (Statement statement = database.connection.createStatement();
                      ResultSet result = statement.executeQuery("SELECT * FROM data ORDER BY id")) {
                    for (int i = 1; i <= 5; i++) {
                      assertTrue(result.next());
                      assertEquals(i, result.getInt("id"));
                      assertEquals("row " + i + ", \"été\"\n東京", result.getString("label"));
                      assertEquals(new BigDecimal("-12345.6789"), result.getBigDecimal("amount"));
                    }
                    assertFalse(result.next());
                  }
                  assertTrue(database.connection.getAutoCommit());
                  assertFalse(database.connection.isClosed());
                }
              }));
      tests.add(
          dynamicTest(
              extension + " null numeric values export as blank cells",
              () -> {
                try (DatabaseFixture database = new DatabaseFixture(directory)) {
                  database.execute("CREATE TABLE data (id INTEGER, amount DECIMAL(12,4))");
                  database.execute("INSERT INTO data VALUES (1, NULL), (2, 0)");
                  assertTrue(ExportXLS.exportTable(database.project, "data", xlsx));
                  try (InputStream input =
                          Files.newInputStream(directory.resolve("data" + extension));
                      Workbook workbook = WorkbookFactory.create(input)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    assertEquals(CellType.BLANK, sheet.getRow(1).getCell(1).getCellType());
                    assertEquals(CellType.NUMERIC, sheet.getRow(2).getCell(1).getCellType());
                    assertEquals(0, sheet.getRow(2).getCell(1).getNumericCellValue());
                  }
                  assertFalse(database.connection.isClosed());
                }
              }));
    }
    return tests;
  }
}
