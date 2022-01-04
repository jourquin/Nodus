/*
 * Copyright (c) 1991-2022 Université catholique de Louvain
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

package com.bbn.openmap.layer.shape.displayindex;

import com.bbn.openmap.io.FormatException;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.util.DataBounds;
import java.io.IOException;

/**
 * A Spatial Index is a variation on a Shape Index, adding the bounding box.
 *
 * @see DisplaySpatialIndexLinear
 */
public class DisplaySpatialIndexLinear extends DisplaySpatialIndexImpl {

  /**
   * Constructor .
   *
   * @param list Graphic list
   */
  public DisplaySpatialIndexLinear(OMGraphicList list) {
    super(list);
  }

  /** A pretty name for the boundary, suitable for a GUI. */
  @Override
  public String getName() {
    return "SpatialIndexLinear";
  }

  /**
   * Creates a subset.
   *
   * @exception IOException if something goes wrong reading the files
   */
  @Override
  public DisplaySpatialIndex getSubsetSet(DataBounds area) throws IOException, FormatException {
    return null;
  }
}
