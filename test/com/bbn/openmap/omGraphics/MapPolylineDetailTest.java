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

package com.bbn.openmap.omGraphics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.proj.EqualEarth;
import com.bbn.openmap.proj.GeoProj;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.Orthographic;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Line2D;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Verifies display accuracy against full geometry, independently of the simplification metric. */
class MapPolylineDetailTest {
  @TestFactory
  Stream<DynamicTest> reductionPreservesGeographyAndBoundsScreenError() {
    return Stream.of(false, true)
        .flatMap(
            equalEarth ->
                Stream.of(OMGraphic.LINETYPE_STRAIGHT, OMGraphic.LINETYPE_GREATCIRCLE)
                    .map(
                        type ->
                            DynamicTest.dynamicTest(
                                (equalEarth ? "EqualEarth" : "Mercator") + " type " + type,
                                () -> {
                                  final MapPolylineDetail cache = new MapPolylineDetail();
                                  final EsriPolyline line = line(type);
                                  final double[] coordinates = line.getLatLonArrayCopy();
                                  final Object marker = new Object();
                                  final BasicStroke stroke = new BasicStroke(3);
                                  line.putAttribute("marker", marker);
                                  line.setLinePaint(Color.MAGENTA);
                                  line.setStroke(stroke);
                                  final OMGraphicList list = list(line);
                                  GeoProj projection = projection(equalEarth);
                                  MapPolylineDetail.Measurement first =
                                      cache.generate(list, projection, true, false);
                                  assertEquals(1, first.getSimplifiedLines());
                                  assertTrue(
                                      first.getDisplayedVertices() < first.getSourceVertices() / 2);
                                  assertAccurate(line, projection);
                                  assertEquals(1, first.getBuiltHierarchies());
                                  assertEquals(1, first.getBuiltLevels());
                                  assertEquals(
                                      0,
                                      cache
                                          .generate(list, projection, true, false)
                                          .getBuiltLevels());
                                  projection.setCenter(42, 10);
                                  MapPolylineDetail.Measurement pan =
                                      cache.generate(list, projection, true, false);
                                  assertEquals(0, pan.getBuiltHierarchies());
                                  assertEquals(0, pan.getBuiltLevels());
                                  assertAccurate(line, projection);
                                  projection.setScale(1000000);
                                  MapPolylineDetail.Measurement zoom =
                                      cache.generate(list, projection, true, false);
                                  assertEquals(0, zoom.getBuiltHierarchies());
                                  assertEquals(1, zoom.getBuiltLevels());
                                  assertAccurate(line, projection);
                                  assertArrayEquals(coordinates, line.getLatLonArrayCopy(), 0);
                                  assertSame(line, list.getOMGraphicAt(0));
                                  assertSame(marker, line.getAttribute("marker"));
                                  assertSame(stroke, line.getStroke());
                                  assertEquals(Color.MAGENTA, line.getLinePaint());
                                })));
  }

  @Test
  void replacingCoordinatesOrChangingProjectionRebuildsTheHierarchy() {
    final MapPolylineDetail cache = new MapPolylineDetail();
    final EsriPolyline line = line(OMGraphic.LINETYPE_STRAIGHT);
    final OMGraphicList list = list(line);
    GeoProj projection = projection(false);
    cache.generate(list, projection, true, false);
    double[] changed = line.getLatLonArrayCopy();
    changed[changed.length / 2] += Math.toRadians(2);
    line.setLocation(changed, OMGraphic.RADIANS);
    assertEquals(1, cache.generate(list, projection, true, false).getBuiltHierarchies());
    assertAccurate(line, projection);
    projection = projection(true);
    assertEquals(1, cache.generate(list, projection, true, false).getBuiltHierarchies());
    assertAccurate(line, projection);
    line.setLineType(OMGraphic.LINETYPE_GREATCIRCLE);
    assertEquals(1, cache.generate(list, projection, true, false).getBuiltHierarchies());
    assertAccurate(line, projection);
  }

  @TestFactory
  Stream<DynamicTest> bypassesPreserveFullProjectedGeometry() {
    return Stream.of("disabled", "selected", "unsupported projection", "EqualEarth rhumb")
        .map(
            mode ->
                DynamicTest.dynamicTest(
                    mode,
                    () -> {
                      final EsriPolyline line =
                          line(
                              mode.equals("EqualEarth rhumb")
                                  ? OMGraphic.LINETYPE_RHUMB
                                  : OMGraphic.LINETYPE_STRAIGHT);
                      final Projection projection =
                          mode.equals("unsupported projection")
                              ? new Orthographic(new LatLonPoint.Double(40, 0), 10000000, 800, 600)
                              : projection(mode.equals("EqualEarth rhumb"));
                      line.setSelected(mode.equals("selected"));
                      MapPolylineDetail.Measurement result =
                          new MapPolylineDetail()
                              .generate(list(line), projection, !mode.equals("disabled"), false);
                      assertEquals(0, result.getSimplifiedLines());
                      assertFullProjection(line, projection);
                    }));
  }

  @Test
  void panningTheEqualEarthSeamAcrossALineBypassesAndThenReusesItsCache() {
    final MapPolylineDetail cache = new MapPolylineDetail();
    final EsriPolyline line = line(OMGraphic.LINETYPE_STRAIGHT);
    final OMGraphicList list = list(line);
    final GeoProj projection = projection(true);
    assertEquals(1, cache.generate(list, projection, true, false).getSimplifiedLines());
    projection.setCenter(40, 180);
    MapPolylineDetail.Measurement seam = cache.generate(list, projection, true, false);
    assertEquals(1, seam.getSeamFallbacks());
    assertEquals(0, seam.getSimplifiedLines());
    assertFullProjection(line, projection);
    projection.setCenter(40, 0);
    MapPolylineDetail.Measurement restored = cache.generate(list, projection, true, false);
    assertEquals(0, restored.getBuiltHierarchies());
    assertEquals(1, restored.getSimplifiedLines());
    assertAccurate(line, projection);
  }

  @Test
  void multipartListsKeepTheirStructureAndSelectedPartsInFullDetail() {
    final EsriPolyline first = line(OMGraphic.LINETYPE_STRAIGHT);
    final EsriPolyline second = line(OMGraphic.LINETYPE_STRAIGHT);
    second.select();
    OMGraphicList multipart = list(first);
    multipart.add(second);
    OMGraphicList outer = new OMGraphicList();
    outer.add(multipart);
    GeoProj projection = projection(false);
    MapPolylineDetail.Measurement result =
        new MapPolylineDetail().generate(outer, projection, true, false);
    assertEquals(1, result.getSimplifiedLines());
    assertSame(multipart, outer.getOMGraphicAt(0));
    assertSame(first, multipart.getOMGraphicAt(0));
    assertSame(second, multipart.getOMGraphicAt(1));
    assertAccurate(first, projection);
    assertFullProjection(second, projection);
  }

  private static EsriPolyline line(int type) {
    double[] coordinates = new double[802];
    for (int i = 0; i <= 400; i++) {
      coordinates[2 * i] = 40 + .05 * Math.sin(i * .06);
      coordinates[2 * i + 1] = -2 + i * .01;
    }
    return new EsriPolyline(coordinates, OMGraphic.DECIMAL_DEGREES, type);
  }

  private static GeoProj projection(boolean equalEarth) {
    LatLonPoint center = new LatLonPoint.Double(40, 0);
    return equalEarth
        ? new EqualEarth(center, 10000000, 800, 600)
        : new Mercator(center, 10000000, 800, 600);
  }

  private static OMGraphicList list(OMGraphic graphic) {
    OMGraphicList list = new OMGraphicList();
    list.add(graphic);
    return list;
  }

  private static OMPoly full(OMPoly source, Projection projection) {
    OMPoly full = new OMPoly(source.getLatLonArrayCopy(), OMGraphic.RADIANS, source.getLineType());
    full.setIsPolygon(false);
    assertTrue(full.generate(projection));
    return full;
  }

  private static void assertFullProjection(OMPoly line, Projection projection) {
    OMPoly reference = full(line, projection);
    assertEquals(reference.xpoints.length, line.xpoints.length);
    for (int i = 0; i < reference.xpoints.length; i++) {
      assertArrayEquals(reference.xpoints[i], line.xpoints[i], 0);
      assertArrayEquals(reference.ypoints[i], line.ypoints[i], 0);
    }
  }

  private static void assertAccurate(OMPoly line, Projection projection) {
    final OMPoly reference = full(line, projection);
    assertTrue(reference.xpoints.length > 0);
    assertTrue(line.xpoints.length > 0);
    assertTrue(reference.xpoints[0].length > 1);
    assertTrue(line.xpoints[0].length > 1);
    // Independent point-to-segment distances on screen, including samples between vertices.
    double maximum = 0;
    for (int part = 0; part < reference.xpoints.length; part++) {
      for (int i = 1; i < reference.xpoints[part].length; i++) {
        for (int sample = 0; sample <= 4; sample++) {
          double weight = sample / 4.0;
          double x =
              reference.xpoints[part][i - 1] * (1 - weight) + reference.xpoints[part][i] * weight;
          double y =
              reference.ypoints[part][i - 1] * (1 - weight) + reference.ypoints[part][i] * weight;
          double minimum = Double.POSITIVE_INFINITY;
          for (int target = 0; target < line.xpoints.length; target++) {
            for (int j = 1; j < line.xpoints[target].length; j++) {
              minimum =
                  Math.min(
                      minimum,
                      Line2D.ptSegDist(
                          line.xpoints[target][j - 1],
                          line.ypoints[target][j - 1],
                          line.xpoints[target][j],
                          line.ypoints[target][j],
                          x,
                          y));
            }
          }
          maximum = Math.max(maximum, minimum);
        }
      }
    }
    assertTrue(
        maximum <= .7501, "Screen error " + maximum + " exceeds 0.75px (plus float rounding)");
  }
}
