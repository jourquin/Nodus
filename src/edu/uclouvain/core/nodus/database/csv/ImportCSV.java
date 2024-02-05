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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.swing.JOptionPane;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

/**
 * Utility that imports a CSV file into a database table. The table structure must already exist in
 * the database before the importation process and the imported file must be RFC4180 compliant.
 *
 * @author Bart Jourquin
 */
public class ImportCSV {
  static I18n i18n = Environment.getI18n();

  /**
   * Imports the table which name is passed as parameter. The CSV file must be located in the
   * project directory. The correspondent table structure (empty table) must exist in the database.
   * If the table is not empty, all the existing records are removed before importation. This method
   * returns true if the file was successfully imported.
   *
   * @param project The Nodus project.
   * @param tableName The name of the table in xhich the file will be imported. The latest must have
   *     the same name.
   * @param withHeader True to parse a file with a header record.
   * @return boolean True on success.
   */
  public static boolean importTable(NodusProject project, String tableName, boolean withHeader) {

    // long start = System.currentTimeMillis();

    int maxBatchSize =
        project.getLocalProperty(NodusC.PROP_MAX_SQL_BATCH_SIZE, NodusC.MAXBATCHSIZE);

    // Table must exist in order to know which structure it has
    if (!JDBCUtils.tableExists(tableName)) {
      JOptionPane.showMessageDialog(
          null,
          i18n.get(
              ImportCSV.class,
              "Table_structure_must_exist_before_importing_CSV_data",
              "Table structure must exist before importing CSV data"),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
      return false;
    }

    try {
      Connection con = project.getMainJDBCConnection();

      // Clean table
      Statement stmt = con.createStatement();
      String sqlStmt = "delete from " + JDBCUtils.getCompliantIdentifier(tableName);
      stmt.executeUpdate(sqlStmt);
      stmt.close();

      // Open a reader for the csv file
      String fileName =
          project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_CSV;

      // Be sure the file exists
      File f = new File(fileName);
      if (!f.exists()) {
        return false;
      }

      Reader in = new FileReader(fileName);
      Iterable<CSVRecord> records;
      if (withHeader) {
        records =
            CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build().parse(in);
      } else {
        records = CSVFormat.RFC4180.parse(in);
      }

      DatabaseMetaData dbmd = con.getMetaData();
      boolean hasBatchSupport = false;
      if (dbmd != null) {
        if (dbmd.supportsBatchUpdates()) {
          hasBatchSupport = true;
        }
      }

      // boolean oldAutoCommit = false;
      boolean oldAutoCommit = con.getAutoCommit();
      // con.setAutoCommit(false);

      PreparedStatement prepStmt = null;

      int nbFields = -1;

      int batchSize = 0;
      for (CSVRecord record : records) {
        // Initialize the prepared statement if not yet done
        if (nbFields == -1) {
          nbFields = record.size();
          String psqlStmt = "INSERT INTO " + tableName + " VALUES (";
          for (int i = 0; i < nbFields; i++) {
            psqlStmt += "?";
            if (i < nbFields - 1) {
              psqlStmt += ",";
            }
          }
          psqlStmt += ")";
          prepStmt = con.prepareStatement(psqlStmt);
        }

        // Handle record
        for (int i = 0; i < nbFields; i++) {
          prepStmt.setString(i + 1, record.get(i));
        }

        // Save into table according to batch policy;
        if (hasBatchSupport) {
          batchSize++;
          prepStmt.addBatch();
          if (batchSize >= maxBatchSize) {
            con.setAutoCommit(false);
            prepStmt.executeBatch();
            con.commit();
            con.setAutoCommit(oldAutoCommit);
            batchSize = 0;
          }
        } else {
          prepStmt.executeUpdate();
        }
      }

      // Flush remaining records in batch
      if (hasBatchSupport) {
        con.setAutoCommit(false);
        prepStmt.executeBatch();
        con.commit();
      }

      prepStmt.close();
      if (oldAutoCommit == false) {
        con.commit();
      }
      con.setAutoCommit(oldAutoCommit);
    } catch (SQLException | IOException e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    // long end = System.currentTimeMillis();
    // System.out.println("Duration : " + ((end - start) / 1000));

    return true;
  }
}
