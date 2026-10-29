/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.uclouvain.core.nodus.NodusC;
import java.awt.image.BufferedImage;
import java.net.URL;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Guards startup and About images when the application version changes, without opening a window.
 */
class SplashResourceTest {
  @Test
  void currentReleaseSplashIsPackagedForBothStartupAndAbout() throws Exception {
    String name = "NodusSplash" + NodusC.VERSION.replace('.', '_') + ".png";
    URL resource = Splash.class.getResource(name);
    assertNotNull(resource, "Missing splash for Nodus " + NodusC.VERSION + ": " + name);
    assertEquals(resource, SplashDlg.class.getResource(name));
    BufferedImage image = ImageIO.read(resource);
    assertNotNull(image, "Splash resource must be a readable image");
    assertEquals(640, image.getWidth());
    assertEquals(320, image.getHeight());
  }
}
