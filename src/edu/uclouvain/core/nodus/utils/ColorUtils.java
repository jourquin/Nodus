/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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

import com.bbn.openmap.util.ColorFactory;
import java.awt.Color;

/**
 * Color manipulation utilities.
 *
 * @author Bart Jourquin
 */
public class ColorUtils {

  /**
   * Returns a Color given its name or ARGB hex representation.
   * @param colorString The name of a color or an ARGB hex value
   * @param defaultColor The Color to return if the decoding fails.
   * @return The Color that was decoded or the default value.
   */
  public static Color getColorFromString(String colorString, Color defaultColor) {

    Color c = ColorFactory.getNamedColor(colorString, null);
    if (c == null) {
      try {
        c = ColorFactory.parseColor(colorString);
      } catch (NumberFormatException e) {
        return defaultColor;
      }
    }
    return c;
  }

  /**
   * Returns a darker shade of the given Color.
   *
   * @param color The Color to start from.
   * @param fraction A "darkness percentage".
   * @return A darker shade of the input Color.
   */
  public static Color darken(Color color, double fraction) {

    int red = (int) Math.round(Math.max(0, color.getRed() - 255 * fraction));
    int green = (int) Math.round(Math.max(0, color.getGreen() - 255 * fraction));
    int blue = (int) Math.round(Math.max(0, color.getBlue() - 255 * fraction));

    int alpha = color.getAlpha();

    return new Color(red, green, blue, alpha);
  }

  /**
   * Generates a palette of colors, starting from an initial color towards darker colors of the same
   * shade.
   *
   * @param startColor The color to start from.
   * @param nbColors The number of color shades to generate.
   * @return An array of colors.
   */
  public static Color[] getShadesPallette(Color startColor, int nbColors) {
    Color[] colors = new Color[nbColors];
    colors[0] = startColor;

    for (int i = 0; i < nbColors; i++) {
      colors[i] =
          darken(
              startColor, (double) i / (double) (nbColors + 3)); // Add 3 to avoid too dark shades
    }
    return colors;
  }
}
