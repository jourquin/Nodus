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

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.services.ServiceHandler;
import edu.uclouvain.core.nodus.utils.CommentedProperties;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("JDBCUtils")
@Timeout(30)
class ProjectTableNamesTest {
  @TempDir Path directory;

  @Test
  void h2RenamesEveryTableFamilyAndPersistsThePreferences() throws Exception {
    checkTableFamilies(false);
  }

  @Test
  void hsqldbRenamesEveryTableFamilyAndPersistsThePreferences() throws Exception {
    checkTableFamilies(true);
  }

  private void checkTableFamilies(boolean hsql) throws Exception {
    try (Fixture fixture = new Fixture(hsql)) {
      ServiceHandler services = new ServiceHandler(fixture.project, false);
      set(fixture.project, "serviceHandler", services);
      Map<String, String> expected =
          Map.of(
              "explicit_rules", "renamed rules",
              "sample_vnet0", "net0",
              "sample_vnet19", "net19",
              "sample_path1_header", "route1_header",
              "sample_path9_detail", "route9_detail",
              "sample_service_header", "lines_header",
              "sample_service_links", "lines_links",
              "sample_service_stops", "lines_stops");
      List<String> unrelated =
          List.of(
              "explicit_rules_old",
              "sample_vnet_archive",
              "sample_vnet02",
              "sampleXvnet19",
              "sample_path9_backup",
              "sample_service_manual");
      for (String source : expected.keySet()) {
        fixture.create(source);
      }
      for (String table : unrelated) {
        fixture.create(table);
      }
      fixture.execute("CREATE INDEX saved_index ON sample_path1_header (qty)");
      fixture.connection.setAutoCommit(false);
      Map<String, String> edits =
          Map.of(
              NodusC.PROP_EXC_TABLE, "renamed rules",
              NodusC.PROP_VNET_TABLE, "net",
              NodusC.PROP_PATH_TABLE_PREFIX, "route",
              NodusC.PROP_SERVICES_TABLE_PREFIX, "lines");

      ProjectTableNames.Plan plan = ProjectTableNames.inspect(fixture.project, edits);
      assertEquals(expected.size(), plan.getRenames().size());
      expected.forEach(
          (source, target) -> assertEquals(name(target), plan.getRenames().get(name(source))));
      assertEquals("explicit_rules", fixture.project.getProperty(NodusC.PROP_EXC_TABLE));
      assertTrue(fixture.has("sample_path1_header"));

      plan.apply();

      for (Map.Entry<String, String> entry : expected.entrySet()) {
        assertFalse(fixture.has(entry.getKey()));
        assertEquals(7, fixture.quantity(entry.getValue()));
      }
      for (String table : unrelated) {
        assertEquals(7, fixture.quantity(table));
      }
      assertEquals(name("lines_header"), services.getServiceHeaderTableName());
      assertTrue(fixture.hasIndex("route1_header", "saved_index"));
      assertThrows(
          SQLException.class, () -> fixture.execute("INSERT INTO route1_header VALUES (1, 30)"));
      fixture.connection.rollback();
      Properties saved = fixture.saved();
      edits.forEach(
          (key, value) -> {
            assertEquals(value, fixture.project.getLocalProperty(key));
            assertEquals(value, saved.getProperty(key));
          });
      assertEquals("retained", saved.getProperty("other.setting"));
    }
  }

  @Test
  void destinationViewsAndChangesAfterConfirmationPreventRenaming() throws Exception {
    try (Fixture fixture = new Fixture(false)) {
      fixture.create("sample_vnet0");
      fixture.create("sample_vnet1");
      Map<String, String> edits = Map.of(NodusC.PROP_VNET_TABLE, "new_net");
      fixture.execute("CREATE VIEW new_net1 AS SELECT * FROM sample_vnet1");
      assertThrows(SQLException.class, () -> ProjectTableNames.inspect(fixture.project, edits));
      fixture.execute("DROP VIEW new_net1");

      ProjectTableNames.Plan plan = ProjectTableNames.inspect(fixture.project, edits);
      fixture.create("new_net1");
      assertThrows(SQLException.class, plan::apply);
      assertTrue(fixture.has("sample_vnet0"));
      assertTrue(fixture.has("sample_vnet1"));
      assertFalse(fixture.has("new_net0"));
      assertNull(fixture.saved().getProperty(NodusC.PROP_VNET_TABLE));
    }
  }

  @Test
  void failedProjectFileSaveRestoresBothNamesAndKeepsPendingWorkIsolated() throws Exception {
    try (Fixture fixture = new Fixture(false)) {
      fixture.create("explicit_rules");
      fixture.create("sample_vnet2");
      fixture.create("pending");
      fixture.connection.setAutoCommit(false);
      fixture.execute("INSERT INTO pending VALUES (2, 8)");
      Path unwritable = Files.createDirectory(directory.resolve("blocked.nodus"));
      set(fixture.project, "projectResourceFileNameAndPath", unwritable.toString());
      ProjectTableNames.Plan plan =
          ProjectTableNames.inspect(
              fixture.project,
              Map.of(NodusC.PROP_EXC_TABLE, "new_rules", NodusC.PROP_VNET_TABLE, "new_vnet"));

      assertThrows(UncheckedIOException.class, plan::apply);

      assertTrue(fixture.has("explicit_rules"));
      assertTrue(fixture.has("sample_vnet2"));
      assertFalse(fixture.has("new_rules"));
      assertFalse(fixture.has("new_vnet2"));
      assertEquals("explicit_rules", fixture.project.getLocalProperty(NodusC.PROP_EXC_TABLE));
      assertEquals("explicit_rules", fixture.project.getProperty(NodusC.PROP_EXC_TABLE));
      assertNull(fixture.project.getProperty(NodusC.PROP_VNET_TABLE));
      fixture.connection.rollback();
      assertEquals(7, fixture.quantity("pending"));
    }
  }

  @Test
  void changedNameWithoutExistingTablesIsStillSaved() throws Exception {
    try (Fixture fixture = new Fixture(true)) {
      ProjectTableNames.Plan plan =
          ProjectTableNames.inspect(
              fixture.project, Map.of(NodusC.PROP_PATH_TABLE_PREFIX, "new_paths"));
      assertTrue(plan.hasChanges());
      assertTrue(plan.getRenames().isEmpty());
      plan.apply();
      assertEquals("new_paths", fixture.saved().getProperty(NodusC.PROP_PATH_TABLE_PREFIX));
      assertFalse(
          ProjectTableNames.inspect(
                  fixture.project, Map.of(NodusC.PROP_PATH_TABLE_PREFIX, "new_paths"))
              .hasChanges());
    }
  }

  @Test
  void tablesInAnotherSchemaAreOutsideThePlan() throws Exception {
    try (Fixture fixture = new Fixture(false)) {
      fixture.create("sample_vnet0");
      fixture.execute("CREATE SCHEMA other");
      fixture.execute("CREATE TABLE other.sample_vnet0 (id INT)");
      fixture.execute("CREATE TABLE other.new_vnet0 (id INT)");
      ProjectTableNames.Plan plan =
          ProjectTableNames.inspect(fixture.project, Map.of(NodusC.PROP_VNET_TABLE, "new_vnet"));
      assertEquals(Map.of(name("sample_vnet0"), name("new_vnet0")), plan.getRenames());
      plan.apply();
      assertEquals(7, fixture.quantity("new_vnet0"));
      try (Statement statement = fixture.connection.createStatement();
          ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM other.sample_vnet0")) {
        assertTrue(rows.next());
        assertEquals(0, rows.getInt(1));
      }
    }
  }

  private static String name(String name) {
    return JDBCUtils.getCompliantIdentifier(name);
  }

  private static void set(NodusProject project, String name, Object value) throws Exception {
    Field field = NodusProject.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(project, value);
  }

  private final class Fixture implements AutoCloseable {
    final NodusProject project = new NodusProject(null);
    final Path projectFile = directory.resolve("sample.nodus");
    final Connection connection;

    Fixture(boolean hsql) throws Exception {
      String url = (hsql ? "jdbc:hsqldb:mem:" : "jdbc:h2:mem:") + UUID.randomUUID();
      connection = DriverManager.getConnection(url, "sa", "");
      assertTrue(JDBCUtils.setConnection(connection));
      CommentedProperties settings = new CommentedProperties();
      settings.setProperty(NodusC.PROP_EXC_TABLE, "explicit_rules");
      settings.setProperty(NodusC.PROP_SERVICES_TABLE_PREFIX, "sample_service");
      settings.setProperty("other.setting", "retained");
      Properties local = new Properties();
      local.putAll(settings);
      local.setProperty(NodusC.PROP_PROJECT_DOTNAME, "sample");
      local.setProperty(NodusC.PROP_JDBC_URL, url);
      local.setProperty(NodusC.PROP_JDBC_USERNAME, "sa");
      set(project, "projectProperties", settings);
      set(project, "localProperties", local);
      set(project, "jdbcConnection", connection);
      set(project, "projectResourceFileNameAndPath", projectFile.toString());
      project.saveProperties();
    }

    void create(String table) throws SQLException {
      String sqlName = JDBCUtils.getQuotedCompliantIdentifier(table);
      execute("CREATE TABLE " + sqlName + " (id INTEGER PRIMARY KEY, qty INTEGER)");
      execute("INSERT INTO " + sqlName + " VALUES (1, 7)");
    }

    boolean has(String table) throws SQLException {
      return JDBCUtils.tableExists(connection, table);
    }

    int quantity(String table) throws SQLException {
      try (Statement statement = connection.createStatement();
          ResultSet rows =
              statement.executeQuery(
                  "SELECT SUM(qty) FROM " + JDBCUtils.getQuotedCompliantIdentifier(table))) {
        assertTrue(rows.next());
        return rows.getInt(1);
      }
    }

    boolean hasIndex(String table, String index) throws SQLException {
      try (ResultSet rows =
          connection
              .getMetaData()
              .getIndexInfo(
                  connection.getCatalog(), connection.getSchema(), name(table), false, false)) {
        while (rows.next()) {
          if (name(index).equals(rows.getString("INDEX_NAME"))) {
            return true;
          }
        }
        return false;
      }
    }

    void execute(String sql) throws SQLException {
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate(sql);
      }
    }

    Properties saved() throws Exception {
      Properties properties = new Properties();
      try (InputStream stream = Files.newInputStream(projectFile)) {
        properties.load(stream);
      }
      return properties;
    }

    @Override
    public void close() throws SQLException {
      JDBCUtils.setConnection(null);
      connection.close();
    }
  }
}
