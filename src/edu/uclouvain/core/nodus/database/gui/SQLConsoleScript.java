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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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
    try (BufferedReader read = new BufferedReader(new FileReader(file))) {
      StringBuilder b = new StringBuilder();
      String s;

      while ((s = read.readLine()) != null) {
        b.append(s);
        b.append(NL);
      }

      return b.toString();
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
   * Decompose the batch file into commands. A regular batch command must end with a ";" but can be
   * written on multiple lines. A comment starts with a "--" or "#". A command block is delimited as
   * in C or Java. Variable definitions start with @@
   */
  static ParsedSQLCommands parseSQLCommands(String sqlCommand) {

    // Comparator used to sort strings in reverse order
    class LengthComparator implements Comparator<String> {
      public int compare(String o1, String o2) {
        return -o1.compareTo(o2);
      }
    }

    boolean isBatchFile = false;

    // Remove block comments
    boolean hasBlockComment = true;
    while (hasBlockComment) {
      hasBlockComment = false;
      int beginIdx = sqlCommand.indexOf("/*");
      int endIdx = sqlCommand.indexOf("*/");
      if (beginIdx != -1 && endIdx != -1 && beginIdx < endIdx) {
        String p1 = sqlCommand.substring(0, beginIdx);
        String p2 = sqlCommand.substring(endIdx + 2, sqlCommand.length());
        sqlCommand = p1 + " " + p2;
        hasBlockComment = true;
      }
    }

    // Split per line
    String[] line = sqlCommand.split("\\R");

    // Concatenate multi-line commands
    Vector<String> commandsToParse = new Vector<>();
    String currentCommand = "";
    for (int i = 0; i < line.length; i++) {
      line[i] = line[i].trim();

      // Ignore comments
      if (line[i].startsWith("#") || line[i].startsWith("--")) {
        continue;
      }

      currentCommand += line[i];
      if (line[i].endsWith(";")) {
        // Remove trailing semi-column and store command
        commandsToParse.add(currentCommand.substring(0, currentCommand.length() - 1).trim());
        currentCommand = "";
        isBatchFile = true;
      } else {
        currentCommand += " ";
      }
    }

    // Parse the commands
    Vector<String> parsedCommands = new Vector<>();

    // Single command
    if (!isBatchFile) {
      parsedCommands.add(currentCommand);
      return new ParsedSQLCommands(parsedCommands, false, false);
    }

    // The names of the variables are stored and sorted by length from longest to shortest.
    // This is needed for this simple parser
    Map<String, String> variables = new TreeMap<String, String>(new LengthComparator());

    for (int i = 0; i < commandsToParse.size(); i++) {

      currentCommand = commandsToParse.get(i).trim();

      // Handle variable definitions
      if (currentCommand.startsWith("@@")) {
        int idx = currentCommand.indexOf(":=");
        if (idx != -1) {
          String varName = currentCommand.substring(0, idx).trim();
          String varValue = currentCommand.substring(idx + 2, currentCommand.length());
          if (varValue.endsWith(";")) {
            varValue = varValue.substring(0, varValue.length() - 1).trim();
          }

          variables.put(varName, varValue);
          continue;
        }
      }

      // Replace all the user defined variables by their value.
      // Longer variable names must be replaced first to avoid partial replacements.
      List<String> varNames = new ArrayList<>(variables.keySet());

      varNames.sort(
          (varName1, varName2) -> {
            int lengthComparison = Integer.compare(varName2.length(), varName1.length());

            if (lengthComparison != 0) {
              return lengthComparison;
            }

            return varName1.compareTo(varName2);
          });

      for (String varName : varNames) {
        String varValue = variables.get(varName) + " ";
        currentCommand = currentCommand.replace(varName, varValue);
      }

      // Store the parsed command command
      parsedCommands.add(currentCommand);
    }

    return new ParsedSQLCommands(parsedCommands, commandsToParse.size() > 1, isBatchFile);
  }
}
