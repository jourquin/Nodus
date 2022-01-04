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

package com.bbn.openmap.layer.image.capabilities;

import java.util.Vector;

/**
 * Contains information about a given inner node parsed from a getCapabilities request.
 *
 * @author Bart Jourquin
 */
public class CapabilitiesInnerTreeNode extends Vector<Object>
    implements ICapabilitiesNodeInterface {

  static final long serialVersionUID = -4330883022280828609L;

  /** Will hold the real content. */
  transient WmsLayerInfo content;

  /**
   * Builds a new inner node with given content.
   *
   * @param content WmsLayerInfo
   */
  public CapabilitiesInnerTreeNode(WmsLayerInfo content) {
    this.content = content;
  }

  /**
   * Returns the content hold by this node.
   *
   * @return WmsLayerInfo
   */
  @Override
  public WmsLayerInfo getLayerInformation() {
    return content;
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public String toString() {
    return content.toString();
  }
}
