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

package com.bbn.openmap.layer.shape.jung;

import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;

/**
 * Convenient class that holds information about virtual links to draw in a JUNG graph.
 *
 * @author Bart Jourquin
 */
public class JungVirtualLink implements Comparable<Object> {

  private JungVirtualNode originJungVirtualNode;

  private JungVirtualNode destinationJungVirtualNode;

  private double quantity;

  private int time;

  private double unitCost;

  private double vehicles;

  /**
   * Creates a new JungVirtualLink.
   *
   * @param originJungVirtualNode The origin virtual node.
   * @param destinationJungVirtualNode The destination virtual node.
   * @param quantity The quantity (tons) transported on the virtual link.
   * @param unitCost The cost per unit (ton) transported on the virtual link.
   * @param vehicles The number of vehicles needed to transport the quantity.
   * @param time The time (minutes after midnight) for this volume (time dependent assignments
   *     only).
   */
  public JungVirtualLink(
      JungVirtualNode originJungVirtualNode,
      JungVirtualNode destinationJungVirtualNode,
      double quantity,
      double unitCost,
      double vehicles,
      int time) {
    this.originJungVirtualNode = originJungVirtualNode;
    this.destinationJungVirtualNode = destinationJungVirtualNode;
    this.quantity = quantity;
    this.unitCost = unitCost;
    this.vehicles = vehicles;
    this.time = time;
  }

  /**
   * Compares this JungVirtualLink to another.
   *
   * @param object The object to be compared.
   * @return A negative integer, zero, or a positive integer as this object is less than, equal to,
   *     or greater than the specified object.
   */
  @Override
  public int compareTo(Object object) {
    return toString().compareTo(((JungVirtualLink) object).toString());
  }

  /**
   * Returns the origin JungVirtualNode.
   *
   * @return The origin JungVirtualNode.
   */
  public JungVirtualNode getOriginJungVirtualNode() {
    return originJungVirtualNode;
  }

  /**
   * Returns the destination JungVirtualNode.
   *
   * @return The destination JungVirtualNode.
   */
  public JungVirtualNode getDestinationJungVirtualNode() {
    return destinationJungVirtualNode;
  }

  /**
   * Returns the transported quantity (tons).
   *
   * @return The transported quantity.
   */
  public double getQuantity() {
    return quantity;
  }

  /**
   * Returns the time of the traffic (minutes).
   *
   * @return The time of the traffic.
   */
  public int getTime() {
    return time;
  }

  /**
   * Returns the type of JungVirtualLink, according to the types defined in VirtualLink.
   *
   * @return The type of virtual link.
   */
  public int getType() {

    int returnType;

    if (originJungVirtualNode.getMode() == 0) {
      returnType = VirtualLink.TYPE_LOAD;
    } else if (destinationJungVirtualNode.getMode() == 0) {
      returnType = VirtualLink.TYPE_UNLOAD;
    } else if (Math.abs(originJungVirtualNode.getNode())
        != Math.abs(destinationJungVirtualNode.getNode())) {
      returnType = VirtualLink.TYPE_MOVE;
    } else if (originJungVirtualNode.getMode() == destinationJungVirtualNode.getMode()
        && originJungVirtualNode.getMeans() == destinationJungVirtualNode.getMeans()) {
      returnType = VirtualLink.TYPE_TRANSIT;
    } else {
      returnType = VirtualLink.TYPE_TRANSHIP;
    }

    return returnType;
  }

  /**
   * Returns the unit cost (per ton) on the virtual link.
   *
   * @return The unit cost.
   */
  public double getUnitCost() {
    return unitCost;
  }

  /**
   * Returns the number of vehicles needed to transport the quantity.
   *
   * @return The number of vehicles.
   */
  public double getVehicles() {
    return vehicles;
  }

  /**
   * Returns the string representation of this JungVirtualLink.
   *
   * @return The string representation of this JungVirtualLink.
   */
  @Override
  public String toString() {
    return toString(true);
  }

  /**
   * Returns the string representation of this JungVirtualLink, with or without the assigment time.
   *
   * @param withTime If true, the assignment time will be added to the string.
   * @return The string representation of this JungVirtualLink.
   */
  public String toString(boolean withTime) {
    StringBuffer returnString =
        new StringBuffer(
            originJungVirtualNode.toString() + "#" + destinationJungVirtualNode.toString());
    if (withTime) {
      returnString.append("#" + time);
    }
    return returnString.toString();
  }
}
