package com.bbn.openmap.layer.shape;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndex;
import com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndexLinear;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;
import edu.uclouvain.core.nodus.NodusC;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Exercises actual layer preparation and edit invalidation without opening a project or GUI. */
public final class MapNavigationTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class Point extends EsriPoint {
    int generations;

    Point(double lat, double lon) {
      super(lat, lon);
      setLinePaint(Color.RED);
      setFillPaint(Color.RED);
    }

    @Override
    public boolean generate(Projection projection) {
      generations++;
      return super.generate(projection);
    }
  }

  private static final class Layer extends NodusEsriLayer {
    final DbfTableModel model = new DbfTableModel(12);
    int builds;
    volatile CountDownLatch building;
    volatile CountDownLatch proceed;

    Layer(EsriGraphicList source) throws Exception {
      setName("Synthetic navigation layer");
      for (int i = 0; i < model.getColumnCount(); i++) {
        model.setColumnName(i, "FIELD" + i);
        model.setType(i, DbfTableModel.TYPE_NUMERIC);
      }
      install(source);
      setProjection(projection(0, 0, 8000000, 800, 600));
    }

    void install(EsriGraphicList source) throws Exception {
      // Supply source data to the real OpenMap getter without invoking shapefile I/O.
      Field field = EsriLayer.class.getDeclaredField("_list");
      field.setAccessible(true);
      field.set(this, source);
    }

    @Override
    protected DisplaySpatialIndex createSpatialIndex(OMGraphicList source) {
      builds++;
      DisplaySpatialIndex index = super.createSpatialIndex(source);
      if (building != null) {
        building.countDown();
        try {
          check(proceed.await(10, TimeUnit.SECONDS), "Timed out waiting for edit/disposal");
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new AssertionError(ex);
        }
      }
      return index;
    }

    @Override
    public DbfTableModel getModel() {
      return model;
    }

    @Override
    public String getTableName() {
      return "synthetic";
    }

    @Override
    public void executeUpdateSqlStmt(String sql) {
      // Geometry edit tests need no database; DBF rows still use the real layer implementation.
    }

    void selectForOverlay(OMGraphic graphic) {
      selectedGraphics.add(graphic);
    }

    void resetCaches() {
      clearRenderCaches();
    }
  }

  private static Projection projection(
      double latitude, double longitude, float scale, int width, int height) {
    return new Mercator(new LatLonPoint.Double(latitude, longitude), scale, width, height);
  }

  private static OMGraphicList matches(Layer layer) {
    return (OMGraphicList) layer.getVisibleEsriGraphicList().get(0);
  }

  private static boolean containsIdentity(OMGraphicList list, OMGraphic graphic) {
    for (OMGraphic item : list) if (item == graphic) return true;
    return false;
  }

  private static void reuseAndEdits() throws Exception {
    Layer layer = new Layer(new EsriPointList());
    Point first = new Point(0, 0);
    Point second = new Point(0, 0);
    check(layer.addRecord(first, 1), "Could not add point");
    check(layer.addRecord(second, 2), "Could not add overlapping point");
    check(layer.builds == 0, "Source reads during edits built a display index");
    first.generations = 0;
    second.generations = 0;
    check(layer.prepare() != null, "Initial preparation failed");
    check(layer.builds == 1, "Initial index not built exactly once");
    check(first.generations == 1 && second.generations == 1, "Graphics projected more than once");
    check(
        matches(layer).get(0) == first && matches(layer).get(1) == second, "Source order changed");
    check(
        layer.getVisibleEsriGraphicList().findClosest(400, 300, 5) == first,
        "Overlapping point selection order changed");

    for (Projection p :
        new Projection[] {
          projection(1, 1, 8000000, 800, 600), projection(0, 0, 4000000, 800, 600),
          projection(0, 0, 8000000, 1000, 700), projection(0, 0, 8000000, 800, 600)
        }) {
      layer.setProjection(p);
      layer.prepare();
    }
    check(layer.builds == 1, "Pan, zoom or resize rebuilt geographic bounds");
    check(first.generations == 5, "Reusing the index skipped projection updates");
    first.setVisible(false);
    layer.setDirtyDbf(true);
    layer.setDisplayResults(true);
    layer.prepare();
    check(layer.builds == 1, "Attribute/result/visibility changes rebuilt the index");
    first.setVisible(true);

    first.setLon(120);
    layer.setDirtyShp(true);
    layer.setDirtyShp(true);
    check(layer.builds == 1, "Invalidation rebuilt eagerly");
    layer.prepare();
    check(
        layer.builds == 2 && !containsIdentity(matches(layer), first),
        "Moved point stayed in view");
    first.setLon(0);
    layer.setDirtyShp(true); // The layer is already dirty; this must still invalidate it.
    layer.prepare();
    check(
        layer.builds == 3 && containsIdentity(matches(layer), first), "Repeated move was ignored");

    Point added = new Point(0, 1);
    layer.addRecord(added, 3);
    layer.prepare();
    check(layer.builds == 4 && containsIdentity(matches(layer), added), "Added point missing");
    layer.removeLastRecord(); // Same removal used when cancelling a newly created record.
    layer.prepare();
    check(
        layer.builds == 5 && !containsIdentity(matches(layer), added), "Cancelled point retained");
    layer.removeRecord(1, false);
    layer.prepare();
    check(layer.builds == 6 && !containsIdentity(matches(layer), second), "Deleted point retained");
    layer.setDirtyShp(false);
    layer.prepare();
    check(layer.builds == 6, "Saving unchanged geometry rebuilt the index");

    EsriPointList replacement = new EsriPointList();
    replacement.add(new Point(0, 2));
    layer.install(replacement);
    layer.prepare();
    check(
        layer.builds == 7 && matches(layer).get(0) == replacement.get(0),
        "Source swap was ignored");
    layer.resetCaches();
    layer.prepare();
    check(layer.builds == 8, "Explicit cache reset failed");
    layer.dispose();
    check(
        layer.prepare() == null && layer.getVisibleEsriGraphicList() == null,
        "Disposed layer rebuilt its caches");
  }

  private static int[] pixels(OMGraphicList graphics, Projection projection) {
    BufferedImage image =
        new BufferedImage(
            projection.getWidth(), projection.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D canvas = image.createGraphics();
    try {
      graphics.render(canvas);
    } finally {
      canvas.dispose();
    }
    return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
  }

  private static void linesAndRendering() throws Exception {
    Layer layer = new Layer(new EsriPolylineList());
    EsriPolyline first =
        new EsriPolyline(
            new double[] {-1, -1, 1, 1}, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
    EsriPolyline second =
        new EsriPolyline(
            new double[] {-1, 1, 1, -1}, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
    first.setLinePaint(Color.BLUE);
    second.setLinePaint(Color.RED);
    layer.addRecord(first, 1, 10, 11, false);
    layer.addRecord(second, 2, 11, 12, false);
    layer.selectForOverlay(second);
    OMGraphicList actual = layer.prepare();
    DisplaySpatialIndex linear = new DisplaySpatialIndexLinear(layer.getEsriGraphicList());
    OMGraphicList expected = linear.locateRecords(-180, -90, 180, 90);
    expected.generate(layer.getProjection());
    OMGraphicList withSelection = new OMGraphicList();
    withSelection.add(new OMGraphicList(Arrays.asList(second)));
    withSelection.add(expected);
    check(
        Arrays.equals(
            pixels(withSelection, layer.getProjection()), pixels(actual, layer.getProjection())),
        "Rendering or selected-graphic overlay changed");

    EsriPolyline moved =
        new EsriPolyline(
            new double[] {40, 100, 41, 101},
            OMGraphic.DECIMAL_DEGREES,
            OMGraphic.LINETYPE_STRAIGHT);
    layer.getEsriGraphicList().setOMGraphicAt(moved, 0);
    layer.setDirtyShp(true);
    layer.prepare();
    check(
        layer.builds == 2
            && !containsIdentity(matches(layer), first)
            && !containsIdentity(matches(layer), moved),
        "Replaced link retained stale bounds");
    layer.dispose();
  }

  private static void dateLine() throws Exception {
    EsriPointList source = new EsriPointList();
    Point east = new Point(0, 179);
    Point west = new Point(0, -179);
    Point middle = new Point(0, 0);
    source.add(east);
    source.add(middle);
    source.add(west);
    Layer layer = new Layer(source);
    layer.setProjection(projection(0, 179, 8000000, 800, 600));
    layer.prepare();
    check(
        matches(layer).size() == 2
            && matches(layer).get(0) == east
            && matches(layer).get(1) == west,
        "Date-line view omitted one half or changed source order");
    layer.setProjection(projection(0, -179, 8000000, 800, 600));
    layer.prepare();
    check(
        layer.builds == 1 && matches(layer).size() == 2,
        "Crossing the date line rebuilt the index");
    layer.setProjection(projection(0, 0, 100, 800, 600));
    layer.prepare();
    check(
        matches(layer).size() == 1 && matches(layer).get(0) == middle,
        "A narrow high-zoom view was treated as the whole world");
    layer.setProjection(projection(0, 0, Float.MAX_VALUE, 800, 600));
    layer.prepare();
    check(matches(layer).size() == 3, "Whole-world view omitted graphics");
    layer.dispose();
  }

  private static void concurrentInvalidation(boolean dispose) throws Exception {
    EsriPointList source = new EsriPointList();
    source.add(new Point(0, 0));
    Layer layer = new Layer(source);
    layer.building = new CountDownLatch(1);
    layer.proceed = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<OMGraphicList> result = executor.submit(layer::prepare);
      check(layer.building.await(10, TimeUnit.SECONDS), "Preparation never started");
      if (dispose) layer.dispose();
      else layer.setDirtyShp(true);
      layer.proceed.countDown();
      check(result.get(10, TimeUnit.SECONDS) == null, "Stale preparation was published");
      if (dispose) {
        check(layer.getVisibleEsriGraphicList() == null, "Disposed graphics resurrected");
      } else {
        check(layer.prepare() != null && layer.builds == 2, "Invalidated preparation was reused");
        layer.dispose();
      }
    } finally {
      layer.proceed.countDown();
      executor.shutdownNow();
    }
  }

  /** Runs layer, rendering, edit and lifecycle checks on synthetic geometry. */
  public static void main(String[] args) throws Exception {
    NodusC.displayMapComputingTimes = false;
    reuseAndEdits();
    linesAndRendering();
    dateLine();
    concurrentInvalidation(false);
    concurrentInvalidation(true);
    supersededProjection();
    System.out.println("Map navigation, rendering and edit-invalidation checks passed.");
  }

  /** A newer pan cancels the display work but retains a completed index of unchanged geometry. */
  private static void supersededProjection() throws Exception {
    EsriPointList source = new EsriPointList();
    source.add(new Point(0, 0));
    Layer layer = new Layer(source);
    layer.building = new CountDownLatch(1);
    layer.proceed = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<OMGraphicList> result = executor.submit(layer::prepare);
      check(layer.building.await(10, TimeUnit.SECONDS), "Preparation never started");
      layer.setProjection(projection(1, 1, 4000000, 800, 600));
      layer.proceed.countDown();
      check(result.get(10, TimeUnit.SECONDS) == null, "Superseded view was published");
      check(
          layer.prepare() != null && layer.builds == 1, "Pan discarded completed geographic work");
      layer.dispose();
    } finally {
      layer.proceed.countDown();
      executor.shutdownNow();
    }
  }
}
