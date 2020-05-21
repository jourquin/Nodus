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

import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import java.util.LinkedList;

/**
 * Container class that holds all the relevant information relative to real links.
 *
 * @author Bart Jourquin
 */
public class RealLink extends RealNetworkObject {

  /** Number of auxiliary standard vehicles assigned downstream to this link. */
  private int auxiliaryStandardVehiclesDown = 0;

  /** Number of auxiliary standard vehicles assigned upstream to this link. */
  private int auxiliaryStandardVehiclesUp = 0;

  /** Number of standard vehicles assigned downstream to this link. */
  private int currentStandardVehiclesDown = 0;

  /** Number of standard vehicles assigned upstream to this link. */
  private int currentStandardVehiclesUp = 0;

  //private float duration = -1;

  /** Length of this real link. */
  private float length = -1;

  private LinkedList<Integer> services;

  /** Origin real node num of this real link. */
  private int originNode = -1;

  private float speed = -1;

  /**
   * Adds a number of auxiliary standard vehicles to this real link.
   *
   * @param virtualLink A VirtualLink related to this real link.
   * @param nbVehicles A number of vehicles to add.
   */
  public void addAuxiliaryStandardVehicles(VirtualLink virtualLink, int nbVehicles) {
    if (virtualLink.getBeginVirtualNode().getRealNodeId(false) == originNode) {
      auxiliaryStandardVehiclesUp += nbVehicles;
    } else {
      auxiliaryStandardVehiclesDown += nbVehicles;
    }
  }

  /**
   * Adds a service ID to this real link.
   *
   * @param serviceId The numeric ID of the line that uses this link
   */
  public void addService(int serviceId) {
    if (services == null) {
      services = new LinkedList<>();
    }
    services.add(Integer.valueOf(serviceId));
  }

  /**
   * Adds a number of standard vehicles to this real link.
   *
   * @param virtualLink VirtualLink
   * @param nbVehicles int
   */
  public void addStandardVehicles(VirtualLink virtualLink, int nbVehicles) {
    if (virtualLink.getBeginVirtualNode().getRealNodeId(false) == originNode) {
      currentStandardVehiclesUp += nbVehicles;
    } else {
      currentStandardVehiclesDown += nbVehicles;
    }
  }

  /** Clears the previously loaded service ID. */
  public void clearServices() {
    if (services != null) {
      services.clear();
      services = null;
    }
  }

  /**
   * Returns the number of auxiliary standard vehicles assigned to this real link.
   *
   * @param virtualLink VirtualLink
   * @return double
   */
  public double getAuxiliaryStandardVehicles(VirtualLink virtualLink) {
    if (virtualLink.getBeginVirtualNode().getRealNodeId(false) == originNode) {
      return auxiliaryStandardVehiclesUp;
    } else {
      return auxiliaryStandardVehiclesDown;
    }
  }

  /**
   * Returns the number of standard vehicles assigned to this real link.
   *
   * @param virtualLink VirtualLink
   * @return double
   */
  public double getCurrentStandardVehicles(VirtualLink virtualLink) {
    if (virtualLink.getBeginVirtualNode().getRealNodeId(false) == originNode) {
      return currentStandardVehiclesUp;
    } else {
      return currentStandardVehiclesDown;
    }
  }

  /**
   * Returns the length of this real link (km).
   *
   * @return The length of this real link (km).
   */
  public float getLength() {
    return length;
  }

  /**
   * Returns the nominal speed on this real link (km/h).
   *
   * @return The nominal speed on this real link.
   */
  public float getSpeed() {
    return speed;
  }

  /** Resets the vehicles on this real link. */
  public void resetStandardVehicles() {
    currentStandardVehiclesUp =
        auxiliaryStandardVehiclesUp =
            currentStandardVehiclesDown = auxiliaryStandardVehiclesDown = 0;
  }

  /**
   * Sets the length of this real link (km).
   *
   * @param length The length expressed in km.
   */
  public void setLength(float length) {
    this.length = length;
  }

  /**
   * Sets the origin node ID of this real link.
   *
   * @param originNodeId The ID of the real node at origin.
   */
  public void setOriginNodeId(int originNodeId) {
    this.originNode = originNodeId;
  }

  /**
   * Sets the nominal speed for this real link (km/h).
   *
   * @param speed The nominal speed as found in the database (km/h).
   */
  public void setSpeed(float speed) {
    this.speed = speed;
  }
}
