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

package edu.uclouvain.core.nodus.database.dbf;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.ProjectFilesTools;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Exports a database table in a .dbf table.
 *
 * @author Bart Jourquin
 */
public class ExportDBF implements ShapeConstants {

  /** Default constructor. */
  public ExportDBF() {}

  /**
   * Creates a .dbf table. The name of the file will be the name of the exported table. It will be
   * located in the Nodus project directory.
   *
   * @param nodusProject The Nodus project.
   * @param tableName The name of the table to export.
   * @return A DBFWriter, or {@code null} on error.
   */
  private static DBFWriter createTable(NodusProject nodusProject, String tableName) {
    DBFField[] field;
    int fieldNum = 0;

    try (ResultSet col = JDBCUtils.getColumns(tableName)) {
      List<String> names = new ArrayList<>();
      List<String> types = new ArrayList<>();
      List<Integer> sizes = new ArrayList<>();
      List<Integer> decimalDigits = new ArrayList<>();

      while (col.next()) {
        String columnName = col.getString("COLUMN_NAME");
        String typeName = col.getString("TYPE_NAME").toUpperCase();
        int decimalDigit = col.getInt("DECIMAL_DIGITS");

        names.add(columnName);
        types.add(typeName);
        decimalDigits.add(decimalDigit);

        if (typeName.contains("CHAR")) {
          sizes.add(col.getInt("COLUMN_SIZE"));
        } else if (typeName.contains("DATE")) {
          sizes.add(8);
        } else {
          // As the metadata doesn't contain a usable width for numerical values, estimate it.
          sizes.add(JDBCUtils.getNumWidth(tableName, columnName, decimalDigit));
        }
      }

      if (names.isEmpty()) {
        return null;
      }

      field = new DBFField[names.size()];

      // Transform into dbf fields.
      for (int i = 0; i < names.size(); i++) {
        fieldNum = i;

        char columnType = 'N';
        if (types.get(i).contains("CHAR")) {
          columnType = 'C';
        } else if (types.get(i).contains("DATE")) {
          columnType = 'D';
        }

        int decimalDigit = 0;
        if (columnType == 'N') {
          decimalDigit = decimalDigits.get(i);
        }

        field[i] = new DBFField(names.get(i), columnType, sizes.get(i), decimalDigit);
      }

      String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
      return new DBFWriter(path + tableName + NodusC.TYPE_DBF, field);

    } catch (Exception e) {
      System.out.println("Field " + fieldNum + ": " + e.toString());
      return null;
    }
  }

  /**
   * Creates the .dbf file from a DbfTableModel and saves it in the directory given as parameter.
   *
   * @param path The path to the directory in which the file must be saved.
   * @param tableName The name of the table to export.
   * @param model The DbfTableModel that contains the data to export.
   * @return A DBFWriter, or {@code null} on error.
   */
  private static DBFWriter createTable(String path, String tableName, DbfTableModel model) {
    DBFField[] field = new DBFField[model.getColumnCount()];

    try {
      for (int i = 0; i < model.getColumnCount(); i++) {
        byte byteType = model.getType(i);

        char charType = 'N';
        if (byteType == DBF_TYPE_CHARACTER.byteValue()) {
          charType = 'C';
        } else if (byteType == DBF_TYPE_DATE.byteValue()) {
          charType = 'D';
        }

        field[i] =
            new DBFField(
                model.getColumnName(i), charType, model.getLength(i), model.getDecimalCount(i));
      }

      return new DBFWriter(path + tableName, field);
    } catch (Exception e) {
      System.out.println(e.toString());
      return null;
    }
  }

  /**
   * Creates a temporary table with two fields: recno and num. This table will be used to ensure
   * that the export of the Esri linked DBF file is in the same order than the original one. This
   * will be done using the following sql statement:
   *
   * <p>"select table.* from table, tmp where table.num = tmp.num order by tmp.recno"
   *
   * <p>Returns true if the table was successfully created.
   *
   * @param path String
   * @param tableName String
   * @param jdbcConnection Connection
   * @param tmpTable String
   * @return boolean
   */
  private static boolean createTmpTable(
      String path, String tableName, Connection jdbcConnection, String tmpTable) {

    JDBCUtils.dropTable(tmpTable);

    try {
      String quotedTmpTable = JDBCUtils.getQuotedCompliantIdentifier(tmpTable);
      String sqlStmt =
          "CREATE TABLE "
              + quotedTmpTable
              + " ("
              + JDBCUtils.getQuotedCompliantIdentifier("recno")
              + " INTEGER, "
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
              + " NUMERIC(5,0))";

      try (Statement stmt = jdbcConnection.createStatement()) {
        stmt.execute(sqlStmt);
      }

      sqlStmt = "INSERT INTO " + quotedTmpTable + " VALUES(?, ?)";

      try (PreparedStatement ps = jdbcConnection.prepareStatement(sqlStmt);
          DBFReader dbfReader = new DBFReader(path + tableName + NodusC.TYPE_DBF)) {
        int n = 0;

        while (dbfReader.hasNextRecord()) {
          Object[] o = dbfReader.nextRecord();
          ps.setInt(1, ++n);
          ps.setInt(2, Integer.parseInt(o[NodusC.DBF_IDX_NUM].toString()));
          ps.execute();
        }
      }

      if (!jdbcConnection.getAutoCommit()) {
        jdbcConnection.commit();
      }

      return true;

    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  /**
   * This method exports the records of a table into a .dbf file. It is not to be used with Esri
   * shape files. Use exportEsriDbf instead.
   *
   * <p>Returns true if file was successfully exported.
   *
   * @param nodusProject NodusProject
   * @param tableName String
   * @return boolean
   */
  private static boolean exportDbf(NodusProject nodusProject, String tableName) {
    // System.out.println("exportDbf...");

    DBFWriter dbf = createTable(nodusProject, tableName);

    if (dbf == null) {
      return false;
    }

    String sqlStmt = "SELECT * FROM " + JDBCUtils.getQuotedCompliantIdentifier(tableName);

    return fillTable(nodusProject, dbf, sqlStmt);
  }

  /**
   * This method exports the records associated to an Esri shape file into a .dbf file. It ensures
   * that the records are saved in their original order, so that each record will remain associated
   * with its shape.
   *
   * @param nodusProject NodusProject
   * @param tableName String
   * @return boolean
   */
  private static boolean exportEsriDbf(NodusProject nodusProject, String tableName) {

    // System.out.println("exportEsriDbf...");

    NodusEsriLayer layer = getLayerByTableName(nodusProject.getNodeLayers(), tableName);

    if (layer == null) {
      layer = getLayerByTableName(nodusProject.getLinkLayers(), tableName);
    }

    // If the table is not associated with a loaded layer.
    if (layer == null) {
      return exportGenericEsriDbf(nodusProject, tableName);
    }

    // Update the dbftableModel of the layer.
    if (!layer.updateDbfTableModel()) {
      return false;
    }

    // Write the dbf file.
    return ExportDBF.exportTable(nodusProject, tableName + NodusC.TYPE_DBF, layer.getModel());
  }

  /**
   * This method exports the records associated to an Esri shape file into a .dbf file. It ensures
   * that the records are saved in their original order, so that each record will remain associated
   * with its shape. This method is slow and should only be called by exportEsriDbf, because this
   * method uses another, faster, method for dbf tables that are associated to NodusEsriLayer.
   *
   * @param nodusProject NodusProject
   * @param tableName String
   * @return boolean
   */
  private static boolean exportGenericEsriDbf(NodusProject nodusProject, String tableName) {

    System.out.println("exportGenericEsriDbf ...");

    String table = JDBCUtils.getQuotedCompliantIdentifier(tableName);
    String tmp = JDBCUtils.getQuotedCompliantIdentifier("tmp");
    String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

    if (!createTmpTable(path, tableName, nodusProject.getMainJDBCConnection(), "tmp")) {
      return false;
    }

    try {
      DBFWriter dbf = createTable(nodusProject, tableName);

      if (dbf == null) {
        return false;
      }

      String num = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM);
      String recno = JDBCUtils.getQuotedCompliantIdentifier("recno");
      String sqlStmt =
          "SELECT t.* FROM "
              + table
              + " t, "
              + tmp
              + " tmp WHERE t."
              + num
              + " = tmp."
              + num
              + " ORDER BY tmp."
              + recno;

      return fillTable(nodusProject, dbf, sqlStmt);

    } finally {
      JDBCUtils.dropTable("tmp");
    }
  }

  /**
   * Exports a database table in a .dbf table. The file will have the same name as the table, and
   * will be located in the project directory. The method returns true if the file was successfully
   * exported.
   *
   * @param nodusProject the Nodus project
   * @param tableName The name of the table to export.
   * @return True on success.
   */
  public static boolean exportTable(NodusProject nodusProject, String tableName) {

    String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

    if (ProjectFilesTools.isValidLayer(path, tableName)) {
      return exportEsriDbf(nodusProject, tableName);
    } else {
      return exportDbf(nodusProject, tableName);
    }
  }

  /**
   * Exports a DbfTable model into a .dbf table, located in the project directory. The method
   * returns true if the file was successfully exported.
   *
   * @param nodusProject The Nodus project.
   * @param tableName The name of the table to export.
   * @param model The DbfTableModel that contains the data to export.
   * @return True on success.
   */
  public static boolean exportTable(
      NodusProject nodusProject, String tableName, DbfTableModel model) {
    String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

    DBFWriter dbf = createTable(path, tableName, model);

    if (dbf == null) {
      return false;
    }

    return fillTable(dbf, model);
  }

  /**
   * Fills the .dbf table with the records fetched in the DbfTableModel.
   *
   * @param dbfWriter DBFWriter
   * @param model DbfTableModel
   * @return boolean
   */
  private static boolean fillTable(DBFWriter dbfWriter, DbfTableModel model) {

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    String defaultDate = dateFormat.format(new Date(0));
    boolean success = true;

    try (DBFWriter dbf = dbfWriter) {
      for (int i = 0; i < model.getRowCount(); i++) {
        Object[] o = new Object[model.getColumnCount()];

        for (int j = 0; j < model.getColumnCount(); j++) {
          o[j] = getDbfValue(model, i, j, dateFormat, defaultDate);
        }

        dbf.addRecord(o);
      }

    } catch (Exception e) {
      System.out.println(e);
      success = false;
    }

    return success;
  }

  /**
   * Fills the .dbf table with the records fetched by means of the SQL statement. Returns true if
   * the table was successfully filled.
   */
  private static boolean fillTable(NodusProject nodusProject, DBFWriter dbfWriter, String sqlStmt) {

    Connection con = nodusProject.getMainJDBCConnection();
    boolean success = true;

    try (Statement stmt = con.createStatement();
        ResultSet rs = stmt.executeQuery(sqlStmt);
        DBFWriter dbf = dbfWriter) {

      ResultSetMetaData rsmd = rs.getMetaData();
      int nbColumns = rsmd.getColumnCount();

      while (rs.next()) {
        Object[] o = new Object[nbColumns];
        for (int i = 0; i < nbColumns; i++) {
          o[i] = rs.getObject(i + 1);
        }

        dbf.addRecord(o);
      }

      if (!con.getAutoCommit()) {
        con.commit();
      }

    } catch (Exception ex) {
      System.out.println(ex.toString());
      success = false;
    }

    return success;
  }

  /** Returns the layer whose table name matches {@code tableName}. */
  private static NodusEsriLayer getLayerByTableName(NodusEsriLayer[] layers, String tableName) {
    if (layers == null || tableName == null) {
      return null;
    }

    for (NodusEsriLayer layer : layers) {
      if (layer == null || layer.getTableName() == null) {
        continue;
      }

      if (layer.getTableName().equals(tableName)) {
        return layer;
      }
    }

    return null;
  }

  /** Converts a DbfTableModel cell value to the value expected by the DBF writer. */
  private static Object getDbfValue(
      DbfTableModel model, int row, int column, SimpleDateFormat dateFormat, String defaultDate) {

    Object value = model.getValueAt(row, column);

    // Avoid empty numerical values.
    if (model.getType(column) == DBF_TYPE_NUMERIC.byteValue()
        && (value == null || value.equals(""))) {
      return Double.valueOf(0);
    }

    // Transform date string into Date.
    if (model.getType(column) == DBF_TYPE_DATE.byteValue() && !(value instanceof Date)) {
      String date = value == null ? defaultDate : value.toString();
      if (date.isEmpty()) {
        date = defaultDate;
      }

      try {
        return dateFormat.parse(date);
      } catch (ParseException e) {
        // Should never happen, but keep the legacy behavior and let DBFWriter reject the value if
        // needed.
        e.printStackTrace();
      }
    }

    return value;
  }
}
