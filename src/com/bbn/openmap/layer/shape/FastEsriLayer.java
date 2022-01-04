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

package com.bbn.openmap.layer.shape;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.io.FormatException;
import com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndexFactory;
import com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndexLinear;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.event.NodusMapMouseInterpreter;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.util.MoreMath;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Properties;

/**
 * An EsriLayer that uses a memory spatial index in order to limit repainting to the visible part of
 * the map. This dramatically boosts the performance for large layers.
 *
 * @author Bart Jourquin
 */
public class FastEsriLayer extends EsriLayer {

  private static final long serialVersionUID = -5646162140876554837L;

  /** List of object to currently display. */
  private OMGraphicList currentProjectedList = null;

  /** Track the previous projection. */
  private Projection previousProj = null;

  /** Spatial index for faster drawing. */
  private DisplaySpatialIndexLinear spatialIndex = null;

  /**
   * Sets the properties for the <code>Layer</code>.
   *
   * @param prefix the token to prefix the property names
   * @param properties the <code>Properties</code> object
   */
  public void setProperties(String prefix, Properties properties) {
    super.setProperties(prefix, properties);
    setMouseEventInterpreter(new NodusMapMouseInterpreter(this));
  }

  /**
   * Builds a description in HTML for a tool tip for the specified OMGraphic. This method overrides
   * the original one in order to properly display large integer values (not in scientific format).
   *
   * @param index the index of the graphic in the table
   */
  @Override
  public String getDescription(int index) {

    StringBuffer v = new StringBuffer();

    if (getModel() != null) {
      v.append("<HTML><BODY>");
      for (int i = 0; i < getTable().getColumnCount(); i++) {
        try {
          String column = getTable().getColumnName(i);
          String value = getTable().getValueAt(index, i) + "";

          // Properly display integer values
          if (getModel().getType(i) == DbfTableModel.TYPE_NUMERIC) {
            if (getModel().getDecimalCount(i) == 0) {
              try {
                value = "" + (long) Double.parseDouble(value);
              } catch (NumberFormatException e1) {
                e1.printStackTrace();
              }
            }
          }

          v.append(i == 0 ? "<b>" : "<BR><b>").append(column).append(":</b> ").append(value);
        } catch (NullPointerException npe) {
          npe.printStackTrace();
        } catch (IndexOutOfBoundsException obe) {
          obe.printStackTrace();
        }
      }

      v.append("</BODY></HTML>");
    }
    return v.toString();
  }

  /**
   * Returns the list of visible objects.
   *
   * @return OMGraphicList
   */
  public OMGraphicList getVisibleEsriGraphicList() {
    if (currentProjectedList == null) {
      currentProjectedList = getEsriGraphicList();
    }

    return currentProjectedList;
  }

  /**
   * Returns the EsriGraphicList for this layer and creates the spatial index if needed.
   *
   * @return The EsriGraphicList for this layer
   */
  @Override
  public synchronized EsriGraphicList getEsriGraphicList() {
    EsriGraphicList retVal = super.getEsriGraphicList();

    if (spatialIndex == null) {
      spatialIndex = (DisplaySpatialIndexLinear) DisplaySpatialIndexFactory.createIndex(retVal);
    }
    return retVal;
  }

  /** Creates the list of objects to refresh on the screen, using a spatial index. */
  private OMGraphicList getSpatialList(Projection projection) {
    OMGraphicList retVal = null;
    LatLonPoint.Double ul = projection.getUpperLeft();
    LatLonPoint.Double lr = projection.getLowerRight();
    float ulLat = ul.getLatitude();
    float ulLon = ul.getLongitude();
    float lrLat = lr.getLatitude();
    float lrLon = lr.getLongitude();

    // check for dateline anomaly on the screen. we check for
    // ulLon >= lrLon, but we need to be careful of the check for
    // equality because of floating point arguments...
    if (ulLon > lrLon || MoreMath.approximately_equal(ulLon, lrLon, .001f)) {

      double ymin = Math.min(ulLat, lrLat);
      double ymax = Math.max(ulLat, lrLat);

      try {
        retVal =
            spatialIndex.getOMGraphics(
                ulLon, ymin, 180.0d, ymax, null, drawingAttributes, projection, null);
      } catch (InterruptedIOException iioe) {
        // This means that the thread has been interrupted,
        // probably due to a projection change. Not a big
        // deal, just return, don't do any more work, and let
        // the next thread solve all problems.
        retVal = null;
      } catch (IOException ex) {
        ex.printStackTrace();
      } catch (FormatException fe) {
        fe.printStackTrace();
      }
    } else {

      double xmin = Math.min(ulLon, lrLon);
      double xmax = Math.max(ulLon, lrLon);
      double ymin = Math.min(ulLat, lrLat);
      double ymax = Math.max(ulLat, lrLat);

      try {
        retVal =
            spatialIndex.getOMGraphics(
                xmin, ymin, xmax, ymax, retVal, getDrawingAttributes(), projection, null);
      } catch (InterruptedIOException iioe) {
        // This means that the thread has been interrupted,
        // probably due to a projection change. Not a big
        // deal, just return, don't do any more work, and let
        // the next thread solve all problems.
        retVal = null;
      } catch (java.io.IOException ex) {
        ex.printStackTrace();
      } catch (FormatException fe) {
        fe.printStackTrace();
      }
    }
    return retVal;
  }

  /** Overrides the original method to limit the search into the current view. */
  @Override
  public String getToolTipTextFor(OMGraphic omg) {
    OMGraphicList list = getVisibleEsriGraphicList();
    Integer attributeIndex = (Integer) omg.getAttribute(SHAPE_INDEX_ATTRIBUTE);
    if (attributeIndex != null) {
      return getDescription(attributeIndex.intValue());
    } else {
      /* Nodes or links that are moved, split... can be out of the visible list if
       * the latest was not yet refreshed
       */
      list = getEsriGraphicList();
      int index = list.indexOf(omg);
      if (index != -1) {
        return getDescription(index);
      } else {
        return null;
      }
    }
  }

  /** Overrides the original method to limit the rendering to the current view. */
  @Override
  public OMGraphicList prepare() {

    Projection proj = getProjection();

    // Force reset of display spatial index on projection change (after zoom, pan or resize)
    if (previousProj != null) {
      if (proj.getScale() != previousProj.getScale()
          || proj.getHeight() != previousProj.getHeight()
          || proj.getWidth() != previousProj.getWidth()
          || proj.getCenter() != previousProj.getCenter()) {
        spatialIndex = null;
        currentProjectedList = null;
      }
    }
    previousProj = proj;

    OMGraphicList list = getVisibleEsriGraphicList();

    if (list != null) {
      if (spatialIndex != null && proj != null) {
        currentProjectedList = getSpatialList(proj);
        if (currentProjectedList != null) {
          list = currentProjectedList;
          list.generate(proj);
        }
      }

      // Setting the list up so that if anything is "selected",
      // it will also be drawn on top of all the other
      // OMGraphics. This maintains order while also making any
      // line edge changes more prominent.
      OMGraphicList parent = new OMGraphicList();
      parent.add(selectedGraphics);
      parent.add(list);
      list = parent;
    }

    return list;
  }
}
