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

package com.bbn.openmap.layer.shape.displayindex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Geographic query expectations independent of the index's own bounds/intersection helpers. */
class DisplaySpatialIndexTest {
  @TestFactory
  Stream<DynamicTest> viewportBoundariesCrossingLinesAndSourceOrder() {
    return variants(
        "viewport",
        tree -> {
          final OMGraphicList source = new OMGraphicList();
          final OMGraphic crossing = line(0, -5, 0, 5);
          final OMGraphic inside = new EsriPoint(0, 0);
          final OMGraphic corner = new EsriPoint(1, 1);
          final OMGraphic touching = line(0, 1, 0, 5);
          final OMGraphic outside = new EsriPoint(4, 4);
          source.add(crossing);
          source.add(outside);
          source.add(inside);
          source.add(corner);
          source.add(touching);
          // Enough distant features to exercise tree branches, rather than its small-list fallback.
          pad(source);
          DisplaySpatialIndex index = index(source, tree);
          // The index deliberately uses strict intersection: mere boundary contact is excluded.
          assertEquals(List.of(crossing, inside), objects(index.locateRecords(-1, -1, 1, 1)));
          assertEquals(
              List.of(crossing, inside, corner, touching),
              objects(index.locateRecords(-1.01, -1.01, 1.01, 1.01)));
          assertTrue(index.locateRecords(80, 70, 81, 71).isEmpty());
          assertEquals(objects(source), objects(index.locateRecords(-180, -90, 180, 90)));
        });
  }

  @TestFactory
  Stream<DynamicTest> wrappedViewsUnionBothHalvesWithoutDuplicates() {
    return variants(
        "date line",
        tree -> {
          final OMGraphicList source = new OMGraphicList();
          final OMGraphic west = new EsriPoint(0, -179);
          final OMGraphic center = new EsriPoint(0, 0);
          final OMGraphic east = new EsriPoint(0, 179);
          final OMGraphic spanning = line(0, -179, 0, 179);
          source.add(west);
          source.add(center);
          source.add(spanning);
          source.add(east);
          pad(source);
          DisplaySpatialIndex index = index(source, tree);
          assertEquals(
              List.of(west, spanning, east), objects(index.locateRecords(170, -10, -170, 10)));
          assertEquals(List.of(spanning, east), objects(index.locateRecords(178, -1, 180, 1)));
        });
  }

  @TestFactory
  Stream<DynamicTest> multipartGeometryRetainsWholeFeatureAndIdentity() {
    return variants(
        "multipart",
        tree -> {
          final OMGraphic first = line(0, -5, 0, 5);
          final OMGraphic second = line(40, 40, 41, 41);
          final OMGraphicList multipart = new OMGraphicList();
          multipart.add(first);
          multipart.add(second);
          final OMGraphicList source = new OMGraphicList();
          source.add(multipart);
          pad(source);
          OMGraphicList result = index(source, tree).locateRecords(-1, -1, 1, 1);
          assertEquals(1, result.size());
          assertSame(multipart, result.getOMGraphicAt(0));
          assertEquals(List.of(first, second), objects(multipart));
        });
  }

  @TestFactory
  Stream<DynamicTest> seededQueriesMatchIndependentBoundingBoxes() {
    return variants(
        "seeded bounds",
        tree -> {
          final Random random = new Random(41723);
          final List<double[]> boxes = new ArrayList<>();
          for (int i = 0; i < 257; i++) {
            double x = random.nextInt(280) - 140;
            double y = random.nextInt(120) - 60;
            boxes.add(new double[] {x, y, x + random.nextInt(8), y + random.nextInt(8)});
          }
          Collections.shuffle(boxes, random);
          final OMGraphicList source = new OMGraphicList();
          for (double[] b : boxes) {
            source.add(line(b[1], b[0], b[3], b[2]));
          }
          DisplaySpatialIndex index = index(source, tree);
          for (int query = 0; query < 100; query++) {
            double west = random.nextInt(300) - 150 + .25;
            double south = random.nextInt(140) - 70 + .25;
            double east = west + 20;
            double north = south + 20;
            List<OMGraphic> expected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
              double[] b = boxes.get(i);
              if (b[0] < east && b[2] > west && b[1] < north && b[3] > south) {
                expected.add(source.getOMGraphicAt(i));
              }
            }
            assertEquals(
                expected, objects(index.locateRecords(west, south, east, north)), "query " + query);
          }
        });
  }

  @TestFactory
  Stream<DynamicTest> emptyAndCoincidentFeaturesRemainQueryable() {
    return variants(
        "empty and coincident",
        tree -> {
          assertTrue(index(new OMGraphicList(), tree).locateRecords(-1, -1, 1, 1).isEmpty());
          OMGraphicList source = new OMGraphicList();
          for (int i = 0; i < 129; i++) {
            source.add(new EsriPoint(0, 0));
          }
          DisplaySpatialIndex index = index(source, tree);
          assertEquals(objects(source), objects(index.locateRecords(-1, -1, 1, 1)));
          assertTrue(index.locateRecords(1, 1, 2, 2).isEmpty());
        });
  }

  @TestFactory
  Stream<DynamicTest> rebuiltIndexesUseEditedCoordinatesIncludingMultipartChildren() {
    return variants(
        "edited coordinates",
        tree -> {
          final EsriPolyline first = (EsriPolyline) line(0, -5, 0, 5);
          final EsriPolylineList multipart = new EsriPolylineList();
          multipart.add(first);
          multipart.add(line(40, 40, 41, 41));
          final OMGraphicList source = new OMGraphicList();
          source.add(multipart);
          pad(source);
          assertEquals(
              List.of(multipart), objects(index(source, tree).locateRecords(-1, -1, 1, 1)));
          first.getExtents();
          multipart.getExtents();
          first.setLocation(new double[] {40, 40, 41, 41}, OMGraphic.DECIMAL_DEGREES);
          assertTrue(index(source, tree).locateRecords(-1, -1, 1, 1).isEmpty());
          first.setLocation(new double[] {0, -5, 0, 5}, OMGraphic.DECIMAL_DEGREES);
          assertEquals(
              List.of(multipart), objects(index(source, tree).locateRecords(-1, -1, 1, 1)));
        });
  }

  private static Stream<DynamicTest> variants(String label, CheckedQuery query) {
    return Stream.of(false, true)
        .map(
            tree ->
                DynamicTest.dynamicTest(
                    label + (tree ? " tree" : " linear"), () -> query.check(tree)));
  }

  private interface CheckedQuery {
    void check(boolean tree) throws Exception;
  }

  private static DisplaySpatialIndex index(OMGraphicList source, boolean tree) {
    return tree ? new DisplaySpatialIndexTree(source) : new DisplaySpatialIndexLinear(source);
  }

  private static OMGraphic line(double lat1, double lon1, double lat2, double lon2) {
    return new EsriPolyline(
        new double[] {lat1, lon1, lat2, lon2},
        OMGraphic.DECIMAL_DEGREES,
        OMGraphic.LINETYPE_STRAIGHT);
  }

  private static void pad(OMGraphicList source) {
    for (int i = 0; i < 80; i++) {
      source.add(new EsriPoint(30 + i * .1, 30 + i * .1));
    }
  }

  private static List<OMGraphic> objects(OMGraphicList list) {
    return new ArrayList<>(list);
  }
}
