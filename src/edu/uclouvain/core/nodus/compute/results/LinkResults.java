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

package edu.uclouvain.core.nodus.compute.results;

import com.bbn.openmap.Environment;
import com.bbn.openmap.Layer;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.LabelLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.results.gui.ResultsDlg;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 * Performs the necessary queries and work to display the results of assignments.
 *
 * @author Bart Jourquin
 */
public class LinkResults implements ShapeConstants {

  static I18n i18n = Environment.getI18n();

  /**
   * Returns the RealLink stored in one of the link layers, or null if it doesn't exists.
   *
   * @param layers NodusEsriLayer[]
   * @param linkNum int
   * @return RealLink
   */
  private static RealLink getRealLink(NodusEsriLayer[] layers, int linkNum) {
    for (NodusEsriLayer element : layers) {
      if (element.numExists(linkNum)) {
        OMGraphic omg = element.getEsriGraphicList().getOMGraphicAt(element.getNumIndex(linkNum));

        return (RealLink) omg.getAttribute(0);
      }
    }

    return null;
  }

  private boolean autoSliceDisplay = false;

  private float brLat;

  private float brLon;

  private boolean cancelDisplay = false;

  private boolean displayNextTimeSlice = false;

  private boolean export;

  private boolean isTimeDependent = false;

  private NodusMapPanel nodusMapPanel;

  private NodusProject nodusProject;

  private boolean relativeToView;

  private int sliceDisplayInterval;

  private float ulLat;

  private float ulLon;

  private static Toolkit toolKit = Toolkit.getDefaultToolkit();

  /**
   * Initializes the class.
   *
   * @param mapPanel The NodusMapPanel.
   * @param relativeToView If true, the max width of the links will be computed taking only into
   *     account the visible links.
   * @param export If true, the results are exported in a shapefile.
   */
  public LinkResults(NodusMapPanel mapPanel, boolean relativeToView, boolean export) {
    nodusMapPanel = mapPanel;
    nodusProject = nodusMapPanel.getNodusProject();
    this.relativeToView = relativeToView;
    this.export = export;

    /*
     * Get latitude/longitude of upper-left and bottom-right corners of the current view
     */
    LatLonPoint.Double llp = mapPanel.getMapBean().getProjection().inverse(0, 0);
    ulLat = llp.getLatitude();
    ulLon = llp.getLongitude();
    int width = nodusMapPanel.getMapBean().getWidth();
    int height = nodusMapPanel.getMapBean().getHeight();
    llp = nodusMapPanel.getMapBean().getProjection().inverse(width, height);
    brLat = llp.getLatitude();
    brLon = llp.getLongitude();
  }

  /**
   * Ask the user which interval to use between two time slices (dynamic assignments only).
   *
   * @return The interval in seconds or 0 if the "enter" key must be used to display the next time
   *     slide.
   */
  private Integer askForDisplayInterval() {
    String[] intervals = {
      i18n.get(LinkResults.class, "Press_enter", "Press 'Enter'"),
      i18n.get(LinkResults.class, "half_second", "0.5 second"),
      i18n.get(LinkResults.class, "1_second", "1 second"),
      i18n.get(LinkResults.class, "2_seconds", "2 seconds"),
      i18n.get(LinkResults.class, "5_seconds", "5 seconds")
    };

    String choice =
        (String)
            JOptionPane.showInputDialog(
                null,
                i18n.get(LinkResults.class, "Display_interval", "Display interval ?"),
                i18n.get(ResultsDlg.class, "Display_results", "Display results"),
                JOptionPane.QUESTION_MESSAGE,
                null,
                intervals,
                intervals[0]);

    // Canceled
    if (choice == null) {
      return null;
    }

    // Get the index of the choice
    int index = 0;
    for (int i = 0; i < intervals.length; i++) {
      if (choice.equals(intervals[i])) {
        index = i;
        break;
      }
    }

    switch (index) {
      case 1:
        return 500;
      case 2:
        return 1000;
      case 3:
        return 2000;
      case 4:
        return 5000;
      default:
        return 0;
    }
  }

  /**
   * Read volumes in the database by means of the passed SQL statement, and updates the links
   * attributes in order to display the volumes on the map.
   *
   * @param sqlStmt The SQL query used to display this result.
   * @return boolean True on success.
   */
  public boolean displayVolumes(String sqlStmt) {
    return displayVolumes(sqlStmt, -1);
  }

  /**
   * Read volumes in the database by means of the passed SQL statement and the starting time (in
   * seconds, starting from midnight), and updates the links attributes in order to display the
   * volumes on the map.
   *
   * @param sqlStmt The SQL query used to display this result.
   * @param time The starting time if the time slice to display, or -1 for non dynamic assignments.
   * @return boolean True on success.
   */
  boolean displayVolumes(String sqlStmt, int time) {

    nodusMapPanel.setBusy(true);

    Connection jdbcConnection = nodusProject.getMainJDBCConnection();
    final NodusEsriLayer[] linkLayers = nodusProject.getLinkLayers();

    // Build time dependent query
    if (time != -1) {
      String tmp1 = sqlStmt.toLowerCase();
      int index = tmp1.indexOf("where") + 5;
      sqlStmt =
          sqlStmt.substring(0, index)
              + " time = "
              + time
              + " AND "
              + sqlStmt.substring(index + 1, sqlStmt.length());
    }

    try {
      // connect to database and execute query
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery(sqlStmt);

      // Retrieve result of query
      while (rs.next()) {
        int n = JDBCUtils.getInt(rs.getObject(1));
        RealLink rl = getRealLink(linkLayers, n);

        if (rl != null) {
          rl.setResult(JDBCUtils.getDouble(rs.getObject(2)));
        }
      }

      rs.close();
      stmt.close();

    } catch (Exception ex) {
      nodusMapPanel.setBusy(false);
      JOptionPane.showMessageDialog(null, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);

      return false;
    }

    if (isTimeDependent) {
      export = false;
    }

    // Retain extreme values if not yet set and delete old export files
    double maxResult = Double.MIN_VALUE;
    double minResult = Double.MAX_VALUE;
    if (!isTimeDependent) {
      for (NodusEsriLayer element : linkLayers) {

        // Only look into displayed layers
        if (element.isVisible()) {
          EsriGraphicList egl = element.getEsriGraphicList();
          Iterator<?> it = egl.iterator();

          while (it.hasNext()) {
            OMPoly omg = (OMPoly) it.next();

            // Is this link in the current view ?
            if (!relativeToView || isLinkInView(omg)) {
              RealLink rl = (RealLink) omg.getAttribute(0);
              double result = rl.getResult();

              if (maxResult < result) {
                maxResult = result;
              }

              if (minResult > result) {
                minResult = result;
              }
            }
          }
        }
      }
    }

    // Round results
    roundResults();

    /* Set the width of the strokes and update map. */
    int maxWidth = nodusProject.getLocalProperty(NodusC.PROP_MAX_WIDTH, NodusC.MAX_WIDTH);
    DbfTableModel resultModel = null;

    for (NodusEsriLayer linkLayer : linkLayers) {
      if (linkLayer.isVisible()) {
        EsriGraphicList egl = linkLayer.getEsriGraphicList();
        DbfTableModel tableModel = linkLayer.getModel();

        if (export) {
          // Create dbfTable with NUM and RESULTS field only
          resultModel = new DbfTableModel(2);
          resultModel.setColumnName(0, NodusC.DBF_NUM);
          resultModel.setType(0, DBF_TYPE_NUMERIC);
          resultModel.setLength(0, 10);
          resultModel.setDecimalCount(0, (byte) 0);
          resultModel.setColumnName(1, NodusC.DBF_RESULT);
          resultModel.setType(1, DBF_TYPE_NUMERIC);
          resultModel.setLength(1, 12);
          resultModel.setDecimalCount(1, (byte) 2);

          // Copy the NUM values
          for (int j = 0; j < tableModel.getRowCount(); j++) {
            resultModel.addBlankRecord();
            resultModel.setValueAt(tableModel.getValueAt(j, 0), j, 0);
          }
        }

        int index = 0;
        Iterator<OMGraphic> it = egl.iterator();
        while (it.hasNext()) {
          OMGraphic omg = it.next();

          RealLink rl = (RealLink) omg.getAttribute(0);
          if (export) {
            // Set Result in second column of the result table model
            resultModel.setValueAt(rl.getResult(), index, 1);
          }

          if (rl.getResult() != 0) {
            double size = rl.getResult() / ((maxResult - minResult) / (double) maxWidth);

            if (size > 0) {
              size++;
            } else {
              size--;
            }

            rl.setSize(java.lang.Math.round(size));

          } else {
            rl.setSize(0);
          }

          index++;
        }

        if (export) {
          ExportDBF.exportTable(
              nodusProject,
              linkLayer.getTableName() + NodusC.SUFFIX_RESULTS + NodusC.TYPE_DBF,
              resultModel);
        }

        linkLayer.setDisplayResults(true);
        linkLayer.getLocationHandler().setDisplayResults(true);
        linkLayer.attachStyles();
        linkLayer.doPrepare();
      }
    }

    nodusProject.getLocationLayer().reloadData();
    nodusProject.getLocationLayer().doPrepare();
    nodusMapPanel.setBusy(false);

    return true;
  }

  /**
   * Read path in the database by means of the passed SQL statement, and updates the links
   * attributes in order to display the used route(weights) on the map.
   *
   * @param sqlStmt The SQL query used to display this result.
   * @return boolean True on success.
   */
  public boolean displayPath(String sqlStmt) {

    double maxResult = Double.MIN_VALUE;
    double minResult = Double.MAX_VALUE;

    nodusMapPanel.setBusy(true);

    // Get the average loads per vehicle
    int currentScenario = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0);

    // TODO retrieve cost functions file name and fetch relevant data in it

    Connection jdbcConnection = nodusProject.getMainJDBCConnection();
    NodusEsriLayer[] linkLayers = nodusProject.getLinkLayers();

    try {
      Statement stmt = jdbcConnection.createStatement();

      // Execute query
      ResultSet rs = stmt.executeQuery(sqlStmt);

      // Retrieve result of query

      while (rs.next()) {
        RealLink rl = getRealLink(linkLayers, JDBCUtils.getInt(rs.getObject(1)));

        if (rl != null) {
          double d = rl.getResult();

          // Add volume to current volume
          d += JDBCUtils.getDouble(rs.getObject(2));
          rl.setResult(d);

          if (d > maxResult) {
            maxResult = d;
          }

          if (d < minResult) {
            minResult = d;
          }
        }
      }

      rs.close();
      stmt.close();

    } catch (Exception ex) {
      nodusMapPanel.setBusy(false);
      JOptionPane.showMessageDialog(null, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);

      return false;
    }

    // Round the volumes on the links
    roundResults();

    // Update the map to display result
    NodusEsriLayer[] layers = nodusProject.getLinkLayers();
    DbfTableModel resultModel = null;

    int maxWidth = nodusProject.getLocalProperty(NodusC.PROP_MAX_WIDTH, NodusC.MAX_WIDTH);
    for (int i = 0; i < layers.length; i++) {

      if (layers[i].isVisible()) {
        EsriGraphicList egl = layers[i].getEsriGraphicList();
        DbfTableModel tableModel = layers[i].getModel();

        if (export) {
          // Create dbfTable with NUM and RESULTS field only
          resultModel = new DbfTableModel(2);
          resultModel.setColumnName(0, NodusC.DBF_NUM);
          resultModel.setType(0, DBF_TYPE_NUMERIC);
          resultModel.setLength(0, 10);
          resultModel.setDecimalCount(0, (byte) 0);
          resultModel.setColumnName(1, NodusC.DBF_RESULT);
          resultModel.setType(1, DBF_TYPE_NUMERIC);
          resultModel.setLength(1, 12);
          resultModel.setDecimalCount(1, (byte) 2);

          // Copy the NUM values
          for (int j = 0; j < tableModel.getRowCount(); j++) {
            resultModel.addBlankRecord();
            resultModel.setValueAt(tableModel.getValueAt(j, 0), j, 0);
          }
        }

        Iterator<?> it = egl.iterator();
        int index = 0;

        while (it.hasNext()) {
          OMGraphic omg = (OMGraphic) it.next();
          RealLink rl = (RealLink) omg.getAttribute(0);

          if (export) {
            // Set Result in last column
            resultModel.setValueAt(rl.getResult(), index, 1);
          }

          if (rl.getResult() != 0) {
            // Get the original width of the link
            // BasicStroke bs = (BasicStroke) layers[i].getStyle(omg, index).getStroke();
            // rl.setSize(bs.getLineWidth());

            // Compute stroke width
            double width;
            if (maxResult > minResult) {
              width = rl.getResult() / ((maxResult - minResult) / (double) maxWidth);
            } else {
              width = maxWidth;
            }

            if (width > 0) {
              width++;
            } else {
              width--;
            }

            rl.setSize(java.lang.Math.round(width));

          } else {
            rl.setSize(0);
          }

          index++;
        }

        if (export) {
          ExportDBF.exportTable(
              nodusProject,
              layers[i].getTableName() + NodusC.SUFFIX_RESULTS + NodusC.TYPE_DBF,
              resultModel);
        }

        linkLayers[i].setDisplayResults(true);
        linkLayers[i].getLocationHandler().setDisplayResults(true);
        linkLayers[i].attachStyles();
        linkLayers[i].doPrepare();
      }
    }

    nodusProject.getLocationLayer().reloadData();
    nodusProject.getLocationLayer().doPrepare();
    nodusMapPanel.setBusy(false);

    return true;
  }

  /**
   * Displays the results of a time dependent assignment. This displays a map for each time slice.
   * The user is asked for the interval he wants between the display of two successive time slices.
   *
   * @param sqlStmt The SQL query used to display this result.
   * @return True on success.
   */
  public boolean displayTimeDependentFlows(String sqlStmt) {

    double maxResult = Double.MIN_VALUE;
    double minResult = Double.MAX_VALUE;

    // Automatic or manual display ?
    Integer answer = askForDisplayInterval();

    // Return without error
    if (answer == null) {
      return true;
    }

    if (answer == 0) {
      autoSliceDisplay = false;
    } else {
      autoSliceDisplay = true;
      sliceDisplayInterval = answer;
    }

    isTimeDependent = true;

    boolean displayVehicles = false;
    if (sqlStmt.toLowerCase().contains(NodusC.DBF_VEHICLES)) {
      displayVehicles = true;
    }

    // Is a label layer present?
    LabelLayer labelLayer = null;
    String oldText = null;
    Layer[] l = nodusMapPanel.getLayerHandler().getLayers();
    for (Layer element : l) {
      if (element.getClass().getName() == "com.bbn.openmap.layer.NodusLabelLayer"
          || element.getClass().getName() == "com.bbn.openmap.layer.LabelLayer") {
        labelLayer = (LabelLayer) element;
        oldText = labelLayer.getLabelText();
        break;
      }
    }

    // Get the information about the time windows in the cost function
    Properties costFunctions;
    NodusProject nodusProject = nodusMapPanel.getNodusProject();
    String s = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.TYPE_COSTS;
    s = nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS, s);
    String costFunctionsFileName = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + s;

    costFunctions = new Properties();
    try {
      costFunctions.load(new FileInputStream(costFunctionsFileName.trim()));
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(
          null, "Cost functions not found", NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    int assignmentStartTime =
        Integer.parseInt(costFunctions.getProperty(NodusC.VARNAME_STARTTIME, "-1"));
    int assignmentEndTime =
        Integer.parseInt(costFunctions.getProperty(NodusC.VARNAME_ENDTIME, "-1"));
    int timeSliceDuration =
        Integer.parseInt(costFunctions.getProperty(NodusC.VARNAME_TIMESLICE, "-1"));

    if (assignmentEndTime == -1 || assignmentStartTime == -1 || timeSliceDuration == -1) {
      JOptionPane.showMessageDialog(
          null,
          "Time related variables not found in cost functions",
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
      return false;
    }

    // Compute min and max values
    Connection jdbcConnection = nodusProject.getMainJDBCConnection();
    try {
      // connect to database and execute query
      Statement stmt = jdbcConnection.createStatement();
      ResultSet rs = stmt.executeQuery(sqlStmt);

      // Retrieve min and max values
      while (rs.next()) {
        double result = JDBCUtils.getDouble(rs.getObject(2));
        if (maxResult < result) {
          maxResult = result;
        }

        if (minResult > result) {
          minResult = result;
        }
      }

      rs.close();
      stmt.close();

    } catch (Exception ex) {
      nodusMapPanel.setBusy(false);
      JOptionPane.showMessageDialog(null, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);

      return false;
    }

    cancelDisplay = false;

    // Intercept ESC and Enter keys
    KeyAdapter ka =
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent evt) {
            // Escape key to interrupt long tasks
            if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
              cancelDisplay = true;
              displayNextTimeSlice = true;
            }

            // Enter key display next time slice
            if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
              displayNextTimeSlice = true;
            }
          }
        };
    nodusMapPanel.getMapBean().addKeyListener(ka);
    nodusMapPanel.getMapBean().requestFocus();

    int currentTime = assignmentStartTime;
    final LabelLayer lbl = labelLayer;

    while (currentTime <= assignmentEndTime) {
      final int t = currentTime;
      displayNextTimeSlice = false;

      if (lbl != null) {
        String labelUnit;
        if (displayVehicles) {
          labelUnit = i18n.get(LinkResults.class, "Vehicles_at", "Vehicles at");
        } else {
          labelUnit = i18n.get(LinkResults.class, "Volume_at", "Volume at");
        }

        int hour = t / 60 % 24;
        int min = t % 60;
        DecimalFormat hourFormatter = new DecimalFormat("00");
        lbl.setLabelText(
            labelUnit + " " + hourFormatter.format(hour) + ":" + hourFormatter.format(min));
        lbl.doPrepare();
      }

      resetResults();
      displayVolumes(sqlStmt, t);

      currentTime += timeSliceDuration;

      // Wait 1 second or press "Enter" to display next time slice
      SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
      Thread work =
          new Thread() {
            public void run() {
              try {
                if (autoSliceDisplay) {
                  Thread.sleep(sliceDisplayInterval);
                } else {
                  while (!displayNextTimeSlice) {
                    Thread.sleep(10);
                  }
                }
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              loop.exit();
            }
          };

      work.start();
      loop.enter();

      if (cancelDisplay) {
        break;
      }
    }

    nodusMapPanel.getMapBean().removeKeyListener(ka);

    if (labelLayer != null) {
      labelLayer.setLabelText("");
      labelLayer.doPrepare();
    }

    if (!cancelDisplay) {
      JOptionPane.showMessageDialog(
          nodusMapPanel,
          i18n.get(LinkResults.class, "All_periods_displayed", "All the periods were displayed"),
          i18n.get(ResultsDlg.class, "Display_results", "Display results"),
          JOptionPane.INFORMATION_MESSAGE);
    }

    // Restore old text label
    if (labelLayer != null && oldText != null) {
      labelLayer.setLabelText(oldText);
    }

    // dlg.resetLayers();
    nodusMapPanel.resetText();
    nodusMapPanel.getMainFrame().requestFocus();

    return true;
  }

  private boolean isInScreen(double lat, double lon) {
    if (lat > ulLat || lat < brLat) {
      return false;
    }
    if (lon < ulLon || lon > brLon) {
      return false;
    }
    return true;
  }

  private boolean isLinkInView(OMPoly omPoly) {
    // Get the coordinates of the end points of the current link
    double[] pts = omPoly.getLatLonArray();

    // Transform from radians to degrees!
    double orgLat = ProjMath.radToDeg(pts[0]);
    double orgLon = ProjMath.radToDeg(pts[1]);
    double dstLat = ProjMath.radToDeg(pts[pts.length - 2]);
    double dstLon = ProjMath.radToDeg(pts[pts.length - 1]);

    // Keep this link if ate least one of its end-nodes is in view
    if (isInScreen(orgLat, orgLon) || isInScreen(dstLat, dstLon)) {
      return true;
    }

    return false;
  }

  /** Resets the link layers in order to reset the result field of each graphic. */
  void resetResults() {

    SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
    Thread work =
        new Thread() {
          public void run() {
            NodusEsriLayer[] linkLayers = nodusProject.getLinkLayers();
            for (NodusEsriLayer element : linkLayers) {
              EsriGraphicList egl = element.getEsriGraphicList();
              Iterator<?> it = egl.iterator();
              while (it.hasNext()) {
                OMGraphic omg = (OMGraphic) it.next();
                RealNetworkObject rno = (RealNetworkObject) omg.getAttribute(0);
                rno.setResult(0.0);
              }
            }
            loop.exit();
          }
        };

    work.start();
    loop.enter();
  }

  /** Rounds the results obtained on the different links. */
  private void roundResults() {
    NodusEsriLayer[] linkLayers = nodusProject.getLinkLayers();

    for (NodusEsriLayer element : linkLayers) {
      EsriGraphicList egl = element.getEsriGraphicList();
      Iterator<?> it = egl.iterator();

      while (it.hasNext()) {
        OMGraphic omg = (OMGraphic) it.next();

        RealLink rl = (RealLink) omg.getAttribute(0);

        if (rl != null) {
          rl.setResult(java.lang.Math.round(rl.getResult()));
        }
      }
    }
  }
}
