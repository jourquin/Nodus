/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.database.xls;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Exports a table in Excel format.
 *
 * @author Bart Jourquin
 */
public class ExportXLS {

  /**
   * Export a database table of the Nodus project in a Excel sheet format.
   *
   * @param nodusProject The Nodus project.
   * @param tableName The name of the table to export.
   * @param isXLSX If true, the table will be exported in XLSX format instead of the XLS format.
   * @return True on success.
   */
  public static boolean exportTable(NodusProject nodusProject, String tableName, boolean isXLSX) {

    JDBCUtils jdbcUtils = new JDBCUtils(nodusProject.getMainJDBCConnection());
    Workbook wbs;
    if (isXLSX) {
      wbs = new XSSFWorkbook();
    } else {
      wbs = new HSSFWorkbook();
    }

    try {
      Sheet sheet = wbs.createSheet(tableName);

      // Browse table
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();

      // Insert table structure in first row
      DatabaseMetaData dbmd = con.getMetaData();
      ResultSet col =
          dbmd.getColumns(null, null, jdbcUtils.getCompliantIdentifier(tableName), null);

      int nbColumns = 0;
      Vector<Boolean> numerical = new Vector<>();
      Row row = sheet.createRow(0);
      while (col.next()) {

        String s = col.getString(4) + ",";
        String type = col.getString(6).toUpperCase();
        if (type.equals("VARCHAR") || type.equals("CHAR")) {
          s += "C," + col.getString(7);
          numerical.add(Boolean.FALSE);
        } else {
          s += "C," + col.getString(7) + "," + col.getString(9);
          numerical.add(Boolean.TRUE);
        }
        Cell cell = row.createCell(nbColumns);
        cell.setCellValue(s);
        nbColumns++;
      }
      col.close();

      // Loop over the rows to import data in table
      String sqlStmt = "select * from " + jdbcUtils.getCompliantIdentifier(tableName);
      ResultSet rs = null;

      try {
        rs = stmt.executeQuery(sqlStmt);
      } catch (Exception e) {
        rs.close();
        return false;
      }

      int currentRow = 1;
      while (rs.next()) {
        row = sheet.createRow(currentRow);

        for (int column = 0; column < nbColumns; column++) {
          Cell cell = row.createCell(column);
          if (numerical.elementAt(column) == true) {
            cell.setCellValue(rs.getDouble(column + 1));
          } else {
            cell.setCellValue(rs.getString(column + 1));
          }
        }
        currentRow++;
      }
      rs.close();
      stmt.close();

      // Write file
      String fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_XLS;
      if (isXLSX) {
        fileName =
            nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                + tableName
                + NodusC.TYPE_XLSX;
      }
      FileOutputStream out = new FileOutputStream(fileName);
      wbs.write(out);
      out.close();
      wbs.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return false;
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }
}
