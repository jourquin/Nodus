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

package edu.uclouvain.core.nodus.database.xls;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableImport;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Iterator;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Imports spreadsheet data using a DBF schema row, an existing table, or an inferred schema.
 * Ordinary headers can be selected explicitly; otherwise new columns are named COL1, COL2, etc.
 *
 * @author Bart Jourquin
 */
public class ImportXLS {

  /** Default constructor. */
  public ImportXLS() {}

  /** Returns the Excel file path associated with the table to import. */
  private static String getFileName(NodusProject nodusProject, String tableName, boolean isXLSX) {
    String fileName =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_XLS;
    if (isXLSX) {
      fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_XLSX;
    }

    return fileName;
  }

  /**
   * Imports the table which name is passed as parameter. The XLSor XLSX file must be located in the
   * project directory. A DBF schema row defines a replacement table. Otherwise an existing table
   * supplies the schema, or the complete sheet is inspected to infer a new table. A successful
   * import replaces the existing contents; failed imports preserve them. Schema-bearing imports are
   * staged before replacing the table (see {@link TableImport}). This method returns true if the
   * file was successfully imported.
   *
   * @param nodusProject The Nodus project.
   * @param tableName The name of the table. Must be the same as the XLS(X) file name, without its
   *     extension.
   * @param isXLSX If true, the table will be imported from an XLSX file instead of the XLS file.
   * @return True on success.
   */
  public static boolean importTable(NodusProject nodusProject, String tableName, boolean isXLSX) {
    return importTable(nodusProject, tableName, isXLSX, false, TableImport::reportError);
  }

  /**
   * Imports Excel data, optionally using ordinary column names in the first row.
   *
   * @param project the destination project and source directory
   * @param table the destination table and source basename
   * @param xlsx true for XLSX, false for XLS
   * @param header true to use ordinary first-row column names
   * @param reportError receives validation or database failures
   * @return true after all rows have been imported successfully
   */
  public static boolean importTable(
      NodusProject project,
      String table,
      boolean xlsx,
      boolean header,
      Consumer<Exception> reportError) {
    return importFile(
        project, table, getFileName(project, table, xlsx), false, header, reportError);
  }

  /** Imports the first ODS sheet using the existing Excel schema and transaction rules. */
  public static boolean importODS(NodusProject project, String table) {
    return importODS(project, table, false, TableImport::reportError);
  }

  /**
   * Imports ODS data, optionally using ordinary column names in the first row.
   *
   * @param project the destination project and source directory
   * @param table the destination table and source basename
   * @param header true to use ordinary first-row column names
   * @param reportError receives validation or database failures
   * @return true after all rows have been imported successfully
   */
  public static boolean importODS(
      NodusProject project, String table, boolean header, Consumer<Exception> reportError) {
    return importFile(
        project,
        table,
        project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + table + NodusC.TYPE_ODS,
        true,
        header,
        reportError);
  }

  // Workbooks and streams are closed here; the borrowed JDBC connection belongs to the project.
  @SuppressWarnings("resource")
  private static boolean importFile(
      NodusProject nodusProject,
      String tableName,
      String fileName,
      boolean ods,
      boolean header,
      Consumer<Exception> reportError) {
    if (!new File(fileName).exists()) {
      return false;
    }
    try (InputStream input = new FileInputStream(fileName);
        Workbook workbook =
            ods
                ? new org.apache.poi.xssf.usermodel.XSSFWorkbook()
                : WorkbookFactory.create(input)) {
      if (ods) {
        OdsCodec.read(input, workbook);
      }
      if (workbook.getNumberOfSheets() == 0) {
        throw new SQLException("Spreadsheet has no sheet");
      }
      Sheet sheet = workbook.getSheetAt(0);
      Connection connection = nodusProject.getMainJDBCConnection();
      SpreadsheetImportPlan plan = SpreadsheetImportPlan.read(connection, tableName, sheet, header);
      int batchSize = nodusProject.getSqlBatchSize();
      if (plan.replacement) {
        TableImport.replace(
            nodusProject,
            tableName,
            (stagingConnection, staged) -> {
              try (Statement statement = stagingConnection.createStatement()) {
                statement.execute(plan.createStatement(staged));
              }
              fillTable(stagingConnection, staged, sheet, plan, batchSize);
            });
      } else {
        boolean autoCommit = connection.getAutoCommit();
        boolean restoreAutoCommit = autoCommit;
        Savepoint savepoint = null;
        try {
          if (autoCommit) {
            connection.setAutoCommit(false);
          }
          savepoint = connection.setSavepoint();
          try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "DELETE FROM " + JDBCUtils.getQuotedCompliantIdentifier(tableName));
          }
          fillTable(connection, tableName, sheet, plan, batchSize);
          if (autoCommit) {
            connection.commit();
          } else {
            connection.releaseSavepoint(savepoint);
          }
        } catch (Exception error) {
          try {
            if (savepoint != null) {
              connection.rollback(savepoint);
            } else if (autoCommit) {
              connection.rollback();
            }
          } catch (SQLException recovery) {
            restoreAutoCommit = false;
            error.addSuppressed(recovery);
          }
          throw error;
        } finally {
          if (restoreAutoCommit) {
            connection.setAutoCommit(true);
          }
        }
      }
      return true;
    } catch (Exception error) {
      reportError.accept(error);
      return false;
    }
  }

  /**
   * Reuses the opened sheet and bounds queued JDBC rows without committing intermediate batches.
   */
  private static void fillTable(
      Connection connection, String table, Sheet sheet, SpreadsheetImportPlan plan, int batchSize)
      throws SQLException {
    String quoted = JDBCUtils.getQuotedCompliantIdentifier(table);
    String placeholders = String.join(",", java.util.Collections.nCopies(plan.columns.size(), "?"));
    try (PreparedStatement statement =
        connection.prepareStatement("INSERT INTO " + quoted + " VALUES (" + placeholders + ")")) {
      Iterator<Row> rows = sheet.rowIterator();
      boolean batchSupported = connection.getMetaData().supportsBatchUpdates();
      int pending = 0;
      while (rows.hasNext()) {
        Row row = rows.next();
        if (row.getRowNum() < plan.firstDataRow) {
          continue;
        }
        for (int i = 0; i < plan.columns.size(); i++) {
          SpreadsheetImportPlan.Column column = plan.columns.get(i);
          Object value = column.value(row);
          if (value == null) {
            statement.setNull(i + 1, column.type);
          } else {
            statement.setObject(i + 1, value);
          }
        }
        if (batchSupported) {
          statement.addBatch();
          if (++pending == batchSize) {
            statement.executeBatch();
            statement.clearBatch();
            pending = 0;
          }
        } else {
          statement.executeUpdate();
        }
      }
      if (pending > 0) {
        statement.executeBatch();
        statement.clearBatch();
      }
    }
  }
}
