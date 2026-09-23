package com.bbn.openmap.omGraphics;

import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolygon;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.MapPolylineDetail.Measurement;
import com.bbn.openmap.proj.EqualEarth;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Standalone display-detail checks: actual projection, source preservation and screen accuracy. */
public final class MapPolylineDetailTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static Mercator projection(double lat, double lon, float scale) {
    return new Mercator(new LatLonPoint.Double(lat, lon), scale, 1000, 800);
  }

  private static EsriPolyline wave(double latitude, double longitude, int count) {
    double[] coordinates = new double[count * 2];
    for (int i = 0; i < count; i++) {
      double fraction = i / (double) (count - 1);
      coordinates[2 * i] = latitude + 0.002 * Math.sin(fraction * 80) + 0.01 * fraction;
      coordinates[2 * i + 1] = longitude + fraction * 0.5;
    }
    return new EsriPolyline(coordinates, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
  }

  private static OMGraphicList list(OMGraphic... graphics) {
    return new OMGraphicList(Arrays.asList(graphics));
  }

  private static int[] pixels(OMGraphic graphic) {
    BufferedImage image = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    graphic.render(graphics);
    graphics.dispose();
    return image.getRGB(0, 0, 1000, 800, null, 0, 1000);
  }

  /** Checks both directions against all segments, including OpenMap's wrapped copies. */
  private static void boundedError(float[][] xs, float[][] ys, OMPoly target) {
    for (int part = 0; part < xs.length; part++) {
      for (int i = 0; i < xs[part].length; i++) {
        // Sample segment interiors too: preserving vertices alone would miss cut corners.
        int samples = i == 0 ? 1 : 4;
        for (int sample = 0; sample < samples; sample++) {
          double t = sample / (double) samples;
          double x = xs[part][i] * (1 - t) + xs[part][Math.max(0, i - 1)] * t;
          double y = ys[part][i] * (1 - t) + ys[part][Math.max(0, i - 1)] * t;
          double best = Double.POSITIVE_INFINITY;
          for (int j = 0; j < target.xpoints.length; j++) {
            for (int k = 1; k < target.xpoints[j].length; k++) {
              best =
                  Math.min(
                      best,
                      Line2D.ptSegDistSq(
                          target.xpoints[j][k - 1],
                          target.ypoints[j][k - 1],
                          target.xpoints[j][k],
                          target.ypoints[j][k],
                          x,
                          y));
            }
          }
          check(best <= 0.76 * 0.76, "Display error exceeds tolerance: " + Math.sqrt(best));
        }
      }
    }
  }

  private static void accuracyAndReuse() {
    Random random = new Random(731);
    for (double latitude : new double[] {0, 50, 84}) {
      EsriPolyline line = wave(latitude, 2, 300);
      double[] original = line.getLatLonArray();
      double[] saved = original.clone();
      double[] bounds = line.getExtents().clone();
      Object attribute = new Object();
      line.putAttribute("source", attribute);
      MapPolylineDetail detail = new MapPolylineDetail();
      EsriPolyline full =
          new EsriPolyline(saved.clone(), OMGraphic.RADIANS, OMGraphic.LINETYPE_STRAIGHT);
      int previous = 0;
      for (float scale : new float[] {10000000, 2000000, 100000, 5000, 100}) {
        Mercator p = projection(latitude, 2.25, scale);
        full.generate(p);
        Measurement m = detail.generate(list(line), p, true, true);
        check(m.getDisplayedVertices() >= previous, "Zooming in removed detail");
        previous = (int) m.getDisplayedVertices();
        if (scale == 10000000) check(previous < 30, "Coarse zoom retained too much detail");
        if (scale == 100) check(previous > 250, "Fine zoom did not restore detail");
        check(line.rawllpts == original && Arrays.equals(saved, original), "Source array modified");
        check(Arrays.equals(bounds, line.getExtents()), "Geographic bounds changed");
        check(line.getAttribute("source") == attribute, "Attribute identity changed");
        // Tiny scales give very large off-screen floats: test the meaningful screen-error bound
        // where rounding of the projected coordinates remains below a hundredth of a pixel.
        if (scale >= 100000) {
          boundedError(full.xpoints, full.ypoints, line);
          boundedError(line.xpoints, line.ypoints, full);
        }
        check(full.xpoints[0][0] == line.xpoints[0][0], "First endpoint moved");
        check(full.ypoints[0][0] == line.ypoints[0][0], "First endpoint moved");
        int end = line.xpoints[0].length - 1;
        check(full.xpoints[0][299] == line.xpoints[0][end], "Last endpoint moved");
        check(full.ypoints[0][299] == line.ypoints[0][end], "Last endpoint moved");
        Measurement reused =
            detail.generate(list(line), projection(latitude + 0.01, 2.26, scale), true, true);
        check(
            reused.getBuiltHierarchies() == 0 && reused.getBuiltLevels() == 0,
            "Pan rebuilt detail");
        Mercator resized = new Mercator(new LatLonPoint.Double(latitude, 2.25), scale, 900, 700);
        reused = detail.generate(list(line), resized, true, true);
        check(
            reused.getBuiltHierarchies() == 0 && reused.getBuiltLevels() == 0,
            "Resize rebuilt detail");
      }
      // Irregular lines exercise the hierarchy's capped child errors, not just a smooth curve.
      for (int trial = 0; trial < 10; trial++) {
        double[] coordinates = saved.clone();
        for (int i = 2; i < coordinates.length - 2; i += 2) {
          coordinates[i] += random.nextGaussian() * 0.0001;
          coordinates[i + 1] += random.nextGaussian() * 0.0001;
        }
        line =
            new EsriPolyline(coordinates.clone(), OMGraphic.RADIANS, OMGraphic.LINETYPE_STRAIGHT);
        full = new EsriPolyline(coordinates, OMGraphic.RADIANS, OMGraphic.LINETYPE_STRAIGHT);
        Mercator p = projection(latitude, 2, 2000000);
        full.generate(p);
        detail.generate(list(line), p, true, false);
        boundedError(full.xpoints, full.ypoints, line);
        boundedError(line.xpoints, line.ypoints, full);
      }
    }
  }

  private static void renderingAndFallbacks() {
    Mercator p = projection(50, 2.25, 2000000);
    EsriPolyline line = wave(50, 2, 100);
    MapPolylineDetail detail = new MapPolylineDetail();
    OMGraphicList nested = list(list(line), new EsriPoint(50, 2));
    OMTextLabeler label = new OMTextLabeler("Road");
    line.putAttribute(OMGraphic.LABEL, label);
    line.setLinePaint(Color.RED);
    line.setStroke(new BasicStroke(3));
    detail.generate(nested, p, true, false);
    int labelX = label.getX();
    detail.generate(nested, projection(50, 2.35, 2000000), true, false);
    check(label.getX() != labelX, "Label did not follow pan");
    // OpenMap renders labels separately; exercise line visibility without an attached label.
    line.removeAttribute(OMGraphic.LABEL);
    detail.generate(nested, p, true, false);
    check(
        nested.findClosest(line.xpoints[0][0], line.ypoints[0][0], 3) == line,
        "Hit test did not return original line");
    check(Arrays.stream(pixels(line)).anyMatch(pixel -> pixel == Color.RED.getRGB()), "Style lost");
    line.setLinePaint(Color.BLUE);
    check(
        Arrays.stream(pixels(line)).anyMatch(pixel -> pixel == Color.BLUE.getRGB()),
        "Live style lost");
    line.setVisible(false);
    check(Arrays.stream(pixels(list(line))).allMatch(pixel -> pixel == 0), "Visibility lost");
    line.setVisible(true);
    for (boolean shapes : new boolean[] {false, true}) {
      line.setDoShapes(shapes);
      detail.generate(nested, p, true, false);
      check(line.getShape() != null, "Shape missing");
      Measurement off = detail.generate(nested, p, false, false);
      check(
          off.getDisplayedVertices() == 100 && off.getSimplifiedLines() == 0, "Off switch failed");
      int[] actual = pixels(line);
      line.generate(p);
      check(Arrays.equals(actual, pixels(line)), "Full-detail rendering changed");
      line.setSelected(true);
      Measurement selected = detail.generate(nested, p, true, false);
      check(selected.getSimplifiedLines() == 0, "Selected line simplified");
      line.setSelected(false);
      line.addArrowHead(true);
      check(detail.generate(nested, p, true, false).getSimplifiedLines() == 0, "Arrow simplified");
      line.addArrowHead(false);
      line.setLineType(OMGraphic.LINETYPE_GREATCIRCLE);
      check(
          detail.generate(nested, p, true, false).getSimplifiedLines() == 1,
          "Gentle curve excluded");
      line.setLineType(OMGraphic.LINETYPE_RHUMB);
      check(
          detail.generate(nested, p, true, false).getSimplifiedLines() == 1,
          "Mercator rhumb excluded");
      line.setLineType(OMGraphic.LINETYPE_STRAIGHT);
      Projection other = new EqualEarth(new LatLonPoint.Double(50, 2), 2000000, 1000, 800);
      check(
          detail.generate(nested, other, true, false).getSimplifiedLines() == 0,
          "Unsupported projection simplified");
    }
    EsriPolygon polygon =
        new EsriPolygon(
            new double[] {50, 2, 50.1, 2.1, 50, 2.2, 50, 2},
            OMGraphic.DECIMAL_DEGREES,
            OMGraphic.LINETYPE_STRAIGHT);
    polygon.generate(p);
    int[] expected = pixels(polygon);
    detail.generate(list(polygon), p, true, false);
    check(Arrays.equals(expected, pixels(polygon)), "Polygon rendering changed");
    // Date-line coordinates must retain full detail; a view wrapping at another meridian must
    // still obey the screen tolerance for both world copies of an ordinary line.
    EsriPolyline crossing =
        new EsriPolyline(
            new double[] {0, 179, 0, 179.5, 0, -179.5, 0, -179},
            OMGraphic.DECIMAL_DEGREES,
            OMGraphic.LINETYPE_STRAIGHT);
    check(
        detail.generate(list(crossing), p, true, false).getSimplifiedLines() == 0,
        "Date-line source simplified");
    EsriPolyline full = wave(0, -0.25, 100);
    EsriPolyline wrap = wave(0, -0.25, 100);
    p = projection(0, 180, 10000000);
    full.generate(p);
    detail.generate(list(wrap), p, true, false);
    boundedError(full.xpoints, full.ypoints, wrap);
    boundedError(wrap.xpoints, wrap.ypoints, full);
    EsriPolyline duplicate =
        new EsriPolyline(
            new double[] {0, 0, 0, 0, 0, 0, 0, 0}, OMGraphic.RADIANS, OMGraphic.LINETYPE_STRAIGHT);
    Measurement m = detail.generate(list(duplicate), projection(0, 0, 10000000), true, false);
    check(m.getDisplayedVertices() == 2, "Degenerate line did not keep endpoints");
    check(m.getCacheNanos() == 0, "Disabled audit recorded time");
  }

  /** Compare with actual OpenMap great-circle generation, including deliberately dense arcs. */
  private static void greatCircleAccuracy() {
    for (double latitude : new double[] {-65, 0, 50, 80, 84}) {
      for (float scale : new float[] {10000000, 2000000, 100000}) {
        EsriPolyline full = wave(latitude, 2, 100);
        full.setLineType(OMGraphic.LINETYPE_GREATCIRCLE);
        full.setNumSegs(5);
        EsriPolyline line = wave(latitude, 2, 100);
        line.setLineType(OMGraphic.LINETYPE_GREATCIRCLE);
        line.setNumSegs(5);
        double[] source = line.getLatLonArray().clone();
        Mercator p = projection(latitude, 2.25, scale);
        MapPolylineDetail detail = new MapPolylineDetail();
        full.generate(p);
        Measurement m = detail.generate(list(line), p, true, false);
        check(m.getCandidateLines() == 1, "Great-circle line not considered");
        if (scale == 10000000) check(m.getSimplifiedLines() == 1, "Wide-view curve not simplified");
        boundedError(full.xpoints, full.ypoints, line);
        boundedError(line.xpoints, line.ypoints, full);
        check(
            Arrays.equals(source, line.getLatLonArray()),
            "Great-circle source coordinates changed");
        check(
            line.getLineType() == OMGraphic.LINETYPE_GREATCIRCLE && line.getNumSegs() == 5,
            "Great-circle source settings changed");
        check(
            detail.generate(list(line), p, true, false).getBuiltHierarchies() == 0,
            "Great-circle hierarchy not reused");
      }
    }
    // A broad arc at high latitude cannot safely become a straight display segment.
    EsriPolyline longArc =
        new EsriPolyline(
            new double[] {60, -40, 60, -10, 60, 10, 60, 40},
            OMGraphic.DECIMAL_DEGREES,
            OMGraphic.LINETYPE_GREATCIRCLE);
    MapPolylineDetail detail = new MapPolylineDetail();
    Mercator p = projection(60, 0, 10000000);
    longArc.generate(p);
    float[][] originalX = longArc.xpoints;
    float[][] originalY = longArc.ypoints;
    Measurement fallback = detail.generate(list(longArc), p, true, false);
    check(
        fallback.getCurvatureFallbacks() == 1 && fallback.getSimplifiedLines() == 0,
        "Long great-circle arc did not use curvature fallback");
    check(
        Arrays.deepEquals(originalX, longArc.xpoints)
            && Arrays.deepEquals(originalY, longArc.ypoints),
        "Curvature fallback changed projected arc");

    // Switching line type on an existing graphic must replace the cached straight-line metadata.
    longArc.setLineType(OMGraphic.LINETYPE_STRAIGHT);
    check(
        detail.generate(list(longArc), p, true, false).getSimplifiedLines() == 1,
        "Straight line did not simplify after changing type");
    longArc.setLineType(OMGraphic.LINETYPE_GREATCIRCLE);
    check(
        detail.generate(list(longArc), p, true, false).getCurvatureFallbacks() == 1,
        "Changed great-circle line reused straight-line metadata");

    // A moderate arc is eligible far out, but must revert to full curvature at close zoom.
    EsriPolyline moderate =
        new EsriPolyline(
            new double[] {60, 0, 60, 0.25, 60, 0.5, 60, 0.75},
            OMGraphic.DECIMAL_DEGREES,
            OMGraphic.LINETYPE_GREATCIRCLE);
    check(
        detail.generate(list(moderate), p, true, false).getSimplifiedLines() == 1,
        "Moderate arc did not simplify at wide zoom");
    Measurement close = detail.generate(list(moderate), projection(60, 0, 10000), true, false);
    check(
        close.getCurvatureFallbacks() == 1 && close.getSimplifiedLines() == 0,
        "Close zoom failed to restore curved source segments");
  }

  private static void rememberCoordinates(
      OMGraphicList graphics, Map<EsriPolyline, double[]> coordinates) {
    for (OMGraphic graphic : graphics) {
      if (graphic instanceof OMGraphicList) {
        rememberCoordinates((OMGraphicList) graphic, coordinates);
      } else if (graphic instanceof EsriPolyline) {
        EsriPolyline line = (EsriPolyline) graphic;
        check(
            line.getLineType() == OMGraphic.LINETYPE_GREATCIRCLE,
            "Fixture no longer exercises the shapefile loader's great-circle default");
        coordinates.put(line, line.getLatLonArray().clone());
      }
    }
  }

  /**
   * Exercise the real SHP loader; synthetic straight-line fixtures missed this integration path.
   */
  private static void loadedShapefiles() throws Exception {
    for (String name : new String[] {"road", "rail", "iww"}) {
      EsriGraphicList graphics =
          EsriGraphicList.getEsriGraphicList(
              Path.of("demo", name + "_polylines.shp").toUri().toURL(),
              (DrawingAttributes) null,
              null,
              null);
      check(graphics != null && !graphics.isEmpty(), "Cannot load demo " + name);
      Map<EsriPolyline, double[]> coordinates = new IdentityHashMap<>();
      rememberCoordinates(graphics, coordinates);
      MapPolylineDetail detail = new MapPolylineDetail();
      Mercator view = projection(50.5, 4.5, 10000000);
      Measurement m = detail.generate(graphics, view, true, true);
      check(
          m.getBuiltHierarchies() > 0 && m.getSimplifiedLines() > 0,
          "Loaded " + name + " shapefile never entered simplification");
      check(m.getDisplayedVertices() < m.getSourceVertices(), "Loaded vertices were not reduced");
      for (Map.Entry<EsriPolyline, double[]> entry : coordinates.entrySet()) {
        check(
            Arrays.equals(entry.getValue(), entry.getKey().getLatLonArray()),
            "Loaded shapefile coordinates changed");
        check(
            entry.getKey().getLineType() == OMGraphic.LINETYPE_GREATCIRCLE,
            "Loaded shapefile line type changed");
        EsriPolyline full =
            new EsriPolyline(
                entry.getValue().clone(), OMGraphic.RADIANS, OMGraphic.LINETYPE_GREATCIRCLE);
        full.generate(view);
        boundedError(full.xpoints, full.ypoints, entry.getKey());
        boundedError(entry.getKey().xpoints, entry.getKey().ypoints, full);
      }
      Measurement pan = detail.generate(graphics, projection(50.6, 4.6, 10000000), true, true);
      check(
          pan.getBuiltHierarchies() == 0 && pan.getBuiltLevels() == 0, "Loaded pan rebuilt detail");
      Measurement off = detail.generate(graphics, projection(50.5, 4.5, 10000000), false, true);
      check(
          off.getDisplayedVertices() == off.getSourceVertices() && off.getSimplifiedLines() == 0,
          "Loaded full-detail comparison failed");
      System.out.printf(
          Locale.ROOT,
          "Loaded demo %s: %,d -> %,d vertices; %,d simplified lines; %,d curvature fallbacks%n",
          name,
          m.getSourceVertices(),
          m.getDisplayedVertices(),
          m.getSimplifiedLines(),
          m.getCurvatureFallbacks());
    }
  }

  private static void benchmark() {
    OMGraphicList lines = new OMGraphicList();
    for (int i = 0; i < 20000; i++) {
      lines.add(wave(48 + (i % 200) * 0.02, 1 + (i / 200) * 0.04, 100));
    }
    Mercator p = projection(50, 4, 10000000);
    for (int i = 0; i < 3; i++) lines.generate(p);
    long full = 0;
    long reduced = 0;
    MapPolylineDetail detail = new MapPolylineDetail();
    long start = System.nanoTime();
    Measurement cold = detail.generate(lines, p, true, true);
    long coldTime = System.nanoTime() - start;
    for (int i = 0; i < 7; i++) {
      p = projection(50, 4 + i * 0.03, 10000000);
      // Alternate order to avoid consistently measuring one mode immediately after the other.
      if ((i & 1) == 0) {
        start = System.nanoTime();
        lines.generate(p);
        full += System.nanoTime() - start;
        start = System.nanoTime();
        detail.generate(lines, p, true, false);
        reduced += System.nanoTime() - start;
      } else {
        start = System.nanoTime();
        detail.generate(lines, p, true, false);
        reduced += System.nanoTime() - start;
        start = System.nanoTime();
        lines.generate(p);
        full += System.nanoTime() - start;
      }
    }
    System.out.printf(
        Locale.ROOT,
        "Synthetic 20,000 lines: %,d -> %,d vertices; cold %.3f ms (cache %.3f ms); "
            + "warm mean full %.3f ms, simplified %.3f ms%n",
        cold.getSourceVertices(),
        cold.getDisplayedVertices(),
        coldTime / 1e6,
        cold.getCacheNanos() / 1e6,
        full / 7e6,
        reduced / 7e6);
  }

  public static void main(String[] args) throws Exception {
    accuracyAndReuse();
    renderingAndFallbacks();
    greatCircleAccuracy();
    loadedShapefiles();
    System.out.println("Map polyline detail, pixel-error and source-preservation checks passed.");
    if (Arrays.asList(args).contains("--benchmark")) benchmark();
  }
}
