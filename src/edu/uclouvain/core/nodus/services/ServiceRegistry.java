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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Services ordered by name for the editor and indexed by numeric ID for assignment lookups. Edits
 * invalidate the ID index; the next lookup rebuilds it once. The index holds service references, so
 * changes to frequencies, means and stops are visible immediately.
 *
 * <p>As with the editable services themselves, edits must not overlap assignment reads.
 */
final class ServiceRegistry {
  private final TreeMap<String, TransportService> byName = new TreeMap<>();

  /** Published together so concurrent assignment readers see a fully built index. */
  private volatile Map<Integer, List<TransportService>> byId;

  TransportService get(String name) {
    return byName.get(name);
  }

  TransportService getById(int id) {
    List<TransportService> matches = idIndex().get(id);
    return matches == null ? null : matches.get(0);
  }

  boolean containsStop(int id, int nodeId) {
    List<TransportService> matches = idIndex().get(id);
    if (matches != null) {
      for (TransportService service : matches) {
        if (service.contains(nodeId)) {
          return true;
        }
      }
    }
    return false;
  }

  private Map<Integer, List<TransportService>> idIndex() {
    Map<Integer, List<TransportService>> current = byId;
    if (current == null) {
      current = new HashMap<>();
      for (TransportService service : byName.values()) {
        // Normally IDs are unique. Keep name order and stop membership even for duplicate IDs.
        current.computeIfAbsent(service.getId(), key -> new ArrayList<>(1)).add(service);
      }
      byId = current;
    }
    return current;
  }

  void put(String name, TransportService service) {
    byName.put(name, service);
    byId = null;
  }

  void remove(String name) {
    byName.remove(name);
    byId = null;
  }

  void clear() {
    byName.clear();
    byId = null;
  }

  boolean isEmpty() {
    return byName.isEmpty();
  }

  Collection<TransportService> values() {
    return Collections.unmodifiableCollection(byName.values());
  }

  Set<Map.Entry<String, TransportService>> entrySet() {
    return Collections.unmodifiableMap(byName).entrySet();
  }

  Iterator<String> namesIterator() {
    Iterator<String> delegate = byName.keySet().iterator();
    return new Iterator<String>() {
      public boolean hasNext() {
        return delegate.hasNext();
      }

      public String next() {
        return delegate.next();
      }

      public void remove() {
        delegate.remove();
        byId = null;
      }
    };
  }

  /** Replaces a service by ID or name, including when an existing service is renamed. */
  void save(TransportService service) {
    byName
        .entrySet()
        .removeIf(
            entry ->
                entry.getKey().equals(service.getName())
                    || entry.getValue().getId() == service.getId());
    put(service.getName(), service);
  }
}
