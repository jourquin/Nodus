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

package edu.uclouvain.core.nodus.database.parquet;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import java.nio.file.Files;
import java.nio.file.Path;

/** Child JVM entry point that must work with only the installed application libraries. */
public class ParquetRuntimeProbe {
  /**
   * Exercises Parquet interchange with only installed application libraries available.
   *
   * @param args the test directory containing external Parquet fixtures
   * @throws Exception if a runtime operation fails
   */
  public static void main(String[] args) throws Exception {
    try {
      Class.forName("org.apache.hadoop.conf.Configuration");
      throw new AssertionError("Hadoop on classpath");
    } catch (ClassNotFoundException expected) {
      System.out.println("Confirmed: no Hadoop or floor on runtime classpath");
    }
    try {
      Class.forName("blue.strategic.parquet.ParquetWriter");
      throw new AssertionError("parquet-floor on classpath");
    } catch (ClassNotFoundException expected) {
    }
    Path dir = Path.of(args[0]);
    for (boolean hsql : new boolean[] {false, true}) {
      try (DatabaseFixture db = new DatabaseFixture(dir, hsql)) {
        db.execute(
            ("CREATE TABLE data (id BIGINT, amount DECIMAL(20,4), label VA"
                + "RCHAR(30), d DATE, flag BOOLEAN, bytes VARBINARY(20), t TIME"
                + "(6), ts TIMESTAMP(6), f REAL, x DOUBLE)"));
        db.execute(
            ("INSERT INTO data VALUES (9007199254740993, 1234567890123456."
                + "7890, 'été', DATE '2026-10-30', TRUE, X'00FF10', TIME '12:34"
                + ":56.123456', TIMESTAMP '1969-12-31 23:59:59.123456', 1.25, -"
                + "3.5)"));
        db.execute("INSERT INTO data VALUES (NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)");
        if (!ExportParquet.exportTable(db.project, "data")) {
          throw new AssertionError("Export failed");
        }
        db.execute("CREATE TABLE emptydata (id BIGINT)");
        if (!ExportParquet.exportTable(db.project, "emptydata")) {
          throw new AssertionError("Empty export failed");
        }
        db.execute("CREATE TABLE bad (items INTEGER ARRAY)");
        Files.writeString(dir.resolve("bad.parquet"), "original");
        if (ExportParquet.exportTable(db.project, "bad")) {
          throw new AssertionError("Unsupported type accepted");
        }
        if (!Files.readString(dir.resolve("bad.parquet")).equals("original")) {
          throw new AssertionError("Lost existing file");
        }

        db.execute("DROP TABLE data");
        if (!ImportParquet.importTable(db.project, "data")) {
          throw new AssertionError("Import failed");
        }
        try (java.sql.Statement statement = db.connection.createStatement();
            java.sql.ResultSet rows =
                statement.executeQuery("SELECT * FROM data WHERE id IS NOT NULL")) {
          if (!rows.next()
              || rows.getLong(1) != 9007199254740993L
              || !rows.getBigDecimal(2).equals(new java.math.BigDecimal("1234567890123456.7890"))
              || !rows.getString(3).equals("été")
              || !rows.getObject(7, java.time.LocalTime.class)
                  .equals(java.time.LocalTime.parse("12:34:56.123456"))
              || !rows.getObject(8, java.time.LocalDateTime.class)
                  .equals(java.time.LocalDateTime.parse("1969-12-31T23:59:59.123456"))) {
            throw new AssertionError("Imported values differ");
          }
        }
        if (!ImportParquet.importTable(db.project, "emptydata")) {
          throw new AssertionError("Empty import failed");
        }
        try (java.sql.Statement statement = db.connection.createStatement();
            java.sql.ResultSet rows =
                statement.executeQuery("SELECT * FROM data WHERE id IS NULL")) {
          if (!rows.next()) {
            throw new AssertionError("Missing null row");
          }
          for (int c = 1; c <= 10; c++) {
            if (rows.getObject(c) != null) {
              throw new AssertionError("Null changed");
            }
          }
        }
        for (String table : new String[] {"snappy", "uncompressed"}) {
          if (!ImportParquet.importTable(db.project, table)) {
            throw new AssertionError("External import failed: " + table);
          }
          try (java.sql.Statement statement = db.connection.createStatement();
              java.sql.ResultSet rows = statement.executeQuery("SELECT * FROM " + table)) {
            if (!rows.next()
                || !rows.getBigDecimal(1).equals(new java.math.BigDecimal("123.45"))
                || !rows.getObject(2, java.time.OffsetDateTime.class)
                    .toInstant()
                    .equals(java.time.Instant.ofEpochSecond(-1, 999999999))
                || rows.getLong(3) != 4294967295L
                || !rows.getString(4).equals("external")) {
              throw new AssertionError("External values differ: " + table);
            }
          }
        }
        // Keep a corrupt copy to test replacement failure without damaging the valid reader
        // fixture.
        Files.copy(
            dir.resolve("data.parquet"),
            dir.resolve("broken.parquet"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        db.execute("CREATE TABLE broken (marker INTEGER)");
        db.execute("INSERT INTO broken VALUES (123)");
        try (java.nio.channels.SeekableByteChannel channel =
            Files.newByteChannel(
                dir.resolve("broken.parquet"), java.nio.file.StandardOpenOption.WRITE)) {
          channel.truncate(20);
        }
        if (ImportParquet.importTable(db.project, "broken")) {
          throw new AssertionError("Corrupt input accepted");
        }
        try (java.sql.Statement statement = db.connection.createStatement();
            java.sql.ResultSet rows = statement.executeQuery("SELECT marker FROM broken")) {
          if (!rows.next() || rows.getInt(1) != 123) {
            throw new AssertionError("Lost existing table");
          }
        }
        System.out.println("Wrote typed and empty tables; verified failure preserves original");
      }
    }
  }
}
