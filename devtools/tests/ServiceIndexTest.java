package edu.uclouvain.core.nodus.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Standalone checks using in-memory services, without a project, database or GUI. */
public final class ServiceIndexTest {
  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static TransportService service(int id, String name, int... stops) {
    TransportService service = new TransportService(id, name, (byte) 1, (byte) 2, id * 10);
    for (int stop : stops) {
      service.addStop(stop);
    }
    return service;
  }

  private static void checkStops(TransportService service, LinkedList<Integer> expected) {
    LinkedList<Integer> actual = service.getStopNodes();
    check(actual.equals(expected), "Stop order or contents changed: " + actual + " vs " + expected);
    check(actual.contains(null) == expected.contains(null), "Null membership differs");
    for (int node = -1; node <= 25; node++) {
      check(service.contains(node) == expected.contains(node), "Stale stop index for node " + node);
    }
  }

  private static void checkStopEdits() throws Exception {
    TransportService service = service(1, "Bus", 3, 1, 3, 2);
    checkStops(service, new LinkedList<>(Arrays.asList(3, 1, 2)));
    service.removeStop(1);
    checkStops(service, new LinkedList<>(Arrays.asList(3, 2)));
    service.setStops(new LinkedList<>(Arrays.asList(null, 5, 4, 5)));
    checkStops(service, new LinkedList<>(Arrays.asList(5, 4)));
    service.setStops(service.getStopNodes());
    checkStops(service, new LinkedList<>(Arrays.asList(5, 4)));
    service.setStops(null);
    checkStops(service, new LinkedList<>());

    // Compare direct edits through the existing live LinkedList API with an ordinary LinkedList.
    LinkedList<Integer> actual = service.getStopNodes();
    LinkedList<Integer> expected = new LinkedList<>();
    Random random = new Random(415932L);
    for (int step = 0; step < 3000; step++) {
      int node = random.nextInt(21);
      int position = expected.isEmpty() ? 0 : random.nextInt(expected.size());
      switch (step % 15) {
        case 0:
          actual.add(node);
          expected.add(node);
          break;
        case 1:
          actual.addFirst(node);
          expected.addFirst(node);
          break;
        case 2:
          actual.offerLast(node);
          expected.offerLast(node);
          break;
        case 3:
          actual.addAll(position, Arrays.asList(node, node));
          expected.addAll(position, Arrays.asList(node, node));
          break;
        case 4:
          if (!expected.isEmpty()) {
            actual.set(position, node);
            expected.set(position, node);
          }
          break;
        case 5:
          if (!expected.isEmpty()) {
            ListIterator<Integer> a = actual.listIterator(position);
            ListIterator<Integer> e = expected.listIterator(position);
            a.next();
            e.next();
            a.set(node);
            e.set(node);
          }
          break;
        case 6:
          if (!expected.isEmpty()) {
            Iterator<Integer> a = actual.iterator();
            Iterator<Integer> e = expected.iterator();
            a.next();
            e.next();
            a.remove();
            e.remove();
          }
          break;
        case 7:
          if (!expected.isEmpty()) {
            Iterator<Integer> a = actual.descendingIterator();
            Iterator<Integer> e = expected.descendingIterator();
            a.next();
            e.next();
            a.remove();
            e.remove();
          }
          break;
        case 8:
          actual.remove((Integer) node);
          expected.remove((Integer) node);
          break;
        case 9:
          if (!expected.isEmpty()) {
            actual.subList(position, actual.size()).set(0, node);
            expected.subList(position, expected.size()).set(0, node);
          }
          break;
        case 10:
          actual.replaceAll(value -> value % 7);
          expected.replaceAll(value -> value % 7);
          break;
        case 11:
          actual.sort(Collections.reverseOrder());
          expected.sort(Collections.reverseOrder());
          break;
        case 12:
          actual.removeIf(value -> value % 2 == 0);
          expected.removeIf(value -> value % 2 == 0);
          break;
        case 13:
          actual.listIterator(position).add(node);
          expected.listIterator(position).add(node);
          break;
        case 14:
          actual.subList(0, actual.size()).clear();
          expected.subList(0, expected.size()).clear();
          break;
        default:
          throw new AssertionError();
      }
      checkStops(service, expected);
    }
    actual.addAll(Arrays.asList(1, 1, null, 2));
    expected.addAll(Arrays.asList(1, 1, null, 2));
    service.removeStop(1);
    expected.remove((Integer) 1);
    checkStops(service, expected);
    actual.clear();
    expected.clear();
    checkStops(service, expected);

    service.addStop(7);
    @SuppressWarnings("unchecked")
    LinkedList<Integer> copy = (LinkedList<Integer>) actual.clone();
    copy.add(8);
    service.addStop(9);
    check(!actual.contains(8) && !copy.contains(9), "Cloned lists share a stop index");

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(actual);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      @SuppressWarnings("unchecked")
      LinkedList<Integer> restored = (LinkedList<Integer>) in.readObject();
      check(restored.equals(actual) && restored.contains(9), "Serialized list lost its stops");
      restored.remove((Integer) 9);
      check(!restored.contains(9) && actual.contains(9), "Restored index is stale or shared");
    }
  }

  private static void checkRegistryEdits() {
    ServiceRegistry services = new ServiceRegistry();
    check(services.getById(1) == null && !services.containsStop(1, 7), "Missing service found");
    TransportService bus = service(1, "Bus", 7, 8);
    services.put(bus.getName(), bus);
    check(services.getById(1) == bus && services.containsStop(1, 7), "Loaded service missing");
    bus.setFrequency(365);
    bus.setMeans(TransportService.ALL_MEANS);
    check(services.getById(1).getFrequency() == 365, "Frequency edit is stale");
    check(services.getById(1).getMeans() == TransportService.ALL_MEANS, "Means edit is stale");
    bus.removeStop(7);
    bus.addStop(9);
    check(!services.containsStop(1, 7) && services.containsStop(1, 9), "Stop edit is stale");
    bus.setStops(new LinkedList<>(Arrays.asList(10, 11)));
    check(!services.containsStop(1, 9) && services.containsStop(1, 10), "Stop replacement stale");

    bus.setName("Express");
    services.save(bus);
    check(services.get("Bus") == null && services.get("Express") == bus, "Rename left old name");
    check(services.getById(1) == bus, "Rename lost ID");
    TransportService replacement = service(1, "New express", 12);
    services.save(replacement);
    check(services.get("Express") == null, "Replacing ID left old name");
    check(!services.containsStop(1, 10) && services.containsStop(1, 12), "Replacing ID is stale");
    TransportService renamedId = service(2, "New express", 13);
    services.save(renamedId);
    check(
        services.getById(1) == null && services.getById(2) == renamedId, "Replacing name is stale");
    services.put("New express", replacement);
    check(
        services.getById(2) == null && services.getById(1) == replacement, "Load overwrite stale");
    services.remove("New express");
    check(services.getById(1) == null && !services.containsStop(1, 12), "Removed service remains");

    services.put("Z", service(2, "Z", 2));
    services.put("A", service(1, "A", 1));
    Iterator<String> names = services.namesIterator();
    check(names.next().equals("A"), "Names no longer sorted");
    check(services.getById(1) != null, "Service missing before iterator removal");
    names.remove();
    check(services.getById(1) == null && names.next().equals("Z"), "Iterator removal is stale");
    services.clear();
    check(services.isEmpty() && services.getById(2) == null, "Cleared service remains");
    services.put("Reloaded", service(2, "Reloaded", 20));
    check(!services.containsStop(2, 2) && services.containsStop(2, 20), "Reload reused old stops");
  }

  private static void checkAgainstLinearLookups() throws Exception {
    ServiceRegistry services = new ServiceRegistry();
    TreeMap<String, TransportService> reference = new TreeMap<>();
    for (int i = 0; i < 400; i++) {
      // Duplicate IDs also exercise the old first-name frequency and any-matching-service stop
      // rules.
      TransportService service = service(i % 350, "Service " + i, i % 23, (i + 5) % 23);
      services.put(service.getName(), service);
      reference.put(service.getName(), service);
    }
    for (int id = -1; id <= 400; id++) {
      TransportService expected = null;
      for (TransportService service : reference.values()) {
        if (service.getId() == id) {
          expected = service;
          break;
        }
      }
      check(services.getById(id) == expected, "ID lookup differs from linear lookup for " + id);
      for (int node = -1; node <= 24; node++) {
        boolean stop = false;
        for (TransportService service : reference.values()) {
          // Copy into an ordinary list so the reference membership check is independent of the
          // index.
          if (new LinkedList<>(service.getStopNodes()).contains(node) && service.getId() == id) {
            stop = true;
            break;
          }
        }
        check(
            services.containsStop(id, node) == stop, "Stop lookup differs for " + id + "/" + node);
      }
    }

    // Start readers with invalidated indexes, as at the beginning of a new assignment.
    TransportService added = service(500, "Added", 20);
    services.put(added.getName(), added);
    added.getStopNodes().addFirst(21);
    ExecutorService workers = Executors.newFixedThreadPool(4);
    try {
      ArrayList<Callable<Boolean>> tasks = new ArrayList<>();
      for (int worker = 0; worker < 4; worker++) {
        tasks.add(
            () -> {
              for (int i = 0; i < 10000; i++) {
                if (services.getById(500) != added
                    || !services.containsStop(500, 21)
                    || services.containsStop(500, -1)) {
                  return false;
                }
              }
              return true;
            });
      }
      for (Future<Boolean> result : workers.invokeAll(tasks)) {
        check(result.get(), "Concurrent assignment read saw an incomplete index");
      }
    } finally {
      workers.shutdownNow();
    }
  }

  public static void main(String[] args) throws Exception {
    checkStopEdits();
    checkRegistryEdits();
    checkAgainstLinearLookups();
    System.out.println("Service indexing checks passed.");
  }
}
