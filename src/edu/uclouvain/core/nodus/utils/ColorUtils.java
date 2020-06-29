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

package edu.uclouvain.core.nodus.utils;

import java.awt.Color;

/**
 * Color manipulation utilities.
 *
 * @author Bart Jourquin
 */
public class ColorUtils {

  /**
   * Generates a palette of colors, starting from an initial color towards darker colors of the same
   * dominant RGB component.
   *
   * @param startColor The color to start from.
   * @param nbColors The number of color shades to generate.
   * @return An array of colors.
   */
  public static Color[] generateColorPalette(Color startColor, int nbColors) {
    Color[] colors = new Color[nbColors];
    colors[0] = startColor;

    float[] rgbComponents = startColor.getRGBColorComponents(null);

    // Gray ?
    if (isGray(startColor)) {
      return generateGrayPalette(rgbComponents[0], nbColors);
    }

    // Identify dominant color
    int dominantColorIndex = -1;
    float dominantColorValue = -1;
    for (int i = 0; i < rgbComponents.length; i++) {
      if (rgbComponents[i] > dominantColorValue) {
        dominantColorValue = rgbComponents[i];
        dominantColorIndex = i;
      }
    }

    // Step towards darker colors
    float relativeStepSize = dominantColorValue / (nbColors + 1);
    float decrement = dominantColorValue * relativeStepSize;

    // Generate colors
    for (int i = 0; i < nbColors - 1; i++) {
      float[] c = rgbComponents.clone();
      c[dominantColorIndex] = dominantColorValue - (i + 1) * decrement;
      colors[i + 1] = new Color(c[0], c[1], c[2]);
    }
    return colors;
  }

  /**
   * Generate a palette of grays, starting from a start level towards darker tones. The start level
   * must be between 0 (black) and 1 (white).
   *
   * @param startLevel Start level between black (0) and white (1).
   * @param nbColors The number of gray shades to generate.
   * @return An array of colors or null if startLevel is invalid.
   */
  public static Color[] generateGrayPalette(float startLevel, int nbColors) {

    // If invalid start level
    if (startLevel < 0 || startLevel > 1) {
      return null;
    }

    Color[] colors = new Color[nbColors];

    // Step towards darker colors
    float relativeStepSize = startLevel / nbColors;
    float decrement = startLevel * relativeStepSize;

    // Generate colors
    for (int i = 0; i < nbColors; i++) {
      for (int j = 0; j < 3; j++) {
        float value = startLevel - i * decrement;
        colors[i] = new Color(value, value, value);
      }
    }
    return colors;
  }

  /**
   * Tests if a given color is a shade of gray.
   *
   * @param color The Color to test.
   * @return True if color is a shade of gray.
   */
  public static boolean isGray(Color color) {
    // Gray when RGB components are identical
    if (color.getBlue() == color.getGreen()) {
      if (color.getBlue() == color.getRed()) {
        return true;
      }
      return false;
    }
    return false;
  }
}
