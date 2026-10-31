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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.parquet.ExportParquet;
import edu.uclouvain.core.nodus.database.parquet.LocalParquetReader;
import edu.uclouvain.core.nodus.database.sql.NodusSQLTokenMaker;
import edu.uclouvain.core.nodus.database.xls.ExportXLS;
import edu.uclouvain.core.nodus.database.xls.ImportXLS;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.swing.text.Segment;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("JDBCUtils")
class NewTableFormatsTest {
  @TempDir Path directory;

  @Test
  void parquetPreservesTypesNullsAndLargeIntegers() throws Exception {
    try (DatabaseFixture db = new DatabaseFixture(directory)) {
      db.execute(
          (("CREATE TABLE data (id BIGINT, amount DECIMAL(20,4), label VA"
              + ""
              + "RCHAR(30), d DATE, flag BOOLEAN)")));
      db.execute(
          (("INSERT INTO data VALUES (9007199254740993, 1234567890123456."
              + ""
              + "7890, 'été', DATE '2026-10-30', TRUE)")));
      db.execute("INSERT INTO data VALUES (NULL,NULL,NULL,NULL,NULL)");
      assertTrue(ExportParquet.exportTable(db.project, "data"));
      try (ParquetReader<Group> reader =
          new LocalParquetReader(directory.resolve("data.parquet")).build()) {
        Group row = reader.read();
        assertEquals(9007199254740993L, row.getLong(0, 0));
        assertEquals(
            new java.math.BigInteger("12345678901234567890"),
            new java.math.BigInteger(row.getBinary(1, 0).getBytes()));
        assertEquals("été", row.getString(2, 0));
        assertEquals(java.time.LocalDate.of(2026, 10, 30).toEpochDay(), row.getInteger(3, 0));
        assertTrue(row.getBoolean(4, 0));
        row = reader.read();
        for (int i = 0; i < 5; i++) {
          assertEquals(0, row.getFieldRepetitionCount(i));
        }
        assertNull(reader.read());
      }
      assertFalse(db.connection.isClosed());
    }
  }

  @Test
  void unsupportedParquetTypeLeavesExistingFileIntact() throws Exception {
    try (DatabaseFixture db = new DatabaseFixture(directory)) {
      db.execute("CREATE TABLE data (items INTEGER ARRAY)");
      Path output = directory.resolve("data.parquet");
      Files.writeString(output, "original");
      assertFalse(ExportParquet.exportTable(db.project, "data"));
      assertEquals("original", Files.readString(output));
    }
  }

  @Test
  void odsRoundTripAndMalformedImportPreserveDestination() throws Exception {
    for (boolean hsql : new boolean[] {false, true}) {
      try (DatabaseFixture db = new DatabaseFixture(directory, hsql)) {
        db.execute("CREATE TABLE data (id INTEGER, label VARCHAR(80))");
        db.execute("INSERT INTO data VALUES (42, '  été & <rail>  ')");
        assertTrue(ExportXLS.exportODS(db.project, "data"));
        db.execute("DROP TABLE data");
        assertTrue(ImportXLS.importODS(db.project, "data"));
        try (Statement statement = db.connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT * FROM data")) {
          assertTrue(rows.next());
          assertEquals(42, rows.getInt(1));
          assertEquals("  été & <rail>  ", rows.getString(2));
          assertFalse(rows.next());
        }
        Files.writeString(directory.resolve("data.ods"), "bad zip");
        assertFalse(ImportXLS.importODS(db.project, "data"));
        try (Statement statement = db.connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM data")) {
          assertTrue(rows.next());
          assertEquals(1, rows.getInt(1));
        }
      }
    }
  }

  @Test
  void lexerRecognizesNewCommandsIgnoringCase() {
    for (String command :
        new String[] {"exportparquet", "IMPORTPARQUET", "EXPORTODS", "ImportODS"}) {
      char[] text = (command + " data").toCharArray();
      Token token =
          new NodusSQLTokenMaker().getTokenList(new Segment(text, 0, text.length), Token.NULL, 0);
      assertEquals(Token.RESERVED_WORD, token.getType());
      assertEquals(command, token.getLexeme());
    }
  }
}
