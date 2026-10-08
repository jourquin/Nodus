/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.compute.modalsplit.Proportional;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.utils.ModalSplitMethodsLoader;
import edu.uclouvain.core.nodus.utils.ProjectLocker;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Checks both project cleanup paths with a deliberately broken modal plugin. */
@ResourceLock("ModalSplitMethodsLoader")
@ResourceLock("JDBCUtils")
@ResourceLock("ProjectLocker")
@ResourceLock(Resources.SYSTEM_ERR)
class NodusProjectCleanupTest {
  @TempDir Path directory;
  private final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
  private PrintStream originalError;
  private PrintStream capturedError;

  @BeforeEach
  void registerBrokenPlugin() {
    ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
    originalError = System.err;
    capturedError = new PrintStream(diagnostics, true, StandardCharsets.UTF_8);
    System.setErr(capturedError);
    ModalSplitMethodsLoader.getAvailableModalSplitMethods()
        .add(
            new Proportional(null) {
              @Override
              public void dispose() {
                throw new IllegalStateException("Broken project plugin");
              }
            });
  }

  @AfterEach
  void restoreSharedState() {
    try {
      ModalSplitMethodsLoader.disposeAvailableModalSplitMethods();
      ProjectLocker.releaseLock();
    } finally {
      System.setErr(originalError);
      capturedError.close();
    }
  }

  @Test
  void failedOpeningStillClosesJdbcConnectionReleasesLockAndResetsBusyState() throws Exception {
    Panel panel = new Panel();
    NodusProject project = new NodusProject(panel);
    setField(project, "localProperties", new Properties());
    project.setLocalProperty(NodusC.PROP_PROJECT_DOTPATH, directory + File.separator);
    project.setLocalProperty(NodusC.PROP_PROJECT_DOTNAME, "cleanup");
    assertTrue(ProjectLocker.createLock(project));
    try (DatabaseFixture database = new DatabaseFixture(directory)) {
      setField(project, "jdbcConnection", database.connection);

      invokeCleanup(project, "cleanupFailedProjectOpen");

      assertTrue(database.connection.isClosed());
      assertFalse(panel.busy);
      assertFalse(panel.fileMenuBusy);
      try (FileChannel channel =
              FileChannel.open(
                  directory.resolve("cleanup" + NodusC.TYPE_LOCK), StandardOpenOption.WRITE);
          FileLock lock = channel.tryLock()) {
        assertNotNull(lock, "Failed project opening must release the project lock");
      }
    }
  }

  @Test
  void normalClosingStillDisposesProjectLayers() throws Exception {
    NodusProject project = new NodusProject(null);
    AtomicInteger disposed = new AtomicInteger();
    NodusEsriLayer layer =
        new NodusEsriLayer() {
          @Override
          public void dispose() {
            disposed.incrementAndGet();
          }
        };
    setField(project, "nodeLayers", new NodusEsriLayer[] {layer});

    invokeCleanup(project, "disposeProjectObjectGraph");

    assertEquals(1, disposed.get());
  }

  @Test
  void failedOpeningStopsRegisteredResourcesBeforeFinishingCleanup() throws Exception {
    Panel panel = new Panel();
    NodusProject project = new NodusProject(panel);
    AtomicInteger stopped = new AtomicInteger();
    project.registerProjectCleanup("test bridge", stopped::incrementAndGet);

    Method cleanup = NodusProject.class.getDeclaredMethod("cleanupFailedProjectOpen");
    cleanup.setAccessible(true);
    cleanup.invoke(project);

    assertTrue(panel.cleanupFinished.await(5, TimeUnit.SECONDS));
    assertEquals(1, stopped.get());
    assertFalse(panel.busy);
    assertFalse(panel.fileMenuBusy);
  }

  @Test
  void closingBeforeProjectIsFullyOpenStillStopsRegisteredResources() throws Exception {
    Panel panel = new Panel();
    NodusProject project = new NodusProject(panel);
    AtomicInteger stopped = new AtomicInteger();
    CountDownLatch closed = new CountDownLatch(1);
    project.registerProjectCleanup("test bridge", stopped::incrementAndGet);

    SwingUtilities.invokeAndWait(() -> project.close(closed::countDown));

    assertTrue(closed.await(5, TimeUnit.SECONDS));
    assertEquals(1, stopped.get());
  }

  /** Invokes the lifecycle stage directly, avoiding unrelated project-loading dialogs. */
  private void invokeCleanup(NodusProject project, String name) throws Exception {
    Method cleanup = NodusProject.class.getDeclaredMethod(name);
    cleanup.setAccessible(true);
    cleanup.invoke(project);
    assertTrue(ModalSplitMethodsLoader.getAvailableModalSplitMethods().isEmpty());
    String output = diagnostics.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("Broken project plugin"), output);
  }

  private static void setField(NodusProject project, String name, Object value) throws Exception {
    Field field = NodusProject.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(project, value);
  }

  /** Only the busy-state callbacks need substituting for failed-open cleanup. */
  private static class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    boolean busy = true;
    boolean fileMenuBusy = true;
    final CountDownLatch cleanupFinished = new CountDownLatch(1);

    @Override
    public void setBusy(boolean value) {
      busy = value;
      if (!value) {
        cleanupFinished.countDown();
      }
    }

    @Override
    public void setFileMenuBusy(boolean value) {
      fileMenuBusy = value;
    }

    @Override
    public void enableMenus(boolean value) {}

    @Override
    public void restoreMainFrameFocus() {}
  }
}
