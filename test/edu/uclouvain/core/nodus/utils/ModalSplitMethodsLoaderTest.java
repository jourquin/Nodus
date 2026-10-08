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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.compute.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.modalsplit.Proportional;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Exercises disposal failures and reentrant cleanup without loading third-party plugins. */
@ResourceLock("ModalSplitMethodsLoader")
@ResourceLock(Resources.SYSTEM_ERR)
class ModalSplitMethodsLoaderTest {
  @TempDir Path directory;
  private final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
  private PrintStream originalError;
  private PrintStream capturedError;

  @BeforeEach
  void prepareRegistryAndCaptureDiagnostics() {
    ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    originalError = System.err;
    capturedError = new PrintStream(diagnostics, true, StandardCharsets.UTF_8);
    System.setErr(capturedError);
  }

  @AfterEach
  void cleanRegistryAndRestoreDiagnostics() {
    try {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    } finally {
      System.setErr(originalError);
      capturedError.close();
    }
  }

  @Test
  void failingDisposersDoNotPreventLaterDisposalOrClassPathClosure() throws Exception {
    AtomicInteger disposed = new AtomicInteger();
    try (PluginClassPath classPath = new PluginClassPath(directory.toString())) {
      classPaths().add(classPath);
      methods().add(null);
      methods()
          .add(
              method(
                  () -> {
                    throw new IllegalStateException("Broken disposer");
                  }));
      methods()
          .add(
              method(
                  () -> {
                    throw new NoClassDefFoundError("Missing plugin dependency");
                  }));
      methods()
          .add(
              method(
                  () -> {
                    // The preceding failures must not close the class path before this disposer
                    // runs.
                    assertEquals(
                        String.class,
                        assertDoesNotThrow(() -> classPath.loadClass("java.lang.String")));
                    disposed.incrementAndGet();
                  }));

      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();

      assertEquals(1, disposed.get());
      assertTrue(methods().isEmpty());
      assertTrue(classPaths().isEmpty());
      assertThrows(IllegalStateException.class, () -> classPath.loadClass("java.lang.String"));
      String output = diagnostics.toString(StandardCharsets.UTF_8);
      assertTrue(output.contains("Could not dispose modal split method"), output);
      assertTrue(output.contains("Broken disposer"), output);
      assertTrue(output.contains("Missing plugin dependency"), output);

      // Retrying project cleanup must not run either failed disposer or the healthy one again.
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
      assertEquals(1, disposed.get());
      assertEquals(output, diagnostics.toString(StandardCharsets.UTF_8));
    }
  }

  @Test
  void reentrantCleanupDoesNotDisposeMethodsTwiceOrCloseTheirClassPathEarly() throws Exception {
    AtomicInteger disposed = new AtomicInteger();
    try (PluginClassPath classPath = new PluginClassPath(directory.toString())) {
      classPaths().add(classPath);
      methods()
          .add(
              method(
                  () -> {
                    disposed.incrementAndGet();
                    ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
                    assertDoesNotThrow(() -> classPath.loadClass("java.lang.String"));
                  }));
      methods().add(method(disposed::incrementAndGet));

      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();

      assertEquals(2, disposed.get());
      assertTrue(methods().isEmpty());
      assertTrue(classPaths().isEmpty());
      assertThrows(IllegalStateException.class, () -> classPath.loadClass("java.lang.String"));
      assertEquals("", diagnostics.toString(StandardCharsets.UTF_8));
    }
  }

  private static ModalSplitMethod method(Runnable disposer) {
    return new Proportional(null) {
      @Override
      public void dispose() {
        disposer.run();
      }
    };
  }

  private static LinkedList<ModalSplitMethod> methods() {
    return ModalSplitMethodsLoader.getAvailableModalSplitMethods();
  }

  /** Registers real closeable class paths without requiring generated plugin JARs. */
  @SuppressWarnings("unchecked")
  private static LinkedList<PluginClassPath> classPaths() throws Exception {
    Field field = ModalSplitMethodsLoader.class.getDeclaredField("modalSplitClassPaths");
    field.setAccessible(true);
    return (LinkedList<PluginClassPath>) field.get(null);
  }
}
