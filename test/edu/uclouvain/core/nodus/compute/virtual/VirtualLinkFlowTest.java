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

package edu.uclouvain.core.nodus.compute.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.costs.VehiclesParser;
import edu.uclouvain.core.nodus.compute.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Checks the demand and vehicle accounting used by multi-flow and equilibrium assignments. */
class VirtualLinkFlowTest {
  @Test
  void exactMultiFlowConsumesValidPathDemandOnceAndKeepsGroupsSeparate() {
    final VirtualLink link = movingLink(new RealLink(), 1, 2);
    link.setNbGroups(2, 1);
    Path rejected = path(0.5);
    rejected.isValid = false;
    Path[] alternatives = {path(0.25), path(0.75), rejected};
    link.addCell((byte) 0, new PathODCell(0, 100));
    link.addCell((byte) 0, new PathODCell(1, 100));
    link.addCell((byte) 0, new PathODCell(2, 100));
    link.addCell((byte) 1, new PathODCell(1, 40));

    link.spreadFlowOverPaths((byte) 0, alternatives);
    assertEquals(100, link.getCurrentVolume((byte) 0), 1e-12);
    assertEquals(0, link.getCurrentVolume((byte) 1), 1e-12);
    link.spreadFlowOverPaths((byte) 0, alternatives);
    assertEquals(100, link.getCurrentVolume((byte) 0), 1e-12);
    link.spreadFlowOverPaths((byte) 1, alternatives);
    assertEquals(30, link.getCurrentVolume((byte) 1), 1e-12);
  }

  @Test
  void fastMultiFlowUsesBothAlternativeAndDestinationIndices() {
    final VirtualLink link = movingLink(new RealLink(), 1, 2);
    link.setNbGroups(1, 1);
    Path[][] alternatives = {{path(0.2), path(0.6)}, {path(0.8), path(0.4)}};
    alternatives[0][1].isValid = false;
    link.addCell((byte) 0, new PathODCell(0, 0, 100));
    link.addCell((byte) 0, new PathODCell(1, 1, 50));
    link.addCell((byte) 0, new PathODCell(0, 1, 50));

    link.spreadVolumeOverPaths((byte) 0, alternatives);
    assertEquals(40, link.getCurrentVolume((byte) 0), 1e-12);
    link.spreadVolumeOverPaths((byte) 0, alternatives);
    assertEquals(40, link.getCurrentVolume((byte) 0), 1e-12);
    link.addCell((byte) 0, new PathODCell(1, 0, 100));
    link.spreadVolumeOverPaths((byte) 0, alternatives);
    assertEquals(120, link.getCurrentVolume((byte) 0), 1e-12);
  }

  @Test
  void equilibriumBlendKeepsPreviousVolumeAndClearsAuxiliaryDemand() {
    VirtualLink link = movingLink(new RealLink(), 1, 2);
    link.setNbGroups(2, 1);
    link.addVolume((byte) 0, 100);
    link.addAuxiliaryVolume((byte) 0, 200);
    link.addVolume((byte) 1, 33);
    link.combineVolumes((byte) 0, 0.25);

    assertEquals(125, link.getCurrentVolume((byte) 0), 1e-12);
    assertEquals(100, link.getPreviousVolume((byte) 0), 1e-12);
    assertEquals(0, link.getAuxiliaryVolume((byte) 0), 1e-12);
    assertEquals(33, link.getCurrentVolume((byte) 1), 1e-12);
    link.addAuxiliaryVolume((byte) 0, 25);
    link.combineVolumes((byte) 0, 0.5);
    assertEquals(75, link.getCurrentVolume((byte) 0), 1e-12);
    assertEquals(125, link.getPreviousVolume((byte) 0), 1e-12);
  }

  @Test
  void zeroStepRetainsCurrentVolumeAndFullStepTakesAuxiliaryVolume() {
    VirtualLink link = movingLink(new RealLink(), 1, 2);
    link.setNbGroups(1, 1);
    link.addVolume((byte) 0, 100);
    link.addAuxiliaryVolume((byte) 0, 200);
    link.combineVolumes((byte) 0, 0);
    assertEquals(100, link.getCurrentVolume((byte) 0), 1e-12);
    assertEquals(0, link.getAuxiliaryVolume((byte) 0), 1e-12);
    link.addAuxiliaryVolume((byte) 0, 300);
    link.combineVolumes((byte) 0, 1);
    assertEquals(300, link.getCurrentVolume((byte) 0), 1e-12);
    assertEquals(100, link.getPreviousVolume((byte) 0), 1e-12);
  }

  @TestFactory
  List<DynamicTest> equilibriumStepsKeepClassFlowsAndDirectionsSeparate() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (double lambda : new double[] {0, 0.25, 1}) {
      tests.add(
          dynamicTest(
              "lambda=" + lambda,
              () -> {
                final VirtualLink link = movingLink(new RealLink(), 1, 2);
                link.setNbGroups(2, 1);
                link.initializeEquilibriumClasses(8);
                link.addEquilibriumVolume((byte) 0, (byte) 2, 80);
                link.addEquilibriumVolume((byte) 0, (byte) 7, 20);
                link.addEquilibriumVolume((byte) 1, (byte) 7, 40);
                link.addAuxiliaryVolume((byte) 0, (byte) 2, 20);
                link.addAuxiliaryVolume((byte) 0, (byte) 7, 80);
                assertEquals(-60, link.getEquilibriumDirection((byte) 0, (byte) 2));
                assertEquals(60, link.getEquilibriumDirection((byte) 0, (byte) 7));
                assertEquals(0, link.getEquilibriumDirection((byte) 0, (byte) 4));
                link.combineVolumes((byte) 0, lambda);
                assertEquals(80 - 60 * lambda, link.getEquilibriumVolume((byte) 0, (byte) 2));
                assertEquals(20 + 60 * lambda, link.getEquilibriumVolume((byte) 0, (byte) 7));
                assertEquals(100, link.getCurrentVolume((byte) 0));
                assertEquals(120 * lambda, link.getEquilibriumVolumeChange((byte) 0));
                assertEquals(0, link.getAuxiliaryVolume((byte) 0));
                assertEquals(-(80 - 60 * lambda), link.getEquilibriumDirection((byte) 0, (byte) 2));
                assertEquals(40, link.getEquilibriumVolume((byte) 1, (byte) 7));
                assertEquals(0, link.getEquilibriumVolume((byte) 1, (byte) 2));
              }));
    }
    return tests;
  }

  @Test
  void resettingGroupsDiscardsClassHistoryAndRestoresSingleClassAccounting() {
    final VirtualLink link = movingLink(new RealLink(), 1, 2);
    link.setNbGroups(2, 1);
    link.initializeEquilibriumClasses(8);
    link.addEquilibriumVolume((byte) 1, (byte) 7, 40);
    link.addAuxiliaryVolume((byte) 1, (byte) 7, 80);
    link.setNbGroups(1, 1);
    link.initializeEquilibriumClasses(1);
    link.addEquilibriumVolume((byte) 0, (byte) 0, 10);
    link.addAuxiliaryVolume((byte) 0, (byte) 0, 30);
    assertEquals(20, link.getEquilibriumDirection((byte) 0, (byte) 0));
    link.combineVolumes((byte) 0, 0.5);
    assertEquals(20, link.getEquilibriumVolume((byte) 0, (byte) 0));
    assertEquals(10, link.getEquilibriumVolumeChange((byte) 0));
    assertEquals(0, link.getEquilibriumVolume((byte) 0, (byte) 7));
    assertEquals(0, link.getEquilibriumDirection((byte) 0, (byte) 7));
  }

  @Test
  void dynamicVolumesStayInTheirGroupAndTimeSliceBeyondByteRange() {
    VirtualLink link = movingLink(new RealLink(), 1, 2);
    link.setNbGroups(2, 256);
    link.addVolume((byte) 0, 0, 60);
    link.addVolume((byte) 1, 200, 35);
    link.addVolume((byte) 1, 200, 5);
    link.volumesToVehicles((byte) 1, 200, 12, 1);

    assertEquals(60, link.getCurrentVolume((byte) 0, 0), 1e-12);
    assertEquals(40, link.getCurrentVolume((byte) 1, 200), 1e-12);
    assertEquals(0, link.getCurrentVolume((byte) 0, 200), 1e-12);
    assertEquals(0, link.getCurrentVolume((byte) 1, 0), 1e-12);
    assertEquals(4, link.getCurrentVehicles((byte) 1, 200));
    assertEquals(0, link.getCurrentVehicles((byte) 1, 0));
  }

  @Test
  void vehicleRoundingAndPassengerCarUnitsKeepDirectionsSeparate() {
    final RealLink real = new RealLink();
    real.setOriginNodeId(1);
    final VirtualLink forward = movingLink(real, 1, 2);
    final VirtualLink reverse = movingLink(real, 2, 1);
    forward.setNbGroups(1, 1);
    reverse.setNbGroups(1, 1);
    forward.addVolume((byte) 0, 35);
    forward.addAuxiliaryVolume((byte) 0, 11);
    reverse.addVolume((byte) 0, 21);
    forward.volumesToVehicles((byte) 0, 0, 10, 1.5);
    reverse.volumesToVehicles((byte) 0, 0, 10, 1.5);

    assertEquals(4, forward.getCurrentVehicles((byte) 0));
    assertEquals(3, reverse.getCurrentVehicles((byte) 0));
    assertEquals(6, real.getCurrentPassengerCarUnits(forward), 1e-12);
    assertEquals(5, real.getCurrentPassengerCarUnits(reverse), 1e-12);
    assertEquals(3, real.getAuxiliaryPassengerCarUnits(forward), 1e-12);
    assertEquals(0, real.getAuxiliaryPassengerCarUnits(reverse), 1e-12);
  }

  @Test
  void frankWolfeProjectionLeavesStoredVolumesAndVehiclesUnchanged() {
    final RealLink real = new RealLink();
    real.setOriginNodeId(1);
    VirtualLink link = movingLink(real, 1, 2);
    link.setNbGroups(1, 1);
    link.addVolume((byte) 0, 35);
    link.addAuxiliaryVolume((byte) 0, 75);
    link.volumesToVehicles((byte) 0, 0, 10, 1.5);
    real.resetPassengerCarUnits();
    link.projectedVolumesToVehicles((byte) 0, 0, 10, 1.5, 0.25);

    assertEquals(8, real.getCurrentPassengerCarUnits(link), 1e-12);
    assertEquals(35, link.getCurrentVolume((byte) 0), 1e-12);
    assertEquals(75, link.getAuxiliaryVolume((byte) 0), 1e-12);
    assertEquals(4, link.getCurrentVehicles((byte) 0));
  }

  @TestFactory
  List<DynamicTest> invalidCharacteristicsCannotChangeVehiclesOrTrafficEvenWithZeroDemand() {
    final List<DynamicTest> tests = new ArrayList<>();
    final double[][] invalid = {
      {0, 1},
      {-1, 1},
      {Double.NaN, 1},
      {Double.POSITIVE_INFINITY, 1},
      {Double.NEGATIVE_INFINITY, 1},
      {1, -1},
      {1, Double.NaN},
      {1, Double.POSITIVE_INFINITY},
      {1, Double.NEGATIVE_INFINITY}
    };
    for (boolean projected : new boolean[] {false, true}) {
      for (double[] pair : invalid) {
        tests.add(
            dynamicTest(
                projected + "/capacity=" + pair[0] + "/pcu=" + pair[1],
                () -> assertRejectedConversion(projected, 0, 0, pair[0], pair[1], 0)));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> unsupportedCountsCannotSaturateOrWrapOrPartiallyUpdateTraffic() {
    final List<DynamicTest> tests = new ArrayList<>();
    // Current volume, auxiliary volume, capacity, PCU, projection lambda.
    final double[][] cases = {
      {Integer.MAX_VALUE + 0.25, 0, 1, 1, 0},
      {0, Integer.MAX_VALUE + 0.25, 1, 1, 1},
      {2, 0, 1, Integer.MAX_VALUE, 0},
      {0, 3, 1, 1e9, 1},
      {1, 0, Double.MIN_VALUE, 1, 0},
      {2, 0, 1, Double.MAX_VALUE, 0},
      {Integer.MAX_VALUE - 1.0, 0, 1, 1, 0},
      {0, Integer.MAX_VALUE - 1.0, 1, 1, 1},
      {0, Double.NaN, 1, 1, 1},
      {Double.POSITIVE_INFINITY, 0, 1, 1, 0},
      {-1, 0, 1, 1, 0}
    };
    for (boolean projected : new boolean[] {false, true}) {
      for (int index = 0; index < cases.length; index++) {
        final double[] values = cases[index];
        tests.add(
            dynamicTest(
                projected + "/unsupported count " + index,
                () ->
                    assertRejectedConversion(
                        projected, values[0], values[1], values[2], values[3], values[4])));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> loadedFractionalCapacityRoundsUpAndZeroPcuSuppressesOnlyRoadTraffic() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (double pcu : new double[] {0, 0.75}) {
      tests.add(
          dynamicTest(
              "pcu=" + pcu,
              () -> {
                final Properties properties = new Properties();
                properties.setProperty(NodusC.VARNAME_AVERAGELOAD + ".1,1", "2.5");
                properties.setProperty(NodusC.VARNAME_PCU + ".1,1", Double.toString(pcu));
                final VehiclesParser parser = new VehiclesParser(7);
                assertTrue(parser.loadVehicleCharacteristics(properties, (byte) 3));
                final RealLink real = new RealLink();
                real.setOriginNodeId(1);
                final VirtualLink link = movingLink(real, 1, 2);
                link.setNbGroups(1, 1);
                link.addVolume((byte) 0, 10.1);
                link.addAuxiliaryVolume((byte) 0, 4.9);
                link.volumesToVehicles(
                    (byte) 0,
                    (byte) 0,
                    parser.getAverageLoad(3, 1, 1),
                    parser.getPassengerCarUnits(3, 1, 1));
                assertEquals(5, link.getCurrentVehicles((byte) 0));
                assertEquals(pcu == 0 ? 0 : 4, real.getCurrentPassengerCarUnits(link));
                assertEquals(pcu == 0 ? 0 : 2, real.getAuxiliaryPassengerCarUnits(link));
              }));
    }
    return tests;
  }

  @Test
  void maximumSupportedCountIsAcceptedForVehiclesTrafficAndProjection() {
    final RealLink real = new RealLink();
    real.setOriginNodeId(1);
    final VirtualLink link = movingLink(real, 1, 2);
    link.setNbGroups(1, 1);
    link.addVolume((byte) 0, Integer.MAX_VALUE);
    link.addAuxiliaryVolume((byte) 0, Integer.MAX_VALUE);
    link.volumesToVehicles((byte) 0, 0, 1, 1);
    assertEquals(Integer.MAX_VALUE, link.getCurrentVehicles((byte) 0));
    assertEquals(Integer.MAX_VALUE, real.getCurrentPassengerCarUnits(link));
    assertEquals(Integer.MAX_VALUE, real.getAuxiliaryPassengerCarUnits(link));
    real.resetPassengerCarUnits();
    link.projectedVolumesToVehicles((byte) 0, 0, 1, 1, 0.5);
    assertEquals(Integer.MAX_VALUE, real.getCurrentPassengerCarUnits(link));
  }

  @Test
  void positiveVolumeStillRequiresOneVehicleWhenDivisionUnderflows() {
    final RealLink real = new RealLink();
    real.setOriginNodeId(1);
    final VirtualLink link = movingLink(real, 1, 2);
    link.setNbGroups(1, 1);
    link.addVolume((byte) 0, Double.MIN_VALUE);
    link.volumesToVehicles((byte) 0, 0, Double.MAX_VALUE, 1);
    assertEquals(1, link.getCurrentVehicles((byte) 0));
    assertEquals(1, real.getCurrentPassengerCarUnits(link));
  }

  private static void assertRejectedConversion(
      boolean projected,
      double volume,
      double auxiliaryVolume,
      double capacity,
      double pcu,
      double lambda) {
    final RealLink real = new RealLink();
    real.setOriginNodeId(1);
    final VirtualLink link = movingLink(real, 1, 2);
    link.setNbGroups(1, 1);
    link.addVolume((byte) 0, 3);
    link.addAuxiliaryVolume((byte) 0, 2);
    link.volumesToVehicles((byte) 0, 0, 1, 1);
    link.addVolume((byte) 0, volume - 3);
    link.addAuxiliaryVolume((byte) 0, auxiliaryVolume - 2);
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          if (projected) {
            link.projectedVolumesToVehicles((byte) 0, (byte) 0, capacity, pcu, lambda);
          } else {
            link.volumesToVehicles((byte) 0, 0, capacity, pcu);
          }
        });
    assertEquals(3, link.getCurrentVehicles((byte) 0));
    assertEquals(3, real.getCurrentPassengerCarUnits(link));
    assertEquals(2, real.getAuxiliaryPassengerCarUnits(link));
    assertEquals(volume, link.getCurrentVolume((byte) 0));
    assertEquals(auxiliaryVolume, link.getAuxiliaryVolume((byte) 0));
  }

  private static Path path(double share) {
    Path path = new Path();
    path.marketShare = share;
    return path;
  }

  private static VirtualLink movingLink(RealLink real, int from, int to) {
    VirtualNode begin = new VirtualNode(from, from, 17, (byte) 1, (byte) 1, (short) 0, 0, 0);
    VirtualNode end = new VirtualNode(to, to, 17, (byte) 1, (byte) 1, (short) 0, 0, 0);
    return new VirtualLink(1, 0, 0, begin, end, real);
  }
}
