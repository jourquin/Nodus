/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.database.gui.SQLConsole;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Guards the source/binary surface and entry points used by scripts and compiled extensions. */
class ExtensionApiCompatibilityTest {
  private static final Path FIXTURES = Path.of("test", "fixtures", "extension-api");

  @Test
  void publicAndProtectedApiMatchesThePreRefactoringClasses() throws Exception {
    assertEquals(Files.readAllLines(FIXTURES.resolve("api.txt")), api());
  }

  @Test
  void sqlConsoleApiMatchesItsPreRefactoringBaseline() throws Exception {
    assertEquals(
        Files.readAllLines(Path.of("test", "fixtures", "sql-console-api", "api.txt")),
        api(SQLConsole.class));
  }

  private static boolean exposed(Member member) {
    return Modifier.isPublic(member.getModifiers()) || Modifier.isProtected(member.getModifiers());
  }

  private static List<String> api() {
    return api(NodusMapPanel.class, NodusProject.class, NodusConsole.class);
  }

  private static List<String> api(Class<?>... types) {
    List<String> lines = new ArrayList<>();
    for (Class<?> type : types) {
      lines.add(type.toGenericString());
      lines.add("extends " + type.getGenericSuperclass().getTypeName());
      Arrays.stream(type.getGenericInterfaces())
          .forEach(i -> lines.add("implements " + i.getTypeName()));
      List<String> members = new ArrayList<>();
      Arrays.stream(type.getDeclaredConstructors())
          .filter(ExtensionApiCompatibilityTest::exposed)
          .forEach(c -> members.add(c.toGenericString()));
      Arrays.stream(type.getDeclaredMethods())
          .filter(ExtensionApiCompatibilityTest::exposed)
          .forEach(m -> members.add(m.toGenericString()));
      Arrays.stream(type.getDeclaredFields())
          .filter(ExtensionApiCompatibilityTest::exposed)
          .forEach(f -> members.add(f.toGenericString()));
      Collections.sort(members);
      lines.addAll(members);
    }
    return lines;
  }

  /** Used once with the original compiled classes to record the compatibility baseline. */
  public static void main(String[] args) throws Exception {
    Files.write(Path.of(args[0]), api());
  }

  @Test
  void originalPluginBytecodeAndSubclassRunAgainstTheRefactoredClasses() throws Exception {
    try (URLClassLoader loader =
        new URLClassLoader(
            new java.net.URL[] {FIXTURES.resolve("legacy-extension.jar").toUri().toURL()},
            getClass().getClassLoader())) {
      NodusMapPanel panel =
          (NodusMapPanel)
              loader
                  .loadClass("external.nodus.compat.LegacyExtension$Panel")
                  .getConstructor()
                  .newInstance();
      NodusPlugin plugin =
          (NodusPlugin)
              loader
                  .loadClass("external.nodus.compat.LegacyExtension")
                  .getConstructor()
                  .newInstance();
      plugin.setNodusMapPanel(panel);
      try {
        SwingUtilities.invokeAndWait(
            () -> {
              // IDE/in-process runs may already have a console open. Compare both API callers
              // in the same EDT turn so window events cannot change visibility between them.
              boolean consoleVisible = NodusConsole.isVisible();
              plugin.execute();
              assertEquals(consoleVisible, panel.retrieveObject("legacy.consoleVisible"));
            });
        assertSame(panel.getNodusProject(), panel.retrieveObject("legacy.project"));
        assertInstanceOf(Properties.class, panel.retrieveObject("legacy.styles"));
        assertSame(
            panel.getNodusProject().getOtherNodeNumbers(), panel.retrieveObject("legacy.nodeIds"));
        assertEquals(42, panel.getNodusProject().getOtherNodeNumbers().get(42));
        assertEquals(false, panel.retrieveObject("legacy.busy"));
        assertEquals(true, panel.retrieveObject("legacy.resetText"));
      } finally {
        SwingUtilities.invokeAndWait(
            () -> {
              try {
                plugin.dispose();
              } finally {
                panel.dispose();
              }
            });
      }
      assertEquals(true, panel.retrieveObject("legacy.disposed"));
      assertNull(plugin.getNodusMapPanel());
    }
  }

  @Test
  void pluginMenusDispatchAndReleaseProjectAndGlobalInstancesAtTheirOriginalScopes()
      throws Exception {
    try (URLClassLoader loader =
        new URLClassLoader(
            new java.net.URL[] {FIXTURES.resolve("legacy-extension.jar").toUri().toURL()},
            getClass().getClassLoader())) {
      NodusMapPanel panel =
          (NodusMapPanel)
              loader
                  .loadClass("external.nodus.compat.LegacyExtension$Panel")
                  .getConstructor()
                  .newInstance();
      // The protected constructor skips the main frame; inspect its menu owner for this fixture.
      java.lang.reflect.Field menusField = NodusMapPanel.class.getDeclaredField("menus");
      menusField.setAccessible(true);
      javax.swing.JMenuBar menuBar = ((MapMenus) menusField.get(panel)).nodusMenuBar;
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              panel.loadPlugins(FIXTURES.toString(), false);
              panel.loadPlugins(FIXTURES.toString(), true);
              assertEquals(1, menuBar.getMenuCount());
              javax.swing.JMenu menu = menuBar.getMenu(0);
              assertEquals(2, menu.getItemCount());
              javax.swing.JMenuItem globalItem = menu.getItem(0);
              javax.swing.JMenuItem projectItem = menu.getItem(1);
              panel.enableMenus(true);
              projectItem.doClick();
              assertSame(panel.getNodusProject(), panel.retrieveObject("legacy.project"));
              panel.removeProjectPlugins();
              assertEquals(1, panel.retrieveObject("legacy.disposeCount"));
              assertEquals(0, projectItem.getActionListeners().length);
              assertEquals(1, menu.getItemCount());
              assertSame(globalItem, menu.getItem(0));
              panel.enableMenus(false);
              assertTrue(globalItem.isEnabled());
              panel.removeStoredObject("legacy.project");
              globalItem.doClick();
              assertSame(panel.getNodusProject(), panel.retrieveObject("legacy.project"));
              panel.removeProjectPlugins();
              assertEquals(1, panel.retrieveObject("legacy.disposeCount"));
            } finally {
              panel.dispose();
            }
            assertEquals(2, panel.retrieveObject("legacy.disposeCount"));
            panel.dispose();
            assertEquals(2, panel.retrieveObject("legacy.disposeCount"));
          });
    }
  }

  @Test
  void groovyKeepsBeanPropertiesOverloadsAndSharedObjectIdentity() throws Exception {
    NodusMapPanel panel =
        new NodusMapPanel() {
          private static final long serialVersionUID = 1L;
          private final NodusProject project = new NodusProject(this);

          @Override
          public NodusProject getNodusProject() {
            return project;
          }
        };
    // The protected constructor deliberately skips project loading in this headless fixture.
    java.lang.reflect.Field properties = NodusProject.class.getDeclaredField("localProperties");
    properties.setAccessible(true);
    properties.set(panel.getNodusProject(), new Properties());
    Binding binding = new Binding();
    binding.setVariable("nodusMapPanel", panel);
    try {
      Object result =
          new GroovyShell(binding)
              .evaluate(
                  "def project = nodusMapPanel.nodusProject\n"
                      + "assert project.nodusMapPanel.is(nodusMapPanel)\n"
                      + "project.setLocalProperty('number', 7)\n"
                      + "project.setLocalProperty('enabled', true)\n"
                      + "assert project.getLocalProperty('number', 0) == 7\n"
                      + "assert project.getLocalProperty('enabled', false)\n"
                      + "def values = nodusMapPanel.storedObjects\n"
                      + "values.put('project', project)\n"
                      + "assert nodusMapPanel.retrieveObject('project').is(project)\n"
                      + "project.otherLinkNumbers.put(9, 9)\n"
                      + "assert project.getOtherLinkNumbers().get(9) == 9\n"
                      + "assert project.getStyleProperties() != null\n"
                      + "return project\n");
      assertSame(panel.getNodusProject(), result);
    } finally {
      panel.dispose();
    }
  }
}
