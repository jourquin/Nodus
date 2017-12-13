/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
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
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.results.gui.ResultsDlg;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.dbf.ExportDBF;
import edu.uclouvain.core.nodus.utils.FileUtils;

import foxtrot.Job;
import foxtrot.Worker;

import java.awt.BasicStroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
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

  private boolean cancelDisplay = false;

  private boolean displayNextTimeSlice = false;

  private boolean export;

  private boolean isTimeDependent = false;

  private double maxResult = Double.MIN_VALUE;

  private double minResult = Double.MAX_VALUE;

  private NodusMapPanel nodusMapPanel;

  private NodusProject nodusProject;

  int sliceDisplayInterval;

  /**
   * Initializes the class.
   *
   * @param mapPanel The NodusMapPanel.
   * @param export If true, the results are exported in a shapefile.
   */
  public LinkResults(NodusMapPanel mapPanel, boolean export) {
    nodusMapPanel = mapPanel;
    nodusProject = nodusMapPanel.getNodusProject();
    this.export = export;
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
   * Read flows in the database by means of the passed SQL statement, and updates the links
   * attributes in order to display the flows on the map.
   *
   * @param sqlStmt The SQL query used to display this result.
   * @return boolean True on success.
   */
  public boolean displayFlows(String sqlStmt) {
    return displayFlows(sqlStmt, -1);
  }

  /**
   * Read flows in the database by means of the passed SQL statement and the starting time (in
   * seconds, starting from midnight), and updates the links attributes in order to display the
   * flows on the map.
   *
   * @param sqlStmt The SQL query used to display this result.
   * @param time The starting time if the time slice to display, or -1 for non dynamic assignments.
   * @return boolean True on success.
   */
  boolean displayFlows(String sqlStmt, int time) {

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

    // Project.setUnit(SHAPE_TYPE_POLYLINE, "t");

    if (isTimeDependent) {
      export = false;
    }

    // Retain extreme values if not yet set
    if (!isTimeDependent) {

      for (NodusEsriLayer element : linkLayers) {

        // Delete old _gis file
        String fileName =
            nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                + element.getTableName()
                + NodusC.SUFFIX_RESULTS
                + NodusC.TYPE_DBF;
        File f = new File(fileName);
        if (f.exists()) {
          boolean ok = f.delete();
          if (!ok) {
            System.err.println("Unable to delete " + fileName);
          }
        }

        fileName =
            nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                + element.getTableName()
                + NodusC.SUFFIX_RESULTS
                + NodusC.TYPE_SHP;
        f = new File(fileName);
        if (f.exists()) {
          boolean ok = f.delete();
          if (!ok) {
            System.err.println("Unable to delete " + fileName);
          }
        }

        fileName =
            nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                + element.getTableName()
                + NodusC.SUFFIX_RESULTS
                + NodusC.TYPE_SHX;
        f = new File(fileName);
        if (f.exists()) {
          boolean ok = f.delete();
          if (!ok) {
            System.err.println("Unable to delete " + fileName);
          }
        }

        // Only look into displayed layers
        if (element.isVisible()) {
          EsriGraphicList egl = element.getEsriGraphicList();
          Iterator<?> it = egl.iterator();

          while (it.hasNext()) {
            OMGraphic omg = (OMGraphic) it.next();

            // if (omg.isVisible()) { // Take only visible graphics into account

            RealLink rl = (RealLink) omg.getAttribute(0);
            double result = rl.getResult();

            if (maxResult < result) {
              maxResult = result;
            }

            if (minResult > result) {
              minResult = result;
            }
            // }
          }
        }
      }
    }

    // Round results
    roundResults();

    /** Set the width of the strokes and update map */
    Worker.post(
        new Job() {
          @Override
          public Object run() {

            int maxWidth = nodusProject.getLocalProperty(NodusC.PROP_MAX_WIDTH, NodusC.MAX_WIDTH);
            DbfTableModel resultModel = null;

            for (NodusEsriLayer linkLayer : linkLayers) {
              if (linkLayer.isVisible()) {
                EsriGraphicList egl = linkLayer.getEsriGraphicList();
                DbfTableModel tableModel = linkLayer.getModel();
                if (export) {
                  // Copy the .shp and .shx files
                  String fromFile =
                      nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                          + linkLayer.getTableName()
                          + NodusC.TYPE_SHP;
                  String toFile =
                      nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                          + linkLayer.getTableName()
                          + NodusC.SUFFIX_RESULTS
                          + NodusC.TYPE_SHP;
                  FileUtils.copyFile(fromFile, toFile);

                  fromFile =
                      nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                          + linkLayer.getTableName()
                          + NodusC.TYPE_SHX;
                  toFile =
                      nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                          + linkLayer.getTableName()
                          + NodusC.SUFFIX_RESULTS
                          + NodusC.TYPE_SHX;
                  FileUtils.copyFile(fromFile, toFile);

                  // Clone dbfTable
                  resultModel = new DbfTableModel(tableModel.getColumnCount() + 1);
                  for (int j = 0; j < tableModel.getColumnCount(); j++) {
                    resultModel.setColumnName(j, tableModel.getColumnName(j));
                    resultModel.setType(j, tableModel.getType(j));
                    resultModel.setLength(j, tableModel.getLength(j));
                    resultModel.setDecimalCount(j, tableModel.getDecimalCount(j));
                  }

                  // Add new column
                  resultModel.setColumnName(tableModel.getColumnCount(), NodusC.DBF_RESULT);
                  resultModel.setType(tableModel.getColumnCount(), DBF_TYPE_NUMERIC);
                  resultModel.setLength(tableModel.getColumnCount(), 12);
                  resultModel.setDecimalCount(tableModel.getColumnCount(), (byte) 2);

                  // Copy the values
                  for (int j = 0; j < tableModel.getRowCount(); j++) {
                    resultModel.addBlankRecord();
                    for (int k = 0; k < tableModel.getColumnCount(); k++) {
                      resultModel.setValueAt(tableModel.getValueAt(j, k), j, k);
                    }
                  }
                }

                int index = 0;
                Iterator<OMGraphic> it = egl.iterator();
                while (it.hasNext()) {
                  OMGraphic omg = it.next();

                  RealLink rl = (RealLink) omg.getAttribute(0);
                  if (export) {
                    // Set Result in last column
                    resultModel.setValueAt(rl.getResult(), index, tableModel.getColumnCount());
                  }

                  // if (omg.isVisible()) {
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

                  // }
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

            return null;
          }
        });

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

    nodusMapPanel.setBusy(true);

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

          // Add flow to current flow
          d += JDBCUtils.getDouble(rs.getObject(2));
          rl.setResult(d);
        }
      }

      rs.close();
      stmt.close();

    } catch (Exception ex) {
      nodusMapPanel.setBusy(false);
      JOptionPane.showMessageDialog(null, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);

      return false;
    }

    // Round the flows on the links
    roundResults();

    // Update the map to display result
    NodusEsriLayer[] layers = nodusProject.getLinkLayers();
    DbfTableModel resultModel = null;

    for (int i = 0; i < layers.length; i++) {

      // Delete old _gis file
      String fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
              + layers[i].getTableName()
              + NodusC.SUFFIX_RESULTS
              + NodusC.TYPE_DBF;
      File f = new File(fileName);
      if (f.exists()) {
        boolean ok = f.delete();
        if (!ok) {
          System.err.println("Unable to delete " + fileName);
        }
      }

      fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
              + layers[i].getTableName()
              + NodusC.SUFFIX_RESULTS
              + NodusC.TYPE_SHP;
      f = new File(fileName);
      if (f.exists()) {
        boolean ok = f.delete();
        if (!ok) {
          System.err.println("Unable to delete " + fileName);
        }
      }

      fileName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
              + layers[i].getTableName()
              + NodusC.SUFFIX_RESULTS
              + NodusC.TYPE_SHX;
      f = new File(fileName);
      if (f.exists()) {
        boolean ok = f.delete();
        if (!ok) {
          System.err.println("Unable to delete " + fileName);
        }
      }

      if (layers[i].isVisible()) {
        EsriGraphicList egl = layers[i].getEsriGraphicList();
        DbfTableModel tableModel = layers[i].getModel();

        if (export) {
          // Copy the .shp and .shx files
          String fromFile =
              nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + layers[i].getTableName()
                  + NodusC.TYPE_SHP;
          String toFile =
              nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + layers[i].getTableName()
                  + NodusC.SUFFIX_RESULTS
                  + NodusC.TYPE_SHP;
          FileUtils.copyFile(fromFile, toFile);

          fromFile =
              nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + layers[i].getTableName()
                  + NodusC.TYPE_SHX;
          toFile =
              nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                  + layers[i].getTableName()
                  + NodusC.SUFFIX_RESULTS
                  + NodusC.TYPE_SHX;
          FileUtils.copyFile(fromFile, toFile);

          // Clone dbfTable
          resultModel = new DbfTableModel(tableModel.getColumnCount() + 1);
          for (int j = 0; j < tableModel.getColumnCount(); j++) {
            resultModel.setColumnName(j, tableModel.getColumnName(j));
            resultModel.setType(j, tableModel.getType(j));
            resultModel.setLength(j, tableModel.getLength(j));
            resultModel.setDecimalCount(j, tableModel.getDecimalCount(j));
          }

          // Add new column
          resultModel.setColumnName(tableModel.getColumnCount(), NodusC.DBF_RESULT);
          resultModel.setType(tableModel.getColumnCount(), DBF_TYPE_NUMERIC);
          resultModel.setLength(tableModel.getColumnCount(), 12);
          resultModel.setDecimalCount(tableModel.getColumnCount(), (byte) 2);

          // Copy the values
          for (int j = 0; j < tableModel.getRowCount(); j++) {
            resultModel.addBlankRecord();
            for (int k = 0; k < tableModel.getColumnCount(); k++) {
              resultModel.setValueAt(tableModel.getValueAt(j, k), j, k);
            }
          }
        }

        Iterator<?> it = egl.iterator();
        int index = 0;

        while (it.hasNext()) {
          OMGraphic omg = (OMGraphic) it.next();
          RealLink rl = (RealLink) omg.getAttribute(0);

          if (export) {
            // Set Result in last column
            resultModel.setValueAt(rl.getResult(), index, tableModel.getColumnCount());
          }

          if (rl.getResult() != 0) {
            // Get the original width of the link
            BasicStroke bs = (BasicStroke) layers[i].getStyle(omg, index).getStroke();
            rl.setSize(bs.getLineWidth());

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
  // TODO Save sql string in dialog ?
  public boolean displayTimeDependentFlows(String sqlStmt) {

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

    //resetResults();

    int currentTime = assignmentStartTime;
    final LabelLayer lbl = labelLayer;

    while (currentTime <= assignmentEndTime) {
      final int t = currentTime;
      displayNextTimeSlice = false;

      Worker.post(
          new Job() {
            @Override
            public Object run() {

              if (lbl != null) {
                int hour = t / 60 % 24;
                int min = t % 60;
                DecimalFormat hourFormatter = new DecimalFormat("00");

                lbl.setLabelText(
                    i18n.get(LinkResults.class, "Flow_at", "Flow at")
                        + " "
                        + hourFormatter.format(hour)
                        + ":"
                        + hourFormatter.format(min));
                lbl.doPrepare();
              }
              return null;
            }
          });

      //resetResults();
      displayFlows(sqlStmt, t);

      currentTime += timeSliceDuration;

      // Wait 1 second or press "Enter" to display next time slice
      Worker.post(
          new Job() {
            @Override
            public Object run() {
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
              return null;
            }
          });

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

  /** Resets the link layers in order to reset the result field of each graphic. */
  /* void resetResults() {

    Worker.post(
        new Job() {
          @Override
          public Object run() {
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
            return null;
          }
        });
  }*/

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
