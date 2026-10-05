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

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.csv.ExportCSV;
import edu.uclouvain.core.nodus.database.csv.ImportCSV;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import edu.uclouvain.core.nodus.database.xls.ExportXLS;
import edu.uclouvain.core.nodus.database.xls.ImportXLS;
import java.io.File;
import java.text.MessageFormat;

/** Nodus data commands, overwrite checks, and safeguards for layer tables. */
final class SQLConsoleOperations {
  static final String CLEARSCENARIO = "CLEARSCENARIO";

  static final String EXPORTCSV = "EXPORTCSV";

  static final String EXPORTCSVH = "EXPORTCSVH";

  static final String EXPORTDBF = "EXPORTDBF";

  static final String EXPORTXLS = "EXPORTXLS";

  static final String EXPORTXLSX = "EXPORTXLSX";

  static final String EXTRACTSHP = "EXTRACTSHP";

  static final String IMPORTCSV = "IMPORTCSV";

  static final String IMPORTCSVH = "IMPORTCSVH";

  static final String IMPORTDBF = "IMPORTDBF";

  static final String IMPORTXLS = "IMPORTXLS";

  static final String IMPORTXLSX = "IMPORTXLSX";

  private static final I18n i18n = Environment.getI18n();
  private static final String NL = System.lineSeparator();
  private final SQLConsole console;
  private final NodusProject nodusProject;
  private final NodusMapPanel nodusMapPanel;
  private final boolean withGUI;

  SQLConsoleOperations(SQLConsole console, NodusProject project, boolean withGUI) {
    this.console = console;
    this.nodusProject = project;
    this.nodusMapPanel = project.getNodusMapPanel();
    this.withGUI = withGUI;
  }

  /**
   * Deletes all the tables related to a scenario.
   *
   * @param sqlCommand SQL command
   */
  void clearScenario(String sqlCommand) {
    String scenarioNumber = sqlCommand.toUpperCase();
    int index = scenarioNumber.indexOf(CLEARSCENARIO);

    if (index != -1) {
      scenarioNumber =
          sqlCommand.substring(index + CLEARSCENARIO.length(), sqlCommand.length()).trim();
    }

    int scenario;

    try {
      scenario = Integer.valueOf(scenarioNumber);
    } catch (NumberFormatException e) {
      console.displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"), CLEARSCENARIO + " n");
      return;
    }

    nodusProject.removeScenario(scenario);

    MessageFormat.format(
        i18n.get(SQLConsole.class, "_succeeded", "{0} of \"{1}\" succeeded."),
        CLEARSCENARIO,
        scenarioNumber);
    console.displayMessageInResult(CLEARSCENARIO, scenarioNumber);
  }

  /**
   * Routine that extracts a shapefile according to a where statement. The syntax must be as such:
   * "extractshp from shapefile1 to shapefile2 where 'some sql condition'".
   *
   * @param stmt The command string to process
   * @param confirmOverwrite Whether to ask before replacing existing output files
   * @return true on success.
   */
  boolean extractShp(String stmt, boolean confirmOverwrite) {
    String stmtLower = stmt.toLowerCase();

    // Get source shapefile name
    int index;
    String token = " from ";

    if ((index = stmtLower.indexOf(token)) == -1) {
      console.displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    String fromClause = stmt.substring(index + token.length()).trim();
    String fromClauseLower = fromClause.toLowerCase();
    int toIndexInFromClause = fromClauseLower.indexOf(" to ");
    if (toIndexInFromClause == -1) {
      console.displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    // Get the destination file name
    token = " to ";

    if ((index = stmtLower.indexOf(token)) == -1) {
      console.displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    String toClause = stmt.substring(index + token.length()).trim();

    if ((index = toClause.indexOf(" ")) == -1) {
      console.displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    String toShapefile = toClause.substring(0, index);

    // get where statement
    token = " where ";

    if ((index = stmtLower.indexOf(token)) == -1) {
      console.displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          i18n.get(
              SQLConsole.class,
              "EXTRACTSHP_FROM",
              "EXTRACTSHP FROM shapefile1 TO shapefile2 WHERE some sql condition"));

      return false;
    }

    String whereStmt = stmt.substring(index + token.length()).trim();

    // Test if it is a valid link layer
    NodusEsriLayer[] layers = nodusProject.getLinkLayers();
    String fromShapefile = fromClause.substring(0, toIndexInFromClause).trim();

    // Check before either layer type can write over its own source files.
    if (fromShapefile.equals(toShapefile)) {
      console.displayMessageInResult(
          i18n.get(SQLConsole.class, "Error", "Error"),
          i18n.get(
              SQLConsole.class,
              "Cannot_extract_a_file_to_itself",
              "Cannot extract a file to itself."));
      return false;
    }

    for (NodusEsriLayer element : layers) {
      if (element.getTableName().equals(fromShapefile)) {
        if (confirmOverwrite
            && !confirmExportOverwrite(EXTRACTSHP, toShapefile, getExtractionFiles(toShapefile))) {
          return false;
        }
        boolean ok = element.extract(toShapefile, whereStmt);

        if (ok) {
          console.displayMessageInResult(
              "ExtractShp",
              MessageFormat.format(
                  i18n.get(SQLConsole.class, "successfuly_extracted", "{0} successfuly extracted."),
                  toShapefile));

          console.addToRecent(stmt);
        } else {
          console.displayMessageInResult(
              "ExtractShp",
              MessageFormat.format(
                  i18n.get(SQLConsole.class, "Error_on_extracting", "Error on extracting {0}"),
                  toShapefile));
        }

        return ok;
      }
    }

    // Test if it is a valid node layer
    layers = nodusProject.getNodeLayers();

    for (NodusEsriLayer element : layers) {
      if (element.getTableName().equals(fromShapefile)) {
        if (confirmOverwrite
            && !confirmExportOverwrite(EXTRACTSHP, toShapefile, getExtractionFiles(toShapefile))) {
          return false;
        }
        boolean ok = element.extract(toShapefile, whereStmt);

        if (ok) {
          console.displayMessageInResult(
              "ExtractShp",
              MessageFormat.format(
                  i18n.get(SQLConsole.class, "successfuly_extracted", "{0} successfuly extracted."),
                  toShapefile));
          console.addToRecent(stmt);

          return true;
        } else {
          console.displayMessageInResult(
              "ExtractShp",
              i18n.get(
                  SQLConsole.class, "Error_on_extracting", "Error on extracting {0}", toShapefile));

          return false;
        }
      }
    }

    // No valid layer was found
    console.displayMessageInResult(
        i18n.get(SQLConsole.class, "Error", "Error"),
        MessageFormat.format(
            i18n.get(SQLConsole.class, "is_not_a_valid_shapefile", "{0} is not a valid shapefile."),
            fromShapefile));

    return false;
  }

  /**
   * Handles the Nodus specific import/export commands.
   *
   * @param sqlStmt String The command to process
   * @param operation String The operation to process (IMPORTDBF, EXPORTDBF, IMPORTCSV,
   *     EXPORTCSV,...)
   * @param confirmOverwrite Whether to ask before replacing an existing table or output file
   * @return true on success.
   */
  boolean importExport(String sqlStmt, String operation, boolean confirmOverwrite) {
    String s = sqlStmt.toUpperCase();
    int index = s.indexOf(operation);

    if (index != -1) {
      s = sqlStmt.substring(index + operation.length(), sqlStmt.length());
    }

    String tableName = s.trim();

    if (tableName.length() == 0) {
      console.displayMessageInResult(
          i18n.get(SQLConsole.class, "Usage", "Usage:"),
          MessageFormat.format(
              i18n.get(SQLConsole.class, "TableName", "{0} TableName"), operation));

      return false;
    }

    File exportFile = getExportFile(operation, tableName);
    if (confirmOverwrite
        && exportFile != null
        && !confirmExportOverwrite(operation, tableName, exportFile)) {
      return false;
    }
    if (confirmOverwrite
        && operation.startsWith("IMPORT")
        && !confirmImportOverwrite(operation, tableName)) {
      return false;
    }

    boolean succeeded = false;

    if (operation.equals(EXPORTDBF)) {
      succeeded = ExportDBF.exportTable(nodusProject, tableName);
    } else if (operation.equals(IMPORTDBF)) {
      succeeded = ImportDBF.importTable(nodusProject, tableName);
    } else if (operation.equals(EXPORTCSV)) {
      succeeded = ExportCSV.exportTable(nodusProject, tableName, false);
    } else if (operation.equals(EXPORTCSVH)) {
      succeeded = ExportCSV.exportTable(nodusProject, tableName, true);
    } else if (operation.equals(IMPORTCSV)) {
      succeeded = ImportCSV.importTable(nodusProject, tableName, false);
    } else if (operation.equals(IMPORTCSVH)) {
      succeeded = ImportCSV.importTable(nodusProject, tableName, true);
    } else if (operation.equals(IMPORTXLS)) {
      succeeded = ImportXLS.importTable(nodusProject, tableName, false);
    } else if (operation.equals(EXPORTXLS)) {
      succeeded = ExportXLS.exportTable(nodusProject, tableName, false);
    } else if (operation.equals(IMPORTXLSX)) {
      succeeded = ImportXLS.importTable(nodusProject, tableName, true);
    } else if (operation.equals(EXPORTXLSX)) {
      succeeded = ExportXLS.exportTable(nodusProject, tableName, true);
    }

    String g = "";
    if (succeeded) {
      g =
          MessageFormat.format(
              i18n.get(SQLConsole.class, "_succeeded", "{0} of \"{1}\" succeeded."),
              operation,
              tableName);

    } else {
      g =
          MessageFormat.format(
              i18n.get(SQLConsole.class, "_failed", "{0} of \"{1}\"{ failed."),
              operation,
              tableName);
    }

    console.displayMessageInResult(operation, g);

    if (succeeded) {
      console.addToRecent(sqlStmt);
    }

    return succeeded;
  }

  /** Resolves the same output path as the table exporters; imports have no output file. */
  private File getExportFile(String operation, String tableName) {
    String extension;
    switch (operation) {
      case EXPORTDBF:
        extension = NodusC.TYPE_DBF;
        break;
      case EXPORTCSV:
      case EXPORTCSVH:
        extension = NodusC.TYPE_CSV;
        break;
      case EXPORTXLS:
        extension = NodusC.TYPE_XLS;
        break;
      case EXPORTXLSX:
        extension = NodusC.TYPE_XLSX;
        break;
      default:
        return null;
    }
    return new File(
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + extension);
  }

  /** Lists the SHP/SHX and separately written DBF paths used by layer extraction. */
  private File[] getExtractionFiles(String name) {
    String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + name;
    String shapePath = path;
    // OpenMap strips an optional shapefile extension; the separate DBF writer appends its own.
    if (path.endsWith(NodusC.TYPE_SHP)
        || path.endsWith(NodusC.TYPE_SHX)
        || path.endsWith(NodusC.TYPE_DBF)) {
      shapePath = path.substring(0, path.length() - 4);
    }
    return new File[] {
      new File(shapePath + NodusC.TYPE_SHP),
      new File(shapePath + NodusC.TYPE_SHX),
      new File(path + NodusC.TYPE_DBF)
    };
  }

  /** Asks before an importer can delete rows or replace the destination table's structure. */
  private boolean confirmImportOverwrite(String operation, String name) {
    if (!JDBCUtils.tableExists(name)) {
      return true;
    }
    String message =
        MessageFormat.format(
            i18n.get(
                SQLConsole.class,
                "Replace_existing_table",
                "Replace existing table \"{0}\"?\n\nIts current contents will be lost."),
            name);
    if (console.confirmOverwrite(message)) {
      return true;
    }
    return reportOverwriteCancelled(operation, name, "Import_cancelled");
  }

  /** Reports a declined export separately from a failed export, stopping the command batch. */
  private boolean confirmExportOverwrite(String operation, String name, File... files) {
    if (confirmFileOverwrite(files)) {
      return true;
    }
    return reportOverwriteCancelled(operation, name, "Export_cancelled");
  }

  /** Reports cancellation without adding the declined command to successful query history. */
  private boolean reportOverwriteCancelled(String operation, String name, String messageKey) {
    String message =
        MessageFormat.format(
            i18n.get(SQLConsole.class, messageKey, "{0} of \"{1}\" cancelled."), operation, name);
    if (!withGUI && console.typeOfResultFormat != 1) {
      System.out.println(message);
    }
    console.displayMessageInResult(operation, message);
    return false;
  }

  /** Asks once for all existing targets before any file is opened or layer data is updated. */
  boolean confirmFileOverwrite(File... files) {
    boolean fullPath = nodusMapPanel != null && nodusMapPanel.getDisplayFullPath();
    StringBuilder existing = new StringBuilder();
    for (File file : files) {
      if (file.exists()) {
        if (existing.length() > 0) {
          existing.append(NL);
        }
        existing.append(fullPath ? file.getAbsolutePath() : file.getName());
      }
    }
    if (existing.length() == 0) {
      return true;
    }
    String message =
        MessageFormat.format(
            i18n.get(SQLConsole.class, "Replace_existing_files", "Replace existing file?\n\n{0}"),
            existing.toString());
    return console.confirmOverwrite(message);
  }

  /**
   * Returns true if delete SQL operations are allowed. They are not allowed for the tables that
   * correspond to the DBF files of shapefiles.
   */
  boolean isDeleteAllowed(String sqlStmt) {
    String tableName = getProtectedTableName(sqlStmt, " from ", false);

    if (tableName == null) {
      // probably an error in the SQL statement. To be handled by the jdbc driver
      return true;
    }

    // Is it a Nodus layer?
    if (nodusProject.getLayer(tableName) == null) {
      return true;
    }

    console.displayMessageInResult(
        i18n.get(SQLConsole.class, "Error", "Error"),
        i18n.get(
            SQLConsole.class,
            "Delete_not_allowed_on_Nodus_layers",
            "SQL DELETE operations not allowed on Nodus layers"));
    return false;
  }

  /**
   * Returns true if insert SQL operations are allowed. They are not allowed for the tables that
   * correspond to the DBF files of shapefiles.
   */
  boolean isInsertAllowed(String sqlStmt) {
    String tableName = getProtectedTableName(sqlStmt, " into ", true);

    if (tableName == null) {
      // probably an error in the SQL statement. To be handled by the jdbc driver
      return true;
    }

    // Is it a Nodus layer?
    if (nodusProject.getLayer(tableName) == null) {
      return true;
    }

    console.displayMessageInResult(
        i18n.get(SQLConsole.class, "Error", "Error"),
        i18n.get(
            SQLConsole.class,
            "Insert_not_allowed_on_Nodus_layers",
            "SQL INSERT operations not allowed on Nodus layers"));
    return false;
  }

  /**
   * Extracts and normalizes the table name targeted by a protected SQL command.
   *
   * @param sqlStmt Full SQL statement.
   * @param token Clause token used to locate the table name.
   * @param stopAtParenthesis Whether an opening parenthesis may terminate the identifier.
   * @return The normalized table name, or {@code null} if the token was not found.
   */
  private String getProtectedTableName(String sqlStmt, String token, boolean stopAtParenthesis) {
    String lowerSqlStmt = sqlStmt.toLowerCase();
    int index = lowerSqlStmt.indexOf(token);
    if (index == -1) {
      return null;
    }

    String remainder = sqlStmt.substring(index + token.length()).trim();
    String identifier = extractSqlIdentifier(remainder, stopAtParenthesis);
    if (identifier == null || identifier.isBlank()) {
      return "";
    }

    String tableName = getLastQualifiedIdentifierPart(identifier);
    tableName = unquoteSqlIdentifier(tableName);
    return JDBCUtils.getCompliantIdentifier(tableName);
  }

  /** Reads a SQL identifier, preserving quoted segments and optional schema qualification. */
  private String extractSqlIdentifier(String text, boolean stopAtParenthesis) {
    StringBuilder identifier = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
        identifier.append(c);
        continue;
      }

      if (!inQuotes && (Character.isWhitespace(c) || c == ',' || (stopAtParenthesis && c == '('))) {
        break;
      }

      identifier.append(c);
    }

    return identifier.toString().trim();
  }

  /** Returns the rightmost part of a possibly schema-qualified SQL identifier. */
  private String getLastQualifiedIdentifierPart(String identifier) {
    boolean inQuotes = false;
    int splitIndex = -1;

    for (int i = 0; i < identifier.length(); i++) {
      char c = identifier.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == '.' && !inQuotes) {
        splitIndex = i;
      }
    }

    if (splitIndex == -1) {
      return identifier;
    }

    return identifier.substring(splitIndex + 1).trim();
  }

  /** Removes surrounding double quotes from a SQL identifier. */
  private String unquoteSqlIdentifier(String identifier) {
    String trimmed = identifier.trim();
    if (trimmed.length() >= 2
        && trimmed.charAt(0) == '"'
        && trimmed.charAt(trimmed.length() - 1) == '"') {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }
}
