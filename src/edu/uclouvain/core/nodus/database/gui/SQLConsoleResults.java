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

import edu.uclouvain.core.nodus.swing.GridSwing;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/** Materializes JDBC results and formats the SQL console text output. */
final class SQLConsoleResults {

  private static final String NL = System.lineSeparator();

  private SQLConsoleResults() {}

  static final class FormattedResult {
    final String[] header;
    final List<String[]> rows;
    final boolean maxRowsReached;

    private FormattedResult(String[] header, List<String[]> rows, boolean maxRowsReached) {
      this.header = header;
      this.rows = rows;
      this.maxRowsReached = maxRowsReached;
    }
  }

  /** Reads and closes the result set before the console applies the data on the EDT. */
  static FormattedResult read(Statement stmt, int maxRows) throws SQLException {
    ResultSet r = stmt.getResultSet();
    if (r == null) {
      return null;
    }
    try (ResultSet result = r) {
      ResultSetMetaData m = result.getMetaData();

      int col = m.getColumnCount();
      String[] header = new String[col];
      List<String[]> rows = new ArrayList<>();

      for (int i = 1; i <= col; i++) {
        header[i - 1] = m.getColumnLabel(i);
      }

      int counter = 0;
      boolean maxRowsReached = false;
      while (result.next()) {
        String[] row = new String[col];

        // The result set may be larger that the max rows set. In such a case, change
        // the color of the text in the query button in order to warn the user.
        counter++;
        if (counter == maxRows) {
          maxRowsReached = true;
        }

        for (int i = 1; i <= col; i++) {
          row[i - 1] = result.getString(i);

          if (result.wasNull()) {
            row[i - 1] = "(null)";
          }
        }

        rows.add(row);
      }

      return new FormattedResult(header, rows, maxRowsReached);
    }
  }

  /** Displays the formatted text result. */
  static String asText(GridSwing gridResultArea, boolean displayHeaders) {

    StringBuffer b = new StringBuffer();

    if (displayHeaders) {
      String[] col = gridResultArea.getHead();
      int width = col.length;
      int[] size = new int[width];
      for (int i = 0; i < width; i++) {
        size[i] = col[i].length();
      }

      for (int i = 0; i < width; i++) {
        b.append(col[i]);

        for (int l = col[i].length(); l <= size[i]; l++) {
          b.append(' ');
        }
      }

      b.append(NL);

      for (int i = 0; i < width; i++) {
        for (int l = 0; l < size[i]; l++) {
          b.append('-');
        }

        b.append(' ');
      }

      b.append(NL);

      // Do not display empty headers
      if (b.charAt(0) == ' ') {
        b = new StringBuffer();
      }
    }

    Vector<?> data = gridResultArea.getData();
    if (data.isEmpty()) {
      return "";
    }
    String[] col = (String[]) data.elementAt(0);
    int width = col.length;
    String[] row;
    int height = data.size();
    int[] size = new int[width];

    for (int i = 0; i < width; i++) {
      size[i] = col[i].length();
    }

    for (int i = 0; i < height; i++) {
      row = (String[]) data.elementAt(i);
      width = row.length;

      for (int j = 0; j < width; j++) {
        int l = row[j].length();

        if (l > size[j]) {
          size[j] = l;
        }
      }
    }

    for (int i = 0; i < height; i++) {
      row = (String[]) data.elementAt(i);

      for (int j = 0; j < width; j++) {
        b.append(row[j]);

        for (int l = row[j].length(); l <= size[j]; l++) {
          b.append(' ');
        }
      }

      b.append(NL);
    }

    return b.toString().trim();
  }
}
