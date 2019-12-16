/**
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.database.dbf;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Used to indicate a fatal error, while writing or reading a database (DBF) file.
 *
 * <p>Adapted from original free code by SV Consulting (not existing anymore).
 *
 * @author Bart Jourquin
 */
public class DBFException extends Exception {

  private static final long serialVersionUID = 8679914878823424570L;

  private Throwable detail = null;

  /**
   * Constructs a JDBFException with a given message.
   *
   * @param message The error message.
   */
  public DBFException(String message) {
    this(message, null);
  }

  /**
   * Constructs a nested JDBFException, containing another exception and a specific message.
   *
   * @param message The error message
   * @param throwable A Throwable.
   */
  public DBFException(String message, Throwable throwable) {
    super(message);
    detail = throwable;
  }

  /**
   * Constructs a nested JDBFException, containing another exception.
   *
   * @param throwable A Throwable
   */
  public DBFException(Throwable throwable) {
    this(throwable.getMessage(), throwable);
  }

  /**
   * Returns the message for the exception.
   *
   * @return The error message.
   */
  @Override
  public String getMessage() {
    if (detail == null) {
      return super.getMessage();
    } else {
      return super.getMessage();
    }
  }

  /** Prints the stack of the exception to the standard error. */
  @Override
  public void printStackTrace() {
    this.printStackTrace(System.err);
  }

  /**
   * Prints the stack of the exception.
   *
   * @param printstream The PrintStream used for output.
   */
  @Override
  public void printStackTrace(PrintStream printstream) {
    if (detail == null) {
      super.printStackTrace(printstream);
    } else {
      synchronized (printstream) {
        printstream.println(this);
        detail.printStackTrace(printstream);
      }
    }
  }

  /**
   * Prints the stack of the exception.
   *
   * @param printwriter The PrintWriter used for output.
   */
  @Override
  public void printStackTrace(PrintWriter printwriter) {
    if (detail == null) {
      super.printStackTrace(printwriter);
    } else {
      synchronized (printwriter) {
        printwriter.println(this);
        detail.printStackTrace(printwriter);
      }
    }
  }
}
