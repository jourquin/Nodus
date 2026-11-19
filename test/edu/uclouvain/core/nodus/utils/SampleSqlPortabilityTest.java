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

package edu.uclouvain.core.nodus.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.parquet.ParquetFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Executes the shipped SQL with both embedded engines and checks parity with the Parquet sample.
 */
class SampleSqlPortabilityTest {
  @TestFactory
  Stream<DynamicTest> portableSqlMatchesAllSampleValues() {
    return Stream.of("h2", "hsqldb")
        .map(engine -> DynamicTest.dynamicTest(engine, () -> check(engine)));
  }

  private void check(String engine) throws Exception {
    Path directory = Path.of("scripts", "master-od-sample");
    String sql = Files.readString(directory.resolve("sample_rows.sql"));
    String url = "jdbc:" + engine + ":mem:portable_sample_" + UUID.randomUUID();
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      // This controlled fixture contains no quoted strings or comments with semicolons.
      int statements = 0;
      for (String command : sql.split(";")) {
        if (!command.isBlank()) {
          statement.execute(command);
          statements++;
        }
      }
      assertEquals(9, statements);
      try (ResultSet values =
              statement.executeQuery("SELECT * FROM sample ORDER BY GRP, ORG, DST");
          ParquetFileReader reader =
              ParquetFiles.openReader(directory.resolve("sample_road.parquet"))) {
        MessageType schema = reader.getFileMetaData().getSchema();
        MessageColumnIO io = new ColumnIOFactory().getColumnIO(schema);
        PageReadStore pages;
        int rowCount = 0;
        while ((pages = reader.readNextRowGroup()) != null) {
          try (PageReadStore block = pages) {
            RecordReader<Group> records =
                io.getRecordReader(block, new GroupRecordConverter(schema));
            for (long row = 0; row < block.getRowCount(); row++) {
              assertTrue(values.next());
              Group expected = records.read();
              for (int column = 0; column < schema.getFieldCount(); column++) {
                compareValue(values, expected, schema, column);
              }
              rowCount++;
            }
          }
        }
        assertEquals(8, rowCount);
        assertEquals(11, values.getMetaData().getColumnCount());
        assertFalse(values.next());
      }
      try (ResultSet totals =
          statement.executeQuery(
              "SELECT SUM(QTY_AVAILABLE_PART), SUM(UNESTIMATED_CELLS), "
                  + "COUNT(*)-COUNT(QTY_AVAILABLE_PART) FROM sample")) {
        assertTrue(totals.next());
        assertEquals(26.0, totals.getDouble(1));
        assertEquals(3, totals.getInt(2));
        assertEquals(1, totals.getInt(3));
      }
    }
  }

  private void compareValue(ResultSet actual, Group expected, MessageType schema, int column)
      throws Exception {
    String name = schema.getFieldName(column);
    if (expected.getFieldRepetitionCount(column) == 0) {
      assertNull(actual.getObject(name), name);
      return;
    }
    PrimitiveTypeName type = schema.getType(column).asPrimitiveType().getPrimitiveTypeName();
    switch (type) {
      case INT32:
        assertEquals(expected.getInteger(column, 0), actual.getInt(name), name);
        break;
      case INT64:
        assertEquals(expected.getLong(column, 0), actual.getLong(name), name);
        break;
      case DOUBLE:
        assertEquals(expected.getDouble(column, 0), actual.getDouble(name), name);
        break;
      case BOOLEAN:
        assertEquals(expected.getBoolean(column, 0), actual.getBoolean(name), name);
        break;
      default:
        throw new AssertionError("Unexpected sample field type: " + type);
    }
    assertFalse(actual.wasNull(), name);
  }
}
