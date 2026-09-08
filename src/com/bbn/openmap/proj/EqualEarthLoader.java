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

import com.bbn.openmap.proj.coords.DatumShiftGCT;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.util.Debug;
import com.bbn.openmap.util.PropUtils;
import java.awt.geom.Point2D;
import java.util.Properties;

/** ProjectionLoader to add the Equal Earth projection to an OpenMap application. */
public class EqualEarthLoader extends BasicProjectionLoader implements ProjectionLoader {

  static final long serialVersionUID = 8314960950824233012L;

  /** Default constructor. */
  public EqualEarthLoader() {
    super(EqualEarth.class, EqualEarth.EqualEarthName, "Equal-area pseudocylindrical projection.");
  }

  @Override
  public Projection create(Properties properties) throws ProjectionException {
    try {
      LatLonPoint center = convertToLLP((Point2D) properties.get(ProjectionFactory.CENTER));
      float scale = PropUtils.floatFromProperties(properties, ProjectionFactory.SCALE, 10000000f);
      int height = PropUtils.intFromProperties(properties, ProjectionFactory.HEIGHT, 100);
      int width = PropUtils.intFromProperties(properties, ProjectionFactory.WIDTH, 100);

      Projection projection = new EqualEarth(center, scale, width, height);
      Ellipsoid datum = (Ellipsoid) properties.get(ProjectionFactory.DATUM);
      if (datum != null && datum != Ellipsoid.WGS_84) {
        projection = new DatumShiftProjection((GeoProj) projection, new DatumShiftGCT(datum));
      }
      return projection;
    } catch (Exception e) {
      if (Debug.debugging("proj")) {
        Debug.output("EqualEarthLoader: problem creating Equal Earth projection " + e.getMessage());
      }
      throw new ProjectionException("EqualEarthLoader: problem creating Equal Earth projection");
    }
  }
}
