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

package edu.uclouvain.core.nodus.utils;

import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.GreatCircle;
import com.bbn.openmap.proj.Length;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.util.List;

/** Shared helpers for initializing real-link data attached to link graphics. */
public final class RealLinkUtils {

  private RealLinkUtils() {}

  /**
   * Initializes the RealLink attached to a link graphic.
   *
   * @param linkGraphic The link graphic.
   * @param linkRecord DBF record attached to the link graphic.
   * @param resetPassengerCarUnits True to reset passenger-car units.
   * @return The initialized RealLink.
   */
  public static RealLink initializeRealLink(
      OMGraphic linkGraphic, List<Object> linkRecord, boolean resetPassengerCarUnits) {
    return initializeRealLink(linkGraphic, linkRecord, null, null, resetPassengerCarUnits);
  }

  /**
   * Initializes the RealLink attached to a link graphic, with optional endpoint-node fallback.
   *
   * @param linkGraphic The link graphic.
   * @param linkRecord DBF record attached to the link graphic.
   * @param node1Graphic First endpoint node graphic, or null.
   * @param node2Graphic Second endpoint node graphic, or null.
   * @param resetPassengerCarUnits True to reset passenger-car units.
   * @return The initialized RealLink.
   */
  public static RealLink initializeRealLink(
      OMGraphic linkGraphic,
      List<Object> linkRecord,
      OMGraphic node1Graphic,
      OMGraphic node2Graphic,
      boolean resetPassengerCarUnits) {
    if (linkGraphic == null) {
      throw new IllegalArgumentException("The link graphic is missing.");
    }
    if (linkRecord == null) {
      throw new IllegalArgumentException("The link record is missing.");
    }

    Object attribute = linkGraphic.getAttribute(0);
    if (!(attribute instanceof RealLink)) {
      linkGraphic.putAttribute(0, new RealLink());
    }

    RealLink realLink = (RealLink) linkGraphic.getAttribute(0);
    realLink.setOriginNodeId(JDBCUtils.getInt(linkRecord.get(NodusC.DBF_IDX_NODE1)));
    realLink.setLength(computeLength(linkGraphic, node1Graphic, node2Graphic));
    realLink.setSpeed(JDBCUtils.getFloat(linkRecord.get(NodusC.DBF_IDX_SPEED)));
    if (resetPassengerCarUnits) {
      realLink.resetPassengerCarUnits();
    }
    return realLink;
  }

  /** Computes the length of a link graphic in kilometers. */
  public static float computeLength(OMGraphic linkGraphic) {
    return computeLength(linkGraphic, null, null);
  }

  /** Computes the length of a link graphic in kilometers, with optional endpoint fallback. */
  public static float computeLength(
      OMGraphic linkGraphic, OMGraphic node1Graphic, OMGraphic node2Graphic) {
    float length = 0;
    if (linkGraphic instanceof EsriPolyline) {
      length = NodusEsriLayer.getLength((EsriPolyline) linkGraphic, Length.KM);
    } else if (linkGraphic instanceof OMPoly) {
      length = computePolyLength((OMPoly) linkGraphic);
    }

    if (length <= 0) {
      length = computeEndpointLength(node1Graphic, node2Graphic);
    }
    return length;
  }

  /** Computes a polyline length in kilometers from its coordinate array. */
  private static float computePolyLength(OMPoly polyline) {
    float length = 0;
    double[] coordinates = polyline.getLatLonArray();
    int nbPairs = coordinates.length / 2;

    for (int index = 0; index < nbPairs - 1; index++) {
      length +=
          GreatCircle.sphericalDistance(
              normalizeRadians(coordinates[index * 2]),
              normalizeRadians(coordinates[index * 2 + 1]),
              normalizeRadians(coordinates[index * 2 + 2]),
              normalizeRadians(coordinates[index * 2 + 3]));
    }
    return Length.KM.fromRadians(length);
  }

  /** Computes a straight-line endpoint length in kilometers. */
  private static float computeEndpointLength(OMGraphic node1Graphic, OMGraphic node2Graphic) {
    if (!(node1Graphic instanceof OMPoint) || !(node2Graphic instanceof OMPoint)) {
      return 0;
    }

    OMPoint point1 = (OMPoint) node1Graphic;
    OMPoint point2 = (OMPoint) node2Graphic;
    return (float)
        Length.KM.fromRadians(
            GreatCircle.sphericalDistance(
                normalizeRadians(point1.getLat()),
                normalizeRadians(point1.getLon()),
                normalizeRadians(point2.getLat()),
                normalizeRadians(point2.getLon())));
  }

  /** Converts degree coordinates to radians while leaving existing radians unchanged. */
  private static double normalizeRadians(double coordinate) {
    if (Math.abs(coordinate) > Math.PI) {
      return Math.toRadians(coordinate);
    }
    return coordinate;
  }
}
