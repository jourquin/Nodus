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

import static org.junit.jupiter.api.Assertions.*;

import edu.uclouvain.core.nodus.Nodus;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves the installed runtime works without the Hadoop classes used for compilation/tests. */
class ParquetRuntimeTest {
  @TempDir Path directory;

  @Test
  void exportsWithOnlyInstalledLibrariesAndReadsBackWithStandardReader() throws Exception {
    writeExternalFixtures();
    List<String> classpath = new ArrayList<>();
    classpath.add(
        Path.of(Nodus.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString());
    classpath.add(
        Path.of(DatabaseFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString());
    try (Stream<Path> jars = Files.walk(Path.of("lib"))) {
      jars.filter(p -> p.toString().endsWith(".jar"))
          .sorted()
          .forEach(p -> classpath.add(p.toAbsolutePath().toString()));
    }
    Path log = directory.resolve("runtime.log");
    Process child =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true",
                "-cp",
                String.join(File.pathSeparator, classpath),
                ParquetRuntimeProbe.class.getName(),
                directory.toString())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    try {
      assertTrue(child.waitFor(40, TimeUnit.SECONDS), "Runtime export timed out");
      assertEquals(0, child.exitValue(), Files.readString(log));
    } finally {
      if (child.isAlive()) child.destroyForcibly();
    }
    try (ParquetReader<Group> reader = reader("data")) {
      Group row = reader.read();
      assertNotNull(row);
      assertEquals(9007199254740993L, row.getLong(0, 0));
      assertEquals(
          new BigInteger("12345678901234567890"), new BigInteger(row.getBinary(1, 0).getBytes()));
      assertEquals("été", row.getString(2, 0));
      assertEquals(java.time.LocalDate.of(2026, 10, 30).toEpochDay(), row.getInteger(3, 0));
      assertTrue(row.getBoolean(4, 0));
      assertArrayEquals(new byte[] {0, (byte) 255, 16}, row.getBinary(5, 0).getBytes());
      assertEquals(45296123456L, row.getLong(6, 0));
      assertEquals(-876544L, row.getLong(7, 0));
      assertEquals(
          1.25,
          row.getType().getType(8).asPrimitiveType().getPrimitiveTypeName()
                  == org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.FLOAT
              ? row.getFloat(8, 0)
              : row.getDouble(8, 0));
      assertEquals(-3.5, row.getDouble(9, 0));
      row = reader.read();
      assertNotNull(row);
      for (int i = 0; i < 10; i++) assertEquals(0, row.getFieldRepetitionCount(i));
      assertNull(reader.read());
    }
    try (ParquetReader<Group> reader = reader("emptydata")) {
      assertNull(reader.read());
    }
    assertEquals("original", Files.readString(directory.resolve("bad.parquet")));
  }

  private void writeExternalFixtures() throws Exception {
    org.apache.parquet.schema.MessageType schema =
        new org.apache.parquet.schema.MessageType(
            "external",
            org.apache.parquet.schema.Types.optional(
                    org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64)
                .as(org.apache.parquet.schema.LogicalTypeAnnotation.decimalType(2, 12))
                .named("amount"),
            org.apache.parquet.schema.Types.optional(
                    org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64)
                .as(
                    org.apache.parquet.schema.LogicalTypeAnnotation.timestampType(
                        true, org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit.NANOS))
                .named("instant"),
            org.apache.parquet.schema.Types.optional(
                    org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32)
                .as(org.apache.parquet.schema.LogicalTypeAnnotation.intType(32, false))
                .named("unsigned"),
            org.apache.parquet.schema.Types.optional(
                    org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY)
                .as(org.apache.parquet.schema.LogicalTypeAnnotation.stringType())
                .named("label"));
    org.apache.parquet.compression.CompressionCodecFactory codecs =
        new org.apache.parquet.compression.CompressionCodecFactory() {
          public BytesInputCompressor getCompressor(
              org.apache.parquet.hadoop.metadata.CompressionCodecName codec) {
            return new BytesInputCompressor() {
              public org.apache.parquet.bytes.BytesInput compress(
                  org.apache.parquet.bytes.BytesInput input) throws java.io.IOException {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                if (codec == org.apache.parquet.hadoop.metadata.CompressionCodecName.SNAPPY) {
                  // Valid raw Snappy with a literal-only block; no native compression test
                  // dependency.
                  byte[] bytes = input.toByteArray();
                  int remaining = bytes.length;
                  while (remaining >= 128) {
                    output.write((remaining & 127) | 128);
                    remaining >>>= 7;
                  }
                  output.write(remaining);
                  if (bytes.length > 0) {
                    int length = bytes.length - 1;
                    if (length < 60) output.write(length << 2);
                    else {
                      int count = (32 - Integer.numberOfLeadingZeros(length) + 7) / 8;
                      output.write((59 + count) << 2);
                      for (int i = 0; i < count; i++) output.write(length >>> (8 * i));
                    }
                    output.write(bytes);
                  }
                } else input.writeAllTo(output);
                return org.apache.parquet.bytes.BytesInput.from(output.toByteArray());
              }

              public org.apache.parquet.hadoop.metadata.CompressionCodecName getCodecName() {
                return codec;
              }

              public void release() {}
            };
          }

          public BytesInputDecompressor getDecompressor(
              org.apache.parquet.hadoop.metadata.CompressionCodecName codec) {
            throw new UnsupportedOperationException();
          }

          public void release() {}
        };
    for (org.apache.parquet.hadoop.metadata.CompressionCodecName codec :
        new org.apache.parquet.hadoop.metadata.CompressionCodecName[] {
          org.apache.parquet.hadoop.metadata.CompressionCodecName.SNAPPY,
          org.apache.parquet.hadoop.metadata.CompressionCodecName.UNCOMPRESSED
        }) {
      try (org.apache.parquet.hadoop.ParquetWriter<Group> writer =
          org.apache.parquet.hadoop.example.ExampleParquetWriter.builder(
                  new org.apache.hadoop.fs.Path(
                      directory
                          .resolve(codec.name().toLowerCase(java.util.Locale.ROOT) + ".parquet")
                          .toUri()))
              .withType(schema)
              .withCompressionCodec(codec)
              .withCodecFactory(codecs)
              .withWriterVersion(
                  org.apache.parquet.column.ParquetProperties.WriterVersion.PARQUET_2_0)
              .build()) {
        writer.write(
            new org.apache.parquet.example.data.simple.SimpleGroupFactory(schema)
                .newGroup()
                .append("amount", 12345L)
                .append("instant", -1L)
                .append("unsigned", -1)
                .append("label", "external"));
      }
    }
  }

  private ParquetReader<Group> reader(String name) throws Exception {
    return ParquetReader.builder(
            new GroupReadSupport(),
            new org.apache.hadoop.fs.Path(directory.resolve(name + ".parquet").toUri()))
        .build();
  }
}
