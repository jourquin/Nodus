package com.bbn.openmap.layer.shape.displayindex;

import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.util.DataBounds;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Differential checks and an optional synthetic benchmark; no project or database is opened. */
public final class DisplaySpatialIndexTest {
  private static volatile long consumed;

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void same(OMGraphicList expected, OMGraphicList actual) {
    check(expected.size() == actual.size(), "Different match counts");
    for (int i = 0; i < expected.size(); i++) {
      check(expected.get(i) == actual.get(i), "Drawing/selection order changed at " + i);
    }
  }

  private static OMGraphicList graphics(int count) {
    Random random = new Random(2938);
    OMGraphicList source = new OMGraphicList();
    for (int i = 0; i < count; i++) {
      double x = random.nextDouble() * 350 - 175;
      double y = random.nextDouble() * 160 - 80;
      OMGraphic graphic;
      if (i % 3 == 0) {
        graphic = new EsriPoint(y, x);
      } else {
        graphic =
            new EsriPolyline(
                new double[] {y, x, y + random.nextDouble(), x + random.nextDouble()},
                OMGraphic.DECIMAL_DEGREES,
                OMGraphic.LINETYPE_STRAIGHT);
      }
      source.add(graphic);
    }
    return source;
  }

  private static void compareQueries(OMGraphicList source, int count) throws Exception {
    DisplaySpatialIndex linear = new DisplaySpatialIndexLinear(source);
    DisplaySpatialIndex tree = DisplaySpatialIndexFactory.createIndex(source);
    Random random = new Random(5437);
    for (int i = 0; i < count; i++) {
      double x = random.nextDouble() * 360 - 180;
      double y = random.nextDouble() * 180 - 90;
      double width = i % 2 == 0 ? 2 : 150;
      double east = Math.min(180, x + width);
      if (i % 7 == 0) east = -170; // Wrapped and ordinary views.
      same(
          linear.locateRecords(x, y, east, Math.min(90, y + width)),
          tree.locateRecords(x, y, east, Math.min(90, y + width)));
    }
    for (double[] box :
        new double[][] {
          {-180, -90, 180, 90}, {0, 0, 0, 0}, {180, -90, -180, 90},
          {-181, -91, 181, 91}, {-10, -10, 10, 10}, {170, -90, -170, 90}
        }) {
      same(
          linear.locateRecords(box[0], box[1], box[2], box[3]),
          tree.locateRecords(box[0], box[1], box[2], box[3]));
    }
  }

  /** Explicit expected results supplement comparison with the existing linear implementation. */
  private static void boundariesAndWrapping() throws Exception {
    OMGraphicList source = graphics(100);
    // Put random filler well outside the latitude band of the checks below.
    source.clear();
    for (int i = 0; i < 100; i++) source.add(new EsriPoint(70, i));
    EsriPoint east = new EsriPoint(0, 179);
    EsriPoint west = new EsriPoint(0, -179);
    EsriPoint center = new EsriPoint(0, 0);
    EsriPoint boundary = new EsriPoint(0, 170);
    EsriPolyline crossing =
        new EsriPolyline(
            new double[] {0, -179, 0, 179}, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
    source.add(east);
    source.add(center);
    source.add(west);
    source.add(boundary);
    source.add(crossing);
    source.add(east); // Preserve an existing repeated source entry, unlike duplicate query hits.
    DisplaySpatialIndex tree = new DisplaySpatialIndexTree(source);
    same(
        new OMGraphicList(Arrays.asList(east, west, crossing, east)),
        tree.locateRecords(170, -10, -170, 10));
    same(new OMGraphicList(Arrays.asList(center, crossing)), tree.locateRecords(-10, -10, 10, 10));

    OMGraphicList nested = new OMGraphicList();
    nested.add(new EsriPoint(1, 1));
    nested.add(new EsriPoint(2, 2));
    source.add(nested);
    compareQueries(source, 500);
    // Unsupported numerical bounds use the previous comparison path rather than unsafe pruning.
    source.add(new EsriPoint(Double.NaN, 0));
    compareQueries(source, 50);
  }

  private static void concurrentQueries() throws Exception {
    OMGraphicList source = graphics(3000);
    DisplaySpatialIndex linear = new DisplaySpatialIndexLinear(source);
    DisplaySpatialIndex tree = new DisplaySpatialIndexTree(source);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      Callable<Void> queries =
          () -> {
            for (int i = 0; i < 200; i++) {
              double x = (i * 17 % 300) - 150;
              same(
                  linear.locateRecords(x, -20, x + 10, 20), tree.locateRecords(x, -20, x + 10, 20));
            }
            return null;
          };
      for (Future<Void> result :
          executor.invokeAll(Arrays.asList(queries, queries, queries, queries))) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static long queryBatch(DisplaySpatialIndex index, List<DataBounds> views)
      throws Exception {
    long started = System.nanoTime();
    long total = 0;
    for (DataBounds view : views) total += index.locateRecords(view).size();
    consumed = total;
    return System.nanoTime() - started;
  }

  private static double median(long[] values) {
    Arrays.sort(values);
    return values[values.length / 2] / 1e6;
  }

  private static void benchmark() throws Exception {
    OMGraphicList source = graphics(100000);
    long start = System.nanoTime();
    DisplaySpatialIndex linear = new DisplaySpatialIndexLinear(source);
    long linearBuild = System.nanoTime() - start;
    start = System.nanoTime();
    DisplaySpatialIndex tree = new DisplaySpatialIndexTree(source);
    long treeBuild = System.nanoTime() - start;
    List<DataBounds> views = new java.util.ArrayList<>();
    for (int i = 0; i < 200; i++) {
      double x = (i * 17 % 300) - 150;
      double y = (i * 13 % 120) - 60;
      views.add(new DataBounds(x, y, x + 4, y + 4));
    }
    for (int i = 0; i < 2; i++) {
      queryBatch(linear, views);
      queryBatch(tree, views);
    }
    long[] linearTimes = new long[5];
    long[] treeTimes = new long[5];
    for (int i = 0; i < 5; i++) {
      if (i % 2 == 0) {
        linearTimes[i] = queryBatch(linear, views);
        treeTimes[i] = queryBatch(tree, views);
      } else {
        treeTimes[i] = queryBatch(tree, views);
        linearTimes[i] = queryBatch(linear, views);
      }
    }
    System.out.printf(
        Locale.ROOT,
        "100,000 shapes; 200 small viewports; median of five batches:%n"
            + "  Reused linear index: %.3f ms; reused tree: %.3f ms%n"
            + "  Construction (single samples): linear %.3f ms; tree %.3f ms%n"
            + "  Query selection only; excludes projection, painting and UI latency.%n",
        median(linearTimes),
        median(treeTimes),
        linearBuild / 1e6,
        treeBuild / 1e6);
    List<DataBounds> world = Arrays.asList(new DataBounds(-181, -91, 181, 91));
    for (int i = 0; i < 5; i++) {
      linearTimes[i] = queryBatch(linear, world);
      treeTimes[i] = queryBatch(tree, world);
    }
    System.out.printf(
        Locale.ROOT,
        "  Whole-world selection: linear %.3f ms; tree %.3f ms%n",
        median(linearTimes),
        median(treeTimes));
  }

  /** Runs deterministic checks; pass --benchmark for optional local measurements. */
  public static void main(String[] args) throws Exception {
    compareQueries(new OMGraphicList(), 30);
    compareQueries(graphics(12), 200);
    compareQueries(graphics(4000), 3000);
    boundariesAndWrapping();
    concurrentQueries();
    System.out.println("Display spatial-index checks passed.");
    if (Arrays.asList(args).contains("--benchmark")) benchmark();
  }
}
