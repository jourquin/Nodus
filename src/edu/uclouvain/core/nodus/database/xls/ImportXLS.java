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
import edu.uclouvain.core.nodus.database.ImportBatch;
import edu.uclouvain.core.nodus.database.ImportProgress;
import edu.uclouvain.core.nodus.database.ImportSource;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableImport;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
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
    return importTable(project, table, xlsx, header, reportError, new ImportProgress());
  }

  /**
   * Imports Excel with progress and cooperative cancellation.
   *
   * @param project destination project and source directory
   * @param table destination table and file basename
   * @param xlsx true for XLSX, false for XLS
   * @param header whether the first row supplies column names
   * @param reportError receiver for validation or database errors
   * @param progress import progress and cancellation control
   * @return true after the entire import succeeds
   */
  public static boolean importTable(
      NodusProject project,
      String table,
      boolean xlsx,
      boolean header,
      Consumer<Exception> reportError,
      ImportProgress progress) {
    return importFile(
        project, table, getFileName(project, table, xlsx), false, header, reportError, progress);
  }

  /**
   * Imports a single-sheet ODS table using the Excel schema and transaction rules.
   *
   * @param project destination project and source directory
   * @param table destination table and source basename
   * @return true after all rows have been imported successfully
   */
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
    return importODS(project, table, header, reportError, new ImportProgress());
  }

  /**
   * Imports ODS with progress and cooperative cancellation.
   *
   * @param project destination project and source directory
   * @param table destination table and file basename
   * @param header whether the first row supplies column names
   * @param reportError receiver for validation or database errors
   * @param progress import progress and cancellation control
   * @return true after the entire import succeeds
   */
  public static boolean importODS(
      NodusProject project,
      String table,
      boolean header,
      Consumer<Exception> reportError,
      ImportProgress progress) {
    return importFile(
        project,
        table,
        project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + table + NodusC.TYPE_ODS,
        true,
        header,
        reportError,
        progress);
  }

  // Workbooks and streams are closed here; the borrowed JDBC connection belongs to the project.
  @SuppressWarnings("resource")
  private static boolean importFile(
      NodusProject nodusProject,
      String tableName,
      String fileName,
      boolean ods,
      boolean header,
      Consumer<Exception> reportError,
      ImportProgress progress) {
    if (!new File(fileName).exists()) {
      return false;
    }
    final ImportSource source;
    try {
      source = new ImportSource(Path.of(fileName));
      progress.phase("Reading");
      if (!ods && fileName.endsWith(NodusC.TYPE_XLSX)) {
        requireSingleXlsxSheet(fileName, progress);
      }
    } catch (Exception error) {
      reportError.accept(error);
      return false;
    }
    try (InputStream input = ImportSource.cancellable(new FileInputStream(fileName), progress);
        Workbook workbook =
            ods
                ? new org.apache.poi.xssf.usermodel.XSSFWorkbook()
                : WorkbookFactory.create(input)) {
      if (ods) {
        OdsCodec.read(input, workbook, progress);
      }
      if (workbook.getNumberOfSheets() != 1) {
        throw new SQLException(
            "Import requires exactly one sheet; found " + workbook.getNumberOfSheets());
      }
      Sheet sheet = workbook.getSheetAt(0);
      Connection connection = nodusProject.getMainJDBCConnection();
      SpreadsheetImportPlan plan =
          SpreadsheetImportPlan.read(connection, tableName, sheet, header, progress);
      int batchSize = nodusProject.getSqlBatchSize();
      if (plan.replacement) {
        TableImport.replace(
            nodusProject,
            tableName,
            (stagingConnection, staged) -> {
              try (Statement statement = stagingConnection.createStatement()) {
                statement.execute(plan.createStatement(staged));
              }
              fillTable(stagingConnection, staged, sheet, plan, batchSize, progress);
              source.verify();
            },
            progress);
      } else {
        TableImport.requireTransactionalRows(connection, tableName);
        boolean autoCommit = connection.getAutoCommit();
        boolean restoreAutoCommit = autoCommit;
        Savepoint savepoint = null;
        try {
          if (autoCommit) {
            connection.setAutoCommit(false);
          }
          savepoint = connection.setSavepoint();
          progress.checkCancelled();
          try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "DELETE FROM " + JDBCUtils.getQuotedCompliantIdentifier(tableName));
          }
          fillTable(connection, tableName, sheet, plan, batchSize, progress);
          source.verify();
          progress.finishing();
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

  /** Reads only workbook metadata so a multi-sheet XLSX is rejected before loading its cells. */
  private static void requireSingleXlsxSheet(String file, ImportProgress progress)
      throws Exception {
    try (org.apache.poi.openxml4j.opc.OPCPackage pkg =
            org.apache.poi.openxml4j.opc.OPCPackage.open(
                new File(file), org.apache.poi.openxml4j.opc.PackageAccess.READ);
        InputStream metadata =
            new org.apache.poi.xssf.eventusermodel.XSSFReader(pkg).getWorkbookData()) {
      javax.xml.stream.XMLInputFactory factory = javax.xml.stream.XMLInputFactory.newFactory();
      factory.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, false);
      factory.setProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      javax.xml.stream.XMLStreamReader xml = factory.createXMLStreamReader(metadata);
      int sheets = 0;
      try {
        while (xml.hasNext()) {
          progress.checkCancelled();
          if (xml.next() == javax.xml.stream.XMLStreamConstants.START_ELEMENT
              && xml.getLocalName().equals("sheet")) {
            sheets++;
          }
        }
      } finally {
        xml.close();
      }
      if (sheets != 1) {
        throw new SQLException("Import requires exactly one sheet; found " + sheets);
      }
    }
  }

  /**
   * Reuses the opened sheet and bounds queued JDBC rows without committing intermediate batches.
   */
  private static void fillTable(
      Connection connection,
      String table,
      Sheet sheet,
      SpreadsheetImportPlan plan,
      int batchSize,
      ImportProgress progress)
      throws SQLException {
    progress.phase("Loading");
    String quoted = JDBCUtils.getQuotedCompliantIdentifier(table);
    String placeholders = String.join(",", java.util.Collections.nCopies(plan.columns.size(), "?"));
    try (PreparedStatement statement =
        connection.prepareStatement("INSERT INTO " + quoted + " VALUES (" + placeholders + ")")) {
      Iterator<Row> rows = sheet.rowIterator();
      ImportBatch batch =
          new ImportBatch(connection, statement, batchSize, "Spreadsheet row", progress);
      int nextRow = plan.firstDataRow;
      while (rows.hasNext()) {
        progress.checkCancelled();
        Row row = rows.next();
        if (row.getRowNum() > plan.lastDataRow) {
          break;
        }
        if (row.getRowNum() < plan.firstDataRow) {
          continue;
        }
        plan.checkRow(row, nextRow++);
        for (int i = 0; i < plan.columns.size(); i++) {
          SpreadsheetImportPlan.Column column = plan.columns.get(i);
          try {
            Object value = column.value(row);
            if (value == null) {
              statement.setNull(i + 1, column.type);
            } else {
              statement.setObject(i + 1, value);
            }
          } catch (SQLException error) {
            if (error.getMessage() != null && error.getMessage().startsWith("Spreadsheet row ")) {
              throw error;
            }
            throw new SQLException(
                "Spreadsheet row "
                    + (row.getRowNum() + 1)
                    + ", column "
                    + (column.source + 1)
                    + ": "
                    + error.getMessage(),
                error);
          }
        }
        batch.add(row.getRowNum() + 1L);
      }
      batch.flush();
    }
  }
}
