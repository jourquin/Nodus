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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusC;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Extracted and empty point/link datasets remain usable as real shapefile layers. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(20)
class NodusEsriLayerPersistenceTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> extractionKeepsSelectedAttributesPairedWithTheirGeometry() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              links ? "extract links" : "extract points",
              () -> {
                try (LayerTestProject project = new LayerTestProject(directory, links)) {
                  final List<List<Object>> before = project.records();
                  assertTrue(project.layer.extract("subset", "NUM<>10"));
                  for (String extension : List.of(".shp", ".shx", ".dbf")) {
                    assertTrue(Files.size(directory.resolve("subset" + extension)) > 0);
                  }
                  project.setLocalProperty("subset" + NodusC.PROP_NAME, "subset");
                  LayerTestProject.TestLayer extracted = new LayerTestProject.TestLayer();
                  try {
                    extracted.setProject(project, "subset");
                    assertEquals(Set.of(20, 30), extracted.getIndex().keySet());
                    for (int num : new int[] {30, 20}) {
                      int sourceIndex = project.layer.getNumIndex(num);
                      int targetIndex = extracted.getNumIndex(num);
                      assertEquals(
                          project.layer.getModel().getRecord(sourceIndex),
                          extracted.getModel().getRecord(targetIndex));
                      OMGraphic expected = project.graphic(num);
                      OMGraphic actual = extracted.getEsriGraphicList().getOMGraphicAt(targetIndex);
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
                    extracted.dispose();
                  }
                  assertEquals(before, project.records());
                  assertEquals(3, project.layer.getEsriGraphicList().size());
                  assertFalse(project.layer.isDirty());
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> deletingAllFeaturesSavesAnEmptyLayerWithItsSchema() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              links ? "empty links" : "empty points",
              () -> {
                try (LayerTestProject project = new LayerTestProject(directory, links)) {
                  final int columns = project.layer.getModel().getColumnCount();
                  final int type = project.layer.getType();
                  while (project.layer.getModel().getRowCount() > 0) {
                    project.layer.removeRecord(0, false);
                  }
                  project.layer.save();
                  assertFalse(project.layer.isDirty());
                  LayerTestProject.TestLayer reloaded = new LayerTestProject.TestLayer();
                  try {
                    reloaded.setProject(project, "features");
                    assertEquals(type, reloaded.getType());
                    assertEquals(0, reloaded.getModel().getRowCount());
                    assertEquals(columns, reloaded.getModel().getColumnCount());
                    assertTrue(reloaded.getEsriGraphicList().isEmpty());
                    assertTrue(reloaded.getIndex().isEmpty());
                    assertEquals("AMOUNT", reloaded.getModel().getColumnName(columns - 2));
                    assertEquals(3, reloaded.getModel().getDecimalCount(columns - 2));
                  } finally {
                    reloaded.dispose();
                  }
                }
              }));
    }
    return tests;
  }
}
