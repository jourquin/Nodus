/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.rules.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.rules.NodeRulesReader;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Scenario storage must cover the UI range in new tables and upgrades of existing projects. */
@Tag("integration")
@ResourceLock("JDBCUtils")
class NodeRulesSchemaTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> fullScenarioRangeWorksWithoutCommittingUnrelatedChanges() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      tests.add(dynamicTest("reader schema/HSQLDB=" + hsql, () -> readerCreatesWideSchema(hsql)));
      tests.add(dynamicTest("editor schema/HSQLDB=" + hsql, () -> editorCreatesWideSchema(hsql)));
      tests.add(dynamicTest("legacy upgrade/HSQLDB=" + hsql, () -> upgradeLegacySchema(hsql)));
    }
    return tests;
  }

  private void readerCreatesWideSchema(boolean hsql) throws Exception {
    try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
      assertTrue(NodeRulesReader.createExclusionsTable("rules"));
      f.execute("INSERT INTO rules VALUES(1," + NodusC.MAXSCENARIOS + ",-1,1,1,2,1,0)");
      assertEquals(1, count(f.connection, "rules"));
    }
  }

  private void editorCreatesWideSchema(boolean hsql) throws Exception {
    try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
      DefaultTableModel model =
          new DefaultTableModel(
              new Object[][] {
                {
                  "" + NodusC.MAXSCENARIOS,
                  "-1",
                  "1",
                  "1",
                  "2",
                  "1",
                  NodeRulesPersistence.SINGLE_ARROW,
                  "1"
                }
              },
              new String[8]);
      assertFalse(NodeRulesPersistence.save(f.project, "rules", 1, NodusC.MAXSCENARIOS, model));
      assertEquals(1, NodeRulesPersistence.load(f.project, "rules", 1, NodusC.MAXSCENARIOS).size());
    }
  }

  private void upgradeLegacySchema(boolean hsql) throws Exception {
    try (DatabaseFixture f = new DatabaseFixture(directory, hsql)) {
      f.execute(
          "CREATE TABLE rules(num INTEGER,scenario NUMERIC(2,0),grp INTEGER,"
              + "mode1 INTEGER,means1 INTEGER,mode2 INTEGER,means2 INTEGER,symetry INTEGER)");
      f.execute("INSERT INTO rules VALUES(1,-1,-1,1,1,2,1,0),(1,99,-1,2,1,1,1,0)");
      f.execute("CREATE TABLE unrelated(marker INTEGER)");
      f.connection.setAutoCommit(false);
      final Savepoint earlier = f.connection.setSavepoint();
      f.execute("INSERT INTO unrelated VALUES(42)");
      NodusProject project =
          new NodusProject(null) {
            @Override
            public Connection getMainJDBCConnection() {
              return f.connection;
            }

            @Override
            public String getLocalProperty(String key) {
              return getLocalProperty(key, (String) null);
            }

            @Override
            public String getLocalProperty(String key, String fallback) {
              return NodusC.PROP_EXC_TABLE.equals(key) ? "rules" : fallback;
            }
          };
      NodeRulesReader.fixExclusionTableIfNeeded(project);
      NodeRulesReader.fixExclusionTableIfNeeded(project);
      assertFalse(f.connection.getAutoCommit());
      assertEquals(2, count(f.connection, "rules"));
      assertEquals(1, count(f.connection, "unrelated"));
      f.execute("INSERT INTO rules VALUES(1," + NodusC.MAXSCENARIOS + ",-1,1,1,2,1,0)");
      f.connection.rollback(earlier);
      assertEquals(0, count(f.connection, "unrelated"));
      assertEquals(2, count(f.connection, "rules"));
    }
  }

  private static int count(Connection connection, String table) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      assertTrue(rows.next());
      return rows.getInt(1);
    }
  }
}
