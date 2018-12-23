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

package com.bbn.openmap.tools.drawing;

import com.bbn.openmap.omGraphics.OMGraphic;

/**
 * Convenience class that us used to store the information used to retrieve a given link or node:
 * the layer and the index into this layer.
 *
 * @author Bart Jourquin
 * @exclude
 */
class GraphicLocation {
  /** Index of the layer that contains the object. */
  public int indexInLayer = -1;

  /** Index of the object into its layer. */
  public int indexOfLayer = -1;

  /** Graphic object. */
  public OMGraphic omg = null;
}
