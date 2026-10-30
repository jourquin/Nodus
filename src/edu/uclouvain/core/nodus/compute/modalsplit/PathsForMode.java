/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.modalsplit;

import edu.uclouvain.core.nodus.compute.assign.workers.PathWeights;
import java.util.LinkedList;

/**
 * Data structure that contains a list of alternative paths for a given (intermodal) mode and an
 * Origin-Destination pair. It just contains public variables. Getters and setters are not used to
 * improve computing performance.
 *
 * @author Bart Jourquin
 */
public class PathsForMode {

  /** Loading mode for this set of alternative paths. */
  public int loadingMode;

  /** Market marketShare assigned to this set of alternative paths. */
  public double marketShare;

  /** Detailed costs of the cheapest path in the list of alternatives. */
  public PathWeights cheapestPathWeights;

  /** List of alternative paths for this mode. */
  public LinkedList<Path> pathList = new LinkedList<>();

  /** Value of the utility given to the cheapest path of the list. */
  public double utility;

  /** Weight (cost) of the cheapest path in the list of alternatives. */
  private double cheapestPathTotalCost = Double.MAX_VALUE;

  /**
   * Initializes a valid paths list.
   *
   * @param path The first path of the set of alternative paths.
   */
  public PathsForMode(Path path) {
    this.loadingMode = path.loadingMode;
    this.cheapestPathTotalCost = path.weights.getCost();
    this.cheapestPathWeights = path.weights;
    this.pathList.add(path);
  }

  /**
   * Adds a new path to the list of alternatives.
   *
   * @param path The new alternative path to add.
   */
  public void addPath(Path path) {
    // Track the cheapest alternative
    if (this.cheapestPathTotalCost > path.weights.getCost()) {
      this.loadingMode = path.loadingMode;
      this.cheapestPathTotalCost = path.weights.getCost();
      this.cheapestPathWeights = path.weights;
    }

    this.pathList.add(path);
  }
}
