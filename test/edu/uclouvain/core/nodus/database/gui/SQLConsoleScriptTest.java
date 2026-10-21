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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** Lexical command boundaries, preserved quoted text and existing Nodus script conventions. */
class SQLConsoleScriptTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> quotedTextCannotStartCommentsOrSeparateCommands() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String sql :
        List.of(
            "SELECT ';'",
            "SELECT '/*keep*/ -- # ;'",
            "SELECT 'it''s;--/*#*/'",
            "SELECT '', '''', ''';''--/*#*/'",
            "SELECT 'été 東京;#/* */--'",
            "SELECT 'first\r\n#still text;\n--still text\r/*still text*/'",
            "SELECT 'C:\\folder\\'",
            "SELECT 'one\\;--#/*two*/'",
            "SELECT \"column;--/*#\" FROM \"table;#\"",
            "SELECT \"a\"\";--/*#*/\" FROM \"table\"",
            "SELECT `a``;--/*#*/` FROM `table;#`",
            "SELECT $$a;'\" -- /* # */\r\n b$$",
            "SELECT $tag$a;$$'\" /* -- # */$other$ b$tag$",
            "SELECT $Tag_12$a;$tag_12$--/*#*/$Tag_12$",
            "SELECT price$tag$, $1, name$part FROM costs",
            "SELECT a # b FROM flags",
            "SELECT json #>> '{key}' FROM data")) {
      tests.add(
          dynamicTest(
              "script: " + sql,
              () -> {
                final var single = SQLConsoleScript.parseSQLCommands(sql);
                assertEquals(List.of(sql), single.commands);
                assertFalse(single.batchFile);
                assertFalse(single.multipleCommands);
                final var batch = SQLConsoleScript.parseSQLCommands(sql + "; SELECT 2;SELECT 3");
                assertEquals(List.of(sql, "SELECT 2", "SELECT 3"), batch.commands);
                assertTrue(batch.batchFile);
                assertTrue(batch.multipleCommands);
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> commentsAndOutsideWhitespaceKeepSqlTokensSeparated() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String sql :
        List.of(
            "SELECT/*note*/1",
            "SELECT /*' ; -- # \" */ 1",
            "SELECT/* outer /* nested ; ' */ still outer */1",
            "SELECT\r\n\t1",
            "SELECT 1 -- trailing ; ' /* ignored",
            "SELECT 1\n# trailing ; ' /* ignored",
            "--before\nSELECT 1",
            "#before\rSELECT 1",
            "/*start*/SELECT/*middle*/1/*end*/")) {
      tests.add(
          dynamicTest(
              "script: " + sql,
              () ->
                  assertEquals(
                      List.of("SELECT 1"), SQLConsoleScript.parseSQLCommands(sql).commands)));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> lineCommentsEndAtEveryPreviouslySupportedLineBreak() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String newline :
        List.of("\r\n", "\n", "\r", "\u000B", "\f", "\u0085", "\u2028", "\u2029")) {
      tests.add(
          dynamicTest(
              "line break " + Integer.toHexString(newline.charAt(0)),
              () -> {
                assertEquals(
                    List.of("SELECT 1 +2", "SELECT 3"),
                    SQLConsoleScript.parseSQLCommands(
                            "SELECT 1 -- ignored; '\"/*" + newline + "+2;SELECT 3")
                        .commands);
                assertEquals(
                    List.of("SELECT 'a" + newline + "#b;--/*c*/'"),
                    SQLConsoleScript.parseSQLCommands("SELECT 'a" + newline + "#b;--/*c*/'")
                        .commands);
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> emptyStatementsAndCommentOnlyScriptsProduceNoExecutableCommands() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String sql :
        List.of(
            "", " \t\r\n", "-- ' /* ;", "# \" /* ;", "/* ' \" ; */", ";", ";; ; --comment\n;")) {
      tests.add(
          dynamicTest(
              "script: " + sql,
              () -> {
                final var parsed = SQLConsoleScript.parseSQLCommands(sql);
                assertTrue(parsed.commands.isEmpty());
                assertFalse(parsed.multipleCommands);
              }));
    }
    return tests;
  }

  @Test
  void finalCommandsDoNotRequireATrailingDelimiter() {
    final var parsed = SQLConsoleScript.parseSQLCommands("SELECT 1;SELECT 2; SELECT 3 -- end");
    assertEquals(List.of("SELECT 1", "SELECT 2", "SELECT 3"), parsed.commands);
    assertTrue(parsed.batchFile);
    assertTrue(parsed.multipleCommands);
  }

  @Test
  void extraDelimitersDoNotTurnADirectCommandIntoAMultiCommandScript() {
    final var parsed = SQLConsoleScript.parseSQLCommands(";EXPORTCSV data;;; --end");
    assertEquals(List.of("EXPORTCSV data"), parsed.commands);
    assertTrue(parsed.batchFile);
    assertFalse(parsed.multipleCommands);
  }

  @Test
  void variablesStillUseLongestNameFirstAndRetainScriptClassification() {
    assertEquals(List.of("@@id := 42"), SQLConsoleScript.parseSQLCommands("@@id := 42").commands);
    final var parsed =
        SQLConsoleScript.parseSQLCommands("@@id := 42;@@idLong := 43;SELECT @@idLong,@@id");
    assertEquals(List.of("SELECT  43 , 42 "), parsed.commands);
    assertTrue(parsed.batchFile);
    assertTrue(parsed.multipleCommands);
    assertEquals(
        List.of("SELECT 7 "), SQLConsoleScript.parseSQLCommands("@@id:=7;SELECT @@id").commands);
    assertTrue(SQLConsoleScript.parseSQLCommands("@@id:=7;").commands.isEmpty());
  }

  @Test
  void quotedVariableValuesAndLegacyTextSubstitutionsKeepTheirContents() {
    assertEquals(
        List.of("SELECT 'it''s; -- # /*kept*/' "),
        SQLConsoleScript.parseSQLCommands("@@value:='it''s; -- # /*kept*/';SELECT @@value;")
            .commands);
    assertEquals(
        List.of("SELECT 'value '"),
        SQLConsoleScript.parseSQLCommands("@@text:=value;SELECT '@@text';").commands);
  }

  @Test
  void runBatchSeparatorsFollowLineCommentsAndSkipCommentOnlyElements() {
    final String joined =
        SQLConsoleScript.joinCommands(
            new String[] {
              "-- ignored; '",
              "#ignored",
              "SELECT 1 -- trailing; '",
              "SELECT 2 -- trailing; \"",
              "SELECT ';';",
              "/*ignored*/",
              "SELECT 3;SELECT 4"
            });
    assertEquals(
        List.of("SELECT 1", "SELECT 2", "SELECT ';'", "SELECT 3", "SELECT 4"),
        SQLConsoleScript.parseSQLCommands(joined).commands);
  }

  @TestFactory
  List<DynamicTest> unclosedQuotesAndCommentsRejectTheWholeBatch() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String invalid :
        List.of(
            "SELECT 'unfinished",
            "SELECT 'escaped''",
            "SELECT \"unfinished",
            "SELECT `unfinished",
            "SELECT $$unfinished",
            "SELECT $tag$unfinished",
            "SELECT $tag$wrong$TAG$",
            "/*unfinished",
            "/*outer /*nested*/ unfinished",
            "SELECT 'comment -- ; /*markers",
            "SELECT $$comment -- ; /*markers")) {
      tests.add(
          dynamicTest(
              invalid,
              () -> {
                final var failure =
                    assertThrows(
                        IllegalArgumentException.class,
                        () -> SQLConsoleScript.parseSQLCommands("DELETE FROM data;" + invalid));
                assertTrue(failure.getMessage().startsWith("Unterminated SQL"));
              }));
    }
    return tests;
  }

  @Test
  void scriptFileReadsAndWritesPreserveLiteralLineEndingsAndUnicode() throws Exception {
    final String sql = "SELECT 'été\r\n--東京;\r#still text';\r\nSELECT 2";
    final Path file = directory.resolve("script.sql");
    SQLConsoleScript.writeFile(file.toString(), sql);
    assertEquals(sql, Files.readString(file, Charset.defaultCharset()));
    assertEquals(sql, SQLConsoleScript.readFile(file.toString()));
    assertEquals(
        List.of("SELECT 'été\r\n--東京;\r#still text'", "SELECT 2"),
        SQLConsoleScript.parseSQLCommands(SQLConsoleScript.readFile(file.toString())).commands);
  }
}
