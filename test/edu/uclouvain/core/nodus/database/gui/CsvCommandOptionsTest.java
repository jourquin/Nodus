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

package edu.uclouvain.core.nodus.database.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class CsvCommandOptionsTest {
  @TestFactory
  List<DynamicTest> optionsWorkInEitherOrderWithExistingBasenames() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String operation : List.of("IMPORTCSV", "IMPORTCSVH", "EXPORTCSV", "EXPORTCSVH")) {
      for (String name : List.of("data", "traffic flows", "\"traffic  flows\"", "`a`` table`")) {
        for (String options :
            List.of(
                "ENCODING UTF-8 DELIMITER ';'",
                "delimiter ';' encoding 'UTF-8'",
                "ENCODING \"UTF-8\" DELIMITER ';'")) {
          tests.add(
              dynamicTest(
                  operation + " " + name + " " + options,
                  () -> {
                    var arguments =
                        SQLConsoleScript.csvArguments(
                            operation + " " + name + " " + options, operation);
                    assertEquals(
                        SQLConsoleScript.tableArgument(operation + " " + name, operation),
                        arguments.table);
                    assertEquals(StandardCharsets.UTF_8, arguments.options.getCharset());
                    assertEquals(';', arguments.options.getDelimiter());
                    assertFalse(arguments.options.isAutomatic());
                  }));
        }
      }
    }
    return tests;
  }

  @Test
  void defaultsSingleOptionsAndLiteralKeywordsRemainUnambiguous() {
    for (String name :
        List.of(
            "data",
            "traffic flows",
            "ENCODING",
            "DELIMITER",
            "'literal'",
            "\"file ENCODING windows-1252\"",
            "`file DELIMITER AUTO`")) {
      var arguments = SQLConsoleScript.csvArguments("IMPORTCSVH " + name, "IMPORTCSVH");
      assertEquals(
          SQLConsoleScript.tableArgument("IMPORTCSVH " + name, "IMPORTCSVH"), arguments.table);
      assertEquals(Charset.defaultCharset(), arguments.options.getCharset());
      assertEquals(',', arguments.options.getDelimiter());
    }
    assertEquals(
        '\t',
        SQLConsoleScript.csvArguments("EXPORTCSV data DELIMITER TAB", "EXPORTCSV")
            .options
            .getDelimiter());
    assertTrue(
        SQLConsoleScript.csvArguments("IMPORTCSV data DELIMITER AUTO", "IMPORTCSV")
            .options
            .isAutomatic());
    assertEquals(
        "windows-1252",
        SQLConsoleScript.csvArguments("EXPORTCSVH data ENCODING windows-1252", "EXPORTCSVH")
            .options
            .getCharset()
            .name());
    assertEquals(
        39,
        (int)
            SQLConsoleScript.csvArguments("IMPORTCSV data DELIMITER " + "'".repeat(4), "IMPORTCSV")
                .options
                .getDelimiter());
    assertEquals("", SQLConsoleScript.csvArguments("IMPORTCSV", "IMPORTCSV").table);
  }

  @TestFactory
  List<DynamicTest> malformedOrDuplicateOptionsAreRejected() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String tail :
        List.of(
            "ENCODING",
            "DELIMITER",
            "ENCODING DELIMITER ';'",
            "ENCODING ''",
            "ENCODING unknown-csv-charset",
            "ENCODING UTF-8 encoding UTF-16",
            "DELIMITER TAB DELIMITER ','",
            "DELIMITER ''",
            "DELIMITER '||'",
            "DELIMITER AUTO DELIMITER TAB",
            "DELIMITER '\r'",
            "DELIMITER '\n'",
            "DELIMITER '\u0000'",
            "DELIMITER '\"'",
            "DELIMITER ','junk",
            "ENCODING 'UTF-8'junk",
            "DELIMITER ;",
            "DELIMITER COMMA",
            "DELIMITER 'TAB'",
            "DELIMITER ';' UNKNOWN value",
            "ENCODING UTF-8 DELIMITER 'unterminated")) {
      tests.add(
          dynamicTest(
              tail,
              () ->
                  assertThrows(
                      IllegalArgumentException.class,
                      () ->
                          SQLConsoleScript.csvArguments(
                              "IMPORTCSVH \"data\" " + tail, "IMPORTCSVH"))));
    }
    for (String operation : List.of("EXPORTCSV", "EXPORTCSVH")) {
      tests.add(
          dynamicTest(
              operation + " AUTO",
              () ->
                  assertThrows(
                      IllegalArgumentException.class,
                      () ->
                          SQLConsoleScript.csvArguments(
                              operation + " data DELIMITER AUTO", operation))));
    }
    return tests;
  }

  @Test
  void delimiterLiteralsSurviveScriptScanningAndVariableSubstitution() {
    var script =
        SQLConsoleScript.parseSQLCommands(
            "@@charset:=UTF-8;@@sep:=';';"
                + "IMPORTCSVH \"traffic flows\" DELIMITER @@sep ENCODING @@charset;SELECT 1;");
    assertEquals(2, script.commands.size());
    var arguments = SQLConsoleScript.csvArguments(script.commands.get(0), "IMPORTCSVH");
    assertEquals("traffic flows", arguments.table);
    assertEquals(';', arguments.options.getDelimiter());
    assertEquals(StandardCharsets.UTF_8, arguments.options.getCharset());
    assertEquals("SELECT 1", script.commands.get(1));
  }
}
