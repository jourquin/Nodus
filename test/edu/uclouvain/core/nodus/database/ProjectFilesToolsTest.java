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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DbfFiles;
import edu.uclouvain.core.nodus.testing.ProjectFilesFixture;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ProjectFilesToolsTest implements ShapeConstants {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> mandatoryFields() {
    return Stream.of(true, false)
        .flatMap(
            point ->
                IntStream.range(0, point ? 3 : 9)
                    .boxed()
                    .flatMap(
                        index ->
                            Stream.of("name", "type", "width", "precision")
                                .map(
                                    attribute ->
                                        DynamicTest.dynamicTest(
                                            "point="
                                                + point
                                                + ", field="
                                                + index
                                                + ", invalid "
                                                + attribute,
                                            () ->
                                                checkMandatoryField(
                                                    point, false, index, attribute)))));
  }

  @TestFactory
  Stream<DynamicTest> malformedLegacySchemasAreNeverUpgraded() {
    return IntStream.range(0, 8)
        .boxed()
        .flatMap(
            index ->
                Stream.of("name", "type", "width", "precision")
                    .map(
                        attribute ->
                            DynamicTest.dynamicTest(
                                "legacy field=" + index + ", invalid " + attribute,
                                () -> checkMandatoryField(false, true, index, attribute))));
  }

  private void checkMandatoryField(boolean point, boolean legacy, int index, String attribute)
      throws Exception {
    ProjectFilesFixture.writeLayer(directory, "layer", point);
    DBFField[] fields = ProjectFilesFixture.fields(point, legacy);
    DBFField original = fields[index];
    fields[index] =
        new DBFField(
            attribute.equals("name") ? "OTHER" : original.getName(),
            attribute.equals("type") ? 'C' : 'N',
            original.getLength() + (attribute.equals("width") ? 1 : 0),
            attribute.equals("type")
                ? 0
                : original.getDecimalCount() + (attribute.equals("precision") ? 1 : 0));
    ProjectFilesFixture.writeDbf(directory, "layer", fields, StandardCharsets.UTF_8, List.of());
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "layer", type(point)));
    byte[] originalBytes = Files.readAllBytes(directory.resolve("layer.dbf"));
    assertFalse(ProjectFilesTools.isValidProject(properties(point, "layer"), message -> {}));
    assertArrayEquals(originalBytes, Files.readAllBytes(directory.resolve("layer.dbf")));
  }

  @TestFactory
  Stream<DynamicTest> truncatedSchemasReturnFalseWithoutThrowing() {
    return Stream.of(true, false)
        .map(point -> DynamicTest.dynamicTest("point=" + point, () -> checkTruncatedSchema(point)));
  }

  private void checkTruncatedSchema(boolean point) throws Exception {
    ProjectFilesFixture.writeLayer(directory, "layer", point);
    for (int length = 1; length < (point ? 3 : 9); length++) {
      ProjectFilesFixture.writeDbf(
          directory,
          "layer",
          Arrays.copyOf(ProjectFilesFixture.fields(point, false), length),
          StandardCharsets.UTF_8,
          List.of());
      assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "layer", type(point)));
      assertFalse(ProjectFilesTools.isValidProject(properties(point, "layer"), message -> {}));
    }
  }

  @TestFactory
  Stream<DynamicTest> acceptsOnlySupportedIdWidthsAndCaseInsensitiveNames() {
    return Stream.of(true, false)
        .map(point -> DynamicTest.dynamicTest("point=" + point, () -> checkIdWidths(point)));
  }

  private void checkIdWidths(boolean point) throws Exception {
    ProjectFilesFixture.writeLayer(directory, "layer", point);
    for (int width : new int[] {4, 5, 6, 7, 8, 9, 10, 11}) {
      DBFField[] fields = ProjectFilesFixture.fields(point, false);
      for (int index : point ? new int[] {0} : new int[] {0, 3, 4}) {
        fields[index] =
            new DBFField(fields[index].getName().toUpperCase(java.util.Locale.ROOT), 'N', width, 0);
      }
      ProjectFilesFixture.writeDbf(directory, "layer", fields, StandardCharsets.UTF_8, List.of());
      assertEquals(
          width == 5 || width == 6 || width == 10,
          ProjectFilesTools.isValidLayer(directory.toString(), "layer", type(point)),
          "width " + width);
    }
  }

  @TestFactory
  Stream<DynamicTest> missingOrTruncatedSidecarIsInvalid() {
    return Stream.of(".shp", ".shx", ".dbf")
        .map(extension -> DynamicTest.dynamicTest(extension, () -> checkSidecar(extension)));
  }

  private void checkSidecar(String extension) throws Exception {
    ProjectFilesFixture.writeLayer(directory, "layer", true);
    Path file = directory.resolve("layer" + extension);
    Files.write(file, new byte[] {0, 1});
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "layer"));
    Files.delete(file);
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "layer"));
  }

  @Test
  void mismatchedShapeTypesAndInvalidHeadersAreRejected() throws Exception {
    ProjectFilesFixture.writeLayer(directory, "nodes", true);
    ProjectFilesFixture.writeLayer(directory, "links", false);
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "nodes", SHAPE_TYPE_POLYLINE));
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "links", SHAPE_TYPE_POINT));
    Files.copy(
        directory.resolve("links.shx"),
        directory.resolve("nodes.shx"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "nodes"));
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "links", SHAPE_TYPE_POLYGON));
  }

  @TestFactory
  Stream<DynamicTest> legacyUpgradePreservesRowsAttributesEncodingAndRunsOnce() {
    return Stream.of("UTF-8", "windows-1252")
        .map(encoding -> DynamicTest.dynamicTest(encoding, () -> checkLegacyUpgrade(encoding)));
  }

  private void checkLegacyUpgrade(String encoding) throws Exception {
    ProjectFilesFixture.writeLayer(directory, "links", false, 7, 3);
    final Charset charset = Charset.forName(encoding);
    final String label = charset.equals(StandardCharsets.UTF_8) ? "Liège 東京 €" : "Liège été €";
    final DBFField[] fields = ProjectFilesFixture.fields(false, true);
    // Supported historical node/link ID widths must also work in the shifted legacy schema.
    fields[0] = new DBFField("num", 'N', 5, 0);
    fields[2] = new DBFField("node1", 'N', 6, 0);
    ProjectFilesFixture.writeDbf(
        directory,
        "links",
        fields,
        charset,
        List.of(
            ProjectFilesFixture.row(false, true, 7, label),
            ProjectFilesFixture.row(false, true, 3, "second")));
    final Properties properties = properties(false, "links");
    properties.setProperty("dbfCharset", "UTF-8");
    properties.setProperty("links.dbfCharset", encoding);
    final List<String> errors = new ArrayList<>();
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "links", SHAPE_TYPE_POLYLINE));
    assertTrue(ProjectFilesTools.isValidProject(properties, errors::add), errors.toString());
    assertTrue(errors.isEmpty());
    assertEquals(charset, DbfFiles.charset(directory.resolve("links.dbf"), null));
    try (DBFReader reader = DbfFiles.reader(directory.resolve("links.dbf"), null)) {
      assertEquals(10, reader.getFieldCount());
      assertEquals("enabled", reader.getField(2).getName().toLowerCase(java.util.Locale.ROOT));
      Object[] first = reader.nextRecord().clone();
      Object[] second = reader.nextRecord().clone();
      assertEquals(7, JDBCUtils.getInt(first[0]));
      assertEquals(3, JDBCUtils.getInt(second[0]));
      assertEquals(1, JDBCUtils.getInt(first[2]));
      assertEquals(1, JDBCUtils.getInt(second[2]));
      assertEquals(1, JDBCUtils.getInt(first[3]));
      assertEquals(2, JDBCUtils.getInt(first[4]));
      assertEquals(65.25, ((Number) first[8]).doubleValue());
      assertEquals(label, first[9]);
      assertEquals("second", second[9]);
      assertFalse(reader.hasNextRecord());
    }
    byte[] upgraded = Files.readAllBytes(directory.resolve("links.dbf"));
    assertTrue(ProjectFilesTools.isValidProject(properties, errors::add));
    assertArrayEquals(upgraded, Files.readAllBytes(directory.resolve("links.dbf")));
  }

  @Test
  void malformedLegacyAndFollowingLayersNeverTriggerUpgrade() throws Exception {
    ProjectFilesFixture.writeLayer(directory, "legacy", false);
    DBFField[] fields = ProjectFilesFixture.fields(false, true);
    fields[7] = new DBFField("speed", 'C', 7, 0);
    ProjectFilesFixture.writeDbf(directory, "legacy", fields, StandardCharsets.UTF_8, List.of());
    final byte[] original = Files.readAllBytes(directory.resolve("legacy.dbf"));
    assertFalse(ProjectFilesTools.isValidLayer(directory.toString(), "legacy"));
    assertFalse(ProjectFilesTools.isValidProject(properties(false, "legacy"), message -> {}));
    assertArrayEquals(original, Files.readAllBytes(directory.resolve("legacy.dbf")));
    ProjectFilesFixture.writeLayer(directory, "bad", false);
    Files.delete(directory.resolve("bad.shx"));
    assertFalse(ProjectFilesTools.isValidProject(properties(false, "bad"), message -> {}));
    ProjectFilesFixture.writeLayer(directory, "good", false);
    assertTrue(
        ProjectFilesTools.isValidProject(properties(false, "good"), message -> fail(message)));
    // A stray ENABLED after the legacy prefix must not cause insertion of a duplicate.
    fields = Arrays.copyOf(ProjectFilesFixture.fields(false, true), 10);
    fields[9] = new DBFField("enabled", 'N', 1, 0);
    ProjectFilesFixture.writeDbf(directory, "legacy", fields, StandardCharsets.UTF_8, List.of());
    assertFalse(ProjectFilesTools.isValidProject(properties(false, "legacy"), message -> {}));
  }

  @Test
  void failedUpgradePreservesOriginalDbfAndCanBeRetried() throws Exception {
    ProjectFilesFixture.writeLayer(directory, "links", false);
    ProjectFilesFixture.writeDbf(
        directory,
        "links",
        ProjectFilesFixture.fields(false, true),
        StandardCharsets.UTF_8,
        List.<Object[]>of(ProjectFilesFixture.row(false, true, 1, "été")));
    final byte[] original = Files.readAllBytes(directory.resolve("links.dbf"));
    Files.delete(directory.resolve("links.cpg"));
    Files.createDirectory(directory.resolve("links.cpg"));
    Properties properties = properties(false, "links");
    properties.setProperty("links.dbfCharset", "UTF-8");
    List<String> errors = new ArrayList<>();
    assertFalse(ProjectFilesTools.isValidProject(properties, errors::add));
    assertEquals(1, errors.size());
    assertArrayEquals(original, Files.readAllBytes(directory.resolve("links.dbf")));
    assertFalse(Files.exists(directory.resolve(".links.nodus-save")));
    Files.delete(directory.resolve("links.cpg"));
    assertTrue(ProjectFilesTools.isValidProject(properties, message -> fail(message)));
  }

  private Properties properties(boolean point, String name) {
    Properties properties = new Properties();
    properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, directory.toString());
    properties.setProperty(point ? NodusC.PROP_NETWORK_NODES : NodusC.PROP_NETWORK_LINKS, name);
    return properties;
  }

  private static int type(boolean point) {
    return point ? SHAPE_TYPE_POINT : SHAPE_TYPE_POLYLINE;
  }
}
