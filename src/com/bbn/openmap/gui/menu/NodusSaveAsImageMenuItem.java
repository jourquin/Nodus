/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package com.bbn.openmap.gui.menu;

import com.bbn.openmap.MapBean;
import com.bbn.openmap.image.AbstractImageFormatter;

import java.awt.event.ActionEvent;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * This class is used to remove the border that could be associated to the MapBean before saving its
 * content as an image.
 *
 * @author Bart Jourquin
 */
public class NodusSaveAsImageMenuItem extends SaveAsImageMenuItem {

  private static final long serialVersionUID = -3297065152534869002L;

  /**
   * Calls the constructor of the super class. Needed because no default constructor exists in super
   * class.
   *
   * @param display A String that will be displayed when this menuitem is shown in GUI
   * @param inFormatter A formatter that knows how to generate an image from MapBean.
   */
  public NodusSaveAsImageMenuItem(String display, AbstractImageFormatter inFormatter) {
    super(display, inFormatter);
  }

  /** Removes the border before calling the original method, then restores the border. */
  @Override
  public void actionPerformed(ActionEvent ae) {

    // Save the current border and remove it
    MapBean mb = (MapBean) mapHandler.get("com.bbn.openmap.MapBean");
    if (mb != null) {
      // Remove the border
      Border oldBorder = mb.getBorder();
      mb.setBorder(new EmptyBorder(0, 0, 0, 0));

      super.actionPerformed(ae);

      // Reset the original border
      mb.setBorder(oldBorder);
    }
  }
}
