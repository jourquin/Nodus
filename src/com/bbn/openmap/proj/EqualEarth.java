/*
 * Copyright (c) 1991-2026 Universite catholique de Louvain
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

package com.bbn.openmap.proj;

import com.bbn.openmap.proj.coords.LatLonPoint;
import java.awt.geom.Point2D;

/**
 * Implements the Equal Earth projection.
 *
 * <p>Equal Earth is an equal-area pseudocylindrical projection designed for small-scale world maps.
 * This implementation uses the spherical equations published by Savric, Patterson and Jenny.
 *
 * @author Bart Jourquin
 */
@SuppressWarnings("unchecked")
public class EqualEarth extends Cylindrical {

  static final long serialVersionUID = 6167369353155818510L;

  /** Projection name. */
  public static final transient String EqualEarthName = "Equal Earth";

  private static final double A1 = 1.340264;
  private static final double A2 = -0.081106;
  private static final double A3 = 0.000893;
  private static final double A4 = 0.003796;
  private static final double M = Math.sqrt(3.0) / 2.0;
  private static final double MAX_THETA = Math.PI / 3.0;
  private static final double MAX_Y = 1.3173627591574;
  private static final double WORLD_WIDTH = 2.0 * Math.PI / (M * A1);
  private static final double INVERSE_TOLERANCE = 1.0E-11;
  private static final int MAX_INVERSE_ITERATIONS = 12;

  /** Half of the projection window height, in pixels. */
  protected transient double hy;

  /** Half of the projection window width, in pixels. */
  protected transient double wx;

  /** Projection center longitude, in radians. */
  protected transient double centerLonRad;

  /** Projection center latitude, in radians. */
  protected transient double centerLatRad;

  /** Equal Earth projected Y coordinate of the projection center. */
  protected transient double centerYProjected;

  /**
   * Constructs an Equal Earth projection.
   *
   * @param center LatLonPoint center of projection
   * @param scale scale of projection
   * @param width width of screen
   * @param height height of screen
   */
  public EqualEarth(LatLonPoint center, float scale, int width, int height) {
    super(center, scale, width, height);
  }

  @Override
  public String toString() {
    return "EqualEarth[" + super.toString() + "]";
  }

  @Override
  protected void computeParameters() {
    super.computeParameters();

    maxscale = Math.floor((planetPixelRadius * WORLD_WIDTH) / width);
    if (maxscale < minscale) {
      maxscale = minscale;
    }
    if (scale > maxscale) {
      scale = maxscale;
      scaled_radius = planetPixelRadius / scale;
    }

    if (world == null) {
      world = new java.awt.Point(0, 0);
    }
    world.x = (int) (planetPixelRadius * WORLD_WIDTH / scale);
    half_world = world.x / 2;

    hy = height / 2.0;
    wx = width / 2.0;
    centerLonRad = centerX;
    centerLatRad = normalizeLatitude(centerY);
    centerY = centerLatRad;
    double centerTheta = theta(centerLatRad);
    double centerTheta2 = centerTheta * centerTheta;
    double centerTheta6 = centerTheta2 * centerTheta2 * centerTheta2;
    centerYProjected = polynomial(centerTheta, centerTheta2, centerTheta6);
  }

  @Override
  public double normalizeLatitude(double lat) {
    if (lat > ProjMath.NORTH_POLE_D) {
      return ProjMath.NORTH_POLE_D;
    }
    if (lat < ProjMath.SOUTH_POLE_D) {
      return ProjMath.SOUTH_POLE_D;
    }
    return lat;
  }

  @Override
  public boolean isPlotable(double lat, double lon) {
    return lat <= 90.0 && lat >= -90.0 && lon <= 180.0 && lon >= -180.0;
  }

  @Override
  public Point2D forward(double lat, double lon, Point2D point, boolean isRadian) {
    if (point == null) {
      point = new Point2D.Double();
    }

    double latRad;
    double lonRad;
    if (isRadian) {
      latRad = normalizeLatitude(lat);
      lonRad = ProjMath.wrapLongitude(lon - centerLonRad);
    } else {
      latRad = normalizeLatitude(Math.toRadians(lat));
      lonRad = ProjMath.wrapLongitude(Math.toRadians(lon) - centerLonRad);
    }

    double theta = theta(latRad);
    double theta2 = theta * theta;
    double theta6 = theta2 * theta2 * theta2;
    double denominator = M * derivative(theta, theta2, theta6);
    double projectedX = lonRad * Math.cos(theta) / denominator;
    double projectedY = polynomial(theta, theta2, theta6);

    point.setLocation(
        wx + scaled_radius * projectedX, hy - scaled_radius * (projectedY - centerYProjected));
    return point;
  }

  @Override
  public <T extends Point2D> T inverse(double x, double y, T point) {
    if (point == null) {
      point = (T) new LatLonPoint.Double();
    }

    double projectedY = centerYProjected + (hy - y) / scaled_radius;
    double theta = inverseTheta(projectedY);
    double theta2 = theta * theta;
    double theta6 = theta2 * theta2 * theta2;
    double cosTheta = Math.cos(theta);
    double lonRad = centerLonRad;

    if (Math.abs(cosTheta) > INVERSE_TOLERANCE) {
      double projectedX = (x - wx) / scaled_radius;
      lonRad += projectedX * M * derivative(theta, theta2, theta6) / cosTheta;
    }

    double sinLat = Math.sin(theta) / M;
    if (sinLat > 1.0) {
      sinLat = 1.0;
    } else if (sinLat < -1.0) {
      sinLat = -1.0;
    }

    point.setLocation(
        Math.toDegrees(ProjMath.wrapLongitude(lonRad)), Math.toDegrees(Math.asin(sinLat)));
    return point;
  }

  @Override
  public String getName() {
    return EqualEarthName;
  }

  private static double theta(double latRad) {
    return Math.asin(M * Math.sin(latRad));
  }

  private static double polynomial(double theta, double theta2, double theta6) {
    return theta * (A1 + A2 * theta2 + theta6 * (A3 + A4 * theta2));
  }

  private static double derivative(double theta, double theta2, double theta6) {
    return A1 + 3.0 * A2 * theta2 + theta6 * (7.0 * A3 + 9.0 * A4 * theta2);
  }

  private static double inverseTheta(double projectedY) {
    if (projectedY > MAX_Y) {
      return MAX_THETA;
    }
    if (projectedY < -MAX_Y) {
      return -MAX_THETA;
    }

    double theta = projectedY / A1;
    for (int i = 0; i < MAX_INVERSE_ITERATIONS; i++) {
      double theta2 = theta * theta;
      double theta6 = theta2 * theta2 * theta2;
      double delta =
          (polynomial(theta, theta2, theta6) - projectedY) / derivative(theta, theta2, theta6);
      theta -= delta;
      if (Math.abs(delta) < INVERSE_TOLERANCE) {
        break;
      }
    }

    if (theta > MAX_THETA) {
      return MAX_THETA;
    }
    if (theta < -MAX_THETA) {
      return -MAX_THETA;
    }
    return theta;
  }
}
