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

package edu.uclouvain.core.nodus.utils;

import java.text.NumberFormat;

/**
 * Convenience set of string related operations.
 *
 * @author Bart Jourquin
 */
public class StringUtils {

  private static NumberFormat nf = NumberFormat.getInstance();

  /** Default constructor. */
  public StringUtils() {}

  /**
   * Returns a string that represents the given numeric value with a given length and precision.
   *
   * @param value The value to convert
   * @param length The length of the string
   * @param precision The precision (number of digits after decimal det)
   * @return String
   */
  public static String getFormatedNum(double value, int length, int precision) {
    nf.setMaximumFractionDigits(precision);
    nf.setMinimumFractionDigits(precision);

    int n = 0;

    if (precision > 0) {
      n = precision + 1;
    }

    nf.setMinimumIntegerDigits(length - n);
    nf.setMaximumIntegerDigits(length - n);

    return nf.format(value);
  }

  /**
   * Returns true if a given string contains a valid numeric value.
   *
   * @param number The string to test.
   * @return True if the string contains a valid numeric value.
   */
  public static boolean isNumeric(String number) {
    boolean result = false;

    try {
      if (number != null) {
        Double.parseDouble(number);
        result = true;
      }
    } catch (NumberFormatException ex) {
      // sCheck is not numeric
    }

    return result;
  }
}
