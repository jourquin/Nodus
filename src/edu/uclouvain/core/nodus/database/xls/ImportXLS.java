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

import com.bbn.openmap.Environment;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableImport;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Iterator;
import java.util.StringTokenizer;
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
   * Builds the CREATE TABLE statement described by the first row of the Excel sheet, or returns
   * null if the row does not use the DBF-style schema syntax.
   */
  private static String getCreateTableStatement(Workbook wb, String tableName) {
    try {
      // Get the first sheet
      Sheet sheet = wb.getSheetAt(0);

      Iterator<Row> rows = sheet.rowIterator();
      if (!rows.hasNext()) {
        return null;
      }

      Row row = rows.next();
      if (row == null) {
        return null;
      }

      // Parse first row
      StringBuilder sqlStmt = new StringBuilder("CREATE TABLE ");
      sqlStmt.append(JDBCUtils.getQuotedCompliantIdentifier(tableName)).append(" (");
      Iterator<Cell> cells = row.cellIterator();
      if (!cells.hasNext()) {
        return null;
      }

      while (cells.hasNext()) {
        Cell cell = cells.next();
        if (cell.getCellType() != CellType.STRING) {
          return null;
        }

        String content = cell.getStringCellValue();
        // The content must have 3 or four tokens
        StringTokenizer st = new StringTokenizer(content, ",");
        int nbTokens = st.countTokens();
        if (nbTokens != 3 && nbTokens != 4) {
          return null;
        }

        // First token is the name of the field
        final String fieldName = st.nextToken().trim();

        // Second is the type of data. Must be N(umeric) or C(haracters)
        String fieldType = st.nextToken().trim();
        if (!fieldType.equalsIgnoreCase("N") && !fieldType.equalsIgnoreCase("C")) {
          return null;
        }

        if (fieldType.equalsIgnoreCase("N") && nbTokens == 3) {
          return null;
        }

        if (fieldType.equalsIgnoreCase("C") && nbTokens == 4) {
          return null;
        }
        // static boolean oldAutoCommit = false;
        // static boolean result = true;

        // Third is length. Must be a strictly positive number
        int fieldLength = Integer.parseInt(st.nextToken());
        if (fieldLength <= 0) {
          return null;
        }

        // Fourth is precision
        int fieldPrecision = 0;
        if (nbTokens == 4) {
          fieldPrecision = Integer.parseInt(st.nextToken());
          if (fieldPrecision < 0) {
            return null;
          }
        }

        // sqlStmt += "\"" + fieldName + "\"";
        sqlStmt.append(JDBCUtils.getQuotedCompliantIdentifier(fieldName));
        if (fieldType.equalsIgnoreCase("N")) {
          sqlStmt
              .append(" NUMERIC(")
              .append(fieldLength)
              .append(',')
              .append(fieldPrecision)
              .append(')');
        } else {
          sqlStmt.append(" VARCHAR(").append(fieldLength).append(')');
        }

        if (cells.hasNext()) {
          sqlStmt.append(',');
        } else {
          sqlStmt.append(')');
        }
      }

      return sqlStmt.toString();
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Imports the table which name is passed as parameter. The XLSor XLSX file must be located in the
   * project directory. The table must exist unless the first row of the sheet contains the field
   * descriptions in the DBF format. A successful import replaces the existing contents; failed
   * imports preserve them. Schema-bearing imports are staged before replacing the table (see {@link
   * TableImport}). This method returns true if the file was successfully imported.
   *
   * @param nodusProject The Nodus project.
   * @param tableName The name of the table. Must be the same as the XLS(X) file name, without its
   *     extension.
   * @param isXLSX If true, the table will be imported from an XLSX file instead of the XLS file.
   * @return True on success.
   */
  public static boolean importTable(NodusProject nodusProject, String tableName, boolean isXLSX) {
    String fileName = getFileName(nodusProject, tableName, isXLSX);
    if (!new File(fileName).exists()) {
      return false;
    }
    try (InputStream input = new FileInputStream(fileName);
        Workbook workbook = WorkbookFactory.create(input)) {
      String schema = getCreateTableStatement(workbook, tableName);
      if (schema != null) {
        TableImport.replace(
            nodusProject,
            tableName,
            (connection, staged) -> {
              try (Statement statement = connection.createStatement()) {
                statement.execute(getCreateTableStatement(workbook, staged));
              }
              fillTable(connection, staged, workbook.getSheetAt(0), true);
            });
      } else {
        if (!JDBCUtils.tableExists(tableName)) {
          throw new SQLException(
              Environment.getI18n()
                  .get(
                      ImportXLS.class,
                      "Table_structure_must_exist_before_importing_XLS_data",
                      "Table structure must exist before importing XLS data"));
        }
        Connection connection = nodusProject.getMainJDBCConnection();
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
          fillTable(connection, tableName, workbook.getSheetAt(0), false);
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
      TableImport.reportError(error);
      return false;
    }
  }

  private static void fillTable(Connection connection, String table, Sheet sheet, boolean hasSchema)
      throws SQLException {
    String quoted = JDBCUtils.getQuotedCompliantIdentifier(table);
    int[] types;
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT * FROM " + quoted + " WHERE 1=0")) {
      ResultSetMetaData metadata = rows.getMetaData();
      types = new int[metadata.getColumnCount()];
      for (int i = 0; i < types.length; i++) {
        types[i] = metadata.getColumnType(i + 1);
      }
    }
    String placeholders = String.join(",", java.util.Collections.nCopies(types.length, "?"));
    try (PreparedStatement statement =
        connection.prepareStatement("INSERT INTO " + quoted + " VALUES (" + placeholders + ")")) {
      Iterator<Row> rows = sheet.rowIterator();
      if (hasSchema && rows.hasNext()) {
        rows.next();
      }
      while (rows.hasNext()) {
        Row row = rows.next();
        for (int i = 0; i < types.length; i++) {
          Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
          if (types[i] == java.sql.Types.CHAR || types[i] == java.sql.Types.VARCHAR) {
            statement.setString(i + 1, cell == null ? "" : cell.getStringCellValue());
          } else {
            statement.setDouble(i + 1, cell == null ? 0 : cell.getNumericCellValue());
          }
        }
        statement.executeUpdate();
      }
    }
  }
}
