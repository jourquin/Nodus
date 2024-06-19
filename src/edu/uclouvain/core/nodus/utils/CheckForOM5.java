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

package edu.uclouvain.core.nodus.utils;

import java.util.Enumeration;
import java.util.Properties;

/**
 * Simple utility that tries to handle OpenMap layers defined for Nodus version prior to 7.0. The
 * latest is based on OpenMap 6.0, which introduces several API changes. This utility tries to make
 * these changes transparent to the end user.
 *
 * @author Bart Jourquin
 */
public class CheckForOM5 {

  private static boolean debug = false;

  private static String[] formerNames = {
    "com.bbn.openmap.plugin.esri.EsriPlugIn",
    "com.bbn.openmap.plugin.esri.EsriLayer",
    "com.bbn.openmap.plugin.esri.NodusEsriPlugin",
    "com.bbn.openmap.plugin.wms.WMSPlugIn",
    "com.bbn.openmap.plugin.wms.NodusWMSPlugIn",
    "com.bbn.openmap.layer.imageTile.MapTileLayer",
    "com.bbn.openmap.layer.imageTile.NodusMapTileLayer",
    "com.bbn.openmap.layer.imageTile.GoogleTileLayer",
    "com.bbn.openmap.layer.imageTile.NodusGoogleTileLayer"
  };

  private static String[] newNames = {
    "com.bbn.openmap.layer.shape.FastEsriLayer",
    "com.bbn.openmap.layer.shape.FastEsriLayer",
    "com.bbn.openmap.layer.shape.NodusEsriLayer",
    "com.bbn.openmap.layer.image.WMSLayer",
    "com.bbn.openmap.layer.image.NodusWMSLayer",
    "com.bbn.openmap.layer.image.MapTileLayer",
    "com.bbn.openmap.layer.image.NodusMapTileLayer",
    "com.bbn.openmap.layer.image.GoogleTileLayer",
    "com.bbn.openmap.layer.image.NodusGoogleTileLayer"
  };

  /** Default constructor. */
  public CheckForOM5() {}

  /**
   * Checks if the properties contain old API names and converts them to new names.
   *
   * @param openMapProperties The OpenMap properties associated to a Nodus project.
   * @return The converted properties.
   */
  public static Properties upgradeApiNames(Properties openMapProperties) {

    int isUpgraded = 0;

    Enumeration<?> enumerator = openMapProperties.propertyNames();

    for (; enumerator.hasMoreElements(); ) {
      String propName = (String) enumerator.nextElement();
      String propValue = (String) openMapProperties.get(propName);
      propValue = propValue.trim();

      for (int i = 0; i < formerNames.length; i++) {
        if (propValue.equals(formerNames[i])) {
          openMapProperties.setProperty(propName, newNames[i]);
          isUpgraded++;

          if (debug) {
            if (isUpgraded == 1) {
              System.err.println("WARNING : The following OpenMap class names should be upgraded:");
            }

            System.err.print(
                "In layer '" + propName + "': '" + formerNames[i] + "' -> '" + newNames[i] + "'\n");
          }
        }
      }
    }
    return openMapProperties;
  }
}
