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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import javax.swing.JOptionPane;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Utility that imports a CSV file into a database table. The table structure must already exist in
 * the database before the importation process and the imported file must be RFC4180 compliant.
 *
 * @author Bart Jourquin
 */
public class ImportCSV {
  static I18n i18n = Environment.getI18n();

  /** Default constructor. */
  public ImportCSV() {}

  /**
   * Builds the SQL insert statement once the number of CSV fields is known.
   *
   * @param tableName The destination table name.
   * @param nbFields The number of fields in the CSV records.
   * @return The parameterized SQL insert statement.
   */
  private static String buildInsertStatement(String tableName, int nbFields) {
    StringBuilder sqlStmt = new StringBuilder("INSERT INTO ");
    sqlStmt.append(tableName).append(" VALUES (");

    for (int i = 0; i < nbFields; i++) {
      if (i > 0) {
        sqlStmt.append(',');
      }
      sqlStmt.append('?');
    }

    sqlStmt.append(')');
    return sqlStmt.toString();
  }

  /**
   * Saves one CSV record using the configured batch policy.
   *
   * @param prepStmt The prepared insert statement.
   * @param record The CSV record to save.
   * @param nbFields The number of fields to bind.
   * @param hasBatchSupport True if JDBC batch updates are supported.
   * @param batchSize The current batch size.
   * @param maxBatchSize The maximum batch size before flushing.
   * @return The updated batch size.
   * @throws SQLException If the record cannot be saved.
   */
  private static int saveRecord(
      PreparedStatement prepStmt,
      CSVRecord record,
      int nbFields,
      boolean hasBatchSupport,
      int batchSize,
      int maxBatchSize)
      throws SQLException {

    for (int i = 0; i < nbFields; i++) {
      prepStmt.setString(i + 1, record.get(i));
    }

    if (hasBatchSupport) {
      batchSize++;
      prepStmt.addBatch();
      if (batchSize >= maxBatchSize) {
        prepStmt.executeBatch();
        return 0;
      }
      return batchSize;
    }

    prepStmt.executeUpdate();
    return batchSize;
  }

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
      try (Statement stmt = con.createStatement()) {
        String sqlStmt = "delete from " + JDBCUtils.getCompliantIdentifier(tableName);
        stmt.executeUpdate(sqlStmt);
      }

      // Open a reader for the csv file
      String fileName =
          project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + tableName + NodusC.TYPE_CSV;

      // Be sure the file exists
      File f = new File(fileName);
      if (!f.exists()) {
        return false;
      }

      try (Reader in = new FileReader(fileName);
          CSVParser records =
              withHeader
                  ? CSVFormat.RFC4180
                      .builder()
                      .setHeader()
                      .setSkipHeaderRecord(true)
                      .get()
                      .parse(in)
                  : CSVFormat.RFC4180.parse(in)) {

        boolean hasBatchSupport = JDBCUtils.hasBatchSupport();
        Iterator<CSVRecord> recordIterator = records.iterator();

        if (recordIterator.hasNext()) {
          CSVRecord firstRecord = recordIterator.next();
          int nbFields = firstRecord.size();
          String psqlStmt = buildInsertStatement(tableName, nbFields);

          try (PreparedStatement prepStmt = con.prepareStatement(psqlStmt)) {
            int batchSize = 0;

            batchSize =
                saveRecord(
                    prepStmt, firstRecord, nbFields, hasBatchSupport, batchSize, maxBatchSize);

            while (recordIterator.hasNext()) {
              batchSize =
                  saveRecord(
                      prepStmt,
                      recordIterator.next(),
                      nbFields,
                      hasBatchSupport,
                      batchSize,
                      maxBatchSize);
            }

            // Flush remaining records in batch
            if (hasBatchSupport) {
              prepStmt.executeBatch();
            }
          }
        }
      }

      if (!con.getAutoCommit()) {
        con.commit();
      }

    } catch (SQLException | IOException e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    // long end = System.currentTimeMillis();
    // System.out.println("Duration : " + ((end - start) / 1000));

    return true;
  }
}
