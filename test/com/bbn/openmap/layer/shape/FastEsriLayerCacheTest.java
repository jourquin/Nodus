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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.layer.shape.displayindex.DisplaySpatialIndex;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.coords.LatLonPoint;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs actual layer preparation synchronously; only loading and index-build observation differ. */
class FastEsriLayerCacheTest {
  @Test
  void panZoomResizeAndStyleChangesReuseTheIndexButRefreshVisibleObjects() {
    try (Layer layer = new Layer()) {
      final EsriPoint near = new EsriPoint(0, 0);
      final EsriPoint far = new EsriPoint(0, 20);
      layer.source.add(near);
      layer.source.add(far);
      assertEquals(List.of(near), layer.prepareVisible());
      assertEquals(1, layer.builds);
      layer.setProjection(projection(20, 1000000, 800));
      assertEquals(List.of(far), layer.prepareVisible());
      layer.setProjection(projection(20, 2000000, 1000));
      far.setLinePaint(Color.RED);
      assertEquals(List.of(far), layer.prepareVisible());
      assertSame(Color.RED, far.getLinePaint());
      assertEquals(1, layer.builds);
    }
  }

  @Test
  void movingReplacingAddingAndDeletingGraphicsRefreshesCachedBounds() {
    try (Layer layer = new Layer()) {
      final EsriPoint original = new EsriPoint(0, 0);
      layer.source.add(original);
      assertEquals(List.of(original), layer.prepareVisible());
      original.setLat(30);
      original.setLon(30);
      layer.invalidateSpatialIndex();
      assertEquals(List.of(), layer.prepareVisible());
      assertEquals(2, layer.builds);
      final EsriPoint replacement = new EsriPoint(0, 0);
      layer.source.setOMGraphicAt(replacement, 0);
      layer.invalidateSpatialIndex();
      assertEquals(List.of(replacement), layer.prepareVisible());
      assertEquals(3, layer.builds);
      final EsriPoint inserted = new EsriPoint(.1, .1);
      layer.source.add(inserted);
      assertEquals(List.of(replacement, inserted), layer.prepareVisible());
      assertEquals(4, layer.builds, "Membership size changes must also invalidate the cache");
      layer.source.remove(0);
      assertEquals(List.of(inserted), layer.prepareVisible());
      assertEquals(5, layer.builds);
    }
  }

  @Test
  void replacingTheSourceWithTheSameSizeCannotReuseTheOldIndex() {
    try (Layer layer = new Layer()) {
      layer.source.add(new EsriPoint(0, 0));
      assertEquals(1, layer.prepareVisible().size());
      layer.source = new EsriPointList();
      layer.source.add(new EsriPoint(30, 30));
      assertEquals(List.of(), layer.prepareVisible());
      assertEquals(2, layer.builds);
    }
  }

  @Test
  void editDuringIndexConstructionPreventsPublishingStaleResults() {
    try (Layer layer = new Layer()) {
      final EsriPoint point = new EsriPoint(0, 0);
      layer.source.add(point);
      layer.afterBuild =
          () -> {
            point.setLat(30);
            point.setLon(30);
            layer.invalidateSpatialIndex();
          };
      assertNull(layer.prepare());
      layer.afterBuild = () -> {};
      assertEquals(List.of(), layer.prepareVisible());
      assertEquals(2, layer.builds);
    }
  }

  @Test
  void disposalCannotResurrectAPreparedCache() {
    try (Layer layer = new Layer()) {
      layer.source.add(new EsriPoint(0, 0));
      layer.afterBuild = layer::dispose;
      assertNull(layer.prepare());
      assertNull(layer.getVisibleEsriGraphicList());
      assertNull(layer.prepare());
      assertEquals(1, layer.builds);
    }
  }

  private static Mercator projection(double longitude, float scale, int width) {
    return new Mercator(new LatLonPoint.Double(0, longitude), scale, width, 600);
  }

  private static void flatten(OMGraphicList source, List<OMGraphic> target) {
    for (OMGraphic graphic : source) {
      if (graphic instanceof OMGraphicList) {
        flatten((OMGraphicList) graphic, target);
      } else {
        target.add(graphic);
      }
    }
  }

  private static final class Layer extends FastEsriLayer implements AutoCloseable {
    private static final long serialVersionUID = 1L;
    EsriGraphicList source = new EsriPointList();
    int builds;
    Runnable afterBuild = () -> {};

    Layer() {
      setProjection(projection(0, 1000000, 800));
    }

    List<OMGraphic> prepareVisible() {
      assertNotNull(prepare());
      List<OMGraphic> result = new ArrayList<>();
      flatten(getVisibleEsriGraphicList(), result);
      return result;
    }

    @Override
    public EsriGraphicList getEsriGraphicList() {
      return source;
    }

    @Override
    protected DisplaySpatialIndex createSpatialIndex(OMGraphicList graphics) {
      builds++;
      DisplaySpatialIndex index = super.createSpatialIndex(graphics);
      afterBuild.run();
      return index;
    }

    @Override
    public void close() {
      dispose();
    }
  }
}
