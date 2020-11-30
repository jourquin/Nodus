/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.services;

import com.bbn.openmap.omGraphics.OMGraphic;
import java.util.LinkedList;

/**
 * Convenience class that holds the data relative to a a service.
 *
 * @author Bart Jourquin
 */
public class Service {

  /** The list of OMGraphics along the service. */
  private LinkedList<OMGraphic> links;

  /** Transportation frequency on the service. */
  private int frequency;

  /** The numeric ID of the service. */
  private int id;

  /** Transportation means used on the service. */
  private int means = -1;

  /** Transportation mode on the service. */
  private int mode = -1;

  /** The name of the service. */
  private String name;

  /** The list of the stopNodes that composes a service. */
  private LinkedList<Integer> stopNodes;

  /** Transportation description on the service. */
  private String description;

  /**
   * Creates a new service.
   *
   * @param id The numeric ID of the service.
   */
  public Service(int id) {
    this.id = id;
    this.mode = -1;
    this.means = -1;
    links = new LinkedList<>();
    stopNodes = new LinkedList<>();
  }

  /**
   * Creates a new service.
   *
   * @param id The numeric ID of the service.
   * @param name The name of the service.
   * @param mode The mode used for this service.
   * @param means The means used along the service.
   * @param frequency The frequency of the service.
   * @param description The description of the service.
   */
  public Service(int id, String name, Byte mode, Byte means, int frequency, String description) {
    this.id = id;
    this.name = name;
    this.mode = mode;
    this.means = means;
    this.frequency = frequency;
    this.description = description;
    links = new LinkedList<>();
    stopNodes = new LinkedList<>();
  }

  /**
   * Adds a given link to the service.
   *
   * @param omg The link to add.
   */
  public void addChunk(OMGraphic omg) {
    links.add(omg);
  }

  /**
   * Adds a given node as stop point to the service.
   *
   * @param nodeId The ID of the node to add.
   */
  public void addStop(int nodeId) {
    stopNodes.add(nodeId);
  }

  /** Removes all the links from the service. */
  public void clear() {
    links.clear();
    mode = -1;
  }

  /**
   * Tests if a given node ID is a stop in the service.
   *
   * @param nodeId The ID of the node to test.
   * @return True if the node ID corresponds to a stop.
   */
  public boolean contains(int nodeId) {
    return stopNodes.contains(nodeId);
  }

  /**
   * Tests if a given link is part of the service.
   *
   * @param omg The link (polyline) to test.
   * @return True if the given link belongs to the service.
   */
  public boolean contains(OMGraphic omg) {
    return links.contains(omg);
  }

  /**
   * Gets the list of links of the service.
   *
   * @return The list if links along the service.
   */
  public LinkedList<OMGraphic> getLinks() {
    return links;
  }

  /**
   * Gets the frequency of the service.
   *
   * @return The frequency on the service.
   */
  public int getFrequency() {
    return frequency;
  }

  /**
   * Gets the numeric ID of the service.
   *
   * @return The numeric ID of the service.
   */
  public int getId() {
    return id;
  }

  /**
   * Get the transportation means used on the service.
   *
   * @return The means used on the service.
   */
  public int getMeans() {
    return means;
  }

  /**
   * Gets the transportation mode used on the service.
   *
   * @return The mode used on the service.
   */
  public int getMode() {
    return mode;
  }

  /**
   * Gets the name of the service.
   *
   * @return The name of the service.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the number of links that composes the service.
   *
   * @return The number of links of the service.
   */
  public int getNbLinks() {
    return links.size();
  }

  /**
   * Gets the number of stop nodes along the service.
   *
   * @return The number of stop nodes along the service.
   */
  public int getNbStops() {
    return stopNodes.size();
  }

  /**
   * Gets the list of stop nodes along the service.
   *
   * @return The list of stop nodes along the service.
   */
  public LinkedList<Integer> getStopNodes() {
    return stopNodes;
  }

  /**
   * Gets the description of the service.
   *
   * @return The description of the service.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Removes a given link from the service.
   *
   * @param link The link (polyline) to remove.
   */
  public void removeChunk(OMGraphic link) {
    links.remove(link);
  }

  /**
   * Removes a given stop node from the service.
   *
   * @param nodeId The ID of the node to remove.
   */
  public void removeStop(int nodeId) {
    stopNodes.remove((Integer) nodeId);
  }

  /**
   * Sets the link list associated to the service.
   *
   * @param links The list of links to associate to the service.
   */
  public void setChunks(LinkedList<OMGraphic> links) {
    this.links = links;
  }

  /**
   * Sets the frequency of the service.
   *
   * @param frequency Frequency of the service.
   */
  public void setFrequency(int frequency) {
    this.frequency = frequency;
  }

  /**
   * Set the transportation means used on the service.
   *
   * @param means Transportation means used on the service.
   */
  public void setMeans(int means) {
    this.means = means;
  }

  /**
   * Sets the transportation mode used on the service.
   *
   * @param mode Transportation mode used on the service.
   */
  public void setMode(int mode) {
    this.mode = mode;
  }

  /**
   * Sets the name of the service.
   *
   * @param name The name of the service.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Sets the list of stop nodes of the service.
   *
   * @param stopNodes The list of stop nodes of the service.
   */
  public void setStops(LinkedList<Integer> stopNodes) {
    this.stopNodes = stopNodes;
  }

  /**
   * Sets the description of the service.
   *
   * @param description The description of the service.
   */
  public void setDescription(String description) {
    this.description = description;
  }
}
