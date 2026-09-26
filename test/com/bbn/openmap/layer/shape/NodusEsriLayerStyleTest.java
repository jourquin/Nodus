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

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Result style transitions are checked on real graphics without a window or pixel comparisons. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(20)
class NodusEsriLayerStyleTest {
  @TempDir Path directory;

  @Test
  void linkResultsChangeWidthAndColorsWithoutMutatingTheSharedStyle() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, true)) {
      EsriPolyline line = (EsriPolyline) project.graphic(30);
      project.layer.setDisplayResults(true);
      result(line, -6);
      project.layer.attachStyles();
      assertEquals(Color.RED, line.getLinePaint());
      assertEquals(Color.BLACK, line.getMattingPaint());
      BasicStroke stroke = (BasicStroke) line.getStroke();
      assertEquals(6, stroke.getLineWidth());
      assertEquals(BasicStroke.CAP_ROUND, stroke.getEndCap());
      assertEquals(BasicStroke.JOIN_BEVEL, stroke.getLineJoin());
      assertArrayEquals(new float[] {4, 2}, stroke.getDashArray());
      assertEquals(1, stroke.getDashPhase());
      result(line, 4);
      project.layer.attachStyles();
      assertEquals(Color.BLUE, line.getLinePaint());
      assertEquals(Color.WHITE, line.getMattingPaint());
      assertEquals(4, ((BasicStroke) line.getStroke()).getLineWidth());
      assertEquals(2, ((BasicStroke) project.style.getStroke()).getLineWidth());
      assertEquals(Color.BLUE, project.style.getLinePaint());
    }
  }

  @Test
  void pointBecomesVisibleAgainAfterZeroResultAndRestoresNormalStyle() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      final EsriPoint point = (EsriPoint) project.graphic(30);
      project.layer.setDisplayResults(true);
      result(point, 0);
      project.layer.attachStyles();
      assertFalse(point.isVisible());
      result(point, -5);
      project.layer.attachStyles();
      assertTrue(point.isVisible());
      assertEquals(5, point.getRadius());
      assertEquals(Color.RED, point.getLinePaint());
      assertEquals(Color.YELLOW, point.getFillPaint());
      result(point, 0);
      project.layer.attachStyles();
      project.layer.setDisplayResults(false);
      project.layer.attachStyles();
      assertTrue(point.isVisible());
      assertEquals(7, point.getRadius());
      assertEquals(Color.GREEN, point.getFillPaint());
      assertTrue(point.isOval());
    }
  }

  @Test
  void hidingZeroLinkResultsDoesNotPersistAfterResultsAreDisabled() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, true)) {
      EsriPolyline line = (EsriPolyline) project.graphic(30);
      project.layer.setDisplayResults(true);
      result(line, 0);
      project.layer.attachStyles();
      assertFalse(line.isVisible());
      project.layer.setDisplayResults(false);
      project.layer.attachStyles();
      assertTrue(line.isVisible());
      assertEquals(2, ((BasicStroke) line.getStroke()).getLineWidth());
    }
  }

  @Test
  void resultStylingMustRespectTheSqlVisibilityFilter() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, true)) {
      project.layer.setDisplayResults(true);
      result(project.graphic(30), 4);
      result(project.graphic(10), 5);
      project.layer.applyWhereFilter("NUM=10");
      project.layer.attachStyles();
      assertFalse(project.graphic(30).isVisible());
      assertTrue(project.graphic(10).isVisible());
      project.layer.setDisplayResults(false);
      project.layer.attachStyles();
      assertFalse(project.graphic(30).isVisible());
      assertTrue(project.graphic(10).isVisible());
    }
  }

  @Test
  void zoomThresholdAndDisabledStylesUseSimpleAppearanceThenRestoreIt() throws Exception {
    try (LayerTestProject project = new LayerTestProject(directory, false)) {
      final EsriPoint point = (EsriPoint) project.graphic(30);
      project.panel.threshold = 1000;
      project.panel.map.setScale(2000);
      project.layer.attachStyles();
      assertEquals(2, point.getRadius());
      assertEquals(Color.GRAY, point.getLinePaint());
      assertFalse(point.isOval());
      project.panel.map.setScale(1000);
      project.layer.attachStyles();
      assertEquals(7, point.getRadius());
      assertEquals(Color.BLUE, point.getLinePaint());
      assertTrue(point.isOval());
      project.panel.threshold = -1;
      project.panel.map.setScale(2000);
      project.layer.attachStyles();
      assertEquals(7, point.getRadius());
      project.layer.setStyleRendering(false);
      project.layer.attachStyles();
      assertEquals(2, point.getRadius());
      project.layer.setStyleRendering(true);
      project.layer.attachStyles();
      assertEquals(7, point.getRadius());
    }
  }

  private static void result(com.bbn.openmap.omGraphics.OMGraphic graphic, float size) {
    ((RealNetworkObject) graphic.getAttribute(0)).setSize(size);
  }
}
