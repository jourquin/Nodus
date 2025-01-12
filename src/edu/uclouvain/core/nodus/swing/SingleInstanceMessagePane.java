/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.swing;

import edu.uclouvain.core.nodus.NodusC;
import java.awt.Component;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * Simple dialog with an information message that can displayed only once. This us useful if an
 * error occurs during a multithreaded assignment.
 *
 * @author Bart Jourquin
 */
public class SingleInstanceMessagePane {

  static JOptionPane pane = null;
  static JDialog dialog = null;

  /** Default constructor. */
  public SingleInstanceMessagePane() {}

  /** Resets the dialog so that it will be displayed on the next call to "display". */
  public static void reset() {
    pane = null;
    dialog = null;
  }

  /**
   * Displays a JOptionPane only when the dialog was not yet created by another task.
   *
   * @param parent The parent component.
   * @param message The message to display.
   * @param messageType The type of message to dispaly (same as in JOptionPane).
   */
  public static void display(Component parent, String message, int messageType) {
    if (dialog == null) {
      dialog = new JDialog();
      pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION, null, null, null);
      dialog = pane.createDialog(parent, NodusC.APPNAME);
      dialog.setLocationRelativeTo(parent);
      dialog.setVisible(true);
    }
  }
}
