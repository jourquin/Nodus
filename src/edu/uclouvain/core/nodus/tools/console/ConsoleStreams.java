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

import java.io.PrintStream;

/** Owns standard-stream redirection and restores only streams it still owns. */
final class ConsoleStreams {
  private PrintStream previousOut;
  private PrintStream previousErr;
  private PrintStream redirectedOut;
  private PrintStream redirectedErr;

  interface ErrorHandler {
    void show(String key, String defaultText, String message);
  }

  private final ConsoleOutputBuffer output;
  private final ErrorHandler errors;

  ConsoleStreams(ConsoleOutputBuffer output, ErrorHandler errors) {
    this.output = output;
    this.errors = errors;
  }

  void report(Exception failure) {
    failure.printStackTrace(previousErr);
  }

  /** Redirects standard output and error streams to this console. */
  void redirect() {
    previousOut = System.out;
    previousErr = System.err;

    try {
      redirectedOut = output.createStream(false);
      System.setOut(redirectedOut);
    } catch (SecurityException se) {
      errors.show(
          "Couldn_t_redirect_STDOUT_to_this_console",
          "Couldn't redirect STDOUT to this console",
          se.getMessage());
    }

    try {
      redirectedErr = output.createStream(true);
      System.setErr(redirectedErr);
    } catch (SecurityException se) {
      errors.show(
          "Couldn_t_redirect_STDERR_to_this_console",
          "Couldn't redirect STDERR to this console",
          se.getMessage());
    }
  }

  /** Restores the standard streams that were active before this console redirected them. */
  void restore() {
    if (previousOut != null && System.out == redirectedOut) {
      System.setOut(previousOut);
    }

    if (previousErr != null && System.err == redirectedErr) {
      System.setErr(previousErr);
    }
  }

  /** Closes redirected streams. */
  void close() {
    if (redirectedOut != null) {
      redirectedOut.close();
      redirectedOut = null;
    }

    if (redirectedErr != null) {
      redirectedErr.close();
      redirectedErr = null;
    }
  }
}
