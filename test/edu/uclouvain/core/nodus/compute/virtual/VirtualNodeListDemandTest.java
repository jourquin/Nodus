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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks demand conservation and isolation between assignment groups, classes and loading nodes.
 */
class VirtualNodeListDemandTest {
  @Test
  void duplicateStaticDemandCombinesWithoutMixingGroupsOrDestinations() {
    VirtualNodeList node = loadingNode();
    node.addDemand(new ODCell(1, 10, 20, 30));
    node.addDemand(new ODCell(1, 10, 20, 15));
    node.addDemand(new ODCell(1, 10, 30, 7));
    node.addDemand(new ODCell(2, 10, 20, 9));

    List<ODCell> groupOne = node.getDemandForGroup(1, (byte) 0);
    assertNotNull(groupOne);
    assertEquals(2, groupOne.size());
    assertEquals(52, totalQuantity(groupOne), 1e-12);
    assertEquals(9, totalQuantity(node.getDemandForGroup(2, (byte) 0)), 1e-12);
    assertEquals(61, totalQuantity(node.getInitialDemandList()), 1e-12);
  }

  @Test
  void timedDemandCombinesOnlyWithinTheSameDeparture() {
    VirtualNodeList node = loadingNode();
    node.addDemand(new ODCell(1, 10, 20, 30, 60));
    node.addDemand(new ODCell(1, 10, 20, 15, 60));
    node.addDemand(new ODCell(1, 10, 20, 20, 90));

    List<ODCell> demand = node.getDemandForGroup(1, (byte) 0);
    assertNotNull(demand);
    assertEquals(2, demand.size());
    assertEquals(
        45,
        demand
            .stream()
            .filter(cell -> cell.getStartingTime() == 3600)
            .mapToDouble(ODCell::getQuantity)
            .sum(),
        1e-12);
    assertEquals(
        20,
        demand
            .stream()
            .filter(cell -> cell.getStartingTime() == 5400)
            .mapToDouble(ODCell::getQuantity)
            .sum(),
        1e-12);
  }

  @Test
  void staticDemandWithDifferentOdClassesRemainsSeparate() {
    VirtualNodeList node = loadingNode();
    node.addDemand(new ODCell(1, 10, 20, 30, (byte) 1));
    node.addDemand(new ODCell(1, 10, 20, 40, (byte) 2));
    node.addDemand(new ODCell(1, 10, 20, 5, (byte) 1));

    assertClassQuantities(node);
  }

  @Test
  void timedDemandWithDifferentOdClassesRemainsSeparate() {
    VirtualNodeList node = loadingNode();
    node.addDemand(new ODCell(1, 10, 20, 30, 60, (byte) 1));
    node.addDemand(new ODCell(1, 10, 20, 40, 60, (byte) 2));
    node.addDemand(new ODCell(1, 10, 20, 5, 60, (byte) 1));

    assertClassQuantities(node);
  }

  @Test
  void relocatedDemandStaysWithItsLoadingNodeAndReusesThatList() {
    VirtualNodeList node = loadingNode();
    node.addDemand(new ODCell(1, 10, 20, 7, 60));
    node.relocateDemand(new ODCell(1, 10, 20, 30, 60), 201);
    node.relocateDemand(new ODCell(1, 10, 20, 40, 60), 202);
    node.relocateDemand(new ODCell(1, 10, 20, 5, 60), 201);

    assertEquals(3, node.getNbDemandLists());
    assertEquals(100, node.getLoadingVirtualNodeId(0));
    assertEquals(201, node.getLoadingVirtualNodeId(1));
    assertEquals(202, node.getLoadingVirtualNodeId(2));
    assertEquals(7, totalQuantity(node.getInitialDemandList()), 1e-12);
    assertEquals(35, totalQuantity(node.getDemandForGroup(1, 1, (byte) 0)), 1e-12);
    assertEquals(40, totalQuantity(node.getDemandForGroup(2, 1, (byte) 0)), 1e-12);
  }

  @Test
  void relocationToTransitNodePreservesOriginalAndUpdatedDepartureInformation() {
    final VirtualNodeList origin = loadingNode();
    final VirtualNodeList transit = new VirtualNodeList(15, NodusC.HANDLING_NONE, null);
    final ODCell demand = new ODCell(1, 10, 20, 30, 60, (byte) 2);
    origin.addDemand(demand);
    demand.setRelocatedOriginNodeId(15);
    demand.setRelocatedStartingTime(3900);
    transit.relocateDemand(demand, 201);
    origin.removeDemand(0, demand);

    assertFalse(origin.hasDemandForGroup(1, (byte) 2));
    assertTrue(transit.hasDemandForGroup(1, (byte) 2));
    assertEquals(1, transit.getNbDemandLists());
    ODCell relocated = transit.getDemandForGroup(0, 1, (byte) 2).getFirst();
    assertSame(demand, relocated);
    assertEquals(30, relocated.getQuantity(), 1e-12);
    assertEquals(10, relocated.getOriginNodeId());
    assertEquals(15, relocated.getRelocatedOriginNodeId());
    assertEquals(3600, relocated.getStartingTime());
    assertEquals(3900, relocated.getRelocatedStartingTime());
  }

  @Test
  void removingDemandOnlyAffectsTheSpecifiedListAndUpdatesAvailability() {
    final VirtualNodeList node = loadingNode();
    final ODCell first = new ODCell(1, 10, 20, 30, 60);
    final ODCell second = new ODCell(1, 10, 20, 40, 60);
    node.relocateDemand(first, 201);
    node.relocateDemand(second, 202);
    node.removeDemand(2, first);
    assertEquals(40, totalQuantity(node.getDemandForGroup(2, 1, (byte) 0)), 1e-12);
    node.removeDemand(1, first);
    assertNull(node.getDemandForGroup(1, 1, (byte) 0));
    assertTrue(node.hasDemandForGroup(1, (byte) 0));
    node.removeDemand(2, second);
    assertFalse(node.hasDemandForGroup(1, (byte) 0));
    node.removeDemand(2, second);
    assertNull(node.getDemandForGroup(2, 1, (byte) 0));
  }

  @Test
  void changingReturnedDemandListDoesNotRemoveStoredDemand() {
    VirtualNodeList node = loadingNode();
    node.addDemand(new ODCell(1, 10, 20, 30));
    node.getDemandForGroup(1, (byte) 0).clear();
    node.getInitialDemandList().clear();
    assertTrue(node.hasDemandForGroup(1, (byte) 0));
    assertEquals(30, totalQuantity(node.getDemandForGroup(1, (byte) 0)), 1e-12);
  }

  private static VirtualNodeList loadingNode() {
    VirtualNodeList node = new VirtualNodeList(10, NodusC.HANDLING_LOAD_UNLOAD, null);
    node.setLoadingVirtualNodeNum(100);
    return node;
  }

  private static double totalQuantity(List<ODCell> cells) {
    assertNotNull(cells);
    return cells.stream().mapToDouble(ODCell::getQuantity).sum();
  }

  private static void assertClassQuantities(VirtualNodeList node) {
    assertEquals(35, totalQuantity(node.getDemandForGroup(1, (byte) 1)), 1e-12);
    assertEquals(40, totalQuantity(node.getDemandForGroup(1, (byte) 2)), 1e-12);
    assertTrue(node.hasDemandForGroup(1, (byte) 1));
    assertTrue(node.hasDemandForGroup(1, (byte) 2));
    assertFalse(node.hasDemandForGroup(1, (byte) 3));
    assertEquals(75, totalQuantity(node.getInitialDemandList()), 1e-12);
  }
}
