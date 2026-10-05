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

import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.EventObject;
import javax.swing.JCheckBox;
import javax.swing.JFrame;

/** Manages the optional native Groovy console without replacing its script entry point. */
final class NativeGroovyConsole {
  /** Native Groovy console instance, when the user selected that console implementation. */
  private groovy.console.ui.Console nativeGroovyConsole;

  private final NodusMapPanel panel;
  private final Runnable restoreApplicationMenus;

  NativeGroovyConsole(NodusMapPanel panel, Runnable restoreApplicationMenus) {
    this.panel = panel;
    this.restoreApplicationMenus = restoreApplicationMenus;
  }

  /** Shows the native Groovy console, or focuses the already open one. */
  void show(String path) {
    if (focusNativeGroovyConsole()) {
      return;
    }

    final groovy.console.ui.Console console = new groovy.console.ui.Console();
    nativeGroovyConsole = console;

    // Set some defaults in UI
    console.askToInterruptScript();
    console.setAutoClearOutput(true);
    console.setSaveOnRun(true);

    JCheckBox dummyCheckBox = new JCheckBox();
    dummyCheckBox.setSelected(false);
    console.showScriptInOutput(new EventObject(dummyCheckBox));

    dummyCheckBox.setSelected(true);
    console.threadInterruption(new EventObject(dummyCheckBox));

    console.setCurrentFileChooserDir(new File(path));
    console.setVariable("nodusMapPanel", panel);
    console.run();

    JFrame consoleFrame = getNativeGroovyConsoleFrame(console);
    if (consoleFrame != null) {
      consoleFrame.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
              clearNativeGroovyConsole(console);
            }

            @Override
            public void windowClosing(WindowEvent e) {
              clearNativeGroovyConsole(console);
            }
          });

      consoleFrame.setLocationRelativeTo(null);
      focusFrame(consoleFrame);
    }

    // Reset the preferences menu
    restoreApplicationMenus.run();
  }

  /** Brings the already open native Groovy console to the front. */
  private boolean focusNativeGroovyConsole() {
    JFrame consoleFrame = getNativeGroovyConsoleFrame(nativeGroovyConsole);
    if (consoleFrame == null || !consoleFrame.isDisplayable()) {
      nativeGroovyConsole = null;
      return false;
    }

    focusFrame(consoleFrame);
    return true;
  }

  /** Clears the retained native console reference if it still points to the closed console. */
  private void clearNativeGroovyConsole(groovy.console.ui.Console console) {
    if (nativeGroovyConsole == console) {
      nativeGroovyConsole = null;
    }
  }

  /** Returns the Swing frame that hosts a native Groovy console. */
  private JFrame getNativeGroovyConsoleFrame(groovy.console.ui.Console console) {
    if (console == null || console.getFrame() == null) {
      return null;
    }
    return (JFrame) console.getFrame().getRootPane().getParent();
  }

  /** Makes a frame visible, de-iconified and focused. */
  private void focusFrame(JFrame frame) {
    if (frame.getState() == Frame.ICONIFIED) {
      frame.setState(Frame.NORMAL);
    }
    frame.setVisible(true);
    frame.toFront();
    frame.requestFocus();
  }
}
