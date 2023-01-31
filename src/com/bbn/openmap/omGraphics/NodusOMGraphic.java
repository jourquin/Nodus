/*
 * Copyright (c) 1991-2023 Université catholique de Louvain
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

import com.bbn.openmap.proj.Projection;
import java.awt.Color;
import java.awt.Paint;

/**
 * Extends OMGraphics with oval and radius attributes, as well as "alternative" painting attributes
 * used to render negative values. This feature is for instance useful when two scenarios are
 * compared and that differences in volumes on the links (can be positive or negative) are to be
 * displayed. The NodusOMGraphic is not intended to be rendered, but is only a placeholder for the
 * graphic attributes loaded from "shape.properties".
 *
 * @author Bart Jourquin
 */
public class NodusOMGraphic extends OMGraphicAdapter {

  private static final boolean DEFAULT_ISOVAL = false;

  private static final int DEFAULT_RADIUS = 2;

  private static final long serialVersionUID = -6793527844340119715L;

  /** . */
  private Paint altFillPaint = clear;

  /** . */
  private Paint altLinePaint = Color.lightGray;

  /** . */
  private Paint altMattingPaint = Color.lightGray;

  /** . */
  private Paint defaultLinePaint = Color.black;

  /** . */
  private boolean oval = DEFAULT_ISOVAL;

  /** . */
  private int radius = DEFAULT_RADIUS;

  /**
   * The NodusOMGraphic is not intended to be rendered, but is only a placeholder for the graphic
   * attributes loaded from "shape.properties". Therefore, this method throws an
   * UnsupportedOperationException if it is called.
   *
   * @param proj Projection
   * @return boolean
   * @hidden
   */
  @Override
  public boolean generate(Projection proj) {
    throw new java.lang.UnsupportedOperationException("Method generate() not implemented.");
  }

  /**
   * Get the "alternative" paint color used to fill the graphic.
   *
   * @return Paint
   */
  public Paint getAltFillPaint() {
    return altFillPaint;
  }

  /**
   * Returns the "alternative" paint color used for lines.
   *
   * @return Paint
   */
  public Paint getAltLinePaint() {
    return altLinePaint;
  }

  /**
   * Returns the "alternative" paint color used for matting.
   *
   * @return Paint
   */
  public Paint getAltMattingPaint() {
    return altMattingPaint;
  }

  /**
   * Returns the color used when graphic is not rendered.
   *
   * @return Paint
   */
  public Paint getDefaultLinePaint() {
    return defaultLinePaint;
  }

  /**
   * Get the radius for the Point.
   *
   * @return int
   */
  public int getRadius() {
    return radius;
  }

  /**
   * Get whether little circles should be marking the point.
   *
   * @return boolean
   */
  public boolean isOval() {
    return oval;
  }

  /**
   * Sets the "alternative" paint color used to fill the graphic.
   *
   * @param p Paint
   */
  public void setAltFillPaint(Paint p) {
    altFillPaint = p;
  }

  /**
   * Sets the "alternative" paint color used for lines.
   *
   * @param p Paint
   */
  public void setAltLinePaint(Paint p) {
    altLinePaint = p;
  }

  /**
   * Sets the "alternative" paint color used for mating.
   *
   * @param p Paint
   */
  public void setAltMattingPaint(Paint p) {
    altMattingPaint = p;
  }

  /**
   * Sets the color used when graphic is not rendered.
   *
   * @param defaultPaint Paint
   */
  public void setDefaultLinePaint(Paint defaultPaint) {
    defaultLinePaint = defaultPaint;
  }

  /**
   * Set whether little circles should be marking the point.
   *
   * @param set true for circles.
   */
  public void setOval(boolean set) {
    if (oval != set) {
      setNeedToRegenerate(true);
      oval = set;
    }
  }

  /**
   * Set the radius of the marking rectangle (or oval). The edge size of the marking rectangle will
   * be 2*radius + 1.
   *
   * @param radius int
   */
  public void setRadius(int radius) {
    this.radius = radius;
    setNeedToRegenerate(true);
  }
}
