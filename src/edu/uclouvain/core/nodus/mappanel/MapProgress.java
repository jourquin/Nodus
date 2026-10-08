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

package edu.uclouvain.core.nodus.mappanel;

import com.bbn.openmap.Environment;
import com.bbn.openmap.InformationDelegator;
import com.bbn.openmap.event.ProgressEvent;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusMapPanel;
import java.awt.Color;
import java.awt.Cursor;
import javax.swing.JOptionPane;

/** Tracks progress, nested busy state and cancellation for one map panel. */
public final class MapProgress {
  /**
   * Control variables for the progress bar, as the "setBusy" method can be called several times
   * with "true".
   */
  private int busyDepth = 0;
  /** Cancellation requested by the UI and checked by the thread performing the task. */
  private volatile boolean canceled;
  /** Control variables for the progress bar. */
  private int currentTask = 0;
  /** Control variables for the displayed cursor. */
  private Cursor defaultBeanCursor = null;
  /** Control variables for the progress bar. */
  private volatile int taskLength = 0;
  /** True when task duration is unknown and progress checks must not advance a percentage. */
  private boolean indeterminateProgress;
  /** True while the portable activity animation replaces the native macOS progress renderer. */
  private boolean portableProgressUI;

  private static final I18n i18n = Environment.getI18n();
  private final NodusMapPanel panel;
  private final InformationDelegator infoDelegator;

  public MapProgress(NodusMapPanel panel, InformationDelegator infoDelegator) {
    this.panel = panel;
    this.infoDelegator = infoDelegator;
  }

  /**
   * Cancels a ProgressBar. See OpenMap documentation for more details on the progress bar mechanism
   * implemented on the MapBean.
   */
  public void cancelLongTask() {
    if (taskLength > 0) { // If a long task is running
      java.awt.Toolkit.getDefaultToolkit().beep();
      canceled = true;
    }
  }

  /**
   * Sets the wait cursor in the MapPanel.
   *
   * @param busy If true, set the wait cursor, else sets the default cursor.
   */
  public void setBusy(boolean busy) {

    if (busy) {
      busyDepth++;
    } else {
      busyDepth--;
    }

    if (busyDepth < 0) {
      busyDepth = 0;
    }

    if (busy && busyDepth == 1) {
      defaultBeanCursor = panel.getMapBean().getCursor();
      panel.getRootPane().getGlassPane().setVisible(true);
      panel.getRootPane().getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    } else if (!busy && busyDepth == 0) {
      panel
          .getRootPane()
          .getGlassPane()
          .setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      panel.getRootPane().getGlassPane().setVisible(false);
      panel.getMapBean().setCursor(defaultBeanCursor);
    }
  }

  /**
   * Starts a new ProgressBar. See OpenMap documentation for more details on the progress bar
   * mechanism implemented on the MapBean.
   *
   * @param finishedValue The max value to reach; zero or negative selects an activity indicator for
   *     work whose total is unknown.
   */
  public synchronized void startProgress(int finishedValue) {
    taskLength = Math.max(1, finishedValue);
    indeterminateProgress = finishedValue <= 0;
    currentTask = 0;
    canceled = false;
    panel.setBusy(true);

    ProgressEvent evt =
        new ProgressEvent(panel.getMapBean(), ProgressEvent.START, "", taskLength, 0);
    displayProgress(evt, indeterminateProgress);
  }

  /**
   * Ends a ProgressBar. See OpenMap documentation for more details on the progress bar mechanism
   * implemented on the MapBean.
   */
  public synchronized void stopProgress() {
    ProgressEvent evt = new ProgressEvent(panel.getMapBean(), ProgressEvent.DONE, "", 0, 0);
    indeterminateProgress = false;
    displayProgress(evt, false);
    panel.resetText();
    taskLength = 0;
    currentTask = 0;
    panel.setBusy(false);
  }

  /**
   * Advances progress by one step, refreshing the display only at the requested interval.
   *
   * <p>Every call still checks cancellation. Determinate tasks count each call as a step;
   * indeterminate tasks update only their status text. Expensive loops can avoid sending a GUI
   * event for every item while retaining accurate progress and prompt cancellation checks. The
   * first and final steps are always displayed. Large tasks also limit display events so the Swing
   * queue can keep up with parallel workers. Calls are serialized to preserve every count and the
   * order of progress events.
   *
   * @param msg The message to display.
   * @param displayInterval Number of steps between display updates; values below one mean one.
   * @return False if the user confirmed cancellation.
   */
  public synchronized boolean updateProgress(String msg, int displayInterval) {
    if (canceled) {
      canceled = false;
      if (JOptionPane.showConfirmDialog(
              panel,
              i18n.get(NodusMapPanel.class, "Abort_task?", "Abort task?"),
              "Nodus",
              JOptionPane.YES_NO_OPTION)
          == JOptionPane.YES_OPTION) {

        busyDepth = 1;
        panel.stopProgress();
        panel.setText(i18n.get(NodusMapPanel.class, "Task_aborted", "Task aborted"));

        return false;
      }
    }

    if (!indeterminateProgress) {
      currentTask++;
    }
    int effectiveInterval = Math.max(Math.max(1, displayInterval), taskLength / 250);
    if (effectiveInterval > 1
        && currentTask > 1
        && currentTask < taskLength
        && currentTask % effectiveInterval != 0) {
      return true;
    }

    ProgressEvent evt =
        new ProgressEvent(
            panel.getMapBean(), ProgressEvent.UPDATE, "  " + msg, taskLength, currentTask);
    displayProgress(evt, indeterminateProgress);

    return true;
  }

  /** Preserves status-message ordering and updates the activity animation on the Swing thread. */
  private void displayProgress(ProgressEvent event, boolean indeterminate) {
    Runnable update =
        () -> {
          // OpenMap changes the Swing model synchronously. Keep those changes on the EDT along
          // with renderer replacement, or Aqua's model listener can run during UI uninstallation.
          infoDelegator.updateProgress(event);
          if (event.getType() == ProgressEvent.UPDATE) {
            return;
          }
          javax.swing.JProgressBar bar = infoDelegator.getProgressBar();
          if (bar != null) {
            // Aqua's native indeterminate bar can appear empty on macOS. Swing's basic renderer
            // paints a moving segment itself, independently of the native animation support.
            if (indeterminate
                && "com.apple.laf.AquaProgressBarUI".equals(bar.getUI().getClass().getName())) {
              bar.setUI(new javax.swing.plaf.basic.BasicProgressBarUI());
              // Aqua declares a black foreground because its native renderer supplies the accent
              // itself. Use the system selection accent for the portable renderer instead.
              Color accent = javax.swing.UIManager.getColor("List.selectionBackground");
              bar.setForeground(
                  new javax.swing.plaf.ColorUIResource(
                      accent != null ? accent : new Color(0, 122, 255)));
              portableProgressUI = true;
            } else if (!indeterminate && portableProgressUI) {
              bar.setIndeterminate(false);
              bar.updateUI();
              portableProgressUI = false;
            }
            bar.setIndeterminate(indeterminate);
            bar.setString(indeterminate ? "" : null);
          }
        };
    if (javax.swing.SwingUtilities.isEventDispatchThread()) {
      update.run();
    } else {
      javax.swing.SwingUtilities.invokeLater(update);
    }
  }

  public boolean isBusy() {
    return busyDepth > 0;
  }
}
