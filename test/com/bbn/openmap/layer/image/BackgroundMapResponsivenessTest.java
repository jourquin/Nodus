/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package com.bbn.openmap.layer.image;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.BufferedMapBean;
import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.MouseDelegator;
import com.bbn.openmap.dataAccess.mapTile.ServerMapTileFactory;
import com.bbn.openmap.dataAccess.mapTile.StandardMapTileFactory;
import com.bbn.openmap.event.SelectMouseMode;
import com.bbn.openmap.gui.NodusLayersPanel;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.sun.net.httpserver.HttpServer;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;

/** Real layer replacement and painting must remain responsive during stalled map responses. */
@Timeout(20)
class BackgroundMapResponsivenessTest {
  @TestFactory
  Stream<DynamicTest> layerVisibilityRemainsResponsiveWhileImagesAreLoading() {
    return Stream.of("server tiles", "standard tiles", "WMS")
        .map(kind -> DynamicTest.dynamicTest(kind, () -> checkResponsiveness(kind)));
  }

  private void checkResponsiveness(String kind) throws Exception {
    boolean wms = kind.equals("WMS");
    try (SlowServer server = new SlowServer()) {
      TileLayer tiles = new TileLayer();
      WmsLayer maps = new WmsLayer();
      OMGraphicHandlerLayer background;
      if (wms) {
        Properties properties = new Properties();
        properties.setProperty("wms.wmsserver", server.url());
        properties.setProperty("wms.layers", "roads");
        maps.setProperties("wms", properties);
        assertTrue(maps.getCapabilities());
        background = maps;
      } else {
        tiles.isServerReachable = true;
        tiles.setTileFactory(
            kind.equals("standard tiles")
                ? new StandardMapTileFactory(tiles, server.url(), ".png")
                : new ServerMapTileFactory(server.url()));
        tiles.setZoomLevel(0);
        background = tiles;
      }
      OMGraphicHandlerLayer other = new OMGraphicHandlerLayer();
      MapBean map = new BufferedMapBean(false);
      LayerHandler handler = new LayerHandler();
      MouseDelegator mouse = new MouseDelegator();
      NodusLayersPanel[] panels = new NodusLayersPanel[1];
      JFrame[] frame = new JFrame[1];
      try {
        SwingUtilities.invokeAndWait(
            () -> {
              map.setSize(256, 256);
              map.setProjection(new Mercator(new LatLonPoint.Double(50, 4), 100000000, 256, 256));
              background.setName(wms ? "WMS" : "tiles");
              other.setName("overlay");
              mouse.setMap(map);
              mouse.addMouseMode(new SelectMouseMode());
              handler.addLayerListener(map);
              panels[0] = new NodusLayersPanel(null);
              panels[0].setLayerHandler(handler);
              handler.setLayers(new Layer[] {other, background});
              // Attach lightweight peers, as in a displayed map, so removing
              // layers exercises
              // Swing/AWT peer cleanup rather than only updating an offscreen
              // component list.
              map.addNotify();
              if (!GraphicsEnvironment.isHeadless()) {
                frame[0] = new JFrame("Nodus background-map responsiveness check");
                frame[0].add(map);
                frame[0].setSize(400, 400);
                frame[0].setVisible(true);
              }
              tiles.ready = true;
              maps.ready = true;
              background.doPrepare();
            });
        assertTrue(
            server.requested.await(5, TimeUnit.SECONDS), "Expected an actual HTTP image request");
        assertTrue(background.isWorking(), "The layer must still be fetching its image");
        FutureTask<Void> toggle =
            new FutureTask<>(
                () -> {
                  // Replacing layers also rebuilds MouseDelegator's layer listeners. This is
                  // the actual EDT path captured during the user's freeze, not a direct lock probe.
                  assertTrue(handler.turnLayerOn(false, other));
                  assertFalse(other.isVisible());
                  assertTrue(handler.turnLayerOn(true, other));
                  assertTrue(other.isVisible());
                  assertTrue(handler.turnLayerOn(false, background));
                  assertFalse(background.isVisible());
                  assertTrue(handler.turnLayerOn(true, background));
                  // Exercise the repaint path as well as accepting the checkbox
                  // action.
                  Graphics2D graphics =
                      new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB).createGraphics();
                  try {
                    map.paint(graphics);
                  } finally {
                    graphics.dispose();
                  }
                  return null;
                });
        SwingUtilities.invokeLater(toggle);
        try {
          toggle.get(1, TimeUnit.SECONDS);
          assertTrue(
              background.isWorking(), "The UI must respond before the HTTP response completes");
        } catch (TimeoutException blocked) {
          for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith("AWT-EventQueue")) {
              throw new AssertionError(
                  "UI blocked while fetching "
                      + (wms ? "WMS" : "tiles")
                      + ": "
                      + List.of(thread.getStackTrace()),
                  blocked);
            }
          }
          throw blocked;
        } finally {
          server.release.countDown();
        }
      } finally {
        server.release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (background.isWorking() && System.nanoTime() < deadline) {
          SwingUtilities.invokeAndWait(() -> {});
          Thread.sleep(10);
        }
        assertFalse(background.isWorking(), "Background request did not finish");
        SwingUtilities.invokeAndWait(
            () -> {
              handler.removeLayerListener(map);
              mouse.setMap(null);
              panels[0].setLayerHandler(null);
              map.removeNotify();
              map.dispose();
              background.dispose();
              other.dispose();
              if (frame[0] != null) {
                frame[0].dispose();
              }
            });
      }
    }
  }

  private static final class TileLayer extends NodusMapTileLayer {
    private static final long serialVersionUID = 1L;
    boolean ready;

    @Override
    public void doPrepare() {
      if (ready) {
        super.doPrepare();
      }
    }
  }

  private static final class WmsLayer extends NodusWMSLayer {
    private static final long serialVersionUID = 1L;
    boolean ready;

    @Override
    public void doPrepare() {
      if (ready) {
        super.doPrepare();
      }
    }
  }

  private static final class SlowServer implements AutoCloseable {
    final HttpServer server;
    final CountDownLatch requested = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    SlowServer() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", png);
      String capabilities =
          Files.readString(Path.of("test/fixtures/wms/capabilities-1.1.1.xml"))
              .replace("https://maps.example.test/wms?token=a&amp;mode=b", url());
      server.createContext(
          "/",
          exchange -> {
            boolean metadata =
                String.valueOf(exchange.getRequestURI().getQuery()).contains("GetCapabilities");
            byte[] bytes =
                metadata
                    ? capabilities.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : png.toByteArray();
            if (!metadata) {
              requested.countDown();
              try {
                release.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
              }
            }
            try {
              exchange
                  .getResponseHeaders()
                  .set("Content-Type", metadata ? "text/xml" : "image/png");
              exchange.sendResponseHeaders(200, bytes.length);
              try (var output = exchange.getResponseBody()) {
                output.write(bytes);
              }
            } finally {
              exchange.close();
            }
          });
      server.start();
    }

    String url() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/wms";
    }

    @Override
    public void close() {
      release.countDown();
      server.stop(0);
    }
  }
}
