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

package edu.uclouvain.core.nodus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs project-owned cleanup actions once, independently of JDBC and Groovy close hooks. */
final class ProjectCleanupRegistry {
  private static final Logger LOGGER = Logger.getLogger(ProjectCleanupRegistry.class.getName());

  private final Map<String, Runnable> actions = new LinkedHashMap<>();
  private CompletableFuture<Void> completion;

  /** Registers an action while the project is opening or open. */
  synchronized void register(String name, Runnable action) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(action, "action");
    if (completion != null) {
      throw new IllegalStateException("Project cleanup has already started");
    }
    if (actions.containsKey(name)) {
      throw new IllegalArgumentException("Duplicate project cleanup action: " + name);
    }
    actions.put(name, action);
  }

  /** Starts cleanup off the UI thread and returns the same completion on repeated calls. */
  synchronized CompletableFuture<Void> closeAsync() {
    if (completion != null) {
      return completion;
    }

    List<Map.Entry<String, Runnable>> pending = new ArrayList<>(actions.entrySet());
    Collections.reverse(pending);
    actions.clear();
    completion = new CompletableFuture<>();
    if (pending.isEmpty()) {
      completion.complete(null);
      return completion;
    }

    CompletableFuture<Void> result = completion;
    Thread worker =
        new Thread(
            () -> {
              try {
                for (Map.Entry<String, Runnable> entry : pending) {
                  try {
                    entry.getValue().run();
                  } catch (Throwable failure) {
                    LOGGER.log(Level.WARNING, "Could not clean up " + entry.getKey(), failure);
                  }
                }
              } finally {
                result.complete(null);
              }
            },
            "Nodus-ProjectCleanup");
    worker.setDaemon(true);
    try {
      worker.start();
    } catch (RuntimeException | Error failure) {
      LOGGER.log(Level.WARNING, "Could not start project cleanup", failure);
      result.completeExceptionally(failure);
    }
    return completion;
  }
}
