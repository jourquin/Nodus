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

package com.bbn.openmap.layer.highlightedarea;

import com.bbn.openmap.Environment;
import com.bbn.openmap.event.MapMouseListener;
import com.bbn.openmap.event.SelectMouseMode;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.omGraphics.OMGraphicConstants;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMRect;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Properties;
import java.util.StringTokenizer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * This layer "highlights" a given rectangle on a map, delimited by its upper-left and bottom-right
 * lat/lon coordinates. These values can be given in the GUI in the form a a semi-column delimited
 * string. The GUI also allows to select the coordinates of current view for the highlighted area.
 *
 * @author Bart Jourquin
 */
public class HighlightedAreaLayer extends OMGraphicHandlerLayer implements MapMouseListener {

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = -5193630397555473457L;

  /** . */
  private DecimalFormat decimalformat;

  /** . */
  private DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols();

  /** . */
  private JButton getCurrentViewButton = new JButton();

  /** . */
  private JPanel gui = null;

  /** . */
  private JTextField highlightedAreaTextField = new JTextField();

  /** . */
  private boolean justGiveVisualFeedback;

  /** . */
  private NodusProject nodusProject;

  /** . */
  private JButton updateButton = new JButton();

  /** lat/lon coordinates of the corners. */
  private float upperLeftLat;

  /** . */
  private float upperLeftLon;

  /** . */
  private float lowerRightLat;

  /** . */
  private float lowerRightLon;

  /**
   * Constructs the HighlightedAreaLayer.
   *
   * @param project The Nodus project
   */
  public HighlightedAreaLayer(NodusProject project) {
    super();
    setAddAsBackground(true);
    nodusProject = project;

    justGiveVisualFeedback = false;
    setName("HighlightedArea");
    Properties p = new Properties();
    p.setProperty(
        "prettyName", i18n.get(HighlightedAreaLayer.class, "Highlighted_area", "Highlighted area"));
    setProperties(p);

    decimalFormatSymbols.setDecimalSeparator('.');
    decimalformat = new DecimalFormat("###.###", decimalFormatSymbols);

    String coordinates =
        nodusProject.getLocalProperty(NodusC.PROP_HIGHLIGHTED_AREA_COORDINATES, "90;-180;-90;180");
    parseCoordinates(coordinates);
  }

  /** Create a red delimited area, surrounded by a "dimmed" grey zone. */
  private synchronized void createHighlightedArea() {

    if (getList() == null) {
      setList(new OMGraphicList());
    } else {
      getList().clear();
    }

    if (!justGiveVisualFeedback) {
      // Get latitude/longitude of upper-left and bottom-right
      // corners of the current view
      LatLonPoint.Double llp =
          nodusProject.getNodusMapPanel().getMapBean().getProjection().inverse(0, 0);
      float ulLat = llp.getLatitude() + 1;
      float ulLon = llp.getLongitude() - 1;
      int width = nodusProject.getNodusMapPanel().getMapBean().getWidth();
      int height = nodusProject.getNodusMapPanel().getMapBean().getHeight();
      llp = nodusProject.getNodusMapPanel().getMapBean().getProjection().inverse(width, height);
      float lrLat = llp.getLatitude() - 1;
      float lrLon = llp.getLongitude() + 1;

      // Create the grayed zone around the active area
      Color color1 =
          new Color(
              Color.LIGHT_GRAY.getRed(),
              Color.LIGHT_GRAY.getGreen(),
              Color.LIGHT_GRAY.getBlue(),
              100);
      Color color2 =
          new Color(
              Color.LIGHT_GRAY.getRed(),
              Color.LIGHT_GRAY.getGreen(),
              Color.LIGHT_GRAY.getBlue(),
              0);
      if (upperLeftLat < ulLat) {
        OMRect omrect =
            new OMRect(ulLat, ulLon, upperLeftLat, lrLon, OMGraphicConstants.LINETYPE_RHUMB);
        omrect.setVisible(true);
        omrect.setFillPaint(color1);
        omrect.setLinePaint(color2);
        getList().add(omrect);
      }

      if (lowerRightLat > lrLat) {
        OMRect omrect =
            new OMRect(lowerRightLat, ulLon, lrLat, lrLon, OMGraphicConstants.LINETYPE_RHUMB);
        omrect.setVisible(true);
        omrect.setFillPaint(color1);
        omrect.setLinePaint(color2);
        getList().add(omrect);
      }

      if (upperLeftLon > ulLon) {
        OMRect omrect =
            new OMRect(
                upperLeftLat,
                ulLon,
                lowerRightLat,
                upperLeftLon,
                OMGraphicConstants.LINETYPE_RHUMB);
        omrect.setVisible(true);
        omrect.setFillPaint(color1);
        omrect.setLinePaint(color2);
        getList().add(omrect);
      }

      if (lowerRightLon < lrLon) {
        OMRect omrect =
            new OMRect(
                upperLeftLat,
                lowerRightLon,
                lowerRightLat,
                lrLon,
                OMGraphicConstants.LINETYPE_RHUMB);
        omrect.setVisible(true);
        omrect.setFillPaint(color1);
        omrect.setLinePaint(color2);
        getList().add(omrect);
      }

      // Create the highlighted area
      OMRect omrect =
          new OMRect(
              upperLeftLat,
              upperLeftLon,
              lowerRightLat,
              lowerRightLon,
              OMGraphicConstants.LINETYPE_RHUMB);
      omrect.setVisible(true);
      omrect.setLinePaint(Color.red);
      getList().add(omrect);
    } else {
      // Just draw small rectangle to give a visual feedback to the "get current view" command
      int width = nodusProject.getNodusMapPanel().getMapBean().getWidth();
      int height = nodusProject.getNodusMapPanel().getMapBean().getHeight();
      int margin = 3;
      OMRect omrect = new OMRect();
      omrect.setLocation(margin, margin, width - margin, height - margin);

      omrect.setVisible(true);
      omrect.setLinePaint(Color.red);

      getList().add(omrect);
    }
    justGiveVisualFeedback = false;
    getList().regenerate(getProjection());
  }

  /**
   * GUI that allows to define a highlighted area.
   *
   * @return Component or null
   */
  @Override
  public Component getGUI() {

    if (gui == null) {
      gui = new JPanel(new GridLayout(0, 1));

      String coordinates =
          decimalformat.format(upperLeftLat)
              + " ; "
              + decimalformat.format(upperLeftLon)
              + " ; "
              + decimalformat.format(lowerRightLat)
              + " ; "
              + decimalformat.format(lowerRightLon);

      int l = coordinates.length();
      // Be sure to have place enough. Longest string is
      String maxString = "90.000 ; -180.000 ; -90.000 ; 180.000";
      while (coordinates.length() < maxString.length()) {
        coordinates += " ";
      }

      highlightedAreaTextField.setText(coordinates);
      highlightedAreaTextField.setCaretPosition(l);

      getCurrentViewButton.setText(
          i18n.get(HighlightedAreaLayer.class, "Get_current_view", "Get current view"));
      getCurrentViewButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              // get the coordinates of the current view
              LatLonPoint llp =
                  nodusProject.getNodusMapPanel().getMapBean().getProjection().inverse(0, 0);
              float ulLat = llp.getLatitude();
              float ulLon = llp.getLongitude();
              int width = nodusProject.getNodusMapPanel().getMapBean().getWidth();
              int height = nodusProject.getNodusMapPanel().getMapBean().getHeight();
              llp =
                  nodusProject
                      .getNodusMapPanel()
                      .getMapBean()
                      .getProjection()
                      .inverse(width, height);
              float lrLat = llp.getLatitude();
              float lrLon = llp.getLongitude();

              String coordinates =
                  decimalformat.format(ulLat)
                      + " ; "
                      + decimalformat.format(ulLon)
                      + " ; "
                      + decimalformat.format(lrLat)
                      + " ; "
                      + decimalformat.format(lrLon);
              highlightedAreaTextField.setText(coordinates);

              // Just give a visual feedback with a red rectangle
              justGiveVisualFeedback = true;
              doPrepare();
            }
          });

      updateButton.setText(i18n.get(HighlightedAreaLayer.class, "Update", "Update"));
      updateButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              String coordinates = highlightedAreaTextField.getText().trim();
              parseCoordinates(coordinates);
              nodusProject.setLocalProperty(NodusC.PROP_HIGHLIGHTED_AREA_COORDINATES, coordinates);
              doPrepare();
            }
          });

      gui.add(highlightedAreaTextField);
      gui.add(getCurrentViewButton);
      gui.add(updateButton);
    }
    return gui;
  }

  /**
   * Returns self as the <code>MapMouseListener</code> in order to receive <code>MapMouseEvent
   * </code>weights. If the implementation would prefer to delegate <code>MapMouseEvent</code>
   * weights, it could return the delegate from this method instead.
   *
   * @return MapMouseListener this
   */
  @Override
  public MapMouseListener getMapMouseListener() {
    return this;
  }

  /** This layer only support the gesture mode mode. */
  public String[] getMouseModeServiceList() {
    return new String[] {SelectMouseMode.modeID};
  }

  /**
   * Tests if a lat/lon point is located in the highlighted area.
   *
   * @param lat Latitude
   * @param lon Longitude
   * @return true if the given lat/lon point is located in the highlighted area.
   */
  public boolean isInHighlightedArea(double lat, double lon) {
    if (lat > upperLeftLat || lat < lowerRightLat) {
      return false;
    }
    if (lon < upperLeftLon || lon > lowerRightLon) {
      return false;
    }
    return true;
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public boolean mouseClicked(MouseEvent e) {
    return false;
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public boolean mouseDragged(MouseEvent e) {
    return false;
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void mouseEntered(MouseEvent e) {}

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void mouseExited(MouseEvent e) {}

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void mouseMoved() {}

  /**
   * .
   *
   * @hidden
   */
  @Override
  public boolean mouseMoved(MouseEvent e) {
    return false;
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public boolean mousePressed(MouseEvent e) {
    return false;
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public boolean mouseReleased(MouseEvent e) {
    return false;
  }

  /** Parse the coordinates from a string like "45.0;8.0;42.2;10.7" */
  private void parseCoordinates(String coordinates) {

    upperLeftLat = 90;
    upperLeftLon = -180;
    lowerRightLat = -90;
    lowerRightLon = 180;

    StringTokenizer st = new StringTokenizer(coordinates, ";");
    if (st.countTokens() != 4) {
      System.err.println("'" + coordinates + "' is not a valid rectangle");
      return;
    }

    try {
      upperLeftLat = Float.parseFloat(st.nextToken());
      upperLeftLon = Float.parseFloat(st.nextToken());
      lowerRightLat = Float.parseFloat(st.nextToken());
      lowerRightLon = Float.parseFloat(st.nextToken());
    } catch (NumberFormatException e) {
      System.err.println("Invalid latitude or longitude");
      upperLeftLat = 90;
      upperLeftLon = -180;
      lowerRightLat = -90;
      lowerRightLon = 180;
      return;
    }
  }

  /**
   * Creates the highlighted area before calling the parent class.
   *
   * @return OMGraphicList
   */
  @Override
  public synchronized OMGraphicList prepare() {
    createHighlightedArea();
    return super.prepare();
  }
}
