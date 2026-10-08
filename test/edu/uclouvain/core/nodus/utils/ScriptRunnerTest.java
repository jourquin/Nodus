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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks that a failing Groovy hook cannot strand its caller's lifecycle continuation. */
class ScriptRunnerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void asynchronousCompletionRunsOnEdtAfterAssertionError() throws Exception {
    Path script = temporaryDirectory.resolve("broken.groovy");
    Files.writeString(script, "assert false : 'broken hook'\n");
    CountDownLatch completion = new CountDownLatch(1);
    CountDownLatch uncaught = new CountDownLatch(1);
    AtomicReference<Boolean> result = new AtomicReference<>();
    AtomicBoolean callbackOnEdt = new AtomicBoolean();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          failure.set(error);
          uncaught.countDown();
        });
    try {
      new ScriptRunner(script.toString())
          .runAsync(
              false,
              success -> {
                result.set(success);
                callbackOnEdt.set(SwingUtilities.isEventDispatchThread());
                completion.countDown();
              });
      assertTrue(completion.await(10, TimeUnit.SECONDS));
      assertTrue(uncaught.await(10, TimeUnit.SECONDS));
      assertNotNull(failure.get());
      assertFalse(result.get());
      assertTrue(callbackOnEdt.get());
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
  }

  @Test
  void synchronousRunDoesNotReportSuccessAfterAssertionError() throws Exception {
    Path script = temporaryDirectory.resolve("broken.groovy");
    Files.writeString(script, "assert false : 'broken hook'\n");
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {});
    try {
      assertFalse(new ScriptRunner(script.toString()).run(false));
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
  }
}
