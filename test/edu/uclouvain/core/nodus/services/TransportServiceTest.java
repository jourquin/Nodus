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

package edu.uclouvain.core.nodus.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import org.junit.jupiter.api.Test;

/** Checks live service editing without leaving stale stop-membership information. */
class TransportServiceTest {
  @Test
  void importingStopsPreservesOrderAndRemovesDuplicatesAndNulls() {
    final LinkedList<Integer> input = new LinkedList<>(Arrays.asList(30, null, 10, 30, 20));
    TransportService service = new TransportService(1);
    service.setStops(input);
    assertEquals(List.of(30, 10, 20), service.getStopNodes());
    assertTrue(service.contains(30));
    input.clear();
    assertEquals(3, service.getNbStops());
    service.addStop(10);
    assertEquals(List.of(30, 10, 20), service.getStopNodes());
    service.setStops(null);
    assertFalse(service.contains(30));
    assertEquals(0, service.getNbStops());
  }

  @Test
  void replacingStopsThroughLiveListInvalidatesMembership() {
    TransportService service = serviceWithStops();
    assertTrue(service.contains(10));
    service.getStopNodes().set(0, 40);
    assertFalse(service.contains(10));
    assertTrue(service.contains(40));
    service.addStop(10);
    assertEquals(List.of(40, 20, 30, 10), service.getStopNodes());
  }

  @Test
  void listIteratorReplacementRemovalAndInsertionUpdateMembership() {
    final TransportService service = serviceWithStops();
    assertTrue(service.contains(10));
    ListIterator<Integer> iterator = service.getStopNodes().listIterator();
    assertEquals(10, iterator.next());
    iterator.set(40);
    assertFalse(service.contains(10));
    assertTrue(service.contains(40));
    iterator.add(50);
    assertTrue(service.contains(50));
    assertEquals(20, iterator.next());
    iterator.remove();
    assertFalse(service.contains(20));
    assertEquals(List.of(40, 50, 30), service.getStopNodes());
  }

  @Test
  void removingOneDuplicateRetainsMembershipUntilTheLastOccurrenceIsRemoved() {
    TransportService service = serviceWithStops();
    service.getStopNodes().addFirst(10);
    assertTrue(service.contains(10));
    service.removeStop(10);
    assertTrue(service.contains(10));
    service.removeStop(10);
    assertFalse(service.contains(10));
    assertEquals(List.of(20, 30), service.getStopNodes());
  }

  @Test
  void bulkEditsThroughSublistAndReplaceAllKeepMembershipAccurate() {
    TransportService service = serviceWithStops();
    assertTrue(service.contains(20));
    service.getStopNodes().subList(1, 3).clear();
    assertFalse(service.contains(20));
    assertFalse(service.contains(30));
    service.getStopNodes().addAll(List.of(40, 50));
    assertTrue(service.contains(40));
    service.getStopNodes().replaceAll(node -> node + 1);
    assertFalse(service.contains(10));
    assertFalse(service.contains(40));
    assertTrue(service.contains(11));
    assertTrue(service.contains(41));
    service.getStopNodes().removeIf(node -> node > 20);
    assertFalse(service.contains(41));
    assertEquals(List.of(11), service.getStopNodes());
  }

  @Test
  @SuppressWarnings("unchecked")
  void cloningTheLiveStopListDoesNotShareItsMembershipIndex() {
    TransportService service = serviceWithStops();
    assertTrue(service.contains(10));
    LinkedList<Integer> copy = (LinkedList<Integer>) service.getStopNodes().clone();
    assertTrue(copy.contains(10));
    copy.add(40);
    copy.remove(Integer.valueOf(10));
    assertTrue(copy.contains(40));
    assertFalse(copy.contains(10));
    assertFalse(service.contains(40));
    assertTrue(service.contains(10));
    service.addStop(50);
    assertFalse(copy.contains(50));
  }

  @Test
  void routeReplacementCopiesInputAndPreservesRepeatedLinksAndDirectionOrder() {
    final OMGraphic first = new OMPoint(0, 0);
    final OMGraphic second = new OMPoint(1, 1);
    final OMGraphic before = new OMPoint(2, 2);
    final LinkedList<OMGraphic> route = new LinkedList<>(Arrays.asList(first, null, second, first));
    TransportService service = new TransportService(1);
    service.setChunks(route);
    route.clear();
    service.addFirstChunk(before);
    assertEquals(List.of(before, first, second, first), service.getLinks());
    service.removeChunk(first);
    assertTrue(service.contains(first));
    assertEquals(List.of(before, second, first), service.getLinks());
    service.setChunks(null);
    assertFalse(service.contains(first));
    assertEquals(0, service.getNbLinks());
  }

  private static TransportService serviceWithStops() {
    TransportService service = new TransportService(1);
    service.setStops(new LinkedList<>(List.of(10, 20, 30)));
    return service;
  }
}
