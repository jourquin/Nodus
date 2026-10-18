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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.utils.NodusPreferences;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Properties;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the startup project recorded when quitting with an open or manually closed project. */
class NodusMapPanelStartupStateTest {
  @TempDir Path home;

  @Test
  void quittingAfterManualCloseClearsSavedProjectAndPreservesChooserDirectory() throws Exception {
    Properties saved = quitAndReloadPreferences(false);

    assertNull(saved.getProperty(NodusC.PROP_LAST_PROJECT));
    assertEquals(home.toString(), saved.getProperty(NodusC.PROP_LAST_PATH));
    assertEquals("true", saved.getProperty(NodusC.PROP_REOPEN_LATST_PROJECT));
  }

  @Test
  void quittingWithOpenProjectKeepsItForNextLaunch() throws Exception {
    Properties saved = quitAndReloadPreferences(true);

    assertEquals("example.nodus", saved.getProperty(NodusC.PROP_LAST_PROJECT));
    assertEquals(home.toString(), saved.getProperty(NodusC.PROP_LAST_PATH));
    assertEquals("true", saved.getProperty(NodusC.PROP_REOPEN_LATST_PROJECT));
  }

  private Properties quitAndReloadPreferences(boolean projectOpen) throws Exception {
    NodusMapPanel panel = new NodusMapPanel();
    Properties preferences = panel.getNodusProperties();
    preferences.setProperty(NodusC.PROP_LAST_PATH, home.toString());
    preferences.setProperty(NodusC.PROP_LAST_PROJECT, "example.nodus");
    preferences.setProperty(NodusC.PROP_REOPEN_LATST_PROJECT, "true");
    DeferredCloseProject project = new DeferredCloseProject(panel, projectOpen);
    Field field = NodusMapPanel.class.getDeclaredField("nodusProject");
    field.setAccessible(true);
    field.set(panel, project);

    SwingUtilities.invokeAndWait(panel::closeAndSaveState);

    assertTrue(project.closeRequested, "Quitting must still close project resources");
    NodusPreferences.save(home, preferences);
    return NodusPreferences.load(home);
  }

  /** Intercepts project closing to inspect preferences without terminating the JVM. */
  private static class DeferredCloseProject extends NodusProject {
    private boolean open;
    private boolean closeRequested;

    DeferredCloseProject(NodusMapPanel panel, boolean open) {
      super(panel);
      this.open = open;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void close(Runnable onClosed) {
      assertNotNull(onClosed);
      closeRequested = true;
      open = false;
    }
  }
}
