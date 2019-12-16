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

package edu.uclouvain.core.nodus.compute.real;

import com.bbn.openmap.layer.location.BasicLocation;

/**
 * Container abstract class that holds all the relevant information relative to the real network
 * objects.
 *
 * @author Bart Jourquin
 */
public abstract class RealNetworkObject {

  private boolean inHighlightedArea;

  private BasicLocation location = null;

  private double result = 0.0;

  private float size = 0;

  /**
   * Returns the OpenMap BasicLocation associated to this real network object.
   *
   * @return The BasicLocation
   */
  public BasicLocation getLocation() {
    return location;
  }

  /**
   * Returns the result value associated to this real network object (used when OD matrices or flows
   * are displayed on the map).
   *
   * @return The value associated to this real network object.
   */
  public double getResult() {
    return result;
  }

  /**
   * Returns the size (width or radius) associated to this real network object.
   *
   * @return The display size (pixels)
   */
  public float getSize() {
    return size;
  }

  /**
   * Returns true if this real network object is located in the highlighted area.
   *
   * @return True if located in the highlighted area.
   */
  public boolean isInHighlightedArea() {
    return inHighlightedArea;
  }

  /**
   * Tells this real network object if it is located in the highlighted area or not.
   *
   * @param inHighlightedArea True if the object is located in the highlighted area.
   */
  public void setInHighlightedArea(boolean inHighlightedArea) {
    this.inHighlightedArea = inHighlightedArea;
  }

  /**
   * Associates an OpenMap BasicLocation to this real network object.
   *
   * @param location The BasicLocation to associate to this object.
   */
  public void setLocation(BasicLocation location) {
    this.location = location;
  }

  /**
   * Associates a result value to this real network object.
   *
   * @param result The numeric value to associate to this real network object.
   */
  public void setResult(double result) {
    this.result = result;
  }

  /**
   * Associates a size (width or radius) to this real network object.
   *
   * @param size The display size of this real network object (pixels).
   */
  public void setSize(float size) {
    this.size = size;
  }
}
