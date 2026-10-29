/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("JDBCUtils")
class JDBCUtilsTableExistsTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> columnLookupUsesTheConnectionsCurrentLiteralSchema() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                        f.execute("CREATE TABLE scoped_table(wrong_column INTEGER)");
                        for (String schema : List.of("scope_name", "scopeXname")) {
                          f.execute(
                              "CREATE SCHEMA \""
                                  + schema
                                  + "\" AUTHORIZATION "
                                  + (hsql ? "DBA" : "SA"));
                          f.connection.setSchema(schema);
                          f.execute(
                              "CREATE TABLE scoped_table("
                                  + (schema.equals("scope_name") ? "id" : "wrong_column")
                                  + " INTEGER)");
                        }
                        f.connection.setSchema("scope_name");
                        try (ResultSet columns =
                            JDBCUtils.getColumns(f.connection, "scoped_table")) {
                          assertTrue(columns.next());
                          assertEquals("ID", columns.getString("COLUMN_NAME"));
                          assertFalse(columns.next());
                        }
                        assertEquals("scope_name", f.connection.getSchema());
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> specialCharactersAreLiteralInTableNames() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of("probe_name", "probe%name", "probe\\name")
                    .map(
                        name ->
                            DynamicTest.dynamicTest(
                                "HSQLDB=" + hsql + ": " + name,
                                () -> {
                                  try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
                                    f.execute("CREATE TABLE probeXname(other_column INTEGER)");
                                    assertFalse(JDBCUtils.tableExists(name));
                                    assertFalse(JDBCUtils.tableExists(f.connection, name));
                                    f.execute(
                                        "CREATE TABLE "
                                            + JDBCUtils.getQuotedCompliantIdentifier(name)
                                            + "(id INTEGER)");
                                    assertTrue(JDBCUtils.tableExists(name));
                                    assertTrue(JDBCUtils.tableExists(f.connection, name));
                                    List<String> columns = new ArrayList<>();
                                    try (ResultSet rows = JDBCUtils.getColumns(name)) {
                                      while (rows.next()) {
                                        columns.add(rows.getString("COLUMN_NAME"));
                                      }
                                    }
                                    assertEquals(List.of("ID"), columns);
                                    assertFalse(JDBCUtils.tableExists((String) null));
                                    assertFalse(JDBCUtils.tableExists(""));
                                  }
                                })));
  }
}
