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

package edu.uclouvain.core.nodus.compute.real;

import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.GreatCircle;
import com.bbn.openmap.proj.Length;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.util.List;

/** Shared helpers for initializing real-link data attached to link graphics. */
public final class RealLinkInitializer {

  private RealLinkInitializer() {}

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

  /**
   * Computes the length of a link graphic in kilometers.
   *
   * @param linkGraphic The link graphic.
   * @return The length of the link graphic in kilometers.
   */
  public static float computeLength(OMGraphic linkGraphic) {
    return computeLength(linkGraphic, null, null);
  }

  /**
   * Computes the length of a link graphic in kilometers, with optional endpoint fallback.
   *
   * <p>Polyline arrays are read in radians using OpenMap's accessor contract; endpoint OMPoint
   * coordinates are in decimal degrees. Missing or zero-length geographic geometry falls back to
   * the endpoint locations, or zero if either endpoint has no geographic location.
   *
   * @param linkGraphic The link graphic.
   * @param node1Graphic First endpoint node graphic, or null.
   * @param node2Graphic Second endpoint node graphic, or null.
   * @return The length of the link graphic in kilometers.
   */
  public static float computeLength(
      OMGraphic linkGraphic, OMGraphic node1Graphic, OMGraphic node2Graphic) {
    float length = 0;
    // EsriPolyline extends OMPoly and exposes the same radians-based coordinate array.
    if (linkGraphic instanceof OMPoly) {
      length = computePolyLength((OMPoly) linkGraphic);
    }

    if (length <= 0) {
      length = computeEndpointLength(node1Graphic, node2Graphic);
    }
    return length;
  }

  /** Computes a polyline length in kilometers from its coordinate array. */
  private static float computePolyLength(OMPoly polyline) {
    if (polyline.getRenderType() != OMGraphic.RENDERTYPE_LATLON) {
      return 0;
    }
    // getLatLonArray converts declared degree coordinates to radians when necessary.
    final double[] coordinates = polyline.getLatLonArray();
    if (coordinates == null) {
      return 0;
    }
    final int nbPairs = coordinates.length / 2;
    double length = 0;

    for (int index = 0; index < nbPairs - 1; index++) {
      length +=
          GreatCircle.sphericalDistance(
              coordinates[index * 2],
              coordinates[index * 2 + 1],
              coordinates[index * 2 + 2],
              coordinates[index * 2 + 3]);
    }
    return (float) Length.KM.fromRadians(length);
  }

  /** Computes a straight-line endpoint length in kilometers. */
  private static float computeEndpointLength(OMGraphic node1Graphic, OMGraphic node2Graphic) {
    if (!isGeographicPoint(node1Graphic) || !isGeographicPoint(node2Graphic)) {
      return 0;
    }

    final OMPoint point1 = (OMPoint) node1Graphic;
    final OMPoint point2 = (OMPoint) node2Graphic;
    return (float)
        Length.KM.fromRadians(
            GreatCircle.sphericalDistance(
                Math.toRadians(point1.getLat()),
                Math.toRadians(point1.getLon()),
                Math.toRadians(point2.getLat()),
                Math.toRadians(point2.getLon())));
  }

  /** Screen-only points have no latitude/longitude; offset points retain a geographic anchor. */
  private static boolean isGeographicPoint(OMGraphic graphic) {
    return graphic instanceof OMPoint
        && (graphic.getRenderType() == OMGraphic.RENDERTYPE_LATLON
            || graphic.getRenderType() == OMGraphic.RENDERTYPE_OFFSET);
  }
}
