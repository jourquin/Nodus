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

package com.bbn.openmap.layer.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real layer edits must preserve SQL rows, geometry/attribute pairing and identifier indexes. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(20)
class NodusEsriLayerEditingTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> editsKeepIdentifiersAndRecordsInSync() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              links ? "link edits" : "point edits",
              () -> {
                try (LayerTestProject project = new LayerTestProject(directory, links)) {
                  final OMGraphic survivor = project.graphic(20);
                  assertEquals(Map.of(30, 0, 10, 1, 20, 2), project.layer.getIndex());
                  boolean added =
                      links
                          ? project.layer.addRecord(LayerTestProject.line(40), 40, 5, 6, false)
                          : project.layer.addRecord(new EsriPoint(0, 0.4), 40);
                  assertTrue(added);
                  assertEquals(Map.of(30, 0, 10, 1, 20, 2, 40, 3), project.layer.getIndex());
                  assertEquals(
                      -1,
                      project.layer.getNumIndex(0),
                      "A blank record must not create an ID zero alias");
                  if (links) {
                    assertEquals(
                        List.of(List.of("5", "6")),
                        project.rows("SELECT NODE1,NODE2 FROM features WHERE NUM=40"));
                  }
                  project.layer.removeRecord(1, false);
                  assertEquals(Map.of(30, 0, 20, 1, 40, 2), project.layer.getIndex());
                  assertSame(survivor, project.graphic(20));
                  assertEquals(3, project.layer.getEsriGraphicList().size());
                  assertEquals(3, project.layer.getModel().getRowCount());
                  assertEquals(
                      List.of(List.of("20"), List.of("30"), List.of("40")),
                      project.rows("SELECT NUM FROM features ORDER BY NUM"));
                  assertTrue(project.layer.isDirty());
                  project.layer.removeLastRecord();
                  assertEquals(Map.of(30, 0, 20, 1), project.layer.getIndex());
                  assertEquals(
                      List.of(List.of("20"), List.of("30")),
                      project.rows("SELECT NUM FROM features ORDER BY NUM"));
                }
              }));
    }
    return tests;
  }

  @Test
  void sqlFiltersSelectByIdentifierAndCanBeReplacedOrCleared() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      final List<List<Object>> records = project.records();
      project.layer.applyWhereFilter("NUM=10");
      assertFalse(project.graphic(30).isVisible());
      assertTrue(project.graphic(10).isVisible());
      assertFalse(project.graphic(20).isVisible());
      project.layer.applyWhereFilter("NUM>=20");
      assertTrue(project.graphic(30).isVisible());
      assertFalse(project.graphic(10).isVisible());
      assertTrue(project.graphic(20).isVisible());
      project.layer.applyWhereFilter("1=0");
      for (OMGraphic graphic : project.layer.getEsriGraphicList()) {
        assertFalse(graphic.isVisible());
      }
      project.layer.applyWhereFilter("");
      for (OMGraphic graphic : project.layer.getEsriGraphicList()) {
        assertTrue(graphic.isVisible());
      }
      assertEquals(records, project.records());
      assertEquals("", project.layer.getWhereStmt());
      assertFalse(project.layer.isDirty());
    }
  }

  @TestFactory
  List<DynamicTest> savedEditsKeepGeometryAndAttributesPaired() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              links ? "saved links" : "saved points",
              () -> {
                try (LayerTestProject project = new LayerTestProject(directory, links)) {
                  project.layer.removeRecord(1, false);
                  int labelColumn = project.layer.getModel().getColumnCount() - 3;
                  project.layer.getModel().setValueAt("O'Brien", 0, labelColumn);
                  project.layer.getModel().setValueAt(-2.375, 0, labelColumn + 1);
                  project.layer.graphicIndex = 0;
                  project.layer.saveRecord(30);
                  assertEquals(
                      List.of(List.of("O'Brien", "-2.375")),
                      project.rows("SELECT LABEL,AMOUNT FROM features WHERE NUM=30"));
                  project.layer.save();
                  assertFalse(project.layer.isDirty());
                  LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
                  try {
                    reloaded.setProject(project, "features");
                    assertEquals(Map.of(30, 0, 20, 1), reloaded.getIndex());
                    assertEquals("O'Brien", reloaded.getModel().getValueAt(0, labelColumn));
                    assertEquals(
                        -2.375,
                        ((Number) reloaded.getModel().getValueAt(0, labelColumn + 1))
                            .doubleValue());
                    for (int num : new int[] {30, 20}) {
                      OMGraphic expected = project.graphic(num);
                      OMGraphic actual =
                          reloaded.getEsriGraphicList().getOMGraphicAt(reloaded.getNumIndex(num));
                      if (links) {
                        assertArrayEquals(
                            ((EsriPolyline) expected).getLatLonArray(),
                            ((EsriPolyline) actual).getLatLonArray(),
                            1e-12);
                      } else {
                        assertEquals(
                            ((EsriPoint) expected).getLat(), ((EsriPoint) actual).getLat(), 1e-12);
                        assertEquals(
                            ((EsriPoint) expected).getLon(), ((EsriPoint) actual).getLon(), 1e-12);
                      }
                    }
                  } finally {
                    reloaded.dispose();
                  }
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> identifierZeroSurvivesSubsequentInsertions() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              links ? "zero link ID" : "zero node ID",
              () -> {
                try (LayerTestProject project = new LayerTestProject(directory, links)) {
                  for (int num : new int[] {0, 50}) {
                    assertTrue(
                        links
                            ? project.layer.addRecord(LayerTestProject.line(num), num, 1, 2, false)
                            : project.layer.addRecord(new EsriPoint(0, num * 0.01), num));
                  }
                  assertEquals(3, project.layer.getNumIndex(0));
                  assertEquals(4, project.layer.getNumIndex(50));
                  project.layer.removeRecord(1, false);
                  assertEquals(2, project.layer.getNumIndex(0));
                  assertEquals(3, project.layer.getNumIndex(50));
                  assertEquals(0, ((Number) project.layer.getModel().getValueAt(2, 0)).intValue());
                }
              }));
    }
    return tests;
  }

  @Test
  void insertingACompleteLinkRecordPreservesQuotesAndDecimalAttributes() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, true)) {
      List<Object> row = new ArrayList<>(project.layer.getModel().getRecord(0));
      row.set(0, 40.0);
      row.set(9, "O'Brien's link");
      row.set(10, -12.375);
      project.layer.addRecord(LayerTestProject.line(40), row);
      assertEquals(3, project.layer.getNumIndex(40));
      assertEquals(
          List.of(List.of("O'Brien's link", "-12.375")),
          project.rows("SELECT LABEL,AMOUNT FROM features WHERE NUM=40"));
      assertEquals("original 30", project.layer.getModel().getValueAt(0, 9));
      assertTrue(project.layer.isDirty());
    }
  }

  @TestFactory
  List<DynamicTest> discardedEditsReloadFromUnchangedShapefiles() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              links ? "discard link edits" : "discard point edits",
              () -> {
                try (LayerTestProject project = new LayerTestProject(directory, links)) {
                  project.layer.removeRecord(1, false);
                  project.layer.rollback();
                  assertFalse(edu.uclouvain.core.nodus.database.JDBCUtils.tableExists("features"));
                  LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
                  try {
                    reloaded.setProject(project, "features");
                    assertEquals(Map.of(30, 0, 10, 1, 20, 2), reloaded.getIndex());
                    assertEquals(3, reloaded.getModel().getRowCount());
                    assertEquals(
                        List.of(List.of("10"), List.of("20"), List.of("30")),
                        project.rows("SELECT NUM FROM features ORDER BY NUM"));
                  } finally {
                    reloaded.dispose();
                  }
                }
              }));
    }
    return tests;
  }
}
