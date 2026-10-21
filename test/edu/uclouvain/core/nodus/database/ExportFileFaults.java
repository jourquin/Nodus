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

package edu.uclouvain.core.nodus.database;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared real-file fault injection; callers capturing console streams must hold both stream locks.
 */
public final class ExportFileFaults extends TableExport {
  public Path temporary;
  public boolean closed;
  public long bytes;
  private final String phase;

  public ExportFileFaults(String phase) {
    this.phase = phase;
  }

  @Override
  protected OutputStream open(Path staged) throws IOException {
    temporary = staged;
    if (phase.equals("open")) {
      throw failure();
    }
    return new FilterOutputStream(Files.newOutputStream(staged)) {
      @Override
      public void write(int value) throws IOException {
        out.write(value);
        bytes++;
        if (phase.equals("write")) {
          throw failure();
        }
      }

      @Override
      public void write(byte[] data, int offset, int length) throws IOException {
        final int count = phase.equals("write") ? Math.min(length, 11) : length;
        out.write(data, offset, count);
        bytes += count;
        if (phase.equals("write")) {
          throw failure();
        }
      }

      @Override
      public void flush() throws IOException {
        out.flush();
        if (phase.equals("flush")) {
          throw failure();
        }
      }

      @Override
      public void close() throws IOException {
        if (closed) {
          return;
        }
        try {
          super.close();
        } finally {
          closed = true;
        }
        if (phase.equals("close")) {
          throw failure();
        }
      }
    };
  }

  @Override
  protected void replace(Path staged, Path target, boolean existing) throws IOException {
    if (phase.equals("replace")) {
      throw failure();
    }
    if (phase.equals("unsupported")) {
      throw new AtomicMoveNotSupportedException(staged.toString(), target.toString(), phase);
    }
    super.replace(staged, target, existing);
  }

  private IOException failure() {
    return new IOException("Injected export " + phase + " failure");
  }

  /** Runs an exporter with its expected diagnostics captured, restoring both global streams. */
  public static boolean quiet(Callable<Boolean> action) throws Exception {
    final PrintStream previousOut = System.out;
    final PrintStream previousErr = System.err;
    try (PrintStream capture = new PrintStream(new ByteArrayOutputStream())) {
      System.setOut(capture);
      System.setErr(capture);
      return action.call();
    } finally {
      System.setOut(previousOut);
      System.setErr(previousErr);
    }
  }

  /** Lists remaining output and spill files without assuming a particular temporary name. */
  public static List<Path> regularFiles(Path directory) throws IOException {
    try (Stream<Path> files = Files.walk(directory)) {
      return files.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
    }
  }
}
