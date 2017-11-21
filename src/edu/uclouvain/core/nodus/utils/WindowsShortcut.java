/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

/**
 * Represents a Windows shortcut (typically visible to Java only as a '.lnk' file). <br>
 * Based on code retrieved on 2011-09-23 from
 * http://stackoverflow.com/questions/309495/windows-shortcut-lnk-parser-in-java/672775#672775. <br>
 *
 * @author Bart Jourquin
 */
public class WindowsShortcut {

  private boolean isDirectory;

  private boolean isLocal;

  private String realFile;

  /**
   * Converts two bytes into a dword. This is little endian because it's for an Intel only OS.
   *
   * @param bytes The bytes to convert.
   * @param offset The offset to start at.
   * @return The word value (32 bits value).
   */
  private static int bytesToDword(byte[] bytes, int offset) {
    return bytesToWord(bytes, offset + 2) << 16 | bytesToWord(bytes, offset);
  }

  /**
   * Converts two bytes into word. This is little endian because it's for an Intel only OS.
   *
   * @param bytes The bytes to convert.
   * @param offset The offset to start at.
   * @return The word value (16 bits value).
   */
  private static int bytesToWord(byte[] bytes, int offset) {
    return (bytes[offset + 1] & 0xff) << 8 | bytes[offset] & 0xff;
  }

  /**
   * Gets all the bytes from an InputStream.
   *
   * @param in the InputStream from which to read bytes
   * @return array of all the bytes contained in 'in'
   * @throws IOException if an IOException is encountered while reading the data from the
   *     InputStream
   */
  private static byte[] getBytes(InputStream in) throws IOException {
    return getBytes(in, null);
  }

  /**
   * Gets up to max bytes from an InputStream.
   *
   * @param in the InputStream from which to read bytes
   * @param max maximum number of bytes to read
   * @return array of all the bytes contained in 'in'
   * @throws IOException if an IOException is encountered while reading the data from the
   *     InputStream
   */
  private static byte[] getBytes(InputStream in, Integer max) throws IOException {
    // read the entire file into a byte buffer
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    byte[] buff = new byte[256];
    while (max == null || max > 0) {
      int n = in.read(buff);
      if (n == -1) {
        break;
      }
      bout.write(buff, 0, n);
      if (max != null) {
        max -= n;
      }
    }
    in.close();
    return bout.toByteArray();
  }

  /**
   * Returns a string retrieved from an array of bytes.
   *
   * @param bytes The arrays of bytes to scan.
   * @param offset The offset to start at.
   * @return The retrieve string.
   */
  private static String getNullDelimitedString(byte[] bytes, int offset) {
    int len = 0;
    // count bytes until the null character (0)
    while (true) {
      if (bytes[offset + len] == 0) {
        break;
      }
      len++;
    }
    return new String(bytes, offset, len);
  }

  /**
   * Returns true if the "shortcut marker" is present in the content of the retrieved link.
   *
   * @param link The array of bytes read from the file representing a shortcut.
   * @return True if the marker is present.
   */
  private static boolean isMagicPresent(byte[] link) {
    final int magic = 0x0000004C;
    final int magicOffset = 0x00;
    return link.length >= 32 && bytesToDword(link, magicOffset) == magic;
  }

  /**
   * Provides a quick test to see if this could be a valid link ! If you try to instantiate a new
   * WindowShortcut and the link is not valid, Exceptions may be thrown and Exceptions are extremely
   * slow to generate, therefore any code needing to loop through several files should first check
   * this.
   *
   * @param file the potential link
   * @return true if may be a link, false otherwise
   * @throws IOException if an IOException is thrown while reading from the file
   */
  public static boolean isPotentialValidLink(File file) throws IOException {
    final int minimumLength = 0x64;
    InputStream fis = new FileInputStream(file);
    boolean isPotentiallyValid = false;
    try {
      isPotentiallyValid =
          file.isFile()
              && file.getName().toLowerCase().endsWith(".lnk")
              && fis.available() >= minimumLength
              && isMagicPresent(getBytes(fis, 32));
    } finally {
      fis.close();
    }
    return isPotentiallyValid;
  }

  /**
   * Creates a new Windows shortcut.
   *
   * @param file The file associated to the shortcut in the Windows file system.
   * @throws IOException On error
   * @throws ParseException On error
   */
  public WindowsShortcut(File file) throws IOException, ParseException {
    InputStream in = new FileInputStream(file);
    try {
      parseLink(getBytes(in));
    } finally {
      in.close();
    }
  }

  /**
   * Get real file name.
   *
   * @return The name of the filesystem object pointed to by this shortcut
   */
  public String getRealFilename() {
    return realFile;
  }

  /**
   * Tests if the shortcut points to a directory.
   *
   * @return True if the 'directory' bit is set in this shortcut, false otherwise
   */
  public boolean isDirectory() {
    return isDirectory;
  }

  /**
   * Tests if the shortcut points to a local resource.
   *
   * @return True if the 'local' bit is set in this shortcut, false otherwise
   */
  public boolean isLocal() {
    return isLocal;
  }

  /**
   * Gobbles up link data by parsing it and storing info in member fields
   *
   * @param link All the bytes from the .lnk file
   */
  private void parseLink(byte[] link) throws ParseException {
    try {
      if (!isMagicPresent(link)) {
        throw new ParseException("Invalid shortcut; magic is missing", 0);
      }

      // get the flags byte
      byte flags = link[0x14];

      // get the file attributes byte
      final int fileAttsOffset = 0x18;
      byte fileAtts = link[fileAttsOffset];
      byte isDirMask = (byte) 0x10;
      if ((fileAtts & isDirMask) > 0) {
        isDirectory = true;
      } else {
        isDirectory = false;
      }

      // if the shell settings are present, skip them
      final int shellOffset = 0x4c;
      final byte hasShellMask = (byte) 0x01;
      int shellLen = 0;
      if ((flags & hasShellMask) > 0) {
        // the plus 2 accounts for the length marker itself
        shellLen = bytesToWord(link, shellOffset) + 2;
      }

      // get to the file settings
      int fileStart = 0x4c + shellLen;

      final int fileLocationInfoFlagOffsetOffset = 0x08;
      int fileLocationInfoFlag = link[fileStart + fileLocationInfoFlagOffsetOffset];
      isLocal = (fileLocationInfoFlag & 2) == 0;
      final int basenameOoffsetOoffset = 0x10;
      final int networkVolumeTableOffsetOffset = 0x14;
      final int finalnameOffsetOffset = 0x18;
      int finalNameOffset = link[fileStart + finalnameOffsetOffset] + fileStart;
      String finalname = getNullDelimitedString(link, finalNameOffset);
      if (isLocal) {
        int baseNameOffset = link[fileStart + basenameOoffsetOoffset] + fileStart;
        String basename = getNullDelimitedString(link, baseNameOffset);
        realFile = basename + finalname;
      } else {
        int networkVolumeTableOffset = link[fileStart + networkVolumeTableOffsetOffset] + fileStart;
        int shareNameOffsetOffset = 0x08;
        int shareNameOffset =
            link[networkVolumeTableOffset + shareNameOffsetOffset] + networkVolumeTableOffset;
        String shareName = getNullDelimitedString(link, shareNameOffset);
        realFile = shareName + "\\" + finalname;
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new ParseException("Could not be parsed, probably not a valid WindowsShortcut", 0);
    }
  }
}
