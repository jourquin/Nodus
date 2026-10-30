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

package org.apache.parquet;

import java.util.Collections;
import org.apache.parquet.bytes.HeapByteBufferAllocator;
import org.apache.parquet.compression.CompressionCodecFactory;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.format.converter.ParquetMetadataConverter;

/**
 * Local read options for the pinned Parquet 1.16.0 library, without Hadoop's input-format builder.
 *
 * <p>This bridge lives in Parquet's package to call its package-private constructor. The public
 * builder loads ParquetInputFormat (and Hadoop MapReduce) even with plain configuration. Keeping
 * this call explicit makes incompatible constructor changes fail at compilation on an upgrade. No
 * filtering, encryption or Hadoop vectored IO is used for complete table imports.
 */
public final class NodusReadOptions extends ParquetReadOptions {
  /**
   * Creates checksum-verified local read options with a 64 MiB page allocation limit.
   *
   * @param codecs local compression codecs
   */
  public NodusReadOptions(CompressionCodecFactory codecs) {
    super(
        false,
        false,
        false,
        false,
        false,
        true,
        false,
        false,
        false,
        FilterCompat.NOOP,
        ParquetMetadataConverter.NO_FILTER,
        codecs,
        new HeapByteBufferAllocator(),
        64 * 1024 * 1024,
        Collections.emptyMap(),
        null,
        null,
        new PlainParquetConfiguration());
  }
}
