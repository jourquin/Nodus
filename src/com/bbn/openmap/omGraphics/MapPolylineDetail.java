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

package com.bbn.openmap.omGraphics;

import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.proj.GreatCircle;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.MoreMath;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Caches zoom-dependent display detail for ordinary shapefile lines in Mercator.
 *
 * <p>Only projected screen coordinates are copied to the original graphic. Geographic coordinates,
 * bounds, attributes, styles and object identity are never replaced, so saving, network operations,
 * hit testing and service overlays continue to use the original objects. This class is in the
 * OpenMap graphics package to access its projected-coordinate arrays without reflection or changing
 * the bundled library. It must be used by one layer preparation worker at a time.
 *
 * <p>A Douglas-Peucker hierarchy is built lazily in normalized Mercator coordinates. Its vertex
 * errors can be reused across centers, scales and window sizes. Each line retains one hierarchy and
 * at most one simplified coordinate array (the last requested zoom level). Geometry edits must
 * discard the entire cache, just like the layer's geographic index.
 */
public final class MapPolylineDetail {

  /**
   * Maximum deviation in screen pixels, apart from OpenMap's floating-point projection rounding.
   */
  private static final double PIXEL_TOLERANCE = 0.75;

  /** Entries use identity because distinct source records can have identical geometry. */
  private final Map<EsriPolyline, Detail> details = new IdentityHashMap<>();

  /** Measurements for one preparation; cache-building time is part of projection time. */
  public static final class Measurement {
    private long sourceVertices;
    private long displayedVertices;
    private int simplifiedLines;
    private int builtHierarchies;
    private int builtLevels;
    private int candidateLines;
    private int curvatureFallbacks;
    private long cacheNanos;
    private String mode;

    /** Returns the number of original polyline vertices in the selected graphics. */
    public long getSourceVertices() {
      return sourceVertices;
    }

    /** Returns the number of polyline vertices submitted to projection. */
    public long getDisplayedVertices() {
      return displayedVertices;
    }

    /** Returns the number of lines projected with fewer vertices. */
    public int getSimplifiedLines() {
      return simplifiedLines;
    }

    /** Returns the number of newly constructed simplification hierarchies. */
    public int getBuiltHierarchies() {
      return builtHierarchies;
    }

    /** Returns the number of newly selected zoom-level coordinate arrays. */
    public int getBuiltLevels() {
      return builtLevels;
    }

    /** Returns the number of lines considered for simplification in a supported projection. */
    public int getCandidateLines() {
      return candidateLines;
    }

    /** Returns the number of great-circle lines kept in full detail to preserve curvature. */
    public int getCurvatureFallbacks() {
      return curvatureFallbacks;
    }

    /** Returns whether detail is enabled, disabled or bypassed for the actual projection. */
    public String getMode() {
      return mode;
    }

    /** Returns cache construction time, already included in the layer's projection measurement. */
    public long getCacheNanos() {
      return cacheNanos;
    }
  }

  /** Geometry metadata and the last requested level; null errors mean unsupported coordinates. */
  private static final class Detail {
    final double[] source;
    final double[] errors;
    final int lineType;
    final double curvatureError;
    double tolerance = Double.NaN;
    OMPoly display;

    Detail(EsriPolyline line, Mercator projection) {
      source = line.rawllpts;
      lineType = line.getLineType();
      errors = buildHierarchy(source, projection);
      curvatureError =
          errors != null && lineType == OMGraphic.LINETYPE_GREATCIRCLE ? curvatureBound(source) : 0;
    }

    /** Selects a level directly from the source, avoiding accumulated simplification error. */
    void select(double requestedTolerance) {
      tolerance = requestedTolerance;
      // Reserve half of the pixel budget for replacing each original great-circle arc by its
      // chord. The other half covers vertex removal. Close views or long arcs fall back intact.
      double vertexTolerance =
          lineType == OMGraphic.LINETYPE_GREATCIRCLE ? tolerance / 2 : tolerance;
      display = null;
      if (curvatureError > vertexTolerance) {
        return;
      }
      double squared = vertexTolerance * vertexTolerance;
      int count = 0;
      for (double error : errors) {
        if (error > squared) {
          count++;
        }
      }
      if (count == errors.length) {
        return;
      }
      double[] coordinates = new double[count * 2];
      int target = 0;
      for (int i = 0; i < errors.length; i++) {
        if (errors[i] > squared) {
          coordinates[target++] = source[2 * i];
          coordinates[target++] = source[2 * i + 1];
        }
      }
      display = new OMPoly(coordinates, OMGraphic.RADIANS, OMGraphic.LINETYPE_STRAIGHT);
      display.setIsPolygon(false);
    }
  }

  /**
   * Projects a visible list, optionally substituting reduced screen geometry for eligible lines.
   *
   * @param graphics Visible source objects, including multipart lists.
   * @param projection Current projection.
   * @param enabled False selects the ordinary full-detail path for comparison.
   * @param audit Whether cache construction should read the timer.
   * @return Counts for this preparation; vertices count polylines, not points or polygons.
   */
  public Measurement generate(
      OMGraphicList graphics, Projection projection, boolean enabled, boolean audit) {
    Measurement measurement = new Measurement();
    measurement.mode = enabled ? "bypassed: unsupported projection" : "disabled";
    double tolerance = 0;
    // Restrict this first version to the exact projection whose metric the hierarchy uses.
    if (enabled && projection.getClass() == Mercator.class) {
      measurement.mode = "enabled";
      Mercator mercator = (Mercator) projection;
      double requested = PIXEL_TOLERANCE * mercator.getScale() / mercator.getPlanetPixelRadius();
      if (Double.isFinite(requested) && requested > 0) {
        // Powers of two avoid rebuilding levels for every small wheel/slider scale adjustment.
        // Round down so a cached level never exceeds the requested pixel tolerance.
        tolerance = Math.scalb(1.0, Math.getExponent(requested));
      }
    }
    generateList(graphics, projection, tolerance, audit, measurement);
    return measurement;
  }

  /** Keeps source order and multipart structure, substituting only individual leaf projections. */
  private void generateList(
      OMGraphicList graphics,
      Projection projection,
      double tolerance,
      boolean audit,
      Measurement measurement) {
    for (OMGraphic graphic : graphics) {
      if (graphic instanceof OMGraphicList) {
        generateList((OMGraphicList) graphic, projection, tolerance, audit, measurement);
      } else {
        OMPoly display = null;
        if (graphic instanceof EsriPolyline) {
          EsriPolyline line = (EsriPolyline) graphic;
          int vertices = line.rawllpts == null ? 0 : line.rawllpts.length / 2;
          measurement.sourceVertices += vertices;
          if (tolerance > 0 && eligible(line)) {
            measurement.candidateLines++;
            display = getDisplay(line, (Mercator) projection, tolerance, audit, measurement);
          }
          if (display != null) {
            measurement.simplifiedLines++;
            measurement.displayedVertices += display.rawllpts.length / 2;
          } else {
            measurement.displayedVertices += vertices;
          }
        }
        if (display == null || !display.generate(projection)) {
          graphic.generate(projection);
        } else {
          copyProjection((OMPoly) graphic, display, projection);
        }
      }
    }
  }

  /** Keeps custom graphics, selected lines and arrowheads on the full-detail path. */
  private static boolean eligible(EsriPolyline line) {
    return line.getClass() == EsriPolyline.class
        && line.getRenderType() == OMGraphic.RENDERTYPE_LATLON
        && (line.getLineType() == OMGraphic.LINETYPE_STRAIGHT
            || line.getLineType() == OMGraphic.LINETYPE_RHUMB
            || line.getLineType() == OMGraphic.LINETYPE_GREATCIRCLE)
        && line.units == OMGraphic.RADIANS
        && line.rawllpts != null
        && line.rawllpts.length >= 8
        && (line.rawllpts.length & 1) == 0
        && !line.isSelected()
        && line.getArrowHead() == null;
  }

  /** Lazily constructs geometry metadata and reuses the most recent zoom-level array. */
  private OMPoly getDisplay(
      EsriPolyline line,
      Mercator projection,
      double tolerance,
      boolean audit,
      Measurement measurement) {
    Detail detail = details.get(line);
    if (detail == null || detail.source != line.rawllpts || detail.lineType != line.getLineType()) {
      final long started = audit ? System.nanoTime() : 0;
      detail = new Detail(line, projection);
      details.put(line, detail);
      if (detail.errors != null) {
        measurement.builtHierarchies++;
      }
      if (audit) {
        measurement.cacheNanos += System.nanoTime() - started;
      }
    }
    if (detail.errors == null) {
      return null;
    }
    if (detail.lineType == OMGraphic.LINETYPE_GREATCIRCLE
        && detail.curvatureError > tolerance / 2) {
      measurement.curvatureFallbacks++;
    }
    if (detail.tolerance != tolerance) {
      final long started = audit ? System.nanoTime() : 0;
      detail.select(tolerance);
      measurement.builtLevels++;
      if (audit) {
        measurement.cacheNanos += System.nanoTime() - started;
      }
    }
    return detail.display;
  }

  /**
   * Bounds Mercator deviation between each original great-circle arc and its endpoint chord.
   *
   * <p>For unit-speed motion along a unit-sphere great circle, the norm of the second derivative of
   * normalized Mercator coordinates is abs(sin(latitude)) / cos(latitude)^2. An arc of length d
   * therefore deviates from its chord by at most maxDerivative * d^2 / 8. Every point is within d/2
   * of one endpoint, giving a conservative latitude bound. The same bound covers OpenMap's
   * piecewise linear approximation of that arc, regardless of its subdivision count.
   *
   * <p>Near-polar arcs fall back to ordinary projection: Mercator clamps latitude there and the
   * smooth-derivative argument no longer applies. Only the maximum over the source segments is
   * retained, so this calculation is paid once per line, not on each pan or zoom.
   */
  private static double curvatureBound(double[] source) {
    double maximum = 0;
    for (int i = 2; i < source.length; i += 2) {
      double distance =
          GreatCircle.sphericalDistance(source[i - 2], source[i - 1], source[i], source[i + 1]);
      double latitude = Math.max(Math.abs(source[i - 2]), Math.abs(source[i])) + distance / 2;
      if (!Double.isFinite(distance) || latitude >= Math.toRadians(85)) {
        return Double.POSITIVE_INFINITY;
      }
      double cosine = Math.cos(latitude);
      double error = Math.sin(latitude) / (cosine * cosine) * distance * distance / 8;
      maximum = Math.max(maximum, error);
    }
    return maximum;
  }

  /**
   * Publishes projected arrays using the same shape/label lifecycle as OMPoly.generate().
   *
   * <p>The geographic array is never swapped, even temporarily: other readers always see full
   * geometry. Copying both projected arrays also keeps distance, shape and editing APIs consistent.
   * Styles and labels are taken from the source graphic, not the private display helper.
   */
  private static void copyProjection(OMPoly source, OMPoly display, Projection projection) {
    source.setNeedToRegenerate(true);
    source.isGeometryClosed();
    source.xpoints = display.xpoints;
    source.ypoints = display.ypoints;
    source.setNeedToRegenerate(false);
    if (source.doShapes) {
      source.setShape(source.createShape());
      source.setLabelLocation(source.getShape(), projection);
    } else {
      source.initLabelingDuringGenerate();
      if (source.checkPoints(source.xpoints, source.ypoints)) {
        source.setLabelLocation(source.xpoints[0], source.ypoints[0], projection);
      }
    }
  }

  /**
   * Computes squared vertex errors in unscaled Mercator space, with endpoints retained forever.
   *
   * <p>Child errors are capped by the parent's error: thresholding then selects a valid cut of the
   * Douglas-Peucker tree. An iterative stack avoids stack overflow on long lines. Temporary metric
   * coordinates and stacks are discarded; only one double per source vertex is retained. Lines
   * spanning half the world, crossing the date line, or containing invalid coordinates fall back to
   * ordinary projection to preserve OpenMap's wrapping decisions.
   */
  private static double[] buildHierarchy(double[] source, Mercator projection) {
    int count = source.length / 2;
    double[] ys = new double[count];
    double west = Double.POSITIVE_INFINITY;
    double east = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < count; i++) {
      double lat = source[2 * i];
      double lon = source[2 * i + 1];
      if (!Double.isFinite(lat)
          || !Double.isFinite(lon)
          || Math.abs(lat) > Math.PI / 2
          || Math.abs(lon) > Math.PI) {
        return null;
      }
      west = Math.min(west, lon);
      east = Math.max(east, lon);
      ys[i] = MoreMath.asinh(Math.tan(projection.normalizeLatitude(lat)));
    }
    if (east - west >= Math.PI) {
      return null;
    }
    double[] errors = new double[count];
    errors[0] = Double.POSITIVE_INFINITY;
    errors[count - 1] = Double.POSITIVE_INFINITY;
    int[] starts = new int[count];
    int[] ends = new int[count];
    double[] limits = new double[count];
    ends[0] = count - 1;
    limits[0] = Double.POSITIVE_INFINITY;
    int pending = 1;
    while (pending > 0) {
      int slot = --pending;
      int start = starts[slot];
      int end = ends[slot];
      double limit = limits[slot];
      double greatest = 0;
      int pivot = -1;
      for (int i = start + 1; i < end; i++) {
        double distance =
            squaredSegmentDistance(
                source[2 * start + 1],
                ys[start],
                source[2 * end + 1],
                ys[end],
                source[2 * i + 1],
                ys[i]);
        if (distance > greatest) {
          greatest = distance;
          pivot = i;
        }
      }
      if (pivot < 0) {
        continue;
      }
      double error = Math.min(limit, greatest);
      errors[pivot] = error;
      if (pivot > start + 1) {
        starts[pending] = start;
        ends[pending] = pivot;
        limits[pending++] = error;
      }
      if (end > pivot + 1) {
        starts[pending] = pivot;
        ends[pending] = end;
        limits[pending++] = error;
      }
    }
    return errors;
  }

  /** Measures the residual directly to avoid cancellation for almost collinear source vertices. */
  private static double squaredSegmentDistance(
      double startX, double startY, double endX, double endY, double x, double y) {
    double dx = endX - startX;
    double dy = endY - startY;
    double offsetX = x - startX;
    double offsetY = y - startY;
    double lengthSquared = dx * dx + dy * dy;
    if (lengthSquared > 0) {
      double position = Math.max(0, Math.min(1, (offsetX * dx + offsetY * dy) / lengthSquared));
      offsetX -= position * dx;
      offsetY -= position * dy;
    }
    return offsetX * offsetX + offsetY * offsetY;
  }
}
