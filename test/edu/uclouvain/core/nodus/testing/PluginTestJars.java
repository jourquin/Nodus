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

package edu.uclouvain.core.nodus.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.uclouvain.core.nodus.NodusPlugin;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

/** Compiles temporary external plugins, including dependencies deliberately omitted from JARs. */
public final class PluginTestJars {
  private final Path classes;
  public final Path initializationMarker;

  /** Builds all fixtures once with Java 11 bytecode and the actual application's dependencies. */
  public PluginTestJars(Path directory) throws Exception {
    Files.createDirectories(directory);
    classes = Files.createDirectory(directory.resolve("plugin-classes"));
    initializationMarker = directory.resolve("plugin-initialized");
    final Path sources = Files.createDirectory(directory.resolve("plugin-sources"));
    final Map<String, String> definitions = new LinkedHashMap<>();
    definitions.put(
        "TrackedPlugin",
        "public abstract class TrackedPlugin extends NodusPlugin {public "
            + "int disposals; protected TrackedPlugin() {}public void "
            + "setNodusMapPanel(NodusMapPanel panel) { super.setNodusMapPanel("
            + "panel); if(panel!=null) panel.storeObject(\"instance.\"+getClass("
            + ").getSimpleName(),this); }public Properties getProperties() { "
            + "Properties p=new Properties();p.setProperty(MENU_ITEM__TEXT,"
            + "getClass().getSimpleName());p.setProperty("
            + "USER_DEFINED_MENUBAR_TEXT,\"Extensions\"); return p; }public "
            + "void execute() { getNodusMapPanel().storeObject("
            + "\"executed.\"+getClass().getSimpleName(),this); }public void "
            + "dispose() { disposals++; NodusMapPanel panel=getNodusMapPanel(); "
            + "if(panel!=null) {String key=\"disposed.\"+getClass()"
            + ".getSimpleName(); Object n=panel.retrieveObject(key);"
            + "panel.storeObject(key,n==null?1:((Integer)n)+1); } super.dispose("
            + "); } }");
    definitions.put("HealthyA", plugin("HealthyA", ""));
    definitions.put("HealthyB", plugin("HealthyB", ""));
    definitions.put(
        "ConstructorFailure",
        plugin(
            "ConstructorFailure",
            "public ConstructorFailure() { throw new IllegalStateException("
                + "\"broken constructor\"); }"));
    definitions.put(
        "StaticFailure",
        plugin(
            "StaticFailure",
            "static { if(Boolean.parseBoolean(\"true\")) throw new "
                + "IllegalStateException(\"broken initialization\"); }"));
    definitions.put(
        "PropertiesFailure",
        plugin(
            "PropertiesFailure",
            "public Properties getProperties() { throw new "
                + "IllegalStateException(\"broken properties\"); }"));
    definitions.put(
        "PropertiesLinkageFailure",
        plugin(
            "PropertiesLinkageFailure",
            "public Properties getProperties() { Missing.touch(); return "
                + "super.getProperties(); }"));
    // Checked exceptions can escape callbacks implemented by dynamic JVM languages.
    for (String name : List.of("CheckedPropertiesFailure", "CheckedDisposeFailure")) {
      String failure =
          name + ".<RuntimeException>fail(new java.io.IOException(\"checked callback failure\"));";
      String callback =
          name.equals("CheckedPropertiesFailure")
              ? "public Properties getProperties() { " + failure + " return null; }"
              : "public Properties getProperties() { throw new IllegalStateException("
                  + "\"broken properties\"); }"
                  + "public void dispose() { super.dispose(); "
                  + failure
                  + " }";
      definitions.put(
          name,
          plugin(
              name,
              callback
                  + "private static <T extends Throwable> void fail(Throwable failure) throws T {"
                  + "throw (T)failure; }"));
    }
    definitions.put(
        "NullProperties",
        plugin("NullProperties", "public Properties getProperties() { return null; }"));
    definitions.put(
        "BindFailure",
        plugin(
            "BindFailure",
            "public void setNodusMapPanel(NodusMapPanel p) { "
                + "super.setNodusMapPanel(p);if(p!=null) throw new "
                + "IllegalStateException(\"broken binding\"); }"));
    definitions.put(
        "DisposeFailure",
        plugin(
            "DisposeFailure",
            "public Properties getProperties() { throw new "
                + "IllegalStateException(\"broken properties\"); }public void "
                + "dispose() { super.dispose(); throw new NoClassDefFoundError("
                + "\"missing cleanup dependency\"); }"));
    definitions.put(
        "DetachFailure",
        plugin(
            "DetachFailure",
            "public Properties getProperties() { throw new "
                + "IllegalStateException(\"broken properties\"); }public void "
                + "setNodusMapPanel(NodusMapPanel p) { super.setNodusMapPanel(p);if("
                + "p==null) throw new NoClassDefFoundError(\"missing detach "
                + "dependency\"); }"));
    for (String[] field :
        new String[][] {
          {"TextMenuId", "MENUBAR_ID", "abc"},
          {"UnknownMenuId", "MENUBAR_ID", "99"},
          {"NegativeMenuId", "MENUBAR_ID", "-1"},
          {"TextItemId", "MENU_ITEM_ID", "abc"},
          {"NegativeItemId", "MENU_ITEM_ID", "-2"},
          {"OverflowItemId", "MENU_ITEM_ID", "2147483648"},
          {"BlankMenu", "USER_DEFINED_MENUBAR_TEXT", " "}
        }) {
      definitions.put(
          field[0],
          plugin(
              field[0],
              "public Properties getProperties() {Properties "
                  + "p=super.getProperties();"
                  + (field[1].equals("MENUBAR_ID") ? "p.remove(USER_DEFINED_MENUBAR_TEXT);" : "")
                  + "p.setProperty("
                  + field[1]
                  + ",\""
                  + field[2]
                  + "\"); return p; }"));
    }
    definitions.put(
        "MissingMenu",
        plugin(
            "MissingMenu",
            "public Properties getProperties() {Properties "
                + "p=super.getProperties(); p.remove(USER_DEFINED_MENUBAR_TEXT); "
                + "return p; }"));
    definitions.put(
        "MissingText",
        plugin(
            "MissingText",
            "public Properties getProperties() {Properties "
                + "p=super.getProperties(); p.setProperty(USER_DEFINED_MENUBAR_TEXT,"
                + "\"Rejected\");p.remove(MENU_ITEM__TEXT); return p; }"));
    definitions.put("Missing", "public class Missing { public static void touch() {} }");
    definitions.put("MissingBase", "public class MissingBase extends TrackedPlugin {}");
    definitions.put("MissingSuperclass", "public class MissingSuperclass extends MissingBase {}");
    definitions.put(
        "MissingSignature",
        plugin(
            "MissingSignature",
            "public MissingSignature() {} public MissingSignature(Missing dependency) {}"));
    definitions.put(
        "NoDefaultConstructor",
        plugin("NoDefaultConstructor", "public NoDefaultConstructor(String parameter) {}"));
    definitions.put(
        "PrivateConstructor", plugin("PrivateConstructor", "private PrivateConstructor() {}"));
    definitions.put(
        "AbstractPlugin", "public abstract class AbstractPlugin extends TrackedPlugin {}");
    definitions.put(
        "OrdinaryClass",
        "public class OrdinaryClass { public OrdinaryClass() {throw new "
            + "IllegalStateException(\"not a plugin\"); } }");
    definitions.put(
        "LazyHelper",
        "public class LazyHelper { public static String value() { return \"helper\"; } }");
    definitions.put(
        "HealthyLazy",
        plugin(
            "HealthyLazy",
            "public void execute() {try(java.io.InputStream input=getClass()"
                + ".getResourceAsStream(\"/plugin-data.txt\")) {if(input==null) "
                + "throw new IllegalStateException(\"missing lazy resource\");"
                + "getNodusMapPanel().storeObject(\"resource\",LazyHelper.value()"
                + "+\":\"+new String(input.readAllBytes(),"
                + "java.nio.charset.StandardCharsets.UTF_8));} catch("
                + "java.io.IOException e) { throw new java.io.UncheckedIOException("
                + "e); } super.execute(); }"));
    definitions.put(
        "ExecuteMissing",
        plugin("ExecuteMissing", "public void execute() { Missing.touch(); super.execute(); }"));
    definitions.put(
        "CustomMenuWins",
        plugin(
            "CustomMenuWins",
            "public Properties getProperties() {Properties "
                + "p=super.getProperties();p.setProperty(MENUBAR_ID,\"ignored\");"
                + "return p; }"));
    definitions.put(
        "AppendItem",
        plugin(
            "AppendItem",
            "public Properties getProperties() {Properties "
                + "p=super.getProperties();p.remove(USER_DEFINED_MENUBAR_TEXT);"
                + "p.setProperty(MENUBAR_ID,\"3\");p.setProperty(MENU_ITEM_ID,"
                + "\"999999\");return p; }"));
    final String marker =
        initializationMarker.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    definitions.put(
        "DiscoveryProbe",
        plugin(
            "DiscoveryProbe",
            "static { try {java.nio.file.Files.writeString("
                + "java.nio.file.Path.of(\""
                + marker
                + "\"),\"initialized\");} catch(java.io.IOException e) { throw new "
                + "java.io.UncheckedIOException(e); } }"));

    final Path application =
        Path.of(NodusPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    final Path repository = Path.of("").toAbsolutePath();
    String classpath = application.toString();
    try (Stream<Path> libraries = Files.walk(repository.resolve("lib"))) {
      classpath +=
          File.pathSeparator
              + libraries
                  .filter(p -> p.toString().endsWith(".jar"))
                  .sorted()
                  .map(Path::toString)
                  .collect(Collectors.joining(File.pathSeparator));
    }
    final List<String> arguments =
        new ArrayList<>(
            List.of(
                "--release",
                "11",
                "-encoding",
                "UTF-8",
                "-classpath",
                classpath,
                "-d",
                classes.toString()));
    for (Map.Entry<String, String> definition : definitions.entrySet()) {
      Path file = sources.resolve(definition.getKey() + ".java");
      Files.writeString(
          file,
          "package fixture; import edu.uclouvain.core.nodus.*; import java.util.Properties; "
              + definition.getValue());
      arguments.add(file.toString());
    }
    final var compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Plugin fixture compilation requires a full JDK");
    final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
    assertEquals(
        0,
        compiler.run(null, diagnostics, diagnostics, arguments.toArray(new String[0])),
        () -> diagnostics.toString(StandardCharsets.UTF_8));
  }

  private static String plugin(String name, String body) {
    return "public class " + name + " extends TrackedPlugin { " + body + " }";
  }

  /** Packages selected classes; omitted dependency classes remain unavailable at runtime. */
  public void write(Path target, String resource, String... names) throws Exception {
    Files.createDirectories(target.getParent());
    final Set<String> classesToInclude =
        new LinkedHashSet<>(List.of("TrackedPlugin", "LazyHelper"));
    classesToInclude.addAll(List.of(names));
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(target))) {
      for (String name : classesToInclude) {
        jar.putNextEntry(new JarEntry("fixture/" + name + ".class"));
        Files.copy(classes.resolve("fixture").resolve(name + ".class"), jar);
        jar.closeEntry();
      }
      jar.putNextEntry(new JarEntry("plugin-data.txt"));
      jar.write(resource.getBytes(StandardCharsets.UTF_8));
      jar.closeEntry();
    }
  }
}
