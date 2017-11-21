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

package edu.uclouvain.core.nodus.compute.virtual;

/**
 * Convenient class used to keep information about one of the alternatives paths for an OD pair.
 * Variables are kept public for performance reasons.
 *
 * @author Bart Jourquin
 */
public class PathODCell {

  /** The # of the path alternative. */
  public int alternativePath;

  /**
   * The index of the OD cell in the current OD matrix line (0 for the Exact MF, because computed
   * cell by cell).
   */
  public int indexInODLine;

  /** total quantity of the OD cell. */
  public double quantity;

  /**
   * Initializes the class for an Exact Multi-Flow assignment.
   *
   * @param alternativePath The # of the path alternative.
   * @param quantity Quantity to spread over all the alternative paths (total quantity of the OD
   *     cell).
   */
  public PathODCell(int alternativePath, double quantity) {
    this(alternativePath, 0, quantity);
  }

  /**
   * Initializes the class for a Fast Multi-Flow assignment.
   *
   * @param alternativePath The # of the path alternative.
   * @param indexInODLine The index of the OD cell in the current OD matrix line.
   * @param quantity Quantity to spread over all the alternative paths (total quantity of the OD
   *     cell).
   */
  public PathODCell(int alternativePath, int indexInODLine, double quantity) {
    this.quantity = quantity;
    this.alternativePath = alternativePath;
    this.indexInODLine = indexInODLine;
  }
}
