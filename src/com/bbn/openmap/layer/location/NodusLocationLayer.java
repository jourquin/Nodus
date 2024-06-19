/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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

package com.bbn.openmap.layer.location;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import javax.swing.Box;

/**
 * The NodusLocationLayer is a LocationLayer that handles the labels for the different
 * NodusEsriLayer (nodes and links) associated to the project.
 *
 * @author Bart Jourquin
 */
public class NodusLocationLayer extends LocationLayer {

  static final long serialVersionUID = 7393919340553905118L;

  //    public NodusLocationLayer() {
  //
  //      RenderingHints rh =
  //          new RenderingHints(RenderingHints.KEY_ANTIALIASING,
  // RenderingHints.VALUE_ANTIALIAS_ON);
  //      RenderingHintsRenderPolicy rp = new RenderingHintsRenderPolicy(this);
  //      rp.setRenderingHints(rh);
  //      setRenderPolicy(rp);
  //
  //      setMouseModeIDsForEvents(new String[] {"Gestures"});
  //    }

  /** Default constructor. */
  public NodusLocationLayer() {
    super();
  }
  
  /**
   * Initializes the location handler with all the nodes and link layers of the project.
   *
   * @param nodeHandlers NodusLocationHandler[]
   * @param linkHandlers NodusLocationHandler[]
   */
  public void addLocationHandler(
      NodusLocationHandler[] nodeHandlers, NodusLocationHandler[] linkHandlers) {

    if (nodeHandlers.length == 0) {
      return;
    }

    int size = nodeHandlers.length + linkHandlers.length;
    int i;

    LocationHandler[] lh = new LocationHandler[size];
    String[] lhn = new String[size];

    for (i = 0; i < nodeHandlers.length; i++) {
      lh[i] = nodeHandlers[i];
      lhn[i] = nodeHandlers[i].getTableName();
      nodeHandlers[i].setPrettyName(nodeHandlers[i].getTableName());
    }

    int offset = nodeHandlers.length;

    for (i = 0; i < linkHandlers.length; i++) {
      lh[i + offset] = linkHandlers[i];
      lhn[i + offset] = linkHandlers[i].getTableName();
      linkHandlers[i].setPrettyName(linkHandlers[i].getTableName());
    }

    setLocationHandlers(lh);
  }

  /**
   * Returns the original GUI, but limits its display size, as it can contain a long list of layers.
   *
   * @return Component
   */
  @Override
  public Component getGUI() {
    Box c = (Box) super.getGUI();
    c.setPreferredSize(new Dimension(250, 500));
    return c;
  }

  /**
   * Intercept right-button clicks, as they are intended for the nodes and links layers.
   *
   * @param e MouseEvent
   * @return boolean
   * @hidden
   */
  public boolean mousePressed(MouseEvent e) {

    return false;
  }

}
