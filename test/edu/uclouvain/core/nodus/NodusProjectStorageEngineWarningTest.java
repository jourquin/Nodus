/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.MapBean;
import com.bbn.openmap.layer.location.NodusLocationLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.testing.StorageEngineJdbc;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Warning ordering and lifecycle checks, without windows, project files or a database server. */
@ResourceLock(Resources.SYSTEM_ERR)
class NodusProjectStorageEngineWarningTest {
  @TempDir Path directory;

  @Test
  void mixedEnginesWarnOnceBeforeOpeningCompletes() throws Exception {
    StorageEngineJdbc jdbc =
        new StorageEngineJdbc(
            "MariaDB",
            "InnoDB",
            "YES",
            new String[] {"network", "MyISAM", "NO"},
            new String[] {"parameters", "InnoDB", "YES"});
    Project project = project(jdbc.connection, true);
    check(project);
    assertEquals(1, project.warnings.size());
    assertTrue(project.warnings.get(0).contains("MyISAM"));
    assertTrue(project.warnings.get(0).contains("InnoDB"));
    assertTrue(project.getLocalProperty(NodusC.PROP_SHOW_STORAGE_ENGINE_WARNING, true));
    check(project);
    assertEquals(1, project.warnings.size());
    assertEquals(2, jdbc.queries.get(), "The second call must not repeat metadata queries");
    jdbc.assertClosed();
  }

  @Test
  void transactionalTablesAndDefaultDoNotInterruptOpening() throws Exception {
    StorageEngineJdbc jdbc =
        new StorageEngineJdbc(
            "MariaDB", "InnoDB", "YES", new String[] {"network", "InnoDB", "YES"});
    Project project = project(jdbc.connection, true);
    check(project);
    assertTrue(project.warnings.isEmpty());
    jdbc.assertClosed();
  }

  @Test
  void emptyDatabaseWithMyIsamDefaultStillWarns() throws Exception {
    StorageEngineJdbc jdbc = new StorageEngineJdbc("MariaDB", "MyISAM", "NO");
    Project project = project(jdbc.connection, true);
    check(project);
    assertEquals(1, project.warnings.size());
    assertTrue(project.warnings.get(0).contains("MyISAM"));
    jdbc.assertClosed();
  }

  @Test
  void unavailableMetadataWarnsWithoutPreventingOpening() throws Exception {
    StorageEngineJdbc jdbc = new StorageEngineJdbc("MariaDB", "InnoDB", "YES");
    jdbc.failTables = true;
    Project project = project(jdbc.connection, true);
    check(project);
    assertEquals(1, project.warnings.size());
    assertFalse(project.warnings.get(0).contains("Injected"));
    check(project);
    assertEquals(1, project.warnings.size());
    jdbc.assertClosed();
  }

  @Test
  void embeddedDatabasesDoNotRunMySqlQueries() throws Exception {
    StorageEngineJdbc jdbc = new StorageEngineJdbc("H2", null, null);
    Project project = project(jdbc.connection, false);
    check(project);
    assertEquals(0, jdbc.queries.get());
    assertTrue(project.warnings.isEmpty());
  }

  @Test
  void replacingProjectDuringWarningPreventsOldProjectCompletion() throws Exception {
    StorageEngineJdbc jdbc = new StorageEngineJdbc("MariaDB", "MyISAM", "NO");
    Project project = project(jdbc.connection, true);
    project.suppressWarning = true;
    project.duringWarning =
        () -> {
          set(project, "jdbcConnection", null);
          set(project, "localProperties", new Properties());
        };
    AtomicInteger completions = new AtomicInteger();
    SwingUtilities.invokeAndWait(
        () -> project.checkStorageEnginesBeforeOpening(completions::incrementAndGet));
    assertTrue(project.warned.await(5, TimeUnit.SECONDS));
    SwingUtilities.invokeAndWait(() -> {});
    assertEquals(0, completions.get());
    assertTrue(project.getLocalProperty(NodusC.PROP_SHOW_STORAGE_ENGINE_WARNING, true));
    jdbc.assertClosed();
  }

  @Test
  void optOutSurvivesSavingAndReloadingOnlyThisProjectsLocalSettings() throws Exception {
    final Path main =
        Files.writeString(directory.resolve("project.nodus"), "# keep project file\n");
    final MapBean map = new MapBean(false);
    NodusMapPanel panel =
        new NodusMapPanel() {
          private static final long serialVersionUID = 1L;

          @Override
          public MapBean getMapBean() {
            return map;
          }
        };
    try {
      StorageEngineJdbc jdbc = new StorageEngineJdbc("MariaDB", "MyISAM", "NO");
      Project first = new Project(panel);
      set(first, "jdbcConnection", jdbc.connection);
      set(first, "monitorMainConnection", true);
      first.suppressWarning = true;
      check(first);
      assertFalse(first.getLocalProperty(NodusC.PROP_SHOW_STORAGE_ENGINE_WARNING, true));
      set(first, "projectResourceFileNameAndPath", main.toString());
      set(first, "nodeLayers", new NodusEsriLayer[0]);
      set(first, "linkLayers", new NodusEsriLayer[0]);
      NodusLocationLayer labels = new NodusLocationLayer();
      labels.setName("labels");
      set(first, "labelsLayer", labels);
      Method save = NodusProject.class.getDeclaredMethod("saveProjectLocalProperties");
      save.setAccessible(true);
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              save.invoke(first);
            } catch (ReflectiveOperationException failure) {
              throw new AssertionError(failure);
            }
          });

      Properties saved = new Properties();
      try (InputStream input = Files.newInputStream(directory.resolve("project.nodus.local"))) {
        saved.load(input);
      }
      assertEquals("false", saved.getProperty(NodusC.PROP_SHOW_STORAGE_ENGINE_WARNING));
      assertEquals("# keep project file\n", Files.readString(main));

      Project reopened = project(jdbc.connection, true);
      set(reopened, "localProperties", saved);
      check(reopened);
      assertTrue(reopened.warnings.isEmpty());
      assertEquals(4, jdbc.queries.get(), "Suppression hides the dialog, not the metadata check");

      Project other = project(jdbc.connection, true);
      check(other);
      assertEquals(1, other.warnings.size());
      jdbc.assertClosed();
    } finally {
      map.dispose();
    }
  }

  private static Project project(Connection connection, boolean mysql) {
    Project project = new Project();
    set(project, "jdbcConnection", connection);
    set(project, "monitorMainConnection", mysql);
    return project;
  }

  private static void check(Project project) throws Exception {
    CountDownLatch complete = new CountDownLatch(1);
    SwingUtilities.invokeAndWait(
        () ->
            project.checkStorageEnginesBeforeOpening(
                () -> {
                  assertTrue(SwingUtilities.isEventDispatchThread());
                  complete.countDown();
                }));
    assertTrue(complete.await(5, TimeUnit.SECONDS), "Engine check must finish opening");
  }

  private static void set(Project project, String name, Object value) {
    try {
      Field field = NodusProject.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(project, value);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
  }

  private static final class Project extends NodusProject {
    final List<String> warnings = new CopyOnWriteArrayList<>();
    final CountDownLatch warned = new CountDownLatch(1);
    Runnable duringWarning = () -> {};
    boolean suppressWarning;

    Project() {
      this(null);
    }

    Project(NodusMapPanel panel) {
      super(panel);
      set(this, "localProperties", new Properties());
    }

    @Override
    void presentStorageEngineWarning(String message, JCheckBox doNotShowAgain) {
      assertTrue(SwingUtilities.isEventDispatchThread());
      assertFalse(doNotShowAgain.isSelected());
      assertFalse(doNotShowAgain.getText().isBlank());
      doNotShowAgain.setSelected(suppressWarning);
      warnings.add(message);
      duringWarning.run();
      warned.countDown();
    }
  }
}
