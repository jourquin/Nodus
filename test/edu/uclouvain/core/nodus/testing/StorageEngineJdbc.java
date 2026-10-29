/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

/** Read-only engine metadata; any attempt to change data, settings or ownership fails the test. */
public final class StorageEngineJdbc {
  public final Connection connection;
  public final AtomicInteger queries = new AtomicInteger();
  private final AtomicInteger statements = new AtomicInteger();
  private final AtomicInteger statementsClosed = new AtomicInteger();
  private final AtomicInteger results = new AtomicInteger();
  private final AtomicInteger resultsClosed = new AtomicInteger();
  public boolean failTables;
  public boolean failResultRead;
  public boolean failStatementClose;

  /**
   * Supplies the product, default engine/capability and rows of table/engine/capability metadata.
   */
  public StorageEngineJdbc(
      String product, String defaultEngine, String defaultTransactions, String[]... tables) {
    DatabaseMetaData metadata =
        proxy(
            DatabaseMetaData.class,
            (method, args) -> {
              if (method.equals("getDatabaseProductName")) {
                return product;
              }
              // Product-level capabilities cannot detect a mixed-engine database.
              throw new AssertionError("Unexpected metadata call: " + method);
            });
    connection =
        proxy(
            Connection.class,
            (method, args) -> {
              if (method.equals("getMetaData")) {
                return metadata;
              }
              if (method.equals("createStatement")) {
                statements.incrementAndGet();
                return statement(defaultEngine, defaultTransactions, Arrays.asList(tables));
              }
              throw new AssertionError("Unexpected connection call: " + method);
            });
  }

  private Statement statement(String engine, String transactions, List<String[]> tables) {
    return proxy(
        Statement.class,
        (method, args) -> {
          switch (method) {
            case "setQueryTimeout":
              assertTrue((int) args[0] > 0 && (int) args[0] <= 5);
              return null;
            case "executeQuery":
              assertFalse(SwingUtilities.isEventDispatchThread());
              queries.incrementAndGet();
              String sql = (String) args[0];
              assertTrue(sql.startsWith("SELECT "), "Engine inspection must be read-only");
              if (sql.contains("information_schema.TABLES")) {
                assertTrue(sql.contains("t.TABLE_SCHEMA=DATABASE()"));
                if (failTables) {
                  throw new SQLException("Injected table metadata failure");
                }
                return rows(tables, failResultRead);
              }
              assertTrue(sql.contains("@@session.default_storage_engine"));
              return rows(
                  engine == null
                      ? Collections.emptyList()
                      : Collections.singletonList(new String[] {engine, transactions}),
                  false);
            case "close":
              statementsClosed.incrementAndGet();
              if (failStatementClose) {
                throw new SQLException("Injected statement closure failure");
              }
              return null;
            default:
              throw new AssertionError("Unexpected statement call: " + method);
          }
        });
  }

  private ResultSet rows(List<String[]> rows, boolean failRead) {
    results.incrementAndGet();
    AtomicInteger position = new AtomicInteger(-1);
    return proxy(
        ResultSet.class,
        (method, args) -> {
          switch (method) {
            case "next":
              if (failRead) {
                throw new SQLException("Injected result read failure");
              }
              return position.incrementAndGet() < rows.size();
            case "getString":
              return rows.get(position.get())[(int) args[0] - 1];
            case "close":
              resultsClosed.incrementAndGet();
              return null;
            default:
              throw new AssertionError("Unexpected result call: " + method);
          }
        });
  }

  /** Checks all resources, while the connection itself must remain owned by the caller. */
  public void assertClosed() {
    assertEquals(statements.get(), statementsClosed.get());
    assertEquals(results.get(), resultsClosed.get());
  }

  @FunctionalInterface
  private interface Call {
    Object invoke(String method, Object[] args) throws Exception;
  }

  private static <T> T proxy(Class<T> type, Call call) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> call.invoke(method.getName(), args)));
  }
}
