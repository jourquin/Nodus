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

package edu.uclouvain.core.nodus.utils;

/**
 * Convenience class to test Java version against a target version. Inspired from Apache lang
 * SystemUtils.
 *
 * @author Bart Jourquin
 */
public class JavaVersionUtil {

  /**
   * Is the Java version at least the requested version.
   *
   * <p>Example input:
   *
   * <ul>
   *   <li><code>6.2f</code> to test for Java 6.2
   *   <li><code>8.31f</code> to test for Java 8.3.1
   * </ul>
   *
   * @param requiredVersion the required version, for example 8.31f
   * @return <code>true</code> if the actual version is equal or greater than the required version
   */
  public static boolean isJavaVersionAtLeast(float requiredVersion) {

    int[] javaVersions = getJavaVersionIntArray();

    float version;

    if (javaVersions.length == 1) {
      version = javaVersions[0];
    } else {
      StringBuffer builder = new StringBuffer();
      builder.append(javaVersions[0]);
      builder.append('.');
      for (int i = 1; i < javaVersions.length; i++) {
        builder.append(javaVersions[i]);
      }
      try {
        version = Float.parseFloat(builder.toString());
      } catch (Exception ex) {
        version = 0f;
      }
    }

    //System.out.println(version);

    return version >= requiredVersion;
  }

  /**
   * Converts the given Java version string to an array of maximum 3 elements.
   *
   * @return the version, for example [1, 5, 0] for Java 1.5.0
   */
  private static int[] getJavaVersionIntArray() {

    String version = System.getProperty("java.version");

    String[] strings = version.split("\\.|_|-|\\s");
    int[] ints = new int[Math.min(3, strings.length)];
    int j = 0;
    for (int i = 0; i < strings.length && j < 3; i++) {
      String s = strings[i];
      if (s.length() > 0) {
        ints[j] = Integer.parseInt(s);
        j++;
      }
    }
    if (ints.length > j) {
      int[] newInts = new int[j];
      System.arraycopy(ints, 0, newInts, 0, j);
      ints = newInts;
    }
    return ints;
  }
}
