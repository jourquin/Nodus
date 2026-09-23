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

package com.bbn.openmap.layer.shape;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.io.FormatException;
import com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndex;
import com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndexFactory;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.event.NodusMapMouseInterpreter;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.util.MoreMath;
import edu.uclouvain.core.nodus.NodusC;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An EsriLayer that uses a memory spatial index in order to limit repainting to the visible part of
 * the map. This dramatically boosts the performance for large layers.
 *
 * @author Bart Jourquin
 */
public class FastEsriLayer extends EsriLayer {

  private static final long serialVersionUID = -5646162140876554837L;

  /** Geometry invalidation is independent of projection changes and does not acquire list locks. */
  private final AtomicLong geometryRevision = new AtomicLong();

  /** Geographic index and, after a successful preparation, the visible graphics. */
  private volatile RenderCache renderCache;

  /** Source list already loaded by OpenMap, retained to avoid lazy SHP reads during disposal. */
  private EsriGraphicList loadedEsriGraphicList = null;

  /** True once this layer has been disposed and must not rebuild heavy caches. */
  private volatile boolean disposed = false;

  /** Associates a geographic index and visible graphics with exactly one source revision. */
  private static final class RenderCache {
    final long revision;
    final EsriGraphicList source;
    final int sourceSize;
    final DisplaySpatialIndex index;
    final OMGraphicList visible;

    RenderCache(
        long revision,
        EsriGraphicList source,
        int sourceSize,
        DisplaySpatialIndex index,
        OMGraphicList visible) {
      this.revision = revision;
      this.source = source;
      this.sourceSize = sourceSize;
      this.index = index;
      this.visible = visible;
    }
  }

  /** Default constructor. */
  public FastEsriLayer() {
    super();
  }

  /**
   * Sets the properties for the <code>Layer</code>.
   *
   * @param prefix the token to prefix the property names
   * @param properties the <code>Properties</code> object
   */
  public void setProperties(String prefix, Properties properties) {
    disposed = false;
    loadedEsriGraphicList = null;
    clearRenderCaches();
    super.setProperties(prefix, properties);
    setMouseEventInterpreter(new NodusMapMouseInterpreter(this));
  }

  /** Clears geographic and projected caches when changing data sources or disposing the layer. */
  protected synchronized void clearRenderCaches() {
    invalidateSpatialIndex();
  }

  /**
   * Invalidates geographic bounds after adding, removing, replacing or moving graphics.
   *
   * <p>Ordinary pan, zoom, resize, style and result changes do not need this call. Rebuilding is
   * deferred until the next preparation, so editing several links does not repeatedly index the
   * layer. Code modifying the live source graphics must call this method after its changes.
   */
  public void invalidateSpatialIndex() {
    geometryRevision.incrementAndGet();
    renderCache = null;
  }

  /**
   * Releases the heavy graphic lists held by this layer.
   *
   * <p>This is intentionally separated from {@link #clearRenderCaches()} because it is destructive:
   * it must only be called when the layer is definitely being closed/disposed.
   */
  protected synchronized void clearLayerData() {
    RenderCache cache = renderCache;
    OMGraphicList projectedList = cache == null ? null : cache.visible;
    if (projectedList != null) {
      projectedList.clear();
    }

    if (selectedGraphics != null) {
      selectedGraphics.clear();
    }

    EsriGraphicList esriGraphicList = loadedEsriGraphicList;
    if (esriGraphicList != null && esriGraphicList != projectedList) {
      synchronized (esriGraphicList) {
        esriGraphicList.clear();
      }
    }
    loadedEsriGraphicList = null;
  }

  /**
   * Releases the mouse interpreter without using setMouseEventInterpreter(null), because OpenMap's
   * setter assumes a non-null argument.
   */
  protected synchronized void clearMouseEventInterpreter() {
    if (mouseEventInterpreter != null) {
      mouseEventInterpreter.setGRP(null);
      mouseEventInterpreter = null;
    }
  }

  /** Releases caches, graphics and mouse callbacks so this layer can be garbage-collected. */
  @Override
  public synchronized void dispose() {
    if (disposed) {
      return;
    }

    disposed = true;
    clearLayerData();
    clearRenderCaches();
    clearMouseEventInterpreter();
    super.dispose();
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
    if (disposed) {
      return null;
    }

    RenderCache cache = renderCache;
    if (cache != null
        && cache.visible != null
        && cache.revision == geometryRevision.get()
        && cache.sourceSize == cache.source.size()) {
      return cache.visible;
    }
    return getEsriGraphicList();
  }

  /**
   * Returns source graphics without building a display index during database or editing work.
   *
   * @return The layer's source graphics.
   */
  @Override
  public synchronized EsriGraphicList getEsriGraphicList() {
    if (disposed) {
      return loadedEsriGraphicList;
    }
    EsriGraphicList source = super.getEsriGraphicList();
    if (source != loadedEsriGraphicList) {
      loadedEsriGraphicList = source;
      invalidateSpatialIndex();
    }
    return source;
  }

  /**
   * Builds an index for a stable source snapshot; called only after geometry invalidation.
   *
   * @param source Source geometry for the current revision.
   * @return A geographic index reusable across projections.
   */
  protected DisplaySpatialIndex createSpatialIndex(OMGraphicList source) {
    return DisplaySpatialIndexFactory.createIndex(source);
  }

  /** Selects visible graphics without projecting them; preparation projects them exactly once. */
  private OMGraphicList getSpatialList(DisplaySpatialIndex index, Projection projection)
      throws IOException, FormatException {
    LatLonPoint.Double ul = projection.getUpperLeft();
    LatLonPoint.Double lr = projection.getLowerRight();
    double west = ul.getLongitude();
    double east = lr.getLongitude();
    double south = Math.min(ul.getLatitude(), lr.getLatitude());
    double north = Math.max(ul.getLatitude(), lr.getLatitude());

    // A nearly closed reversed interval represents a whole-world view. Keep narrow west-to-east
    // views narrow at high zoom. Other reversed intervals cross the date line; the index combines
    // their two halves in source order without duplicating crossing graphics.
    if (west >= east && MoreMath.approximately_equal(west, east, .001)) {
      west = -180;
      east = 180;
    }
    return index.locateRecords(west, south, east, north);
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
    if (disposed) {
      return null;
    }
    Projection projection = getProjection();
    if (projection == null) {
      return null;
    }

    boolean audit = NodusC.displayMapComputingTimes;
    final long started = audit ? System.nanoTime() : 0;
    EsriGraphicList source = getEsriGraphicList();
    if (source == null) {
      return null;
    }
    long revision = geometryRevision.get();
    int sourceSize = source.size();
    RenderCache cache = renderCache;
    boolean rebuild =
        cache == null
            || cache.revision != revision
            || cache.source != source
            || cache.sourceSize != sourceSize;
    DisplaySpatialIndex index;
    if (rebuild) {
      // Snapshot membership under the same lock used by Nodus add/remove operations. Bounds and
      // tree construction then use this snapshot without keeping the source-list lock held.
      OMGraphicList snapshot;
      synchronized (source) {
        snapshot = new OMGraphicList(source);
      }
      index = createSpatialIndex(snapshot);
      // Keep completed geographic work even if a new pan cancels this projection. It can be
      // reused by the next worker, whereas a geometry edit or disposal must discard it.
      synchronized (this) {
        if (disposed || revision != geometryRevision.get() || sourceSize != source.size()) {
          return null;
        }
        renderCache = new RenderCache(revision, source, sourceSize, index, null);
      }
    } else {
      index = cache.index;
    }
    final long indexed = audit ? System.nanoTime() : 0;

    OMGraphicList matches;
    try {
      matches = getSpatialList(index, projection);
    } catch (IOException | FormatException ex) {
      ex.printStackTrace();
      return null;
    }
    final long selected = audit ? System.nanoTime() : 0;
    if (disposed
        || revision != geometryRevision.get()
        || Thread.currentThread().isInterrupted()
        || !projection.equals(getProjection())) {
      return null;
    }

    // Force generation for the new projection, even when the geographic index was reused.
    matches.generate(projection);
    OMGraphicList visible = new OMGraphicList();
    visible.add(matches);
    OMGraphicList parent = new OMGraphicList();
    parent.add(selectedGraphics);
    parent.add(visible);

    // Disposal uses the same lock: a preparation finishing late cannot resurrect closed caches.
    synchronized (this) {
      if (disposed
          || revision != geometryRevision.get()
          || sourceSize != source.size()
          || Thread.currentThread().isInterrupted()
          || !projection.equals(getProjection())) {
        return null;
      }
      renderCache = new RenderCache(revision, source, sourceSize, index, visible);
    }
    if (audit) {
      long finished = System.nanoTime();
      System.out.printf(
          Locale.ROOT,
          "Map computing times: %s (%d shapes, %d selected, index %s)%n"
              + "  Total preparation: %.3f ms; source/index: %.3f ms;"
              + " selection: %.3f ms; projection: %.3f ms%n",
          getName(),
          sourceSize,
          matches.size(),
          rebuild ? "rebuilt" : "reused",
          (finished - started) / 1e6,
          (indexed - started) / 1e6,
          (selected - indexed) / 1e6,
          (finished - selected) / 1e6);
    }
    return parent;
  }
}
