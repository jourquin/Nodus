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

package edu.uclouvain.core.nodus.compute.assign.modalsplit;

import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;

/**
 * Data structure that contains the details of a computed path. It just contains public variables.
 * Getters and setters are not used to improve computing performance.
 *
 * @author Bart Jourquin
 */
public class Path {

  /** True for a valid path (not a duplicate for instance). */
  public boolean isValid = true;

  /** True if the path is intermodal. False by default. */
  public boolean intermodal = false;

  /**
   * Hash key associated to the list of virtual links that are encountered along this path. This is
   * used to detect identical paths.
   */
  public double detailedPathKey = 0.0;

  /** Market share of this path among all the alternative paths. */
  public double marketShare = 0;

  /** Transport mode used at the beginning of the path. */
  public byte loadingMode;

  /** Transport means used at the beginning of the path. */
  public byte loadingMeans;

  /**
   * Hash key that represents the the combination of modes used along the path. Is equivalent to
   * loadingMode if this path only uses one mode.
   */
  public int intermodalModeKey = -1; // noMode;

  /**
   * Detailed costs (loading, unloading, transit, transhipment, moving and total). Is used only when
   * detailed costs are asked for the assignment.
   */
  public PathWeights weights;
  
  /** Default constructor. */
  public Path() {}
}
