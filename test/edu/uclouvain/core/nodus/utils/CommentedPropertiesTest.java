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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Uses java.util.Properties as an independent oracle for the property-file syntax. */
class CommentedPropertiesTest {
  @TestFactory
  List<DynamicTest> propertySyntaxRoundTripsThroughBothStandardAndCommentedReaders() {
    final List<DynamicTest> tests = new ArrayList<>();
    final List<String> documents =
        List.of(
            "# Coût et paramètres\n\nfirst=one\n! Keep this comment\nsecond:two\n",
            "escaped\\ key\\:\\==  leading\\ space\\nline\\tvalue\n",
            "path=C\\:\\\\Users\\\\sample\\\\folder\nquote=\\#\\!\\=\\:\\\\\n",
            "accent=café\ncity=\\u6771\\u4eac\nmusic=\\uD834\\uDD1E\n",
            "=empty key\nbare\nempty=\nduplicate=first\nduplicate=last\n",
            "form\\ffeed=value\\fend\n\t\f leading = value\n",
            "key\\\n  Part=value\\\n  continued\n",
            "comment=value\\\n  #literal continuation\n# comment ending with slash\\\nnext=value\n",
            "trailing=value\\",
            "\\",
            "continued=value\\\n\nnext=another\n");
    for (int i = 0; i < documents.size(); i++) {
      final String input = documents.get(i);
      tests.add(
          dynamicTest(
              "syntax " + i,
              () -> {
                final Properties expected = standard(input);
                final CommentedProperties actual = load(input);
                assertEquals(expected, actual);
                final String saved = store(actual);
                assertEquals(expected, standard(saved));
                assertEquals(expected, load(saved));
                assertEquals(
                    saved, store(load(saved)), "Repeated saves must not accumulate metadata");
              }));
    }
    for (String newline : List.of("\n", "\r", "\r\n")) {
      tests.add(
          dynamicTest(
              "continuation newline " + newline.length() + newline.charAt(0),
              () -> {
                final String input =
                    "# Header" + newline + "key=one\\" + newline + " two" + newline;
                final CommentedProperties properties = load(input);
                assertEquals(standard(input), properties);
                assertEquals("onetwo", standard(store(properties)).getProperty("key"));
              }));
    }
    return tests;
  }

  @Test
  void mutationsKeepCommentsAndPersistNewKeysWithoutResurrectingRemovedKeys() throws Exception {
    final CommentedProperties properties = load("# Header\nold=remove\n\n! Footer\nkeep=before\n");
    properties.remove("old");
    properties.setProperty("keep", "after");
    properties.setProperty("new key", "  café 東京\nsecond line");
    properties.putAll(Map.of("", "empty key", "putAll", "present"));
    final String saved = store(properties);
    assertTrue(saved.startsWith("# Header\n"));
    assertTrue(saved.contains("\n\n! Footer\n"));
    assertFalse(saved.contains("old="));
    assertEquals(properties, standard(saved));
    assertEquals(properties, load(saved));
  }

  @Test
  void readerAndWriterOverloadsKeepUnicodeCommentsAndCallerOwnedResourcesOpen() throws Exception {
    final TrackingReader reader = new TrackingReader("# 東京\nkey=東京\n");
    final CommentedProperties properties = new CommentedProperties();
    properties.load(reader);
    assertFalse(reader.closed);
    final TrackingWriter writer = new TrackingWriter();
    properties.store(writer, "ignored");
    assertFalse(writer.closed);
    assertTrue(writer.toString().startsWith("# 東京\n"));
    final Properties expected = new Properties();
    expected.load(new StringReader(writer.toString()));
    assertEquals("東京", expected.getProperty("key"));
    final String saved = store(properties);
    assertTrue(saved.startsWith("# \\u6771\\u4eac\n"));
    assertEquals(expected, standard(saved));
    assertEquals(saved, store(load(saved)));
  }

  @TestFactory
  List<DynamicTest> malformedUnicodeEscapesAreRejectedLikeStandardProperties() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String document : List.of("key=\\u12", "key=\\uZZZZ", "\\u12=value", "\\uZZZZ=value")) {
      tests.add(
          dynamicTest(
              document,
              () -> {
                assertThrows(IllegalArgumentException.class, () -> standard(document));
                assertThrows(IllegalArgumentException.class, () -> load(document));
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> outputWriteAndFlushFailuresArePropagatedWithoutClosingTheCallersStream() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (String phase : List.of("first write", "later write", "flush")) {
      tests.add(
          dynamicTest(
              phase,
              () -> {
                final CommentedProperties properties = load("key=" + "x".repeat(30000) + "\n");
                final FailingOutput output = new FailingOutput(phase);
                final IOException failure =
                    assertThrows(IOException.class, () -> properties.store(output, null));
                assertTrue(failure.getMessage().contains(phase));
                assertFalse(output.closed);
                if (phase.equals("later write")) {
                  assertTrue(output.written > 0);
                }
              }));
    }
    return tests;
  }

  @Test
  void inputAndOutputStreamsRemainOwnedByTheCallerOnSuccessAndFailedReads() throws Exception {
    final ByteArrayInputStream input =
        new ByteArrayInputStream("key=value\n".getBytes(StandardCharsets.ISO_8859_1)) {
          @Override
          public void close() {
            throw new AssertionError("Caller owns input");
          }
        };
    final CommentedProperties properties = new CommentedProperties();
    properties.load(input);
    final ByteArrayOutputStream output =
        new ByteArrayOutputStream() {
          @Override
          public void close() {
            throw new AssertionError("Caller owns output");
          }
        };
    properties.store(output, null);
    assertEquals(properties, standard(output.toString(StandardCharsets.ISO_8859_1)));
    final java.io.InputStream failing =
        new java.io.InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("read failed");
          }

          @Override
          public void close() {
            throw new AssertionError("Caller owns input");
          }
        };
    assertThrows(IOException.class, () -> properties.load(failing));
  }

  @Test
  void clearDiscardsOldCommentsAndNewPropertiesStillSave() throws Exception {
    final CommentedProperties properties = load("# Old project\nold=value\n");
    properties.clear();
    properties.setProperty("new", "value");
    final String saved = store(properties);
    assertFalse(saved.contains("Old project"));
    assertEquals(Map.of("new", "value"), standard(saved));
  }

  private static CommentedProperties load(String document) throws IOException {
    final CommentedProperties properties = new CommentedProperties();
    properties.load(new ByteArrayInputStream(document.getBytes(StandardCharsets.ISO_8859_1)));
    return properties;
  }

  private static Properties standard(String document) throws IOException {
    final Properties properties = new Properties();
    properties.load(new ByteArrayInputStream(document.getBytes(StandardCharsets.ISO_8859_1)));
    return properties;
  }

  private static String store(CommentedProperties properties) throws IOException {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    properties.store(output, "ignored");
    return output.toString(StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
  }

  private static final class TrackingReader extends StringReader {
    boolean closed;

    TrackingReader(String text) {
      super(text);
    }

    @Override
    public void close() {
      closed = true;
      super.close();
    }
  }

  private static final class TrackingWriter extends StringWriter {
    boolean closed;

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class FailingOutput extends OutputStream {
    final String phase;
    int written;
    boolean closed;

    FailingOutput(String phase) {
      this.phase = phase;
    }

    @Override
    public void write(int value) throws IOException {
      if (phase.equals("first write") || (phase.equals("later write") && written >= 9000)) {
        throw new IOException(phase);
      }
      written++;
    }

    @Override
    public void flush() throws IOException {
      if (phase.equals("flush")) {
        throw new IOException(phase);
      }
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
