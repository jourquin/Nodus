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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.gui.NodusLayersPanel;
import com.bbn.openmap.layer.location.NodusLocationLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.coords.LatLonPoint;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.utils.CommentedProperties;
import edu.uclouvain.core.nodus.utils.ProjectLocker;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Runs real successful/aborted close sequences, substituting only presentation and opening B. */
@ResourceLock("ProjectLocker")
@ResourceLock("JDBCUtils")
@ResourceLock("ModalSplitMethodsLoader")
@Timeout(25)
class NodusProjectCloseWorkflowTest {
  @TempDir Path directory;

  @Test
  void openingNextProjectWaitsForHookCleanupJdbcClosureAndLockRelease() throws Exception {
    try (Fixture fixture = new Fixture()) {
      SwingUtilities.invokeAndWait(() -> fixture.panel.openProject("next.nodus"));
      assertTrue(fixture.hookEntered.await(5, TimeUnit.SECONDS));
      assertEquals(0, fixture.opened.get());
      assertFalse(fixture.connection.isClosed());
      assertTrue(fixture.project.isOpen());
      assertFalse(ProjectLocker.createLock(fixture.next));
      final AtomicInteger callbacks = new AtomicInteger();
      final CountDownLatch callbackDone = new CountDownLatch(1);
      SwingUtilities.invokeAndWait(
          () ->
              fixture.project.close(
                  () -> {
                    fixture.events.add("callback");
                    fixture.callbacksOnEdt &= SwingUtilities.isEventDispatchThread();
                    callbacks.incrementAndGet();
                    callbackDone.countDown();
                  }));
      assertEquals(List.of("hook"), fixture.events);
      fixture.hookRelease.countDown();
      assertTrue(fixture.cleanupEntered.await(5, TimeUnit.SECONDS));
      assertEquals(0, fixture.opened.get());
      assertEquals(0, callbacks.get());
      assertFalse(fixture.connection.isClosed());
      assertFalse(ProjectLocker.createLock(fixture.next));
      fixture.cleanupRelease.countDown();
      assertTrue(fixture.openDone.await(5, TimeUnit.SECONDS));
      assertTrue(callbackDone.await(5, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(() -> {});
      assertEquals(1, fixture.opened.get());
      assertEquals(1, callbacks.get());
      assertEquals(1, fixture.disposed.get());
      assertTrue(fixture.callbacksOnEdt);
      assertTrue(fixture.closedBeforeOpen);
      assertTrue(fixture.connectionClosedBeforeOpen);
      assertTrue(fixture.nextLockAcquired);
      assertFalse(fixture.project.isOpen());
      assertFalse(fixture.panel.busy);
      assertEquals(List.of("hook", "cleanup", "cleanup done", "open", "callback"), fixture.events);
      assertTrue(Files.exists(directory.resolve("first.nodus.local")));
    }
  }

  @Test
  void cancelledCloseRetainsOwnershipAndDiscardsTheOpenCallback() throws Exception {
    checkAbortedClose(JOptionPane.CANCEL_OPTION, false);
  }

  @Test
  void failedSaveRetainsOwnershipAndDiscardsTheOpenCallback() throws Exception {
    checkAbortedClose(JOptionPane.YES_OPTION, true);
  }

  private void checkAbortedClose(int answer, boolean failSave) throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.project.dirty = true;
      fixture.project.answer = answer;
      fixture.failSave = failSave;
      SwingUtilities.invokeAndWait(() -> fixture.panel.openProject("next.nodus"));
      assertTrue(fixture.panel.finished.await(5, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(() -> {});
      assertTrue(fixture.project.isOpen());
      assertFalse(fixture.connection.isClosed());
      assertFalse(ProjectLocker.createLock(fixture.next));
      assertEquals(0, fixture.opened.get());
      assertEquals(0, fixture.disposed.get());
      assertTrue(fixture.events.isEmpty());
      assertFalse(fixture.panel.busy);
      // A later successful close must not resurrect the discarded open-B callback.
      fixture.project.dirty = false;
      fixture.hookRelease.countDown();
      fixture.cleanupRelease.countDown();
      final AtomicInteger callbacks = new AtomicInteger();
      final CountDownLatch closed = new CountDownLatch(1);
      SwingUtilities.invokeAndWait(
          () ->
              fixture.project.close(
                  () -> {
                    fixture.callbacksOnEdt &= SwingUtilities.isEventDispatchThread();
                    callbacks.incrementAndGet();
                    closed.countDown();
                  }));
      assertTrue(closed.await(5, TimeUnit.SECONDS));
      assertEquals(1, callbacks.get());
      assertEquals(0, fixture.opened.get());
      assertTrue(fixture.callbacksOnEdt);
      assertTrue(fixture.connection.isClosed());
      assertFalse(fixture.project.isOpen());
      assertTrue(ProjectLocker.createLock(fixture.next));
    }
  }

  private final class Fixture implements AutoCloseable {
    final List<String> events = new CopyOnWriteArrayList<>();
    final CountDownLatch hookEntered = new CountDownLatch(1);
    final CountDownLatch hookRelease = new CountDownLatch(1);
    final CountDownLatch cleanupEntered = new CountDownLatch(1);
    final CountDownLatch cleanupRelease = new CountDownLatch(1);
    final CountDownLatch openDone = new CountDownLatch(1);
    final AtomicInteger opened = new AtomicInteger();
    final AtomicInteger disposed = new AtomicInteger();
    final Panel panel = new Panel();
    final Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:close_" + UUID.randomUUID());
    final ClosingProject project = new ClosingProject(panel, this);
    final NodusProject next;
    volatile boolean callbacksOnEdt = true;
    volatile boolean closedBeforeOpen;
    volatile boolean connectionClosedBeforeOpen;
    volatile boolean nextLockAcquired;
    boolean failSave;

    Fixture() throws Exception {
      assertTrue(JDBCUtils.setConnection(connection));
      connection.setAutoCommit(false);
      final Properties properties = new Properties();
      properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, directory + File.separator);
      properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "first");
      next =
          new NodusProject(null) {
            @Override
            public String getLocalProperty(String key) {
              return NodusC.PROP_PROJECT_DOTPATH.equals(key) ? directory + File.separator : "next";
            }
          };
      set(project, "localProperties", properties);
      set(project, "projectProperties", new CommentedProperties());
      set(project, "projectResourceFileNameAndPath", directory.resolve("first.nodus").toString());
      set(project, "jdbcConnection", connection);
      set(project, "isOpen", true);
      set(project, "initialScale", panel.map.getScale());
      set(project, "initialCenterPoint", panel.map.getCenter());
      final NodusEsriLayer layer =
          new NodusEsriLayer() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean saveChanges() {
              return !failSave;
            }

            @Override
            public void dispose() {
              disposed.incrementAndGet();
            }
          };
      layer.setName("nodes");
      set(project, "nodeLayers", new NodusEsriLayer[] {layer});
      set(project, "linkLayers", new NodusEsriLayer[0]);
      final NodusLocationLayer labels = new NodusLocationLayer();
      labels.setName("labels");
      set(project, "labelsLayer", labels);
      set(panel, "nodusProject", project);
      set(panel, "scenarioComboBox", new javax.swing.JComboBox<String>());
      set(panel, "scenarioLabel", new javax.swing.JLabel());
      panel.storeObject("events", events);
      panel.storeObject("hookEntered", hookEntered);
      panel.storeObject("hookRelease", hookRelease);
      Files.writeString(
          directory.resolve("first.groovy"),
          "if (closeProject) {\n"
              + "  nodusMapPanel.retrieveObject('events').add('hook')\n"
              + "  nodusMapPanel.retrieveObject('hookEntered').countDown()\n"
              + "  def release = nodusMapPanel.retrieveObject('hookRelease')\n"
              + "  assert release.await(10, java.util.concurrent.TimeUnit.SECONDS)\n"
              + "}\n");
      project.registerProjectCleanup(
          "blocked resource",
          () -> {
            events.add("cleanup");
            cleanupEntered.countDown();
            try {
              if (!cleanupRelease.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Cleanup release timed out");
              }
            } catch (InterruptedException failure) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(failure);
            }
            events.add("cleanup done");
          });
      assertTrue(ProjectLocker.createLock(project));
    }

    @Override
    public void close() throws Exception {
      hookRelease.countDown();
      cleanupRelease.countDown();
      try {
        if (project.isOpen()) {
          project.dirty = false;
          final CountDownLatch closed = new CountDownLatch(1);
          SwingUtilities.invokeAndWait(() -> project.close(closed::countDown));
          assertTrue(closed.await(5, TimeUnit.SECONDS), "Close worker must finish before teardown");
        }
        SwingUtilities.invokeAndWait(() -> {});
      } finally {
        ProjectLocker.releaseLock();
        JDBCUtils.setConnection(null);
        connection.close();
        SwingUtilities.invokeAndWait(panel.map::dispose);
      }
    }
  }

  private static final class ClosingProject extends NodusProject {
    final Fixture fixture;
    boolean dirty;
    int answer;

    ClosingProject(Panel panel, Fixture fixture) {
      super(panel);
      this.fixture = fixture;
    }

    @Override
    public boolean isDirty() {
      return dirty;
    }

    @Override
    protected int confirmLayerSaveOnClose() {
      return answer;
    }

    @Override
    public void openProject(String name) {
      fixture.callbacksOnEdt &= SwingUtilities.isEventDispatchThread();
      fixture.closedBeforeOpen = !isOpen();
      try {
        fixture.connectionClosedBeforeOpen = fixture.connection.isClosed();
      } catch (java.sql.SQLException failure) {
        throw new IllegalStateException(failure);
      }
      fixture.nextLockAcquired = ProjectLocker.createLock(fixture.next);
      fixture.events.add("open");
      fixture.opened.incrementAndGet();
      fixture.openDone.countDown();
    }
  }

  private static void set(Object target, String name, Object value) throws Exception {
    final Class<?> owner =
        target instanceof NodusProject ? NodusProject.class : NodusMapPanel.class;
    final Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static final class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    final MapBean map = new MapBean(false);
    final LayerHandler layers = new LayerHandler();
    final NodusLayersPanel controls = new NodusLayersPanel(this);
    final CountDownLatch finished = new CountDownLatch(1);
    volatile boolean busy;

    Panel() {
      map.setProjection(new Mercator(new LatLonPoint.Double(0, 0), 1000000, 800, 600));
    }

    @Override
    public MapBean getMapBean() {
      return map;
    }

    @Override
    public LayerHandler getLayerHandler() {
      return layers;
    }

    @Override
    public NodusLayersPanel getNodusLayersPanel() {
      return controls;
    }

    @Override
    public java.awt.Frame getMainFrame() {
      return null;
    }

    @Override
    public String getActiveMouseMode() {
      return "Gestures";
    }

    @Override
    public void displayHighlightedAreaLayer(boolean added, boolean visible) {}

    @Override
    public void displayPoliticalBoundaries(boolean added, boolean visible) {}

    @Override
    public void resetMap() {}

    @Override
    public void resetTitle() {}

    @Override
    public void resetText() {}

    @Override
    public void removeProjectPlugins() {}

    @Override
    public void enableMenus(boolean enabled) {}

    @Override
    public void setFileMenuBusy(boolean value) {}

    @Override
    public void setBusy(boolean value) {
      busy = value;
    }

    @Override
    public void restoreMainFrameFocus() {
      finished.countDown();
    }
  }
}
