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

package com.bbn.openmap.event;

import com.bbn.openmap.proj.ProjectionStack;
import java.awt.event.KeyEvent;

/**
 * Add keyboard shortcuts to the ProjMapBeanKeyListener.
 *
 * <br>
 * "+" to zoom in
 *
 * <br>
 * "-" to zoom out
 *
 * <br>
 *  "Left arrow" for previous view in stack
 *
 * <br> 
 * "Right arrow" for next view in stack
 *
 * @author Bart Jourquin
 */
public class NodusProjMapBeanKeyListener extends ProjMapBeanKeyListener {
    
    /** Default constructor. */
    public NodusProjMapBeanKeyListener() {
      super();
    }

  /** Add additional key codes to the key listener. */
  @Override
  public void keyReleased(KeyEvent e) {
    super.keyReleased(e);

    int keyCode = e.getKeyCode();

    // Additional keys...
    switch (keyCode) {
      case KeyEvent.VK_ADD:
        zoomers.fireZoom(ZoomEvent.RELATIVE, 1f / zoomFactor);
        break;

      case KeyEvent.VK_SUBTRACT:
        zoomers.fireZoom(ZoomEvent.RELATIVE, zoomFactor);
        break;

      case KeyEvent.VK_LESS:
        fireProjectionStackEvent(ProjectionStack.BackProjCmd);
        break;

      case KeyEvent.VK_GREATER:
        fireProjectionStackEvent(ProjectionStack.ForwardProjCmd);
        break;

      default:
        break;
    }
  }
}
