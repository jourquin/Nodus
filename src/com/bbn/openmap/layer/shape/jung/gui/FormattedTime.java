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

package com.bbn.openmap.layer.shape.jung.gui;

import java.text.DecimalFormat;

/**
 * Simple class that maintains a formatted hour (string) associated to an int value (in seconds).
 *
 * @author Bart Jourquin
 */
class FormattedTime extends Object {
  private String label;
  private int seconds;

  /**
   * Creates a string representing hours, minutes and seconds from a duration expressed in seconds.
   *
   * @param seconds The amount of seconds to change in a readable time.
   */
  public FormattedTime(int seconds) {
    this.seconds = seconds;
    DecimalFormat hourFormatter = new DecimalFormat("00");
    int hour = seconds / 60 % 24;
    int min = seconds % 60;
    label = hourFormatter.format(hour) + ":" + hourFormatter.format(min);
  }

  /**
   * Returns the time expressed in seconds.
   *
   * @return The time expressed in seconds.
   */
  public int getSeconds() {
    return seconds;
  }

  /** Returns a HH:MM:SS string representation of the amount of seconds . */
  @Override
  public String toString() {
    return label;
  }
}
