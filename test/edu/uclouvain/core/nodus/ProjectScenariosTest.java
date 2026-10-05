/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("JDBCUtils")
class ProjectScenariosTest {
  @TempDir Path directory;

  @Test
  void renameAndRemoveKeepDatabasePropertiesAndOverrideCallbacksTogether() throws Exception {
    AtomicInteger refreshed = new AtomicInteger();
    AtomicInteger renamed = new AtomicInteger();
    AtomicInteger removed = new AtomicInteger();
    NodusMapPanel panel =
        new NodusMapPanel() {
          private static final long serialVersionUID = 1L;

          @Override
          public void updateScenarioComboBox(boolean forceReset) {
            assertFalse(forceReset);
            refreshed.incrementAndGet();
          }
        };
    NodusProject project =
        new NodusProject(panel) {
          @Override
          public void renameLocalProperty(String oldKey, String newKey) {
            renamed.incrementAndGet();
            super.renameLocalProperty(oldKey, newKey);
          }

          @Override
          public void removeLocalProperty(String key) {
            removed.incrementAndGet();
            super.removeLocalProperty(key);
          }
        };
    Properties properties = new Properties();
    for (Field constant : NodusC.class.getFields()) {
      if (constant.getType() == String.class && constant.getName().startsWith("PROP_")) {
        String key = (String) constant.get(null);
        properties.setProperty(key + "1", "saved " + key);
      }
    }
    properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "compat");
    properties.setProperty(NodusC.PROP_VNET_TABLE, "network_");
    properties.setProperty(NodusC.PROP_PATH_TABLE_PREFIX, "paths_");
    Field local = NodusProject.class.getDeclaredField("localProperties");
    local.setAccessible(true);
    local.set(project, properties);
    try (DatabaseFixture database = new DatabaseFixture(directory)) {
      database.execute("CREATE TABLE network_1 (marker INTEGER)");
      database.execute("INSERT INTO network_1 VALUES (42)");
      database.execute("CREATE TABLE paths_1" + NodusC.SUFFIX_HEADER + " (marker INTEGER)");
      database.execute("CREATE TABLE paths_1" + NodusC.SUFFIX_DETAIL + " (marker INTEGER)");
      database.execute("CREATE TABLE network_9 (marker INTEGER)");
      project.renameScenario(1, 2);
      assertFalse(JDBCUtils.tableExists("network_1"));
      assertTrue(JDBCUtils.tableExists("network_2"));
      assertTrue(JDBCUtils.tableExists("paths_2" + NodusC.SUFFIX_HEADER));
      assertTrue(JDBCUtils.tableExists("paths_2" + NodusC.SUFFIX_DETAIL));
      try (java.sql.Statement statement = database.connection.createStatement();
          java.sql.ResultSet result = statement.executeQuery("SELECT marker FROM network_2")) {
        assertTrue(result.next());
        assertEquals(42, result.getInt(1));
      }
      assertEquals(
          "saved " + NodusC.PROP_COST_FUNCTIONS,
          project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "2"));
      assertNull(project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "1"));
      assertEquals(2, project.getCurrentScenario());
      assertTrue(renamed.get() > 0, "Keep virtual property migration hooks");
      int removalsAfterRename = removed.get();
      project.removeScenario(2);
      assertFalse(JDBCUtils.tableExists("network_2"));
      assertFalse(JDBCUtils.tableExists("paths_2" + NodusC.SUFFIX_HEADER));
      assertFalse(JDBCUtils.tableExists("paths_2" + NodusC.SUFFIX_DETAIL));
      assertTrue(JDBCUtils.tableExists("network_9"));
      assertNull(project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + "2"));
      assertTrue(removed.get() > removalsAfterRename, "Keep virtual property removal hooks");
      assertEquals(2, refreshed.get());
    } finally {
      panel.dispose();
    }
  }
}
