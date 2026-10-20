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

package edu.uclouvain.core.nodus.mappanel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusPlugin;
import edu.uclouvain.core.nodus.testing.PluginTestJars;
import edu.uclouvain.core.nodus.utils.PluginsLoader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Broken external plugins must not strand healthy instances, menu commands or class loaders. */
@ResourceLock(Resources.SYSTEM_ERR)
@Timeout(40)
class PluginDiscoveryTest {
  private static PluginTestJars jars;
  @TempDir Path directory;
  private final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
  private PrintStream originalError;
  private PrintStream capturedError;

  @BeforeAll
  static void compileFixtures(@TempDir Path directory) throws Exception {
    jars = new PluginTestJars(directory);
  }

  @BeforeEach
  void captureDiagnostics() {
    originalError = System.err;
    capturedError = new PrintStream(diagnostics, true, StandardCharsets.UTF_8);
    System.setErr(capturedError);
  }

  @AfterEach
  void restoreDiagnostics() {
    System.setErr(originalError);
    capturedError.close();
  }

  @TestFactory
  Stream<DynamicTest> rejectedPluginsDoNotInterruptHealthyCommandsOrLeakInstances() {
    return Stream.of(
            "ConstructorFailure",
            "StaticFailure",
            "PropertiesFailure",
            "PropertiesLinkageFailure",
            "CheckedPropertiesFailure",
            "CheckedDisposeFailure",
            "NullProperties",
            "BindFailure",
            "DisposeFailure",
            "DetachFailure",
            "TextMenuId",
            "UnknownMenuId",
            "NegativeMenuId",
            "TextItemId",
            "NegativeItemId",
            "OverflowItemId",
            "BlankMenu",
            "MissingMenu",
            "MissingText")
        .flatMap(
            fault ->
                Stream.of(false, true)
                    .map(
                        project ->
                            dynamicTest(
                                fault + ", project=" + project,
                                () -> checkRejectedPlugin(fault, project))));
  }

  private void checkRejectedPlugin(String fault, boolean project) throws Exception {
    diagnostics.reset();
    final Path scope = Files.createTempDirectory(directory, "plugins-");
    final Path plugins = scope.resolve("project");
    jars.write(plugins.resolve("mixed.jar"), "resource", "HealthyA", fault, "HealthyB");
    final Path global = scope.resolve("global");
    if (project) {
      jars.write(global.resolve("global.jar"), "resource", "HealthyLazy");
    }
    try (Fixture fixture = new Fixture()) {
      SwingUtilities.invokeAndWait(
          () -> {
            if (project) {
              fixture.manager.loadPlugins(global.toString(), false);
            }
            assertDoesNotThrow(() -> fixture.manager.loadPlugins(plugins.toString(), project));
            fixture.manager.enableMenus(true);
            assertEquals(
                6, fixture.menus.getMenuCount(), "Rejected plugins must leave no extra menus");
            assertEquals(project ? 3 : 2, fixture.menu("Extensions").getItemCount());
            assertNull(fixture.itemOrNull(fault));
            fixture.click("HealthyA");
            fixture.click("HealthyB");
            for (String name : List.of("HealthyA", "HealthyB")) {
              assertSame(
                  fixture.panel.retrieveObject("instance." + name),
                  fixture.panel.retrieveObject("executed." + name));
              assertNull(fixture.panel.retrieveObject("disposed." + name));
            }
            NodusPlugin rejected = (NodusPlugin) fixture.panel.retrieveObject("instance." + fault);
            if (rejected != null) {
              assertNull(rejected.getNodusMapPanel());
              assertEquals(1, fixture.panel.retrieveObject("disposed." + fault));
            } else {
              assertTrue(fault.equals("ConstructorFailure") || fault.equals("StaticFailure"));
            }
            if (project) {
              fixture.click("HealthyLazy");
              assertEquals("helper:resource", fixture.panel.retrieveObject("resource"));
            }
          });
    }
    assertTrue(
        diagnostics.toString(StandardCharsets.UTF_8).contains(fault),
        "Rejected plugin diagnostics must identify " + fault);
  }

  @Test
  void discoverySkipsBrokenSignaturesSuperclassAndNoninstantiableHelpers() throws Exception {
    jars.write(
        directory.resolve("mixed.jar"),
        "resource",
        "HealthyA",
        "MissingSignature",
        "MissingSuperclass",
        "PrivateConstructor",
        "NoDefaultConstructor",
        "AbstractPlugin",
        "OrdinaryClass",
        "HealthyB",
        "DiscoveryProbe");
    try (PluginsLoader loader = assertDoesNotThrow(() -> new PluginsLoader(directory.toString()))) {
      assertEquals(
          List.of("HealthyA", "HealthyB", "DiscoveryProbe"),
          loader
              .getAvailablePlugins()
              .stream()
              .map(Class::getSimpleName)
              .collect(Collectors.toList()));
      assertFalse(
          Files.exists(jars.initializationMarker), "Discovery must not initialize plugin classes");
    }
    String output = diagnostics.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("MissingSignature"), output);
    assertTrue(output.contains("MissingSuperclass"), output);
  }

  @Test
  void corruptArchivesAndClassFilesDoNotHideLaterPluginsAndDuplicatesAreLoadedOnce()
      throws Exception {
    Files.writeString(directory.resolve("a-corrupt.jar"), "not a ZIP archive");
    try (JarOutputStream jar =
        new JarOutputStream(Files.newOutputStream(directory.resolve("b-broken-class.jar")))) {
      jar.putNextEntry(new JarEntry("fixture/Unreadable.class"));
      jar.write(new byte[] {0, 1, 2});
      jar.closeEntry();
    }
    jars.write(directory.resolve("c-valid.jar"), "resource", "HealthyA");
    Files.copy(directory.resolve("c-valid.jar"), directory.resolve("d-duplicate.jar"));
    try (PluginsLoader loader = new PluginsLoader(directory.toString())) {
      assertEquals(
          List.of("HealthyA"),
          loader
              .getAvailablePlugins()
              .stream()
              .map(Class::getSimpleName)
              .collect(Collectors.toList()));
    }
    String output = diagnostics.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("a-corrupt.jar"), output);
    assertTrue(output.contains("Unreadable"), output);
  }

  @Test
  void dependenciesCanLiveInAnotherJarAndResourcesRemainLazyUntilExecution() throws Exception {
    jars.write(
        directory.resolve("a-plugin.jar"), "resource", "PropertiesLinkageFailure", "HealthyLazy");
    jars.write(directory.resolve("z-dependency.jar"), "resource", "Missing");
    try (Fixture fixture = new Fixture()) {
      SwingUtilities.invokeAndWait(
          () -> {
            fixture.manager.loadPlugins(directory.toString(), true);
            assertNull(fixture.panel.retrieveObject("resource"));
            fixture.manager.enableMenus(true);
            fixture.click("PropertiesLinkageFailure");
            fixture.click("HealthyLazy");
            assertEquals("helper:resource", fixture.panel.retrieveObject("resource"));
          });
    }
    assertEquals("", diagnostics.toString(StandardCharsets.UTF_8));
  }

  @Test
  void missingExecutionDependencyLeavesOtherPluginCommandsUsable() throws Exception {
    jars.write(directory.resolve("plugins.jar"), "resource", "ExecuteMissing", "HealthyB");
    try (Fixture fixture = new Fixture()) {
      SwingUtilities.invokeAndWait(
          () -> {
            fixture.manager.loadPlugins(directory.toString(), true);
            fixture.manager.enableMenus(true);
            assertDoesNotThrow(() -> fixture.click("ExecuteMissing"));
            fixture.click("HealthyB");
            assertNotNull(fixture.panel.retrieveObject("executed.HealthyB"));
          });
    }
    assertTrue(diagnostics.toString(StandardCharsets.UTF_8).contains("ExecuteMissing"));
    assertTrue(diagnostics.toString(StandardCharsets.UTF_8).contains("Missing"));
  }

  @Test
  void rejectedOrEmptyGlobalBatchDoesNotChangeExistingCommandOffsets() throws Exception {
    final Path global = directory.resolve("global");
    final Path broken = directory.resolve("broken");
    final Path project = directory.resolve("project");
    jars.write(global.resolve("global.jar"), "resource", "HealthyA");
    jars.write(broken.resolve("bad.jar"), "resource", "NullProperties");
    jars.write(project.resolve("project.jar"), "resource", "HealthyB");
    try (Fixture fixture = new Fixture()) {
      SwingUtilities.invokeAndWait(
          () -> {
            fixture.manager.loadPlugins(global.toString(), false);
            fixture.manager.loadPlugins(broken.toString(), false);
            NodusPlugin rejected =
                (NodusPlugin) fixture.panel.retrieveObject("instance.NullProperties");
            assertNull(rejected.getClass().getClassLoader().getResource("plugin-data.txt"));
            fixture.manager.loadPlugins(directory.resolve("absent").toString(), false);
            fixture.manager.loadPlugins(project.toString(), true);
            fixture.manager.enableMenus(true);
            fixture.click("HealthyA");
            fixture.click("HealthyB");
            assertNotNull(fixture.panel.retrieveObject("executed.HealthyA"));
            assertNotNull(fixture.panel.retrieveObject("executed.HealthyB"));
            fixture.manager.removeProjectPlugins();
            fixture.panel.removeStoredObject("executed.HealthyA");
            fixture.click("HealthyA");
            assertNotNull(fixture.panel.retrieveObject("executed.HealthyA"));
          });
    }
  }

  @Test
  void customMenusIgnoreStandardMenuIdsAndLargeInsertionPositionsStillAppend() throws Exception {
    jars.write(directory.resolve("plugins.jar"), "resource", "CustomMenuWins", "AppendItem");
    try (Fixture fixture = new Fixture()) {
      SwingUtilities.invokeAndWait(
          () -> {
            fixture.manager.loadPlugins(directory.toString(), true);
            fixture.manager.enableMenus(true);
            fixture.click("CustomMenuWins");
            fixture.click("AppendItem");
            assertEquals("AppendItem", fixture.menu("Tools").getItem(0).getText());
            assertNotNull(fixture.panel.retrieveObject("executed.CustomMenuWins"));
            assertNotNull(fixture.panel.retrieveObject("executed.AppendItem"));
          });
    }
    assertEquals("", diagnostics.toString(StandardCharsets.UTF_8));
  }

  private static final class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
  }

  private static final class Fixture implements AutoCloseable {
    final Panel panel = new Panel();
    final JMenuBar menus = new JMenuBar();
    final JMenu file = new JMenu("File");
    final JMenu project = new JMenu("Project");
    final JMenu control = new JMenu("Control");
    final JMenu tools = new JMenu("Tools");
    final JMenu help = new JMenu("Help");
    final MapPluginManager manager =
        new MapPluginManager(panel, menus, file, project, control, tools, help);

    Fixture() {
      for (JMenu menu : List.of(file, project, control, tools, help)) {
        menus.add(menu);
      }
    }

    JMenu menu(String text) {
      for (int index = 0; index < menus.getMenuCount(); index++) {
        if (menus.getMenu(index).getText().equals(text)) {
          return menus.getMenu(index);
        }
      }
      throw new AssertionError("Missing menu " + text);
    }

    JMenuItem itemOrNull(String text) {
      for (int index = 0; index < menus.getMenuCount(); index++) {
        JMenu menu = menus.getMenu(index);
        for (int item = 0; item < menu.getItemCount(); item++) {
          JMenuItem candidate = menu.getItem(item);
          if (candidate != null && candidate.getText().equals(text)) {
            return candidate;
          }
        }
      }
      return null;
    }

    void click(String text) {
      JMenuItem item = itemOrNull(text);
      assertNotNull(item, "Missing plugin menu item " + text);
      item.doClick(0);
    }

    @Override
    public void close() throws Exception {
      SwingUtilities.invokeAndWait(
          () -> {
            manager.disposeAllPlugins();
            panel.dispose();
          });
    }
  }
}
