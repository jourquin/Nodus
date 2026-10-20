/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.database;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DbfFiles;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Stages an exact numeric schema repair, copying other DBF bytes and physical rows unchanged. */
final class DbfSchemaConversion implements AutoCloseable {
  private final Path file;
  private final LayerFileSave save;
  final Path backup;
  private byte[] originalHash;

  private DbfSchemaConversion(Path file) throws IOException {
    this.file = file;
    String name = file.getFileName().toString();
    save = new LayerFileSave(file.getParent(), name.substring(0, name.length() - 4));
    Path candidate = file.resolveSibling(name + ".bak");
    int version = 1;
    while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
      candidate = file.resolveSibling(name + "." + version++ + ".bak");
    }
    backup = candidate;
  }

  static DbfSchemaConversion prepare(
      Path file, DBFField[] original, DBFField[] replacement, boolean addEnabled)
      throws IOException {
    DbfSchemaConversion conversion = new DbfSchemaConversion(file.toAbsolutePath());
    try {
      Path cpg = DbfFiles.cpg(file);
      if (Files.exists(cpg) && !Files.isRegularFile(cpg)) {
        throw new IOException("Not a regular file: " + cpg);
      }
      conversion.write(original, replacement, addEnabled);
      return conversion;
    } catch (IOException | RuntimeException failure) {
      try {
        conversion.close();
      } catch (IOException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private void write(DBFField[] original, DBFField[] replacement, boolean addEnabled)
      throws IOException {
    final MessageDigest digest = digest();
    final Path staged = save.stage(file.getFileName().toString());
    try (InputStream input =
            new DigestInputStream(new BufferedInputStream(Files.newInputStream(file)), digest);
        OutputStream output = new BufferedOutputStream(Files.newOutputStream(staged))) {
      byte[] prefix = input.readNBytes(32);
      if (prefix.length != 32) {
        throw new IOException("Truncated DBF header");
      }
      final int headerLength = unsignedShort(prefix, 8);
      final int recordLength = unsignedShort(prefix, 10);
      final int descriptorsEnd = 32 + original.length * 32;
      if (headerLength <= descriptorsEnd || recordLength < 1) {
        throw new IOException("Unsupported DBF header layout");
      }
      byte[] header = Arrays.copyOf(prefix, headerLength);
      readFully(input, header, 32, headerLength - 32);
      if (header[descriptorsEnd] != 13) {
        throw new IOException("Unsupported DBF field descriptors");
      }
      int sourceLength = 1;
      int targetLength = addEnabled ? 2 : 1;
      boolean[] changed = new boolean[original.length];
      for (int column = 0; column < original.length; column++) {
        DBFField before = original[column];
        DBFField after = replacement[column];
        int offset = 32 + 32 * column;
        if ((header[offset + 16] & 255) != before.getLength()
            || (header[offset + 17] & 255) != before.getDecimalCount()
            || (char) header[offset + 11] != before.getType()) {
          throw new IOException("DBF schema changed or uses an unsupported field layout");
        }
        changed[column] =
            before.getType() != after.getType()
                || before.getLength() != after.getLength()
                || before.getDecimalCount() != after.getDecimalCount();
        sourceLength += before.getLength();
        targetLength += after.getLength();
      }
      if (recordLength != sourceLength
          || targetLength > 65535
          || headerLength + (addEnabled ? 32 : 0) > 65535) {
        throw new IOException("Unsupported DBF record layout");
      }
      long records =
          (prefix[4] & 255L)
              | (prefix[5] & 255L) << 8
              | (prefix[6] & 255L) << 16
              | (prefix[7] & 255L) << 24;
      if (Files.size(file) < headerLength + records * recordLength) {
        throw new IOException("Truncated DBF records");
      }
      byte[] targetHeader = header;
      if (addEnabled) {
        int insertion = 32 + 32 * NodusC.DBF_IDX_ENABLED;
        targetHeader = new byte[headerLength + 32];
        System.arraycopy(header, 0, targetHeader, 0, insertion);
        System.arraycopy(header, insertion, targetHeader, insertion + 32, headerLength - insertion);
        byte[] name = NodusC.DBF_ENABLED.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, targetHeader, insertion, name.length);
        targetHeader[insertion + 11] = 'N';
        targetHeader[insertion + 16] = 1;
      }
      putShort(targetHeader, 8, targetHeader.length);
      putShort(targetHeader, 10, targetLength);
      int targetOffset = 1;
      for (int column = 0; column < replacement.length; column++) {
        if (addEnabled && column == NodusC.DBF_IDX_ENABLED) {
          int inserted = 32 + 32 * NodusC.DBF_IDX_ENABLED;
          for (int byteIndex = 0; byteIndex < 4; byteIndex++) {
            targetHeader[inserted + 12 + byteIndex] = (byte) (targetOffset >> (8 * byteIndex));
          }
          targetOffset++;
        }
        int index = column + (addEnabled && column >= NodusC.DBF_IDX_ENABLED ? 1 : 0);
        int descriptor = 32 + index * 32;
        DBFField field = replacement[column];
        targetHeader[descriptor + 11] = (byte) field.getType();
        targetHeader[descriptor + 16] = (byte) field.getLength();
        targetHeader[descriptor + 17] = (byte) field.getDecimalCount();
        // Some DBF dialects store field addresses; keep unused addresses zero.
        if (targetHeader[descriptor + 12] != 0
            || targetHeader[descriptor + 13] != 0
            || targetHeader[descriptor + 14] != 0
            || targetHeader[descriptor + 15] != 0) {
          for (int byteIndex = 0; byteIndex < 4; byteIndex++) {
            targetHeader[descriptor + 12 + byteIndex] = (byte) (targetOffset >> (8 * byteIndex));
          }
        }
        targetOffset += field.getLength();
      }
      output.write(targetHeader);
      byte[] row = new byte[recordLength];
      for (long record = 1; record <= records; record++) {
        readFully(input, row, 0, row.length);
        if (row[0] != ' ' && row[0] != '*') {
          throw new IOException("Invalid DBF deletion flag at record " + record);
        }
        output.write(row[0]);
        int offset = 1;
        for (int column = 0; column < original.length; column++) {
          if (addEnabled && column == NodusC.DBF_IDX_ENABLED) {
            output.write('1');
          }
          if (changed[column]) {
            output.write(convert(row, offset, original[column], replacement[column], record));
          } else {
            output.write(row, offset, original[column].getLength());
          }
          offset += original[column].getLength();
        }
      }
      int trailing;
      while ((trailing = input.read()) != -1) {
        if (trailing != 0 && trailing != 26) {
          throw new IOException("Unexpected data after the declared DBF records");
        }
        output.write(trailing);
      }
    }
    originalHash = digest.digest();
  }

  private static byte[] convert(
      byte[] row, int offset, DBFField source, DBFField target, long record) throws IOException {
    String text = new String(row, offset, source.getLength(), StandardCharsets.US_ASCII).trim();
    byte[] result = new byte[target.getLength()];
    Arrays.fill(result, (byte) ' ');
    if (text.isEmpty()) {
      return result;
    }
    try {
      BigDecimal decimal = new BigDecimal(text);
      if (decimal.signum() != 0
          && (decimal.stripTrailingZeros().scale() > target.getDecimalCount()
              || (long) decimal.precision() - decimal.scale() > target.getLength())) {
        throw new ArithmeticException("value does not fit the required precision or width");
      }
      String number =
          decimal.setScale(target.getDecimalCount(), RoundingMode.UNNECESSARY).toPlainString();
      byte[] bytes = number.getBytes(StandardCharsets.US_ASCII);
      if (bytes.length > result.length) {
        throw new ArithmeticException("value does not fit the required width");
      }
      System.arraycopy(bytes, 0, result, result.length - bytes.length, bytes.length);
      return result;
    } catch (NumberFormatException | ArithmeticException invalid) {
      throw new IOException(
          "Field '"
              + source.getName()
              + "', record "
              + record
              + " cannot be converted without loss: "
              + text,
          invalid);
    }
  }

  void commit() throws IOException {
    if (!Arrays.equals(originalHash, hash(file))) {
      throw new IOException("DBF changed while awaiting confirmation: " + file.getFileName());
    }
    // No REPLACE_EXISTING: a previous backup must never be overwritten.
    Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
    if (!Arrays.equals(originalHash, hash(backup))) {
      throw new IOException("DBF changed while creating its backup");
    }
    save.commit();
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private static byte[] hash(Path file) throws IOException {
    MessageDigest digest = digest();
    try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
      byte[] buffer = new byte[8192];
      int length;
      while ((length = input.read(buffer)) != -1) {
        digest.update(buffer, 0, length);
      }
    }
    return digest.digest();
  }

  private static int unsignedShort(byte[] bytes, int offset) {
    return (bytes[offset] & 255) | (bytes[offset + 1] & 255) << 8;
  }

  private static void putShort(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte) value;
    bytes[offset + 1] = (byte) (value >> 8);
  }

  private static void readFully(InputStream input, byte[] target, int offset, int count)
      throws IOException {
    int read = input.readNBytes(target, offset, count);
    if (read != count) {
      throw new IOException("Truncated DBF file");
    }
  }

  @Override
  public void close() throws IOException {
    save.close();
  }
}
