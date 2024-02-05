/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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

package com.bbn.openmap.tools.drawing;

import java.util.LinkedList;

/**
 * This class is used to store the information about the line, we store all the characteristics
 * about the line, including the index of the layer, the origin and destination nodes and the new
 * node.
 *
 * @author Jorge Pinna
 * @hidden
 */
class LineSplitter {
  /** Number of the Destination Node. */
  private int destinationNode;

  /** Flag for know if we are in a Fusion Mode. */
  private boolean flag;

  /** index of the line in the layer. */
  private int index;

  /** Number of the New Node ( Fusion Node ). */
  private int insertedNode;

  /** Layer index about the link. */
  private int layerIndex;

  /** All the characteristics about the line. */
  private Object[] object;

  /** Number of the Origin Node. */
  private int originNode;
  /** Services of the link. */
  private LinkedList<String> services;

  /** Constructor Initialization of all the values. */
  public LineSplitter() {
    flag = false;
    layerIndex = 0;
    originNode = 0;
    destinationNode = 0;
    insertedNode = 0;
  }

  /**
   * Get the Number of the Destination Node.
   *
   * @return The number of the Destination Node.
   */
  public int getDestinationNode() {
    return destinationNode;
  }

  /**
   * Get the Flag for know if we are in ah Fusion Mode.
   *
   * @return The Flag for know if we are in ah Fusion Mode.
   */
  public boolean getFlag() {
    return flag;
  }

  /**
   * Get the Number of the Fusion Node.
   *
   * @return The number of the Fusion Node.
   */
  public int getInsertedNode() {
    return insertedNode;
  }

  /**
   * Get the Layer index about the link.
   *
   * @return The Layer index about the link.
   */
  public int getLayerIndex() {
    return layerIndex;
  }

  /**
   * Get the index of the line in the layer.
   *
   * @return The index of the line in the layer.
   */
  public int getLineIndex() {
    return index;
  }

  /**
   * Get the Object from a position in the array.
   *
   * @param index . Position in the array.
   * @return The Object in the array.
   */
  public Object getLineProperty(int index) {
    return object[index];
  }

  /**
   * Get the Number of the Origin Node.
   *
   * @return The ID of the Origin Node.
   */
  public int getOriginNode() {
    return originNode;
  }

  /**
   * Get the Services.
   *
   * @return TransportService
   */
  public LinkedList<String> getServices() {
    return services;
  }

  /**
   * Set the Number of the Destination Node.
   *
   * @param Destination Node. ID of the Destination Node.
   */
  public void setDestinationNode(int destinationNode) {
    this.destinationNode = destinationNode;
  }

  /**
   * Set the Flag for know if we are in a Fusion Mode.
   *
   * @param Flag . Flag for know if we are in ah Fusion Mode.
   */
  public void setFlag(boolean flag) {
    this.flag = flag;
  }

  /**
   * Set the Number of the Fusion Node.
   *
   * @param LineSplitter Node. Number of the Fusion Node.
   */
  public void setInsertedNode(int insertedFusion) {
    this.insertedNode = insertedFusion;
  }

  /**
   * Set the Layer index about the link.
   *
   * @param Layer Index. Layer index about the link.
   */
  public void setLayerIndex(int layerIndex) {
    this.layerIndex = layerIndex;
  }

  /**
   * Set the index of the line in the layer.
   *
   * @param Index . index of the line in the layer.
   */
  public void setLineIndex(int index) {
    this.index = index;
  }

  /**
   * Initialization of the array Object used for store all characteristics of the link.
   *
   * @param i . Length of the array of
   *     Objects.linksLayers[layerIndexOfSelectedGraphic].reloadLabels();
   */
  public void setLineProperties(int i) {
    object = new Object[i];
  }

  /**
   * Set the Object used for store a characteristics of the link.
   *
   * @param i . Position in the array.
   * @param o . Object to set in the array.
   */
  public void setLineproperty(int i, Object o) {
    object[i] = o;
  }
  
  /**
   * Set the Number of the Origin Node.
   *
   * @param Origin Node. Number of the Origin Node.
   */
  public void setOriginNode(int originNode) {
    this.originNode = originNode;
  }
  
  /**
   * Set the Services.
   *
   * @param services The service ID
   */
  public void setServices(LinkedList<String> services) {
    this.services = services;
  }
}
