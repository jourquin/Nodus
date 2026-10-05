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

package edu.uclouvain.core.nodus.tools.console;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;

/**
 * Console that intercepts System.out and System.err and displays their output in batches.
 *
 * <p>Producers append to a shared buffer without waiting for Swing. A coalescing timer displays
 * bounded batches every 50 milliseconds, updating the caret only once per batch. Clear removes
 * queued text too, and Save includes messages still awaiting display. Closing restores the previous
 * streams and releases the buffer and timer. The complete log remains available until the user
 * clears or closes the console.
 *
 * @author Bart Jourquin
 */
public class NodusConsole extends WindowAdapter {
  private final ConsoleWindow window = new ConsoleWindow(this);

  static I18n i18n = Environment.getI18n();

  /**
   * Returns true if the console is displayed.
   *
   * @return True if the console is visible.
   */
  public static boolean isVisible() {
    return ConsoleWindow.isVisible();
  }

  /** Initializes a new console. */
  public NodusConsole() {
    this(null);
  }

  /**
   * Initializes a new console and set the default directory in which the output can be saved.
   *
   * @param defaultDirectory The default directory used to save the output.
   */
  public NodusConsole(String defaultDirectory) {
    if (SwingUtilities.isEventDispatchThread()) {
      window.initialize(defaultDirectory);
    } else {
      try {
        SwingUtilities.invokeAndWait(() -> window.initialize(defaultDirectory));
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while opening the console", failure);
      } catch (InvocationTargetException failure) {
        throw new IllegalStateException("Cannot open the console", failure.getCause());
      }
    }
  }

  /** Clears both displayed and queued output on the event thread. */
  public void clear() {
    window.clear();
  }

  /**
   * Sets the size of the console.
   *
   * @param width The width, expressed in pixels.
   * @param height The height, expressed in pixels.
   */
  public void setSize(int width, int height) {
    window.setSize(width, height);
  }

  /**
   * Stops display updates, restores standard streams and releases the console resources.
   *
   * @param evt WindowEvent
   * @hidden
   */
  @Override
  public void windowClosed(WindowEvent evt) {
    window.disposeConsole();
  }

  /**
   * Closes the console.
   *
   * @param evt WindowEvent.
   * @hidden
   */
  @Override
  public void windowClosing(WindowEvent evt) {
    window.closeConsole();
  }

}
