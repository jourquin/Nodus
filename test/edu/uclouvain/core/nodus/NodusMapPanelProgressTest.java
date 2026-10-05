/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.InformationDelegator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JProgressBar;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real OpenMap progress widget without starting the application or opening a window.
 */
class NodusMapPanelProgressTest {
  private NodusMapPanel panel;
  private JProgressBar bar;
  private LookAndFeel previousLookAndFeel;
  private Class<?> nativeProgressUI;

  @BeforeEach
  void createPanel() throws Exception {
    previousLookAndFeel = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    SwingUtilities.invokeAndWait(
        () ->
            panel =
                new NodusMapPanel() {
                  @Override
                  public void setBusy(boolean busy) {
                    // No root frame or glass pane in this headless fixture.
                  }

                  @Override
                  public void resetText() {
                    // No project status text in this headless fixture.
                  }
                });
    // Inspect the actual widget without exposing a UI implementation detail in the public API.
    Field field = NodusMapPanel.class.getDeclaredField("infoDelegator");
    field.setAccessible(true);
    bar = ((InformationDelegator) field.get(panel)).getProgressBar();
    nativeProgressUI = bar.getUI().getClass();
  }

  @AfterEach
  void stopAnimation() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          panel.stopProgress();
          if (bar.isDisplayable()) {
            bar.removeNotify();
          }
        });
    UIManager.setLookAndFeel(previousLookAndFeel);
  }

  @Test
  void activityBarActuallyPaintsMovingFrames() throws Exception {
    CountDownLatch changed = new CountDownLatch(1);
    Timer[] sampler = new Timer[1];
    SwingUtilities.invokeAndWait(
        () -> {
          bar.setSize(180, 24);
          // A displayable peer activates Swing's animation timer without opening a window.
          bar.addNotify();
          panel.startProgress(0);
          int[] first = paintBar();
          assertTrue(Arrays.stream(first).anyMatch(pixel -> pixel != Color.WHITE.getRGB()));
          sampler[0] =
              new Timer(
                  50,
                  event -> {
                    if (!Arrays.equals(first, paintBar())) {
                      changed.countDown();
                    }
                  });
          sampler[0].start();
        });
    try {
      assertTrue(
          changed.await(5, TimeUnit.SECONDS), "Activity must visibly move, not just be flagged");
    } finally {
      SwingUtilities.invokeAndWait(() -> sampler[0].stop());
    }
  }

  /** Renders the real progress widget, including its current animation frame. */
  private int[] paintBar() {
    BufferedImage image =
        new BufferedImage(bar.getWidth(), bar.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    bar.paint(graphics);
    graphics.dispose();
    return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
  }

  @Test
  void repeatedFittingChecksNeverFillAnIndeterminateBar() throws Exception {
    // Called off the EDT, as during parameter estimation in SwingWorker.
    panel.startProgress(0);
    for (int check = 0; check < 10000; check++) {
      assertTrue(panel.updateProgress("Estimating MNP for group 1"));
    }
    SwingUtilities.invokeAndWait(
        () -> {
          assertTrue(bar.isVisible());
          assertTrue(bar.isIndeterminate());
          assertEquals(0, bar.getValue());
          assertEquals("", bar.getString());
        });
    panel.stopProgress();
    SwingUtilities.invokeAndWait(
        () -> {
          assertFalse(bar.isVisible());
          assertFalse(bar.isIndeterminate());
        });
  }

  @Test
  void progressModelChangesRunOnSwingThread() throws Exception {
    AtomicBoolean changedOffSwingThread = new AtomicBoolean();
    AtomicInteger changes = new AtomicInteger();
    SwingUtilities.invokeAndWait(
        () ->
            bar.addChangeListener(
                event -> {
                  changes.incrementAndGet();
                  if (!SwingUtilities.isEventDispatchThread()) {
                    changedOffSwingThread.set(true);
                  }
                }));

    panel.startProgress(10);
    for (int step = 0; step < 10; step++) {
      panel.updateProgress("Routing");
    }
    panel.stopProgress();
    SwingUtilities.invokeAndWait(
        () -> {
          assertFalse(changedOffSwingThread.get(), "Progress model must only change on the EDT");
          assertEquals(10, changes.get());
          assertEquals(100, bar.getValue());
          assertFalse(bar.isVisible());
        });
  }

  @Test
  void subsequentTasksRestoreDeterminateProgressAndFinishNormally() throws Exception {
    panel.startProgress(0);
    panel.updateProgress("Preparing observations");
    panel.stopProgress();
    panel.startProgress(10);
    for (int step = 0; step < 4; step++) {
      panel.updateProgress("Routing");
    }
    SwingUtilities.invokeAndWait(
        () -> {
          assertFalse(bar.isIndeterminate());
          assertTrue(bar.isVisible());
          assertEquals(nativeProgressUI, bar.getUI().getClass());
          assertEquals(40, bar.getValue());
        });
    for (int step = 4; step < 10; step++) {
      panel.updateProgress("Routing");
    }
    SwingUtilities.invokeAndWait(() -> assertEquals(100, bar.getValue()));
  }
}
