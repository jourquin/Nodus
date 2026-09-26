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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.layer.location.BasicLocation;
import com.bbn.openmap.layer.location.NodusLocationHandler;
import com.bbn.openmap.layer.location.NodusLocationLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import java.awt.Font;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real label queries, locations and refreshes on temporary point and link layers. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(20)
class NodusLocationHandlerIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> coordinatesAndTextFollowTheirOriginalGeometry() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean links : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              links ? "link labels" : "point labels",
              () -> {
                try (LayerTestProject project = new LayerTestProject(directory, links);
                    Labels labels = new Labels(project)) {
                  labels.handler.reloadData();
                  assertEquals(
                      List.of("original 30", "original 10", "original 20"), labels.names());
                  for (int num : new int[] {30, 10, 20}) {
                    BasicLocation location = object(project, num).getLocation();
                    assertEquals(0, location.lat, 1e-12);
                    // Point label coordinates pass through OpenMap's float coordinate API.
                    assertEquals(num * 0.01 + (links ? 0.005 : 0), location.lon, 1e-7);
                    assertEquals(
                        project.layer.getNumIndex(num), object(project, num).getRowIndex());
                  }
                }
              }));
    }
    return tests;
  }

  @Test
  void databaseRefreshUpdatesTextsAndMembershipOfAnUnchangedLabelFilter() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false);
        Labels labels = new Labels(project)) {
      labels.handler.setWhereStmt("LABEL='original 30'");
      labels.handler.reloadData();
      assertEquals(1, labels.names().size());
      project.execute("UPDATE features SET LABEL='original 30' WHERE NUM=10");
      project.execute("UPDATE features SET LABEL='changed' WHERE NUM=30");
      assertTrue(project.layer.updateDbfTableModel());
      assertNull(object(project, 30).getLocation());
      assertEquals("original 30", object(project, 10).getLocation().getName());
      assertEquals(List.of("original 30"), labels.names());
    }
  }

  @Test
  void caseChangesInsideSqlLiteralsMustRefreshTheLabelSelection() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false);
        Labels labels = new Labels(project)) {
      labels.handler.setWhereStmt("LABEL='original 30'");
      labels.handler.reloadData();
      assertEquals(1, labels.names().size());
      labels.handler.setWhereStmt("LABEL='ORIGINAL 30'");
      labels.handler.reloadData();
      assertTrue(labels.names().isEmpty());
      labels.handler.setWhereStmt("");
      labels.handler.reloadData();
      assertEquals(3, labels.names().size());
    }
  }

  @Test
  void removingAMiddleRowRebuildsLabelIndexes() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false);
        Labels labels = new Labels(project)) {
      labels.handler.reloadData();
      project.layer.removeRecord(1, true);
      assertEquals(List.of("original 30", "original 20"), labels.names());
      assertEquals(1, object(project, 20).getRowIndex());
      project.layer.applyWhereFilter("NUM=20");
      labels.handler.reloadData();
      assertEquals(List.of("original 20"), labels.names());
    }
  }

  @Test
  void resultLabelsUseValuesNotSizesAndHideWhenTheLayerIsHidden() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false);
        Labels labels = new Labels(project)) {
      object(project, 30).setResult(1234.567);
      object(project, 30).setSize(4);
      object(project, 10).setResult(-2.345);
      object(project, 10).setSize(-5);
      object(project, 20).setResult(99);
      object(project, 20).setSize(0);
      labels.handler.setDisplayResults(true);
      labels.handler.reloadData();
      NumberFormat format = NumberFormat.getInstance();
      format.setMaximumFractionDigits(2);
      assertEquals(List.of(format.format(1234.567), format.format(-2.345)), labels.names());
      labels.handler.setVisible(false);
      assertTrue(labels.names().isEmpty());
      labels.handler.setVisible(true);
      assertEquals(2, labels.names().size());
      labels.handler.setDisplayResults(false);
      labels.handler.reloadData();
      assertEquals(3, labels.names().size());
    }
  }

  @Test
  void emptyOrMissingFieldsProduceNoLabelsAndCanBeReplaced() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false);
        Labels labels = new Labels(project)) {
      labels.handler.setLocationFieldName("MISSING");
      labels.handler.reloadData();
      assertTrue(labels.names().isEmpty());
      labels.handler.setLocationFieldName("LABEL");
      project.layer.getModel().setValueAt(null, 0, 3);
      project.layer.getModel().setValueAt("", 1, 3);
      labels.handler.reloadData();
      assertEquals(List.of("original 20"), labels.names());
    }
  }

  @Test
  void fontPropertiesRoundTripIncludingBoldItalic() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false);
        Labels labels = new Labels(project)) {
      for (int style : new int[] {Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC}) {
        labels.handler.setFontName(new Font("Dialog", style, 17));
        labels.handler.setProperties("", project.properties);
        assertEquals(style, labels.handler.getFontName().getStyle());
        assertEquals(17, labels.handler.getFontName().getSize());
      }
    }
  }

  @Test
  void disposeClearsAttachedLocationsAndRenderedLabels() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false);
        Labels labels = new Labels(project)) {
      labels.handler.reloadData();
      assertFalse(labels.names().isEmpty());
      labels.handler.dispose();
      assertTrue(labels.names().isEmpty());
      for (int num : new int[] {30, 10, 20}) {
        assertNull(object(project, num).getLocation());
      }
    }
  }

  private static RealNetworkObject object(LayerTestProject project, int num) {
    return (RealNetworkObject) project.graphic(num).getAttribute(0);
  }

  private static final class Labels implements AutoCloseable {
    final NodusLocationLayer owner =
        new NodusLocationLayer() {
          private static final long serialVersionUID = 1L;

          @Override
          public void doPrepare() {}
        };
    final NodusLocationHandler handler;

    Labels(LayerTestProject project) {
      handler = new NodusLocationHandler(project.layer);
      handler.setLayer(owner);
      handler.setProperties("", project.properties);
      handler.setLocationFieldName("LABEL");
      project.layer.setLocationHandler(handler);
      project.layer.realLabels = true;
    }

    List<String> names() {
      List<String> names = new ArrayList<>();
      OMGraphicList graphics = handler.get(90, -180, -90, 180, null);
      for (OMGraphic graphic : graphics) {
        names.add(((BasicLocation) graphic).getName());
      }
      return names;
    }

    @Override
    public void close() {
      handler.dispose();
      owner.dispose();
    }
  }
}
