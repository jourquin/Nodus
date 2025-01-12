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

package edu.uclouvain.core.nodus.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Additional functions to be added to the Derby database manager.
 *
 * @author Bart Jourquin
 */
public class NodusDerbyFunctions {

  /** Default constructor. */
  public NodusDerbyFunctions() {}

  /**
   * Equivalent of SQL ROUND function.
   *
   * @param value Value to be rounded
   * @param precision Number of digits after the decimal dot
   * @return Rounded value to given precision
   */
  public static double round(double value, int precision) {

    BigDecimal bd = new BigDecimal(value).setScale(precision, RoundingMode.HALF_EVEN);
    return bd.doubleValue();
  }

  /**
   * For testing purpose...
   *
   * @param args .
   * @hidden
   */
  public static void main(final String[] args) {
    System.out.println(round(12.3, 2));
  }
}
