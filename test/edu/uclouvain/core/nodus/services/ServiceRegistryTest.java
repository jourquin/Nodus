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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Protects assignment lookups after services are edited, renamed or removed. */
class ServiceRegistryTest {
  @Test
  void replacingANameRemovesThePreviousServiceFromIdAndStopLookups() {
    ServiceRegistry registry = new ServiceRegistry();
    registry.put("Route", service(1, "Route", 10));
    assertTrue(registry.containsStop(1, 10));
    TransportService replacement = service(2, "Route", 20);
    registry.put("Route", replacement);
    assertNull(registry.getById(1));
    assertFalse(registry.containsStop(1, 10));
    assertSame(replacement, registry.getById(2));
    assertTrue(registry.containsStop(2, 20));
  }

  @Test
  void duplicateIdsUseNameOrderAndAllMatchingServicesContributeStops() {
    final ServiceRegistry registry = new ServiceRegistry();
    final TransportService alpha = service(1, "Alpha", 10);
    final TransportService zulu = service(1, "Zulu", 20);
    registry.put("Zulu", zulu);
    registry.put("Alpha", alpha);
    assertSame(alpha, registry.getById(1));
    assertTrue(registry.containsStop(1, 10));
    assertTrue(registry.containsStop(1, 20));
    assertEquals(List.of(alpha, zulu), new ArrayList<>(registry.values()));
    registry.remove("Alpha");
    assertSame(zulu, registry.getById(1));
    assertFalse(registry.containsStop(1, 10));
    assertTrue(registry.containsStop(1, 20));
  }

  @Test
  void savingARenamedServiceReplacesOldIdAndConflictingNameEntries() {
    final ServiceRegistry registry = new ServiceRegistry();
    registry.put("Old", service(1, "Old", 10));
    registry.put("New", service(2, "New", 20));
    final TransportService retained = service(3, "Retained", 30);
    registry.put("Retained", retained);
    assertTrue(registry.containsStop(1, 10));
    TransportService renamed = service(1, "New", 40);
    registry.save(renamed);

    assertNull(registry.get("Old"));
    assertNull(registry.getById(2));
    assertSame(renamed, registry.get("New"));
    assertSame(renamed, registry.getById(1));
    assertFalse(registry.containsStop(1, 10));
    assertTrue(registry.containsStop(1, 40));
    assertSame(retained, registry.getById(3));
    assertEquals(2, registry.values().size());
  }

  @Test
  void removingThroughTheNameIteratorInvalidatesExistingIdLookups() {
    final ServiceRegistry registry = new ServiceRegistry();
    registry.put("Alpha", service(1, "Alpha", 10));
    registry.put("Zulu", service(2, "Zulu", 20));
    assertTrue(registry.containsStop(1, 10));
    Iterator<String> names = registry.namesIterator();
    assertEquals("Alpha", names.next());
    names.remove();
    assertNull(registry.getById(1));
    assertFalse(registry.containsStop(1, 10));
    assertEquals("Zulu", names.next());
    assertTrue(registry.containsStop(2, 20));
  }

  @Test
  void editingARegisteredServicesStopsIsVisibleWithoutSavingAgain() {
    final TransportService service = service(1, "Route", 10);
    ServiceRegistry registry = new ServiceRegistry();
    registry.save(service);
    assertTrue(registry.containsStop(1, 10));
    service.getStopNodes().set(0, 20);
    assertFalse(registry.containsStop(1, 10));
    assertTrue(registry.containsStop(1, 20));
    service.addStop(30);
    assertTrue(registry.containsStop(1, 30));
  }

  @Test
  void exposedCollectionsCannotBypassIndexInvalidation() {
    final ServiceRegistry registry = new ServiceRegistry();
    final TransportService original = service(1, "Route", 10);
    registry.save(original);
    assertSame(original, registry.getById(1));
    assertThrows(UnsupportedOperationException.class, () -> registry.values().clear());
    final Map.Entry<String, TransportService> entry = registry.entrySet().iterator().next();
    assertThrows(
        UnsupportedOperationException.class, () -> entry.setValue(service(2, "Other", 20)));
    assertThrows(UnsupportedOperationException.class, () -> registry.entrySet().clear());
    assertSame(original, registry.getById(1));
    assertNull(registry.getById(2));
  }

  @Test
  void clearDiscardsTheIndexBeforeTheNextProjectIsLoaded() {
    ServiceRegistry registry = new ServiceRegistry();
    registry.save(service(1, "Old", 10));
    assertTrue(registry.containsStop(1, 10));
    registry.clear();
    assertTrue(registry.isEmpty());
    assertNull(registry.getById(1));
    registry.save(service(1, "New", 20));
    assertFalse(registry.containsStop(1, 10));
    assertTrue(registry.containsStop(1, 20));
  }

  private static TransportService service(int id, String name, int stop) {
    TransportService service = new TransportService(id);
    service.setName(name);
    service.addStop(stop);
    return service;
  }
}
