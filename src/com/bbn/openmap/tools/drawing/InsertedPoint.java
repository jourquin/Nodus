/*
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

package com.bbn.openmap.tools.drawing;

/**
 * This class is used to store and process the information about the points (distance and
 * coordinate) in the polyline (x1,y1) (x2,y2) and return the intersection point (xi,yi) between the
 * line formed by the points (x1,y1) (x2,y2) and his perpendicular with the point of the click
 * (xc,yc), and return if the new point is between (x1,y1) and (x2,y2) and return the distance
 * between (xi,yi) and (xc,yc).
 *
 * @author Jorge Pinna
 * @hidden
 */
class InsertedPoint {
  /**
   * variable boolean show if the point of intersection is or not in the segment of the line (x1,y1)
   * (x2,y2).
   */
  public boolean inclu;

  /** Show the distance between (xi,yi) and (xc,yc). */
  public double length;

  /** Coordinate X of the point of intersection. */
  public double xi;

  /** Coordinate Y of the point of intersection. */
  public double yi;

  /**
   * Constructor Store and process the information about the points in the polyline (x1,y1) (x2,y2)
   * and return the intersection point (xi,yi) between the line formed by the points (x1,y1) (x2,y2)
   * and his perpendicular with the point (xc,yc), return if the new point is between (x1,y1) and
   * (x2,y2) and return the distance between the points (xi,yi) and (xc,yc).
   *
   * @param x1 Coordinate X of the first point.
   * @param y1 Coordinate Y of the first point.
   * @param x2 Coordinate X of the second point.
   * @param y2 Coordinate Y of the second point.
   * @param xc Coordinate X of the click point.
   * @param yc Coordinate Y of the click point.
   */
  public InsertedPoint(double x1, double y1, double x2, double y2, double xc, double yc) {
    final double a = (y2 - y1) / (x2 - x1);
    final double b = y1 - a * x1;
    final double c = yc + 1 / a * xc;

    if (y1 == y2) {
      xi = x1;
      yi = yc;
      return;
    }
    if (x1 == x2) {
      xi = xc;
      yi = y1;
      return;
    }
    if (x1 == x2 && y1 == y2) {
      inclu = false;
      return;
    }
    xi = a / (Math.pow(a, 2) + 1) * (c - b);
    yi = a * xi + b;
    if (length(x1, y1, xi, yi) > length(x1, y1, x2, y2)) {
      inclu = false;
    } else if (length(xi, yi, x2, y2) > length(x1, y1, x2, y2)) {
      inclu = false;
    } else {
      inclu = true;
    }

    length = length(xi, yi, xc, yc);
  }

  /**
   * Return the distance between two points (x1,y1) and (x2,y2).
   *
   * @param x1 Coordinate X of the first point.
   * @param y1 Coordinate Y of the first point.
   * @param x2 Coordinate X of the second point.
   * @param y2 Coordinate Y of the second point.
   * @return (double) Distance between two points
   */
  private double length(double x1, double y1, double x2, double y2) {
    return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
  }
}
