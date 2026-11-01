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

import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("JDBCUtils")
class DbfNumericImportTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> exactNumbersAndDeletedRecords() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean deleted : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                hsql + "/deleted=" + deleted,
                () -> {
                  Path source = directory.resolve("numbers.dbf");
                  DBFField[] fields = {
                    new DBFField("big", 'N', 20, 0), new DBFField("amount", 'N', 20, 6)
                  };
                  try (DBFWriter writer =
                      new DBFWriter(source.toString(), fields, StandardCharsets.UTF_8)) {
                    writer.addRecord(new Object[] {0, 0});
                    writer.addRecord(new Object[] {0, 0});
                  }
                  byte[] bytes = Files.readAllBytes(source);
                  int header = (bytes[8] & 255) | ((bytes[9] & 255) << 8);
                  int width = (bytes[10] & 255) | ((bytes[11] & 255) << 8);
                  String[] integers = {"9223372036854775808", "-3000000000"};
                  String[] decimals = {"123456789012.123456", "-12345678901.654321"};
                  for (int row = 0; row < 2; row++) {
                    int offset = header + row * width;
                    bytes[offset] = row == 1 && deleted ? (byte) '*' : (byte) ' ';
                    System.arraycopy(
                        String.format(
                                java.util.Locale.ROOT, "%20s%20s", integers[row], decimals[row])
                            .getBytes(StandardCharsets.US_ASCII),
                        0,
                        bytes,
                        offset + 1,
                        40);
                  }
                  Files.write(source, bytes);
                  try (DatabaseFixture db = new DatabaseFixture(directory, hsql)) {
                    List<Exception> errors = new ArrayList<>();
                    ImportProgress progress = new ImportProgress();
                    assertTrue(
                        ImportDBF.importTable(db.project, "numbers", errors::add, progress),
                        errors.toString());
                    assertEquals(deleted ? 1 : 2, progress.getRecords());
                    try (Statement s = db.connection.createStatement();
                        ResultSet rows =
                            s.executeQuery("SELECT * FROM numbers ORDER BY big DESC")) {
                      for (int row = 0; row < (deleted ? 1 : 2); row++) {
                        assertTrue(rows.next());
                        assertEquals(
                            0, new BigDecimal(integers[row]).compareTo(rows.getBigDecimal(1)));
                        assertEquals(
                            0, new BigDecimal(decimals[row]).compareTo(rows.getBigDecimal(2)));
                      }
                      assertFalse(rows.next());
                    }
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> extremeExponentsAndFractionalIntegersFailWithoutRounding() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (String value : List.of("1E999999999", "1E-999999999", "1.5")) {
        tests.add(
            dynamicTest(
                hsql + "/" + value,
                () -> {
                  Path source = directory.resolve("data.dbf");
                  DBFField[] fields = {new DBFField("id", 'N', 20, 0)};
                  try (DBFWriter writer =
                      new DBFWriter(source.toString(), fields, StandardCharsets.UTF_8)) {
                    writer.addRecord(new Object[] {0});
                  }
                  byte[] bytes = Files.readAllBytes(source);
                  int header = (bytes[8] & 255) | ((bytes[9] & 255) << 8);
                  byte[] number =
                      String.format(java.util.Locale.ROOT, "%20s", value)
                          .getBytes(StandardCharsets.US_ASCII);
                  System.arraycopy(number, 0, bytes, header + 1, 20);
                  Files.write(source, bytes);
                  try (DatabaseFixture db = new DatabaseFixture(directory, hsql)) {
                    db.execute("CREATE TABLE data(id NUMERIC(20))");
                    db.execute("INSERT INTO data VALUES(99)");
                    List<Exception> errors = new ArrayList<>();
                    assertFalse(ImportDBF.importTable(db.project, "data", errors::add));
                    assertTrue(
                        errors.get(0).getMessage().contains("DBF record 1"), errors.toString());
                    try (Statement s = db.connection.createStatement();
                        ResultSet rows = s.executeQuery("SELECT id FROM data")) {
                      assertTrue(rows.next());
                      assertEquals(99, rows.getInt(1));
                      assertFalse(rows.next());
                    }
                  }
                }));
      }
    }
    return tests;
  }
}
