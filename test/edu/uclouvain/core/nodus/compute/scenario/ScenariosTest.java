/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * This file is part of Nodus, distributed under the GNU General Public License, version 3
 * or (at your option) any later version. See http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.compute.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.ResourceLock;

@Tag("integration")
@ResourceLock("JDBCUtils")
class ScenariosTest {
  private static final List<String> DESCRIPTORS =
      List.of(
          "node1",
          "link1",
          "mode1",
          "means1",
          "service1",
          "node2",
          "link2",
          "mode2",
          "means2",
          "service2",
          "time");

  @FunctionalInterface
  private interface Example {
    void check(Fixture fixture, boolean compare) throws Exception;
  }

  private Stream<DynamicTest> examples(String label, Example example) {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(false, true)
                    .map(
                        compare ->
                            DynamicTest.dynamicTest(
                                (hsql ? "HSQLDB " : "H2 ")
                                    + (compare ? "compare " : "sum ")
                                    + label,
                                () -> {
                                  try (Fixture f = new Fixture(hsql)) {
                                    example.check(f, compare);
                                    f.assertClean();
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> matchingAndDisjointLinksUseNamedCommodityColumns() {
    return examples(
        "commodity union and costs",
        (f, compare) -> {
          final Row common = row(1, 2, 30);
          final Row firstOnly = row(3, 4, 30);
          final Row secondOnly = row(5, 6, 30);
          f.source(
              1,
              false,
              true,
              new int[] {3, 1},
              common.copy().group(3, 8, 30, 3).group(1, 2, 10, 1),
              firstOnly.copy().group(1, 5, 6, 1).group(3, 3, 4, 2));
          f.source(
              2,
              false,
              false,
              new int[] {2, 3},
              common.copy().length("2.750").group(2, 9, 20, 2).group(3, 4, 10, 1),
              secondOnly.copy().group(2, 3, 4, 1).group(3, 6, 8, 2));
          final List<String> before1 = f.snapshot(f.table(1));
          final List<String> before2 = f.snapshot(f.table(2));
          f.run(compare, null);
          f.assertSchema(1, 2, 3);
          f.assertCount(3);
          if (compare) {
            f.assertRow(
                common,
                "-0.833",
                "10",
                1,
                new Triple(1, 2, 10, 1),
                new Triple(2, -9, -20, -2),
                new Triple(3, 4, 20, 2));
            f.assertRow(
                firstOnly,
                "4.200",
                "10",
                3,
                new Triple(1, 5, 6, 1),
                new Triple(2, 0, 0, 0),
                new Triple(3, 3, 4, 2));
            f.assertRow(
                secondOnly,
                "-5",
                "-12",
                -3,
                new Triple(1, 0, 0, 0),
                new Triple(2, -3, -4, -1),
                new Triple(3, -6, -8, -2));
          } else {
            f.assertRow(
                common,
                "6.857",
                "70",
                7,
                new Triple(1, 2, 10, 1),
                new Triple(2, 9, 20, 2),
                new Triple(3, 7, 40, 4));
            f.assertRow(
                firstOnly,
                "4.200",
                "10",
                3,
                new Triple(1, 5, 6, 1),
                new Triple(2, 0, 0, 0),
                new Triple(3, 3, 4, 2));
            f.assertRow(
                secondOnly,
                "5",
                "12",
                3,
                new Triple(1, 0, 0, 0),
                new Triple(2, 3, 4, 1),
                new Triple(3, 6, 8, 2));
          }
          assertEquals(before1, f.snapshot(f.table(1)));
          assertEquals(before2, f.snapshot(f.table(2)));
        });
  }

  @TestFactory
  Stream<DynamicTest> fullDescriptorsKeepOperationsDirectionsAndPeriodsSeparate() {
    return examples(
        "complete link identity",
        (f, compare) -> {
          final Row base = row(1, 2, 30);
          final List<Row> links = new ArrayList<>();
          links.add(base);
          for (int descriptor = 0; descriptor < DESCRIPTORS.size(); descriptor++) {
            links.add(base.copy().descriptor(descriptor, base.descriptors[descriptor] + 1));
          }
          links.add(base.copy().descriptor(0, -1).descriptor(5, -2));
          for (int type = 1; type <= 6; type++) {
            links.add(base.copy().type(type));
          }
          // These two descriptors collided with the old concatenated, minimum-width string key.
          links.add(row(123456789, 2, 30).descriptor(1, 12345678));
          links.add(row(12345678, 2, 30).descriptor(1, 912345678));
          links.add(row(3000000000L, 4000000000L, 300).descriptor(1, 3000000000L));
          f.source(
              1,
              false,
              false,
              new int[] {1},
              links.stream().map(r -> r.copy().group(1, 10, 2, 3000000000L)).toArray(Row[]::new));
          f.source(
              2,
              false,
              true,
              new int[] {1},
              links
                  .stream()
                  .map(r -> r.copy().length("9.125").group(1, 4, 1, 4000000000L))
                  .toArray(Row[]::new));
          f.run(compare, null);
          f.assertCount(links.size());
          for (Row link : links) {
            f.assertRow(
                link,
                compare ? "6" : "8",
                compare ? "1" : "3",
                compare ? -1000000000L : 7000000000L,
                new Triple(
                    1, compare ? 6 : 8, compare ? 1 : 3, compare ? -1000000000L : 7000000000L));
          }
          assertEquals(0, f.stopLookups);
        });
  }

  @TestFactory
  Stream<DynamicTest> currentAndLegacySchemasInferAllOperationTypes() {
    return Stream.of(false, true)
        .flatMap(
            firstLegacy ->
                Stream.of(false, true)
                    .flatMap(
                        secondLegacy ->
                            examples(
                                "legacy=" + firstLegacy + "/" + secondLegacy,
                                (f, compare) -> {
                                  final Row[] links = {
                                    row(1, 2, 30),
                                    row(3, 3, 30).descriptor(2, 0).type(VirtualLink.TYPE_LOAD),
                                    row(4, 4, 30).descriptor(7, 0).type(VirtualLink.TYPE_UNLOAD),
                                    row(5, -5, 30).type(VirtualLink.TYPE_TRANSIT),
                                    row(6, 6, 30).descriptor(8, 3).type(VirtualLink.TYPE_TRANSHIP),
                                    row(7, 7, 30)
                                        .descriptor(4, 10)
                                        .descriptor(9, 11)
                                        .type(VirtualLink.TYPE_SWITCH),
                                    row(-7, 7, 30)
                                        .descriptor(4, 10)
                                        .descriptor(9, 10)
                                        .type(VirtualLink.TYPE_STOP),
                                    row(8, 8, 30)
                                        .descriptor(4, 10)
                                        .descriptor(9, 10)
                                        .type(VirtualLink.TYPE_TRANSIT)
                                  };
                                  f.source(
                                      1,
                                      firstLegacy,
                                      true,
                                      new int[] {1},
                                      Arrays.stream(links)
                                          .map(r -> r.copy().group(1, 10, 2, 2))
                                          .toArray(Row[]::new));
                                  f.source(
                                      2,
                                      secondLegacy,
                                      false,
                                      new int[] {1},
                                      Arrays.stream(links)
                                          .map(r -> r.copy().group(1, 4, 1, 1))
                                          .toArray(Row[]::new));
                                  f.run(compare, null);
                                  f.assertCount(links.length);
                                  for (Row link : links) {
                                    f.assertRow(
                                        link,
                                        compare ? "6" : "8",
                                        compare ? "1" : "3",
                                        compare ? 1 : 3,
                                        new Triple(
                                            1, compare ? 6 : 8, compare ? 1 : 3, compare ? 1 : 3));
                                  }
                                  assertEquals(
                                      (firstLegacy ? 2 : 0) + (secondLegacy ? 2 : 0),
                                      f.stopLookups);
                                })));
  }

  @TestFactory
  Stream<DynamicTest> inferredStopCannotMergeWithAnExplicitTransit() {
    return examples(
        "stop versus transit",
        (f, compare) -> {
          final Row transit =
              row(7, 7, 30).descriptor(4, 10).descriptor(9, 10).type(VirtualLink.TYPE_TRANSIT);
          f.source(1, true, false, new int[] {1}, transit.copy().group(1, 10, 2, 2));
          f.source(2, false, false, new int[] {1}, transit.copy().group(1, 4, 1, 1));
          f.run(compare, null);
          f.assertCount(2);
          f.assertRow(
              transit.copy().type(VirtualLink.TYPE_STOP), "10", "2", 2, new Triple(1, 10, 2, 2));
          f.assertRow(
              transit,
              compare ? "-4" : "4",
              compare ? "-1" : "1",
              compare ? -1 : 1,
              new Triple(1, compare ? -4 : 4, compare ? -1 : 1, compare ? -1 : 1));
        });
  }

  @TestFactory
  Stream<DynamicTest> filtersAndEmptyInputsRetainTheUnionSchema() {
    return examples(
        "filters and empty inputs",
        (f, compare) -> {
          final Row early = row(1, 2, 30).group(1, 8, 2, 1);
          final Row late = row(1, 2, 60).group(1, 4, 1, 1);
          f.source(1, false, false, new int[] {1}, early, late);
          f.source(
              2,
              false,
              true,
              new int[] {2},
              early.copy().group(2, 6, 1, 1),
              late.copy().group(2, 3, 1, 1));
          f.run(compare, "time = 30");
          f.assertCount(1);
          f.assertSchema(1, 2);
          f.assertRow(
              early,
              compare ? "2" : "7.333",
              compare ? "1" : "3",
              compare ? 0 : 2,
              new Triple(1, 8, 2, 1),
              new Triple(2, compare ? -6 : 6, compare ? -1 : 1, compare ? -1 : 1));
          f.run(compare, "1=0");
          f.assertCount(0);
          f.assertSchema(1, 2);
          f.run(compare, "  ");
          f.assertCount(2);
          f.execute("DELETE FROM " + f.quotedTable(2));
          f.run(compare, null);
          f.assertCount(2);
          f.assertSchema(1, 2);
          f.execute("DELETE FROM " + f.quotedTable(1));
          f.run(compare, null);
          f.assertCount(0);
          f.assertSchema(1, 2);
          f.execute("DROP TABLE " + f.quotedTable(2));
          f.source(
              2,
              false,
              true,
              new int[] {2},
              early.copy().group(2, 6, 1, 1),
              late.copy().group(2, 3, 1, 1));
          f.run(compare, null);
          f.assertCount(2);
          f.assertSchema(1, 2);
        });
  }

  @TestFactory
  Stream<DynamicTest> zeroAndSignedQuantitiesHaveDefinedUnitCosts() {
    return examples(
        "zero and signed weights",
        (f, compare) -> {
          final Row[] first = {
            row(1, 2, 30).group(1, 10, 5, 1),
            row(3, 4, 30).group(1, 6, -5, -2),
            row(5, 6, 30).group(1, 10, 0, 0),
            row(7, 8, 30).group(1, 10, 5, 1)
          };
          final Row[] second = {
            row(1, 2, 30).group(1, 14, 5, 1),
            row(3, 4, 30).group(1, 2, -3, -1),
            row(5, 6, 30).group(1, 14, 0, 0),
            row(7, 8, 30).group(1, 14, -5, -1)
          };
          f.source(1, false, false, new int[] {1}, first);
          f.source(2, false, false, new int[] {1}, second);
          f.run(compare, null);
          f.assertRow(
              first[0],
              compare ? "-4" : "12",
              compare ? "0" : "10",
              compare ? 0 : 2,
              new Triple(1, compare ? -4 : 12, compare ? 0 : 10, compare ? 0 : 2));
          f.assertRow(
              first[1],
              compare ? "4" : "4.5",
              compare ? "-2" : "-8",
              compare ? -1 : -3,
              new Triple(1, compare ? 4 : 4.5, compare ? -2 : -8, compare ? -1 : -3));
          f.assertRow(first[2], "0", "0", 0, new Triple(1, compare ? -4 : 0, 0, 0));
          f.assertRow(
              first[3],
              compare ? "-4" : "0",
              compare ? "10" : "0",
              compare ? 2 : 0,
              new Triple(1, compare ? -4 : 0, compare ? 10 : 0, compare ? 2 : 0));
        });
  }

  @TestFactory
  Stream<DynamicTest> overlappingScenarioIdsAreRejectedBeforeChangingAnyTable() {
    return Stream.of(new int[] {1, 1, 3}, new int[] {1, 2, 1}, new int[] {1, 2, 2})
        .flatMap(
            ids ->
                examples(
                    "colliding IDs " + Arrays.toString(ids),
                    (f, compare) -> {
                      f.seed();
                      final Map<String, List<String>> before = f.outputs();
                      assertThrows(
                          IllegalArgumentException.class,
                          () -> f.run(compare, ids[0], ids[1], ids[2], null));
                      assertEquals(before, f.outputs());
                      assertEquals(0, f.batches + f.updates);
                    }));
  }

  @TestFactory
  Stream<DynamicTest> outputFailuresPreserveInputsAndTheCompletePreviousResult() {
    return Stream.of(
            Fault.LATE_BATCH,
            Fault.BATCH_STATUS,
            Fault.NO_BATCH_FAILURE,
            Fault.CLOSE,
            Fault.RENAME,
            Fault.RENAME_AFTER,
            Fault.LATE_BACKUP_AFTER,
            Fault.COMMIT,
            Fault.COMMIT_AFTER,
            Fault.PUBLISH_COMMIT)
        .flatMap(
            fault ->
                Stream.of(false, true)
                    .filter(auto -> !auto || !fault.transactionOnly)
                    .flatMap(
                        auto ->
                            examples(
                                "failure " + fault + ", auto=" + auto,
                                (f, compare) -> {
                                  f.seed();
                                  final Map<String, List<String>> before = f.outputs();
                                  f.raw.setAutoCommit(auto);
                                  f.fault = fault;
                                  final IllegalStateException failure =
                                      assertThrows(
                                          IllegalStateException.class, () -> f.run(compare, null));
                                  assertTrue(f.triggered);
                                  assertTrue(failure.getCause() instanceof SQLException);
                                  assertEquals(before, f.outputs());
                                  // A retry must succeed after the one-shot fault, preserving both
                                  // sources' paths.
                                  f.fault = Fault.NONE;
                                  f.run(compare, null);
                                  f.assertCount(5);
                                  assertFalse(f.exists(f.header(3)));
                                  assertFalse(f.exists(f.detail(3)));
                                  for (String table :
                                      List.of(
                                          f.table(1),
                                          f.header(1),
                                          f.detail(1),
                                          f.table(2),
                                          f.header(2),
                                          f.detail(2))) {
                                    assertEquals(before.get(table), f.snapshot(table));
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> failedFirstAttemptLeavesNoPublicOutput() {
    return examples(
        "first attempt failure",
        (f, compare) -> {
          f.seed();
          f.execute("DROP TABLE " + f.quotedTable(3));
          f.execute("DROP TABLE " + quote(f.header(3)));
          f.execute("DROP TABLE " + quote(f.detail(3)));
          f.fault = Fault.LATE_BATCH;
          assertThrows(IllegalStateException.class, () -> f.run(compare, null));
          assertTrue(f.triggered);
          assertFalse(f.exists(f.table(3)));
          assertFalse(f.exists(f.header(3)));
          assertFalse(f.exists(f.detail(3)));
        });
  }

  @TestFactory
  Stream<DynamicTest> driversWithoutBatchSupportWriteEveryRow() {
    return examples(
        "non-batch driver",
        (f, compare) -> {
          f.seed();
          f.fault = Fault.NO_BATCH;
          f.run(compare, null);
          f.assertCount(5);
          assertEquals(0, f.batches);
          assertEquals(5, f.updates);
          f.assertRow(
              row(1, 2, 30),
              compare ? "6" : "8",
              compare ? "1" : "3",
              compare ? 1 : 3,
              new Triple(1, compare ? 6 : 8, compare ? 1 : 3, compare ? 1 : 3));
          assertFalse(f.exists(f.header(3)));
          assertFalse(f.exists(f.detail(3)));
        });
  }

  @TestFactory
  Stream<DynamicTest> unreadableSourcesNeverReplaceAnExistingResult() {
    return Stream.of(
            "duplicate first",
            "duplicate second",
            "incomplete group",
            "null metric",
            "missing source",
            "bad filter",
            "missing descriptor",
            "missing legacy services")
        .flatMap(
            problem ->
                examples(
                    problem,
                    (f, compare) -> {
                      f.seed();
                      String filter = null;
                      if (problem.startsWith("duplicate")) {
                        final String table = f.quotedTable(problem.endsWith("first") ? 1 : 2);
                        f.execute(
                            "INSERT INTO " + table + " SELECT * FROM " + table + " WHERE node1=1");
                      } else if (problem.equals("incomplete group")) {
                        f.execute("ALTER TABLE " + f.quotedTable(2) + " DROP COLUMN veh1");
                      } else if (problem.equals("null metric")) {
                        f.execute("UPDATE " + f.quotedTable(2) + " SET qty1=NULL WHERE node1=1");
                      } else if (problem.equals("missing source")) {
                        f.execute("DROP TABLE " + f.quotedTable(2));
                      } else if (problem.equals("bad filter")) {
                        filter = "missing_column=1";
                      } else if (problem.equals("missing descriptor")) {
                        f.execute("DELETE FROM " + f.quotedTable(2));
                        f.execute("ALTER TABLE " + f.quotedTable(2) + " DROP COLUMN node1");
                      } else {
                        f.execute("DROP TABLE " + f.quotedTable(2));
                        f.source(
                            2,
                            true,
                            false,
                            new int[] {1},
                            row(7, 7, 30).descriptor(4, 10).descriptor(9, 10).group(1, 4, 1, 1));
                        f.services = null;
                      }
                      final Map<String, List<String>> before = f.outputs();
                      final String predicate = filter;
                      assertThrows(IllegalStateException.class, () -> f.run(compare, predicate));
                      assertEquals(before, f.outputs());
                      assertEquals(0, f.batches + f.updates);
                    }));
  }

  private static Row row(long first, long second, int time) {
    return new Row(new long[] {first, 11, 1, 2, 0, second, 12, 1, 2, 0, time});
  }

  private static final class Row {
    final long[] descriptors;
    final Map<Integer, Triple> groups = new LinkedHashMap<>();
    int type;
    BigDecimal length = new BigDecimal("1.250");

    Row(long[] descriptors) {
      this.descriptors = descriptors;
    }

    Row copy() {
      Row copy = new Row(descriptors.clone());
      copy.type = type;
      copy.length = length;
      copy.groups.putAll(groups);
      return copy;
    }

    Row descriptor(int index, long value) {
      descriptors[index] = value;
      return this;
    }

    Row type(int value) {
      type = value;
      return this;
    }

    Row length(String value) {
      length = new BigDecimal(value);
      return this;
    }

    Row group(int group, double cost, double quantity, long vehicles) {
      groups.put(group, new Triple(group, cost, quantity, vehicles));
      return this;
    }
  }

  private static final class Triple {
    final int group;
    final BigDecimal cost;
    final BigDecimal quantity;
    final long vehicles;

    Triple(int group, double cost, double quantity, long vehicles) {
      this.group = group;
      this.cost = BigDecimal.valueOf(cost);
      this.quantity = BigDecimal.valueOf(quantity);
      this.vehicles = vehicles;
    }
  }

  private enum Fault {
    NONE(false),
    NO_BATCH(false),
    LATE_BATCH(false),
    BATCH_STATUS(false),
    NO_BATCH_FAILURE(false),
    CLOSE(false),
    RENAME(false),
    RENAME_AFTER(false),
    LATE_BACKUP_AFTER(false),
    COMMIT(true),
    COMMIT_AFTER(true),
    PUBLISH_COMMIT(true);

    final boolean transactionOnly;

    Fault(boolean transactionOnly) {
      this.transactionOnly = transactionOnly;
    }
  }

  /** Owns real SQL data; the proxy injects faults only at the output JDBC boundary. */
  private static final class Fixture extends NodusProject implements AutoCloseable {
    final Connection raw;
    final Connection connection;
    final List<PreparedStatement> writers = new ArrayList<>();
    ServiceHandler services;
    Fault fault = Fault.NONE;
    boolean triggered;
    int stopLookups;
    int batches;
    int updates;
    int commits;

    Fixture(boolean hsql) throws Exception {
      super(null);
      raw =
          DriverManager.getConnection(
              (hsql ? "jdbc:hsqldb:mem:" : "jdbc:h2:mem:") + "scenario_" + UUID.randomUUID(),
              "sa",
              "");
      connection =
          proxy(
              Connection.class,
              raw,
              (method, args) -> {
                if (method.getName().equals("getMetaData")) {
                  return proxy(
                      DatabaseMetaData.class,
                      raw.getMetaData(),
                      (metadataMethod, metadataArgs) -> {
                        if (metadataMethod.getName().equals("supportsBatchUpdates")
                            && (fault == Fault.NO_BATCH || fault == Fault.NO_BATCH_FAILURE)) {
                          return false;
                        }
                        return invoke(raw.getMetaData(), metadataMethod, metadataArgs);
                      });
                }
                if (method.getName().equals("prepareStatement")
                    && ((String) args[0])
                        .toUpperCase(Locale.ROOT)
                        .startsWith("INSERT INTO \"NO_")) {
                  return writer((PreparedStatement) invoke(raw, method, args));
                }
                if (method.getName().equals("createStatement")) {
                  final Statement statement = (Statement) invoke(raw, method, args);
                  return proxy(
                      Statement.class,
                      statement,
                      (sqlMethod, sqlArgs) -> {
                        if (sqlMethod.getName().equals("execute") && sqlArgs[0] instanceof String) {
                          final String sql = ((String) sqlArgs[0]).toUpperCase(Locale.ROOT);
                          final boolean installing = sql.startsWith("ALTER TABLE \"NO_");
                          final boolean lateBackup =
                              sql.startsWith("ALTER TABLE " + quote(detail(3)))
                                  && sql.contains(" RENAME TO ");
                          if (!triggered
                              && ((installing
                                      && (fault == Fault.RENAME || fault == Fault.RENAME_AFTER))
                                  || (lateBackup && fault == Fault.LATE_BACKUP_AFTER))) {
                            triggered = true;
                            if (fault != Fault.RENAME) {
                              invoke(statement, sqlMethod, sqlArgs);
                            }
                            throw new SQLException("Injected publication failure");
                          }
                        }
                        return invoke(statement, sqlMethod, sqlArgs);
                      });
                }
                if (method.getName().equals("commit")) {
                  commits++;
                  if (!triggered
                      && ((commits == 1 && (fault == Fault.COMMIT || fault == Fault.COMMIT_AFTER))
                          || (commits == 2 && fault == Fault.PUBLISH_COMMIT))) {
                    triggered = true;
                    if (fault == Fault.COMMIT_AFTER) {
                      raw.commit();
                    }
                    throw new SQLException("Injected commit failure");
                  }
                }
                return invoke(raw, method, args);
              });
      JDBCUtils.setConnection(connection);
      services =
          new ServiceHandler(this, false) {
            @Override
            public boolean isNodeStopService(int node, int service) {
              stopLookups++;
              return node == 7 && service == 10;
            }
          };
    }

    private PreparedStatement writer(PreparedStatement statement) {
      writers.add(statement);
      return proxy(
          PreparedStatement.class,
          statement,
          (method, args) -> {
            final String name = method.getName();
            if (name.equals("executeBatch")) {
              batches++;
              if (!triggered && batches == 2 && fault == Fault.LATE_BATCH) {
                triggered = true;
                throw new SQLException("Injected later batch failure");
              }
              final int[] counts = (int[]) invoke(statement, method, args);
              if (!triggered && batches == 2 && fault == Fault.BATCH_STATUS) {
                triggered = true;
                counts[0] = Statement.EXECUTE_FAILED;
              }
              return counts;
            }
            if (name.equals("executeUpdate")) {
              updates++;
              if (!triggered && updates == 3 && fault == Fault.NO_BATCH_FAILURE) {
                triggered = true;
                throw new SQLException("Injected later row failure");
              }
            }
            if (name.equals("close") && !triggered && fault == Fault.CLOSE) {
              triggered = true;
              statement.close();
              throw new SQLException("Injected statement-close failure");
            }
            return invoke(statement, method, args);
          });
    }

    @Override
    public Connection getMainJDBCConnection() {
      return connection;
    }

    @Override
    public ServiceHandler getServiceHandler() {
      return services;
    }

    @Override
    public int getSqlBatchSize() {
      return 2;
    }

    @Override
    public String getLocalProperty(String key) {
      return getLocalProperty(key, (String) null);
    }

    @Override
    public String getLocalProperty(String key, String fallback) {
      if (NodusC.PROP_PROJECT_DOTNAME.equals(key)) {
        return "scenario";
      }
      if (NodusC.PROP_VNET_TABLE.equals(key)) {
        return "scenario result_";
      }
      if (NodusC.PROP_PATH_TABLE_PREFIX.equals(key)) {
        return "result paths";
      }
      return fallback;
    }

    String table(int scenario) {
      return "scenario result_" + scenario;
    }

    String quotedTable(int scenario) {
      return quote(table(scenario));
    }

    String header(int scenario) {
      return "result paths" + scenario + NodusC.SUFFIX_HEADER;
    }

    String detail(int scenario) {
      return "result paths" + scenario + NodusC.SUFFIX_DETAIL;
    }

    void source(int scenario, boolean legacy, boolean reverse, int[] groups, Row... rows)
        throws SQLException {
      final Map<String, String> schema = new LinkedHashMap<>();
      for (String name : DESCRIPTORS) {
        schema.put(name, "NUMERIC(11)");
      }
      schema.put("length", "NUMERIC(8,3)");
      if (!legacy) {
        schema.put("vtype", "NUMERIC(1)");
      }
      for (int group : groups) {
        schema.put("ucost" + group, "NUMERIC(13,3)");
        schema.put("qty" + group, "NUMERIC(13,3)");
        schema.put("veh" + group, "NUMERIC(10)");
      }
      schema.put("ucost", "NUMERIC(13,3)");
      schema.put("qty", "NUMERIC(13,3)");
      schema.put("veh", "NUMERIC(10)");
      final List<String> names = new ArrayList<>(schema.keySet());
      if (reverse) {
        Collections.reverse(names);
      }
      execute(
          "CREATE TABLE "
              + quotedTable(scenario)
              + " ("
              + String.join(
                  ",", names.stream().map(n -> n + " " + schema.get(n)).toArray(String[]::new))
              + ")");
      try (PreparedStatement insert =
          raw.prepareStatement(
              "INSERT INTO "
                  + quotedTable(scenario)
                  + " ("
                  + String.join(",", names)
                  + ") VALUES ("
                  + String.join(",", Collections.nCopies(names.size(), "?"))
                  + ")")) {
        for (Row row : rows) {
          final Map<String, BigDecimal> values = new LinkedHashMap<>();
          for (int i = 0; i < DESCRIPTORS.size(); i++) {
            values.put(DESCRIPTORS.get(i), BigDecimal.valueOf(row.descriptors[i]));
          }
          values.put("length", row.length);
          values.put("vtype", BigDecimal.valueOf(row.type));
          for (int group : groups) {
            Triple amount = row.groups.getOrDefault(group, new Triple(group, 0, 0, 0));
            values.put("ucost" + group, amount.cost);
            values.put("qty" + group, amount.quantity);
            values.put("veh" + group, BigDecimal.valueOf(amount.vehicles));
          }
          // Deliberately inconsistent source aggregates must be recomputed from group amounts.
          values.put("ucost", new BigDecimal("999"));
          values.put("qty", new BigDecimal("999"));
          values.put("veh", new BigDecimal("999"));
          for (int i = 0; i < names.size(); i++) {
            insert.setBigDecimal(i + 1, values.get(names.get(i)));
          }
          insert.executeUpdate();
        }
      }
    }

    void seed() throws SQLException {
      final Row[] first = new Row[5];
      final Row[] second = new Row[5];
      for (int i = 0; i < first.length; i++) {
        first[i] = row(i + 1, i + 2, 30).group(1, 10, 2, 2);
        second[i] = row(i + 1, i + 2, 30).group(1, 4, 1, 1);
      }
      source(1, false, false, new int[] {1}, first);
      source(2, false, false, new int[] {1}, second);
      source(3, false, false, new int[] {1}, row(99, 100, 30).group(1, 77, 88, 9));
      for (int scenario = 1; scenario <= 3; scenario++) {
        for (String table : List.of(header(scenario), detail(scenario))) {
          execute("CREATE TABLE " + quote(table) + " (marker INTEGER)");
          execute("INSERT INTO " + quote(table) + " VALUES (" + scenario + ")");
        }
      }
    }

    void run(boolean compare, String filter) {
      run(compare, 1, 2, 3, filter);
    }

    void run(boolean compare, int first, int second, int result, String filter) {
      final Scenarios scenarios = new Scenarios(this);
      if (compare) {
        scenarios.compare(first, second, result, filter);
      } else {
        scenarios.sum(first, second, result, filter);
      }
    }

    void execute(String sql) throws SQLException {
      try (Statement statement = raw.createStatement()) {
        statement.executeUpdate(sql);
      }
    }

    boolean exists(String name) throws SQLException {
      return JDBCUtils.tableExists(name);
    }

    Map<String, List<String>> outputs() throws SQLException {
      final Map<String, List<String>> result = new LinkedHashMap<>();
      for (int scenario = 1; scenario <= 3; scenario++) {
        for (String table : List.of(table(scenario), header(scenario), detail(scenario))) {
          if (exists(table)) {
            result.put(table, snapshot(table));
          }
        }
      }
      return result;
    }

    List<String> snapshot(String table) throws SQLException {
      final List<String> result = new ArrayList<>();
      try (Statement statement = raw.createStatement();
          ResultSet rows = statement.executeQuery("SELECT * FROM " + quote(table))) {
        final int columns = rows.getMetaData().getColumnCount();
        while (rows.next()) {
          final List<String> values = new ArrayList<>();
          for (int i = 1; i <= columns; i++) {
            values.add(rows.getString(i));
          }
          result.add(values.toString());
        }
      }
      Collections.sort(result);
      return result;
    }

    void assertCount(int count) throws SQLException {
      assertEquals(count, snapshot(table(3)).size());
    }

    void assertSchema(int... groups) throws SQLException {
      try (Statement statement = raw.createStatement();
          ResultSet rows = statement.executeQuery("SELECT * FROM " + quotedTable(3))) {
        final ResultSetMetaData metadata = rows.getMetaData();
        assertEquals(16 + groups.length * 3, metadata.getColumnCount());
        for (int i = 0; i < groups.length; i++) {
          assertEquals("UCOST" + groups[i], metadata.getColumnName(14 + 3 * i));
          assertEquals("QTY" + groups[i], metadata.getColumnName(15 + 3 * i));
          assertEquals("VEH" + groups[i], metadata.getColumnName(16 + 3 * i));
        }
      }
    }

    void assertRow(Row row, String cost, String quantity, long vehicles, Triple... groups)
        throws SQLException {
      final List<String> predicates = new ArrayList<>();
      for (int i = 0; i < DESCRIPTORS.size(); i++) {
        predicates.add(DESCRIPTORS.get(i) + "=" + row.descriptors[i]);
      }
      predicates.add("vtype=" + row.type);
      try (Statement statement = raw.createStatement();
          ResultSet rows =
              statement.executeQuery(
                  "SELECT * FROM "
                      + quotedTable(3)
                      + " WHERE "
                      + String.join(" AND ", predicates))) {
        assertTrue(rows.next(), predicates.toString());
        equalDecimal(row.length, rows.getBigDecimal("length"), "length");
        equalDecimal(new BigDecimal(cost), rows.getBigDecimal("ucost"), "aggregate cost");
        equalDecimal(new BigDecimal(quantity), rows.getBigDecimal("qty"), "aggregate quantity");
        assertEquals(vehicles, rows.getLong("veh"));
        for (Triple group : groups) {
          equalDecimal(group.cost, rows.getBigDecimal("ucost" + group.group), "group cost");
          equalDecimal(group.quantity, rows.getBigDecimal("qty" + group.group), "group quantity");
          assertEquals(group.vehicles, rows.getLong("veh" + group.group));
        }
        assertFalse(rows.next(), "Duplicate descriptor " + predicates);
      }
    }

    void assertClean() throws SQLException {
      for (PreparedStatement writer : writers) {
        assertTrue(writer.isClosed(), "Leaked output statement");
      }
      try (ResultSet tables =
          raw.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
        while (tables.next()) {
          final String name = tables.getString("TABLE_NAME").toUpperCase(Locale.ROOT);
          assertFalse(name.startsWith("NO_") || name.startsWith("NB_"), "Leaked table " + name);
        }
      }
    }

    @Override
    public void close() {
      JDBCUtils.setConnection(null);
      try {
        raw.close();
      } catch (SQLException failure) {
        throw new IllegalStateException(failure);
      }
    }
  }

  private static void equalDecimal(BigDecimal expected, BigDecimal actual, String label) {
    assertNotNull(actual, label);
    assertEquals(
        0, expected.compareTo(actual), label + ": expected " + expected + ", got " + actual);
  }

  private static String quote(String name) {
    return JDBCUtils.getQuotedCompliantIdentifier(name);
  }

  @FunctionalInterface
  private interface JdbcCall {
    Object call(Method method, Object[] args) throws Throwable;
  }

  private static <T> T proxy(Class<T> type, T delegate, JdbcCall call) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (ignored, method, args) -> call.call(method, args)));
  }

  private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException failure) {
      throw failure.getCause();
    }
  }
}
