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

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.TableExport;
import edu.uclouvain.core.nodus.database.TableImport;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;

/** Streams a JDBC table to a staged Parquet file with nullable, typed columns. */
public final class ExportParquet {
  private ExportParquet() {}

  /** Exports a complete table to tableName.parquet in the project directory. */
  public static boolean exportTable(NodusProject project, String tableName) {
    try {
      Path target =
          Path.of(
              project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + tableName
                  + NodusC.TYPE_PARQUET);
      new TableExport()
          .write(
              target,
              output -> {
                @SuppressWarnings("resource") // The project owns and closes this connection.
                Connection connection = project.getMainJDBCConnection();
                try (Statement statement = connection.createStatement();
                    ResultSet rows =
                        statement.executeQuery(
                            "SELECT * FROM " + JDBCUtils.getQuotedCompliantIdentifier(tableName))) {
                  write(rows, output);
                }
              });
      return true;
    } catch (Exception error) {
      TableImport.reportError(error);
      return false;
    }
  }

  /** Serializes without taking ownership of the JDBC result set or caller's output stream. */
  static void write(ResultSet rows, OutputStream output) throws Exception {
    ResultSetMetaData metadata = rows.getMetaData();
    List<Type> fields = new ArrayList<>();
    for (int i = 1; i <= metadata.getColumnCount(); i++) {
      PrimitiveTypeName physical;
      LogicalTypeAnnotation logical = null;
      switch (metadata.getColumnType(i)) {
        case Types.BOOLEAN:
        case Types.BIT:
          physical = PrimitiveTypeName.BOOLEAN;
          break;
        case Types.TINYINT:
        case Types.SMALLINT:
        case Types.INTEGER:
          physical = PrimitiveTypeName.INT32;
          break;
        case Types.BIGINT:
          physical = PrimitiveTypeName.INT64;
          break;
        case Types.REAL:
          physical = PrimitiveTypeName.FLOAT;
          break;
        case Types.FLOAT:
        case Types.DOUBLE:
          physical = PrimitiveTypeName.DOUBLE;
          break;
        case Types.NUMERIC:
        case Types.DECIMAL:
          physical = PrimitiveTypeName.BINARY;
          logical =
              LogicalTypeAnnotation.decimalType(metadata.getScale(i), metadata.getPrecision(i));
          break;
        case Types.DATE:
          physical = PrimitiveTypeName.INT32;
          logical = LogicalTypeAnnotation.dateType();
          break;
        case Types.TIME:
          physical = PrimitiveTypeName.INT64;
          logical = LogicalTypeAnnotation.timeType(false, LogicalTypeAnnotation.TimeUnit.MICROS);
          break;
        case Types.TIMESTAMP:
          physical = PrimitiveTypeName.INT64;
          logical =
              LogicalTypeAnnotation.timestampType(false, LogicalTypeAnnotation.TimeUnit.MICROS);
          break;
        case Types.BINARY:
        case Types.VARBINARY:
        case Types.LONGVARBINARY:
          physical = PrimitiveTypeName.BINARY;
          break;
        case Types.CHAR:
        case Types.VARCHAR:
        case Types.LONGVARCHAR:
        case Types.NCHAR:
        case Types.NVARCHAR:
        case Types.LONGNVARCHAR:
          physical = PrimitiveTypeName.BINARY;
          logical = LogicalTypeAnnotation.stringType();
          break;
        default:
          throw new SQLException(
              "Unsupported Parquet column type: "
                  + metadata.getColumnTypeName(i)
                  + " ("
                  + metadata.getColumnName(i)
                  + ")");
      }
      org.apache.parquet.schema.Types.PrimitiveBuilder<PrimitiveType> builder =
          org.apache.parquet.schema.Types.optional(physical);
      if (logical != null) {
        builder.as(logical);
      }
      fields.add(builder.named(metadata.getColumnName(i)));
    }
    MessageType schema = new MessageType("nodus", fields);
    OutputFile file =
        new OutputFile() {
          public boolean supportsBlockSize() {
            return false;
          }

          public long defaultBlockSize() {
            return 0;
          }

          public PositionOutputStream create(long hint) {
            return stream();
          }

          public PositionOutputStream createOrOverwrite(long hint) {
            return stream();
          }

          private PositionOutputStream stream() {
            return new PositionOutputStream() {
              private long position;

              public long getPos() {
                return position;
              }

              public void write(int value) throws IOException {
                output.write(value);
                position++;
              }

              public void write(byte[] bytes, int offset, int length) throws IOException {
                output.write(bytes, offset, length);
                position += length;
              }

              public void flush() throws IOException {
                output.flush();
              }
            };
          }
        };
    SimpleGroupFactory groups = new SimpleGroupFactory(schema);
    try (ParquetWriter<Group> writer =
        ExampleParquetWriter.builder(file)
            .withType(schema)
            .withConf(new PlainParquetConfiguration())
            .withCodecFactory(new ParquetCodecs())
            .withCompressionCodec(CompressionCodecName.GZIP)
            .withRowGroupSize(16L * 1024 * 1024)
            .build()) {
      while (rows.next()) {
        Group group = groups.newGroup();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
          if (rows.getObject(i) == null) {
            continue;
          }
          int c = i - 1;
          switch (metadata.getColumnType(i)) {
            case Types.BOOLEAN:
            case Types.BIT:
              group.add(c, rows.getBoolean(i));
              break;
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
              group.add(c, rows.getInt(i));
              break;
            case Types.BIGINT:
              group.add(c, rows.getLong(i));
              break;
            case Types.REAL:
              group.add(c, rows.getFloat(i));
              break;
            case Types.FLOAT:
            case Types.DOUBLE:
              group.add(c, rows.getDouble(i));
              break;
            case Types.NUMERIC:
            case Types.DECIMAL:
              group.add(
                  c,
                  Binary.fromConstantByteArray(
                      rows.getBigDecimal(i)
                          .setScale(metadata.getScale(i))
                          .unscaledValue()
                          .toByteArray()));
              break;
            case Types.DATE:
              group.add(c, Math.toIntExact(rows.getDate(i).toLocalDate().toEpochDay()));
              break;
            case Types.TIME:
              java.time.LocalTime time = rows.getObject(i, java.time.LocalTime.class);
              if (time.getNano() % 1000 != 0) {
                throw new SQLException("Sub-microsecond TIME unsupported");
              }
              group.add(c, time.toNanoOfDay() / 1000);
              break;
            case Types.TIMESTAMP:
              java.time.LocalDateTime ts = rows.getObject(i, java.time.LocalDateTime.class);
              if (ts.getNano() % 1000 != 0) {
                throw new SQLException("Sub-microsecond TIMESTAMP unsupported");
              }
              group.add(
                  c,
                  Math.addExact(
                      Math.multiplyExact(ts.toEpochSecond(java.time.ZoneOffset.UTC), 1000000L),
                      ts.getNano() / 1000));
              break;
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
              group.add(c, Binary.fromConstantByteArray(rows.getBytes(i)));
              break;
            default:
              group.add(c, rows.getString(i));
              break;
          }
        }
        writer.write(group);
      }
    }
  }
}
