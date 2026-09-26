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

package com.bbn.openmap.omGraphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Scoped style settings and fallback colors used by Nodus layers. */
class NodusDrawingAttributesTest {
  @Test
  void scopedPropertiesLoadIndependentPositiveNegativeAndDefaultColors() {
    Properties properties = new Properties();
    properties.setProperty("roads.radius", "8");
    properties.setProperty("roads.oval", "true");
    properties.setProperty("roads.lineColor", "ff112233");
    properties.setProperty("roads.fillColor", "ff445566");
    properties.setProperty("roads.defaultLineColor", "ff778899");
    properties.setProperty("roads.altLineColor", "ffaabbcc");
    properties.setProperty("roads.altFillColor", "ffddee00");
    properties.setProperty("roads.altMattingColor", "ff123456");
    properties.setProperty("other.radius", "99");
    final NodusDrawingAttributes style = new NodusDrawingAttributes();
    style.setProperties("roads", properties);
    assertEquals(8, style.getRadius());
    assertTrue(style.getOval());
    assertEquals(new Color(0x112233), style.getLinePaint());
    assertEquals(new Color(0x445566), style.getFillPaint());
    assertEquals(new Color(0x778899), style.getDefaultLinePaint());
    assertEquals(new Color(0xaabbcc), style.getAltLinePaint());
    assertEquals(new Color(0xddee00), style.getAltFillPaint());
    assertEquals(new Color(0x123456), style.getAltMattingPaint());
  }

  @Test
  void missingAlternativeColorsUseTheConfiguredBaseColors() {
    Properties properties = new Properties();
    properties.setProperty("roads.lineColor", "ff112233");
    properties.setProperty("roads.fillColor", "ff445566");
    final NodusDrawingAttributes style = new NodusDrawingAttributes();
    style.setProperties("roads.", properties);
    assertEquals(2, style.getRadius());
    assertFalse(style.getOval());
    assertEquals(new Color(0x112233), style.getDefaultLinePaint());
    assertEquals(new Color(0x112233), style.getAltLinePaint());
    assertEquals(new Color(0x112233), style.getAltMattingPaint());
    assertEquals(new Color(0x445566), style.getAltFillPaint());
  }

  @Test
  void reloadingPropertiesReplacesPreviousAlternativeColorsAndShapeSettings() {
    final NodusDrawingAttributes style = new NodusDrawingAttributes();
    Properties first = new Properties();
    first.setProperty("roads.radius", "9");
    first.setProperty("roads.oval", "true");
    first.setProperty("roads.altLineColor", "ffff0000");
    style.setProperties("roads", first);
    Properties second = new Properties();
    second.setProperty("roads.lineColor", "ff0000ff");
    style.setProperties("roads", second);
    assertEquals(2, style.getRadius());
    assertFalse(style.getOval());
    assertEquals(Color.BLUE, style.getAltLinePaint());
    assertEquals(Color.BLUE, style.getDefaultLinePaint());
  }
}
