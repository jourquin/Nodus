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

package edu.uclouvain.core.nodus.tools.console;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Buffers console output without waiting for Swing or using capacity-limited pipes.
 *
 * <p>Writers decode bytes incrementally, preserving characters split across writes, and coalesce
 * adjacent output of the same colour. The event thread takes bounded batches for display. All
 * output is retained until displayed, cleared or closed; there is no silent log truncation. No
 * Swing work is performed while holding this buffer's monitor.
 */
final class ConsoleOutputBuffer implements AutoCloseable {
  private static final int CHUNK_SIZE = 8192;
  private static final int BATCH_SIZE = 32768;
  private static final int MAX_BATCH_CHUNKS = 64;

  private final Charset charset;
  private final Deque<Chunk> pending = new ArrayDeque<>();
  private boolean closed;

  ConsoleOutputBuffer(Charset charset) {
    this.charset = charset;
  }

  /** Creates an independently decoded stream; closing it does not close the other stream. */
  PrintStream createStream(boolean error) {
    return new PrintStream(new DecodingStream(error), true, charset);
  }

  /** Takes a bounded amount of work so a burst of output cannot monopolize the event thread. */
  synchronized List<Chunk> drain() {
    List<Chunk> batch = new ArrayList<>();
    int size = 0;
    while (!pending.isEmpty() && batch.size() < MAX_BATCH_CHUNKS) {
      if (size + pending.peekFirst().text.length() > BATCH_SIZE) {
        break;
      }
      Chunk chunk = pending.removeFirst();
      size += chunk.text.length();
      batch.add(chunk);
    }
    return batch;
  }

  /** Takes the current backlog once, so Save also includes output awaiting its next repaint. */
  synchronized List<Chunk> drainAll() {
    List<Chunk> batch = new ArrayList<>(pending);
    pending.clear();
    return batch;
  }

  /** Drops pending output as well as the displayed text cleared by the caller. */
  synchronized void clear() {
    pending.clear();
  }

  /** Rejects further writes and releases queued text when the console closes. */
  @Override
  public synchronized void close() {
    closed = true;
    pending.clear();
  }

  /** Appends decoded characters while the caller holds this buffer's monitor. */
  private void append(CharBuffer characters, boolean error) {
    characters.flip();
    while (characters.hasRemaining()) {
      Chunk chunk = pending.peekLast();
      if (chunk == null || chunk.error != error || chunk.text.length() == CHUNK_SIZE) {
        chunk = new Chunk(error);
        pending.addLast(chunk);
      }
      int count = Math.min(characters.remaining(), CHUNK_SIZE - chunk.text.length());
      chunk.text.append(characters, 0, count);
      characters.position(characters.position() + count);
    }
    characters.clear();
  }

  /** A colour run; after removal from the queue its text is owned exclusively by the caller. */
  static final class Chunk {
    final boolean error;
    private final StringBuilder text = new StringBuilder();

    private Chunk(boolean error) {
      this.error = error;
    }

    String text() {
      return text.toString();
    }
  }

  /**
   * Keeps incomplete encoded characters between writes instead of decoding each byte separately.
   */
  private final class DecodingStream extends OutputStream {
    private final boolean error;
    private final CharsetDecoder decoder =
        charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    private final ByteBuffer bytes = ByteBuffer.allocate(CHUNK_SIZE);
    private final CharBuffer characters = CharBuffer.allocate(CHUNK_SIZE);
    private boolean streamClosed;

    private DecodingStream(boolean error) {
      this.error = error;
    }

    @Override
    public void write(int value) throws IOException {
      write(new byte[] {(byte) value}, 0, 1);
    }

    @Override
    public void write(byte[] source, int offset, int length) throws IOException {
      Objects.checkFromIndexSize(offset, length, source.length);
      synchronized (ConsoleOutputBuffer.this) {
        if (closed || streamClosed) {
          throw new IOException("Console output is closed");
        }
        while (length > 0) {
          int count = Math.min(length, bytes.remaining());
          bytes.put(source, offset, count);
          offset += count;
          length -= count;
          decode(false);
        }
      }
    }

    private void decode(boolean end) throws IOException {
      bytes.flip();
      CoderResult result;
      do {
        result = decoder.decode(bytes, characters, end);
        append(characters, error);
      } while (result.isOverflow());
      if (result.isError()) {
        result.throwException();
      }
      bytes.compact();
    }

    @Override
    public void close() throws IOException {
      synchronized (ConsoleOutputBuffer.this) {
        if (streamClosed) {
          return;
        }
        streamClosed = true;
        if (!closed) {
          decode(true);
          CoderResult result;
          do {
            result = decoder.flush(characters);
            append(characters, error);
          } while (result.isOverflow());
        }
      }
    }
  }
}
