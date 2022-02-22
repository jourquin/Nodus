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

package edu.uclouvain.core.nodus.compute.virtual;

import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class holds all the information relative to a virtual link.
 *
 * @author Bart Jourquin
 */
public class VirtualLink {
  /** Virtual link that represent a loading operation. */
  public static final byte TYPE_LOAD = 2;

  /** Virtual link that represent a moving operation. */
  public static final byte TYPE_MOVE = 0;

  /** Virtual link that represents the Stop TransportService operation. */
  public static final byte TYPE_STOP = 6;

  /** Virtual link that represents a Change TransportService operation. */
  public static final byte TYPE_SWITCH = 5;

  /** Virtual link that represents a transhipment operation. */
  public static final byte TYPE_TRANSHIP = 4;

  /** Virtual link that represent a transiting operation. */
  public static final byte TYPE_TRANSIT = 1;

  /** Virtual link that represent an unloading operation. */
  public static final byte TYPE_UNLOAD = 3;

  /** Auxiliary volumes on the virtual link, per group. */
  private double[] auxiliaryVolume;

  /** Auxiliary vehicles on the virtual link, per group. */
  private int[] auxiliaryVehicles;

  /** Begin and end virtual nodes for this virtual link. */
  private VirtualNode beginVirtualNode;

  /** Current volumes on the virtual link, per group and time slice. */
  private double[][] currentVolume;

  /** Current vehicles on the virtual link, per group and time slice. */
  private int[][] currentVehicles;

  /** Begin and end virtual nodes for this virtual link. */
  private VirtualNode endVirtualNode;

  /**
   * These two indexes are used to find the .dbf record of the real link associated with this
   * virtual link. This is only pertinent for moving virtual links. These indexes can be related to
   * real nodes or real links in their DbfTableModels
   */
  private int indexInLayer;

  private int layerIndex;

  /* Number of groups in the OD matrix */
  private byte nbGroups;

  /* Used for the fast multiflow assignment */
  private LinkedList<PathODCell>[] pathODCellList = null;

  /**
   * Current volume (per group) computed during the precedent iteration. Needed to compute the
   * lambda parameter used in the equilibrium assignment algorithms
   */
  private double[] previousVolume;

  /* Real link the virtual link is generated from */
  private RealLink realLink;

  private boolean upStream;

  /* Virtual link number */
  private int virtualLinkId;

  /** Type of virtual link. Can be TypeMOVE, TypeTRANSIT, TypeLOAD, TypeUNLOAD or TypeTRANSHIP */
  private byte virtualLinkType;

  /* This variable will contain the cost computed for this particular virtual link */
  private double[] cost = null;

  /* This variable will contain the transit time computed for this particular virtual link */
  private double[] duration = null;

  /**
   * Constructor for a non moving virtual link, given a virtual link number, the location of the
   * real node it is generated from in the Shape layers (layer index and index in layer),the two
   * virtual end nodes and the type of virtual link (any type but TypeMOVE).
   *
   * @param id The ID of the virtual link to create.
   * @param layerIndex The index of the layer the real link belongs to.
   * @param indexInLayer The index into the layer the real link belongs to.
   * @param beginVirtualNode The ID of virtual node at the origin.
   * @param endVirtualNode The ID of virtual node at the end.
   * @param type The type of virtual link (load, unload, transit or tranship).
   */
  public VirtualLink(
      int id,
      int layerIndex,
      int indexInLayer,
      VirtualNode beginVirtualNode,
      VirtualNode endVirtualNode,
      byte type) {
    virtualLinkId = id;
    this.beginVirtualNode = beginVirtualNode;
    this.endVirtualNode = endVirtualNode;
    this.layerIndex = layerIndex;
    this.indexInLayer = indexInLayer;
    virtualLinkType = type;
    realLink = null;

    upStream = false;
    if (beginVirtualNode.getRealNodeId(false) < endVirtualNode.getRealNodeId(false)) {
      upStream = true;
    }
  }

  /**
   * Constructor for a moving virtual link, given a virtual link number, the location of the real
   * link it is generated from in the Shape layers (layer index and index in layer) and the two
   * virtual end nodes.
   *
   * @param id The ID of the virtual link to create.
   * @param layerIndex The index of the layer the real link belongs to.
   * @param indexInLayer The index into the layer the real link belongs to.
   * @param beginVirtualNode The ID of virtual node at the origin.
   * @param endVirtualNode The ID of virtual node at the end.
   * @param realLink The ID of the real link this virtual link is generated from.
   */
  public VirtualLink(
      int id,
      int layerIndex,
      int indexInLayer,
      VirtualNode beginVirtualNode,
      VirtualNode endVirtualNode,
      RealLink realLink) {
    virtualLinkId = id;
    this.beginVirtualNode = beginVirtualNode;
    this.endVirtualNode = endVirtualNode;
    this.layerIndex = layerIndex;
    this.indexInLayer = indexInLayer;
    virtualLinkType = TYPE_MOVE;
    this.realLink = realLink;

    upStream = false;
    if (beginVirtualNode.getRealNodeId(false) < endVirtualNode.getRealNodeId(false)) {
      upStream = true;
    }
  }

  /**
   * Adds an auxiliary volume to this virtual link for a given group index.
   *
   * @param groupIndex Index of the group of commodities.
   * @param volume Quantity to add.
   */
  public void addAuxiliaryVolume(byte groupIndex, double volume) {
    auxiliaryVolume[groupIndex] += volume;
  }

  /**
   * Adds a pathODCell to this virtual link.
   *
   * @param groupIndex Index of the group of commodities.
   * @param pathODCell The pathODCell to add.
   */
  public void addCell(byte groupIndex, PathODCell pathODCell) {
    if (pathODCellList[groupIndex] == null) {
      pathODCellList[groupIndex] = new LinkedList<>();
    }

    pathODCellList[groupIndex].add(pathODCell);
  }

  /**
   * Adds a quantity of a dynamic assignment to this virtual link.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice the volume belongs to.
   * @param qty The quantity to add.
   */
  public void addVolume(byte groupIndex, byte timeSlice, double qty) {

    // The volume can be a NaN instead of 0 in the Multinomial logit modal split function
    if (Double.isNaN(qty)) {
      qty = 0.0;
    }

    currentVolume[groupIndex][timeSlice] += qty;
  }

  /**
   * Adds a new quantity to the current volume on this virtual link.
   *
   * @param groupIndex The index of the group of commodities.
   * @param qty The quantity to add.
   */
  public void addVolume(byte groupIndex, double qty) {
    addVolume(groupIndex, (byte) 0, qty);
  }

  /**
   * Balances the volume on this virtual link using the given lambda.
   *
   * @param groupIndex The index of the group of commodities.
   * @param lambda The balance factor to use : (1-lambda) + previous volume + lambda * current
   *     volume.
   */
  public void combineVolumes(byte groupIndex, double lambda) {
    if (lambda != 1.0) {
      previousVolume[groupIndex] = currentVolume[groupIndex][0];
    }

    currentVolume[groupIndex][0] =
        (1 - lambda) * currentVolume[groupIndex][0] + lambda * auxiliaryVolume[groupIndex];

    auxiliaryVolume[groupIndex] = 0;

    if (virtualLinkType == TYPE_MOVE) {
      auxiliaryVehicles[groupIndex] = 0;
    }
  }

  /**
   * Returns the auxiliary volume on this virtual link for a given group.
   *
   * @param groupIndex The index of the group of commodities.
   * @return The auxiliary volume associated to this virtual link.
   */
  public double getAuxiliaryVolume(byte groupIndex) {
    return auxiliaryVolume[groupIndex];
  }

  /**
   * Returns the virtual node at the beginning of this virtual link.
   *
   * @return The virtual node at the beginning of this virtual link.
   */
  public VirtualNode getBeginVirtualNode() {
    return beginVirtualNode;
  }

  /**
   * Returns the current volume on this virtual link for a given group index.
   *
   * @param groupIndex The index of the group of commodities.
   * @return The current volume for the group of commodities.
   */
  public double getCurrentVolume(byte groupIndex) {
    return currentVolume[groupIndex][0];
  }

  /**
   * Returns the current volume on this virtual link for a given group index and time slice.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice for which the volume is asked for.
   * @return The current volume for the group of commodities and time slice.
   */
  public double getCurrentVolume(byte groupIndex, byte timeSlice) {
    return currentVolume[groupIndex][timeSlice];
  }

  /**
   * Returns the number of vehicles computed for a given group. Returns 0 for non moving virtual
   * links
   *
   * @param groupIndex The index of the group of commodities.
   * @return The number of vehicles.
   */
  public int getCurrentVehicles(byte groupIndex) {
    return getCurrentVehicles(groupIndex, (byte) 0);
  }

  /**
   * Returns the number of vehicles computed for a given group. Returns 0 for transhipment virtual
   * links.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice the number of vehicles is asked for.
   * @return The number of vehicles.
   */
  public int getCurrentVehicles(byte groupIndex, byte timeSlice) {
    if (virtualLinkType == TYPE_MOVE
        || virtualLinkType == TYPE_LOAD
        || virtualLinkType == TYPE_UNLOAD
        || virtualLinkType == TYPE_TRANSIT) {
      return currentVehicles[groupIndex][timeSlice];
    } else {
      return 0;
    }
  }

  /**
   * Returns the end virtual node of this virtual link.
   *
   * @return The end virtual.
   */
  public VirtualNode getEndVirtualNode() {
    return endVirtualNode;
  }

  /**
   * Returns the index of the real network object related to this virtual link in the layer.
   *
   * @return The index in the layer.
   */
  public int getIndexInLayer() {
    return indexInLayer;
  }

  /**
   * Returns the index of the layer (either a node or link layer) in which the real network object
   * associated with this virtual link is stored.
   *
   * @return Index of the relevant layer.
   */
  public int getLayerIndex() {
    return layerIndex;
  }

  /**
   * Returns the length of this virtual link. The length is strictly positive only for "moving"
   * virtual link, and is then equal to the length of the real link it was generated from.
   *
   * @return The length of the reak link associated to this virtual link (or 0 if this is not a
   *     moving virtual link).
   */
  public double getLength() {
    if (virtualLinkType != TYPE_MOVE) {
      return 0.0;
    }

    return realLink.getLength();
  }

  /**
   * Returns the ID of this virtual link.
   *
   * @return The ID of this virtual link
   */
  public int getId() {
    return virtualLinkId;
  }

  /**
   * Returns the volume computed during the previous iteration.
   *
   * @param groupIndex The index of the group of commodities.
   * @return The volume computed at the previous iteration.
   */
  public double getPreviousVolume(byte groupIndex) {
    return previousVolume[groupIndex];
  }

  /**
   * Returns the speed on the virtual link. Returns 0 if this is not a moving virtual link.
   *
   * @return The speed on this moving virtual link.
   */
  public double getSpeed() {
    if (virtualLinkType != TYPE_MOVE) {
      return 0.0;
    }

    return realLink.getSpeed();
  }

  /**
   * Returns the type of virtual link.
   *
   * @return The type of virtual link.
   */
  public byte getType() {
    return virtualLinkType;
  }

  /**
   * Returns true if the ID of the begin real node is lower than the ID of the end real node.
   *
   * @return True or False depending on the order of the ID of the begin and end real nodes.
   */
  public boolean getUpStream() {
    return upStream;
  }

  /**
   * Returns the cost associated to a given group index.
   *
   * @param groupIndex The index of the group of commodities
   * @return The cost associated to the group.
   */
  public double getCost(byte groupIndex) {
    return cost[groupIndex];
  }

  /**
   * Returns the default transit time for a moving virtual link. It corresponds to the time needed
   * to travel along the link at nominal speed, expressed in seconds.
   *
   * @return double
   */
  public double getDefaultDuration() {
    if (virtualLinkType != TYPE_MOVE) {
      return 0;
    }
    return realLink.getDefaultTransitTime();
  }

  /**
   * Returns the transit time associated to a given group index.
   *
   * @param groupIndex The index of the group of commodities
   * @return The transit time associated to the group.
   */
  public double getDuration(byte groupIndex) {
    return duration[groupIndex];
  }

  /**
   * Returns the set of costs (one per group of commodities) associated to this virtual link.
   *
   * @return An array with the costs for all the groups of commodities.
   */
  public double[] getCosts() {
    return cost;
  }

  /**
   * Returns true if the current volume on this virtual link is positive for at least one group.
   *
   * @return True if there is a volume for at least one group of commodities.
   */
  public boolean hasVolume() {
    return hasVolume((byte) 0);
  }

  /**
   * Returns true if the current volume on this virtual link is positive for at least one group
   * during the given time slice.
   *
   * @param timeSlice The time slice a volume is looking for.
   * @return True if there is a volume for at least one group of commodities.
   */
  public boolean hasVolume(byte timeSlice) {
    for (int i = 0; i < nbGroups; i++) {
      if (currentVolume[i][timeSlice] > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Transform the volumes on the virtual link into vehicles and updates the corresponding real link
   * PCU's.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice to consider.
   * @param averageLoad The average load for a vehicle using this virtual link.
   * @param passengerCarUnits The number of equivalent standard vehicles a vehicle using this
   *     virtual link represents.
   */
  public void volumesToVehicles(
      byte groupIndex, byte timeSlice, double averageLoad, double passengerCarUnits) {

    int nbVehicles = (int) Math.ceil(currentVolume[groupIndex][timeSlice] / averageLoad);
    currentVehicles[groupIndex][timeSlice] = nbVehicles;

    int nbAuxiliaryVehicles = (int) Math.ceil(auxiliaryVolume[groupIndex] / averageLoad);
    auxiliaryVehicles[groupIndex] = nbAuxiliaryVehicles;

    if (virtualLinkType == TYPE_MOVE) {
      realLink.addPassengerCarUnits(this, (int) Math.ceil(nbVehicles * passengerCarUnits));
      realLink.addAuxiliaryPassengerCarUnits(
          this, (int) Math.ceil(nbAuxiliaryVehicles * passengerCarUnits));
    }
  }

  /**
   * Sets the number of groups of commodities the assignment has to handle. This method also
   * initializes all internal data arrays.
   *
   * @param nbGroups Number of groups the assignment has to handle.
   * @param nbTimeSlices Number of time slices the assignment will cover.
   */
  @SuppressWarnings("unchecked")
  public void setNbGroups(int nbGroups, int nbTimeSlices) {
    cost = new double[nbGroups];
    duration = new double[nbGroups];
    currentVolume = new double[nbGroups][nbTimeSlices];
    auxiliaryVolume = new double[nbGroups];
    previousVolume = new double[nbGroups];
    // withFlow = false;
    this.nbGroups = (byte) nbGroups;

    for (int i = 0; i < nbGroups; i++) {
      cost[i] = -1;
      duration[i] = 0;

      for (int j = 0; j < nbTimeSlices; j++) {
        currentVolume[i][j] = 0;
      }
      auxiliaryVolume[i] = previousVolume[i] = 0;
    }

    if (virtualLinkType == TYPE_MOVE
        || virtualLinkType == TYPE_LOAD
        || virtualLinkType == TYPE_UNLOAD
        || virtualLinkType == TYPE_TRANSIT) {

      currentVehicles = new int[nbGroups][nbTimeSlices];
      auxiliaryVehicles = new int[nbGroups];

      for (int i = 0; i < nbGroups; i++) {
        for (int j = 0; j < nbTimeSlices; j++) {
          currentVehicles[i][j] = 0;
        }
        auxiliaryVehicles[i] = 0;
      }
    }

    pathODCellList = new LinkedList[nbGroups];
  }

  /**
   * Set the transit time of this virtual link for a given group index.
   *
   * @param groupIndex Index of the group of commodities.
   * @param duration Transit time (seconds) to associate to this virtual link.
   */
  public void setDuration(byte groupIndex, double duration) {
    this.duration[groupIndex] = duration;
  }

  /**
   * Set the cost of this virtual link for a given group index.
   *
   * @param groupIndex Index of the group of commodities.
   * @param cost Weight (cost) to associate to this virtual link.
   */
  public void setCost(byte groupIndex, double cost) {
    this.cost[groupIndex] = cost;
  }

  /**
   * For each path that makes use of this virtual link, assigns a marketShare of the demand. This is
   * called by the exact multi-flow assignment algorithm once the modal split function has been
   * called.
   *
   * @param groupIndex The index of the group of commodities.
   * @param path The set of path computed by the one of the exact multi-flow assignment algorithm.
   */
  public void spreadFlowOverPaths(byte groupIndex, Path[] path) {
    if (pathODCellList[groupIndex] == null) {
      return;
    }

    Iterator<PathODCell> it = pathODCellList[groupIndex].iterator();
    while (it.hasNext()) {
      PathODCell pathODCell = it.next();

      if (path[pathODCell.alternativePath].isValid) {
        addVolume(groupIndex, pathODCell.quantity * path[pathODCell.alternativePath].marketShare);
      }
    }

    pathODCellList[groupIndex].clear();
  }

  /**
   * For each path that makes use of this virtual link, assigns a marketShare of the demand. This is
   * called by the exact multi-flow assignment algorithm once the modal split function has been
   * called.
   *
   * @param groupIndex The index of the group of commodities.
   * @param path The set of path computed by the one of the fast multi-flow assignment algorithm.
   */
  public void spreadVolumeOverPaths(byte groupIndex, Path[][] path) {
    if (pathODCellList[groupIndex] == null) {
      return;
    }

    Iterator<PathODCell> it = pathODCellList[groupIndex].iterator();

    while (it.hasNext()) {
      PathODCell pathODCell = it.next();
      if (path[pathODCell.alternativePath][pathODCell.indexInODLine].isValid) {
        addVolume(
            groupIndex,
            pathODCell.quantity
                * path[pathODCell.alternativePath][pathODCell.indexInODLine].marketShare);
      }
    }

    pathODCellList[groupIndex].clear();
  }

  /** Returns a string representation of the virtual link. */
  @Override
  public String toString() {
    return beginVirtualNode.getRealNodeId(true)
        + "."
        + beginVirtualNode.getRealLinkId()
        + "."
        + beginVirtualNode.getMode()
        + "."
        + beginVirtualNode.getMeans()
        + "-"
        + endVirtualNode.getRealNodeId(true)
        + "."
        + endVirtualNode.getRealLinkId()
        + "."
        + endVirtualNode.getMode()
        + "."
        + endVirtualNode.getMeans();
  }
}
