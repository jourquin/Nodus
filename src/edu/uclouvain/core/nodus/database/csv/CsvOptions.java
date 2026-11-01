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

package edu.uclouvain.core.nodus.database.csv;

import edu.uclouvain.core.nodus.database.ImportProgress;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Per-command CSV encoding and delimiter, with bounded optional delimiter detection. */
public final class CsvOptions {
  private static final int SAMPLE_LIMIT = 65536;
  private static final int SAMPLE_RECORDS = 20;
  private final Charset charset;
  private final char delimiter;
  private final boolean automatic;

  /** Uses the historical Java default encoding and comma delimiter. */
  public CsvOptions() {
    this(null, ',', false);
  }

  /**
   * Validates per-command settings without opening a file or changing the database.
   *
   * @param encoding charset name, or null for Java's default encoding
   * @param delimiter one explicit CSV separator; ignored when automatic is true
   * @param automatic whether imports should detect comma, semicolon, tab or pipe
   * @throws IllegalArgumentException for an unsupported encoding or invalid delimiter
   */
  public CsvOptions(String encoding, char delimiter, boolean automatic) {
    try {
      charset = encoding == null ? Charset.defaultCharset() : Charset.forName(encoding);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException(
          "Invalid or unsupported CSV encoding: " + encoding, invalid);
    }
    char selected = automatic ? ',' : delimiter;
    if (selected == 0
        || selected == '"'
        || selected == '\r'
        || selected == '\n'
        || selected == '\u000B'
        || selected == '\f'
        || selected == '\u0085'
        || selected == '\u2028'
        || selected == '\u2029'
        || Character.isSurrogate(selected)) {
      throw new IllegalArgumentException(
          "CSV delimiter must be one character other than a quote, newline or NUL");
    }
    this.delimiter = selected;
    this.automatic = automatic;
  }

  /**
   * Returns the charset selected for this command.
   *
   * @return selected charset, including the Java default when no option was supplied
   */
  public Charset getCharset() {
    return charset;
  }

  /**
   * Returns the explicitly selected separator, or the fallback comma for automatic detection.
   *
   * @return explicit separator or comma fallback
   */
  public char getDelimiter() {
    return delimiter;
  }

  /**
   * Returns whether delimiter detection was requested.
   *
   * @return whether imports should sample the input to detect its delimiter
   */
  public boolean isAutomatic() {
    return automatic;
  }

  String errorMessage(Exception error, boolean exporting) {
    Throwable cause = error;
    for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
      if (cause instanceof CharacterCodingException) {
        return exporting
            ? "CSV export cannot represent the text using "
                + charset.name()
                + "; check the text or choose a compatible ENCODING"
            : "CSV input contains an invalid character sequence for "
                + charset.name()
                + "; specify the correct ENCODING";
      }
    }
    return error.getMessage() == null ? error.toString() : error.getMessage();
  }

  /**
   * Returns a strict decoder and removes an initial Unicode byte-order marker for both CSV modes.
   */
  PushbackReader reader(InputStream input) throws IOException {
    PushbackReader reader =
        new PushbackReader(
            new InputStreamReader(
                input,
                charset
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)),
            automatic ? SAMPLE_LIMIT + 1 : 1);
    try {
      int first = reader.read();
      if (first != -1 && first != '\uFEFF') {
        reader.unread(first);
      }
      return reader;
    } catch (IOException error) {
      try {
        reader.close();
      } catch (IOException closing) {
        error.addSuppressed(closing);
      }
      throw error;
    }
  }

  /** Peeks at a bounded prefix and pushes it back, without another pass over the source file. */
  CSVFormat format(PushbackReader reader, ImportProgress progress) throws Exception {
    if (!automatic) {
      return format(delimiter);
    }
    char[] buffer = new char[SAMPLE_LIMIT + 1];
    int length = 0;
    boolean eof = false;
    while (length < buffer.length) {
      progress.checkCancelled();
      int count = reader.read(buffer, length, buffer.length - length);
      if (count < 0) {
        eof = true;
        break;
      }
      length += count;
    }
    reader.unread(buffer, 0, length);
    String sample = new String(buffer, 0, Math.min(length, SAMPLE_LIMIT));
    List<Candidate> possible = new ArrayList<>();
    List<Candidate> consistent = new ArrayList<>();
    boolean hasRecord = false;
    for (char separator : new char[] {',', ';', '\t', '|'}) {
      progress.checkCancelled();
      Candidate candidate = candidate(sample, eof, separator);
      hasRecord |= candidate.records > 0;
      if (candidate.width > 1) {
        possible.add(candidate);
        if (candidate.consistent) {
          consistent.add(candidate);
        }
      }
    }
    List<Candidate> choices = consistent.isEmpty() ? possible : consistent;
    if (choices.size() == 1) {
      return format(choices.get(0).delimiter);
    }
    if (choices.isEmpty() && (hasRecord || sample.isEmpty())) {
      return format(',');
    }
    throw new IOException(
        choices.isEmpty()
            ? "Cannot detect the CSV delimiter from the first 64 Ki characters;"
                + " specify DELIMITER explicitly"
            : "Ambiguous CSV delimiter; specify DELIMITER explicitly");
  }

  private static CSVFormat format(char delimiter) {
    return CSVFormat.RFC4180.builder().setDelimiter(delimiter).get();
  }

  private static Candidate candidate(String sample, boolean eof, char delimiter) {
    List<Integer> widths = new ArrayList<>();
    boolean failed = false;
    boolean exhausted = false;
    try (CSVParser parser = format(delimiter).parse(new StringReader(sample))) {
      Iterator<CSVRecord> rows = parser.iterator();
      while (widths.size() <= SAMPLE_RECORDS) {
        if (!rows.hasNext()) {
          exhausted = true;
          break;
        }
        widths.add(rows.next().size());
      }
    } catch (IOException | RuntimeException invalid) {
      // An incomplete final sampled record must not invalidate earlier complete records.
      failed = true;
    }
    if (!eof && exhausted && !failed && !widths.isEmpty()) {
      widths.remove(widths.size() - 1);
    }
    int count = Math.min(SAMPLE_RECORDS, widths.size());
    int width = count == 0 ? 0 : widths.get(0);
    boolean consistent = true;
    for (int i = 1; i < count; i++) {
      consistent &= widths.get(i) == width;
    }
    return new Candidate(delimiter, width, count, consistent);
  }

  private static final class Candidate {
    final char delimiter;
    final int width;
    final int records;
    final boolean consistent;

    Candidate(char delimiter, int width, int records, boolean consistent) {
      this.delimiter = delimiter;
      this.width = width;
      this.records = records;
      this.consistent = consistent;
    }
  }
}
