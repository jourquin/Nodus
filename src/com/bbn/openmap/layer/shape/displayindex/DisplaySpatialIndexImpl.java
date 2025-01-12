/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

package com.bbn.openmap.layer.shape.displayindex;

import com.bbn.openmap.dataAccess.shape.EsriGraphic;
import com.bbn.openmap.io.FormatException;
import com.bbn.openmap.omGraphics.DrawingAttributes;
import com.bbn.openmap.omGraphics.OMGeometry;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMLine;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.omGraphics.OMRasterObject;
import com.bbn.openmap.omGraphics.OMRect;
import com.bbn.openmap.omGraphics.OMText;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.DataBounds;
import com.bbn.openmap.util.DataBoundsProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A Spatial Index is a variation on a Shape Index, adding the bounding box. */
public abstract class DisplaySpatialIndexImpl implements DisplaySpatialIndex {

  private static class BoundsEntry {

    DataBounds bounds;

    OMGraphic omg;

    /**
     * An element of the index.
     *
     * @param bounds Bounding box
     * @param byteOffset Offset in the shape file
     * @param omg The graphic
     */
    public BoundsEntry(DataBounds bounds, OMGraphic omg) {
      this.bounds = bounds;
      this.omg = omg;
    }

    /**
     * Return the graphic related to the index element.
     *
     * @return The graphic
     */
    public OMGraphic getGraphic() {
      return omg;
    }

    /**
     * Returns true if the element intersects with the bounding box.
     *
     * @param boundingBox The bounding box
     * @return True if intersects
     */
    public boolean intersects(DataBounds boundingBox) {
      return DisplaySpatialIndexImpl.intersects(bounds, boundingBox);
    }
  }

  private static int LL_MAX_Lat = 0;

  private static int LL_MAX_Lon = 1;

  private static int LL_MIN_Lat = 2;

  private static int LL_MIN_Lon = 3;

  /**
   * Determines if two rectangles intersect. Actually, this method determines if two rectangles
   * don't intersect, and then returns a negation of that result. But the bottom line is the same.
   *
   * @param boundingBoxA First rectangle
   * @param boundingBoxB Second rectangle
   * @return <code>true</code> if the rectangles intersect, <code>false</code> if they do not
   */
  protected static boolean intersects(DataBounds boundingBoxA, DataBounds boundingBoxB) {
    return intersects(
        boundingBoxA.getMin().getX(),
        boundingBoxA.getMin().getY(),
        boundingBoxA.getMax().getX(),
        boundingBoxA.getMax().getY(),
        boundingBoxB.getMin().getX(),
        boundingBoxB.getMin().getY(),
        boundingBoxB.getMax().getX(),
        boundingBoxB.getMax().getY());
  }

  /**
   * Determines if two rectangles intersect. Actually, this method determines if two rectangles
   * don't intersect, and then returns a negation of that result. But the bottom line is the same.
   *
   * @param xmin1 the small x of rectangle 1
   * @param ymin1 the small y of rectangle 1
   * @param xmax1 the big x of rectangle 1
   * @param ymax1 the big y of rectangle 1
   * @param xmin2 the small x of rectangle 2
   * @param ymin2 the small y of rectangle 2
   * @param xmax2 the big x of rectangle 2
   * @param ymax2 the big y of rectangle 2
   * @return <code>true</code> if the rectangles intersect, <code>false</code> if they do not
   */
  protected static boolean intersects(
      double xmin1,
      double ymin1,
      double xmax1,
      double ymax1,
      double xmin2,
      double ymin2,
      double xmax2,
      double ymax2) {
    return !(xmax1 <= xmin2 || ymax1 <= ymin2 || xmin1 >= xmax2 || ymin1 >= ymax2);
  }

  /**
   * Determines if two rectangles intersect. Actually, this method determines if two rectangles
   * don't intersect, and then returns a negation of that result. But the bottom line is the same.
   *
   * @param boundingBoxA First rectangle
   * @param boundingBoxB Second rectangle
   * @return <code>true</code> if the rectangles intersect, <code>false</code> if they do not
   *     A------------| | B-----| | | | | | | |-----| | |____________|
   */
  protected static boolean subset(DataBounds boundingBoxA, DataBounds boundingBoxB) {
    return subset(
        boundingBoxA.getMin().getX(),
        boundingBoxA.getMin().getY(),
        boundingBoxA.getMax().getX(),
        boundingBoxA.getMax().getY(),
        boundingBoxB.getMin().getX(),
        boundingBoxB.getMin().getY(),
        boundingBoxB.getMax().getX(),
        boundingBoxB.getMax().getY());
  }

  /**
   * Determines if two rectangles intersect. Actually, this method determines if two rectangles
   * don't intersect, and then returns a negation of that result. But the bottom line is the same.
   *
   * @param xmin1 the small x of rectangle A
   * @param ymin1 the small y of rectangle A
   * @param xmax1 the big x of rectangle A
   * @param ymax1 the big y of rectangle A
   * @param xmin2 the small x of rectangle B
   * @param ymin2 the small y of rectangle B
   * @param xmax2 the big x of rectangle B
   * @param ymax2 the big y of rectangle B
   * @return <code>true</code> if the rectangles intersect, <code>false</code> if they do not
   *     A------------| | B-----| | | | | | | |-----| | |____________|
   */
  protected static boolean subset(
      double xminA,
      double yminA,
      double xmaxA,
      double ymaxA,
      double xminB,
      double yminB,
      double xmaxB,
      double ymaxB) {
    return xmaxA >= xmaxB && ymaxA >= ymaxB && xminA <= xminB && yminA <= yminB;
  }

  protected OMGraphicList graphicList;

  /** A cached list of the SpatialIndexInMem file spatial index, for repeated reference. */
  protected OMGraphicList cachedList;

  /** A cached list of the SpatialIndexLinear file spatial index, for repeated reference. */
  protected List<BoundsEntry> boundsEntryList;

  /** The bounds of all the shapes in the shape file. */
  protected DataBounds globalDataBounds = null;

  /**
   * Constructor.
   *
   * @param list The graphic list.
   */
  public DisplaySpatialIndexImpl(OMGraphicList list) {
    setList(list);
  }

  protected void addBounds(DataBounds db) {
    if (globalDataBounds == null) {
      globalDataBounds = new DataBounds();
    }
    globalDataBounds.add(db.getMin());
    globalDataBounds.add(db.getMax());
  }

  protected int addEntry(BoundsEntry e) {
    int retVal = -1;
    if (e != null) {
      List<BoundsEntry> lst = getBoundsEntryList();
      lst.add(e);
      retVal += lst.size();
      addBounds(e.bounds);
    }
    return retVal;
  }

  /**
   * Replacement for the OMGraphicList.clone() method which throws a concurrent exception since
   * OpenMap 5.01.
   *
   * @param list The graphic list.
   * @return A cloned list.
   */
  private OMGraphicList clone(OMGraphicList list) {
    OMGraphicList omgl = new OMGraphicList(list.size());

    Iterator<OMGraphic> it = list.iterator();
    while (it.hasNext()) {
      OMGraphic omg = it.next();
      if (omg instanceof OMGraphicList) {
        omgl.add(clone((OMGraphicList) omg));
      } else {
        omgl.add(omg);
      }
    }
    return omgl;
  }

  /**
   * Provides an iterator over the SpatialIndexLinear spatial index.
   *
   * @return An iterator
   * @throws IOException On error
   * @throws FormatException On error
   */
  @Override
  public Iterator<BoundsEntry> entryIterator() throws IOException, FormatException {
    List<BoundsEntry> lst = getBoundsEntryList();
    if (lst == null) {
      indexList(graphicList);
    }

    return lst.iterator();
  }

  protected List<BoundsEntry> getBoundsEntryList() {
    if (boundsEntryList == null) {
      boundsEntryList = new ArrayList<>();
      globalDataBounds = null;
    }
    return boundsEntryList;
  }

  /** Creates a subset. */
  protected List<BoundsEntry> getBoundsEntrySubSet(DataBounds area) {
    List<BoundsEntry> retVal = new ArrayList<>();
    BoundsEntry entry = null;
    Iterator<BoundsEntry> it = null;

    try {
      it = entryIterator();
    } catch (Exception e) {
      System.err.println("Should never happen.");
      e.printStackTrace();
    }

    while (it != null && it.hasNext()) {
      entry = it.next();
      if (entry.intersects(area)) {
        retVal.add(entry);
      }
    }

    return retVal;
  }

  /**
   * Returns a DataBounds object describing the area of coverage. May be null if the data hasn't
   * been evaluated yet.
   */
  @Override
  public DataBounds getDataBounds() {
    if (globalDataBounds == null) {
      try {
        locateRecords(-180, -90, 180, 90);
      } catch (IOException ioe) {
        globalDataBounds = null;
      } catch (FormatException fe) {
        globalDataBounds = null;
      }
    }
    return globalDataBounds;
  }

  /**
   * Returns the data bounds of a geometry.
   *
   * @param geom The geometry
   * @return The data bounds
   */
  public DataBounds getDataBounds(OMGeometry geom) {
    DataBounds retVal = null;
    if (geom != null) {
      double ymin;
      double ymax;
      double xmin;
      double xmax;
      ymin = xmin = Double.MAX_VALUE;
      ymax = xmax = Double.MIN_VALUE;

      if (geom instanceof EsriGraphic) {
        EsriGraphic eomg = (EsriGraphic) geom;
        if (eomg != null) {
          double[] extents = eomg.getExtents();
          ymin = Math.min(extents[LL_MIN_Lat], extents[LL_MAX_Lat]);
          ymax = Math.max(extents[LL_MIN_Lat], extents[LL_MAX_Lat]);
          xmin = Math.min(extents[LL_MIN_Lon], extents[LL_MAX_Lon]);
          xmax = Math.max(extents[LL_MIN_Lon], extents[LL_MAX_Lon]);
        }
      } else if (geom instanceof DataBoundsProvider) {
        retVal = ((DataBoundsProvider) geom).getDataBounds();
      } else if (geom instanceof OMPoint) {
        OMPoint omp = (OMPoint) geom;
        ymin = ymax = omp.getLat();
        xmin = xmax = omp.getLon();
      } else if (geom instanceof OMPoly) {
        retVal = getLatLonArrDataBounds(((OMPoly) geom).getLatLonArray());
      } else if (geom instanceof OMLine) {
        retVal = getLatLonArrDataBounds(((OMLine) geom).getLL());
      } else if (geom instanceof OMText) {
        OMText omp = (OMText) geom;
        ymin = ymax = omp.getLat();
        xmin = xmax = omp.getLon();
      } else if (geom instanceof OMRasterObject) {
        OMRasterObject omp = (OMRasterObject) geom;
        ymin = ymax = omp.getLat();
        xmin = xmax = omp.getLon();
      } else if (geom instanceof OMRect) {
        OMRect omgr = (OMRect) geom;
        if (omgr != null) {
          ymin = omgr.getSouthLat();
          ymax = omgr.getNorthLat();
          xmin = omgr.getEastLon();
          xmax = omgr.getWestLon();
        }
      } else if (geom instanceof OMGraphicList) {
        OMGraphicList omgrl = (OMGraphicList) geom;
        DataBounds ret = null;
        for (Iterator<?> iter = omgrl.iterator(); iter.hasNext(); ) {
          if (retVal == null) {
            retVal = getDataBounds((OMGeometry) iter.next());
          } else {
            ret = getDataBounds((OMGeometry) iter.next());
            if (ret != null) {
              retVal.add(ret.getMin());
              retVal.add(ret.getMax());
            }
          }
        }
      }

      if (retVal == null && xmax != Double.MIN_VALUE) {
        retVal = new DataBounds(xmin, ymin, xmax, ymax);
      }
    }
    return retVal;
  }

  @Override
  public OMGraphicList getEmptyList() {
    OMGraphicList retVal = null;
    if (cachedList != null) {
      retVal = clone(cachedList);
    }
    return retVal;
  }

  protected BoundsEntry getEntry(int idx) {
    BoundsEntry retVal = null;
    List<BoundsEntry> lst = getBoundsEntryList();
    if (idx < lst.size()) {
      retVal = lst.get(idx);
    }
    return retVal;
  }

  /**
   * Returns the data bounds that covers all the points passed as parameter.
   *
   * @param rawllpts An area of raw lat lon points
   * @return the data bounds
   */
  public DataBounds getLatLonArrDataBounds(double[] rawllpts) {
    DataBounds retVal = null;
    // ## NOTE # Array is in radians
    // OpenMap is lat,lon,lat,lon.*/
    for (int i = 0; i < rawllpts.length; i += 2) {
      if (retVal == null) {
        retVal = new DataBounds(rawllpts[i + 1], rawllpts[i], rawllpts[i + 1], rawllpts[i]);
      } else {
        retVal.add(rawllpts[i + 1], rawllpts[i]);
      }
    }

    if (retVal != null) {
      retVal =
          new DataBounds(
              ProjMath.radToDeg(retVal.getMin().getX()),
              ProjMath.radToDeg(retVal.getMin().getY()),
              ProjMath.radToDeg(retVal.getMax().getX()),
              ProjMath.radToDeg(retVal.getMax().getY()));
    }
    return retVal;
  }

  /** A pretty name for the boundary, suitable for a GUI. */
  @Override
  public String getName() {
    return "SpatialIndexImpl";
  }

  /**
   * Skips the BinaryFile for the shp data to the offset and reads the record data there, creating
   * an OMGraphic from that data.
   *
   * @param byteOffset Usually gotten from an Entry object.
   * @param drawingAttributes The drawin attributes
   * @return The graphic
   * @throws IOException On error
   * @throws FormatException On error
   */
  @Override
  public OMGraphic getOMGraphicAtOffset(int byteOffset, DrawingAttributes drawingAttributes)
      throws IOException, FormatException {
    return graphicList.getTargets().get(byteOffset);
  }

  /**
   * Locates OMGraphics in the shape file that intersect with the given rectangle. The spatial index
   * is searched for intersections and the appropriate OMGraphics are created from the shape file.
   *
   * @param area The data bounds
   * @param list OMGraphicList to add OMGraphics to and return, if null one will be created.
   * @param drawingAttributes DrawingAttributes to set on the OMGraphics.
   * @param mapProj the Map Projection for the OMGraphics so they can be generated right after
   *     creation.
   * @param dataProj for preprojected data, the data projection to use to translate the
   *     coordinates to decimal degree lat/lon. Can be null to leave the coordinates untouched.
   * @return an OMGraphicList containing OMGraphics that intersect the given rectangle
   * @exception IOException if something goes wrong reading the files
   */
  @Override
  public OMGraphicList getOMGraphics(
      DataBounds area,
      OMGraphicList list,
      DrawingAttributes drawingAttributes,
      Projection mapProj,
      Projection dataProj)
      throws IOException, FormatException {
    OMGraphicList retVal = list;
    if (retVal == null) {
      retVal = getEmptyList();
    }

    if (graphicList == null) {
      return retVal;
    }

    OMGraphicList matches = locateRecords(area);
    if (matches != null) {
      matches.project(mapProj);
    }
    retVal.add(matches);

    return retVal;
  }

  /**
   * Locates OMGraphics in the shape file that intersect with the given rectangle. The spatial index
   * is searched for intersections and the appropriate OMGraphics are created from the shape file.
   *
   * @param xmin the smaller of the x coordinates
   * @param ymin the smaller of the y coordinates
   * @param xmax the larger of the x coordinates
   * @param ymax the larger of the y coordinates
   * @param list OMGraphicList to add OMGraphics to and return, if null one will be created.
   * @param drawingAttributes DrawingAttributes to set on the OMGraphics.
   * @param mapProj the Map Projection for the OMGraphics so they can be generated right after
   *     creation.
   * @param dataProj for preprojected data, the data projection to use to translate the
   *     coordinates to decimal degree lat/lon. Can be null to leave the coordinates untouched.
   * @return an OMGraphicList containing OMGraphics that intersect the given rectangle
   * @exception IOException if something goes wrong reading the files
   */
  @Override
  public OMGraphicList getOMGraphics(
      double xmin,
      double ymin,
      double xmax,
      double ymax,
      OMGraphicList list,
      DrawingAttributes drawingAttributes,
      Projection mapProj,
      Projection dataProj)
      throws IOException, FormatException {
    return getOMGraphics(
        new DataBounds(xmin, ymin, xmax, ymax), list, drawingAttributes, mapProj, dataProj);
  }

  protected int getSize() {
    int retVal = -1;
    if (graphicList != null) {
      retVal = graphicList.size();
    }
    return retVal;
  }

  @Override
  public DisplaySpatialIndex getSubsetSet(double xmin, double ymin, double xmax, double ymax)
      throws IOException, FormatException {
    return getSubsetSet(new DataBounds(xmin, ymin, xmax, ymax));
  }

  /**
   * Indexes the list of graphics.
   *
   * @param bounds if not null, add min/max values to them.
   * @return The size of the list
   * @throws IOException On error
   * @throws FormatException On error
   */
  protected synchronized int indexList(OMGraphicList elist) throws IOException, FormatException {
    int retVal = 0;
    OMGraphic geom = null;
    List<?> targets = elist.getTargets();
    DataBounds omgBounds = null;

    for (int idx = 0; idx < targets.size(); idx++, retVal++) {
      geom = (OMGraphic) targets.get(idx);
      omgBounds = getDataBounds(geom);
      addEntry(new BoundsEntry(omgBounds, geom));
    }
    return retVal;
  }

  /**
   * Locates records in the shape file that intersect with the given rectangle. The spatial index is
   * searched for intersections and the appropriate records are read from the shape file.
   *
   * @param area The rectangle to check
   * @return an array of records that intersect the given rectangle
   * @exception IOException if something goes wrong reading the files
   */
  @Override
  public OMGraphicList locateRecords(DataBounds area) throws IOException, FormatException {
    OMGraphicList retVal = null;
    if (retVal == null) {
      retVal = getEmptyList();
    }

    // long starttime = System.currentTimeMillis();
    List<BoundsEntry> matches = getBoundsEntrySubSet(area);

    OMGraphic omg = null;
    BoundsEntry entry = null;
    for (Iterator<BoundsEntry> it = matches.iterator(); it.hasNext(); ) {
      entry = it.next();
      if (entry != null) {
        omg = entry.getGraphic();
        if (omg != null) {
          retVal.add(omg);
        }
      }
    }

    return retVal;
  }

  /**
   * Locates records in the shape file that intersect with the given rectangle. The spatial index is
   * searched for intersections and the appropriate records are read from the shape file.
   *
   * @param xmin the smaller of the x coordinates
   * @param ymin the smaller of the y coordinates
   * @param xmax the larger of the x coordinates
   * @param ymax the larger of the y coordinates
   * @return an array of records that intersect the given rectangle
   * @exception IOException if something goes wrong reading the files
   */
  @Override
  public OMGraphicList locateRecords(double xmin, double ymin, double xmax, double ymax)
      throws IOException, FormatException {
    return locateRecords(new DataBounds(xmin, ymin, xmax, ymax));
  }

  /** Reset the bounds so they will be recalculated the next time a file is read. */
  @Override
  public void resetBounds() {
    globalDataBounds = null;
  }

  protected void setList(OMGraphicList list) {
    if (list != null) {

      graphicList = list;
      cachedList = clone(list);
      cachedList.clear();
      try {
        indexList(graphicList);
      } catch (Exception e) {
        System.err.println("Should never happen.");
        e.printStackTrace();
      }
    }
  }
}
