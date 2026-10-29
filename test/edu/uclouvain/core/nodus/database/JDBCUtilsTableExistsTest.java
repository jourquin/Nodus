/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("JDBCUtils")
class JDBCUtilsTableExistsTest {
  @TempDir Path directory;

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
                                    f.execute("CREATE TABLE probeXname(id INTEGER)");
                                    assertFalse(JDBCUtils.tableExists(name));
                                    assertFalse(JDBCUtils.tableExists(f.connection, name));
                                    f.execute(
                                        "CREATE TABLE "
                                            + JDBCUtils.getQuotedCompliantIdentifier(name)
                                            + "(id INTEGER)");
                                    assertTrue(JDBCUtils.tableExists(name));
                                    assertTrue(JDBCUtils.tableExists(f.connection, name));
                                    assertFalse(JDBCUtils.tableExists((String) null));
                                    assertFalse(JDBCUtils.tableExists(""));
                                  }
                                })));
  }
}
