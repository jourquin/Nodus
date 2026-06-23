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

import edu.uclouvain.core.nodus.NodusC;
import groovy.lang.GroovyShell;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Runs a Groovy script, passing a list of variables.
 *
 * @author Bart Jourquin
 */
public class ScriptRunner {

  GroovyShell shell;
  String scriptFileName;

  /**
   * Initialize the script runner.
   *
   * @param scriptFileName Full path and file name name of the script to run.
   */
  public ScriptRunner(String scriptFileName) {
    this.scriptFileName = scriptFileName;
    shell = new GroovyShell();
  }

  /**
   * Add a variable to the shell.
   *
   * @param name The name of the variable.
   * @param value The value of the variable
   */
  public void setVariable(String name, Object value) {
    shell.setVariable(name, value);
  }

  /**
   * Runs the Groovy script in a thread.
   *
   * @param ignoreMissingScript If set to false, no error is returned if the script doesn't exist.
   * @return True if script was run successfully.
   */
  public boolean run(boolean ignoreMissingScript) {

    AtomicBoolean success = new AtomicBoolean(true);

    Thread thread =
        new Thread(
            () -> success.set(evaluateScript(ignoreMissingScript)),
            "Nodus-ScriptRunner");

    thread.start();

    try {
      thread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      success.set(false);
      showError(e);
    }

    return success.get();
  }

  /**
   * Runs the Groovy script in a background thread and invokes {@code onDone} on the EDT when the
   * script finishes.
   *
   * @param ignoreMissingScript If set to false, no error is returned if the script doesn't exist.
   * @param onDone Optional callback receiving the success flag once execution completes.
   */
  public void runAsync(boolean ignoreMissingScript, Consumer<Boolean> onDone) {
    Thread thread =
        new Thread(
            () -> {
              boolean success = evaluateScript(ignoreMissingScript);
              if (onDone != null) {
                SwingUtilities.invokeLater(() -> onDone.accept(Boolean.valueOf(success)));
              }
            },
            "Nodus-ScriptRunner");
    thread.start();
  }

  /** Evaluates the configured script and returns true on success. */
  private boolean evaluateScript(boolean ignoreMissingScript) {
    try {
      shell.evaluate(new File(scriptFileName));
      return true;
    } catch (IOException e) {
      if (!ignoreMissingScript) {
        showError(e);
        return false;
      }
      return true;
    } catch (Exception e) {
      showError(e);
      return false;
    }
  }

  /**
   * Display the error if something went wrong.
   *
   * @param e The exception that was caught.
   */
  private void showError(Exception e) {
    Runnable showDialog =
        () ->
            JOptionPane.showMessageDialog(
                null, e.getMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);

    if (SwingUtilities.isEventDispatchThread()) {
      showDialog.run();
    } else {
      SwingUtilities.invokeLater(showDialog);
    }
  }
}
