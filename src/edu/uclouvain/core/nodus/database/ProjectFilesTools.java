/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.database;

import com.bbn.openmap.dataAccess.shape.EsriGraphicFactory;
import com.bbn.openmap.dataAccess.shape.EsriGraphicFactory.Header;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.dataAccess.shape.input.LittleEndianInputStream;
import com.bbn.openmap.dataAccess.shape.input.ShpInputStream;
import com.bbn.openmap.dataAccess.shape.output.ShpOutputStream;
import com.bbn.openmap.dataAccess.shape.output.ShxOutputStream;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.dbf.DBFException;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;

import foxtrot.Job;
import foxtrot.Worker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.swing.JOptionPane;

/**
 * Some handy utilities to perform some operations on tables and files of a Nodus project.
 *
 * @author Bart Jourquin
 */
public class ProjectFilesTools implements ShapeConstants {
  private static String errorMessage;

  // Add the "enabled" field if needed in the link layers
  private static boolean enabledFieldMustBeAdded = false;

  private static boolean result;

  /**
   * Add the "enabled" field to the dbf file of a link layer. This is a convenient method added in
   * order to upgrade the DBF files transparently (no end user action requested).
   *
   * @param path Path to the directory where the shape file is stored.
   * @param layerName Shapefile name (without suffix)
   * @return True on success
   */
  private static boolean addEnabledField(final String path, final String layerName) {

    new NodusConsole();

    result = true;
    Worker.post(
        new Job() {
          @Override
          public Object run() {

            System.out.println("Upgrading " + path + layerName + NodusC.TYPE_DBF);

            List<Object[]> data = new LinkedList<Object[]>();

            DBFField[] fields;
            try {
              DBFReader dbfReader = new DBFReader(path + layerName + NodusC.TYPE_DBF);

              // Retain structure
              fields = new DBFField[dbfReader.getFieldCount() + 1];

              int j = 0;
              for (int i = 0; i < fields.length; i++) {
                // Insert the "enabled" field at the right place
                if (i == NodusC.DBF_IDX_ENABLED) {
                  fields[i] = new DBFField(NodusC.DBF_ENABLED, 'N', 1, 0);
                } else {
                  fields[i] = dbfReader.getField(j);
                  j++;
                }
              }

              // Read the dbf file
              while (dbfReader.hasNextRecord()) {
                Object[] record = dbfReader.nextRecord().clone();

                // Insert default "enabled" value = 1
                Object[] newRecord = new Object[record.length + 1];
                j = 0;
                for (int i = 0; i < newRecord.length; i++) {
                  if (i == NodusC.DBF_IDX_ENABLED) {
                    newRecord[i] = new Integer(1);
                  } else {
                    newRecord[i] = record[j];
                    j++;
                  }
                }
                data.add(newRecord);
              }
            } catch (DBFException e) {
              e.printStackTrace();
              result = false;
              return null;
            }

            // Write the upgraded DBF file
            try {
              DBFWriter dbfWriter = new DBFWriter(path + layerName + NodusC.TYPE_DBF, fields);
              ListIterator<Object[]> it = data.listIterator();
              while (it.hasNext()) {
                dbfWriter.addRecord(it.next());
              }
              dbfWriter.close();
            } catch (DBFException e) {
              e.printStackTrace();
              result = false;
              return null;
            }

            return null;
          }
        });

    return result;
  }

  /**
   * Creates a new empty shape file for a given layer type. The structure of this new layer only
   * contains the mandatory fiels for the layer type.
   *
   * @param path The directory in which the shapefile must be created.
   * @param layerName The name of the layer (and thus the name of the shapefile).
   * @param layerType The type of layer. Can be SHAPE_TYPE_POINT or SHAPE_TYPE_POLYLINE.
   * @return True on success.
   */
  public static boolean createDefaultEmptyLayer(String path, String layerName, int layerType) {
    if (!createEmptyShapefile(path, layerName, layerType)) {
      return false;
    }

    String[] mandatoryNames = null;
    char[] mandatoryTypes = null;
    int[] mandatoryLengths = null;
    int[] mandatoryDecimalCounts = null;

    // NodusC nodusC = new NodusC(null);
    if (layerType == SHAPE_TYPE_POINT) {
      mandatoryNames = NodusC.NODES_MANDATORY_NAMES;
      mandatoryTypes = NodusC.NODES_MANDATORY_TYPES;
      mandatoryLengths = NodusC.NODES_MANDATORY_LENGTHS;
      mandatoryDecimalCounts = NodusC.NODES_MANDATORY_DECIMAL_COUNTS;
    } else {
      mandatoryNames = NodusC.LINKS_MANDATORY_NAMES;
      mandatoryTypes = NodusC.LINKS_MANDATORY_TYPES;
      mandatoryLengths = NodusC.LINKS_MANDATORY_LENGTHS;
      mandatoryDecimalCounts = NodusC.LINKS_MANDATORY_DECIMAL_COUNTS;
    }

    DBFField[] field = new DBFField[mandatoryNames.length];

    try {
      for (int i = 0; i < mandatoryNames.length; i++) {
        field[i] =
            new DBFField(
                mandatoryNames[i],
                mandatoryTypes[i],
                mandatoryLengths[i],
                mandatoryDecimalCounts[i]);
      }

      DBFWriter dbf = new DBFWriter(path + layerName + NodusC.TYPE_DBF, field);
      dbf.close();
    } catch (DBFException ex) {
      System.out.println(ex.toString());

      return false;
    }

    return true;
  }

  /**
   * Creates a new empty shape file for a given layer type. The structure of this new layer copied
   * from another layer located in the same directory..
   *
   * @param path The directory in which the shapefile must be created.
   * @param layerName The name of the layer (and thus the name of the shapefile).
   * @param layerType The type of layer. Can be SHAPE_TYPE_POINT or SHAPE_TYPE_POLYLINE.
   * @param modelTable The name of the shapefile the structure must be copied from.
   * @return True on success.
   */
  public static boolean createEmptyLayer(
      String path, String layerName, int layerType, String modelTable) {
    if (modelTable == null) {
      return createDefaultEmptyLayer(path, layerName, layerType);
    }

    // Create the shape file
    if (!createEmptyShapefile(path, layerName, layerType)) {
      return false;
    }

    // Create the .dbf file
    try {
      // Get the structure of the table model
      DBFReader dbfReader = new DBFReader(path + modelTable + NodusC.TYPE_DBF);

      if (dbfReader.isOpen()) {
        int nbFields = dbfReader.getFieldCount();
        DBFField[] field = new DBFField[nbFields];

        for (int i = 0; i < nbFields; i++) {
          field[i] = dbfReader.getField(i);
        }

        // Create an empty dbf file with the given structure
        DBFWriter dbf = new DBFWriter(path + layerName + NodusC.TYPE_DBF, field);
        dbf.close();
      }
    } catch (DBFException ex) {
      errorMessage = ex.toString();

      return false;
    }

    return true;
  }

  /**
   * Creates a new empty shape (without DBF) file for a given layer type.
   *
   * @param path The directory in which the shapefile must be created.
   * @param layerName The name of the layer (and thus the name of the shapefile).
   * @param layerType The type of layer. Can be SHAPE_TYPE_POINT or SHAPE_TYPE_POLYLINE.
   * @return True on success.
   */
  private static boolean createEmptyShapefile(String path, String layerName, int layerType) {
    EsriGraphicList list;

    if (layerType == SHAPE_TYPE_POLYLINE) {
      list = new EsriPolylineList();
    } else {
      list = new EsriPointList();
    }

    // Create an empty layer
    try {
      ShpOutputStream pos =
          new ShpOutputStream(new FileOutputStream(path + layerName + NodusC.TYPE_SHP));
      int[][] indexData = pos.writeGeometry(list);

      ShxOutputStream xos =
          new ShxOutputStream(new FileOutputStream(path + layerName + NodusC.TYPE_SHX));
      xos.writeIndex(indexData, list.getType());
    } catch (IOException ex) {
      System.out.println(ex.toString());

      return false;
    }

    return true;
  }

  /**
   * Deletes a the files of a layer.
   *
   * @param path The directory in which the layer is located.
   * @param layerName The name of the layer.
   */
  public static void deleteLayerFiles(String path, String layerName) {
    File f = new File(path + layerName + NodusC.TYPE_SHP);
    if (f.exists()) {
      boolean ok = f.delete();
      if (!ok) {
        System.err.println("Unable to delete " + layerName);
      }
    }

    f = new File(path + layerName + NodusC.TYPE_SHX);
    if (f.exists()) {
      boolean ok = f.delete();
      if (!ok) {
        System.err.println("Unable to delete " + layerName);
      }
    }

    f = new File(path + layerName + NodusC.TYPE_DBF);
    if (f.exists()) {
      boolean ok = f.delete();
      if (!ok) {
        System.err.println("Unable to delete " + layerName);
      }
    }
  }

  /**
   * Returns a vector that contains the names of all the available valid Nodus layers of a given
   * type in the directory pointed by path.
   *
   * @param path The directory in which the shapefile must be created.
   * @param layerType The type of layer. Can be SHAPE_TYPE_POINT or SHAPE_TYPE_POLYLINE.
   * @return An array of valid layer names.
   */
  public static String[] getAvailableLayers(String path, int layerType) {
    LinkedList<String> availableLayers = new LinkedList<>();

    File dir = new File(path);
    FilenameFilter filter =
        new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            return name.endsWith(NodusC.TYPE_SHP);
          }
        };

    String[] children = dir.list(filter);

    for (String element : children) {
      String name = element.substring(0, element.lastIndexOf(NodusC.TYPE_SHP));

      if (isValidLayer(path, name, layerType)) {
        availableLayers.add(name);
      }
    }

    // Transform the list into an array
    return availableLayers.toArray(new String[availableLayers.size()]);
  }

  /**
   * Tests if the fields given as parameter contain the mandatory fields for a type of shapefile.
   *
   * @param fileName The name of the shapefile.
   * @param field An array of fields.
   * @param layerType The type of layer. Can be SHAPE_TYPE_POINT or SHAPE_TYPE_POLYLINE.
   * @return True if valid.
   */
  private static boolean isValidDbfFile(String fileName, DBFField[] field, int layerType) {

    String[] mandatoryNames = null;
    char[] mandatoryTypes = null;
    int[] mandatoryLengths = null;
    int[] mandatoryDecimalCounts = null;

    if (layerType == SHAPE_TYPE_POINT) {
      mandatoryNames = NodusC.NODES_MANDATORY_NAMES;
      mandatoryTypes = NodusC.NODES_MANDATORY_TYPES;
      mandatoryLengths = NodusC.NODES_MANDATORY_LENGTHS;
      mandatoryDecimalCounts = NodusC.NODES_MANDATORY_DECIMAL_COUNTS;

    } else {

      mandatoryNames = NodusC.LINKS_MANDATORY_NAMES;
      mandatoryTypes = NodusC.LINKS_MANDATORY_TYPES;
      mandatoryLengths = NodusC.LINKS_MANDATORY_LENGTHS;
      mandatoryDecimalCounts = NodusC.LINKS_MANDATORY_DECIMAL_COUNTS;
    }

    if (field.length < mandatoryNames.length) {
      // Could be an older file, without the "enabled" field
      boolean wrongStructure = true;
      if (field.length == mandatoryNames.length - 1) {
        if (!field[NodusC.DBF_IDX_ENABLED].getName().equalsIgnoreCase(NodusC.DBF_ENABLED)) {
          wrongStructure = false;
        }
      }

      if (wrongStructure) {
        errorMessage = fileName + " is an invalid .dbf file";
        return false;
      }
    }

    for (int i = 0; i < mandatoryNames.length; i++) {
      if (field[i].getName().compareToIgnoreCase(mandatoryNames[i]) != 0
          || field[i].getType() != mandatoryTypes[i]
          || field[i].getLength() != mandatoryLengths[i]
          || field[i].getDecimalCount() != mandatoryDecimalCounts[i]) {
        String validField =
            mandatoryNames[i]
                + " "
                + mandatoryTypes[i]
                + "("
                + mandatoryLengths[i]
                + ","
                + mandatoryDecimalCounts[i]
                + ")";
        errorMessage =
            fileName + NodusC.TYPE_DBF + " : Field " + i + ":" + validField + " expected";

        // The field name must be the same
        if (field[i].getName().compareToIgnoreCase(mandatoryNames[i]) != 0) {
          // The "enabled" field was added in Nodus 7.0
          if (mandatoryNames[i].equals(NodusC.DBF_ENABLED)) {
            enabledFieldMustBeAdded = true;
          } else {
            return false;
          }
        }

        // Maybe the database was in the old format?
        if (!wasValidDbfField(layerType, i, field[i])) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Tests if the file located at the given path is a valid Nodus layer. This method tests both
   * types of layers.
   *
   * @param path The directory in which the shapefile must be created.
   * @param layerName The name of the layer (and thus the name of the shapefile).
   * @return True if valid.
   */
  public static boolean isValidLayer(String path, String layerName) {
    if (isValidLayer(path, layerName, SHAPE_TYPE_POINT)) {
      return true;
    }

    if (isValidLayer(path, layerName, SHAPE_TYPE_POLYLINE)) {
      return true;
    }

    return false;
  }

  /**
   * Tests if the file located at the given path is a valid Nodus layer.
   *
   * @param path The directory in which the shapefile must be created.
   * @param layerName The name of the layer (and thus the name of the shapefile).
   * @param layerType The type of layer. Can be SHAPE_TYPE_POINT or SHAPE_TYPE_POLYLINE.
   * @return True if valid.
   */
  public static boolean isValidLayer(String path, String layerName, int layerType) {

    /* The file names that ends with "_result" are tables that are exported by Nodus
     * to use with another GIS. Not valid. */
    String fn = layerName.toUpperCase();
    String gis = NodusC.SUFFIX_RESULTS.toUpperCase();
    if (fn.endsWith(gis)) {
      return false;
    }

    // Test the shape file
    try {
      BufferedInputStream bis =
          new BufferedInputStream(new FileInputStream(path + layerName + NodusC.TYPE_SHP));

      Header header = new EsriGraphicFactory.Header(new LittleEndianInputStream(bis));

      if (layerType == SHAPE_TYPE_POINT && header.shapeType != SHAPE_TYPE_POINT) {
        errorMessage = layerName + NodusC.TYPE_SHP + " is not a valid POINT shapefile";
        return false;
      }

      if (layerType == SHAPE_TYPE_POLYLINE && header.shapeType != SHAPE_TYPE_POLYLINE) {
        errorMessage = layerName + NodusC.TYPE_SHP + "is not a valid POLYLINE shapefile";

        return false;
      }

      // Try index file
      new ShpInputStream(new FileInputStream(path + layerName + NodusC.TYPE_SHX));
    } catch (IOException ex) {
      errorMessage = ex.toString();
      return false;
    }

    // Test dbf files
    try {
      DBFReader dbfReader = new DBFReader(path + layerName + NodusC.TYPE_DBF);

      if (dbfReader.isOpen()) {
        int nbFields = dbfReader.getFieldCount();
        DBFField[] field = new DBFField[nbFields];

        for (int i = 0; i < nbFields; i++) {
          field[i] = dbfReader.getField(i);
        }

        if (!isValidDbfFile(layerName, field, layerType)) {
          return false;
        }
      } else {
        errorMessage = "Invalid or inexistant " + layerName + NodusC.TYPE_DBF + "  file";

        return false;
      }
    } catch (DBFException ex) {
      errorMessage = ex.toString();

      return false;
    }

    return true;
  }

  /**
   * Verifies if the different shape files listed in a project property file (nodes and
   * links) correspond to what is expected by Nodus: <br>
   * - The shape files must contain points or polylines; <br>
   * - The .dbf files must have some mandatory fields. <br>
   *
   * @param nodusProjectProperties The Nodus project properties.
   * @return True on success.
   */
  public static boolean isValidProject(Properties nodusProjectProperties) {
    String path = nodusProjectProperties.getProperty(NodusC.PROP_PROJECT_DOTPATH);

    // Nodes
    String tokens = nodusProjectProperties.getProperty(NodusC.PROP_NETWORK_NODES);
    StringTokenizer st = new StringTokenizer(tokens);

    while (st.hasMoreTokens()) {
      String currentName = st.nextToken();

      if (!isValidLayer(path, currentName, SHAPE_TYPE_POINT)) {
        JOptionPane.showMessageDialog(
            null, errorMessage, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);

        return false;
      }
    }

    // Links
    tokens = nodusProjectProperties.getProperty(NodusC.PROP_NETWORK_LINKS);
    st = new StringTokenizer(tokens);

    while (st.hasMoreTokens()) {
      String currentName = st.nextToken();

      if (!isValidLayer(path, currentName, SHAPE_TYPE_POLYLINE)) {
        if (enabledFieldMustBeAdded) {
          addEnabledField(path, currentName);
          enabledFieldMustBeAdded = false;
        } else {
          JOptionPane.showMessageDialog(
              null, errorMessage, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);

          return false;
        }
      }
    }

    return true;
  }

  /**
   * The Nodus table structure was changed in order to allow node numbers up to 999999 instead of
   * 99999, and even 99999999 in Nodus 7+. Just accept the old style.
   *
   * @param type Type of database (SHAPE_TYPE_ARC or SHAPE_TYPE_POINT)
   * @param n Index of the field
   * @param field Dbase field structure
   * @return True if the node or link ID number is a N,5,0 (which was the old format)
   */
  private static boolean wasValidDbfField(int type, int n, DBFField field) {
    boolean mustBeTested = false;

    // First field must be tested
    if (n == NodusC.DBF_IDX_NUM) {
      mustBeTested = true;
    }

    if (type == SHAPE_TYPE_ARC) {
      if (n == NodusC.DBF_IDX_NODE1 || n == NodusC.DBF_IDX_NODE2) {
        mustBeTested = true;
      }
    }

    boolean isOk = true;
    if (mustBeTested) {
      // Test the old 5 digits
      if (field.getType() != 'N' || field.getLength() != 5 || field.getDecimalCount() != 0) {
        isOk = false;
      }

      if (!isOk) {
        /* Test the 6 digits format (prior to Nodus 7 and Etis networks
         *  compatibility with 10 digits) */
        isOk = true;
        if (field.getType() != 'N' || field.getLength() != 6 || field.getDecimalCount() != 0) {
          isOk = false;
        }
      }
    }

    return isOk;
  }
}
