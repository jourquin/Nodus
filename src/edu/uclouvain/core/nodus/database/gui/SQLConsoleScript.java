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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/** Script parsing and text-file storage for the SQL console. */
final class SQLConsoleScript {
  private static final String NL = System.lineSeparator();

  private SQLConsoleScript() {}

  /** Keeps batch information even when variable definitions leave only one executable command. */
  static final class ParsedSQLCommands {
    final Vector<String> commands;
    final boolean multipleCommands;

    final boolean batchFile;

    private ParsedSQLCommands(
        Vector<String> commands, boolean multipleCommands, boolean batchFile) {
      this.commands = commands;
      this.multipleCommands = multipleCommands;
      this.batchFile = batchFile;
    }
  }

  /** Returns true if the statement is exactly the given command, ignoring case and edge spaces. */
  static boolean isCommand(String sqlStmt, String command) {
    return sqlStmt != null && sqlStmt.trim().equalsIgnoreCase(command);
  }

  /**
   * Returns true if the statement starts with the given command followed by a whitespace boundary.
   */
  static boolean startsWithCommand(String sqlStmt, String command) {
    if (sqlStmt == null) {
      return false;
    }

    String trimmed = sqlStmt.trim();
    int commandLength = command.length();
    if (!trimmed.regionMatches(true, 0, command, 0, commandLength)) {
      return false;
    }

    return trimmed.length() == commandLength
        || Character.isWhitespace(trimmed.charAt(commandLength));
  }

  /**
   * Reads a SQL batch file.
   *
   * @param file String
   * @return String
   */
  static String readFile(String file) {
    try {
      return Files.readString(Path.of(file), Charset.defaultCharset());
    } catch (IOException e) {
      return e.getMessage();
    }
  }

  /**
   * Writes an SQL batch file or an output text file.
   *
   * @param file String
   * @param text String
   */
  static void writeFile(String file, String text) {
    try (FileWriter write = new FileWriter(file)) {
      write.write(text);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Splits commands at unquoted semicolons, strips comments outside quotes and retains the final
   * command with or without a delimiter. SQL doubled quotes and PostgreSQL/H2 dollar strings are
   * preserved verbatim. Nodus's existing textual variable substitutions are applied afterwards.
   *
   * @throws IllegalArgumentException if a quote or block comment is not closed
   */
  static ParsedSQLCommands parseSQLCommands(String sqlCommand) {
    final CommandScanner scanner = new CommandScanner(sqlCommand);
    scanner.scan();
    if (!scanner.delimited) {
      return new ParsedSQLCommands(scanner.commands, false, false);
    }
    final Vector<String> parsedCommands = new Vector<>();
    final Map<String, String> variables = new TreeMap<>();
    for (String command : scanner.commands) {
      String currentCommand = command;
      // Keep textual @@ substitutions compatible with existing scripts, including their spacing.
      if (currentCommand.startsWith("@@")) {
        final int idx = currentCommand.indexOf(":=");
        if (idx != -1) {
          variables.put(currentCommand.substring(0, idx).trim(), currentCommand.substring(idx + 2));
          continue;
        }
      }
      final List<String> varNames = new ArrayList<>(variables.keySet());
      varNames.sort(
          (first, second) -> {
            final int byLength = Integer.compare(second.length(), first.length());
            return byLength == 0 ? first.compareTo(second) : byLength;
          });
      for (String varName : varNames) {
        currentCommand = currentCommand.replace(varName, variables.get(varName) + " ");
      }
      parsedCommands.add(currentCommand);
    }
    return new ParsedSQLCommands(parsedCommands, scanner.commands.size() > 1, scanner.delimited);
  }

  /** Places array-element separators on their own lines, beyond any trailing line comment. */
  static String joinCommands(String[] commands) {
    final StringBuilder text = new StringBuilder();
    for (String command : commands) {
      text.append(command.trim()).append(NL).append(';').append(NL);
    }
    return text.toString();
  }

  /** A lexical scanner; statement syntax and execution remain the database's responsibility. */
  private static final class CommandScanner {
    private final String text;
    private final Vector<String> commands = new Vector<>();
    private final StringBuilder current = new StringBuilder();
    private boolean delimited;

    CommandScanner(String text) {
      this.text = text;
    }

    void scan() {
      char quote = 0;
      int quoteStart = 0;
      String dollarQuote = null;
      int blockDepth = 0;
      int blockStart = 0;
      boolean lineComment = false;
      boolean lineStart = true;
      for (int index = 0; index < text.length(); index++) {
        final char value = text.charAt(index);
        final char next = index + 1 < text.length() ? text.charAt(index + 1) : 0;
        // Keep legacy full-line # comments without stripping PostgreSQL's inline # operators.
        final boolean hashComment = value == '#' && lineStart;
        if (lineBreak(value)) {
          lineStart = true;
        } else if (!Character.isWhitespace(value)
            && !lineComment
            && blockDepth == 0
            && !(quote == 0 && dollarQuote == null && value == '/' && next == '*')) {
          lineStart = false;
        }
        if (lineComment) {
          if (lineBreak(value)) {
            lineComment = false;
            separate();
          }
        } else if (blockDepth > 0) {
          if (value == '/' && next == '*') {
            blockDepth++;
            index++;
          } else if (value == '*' && next == '/') {
            blockDepth--;
            index++;
          }
        } else if (dollarQuote != null) {
          if (text.startsWith(dollarQuote, index)) {
            current.append(dollarQuote);
            index += dollarQuote.length() - 1;
            dollarQuote = null;
          } else {
            current.append(value);
          }
        } else if (quote != 0) {
          current.append(value);
          if (value == quote) {
            if (next == quote) {
              current.append(next);
              index++;
            } else {
              quote = 0;
            }
          }
        } else if (value == '\'' || value == '"' || value == '`') {
          quote = value;
          quoteStart = index;
          current.append(value);
        } else if (value == '/' && next == '*') {
          blockDepth = 1;
          blockStart = index;
          separate();
          index++;
        } else if (value == '-' && next == '-' || hashComment) {
          lineComment = true;
          separate();
          if (value == '-') {
            index++;
          }
        } else if (Character.isWhitespace(value) || value == '\u0085') { // Unicode newline
          separate();
        } else if (value == ';') {
          addCommand();
          delimited = true;
        } else {
          final String delimiter = dollarDelimiter(index);
          if (delimiter != null) {
            dollarQuote = delimiter;
            quoteStart = index;
            current.append(delimiter);
            index += delimiter.length() - 1;
          } else {
            current.append(value);
          }
        }
      }
      if (quote != 0 || dollarQuote != null) {
        throw new IllegalArgumentException(
            "Unterminated SQL quote at character " + (quoteStart + 1));
      }
      if (blockDepth != 0) {
        throw new IllegalArgumentException(
            "Unterminated SQL block comment at character " + (blockStart + 1));
      }
      addCommand();
    }

    private void separate() {
      if (current.length() > 0 && current.charAt(current.length() - 1) != ' ') {
        current.append(' ');
      }
    }

    private static boolean lineBreak(char value) {
      return value == '\r'
          || value == '\n'
          || value == '\u000B'
          || value == '\f'
          || value == '\u0085'
          || value == '\u2028'
          || value == '\u2029'; // Unicode newlines
    }

    private void addCommand() {
      final String command = current.toString().trim();
      if (!command.isEmpty()) {
        commands.add(command);
      }
      current.setLength(0);
    }

    /** Dollar quoting starts at a token boundary; dollar signs inside identifiers are ordinary. */
    private String dollarDelimiter(int index) {
      if (text.charAt(index) != '$' || index > 0 && identifierPart(text.charAt(index - 1))) {
        return null;
      }
      for (int end = index + 1; end < text.length(); end++) {
        final char value = text.charAt(end);
        if (value == '$') {
          return text.substring(index, end + 1);
        }
        if (value != '_'
            && !Character.isLetter(value)
            && (end == index + 1 || !Character.isDigit(value))) {
          return null;
        }
      }
      return null;
    }

    private static boolean identifierPart(char value) {
      return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }
  }
}
