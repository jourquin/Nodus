/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * Simple CSV file writer.
 *
 * @author Bart Jourquin
 */
public class ExportCSV {

  /** Default constructor. */
  public ExportCSV() {}

  /**
   * Exports the table which name is given as parameter in the directory of the project. Returns
   * true if the table is successfully exported.
   *
   * @param project The Nodus project.
   * @param tableName The name of the file to export.
   * @param withHeader If true, the first line will contain the field names.
   * @return boolean True on success.
   */
  public static boolean exportTable(NodusProject project, String tableName, boolean withHeader) {
    try {
      // Create output file
      String fileName =
          project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_CSV;
      BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));

      Connection con = project.getMainJDBCConnection();
      Statement stmt = con.createStatement();

      ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);
      ResultSetMetaData rsmd = rs.getMetaData();
      int nbColumns = rsmd.getColumnCount();

      // Save header if needed
      if (withHeader) {
        for (int i = 0; i < nbColumns; i++) {
          bw.write(rsmd.getColumnName(i + 1));
          if (i < nbColumns - 1) {
            bw.write(',');
          }
        }
        bw.newLine();
      }

      // Retrieve result of query
      while (rs.next()) {
        for (int i = 0; i < nbColumns; i++) {
          try {
            bw.write(rs.getObject(i + 1).toString());
          } catch (Exception e) { // if null
            bw.write("");
          }

          if (i < nbColumns - 1) {
            bw.write(',');
          }
        }

        bw.newLine();
      }

      bw.close();
      rs.close();
      stmt.close();
    } catch (Exception ex) {
      System.out.println(ex.toString());

      return false;
    }

    return true;
  }
}
