/*
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

package com.bbn.openmap.omGraphics.event;

import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import java.awt.event.MouseEvent;
import javax.swing.ToolTipManager;

/**
 * This MapMouseInterpreter overrides the StandardMapMouseInterpreter in order not to display
 * tooltips for background layers.
 *
 * @author Bart Jourquin
 */
public class NodusMapMouseInterpreter extends StandardMapMouseInterpreter {

  /** Just calls the original constructor. */
  public NodusMapMouseInterpreter(OMGraphicHandlerLayer l) {
    super(l);
  }

  /**
   * Given a tool tip String, use the layer to get it displayed, but only if the latest is not a
   * background layer.
   */
  @Override
  protected void handleToolTip(String tip, MouseEvent me) {
    if (layer.getAddAsBackground()) {
      return;
    }

    // Force to wait for a delay before another tooltip is displayed
    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
    toolTipManager.mousePressed(null);
    toolTipManager.setReshowDelay(0);

    super.handleToolTip(tip, me);
  }
}
