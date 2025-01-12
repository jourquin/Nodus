/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

import com.bbn.openmap.util.PropUtils;
import java.awt.Color;
import java.awt.Paint;
import java.util.Properties;

/**
 * Adds the a "radius" and "oval" property to the basic DrawingAttributes. Also adds alternative
 * LinePaint, MattingPaint and FillPaint properties. These are used in Nodus to differentiate
 * rendering for positve and negative values obtained as a result of a computation. This is for
 * instance the case when two scenarios are compared.
 *
 * @author Bart Jourquin
 */
public class NodusDrawingAttributes extends DrawingAttributes {

  private static final long serialVersionUID = 499657457412708371L;

  /** The name of the property that holds the "alternative" fill color. */
  public static final String DEFAULT_LINE_PAINT_PROPERTY = "defaultLineColor";

  /** The name of the property that holds the "alternative" fill color. */
  public static final String ALT_FILL_PAINT_PROPERTY = "altFillColor";

  /** The name of the property that holds the "alternative" line color. */
  public static final String ALT_LINE_PAINT_PROPERTY = "altLineColor";

  /** The name of the property that holds the "alternative" matting color. */
  public static final String ALT_MATTING_PAINT_PROPERTY = "altMattingColor";

  /** The default oval state. */
  public static final boolean DEFAULT_OVAL = false;

  /** The default radius. */
  public static final int DEFAULT_RADIUS = 2;

  /** The name of the property that holds the oval state of the graphics. */
  public static final String OVAL_PROPERTY = "oval";

  /** The name of the property that holds the radius of the graphics. */
  public static final String RADIUS_PROPERTY = "radius";

  /** Default line color. */
  Paint defaultLinePaint = Color.black;

  /** Default constructor. */
  public NodusDrawingAttributes() {
    super();
  }

  /**
   * Returns the default color used when a graphic is not rendered.
   *
   * @return The default paint.
   */
  public Paint getDefaultLinePaint() {
    return defaultLinePaint;
  }

  /**
   * Sets the line color when a graphic must not be rendered.
   *
   * @param defaultLinePaint The default Paint to use.
   */
  public void setDefaultLinePaint(Paint defaultLinePaint) {
    this.defaultLinePaint = defaultLinePaint;
  }

  /** Default "alternative" fill color. */
  Paint altFillPaint = Color.lightGray;

  /** Default "alternative" line color. */
  Paint altLinePaint = Color.lightGray;

  /** Default "alternative" matting color. */
  Paint altMattingPaint = Color.lightGray;

  /** . */
  private boolean oval = DEFAULT_OVAL;

  /** . */
  private int radius = DEFAULT_RADIUS;

  /**
   * Returns the "alternative" fill paint color of the graphic.
   *
   * @return Paint
   */
  public Paint getAltFillPaint() {
    return altFillPaint;
  }

  /**
   * Returns the "alternative" paint color of the line.
   *
   * @return Paint
   */
  public Paint getAltLinePaint() {
    return altLinePaint;
  }

  /**
   * Returns the "alternative" matting paint color of the graphic.
   *
   * @return Paint
   */
  public Paint getAltMattingPaint() {
    return altMattingPaint;
  }

  /**
   * Returns the oval state of the drawing attribute.
   *
   * @return boolean
   */
  public boolean getOval() {
    return oval;
  }

  /**
   * Returns the radius drawing attribute.
   *
   * @return int
   */
  public int getRadius() {
    return radius;
  }

  /**
   * Set the "alternative" fill paint color of the graphic.
   *
   * @param paintColor Paint
   */
  public void setAltFillPaint(Paint paintColor) {
    altFillPaint = paintColor;
  }

  /**
   * Set the "alternative" color for the line.
   *
   * @param paintColor Paint
   */
  public void setAltLinePaint(Paint paintColor) {
    altLinePaint = paintColor;
  }

  /**
   * Set the "alternative" matting paint color of the graphic.
   *
   * @param paintColor Paint
   */
  public void setAltMattingPaint(Paint paintColor) {
    altMattingPaint = paintColor;
  }

  /**
   * Set the oval state of the drawing attribute.
   *
   * @param oval boolean
   */
  public void setOval(boolean oval) {
    this.oval = oval;
  }

  /**
   * Stores the alternative drawing attributes, radius and oval state in the properties file.
   *
   * @param prefix String
   * @param props Properties
   */
  @Override
  public void setProperties(String prefix, Properties props) {
    super.setProperties(prefix, props);

    if (props == null) {
      return;
    }

    String realPrefix = PropUtils.getScopedPropertyPrefix(prefix);
    radius = PropUtils.intFromProperties(props, realPrefix + RADIUS_PROPERTY, DEFAULT_RADIUS);

    oval = PropUtils.booleanFromProperties(props, realPrefix + OVAL_PROPERTY, DEFAULT_OVAL);

    // Get Default line color
    defaultLinePaint =
        PropUtils.parseColorFromProperties(
            props, realPrefix + DEFAULT_LINE_PAINT_PROPERTY, getLinePaint());

    // Get alternate colors
    altLinePaint =
        PropUtils.parseColorFromProperties(
            props, realPrefix + ALT_LINE_PAINT_PROPERTY, getLinePaint());
    altMattingPaint =
        PropUtils.parseColorFromProperties(
            props, realPrefix + ALT_MATTING_PAINT_PROPERTY, getLinePaint());
    altFillPaint =
        PropUtils.parseColorFromProperties(
            props, realPrefix + ALT_FILL_PAINT_PROPERTY, getFillPaint());
  }

  /**
   * Sets the value of the radius of the drawing attribute.
   *
   * @param radius int
   */
  public void setRadius(int radius) {
    this.radius = radius;
  }
}
