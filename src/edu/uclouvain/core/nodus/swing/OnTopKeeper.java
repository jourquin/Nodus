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

package edu.uclouvain.core.nodus.swing;

import com.bbn.openmap.Environment;
import com.bbn.openmap.tools.drawing.NodusOMDrawingToolLauncher;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusMapPanel;

import java.awt.Frame;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFrame;

/**
 * This class implements a mechanism that ensures that all the sub-frames remain always on top.
 *
 * @author Bart Jourquin
 */
public class OnTopKeeper {
  private static I18n i18n = Environment.getI18n();

  private NodusMapPanel nodusMapPanel;

  private boolean running = false;

  private Timer timer;

  /**
   * Creates a new OnTopKeeper.
   *
   * @param nodusMapPanel The Nodus map panel.
   */
  public OnTopKeeper(NodusMapPanel nodusMapPanel) {
    this.nodusMapPanel = nodusMapPanel;
  }

  /**
   * Launches a timer which regularly browses the displayed frames and make sure they are kept on
   * top.
   *
   * @param stickyDrawingTool If true, the drawing tool dialog will always be placed at the
   *     bottom-left of the main frame, even if the user tries to move it.
   */
  public void run(final boolean stickyDrawingTool) {

    if (running) {
      stop();
    }

    timer = new java.util.Timer();
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            setAlwaysOnTop(stickyDrawingTool);
          }
        },
        0,
        1000);
    running = true;
  }

  /** Browses the visible sub-frames and keep them on top. */
  private void setAlwaysOnTop(boolean stickyDrawingTool) {

    Frame[] frame = Frame.getFrames();

    for (Frame element : frame) {

      if (element != nodusMapPanel.getMainFrame() && element.isVisible()) {

        if (element.getClass().getName().startsWith("com.bbn.openmap.gui.WindowSupport")) {
          JFrame jf = (JFrame) element;
          if (!jf.isAlwaysOnTop()) {
            jf.setAlwaysOnTop(true);
          }

          // Keep the drawing tool on bottom left...
          if (stickyDrawingTool) {
            if (jf.getTitle()
                .equals(i18n.get(NodusOMDrawingToolLauncher.class, "Map_editor", "Map editor"))) {

              jf.setLocation(
                  nodusMapPanel.getMainFrame().getX() + 20,
                  nodusMapPanel.getMainFrame().getY()
                      + nodusMapPanel.getMainFrame().getHeight()
                      - 50
                      - jf.getHeight());
            }
          }
        }
      }
    }
  }

  /** Stops the timer. */
  public void stop() {
    if (!running) {
      return;
    }
    timer.cancel();
    running = false;
  }
}
