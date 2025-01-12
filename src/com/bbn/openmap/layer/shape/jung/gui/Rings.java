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

package com.bbn.openmap.layer.shape.jung.gui;

import edu.uci.ics.jung.algorithms.layout.PolarPoint;
import edu.uci.ics.jung.algorithms.layout.RadialTreeLayout;
import edu.uci.ics.jung.graph.Forest;
import edu.uci.ics.jung.visualization.Layer;
import edu.uci.ics.jung.visualization.VisualizationServer;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Draw rings in a Jung graph. Used to draw the virtual network for a node.<br>
 * See the JUNG TreeLayoutDemo example.<br>
 *
 * @author Bart Jourquin
 */
class Rings implements VisualizationServer.Paintable {

  private Collection<Double> depths;

  private Forest<String, String> graph;

  private RadialTreeLayout<String, String> radialLayout;

  private VisualizationViewer<String, String> vv;

  /**
   * Creates a new visualization server that draws rings.
   *
   * @param graph Forest
   * @param radialLayout RadialTreeLayout
   * @param vv VisualizationViewer
   */
  public Rings(
      Forest<String, String> graph,
      RadialTreeLayout<String, String> radialLayout,
      VisualizationViewer<String, String> vv) {
    this.graph = graph;
    this.radialLayout = radialLayout;
    this.vv = vv;
    depths = getDepths();
  }

  /**
   * .
   *
   * @hidden
   */
  private Collection<Double> getDepths() {
    Set<Double> depths = new HashSet<>();
    Map<String, PolarPoint> polarLocations = radialLayout.getPolarLocations();
    for (String v : graph.getVertices()) {
      PolarPoint pp = polarLocations.get(v);
      depths.add(pp.getRadius());
    }
    return depths;
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void paint(Graphics g) {
    g.setColor(Color.lightGray);

    Graphics2D g2d = (Graphics2D) g;
    Point2D center = radialLayout.getCenter();

    Ellipse2D ellipse = new Ellipse2D.Double();
    for (double d : depths) {
      ellipse.setFrameFromDiagonal(
          center.getX() - d, center.getY() - d, center.getX() + d, center.getY() + d);
      Shape shape =
          vv.getRenderContext()
              .getMultiLayerTransformer()
              .getTransformer(Layer.LAYOUT)
              .transform(ellipse);
      g2d.draw(shape);
    }
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public boolean useTransform() {
    return true;
  }
}
