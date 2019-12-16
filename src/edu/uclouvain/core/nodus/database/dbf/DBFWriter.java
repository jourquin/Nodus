/**
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.Calendar;

/**
 * Used to write database (DBF) files. <br>
 * Create a DBFWriter passing a file name and a list of fields, then add the records one by one, and
 * close it. Make sure you always close your DBF files, even if there is an error writing some of
 * the records.
 *
 * <p>Adapted from original free code by SV Consulting (not existing anymore).
 *
 * @author Bart Jourquin
 */
public class DBFWriter {

  private Charset dbfEncoding = null;

  private DBFField[] fields = null;

  private String fileName = null;

  private int recCount = 0;

  private BufferedOutputStream stream = null;

  private boolean succeeded;

  /**
   * Opens an output stream for writing.
   *
   * @param outputstream The OutputStream used to write the file.
   * @param dbfFields The structure of a record.
   * @throws DBFException On error
   */
  public DBFWriter(OutputStream outputstream, DBFField[] dbfFields) throws DBFException {
    stream = null;
    recCount = 0;
    fields = null;
    fileName = null;
    dbfEncoding = null;
    init(outputstream, dbfFields);
  }

  /**
   * Opens a DBF file for writing.
   *
   * @param fileName The name of the output file.
   * @param dbfFields The structure of a record.
   * @throws DBFException On error
   */
  public DBFWriter(String fileName, DBFField[] dbfFields) throws DBFException {
    stream = null;
    recCount = 0;
    fields = null;
    dbfEncoding = null;
    this.fileName = fileName;

    try {
      init(new FileOutputStream(fileName), dbfFields);
    } catch (FileNotFoundException filenotfoundexception) {
      succeeded = false;
      throw new DBFException(filenotfoundexception);
    }
  }

  /**
   * Opens a DBF file for writing.
   *
   * @param fileName The name of the output file.
   * @param dbfFields The structure of a record.
   * @param encoding The Charset used for encoding.
   * @throws DBFException On error
   */
  public DBFWriter(String fileName, DBFField[] dbfFields, Charset encoding) throws DBFException {
    stream = null;
    recCount = 0;
    fields = null;
    dbfEncoding = null;
    this.fileName = fileName;

    try {
      dbfEncoding = encoding;
      init(new FileOutputStream(fileName), dbfFields);
    } catch (FileNotFoundException filenotfoundexception) {
      succeeded = false;
      throw new DBFException(filenotfoundexception);
    }
  }

  /**
   * Writes a record to the DBF file.
   *
   * @param record The array of objects to be written.
   * @throws DBFException On error
   */
  public void addRecord(Object[] record) throws DBFException {
    if (record.length != fields.length) {
      succeeded = false;
      throw new DBFException(
          "Error adding record: Wrong number of values. Expected "
              + fields.length
              + ", got "
              + record.length
              + ".");
    }

    int i = 0;

    for (DBFField element : fields) {
      i += element.getLength();
    }

    byte[] abyte0 = new byte[i];
    int k = 0;

    for (int l = 0; l < fields.length; l++) {
      String s = fields[l].format(record[l]);
      byte[] abyte1;

      if (dbfEncoding != null) {
        abyte1 = s.getBytes(dbfEncoding);
      } else {
        abyte1 = s.getBytes();
      }

      for (int i1 = 0; i1 < fields[l].getLength(); i1++) {
        abyte0[k + i1] = abyte1[i1];
      }

      k += fields[l].getLength();
    }

    try {
      stream.write(32);
      stream.write(abyte0, 0, abyte0.length);
      stream.flush();
    } catch (IOException ioexception) {
      succeeded = false;
      throw new DBFException(ioexception);
    }

    recCount++;
  }

  /**
   * Closes the DBF file.
   *
   * @throws DBFException On error
   */
  public void close() throws DBFException {
    try {
      stream.write(26);
      stream.close();

      RandomAccessFile randomaccessfile = new RandomAccessFile(fileName, "rw");
      randomaccessfile.seek(4L);

      byte[] abyte0 = new byte[4];
      abyte0[0] = (byte) (recCount % 256);
      abyte0[1] = (byte) (recCount / 256 % 256);
      abyte0[2] = (byte) (recCount / 65536 % 256);
      abyte0[3] = (byte) (recCount / 16777216 % 256);
      randomaccessfile.write(abyte0, 0, abyte0.length);
      randomaccessfile.close();
      succeeded = true;
    } catch (IOException ioexception) {
      succeeded = false;
      throw new DBFException(ioexception);
    }
  }

  /**
   * Initializes the writer.
   *
   * @param outputstream OutputStream
   * @param ajdbfield JDBField[]
   * @throws DBFException On error
   */
  private void init(OutputStream outputstream, DBFField[] ajdbfield) throws DBFException {
    fields = ajdbfield;

    try {
      stream = new BufferedOutputStream(outputstream);
      writeHeader();

      for (DBFField element : ajdbfield) {
        writeFieldHeader(element);
      }

      stream.write(13);
      stream.flush();
    } catch (Exception exception) {
      succeeded = false;
      throw new DBFException(exception);
    }
  }

  /**
   * Checks if the file was successfully exported.
   *
   * @return True on success.
   */
  public boolean isExportedWithSuccess() {
    return succeeded;
  }

  /**
   * Writes .dbf field header.
   *
   * @param jdbfield JDBField
   * @throws IOException On error
   */
  private void writeFieldHeader(DBFField jdbfield) throws IOException {
    byte[] abyte0 = new byte[16];
    String s = jdbfield.getName();
    int i = s.length();

    if (i > 10) {
      i = 10;
    }

    for (int j = 0; j < i; j++) {
      abyte0[j] = (byte) s.charAt(j);
    }

    for (int k = i; k <= 10; k++) {
      abyte0[k] = 0;
    }

    abyte0[11] = (byte) jdbfield.getType();
    abyte0[12] = 0;
    abyte0[13] = 0;
    abyte0[14] = 0;
    abyte0[15] = 0;
    stream.write(abyte0, 0, abyte0.length);

    for (int l = 0; l < 16; l++) {
      abyte0[l] = 0;
    }

    abyte0[0] = (byte) jdbfield.getLength();
    abyte0[1] = (byte) jdbfield.getDecimalCount();
    stream.write(abyte0, 0, abyte0.length);
  }

  /**
   * Writes .dbf header.
   *
   * @throws IOException On error
   */
  private void writeHeader() throws IOException {
    byte[] abyte0 = new byte[16];
    abyte0[0] = 3;

    Calendar calendar = Calendar.getInstance();
    abyte0[1] = (byte) (calendar.get(1) - 1900);
    abyte0[2] = (byte) calendar.get(2);
    abyte0[3] = (byte) calendar.get(5);
    abyte0[4] = 0;
    abyte0[5] = 0;
    abyte0[6] = 0;
    abyte0[7] = 0;

    int i = (fields.length + 1) * 32 + 1;
    abyte0[8] = (byte) (i % 256);
    abyte0[9] = (byte) (i / 256);

    int j = 1;

    for (DBFField element : fields) {
      j += element.getLength();
    }

    abyte0[10] = (byte) (j % 256);
    abyte0[11] = (byte) (j / 256);
    abyte0[12] = 0;
    abyte0[13] = 0;
    abyte0[14] = 0;
    abyte0[15] = 0;
    stream.write(abyte0, 0, abyte0.length);

    for (int l = 0; l < 16; l++) {
      abyte0[l] = 0;
    }

    stream.write(abyte0, 0, abyte0.length);
  }
}
