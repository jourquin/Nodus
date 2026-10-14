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
import edu.uclouvain.core.nodus.NodusProject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Shared per-file DBF encoding for network attributes, imports and exports. */
public final class DbfFiles {
  /** Optional project default or table-specific override, for example road_links.dbfCharset. */
  public static final String CHARSET_PROPERTY = "dbfCharset";

  private DbfFiles() {}

  /**
   * Returns an explicit layer override, then an explicit project override, or null.
   *
   * @param project current project, or null for standalone files
   * @param file DBF path whose filename stem selects the layer override
   * @return configured override, or blank/null when none is set
   */
  public static String override(NodusProject project, Path file) {
    if (project == null) {
      return null;
    }
    return project.getLocalProperty(
        stem(file) + "." + CHARSET_PROPERTY, project.getLocalProperty(CHARSET_PROPERTY, ""));
  }

  /**
   * Selects an explicit override, CPG metadata, DBF language driver, then the UTF-8 fallback.
   *
   * @param file DBF path; missing files use the fallback unless an override or CPG exists
   * @param override optional explicit charset name
   * @return resolved per-file charset
   * @throws IOException for invalid metadata or an unreadable existing file
   */
  public static Charset charset(Path file, String override) throws IOException {
    if (override != null && !override.isBlank()) {
      return parseCharset(override, file);
    }
    Path cpg = cpg(file);
    if (Files.exists(cpg)) {
      return parseCharset(Files.readString(cpg, StandardCharsets.UTF_8).replace("\uFEFF", ""), cpg);
    }
    if (Files.exists(file)) {
      try (InputStream input = Files.newInputStream(file)) {
        byte[] header = input.readNBytes(32);
        if (header.length != 32) {
          // There is no language-driver byte to resolve. A reader still rejects this invalid
          // file, while an explicitly requested export may replace it using the UTF-8 fallback.
          return StandardCharsets.UTF_8;
        }
        Charset detected = DBFCharsetHelper.getCharsetByByte(header[29] & 0xff);
        if (detected != null) {
          return detected;
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  private static Charset parseCharset(String value, Path source) throws IOException {
    String name = value.trim();
    if (name.equals("65001")) {
      name = "UTF-8";
    } else if (name.matches("125[0-8]")) {
      name = "windows-" + name;
    } else if (name.matches("[0-9]+")) {
      name = "IBM" + name;
    } else if (name.toUpperCase(Locale.ROOT).matches("ANSI[ -]?125[0-8]")) {
      name = "windows-" + name.substring(name.length() - 4);
    }
    try {
      return Charset.forName(name);
    } catch (IllegalArgumentException failure) {
      throw new IOException("Invalid DBF charset '" + value.trim() + "' for " + source, failure);
    }
  }

  /**
   * Opens a DBF with the same policy used by shapefile attributes and exports.
   *
   * @param file DBF path
   * @param override optional explicit charset name
   * @return reader owned by the caller, which must close it
   * @throws IOException if encoding metadata cannot be resolved
   */
  public static DBFReader reader(Path file, String override) throws IOException {
    return new DBFReader(file.toString(), charset(file, override));
  }

  /**
   * Opens a project's DBF with its optional table-specific or project override.
   *
   * @param project project supplying encoding overrides
   * @param file DBF path
   * @return reader owned by the caller, which must close it
   * @throws IOException if encoding metadata cannot be resolved
   */
  public static DBFReader reader(NodusProject project, Path file) throws IOException {
    return reader(file, override(project, file));
  }

  /** A loaded OpenMap-compatible table with its effective charset retained for later saves. */
  public static final class Table {
    /** Table data in physical shapefile record order. */
    public final DbfTableModel model;
    /** Charset selected when the table was read. */
    public final Charset charset;

    private Table(DbfTableModel model, Charset charset) {
      this.model = model;
      this.charset = charset;
    }
  }

  /**
   * Reads all physical rows so attributes stay aligned with the shapefile's geometry records.
   *
   * @param file DBF path
   * @param override optional explicit charset name
   * @return loaded table and charset to preserve on later saves
   * @throws IOException if encoding metadata cannot be resolved
   */
  public static Table readModel(Path file, String override) throws IOException {
    Charset encoding = charset(file, override);
    try (DBFReader reader = new DBFReader(file.toString(), encoding, true)) {
      int columns = reader.getFieldCount() - 1;
      DbfTableModel model = new DbfTableModel(columns);
      for (int column = 0; column < columns; column++) {
        DBFField field = reader.getField(column + 1);
        model.setColumnName(column, field.getName());
        model.setType(column, (byte) field.getType());
        model.setLength(column, field.getLength());
        model.setDecimalCount(column, (byte) field.getDecimalCount());
      }
      SimpleDateFormat dates = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
      while (reader.hasNextRecord()) {
        Object[] record = reader.nextRecord();
        if (record == null) {
          continue;
        }
        List<Object> row = new ArrayList<>(columns);
        // showDeletedRows prepends a flag; retaining the row itself preserves SHP/DBF alignment.
        for (int column = 0; column < columns; column++) {
          Object value = record[column + 1];
          if (value instanceof Number) {
            value = ((Number) value).doubleValue();
          } else if (value instanceof Date) {
            value = dates.format((Date) value);
          } else if (value instanceof Boolean) {
            value = (Boolean) value ? "T" : "F";
          }
          row.add(value);
        }
        model.addRecord(row);
      }
      return new Table(model, encoding);
    }
  }

  /**
   * Returns an existing CPG sidecar, preserving its filename case, or a new lowercase path.
   *
   * @param dbf DBF path
   * @return sidecar path
   * @throws IOException for inaccessible directories or multiple conflicting sidecars
   */
  public static Path cpg(Path dbf) throws IOException {
    Path absolute = dbf.toAbsolutePath();
    String name = stem(dbf) + ".cpg";
    Path lower = absolute.resolveSibling(name);
    if (Files.isDirectory(absolute.getParent())) {
      try (var files = Files.list(absolute.getParent())) {
        List<Path> matches =
            files
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(name))
                .collect(java.util.stream.Collectors.toList());
        if (matches.size() > 1) {
          throw new IOException("Conflicting CPG sidecars for " + dbf);
        }
        if (!matches.isEmpty()) {
          return matches.get(0);
        }
      }
    }
    return lower;
  }

  /**
   * Writes explicit charset metadata, including UTF-8 which has no standard DBF driver byte.
   *
   * @param cpg sidecar destination, including a staged path in a layer-save transaction
   * @param charset charset used to write the corresponding DBF
   * @throws IOException if metadata cannot be written
   */
  public static void writeCpg(Path cpg, Charset charset) throws IOException {
    Files.writeString(cpg, charset.name() + "\n", StandardCharsets.US_ASCII);
  }

  private static String stem(Path file) {
    String name = file.getFileName().toString();
    return name.toLowerCase(Locale.ROOT).endsWith(".dbf")
        ? name.substring(0, name.length() - 4)
        : name;
  }
}
