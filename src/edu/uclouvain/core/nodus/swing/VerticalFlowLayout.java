/*
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * A vertical flow layout is similar to a flow layout but it layouts the components vertically
 * instead of horizontally.
 *
 * @author Bart Jourquin, heavily inspired by code from Vassili Dzuba
 */
public class VerticalFlowLayout implements LayoutManager, java.io.Serializable {

  private static final long serialVersionUID = -7280145247979732917L;

  static final int DEFAULT_MARGINS = 4;

  public static final int BOTTOM = 2;
  public static final int CENTER = 1;
  public static final int LEFT = 3;
  public static final int RIGHT = 4;
  public static final int TOP = 0;

  int horizontalAlign;
  int horizontalGap;
  int verticalAlign;
  int verticalGap;

  /** Constructor for the VerticalFlowLayout object. */
  public VerticalFlowLayout() {
    this(CENTER, CENTER, DEFAULT_MARGINS, DEFAULT_MARGINS);
  }

  /**
   * Constructor for the VerticalFlowLayout object.
   *
   * @param halign Description of Parameter
   * @param valign Description of Parameter
   */
  public VerticalFlowLayout(int halign, int valign) {
    this(halign, valign, DEFAULT_MARGINS, DEFAULT_MARGINS);
  }

  /**
   * Constructor for the VerticalFlowLayout object.
   *
   * @param halign Description of Parameter
   * @param valign Description of Parameter
   * @param hgap Description of Parameter
   * @param vgap Description of Parameter
   */
  public VerticalFlowLayout(int halign, int valign, int hgap, int vgap) {
    horizontalGap = hgap;
    verticalGap = vgap;
    setAlignment(halign, valign);
  }

  /**
   * Adds a feature to the LayoutComponent attribute of the VerticalFlowLayout object.
   *
   * @param name The feature to be added to the LayoutComponent attribute
   * @param comp The feature to be added to the LayoutComponent attribute
   */
  @Override
  public void addLayoutComponent(String name, Component comp) {

  }

  /**
   * Gets the Halignment attribute of the VerticalFlowLayout object.
   *
   * @return The Halignment value
   */
  public int getHalignment() {
    return horizontalAlign;
  }

  /**
   * Gets the Hgap attribute of the VerticalFlowLayout object.
   *
   * @return The Hgap value
   */
  public int getHgap() {
    return horizontalGap;
  }

  /**
   * Gets the Valignment attribute of the VerticalFlowLayout object.
   *
   * @return The Valignment value
   */
  public int getValignment() {
    return verticalAlign;
  }

  /**
   * Gets the Vgap attribute of the VerticalFlowLayout object.
   *
   * @return The Vgap value
   */
  public int getVgap() {
    return verticalGap;
  }

  /**
   * Description of the Method.
   *
   * @param target Description of Parameter
   */
  @Override
  public void layoutContainer(Container target) {
    synchronized (target.getTreeLock()) {
      Insets insets = target.getInsets();
      int maxheight = target.getHeight() - (insets.top + insets.bottom + verticalGap * 2);
      int nmembers = target.getComponentCount();
      int y = 0;

      Dimension preferredSize = preferredLayoutSize(target);
      Dimension targetSize = target.getSize();

      switch (verticalAlign) {
        case TOP:
          y = insets.top;
          break;
        case CENTER:
          y = (targetSize.height - preferredSize.height) / 2;
          break;
        case BOTTOM:
          y = targetSize.height - preferredSize.height - insets.bottom;
          break;
        default:
          break;
      }

      for (int i = 0; i < nmembers; i++) {
        Component m = target.getComponent(i);
        if (m.isVisible()) {
          Dimension d = m.getPreferredSize();
          m.setSize(d.width, d.height);

          if (y + d.height <= maxheight) {
            if (y > 0) {
              y += verticalGap;
            }

            int x = 0;
            switch (horizontalAlign) {
              case LEFT:
                x = insets.left;
                break;
              case CENTER:
                x = (targetSize.width - d.width) / 2;
                break;
              case RIGHT:
                x = targetSize.width - d.width - insets.right;
                break;
              default:
                break;
            }

            m.setLocation(x, y);

            y += d.getHeight();

          } else {
            break;
          }
        }
      }
    }
  }

  /**
   * Description of the Method.
   *
   * @param target Description of Parameter
   * @return Description of the Returned Value
   */
  @Override
  public Dimension minimumLayoutSize(Container target) {
    synchronized (target.getTreeLock()) {
      Dimension dim = new Dimension(0, 0);
      int nmembers = target.getComponentCount();
      boolean firstVisibleComponent = true;

      for (int ii = 0; ii < nmembers; ii++) {
        Component m = target.getComponent(ii);
        if (m.isVisible()) {
          Dimension d = m.getPreferredSize();
          dim.width = Math.max(dim.width, d.width);
          if (firstVisibleComponent) {
            firstVisibleComponent = false;
          } else {
            dim.height += verticalGap;
          }
          dim.height += d.height;
        }
      }
      Insets insets = target.getInsets();
      dim.width += insets.left + insets.right + horizontalGap * 2;
      dim.height += insets.top + insets.bottom + verticalGap * 2;
      return dim;
    }
  }

  /**
   * Description of the Method.
   *
   * @param target Description of Parameter
   * @return Description of the Returned Value
   */
  @Override
  public Dimension preferredLayoutSize(Container target) {
    synchronized (target.getTreeLock()) {
      Dimension dim = new Dimension(0, 0);
      int nmembers = target.getComponentCount();
      boolean firstVisibleComponent = true;

      for (int ii = 0; ii < nmembers; ii++) {
        Component m = target.getComponent(ii);
        if (m.isVisible()) {
          Dimension d = m.getPreferredSize();
          dim.width = Math.max(dim.width, d.width);
          if (firstVisibleComponent) {
            firstVisibleComponent = false;
          } else {
            dim.height += verticalGap;
          }
          dim.height += d.height;
        }
      }
      Insets insets = target.getInsets();
      dim.width += insets.left + insets.right + horizontalGap * 2;
      dim.height += insets.top + insets.bottom + verticalGap * 2;
      return dim;
    }
  }

  /**
   * Description of the Method.
   *
   * @param comp Description of Parameter
   */
  @Override
  public void removeLayoutComponent(Component comp) {

  }

  /**
   * Sets the Alignment attribute of the VerticalFlowLayout object.
   *
   * @param halign The new Alignment value
   * @param valign The new Alignment value
   */
  public void setAlignment(int halign, int valign) {
    horizontalAlign = halign;
    verticalAlign = valign;
  }

  /**
   * Sets the Hgap attribute of the VerticalFlowLayout object.
   *
   * @param hgap The new Hgap value
   */
  public void setHgap(int hgap) {
    horizontalGap = hgap;
  }

  /**
   * Sets the Vgap attribute of the VerticalFlowLayout object.
   *
   * @param vgap The new Vgap value
   */
  public void setVgap(int vgap) {
    verticalGap = vgap;
  }

  /**
   * .
   *  {@inheritDoc} 
   **/
  @Override
  public String toString() {
    String halign = "";
    switch (horizontalAlign) {
      case TOP:
        halign = "top";
        break;
      case CENTER:
        halign = "center";
        break;
      case BOTTOM:
        halign = "bottom";
        break;
      default:
        break;
    }
    String valign = "";
    switch (verticalAlign) {
      case TOP:
        valign = "top";
        break;
      case CENTER:
        valign = "center";
        break;
      case BOTTOM:
        valign = "bottom";
        break;
      default:
        break;
    }
    return getClass().getName()
        + "[hgap="
        + horizontalGap
        + ",vgap="
        + verticalGap
        + ",halign="
        + halign
        + ",valign="
        + valign
        + "]";
  }
}
