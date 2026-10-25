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

package edu.uclouvain.core.nodus.compute.assign;

import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Controlled clocks and public report assertions shared by the accounting and observer tests. */
final class ComputingTimesTestSupport implements AutoCloseable {
  private final boolean previous = NodusC.displayComputingTimes;
  final Clock clock = new Clock();
  final AssignmentComputingTimes audit;
  final Path directory;

  ComputingTimesTestSupport(boolean enabled) {
    NodusC.displayComputingTimes = enabled;
    try {
      directory = Files.createTempDirectory("nodus-computing-times-");
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    audit = new AssignmentComputingTimes(clock);
    audit.startAssignment();
  }

  String report(boolean success) {
    try {
      Path report = audit.finishAndSave(directory, "test", "test", 7, 2, success);
      return report == null ? "" : Files.readString(report, StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  static String report(AssignmentParameters parameters) throws IOException {
    Path report = parameters.getComputingTimes().getReportPath();
    return report == null ? "" : Files.readString(report, StandardCharsets.UTF_8);
  }

  static String capture(Runnable action) {
    PrintStream original = System.out;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setOut(output);
      try {
        action.run();
      } finally {
        System.setOut(original);
      }
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  static double seconds(String report, String label) {
    return Double.parseDouble(value(report, label, " s"));
  }

  static long count(String report, String label) {
    return Long.parseLong(value(report, label, ""));
  }

  private static String value(String report, String label, String suffix) {
    Matcher match =
        Pattern.compile(
                "<tr><td>"
                    + Pattern.quote(AssignmentComputingTimesReport.escape(label + ":"))
                    + "</td><td>(\\S+)"
                    + suffix
                    + "</td></tr>")
            .matcher(report);
    assertTrue(match.find(), () -> "Missing report row: " + label + "\n" + report);
    return match.group(1);
  }

  @Override
  public void close() {
    NodusC.displayComputingTimes = previous;
    try (var files = Files.walk(directory)) {
      for (Path file :
          files.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) {
        Files.delete(file);
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /** Explicit timestamps by default; a one-second step also gives deterministic search tails. */
  static final class Clock implements LongSupplier {
    private final AtomicLong now = new AtomicLong();
    private final AtomicLong reads = new AtomicLong();
    private long step;

    void at(long seconds) {
      now.set(seconds * 1_000_000_000L);
    }

    void stepEachRead() {
      step = 1_000_000_000L;
    }

    long reads() {
      return reads.get();
    }

    @Override
    public long getAsLong() {
      reads.incrementAndGet();
      return now.getAndAdd(step);
    }
  }
}
