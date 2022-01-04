/*
 * Copyright (c) 1991-2022 Université catholique de Louvain
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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

/**
 * Exports a database table in a .dbf table
 *
 * @author Bart Jourquin
 */
public class ExportDBF implements ShapeConstants {

  /**
   * Creates a .dbf table. Then name of the file will be the name of the exported table. It will be
   * located in the Nodus project directory.
   *
   * @param nodusProject The Nodus project.
   * @param tableName Tha name of the table to export
   * @return A DBFWriter
   */
  private static DBFWriter createTable(NodusProject nodusProject, String tableName) {
    Connection con = nodusProject.getMainJDBCConnection();
    DBFField[] field = null;
    DBFWriter dbf = null;

    int fieldNum = 0;

    try {
      // Create a result set
      DatabaseMetaData dbmd = con.getMetaData();

      // Specify the type of object; in this case we want tables
      ResultSet col =
          dbmd.getColumns(null, null, JDBCUtils.getCompliantIdentifier(tableName), null);

      Vector<String> names = new Vector<>();
      Vector<String> types = new Vector<>();
      Vector<Integer> sizes = new Vector<>();
      Vector<Integer> decimalDigits = new Vector<>();

      while (col.next()) {
        names.add(col.getString("COLUMN_NAME"));
        String typeName = col.getString("TYPE_NAME").toUpperCase();
        types.add(typeName);
        decimalDigits.add(col.getInt("DECIMAL_DIGITS"));

        if (typeName.contains("CHAR")) {
          sizes.add(col.getInt("COLUMN_SIZE"));
        } else if (!typeName.contains("DATE")) {
          // As the metadata doesn't contain a usable width for numerical values, estimate it
          int w =
              JDBCUtils.getNumWidth(
                  tableName, col.getString("COLUMN_NAME"), col.getInt("DECIMAL_DIGITS"));
          sizes.add(w);
        } else if (typeName.contains("DATE")) {
          sizes.add(8);
        }
      }
      col.close();

      if (names.size() == 0) {
        return null;
      }

      field = new DBFField[names.size()];

      // Transform into dbf fields
      for (int i = 0; i < names.size(); i++) {
        fieldNum = i;

        char columnType = 'N';
        if (types.get(i).contains("CHAR")) {
          columnType = 'C';
        }

        if (types.get(i).contains("DATE")) {
          columnType = 'D';
        }

        int columnSize = sizes.get(i);
        int decimalDigit = 0;
        if (columnType == 'N') {
          decimalDigit = decimalDigits.get(i);
        }

        field[i] = new DBFField(names.get(i), columnType, columnSize, decimalDigit);
      }

      String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
      dbf = new DBFWriter(path + tableName + NodusC.TYPE_DBF, field);
    } catch (Exception e) {
      System.out.println("Field " + fieldNum + ": " + e.toString());

      return null;
    }

    return dbf;
  }

  /**
   * Creates the .dbf file from a DbfTableModel and saves it in the directory given as parameter.
   *
   * @param path The path to the directory in which the file must be saved.
   * @param tableName The name of the table to export.
   * @param model The DbfTableModel that contains the data to export.
   * @return JDBFWriter
   */
  private static DBFWriter createTable(String path, String tableName, DbfTableModel model) {
    DBFField[] field = new DBFField[model.getColumnCount()];
    DBFWriter dbf = null;

    for (int i = 0; i < model.getColumnCount(); i++) {
      Byte byteType = Byte.valueOf(model.getType(i));

      char charType = 'N';
      if (byteType.equals(DBF_TYPE_CHARACTER)) {
        charType = 'C';
      } else if (byteType.equals(DBF_TYPE_DATE)) {
        charType = 'D';
      }

      field[i] =
          new DBFField(
              model.getColumnName(i), charType, model.getLength(i), model.getDecimalCount(i));
    }

    dbf = new DBFWriter(path + tableName, field);

    return dbf;
  }

  /**
   * Creates a temporary table with two fields : recno and num. This table will be used to ensure
   * that the export of the Esri linked DBF file is in the same order than the original one. This
   * will be done using the following sql statement
   *
   * <p>"select table. from table, tmp where table.num = tmp.num order by tmp.recno"
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
      String sqlStmt =
          "CREATE TABLE "
              + tmpTable
              + " ("
              + JDBCUtils.getQuotedCompliantIdentifier("recno")
              + " INTEGER, "
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
              + " NUMERIC(5,0))";

      Statement stmt = jdbcConnection.createStatement();
      stmt.execute(sqlStmt);
      stmt.close();

      sqlStmt = "INSERT INTO " + tmpTable + " VALUES(?, ?)";
      PreparedStatement ps = jdbcConnection.prepareStatement(sqlStmt);

      DBFReader dbfReader = new DBFReader(path + tableName + NodusC.TYPE_DBF);
      int n = 0;

      while (dbfReader.hasNextRecord()) {
        Object[] o = dbfReader.nextRecord();
        ps.setInt(1, ++n);
        ps.setInt(2, Integer.parseInt(o[NodusC.DBF_IDX_NUM].toString()));
        ps.execute();
      }
      dbfReader.close();
      ps.close();
      if (!jdbcConnection.getAutoCommit()) {
        jdbcConnection.commit();
      }

    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }

    return true;
  }

  /**
   * This method exports the records of a table into a .dbf file. It is not to be used with Esri
   * shape files. Use exportEsriDbf instead.
   *
   * <p>Returns true if file was successfully exported
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

    String sqlStmt = "SELECT * FROM " + JDBCUtils.getCompliantIdentifier(tableName);

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

    // Get the layer that corresponds to this table
    NodusEsriLayer layer = null;
    NodusEsriLayer[] layers = nodusProject.getNodeLayers();

    for (NodusEsriLayer element : layers) {
      if (element.getTableName().equals(tableName)) {
        layer = element;

        break;
      }
    }

    if (layer == null) {
      layers = nodusProject.getLinkLayers();

      for (NodusEsriLayer element : layers) {
        if (element.getTableName().equals(tableName)) {
          layer = element;

          break;
        }
      }
    }

    // If the table is not associated with a loaded layer
    if (layer == null) {
      return exportGenericEsriDbf(nodusProject, tableName);
    }

    // Update the dbftableModel of the layer
    if (!layer.updateDbfTableModel()) {
      return false;
    }

    // Write the dbf file
    if (!ExportDBF.exportTable(nodusProject, tableName + NodusC.TYPE_DBF, layer.getModel())) {
      return false;
    }

    return true;
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

    String table = JDBCUtils.getCompliantIdentifier(tableName);
    String tmp = JDBCUtils.getCompliantIdentifier("tmp");

    // We need to know in which order to store the records. Create a
    // temporary table for that purpose
    String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
    if (!createTmpTable(path, tableName, nodusProject.getMainJDBCConnection(), tmp)) {
      return false;
    }

    DBFWriter dbf = createTable(nodusProject, tableName);

    if (dbf == null) {
      return false;
    }

    // Create an SQL statement that ensures the correct order of records
    String sqlStmt =
        "SELECT "
            + table
            + ".* FROM "
            + table
            + ", "
            + tmp
            + " WHERE "
            + table
            + "."
            + JDBCUtils.getCompliantIdentifier(NodusC.DBF_NUM)
            + " = "
            + tmp
            + "."
            + JDBCUtils.getCompliantIdentifier(NodusC.DBF_NUM)
            + " ORDER BY "
            + tmp
            + "."
            + JDBCUtils.getCompliantIdentifier("recno");

    // OK, now fill the table
    boolean result = fillTable(nodusProject, dbf, sqlStmt);

    // Remove tmp table
    JDBCUtils.dropTable(tmp);

    return result;
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
      boolean success = exportEsriDbf(nodusProject, tableName);

      return success;
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

    Object[] o = new Object[model.getColumnCount()];

    try {
      for (int i = 0; i < model.getRowCount(); i++) {
        for (int j = 0; j < model.getColumnCount(); j++) {
          o[j] = model.getValueAt(i, j);

          // Avoid empty numerical values
          if (model.getType(j) == DBF_TYPE_NUMERIC.byteValue()) {
            if (o[j].equals("")) {
              o[j] = Double.valueOf(0);
            }
          }

          // Transform date string into Date
          if (model.getType(j) == DBF_TYPE_DATE.byteValue() && !(o[j] instanceof Date)) {
            try {
              String date = (String) o[j];
              if (date == null || date.equals("")) {
                date = defaultDate;
              }
              Date d = dateFormat.parse(date);
              o[j] = d;
            } catch (ParseException e) {
              // Should never happen...
              e.printStackTrace();
            }
          }
        }

        dbfWriter.addRecord(o);
      }

      dbfWriter.close();

      /*if (!dbfWriter.isExportedWithSuccess()) {
        return false;
      }*/
    } catch (DBFException e) {
      System.out.println(e);

      return false;
    }

    return true;
  }

  /**
   * Fills the .dbf table with the records fetched by means of the SQL statement. Returns true if
   * the table was successfully filled.
   */
  private static boolean fillTable(NodusProject nodusProject, DBFWriter dbfWriter, String sqlStmt) {
    try {
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();

      ResultSet rs = stmt.executeQuery(sqlStmt);
      ResultSetMetaData rsmd = rs.getMetaData();
      int nbColumns = rsmd.getColumnCount();
      Object[] o = new Object[nbColumns];

      // Retrieve result of query
      while (rs.next()) {
        for (int i = 0; i < nbColumns; i++) {
          o[i] = rs.getObject(i + 1);
        }

        dbfWriter.addRecord(o);
      }

      rs.close();
      stmt.close();

      if (!con.getAutoCommit()) {
        con.commit();
      }

      dbfWriter.close();

      /*if (!dbfWriter.isExportedWithSuccess()) {
        return false;
      }*/
    } catch (Exception ex) {
      System.out.println(ex.toString());

      return false;
    }

    return true;
  }
}
