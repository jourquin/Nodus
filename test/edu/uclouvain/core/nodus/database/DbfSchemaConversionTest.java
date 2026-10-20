/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DbfFiles;
import edu.uclouvain.core.nodus.testing.ProjectFilesFixture;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class DbfSchemaConversionTest implements ShapeConstants {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> wideningKeepsEveryPhysicalRowAttributeAndEncoding() {
    return Stream.of("UTF-8", "windows-1252")
        .flatMap(
            encoding ->
                Stream.of(false, true)
                    .map(
                        legacy ->
                            DynamicTest.dynamicTest(
                                encoding + ", legacy=" + legacy,
                                () -> {
                                  final Path folder =
                                      Files.createTempDirectory(directory, "preserved-");
                                  final Charset charset = Charset.forName(encoding);
                                  final String label =
                                      encoding.equals("UTF-8") ? "Liège 東京 €" : "Liège été €";
                                  final Object[] speeds = {65, 9999, -999, null};
                                  final DBFField[] fields =
                                      ProjectFilesFixture.fields(false, legacy);
                                  final int speed = legacy ? 7 : 8;
                                  fields[speed] = new DBFField("speed", 'N', 4, 0);
                                  ProjectFilesFixture.writeLayer(
                                      folder, "con_be", false, 7, 3, 9, 8);
                                  final List<Object[]> rows = new ArrayList<>();
                                  for (int i = 0; i < speeds.length; i++) {
                                    Object[] row =
                                        ProjectFilesFixture.row(false, legacy, i + 1, label + i);
                                    row[speed] = speeds[i];
                                    rows.add(row);
                                  }
                                  ProjectFilesFixture.writeDbf(
                                      folder, "con_be", fields, charset, rows);
                                  final Path dbf = folder.resolve("con_be.dbf");
                                  final Path cpg = folder.resolve("con_be.CPG");
                                  Files.move(folder.resolve("con_be.cpg"), cpg);
                                  markDeleted(dbf, 1);
                                  final byte[] original = Files.readAllBytes(dbf);
                                  final byte[] geometry =
                                      Files.readAllBytes(folder.resolve("con_be.shp"));
                                  final byte[] index =
                                      Files.readAllBytes(folder.resolve("con_be.shx"));
                                  final byte[] encodingBytes = Files.readAllBytes(cpg);
                                  final List<String> prompts = new ArrayList<>();
                                  assertTrue(
                                      validate(
                                          properties(folder, "", "con_be"),
                                          message -> {
                                            prompts.add(message);
                                            assertTrue(message.contains("con_be"));
                                            assertTrue(message.contains("speed: N(4,0) → N(7,2)"));
                                            assertTrue(message.contains("con_be.dbf.bak"));
                                            assertArrayEquals(original, bytes(dbf));
                                            assertFalse(
                                                Files.exists(folder.resolve("con_be.dbf.bak")));
                                            assertTrue(
                                                Files.isRegularFile(
                                                    folder.resolve(
                                                        ".con_be.nodus-save/con_be.dbf")));
                                            return true;
                                          }));
                                  assertEquals(1, prompts.size());
                                  assertTrue(
                                      ProjectFilesTools.isValidLayer(
                                          folder.toString(), "con_be", SHAPE_TYPE_POLYLINE));
                                  assertArrayEquals(
                                      original,
                                      Files.readAllBytes(folder.resolve("con_be.dbf.bak")));
                                  assertArrayEquals(
                                      geometry, Files.readAllBytes(folder.resolve("con_be.shp")));
                                  assertArrayEquals(
                                      index, Files.readAllBytes(folder.resolve("con_be.shx")));
                                  assertArrayEquals(encodingBytes, Files.readAllBytes(cpg));
                                  final DbfTableModel model = DbfFiles.readModel(dbf, null).model;
                                  assertEquals(4, model.getRowCount());
                                  for (int row = 0; row < speeds.length; row++) {
                                    assertEquals(
                                        row + 1, ((Number) model.getValueAt(row, 0)).intValue());
                                    assertEquals(label + row, model.getValueAt(row, 9));
                                    assertEquals(
                                        speeds[row] == null
                                            ? null
                                            : ((Number) speeds[row]).doubleValue(),
                                        model.getValueAt(row, 8));
                                  }
                                  assertEquals('*', deletionFlag(dbf, 1));
                                  assertEquals(' ', deletionFlag(dbf, 0));
                                  assertFalse(Files.exists(folder.resolve(".con_be.nodus-save")));
                                  final byte[] converted = Files.readAllBytes(dbf);
                                  assertTrue(
                                      validate(
                                          properties(folder, "", "con_be"),
                                          message -> fail("Unexpected second prompt")));
                                  assertArrayEquals(converted, Files.readAllBytes(dbf));
                                  assertFalse(Files.exists(folder.resolve("con_be.dbf.1.bak")));
                                })));
  }

  @TestFactory
  Stream<DynamicTest> narrowingAndNumericTextAreAllowedOnlyWhenValuesAreExact() {
    return Stream.of(
            new String[] {"N", "10", "0", "1234"},
            new String[] {"N", "8", "3", "65.250"},
            new String[] {"F", "10", "2", "65.25"},
            new String[] {"C", "12", "0", "+65.250"},
            new String[] {"C", "12", "0", "6.525e1"})
        .map(
            spec ->
                DynamicTest.dynamicTest(
                    Arrays.toString(spec),
                    () -> {
                      final boolean text = spec[0].equals("C");
                      malformedSpeed(
                          spec[0].charAt(0),
                          Integer.parseInt(spec[1]),
                          Integer.parseInt(spec[2]),
                          text ? spec[3] : Double.valueOf(spec[3]));
                      assertTrue(validate(properties("", "links"), message -> true));
                      try (DBFReader reader =
                          DbfFiles.reader(directory.resolve("links.dbf"), null)) {
                        assertEquals('N', reader.getField(8).getType());
                        assertEquals(7, reader.getField(8).getLength());
                        assertEquals(2, reader.getField(8).getDecimalCount());
                        assertEquals(
                            Double.parseDouble(spec[3]),
                            ((Number) reader.nextRecord()[8]).doubleValue());
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> valuesThatWouldBeRoundedTruncatedOrMisinterpretedAreNeverOffered() {
    return Stream.of(
            "12345", "-1000", "65.251", "fast", "12,50", "NaN", "1e2147483647", "1e-2147483647")
        .map(
            value ->
                DynamicTest.dynamicTest(
                    value,
                    () -> {
                      malformedSpeed('C', 24, 0, value);
                      final byte[] original = Files.readAllBytes(directory.resolve("links.dbf"));
                      final List<String> errors = new ArrayList<>();
                      assertFalse(
                          ProjectFilesTools.isValidProject(
                              properties("", "links"),
                              errors::add,
                              message -> fail("Cannot offer a lossy conversion")));
                      assertEquals(1, errors.size());
                      assertTrue(errors.get(0).contains("record 1"), errors.toString());
                      assertArrayEquals(
                          original, Files.readAllBytes(directory.resolve("links.dbf")));
                      assertFalse(Files.exists(directory.resolve("links.dbf.bak")));
                      assertFalse(Files.exists(directory.resolve(".links.nodus-save")));
                    }));
  }

  @Test
  void checksDeletedRecordsForLossToo() throws Exception {
    malformedSpeed('N', 9, 3, 65.251);
    markDeleted(directory.resolve("links.dbf"), 0);
    assertFalse(
        ProjectFilesTools.isValidProject(
            properties("", "links"),
            message -> {},
            message -> fail("Deleted rows must also fit without loss")));
    assertEquals('*', deletionFlag(directory.resolve("links.dbf"), 0));
  }

  @Test
  void severalFieldsProduceOnlyOnePromptForEachAffectedLayer() throws Exception {
    ProjectFilesFixture.writeLayer(directory, "nodes", true, 1);
    DBFField[] nodes = ProjectFilesFixture.fields(true, false);
    nodes[0] = new DBFField("num", 'N', 4, 0);
    ProjectFilesFixture.writeDbf(
        directory,
        "nodes",
        nodes,
        StandardCharsets.UTF_8,
        List.<Object[]>of(ProjectFilesFixture.row(true, false, 1, "node")));
    for (String name : List.of("first", "second")) {
      ProjectFilesFixture.writeLayer(directory, name, false, 1);
      DBFField[] fields = ProjectFilesFixture.fields(false, false);
      fields[5] = new DBFField("mode", 'C', 2, 0);
      fields[7] = new DBFField("capacity", 'N', 4, 0);
      fields[8] = new DBFField("speed", 'N', 4, 0);
      Object[] row = ProjectFilesFixture.row(false, false, 1, "link");
      row[5] = "3";
      row[8] = 65;
      ProjectFilesFixture.writeDbf(
          directory, name, fields, StandardCharsets.UTF_8, List.<Object[]>of(row));
    }
    final List<String> prompts = new ArrayList<>();
    assertTrue(
        validate(
            properties("nodes", "first second"),
            message -> {
              prompts.add(message);
              return true;
            }));
    assertEquals(3, prompts.size());
    assertTrue(prompts.get(0).contains("Layer 'nodes'"));
    for (int i = 1; i < prompts.size(); i++) {
      assertTrue(prompts.get(i).contains("mode:"));
      assertTrue(prompts.get(i).contains("capacity:"));
      assertTrue(prompts.get(i).contains("speed:"));
    }
    for (String name : List.of("nodes", "first", "second")) {
      assertTrue(Files.isRegularFile(directory.resolve(name + ".dbf.bak")));
    }
  }

  @Test
  void decliningKeepsEveryFileAndCreatesNoBackup() throws Exception {
    malformedSpeed('N', 4, 0, 65);
    final byte[] dbf = Files.readAllBytes(directory.resolve("links.dbf"));
    final byte[] cpg = Files.readAllBytes(directory.resolve("links.cpg"));
    final List<String> prompts = new ArrayList<>();
    assertFalse(
        validate(
            properties("", "links"),
            message -> {
              prompts.add(message);
              return false;
            }));
    assertEquals(1, prompts.size());
    assertArrayEquals(dbf, Files.readAllBytes(directory.resolve("links.dbf")));
    assertArrayEquals(cpg, Files.readAllBytes(directory.resolve("links.cpg")));
    assertFalse(Files.exists(directory.resolve("links.dbf.bak")));
    assertFalse(Files.exists(directory.resolve(".links.nodus-save")));
  }

  @Test
  void existingBackupsAreNeverOverwritten() throws Exception {
    malformedSpeed('N', 4, 0, 65);
    final byte[] original = Files.readAllBytes(directory.resolve("links.dbf"));
    Files.writeString(directory.resolve("links.dbf.bak"), "older backup");
    Files.writeString(directory.resolve("links.dbf.1.bak"), "another backup");
    assertTrue(
        validate(
            properties("", "links"),
            message -> {
              assertTrue(message.contains("links.dbf.2.bak"));
              return true;
            }));
    assertEquals("older backup", Files.readString(directory.resolve("links.dbf.bak")));
    assertEquals("another backup", Files.readString(directory.resolve("links.dbf.1.bak")));
    assertArrayEquals(original, Files.readAllBytes(directory.resolve("links.dbf.2.bak")));
  }

  @Test
  void backupCreatedDuringThePromptPreventsConversion() throws Exception {
    malformedSpeed('N', 4, 0, 65);
    final byte[] original = Files.readAllBytes(directory.resolve("links.dbf"));
    final List<String> errors = new ArrayList<>();
    assertFalse(
        ProjectFilesTools.isValidProject(
            properties("", "links"),
            errors::add,
            message -> {
              write(
                  directory.resolve("links.dbf.bak"),
                  "created elsewhere".getBytes(StandardCharsets.UTF_8));
              return true;
            }));
    assertEquals(1, errors.size());
    assertEquals("created elsewhere", Files.readString(directory.resolve("links.dbf.bak")));
    assertArrayEquals(original, Files.readAllBytes(directory.resolve("links.dbf")));
    assertTrue(validate(properties("", "links"), message -> true));
    assertArrayEquals(original, Files.readAllBytes(directory.resolve("links.dbf.1.bak")));
  }

  @Test
  void changedSourcesAreNeverReplacedByTheStagedOldVersion() throws Exception {
    malformedSpeed('N', 4, 0, 65);
    final byte[] changed = Files.readAllBytes(directory.resolve("links.dbf"));
    changed[changed.length - 2] = 'X';
    final List<String> errors = new ArrayList<>();
    assertFalse(
        ProjectFilesTools.isValidProject(
            properties("", "links"),
            errors::add,
            message -> {
              write(directory.resolve("links.dbf"), changed);
              return true;
            }));
    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("changed while awaiting confirmation"));
    assertArrayEquals(changed, Files.readAllBytes(directory.resolve("links.dbf")));
    assertFalse(Files.exists(directory.resolve("links.dbf.bak")));
  }

  @Test
  void failedInstallationRetainsTheOriginalAndItsBackupAndCanBeRetried() throws Exception {
    malformedSpeed('N', 4, 0, 65);
    final byte[] original = Files.readAllBytes(directory.resolve("links.dbf"));
    final List<String> errors = new ArrayList<>();
    assertFalse(
        ProjectFilesTools.isValidProject(
            properties("", "links"),
            errors::add,
            message -> {
              try {
                Files.delete(directory.resolve(".links.nodus-save/links.dbf"));
              } catch (java.io.IOException failure) {
                throw new AssertionError(failure);
              }
              return true;
            }));
    assertEquals(1, errors.size());
    assertArrayEquals(original, Files.readAllBytes(directory.resolve("links.dbf")));
    assertArrayEquals(original, Files.readAllBytes(directory.resolve("links.dbf.bak")));
    assertFalse(Files.exists(directory.resolve(".links.nodus-save")));
    assertTrue(validate(properties("", "links"), message -> true));
  }

  @Test
  void truncatedDataIsRejectedBeforeAnyPrompt() throws Exception {
    malformedSpeed('N', 4, 0, 65);
    final Path dbf = directory.resolve("links.dbf");
    final byte[] original = Files.readAllBytes(dbf);
    Files.write(dbf, Arrays.copyOf(original, original.length - 5));
    final byte[] truncated = Files.readAllBytes(dbf);
    assertFalse(
        ProjectFilesTools.isValidProject(
            properties("", "links"),
            message -> {},
            message -> fail("Cannot convert a truncated file")));
    assertArrayEquals(truncated, Files.readAllBytes(dbf));
    assertFalse(Files.exists(directory.resolve("links.dbf.bak")));
  }

  private void malformedSpeed(char type, int width, int decimals, Object value) throws Exception {
    ProjectFilesFixture.writeLayer(directory, "links", false, 1);
    final DBFField[] fields = ProjectFilesFixture.fields(false, false);
    fields[8] = new DBFField("speed", type, width, decimals);
    final Object[] row = ProjectFilesFixture.row(false, false, 1, "Liège 東京");
    row[8] = value;
    ProjectFilesFixture.writeDbf(
        directory, "links", fields, StandardCharsets.UTF_8, List.<Object[]>of(row));
  }

  private Properties properties(String nodes, String links) {
    return properties(directory, nodes, links);
  }

  private static Properties properties(Path folder, String nodes, String links) {
    final Properties properties = new Properties();
    properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, folder.toString());
    properties.setProperty(NodusC.PROP_NETWORK_NODES, nodes);
    properties.setProperty(NodusC.PROP_NETWORK_LINKS, links);
    return properties;
  }

  private static boolean validate(Properties properties, Predicate<String> confirm) {
    return ProjectFilesTools.isValidProject(properties, message -> fail(message), confirm);
  }

  private static byte[] bytes(Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (java.io.IOException failure) {
      throw new AssertionError(failure);
    }
  }

  private static void write(Path file, byte[] bytes) {
    try {
      Files.write(file, bytes);
    } catch (java.io.IOException failure) {
      throw new AssertionError(failure);
    }
  }

  private static void markDeleted(Path file, int row) throws Exception {
    try (RandomAccessFile dbf = new RandomAccessFile(file.toFile(), "rw")) {
      dbf.seek(8);
      int header = dbf.readUnsignedByte() | dbf.readUnsignedByte() << 8;
      int length = dbf.readUnsignedByte() | dbf.readUnsignedByte() << 8;
      dbf.seek(header + (long) row * length);
      dbf.write('*');
    }
  }

  private static char deletionFlag(Path file, int row) throws Exception {
    try (RandomAccessFile dbf = new RandomAccessFile(file.toFile(), "r")) {
      dbf.seek(8);
      int header = dbf.readUnsignedByte() | dbf.readUnsignedByte() << 8;
      int length = dbf.readUnsignedByte() | dbf.readUnsignedByte() << 8;
      dbf.seek(header + (long) row * length);
      return (char) dbf.readUnsignedByte();
    }
  }
}
