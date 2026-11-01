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

package edu.uclouvain.core.nodus.database.csv;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableExport;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.function.Consumer;

/**
 * Simple CSV file writer.
 *
 * @author Bart Jourquin
 */
public class ExportCSV {

  /** Default constructor. */
  public ExportCSV() {}

  /** Escapes a value according to RFC4180 so exported files can be safely re-imported. */
  private static String escapeCsvValue(Object value, char delimiter) {
    if (value == null) {
      return "";
    }

    String text = value.toString();
    if (text.indexOf(delimiter) == -1
        && text.indexOf('"') == -1
        && text.indexOf('\n') == -1
        && text.indexOf('\r') == -1) {
      return text;
    }

    return '"' + text.replace("\"", "\"\"") + '"';
  }

  /**
   * Exports the table which name is given as parameter in the directory of the project. Returns
   * true if the table is successfully exported.
   *
   * <p>Writes a sibling temporary file and publishes it only after the writer and JDBC resources
   * close successfully. Existing exports require atomic replacement and remain intact on failure.
   *
   * @param project The Nodus project.
   * @param tableName The name of the file to export.
   * @param withHeader If true, the first line will contain the field names.
   * @return boolean True on success.
   */
  public static boolean exportTable(NodusProject project, String tableName, boolean withHeader) {
    return exportTable(project, tableName, withHeader, new TableExport());
  }

  /**
   * Exports CSV with a strict encoder and a per-command explicit delimiter.
   *
   * @param project source project and output directory
   * @param tableName source table and output basename
   * @param withHeader whether to write column names
   * @param options validated encoding and explicit separator
   * @param reportError receives export failures without opening a dialog
   * @return true after the complete output file has been published
   */
  public static boolean exportTable(
      NodusProject project,
      String tableName,
      boolean withHeader,
      CsvOptions options,
      Consumer<Exception> reportError) {
    return exportTable(project, tableName, withHeader, new TableExport(), options, reportError);
  }

  /** Uses the production staging workflow with injectable file operations for failure tests. */
  static boolean exportTable(
      NodusProject project, String tableName, boolean withHeader, TableExport files) {
    return exportTable(
        project,
        tableName,
        withHeader,
        files,
        new CsvOptions(),
        error -> System.out.println(error.toString()));
  }

  @SuppressWarnings("resource") // The project owns the borrowed source connection.
  private static boolean exportTable(
      NodusProject project,
      String tableName,
      boolean withHeader,
      TableExport files,
      CsvOptions options,
      Consumer<Exception> reportError) {
    String fileName =
        project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_CSV;

    try {
      if (options.isAutomatic()) {
        throw new IllegalArgumentException("DELIMITER AUTO is only valid for CSV imports");
      }
      Connection con = project.getMainJDBCConnection();
      if (!TableExport.hasSourceColumns(con, tableName)) {
        return false;
      }
      files.write(
          Path.of(fileName),
          output -> {
            try (Statement stmt = con.createStatement();
                ResultSet rs =
                    stmt.executeQuery(
                        "SELECT * FROM " + JDBCUtils.getQuotedCompliantIdentifier(tableName))) {

              ResultSetMetaData rsmd = rs.getMetaData();
              int nbColumns = rsmd.getColumnCount();

              try (BufferedWriter bw =
                  new BufferedWriter(
                      new OutputStreamWriter(
                          output,
                          options
                              .getCharset()
                              .newEncoder()
                              .onMalformedInput(CodingErrorAction.REPORT)
                              .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
                // Save header if needed
                if (withHeader) {
                  for (int i = 0; i < nbColumns; i++) {
                    bw.write(escapeCsvValue(rsmd.getColumnName(i + 1), options.getDelimiter()));
                    if (i < nbColumns - 1) {
                      bw.write(options.getDelimiter());
                    }
                  }
                  bw.newLine();
                }

                // Retrieve result of query
                while (rs.next()) {
                  for (int i = 0; i < nbColumns; i++) {
                    bw.write(escapeCsvValue(rs.getObject(i + 1), options.getDelimiter()));

                    if (i < nbColumns - 1) {
                      bw.write(options.getDelimiter());
                    }
                  }

                  bw.newLine();
                }
              }
            }
          });
    } catch (Exception ex) {
      reportError.accept(new IOException(options.errorMessage(ex, true), ex));

      return false;
    }

    return true;
  }
}
