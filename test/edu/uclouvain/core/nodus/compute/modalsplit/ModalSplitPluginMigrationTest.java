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

package edu.uclouvain.core.nodus.compute.modalsplit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import edu.uclouvain.core.nodus.utils.ModalSplitMethodsLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Exercises plugin discovery and routing data across the Nodus 9.0 package change.
 *
 * <p>The deliberately incompatible JAR is created in a private temporary directory. Its expected
 * diagnostic is captured through the loader's reporter, never displayed as an interactive warning.
 * Source paths and compiler dependencies come from the loaded application and its repository, not
 * the working directory or launcher classpath used by Eclipse, Ant or JUnit.
 */
@ResourceLock("ModalSplitMethodsLoader")
class ModalSplitPluginMigrationTest {
  @TempDir java.nio.file.Path directory;

  @Test
  void skipsOldBinaryAndLoadsRecompiledDemoAlongsideBuiltInMethods() throws Exception {
    createLegacyPlugin();
    java.nio.file.Path repository = repositoryRoot();
    compileJar(
        repository,
        "z-current.jar",
        "MLogit",
        List.of(repository.resolve("demo/MLogit/MLogit.java")));

    List<String> warnings = new ArrayList<>();
    try {
      new ModalSplitMethodsLoader(project(), warnings::add);
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("a-legacy.jar"));
      assertTrue(warnings.get(0).contains("recompile it against nodus9.jar"));
      assertEquals(4, ModalSplitMethodsLoader.getAvailableModalSplitMethods().size());
      checkShares("MLogit");
    } finally {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    }
  }

  @Test
  void upgradesApprovedBinaryWithoutACompilerAndKeepsAnExactBackup() throws Exception {
    createLegacyPlugin();
    java.nio.file.Path jar = directory.resolve("a-legacy.jar");
    byte[] original = Files.readAllBytes(jar);
    List<String> warnings = new ArrayList<>();
    List<File> prompts = new ArrayList<>();
    File canonicalJar = jar.toFile().getCanonicalFile();
    File canonicalBackup = directory.resolve("a-legacy.jar.nodus8").toFile().getCanonicalFile();
    try {
      new ModalSplitMethodsLoader(
          project(),
          warnings::add,
          (plugin, backup) -> {
            assertEquals(canonicalJar, plugin);
            assertEquals(canonicalBackup, backup);
            prompts.add(plugin);
            return true;
          });
      assertTrue(warnings.isEmpty(), warnings.toString());
      assertEquals(1, prompts.size());
      assertArrayEquals(original, Files.readAllBytes(directory.resolve("a-legacy.jar.nodus8")));
      assertEquals(4, ModalSplitMethodsLoader.getAvailableModalSplitMethods().size());
      checkShares("LegacyMLogit");
      try (JarFile updated = new JarFile(jar.toFile())) {
        assertArrayEquals(
            new byte[] {0, 1, 2, -1},
            updated.getInputStream(updated.getJarEntry("plugin-resource.bin")).readAllBytes());
      }
      ModalSplitMethod plugin = ModalSplitMethodsLoader.getModalSplitMethod("LegacyMLogit");
      assertEquals(Path.class, plugin.getClass().getMethod("reflectedPathType").invoke(plugin));
      java.util.function.Supplier<?> factory =
          (java.util.function.Supplier<?>) plugin.getClass().getMethod("factory").invoke(plugin);
      assertEquals(Path.class, factory.get().getClass());
      assertEquals(
          Path.class.getName() + "[]",
          plugin.getClass().getMethod("pathArray").getGenericReturnType().getTypeName());
      // A second project open loads the converted archive directly and ignores the backup.
      new ModalSplitMethodsLoader(
          project(),
          warnings::add,
          (file, backup) -> {
            throw new AssertionError("An upgraded plugin must not prompt again");
          });
      assertEquals(4, ModalSplitMethodsLoader.getAvailableModalSplitMethods().size());
      assertTrue(warnings.isEmpty(), warnings.toString());
      checkShares("LegacyMLogit");
    } finally {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    }
  }

  @Test
  void decliningUpgradeLeavesTheJarAloneAndLoadsCurrentPlugins() throws Exception {
    createLegacyPlugin();
    java.nio.file.Path jar = directory.resolve("a-legacy.jar");
    byte[] original = Files.readAllBytes(jar);
    compileJar(
        repositoryRoot(),
        "z-current.jar",
        "MLogit",
        List.of(repositoryRoot().resolve("demo/MLogit/MLogit.java")));
    List<String> warnings = new ArrayList<>();
    List<File> prompts = new ArrayList<>();
    try {
      new ModalSplitMethodsLoader(
          project(),
          warnings::add,
          (plugin, backup) -> {
            prompts.add(plugin);
            return false;
          });
      assertEquals(1, prompts.size());
      assertTrue(warnings.isEmpty(), warnings.toString());
      assertArrayEquals(original, Files.readAllBytes(jar));
      assertFalse(Files.exists(directory.resolve("a-legacy.jar.nodus8")));
      assertEquals(4, ModalSplitMethodsLoader.getAvailableModalSplitMethods().size());
      checkShares("MLogit");
    } finally {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    }
  }

  @Test
  void existingBackupIsNeverOverwritten() throws Exception {
    createLegacyPlugin();
    java.nio.file.Path jar = directory.resolve("a-legacy.jar");
    byte[] original = Files.readAllBytes(jar);
    java.nio.file.Path backup = directory.resolve("a-legacy.jar.nodus8");
    Files.writeString(backup, "Keep this earlier backup");
    List<String> warnings = new ArrayList<>();
    try {
      new ModalSplitMethodsLoader(
          project(),
          warnings::add,
          (file, target) -> {
            throw new AssertionError("An existing backup must prevent migration");
          });
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("backup already exists"));
      assertEquals("Keep this earlier backup", Files.readString(backup));
      assertArrayEquals(original, Files.readAllBytes(jar));
    } finally {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    }
  }

  @Test
  void signedArchiveRequiresRebuildingAndIsLeftUnchanged() throws Exception {
    createLegacyPlugin();
    java.nio.file.Path jar = directory.resolve("a-legacy.jar");
    java.nio.file.Path signed = directory.resolve("signed.tmp");
    try (JarFile source = new JarFile(jar.toFile());
        JarOutputStream target = new JarOutputStream(Files.newOutputStream(signed))) {
      var entries = source.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        target.putNextEntry(new JarEntry(entry.getName()));
        source.getInputStream(entry).transferTo(target);
        target.closeEntry();
      }
      target.putNextEntry(new JarEntry("META-INF/TEST.SF"));
      target.write("Signature-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
      target.closeEntry();
    }
    Files.move(signed, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    byte[] original = Files.readAllBytes(jar);
    List<String> warnings = new ArrayList<>();
    try {
      new ModalSplitMethodsLoader(
          project(),
          warnings::add,
          (file, backup) -> {
            throw new AssertionError("A signed archive must not offer automatic conversion");
          });
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("signed"));
      assertArrayEquals(original, Files.readAllBytes(jar));
      assertFalse(Files.exists(directory.resolve("a-legacy.jar.nodus8")));
    } finally {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    }
  }

  @Test
  void removedAbrahamReferenceRequiresManualRebuilding() throws Exception {
    createLegacyPlugin(
        "public String removedType() { return \""
            + "edu.uclouvain.core.nodus.compute.assign.modalsplit.Abraham\"; }",
        "");
    java.nio.file.Path jar = directory.resolve("a-legacy.jar");
    byte[] original = Files.readAllBytes(jar);
    List<String> warnings = new ArrayList<>();
    try {
      new ModalSplitMethodsLoader(
          project(),
          warnings::add,
          (file, backup) -> {
            throw new AssertionError("Removed types cannot be converted");
          });
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("Abraham"));
      assertArrayEquals(original, Files.readAllBytes(jar));
      assertFalse(Files.exists(directory.resolve("a-legacy.jar.nodus8")));
    } finally {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    }
  }

  @Test
  void failedLinkageCheckDoesNotReplaceThePluginOrCreateABackup() throws Exception {
    createLegacyPlugin(
        "public MissingSupport missing() { return null; }", "class MissingSupport {}");
    java.nio.file.Path jar = directory.resolve("a-legacy.jar");
    byte[] original = Files.readAllBytes(jar);
    List<String> warnings = new ArrayList<>();
    try {
      new ModalSplitMethodsLoader(project(), warnings::add, (file, backup) -> true);
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("could not be linked"), warnings.toString());
      assertArrayEquals(original, Files.readAllBytes(jar));
      assertFalse(Files.exists(directory.resolve("a-legacy.jar.nodus8")));
      try (Stream<java.nio.file.Path> files = Files.list(directory)) {
        assertFalse(files.anyMatch(file -> file.getFileName().toString().startsWith(".nodus9-")));
      }
    } finally {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    }
  }

  private void createLegacyPlugin() throws Exception {
    createLegacyPlugin("", "");
  }

  private void createLegacyPlugin(String extraMethods, String extraClasses) throws Exception {
    String currentPackage = "edu.uclouvain.core.nodus.compute.modalsplit";
    String oldPackage = "edu.uclouvain.core.nodus.compute.assign.modalsplit";
    java.nio.file.Path repository = repositoryRoot();
    java.nio.file.Path demoSource = repository.resolve("demo/MLogit/MLogit.java");
    String demo = Files.readString(demoSource);
    java.nio.file.Path legacySources = Files.createDirectory(directory.resolve("legacy-src"));
    List<java.nio.file.Path> sources = new ArrayList<>();
    // Compile against genuinely different class names, then omit those old API classes from the
    // JAR.
    for (String name : List.of("ModalSplitMethod", "Path", "PathsForMode")) {
      String api =
          Files.readString(
              repository.resolve("src/" + currentPackage.replace('.', '/') + "/" + name + ".java"));
      java.nio.file.Path source = legacySources.resolve(name + ".java");
      Files.writeString(source, api.replace(currentPackage, oldPackage));
      sources.add(source);
    }
    java.nio.file.Path oldPlugin = legacySources.resolve("LegacyMLogit.java");
    String plugin = demo.replace(currentPackage, oldPackage).replace("MLogit", "LegacyMLogit");
    // Reflection literals, arrays, generic descriptors and a lambda all carry class references.
    String extra =
        "\npublic Class<?> reflectedPathType() throws Exception { return Class.forName(\""
            + oldPackage
            + ".Path\"); }\n"
            + "public "
            + oldPackage
            + ".Path[] pathArray() { return new "
            + oldPackage
            + ".Path[0]; }\n"
            + "public java.util.function.Supplier<"
            + oldPackage
            + ".Path> factory() { return "
            + oldPackage
            + ".Path::new; }\n";
    Files.writeString(
        oldPlugin,
        plugin.substring(0, plugin.lastIndexOf('}')) + extra + extraMethods + "}\n" + extraClasses);
    sources.add(oldPlugin);
    compileJar(repository, "a-legacy.jar", "LegacyMLogit", sources);
  }

  private NodusProject project() {
    return new NodusProject(null) {
      @Override
      public String getLocalProperty(String key) {
        return NodusC.PROP_PROJECT_DOTPATH.equals(key) ? directory.toString() : null;
      }
    };
  }

  private void checkShares(String name) throws Exception {
    assertTrue(ModalSplitMethodsLoader.getModalSplitMethod("MNL") instanceof MultinomialLogit);
    assertTrue(ModalSplitMethodsLoader.getModalSplitMethod("MNP") instanceof MultinomialProbit);
    assertTrue(ModalSplitMethodsLoader.getModalSplitMethod("Proportional") instanceof Proportional);
    ModalSplitMethod plugin = ModalSplitMethodsLoader.getModalSplitMethod(name);
    assertNotNull(plugin);
    AssignmentParameters parameters = new AssignmentParameters(project());
    Properties coefficients = new Properties();
    coefficients.setProperty("log(cost).1.0", "-1");
    coefficients.setProperty("log(cost).2.0", "-1");
    parameters.setCostFunctions(coefficients);
    plugin.initialize(parameters);
    plugin = (ModalSplitMethod) plugin.clone();
    plugin.initializeGroup(0);
    PathsForMode road = mode(1, 10);
    PathsForMode rail = mode(2, 20);
    assertTrue(plugin.split(null, List.of(road, rail)));
    assertEquals(2.0 / 3, road.marketShare, 1e-12);
    assertEquals(1.0 / 3, rail.pathList.getFirst().marketShare, 1e-12);
    plugin.dispose();
  }

  private java.nio.file.Path repositoryRoot() throws Exception {
    java.nio.file.Path location = applicationLocation();
    for (java.nio.file.Path candidate = location;
        candidate != null;
        candidate = candidate.getParent()) {
      if (Files.isRegularFile(candidate.resolve("build-tests.xml"))
          && Files.isDirectory(candidate.resolve("src"))) {
        return candidate;
      }
    }
    throw new IllegalStateException("Cannot locate the source repository above " + location);
  }

  private java.nio.file.Path applicationLocation() throws Exception {
    return java.nio.file.Path.of(
        ModalSplitMethod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  }

  private void compileJar(
      java.nio.file.Path repository,
      String jarName,
      String pluginName,
      List<java.nio.file.Path> sources)
      throws Exception {
    String classpath = applicationLocation().toString();
    try (Stream<java.nio.file.Path> libraries = Files.walk(repository.resolve("lib"))) {
      classpath +=
          File.pathSeparator
              + libraries
                  .filter(path -> path.toString().endsWith(".jar"))
                  .sorted()
                  .map(java.nio.file.Path::toString)
                  .collect(Collectors.joining(File.pathSeparator));
    }
    java.nio.file.Path output = Files.createDirectory(directory.resolve(pluginName));
    List<String> arguments =
        new ArrayList<>(
            List.of(
                "--release",
                "11",
                "-encoding",
                "UTF-8",
                "-classpath",
                classpath,
                "-d",
                output.toString()));
    sources.forEach(source -> arguments.add(source.toString()));
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "The plugin migration test requires a full JDK");
    ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
    assertEquals(
        0,
        compiler.run(null, diagnostics, diagnostics, arguments.toArray(new String[0])),
        () -> diagnostics.toString(StandardCharsets.UTF_8));
    try (JarOutputStream jar =
        new JarOutputStream(Files.newOutputStream(directory.resolve(jarName)))) {
      jar.putNextEntry(new JarEntry(pluginName + ".class"));
      Files.copy(output.resolve(pluginName + ".class"), jar);
      jar.closeEntry();
      jar.putNextEntry(new JarEntry("plugin-resource.bin"));
      jar.write(new byte[] {0, 1, 2, -1});
      jar.closeEntry();
    }
  }

  private PathsForMode mode(int id, double cost) {
    Path path = new Path();
    path.loadingMode = (byte) id;
    path.weights = new PathWeights();
    path.weights.mvCost = cost;
    return new PathsForMode(path);
  }
}
