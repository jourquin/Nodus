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

package com.bbn.openmap.layer.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.dataAccess.mapTile.ServerMapTileFactory;
import com.bbn.openmap.dataAccess.mapTile.StandardMapTileFactory;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises persistent control actions and cache isolation without downloading or painting tiles.
 */
class NodusMapTileLayerTest {
  @TempDir Path directory;

  @Test
  void serverTilesExposeOneWorkingTransparencySliderAndOneClearAction() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final Properties props = new Properties();
          props.setProperty("tiles.transparency", "0.4");
          final TestLayer layer = new TestLayer();
          layer.setProperties("tiles", props);
          layer.setTileFactory(new ServerMapTileFactory());
          layer.isServerReachable = true;
          final Component gui = layer.getGUI();
          final List<JSlider> sliders = components(gui, JSlider.class);
          assertEquals(1, sliders.size());
          assertEquals(1, components(gui, JButton.class).size());
          final JSlider slider = sliders.get(0);
          assertEquals(102, slider.getValue());
          for (int value : new int[] {0, 153, 255}) {
            slider.setValue(value);
            assertEquals(value / 255f, layer.getTransparency(), 0.000001f);
            assertEquals(
                value / 255f, Float.parseFloat(props.getProperty("tiles.transparency")), 0.000001f);
          }
          layer.getGUI();
          slider.setValue(51);
          assertEquals(0.2f, Float.parseFloat(props.getProperty("tiles.transparency")), 0.000001f);
          assertFalse(props.containsKey(OMGraphicHandlerLayer.TransparencyProperty));
        });
  }

  @Test
  void localTilesAlsoPersistSliderChangesAfterReopeningControls() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final Properties props = new Properties();
          final TestLayer layer = new TestLayer();
          layer.setProperties("local.", props);
          layer.setTileFactory(new StandardMapTileFactory());
          layer.isServerReachable = true;
          components(layer.getGUI(), JSlider.class).get(0).setValue(204);
          final TestLayer reopened = new TestLayer();
          reopened.setProperties("local.", props);
          reopened.setTileFactory(new StandardMapTileFactory());
          reopened.isServerReachable = true;
          assertEquals(204, components(reopened.getGUI(), JSlider.class).get(0).getValue());
          assertEquals(0.8f, reopened.getTransparency(), 0.000001f);
        });
  }

  @Test
  void clearActionResetsMemoryAndOnlyDeletesThisLayersDiskCache() throws Exception {
    final String cacheName = directory.getFileName() + "/first";
    final Properties props = new Properties();
    props.setProperty("first.cacheDirName", cacheName);
    props.setProperty("second.cacheDirName", directory.getFileName() + "/second");
    final TestLayer first = new TestLayer();
    final TestLayer second = new TestLayer();
    first.setProperties("first", props);
    second.setProperties("second", props);
    final Path firstCache = Path.of(props.getProperty("first.localCacheRootDir"));
    final Path secondCache = Path.of(props.getProperty("second.localCacheRootDir"));
    // All disk effects are confined to JUnit's own temporary directory.
    assertTrue(firstCache.getParent().toRealPath().equals(directory.toRealPath()));
    Files.createDirectories(firstCache.resolve("1/2"));
    Files.writeString(firstCache.resolve("1/2/tile.png"), "first tile");
    Files.createDirectories(secondCache);
    final Path otherTile = Files.writeString(secondCache.resolve("tile.png"), "other tile");
    final RecordingFactory factory = new RecordingFactory();
    first.setTileFactory(factory);
    SwingUtilities.invokeAndWait(
        () -> {
          first.isServerReachable = true;
          components(first.getGUI(), JButton.class).get(0).doClick();
        });
    assertEquals(1, factory.resets);
    assertFalse(Files.exists(firstCache));
    assertEquals("other tile", Files.readString(otherTile));
    first.clearCache();
    assertEquals(2, factory.resets);
    assertTrue(Files.exists(otherTile));
  }

  @Test
  void missingTileServerDoesNotPrepareOrDownloadTiles() {
    final TestLayer layer = new TestLayer();
    layer.setProperties("offline", new Properties());
    final RecordingFactory factory = new RecordingFactory();
    layer.setTileFactory(factory);
    layer.setProjection(new Mercator(new LatLonPoint.Double(50, 4), 100000, 200, 200));
    assertNull(layer.prepare());
    assertNull(layer.getGUI());
    assertEquals(0, factory.resets);
    assertEquals(0, factory.requests);
    layer.isServerReachable = true;
    assertSame(factory.graphics, layer.prepare());
    assertEquals(1, factory.requests);
  }

  private static <T> List<T> components(Component root, Class<T> type) {
    final List<T> result = new ArrayList<>();
    if (type.isInstance(root)) {
      result.add(type.cast(root));
    }
    if (root instanceof Container) {
      for (Component child : ((Container) root).getComponents()) {
        result.addAll(components(child, type));
      }
    }
    return result;
  }

  private static final class TestLayer extends NodusMapTileLayer {
    private static final long serialVersionUID = 1L;

    @Override
    public void doPrepare() {
      // These tests call prepare() directly; factory setup must not start competing workers.
    }
  }

  private static final class RecordingFactory extends ServerMapTileFactory {
    int resets;
    int requests;
    final OMGraphicList graphics = new OMGraphicList();

    @Override
    public OMGraphicList getTiles(Projection projection, int zoom, OMGraphicList result) {
      requests++;
      return graphics;
    }

    @Override
    public void reset() {
      resets++;
    }
  }
}
