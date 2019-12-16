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

package com.bbn.openmap.layer.shape.jung;

import edu.uclouvain.core.nodus.NodusC;

/**
 * Convenient class that holds information about virtual nodes to draw in a JUNG graph.
 *
 * @author Bart Jourquin
 */
public class JungVirtualNode implements Comparable<Object> {

  private int linkId;

  private byte meansId;

  private byte modeId;

  private int nodeId;

  private int serviceId;

  /**
   * Creates a new JungVirtualNode.
   *
   * @param nodeId The ID of the "node" element of the virtual node.
   * @param linkId The ID of the "link" element of the virtual node.
   * @param modeId The ID of the "mode" element of the virtual node.
   * @param meansId The ID of the "means" element of the virtual node.
   * @param serviceId The ID of the "service" element of the virtual node.
   */
  public JungVirtualNode(int nodeId, int linkId, byte modeId, byte meansId, int serviceId) {
    this.nodeId = nodeId;
    this.linkId = linkId;
    this.modeId = modeId;
    this.meansId = meansId;
    this.serviceId = serviceId;
  }

  /**
   * Compares this JungVirtualNode to another.
   *
   * @param object The object to be compared.
   * @return A negative integer, zero, or a positive integer as this object is less than, equal to,
   *     or greater than the specified object.
   */
  @Override
  public int compareTo(Object object) {
    return toString().compareTo(((JungVirtualNode) object).toString());
  }

  /**
   * Returns the "link" element of the virtual node.
   *
   * @return The ID of the "link" element.
   */
  public int getLink() {
    return linkId;
  }

  /**
   * Returns the "means" element of the virtual node.
   *
   * @return The ID of the "means" element.
   */
  public byte getMeans() {
    return meansId;
  }

  /**
   * Returns the "mode" element of the virtual node.
   *
   * @return The ID of the "mode" element.
   */
  public byte getMode() {
    return modeId;
  }

  /**
   * Returns an long integer that represents the line+mode+means combination.
   *
   * @return A long representing the mode-means-service combination.
   */
  public long getModeMeansServiceKey() {
    return NodusC.MAXMM * NodusC.MAXMM * serviceId + NodusC.MAXMM * modeId + meansId;
  }

  /**
   * Returns the "node" element of the virtual node.
   *
   * @return The ID of the "node" element.
   */
  public int getNode() {
    return nodeId;
  }

  /**
   * Returns the "service" element of the virtual node.
   *
   * @return The ID of the "service" element.
   */
  public int getService() {
    return serviceId;
  }

  /**
   * Checks if this virtual node corresponds to a loading or unloading point (centroid).
   *
   * @return True if this virtual node is a centroid.
   */
  public boolean isCentroid() {
    boolean returnValue;
    if (linkId == 0 && modeId == 0 && meansId == 0) {
      returnValue = true;
    } else {
      returnValue = false;
    }
    return returnValue;
  }

  /**
   * Returns the string representation of this JungVirtualNode.
   *
   * @return The string representation of this JungVirtualNode.
   */
  @Override
  public String toString() {
    return toString(false);
  }

  /**
   * Returns the string representation of this JungVirtualNode, with or without its sign.
   *
   * @param unsigned If true, the sign of the virtual node will not be added.
   * @return The string representation of this JungVirtualNode.
   */
  public String toString(boolean unsigned) {

    int node;
    if (unsigned) {
      node = Math.abs(nodeId);
    } else {
      node = nodeId;
    }

    return node + ":" + linkId + ":" + modeId + ":" + meansId + ":" + serviceId;
  }
}
