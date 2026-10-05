/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.database.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Exercises existing compiled subclasses and scripts through the original console API. */
@ResourceLock("JDBCUtils")
class SQLConsoleCompatibilityTest {
  @TempDir Path directory;

  @Test
  void originalSubclassBytecodeKeepsBatchCallsAndOverwriteCallbacks() throws Exception {
    Path fixture = Path.of("test", "fixtures", "sql-console-api", "legacy-sql-console.jar");
    try (DatabaseFixture database = new DatabaseFixture(directory);
        URLClassLoader loader =
            new URLClassLoader(new URL[] {fixture.toUri().toURL()}, getClass().getClassLoader())) {
      Class<?> legacy = loader.loadClass("external.nodus.compat.LegacySQLConsole");
      SQLConsole console =
          (SQLConsole) legacy.getConstructor(NodusProject.class).newInstance(database.project);
      try {
        assertNull(console.getFrame());
        assertEquals(
            true,
            legacy
                .getMethod("batch", String[].class)
                .invoke(
                    console,
                    (Object)
                        new String[] {
                          "CREATE TABLE COMPAT (ID INTEGER)",
                          "INSERT INTO COMPAT VALUES (42)",
                          "EXPORTCSV COMPAT"
                        }));
        Path output = directory.resolve("COMPAT.csv");
        final byte[] original = Files.readAllBytes(output);
        console.getSqlCommandArea().setText("EXPORTCSV COMPAT");
        console.resetScript();
        assertFalse(console.execute(false));
        assertEquals(1, legacy.getField("resets").getInt(console));
        assertEquals(1, legacy.getField("prompts").getInt(console));
        assertTrue(legacy.getField("promptOnEdt").getBoolean(console));
        assertArrayEquals(original, Files.readAllBytes(output));
      } finally {
        legacy.getMethod("close").invoke(console);
      }
      assertFalse(database.connection.isClosed());
    }
  }

  @Test
  void groovyPropertiesAndBatchScriptsKeepTheirBehavior() throws Exception {
    try (DatabaseFixture database = new DatabaseFixture(directory)) {
      Binding binding = new Binding();
      binding.setVariable("project", database.project);
      Object result =
          new GroovyShell(binding)
              .evaluate(
                  "import edu.uclouvain.core.nodus.database.gui.SQLConsole\n"
                      + "def console = new SQLConsole(project, false)\n"
                      + "try {\n"
                      + "  assert console.frame == null\n"
                      + "  assert console.sqlCommandArea.is(console.getSqlCommandArea())\n"
                      + "  console.resetScript()\n"
                      + "  return console.runBatch([\n"
                      + "    '-- comment', '@@id := 42', '@@idLong := 43',\n"
                      + "    'CREATE TABLE SCRIPTED (ID INTEGER)',\n"
                      + "    'INSERT INTO SCRIPTED /* block comment */ "
                      + "VALUES (@@id), (@@idLong)',\n"
                      + "    'STOP', 'INSERT INTO SCRIPTED VALUES (99)'\n"
                      + "  ] as String[])\n"
                      + "} finally { console.windowClosing(null) }\n");
      assertEquals(true, result);
      assertFalse(database.connection.isClosed());
      try (Statement statement = database.connection.createStatement();
          ResultSet rows = statement.executeQuery("SELECT ID FROM SCRIPTED ORDER BY ID")) {
        assertTrue(rows.next());
        assertEquals(42, rows.getInt(1));
        assertTrue(rows.next());
        assertEquals(43, rows.getInt(1));
        assertFalse(rows.next());
      }
    }
  }
}
