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

package com.bbn.openmap.layer.location;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.layer.location.gui.LocationFieldChooserDlg;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.GreatCircle;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.util.ColorFactory;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.real.RealNode;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Properties;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;

/**
 * The NodusLocationHandler is an AbstractLocationHandler that uses the values of a given field of
 * the .dbf file of an EsriLayer as label. Alternatively, other data (results) associated to the
 * OMGraphics of the same layer can be displayed.
 *
 * @author Bart Jourquin
 */
public class NodusLocationHandler extends AbstractLocationHandler
    implements LocationHandler, ActionListener {

  private static I18n i18n = Environment.getI18n();

  /**
   * Transforms a Font to a string suitable to be stored in a property file.
   *
   * @param f Font
   * @return String
   */
  private static String getFontString(Font f) {
    String style = "";

    if (f.isPlain()) {
      style = "-PLAIN-";
    }

    if (f.isBold()) {
      style = "-BOLD-";
    }

    if (f.isItalic()) {
      style = "-ITALIC-";
    }

    if (f.isBold() && f.isItalic()) {
      style = "-BOLDITALIC";
    }

    return f.getFamily() + style + f.getSize();
  }

  /** Replaces the rereadFiles button of the original GUI with a "Query database". */
  private Box box = null;

  private Connection con;

  /** Type of labels to display : regular (.dbf related) or results elsewhere computed?. */
  private boolean displayResults = false;

  /** Font name. */
  private Font fontName = new Font("Default", java.awt.Font.PLAIN, 12);

  private OMGraphicList graphicList = new OMGraphicList();

  /** By default, no field is displayed. */
  // private int locationFieldIndex = -1;

  private String locationFieldName = "";

  /** SQL "WHERE" statement that can be used to filter the fields to display. */
  private String locationWhereStmt = "";

  /** The geographical coords of the object will be found in a EsriGraphicList. */
  private NodusEsriLayer nodusEsriLayer;

  /** Used to limit the DB query to cases where the filter has changed. */
  String oldLocationQueryString = "";

  /** The properties file associated to the project. */
  private Properties projectProperties;

  /** Display or not the labels. */
  private boolean isVisible = true;

  /**
   * Associate this location handler to a given NodusEsriLayer.
   *
   * @param layer NodusEsriLayer
   */
  public NodusLocationHandler(NodusEsriLayer layer) {
    nodusEsriLayer = layer;
    con = nodusEsriLayer.getNodusMapPanel().getNodusProject().getMainJDBCConnection();
  }

  /**
   * The Action Listener method, that reacts to widgets actions.
   *
   * @param e ActionEvent
   */
  @Override
  public void actionPerformed(ActionEvent e) {

    String cmd = e.getActionCommand();

    if (cmd.equals(readDataCommand)) {
      LocationFieldChooserDlg dbfFieldChooserDlg =
          new LocationFieldChooserDlg(
              nodusEsriLayer.getNodusMapPanel().getMainFrame(), getLayer().getName(), this);

      // Hide GUI
      Component p = box;
      while (p != null) {
        if (p instanceof JRootPane) {
          p.getParent().setVisible(false);
          break;
        }
        p = p.getParent();
      }

      dbfFieldChooserDlg.setVisible(true);
      reloadData();
      getLayer().doPrepare();
    } else if (cmd.equals(showLocationsCommand)) {
      JCheckBox locationCheck = (JCheckBox) e.getSource();
      setShowLocations(locationCheck.isSelected());
      nodusEsriLayer
          .getNodusMapPanel()
          .getNodusProject()
          .setLocalProperty(
              nodusEsriLayer.getTableName() + NodusC.PROP_SHOW_LOCATIONS,
              Boolean.toString(isShowLocations()));
      getLayer().repaint();
    } else if (cmd.equals(showNamesCommand)) {
      JCheckBox namesCheck = (JCheckBox) e.getSource();
      setShowNames(namesCheck.isSelected());
      nodusEsriLayer
          .getNodusMapPanel()
          .getNodusProject()
          .setLocalProperty(
              nodusEsriLayer.getTableName() + NodusC.PROP_SHOW_NAMES,
              Boolean.toString(isShowNames()));
      getLayer().repaint();
    }
  }

  /**
   * Fills a vector of OMGraphics to represent the data from this handler.
   *
   * @param nwLat NorthWest latitude of area of interest.
   * @param nwLon NorthWest longitude of area of interest.
   * @param seLat SouthEast latitude of area of interest.
   * @param seLon SouthEast longitude of area of interest.
   * @param graphicList Vector to add Locations to. If null, the LocationHandler should create a new
   *     Vector to place graphics into.
   * @return The OMGraphicList passed in.
   */
  @Override
  public synchronized OMGraphicList get(
      double nwLat, double nwLon, double seLat, double seLon, OMGraphicList graphicList) {
    graphicList.addAll(this.graphicList);
    return graphicList;
  }

  /**
   * Computes the "center" of a polyline as the center of the chunk that contains the location at
   * the half of its total length.
   *
   * @param omp OMPoly
   * @return Float
   */
  private Point2D.Double getCenter(OMPoly omp) {
    Point2D.Double p = null;

    double[] ll = omp.getLatLonArray();

    // Compute the total length of the polyline
    double totalLength = 0;
    int nbPairs = ll.length / 2;

    for (int k = 0; k < nbPairs - 1; k++) {
      totalLength +=
          GreatCircle.sphericalDistance(ll[k * 2], ll[k * 2 + 1], ll[k * 2 + 2], ll[k * 2 + 3]);
    }

    // Go to half of its length
    float currentLength = 0;

    for (int k = 0; k < nbPairs - 1; k++) {
      currentLength +=
          GreatCircle.sphericalDistance(ll[k * 2], ll[k * 2 + 1], ll[k * 2 + 2], ll[k * 2 + 3]);

      if (currentLength >= totalLength / 2) {
        // Take the middle of this chunk and transform the obtained
        // point in Deg
        double lat = (ll[k * 2] + ll[k * 2 + 2]) / 2;
        double lon = (ll[k * 2 + 1] + ll[k * 2 + 3]) / 2;
        p = new Point2D.Double(ProjMath.radToDeg(lat), ProjMath.radToDeg(lon));

        break;
      }
    }

    return p;
  }

  /**
   * Get CurrentFieldIndex.
   *
   * @return int
   */
  public int getLocationFieldIndex() {
    return nodusEsriLayer.getModel().getColumnIndexForName(locationFieldName);
  }

  /**
   * Get the name of the field that is used as location label.
   *
   * @return The name of the field.
   */
  public String getLocationFieldName() {
    return locationFieldName;
  }

  /**
   * Returns the SQL string used to filter the labels.
   *
   * @return String
   */
  public String getWhereStmt() {
    return locationWhereStmt;
  }

  /**
   * Returns the NodusEsriLayer that is associated to this location handler.
   *
   * @return NodusEsriLayer
   */
  public NodusEsriLayer getEsriLayer() {
    return nodusEsriLayer;
  }

  /**
   * Creates the GUI associated to this handler.
   *
   * @return The component to display
   */
  @Override
  public java.awt.Component getGUI() {
    if (box == null) {

      JCheckBox showDbLocationCheck =
          new JCheckBox(
              i18n.get(NodusLocationHandler.class, "Show_Locations", "Show Locations"),
              isShowLocations());

      showDbLocationCheck.setActionCommand(showLocationsCommand);
      showDbLocationCheck.addActionListener(this);

      JCheckBox showNameCheck =
          new JCheckBox(
              i18n.get(NodusLocationHandler.class, "Show_Location_Names", "Show Location Names"),
              isShowNames());

      showNameCheck.setActionCommand(showNamesCommand);
      showNameCheck.addActionListener(this);

      JButton rereadFilesButton =
          new JButton(
              i18n.get(
                  NodusLocationHandler.class,
                  "Query_database_for_labels",
                  "Query database for labels"));

      rereadFilesButton.setActionCommand(readDataCommand);
      rereadFilesButton.addActionListener(this);
      rereadFilesButton.setHorizontalAlignment((int) Component.CENTER_ALIGNMENT);

      box = Box.createVerticalBox();
      box.add(showDbLocationCheck);
      box.add(showNameCheck);
      box.add(rereadFilesButton);
    }

    return box;
  }

  /**
   * Returns the structure of the .dbf file associated to this location hander.
   *
   * @return DbfTableModel
   */
  public DbfTableModel getModel() {
    return nodusEsriLayer.getModel();
  }

  /**
   * Get the name of the font used to display the labels.
   *
   * @return Font
   */
  public Font getFontName() {
    return fontName;
  }

  /**
   * Get the table name associated to this location handler.
   *
   * @return String
   */
  public String getTableName() {
    return nodusEsriLayer.getTableName();
  }

  /**
   * Returns true if "results" labels are currently displayed.
   *
   * @return boolean
   */
  public boolean isDisplayResults() {
    return displayResults;
  }

  /** Force the reload of the locations. */
  public void reset() {
    oldLocationQueryString = "";
  }

  /** Looks in the database and creates the QuadTree holding all the Locations. */
  @Override
  public synchronized void reloadData() {
    // Be sure that a the EsriLayer that is attached contains nodes or links
    if (nodusEsriLayer.getEsriGraphicList() == null) {
      return;
    }

    int locationFieldIndex = getLocationFieldIndex();

    // If there is nothing to display
    if (!isDisplayResults() && (!isVisible || locationFieldIndex == -1)) {
      graphicList.clear();
      getLayer().doPrepare();
      return;
    }

    // Create basic query statement
    String s = JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM);
    String locationQueryString = "SELECT  " + s + " FROM " + nodusEsriLayer.getTableName();

    // Add the where statement
    if (locationWhereStmt.length() > 0) {
      locationQueryString += " WHERE " + locationWhereStmt;
    }

    // Only query the DB if something has changed
    if (!oldLocationQueryString.equalsIgnoreCase(locationQueryString)) {

      oldLocationQueryString = locationQueryString;

      // Clear the locations currently attached to the graphics
      Iterator<OMGraphic> it = nodusEsriLayer.getEsriGraphicList().iterator();
      while (it.hasNext()) {
        OMGraphic omg = it.next();
        Object o = omg.getAttribute(0);

        if (o instanceof RealNetworkObject) {
          RealNetworkObject rnbo = (RealNetworkObject) o;

          if (rnbo != null) {
            rnbo.setLocation(null);
          }
        }
      }

      // Execute query
      try {

        Statement stmt = con.createStatement();
        ResultSet rs = stmt.executeQuery(locationQueryString);

        OMGraphic omGraphic = null;

        while (rs.next()) {
          // Find the index of the selected record from its "num" field
          int num = JDBCUtils.getInt(rs.getObject(1));

          int index = nodusEsriLayer.getNumIndex(num);
          if (index == -1) {
            System.err.println("Invalid record found for num " + num);
            continue;
          }

          // Find lat/long coords for the object
          try {
            omGraphic = nodusEsriLayer.getEsriGraphicList().getOMGraphicAt(index);
          } catch (Exception e) {
            // This could happen for unbalanced layers when the integrity tester has not finished.
            // Ignore exception because the project will not be opened.
          }

          // Be sure an AppObject is attached to this graphic
          if (omGraphic.getAttribute(0) == null) {
            if (omGraphic instanceof EsriPolyline) {
              omGraphic.putAttribute(0, new RealLink());
            } else {
              omGraphic.putAttribute(0, new RealNode());
            }
          }

          // Create an empty label
          double lat;
          double lon;
          if (omGraphic instanceof EsriPolyline) {
            Point2D.Double p = getCenter((OMPoly) omGraphic);
            lat = p.getX();
            lon = p.getY();
          } else {
            EsriPoint ep = (EsriPoint) omGraphic;
            lat = ep.getLat();
            lon = ep.getLon();
          }
          BasicLocation loc;
          loc = new BasicLocation(lat, lon, "", null);
          loc.setLocationHandler(this);

          RealNetworkObject rn = (RealNetworkObject) omGraphic.getAttribute(0);
          rn.setLocation(loc);
          rn.setRowIndex(index);
        }

        rs.close();
        stmt.close();

      } catch (SQLException e) {
        if (e.getClass() != java.sql.SQLNonTransientConnectionException.class) {
          e.printStackTrace();
          JOptionPane.showMessageDialog(null, e.toString(), "SQL error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }

    // Update the labels
    graphicList.clear();
    Iterator<OMGraphic> it = nodusEsriLayer.getEsriGraphicList().iterator();
    // int index = 0;
    DbfTableModel model = nodusEsriLayer.getModel();

    while (it.hasNext()) {
      OMGraphic omGraphic = it.next();

      // Display a label only if the omGraphic is visible!
      if (!omGraphic.isVisible()) {
        continue;
      }

      Object o = omGraphic.getAttribute(0);

      if (o instanceof RealNetworkObject) {
        RealNetworkObject rnbo = (RealNetworkObject) o;

        BasicLocation loc = rnbo.getLocation();

        if (loc != null) {
          // Build the label to display
          String label = "";

          if (!displayResults) { // Use the selected field as label
            if (locationFieldIndex != -1) {
              Object value = model.getValueAt(rnbo.getRowIndex(), locationFieldIndex);
              if (value instanceof Double) {
                BigDecimal bd = new BigDecimal((Double) value);
                label = bd.toString();
              } else {
                label = model.getValueAt(rnbo.getRowIndex(), locationFieldIndex).toString();
              }
            }
          } else {
            label = "";

            NumberFormat nf = NumberFormat.getInstance();
            nf.setMaximumFractionDigits(2);

            if (rnbo.getSize() != 0) {
              label = nf.format(rnbo.getResult());

              // EsriLayer.getProject().getUnit(EsriLayer.getType());
            }
          }

          if (label.length() > 0) {
            graphicList.add(loc);
            loc.setName(label);

            loc.setShowName(isShowNames());
            loc.setShowLocation(isShowLocations());

            loc.setLocationPaint(getLocationColor());
            loc.getLabel().setLinePaint(getNameColor());
            loc.getLabel().setFont(getFontName());

            // Hide labels for non visible graphics
            loc.getLabel().setVisible(omGraphic.isVisible());
            loc.getLocationMarker().setVisible(omGraphic.isVisible());
          }
        }
      }
      // index++;
    }

    return;
  }

  /**
   * Sets the field name to display and updates the property file.
   *
   * @param fieldName The name of the DBF field to display.
   */
  public void setLocationFieldName(String fieldName) {

    this.locationFieldName = fieldName;
    if (fieldName != null) {
      projectProperties.setProperty(
          nodusEsriLayer.getTableName() + NodusC.PROP_LOCATION_FIELD_NAME, fieldName);
    } else {
      projectProperties.remove(nodusEsriLayer.getTableName() + NodusC.PROP_LOCATION_FIELD_NAME);
    }
  }

  /**
   * Sets CurrentWhereStmt and update property file.
   *
   * @param currentWhereStmt java.lang.String
   */
  public void setWhereStmt(String currentWhereStmt) {
    locationWhereStmt = currentWhereStmt;
    projectProperties.setProperty(
        nodusEsriLayer.getTableName() + NodusC.PROP_LOCATION_WHERESTMT, locationWhereStmt);
  }

  /**
   * Forces the results to be displayed instead of regular labels if "true" is passed.
   *
   * @param b boolean
   */
  public void setDisplayResults(boolean b) {
    displayResults = b;
  }

  /**
   * Sets the color name used to display the labels and update property file.
   *
   * @param color Color
   */
  @Override
  public void setNameColor(Color color) {
    super.setNameColor(color);
    projectProperties.setProperty(
        nodusEsriLayer.getTableName() + NodusC.PROP_COLOR, ColorFactory.getHexColorString(color));
  }

  /**
   * Sets the font used to display the labels and update the property file.
   *
   * @param nameFont Font
   */
  public void setFontName(Font nameFont) {
    fontName = nameFont;

    String code = getFontString(fontName);
    projectProperties.setProperty(nodusEsriLayer.getTableName() + NodusC.PROP_FONT, code);
  }

  /**
   * Make the locations stored in this handler visible or not. This is used to synchronize the
   * display of the locations with the display of their correspondent NodusEsriLayer.
   *
   * @param visible If true, the locations and location names are displayed if the correspondent
   *     checkboxes are checked.
   */
  public void setVisible(boolean visible) {
    if (this.isVisible != visible) {
      this.isVisible = visible;
      reloadData();
      getLayer().doPrepare();
    }
  }

  /**
   * Loads information stored in the property file (previously used field index, SQL filter
   * statement, font, color and display parameters.
   *
   * @param prefix String
   * @param properties Properties
   */
  @Override
  public void setProperties(String prefix, Properties properties) {
    projectProperties = properties;

    // Restore saved settings
    locationFieldName =
        projectProperties.getProperty(
            nodusEsriLayer.getTableName() + NodusC.PROP_LOCATION_FIELD_NAME, "");
    locationWhereStmt =
        projectProperties.getProperty(
            nodusEsriLayer.getTableName() + NodusC.PROP_LOCATION_WHERESTMT, "");

    String value;
    value = projectProperties.getProperty(nodusEsriLayer.getTableName() + NodusC.PROP_FONT, "");

    if (value.length() > 0) {
      fontName =
          Font.decode(
              projectProperties.getProperty(nodusEsriLayer.getTableName() + NodusC.PROP_FONT));
    }

    setNameColor(
        PropUtils.parseColorFromProperties(
            projectProperties, nodusEsriLayer.getTableName() + NodusC.PROP_COLOR, "FF000000"));

    setShowNames(
        PropUtils.booleanFromProperties(
            projectProperties, nodusEsriLayer.getTableName() + NodusC.PROP_SHOW_NAMES, true));
    setShowLocations(
        PropUtils.booleanFromProperties(
            projectProperties, nodusEsriLayer.getTableName() + NodusC.PROP_SHOW_LOCATIONS, true));
  }
}
