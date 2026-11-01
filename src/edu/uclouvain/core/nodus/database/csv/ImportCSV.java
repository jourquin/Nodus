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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.ImportProgress;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.util.function.Consumer;
import javax.swing.JOptionPane;

/**
 * Imports RFC4180 CSV records. Headerless imports use an existing table's column positions. Header
 * imports map names to an existing table or infer and create a new table.
 *
 * @author Bart Jourquin
 */
public class ImportCSV {
  static I18n i18n = Environment.getI18n();

  /** Default constructor. */
  public ImportCSV() {}

  /**
   * Imports the table which name is passed as parameter. The CSV file must be located in the
   * project directory. Headerless imports require an existing table. Header imports match columns
   * by name, or infer a missing table from the complete file. A successful import replaces the
   * existing contents; failed imports preserve them.
   *
   * @param project The Nodus project.
   * @param tableName The name of the table in xhich the file will be imported. The latest must have
   *     the same name.
   * @param withHeader True to map header names or infer a missing table.
   * @return boolean True on success.
   */
  public static boolean importTable(NodusProject project, String tableName, boolean withHeader) {
    return importTable(
        project,
        tableName,
        withHeader,
        message ->
            JOptionPane.showMessageDialog(
                null, message, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE));
  }

  /**
   * Runs an import with an error reporter usable by the SQL console and headless callers.
   *
   * @param project the destination project and source directory
   * @param tableName the destination table and CSV basename
   * @param withHeader true to map header names or infer a missing table
   * @param reportError receives validation or database error messages
   * @return true after the complete import has succeeded
   */
  public static boolean importTable(
      NodusProject project, String tableName, boolean withHeader, Consumer<String> reportError) {
    return importTable(project, tableName, withHeader, reportError, new ImportProgress());
  }

  /**
   * Imports CSV with progress and cooperative cancellation.
   *
   * @param project destination project and source directory
   * @param tableName table and CSV basename
   * @param withHeader whether the first record supplies column names
   * @param reportError receiver for validation or database errors
   * @param progress import progress and cancellation control
   * @return true after the entire import succeeds
   */
  public static boolean importTable(
      NodusProject project,
      String tableName,
      boolean withHeader,
      Consumer<String> reportError,
      ImportProgress progress) {
    return importTable(project, tableName, withHeader, reportError, progress, new CsvOptions());
  }

  /**
   * Imports CSV with per-command encoding, delimiter and optional automatic detection.
   *
   * @param project destination project and source directory
   * @param tableName table and CSV basename
   * @param withHeader whether the first record supplies column names
   * @param reportError receiver for validation or database errors
   * @param progress import progress and cancellation control
   * @param options validated encoding and delimiter settings
   * @return true after the entire import succeeds
   */
  public static boolean importTable(
      NodusProject project,
      String tableName,
      boolean withHeader,
      Consumer<String> reportError,
      ImportProgress progress,
      CsvOptions options) {

    if (!withHeader && !JDBCUtils.tableExists(tableName)) {
      reportError.accept(
          i18n.get(
              ImportCSV.class,
              "Table_structure_must_exist_before_importing_CSV_data",
              "Table structure must exist before importing CSV data"));
      return false;
    }
    return CsvHeaderImport.importTable(
        project, tableName, withHeader, reportError, progress, options);
  }
}
