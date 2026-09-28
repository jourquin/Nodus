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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.coords.LatLonPoint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Ensures real editing operations invalidate the layer's geographic query cache. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class NodusEsriLayerSpatialIndexTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> editsRefreshViewportSelectionWithoutManualIndexInvalidation() {
    return Stream.of(false, true)
        .map(
            links ->
                DynamicTest.dynamicTest(
                    links ? "links" : "nodes",
                    () -> {
                      try (LayerTestProject project = new LayerTestProject(directory, links)) {
                        final NodusEsriLayer layer = project.layer;
                        layer.setProjection(
                            new Mercator(new LatLonPoint.Double(0, .1), 100000, 200, 200));
                        final OMGraphic original = project.graphic(10);
                        assertEquals(List.of(original), visible(layer));
                        final OMGraphic inserted;
                        if (links) {
                          EsriPolyline line =
                              new EsriPolyline(
                                  new double[] {0, .105, 0, .115},
                                  OMGraphic.DECIMAL_DEGREES,
                                  OMGraphic.LINETYPE_STRAIGHT);
                          assertTrue(layer.addRecord(line, 40, 1, 2, false));
                          inserted = line;
                        } else {
                          EsriPoint point = new EsriPoint(0, .105);
                          assertTrue(layer.addRecord(point, 40, false));
                          inserted = point;
                        }
                        assertEquals(List.of(original, inserted), visible(layer));
                        if (links) {
                          ((OMPoly) original)
                              .setLocation(
                                  new double[] {10, 10, 10, 10.01}, OMGraphic.DECIMAL_DEGREES);
                        } else {
                          ((OMPoint) original).setLat(10);
                          ((OMPoint) original).setLon(10);
                        }
                        layer.setDirtyShp(true);
                        assertEquals(List.of(inserted), visible(layer));
                        layer.removeRecord(layer.getNumIndex(40), false);
                        assertEquals(List.of(), visible(layer));
                        assertEquals(3, layer.getModel().getRowCount());
                      }
                    }));
  }

  private static List<OMGraphic> visible(NodusEsriLayer layer) {
    assertNotNull(layer.prepare());
    List<OMGraphic> result = new ArrayList<>();
    flatten(layer.getVisibleEsriGraphicList(), result);
    return result;
  }

  private static void flatten(OMGraphicList source, List<OMGraphic> target) {
    for (OMGraphic graphic : source) {
      if (graphic instanceof OMGraphicList) {
        flatten((OMGraphicList) graphic, target);
      } else {
        target.add(graphic);
      }
    }
  }
}
