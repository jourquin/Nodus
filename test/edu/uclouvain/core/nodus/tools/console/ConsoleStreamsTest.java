/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package edu.uclouvain.core.nodus.tools.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_OUT)
@ResourceLock(Resources.SYSTEM_ERR)
class ConsoleStreamsTest {
  @Test
  void redirectsBothStreamsAndRestoresWithoutClosingThePreviousStreams() {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream priorOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream priorErrors = new ByteArrayOutputStream();
    try (PrintStream previousOut = new PrintStream(priorOutput);
        PrintStream previousErr = new PrintStream(priorErrors);
        ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8)) {
      System.setOut(previousOut);
      System.setErr(previousErr);
      ConsoleStreams streams =
          new ConsoleStreams(buffer, (key, fallback, message) -> fail(message));
      try {
        streams.redirect();
        System.out.print("output");
        System.err.print("error");
        java.util.List<ConsoleOutputBuffer.Chunk> chunks = buffer.drainAll();
        assertEquals(2, chunks.size());
        assertEquals("output", chunks.get(0).text());
        assertFalse(chunks.get(0).error);
        assertEquals("error", chunks.get(1).text());
        assertTrue(chunks.get(1).error);
      } finally {
        streams.restore();
        streams.close();
      }
      assertSame(previousOut, System.out);
      assertSame(previousErr, System.err);
      System.out.print("still open");
      System.err.print("also open");
      assertEquals("still open", priorOutput.toString(StandardCharsets.UTF_8));
      assertEquals("also open", priorErrors.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  @Test
  void closingDoesNotOverwriteStreamsInstalledByAnotherOwner() {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    try (PrintStream replacementOut = new PrintStream(new ByteArrayOutputStream());
        PrintStream replacementErr = new PrintStream(new ByteArrayOutputStream());
        ConsoleOutputBuffer buffer = new ConsoleOutputBuffer(StandardCharsets.UTF_8)) {
      ConsoleStreams streams =
          new ConsoleStreams(buffer, (key, fallback, message) -> fail(message));
      try {
        streams.redirect();
        System.setOut(replacementOut);
        System.setErr(replacementErr);
      } finally {
        streams.restore();
        streams.close();
      }
      streams.restore();
      streams.close();
      assertSame(replacementOut, System.out);
      assertSame(replacementErr, System.err);
      assertFalse(replacementOut.checkError());
      assertFalse(replacementErr.checkError());
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }
}
