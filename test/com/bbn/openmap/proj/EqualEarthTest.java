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

package com.bbn.openmap.proj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.proj.coords.LatLonPoint;
import java.awt.geom.Point2D;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Independent projection reference coordinates and screen/longitude boundary checks. */
class EqualEarthTest {
  // Generated independently with PROJ: proj -f '%.15f' +proj=eqearth +R=1
  // Input columns for PROJ are longitude and latitude in degrees. No PROJ runtime is needed.
  private static final double[][] REFERENCE = {
    {0, 0, 0, 0},
    {0, 30, 0.451104997282679, 0},
    {45, 30, 0.386618166367661, 0.860231085522010},
    {-60, 120, 1.359373391714373, -1.088300835505319},
    {90, 180, 1.603588648284052, 1.317362759157413},
    {-90, -180, -1.603588648284052, -1.317362759157413},
    {80, 179.999, 1.670267900429383, 1.288211196220253}
  };

  @TestFactory
  Stream<DynamicTest> forwardAndInverseMatchIndependentProjReference() {
    return Stream.of(REFERENCE)
        .map(
            c ->
                DynamicTest.dynamicTest(
                    c[0] + ", " + c[1],
                    () -> {
                      final EqualEarth projection = projection(0, 0);
                      final double radius =
                          projection.getPlanetPixelRadius() / projection.getScale();
                      final Point2D expected =
                          new Point2D.Double(400 + radius * c[2], 300 - radius * c[3]);
                      final Point2D normalized = new Point2D.Double();
                      assertSame(
                          normalized,
                          EqualEarth.forwardNormalized(
                              Math.toRadians(c[0]), Math.toRadians(c[1]), normalized));
                      assertEquals(c[2], normalized.getX(), 2e-14);
                      assertEquals(c[3], normalized.getY(), 2e-14);
                      Point2D actual = projection.forward(c[0], c[1], new Point2D.Double(), false);
                      assertEquals(expected.getX(), actual.getX(), 1e-8);
                      assertEquals(expected.getY(), actual.getY(), 1e-8);
                      // Invert independent screen coordinates, not the result of forward().
                      Point2D inverse =
                          projection.inverse(
                              expected.getX(), expected.getY(), new Point2D.Double());
                      assertEquals(c[0], inverse.getY(), 2e-6);
                      assertEquals(0, Math.IEEEremainder(inverse.getX() - c[1], 360), 1e-9);
                    }));
  }

  @Test
  void screenCenterAndRelativeLongitudeFollowTheRequestedCenter() {
    EqualEarth projection = projection(45, 30);
    Point2D center = projection.forward(45, 30, new Point2D.Double(), false);
    assertEquals(400, center.getX(), 1e-10);
    assertEquals(300, center.getY(), 1e-10);
    Point2D east = projection.forward(45, 60, new Point2D.Double(), false);
    assertEquals(
        400 + projection.getPlanetPixelRadius() / projection.getScale() * REFERENCE[2][2],
        east.getX(),
        1e-8);
    Point2D radians = projection.forward(Math.PI / 4, Math.PI / 3, new Point2D.Double(), true);
    assertEquals(east.getX(), radians.getX(), 1e-8);
    assertEquals(east.getY(), radians.getY(), 1e-8);
  }

  @Test
  void longitudeWrappingUsesTheCentralMeridian() {
    EqualEarth projection = projection(0, 170);
    Point2D west = projection.forward(20, -175, new Point2D.Double(), false);
    Point2D east = projection.forward(20, 185, new Point2D.Double(), false);
    assertEquals(west.getX(), east.getX(), 1e-8);
    assertEquals(west.getY(), east.getY(), 1e-8);
    Point2D inverse = projection.inverse(west.getX(), west.getY(), new Point2D.Double());
    assertEquals(-175, inverse.getX(), 1e-9);
    assertEquals(20, inverse.getY(), 1e-9);
  }

  @Test
  void polesAndCoordinatesBeyondTheMapRemainFiniteAndClamped() {
    EqualEarth projection = projection(0, 0);
    assertEquals(Math.PI / 2, projection.normalizeLatitude(Math.PI), 0);
    assertEquals(-Math.PI / 2, projection.normalizeLatitude(-Math.PI), 0);
    for (double y : new double[] {-1e9, 1e9}) {
      Point2D geographic = projection.inverse(400, y, new Point2D.Double());
      assertEquals(y < 0 ? 90 : -90, geographic.getY(), 2e-6);
      assertEquals(0, geographic.getX(), 0);
    }
    assertTrue(projection.isPlotable(90, 180));
    assertTrue(projection.isPlotable(-90, -180));
    assertFalse(projection.isPlotable(90.001, 0));
    assertFalse(projection.isPlotable(0, 180.001));
    assertFalse(projection.isPlotable(Double.NaN, 0));
  }

  private static EqualEarth projection(double lat, double lon) {
    return new EqualEarth(new LatLonPoint.Double(lat, lon), 10000000, 800, 600);
  }
}
