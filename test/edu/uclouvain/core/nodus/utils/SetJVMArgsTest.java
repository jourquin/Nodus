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

package edu.uclouvain.core.nodus.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Regression checks for JVM arguments retained across Java upgrades. */
class SetJVMArgsTest {
  @TempDir Path directory;

  @TestFactory
  @EnabledOnOs({OS.LINUX, OS.MAC})
  Stream<DynamicTest> upgradedShellScriptSelectsOptionsForTheJavaUsedAtLaunch() {
    return Stream.of("11.0.32", "16.0.2", "17.0.20", "25.0.4", "27", "27-ea")
        .map(
            version ->
                DynamicTest.dynamicTest("Java " + version, () -> checkShellUpgrade(version)));
  }

  private void checkShellUpgrade(String version) throws Exception {
    final Path script = Files.createDirectory(directory.resolve(version)).resolve("jvmargs.sh");
    final String legacy = "JVMARGS=\"-Xmx4096m -Xms1024m -Dcustom=value --illegal-access=deny\"\n";
    write(script, legacy);
    SetJVMArgs.upgradeLegacyScript(script, false);
    assertEquals(legacy, read(script.resolveSibling("jvmargs.sh.bak")));

    final String arguments = shellArguments(script, version);
    assertTrue(arguments.contains("-Xmx4096m -Xms1024m -Dcustom=value"), arguments);
    boolean oldJava = version.startsWith("11.") || version.startsWith("16.");
    assertEquals(oldJava, arguments.contains("--illegal-access=deny"), arguments);
    boolean nativeAccess = version.startsWith("25.") || version.startsWith("27");
    assertEquals(nativeAccess, arguments.contains("--enable-native-access=ALL-UNNAMED"), arguments);

    // Running SetJVMArgs again must neither regenerate the file nor overwrite the original backup.
    String migrated = read(script);
    SetJVMArgs.upgradeLegacyScript(script, false);
    assertEquals(migrated, read(script));
    assertEquals(legacy, read(script.resolveSibling("jvmargs.sh.bak")));
  }

  @TestFactory
  Stream<DynamicTest> windowsLegacyOptionsAreReplacedByAVersionGuardWithoutLosingCustomSettings() {
    return Stream.of("deny", "permit", "warn", "debug")
        .map(mode -> DynamicTest.dynamicTest(mode, () -> checkWindowsUpgrade(mode)));
  }

  private void checkWindowsUpgrade(String mode) throws Exception {
    final Path script = Files.createDirectory(directory.resolve(mode)).resolve("jvmargs.bat");
    final String parameters = "-Xmx3072m -Xms512m -Dcustom=value --illegal-access=" + mode;
    final String legacy = "set JVMARGS=" + parameters + "\r\n";
    write(script, legacy);
    SetJVMArgs.upgradeLegacyScript(script, true);
    final String migrated = read(script);
    String firstLine = migrated.lines().findFirst().orElseThrow();
    assertEquals("set \"JVMARGS=-Xmx3072m -Xms512m -Dcustom=value\"", firstLine);
    assertTrue(
        migrated.contains(
            "if %JAVA_FEATURE% GEQ 9 if %JAVA_FEATURE% LEQ 16 set"
                + " \"JVMARGS=%JVMARGS% --illegal-access=deny\""));
    assertEquals(legacy, read(script.resolveSibling("jvmargs.bat.bak")));
  }

  @Test
  void quotedWindowsAssignmentKeepsQuotedPropertiesAndEmbeddedOptionText() throws Exception {
    Path script = directory.resolve("jvmargs.bat");
    String custom =
        "-Xmx2048m -Dlabel=\"example --illegal-access=deny text\""
            + " -Dmarker=--illegal-access=deny";
    write(script, "@SET \"JVMARGS=--illegal-access=deny " + custom + "\"\r\n");
    SetJVMArgs.upgradeLegacyScript(script, true);
    assertEquals("set \"JVMARGS=" + custom + "\"", read(script).lines().findFirst().orElseThrow());
  }

  @Test
  void filesWithoutAStandaloneLegacyOptionAreNotRewritten() throws Exception {
    Path script = directory.resolve("jvmargs.sh");
    String original = "JVMARGS=\"-Xmx2048m -Dmarker=--illegal-access=deny\"\n";
    write(script, original);
    SetJVMArgs.upgradeLegacyScript(script, false);
    assertEquals(original, read(script));
    assertFalse(Files.exists(script.resolveSibling("jvmargs.sh.bak")));
  }

  @Test
  void currentGeneratedScriptsAreNotRewritten() throws Exception {
    for (boolean windows : new boolean[] {false, true}) {
      Path script = directory.resolve(windows ? "jvmargs.bat" : "jvmargs.sh");
      SetJVMArgs.createScript(script, "-Xmx2048m -Dcustom=value", windows);
      String original = read(script);
      SetJVMArgs.upgradeLegacyScript(script, windows);
      assertEquals(original, read(script));
      assertFalse(Files.exists(script.resolveSibling(script.getFileName() + ".bak")));
    }
  }

  @Test
  void customMultiLineScriptsAreNotRewritten() throws Exception {
    for (boolean windows : new boolean[] {false, true}) {
      Path script = directory.resolve(windows ? "jvmargs.bat" : "jvmargs.sh");
      String original =
          windows
              ? "rem Custom configuration\r\nset JVMARGS=-Xmx2048m --illegal-access=deny\r\n"
              : "# Custom configuration\nJVMARGS=\"-Xmx2048m --illegal-access=deny\"\n";
      write(script, original);
      SetJVMArgs.upgradeLegacyScript(script, windows);
      assertEquals(original, read(script));
      assertFalse(Files.exists(script.resolveSibling(script.getFileName() + ".bak")));
    }
  }

  @Test
  void singleLineScriptsWithCustomCommandsAreNotRewritten() throws Exception {
    Path script = directory.resolve("jvmargs.bat");
    String original = "set JVMARGS=-Xmx2048m --illegal-access=deny & echo Custom startup\r\n";
    write(script, original);
    SetJVMArgs.upgradeLegacyScript(script, true);
    assertEquals(original, read(script));
    assertFalse(Files.exists(script.resolveSibling("jvmargs.bat.bak")));
  }

  private static String shellArguments(Path script, String version) throws Exception {
    ProcessBuilder builder =
        new ProcessBuilder(
            "sh",
            "-c",
            "java() { printf 'openjdk version \"%s\"\\n' \"$NODUS_TEST_JAVA_VERSION\" >&2; }\n"
                + ". \"$1\"\nprintf '%s\\n' \"$JVMARGS\"",
            "nodus-jvmargs-test",
            script.toString());
    builder.environment().put("NODUS_TEST_JAVA_VERSION", version);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    try {
      assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Script did not finish");
      String output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
      assertEquals(0, process.exitValue(), output);
      return output.strip();
    } finally {
      process.destroyForcibly();
    }
  }

  private static String read(Path file) throws Exception {
    return Files.readString(file, Charset.defaultCharset());
  }

  private static void write(Path file, String text) throws Exception {
    Files.writeString(file, text, Charset.defaultCharset());
  }
}
