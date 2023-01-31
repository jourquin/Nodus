/*
 * Copyright (c) 1991-2023 Université catholique de Louvain
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

import edu.uclouvain.core.nodus.NodusC;
import java.util.LinkedList;

/**
 * This class holds all the information relative to a virtual node.
 *
 * @author Bart Jourquin
 */
public class VirtualNode {
  /** Specifies a virtual node with a negative sign. */
  public static final boolean NEGATIVE = false;

  /** Specifies a virtual node with a positive sign. */
  public static final boolean POSITIVE = true;

  /** Latitude of real node. */
  private double latitude;

  /** Longitude of real node. */
  private double longitude;

  /** "Transportation means" element of the virtual node (0-99). */
  private byte means;

  /** "Transportation mode" element of the virtual node (0-99). */
  private byte mode;

  /** "Real link" element of the virtual node. */
  private int realLinkId;

  /** "Real node" element of the virtual node. */
  private int realNodeId;

  /** "TransportService" element of the virtual node (0-9999). */
  private int service;

  /** Sign of the virtual node. */
  private boolean sign;

  /** List of virtual links that have this virtual node as origin. */
  private LinkedList<VirtualLink> virtualLinks = null;

  /** Number given to this virtual node. */
  private int virtualNodeId;

  /**
   * Creates a new virtual node.
   *
   * @param id ID to give to the new virtual node.
   * @param realNodeId "real node" element of the virtual node
   * @param realLinkId "real link" element of the virtual node.
   * @param mode "Mode" element of the virtual node.
   * @param means "Means" element of the virtual node.
   * @param service "TransportService" element of the virtual node.
   * @param latitude Latitude of the real node.
   * @param longitude Longitude of the real node.
   */
  public VirtualNode(
      int id,
      int realNodeId,
      int realLinkId,
      byte mode,
      byte means,
      short service,
      double latitude,
      double longitude) {
    virtualNodeId = id;

    this.realNodeId = realNodeId;
    this.realLinkId = realLinkId;
    this.mode = mode;
    this.means = means;
    this.service = service;

    this.latitude = latitude;
    this.longitude = longitude;

    if (realNodeId < 0) {
      sign = NEGATIVE;
    } else {
      sign = POSITIVE;
    }

    if (this.mode < 0 || this.mode > NodusC.MAXMM) {
      System.err.println("Invalid mode");
    }

    if (this.means < 0 || this.means > NodusC.MAXMM) {
      System.err.println("Invalid means");
    }
    if (this.service < 0 || this.service > NodusC.MAXSERVICES) {
      System.err.println("Invalid Line");
    }
    virtualLinks = new LinkedList<>();
  }

  /**
   * Adds a new virtual link starting from this virtual node.
   *
   * @param virtualLink The virtual link to add.
   */
  public void add(VirtualLink virtualLink) {
    virtualLinks.add(virtualLink);
  }

  /**
   * Returns the latitude of real node associated to this virtual node.
   *
   * @return The latitude.
   */
  public double getLatitude() {
    return latitude;
  }

  /**
   * Returns the longitude of real node associated to this virtual node.
   *
   * @return The longitude.
   */
  public double getLongitude() {
    return longitude;
  }

  /**
   * Returns the "means" element of the virtual node.
   *
   * @return The means ID.
   */
  public byte getMeans() {
    return means;
  }

  /**
   * Returns the "mode" element of the virtual node.
   *
   * @return The mode ID.
   */
  public byte getMode() {
    return mode;
  }

  /**
   * Returns an integer that represents the mode+means combination. For instance (0)401 for mode 4,
   * means 1
   *
   * @return An int representing the mode-means combination.
   */
  public int getModeMeansKey() {
    return NodusC.MAXMM * mode + means;
  }

  /**
   * Returns an long integer that represents the line+mode+means combination.
   *
   * @return A long representing the mode-means-service combination.
   */
  public long getModeMeansServiceKey() {
    return NodusC.MAXMM * NodusC.MAXMM * service + NodusC.MAXMM * mode + means;
  }

  /**
   * Returns the ID of this virtual node.
   *
   * @return The ID of this virtual node.
   */
  public int getId() {
    return virtualNodeId;
  }

  /**
   * Returns the "link" element of the virtual node.
   *
   * @return The ID of the "link" element of this virtual node.
   */
  public int getRealLinkId() {
    return realLinkId;
  }

  /**
   * Returns the "node" element of the virtual node.
   *
   * @param signed boolean true if one needs the signed node ID.
   * @return The ID of the "node" element of this virtual node.
   */
  public int getRealNodeId(boolean signed) {
    if (signed) {
      return realNodeId;
    }

    if (realNodeId < 0) {
      return -realNodeId;
    }

    return realNodeId;
  }

  /**
   * Returns the "TransportService" element ID of the virtual node.
   *
   * @return The "service" ID associated to this virtual node.
   */
  public int getService() {
    return service;
  }

  /**
   * Returns POSITIVE or NEGATIVE, depending on the sign given to this virtual node.
   *
   * @return POSITIVE (true) or NEGATIVE (false)
   */
  public boolean getSign() {
    return sign;
  }

  /**
   * Returns the list of virtual links associated to this virtual node.
   *
   * @return Linked list of virtual links.
   */
  public LinkedList<VirtualLink> getVirtualLinkList() {
    return virtualLinks;
  }
}
