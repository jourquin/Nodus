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

package edu.uclouvain.core.nodus.compute.od;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Discovers OD candidates using actual H2/HSQLDB metadata, independently of demand loading. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock(Resources.LOCALE)
@ResourceLock(Resources.SYSTEM_ERR)
@Timeout(20)
class ODTableDiscoveryTest {
  private static final String BASIC = "GRP INTEGER, ORG INTEGER, DST INTEGER, QTY DECIMAL(12,3)";

  @TestFactory
  Stream<DynamicTest> requiredColumnsAllowDifferentOrderCaseAndOptionalFields() {
    return engines(
        hsql ->
            Stream.of(
                    BASIC,
                    "QTY DOUBLE, DST BIGINT, ORG SMALLINT, GRP TINYINT",
                    "\"gRp\" INTEGER, \"OrG\" INTEGER, \"DsT\" INTEGER, \"QtY\" NUMERIC(18,6)",
                    BASIC + ", CLASS INTEGER, TIME INTEGER, NOTE VARCHAR(50)")
                .map(
                    schema ->
                        dynamicTest(
                            label(hsql, schema),
                            () -> {
                              try (Fixture f = new Fixture(hsql)) {
                                f.table("DEMAND", schema);
                                assertTables(f, "DEMAND");
                              }
                            })));
  }

  @TestFactory
  Stream<DynamicTest> missingColumnsAndUnprefixedPathHeadersAreExcluded() {
    return engines(
        hsql ->
            Stream.of("GRP", "ORG", "DST", "QTY", "iteration first", "iteration last")
                .map(
                    missing ->
                        dynamicTest(
                            label(hsql, missing),
                            () -> {
                              try (Fixture f = new Fixture(hsql)) {
                                String schema =
                                    missing.equals("iteration first")
                                        ? "iteration INTEGER, " + BASIC
                                        : missing.equals("iteration last")
                                            ? BASIC + ", \"ItErAtIoN\" INTEGER"
                                            : Stream.of(BASIC.split(", "))
                                                .filter(column -> !column.startsWith(missing + " "))
                                                .collect(Collectors.joining(", "));
                                f.table("INVALID", schema);
                                f.table("VALID", BASIC);
                                assertTables(f, "VALID");
                              }
                            })));
  }

  @TestFactory
  Stream<DynamicTest> reservedPrefixesAndLoadedLayersStayExcludedUnderAnyLocale() {
    return engines(
        hsql ->
            Stream.of(false, true)
                .flatMap(
                    custom ->
                        Stream.of(Locale.ROOT, Locale.forLanguageTag("tr-TR"))
                            .map(
                                locale ->
                                    dynamicTest(
                                        label(hsql, "custom=" + custom + ", locale=" + locale),
                                        () -> {
                                          Locale previous = Locale.getDefault();
                                          try (Fixture f = new Fixture(hsql)) {
                                            Locale.setDefault(locale);
                                            String[] keys = {
                                              NodusC.PROP_VNET_TABLE,
                                              NodusC.PROP_PATH_TABLE_PREFIX,
                                              NodusC.PROP_EXC_TABLE,
                                              NodusC.PROP_SERVICES_TABLE_PREFIX
                                            };
                                            String[] suffixes = {
                                              NodusC.SUFFIX_VNET,
                                              NodusC.SUFFIX_PATH,
                                              NodusC.SUFFIX_EXC,
                                              NodusC.SUFFIX_SERVICES
                                            };
                                            List<String> expected = new ArrayList<>();
                                            for (int i = 0; i < keys.length; i++) {
                                              String defaultPrefix = "Mini" + suffixes[i];
                                              String prefix =
                                                  custom ? "Traffic" + i : defaultPrefix;
                                              if (custom) {
                                                f.properties.setProperty(keys[i], prefix);
                                                String allowedDefault =
                                                    defaultPrefix.toUpperCase(Locale.ROOT) + "1";
                                                f.table(allowedDefault, BASIC);
                                                expected.add(allowedDefault);
                                              }
                                              f.table(prefix.toUpperCase(Locale.ROOT), BASIC);
                                              f.table(
                                                  prefix.toUpperCase(Locale.ROOT) + "12_DETAIL",
                                                  BASIC);
                                              String nearMiss =
                                                  "OD_" + prefix.toUpperCase(Locale.ROOT);
                                              f.table(nearMiss, BASIC);
                                              expected.add(nearMiss);
                                            }
                                            f.table("ROAD_LAYER", BASIC);
                                            f.table("NODE_LAYER", BASIC);
                                            f.layers.addAll(List.of("road_layer", "node_layer"));
                                            f.table("MINI_VNE", BASIC);
                                            expected.add("MINI_VNE");
                                            assertTables(f, expected.toArray(new String[0]));
                                          } finally {
                                            Locale.setDefault(previous);
                                          }
                                        }))));
  }

  @TestFactory
  Stream<DynamicTest> quotedTableNamesAreReturnedExactlyAsStoredAndCannotBecomeSql() {
    return engines(
        hsql ->
            Stream.of(
                    "MixedCase",
                    "Demand 2026",
                    "select",
                    "flux_été",
                    "OD\"quoted",
                    "\"Wrapped\"",
                    "OD.with.dot",
                    "OD\"; DROP TABLE SENTINEL; --")
                .map(
                    name ->
                        dynamicTest(
                            label(hsql, name),
                            () -> {
                              try (Fixture f = new Fixture(hsql)) {
                                f.execute("CREATE TABLE SENTINEL (ID INTEGER)");
                                f.execute("INSERT INTO SENTINEL VALUES (42)");
                                f.table(name, BASIC);
                                assertTables(f, name);
                                assertEquals(42, f.number("SELECT ID FROM SENTINEL"));
                              }
                            })));
  }

  @TestFactory
  Stream<DynamicTest> distinctQuotedNamesAreValidatedAgainstTheirOwnSchemas() {
    return engines(
        hsql ->
            Stream.of(false, true)
                .map(
                    both ->
                        dynamicTest(
                            label(hsql, "case-distinct tables, both valid=" + both),
                            () -> {
                              try (Fixture f = new Fixture(hsql)) {
                                f.table("Demand", BASIC);
                                f.table(
                                    "DEMAND",
                                    both ? BASIC : "GRP INTEGER, ORG INTEGER, DST INTEGER");
                                assertTables(
                                    f,
                                    both
                                        ? new String[] {"Demand", "DEMAND"}
                                        : new String[] {"Demand"});
                              }
                            })));
  }

  @TestFactory
  Stream<DynamicTest> viewsAreExcludedEvenWhenTheyExposeTheRequiredColumns() {
    return engines(
        hsql ->
            Stream.of(
                dynamicTest(
                    label(hsql, "view exclusion"),
                    () -> {
                      try (Fixture f = new Fixture(hsql)) {
                        f.table("DEMAND", BASIC);
                        f.execute("CREATE VIEW DEMAND_VIEW AS SELECT * FROM DEMAND");
                        assertTables(f, "DEMAND");
                      }
                    })));
  }

  @TestFactory
  Stream<DynamicTest> discoveryUsesTheActiveSchemaEvenWhenItsNameContainsMetadataWildcards() {
    return engines(
        hsql ->
            Stream.of(
                dynamicTest(
                    label(hsql, "schema scope"),
                    () -> {
                      try (Fixture f = new Fixture(hsql)) {
                        f.table("PUBLIC_ONLY", BASIC);
                        for (String schema : List.of("Scope_%", "Scope_AX")) {
                          f.execute("CREATE SCHEMA " + quote(schema) + " AUTHORIZATION SA");
                          f.connection.setSchema(schema);
                          f.table("SHARED", BASIC);
                          f.table(
                              schema.equals("Scope_%") ? "CURRENT_ONLY" : "FOREIGN_ONLY", BASIC);
                        }
                        f.connection.setSchema("Scope_%");
                        assertTables(f, "CURRENT_ONLY", "SHARED");
                        assertEquals("Scope_%", f.connection.getSchema());
                      }
                    })));
  }

  @TestFactory
  Stream<DynamicTest> discoveryDoesNotDependOnOrReplaceTheSharedJdbcConnection() {
    return engines(
        hsql ->
            Stream.of(false, true)
                .map(
                    absent ->
                        dynamicTest(
                            label(hsql, "shared connection absent=" + absent),
                            () -> {
                              try (Fixture f = new Fixture(hsql);
                                  Connection other = open(!hsql)) {
                                f.table("PROJECT_ONLY", BASIC);
                                try (Statement s = other.createStatement()) {
                                  s.execute("CREATE TABLE FOREIGN_ONLY (" + BASIC + ")");
                                }
                                assertTrue(JDBCUtils.setConnection(absent ? null : other));
                                int engine =
                                    absent ? JDBCUtils.DB_UNKNOWN : JDBCUtils.getDbEngine();
                                assertTables(f, "PROJECT_ONLY");
                                assertSame(absent ? null : other, JDBCUtils.getConnection());
                                if (!absent) {
                                  assertEquals(engine, JDBCUtils.getDbEngine());
                                  assertFalse(other.isClosed());
                                }
                                assertFalse(f.connection.isClosed());
                              }
                            })));
  }

  @TestFactory
  Stream<DynamicTest> absentProjectConnectionLeavesTheSharedConnectionAlone() {
    return engines(
        hsql ->
            Stream.of(
                dynamicTest(
                    label(hsql, "no project connection"),
                    () -> {
                      try (Fixture f = new Fixture(hsql)) {
                        f.table("DEMAND", BASIC);
                        f.exposed = null;
                        assertTables(f);
                        assertSame(f.connection, JDBCUtils.getConnection());
                        assertFalse(f.connection.isClosed());
                      }
                    })));
  }

  @TestFactory
  Stream<DynamicTest> successAndFailuresPreserveTransactionStateAndEarlierUncommittedWork() {
    return engines(
        hsql ->
            Stream.of(false, true)
                .flatMap(
                    autoCommit ->
                        Stream.of("none", "query", "metadata")
                            .map(
                                failure ->
                                    dynamicTest(
                                        label(hsql, "autoCommit=" + autoCommit + ", " + failure),
                                        () -> {
                                          try (Fixture f = new Fixture(hsql)) {
                                            f.table("GOOD", BASIC);
                                            f.table("BAD", BASIC);
                                            f.execute("CREATE TABLE EARLIER (ID INTEGER)");
                                            f.connection.setAutoCommit(autoCommit);
                                            f.execute("INSERT INTO EARLIER VALUES (1)");
                                            final Savepoint savepoint =
                                                autoCommit ? null : f.connection.setSavepoint();
                                            f.execute("INSERT INTO EARLIER VALUES (2)");
                                            final String schema = f.connection.getSchema();
                                            final String catalog = f.connection.getCatalog();
                                            final boolean readOnly = f.connection.isReadOnly();
                                            final int isolation =
                                                f.connection.getTransactionIsolation();
                                            Tracker tracker = new Tracker(f.connection, failure);
                                            f.exposed = tracker.connection;
                                            assertTrue(JDBCUtils.setConnection(tracker.connection));
                                            List<String> found = new ArrayList<>();
                                            String diagnostics =
                                                captureErrors(
                                                    () -> {
                                                      found.addAll(ODReader.getValidODTables(f));
                                                      return null;
                                                    });
                                            assertEquals(
                                                failure.equals("metadata")
                                                    ? List.of()
                                                    : failure.equals("query")
                                                        ? List.of("GOOD")
                                                        : List.of("BAD", "GOOD"),
                                                sorted(found));
                                            assertEquals(
                                                failure.equals("metadata"),
                                                diagnostics.contains("injected metadata"));
                                            assertEquals(autoCommit, f.connection.getAutoCommit());
                                            assertEquals(readOnly, f.connection.isReadOnly());
                                            assertEquals(
                                                isolation, f.connection.getTransactionIsolation());
                                            assertEquals(schema, f.connection.getSchema());
                                            assertEquals(catalog, f.connection.getCatalog());
                                            assertSame(
                                                tracker.connection, JDBCUtils.getConnection());
                                            assertFalse(f.connection.isClosed());
                                            tracker.assertClean();
                                            assertEquals(
                                                2, f.number("SELECT COUNT(*) FROM EARLIER"));
                                            if (!autoCommit) {
                                              f.connection.rollback(savepoint);
                                              assertEquals(
                                                  1, f.number("SELECT COUNT(*) FROM EARLIER"));
                                              f.connection.rollback();
                                              assertEquals(
                                                  0, f.number("SELECT COUNT(*) FROM EARLIER"));
                                            }
                                          }
                                        }))));
  }

  @TestFactory
  Stream<DynamicTest> badTableProbesCloseResourcesAndDoNotHideOtherCandidates() {
    return engines(
        hsql ->
            Stream.of("query", "columns", "result-close", "statement-close")
                .map(
                    failure ->
                        dynamicTest(
                            label(hsql, failure),
                            () -> {
                              try (Fixture f = new Fixture(hsql)) {
                                f.table("BAD", BASIC);
                                f.table("GOOD", BASIC);
                                Tracker tracker = new Tracker(f.connection, failure);
                                f.exposed = tracker.connection;
                                assertTrue(JDBCUtils.setConnection(tracker.connection));
                                assertTables(f, "GOOD");
                                assertEquals(1, tracker.injected);
                                tracker.assertClean();
                              }
                            })));
  }

  @TestFactory
  Stream<DynamicTest> failedMetadataIterationClosesItsCursorAndCanBeRetried() {
    return engines(
        hsql ->
            Stream.of(
                dynamicTest(
                    label(hsql, "metadata iteration failure"),
                    () -> {
                      try (Fixture f = new Fixture(hsql)) {
                        f.table("FIRST", BASIC);
                        f.table("SECOND", BASIC);
                        Tracker tracker = new Tracker(f.connection, "metadata-next");
                        f.exposed = tracker.connection;
                        assertTrue(JDBCUtils.setConnection(tracker.connection));
                        List<String> found = new ArrayList<>();
                        String diagnostics =
                            captureErrors(
                                () -> {
                                  found.addAll(ODReader.getValidODTables(f));
                                  return null;
                                });
                        assertEquals(1, found.size());
                        assertTrue(List.of("FIRST", "SECOND").contains(found.get(0)));
                        assertTrue(diagnostics.contains("injected metadata-next"));
                        assertEquals(1, tracker.injected);
                        tracker.assertClean();
                        tracker.failure = "none";
                        assertTables(f, "FIRST", "SECOND");
                        tracker.assertClean();
                      }
                    })));
  }

  @TestFactory
  Stream<DynamicTest> repeatedDiscoveryReflectsSchemaChangesAndReturnsIndependentLists() {
    return engines(
        hsql ->
            Stream.of(
                dynamicTest(
                    label(hsql, "fresh discovery"),
                    () -> {
                      try (Fixture f = new Fixture(hsql)) {
                        f.table("DEMAND", BASIC);
                        var first = ODReader.getValidODTables(f);
                        first.clear();
                        assertTables(f, "DEMAND");
                        f.execute("ALTER TABLE DEMAND ADD iteration INTEGER");
                        assertTables(f);
                        f.execute("DROP TABLE DEMAND");
                        f.table("REPLACEMENT", BASIC);
                        assertTables(f, "REPLACEMENT");
                      }
                    })));
  }

  private static Stream<DynamicTest> engines(EngineCases cases) {
    return Stream.of(false, true).flatMap(cases::tests);
  }

  private interface EngineCases {
    Stream<DynamicTest> tests(boolean hsql);
  }

  private static String label(boolean hsql, String description) {
    return (hsql ? "HSQLDB: " : "H2: ") + description;
  }

  private static List<String> sorted(List<String> names) {
    return names.stream().sorted().collect(Collectors.toList());
  }

  private static void assertTables(Fixture f, String... names) {
    assertEquals(sorted(List.of(names)), sorted(ODReader.getValidODTables(f)));
  }

  private static String quote(String name) {
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  private static Connection open(boolean hsql) throws SQLException {
    return DriverManager.getConnection(
        (hsql ? "jdbc:hsqldb:mem:" : "jdbc:h2:mem:")
            + "discovery_"
            + UUID.randomUUID()
            + (hsql ? ";shutdown=true" : ""),
        "sa",
        "");
  }

  private static String captureErrors(Supplier<?> action) {
    PrintStream original = System.err;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setErr(output);
      try {
        action.get();
      } finally {
        System.setErr(original);
      }
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  private static final class Fixture extends NodusProject implements AutoCloseable {
    final Connection connection;
    Connection exposed;
    final Properties properties = new Properties();
    final Set<String> layers = new HashSet<>();
    private final NodusEsriLayer marker = new NodusEsriLayer();

    Fixture(boolean hsql) throws SQLException {
      super(null);
      connection = open(hsql);
      exposed = connection;
      properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "Mini");
      assertTrue(JDBCUtils.setConnection(connection));
    }

    void table(String name, String schema) throws SQLException {
      execute("CREATE TABLE " + quote(name) + " (" + schema + ")");
    }

    void execute(String sql) throws SQLException {
      try (Statement s = connection.createStatement()) {
        s.execute(sql);
      }
    }

    int number(String sql) throws SQLException {
      try (Statement s = connection.createStatement();
          ResultSet r = s.executeQuery(sql)) {
        assertTrue(r.next());
        return r.getInt(1);
      }
    }

    @Override
    public Connection getMainJDBCConnection() {
      return exposed;
    }

    @Override
    public String getLocalProperty(String key) {
      return properties.getProperty(key);
    }

    @Override
    public String getLocalProperty(String key, String fallback) {
      return properties.getProperty(key, fallback);
    }

    @Override
    public NodusEsriLayer getLayer(String name) {
      return layers.stream().anyMatch(name::equalsIgnoreCase) ? marker : null;
    }

    @Override
    public void close() {
      JDBCUtils.setConnection(null);
      try {
        connection.close();
      } catch (SQLException failure) {
        throw new IllegalStateException(failure);
      }
    }
  }

  /** Delegates to the real driver while tracking ownership and injecting one selected failure. */
  private static final class Tracker {
    final Connection connection;
    final List<Resource> resources = new ArrayList<>();
    final List<String> mutations = new ArrayList<>();
    String failure;
    int injected;

    Tracker(Connection real, String failure) {
      this.failure = failure;
      connection =
          proxy(
              Connection.class,
              (p, m, a) -> {
                if (m.getName().startsWith("set")
                    || List.of("commit", "rollback", "close", "abort").contains(m.getName())) {
                  mutations.add(m.getName());
                }
                Object value = invoke(real, m, a);
                if (m.getName().equals("getMetaData")) {
                  return metadata((DatabaseMetaData) value);
                }
                return m.getName().equals("createStatement") ? statement((Statement) value) : value;
              });
    }

    private DatabaseMetaData metadata(DatabaseMetaData real) {
      return proxy(
          DatabaseMetaData.class,
          (p, m, a) -> {
            if (m.getName().equals("getTables")) {
              fail("metadata");
              return rows((ResultSet) invoke(real, m, a), null, true);
            }
            return invoke(real, m, a);
          });
    }

    private Statement statement(Statement real) {
      Resource resource = new Resource();
      resources.add(resource);
      return proxy(
          Statement.class,
          (p, m, a) -> {
            if (m.getName().equals("executeQuery")) {
              resource.sql = (String) a[0];
              assertTrue(resource.sql.endsWith("WHERE 1 = 0"), resource.sql);
              if (resource.bad()) {
                fail("query");
              }
              return rows((ResultSet) invoke(real, m, a), resource.sql, false);
            }
            Object value = invoke(real, m, a);
            if (m.getName().equals("close")) {
              resource.closed = true;
              if (resource.bad()) {
                fail("statement-close");
              }
            }
            return value;
          });
    }

    private ResultSet rows(ResultSet real, String sql, boolean tables) {
      Resource resource = new Resource();
      resource.sql = sql;
      resources.add(resource);
      return proxy(
          ResultSet.class,
          (p, m, a) -> {
            if (m.getName().equals("next")) {
              assertTrue(tables, "Schema probes must not read demand rows");
              if (resource.advances++ == 1) {
                fail("metadata-next");
              }
            }
            if (m.getName().equals("getMetaData") && resource.bad()) {
              fail("columns");
            }
            Object value = invoke(real, m, a);
            if (m.getName().equals("close")) {
              resource.closed = true;
              if (resource.bad()) {
                fail("result-close");
              }
            }
            return value;
          });
    }

    private void fail(String point) throws SQLException {
      if (failure.equals(point)) {
        injected++;
        throw new SQLException("injected " + point);
      }
    }

    void assertClean() {
      assertTrue(mutations.isEmpty(), mutations.toString());
      for (Resource resource : resources) {
        assertTrue(resource.closed, "Unclosed JDBC resource for " + resource.sql);
      }
    }
  }

  private static final class Resource {
    String sql;
    boolean closed;
    int advances;

    boolean bad() {
      return sql != null && sql.contains("\"BAD\"");
    }
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }

  private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException failure) {
      throw failure.getCause();
    }
  }
}
