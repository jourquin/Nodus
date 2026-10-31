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
import edu.uclouvain.core.nodus.database.TableExport;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.util.WorkbookUtil;

/** Streams a table to ODS using the same schema row and numeric conventions as Excel exports. */
final class ExportODS {
  private ExportODS() {}

  static boolean exportTable(NodusProject project, String table) {
    return exportTable(project, table, new TableExport());
  }

  /** Serializes JDBC rows directly into staging, closing every resource before publication. */
  @SuppressWarnings("resource") // The project owns and closes the connection used by the writer.
  static boolean exportTable(NodusProject project, String table, TableExport files) {
    try {
      Connection connection = project.getMainJDBCConnection();
      List<String> headers = new ArrayList<>();
      List<Boolean> numericColumns = new ArrayList<>();
      try (ResultSet columns = JDBCUtils.getColumns(connection, table)) {
        while (columns.next()) {
          if (headers.size() >= SpreadsheetVersion.EXCEL2007.getMaxColumns()) {
            throw new IllegalArgumentException("ODS export exceeds the spreadsheet column limit");
          }
          boolean numeric = !isText(columns.getInt("DATA_TYPE"));
          String description =
              columns.getString("COLUMN_NAME")
                  + (numeric ? ",N," : ",C,")
                  + columns.getString("COLUMN_SIZE");
          if (numeric) {
            description += "," + columns.getString("DECIMAL_DIGITS");
          }
          headers.add(description);
          numericColumns.add(numeric);
        }
      }
      if (headers.isEmpty()) {
        return false;
      }
      boolean[] numerical = new boolean[headers.size()];
      for (int i = 0; i < numerical.length; i++) {
        numerical[i] = numericColumns.get(i);
      }
      String[] descriptions = headers.toArray(new String[0]);
      Path target =
          Path.of(project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + table + NodusC.TYPE_ODS);
      files.write(
          target,
          output -> {
            String sql = "SELECT * FROM " + JDBCUtils.getQuotedCompliantIdentifier(table);
            try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
              if (rows.getMetaData().getColumnCount() != descriptions.length) {
                throw new IllegalArgumentException(
                    "Source table columns changed during ODS export");
              }
              OdsCodec.write(
                  WorkbookUtil.createSafeSheetName(table), descriptions, numerical, rows, output);
            }
          });
      return true;
    } catch (Exception failure) {
      System.err.println(failure.toString());
      return false;
    }
  }

  private static boolean isText(int type) {
    switch (type) {
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.LONGVARCHAR:
      case Types.NCHAR:
      case Types.NVARCHAR:
      case Types.LONGNVARCHAR:
      case Types.DATE:
      case Types.TIME:
      case Types.TIMESTAMP:
      case Types.TIME_WITH_TIMEZONE:
      case Types.TIMESTAMP_WITH_TIMEZONE:
        return true;
      default:
        return false;
    }
  }
}
