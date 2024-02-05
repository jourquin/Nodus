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

/**
 * MacOS specific utilities.
 *
 * @author Bart Jourquin
 */
public class MacUtils {

  /**
   * Nodus crashes on macOS Sonoma < 14.2 if it runs with an OpenJDK VM provided by Homebrew. The
   * problem is related to the use of screen menu bars.
   *
   * @return True if Nodus runs on such a system.
   */
  public static boolean isSonomaWithHomebrewOpenJDK() {

    if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
      String version = System.getProperty("os.version");
      if (version.startsWith("14.0") || version.startsWith("14.1")) {
        if (System.getProperty("java.vendor").toLowerCase().startsWith("homebrew")) {
          if (System.getProperty("java.vm.name").toLowerCase().startsWith("openjdk")) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
