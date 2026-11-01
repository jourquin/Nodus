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

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Exercises the MySQL/MariaDB-specific metadata guards without connecting to a live server. */
@ResourceLock("JDBCUtils")
class ImportEngineGuardTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> rollbackMustBeVerifiedForTheActualDestinationEngine() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String engine : new String[] {"YES", "NO", null}) {
      tests.add(
          dynamicTest(
              "transaction support " + engine,
              () -> {
                try (DatabaseFixture db = new DatabaseFixture(directory)) {
                  List<String> parameters = new ArrayList<>();
                  Connection connection =
                      mysql(
                          db.connection,
                          sql -> {
                            assertTrue(sql.contains("t.TABLE_NAME=?"), sql);
                            return engine;
                          },
                          parameters);
                  if ("YES".equals(engine)) {
                    TableImport.requireTransactionalRows(connection, "traffic flows");
                  } else {
                    SQLException error =
                        assertThrows(
                            SQLException.class,
                            () ->
                                TableImport.requireTransactionalRows(connection, "traffic flows"));
                    assertTrue(error.getMessage().contains("rollback support"), error.getMessage());
                  }
                  assertEquals(List.of("TRAFFIC FLOWS"), parameters);
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> hiddenTriggersCannotBeMistakenForNoTriggers() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean visibility : new boolean[] {false, true}) {
      tests.add(
          dynamicTest(
              "TRIGGER privilege " + visibility,
              () -> {
                try (DatabaseFixture db = new DatabaseFixture(directory)) {
                  db.execute("CREATE TABLE data(id INTEGER,label VARCHAR(16))");
                  List<String> parameters = new ArrayList<>();
                  Connection connection =
                      mysql(
                          db.connection,
                          sql -> {
                            if (sql.equals("SELECT CURRENT_USER()")) {
                              return "tester@localhost";
                            }
                            if (sql.contains("PRIVILEGE_TYPE='TRIGGER'")) {
                              return visibility ? "1" : null;
                            }
                            throw new AssertionError(sql);
                          },
                          parameters);
                  if (visibility) {
                    TableDefinitionGuard.check(connection, "data");
                  } else {
                    SQLException error =
                        assertThrows(
                            SQLException.class,
                            () -> TableDefinitionGuard.check(connection, "data"));
                    assertTrue(
                        error.getMessage().contains("trigger visibility"), error.getMessage());
                  }
                  assertTrue(parameters.contains("'tester'@'localhost'"), parameters.toString());
                }
              }));
    }
    return tests;
  }

  private static Connection mysql(Connection delegate, Query query, List<String> parameters)
      throws SQLException {
    DatabaseMetaData original = delegate.getMetaData();
    DatabaseMetaData metadata =
        (DatabaseMetaData)
            Proxy.newProxyInstance(
                ImportEngineGuardTest.class.getClassLoader(),
                new Class<?>[] {DatabaseMetaData.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getDatabaseProductName")) {
                    return "MariaDB";
                  }
                  try {
                    return method.invoke(original, args);
                  } catch (InvocationTargetException error) {
                    throw error.getCause();
                  }
                });
    return (Connection)
        Proxy.newProxyInstance(
            ImportEngineGuardTest.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getMetaData")) {
                return metadata;
              }
              if (method.getName().equals("prepareStatement")) {
                String sql = (String) args[0];
                if (sql.contains("e.TRANSACTIONS")
                    || sql.equals("SELECT CURRENT_USER()")
                    || sql.contains("PRIVILEGE_TYPE='TRIGGER'")) {
                  return (PreparedStatement)
                      Proxy.newProxyInstance(
                          ImportEngineGuardTest.class.getClassLoader(),
                          new Class<?>[] {PreparedStatement.class},
                          (statement, operation, values) -> {
                            if (operation.getName().equals("setString")) {
                              parameters.add((String) values[1]);
                            }
                            if (operation.getName().equals("executeQuery")) {
                              return row(query.value(sql));
                            }
                            return null;
                          });
                }
              }
              try {
                return method.invoke(delegate, args);
              } catch (InvocationTargetException error) {
                throw error.getCause();
              }
            });
  }

  private static ResultSet row(String value) throws SQLException {
    RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
    metadata.setColumnCount(1);
    metadata.setColumnName(1, "value");
    metadata.setColumnType(1, Types.VARCHAR);
    CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
    rows.setMetaData(metadata);
    if (value != null) {
      rows.moveToInsertRow();
      rows.updateString(1, value);
      rows.insertRow();
      rows.moveToCurrentRow();
    }
    rows.beforeFirst();
    return rows;
  }

  @FunctionalInterface
  private interface Query {
    String value(String sql);
  }
}
