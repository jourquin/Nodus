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

package edu.uclouvain.core.nodus.utils;

import java.util.TimerTask;

/**
 * Simple utility that launches a garbage collector task at a given interval.
 *
 * @author Bart Jourquin
 */
public class GarbageCollectionRunner {

  private java.util.Timer timer = null;

  /**
   * Creates a new garbage collector task with a given interval.
   *
   * @param interval Interval in seconds
   */
  public GarbageCollectionRunner(int interval) {

    // No forced gc
    if (interval == 0) {
      return;
    }

    timer = new java.util.Timer();
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            System.gc();
          }
        },
        0,
        1000 * interval);
  }

  /** Stops the garbage collector scheduled task. */
  public void stop() {
    if (timer != null) {
      timer.cancel();
    }
  }
}
