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

package edu.uclouvain.core.nodus.database;

import com.bbn.openmap.dataAccess.shape.EsriGraphicFactory.Header;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.dataAccess.shape.input.LittleEndianInputStream;
import com.bbn.openmap.dataAccess.shape.output.ShpOutputStream;
import com.bbn.openmap.dataAccess.shape.output.ShxOutputStream;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.dbf.DBFException;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;
import edu.uclouvain.core.nodus.database.dbf.DbfFiles;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.JOptionPane;

/**
 * Some handy utilities to perform some operations on tables and files of a Nodus project.
 *
 * @author Bart Jourquin
 */
public class ProjectFilesTools implements ShapeConstants {
  /** Default constructor. */
  public ProjectFilesTools() {}

  /** Upgrades a validated legacy schema, keeping a permanent copy of the original DBF. */
  private static void addEnabledField(String path, String layerName, String override)
      throws IOException {
    final Path file = Path.of(path, layerName + NodusC.TYPE_DBF);
    final DBFField[] fields;
    try (DBFReader reader = DbfFiles.reader(file, override)) {
      fields = new DBFField[reader.getFieldCount()];
      for (int i = 0; i < fields.length; i++) {
        fields[i] = reader.getField(i);
      }
    }
    try (DbfSchemaConversion conversion = DbfSchemaConversion.prepare(file, fields, fields, true)) {
      conversion.commit();
    }
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

      Path file = Path.of(path, layerName + NodusC.TYPE_DBF);
      Charset charset = DbfFiles.charset(file, null);
      try (DBFWriter dbf = new DBFWriter(file.toString(), field, charset)) {
        // Empty DBF file: closing the writer writes the header/footer.
      }
      DbfFiles.writeCpg(DbfFiles.cpg(file), charset);
    } catch (DBFException | IOException ex) {
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
    try (DBFReader dbfReader = DbfFiles.reader(Path.of(path, modelTable + NodusC.TYPE_DBF), null)) {
      // Get the structure of the table model
      if (dbfReader.isOpen()) {
        int nbFields = dbfReader.getFieldCount();
        DBFField[] field = new DBFField[nbFields];

        for (int i = 0; i < nbFields; i++) {
          field[i] = dbfReader.getField(i);
        }

        // Create an empty dbf file with the given structure
        Path file = Path.of(path, layerName + NodusC.TYPE_DBF);
        Charset charset = DbfFiles.charset(file, null);
        try (DBFWriter dbf = new DBFWriter(file.toString(), field, charset)) {
          // Empty DBF file: closing the writer writes the header/footer.
        }
        DbfFiles.writeCpg(DbfFiles.cpg(file), charset);
      }
    } catch (DBFException | IOException ex) {
      System.err.println(ex.toString());
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
    try (FileOutputStream shpOutputStream =
            new FileOutputStream(path + layerName + NodusC.TYPE_SHP);
        FileOutputStream shxOutputStream =
            new FileOutputStream(path + layerName + NodusC.TYPE_SHX)) {
      ShpOutputStream pos = new ShpOutputStream(shpOutputStream);
      int[][] indexData = pos.writeGeometry(list);

      ShxOutputStream xos = new ShxOutputStream(shxOutputStream);
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
   * @return An array of valid layer names or null if no layers are available.
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
    if (children == null) {
      return null;
    }
    for (String element : children) {
      String name = element.substring(0, element.lastIndexOf(NodusC.TYPE_SHP));

      if (isValidLayer(path, name, layerType)) {
        availableLayers.add(name);
      }
    }

    // Transform the list into an array
    return availableLayers.toArray(new String[availableLayers.size()]);
  }

  /** Per-file validation state; a repair decision must never leak to the next layer. */
  private static final class Validation {
    final String error;
    final boolean legacy;
    final DBFField[] fields;
    final DBFField[] repaired;
    final List<String> changes;

    Validation(String error, boolean legacy) {
      this(error, legacy, null, null, List.of());
    }

    Validation(
        String error,
        boolean legacy,
        DBFField[] fields,
        DBFField[] repaired,
        List<String> changes) {
      this.error = error;
      this.legacy = legacy;
      this.fields = fields;
      this.repaired = repaired;
      this.changes = changes;
    }
  }

  /** Checks the mandatory prefix and identifies numeric-format repairs for value-level checking. */
  private static Validation validateDbf(String name, DBFField[] fields, int type) {
    final boolean point = type == SHAPE_TYPE_POINT;
    final String[] names = point ? NodusC.NODES_MANDATORY_NAMES : NodusC.LINKS_MANDATORY_NAMES;
    final char[] types = point ? NodusC.NODES_MANDATORY_TYPES : NodusC.LINKS_MANDATORY_TYPES;
    final int[] lengths = point ? NodusC.NODES_MANDATORY_LENGTHS : NodusC.LINKS_MANDATORY_LENGTHS;
    final int[] decimals =
        point ? NodusC.NODES_MANDATORY_DECIMAL_COUNTS : NodusC.LINKS_MANDATORY_DECIMAL_COUNTS;
    final boolean legacy =
        !point
            && fields.length > NodusC.DBF_IDX_ENABLED
            && fields[NodusC.DBF_IDX_ENABLED].getName().equalsIgnoreCase(NodusC.DBF_NODE1);
    if (fields.length < names.length - (legacy ? 1 : 0)) {
      return new Validation(name + ": incomplete mandatory DBF fields", false);
    }
    if (legacy) {
      for (DBFField field : fields) {
        if (field.getName().equalsIgnoreCase(NodusC.DBF_ENABLED)) {
          return new Validation(name + ": misplaced ENABLED field", false);
        }
      }
    }
    final DBFField[] repaired = fields.clone();
    final List<String> changes = new ArrayList<>();
    boolean convertible = true;
    for (int i = 0; i < names.length; i++) {
      if (legacy && i == NodusC.DBF_IDX_ENABLED) {
        continue;
      }
      final int index = legacy && i > NodusC.DBF_IDX_ENABLED ? i - 1 : i;
      DBFField field = fields[index];
      if (!field.getName().equalsIgnoreCase(names[i])) {
        return new Validation(name + ": field " + i + ": " + names[i] + " expected", false);
      }
      boolean id =
          i == NodusC.DBF_IDX_NUM
              || (!point && (i == NodusC.DBF_IDX_NODE1 || i == NodusC.DBF_IDX_NODE2));
      boolean oldWidth = id && (field.getLength() == 5 || field.getLength() == 6);
      if (field.getType() != types[i]
          || (field.getLength() != lengths[i] && !oldWidth)
          || field.getDecimalCount() != decimals[i]) {
        String expected = types[i] + "(" + lengths[i] + "," + decimals[i] + ")";
        changes.add(
            names[i]
                + ": "
                + field.getType()
                + "("
                + field.getLength()
                + ","
                + field.getDecimalCount()
                + ") → "
                + expected);
        convertible &=
            (field.getType() == 'N' || field.getType() == 'F' || field.getType() == 'C')
                && !field.isBinary()
                && !field.isNullable();
        repaired[index] = new DBFField(field.getName(), types[i], lengths[i], decimals[i]);
      }
    }
    if (changes.isEmpty()) {
      return new Validation(null, legacy);
    }
    return new Validation(
        name + ": incompatible DBF fields\n" + String.join("\n", changes),
        legacy,
        fields,
        convertible ? repaired : null,
        changes);
  }

  /**
   * Tests both supported Nodus layer types.
   *
   * @param path directory containing the shapefile
   * @param layerName shapefile basename
   * @return true for a valid current node or link layer
   */
  public static boolean isValidLayer(String path, String layerName) {
    return isValidLayer(path, layerName, SHAPE_TYPE_POINT)
        || isValidLayer(path, layerName, SHAPE_TYPE_POLYLINE);
  }

  /**
   * Tests the geometry headers, sidecars and mandatory DBF structure of a Nodus layer.
   *
   * @param path directory containing the shapefile
   * @param layerName shapefile basename
   * @param layerType SHAPE_TYPE_POINT or SHAPE_TYPE_POLYLINE
   * @return true for a valid current layer
   */
  public static boolean isValidLayer(String path, String layerName, int layerType) {
    Validation validation = validateLayer(path, layerName, layerType, null);
    return validation.error == null && !validation.legacy;
  }

  private static Validation validateLayer(
      String path, String layerName, int layerType, String charset) {
    if (layerType != SHAPE_TYPE_POINT && layerType != SHAPE_TYPE_POLYLINE) {
      return new Validation("Unsupported Nodus layer type: " + layerType, false);
    }
    try {
      for (String extension : new String[] {NodusC.TYPE_SHP, NodusC.TYPE_SHX}) {
        try (BufferedInputStream input =
            new BufferedInputStream(
                new FileInputStream(Path.of(path, layerName + extension).toFile()))) {
          Header header = new Header(new LittleEndianInputStream(input));
          if (header.fileCode != 9994
              || header.version != 1000
              || header.fileLength < 50
              || header.shapeType != layerType) {
            return new Validation(layerName + extension + ": invalid shapefile header", false);
          }
        }
      }
      try (DBFReader reader =
          DbfFiles.reader(Path.of(path, layerName + NodusC.TYPE_DBF), charset)) {
        if (!reader.isOpen()) {
          return new Validation(layerName + ": invalid DBF file", false);
        }
        DBFField[] fields = new DBFField[reader.getFieldCount()];
        for (int i = 0; i < fields.length; i++) {
          fields[i] = reader.getField(i);
        }
        return validateDbf(layerName, fields, layerType);
      }
    } catch (IOException | DBFException error) {
      return new Validation(error.toString(), false);
    }
  }

  /**
   * Validates project network layers and upgrades the supported pre-ENABLED link schema.
   *
   * @param nodusProjectProperties project properties
   * @return true when every layer is valid
   */
  public static boolean isValidProject(Properties nodusProjectProperties) {
    return isValidProject(
        nodusProjectProperties,
        message ->
            JOptionPane.showMessageDialog(null, message, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE),
        message ->
            JOptionPane.showConfirmDialog(
                    null,
                    message,
                    "Convert layer DBF",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE)
                == JOptionPane.YES_OPTION);
  }

  /** Validates a project with a caller-supplied diagnostic destination. */
  static boolean isValidProject(Properties properties, Consumer<String> reportError) {
    return isValidProject(properties, reportError, message -> false);
  }

  /** Checks every value before asking once per affected layer to install a lossless repair. */
  static boolean isValidProject(
      Properties properties, Consumer<String> reportError, Predicate<String> confirmConversion) {
    final String path = properties.getProperty(NodusC.PROP_PROJECT_DOTPATH);
    if (path == null) {
      reportError.accept("Missing project directory");
      return false;
    }
    for (int type : new int[] {SHAPE_TYPE_POINT, SHAPE_TYPE_POLYLINE}) {
      String property =
          type == SHAPE_TYPE_POINT ? NodusC.PROP_NETWORK_NODES : NodusC.PROP_NETWORK_LINKS;
      StringTokenizer names = new StringTokenizer(properties.getProperty(property, ""));
      while (names.hasMoreTokens()) {
        String name = names.nextToken();
        String charset =
            properties.getProperty(name + ".dbfCharset", properties.getProperty("dbfCharset", ""));
        Validation validation = validateLayer(path, name, type, charset);
        if (validation.error != null && validation.repaired != null) {
          final Path file = Path.of(path, name + NodusC.TYPE_DBF);
          try (DbfSchemaConversion conversion =
              DbfSchemaConversion.prepare(
                  file, validation.fields, validation.repaired, validation.legacy)) {
            String message =
                "Layer '"
                    + name
                    + "' has DBF fields that can be converted without "
                    + "rounding or truncating values:\n\n"
                    + String.join("\n", validation.changes)
                    + (validation.legacy ? "\nThe missing ENABLED field will also be added." : "")
                    + "\n\nConvert this layer now?\nThe original will be kept as "
                    + conversion.backup.getFileName()
                    + ".";
            if (!confirmConversion.test(message)) {
              return false;
            }
            conversion.commit();
            validation = validateLayer(path, name, type, charset);
          } catch (IOException | DBFException error) {
            reportError.accept(name + ": cannot convert DBF: " + error.getMessage());
            return false;
          }
        } else if (validation.error == null && validation.legacy) {
          try {
            addEnabledField(path, name, charset);
            validation = validateLayer(path, name, type, charset);
          } catch (IOException | DBFException error) {
            validation = new Validation(error.toString(), false);
          }
        }
        if (validation.error != null || validation.legacy) {
          reportError.accept(
              validation.error == null ? "Cannot upgrade " + name : validation.error);
          return false;
        }
      }
    }
    return true;
  }
}
