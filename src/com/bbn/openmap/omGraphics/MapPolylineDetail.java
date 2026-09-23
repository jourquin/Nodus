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
import com.bbn.openmap.proj.EqualEarth;
import com.bbn.openmap.proj.GeoProj;
import com.bbn.openmap.proj.GreatCircle;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.MoreMath;
import java.awt.geom.Point2D;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Caches zoom-dependent display detail for ordinary shapefile lines in Mercator and Equal Earth.
 *
 * <p>Only projected screen coordinates are copied to the original graphic. Geographic coordinates,
 * bounds, attributes, styles and object identity are never replaced, so saving, network operations,
 * hit testing and service overlays continue to use the original objects. This class is in the
 * OpenMap graphics package to access its projected-coordinate arrays without reflection or changing
 * the bundled library. It must be used by one layer preparation worker at a time.
 *
 * <p>A Douglas-Peucker hierarchy is built lazily using projection-specific coordinates. Its vertex
 * errors can be reused across centers, scales and window sizes. Each line retains one hierarchy and
 * at most one simplified coordinate array (the last requested zoom level). Geometry edits must
 * discard the entire cache, just like the layer's geographic index. Switching projection types
 * replaces each line's detail lazily, without retaining an extra hierarchy for the previous
 * projection.
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
    private int seamFallbacks;
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

    /** Returns lines kept in full detail because they cross Equal Earth's current wrap boundary. */
    public int getSeamFallbacks() {
      return seamFallbacks;
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
    final Class<?> projectionType;
    final double longitudeCenter;
    final double longitudeRadius;
    final double curvatureError;
    double tolerance = Double.NaN;
    OMPoly display;

    Detail(EsriPolyline line, GeoProj projection) {
      source = line.rawllpts;
      lineType = line.getLineType();
      projectionType = projection.getClass();
      Metric metric = createMetric(source, projection);
      errors = metric == null ? null : buildHierarchy(metric);
      longitudeCenter = metric == null ? 0 : metric.longitudeCenter;
      longitudeRadius = metric == null ? 0 : metric.longitudeRadius;
      curvatureError =
          errors != null && lineType == OMGraphic.LINETYPE_GREATCIRCLE
              ? curvatureBound(source, projectionType == EqualEarth.class)
              : 0;
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
    // Only exact projection classes whose metric and curvature bounds are implemented here.
    if (enabled
        && (projection.getClass() == Mercator.class || projection.getClass() == EqualEarth.class)) {
      measurement.mode = "enabled";
      GeoProj geographic = (GeoProj) projection;
      double requested =
          PIXEL_TOLERANCE * geographic.getScale() / geographic.getPlanetPixelRadius();
      if (Double.isFinite(requested) && requested > 0) {
        // Powers of two avoid rebuilding levels for every small wheel/slider scale adjustment.
        // Round down so a cached level never exceeds the requested pixel tolerance.
        tolerance = Math.scalb(1.0, Math.getExponent(requested));
      }
    }
    double centerLongitude =
        projection.getClass() == EqualEarth.class
            ? Math.toRadians(projection.getCenter().getX())
            : 0;
    generateList(graphics, projection, centerLongitude, tolerance, audit, measurement);
    return measurement;
  }

  /** Keeps source order and multipart structure, substituting only individual leaf projections. */
  private void generateList(
      OMGraphicList graphics,
      Projection projection,
      double centerLongitude,
      double tolerance,
      boolean audit,
      Measurement measurement) {
    for (OMGraphic graphic : graphics) {
      if (graphic instanceof OMGraphicList) {
        generateList(
            (OMGraphicList) graphic, projection, centerLongitude, tolerance, audit, measurement);
      } else {
        OMPoly display = null;
        if (graphic instanceof EsriPolyline) {
          EsriPolyline line = (EsriPolyline) graphic;
          int vertices = line.rawllpts == null ? 0 : line.rawllpts.length / 2;
          measurement.sourceVertices += vertices;
          if (tolerance > 0 && eligible(line, projection)) {
            measurement.candidateLines++;
            display =
                getDisplay(
                    line, (GeoProj) projection, centerLongitude, tolerance, audit, measurement);
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
  private static boolean eligible(EsriPolyline line, Projection projection) {
    return line.getClass() == EsriPolyline.class
        && line.getRenderType() == OMGraphic.RENDERTYPE_LATLON
        && (line.getLineType() == OMGraphic.LINETYPE_STRAIGHT
            || line.getLineType() == OMGraphic.LINETYPE_RHUMB
            || line.getLineType() == OMGraphic.LINETYPE_GREATCIRCLE)
        // OpenMap's non-Mercator rhumb interpolation quantizes coordinates through integer
        // Mercator pixels. Keep that rendering path intact, including its rounding behaviour.
        && (projection.getClass() != EqualEarth.class
            || line.getLineType() != OMGraphic.LINETYPE_RHUMB)
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
      GeoProj projection,
      double centerLongitude,
      double tolerance,
      boolean audit,
      Measurement measurement) {
    Detail detail = details.get(line);
    if (detail == null
        || detail.source != line.rawllpts
        || detail.lineType != line.getLineType()
        || detail.projectionType != projection.getClass()) {
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
    if (detail.projectionType == EqualEarth.class) {
      double offset = Math.abs(ProjMath.wrapLongitude(detail.longitudeCenter - centerLongitude));
      // The cached 3D metric assumes all points use the same longitude-wrap branch. Keep the
      // hierarchy but bypass it when a pan moves the seam into this line's longitude interval.
      if (offset + detail.longitudeRadius >= Math.PI - 1e-7) {
        measurement.seamFallbacks++;
        return null;
      }
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
   * Bounds projection deviation between each original great-circle arc and its endpoint chord.
   *
   * <p>For unit-speed motion along a unit-sphere great circle, the norm of the second derivative of
   * normalized Mercator coordinates is abs(sin(latitude)) / cos(latitude)^2. An arc of length d
   * therefore deviates from its chord by at most maxDerivative * d^2 / 8. Every point is within d/2
   * of one endpoint, giving a conservative latitude bound. The same bound covers OpenMap's
   * piecewise linear approximation of that arc, regardless of its subdivision count.
   *
   * <p>Near-polar arcs fall back to ordinary projection: Mercator clamps latitude there and the
   * smooth-derivative argument no longer applies. Only the maximum over the source segments is
   * retained, so this calculation is paid once per line, not on each pan or zoom. Equal Earth uses
   * its own derivative bound, valid at every central meridian away from the longitude seam.
   */
  private static double curvatureBound(double[] source, boolean equalEarth) {
    double maximum = 0;
    for (int i = 2; i < source.length; i += 2) {
      double distance =
          GreatCircle.sphericalDistance(source[i - 2], source[i - 1], source[i], source[i + 1]);
      double latitude = Math.max(Math.abs(source[i - 2]), Math.abs(source[i])) + distance / 2;
      if (!Double.isFinite(distance) || latitude >= Math.toRadians(85)) {
        return Double.POSITIVE_INFINITY;
      }
      double cosine = Math.cos(latitude);
      double error =
          equalEarth
              ? EqualEarth.greatCircleChordErrorBound(distance, latitude)
              : Math.sin(latitude) / (cosine * cosine) * distance * distance / 8;
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

  /** Temporary metric coordinates; only the resulting hierarchy is retained by the cache. */
  private static final class Metric {
    final double[] xs;
    final double[] ys;
    final double[] zs;
    final double longitudeCenter;
    final double longitudeRadius;

    Metric(int count, boolean equalEarth, double west, double east) {
      xs = new double[count];
      ys = new double[count];
      zs = equalEarth ? new double[count] : null;
      longitudeCenter = (west + east) / 2;
      longitudeRadius = (east - west) / 2;
    }
  }

  /**
   * Builds Mercator's 2D metric or Equal Earth's center-independent 3D metric.
   *
   * <p>Equal Earth has x = (longitude-center)*A(latitude), y = B(latitude). For a line centered at
   * longitude L, store ((longitude-L)*A, B, pi*A). In any view that does not cut the line at its
   * longitude seam, projected x is metric.xs + c*metric.zs, with abs(c) <= 1, and y is metric.ys.
   * This linear map stretches any residual by at most sqrt(2). Multiplying squared hierarchy errors
   * by two thus bounds screen error for every central meridian. No hierarchy rebuild is needed when
   * panning horizontally. A cheap per-view interval check handles the seam separately.
   */
  private static Metric createMetric(double[] source, GeoProj projection) {
    int count = source.length / 2;
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
    }
    boolean equalEarth = projection.getClass() == EqualEarth.class;
    // OpenMap compares projected segments with a rounded half-world pixel width. A newly
    // shortened, very wide chord can trigger a wrapped copy even without crossing the view seam.
    // Keep quarter-world Equal Earth lines intact, well away from that rounding threshold.
    double maximumSpan = equalEarth ? Math.PI / 2 : Math.PI;
    if (east - west >= maximumSpan - 1e-7) {
      return null;
    }
    Metric metric = new Metric(count, equalEarth, west, east);
    Point2D point = equalEarth ? new Point2D.Double() : null;
    for (int i = 0; i < count; i++) {
      double lat = source[2 * i];
      double lon = source[2 * i + 1];
      if (equalEarth) {
        EqualEarth.forwardNormalized(lat, 1, point);
        double horizontalScale = point.getX();
        metric.xs[i] = (lon - metric.longitudeCenter) * horizontalScale;
        metric.ys[i] = point.getY();
        metric.zs[i] = Math.PI * horizontalScale;
      } else {
        metric.xs[i] = lon;
        metric.ys[i] = MoreMath.asinh(Math.tan(projection.normalizeLatitude(lat)));
      }
    }
    return metric;
  }

  /**
   * Computes squared vertex errors in the selected metric, retaining endpoints forever.
   *
   * <p>Child errors are capped by the parent's error: thresholding selects a valid cut of the
   * Douglas-Peucker tree. An iterative stack avoids stack overflow on long lines. Temporary metric
   * coordinates and stacks are discarded; only one double per source vertex is retained.
   */
  private static double[] buildHierarchy(Metric metric) {
    int count = metric.xs.length;
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
        double distance = squaredSegmentDistance(metric, start, end, i);
        if (distance > greatest) {
          greatest = distance;
          pivot = i;
        }
      }
      if (pivot < 0) {
        continue;
      }
      double error = Math.min(limit, greatest);
      errors[pivot] = metric.zs == null ? error : 2 * error;
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

  /** Measures 2D/3D residuals directly to avoid cancellation for almost collinear vertices. */
  private static double squaredSegmentDistance(Metric metric, int start, int end, int vertex) {
    double dx = metric.xs[end] - metric.xs[start];
    double dy = metric.ys[end] - metric.ys[start];
    double dz = metric.zs == null ? 0 : metric.zs[end] - metric.zs[start];
    double offsetX = metric.xs[vertex] - metric.xs[start];
    double offsetY = metric.ys[vertex] - metric.ys[start];
    double offsetZ = metric.zs == null ? 0 : metric.zs[vertex] - metric.zs[start];
    double lengthSquared = dx * dx + dy * dy + dz * dz;
    if (lengthSquared > 0) {
      double position =
          Math.max(0, Math.min(1, (offsetX * dx + offsetY * dy + offsetZ * dz) / lengthSquared));
      offsetX -= position * dx;
      offsetY -= position * dy;
      offsetZ -= position * dz;
    }
    return offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
  }
}
