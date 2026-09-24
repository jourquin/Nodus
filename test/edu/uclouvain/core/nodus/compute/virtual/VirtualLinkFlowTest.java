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

import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import org.junit.jupiter.api.Test;

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
