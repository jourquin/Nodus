/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Checks project shutdown without a database or UI. */
class ProjectCleanupRegistryTest {
  @Test
  void runsEveryActionOnceInReverseOrderEvenWhenOneFails() throws Exception {
    ProjectCleanupRegistry registry = new ProjectCleanupRegistry();
    List<String> calls = Collections.synchronizedList(new ArrayList<>());
    registry.register("first", () -> calls.add("first"));
    registry.register(
        "broken",
        () -> {
          calls.add("broken");
          throw new IllegalStateException("Expected shutdown failure");
        });
    registry.register("last", () -> calls.add("last"));

    CompletableFuture<Void> closing = registry.closeAsync();
    assertSame(closing, registry.closeAsync());
    closing.get(5, TimeUnit.SECONDS);

    assertEquals(List.of("last", "broken", "first"), calls);
    assertSame(closing, registry.closeAsync());
  }

  @Test
  void rejectsDuplicateAndLateRegistrations() throws Exception {
    ProjectCleanupRegistry registry = new ProjectCleanupRegistry();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    registry.register(
        "bridge",
        () -> {
          started.countDown();
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
          }
        });
    assertThrows(IllegalArgumentException.class, () -> registry.register("bridge", () -> {}));

    CompletableFuture<Void> closing = registry.closeAsync();
    try {
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(IllegalStateException.class, () -> registry.register("late", () -> {}));
    } finally {
      release.countDown();
    }
    closing.get(5, TimeUnit.SECONDS);
  }
}
