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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Checks burst throughput, byte decoding and lifecycle without creating desktop windows. */
@Timeout(10)
class ConsoleOutputBufferTest {
  @Test
  void largeBurstDoesNotWaitForDisplayAndIsDeliveredInBoundedBatches() {
    try (ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8);
        PrintStream out = buffer.createStream(false)) {
      StringBuilder expected = new StringBuilder();
      for (int index = 0; index < 20000; index++) {
        String line = "Estimation record " + index + ": coefficient = 1.23456789\n";
        out.print(line);
        expected.append(line);
      }
      assertFalse(out.checkError());
      StringBuilder actual = new StringBuilder();
      int insertions = 0;
      List<ConsoleOutputBuffer.Chunk> batch;
      while (!(batch = buffer.drain()).isEmpty()) {
        int size = 0;
        for (ConsoleOutputBuffer.Chunk chunk : batch) {
          assertFalse(chunk.error);
          size += chunk.text().length();
          actual.append(chunk.text());
          insertions++;
        }
        assertTrue(size <= 32768);
      }
      assertEquals(expected.toString(), actual.toString());
      assertTrue(insertions < 200, "Thousands of messages should be coalesced before display");
    }
  }

  @Test
  void preservesInterleavedOutputColoursAndPartialLines() {
    try (ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8);
        PrintStream out = buffer.createStream(false);
        PrintStream err = buffer.createStream(true)) {
      out.print("first ");
      out.print("line\n");
      err.print("warning\n");
      out.print("unfinished");
      List<ConsoleOutputBuffer.Chunk> batch = buffer.drain();
      assertEquals(List.of("first line\n", "warning\n", "unfinished"), texts(batch));
      assertFalse(batch.get(0).error);
      assertTrue(batch.get(1).error);
      assertFalse(batch.get(2).error);
      assertTrue(buffer.drain().isEmpty());
    }
  }

  @Test
  void preservesMultibyteCharactersAcrossWritesAndDisplayBatches() {
    try (ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8);
        PrintStream out = buffer.createStream(false);
        PrintStream err = buffer.createStream(true)) {
      String expected = "Réseau € 漢字 🚆";
      StringBuilder actual = new StringBuilder();
      for (byte value : expected.getBytes(StandardCharsets.UTF_8)) {
        out.write(value);
        for (ConsoleOutputBuffer.Chunk chunk : buffer.drain()) {
          actual.append(chunk.text());
        }
      }
      assertEquals(expected, actual.toString());
      byte[] euro = "€".getBytes(StandardCharsets.UTF_8);
      out.write(euro, 0, 1);
      err.print("error");
      out.write(euro, 1, 2);
      assertEquals(List.of("error", "€"), texts(buffer.drain()));
    }
  }

  @Test
  void clearDiscardsBacklogAndSaveSnapshotIncludesAllRemainingMessages() {
    try (ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8);
        PrintStream out = buffer.createStream(false)) {
      out.print("old message".repeat(10000));
      buffer.clear();
      assertTrue(buffer.drain().isEmpty());
      String expected = "new message\n".repeat(10000);
      out.print(expected);
      assertEquals(expected, String.join("", texts(buffer.drainAll())));
      assertTrue(buffer.drain().isEmpty());
      out.print("after snapshot");
      assertEquals(List.of("after snapshot"), texts(buffer.drain()));
    }
  }

  @Test
  void frequentColourChangesStillHaveBoundedDisplayWork() {
    try (ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8);
        PrintStream out = buffer.createStream(false);
        PrintStream err = buffer.createStream(true)) {
      for (int index = 0; index < 1000; index++) {
        out.print("a");
        err.print("b");
      }
      StringBuilder actual = new StringBuilder();
      List<ConsoleOutputBuffer.Chunk> batch;
      while (!(batch = buffer.drain()).isEmpty()) {
        assertTrue(batch.size() <= 64);
        actual.append(String.join("", texts(batch)));
      }
      assertEquals("ab".repeat(1000), actual.toString());
    }
  }

  @Test
  void concurrentWritersDoNotLoseRecords() throws Exception {
    ExecutorService writers = Executors.newFixedThreadPool(2);
    try (ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8);
        PrintStream out = buffer.createStream(false);
        PrintStream err = buffer.createStream(true)) {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> jobs = new ArrayList<>();
      for (PrintStream stream : List.of(out, err)) {
        jobs.add(
            writers.submit(
                () -> {
                  start.await();
                  for (int index = 0; index < 1000; index++) {
                    stream.print(index + "\n");
                  }
                  return null;
                }));
      }
      start.countDown();
      StringBuilder actualOut = new StringBuilder();
      StringBuilder actualErr = new StringBuilder();
      while (jobs.stream().anyMatch(job -> !job.isDone())) {
        collect(buffer.drain(), actualOut, actualErr);
        Thread.yield();
      }
      for (Future<?> job : jobs) {
        job.get();
      }
      collect(buffer.drainAll(), actualOut, actualErr);
      StringBuilder expected = new StringBuilder();
      for (int index = 0; index < 1000; index++) {
        expected.append(index).append('\n');
      }
      assertEquals(expected.toString(), actualOut.toString());
      assertEquals(expected.toString(), actualErr.toString());
    } finally {
      writers.shutdownNow();
      assertTrue(writers.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void closingOneStreamFlushesIncompleteCharactersAndLeavesOtherUsable() {
    try (ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8);
        PrintStream err = buffer.createStream(true)) {
      try (PrintStream out = buffer.createStream(false)) {
        out.write(0xe2); // Incomplete UTF-8 character is replaced only when the stream closes.
        out.flush();
        assertTrue(buffer.drain().isEmpty());
      }
      err.print("still open");
      assertFalse(err.checkError());
      assertEquals(List.of("�", "still open"), texts(buffer.drainAll()));
    }
  }

  @Test
  void closingBufferDiscardsPendingTextAndRejectsFurtherOutput() {
    try (ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8);
        PrintStream out = buffer.createStream(false)) {
      out.print("pending");
      buffer.close();
      assertTrue(buffer.drain().isEmpty());
      out.print("late message");
      assertTrue(out.checkError());
      assertTrue(buffer.drain().isEmpty());
    }
  }

  private static List<String> texts(List<ConsoleOutputBuffer.Chunk> chunks) {
    List<String> result = new ArrayList<>();
    for (ConsoleOutputBuffer.Chunk chunk : chunks) {
      result.add(chunk.text());
    }
    return result;
  }

  private static void collect(
      List<ConsoleOutputBuffer.Chunk> chunks, StringBuilder out, StringBuilder err) {
    for (ConsoleOutputBuffer.Chunk chunk : chunks) {
      (chunk.error ? err : out).append(chunk.text());
    }
  }
}
