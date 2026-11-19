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

package edu.uclouvain.core.nodus.database.parquet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.parquet.NodusReadOptions;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;

/** Local Parquet readers using the same codecs as Nodus table imports. */
public final class ParquetFiles {
  private ParquetFiles() {}

  /**
   * Opens a local file without Hadoop configuration or codec discovery.
   *
   * <p>Supports GZIP, SNAPPY and UNCOMPRESSED pages, with the same 64 MiB decoded-page limit as
   * Nodus imports. Callers may set a requested schema for column projection before reading.
   *
   * @param file local Parquet file
   * @return reader owned by the caller; close it with try-with-resources
   * @throws IOException if opening or reading the footer fails
   */
  public static ParquetFileReader openReader(Path file) throws IOException {
    return ParquetFileReader.open(
        new LocalInputFile(file), new NodusReadOptions(new ParquetCodecs()));
  }

  /**
   * Opens a streaming reader for selected top-level columns, in the requested order.
   *
   * <p>The returned facade exposes Parquet schemas and groups without exposing reader methods that
   * reference Hadoop classes. It can therefore be used by Groovy scripts compiled against Nodus's
   * installed libraries alone. Projection and record interpretation are schema-neutral: names,
   * types, nested values, nulls and logical annotations come from the file.
   *
   * @param file local Parquet file, never null
   * @param columns nonempty list of distinct, case-sensitive top-level field names
   * @return caller-owned reader; close it with try-with-resources
   * @throws IllegalArgumentException if columns are empty, duplicated or absent from the file
   * @throws IOException if the file cannot be opened or its footer cannot be read
   */
  public static ProjectedReader openProjectedReader(Path file, List<String> columns)
      throws IOException {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(columns, "columns");
    ParquetFileReader reader = openReader(file);
    try {
      return new ProjectedReader(reader, columns);
    } catch (RuntimeException | Error failure) {
      try {
        reader.close();
      } catch (IOException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  /**
   * A sequential projected row stream, decoding at most one row group at a time.
   *
   * <p>Instances are not thread-safe. All operations, including closure, belong to the caller; do
   * not interrupt file I/O on another thread to cancel it. Returned groups are materialized values
   * and remain usable after the next read or after reader closure. No rows are filtered, grouped or
   * converted to database-specific types.
   */
  public static final class ProjectedReader implements AutoCloseable {
    private final ParquetFileReader reader;
    private final MessageType schema;
    private final MessageColumnIO columnIO;
    private PageReadStore pages;
    private RecordReader<Group> records;
    private long remaining;
    private boolean exhausted;
    private boolean closed;

    private ProjectedReader(ParquetFileReader reader, List<String> columns) {
      this.reader = reader;
      MessageType source = reader.getFileMetaData().getSchema();
      if (columns.isEmpty()) {
        throw new IllegalArgumentException("Select at least one Parquet column.");
      }
      Set<String> seen = new HashSet<>();
      List<Type> selected = new ArrayList<>();
      for (String name : columns) {
        if (name == null || !seen.add(name) || !source.containsField(name)) {
          throw new IllegalArgumentException("Missing or duplicate Parquet column: " + name);
        }
        selected.add(source.getType(name));
      }
      schema = new MessageType(source.getName(), selected);
      reader.setRequestedSchema(schema);
      columnIO = new ColumnIOFactory().getColumnIO(schema, source, true);
    }

    /**
     * Returns the selected schema in requested column order, with original types and annotations.
     *
     * @return selected immutable Parquet schema
     */
    public MessageType getSchema() {
      return schema;
    }

    /**
     * Reads the next materialized group, preserving scalar/nested values and nulls.
     *
     * @return the next row, or null at end of file; subsequent reads also return null
     * @throws IOException if reading fails or the reader has already been closed
     */
    public Group read() throws IOException {
      if (closed) {
        throw new IOException("Parquet reader is closed.");
      }
      if (exhausted) {
        return null;
      }
      while (remaining == 0) {
        if (pages != null) {
          pages.close();
          pages = null;
        }
        pages = reader.readNextRowGroup();
        if (pages == null) {
          exhausted = true;
          return null;
        }
        remaining = pages.getRowCount();
        records = columnIO.getRecordReader(pages, new GroupRecordConverter(schema));
      }
      remaining--;
      return records.read();
    }

    /**
     * Releases the current row group and file reader. Repeated closure is harmless.
     *
     * @throws IOException if closing the underlying file fails
     */
    @Override
    public void close() throws IOException {
      if (!closed) {
        closed = true;
        try {
          if (pages != null) {
            pages.close();
          }
        } finally {
          reader.close();
        }
      }
    }
  }
}
