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

package edu.uclouvain.core.nodus.compute.real;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMLine;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Geographic link lengths and repeated initialization must respect units and existing traffic. */
class RealLinkInitializerTest {
  // OpenMap uses a sphere with the WGS84 equatorial radius for its kilometer distances.
  private static final double EARTH_RADIUS_KM = 6378.137;
  private static final double LENGTH_TOLERANCE_KM = 0.001;

  private static final double[][] ROUTES_DEGREES = {
    {0, 0, 0, 1},
    {1, 1, 2, 1},
    {50, 1, 51, 2},
    {-12, -40, -11, -41},
    {1, -1, 1, 1},
    {1, 179, 1, 181}
  };

  @TestFactory
  List<DynamicTest> geographicPolylinesUseDeclaredUnitsInEitherDirection() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean esri : new boolean[] {false, true}) {
      for (int units : new int[] {OMGraphic.DECIMAL_DEGREES, OMGraphic.RADIANS}) {
        for (double[] degrees : ROUTES_DEGREES) {
          tests.add(
              dynamicTest(
                  esri + "/units=" + units + "/" + Arrays.toString(degrees),
                  () -> {
                    final OMPoly graphic = polyline(esri, units, degrees);
                    final double[] before = graphic.getLatLonArray().clone();
                    final double expected = referenceLength(degrees);
                    assertEquals(
                        expected, RealLinkInitializer.computeLength(graphic), LENGTH_TOLERANCE_KM);
                    assertEquals(
                        expected, RealLinkInitializer.computeLength(graphic), LENGTH_TOLERANCE_KM);
                    assertArrayEquals(before, graphic.getLatLonArray());
                    assertEquals(
                        expected,
                        RealLinkInitializer.computeLength(
                            polyline(esri, units, reverseVertices(degrees))),
                        LENGTH_TOLERANCE_KM);
                  }));
        }
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> endpointFallbackAlwaysUsesDegreesInEitherDirection() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (double[] degrees : ROUTES_DEGREES) {
      tests.add(
          dynamicTest(
              Arrays.toString(degrees),
              () -> {
                final OMPoint first = new OMPoint(degrees[0], degrees[1]);
                final OMPoint second = new OMPoint(degrees[2], degrees[3]);
                final double expected = referenceLength(degrees);
                assertEquals(
                    expected,
                    RealLinkInitializer.computeLength(null, first, second),
                    LENGTH_TOLERANCE_KM);
                assertEquals(
                    expected,
                    RealLinkInitializer.computeLength(new OMLine(), second, first),
                    LENGTH_TOLERANCE_KM);
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> allPolylineSegmentsTakePrecedenceOverEndpointFallback() {
    final List<DynamicTest> tests = new ArrayList<>();
    final double[] degrees = {0, 0, 1, 0, 1, 1};
    for (boolean esri : new boolean[] {false, true}) {
      for (int units : new int[] {OMGraphic.DECIMAL_DEGREES, OMGraphic.RADIANS}) {
        tests.add(
            dynamicTest(
                esri + "/units=" + units,
                () ->
                    assertEquals(
                        referenceLength(degrees),
                        RealLinkInitializer.computeLength(
                            polyline(esri, units, degrees),
                            new OMPoint(0.0, 0.0),
                            new OMPoint(0.0, 80.0)),
                        LENGTH_TOLERANCE_KM)));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> missingOrDegeneratePolylineGeometryUsesEndpoints() {
    final List<DynamicTest> tests = new ArrayList<>();
    final List<OMPoly> graphics = new ArrayList<>();
    graphics.add(new OMPoly());
    graphics.add(new OMPoly(new int[] {10, 20, 30, 40}));
    final OMPoly changedToScreenCoordinates =
        polyline(false, OMGraphic.DECIMAL_DEGREES, new double[] {0, 0, 0, 10});
    changedToScreenCoordinates.setLocation(new int[] {10, 20, 30, 40});
    graphics.add(changedToScreenCoordinates);
    for (boolean esri : new boolean[] {false, true}) {
      for (double[] degrees : new double[][] {{}, {1, 1}, {1, 1, 1, 1}}) {
        graphics.add(polyline(esri, OMGraphic.DECIMAL_DEGREES, degrees));
      }
    }
    for (int index = 0; index < graphics.size(); index++) {
      final OMPoly graphic = graphics.get(index);
      tests.add(
          dynamicTest(
              "geometry " + index,
              () -> {
                assertEquals(0, RealLinkInitializer.computeLength(graphic));
                assertEquals(
                    EARTH_RADIUS_KM * Math.PI / 180,
                    RealLinkInitializer.computeLength(
                        graphic, new OMPoint(0.0, 0.0), new OMPoint(0.0, 1.0)),
                    LENGTH_TOLERANCE_KM);
              }));
    }
    return tests;
  }

  @Test
  void missingOrCoincidentEndpointsHaveZeroFallbackLength() {
    final OMPoint point = new OMPoint(1.0, 1.0);
    assertEquals(0, RealLinkInitializer.computeLength(null));
    assertEquals(0, RealLinkInitializer.computeLength(null, null, point));
    assertEquals(0, RealLinkInitializer.computeLength(null, point, null));
    assertEquals(0, RealLinkInitializer.computeLength(null, new OMLine(), point));
    assertEquals(0, RealLinkInitializer.computeLength(null, point, new OMLine()));
    assertEquals(0, RealLinkInitializer.computeLength(null, point, new OMPoint(1.0, 1.0)));
  }

  @Test
  void screenPointsCannotSupplyGeographicEndpointsButOffsetPointsCan() {
    final OMPoint point = new OMPoint(0.0, 1.0);
    assertEquals(0, RealLinkInitializer.computeLength(null, new OMPoint(10, 20), point));
    assertEquals(0, RealLinkInitializer.computeLength(null, point, new OMPoint(10, 20)));
    assertEquals(0, RealLinkInitializer.computeLength(null, new OMPoint(), point));
    assertEquals(
        EARTH_RADIUS_KM * Math.PI / 180,
        RealLinkInitializer.computeLength(null, new OMPoint(0.0, 0.0, 10, 20), point),
        LENGTH_TOLERANCE_KM);
  }

  @Test
  void repeatedInitializationRefreshesGeometryAndPreservesOrResetsDirectionalTraffic() {
    final OMPoly graphic = polyline(false, OMGraphic.DECIMAL_DEGREES, new double[] {0, 0, 0, 1});
    graphic.putAttribute(0, "uninitialized");
    final List<Object> record = new ArrayList<>(Collections.nCopies(NodusC.DBF_IDX_SPEED + 1, 0));
    record.set(NodusC.DBF_IDX_NODE1, 11);
    record.set(NodusC.DBF_IDX_NODE2, 22);
    record.set(NodusC.DBF_IDX_SPEED, 72.0);
    final RealLink real = RealLinkInitializer.initializeRealLink(graphic, record, false);
    assertSame(real, graphic.getAttribute(0));
    assertEquals(EARTH_RADIUS_KM * Math.PI / 180, real.getLength(), LENGTH_TOLERANCE_KM);
    assertEquals(72, real.getSpeed());

    final VirtualNode first = new VirtualNode(1, 11, 5, (byte) 1, (byte) 1, (short) 0, 0, 0);
    final VirtualNode second = new VirtualNode(2, 22, 5, (byte) 1, (byte) 1, (short) 0, 0, 1);
    final VirtualLink forward = new VirtualLink(1, 0, 0, first, second, real);
    final VirtualLink reverse = new VirtualLink(2, 0, 0, second, first, real);
    real.addPassengerCarUnits(forward, 7);
    real.addPassengerCarUnits(reverse, 11);
    real.addAuxiliaryPassengerCarUnits(forward, 3);
    real.addAuxiliaryPassengerCarUnits(reverse, 5);
    graphic.setLocation(new double[] {0, 0, 0, 2}, OMGraphic.DECIMAL_DEGREES);
    record.set(NodusC.DBF_IDX_SPEED, 36.0);
    assertSame(real, RealLinkInitializer.initializeRealLink(graphic, record, false));
    assertEquals(EARTH_RADIUS_KM * Math.PI / 90, real.getLength(), LENGTH_TOLERANCE_KM);
    assertEquals(36, real.getSpeed());
    assertTraffic(real, forward, reverse, 7, 11, 3, 5);
    record.set(NodusC.DBF_IDX_NODE1, 22);
    record.set(NodusC.DBF_IDX_NODE2, 11);
    assertSame(real, RealLinkInitializer.initializeRealLink(graphic, record, false));
    assertTraffic(real, forward, reverse, 11, 7, 5, 3);
    assertSame(real, RealLinkInitializer.initializeRealLink(graphic, record, true));
    assertTraffic(real, forward, reverse, 0, 0, 0, 0);
  }

  @Test
  void initializationCanUseEndpointFallbackAndRejectsMissingRequiredInputs() {
    final OMGraphic graphic = new OMLine();
    final List<Object> record = new ArrayList<>(Collections.nCopies(NodusC.DBF_IDX_SPEED + 1, 0));
    record.set(NodusC.DBF_IDX_NODE1, 11);
    record.set(NodusC.DBF_IDX_SPEED, 50);
    final RealLink real =
        RealLinkInitializer.initializeRealLink(
            graphic, record, new OMPoint(0.0, 0.0), new OMPoint(0.0, 1.0), true);
    assertSame(real, graphic.getAttribute(0));
    assertEquals(EARTH_RADIUS_KM * Math.PI / 180, real.getLength(), LENGTH_TOLERANCE_KM);
    assertEquals(50, real.getSpeed());
    assertThrows(
        IllegalArgumentException.class,
        () -> RealLinkInitializer.initializeRealLink(null, record, false));
    assertThrows(
        IllegalArgumentException.class,
        () -> RealLinkInitializer.initializeRealLink(graphic, null, false));
  }

  private static OMPoly polyline(boolean esri, int units, double[] degrees) {
    final double[] coordinates = degrees.clone();
    if (units == OMGraphic.RADIANS) {
      for (int index = 0; index < coordinates.length; index++) {
        coordinates[index] = Math.toRadians(coordinates[index]);
      }
    }
    return esri
        ? new EsriPolyline(coordinates, units, OMGraphic.LINETYPE_GREATCIRCLE)
        : new OMPoly(coordinates, units, OMGraphic.LINETYPE_GREATCIRCLE);
  }

  private static double[] reverseVertices(double[] coordinates) {
    final double[] reversed = new double[coordinates.length];
    for (int index = 0; index < coordinates.length; index += 2) {
      reversed[index] = coordinates[coordinates.length - index - 2];
      reversed[index + 1] = coordinates[coordinates.length - index - 1];
    }
    return reversed;
  }

  /** Independent haversine reference from declared degree inputs, without OpenMap helpers. */
  private static double referenceLength(double[] degrees) {
    double length = 0;
    for (int index = 2; index < degrees.length; index += 2) {
      final double lat1 = Math.toRadians(degrees[index - 2]);
      final double lat2 = Math.toRadians(degrees[index]);
      final double halfLatitude = Math.toRadians(degrees[index] - degrees[index - 2]) / 2;
      final double halfLongitude = Math.toRadians(degrees[index + 1] - degrees[index - 1]) / 2;
      final double haversine =
          Math.pow(Math.sin(halfLatitude), 2)
              + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(halfLongitude), 2);
      length += 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }
    return length;
  }

  private static void assertTraffic(
      RealLink real,
      VirtualLink forward,
      VirtualLink reverse,
      int currentForward,
      int currentReverse,
      int auxiliaryForward,
      int auxiliaryReverse) {
    assertEquals(currentForward, real.getCurrentPassengerCarUnits(forward));
    assertEquals(currentReverse, real.getCurrentPassengerCarUnits(reverse));
    assertEquals(auxiliaryForward, real.getAuxiliaryPassengerCarUnits(forward));
    assertEquals(auxiliaryReverse, real.getAuxiliaryPassengerCarUnits(reverse));
  }
}
