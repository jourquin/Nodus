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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream;
import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.compression.CompressionCodecFactory;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

/** Local codecs using the JDK and existing Commons Compress library, without Hadoop discovery. */
final class ParquetCodecs implements CompressionCodecFactory {
  private static final int MAX_PAGE_SIZE = 64 * 1024 * 1024;

  @Override
  public BytesInputCompressor getCompressor(CompressionCodecName codec) {
    if (codec != CompressionCodecName.GZIP) {
      throw new IllegalArgumentException("Unsupported export compression: " + codec);
    }
    return new BytesInputCompressor() {
      @Override
      public BytesInput compress(BytesInput input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
          input.writeAllTo(gzip);
        }
        return BytesInput.from(output.toByteArray());
      }

      @Override
      public CompressionCodecName getCodecName() {
        return codec;
      }

      @Override
      public void release() {}
    };
  }

  @Override
  public BytesInputDecompressor getDecompressor(CompressionCodecName codec) {
    if (codec != CompressionCodecName.GZIP
        && codec != CompressionCodecName.UNCOMPRESSED
        && codec != CompressionCodecName.SNAPPY) {
      throw new IllegalArgumentException(
          "Unsupported Parquet compression: " + codec + ". Supported: GZIP, SNAPPY, UNCOMPRESSED.");
    }
    return new BytesInputDecompressor() {
      @Override
      public BytesInput decompress(BytesInput input, int size) throws IOException {
        if (size < 0 || size > MAX_PAGE_SIZE) {
          throw new IOException("Parquet page exceeds 64 MiB");
        }
        InputStream raw = input.toInputStream();
        try (InputStream decoded =
            codec == CompressionCodecName.GZIP
                ? new GZIPInputStream(raw)
                : codec == CompressionCodecName.SNAPPY
                    ? new SnappyCompressorInputStream(raw)
                    : raw) {
          byte[] bytes = decoded.readNBytes(size + 1);
          if (bytes.length != size) {
            throw new IOException("Incorrect Parquet page size");
          }
          return BytesInput.from(bytes);
        }
      }

      @Override
      public void decompress(ByteBuffer input, int compressedSize, ByteBuffer output, int size)
          throws IOException {
        if (compressedSize < 0 || compressedSize > input.remaining()) {
          throw new IOException("Invalid compressed Parquet page size");
        }
        byte[] bytes = new byte[compressedSize];
        input.get(bytes);
        try (InputStream decoded = decompress(BytesInput.from(bytes), size).toInputStream()) {
          output.put(decoded.readNBytes(size));
        }
      }

      @Override
      public void release() {}
    };
  }

  @Override
  public void release() {}
}
