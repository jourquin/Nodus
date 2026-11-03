/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>Nodus is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with Nodus. If not,
 * see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import groovy.lang.Binding;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises real Groovy compilation with matching filenames and explicit class declarations. */
class NodusGroovyShellTest {
  @TempDir Path directory;

  @Test
  void fileEvaluationRetainsPackageBindingAndUnicodeAndClosesTheReader() throws Exception {
    Path folder = Files.createDirectory(directory.resolve("scripts with spaces"));
    Path file = folder.resolve("Tool.groovy");
    Files.writeString(
        file,
        "package example\nclass Tool { def panel; String label = 'café 東京' }\n"
            + "def tool = new Tool(panel: nodusMapPanel)\n"
            + "assert tool.label == 'café 東京'\nreturn tool.panel\n");
    NodusGroovyShell shell = new NodusGroovyShell();
    Object panel = new Object();
    shell.setVariable("nodusMapPanel", panel);

    assertSame(panel, shell.evaluate(file.toFile()));
    // In particular, Windows must be able to remove the source immediately after execution.
    Files.delete(file);
    assertFalse(Files.exists(file));
  }

  @Test
  void configuredSourceEncodingIsPreserved() throws Exception {
    Path file = directory.resolve("Encoded.groovy");
    Files.writeString(file, "return 'café'", StandardCharsets.ISO_8859_1);
    CompilerConfiguration configuration = new CompilerConfiguration();
    configuration.setSourceEncoding(StandardCharsets.ISO_8859_1.name());
    NodusGroovyShell shell = new NodusGroovyShell(null, new Binding(), configuration);

    assertEquals("café", shell.evaluate(file.toFile()));
  }

  @Test
  void runtimeFailuresKeepSourceLineNumbersAndIdentifyTheLogicalFilename() throws Exception {
    Path file = directory.resolve("Broken.groovy");
    Files.writeString(file, "class Broken {}\nthrow new IllegalStateException('script failure')\n");
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class, () -> new NodusGroovyShell().evaluate(file.toFile()));

    assertEquals("script failure", failure.getMessage());
    assertEquals("_Broken.groovy", failure.getStackTrace()[0].getFileName());
    assertEquals(2, failure.getStackTrace()[0].getLineNumber());
    Files.delete(file);
  }

  @Test
  void argbExampleCompilesWithItsOriginalClassNameWithoutOpeningAWindow() throws Exception {
    String original = Files.readString(Path.of("scripts", "ARGBConverter.groovy"));
    String headless =
        original.replace("new ARGBConverter(nodusMapPanel);", "return ARGBConverter.name;");
    assertNotEquals(original, headless, "Replace only the dialog launch for headless execution");
    Path file = directory.resolve("ARGBConverter.groovy");
    Files.writeString(file, headless);

    assertEquals("ARGBConverter", new NodusGroovyShell().evaluate(file.toFile()));
  }
}
