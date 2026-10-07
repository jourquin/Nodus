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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.database.ShapeIntegrityTester;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class NodusProjectSaveFailureTest {
  @TempDir Path directory;

  @Test
  void failedSaveKeepsProjectOpenAndDiscardsExitOrOpenCallbacks() throws Exception {
    Panel panel = new Panel();
    AtomicInteger saves = new AtomicInteger();
    NodusEsriLayer layer =
        new NodusEsriLayer() {
          @Override
          public boolean saveChanges() {
            saves.incrementAndGet();
            return false;
          }
        };
    NodusProject project =
        new NodusProject(panel) {
          @Override
          public boolean isDirty() {
            return true;
          }

          @Override
          protected int confirmLayerSaveOnClose() {
            return JOptionPane.YES_OPTION;
          }

          @Override
          public NodusEsriLayer[] getNodeLayers() {
            return new NodusEsriLayer[] {layer};
          }

          @Override
          public NodusEsriLayer[] getLinkLayers() {
            return new NodusEsriLayer[0];
          }
        };
    Field open = NodusProject.class.getDeclaredField("isOpen");
    open.setAccessible(true);
    open.setBoolean(project, true);
    Path marker = installCloseHook(project);
    AtomicInteger closed = new AtomicInteger();
    for (int attempt = 1; attempt <= 2; attempt++) {
      panel.finished = new CountDownLatch(1);
      SwingUtilities.invokeAndWait(() -> project.close(closed::incrementAndGet));
      assertTrue(panel.finished.await(5, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(() -> {});
      assertTrue(project.isOpen());
      assertEquals(attempt, saves.get());
      assertEquals(0, closed.get());
      assertFalse(panel.busy);
      assertFalse(Files.exists(marker), "Failed close must not run the project shutdown hook");
    }
    // A future request on an already closed project must not resurrect either failed callback.
    open.setBoolean(project, false);
    SwingUtilities.invokeAndWait(() -> project.close(closed::incrementAndGet));
    assertEquals(1, closed.get());
  }

  @Test
  void cancelledClosePreservesHookAndRestartsIntegrityTester() throws Exception {
    Panel panel = new Panel();
    NodusProject project = new NodusProject(panel) {
      @Override
      public boolean isDirty() {
        return true;
      }

      @Override
      protected int confirmLayerSaveOnClose() {
        return JOptionPane.CANCEL_OPTION;
      }

      @Override
      public NodusEsriLayer[] getNodeLayers() {
        return new NodusEsriLayer[0];
      }

      @Override
      public NodusEsriLayer[] getLinkLayers() {
        return new NodusEsriLayer[0];
      }
    };
    Field open = NodusProject.class.getDeclaredField("isOpen");
    open.setAccessible(true);
    open.setBoolean(project, true);
    Path marker = installCloseHook(project);
    Field testerField = NodusProject.class.getDeclaredField("shapeIntegrityTester");
    testerField.setAccessible(true);
    ShapeIntegrityTester originalTester = new ShapeIntegrityTester(project);
    testerField.set(project, originalTester);
    try {
      panel.finished = new CountDownLatch(1);
      SwingUtilities.invokeAndWait(project::close);
      assertTrue(panel.finished.await(5, TimeUnit.SECONDS));
      assertTrue(project.isOpen());
      assertFalse(Files.exists(marker));
      ShapeIntegrityTester replacement = (ShapeIntegrityTester) testerField.get(project);
      assertNotNull(replacement);
      assertNotSame(originalTester, replacement);
    } finally {
      ShapeIntegrityTester tester = (ShapeIntegrityTester) testerField.get(project);
      if (tester != null) {
        tester.stop();
      }
    }
  }

  private Path installCloseHook(NodusProject project) throws Exception {
    Path marker = directory.resolve("close-hook-ran");
    Files.writeString(directory.resolve("mini.groovy"),
        "new File('" + marker + "').text = 'closed'\n");
    Properties properties = new Properties();
    properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, directory + "/");
    properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "mini");
    Field local = NodusProject.class.getDeclaredField("localProperties");
    local.setAccessible(true);
    local.set(project, properties);
    return marker;
  }

  private static class Panel extends NodusMapPanel {
    private static final long serialVersionUID = 7168837538790300219L;
    CountDownLatch finished;
    boolean busy;

    @Override
    public void setBusy(boolean value) {
      busy = value;
    }

    @Override
    public void setFileMenuBusy(boolean value) {}

    @Override
    public void enableMenus(boolean value) {}

    @Override
    public void restoreMainFrameFocus() {
      finished.countDown();
    }
  }
}
