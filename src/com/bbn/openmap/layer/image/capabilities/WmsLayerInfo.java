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

package com.bbn.openmap.layer.image.capabilities;

import java.util.HashMap;

/**
 * Keeps information about a WMS layer.
 *
 * @author Bart Jourquin
 */
public class WmsLayerInfo {

  private float[] latLonBoundingBox;

  private HashMap<String, String> layerData;

  private WmsLayerInfo parentLayer;

  private WmsServerInfo wmsServer;

  /**
   * The information will be stored in a HashMap. A link is also set to the server information.
   *
   * @param server WmsServerInfo
   */
  public WmsLayerInfo(WmsServerInfo server) {
    wmsServer = server;
    layerData = new HashMap<>();
  }

  /**
   * Retrieves the value string kept for a given key.
   *
   * @param key String
   * @return String
   */
  public String getField(String key) {
    return layerData.get(key);
  }

  /**
   * Retrieves the bounding-box area for which this layer is available.
   *
   * @return float[]
   */
  public float[] getLatLonBoundingBox() {
    if (latLonBoundingBox == null && parentLayer != null) {
      return parentLayer.getLatLonBoundingBox();
    }

    return latLonBoundingBox;
  }

  /**
   * Retrieves parent layer.
   *
   * @return WmsLayerInfo
   */
  public WmsLayerInfo getParentLayer() {
    return parentLayer;
  }

  /**
   * Returns the server information related to this layer.
   *
   * @return WmsServerInfo
   */
  public WmsServerInfo getServerInformation() {
    return wmsServer;
  }

  /**
   * Put a given key-value pair in the data HashMap.
   *
   * @param key String
   * @param value String
   */
  public void setField(String key, String value) {
    layerData.put(key, value);
  }

  /**
   * Sets the bounding-box area for which this layer is available.
   *
   * @param minx float
   * @param miny float
   * @param maxx float
   * @param maxy float
   */
  public void setLatLonBoundingBox(float minx, float miny, float maxx, float maxy) {
    latLonBoundingBox = new float[] {minx, miny, maxx, maxy};
  }

  /**
   * Link to the parent layer.
   *
   * @param parent WmsLayerInfo
   */
  public void setParentLayer(WmsLayerInfo parent) {
    parentLayer = parent;
  }

  /**
   * Returns the content of the "title" field, if any.
   *
   * @return String
   */
  @Override
  public String toString() {
    if (getField("title") != null) {
      return getField("title");
    } else {
      return super.toString();
    }
  }
}
