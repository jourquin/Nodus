/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
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

import edu.uclouvain.core.nodus.compute.modalsplit.Path;
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

  /** Allocated only for multiclass equilibrium assignments, with lazy rows per demand group. */
  private ClassVolumes[] equilibriumVolumes;

  private int equilibriumClasses;

  /** A group's class flows are written by its worker, then read after the coordinator joins it. */
  private static final class ClassVolumes {
    final double[] current;
    final double[] auxiliary;
    double change;
    double weightedCost;

    ClassVolumes(int classes) {
      current = new double[classes];
      auxiliary = new double[classes];
    }

    void combine(double lambda) {
      change = 0;
      for (int i = 0; i < current.length; i++) {
        final double previous = current[i];
        current[i] = (1 - lambda) * previous + lambda * auxiliary[i];
        change += Math.abs(current[i] - previous);
        auxiliary[i] = 0;
      }
    }
  }

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

  /** Type of virtual link, such as moving, transit, loading, transhipment, switch, or stop. */
  private byte virtualLinkType;

  /* This variable will contain the cost computed for this particular virtual link */
  private double[] cost = null;

  /** Costs retained for completed timed cost passes; allocated only for timed coordinators. */
  private double[][] timeCosts;

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
   * @param type The type of non-moving virtual link.
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
   * Adds a trial assignment to both the group total and its OD class.
   *
   * @param groupIndex commodity group index
   * @param odClass OD class
   * @param volume quantity to add
   */
  public void addAuxiliaryVolume(byte groupIndex, byte odClass, double volume) {
    addAuxiliaryVolume(groupIndex, volume);
    if (equilibriumVolumes != null && volume != 0) {
      classVolumes(groupIndex).auxiliary[odClass] += volume;
    }
  }

  /**
   * Adds an incremental warm-up flow while retaining its OD class for later equilibrium steps.
   *
   * @param groupIndex commodity group index
   * @param odClass OD class
   * @param volume quantity to add
   */
  public void addEquilibriumVolume(byte groupIndex, byte odClass, double volume) {
    addVolume(groupIndex, volume);
    if (equilibriumVolumes != null && volume != 0 && !Double.isNaN(volume)) {
      classVolumes(groupIndex).current[odClass] += volume;
    }
  }

  private ClassVolumes classVolumes(byte groupIndex) {
    if (equilibriumVolumes[groupIndex] == null) {
      equilibriumVolumes[groupIndex] = new ClassVolumes(equilibriumClasses);
    }
    return equilibriumVolumes[groupIndex];
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
  public void addVolume(byte groupIndex, int timeSlice, double qty) {

    // The volume can be a NaN instead of 0 in the Multinomial logit modal split function
    if (Double.isNaN(qty)) {
      qty = 0.0;
    }

    currentVolume[groupIndex][timeSlice] += qty;
  }

  /**
   * Adds a quantity of a dynamic assignment to this virtual link.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice the volume belongs to.
   * @param qty The quantity to add.
   */
  public void addVolume(byte groupIndex, byte timeSlice, double qty) {
    addVolume(groupIndex, (int) timeSlice, qty);
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
    previousVolume[groupIndex] = currentVolume[groupIndex][0];

    currentVolume[groupIndex][0] =
        (1 - lambda) * currentVolume[groupIndex][0] + lambda * auxiliaryVolume[groupIndex];

    auxiliaryVolume[groupIndex] = 0;

    if (equilibriumVolumes != null && equilibriumVolumes[groupIndex] != null) {
      equilibriumVolumes[groupIndex].combine(lambda);
    }

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
   * Returns a class's assigned flow, with the original aggregate storage used for class zero in
   * single-class assignments.
   *
   * @param groupIndex commodity group index
   * @param odClass OD class
   * @return current class flow
   */
  public double getEquilibriumVolume(byte groupIndex, byte odClass) {
    if (equilibriumVolumes == null) {
      return odClass == 0 ? getCurrentVolume(groupIndex) : 0;
    }
    final ClassVolumes volumes = equilibriumVolumes[groupIndex];
    return volumes == null ? 0 : volumes.current[odClass];
  }

  /**
   * Returns the class-specific direction used with this class's costs during line search.
   *
   * @param groupIndex commodity group index
   * @param odClass OD class
   * @return auxiliary minus current class flow
   */
  public double getEquilibriumDirection(byte groupIndex, byte odClass) {
    if (equilibriumVolumes == null) {
      return odClass == 0 ? getAuxiliaryVolume(groupIndex) - getCurrentVolume(groupIndex) : 0;
    }
    final ClassVolumes volumes = equilibriumVolumes[groupIndex];
    return volumes == null ? 0 : volumes.auxiliary[odClass] - volumes.current[odClass];
  }

  /**
   * Sums absolute changes before classes are aggregated, so opposite class shifts cannot cancel.
   *
   * @param groupIndex commodity group index
   * @return absolute flow change over the last equilibrium step
   */
  public double getEquilibriumVolumeChange(byte groupIndex) {
    if (equilibriumVolumes == null) {
      return Math.abs(getCurrentVolume(groupIndex) - getPreviousVolume(groupIndex));
    }
    final ClassVolumes volumes = equilibriumVolumes[groupIndex];
    return volumes == null ? 0 : volumes.change;
  }

  /**
   * Enables class flow accounting before the first equilibrium or incremental warm-up assignment.
   * Single-class assignments retain the original storage without additional arrays.
   *
   * @param classes number of OD class slots
   */
  public void initializeEquilibriumClasses(int classes) {
    equilibriumClasses = classes;
    equilibriumVolumes = classes > 1 ? new ClassVolumes[nbGroups] : null;
  }

  void resetEquilibriumCostAverage() {
    for (ClassVolumes volumes : equilibriumVolumes) {
      if (volumes != null) {
        volumes.weightedCost = 0;
      }
    }
  }

  void accumulateEquilibriumCost(byte groupIndex, byte odClass) {
    final ClassVolumes volumes = equilibriumVolumes[groupIndex];
    if (volumes != null && volumes.current[odClass] > 0) {
      volumes.weightedCost += volumes.current[odClass] * cost[groupIndex];
    }
  }

  void applyEquilibriumCostAverage() {
    for (byte group = 0; group < nbGroups; group++) {
      final ClassVolumes volumes = equilibriumVolumes[group];
      if (volumes != null && getCurrentVolume(group) > 0) {
        cost[group] = volumes.weightedCost / getCurrentVolume(group);
      }
    }
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
  public double getCurrentVolume(byte groupIndex, int timeSlice) {
    return currentVolume[groupIndex][timeSlice];
  }

  /**
   * Returns the current volume on this virtual link for a given group index and time slice.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice for which the volume is asked for.
   * @return The current volume for the group of commodities and time slice.
   */
  public double getCurrentVolume(byte groupIndex, byte timeSlice) {
    return getCurrentVolume(groupIndex, (int) timeSlice);
  }

  /**
   * Returns the number of vehicles computed for a given group. Returns 0 for non moving virtual
   * links
   *
   * @param groupIndex The index of the group of commodities.
   * @return The number of vehicles.
   */
  public int getCurrentVehicles(byte groupIndex) {
    return getCurrentVehicles(groupIndex, 0);
  }

  /**
   * Returns the number of vehicles computed for a given group. Returns 0 for transhipment virtual
   * links.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice the number of vehicles is asked for.
   * @return The number of vehicles.
   */
  public int getCurrentVehicles(byte groupIndex, int timeSlice) {
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
   * Returns the number of vehicles computed for a given group and time slice.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice.
   * @return The number of vehicles.
   */
  public int getCurrentVehicles(byte groupIndex, byte timeSlice) {
    return getCurrentVehicles(groupIndex, (int) timeSlice);
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
   * Returns the recorded cost of a time slice, or the current cost for untimed cost passes.
   *
   * @param groupIndex commodity group index
   * @param timeSlice time slice index
   * @return unit cost evaluated for the specified slice
   */
  public double getCost(byte groupIndex, int timeSlice) {
    if (timeCosts != null && !Double.isNaN(timeCosts[groupIndex][timeSlice])) {
      return timeCosts[groupIndex][timeSlice];
    }
    return cost[groupIndex];
  }

  /**
   * Retains the current costs before a later time slice overwrites them.
   *
   * @param timeSlice index of the completed cost pass
   */
  public void snapshotCosts(int timeSlice) {
    if (timeCosts == null) {
      timeCosts = new double[cost.length][currentVolume[0].length];
      for (double[] values : timeCosts) {
        java.util.Arrays.fill(values, Double.NaN);
      }
    }
    for (int group = 0; group < cost.length; group++) {
      timeCosts[group][timeSlice] = cost[group];
    }
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
    return hasVolume(0);
  }

  /**
   * Returns true if the current volume on this virtual link is positive for at least one group
   * during the given time slice.
   *
   * @param timeSlice The time slice a volume is looking for.
   * @return True if there is a volume for at least one group of commodities.
   */
  public boolean hasVolume(int timeSlice) {
    for (int i = 0; i < nbGroups; i++) {
      if (currentVolume[i][timeSlice] > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the current volume is positive during the given time slice.
   *
   * @param timeSlice The time slice.
   * @return True if there is a volume for at least one group.
   */
  public boolean hasVolume(byte timeSlice) {
    return hasVolume((int) timeSlice);
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
   * @throws IllegalArgumentException if characteristics are invalid, volumes are
   *     negative/non-finite or a vehicle/PCU count or directional total exceeds {@link
   *     Integer#MAX_VALUE}; this link's counts and its real-link traffic remain unchanged on
   *     rejection
   */
  public void volumesToVehicles(
      byte groupIndex, int timeSlice, double averageLoad, double passengerCarUnits) {

    validateVehicleCharacteristics(averageLoad, passengerCarUnits);
    final int nbVehicles = vehiclesForVolume(currentVolume[groupIndex][timeSlice], averageLoad);
    final int nbAuxiliaryVehicles = vehiclesForVolume(auxiliaryVolume[groupIndex], averageLoad);

    if (virtualLinkType == TYPE_MOVE) {
      final int currentPcu = checkedCount(nbVehicles * passengerCarUnits);
      final int auxiliaryPcu = checkedCount(nbAuxiliaryVehicles * passengerCarUnits);
      // Check both sums before updating either counter or publishing vehicle counts.
      checkedCount(realLink.getCurrentPassengerCarUnits(this) + currentPcu);
      checkedCount(realLink.getAuxiliaryPassengerCarUnits(this) + auxiliaryPcu);
      realLink.addPassengerCarUnits(this, currentPcu);
      realLink.addAuxiliaryPassengerCarUnits(this, auxiliaryPcu);
    }
    currentVehicles[groupIndex][timeSlice] = nbVehicles;
    auxiliaryVehicles[groupIndex] = nbAuxiliaryVehicles;
  }

  /**
   * Transforms volumes into vehicles for a byte-indexed time slice.
   *
   * @param groupIndex The index of the group of commodities.
   * @param timeSlice The time slice.
   * @param averageLoad The average load.
   * @param passengerCarUnits The passenger-car-unit factor.
   */
  public void volumesToVehicles(
      byte groupIndex, byte timeSlice, double averageLoad, double passengerCarUnits) {
    volumesToVehicles(groupIndex, (int) timeSlice, averageLoad, passengerCarUnits);
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
    equilibriumVolumes = null;
    equilibriumClasses = 0;
    cost = new double[nbGroups];
    timeCosts = null;
    duration = new double[nbGroups];
    currentVolume = new double[nbGroups][nbTimeSlices];
    auxiliaryVolume = new double[nbGroups];
    previousVolume = new double[nbGroups];
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

  /**
   * Temporarily converts the projected Frank-Wolfe volume (1-lambda) * current + lambda * auxiliary
   * into PCUs.
   *
   * <p>This does not modify currentVolume or auxiliaryVolume.
   *
   * @param groupIndex The group index.
   * @param timeSlice The time slice.
   * @param averageLoad The average load of the vehicle.
   * @param passengerCarUnits The PCU for this vehicle.
   * @param lambda The lambda value
   * @throws IllegalArgumentException if vehicle characteristics or the projected count/PCU total
   *     are invalid or outside the supported integer range; real-link traffic remains unchanged
   */
  public void projectedVolumesToVehicles(
      byte groupIndex, int timeSlice, double averageLoad, double passengerCarUnits, double lambda) {

    if (virtualLinkType != TYPE_MOVE) {
      return;
    }

    validateVehicleCharacteristics(averageLoad, passengerCarUnits);
    final double projectedVolume =
        (1.0 - lambda) * currentVolume[groupIndex][timeSlice]
            + lambda * auxiliaryVolume[groupIndex];

    final int nbVehicles = vehiclesForVolume(projectedVolume, averageLoad);
    final int currentPcu = checkedCount(nbVehicles * passengerCarUnits);
    checkedCount(realLink.getCurrentPassengerCarUnits(this) + currentPcu);
    realLink.addPassengerCarUnits(this, currentPcu);
  }

  /**
   * Converts a projected volume for a byte-indexed time slice.
   *
   * @param groupIndex The group index.
   * @param timeSlice The time slice.
   * @param averageLoad The average load.
   * @param passengerCarUnits The PCU factor.
   * @param lambda The lambda value.
   */
  public void projectedVolumesToVehicles(
      byte groupIndex,
      byte timeSlice,
      double averageLoad,
      double passengerCarUnits,
      double lambda) {
    projectedVolumesToVehicles(groupIndex, (int) timeSlice, averageLoad, passengerCarUnits, lambda);
  }

  private static void validateVehicleCharacteristics(double averageLoad, double passengerCarUnits) {
    if (!Double.isFinite(averageLoad) || averageLoad <= 0) {
      throw new IllegalArgumentException(
          "Average vehicle load must be finite and greater than zero.");
    }
    if (!Double.isFinite(passengerCarUnits) || passengerCarUnits < 0) {
      throw new IllegalArgumentException("Vehicle PCU factor must be finite and nonnegative.");
    }
  }

  private static int vehiclesForVolume(double volume, double averageLoad) {
    if (!Double.isFinite(volume) || volume < 0) {
      throw new IllegalArgumentException("Vehicle volume must be finite and nonnegative.");
    }
    final int count = checkedCount(volume / averageLoad);
    // A positive demand still needs a vehicle if floating-point division underflows to zero.
    return volume > 0 ? Math.max(1, count) : 0;
  }

  private static int checkedCount(double value) {
    final double count = Math.ceil(value);
    if (!Double.isFinite(count) || count < 0 || count > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "Vehicle or PCU count is outside the supported range 0 to " + Integer.MAX_VALUE + ".");
    }
    return (int) count;
  }
}
