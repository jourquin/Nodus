/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>Nodus is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with Nodus. If not,
 * see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.swing;

import java.awt.AWTEvent;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;

/** Keeps an independent script editor above Nodus when a main-frame-owned dialog is activated. */
public final class ScriptEditorWindowSupport implements AWTEventListener {
  private final Window editor;
  private final Window mainWindow;
  private boolean pending;
  private boolean closed;

  private ScriptEditorWindowSupport(Window editor, Window mainWindow) {
    this.editor = editor;
    this.mainWindow = mainWindow;
    Toolkit.getDefaultToolkit()
        .addAWTEventListener(this, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.WINDOW_FOCUS_EVENT_MASK);
    scheduleRestore();
  }

  /**
   * Installs window-order handling for the lifetime of a script editor.
   *
   * @param editor legacy editor or native console window
   * @param mainWindow Nodus main window, which scripts commonly use as their dialog owner
   */
  public static void install(Window editor, Window mainWindow) {
    if (editor == null || mainWindow == null || editor == mainWindow) {
      return;
    }
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> install(editor, mainWindow));
      return;
    }
    if (editor.isDisplayable() && mainWindow.isDisplayable()) {
      new ScriptEditorWindowSupport(editor, mainWindow);
    }
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    WindowEvent windowEvent = (WindowEvent) event;
    Window window = windowEvent.getWindow();
    if (event.getID() == WindowEvent.WINDOW_CLOSED
        && (window == editor || window == mainWindow)) {
      closed = true;
      Toolkit.getDefaultToolkit().removeAWTEventListener(this);
      return;
    }
    if (window != mainWindow && !isOwnedBy(window, mainWindow)) {
      return;
    }
    if (event.getID() == WindowEvent.WINDOW_ACTIVATED
        || event.getID() == WindowEvent.WINDOW_GAINED_FOCUS
        || event.getID() == WindowEvent.WINDOW_OPENED) {
      scheduleRestore();
    }
  }

  /** Coalesces activation notifications and waits for the native window activation to finish. */
  private void scheduleRestore() {
    if (!closed && !pending) {
      pending = true;
      SwingUtilities.invokeLater(this::restoreOrder);
    }
  }

  /** Raises the editor without taking keyboard focus away from a script's dialog. */
  private void restoreOrder() {
    pending = false;
    if (closed || !editor.isShowing() || !mainWindow.isShowing()) {
      return;
    }
    if (editor instanceof Frame && (((Frame) editor).getExtendedState() & Frame.ICONIFIED) != 0) {
      return;
    }

    Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    // A queued activation must not bring Nodus back after the user switches to another window
    // or application. An editor-owned file chooser is already above the editor.
    if (active != mainWindow && !isOwnedBy(active, mainWindow)) {
      return;
    }
    raiseWithoutFocus(editor);
    if (active != mainWindow) {
      raiseWithoutFocus(active);
    }
  }

  private static boolean isOwnedBy(Window window, Window owner) {
    for (Window current = window; current != null; current = current.getOwner()) {
      if (current == owner) {
        return true;
      }
    }
    return false;
  }

  private static void raiseWithoutFocus(Window window) {
    boolean autoRequestFocus = window.isAutoRequestFocus();
    window.setAutoRequestFocus(false);
    try {
      window.toFront();
    } finally {
      window.setAutoRequestFocus(autoRequestFocus);
    }
  }
}
