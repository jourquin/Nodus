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

import com.bbn.openmap.io.FormatException;
import com.bbn.openmap.omGraphics.DrawingAttributes;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.DataBounds;
import com.bbn.openmap.util.DataBoundsProvider;
import java.io.IOException;
import java.util.Iterator;

/**
 * A specific spatial index intended to fasten the rendering of large shapefiles.
 *
 * @author Bart Jourquin, inspired from ???
 */
public interface DisplaySpatialIndex extends DataBoundsProvider {

  /**
   * Provides an iterator over the SpatialIndexInMem spatialindex.
   *
   * @return An iterator over the spatial index
   * @throws IOException On error
   * @throws FormatException On error
   */
  public Iterator<?> entryIterator() throws IOException, FormatException;

  public OMGraphicList getEmptyList();

  /**
   * Skips the BinaryFile for the shp data to the offset and reads the record data there, creating
   * an OMGraphic from that data.
   *
   * @param byteOffset Usually gotten from an Entry object.
   * @param drawingAttributes Drawing attributes
   * @return an OMGraphic An OMGraphic
   * @throws IOException On error
   * @throws FormatException On error
   */
  public OMGraphic getOMGraphicAtOffset(int byteOffset, DrawingAttributes drawingAttributes)
      throws IOException, FormatException;

  /**
   * Locates OMGraphics in the shape file that intersect with the given rectangle. The spatial index
   * is searched for intersections and the appropriate OMGraphics are created from the shape file.
   *
   * @param area The rectangle to check
   * @param list OMGraphicList to add OMGraphics to and return, if null one will be created.
   * @param drawingAttributes DrawingAttributes to set on the OMGraphics.
   * @param mapProj the Map Projection for the OMGraphics so they can be generated right after
   *     creation.
   * @param dataProj for preprojected data, the data projection to use to translate the coordinates
   *     to decimal degree lat/lon. Can be null to leave the coordinates untouched.
   * @return an OMGraphicList containing OMGraphics that intersect the given rectangle
   * @exception IOException if something goes wrong reading the files
   */
  public OMGraphicList getOMGraphics(
      DataBounds area,
      OMGraphicList list,
      DrawingAttributes drawingAttributes,
      Projection mapProj,
      Projection dataProj)
      throws IOException, FormatException;

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
   * @param dataProj for preprojected data, the data projection to use to translate the coordinates
   *     to decimal degree lat/lon. Can be null to leave the coordinates untouched.
   * @return an OMGraphicList containing OMGraphics that intersect the given rectangle
   * @exception IOException if something goes wrong reading the files
   */
  public OMGraphicList getOMGraphics(
      double xmin,
      double ymin,
      double xmax,
      double ymax,
      OMGraphicList list,
      DrawingAttributes drawingAttributes,
      Projection mapProj,
      Projection dataProj)
      throws IOException, FormatException;

  /**
   * Creates a subset.
   *
   * @exception IOException if something goes wrong reading the files
   */
  public DisplaySpatialIndex getSubsetSet(DataBounds area) throws IOException, FormatException;

  public DisplaySpatialIndex getSubsetSet(double xmin, double ymin, double xmax, double ymax)
      throws IOException, FormatException;

  /**
   * Locates records in the shape file that intersect with the given rectangle. The spatial index is
   * searched for intersections and the appropriate records are read from the shape file.
   *
   * @param area The rectangle to check
   * @return an list of records that intersect the given rectangle
   * @exception IOException if something goes wrong reading the files
   */
  public OMGraphicList locateRecords(DataBounds area) throws IOException, FormatException;

  /**
   * Locates records in the shape file that intersect with the given rectangle. The spatial index is
   * searched for intersections and the appropriate records are read from the shape file.
   *
   * @param xmin the smaller of the x coordinates
   * @param ymin the smaller of the y coordinates
   * @param xmax the larger of the x coordinates
   * @param ymax the larger of the y coordinates
   * @return an list of records that intersect the given rectangle
   * @exception IOException if something goes wrong reading the files
   */
  public OMGraphicList locateRecords(double xmin, double ymin, double xmax, double ymax)
      throws IOException, FormatException;

  // ------------------------------------------------------------------------------
  /** Reset the bounds so they will be recalculated the next time a file is read. */
  public void resetBounds();
}
