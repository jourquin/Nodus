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

package com.bbn.openmap.layer.image.capabilities;

import java.util.Vector;

/**
 * Keeps information about a WMS server.
 *
 * @author Bart Jourquin
 */
public class WmsServerInfo extends Vector<Object> {

  static final long serialVersionUID = -4965813404972471051L;

  private Vector<String> getMapFormats = new Vector<>();

  private String getMapUrl;

  private String serverName;

  /**
   * Adds a format to the possible formats for this server.
   *
   * @param format String
   */
  public void addGetMapFormat(String format) {
    getMapFormats.add(format);
  }

  /**
   * Retrieves the available formats for this server.
   *
   * @return Vector
   */
  public Vector<String> getGetMapFormats() {
    return getMapFormats;
  }

  /**
   * Returns the URL string that represents the server to query for getMap requests.
   *
   * @return String
   */
  public String getGetMapUrl() {
    return getMapUrl;
  }

  /**
   * Returns the name of the server.
   *
   * @return String
   */
  public String getServerName() {
    return serverName;
  }

  /**
   * Sets the URL to be used for getMap requests.
   *
   * @param url String
   */
  public void setGetMapUrl(String url) {
    getMapUrl = url;
  }

  /**
   * Sets the name of the WMS server.
   *
   * @param name String
   */
  public void setServerName(String name) {
    serverName = name;
  }

  /**
   * Returns true if a given format is supported.
   *
   * @param format String
   * @return boolean
   */
  public boolean supportsGetMapFormat(String format) {
    return getMapFormats.contains(format);
  }

  /**
   * Returns the name of the WMS server.
   *
   * @return String
   */
  @Override
  public String toString() {
    return serverName;
  }
}
