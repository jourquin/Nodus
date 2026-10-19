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

package edu.uclouvain.core.nodus.testing;

import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.dataAccess.shape.output.ShpOutputStream;
import com.bbn.openmap.dataAccess.shape.output.ShxOutputStream;
import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;
import edu.uclouvain.core.nodus.database.dbf.DbfFiles;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes small, valid persisted Nodus layers without replacing their production loaders. */
public final class ProjectFilesFixture {
  private ProjectFilesFixture() {}

  /** Returns the mandatory node/link schema and one custom text column. */
  public static DBFField[] fields(boolean point, boolean legacy) {
    String[] names = point ? NodusC.NODES_MANDATORY_NAMES : NodusC.LINKS_MANDATORY_NAMES;
    int[] lengths = point ? NodusC.NODES_MANDATORY_LENGTHS : NodusC.LINKS_MANDATORY_LENGTHS;
    int[] decimals =
        point ? NodusC.NODES_MANDATORY_DECIMAL_COUNTS : NodusC.LINKS_MANDATORY_DECIMAL_COUNTS;
    List<DBFField> fields = new ArrayList<>();
    for (int i = 0; i < names.length; i++) {
      if (!legacy || i != NodusC.DBF_IDX_ENABLED) {
        fields.add(new DBFField(names[i], 'N', lengths[i], decimals[i]));
      }
    }
    fields.add(new DBFField("LABEL", 'C', 60, 0));
    return fields.toArray(new DBFField[0]);
  }

  /** Builds a record with safe network defaults and a custom label. */
  public static Object[] row(boolean point, boolean legacy, int id, String label) {
    if (point) {
      return new Object[] {id, 0, 0, label};
    }
    return legacy
        ? new Object[] {id, 0, 1, 2, 3, 4, 1000, 65.25, label}
        : new Object[] {id, 0, 1, 1, 2, 3, 4, 1000, 65.25, label};
  }

  /** Writes the geometry and DBF sidecars for records in their supplied order. */
  public static void writeLayer(Path directory, String name, boolean point, int... ids)
      throws Exception {
    final EsriGraphicList graphics = point ? new EsriPointList() : new EsriPolylineList();
    final List<Object[]> rows = new ArrayList<>();
    for (int id : ids) {
      graphics.add(
          point
              ? new EsriPoint(50, 4 + id * .01)
              : new EsriPolyline(
                  new double[] {50, 4, 50, 4.1},
                  OMGraphic.DECIMAL_DEGREES,
                  OMGraphic.LINETYPE_STRAIGHT));
      rows.add(row(point, false, id, "Route d'été " + id));
    }
    try (OutputStream shp = Files.newOutputStream(directory.resolve(name + ".shp"));
        OutputStream shx = Files.newOutputStream(directory.resolve(name + ".shx"))) {
      int[][] index = new ShpOutputStream(shp).writeGeometry(graphics);
      new ShxOutputStream(shx).writeIndex(index, graphics.getType());
    }
    writeDbf(directory, name, fields(point, false), StandardCharsets.UTF_8, rows);
  }

  /** Replaces a DBF with an explicitly supplied schema, encoding and rows. */
  public static void writeDbf(
      Path directory, String name, DBFField[] fields, Charset charset, List<Object[]> rows)
      throws Exception {
    Path dbf = directory.resolve(name + ".dbf");
    try (DBFWriter writer = new DBFWriter(dbf.toString(), fields, charset)) {
      for (Object[] row : rows) {
        writer.addRecord(row);
      }
    }
    DbfFiles.writeCpg(DbfFiles.cpg(dbf), charset);
  }
}
