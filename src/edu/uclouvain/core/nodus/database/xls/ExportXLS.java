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
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import java.awt.GraphicsEnvironment;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.Vector;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Exports a table in Excel format.
 *
 * @author Bart Jourquin
 */
public class ExportXLS {

  /** Maximum number of XLSX rows retained in memory; older rows use compressed temporary files. */
  private static final int ROW_WINDOW = 100;

  /** Default constructor. */
  public ExportXLS() {}

  /**
   * Exports a database table, with a DBF-style schema in the first row.
   *
   * <p>XLSX rows are streamed to temporary files, which the workbook closes and deletes on success
   * or failure. Legacy XLS retains its workbook in memory. Both formats reject rows or columns
   * beyond their sheet limits; the schema consumes one row of that limit.
   *
   * @param nodusProject The Nodus project.
   * @param tableName The name of the table to export.
   * @param isXLSX If true, the table will be exported in XLSX format instead of the XLS format.
   * @return True on success.
   */
  public static boolean exportTable(NodusProject nodusProject, String tableName, boolean isXLSX) {

    try (Workbook wbs =
        isXLSX ? new SXSSFWorkbook(null, ROW_WINDOW, true, false) : new HSSFWorkbook()) {
      SpreadsheetVersion version =
          isXLSX ? SpreadsheetVersion.EXCEL2007 : SpreadsheetVersion.EXCEL97;
      String sheetName = WorkbookUtil.createSafeSheetName(tableName);
      if (sheetName.length() > 31) {
        sheetName = sheetName.substring(0, 31);
      }
      Sheet sheet = wbs.createSheet(sheetName);

      // Browse table. Do not close the project-owned JDBC connection here.
      Connection con = nodusProject.getMainJDBCConnection();

      // Insert table structure in first row
      int nbColumns = 0;
      Vector<Boolean> numerical = new Vector<>();
      Row row = sheet.createRow(0);
      try (ResultSet col = JDBCUtils.getColumns(tableName)) {
        while (col.next()) {
          if (nbColumns >= version.getMaxColumns()) {
            throw new IllegalArgumentException(
                "Excel export supports at most " + version.getMaxColumns() + " columns.");
          }

          String s = col.getString(4) + ",";
          // JDBC type codes also cover vendor names such as H2's CHARACTER VARYING.
          int type = col.getInt("DATA_TYPE");
          if (type == Types.CHAR
              || type == Types.VARCHAR
              || type == Types.LONGVARCHAR
              || type == Types.NCHAR
              || type == Types.NVARCHAR
              || type == Types.LONGNVARCHAR) {
            s += "C," + col.getString(7);
            numerical.add(Boolean.FALSE);
          } else if (type == Types.DATE
              || type == Types.TIME
              || type == Types.TIMESTAMP
              || type == Types.TIME_WITH_TIMEZONE
              || type == Types.TIMESTAMP_WITH_TIMEZONE) {
            s += "C," + col.getString(7);
            numerical.add(Boolean.FALSE);
          } else {
            s += "N," + col.getString(7) + "," + col.getString(9);
            numerical.add(Boolean.TRUE);
          }
          Cell cell = row.createCell(nbColumns);
          cell.setCellValue(s);
          nbColumns++;
        }
      }

      // Loop over the rows to export data from table
      String sqlStmt = "select * from " + JDBCUtils.getQuotedCompliantIdentifier(tableName);

      try (Statement stmt = con.createStatement();
          ResultSet rs = stmt.executeQuery(sqlStmt)) {
        int currentRow = 1;
        while (rs.next()) {
          if (currentRow >= version.getMaxRows()) {
            throw new IllegalArgumentException(
                "Excel export supports at most "
                    + (version.getMaxRows() - 1)
                    + " data rows because the first row contains the schema.");
          }
          row = sheet.createRow(currentRow);

          for (int column = 0; column < nbColumns; column++) {
            Cell cell = row.createCell(column);
            if (Boolean.TRUE.equals(numerical.elementAt(column))) {
              Object value = rs.getObject(column + 1);
              if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
              }
            } else {
              cell.setCellValue(rs.getString(column + 1));
            }
          }
          currentRow++;
        }
      }

      // Write file
      String fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_XLS;
      if (isXLSX) {
        fileName =
            nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                + tableName
                + NodusC.TYPE_XLSX;
      }
      try (FileOutputStream out = new FileOutputStream(fileName)) {
        wbs.write(out);
      }
    } catch (Exception e) {
      if (!GraphicsEnvironment.isHeadless()) {
        new NodusConsole();
      }
      // nodusProject.getNodusMapPanel().setBusy(false);
      e.printStackTrace();
      return false;
    }

    return true;
  }
}
