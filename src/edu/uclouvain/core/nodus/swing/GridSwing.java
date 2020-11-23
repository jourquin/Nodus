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

package edu.uclouvain.core.nodus.swing;

import java.util.Vector;
import javax.swing.table.AbstractTableModel;

/**
 * Grid used to display the results of a query.
 *
 * @author Bart Jourquin, mostly taken from code of HSQLDB.
 */
public class GridSwing extends AbstractTableModel {

  private static final long serialVersionUID = 8944633970142019137L;

  /** . */
  private String[] headers;

  /** . */
  private Vector<String[]> rows;

  /** Default constructor. */
  public GridSwing() {
    super();

    headers = new String[0]; // initially empty
    rows = new Vector<>(); // initially empty
  }

  /**
   * Append a tuple to the end of the table.
   *
   * @param r String[]
   */
  public void addRow(String[] r) {
    String[] row = new String[r.length];

    // System.arraycopy(r, 0, row, 0, r.length);
    for (int i = 0; i < r.length; i++) {
      row[i] = r[i];

      if (row[i] == null) {
        row[i] = "(null)";
      }
    }

    rows.addElement(row);
  }

  /** Remove data from all cells in the table (without affecting the current headings). */
  public void clear() {
    rows.removeAllElements();
  }

  /**
   * Get the number of columns.
   *
   * @return int
   */
  @Override
  public int getColumnCount() {
    return headers.length;
  }

  /**
   * Get the name for the specified column.
   *
   * @param i int
   * @return String
   */
  @Override
  public String getColumnName(int i) {
    return headers[i];
  }

  /**
   * Get the current table data. Each row is represented as a <code>String[]</code> with a single
   * non-null value in the 0-relative column position.
   *
   * <p>The first row is at offset 0, the nth row at offset n etc.
   *
   * @return Vector
   */
  public Vector<String[]> getData() {
    return rows;
  }

  /**
   * Get the current column headings.
   *
   * @return String[]
   */
  public String[] getHead() {
    return headers;
  }

  /**
   * Get the number of rows currently in the table.
   *
   * @return int
   */
  @Override
  public int getRowCount() {
    return rows.size();
  }

  /**
   * Get the object at the specified cell location.
   *
   * @param row int
   * @param col int
   * @return Object
   */
  @Override
  public Object getValueAt(int row, int col) {
    return rows.elementAt(row)[col];
  }

  /**
   * Set the name of the column headings.
   *
   * @param h String[]
   */
  public void setHead(String[] h) {
    headers = new String[h.length];

    // System.arraycopy(h, 0, headers, 0, h.length);
    for (int i = 0; i < h.length; i++) {
      headers[i] = h[i];
    }
  }
}
