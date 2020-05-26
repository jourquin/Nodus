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

package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.compute.od.ODCell;

/**
 * Simple utility class that is used in multi-flow assignment algorithms to store the header
 * information of a path.
 *
 * @author Bart Jourquin
 */
class MFPathHeader {
  /** OD cell. */
  public ODCell demand;

  /** Index in path header table. */
  public int index;

  /** Alternative route number. */
  public int iteration;

  /** Means at loading time. */
  public byte loadingMeans;

  /** Mode at loading time. */
  public byte loadingMode;

  /** Number of transhipments along the route. */
  public int nbTranshipments;

  /** Detailed costs, durations and route length. */
  public PathWeights weights;

  /** Means at unloading time. */
  public byte unloadingMeans;

  /** Mode at unloading time. */
  public byte unloadingMode;

  /**
   * Contains the information that will be stored in the path header table.
   *
   * @param iteration Alternative route number.
   * @param demand OD cell.
   * @param weights Detailed costs, durations and route length.
   * @param loadingMode Transport mode at loading.
   * @param loadingMeans Transport means at loading.
   * @param unloadingMode Transport mode at unloading.
   * @param unloadingMeans Transport means at unloading.
   * @param nbTranshipments Number of transhipments along the route.
   * @param index Index in path header table.
   */
  public MFPathHeader(
      int iteration,
      ODCell demand,
      PathWeights weights,
      byte loadingMode,
      byte loadingMeans,
      byte unloadingMode,
      byte unloadingMeans,
      int nbTranshipments,
      int index) {
    this.iteration = iteration;
    this.demand = demand;
    this.weights = weights;
    this.loadingMode = loadingMode;
    this.loadingMeans = loadingMeans;
    this.unloadingMode = unloadingMode;
    this.unloadingMeans = unloadingMeans;
    this.nbTranshipments = nbTranshipments;
    this.index = index;
  }
}
