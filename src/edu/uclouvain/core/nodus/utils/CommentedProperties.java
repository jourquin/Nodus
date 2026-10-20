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

package edu.uclouvain.core.nodus.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.FilterReader;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Properties with the standard Java syntax, retaining comment and blank-line order on saving.
 *
 * @author
 *     https://www.dreamincode.net/forums/topic/53734-java-code-to-modify-properties-file-and-preserve-comments/
 */
public class CommentedProperties extends Properties {
  private static final long serialVersionUID = 4132817967592863730L;

  /** Comment/blank lines, or an empty placeholder for a property ("=" for an empty key). */
  public Vector<String> lineData = new Vector<>(0, 1);

  /** Keys parallel to lineData; an empty string also marks comment and blank lines. */
  public Vector<String> keyData = new Vector<>(0, 1);

  /** Default constructor. */
  public CommentedProperties() {}

  private static final class NonClosingInputStream extends FilterInputStream {
    NonClosingInputStream(InputStream input) {
      super(input);
    }

    @Override
    public void close() {
      /* The caller owns the stream. */
    }
  }

  private static final class NonClosingOutputStream extends FilterOutputStream {
    NonClosingOutputStream(OutputStream output) {
      super(output);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      out.write(bytes, offset, length);
    }

    @Override
    public void close() throws IOException {
      flush();
    }
  }

  private static final class NonClosingReader extends FilterReader {
    NonClosingReader(Reader input) {
      super(input);
    }

    @Override
    public void close() {
      /* The caller owns the reader. */
    }
  }

  private static final class NonClosingWriter extends FilterWriter {
    NonClosingWriter(Writer output) {
      super(output);
    }

    @Override
    public void close() throws IOException {
      flush();
    }
  }

  /**
   * Loads ISO-8859-1 properties without closing the caller's stream.
   *
   * @param input property file stream
   * @throws IOException on a read failure
   * @throws IllegalArgumentException on a malformed Unicode escape
   */
  @Override
  public synchronized void load(InputStream input) throws IOException {
    try (Reader reader =
        new InputStreamReader(new NonClosingInputStream(input), StandardCharsets.ISO_8859_1)) {
      load(reader);
    }
  }

  /**
   * Loads Java property records, delegating decoding and validation to the standard parser.
   *
   * @param input caller-owned reader
   * @throws IOException on a read failure
   * @throws IllegalArgumentException on a malformed Unicode escape
   */
  @Override
  public synchronized void load(Reader input) throws IOException {
    try (BufferedReader reader = new BufferedReader(new NonClosingReader(input))) {
      String line;
      while ((line = reader.readLine()) != null) {
        int start = 0;
        while (start < line.length()
            && (line.charAt(start) == ' '
                || line.charAt(start) == '\t'
                || line.charAt(start) == '\f')) {
          start++;
        }
        if (start == line.length() || line.charAt(start) == '#' || line.charAt(start) == '!') {
          addLine(line);
          continue;
        }
        final StringBuilder record = new StringBuilder(line);
        while (continues(line)) {
          line = reader.readLine();
          if (line == null) {
            break;
          }
          record.append('\n').append(line);
        }
        final Properties entry = new Properties();
        entry.load(new StringReader(record.toString()));
        if (!entry.isEmpty()) {
          final String key = entry.stringPropertyNames().iterator().next();
          add(key, entry.getProperty(key));
        }
      }
    }
  }

  private static boolean continues(String line) {
    int slashes = 0;
    for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
      slashes++;
    }
    return slashes % 2 == 1;
  }

  /**
   * Stores comments and current entries, propagating failures and keeping the stream open.
   *
   * @param output caller-owned ISO-8859-1 output stream
   * @param header ignored, since stored comments already supply the header
   * @throws IOException on a write or flush failure
   */
  @Override
  public synchronized void store(OutputStream output, String header) throws IOException {
    try (Writer writer =
        new OutputStreamWriter(new NonClosingOutputStream(output), StandardCharsets.ISO_8859_1)) {
      writeContent(writer, true);
    }
  }

  /**
   * Stores existing entries in their recorded order and appends newly added keys deterministically.
   * Removed keys are omitted; comments and blank lines remain in place.
   *
   * @param output caller-owned writer
   * @param header ignored, since stored comments already supply the header
   * @throws IOException on a write or flush failure
   */
  @Override
  public synchronized void store(Writer output, String header) throws IOException {
    writeContent(output, false);
  }

  private void writeContent(Writer output, boolean escapeComments) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new NonClosingWriter(output))) {
      final Set<String> written = new HashSet<>();
      for (int i = 0; i < lineData.size(); i++) {
        final String key = keyData.get(i);
        if (!key.isEmpty() || "=".equals(lineData.get(i))) {
          if (containsKey(key)) {
            writeEntry(writer, key);
            written.add(key);
          }
        } else {
          final String line = lineData.get(i);
          for (int j = 0; j < line.length(); j++) {
            final char character = line.charAt(j);
            if (escapeComments && character > 0xff) {
              final String hex = Integer.toHexString(character);
              writer.write("\\u0000".substring(0, 6 - hex.length()));
              writer.write(hex);
            } else {
              writer.write(character);
            }
          }
          writer.newLine();
        }
      }
      final Set<String> remaining = new TreeSet<>();
      for (Object key : keySet()) {
        if (!written.contains(key)) {
          remaining.add((String) key);
        }
      }
      for (String key : remaining) {
        writeEntry(writer, key);
      }
    }
  }

  private void writeEntry(BufferedWriter writer, String key) throws IOException {
    final StringBuffer line = new StringBuffer();
    formatForOutput(key, line, true);
    line.append('=');
    formatForOutput((String) get(key), line, false);
    writer.write(line.toString());
    writer.newLine();
  }

  private void formatForOutput(String str, StringBuffer buffer, boolean key) {
    if (key) {
      buffer.setLength(0);
      buffer.ensureCapacity(str.length());
    } else {
      buffer.ensureCapacity(buffer.length() + str.length());
    }
    boolean head = true;
    int size = str.length();
    for (int i = 0; i < size; i++) {
      char c = str.charAt(i);
      switch (c) {
        case '\n':
          buffer.append("\\n");
          break;
        case '\r':
          buffer.append("\\r");
          break;
        case '\t':
          buffer.append("\\t");
          break;
        case ' ':
          buffer.append(head ? "\\ " : " ");
          break;
        case '\\':
        case '!':
        case '#':
        case '=':
        case ':':
          buffer.append('\\').append(c);
          break;
        default:
          if (c < ' ' || c > '~') {
            String hex = Integer.toHexString(c);
            buffer.append("\\u0000".substring(0, 6 - hex.length()));
            buffer.append(hex);
          } else {
            buffer.append(c);
          }
      }
      if (c != ' ') {
        head = key;
      }
    }
  }

  /**
   * Adds an entry at an explicit position in the saved order.
   *
   * @param keyString property key
   * @param value property value
   */
  public synchronized void add(String keyString, String value) {
    put(keyString, value);
    lineData.add(keyString.isEmpty() ? "=" : "");
    keyData.add(keyString);
  }

  /**
   * Adds a comment or a blank line at the end of the recorded order.
   *
   * @param line comment or blank line
   */
  public synchronized void addLine(String line) {
    lineData.add(line);
    keyData.add("");
  }

  /** Clears both the values and the saved comment/entry order. */
  @Override
  public synchronized void clear() {
    super.clear();
    lineData.clear();
    keyData.clear();
  }
}
