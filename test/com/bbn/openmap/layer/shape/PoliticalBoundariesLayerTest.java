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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.io.BinaryFile;
import com.bbn.openmap.io.URLInputReader;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.coords.LatLonPoint;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Exercises removal while OpenMap is reading a compressed, installed boundary shapefile. */
@ResourceLock(Resources.SYSTEM_ERR)
@Timeout(20)
class PoliticalBoundariesLayerTest {
  @TempDir Path directory;

  @Test
  void removalKeepsEdtResponsiveDuringJarRenderingAndLayerCanBeRenderedAgain() throws Exception {
    Path jar = directory.resolve("boundaries with spaces.jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      for (String extension : new String[] {"shp", "ssx"}) {
        String name = "cntry02." + extension;
        output.putNextEntry(new JarEntry(name));
        try (InputStream input = PoliticalBoundariesLayer.class.getResourceAsStream(name)) {
          assertNotNull(input);
          input.transferTo(output);
        }
        output.closeEntry();
      }
    }
    String shape = "jar:" + jar.toUri().toURL() + "!/cntry02.shp";
    ReadGate gate = new ReadGate();
    SpatialIndex index =
        new SpatialIndex(shape) {
          @Override
          protected synchronized BinaryFile getShpFile(String name) throws IOException {
            BinaryFile file = new BinaryFile(name);
            file.close();
            file.setInputReader(new PausingReader(new URL(name), gate));
            return file;
          }
        };
    PoliticalBoundariesLayer layer = new PoliticalBoundariesLayer();
    layer.setSpatialIndex(index);
    layer.setProjection(new Mercator(new LatLonPoint.Double(50, 4), 1000000, 800, 600));
    OMGraphicList previousGraphics = layer.prepare();
    int expected = previousGraphics.size();
    layer.setList(previousGraphics);
    assertTrue(expected > 1, "The viewport must contain actual boundary graphics");
    var workers = Executors.newSingleThreadExecutor();
    ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
    PrintStream previousError = System.err;
    try (PrintStream errors = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
      System.setErr(errors);
      try {
        gate.armed.set(true);
        final Future<OMGraphicList> rendering = workers.submit(layer::prepare);
        assertTrue(
            gate.reading.await(5, TimeUnit.SECONDS), "Render must reach the shapefile stream");
        final int closedBeforeRemoval = gate.closed.get();
        CountDownLatch removalReturned = new CountDownLatch(1);
        CountDownLatch nextUiEvent = new CountDownLatch(1);
        AtomicReference<Throwable> removalFailure = new AtomicReference<>();
        // MapBean can notify removal while AWT holds the component tree lock. Never wait for
        // prepare() here: the worker may itself need AWT while generating its graphics.
        SwingUtilities.invokeLater(
            () -> {
              try {
                synchronized (layer.getTreeLock()) {
                  layer.removed(null);
                }
              } catch (Throwable failure) {
                removalFailure.set(failure);
              } finally {
                removalReturned.countDown();
              }
            });
        SwingUtilities.invokeLater(nextUiEvent::countDown);
        assertTrue(
            removalReturned.await(2, TimeUnit.SECONDS),
            "Layer removal must return on the EDT while a shapefile read is paused");
        assertTrue(
            nextUiEvent.await(2, TimeUnit.SECONDS), "The next UI event must remain runnable");
        assertNull(removalFailure.get());
        assertNull(layer.getList(), "Removal must detach the displayed graphics");
        assertEquals(expected, previousGraphics.size(), "Removal must not mutate shared graphics");
        assertEquals(1, gate.resume.getCount(), "The render is still paused");
        assertEquals(
            closedBeforeRemoval, gate.closed.get(), "Removal must not close the active stream");
        gate.resume.countDown();
        OMGraphicList rendered = rendering.get(5, TimeUnit.SECONDS);
        assertTrue(
            gate.closed.get() > closedBeforeRemoval, "The renderer must close its own stream");
        assertEquals("", diagnostics.toString(StandardCharsets.UTF_8));
        assertEquals(expected, rendered.size(), "Removal must not truncate the render");
        // The singleton boundary layer is reused when opening projects and toggling its visibility.
        for (int reopen = 0; reopen < 3; reopen++) {
          assertEquals(expected, layer.prepare().size());
          layer.removed(null);
        }
        assertEquals("", diagnostics.toString(StandardCharsets.UTF_8));
      } finally {
        gate.resume.countDown();
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        System.setErr(previousError);
        SwingUtilities.invokeAndWait(() -> {});
        index.close(true);
        layer.dispose();
      }
    }
  }

  private static final class ReadGate {
    final AtomicBoolean armed = new AtomicBoolean();
    final AtomicInteger closed = new AtomicInteger();
    final CountDownLatch reading = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);
  }

  /** Pauses inside the actual compressed stream, where a concurrent close used to fail. */
  private static final class PausingReader extends URLInputReader {
    private ReadGate gate;

    PausingReader(URL url, ReadGate gate) throws IOException {
      super(url);
      this.gate = gate;
      wrapStream();
    }

    @Override
    public void reopen() throws IOException {
      super.reopen();
      if (gate != null) {
        wrapStream();
      }
    }

    private void wrapStream() {
      inputStream =
          new FilterInputStream(inputStream) {
            @Override
            public void close() throws IOException {
              gate.closed.incrementAndGet();
              super.close();
            }

            @Override
            public long skip(long count) throws IOException {
              if (gate.armed.compareAndSet(true, false)) {
                gate.reading.countDown();
                try {
                  if (!gate.resume.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for concurrent removal");
                  }
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                  throw new IOException(ex);
                }
              }
              return super.skip(count);
            }
          };
    }
  }
}
