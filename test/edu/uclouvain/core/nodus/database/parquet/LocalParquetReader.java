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

import java.nio.file.Path;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.io.LocalInputFile;

/** Apache's standard group reader using local files without Hadoop filesystem authentication. */
public final class LocalParquetReader extends ParquetReader.Builder<Group> {
  /**
   * Selects the local-file API so tests also run on JDKs without Subject.getSubject support.
   *
   * @param path the Parquet test file
   */
  public LocalParquetReader(Path path) {
    super(new LocalInputFile(path), new PlainParquetConfiguration());
  }

  @Override
  protected ReadSupport<Group> getReadSupport() {
    return new GroupReadSupport();
  }
}
