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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Used to read database (DBF) files.
 *
 * <p>Create a DBFReader object passing a file name to be opened, and use hasNextRecord and
 * nextRecord functions to iterate through the records of the file. <br>
 * The getFieldCount and getField methods allow you to find out what are the fields of the database
 * file.
 *
 * <p>Adapted from original free code by SV Consulting (not existing anymore).
 *
 * @author Bart Jourquin
 */
public class DBFReader {

  private DBFField[] fields = null;

  private boolean isOpen;

  private byte[] nextRecord = null;

  private DataInputStream stream = null;

  private boolean standardDouble = true;

  private Charset encoding = null;

  /**
   * Opens a stream, containing DBF for reading.
   *
   * @param inputstream The InputStream corresponding the the file to read.
   * @throws DBFException On error
   */
  public DBFReader(InputStream inputstream) throws DBFException {
    this(inputstream, null, true);
  }

  /**
   * Opens a stream, containing DBF for reading.
   *
   * @param inputstream The InputStream corresponding the the file to read.
   * @param encoding The Charset used for string encoding.
   * @throws DBFException On error
   */
  public DBFReader(InputStream inputstream, Charset encoding) throws DBFException {
    this(inputstream, encoding, true);
  }

  /**
   * Opens a stream, containing DBF for reading.
   *
   * @param inputstream The InputStream corresponding the the file to read.
   * @param encoding The Charset used for string encoding.
   * @param standardDouble If false, the reader will try to load files with non standard format for
   *     double values, as found in some shapefiles.
   * @throws DBFException On error
   */
  public DBFReader(InputStream inputstream, Charset encoding, boolean standardDouble)
      throws DBFException {
    stream = null;
    fields = null;
    nextRecord = null;
    this.standardDouble = standardDouble;
    this.encoding = encoding;
    init(inputstream);
  }

  /**
   * Opens a DBF file for reading.
   *
   * @param fileName The name of the file to read.
   * @throws DBFException On error
   */
  public DBFReader(String fileName) throws DBFException {
    this(fileName, null, true);
  }

  /**
   * Opens a DBF file for reading.
   *
   * @param fileName The name of the file to read.
   * @param encoding The Charset used for string encoding.
   * @throws DBFException On error
   */
  public DBFReader(String fileName, Charset encoding) throws DBFException {
    this(fileName, encoding, true);
  }

  /**
   * Opens a DBF file for reading.
   *
   * @param fileName The name of the file to read.
   * @param encoding The Charset used for string encoding.
   * @param standardDouble If false, the reader will try to load files with non standard format for
   *     double values, as found in some shapefiles.
   * @throws DBFException On error
   */
  public DBFReader(String fileName, Charset encoding, boolean standardDouble) throws DBFException {
    stream = null;
    fields = null;
    nextRecord = null;
    isOpen = false;
    this.standardDouble = standardDouble;
    this.encoding = encoding;

    try {
      init(new FileInputStream(fileName.trim()));
    } catch (FileNotFoundException filenotfoundexception) {
      throw new DBFException(filenotfoundexception);
    }
  }

  /**
   * Returns a field at a specified position.
   *
   * @param index Index of the field in the record.
   * @return JDBField
   */
  public DBFField getField(int index) {
    return fields[index];
  }

  /**
   * Returns the field count of the database file.
   *
   * @return The number of fields in the record.
   */
  public int getFieldCount() {
    return fields.length;
  }

  /**
   * Checks to see if there are more records in the file.
   *
   * @return True if more records can be read.
   */
  public boolean hasNextRecord() {
    return nextRecord != null;
  }

  /**
   * Initializes the reader.
   *
   * @param inputstream InputStream
   * @throws DBFException On error
   */
  private void init(InputStream inputstream) throws DBFException {
    try {

      stream = new DataInputStream(inputstream);

      int i = readHeader();
      fields = new DBFField[i];

      int j = 1;

      for (int k = 0; k < i; k++) {
        fields[k] = readFieldHeader();
        j += fields[k].getLength();
      }

      if (stream.read() < 1) {
        throw new DBFException("Unexpected end of file reached.");
      }

      nextRecord = new byte[j];

      try {
        stream.readFully(nextRecord);
      } catch (EOFException eofexception) {
        nextRecord = null;
        stream.close();
      }

      isOpen = true;
    } catch (IOException ioexception) {
      throw new DBFException(ioexception);
    }
  }

  /**
   * Checks if the reader is open.
   *
   * @return True if the reader is open.
   */
  public boolean isOpen() {
    return isOpen;
  }

  /**
   * Returns an array of objects, representing one record in the database file.
   *
   * @return An array of Objects.
   * @throws DBFException On error
   */
  public Object[] nextRecord() throws DBFException {
    if (!hasNextRecord()) {
      throw new DBFException("No more records available.");
    }

    Object[] aobj = new Object[fields.length];
    int i = 1;

    for (int j = 0; j < aobj.length; j++) {
      int k = fields[j].getLength();

      StringBuffer stringbuffer = new StringBuffer(k);

      // Handle string encoding, if any
      if (fields[j].getType() == 'C' && encoding != null) {
        byte[] fieldBytes = new byte[k];
        System.arraycopy(nextRecord, i, fieldBytes, 0, k);
        String s = new String(fieldBytes, encoding);
        stringbuffer.append(s);
      } else {
        stringbuffer.append(new String(nextRecord, i, k));
      }

      aobj[j] = fields[j].parse(stringbuffer.toString());

      i += fields[j].getLength();
    }

    try {
      stream.readFully(nextRecord);
    } catch (EOFException eofexception) {
      nextRecord = null;
    } catch (IOException ioexception) {
      throw new DBFException(ioexception);
    }

    return aobj;
  }

  /**
   * Reads field header.
   *
   * @return JDBField
   * @throws IOException On error
   * @throws DBFException On error
   */
  private DBFField readFieldHeader() throws IOException, DBFException {
    byte[] abyte0 = new byte[16];

    try {
      stream.readFully(abyte0);
    } catch (EOFException eofexception) {
      throw new DBFException("Unexpected end of file reached.");
    }

    StringBuffer stringbuffer = new StringBuffer(10);

    for (int i = 0; i < 10; i++) {
      if (abyte0[i] == 0) {
        break;
      }

      stringbuffer.append((char) abyte0[i]);
    }

    final char c = (char) abyte0[11];

    try {
      stream.readFully(abyte0);
    } catch (EOFException eofexception1) {
      throw new DBFException("Unexpected end of file reached.");
    }

    int j = abyte0[0];
    int k = abyte0[1];

    if (j < 0) {
      j += 256;
    }

    if (k < 0) {
      k += 256;
    }

    return new DBFField(stringbuffer.toString(), c, j, k, standardDouble);
  }

  /**
   * Reads header.
   *
   * @return int
   * @throws IOException On error
   * @throws DBFException On error
   */
  private int readHeader() throws IOException, DBFException {
    byte[] abyte0 = new byte[16];

    try {
      stream.readFully(abyte0);
    } catch (EOFException eofexception) {
      throw new DBFException("Unexpected end of file reached.");
    }

    int i = abyte0[8];

    if (i < 0) {
      i += 256;
    }

    i += 256 * abyte0[9];
    i = --i / 32;
    i--;

    try {
      stream.readFully(abyte0);
    } catch (EOFException eofexception1) {
      throw new DBFException("Unexpected end of file reached.");
    }

    return i;
  }
}
