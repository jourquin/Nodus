/**
 * Copyright (c) 1991-2019 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.assign.workers;

/**
 * Convenient class to keep the details of the costs of a path.
 *
 * @author Bart Jourquin
 */
public class PathDetailedCosts {

  /** loading cost. */
  public double ldCosts = 0;

  /** Moving cost. */
  public double mvCosts = 0;

  /** Transhipment cost. */
  public double tpCosts = 0;

  /** Transit cost. */
  public double trCosts = 0;

  /** Unloading cost. */
  public double ulCosts = 0;

  /**
   * Return the total cost of the path, computed as the sum of loading, unloading, transit,
   * transhipment and moving costs.
   *
   * @return The total cost.
   */
  public double getTotalCost() {
    return ldCosts + ulCosts + trCosts + tpCosts + mvCosts;
  }
}
