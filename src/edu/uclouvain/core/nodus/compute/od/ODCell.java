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

package edu.uclouvain.core.nodus.compute.od;

/**
 * Contains the information relative to an origin-destination cell.
 *
 * @author Bart Jourquin
 */
public class ODCell {
  /** Destination node number. */
  private int destinationNodeId;

  /** Group number to which this demand is related. */
  private int group;

  /** Intermediate origin and time this OD pair (for dynamic assignments. */
  private int relocatedOriginNodeId;

  private int relocatedStartingTime;

  /** Class to which this OD pair belongs to. */
  private byte odClass = 0;

  /** Origin node number. */
  private int originNodeId;

  /** Index used when detailed paths are saved. */
  private int pathIndex = -1;

  /** Quantity to be transported. */
  private double quantity;

  /** Time of departure (in seconds, starting at 0 am. Default is -1 for non time dependency */
  private int startingTime = -1;

  /**
   * Initializes a new demand between an origin and a destination for a given group and quantity to
   * be transported.
   *
   * @param group The group of commodities
   * @param originNodeId The ID of the origin real node.
   * @param destinationNodeId The ID of the destination real node.
   * @param quantity double The quantity to send from the origin to the destination for this group
   *     of commodities.
   */
  public ODCell(int group, int originNodeId, int destinationNodeId, double quantity) {
    this.group = group;
    this.originNodeId = relocatedOriginNodeId = originNodeId;
    this.destinationNodeId = destinationNodeId;
    this.quantity = quantity;
  }

  /**
   * Initializes a new demand between an origin and a destination for a given group and quantity to
   * be transported.
   *
   * @param group The group of commodities
   * @param originNodeId The ID of the origin real node.
   * @param destinationNodeId The ID of the destination real node.
   * @param quantity double The quantity to send from the origin to the destination for this group
   *     of commodities.
   * @param odClass The class this OD cell belongs to.
   */
  public ODCell(int group, int originNodeId, int destinationNodeId, double quantity, byte odClass) {
    this(group, originNodeId, destinationNodeId, quantity);
    this.odClass = odClass;
  }

  /**
   * Initializes a new demand between an origin and a destination for a given group and quantity to
   * be transported.
   *
   * @param group The group of commodities
   * @param originNodeId The ID of the origin real node.
   * @param destinationNodeId The ID of the destination real node.
   * @param quantity double The quantity to send from the origin to the destination for this group
   *     of commodities.
   * @param startingTime The starting time of the flow, expressed in minutes after midnight.
   */
  public ODCell(
      int group, int originNodeId, int destinationNodeId, double quantity, int startingTime) {
    this(group, originNodeId, destinationNodeId, quantity);
    this.startingTime = this.relocatedStartingTime = startingTime * 60;
  }

  /**
   * Initializes a new demand between an origin and a destination for a given group and quantity to
   * be transported.
   *
   * @param group The group of commodities
   * @param originNodeId The ID of the origin real node.
   * @param destinationNodeId The ID of the destination real node.
   * @param quantity double The quantity to send from the origin to the destination for this group
   *     of commodities.
   * @param startingTime The starting time of the flow, expressed in minutes after midnight.
   * @param odClass The class this OD cell belongs to.
   */
  public ODCell(
      int group,
      int originNodeId,
      int destinationNodeId,
      double quantity,
      int startingTime,
      byte odClass) {
    this(group, originNodeId, destinationNodeId, quantity);
    this.startingTime = this.relocatedStartingTime = startingTime * 60;
    this.odClass = odClass;
  }

  /**
   * Adds a additional quantity to the ODCell.
   *
   * @param quantity The quantity to add
   */
  public void addQuantity(double quantity) {
    this.quantity += quantity;
  }

  /**
   * Returns the ID of the real node at destination.
   *
   * @return The ID of the real node at destination.
   */
  public int getDestinationNodeId() {
    return destinationNodeId;
  }

  /**
   * Returns the group to which this demand belongs to.
   *
   * @return The group ID.
   */
  public int getGroup() {
    return group;
  }

  /**
   * Returns the ID of the real node to which the demand is relocated to during a time dependent
   * assignment.
   *
   * @return The ID of the real node to which the demand was reallocated to.
   */
  public int getRelocatedOriginNodeId() {
    return relocatedOriginNodeId;
  }

  /**
   * Returns the starting time for a relocated demand during a time dependent assignment.
   *
   * @return The starting time for the relocated demand, expressed in seconds after midnight.
   */
  public int getRelocatedStartingTime() {
    return relocatedStartingTime;
  }

  /**
   * Returns the OD class this demand belongs to.
   *
   * @return The OD class.
   */
  public byte getODClass() {
    return odClass;
  }

  /**
   * Returns the ID of the real node at origin.
   *
   * @return The ID of the real node at origin.
   */
  public int getOriginNodeId() {
    return originNodeId;
  }

  /**
   * Returns the path index this OD cell as allocated to. Used in the dynamic time dependent
   * assignment.
   *
   * @return The path index.
   */
  public int getPathIndex() {
    return pathIndex;
  }

  /**
   * Returns the quantity to be transported.
   *
   * @return The quantity stored in this OD cell.
   */
  public double getQuantity() {
    return quantity;
  }

  /**
   * Returns the starting time in seconds or -1 if this OD cell is not tim dependent.
   *
   * @return The starting time of the demand, expressed in seconds after midnight or -1 if this OD
   *     cell is not time dependent.
   */
  public int getStartingTime() {
    return startingTime;
  }

  /**
   * Sets the real origin node ID to which this demand must be reallocated to.
   *
   * @param relocatedOriginNodeId The ID of the relocated node
   */
  public void setRelocatedOriginNodeId(int relocatedOriginNodeId) {
    this.relocatedOriginNodeId = relocatedOriginNodeId;
  }

  /**
   * Sets the starting time of the relocated demand.
   *
   * @param relocatedStartingTime The starting time of the relocated demand, expressed in second
   *     after midnight.
   */
  public void setRelocatedStartingTime(int relocatedStartingTime) {
    this.relocatedStartingTime = relocatedStartingTime;
  }

  /**
   * Sets the index of the path this OD cell is assigned to during a dynamic time dependent
   * assignment.
   *
   * @param pathIndex The path index this OD cell is assigned to.
   */
  public void setPathIndex(int pathIndex) {
    this.pathIndex = pathIndex;
  }

  /** Returns a string representation of this OD cell. */
  @Override
  public String toString() {
    String s = "";
    s += "origin " + originNodeId + "\n";
    s += "destination " + destinationNodeId + "\n";
    s += "group " + group + "\n";
    s += "quantity " + quantity + "\n";
    s += "starting time " + startingTime + "\n";
    return s;
  }
}
