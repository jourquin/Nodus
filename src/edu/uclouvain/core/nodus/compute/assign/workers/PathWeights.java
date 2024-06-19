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

package edu.uclouvain.core.nodus.compute.assign.workers;

/**
 * Convenient class to keep the details of the costs and transit times of a path.
 *
 * @author Bart Jourquin
 */
public class PathWeights {

  /** loading cost. */
  public double ldCost = 0.0;

  /** Moving cost. */
  public double mvCost = 0.0;

  /** Transhipment cost. */
  public double tpCost = 0.0;

  /** Transit cost. */
  public double trCost = 0.0;

  /** Unloading cost. */
  public double ulCost = 0.0;

  /** Stop cost. */
  public double stpCost = 0.0;

  /** Switch cost. */
  public double swCost = 0.0;

  /** loading duration. */
  public float ldDuration = 0;

  /** Moving duration. */
  public float mvDuration = 0;

  /** Transhipment duration. */
  public float tpDuration = 0;

  /** Transit duration. */
  public float trDuration = 0;

  /** Unloading duration. */
  public float ulDuration = 0;

  /** Stop duration. */
  public float stpDuration = 0;

  /** Switch duration. */
  public float swDuration = 0;

  /** Length of the path. */
  public float length;

  /** Default constructor. */
  public PathWeights() {}

  /**
   * Return the total cost of the path, computed as the sum of loading, unloading, transit,
   * transhipment, moving costs, stop and switch costs.
   *
   * @return The total cost.
   */
  public double getCost() {
    return ldCost + ulCost + trCost + tpCost + mvCost + stpCost + swCost;
  }

  /**
   * Return the total length of the path.
   *
   * @return The total cost.
   */
  public float getLength() {
    return length;
  }

  /**
   * Return the total duration of the path, computed as the sum of loading, unloading, transit,
   * transhipment, moving, stop and switch durations.
   *
   * @return The total cost.
   */
  public float getTransitTime() {
    return ldDuration
        + ulDuration
        + trDuration
        + tpDuration
        + mvDuration
        + stpDuration
        + swDuration;
  }
}
