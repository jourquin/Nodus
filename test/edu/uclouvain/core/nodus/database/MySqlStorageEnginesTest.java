/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.testing.StorageEngineJdbc;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class MySqlStorageEnginesTest {
  @TestFactory
  Stream<DynamicTest> mixedEnginesAreDetectedEvenWithATransactionalDefault() {
    return Stream.of("MariaDB", "MySQL")
        .map(
            product ->
                DynamicTest.dynamicTest(
                    product,
                    () -> {
                      StorageEngineJdbc jdbc =
                          new StorageEngineJdbc(
                              product,
                              "InnoDB",
                              "YES",
                              new String[] {"network", "MyISAM", "NO"},
                              new String[] {"parameters", "InnoDB", "YES"},
                              new String[] {"stops", "Aria", "NO"});
                      MySqlStorageEngines.Inspection result =
                          MySqlStorageEngines.inspect(jdbc.connection);
                      assertTrue(result.requiresWarning());
                      assertFalse(result.isDefaultNonTransactional());
                      assertEquals(
                          Map.of("network", "MyISAM", "stops", "Aria"),
                          result.getNonTransactionalTables());
                      assertFalse(result.hasUnverifiedEngines());
                      assertThrows(
                          UnsupportedOperationException.class,
                          () -> result.getNonTransactionalTables().clear());
                      jdbc.assertClosed();
                    }));
  }

  @Test
  void nonTransactionalDefaultWarnsEvenBeforeAnyTablesExist() throws Exception {
    StorageEngineJdbc jdbc = new StorageEngineJdbc("MariaDB", "MyISAM", "NO");
    MySqlStorageEngines.Inspection result = MySqlStorageEngines.inspect(jdbc.connection);
    assertTrue(result.requiresWarning());
    assertTrue(result.isDefaultNonTransactional());
    assertEquals("MyISAM", result.getDefaultEngine());
    assertTrue(result.getNonTransactionalTables().isEmpty());
    jdbc.assertClosed();
  }

  @Test
  void nonTransactionalDefaultWarnsEvenWhenExistingTablesAreTransactional() throws Exception {
    StorageEngineJdbc jdbc =
        new StorageEngineJdbc(
            "MariaDB", "MyISAM", "NO", new String[] {"parameters", "InnoDB", "YES"});
    MySqlStorageEngines.Inspection result = MySqlStorageEngines.inspect(jdbc.connection);
    assertTrue(result.requiresWarning());
    assertTrue(result.isDefaultNonTransactional());
    assertTrue(result.getNonTransactionalTables().isEmpty());
    jdbc.assertClosed();
  }

  @TestFactory
  Stream<DynamicTest> transactionalEnginesDoNotWarn() {
    return Stream.of("InnoDB", "ROCKSDB")
        .map(
            engine ->
                DynamicTest.dynamicTest(
                    engine,
                    () -> {
                      StorageEngineJdbc jdbc =
                          new StorageEngineJdbc(
                              "MariaDB", engine, "yes", new String[] {"network", engine, "yes"});
                      assertFalse(MySqlStorageEngines.inspect(jdbc.connection).requiresWarning());
                      jdbc.assertClosed();
                    }));
  }

  @TestFactory
  Stream<DynamicTest> otherDatabaseProductsDoNotUseMySqlMetadata() {
    return Stream.of("H2", "HSQL Database Engine", "PostgreSQL")
        .map(
            product ->
                DynamicTest.dynamicTest(
                    product,
                    () -> {
                      StorageEngineJdbc jdbc = new StorageEngineJdbc(product, null, null);
                      assertFalse(MySqlStorageEngines.inspect(jdbc.connection).requiresWarning());
                      assertEquals(0, jdbc.queries.get());
                      jdbc.assertClosed();
                    }));
  }

  @Test
  void unknownCapabilitiesAreReportedAsUnverified() throws Exception {
    StorageEngineJdbc jdbc =
        new StorageEngineJdbc(
            "MariaDB", "InnoDB", "YES", new String[] {"custom_table", "custom_engine", null});
    MySqlStorageEngines.Inspection result = MySqlStorageEngines.inspect(jdbc.connection);
    assertTrue(result.requiresWarning());
    assertTrue(result.hasUnverifiedEngines());
    assertFalse(result.isDefaultNonTransactional());
    assertTrue(result.getNonTransactionalTables().isEmpty());
    assertEquals(List.of("custom_table"), result.getUnverifiedTables());
    jdbc.assertClosed();

    StorageEngineJdbc missingDefault = new StorageEngineJdbc("MariaDB", null, null);
    MySqlStorageEngines.Inspection unavailable =
        MySqlStorageEngines.inspect(missingDefault.connection);
    assertTrue(unavailable.requiresWarning());
    assertTrue(unavailable.hasUnverifiedEngines());
    assertTrue(unavailable.getUnverifiedTables().isEmpty());
    missingDefault.assertClosed();
  }

  @TestFactory
  Stream<DynamicTest> failuresNeverLeakStatementsOrResults() {
    return Stream.of("query", "read", "close")
        .map(
            failure ->
                DynamicTest.dynamicTest(
                    failure,
                    () -> {
                      StorageEngineJdbc jdbc = new StorageEngineJdbc("MariaDB", "InnoDB", "YES");
                      jdbc.failTables = failure.equals("query");
                      jdbc.failResultRead = failure.equals("read");
                      jdbc.failStatementClose = failure.equals("close");
                      assertThrows(
                          SQLException.class, () -> MySqlStorageEngines.inspect(jdbc.connection));
                      jdbc.assertClosed();
                    }));
  }
}
