/*
 * Copyright (c) 1991-2023 Université catholique de Louvain
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

import java.awt.Dimension;
import java.awt.Toolkit;
import javax.swing.JDialog;

/**
 * A few convenient static methods used in Swing related java code.
 *
 * @author Bart Jourquin
 */
public class GUIUtils {

  /**
   * Recompute a new position of a dialog in order to keep it entirely in the screen.
   *
   * @param dialog The JDialog to control.
   */
  public static void keepDialogInScreen(JDialog dialog) {
    // Be sure the dialog is completely visible
    boolean move = false;
    Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
    int x = dialog.getX();
    int y = dialog.getY();

    if (x + dialog.getWidth() > d.width) {
      x = d.width - dialog.getWidth();
      move = true;
    }

    if (y + dialog.getHeight() > d.height) {
      y = d.height - dialog.getHeight();
      move = true;
    }

    if (x < 0) {
      x = 0;
      move = true;
    }

    if (y < 0) {
      y = 0;
      move = true;
    }

    if (move) {
      dialog.setLocation(x, y);
    }
  }

}
