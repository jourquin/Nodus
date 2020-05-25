/*
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.StringTokenizer;
import javax.swing.JOptionPane;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Imports a .xls(x) file into a database table. The table structure must already exist in the
 * database before the importation process, unless the first row of the sheet contains the format of
 * each column, following the DBF standard.
 *
 * @author Bart Jourquin
 */
public class ImportXLS {

  private static boolean couldCreateTable = false;

  private static I18n i18n = Environment.getI18n();

  /**
   * Creates a database table using the field descriptions found in the first row of the Excel
   * sheet.
   */
  private static boolean createTable(NodusProject nodusProject, String tableName, boolean isXLSX) {
    // Open a reader for the xls file
    String fileName =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_XLS;
    if (isXLSX) {
      fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_XLSX;
    }

    InputStream inp;
    try {
      inp = new FileInputStream(fileName);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return false;
    }

    Workbook wb;

    try {
      wb = WorkbookFactory.create(inp);
    } catch (EncryptedDocumentException e1) {
      e1.printStackTrace();
      return false;
    } catch (IOException e1) {
      e1.printStackTrace();
      return false;
    }

    // Get the first sheet
    Sheet sheet = wb.getSheetAt(0);

    Iterator<Row> rows = sheet.rowIterator();
    Row row = rows.next();
    if (row == null) {
      return false;
    }

    // Parse first row
    String sqlStmt = "CREATE TABLE " + tableName + " (";
    Iterator<Cell> cells = row.cellIterator();

    while (cells.hasNext()) {
      Cell cell = cells.next();
      if (cell.getCellType() != CellType.STRING) {
        return false;
      }

      String content = cell.getStringCellValue();
      // The content must have 3 or four tokens
      StringTokenizer st = new StringTokenizer(content, ",");
      int nbTokens = st.countTokens();
      if (nbTokens != 3 && nbTokens != 4) {
        return false;
      }

      // First token is the name of the field
      final String fieldName = st.nextToken().trim();

      // Second is the type of data. Must be N(umeric) or C(haracters)
      String fieldType = st.nextToken().trim();
      if (!fieldType.equalsIgnoreCase("N") && !fieldType.equalsIgnoreCase("C")) {
        return false;
      }

      if (fieldType.equalsIgnoreCase("N") && nbTokens == 3) {
        return false;
      }

      if (fieldType.equalsIgnoreCase("C") && nbTokens == 4) {
        return false;
      }
      // static boolean oldAutoCommit = false;
      // static boolean result = true;

      // Third is length. Must be a strictly positive number
      int fieldLength = Integer.parseInt(st.nextToken());
      if (fieldLength <= 0) {
        return false;
      }

      // Fourth is precision
      int fieldPrecision = 0;
      if (nbTokens == 4) {
        fieldPrecision = Integer.parseInt(st.nextToken());
        if (fieldPrecision < 0) {
          return false;
        }
      }

      //sqlStmt += "\"" + fieldName + "\"";
      sqlStmt += JDBCUtils.getQuotedCompliantIdentifier(fieldName);
      if (fieldType.equalsIgnoreCase("N")) {
        sqlStmt += " NUMERIC(" + fieldLength + "," + fieldPrecision + ")";
      } else {
        sqlStmt += " VARCHAR(" + fieldLength + ")";
      }

      if (cells.hasNext()) {
        sqlStmt += ",";
      } else {
        sqlStmt += ")";
      }
    }
    // Now create the table
    try {
      Connection con = nodusProject.getMainJDBCConnection();
      JDBCUtils.dropTable(tableName);
      Statement stmt = con.createStatement();
      stmt.execute(sqlStmt);
      stmt.close();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    couldCreateTable = true;
    return true;
  }

  /**
   * Imports the table which name is passed as parameter. The XLSor XLSX file must be located in the
   * project directory. The table must exist unless the first row of the sheet contains the field
   * descriptions in the DBF format. If the table is already filled, all the existing records are
   * removed before import. This method returns true if the file was successfully imported.
   *
   * @param nodusProject The Nodus project.
   * @param tableName The name of the table. Must be the same as the XLS(X) file name, without its
   *     extension.
   * @param isXLSX If true, the table will be imported from an XLSX file instead of the XLS file.
   * @return True on success.
   */
  public static boolean importTable(NodusProject nodusProject, String tableName, boolean isXLSX) {

    // Test if table can be created from the the content of the first row of the .xls file
    if (!createTable(nodusProject, tableName, isXLSX)) {
      // Table must exist in order to know which structure it has
      if (!JDBCUtils.tableExists(tableName)) {
        JOptionPane.showMessageDialog(
            null,
            i18n.get(
                ImportXLS.class,
                "Table_structure_must_exist_before_importing_XLS_data",
                "Table structure must exist before importing XLS data"),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);

        return false;
      }
    }

    Connection con = nodusProject.getMainJDBCConnection();
    Statement stmt;
    PreparedStatement prepStmt;

    // Clean table
    String sqlStmt;
    ResultSetMetaData metaData;
    ResultSet rs;

    try {
      stmt = con.createStatement();
      sqlStmt = "delete from " + JDBCUtils.getCompliantIdentifier(tableName);
      stmt.executeUpdate(sqlStmt);

      // Get table structure
      sqlStmt = "select * from " + JDBCUtils.getCompliantIdentifier(tableName);
      rs = stmt.executeQuery(sqlStmt);
      metaData = rs.getMetaData();
    } catch (SQLException e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    // Open a reader for the xls file
    String fileName =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_XLS;
    if (isXLSX) {
      fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_XLSX;
    }

    InputStream inp;
    try {
      inp = new FileInputStream(fileName);
    } catch (FileNotFoundException e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    // Read records and import them in SQL database
    try {
      Workbook wb = WorkbookFactory.create(inp);

      // Get the first sheet
      Sheet sheet = wb.getSheetAt(0);

      // Loop over the rows to import data in table
      Iterator<Row> rows = sheet.rowIterator();
      if (couldCreateTable) {
        rows.next();
      }

      int nbCols = metaData.getColumnCount();

      // Use a prepared statement to increase insert speed
      sqlStmt = "INSERT INTO " + tableName + " VALUES (";
      for (int i = 0; i < nbCols; i++) {
        sqlStmt += "?";
        if (i < nbCols - 1) {
          sqlStmt += ",";
        }
      }
      sqlStmt += ")";
      prepStmt = con.prepareStatement(sqlStmt);

      while (rows.hasNext()) {
        Row row = rows.next();

        for (int i = 0; i < nbCols; i++) {
          Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

          if (metaData.getColumnType(i + 1) == java.sql.Types.CHAR
              || metaData.getColumnType(i + 1) == java.sql.Types.VARCHAR) {
            String s = "";
            if (cell != null) {
              s = cell.getStringCellValue();
            }
            prepStmt.setString(i + 1, s);

          } else {
            double d = 0;
            if (cell != null) {
              d = cell.getNumericCellValue();
            }
            prepStmt.setDouble(i + 1, d);
          }
        }
        prepStmt.execute();
      }

      stmt.close();
      prepStmt.close();
      rs.close();

      if (!con.getAutoCommit()) {
        con.commit();
      }
    } catch (IOException e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    } catch (SQLException e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    return true;
  }
}
