/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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

package com.bbn.openmap.layer;

import com.bbn.openmap.Environment;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMLine;
import com.bbn.openmap.omGraphics.OMRaster;
import com.bbn.openmap.omGraphics.OMText;
import com.bbn.openmap.proj.GreatCircle;
import com.bbn.openmap.proj.Length;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Paint;
import java.util.Properties;
import java.util.StringTokenizer;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/**
 * This class is intended to display a scale, which is often required when a map is published. It
 * can also display a copyright (or any other text), a wind rose and the content of an external int
 * upperY = h + locationYoffset - height; image.
 *
 * <p>Example properties:
 *
 * <p>scaleLayer.class=com.bbn.openmap.layer.ScaleAndCompassLayer<br>
 * scaleLayer.prettyName=Scale<br>
 * scaleLayer.scaleLineColor=ffCC0033<br>
 * scaleLayer.scaleTextColor=ffCC0033<br>
 * scaleLayer.copyrightTextColor=ff000000<br>
 * scaleLayer.scaleIntervals=1 2 5 10 20 50 100 250 500 1000 2000 5000 10000<br>
 * scaleLayer.copyright=(c) Nodus 8.0<br>
 * scaleLayer.image=/home/jourquin/data/nodus6/test/cartouche.gif<br>
 */
public class ScaleAndCompassLayer extends OMGraphicHandlerLayer {

  /** Properties key : Copyright text to display (can also be a simple legend). */
  public static final String CopyrightProperty = "copyright";

  /** Properties key : Copyright (or label) text color ("FFFFFF" by default). */
  public static final String CopyrightTextColorProperty = "copyrightTextColor";

  /** Properties key : Copyright (or label) Font. */
  public static final String CopyrightTextFontProperty = "copyrightTextFont";

  /** Properties key : "true" (default) or "false". Displays an wind rose on the map. */
  public static final String DisplayWindRoseProperty = "displayWindRose";

  /** Properties key : Complete path to an image (a .gif for instance) to display. */
  public static final String imageProperty = "image";

  /**
   * Properties key : A series of space separated integer values that specify the successive scales
   * to display when zooming.<br>
   * Set to "1, 2, 5, 10, 20, 50, 100, 250, 500, 1000, 2000, 5000, 10000" by default.
   */
  public static final String ScaleIntervalsProperty = "scaleIntervals";

  /** Properties key : Scale line color ("FFFFFF" by default). */
  public static final String ScaleLineColorProperty = "scaleLineColor";

  /** Properties key : Scale text color ("FFFFFF" by default). */
  public static final String ScaleTextColorProperty = "scaleTextColor";

  /** Properties key : Scale text Font. */
  public static final String ScaleTextFontProperty = "scaleTextFont";

  /** Properties key : Unit used for distances : "km" (default) , "m", "nm" or "miles". */
  public static final String UnitOfMeasureProperty = "unitOfMeasure";

  private static final long serialVersionUID = -6244048548566584121L;

  private static I18n i18n = Environment.getI18n();

  /** . */
  private String copyright = null;

  /** . */
  private OMText copyrightText;

  /** . */
  private Color copyrightTextColor = null;

  /** . */
  private String defaultLineColorString = "FFFFFF";

  /** . */
  private String defaultTextColorString = "FFFFFF";

  /** . */
  private String defaultUnitOfMeasureString = "km";

  /** . */
  private boolean displayWindRose = true;

  /** . */
  private int height = 10;

  /** . */
  private OMRaster imageRaster;

  /** . */
  private JPanel panel1;

  /** . */
  private JPanel panel2;

  /** . */
  private JPanel panel3;

  /** . */
  private JRadioButton kmRadioButton;

  /** . */
  private int locationXoffset = -10;

  /** . */
  private int locationYoffset = -10;

  /** . */
  private JRadioButton meterRadioButton;

  /** . */
  private JRadioButton mileRadioButton;

  /** . */
  private JRadioButton nmRadioButton;

  /** Intervals to display. */
  private int[] scaleIntervals =
      new int[] {1, 2, 5, 10, 20, 50, 100, 250, 500, 1000, 2000, 5000, 10000};

  /** . */
  private Color scaleLineColor = null;

  /** . */
  private Color scaleTextColor = null;

  /** . */
  private String unitOfMeasureString = null;

  /** . */
  private Length unitOfMeasure = Length.get(defaultUnitOfMeasureString);

  /** . */
  private String unitOfMeasureAbbreviation = unitOfMeasure.getAbbr();

  /** . */
  private ButtonGroup uomButtonGroup;

  /** . */
  private int width = 150;

  /** . */
  private OMRaster windRoseRaster;

  /** . */
  private final Font defaultFont = new JLabel().getFont();

  /** . */
  private Font copyrightTextFont = defaultFont;

  /** . */
  private Font scaleTextFont = defaultFont;

  /**
   * Creates the GUI.
   *
   * @return Component
   */
  @Override
  public Component getGUI() {

    if (palette == null) {

      palette = javax.swing.Box.createVerticalBox();
      uomButtonGroup = new ButtonGroup();
      panel1 = new JPanel();
      panel2 = new JPanel();
      panel3 = new JPanel();
      kmRadioButton = new JRadioButton();
      meterRadioButton = new JRadioButton();
      nmRadioButton = new JRadioButton();
      mileRadioButton = new JRadioButton();

      panel1.setLayout(new javax.swing.BoxLayout(panel1, javax.swing.BoxLayout.Y_AXIS));

      kmRadioButton.setText(i18n.get(ScaleAndCompassLayer.class, "KM", "KM"));

      kmRadioButton.setToolTipText(
          i18n.get(ScaleAndCompassLayer.class, "Kilometers", "Kilometers"));

      uomButtonGroup.add(kmRadioButton);
      panel3.add(kmRadioButton);

      meterRadioButton.setText(i18n.get(ScaleAndCompassLayer.class, "M", "M"));

      meterRadioButton.setToolTipText(i18n.get(ScaleAndCompassLayer.class, "Meters", "Meters"));

      uomButtonGroup.add(meterRadioButton);
      panel3.add(meterRadioButton);

      nmRadioButton.setText(i18n.get(ScaleAndCompassLayer.class, "NM", "NM"));

      nmRadioButton.setToolTipText(
          i18n.get(ScaleAndCompassLayer.class, "Nautical_Miles", "Nautical Miles"));

      uomButtonGroup.add(nmRadioButton);
      panel3.add(nmRadioButton);

      mileRadioButton.setText(i18n.get(ScaleAndCompassLayer.class, "Mile", "Mile"));

      mileRadioButton.setToolTipText(
          i18n.get(ScaleAndCompassLayer.class, "Statute_Miles", "Statute Miles"));

      uomButtonGroup.add(mileRadioButton);
      panel3.add(mileRadioButton);

      panel2.add(panel3);

      panel1.add(panel2);

      palette.add(panel1);

      java.awt.event.ActionListener al =
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              String ac = e.getActionCommand();

              if (ac.equalsIgnoreCase(UnitOfMeasureProperty)) {
                JRadioButton jrb = (JRadioButton) e.getSource();
                setUnitOfMeasure(jrb.getText());
                doPrepare();
              }
            }
          };

      kmRadioButton.addActionListener(al);
      kmRadioButton.setActionCommand(UnitOfMeasureProperty);
      meterRadioButton.addActionListener(al);
      meterRadioButton.setActionCommand(UnitOfMeasureProperty);
      nmRadioButton.addActionListener(al);
      nmRadioButton.setActionCommand(UnitOfMeasureProperty);
      mileRadioButton.addActionListener(al);
      mileRadioButton.setActionCommand(UnitOfMeasureProperty);
    }
    if (unitOfMeasureString.equalsIgnoreCase("km")) {
      kmRadioButton.setSelected(true);
    } else if (unitOfMeasureString.equalsIgnoreCase("m")) {
      meterRadioButton.setSelected(true);
    } else if (unitOfMeasureString.equalsIgnoreCase("nm")) {
      nmRadioButton.setSelected(true);
    } else if (unitOfMeasureString.equalsIgnoreCase("mile")) {
      mileRadioButton.setSelected(true);
    }
    return palette;
  }

  /**
   * Prepares the rendering of all the information to display.
   *
   * @return OMGraphicList
   */
  @Override
  public synchronized OMGraphicList prepare() {

    Projection projection = getProjection();
    int w = projection.getWidth();
    int h = projection.getHeight();

    int currentWidth = width;

    int lowerY = h + locationYoffset;
    int leftX = w + locationXoffset - currentWidth;
    int rightX = w + locationXoffset;

    LatLonPoint.Double loc1 = projection.inverse(leftX, lowerY);
    LatLonPoint.Double loc2 = projection.inverse(rightX, lowerY);

    double dist =
        GreatCircle.sphericalDistance(
            loc1.getRadLat(), loc1.getRadLon(), loc2.getRadLat(), loc2.getRadLon());
    dist = unitOfMeasure.fromRadians(dist);

    // Find the relevant scale
    int scaleIndex = 0;
    for (int element : scaleIntervals) {
      if (element > dist) {
        break;
      } else {
        scaleIndex++;
      }
    }

    // Adjust ruler to scale
    while (dist < scaleIntervals[scaleIndex]) {
      currentWidth++;

      leftX = w + locationXoffset - currentWidth;
      rightX = w + locationXoffset;

      loc1 = projection.inverse(leftX, lowerY);
      loc2 = projection.inverse(rightX, lowerY);

      dist =
          GreatCircle.sphericalDistance(
              loc1.getRadLat(), loc1.getRadLon(), loc2.getRadLat(), loc2.getRadLon());
      dist = unitOfMeasure.fromRadians(dist);
    }

    OMGraphicList graphics = new OMGraphicList();
    graphics.clear();

    int lineWidth = 2;
    BasicStroke bs = new BasicStroke(lineWidth);

    OMLine line = new OMLine(leftX, lowerY, rightX, lowerY);
    line.setStroke(bs);
    line.setLinePaint(scaleLineColor);
    graphics.add(line);

    int upperY = h + locationYoffset - height;
    line = new OMLine(leftX, lowerY, leftX, upperY);
    line.setStroke(bs);
    line.setLinePaint(scaleLineColor);
    graphics.add(line);

    line = new OMLine(rightX, lowerY, rightX, upperY);
    line.setStroke(bs);
    line.setLinePaint(scaleLineColor);
    graphics.add(line);

    String proj = getProjection().getName();
    if (proj.equalsIgnoreCase("LLXY")) {
      proj = "WGS 84";
    }
    String outtext =
        scaleIntervals[scaleIndex] + " " + unitOfMeasureAbbreviation + " (" + proj + ")";

    OMText text = new OMText((leftX + rightX) / 2, lowerY - 3, "" + outtext, OMText.JUSTIFY_CENTER);
    text.setFont(scaleTextFont);
    text.setLinePaint(scaleTextColor);
    graphics.add(text);

    // Display compass
    if (displayWindRose) {
      graphics.add(windRoseRaster);
    }

    // Copyright notice
    if (copyright != null) {
      copyrightText.setX(-locationXoffset);
      copyrightText.setY(h + locationYoffset);
      graphics.add(copyrightText);
    }

    // Legend
    if (imageRaster != null) {
      imageRaster.setX(w - imageRaster.getWidth() + locationXoffset);
      imageRaster.setY(-locationYoffset);
      graphics.add(imageRaster);
    }

    graphics.generate(projection);

    return graphics;
  }

  /**
   * Parses the properties, like any other OpenMap layer.
   *
   * @param prefix The layer prefix in the properties
   * @param properties A Properties object that contains the parameters for this lauer.
   */
  @Override
  public synchronized void setProperties(String prefix, Properties properties) {
    super.setProperties(prefix, properties);
    prefix = com.bbn.openmap.util.PropUtils.getScopedPropertyPrefix(prefix);

    try {
      scaleLineColor =
          PropUtils.parseColorFromProperties(
              properties, prefix + ScaleLineColorProperty, defaultLineColorString);

      scaleTextColor =
          PropUtils.parseColorFromProperties(
              properties, prefix + ScaleTextColorProperty, defaultTextColorString);

      copyrightTextColor =
          PropUtils.parseColorFromProperties(
              properties, prefix + CopyrightTextColorProperty, defaultTextColorString);

    } catch (NumberFormatException ex) {
      ex.printStackTrace();
    }

    // Fonts
    String f = properties.getProperty(prefix + CopyrightTextFontProperty, null);
    if (f != null) {
      Font font = Font.decode(f);
      if (font != null) {
        copyrightTextFont = font;
      }
    }

    f = properties.getProperty(prefix + ScaleTextFontProperty, null);
    if (f != null) {
      Font font = Font.decode(f);
      if (font != null) {
        scaleTextFont = font;
      }
    }

    String unitOfMeasure = properties.getProperty(prefix + UnitOfMeasureProperty);
    setUnitOfMeasure(unitOfMeasure);

    // Must the wind rose be displayed ?
    displayWindRose =
        PropUtils.booleanFromProperties(properties, prefix + DisplayWindRoseProperty, true);
    if (displayWindRose) {
      // Load wind rose, with no border
      ImageIcon windRose = new ImageIcon(getClass().getResource("windrose.png"));
      windRoseRaster = new OMRaster(-locationXoffset, -locationYoffset, windRose);
      Paint p = new Color(0, 0, 0, 0);
      windRoseRaster.setLinePaint(p);
    }

    // Get the copyright of the map
    copyright = properties.getProperty(prefix + CopyrightProperty, null);

    if (copyright != null) {
      copyrightText = new OMText(0, 0, copyright, OMText.JUSTIFY_LEFT);
      copyrightText.setLinePaint(copyrightTextColor);
      copyrightText.setFont(copyrightTextFont);
    }

    // Image to display ?
    String l = properties.getProperty(prefix + imageProperty, null);
    imageRaster = null;

    if (l != null) {
      ImageIcon image = new ImageIcon(l);

      if (image.getIconWidth() != -1) {
        imageRaster = new OMRaster(0, 0, image);
      }
    }

    // Get the intervals for the scale
    String ranges = properties.getProperty(prefix + ScaleIntervalsProperty, null);
    if (ranges != null) {
      StringTokenizer st = new StringTokenizer(ranges);
      int[] r = new int[st.countTokens()];
      int i = 0;
      boolean error = false;
      while (st.hasMoreTokens()) {
        try {
          int k = Integer.parseInt(st.nextToken());

          // Interval must have increasing int values
          if (i > 0 && k <= r[i - 1]) {
            error = true;
            break;
          }
          r[i++] = k;
        } catch (NumberFormatException e) {

          error = true;
        }
      }
      if (!error) {
        scaleIntervals = r;
      } else {
        System.err.println("Invalid scale interval");
      }
    }
  }

  /**
   * Setter for property unitOfMeasure.
   *
   * @param unit New value of property unitOfMeasure.
   */
  private void setUnitOfMeasure(String unit) {
    if (unit == null) {
      unit = Length.KM.toString();
    }

    this.unitOfMeasureString = unit;
    this.unitOfMeasure = Length.get(unit);

    // If no unit of measure is found, assign Kilometers as the default.
    if (this.unitOfMeasure == null) {
      this.unitOfMeasure = Length.KM;
    }

    unitOfMeasureAbbreviation = this.unitOfMeasure.getAbbr();
  }
}
