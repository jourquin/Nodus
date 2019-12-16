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

package javax.media.jai;

/**
 * Just a fake class to avoid a stack trace with some OpenMap layers that test the presence of the
 * Java Advanced Imaging API (JAI). OpenMap tests if JAI is available, but this test sometimes
 * returns a false true in Eclipse. This class permits to avoid this.
 *
 * @author Bart Jourquin
 */
public class JAI {

  /** Empty constructor. */
  public JAI() {

  }

  /**
   * The methods simulates the equivalent JAI method, but always returns null.
   *
   * @param s Not used.
   * @param o Not used.
   * @return Always null.
   */
  public Object create(String s, Object o) {
    return null;
  }
}
