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

package edu.uclouvain.core.nodus;

import com.bbn.openmap.Environment;
import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.DeclutterMatrix;
import com.bbn.openmap.layer.drawing.NodusDrawingToolLayer;
import com.bbn.openmap.layer.location.NodusLocationHandler;
import com.bbn.openmap.layer.location.NodusLocationLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.NodusDrawingAttributes;
import com.bbn.openmap.omGraphics.NodusOMGraphic;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.ProjectionFactory;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.compute.rules.NodeRulesReader;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.ProjectFilesTools;
import edu.uclouvain.core.nodus.database.ShapeIntegrityTester;
import edu.uclouvain.core.nodus.database.dbf.DBFException;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.ImportDBF;
import edu.uclouvain.core.nodus.services.ServiceEditor;
import edu.uclouvain.core.nodus.utils.CheckForOM5;
import edu.uclouvain.core.nodus.utils.CommentedProperties;
import edu.uclouvain.core.nodus.utils.ModalSplitMethodsLoader;
import edu.uclouvain.core.nodus.utils.NodusFileFilter;
import edu.uclouvain.core.nodus.utils.ProjectLocker;
import groovy.lang.GroovyShell;
import java.awt.Color;
import java.awt.Container;
import java.awt.Frame;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.codehaus.groovy.control.CompilationFailedException;

/**
 * A Nodus project file is a properties file with a .nodus extension. <br>
 * <br>
 * <br>
 * #--------------------------------------------------------------------- <br>
 * # Demo project file for Nodus 8.0 <br>
 * #--------------------------------------------------------------------- <br>
 * <br>
 * # By default, Nodus uses an embedded DBMS, but other can be used, such as MySQL/MariaDB. <br>
 * # Some JDBC drivers are provided in the Nodus distribution. It is the case for MySQL/MariaDB.<br>
 * # In this example, the MYISAM engine is used, which is faster that INNODB, but less secure.<br>
 * <br>
 * #jdbc.driver=org.mariadb.jdbc.Driver <br>
 * #jdbc.user=nodus <br>
 * #jdbc.password=nodus <br>
 * #jdbc.url=jdbc:mysql://localhost/demo?sessionVariables=default_storage_engine=MYISAM <br>
 * <br>
 * # These two properties are mandatory. They contain a list of node and link layers <br>
 * network.nodes= centroids road_points iww_points rail_points terminals <br>
 * network.links= road_polylines iww_polylines rail_polylines road_con iww_con rail_con <br>
 * <br>
 * # This property specifies a list of additional DBF tables to import in the DBMS, <br>
 * # such as orifin-destination tables for instance. This is not mandatory. <br>
 * importTables= od_road od_iww od_rail <br>
 * <br>
 * # A pretty name can be given to each layer. Not mandatory. <br>
 * centroids.prettyName = Centroids <br>
 * terminals.prettyName = Terminals <br>
 * <br>
 * road_points.prettyName = Road (points) <br>
 * road_polylines.prettyName = Roads <br>
 * road_con.prettyName = Road connectors <br>
 * <br>
 * iww_points.prettyName = IWW (points) <br>
 * iww_polylines.prettyName = IWW <br>
 * iww_con.prettyName = IWW connectors <br>
 * <br>
 * rail_points.prettyName = Rail (points) <br>
 * rail_polylines.prettyName = Railways <br>
 * rail_con.prettyName = Rail connectors <br>
 * <br>
 * # This property can be used to specify a set of background layer. <br>
 * # These are handled by OpenMap. Please refer to its documentation. <br>
 * # This is not mandatory <br>
 * openmap.layers=demo <br>
 * <br>
 * <br>
 * #---------------------------------------------------------------------- <br>
 * # End of demo project file <br>
 * #---------------------------------------------------------------------- <br>
 */
public class NodusProject implements ShapeConstants {

  /** i18n mechanism. */
  private static I18n i18n = Environment.getI18n();

  /** Main state of a project: open or not. */
  private boolean isOpen = false;

  /** Logger handler. */
  Handler loggerHandler;

  /** JDBC connection to the database that holds the tables managed by Nodus. */
  private Connection jdbcConnection = null;

  /**
   * The layer that will handle the different location handlers. See OpenMap documentation for more
   * details on LocationLayers.
   */
  private NodusLocationLayer labelsLayer = null;

  /**
   * Array of ESRI layers that contain the links of the network. See OpenMap documentation for more
   * details on ESRILayer.
   */
  private NodusEsriLayer[] linkLayers = null;

  /**
   * Array of location handlers that will handle the labels for the links of the network. See
   * OpenMap documentation for more details on LocationHandlers
   */
  private NodusLocationHandler[] linksLocationHandler = null;

  /**
   * Array of drawings attributes used for each defined numeric style. The style can be used to
   * render the links on a map (Railway, highway, ...).
   */
  private NodusOMGraphic[] linkStyle;

  /**
   * The local properties is the placeholder for a lot of parameters saved and restored when a
   * project is opened or closed (zoom, colors, fonts, ...).
   */
  private Properties localProperties = null;

  /**
   * Array of ESRI layers that contains the nodes of the network. See OpenMap documentation for more
   * details on ESRILayer.
   */
  private NodusEsriLayer[] nodeLayers = null;

  /**
   * Array of location handlers that will handle the labels for the nodes of the network. See
   * OpenMap documentation for more details on LocationHandlers
   */
  private NodusLocationHandler[] nodesLocationHandler = null;

  /**
   * Array of drawings attributes used for each defined numeric style. The style can be used to
   * render the nodes on a map.
   */
  private NodusOMGraphic[] nodeStyle;

  /** Map panel of the application. */
  private NodusMapPanel nodusMapPanel;

  /**
   * Used to store the ID's of the links that are present in layers that are found in the project
   * directory, but that are not in the project. This is used to ensure that the ID given to a new
   * ink is not already used in another, not loaded, layer.
   */
  private HashMap<Integer, Integer> otherLinkNumbers = new HashMap<>();

  /**
   * Used to store the ID's of the nodes that are present in layers that are found in the project
   * directory, but that are not in the project. This is used to ensure that the ID given to a new
   * node is not already used in another, not loaded, layer.
   */
  private HashMap<Integer, Integer> otherNodeNumbers = new HashMap<>();

  /** True if the ID's of the other layers present in the project directory are loaded. */
  private boolean otherObjectsLoaded = false;

  /**
   * Project properties. Implemented as a PropertiesConfiguration so that it can be updated without
   * any change to its original structure
   */
  private CommentedProperties projectProperties = null;

  /** Full path to the projet's resource file. */
  private String projectResourceFileNameAndPath;

  /** Added to the "File open" dialog, to force the reimport of all layers in the database. */
  private JCheckBox reImportCheckBox = new JCheckBox();

  /** Lines and services editor. */
  private ServiceEditor serviceEditor;

  /** Properties file that contains the styles for the nodes and links. */
  private Properties stylesProperties;

  /** Scale of view at starting time. */
  private float initialScale;

  /** Lat/Lon of center point at starting time. */
  private LatLonPoint initialCenterPoint;

  private static Toolkit toolKit = Toolkit.getDefaultToolkit();

  /**
   * The constructor just needs to know the frame the project will be displayed on.
   *
   * @param nodusMapPanel The Nodus map panel.
   */
  public NodusProject(NodusMapPanel nodusMapPanel) {
    this.nodusMapPanel = nodusMapPanel;
  }

  /**
   * Adds OpenMap layers to the project. All kinds of layers can be added to the display. These
   * layers are described in a property file. See OpenMap documentation for more details on the
   * available layer types and the way they must be described in the property file.
   *
   * @param props Properties that contains the description of the layers.
   */
  public void addOpenMapLayers(Properties props) {
    // Test if valid openmap file
    String s = props.getProperty(NodusC.PROP_OPENMAP_LAYERS);

    if (s == null) {
      return;
    }

    // Fetch the path to the project
    String projectPath = localProperties.getProperty(NodusC.PROP_PROJECT_DOTPATH);

    // Upgrade layer class names
    props = CheckForOM5.upgradeApiNames(props);

    // Merge this property file with the local property
    Enumeration<?> enumerator = props.propertyNames();

    for (; enumerator.hasMoreElements(); ) {
      String propName = (String) enumerator.nextElement();
      String propValue = (String) props.get(propName);

      // Add project path to resource file name that starts with "./" (project's directory)

      if (propValue.startsWith(".")) {
        propValue = projectPath + propValue.substring(2, propValue.length());
      }
      localProperties.setProperty(propName, propValue);
    }

    List<String> startuplayers;
    List<String> layersValue;

    layersValue =
        PropUtils.parseSpacedMarkers(localProperties.getProperty(NodusC.PROP_OPENMAP_LAYERS));
    startuplayers =
        PropUtils.parseSpacedMarkers(
            localProperties.getProperty(NodusC.PROP_OPENMAP_STARTUPLAYERS));

    Layer[] layers = LayerHandler.getLayers(layersValue, startuplayers, localProperties);
    for (Layer layer : layers) {
      layer.setAddAsBackground(true);
      nodusMapPanel.getLayerHandler().addLayer(layer);
    }

    nodusMapPanel.getLayerHandler().setLayers();
  }

  /**
   * Removes all the objects of a layer.
   *
   * @param layerName Tha name of the layer to clear.
   */
  public void clearLayer(String layerName) {
    NodusEsriLayer layer = getLayer(layerName);
    if (layer == null) {
      System.err.println("Layer " + layerName + " not found.");
      return;
    }

    // remove all the records
    int n = layer.getEsriGraphicList().size();
    for (int i = 0; i < n; i++) {
      layer.removeRecord(0);
    }
  }

  /**
   * Closes the project. If the network is modified, the user is asked to save or not the changes in
   * the shape files and their associated .dbf files.
   */
  public void close() {
    if (isOpen) {
      nodusMapPanel.setBusy(true);
      nodusMapPanel.getMenuFile().setEnabled(false);

      // Close all the open children frames
      Frame[] frame = Frame.getFrames();
      for (Frame element : frame) {
        if (element != nodusMapPanel.getMainFrame()) {
          element.setVisible(false);
        }
      }

      Layer[] layer = nodusMapPanel.getLayerHandler().getLayers();

      String layerOrder = "";
      for (Layer element : layer) {
        Container c = element.getPalette();
        layerOrder += element.getName() + ",";
        if (c != null) {
          c.setVisible(false);
        }
      }

      // Save the current settings in the property file
      float scale = nodusMapPanel.getMapBean().getScale();
      LatLonPoint.Double llp = (LatLonPoint.Double) nodusMapPanel.getMapBean().getCenter();
      this.setLocalProperty(NodusC.PROP_MAP_SCALE, scale);
      this.setLocalProperty(NodusC.PROP_MAP_LATITUDE, llp.getLatitude());
      this.setLocalProperty(NodusC.PROP_MAP_LONGITUDE, llp.getLongitude());

      Color color = (Color) nodusMapPanel.getMapBean().getBckgrnd();
      this.setLocalProperty(NodusC.PROP_MAP_BACKGROUNDCOLOR, Integer.toString(color.getRGB()));
      this.setLocalProperty(
          NodusC.PROP_PROJECTION, nodusMapPanel.getMapBean().getProjection().getClass().getName());

      this.setLocalProperty(NodusC.PROP_ACTIVE_MOUSE_MODE, nodusMapPanel.getActiveMouseMode());

      this.setLocalProperty(NodusC.PROP_MAP_ORDER, layerOrder);

      if (nodeLayers != null && linkLayers != null) {

        // Save the Services
        serviceEditor.close();

        if (isDirty()) {

          int answer =
              JOptionPane.showConfirmDialog(
                  null,
                  i18n.get(
                      NodusProject.class, "Commit_changes_to_layers", "Commit changes to layers?"),
                  i18n.get(NodusProject.class, "Network_was_modified", "Network was modified"),
                  JOptionPane.YES_NO_OPTION);

          if (answer == JOptionPane.YES_OPTION) {
            saveEsriLayers();
          } else {
            /*
             * Drop the sql tables as they are changed and that the user don't want to save these
             * changes. They will be reimported when project will be reopened
             */
            rollBack();
          }
        }

        // Close JDBC connection. Compact it if needed
        try {
          if (!jdbcConnection.getAutoCommit()) {
            jdbcConnection.commit();
          }

          // Ask if a "shutdown compact" must be performed as this can take a while
          if (JDBCUtils.getDbEngine() == JDBCUtils.DB_HSQLDB
              || JDBCUtils.getDbEngine() == JDBCUtils.DB_H2) {

            if (Boolean.parseBoolean(getLocalProperty(NodusC.PROP_SHUTDOWN_COMPACT, "true"))) {

              int answer =
                  JOptionPane.showConfirmDialog(
                      null,
                      i18n.get(NodusProject.class, "AskForCompact", "Compact database?"),
                      NodusC.APPNAME,
                      JOptionPane.YES_NO_OPTION);

              if (answer == JOptionPane.YES_OPTION) {
                nodusMapPanel.setText(
                    i18n.get(NodusProject.class, "Compacting", "Compacting database..."));

                SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
                Thread work =
                    new Thread() {
                      public void run() {
                        JDBCUtils.shutdownCompact();
                        loop.exit();
                      }
                    };

                work.start();
                loop.enter();
              }
            }
          }

          jdbcConnection.close();
        } catch (Exception e) {
          e.printStackTrace();
        }

        // Reset JDBCUtils
        JDBCUtils.setConnection(null);

        // Save time stamps of dbf files
        for (NodusEsriLayer nodeLayer : nodeLayers) {
          String key = nodeLayer.getTableName() + NodusC.PROP_DOTLASTMODIFIED;
          File f =
              new File(
                  getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                      + nodeLayer.getTableName()
                      + NodusC.TYPE_DBF);
          setLocalProperty(key, f.lastModified());
        }

        for (NodusEsriLayer linkLayer : linkLayers) {
          String key = linkLayer.getTableName() + NodusC.PROP_DOTLASTMODIFIED;
          File f =
              new File(
                  getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                      + linkLayer.getTableName()
                      + NodusC.TYPE_DBF);
          setLocalProperty(key, f.lastModified());
        }

        // Save project local properties
        if (localProperties != null) {
          try {
            for (NodusEsriLayer element : nodeLayers) {
              localProperties.setProperty(
                  element.getTableName() + NodusC.PROP_VISIBLE,
                  Boolean.toString(element.isVisible()));
            }

            for (NodusEsriLayer element : linkLayers) {
              localProperties.setProperty(
                  element.getTableName() + NodusC.PROP_VISIBLE,
                  Boolean.toString(element.isVisible()));
            }

            if (getNodusMapPanel().isHighlightedAreaLayerAdded()) {
              localProperties.setProperty(NodusC.PROP_ADD_HIGHLIGHTED_AREA, Boolean.toString(true));
              localProperties.setProperty(
                  NodusC.PROP_DISPLAY_HIGHLIGHTED_AREA,
                  Boolean.toString(getNodusMapPanel().isHighlightedAreaLayerVisible()));
            } else {
              localProperties.setProperty(
                  NodusC.PROP_ADD_HIGHLIGHTED_AREA, Boolean.toString(false));
            }

            localProperties.setProperty(
                labelsLayer.getName() + NodusC.PROP_VISIBLE,
                Boolean.toString(labelsLayer.isVisible()));
            localProperties.setProperty(
                NodusC.PROP_DISPLAY_POLITICAL_BOUNDARIES,
                Boolean.toString(nodusMapPanel.isPoliticalBoundariesVisible()));
            localProperties.setProperty(
                NodusC.PROP_ADD_POLITICAL_BOUNDARIES,
                Boolean.toString(nodusMapPanel.isPoliticalBoundariesAdded()));

            localProperties.store(
                new FileOutputStream(projectResourceFileNameAndPath + NodusC.TYPE_LOCAL), null);
            localProperties = null;
          } catch (IOException ex) {
            System.err.println(
                "Caught IOException saving resources: " + projectResourceFileNameAndPath);
          }
        }
      }

      // Now remove all the layers, but leave political boundaries visible
      getNodusMapPanel().displayHighlightedAreaLayer(false, false);
      getNodusMapPanel().displayPoliticalBoundaries(true, true);

      labelsLayer.setRemovable(true);

      if (nodeLayers != null) {
        for (NodusEsriLayer nodeLayer : nodeLayers) {
          nodeLayer.setRemovable(true);
        }
      }

      if (linkLayers != null) {
        for (NodusEsriLayer linkLayer : linkLayers) {
          linkLayer.setRemovable(true);
        }
      }

      nodusMapPanel.getLayerHandler().removeAll();
      nodusMapPanel.getLayerHandler().setLayers(new Layer[0]);

      // Reset projection to default values
      nodusMapPanel.resetMap();
      Projection projection = nodusMapPanel.getMapBean().getProjection();
      Point2D ctr = projection.getCenter();
      Projection newProj =
          new ProjectionFactory()
              .makeProjection(
                  Mercator.class.getName(),
                  ctr,
                  projection.getScale(),
                  projection.getWidth(),
                  projection.getHeight());
      nodusMapPanel.getMapBean().setProjection(newProj);

      // Enable some menu items and remove project plugins menus
      nodusMapPanel.removeProjectPlugins();
      nodusMapPanel.enableMenus(false);

      otherNodeNumbers.clear();
      otherLinkNumbers.clear();
      otherObjectsLoaded = false;

      // Reset Classpath
      // ClassPathHacker.setClassPath(oldClasspath);

      ProjectLocker.releaseLock();

      isOpen = false;

      // Reset rendering scale threshold
      nodusMapPanel.setRenderingScaleThreshold(-1);

      // Close the log file for this project
      loggerHandler.flush();
      loggerHandler.close();
      Nodus.nodusLogger.removeHandler(loggerHandler);

      nodusMapPanel.setBusy(false);
      nodusMapPanel.getMenuFile().setEnabled(true);

      nodusMapPanel.resetTitle();
      nodusMapPanel.resetText();
      nodusMapPanel.getNodusLayersPanel().enableButtons(false);

      // Reset view to initial values
      getNodusMapPanel().getMapBean().setScale(initialScale);
      getNodusMapPanel().getMapBean().setCenter(initialCenterPoint);
    }
  }

  /**
   * Returns the full path to the cost functions for the current scenario.
   *
   * @return Full path and file name
   */
  public String getCurrentCostFunctionsFileName() {

    int scenario = getCurrentScenario();
    String costFunctionsFileName = getLocalProperty(NodusC.PROP_COST_FUNCTIONS + scenario, null);

    if (costFunctionsFileName == null) {
      costFunctionsFileName = getLocalProperty(NodusC.PROP_COST_FUNCTIONS, null);
    }

    if (costFunctionsFileName == null) {
      costFunctionsFileName = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.TYPE_COSTS;
    }

    return getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + costFunctionsFileName;
  }

  /**
   * Returns the ID of the current scenario.
   *
   * @return The ID of the current scenario.
   */
  public int getCurrentScenario() {
    return getLocalProperty(NodusC.PROP_SCENARIO, 0);
  }

  /**
   * A set of default styles is embedded in the source tree. These are loaded if no
   * "shapes.properties" file is found in the project directory.
   *
   * @return The default style properties.
   */
  private Properties getDefaultStyle() {
    Properties p = new Properties();
    try {
      InputStream in = NodusMapPanel.class.getResource("shapes.properties").openStream();
      p.load(in);
    } catch (IOException ioe) { // Should never happen
      ioe.printStackTrace();
      return null;
    }
    return p;
  }

  /**
   * Returns the node or link layer which pretty name or table name corresponds to the given name.
   *
   * @param name Pretty name or table name of the layer.
   * @return The corresponding layer or null if not found.
   */
  public NodusEsriLayer getLayer(String name) {
    for (NodusEsriLayer element : nodeLayers) {
      if (element.getName().equalsIgnoreCase(name)) {
        return element;
      }
      if (element.getTableName().equalsIgnoreCase(name)) {
        return element;
      }
    }

    for (NodusEsriLayer element : linkLayers) {
      if (element.getName().equalsIgnoreCase(name)) {
        return element;
      }
      if (element.getTableName().equalsIgnoreCase(name)) {
        return element;
      }
    }

    return null;
  }

  /**
   * Returns the array of link layers of the project.
   *
   * @return NodusEsriLayer[]
   */
  public NodusEsriLayer[] getLinkLayers() {
    return linkLayers;
  }

  /**
   * Returns the value associated to a given key in the project's local properties.
   *
   * @param key The key of the property.
   * @return The value of the property associated to the given key.
   */
  public String getLocalProperty(String key) {
    return localProperties.getProperty(key);
  }

  /**
   * Returns the value associated to a given key in the projects local properties. A default value
   * is also passed as a parameter
   *
   * @param key The key of the property.
   * @param defValue The value returned if the property is not found.
   * @return The value of the property.
   */
  public boolean getLocalProperty(String key, boolean defValue) {
    return PropUtils.booleanFromProperties(localProperties, key, defValue);
  }

  /**
   * Returns the value associated to a given key in the project's local properties. A default value
   * is also passed as a parameter
   *
   * @param key The key of the property.
   * @param defValue The value returned if the property is not found.
   * @return The value of the property.
   */
  public double getLocalProperty(String key, double defValue) {
    return PropUtils.doubleFromProperties(localProperties, key, defValue);
  }

  /**
   * Returns the value associated to a given key in the project's local properties. A default value
   * is also passed as a parameter
   *
   * @param key The key of the property.
   * @param defValue The value returned if the property is not found.
   * @return The value of the property.
   */
  public float getLocalProperty(String key, float defValue) {
    return PropUtils.floatFromProperties(localProperties, key, defValue);
  }

  /**
   * Returns the value associated to a given key in the project's local properties. A default value
   * is also passed as a parameter
   *
   * @param key The key of the property.
   * @param defValue The value returned if the property is not found.
   * @return The value of the property.
   */
  public int getLocalProperty(String key, int defValue) {
    return PropUtils.intFromProperties(localProperties, key, defValue);
  }

  /**
   * Returns the value associated to a given key in the project's local properties. A default value
   * is also passed as a parameter
   *
   * @param key The key of the property.
   * @param defValue The value returned if the property is not found.
   * @return The value of the property.
   */
  public String getLocalProperty(String key, String defValue) {
    return localProperties.getProperty(key, defValue);
  }

  /**
   * Returns the location layer.
   *
   * @return The location layer.
   */
  public NodusLocationLayer getLocationLayer() {
    return labelsLayer;
  }

  /**
   * Returns the main frame of the application.
   *
   * @return The application main frame.
   */
  public Frame getMainFrame() {
    return nodusMapPanel.getMainFrame();
  }

  /**
   * Returns the JDBC connection to the database associated to this project.
   *
   * @return The JDBC connection used by the project.
   */
  public Connection getMainJDBCConnection() {
    try {
      // Connection could be closed...
      if (jdbcConnection == null || jdbcConnection.isClosed()) {
        jdbcConnection =
            DriverManager.getConnection(
                localProperties.getProperty(NodusC.PROP_JDBC_URL),
                localProperties.getProperty(NodusC.PROP_JDBC_USERNAME),
                localProperties.getProperty(NodusC.PROP_JDBC_PASSWORD));
        jdbcConnection.setAutoCommit(false);
      }
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
      JOptionPane.showMessageDialog(
          null,
          MessageFormat.format(
              i18n.get(
                  NodusProject.class,
                  "Could_not_get_connection_on",
                  "Could not get connection on {0}"),
              localProperties.getProperty(NodusC.PROP_JDBC_URL)),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);

      return null;
    }

    return jdbcConnection;
  }

  /**
   * Returns the number of styles of a given OMGraphic type (node or link).
   *
   * @param omg An OMGraphic.
   * @return The ID of its style.
   */
  public int getNbStyles(OMGraphic omg) {
    if (omg instanceof EsriPolyline) {
      if (linkStyle == null) {
        return 0;
      }

      return linkStyle.length;
    } else {
      if (nodeStyle == null) {
        return 0;
      }

      return nodeStyle.length;
    }
  }

  /**
   * Searches a new unique ID for a node or a link. Existent ID's are searched in the loaded layers,
   * but also in all the Nodus compatible layers found in the project directory.
   *
   * @param layer The array of links or nodes layers.
   * @return The ID of the new link or node.
   */
  private int getNewId(NodusEsriLayer[] layer) {
    int num = 1;
    boolean foundNewNumber = false;

    // Get the already given numbers in external layers
    HashMap<Integer, Integer> otherObjects;
    if (layer[0].getType() == ShapeConstants.SHAPE_TYPE_POINT) {
      otherObjects = nodusMapPanel.getNodusProject().getOtherNodeNumbers();
    } else {
      otherObjects = nodusMapPanel.getNodusProject().getOtherLinkNumbers();
    }

    while (!foundNewNumber) {

      // Test if number was already given in an external layer
      if (otherObjects.get(num) != null) {
        num++;
        continue;
      }

      int currentLayer = 0;

      for (NodusEsriLayer element : layer) {
        if (!element.numExists(num)) {
          currentLayer++;
        }
      }

      if (currentLayer == layer.length) {
        foundNewNumber = true;
      } else {
        num++;
      }
    }
    return num;
  }

  /**
   * Get a new unique ID for a new link.
   *
   * @return new link ID
   */
  public int getNewLinkId() {
    return getNewId(linkLayers);
  }

  /**
   * Get a new unique ID for a new node.
   *
   * @return new node ID
   */
  public int getNewNodeId() {
    return getNewId(nodeLayers);
  }

  /**
   * Returns the array of node layers.
   *
   * @return NodusEsriLayer[]
   */
  public NodusEsriLayer[] getNodeLayers() {
    return nodeLayers;
  }

  /**
   * Returns the Nodus map panel.
   *
   * @return The MapPanel of the main frame.
   */
  public NodusMapPanel getNodusMapPanel() {
    return nodusMapPanel;
  }

  /**
   * Returns an hashmap that contains the ID's of the links that are found in the Nodus compatible
   * layers, not loaded in the project.
   *
   * @return The hashmap of "other" links.
   */
  public HashMap<Integer, Integer> getOtherLinkNumbers() {
    return otherLinkNumbers;
  }

  /**
   * Returns an hashmap that contains the ID's of the nodes that are found in the Nodus compatible
   * layers, not loaded in the project.
   *
   * @return The hashmap of "other" nodes.
   */
  public HashMap<Integer, Integer> getOtherNodeNumbers() {
    return otherNodeNumbers;
  }

  /**
   * Returns the file name of the project.
   *
   * @return The file name of the project, or null if no,project is loaded
   */
  public String getName() {
    if (isOpen()) {
      return getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.TYPE_NODUS;
    } else {
      return null;
    }
  }

  /**
   * Returns the value associated to a given key in the project's properties.
   *
   * @param key Key of the property.
   * @return String value of the property.
   */
  public String getProperty(String key) {
    return (String) projectProperties.getProperty(key);
  }

  /**
   * Returns the lines and services editor.
   *
   * @return The editor.
   */
  public ServiceEditor getServiceEditor() {
    return serviceEditor;
  }

  /**
   * Returns the drawing attributed (embedded in a NodusOMGraphic) associated to a given index. The
   * OMGraphic that is passed as parameter is used to determine if the index is related to nodes or
   * links.
   *
   * @param omg A real node or link.
   * @param index The index of the style.
   * @return A NodusOMGraphic representing a style.
   */
  public NodusOMGraphic getStyle(OMGraphic omg, int index) {
    if (omg instanceof EsriPolyline) {
      if (linkStyle == null) {
        return null;
      }

      return linkStyle[index];
    } else {
      if (nodeStyle == null) {
        return null;
      }

      return nodeStyle[index];
    }
  }

  /**
   * Loads the styles used to render real nodes and links.
   *
   * @return The loaded properties or null on error
   */
  public Properties getStyleProperties() {

    String fileName = null;

    // Test if there is a shape.properties file specific to this project
    if (localProperties != null) { // project didn't open
      fileName = localProperties.getProperty(NodusC.PROP_PROJECT_DOTPATH) + "shapes.properties";
      File f = new File(fileName);

      if (!f.exists()) {
        // Use embedded styles...
        return getDefaultStyle();
      }
    } else {
      return getDefaultStyle();
    }

    // Load the project specific styles
    Properties prop = new Properties();
    try {
      prop.load(new FileInputStream(fileName));
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
      return null;
    } catch (IOException ex) {
      ex.printStackTrace();
      return null;
    }

    return prop;
  }

  /** Import the DBF tables specified in the project if needed. */
  private void importDBFTables() {

    String tablesToImport = projectProperties.getProperty(NodusC.PROP_IMPORT_TABLES, null);
    NodusProject thisProject = this;

    if (tablesToImport == null) {
      // Could be a project with the old property name
      tablesToImport = projectProperties.getProperty("importTables", null);
      if (tablesToImport == null) {
        return;
      }
    }

    StringTokenizer st = new StringTokenizer(tablesToImport);
    while (st.hasMoreTokens()) {
      final String currentTable = st.nextToken();
      // Import only if not exists
      if (!JDBCUtils.tableExists(currentTable)) {
        SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
        Thread work =
            new Thread() {
              public void run() {
                getNodusMapPanel()
                    .setText(
                        MessageFormat.format(
                            i18n.get(
                                NodusProject.class, "Importing", "Importing \"{0}\" in database"),
                            currentTable));
                ImportDBF.importTable(thisProject, currentTable);
                loop.exit();
              }
            };

        work.start();
        loop.enter();
      }
    }
  }

  /**
   * Tests if the .dbf file associated to a layer was modified since its last import in the
   * database.
   *
   * @param layerName The name of shapefile containing the layer.
   * @return False if not changed or never loaded before.
   */
  private boolean isDbfModified(String layerName) {

    String key = layerName + NodusC.PROP_DOTLASTMODIFIED;
    String value = getLocalProperty(key, null);

    if (value == null) {
      return false;
    }

    File f = new File(getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + layerName + NodusC.TYPE_DBF);
    if (f.lastModified() == Long.parseLong(value)) {
      return false;
    }

    return true;
  }

  /**
   * Returns true if any node or link layer is dirty (modified).
   *
   * @return True if at least one layer was modified.
   */
  public boolean isDirty() {
    boolean dirty = false;

    for (NodusEsriLayer element : nodeLayers) {
      if (element.isDirty()) {
        dirty = true;
      }
    }

    for (NodusEsriLayer element : linkLayers) {
      if (element.isDirty()) {
        dirty = true;
      }
    }

    return dirty;
  }

  /**
   * Returns the state of the project : open or not.
   *
   * @return True if the project is loaded.
   */
  public boolean isOpen() {
    return isOpen;
  }

  /**
   * Returns true if the name corresponds to a layer of the project.
   *
   * @param layerName The name of the shapefile containing the layer.
   * @param type SHAPE_TYPE_POINT or SHAPE_TYPE_ARC.
   * @return True if the layer is not associated to the project.
   */
  private boolean isOtherNodusLayer(String layerName, int type) {

    NodusEsriLayer[] layer;

    if (type == SHAPE_TYPE_POINT) {
      layer = nodeLayers;
    } else {
      layer = linkLayers;
    }

    for (NodusEsriLayer element : layer) {
      if (element.getTableName().equals(layerName)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if the ID's of the nodes or links of the "non project" layers found in the
   * project's directory are loaded.
   *
   * @return True if the ID's are loaded.
   */
  public boolean isOtherObjectsLoaded() {
    return otherObjectsLoaded;
  }

  /**
   * Verifies that the existing virtual network tables are compatible with this version of Nodus.
   *
   * @return True if the tables are compatible.
   */
  private boolean isValidVirtualNetworkVersion() {

    /*
     * The test was perhaps already performed
     */
    int version = getLocalProperty(NodusC.PROP_VIRTUAL_NETWORK_VERSION, 0);
    if (version == 4) {
      return true;
    }

    for (int scenario = 0; scenario < NodusC.MAXSCENARIOS; scenario++) {

      // Get table name for scenario
      String tableName = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
      tableName = getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + scenario;
      tableName = JDBCUtils.getCompliantIdentifier(tableName);
      if (!JDBCUtils.tableExists(tableName)) {
        continue;
      }

      if (!JDBCUtils.hasField(tableName, NodusC.DBF_SERVICE1)) {
        // This is a version 2 virtual network
        JOptionPane.showMessageDialog(
            null,
            "This project has virtual network version 2 tables.\nPlease upgrade to version 4.",
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);
        return false;
      }

      if (!JDBCUtils.hasField(tableName, NodusC.DBF_TIME)) {
        // This is a version 3 virtual network
        JOptionPane.showMessageDialog(
            null,
            "This project has virtual network version 3 tables.\nPlease upgrade to version 4.",
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);
        return false;
      }

      /*
       * The table is OK
       */
      setLocalProperty(NodusC.PROP_VIRTUAL_NETWORK_VERSION, 4);
      return true;
    }

    /*
     * No virtual network table exist.
     */
    setLocalProperty(NodusC.PROP_VIRTUAL_NETWORK_VERSION, 4);
    return true;
  }

  /**
   * Loads the numbers of the objects of a layer in the given HashMap.
   *
   * @param layerName The name of the shapefile containing the layer.
   * @param The HashMap used to store the ID's
   */
  private void loadObjectIDs(String layerName, HashMap<Integer, Integer> map) {

    try {
      DBFReader dbfReader =
          new DBFReader(
              getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + layerName + NodusC.TYPE_DBF);
      Object[] o;
      if (dbfReader.isOpen()) {
        while (dbfReader.hasNextRecord()) {
          o = dbfReader.nextRecord();
          int num = JDBCUtils.getInt(o[NodusC.DBF_IDX_NUM]);
          map.put(num, num);
        }
      }
      dbfReader.close();
    } catch (DBFException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Scan the directory to find other Nodus compatible shapefiles, which object ID's will be stored.
   */
  private void loadOtherLayersObjectNumbers() {
    otherObjectsLoaded = false;
    // Scan all node layers
    String[] layerName =
        ProjectFilesTools.getAvailableLayers(
            getLocalProperty(NodusC.PROP_PROJECT_DOTPATH), SHAPE_TYPE_POINT);
    for (String element : layerName) {
      if (isOtherNodusLayer(element, SHAPE_TYPE_POINT)) {
        loadObjectIDs(element, otherNodeNumbers);
      }
    }

    // Scan all link layers
    layerName =
        ProjectFilesTools.getAvailableLayers(
            getLocalProperty(NodusC.PROP_PROJECT_DOTPATH), SHAPE_TYPE_POLYLINE);
    for (String element : layerName) {
      if (isOtherNodusLayer(element, SHAPE_TYPE_POLYLINE)) {
        loadObjectIDs(element, otherLinkNumbers);
      }
    }
    otherObjectsLoaded = true;
  }

  /**
   * Loads the styles for a given type of objects. Prefix can be "node" or "link". The drawing
   * attributes are stored in "shape.properties". This file can be modified to add or remove styles
   * for nodes or links. If no such file is found in the project's directory, the default file,
   * provided in the Nodus 'data' dir will be used
   *
   * @param prefix "node" or "link".
   * @return An array of NodusOMGraphics containing the styles.
   */
  public NodusOMGraphic[] loadStyles(String prefix) {
    if (stylesProperties == null) {
      return null;
    }

    LinkedList<NodusOMGraphic> ll = new LinkedList<>();
    NodusOMGraphic model;

    // Create default style
    ll.add(new NodusOMGraphic());

    // Open the properties file
    NodusDrawingAttributes da = new NodusDrawingAttributes();
    int index = 1;

    while (true) {
      if (stylesProperties.getProperty(prefix + index + ".name", null) == null) {
        break;
      }

      da.setProperties(prefix + index + ".", stylesProperties);
      model = new NodusOMGraphic();
      model.setStroke(da.getStroke());
      model.setDefaultLinePaint(da.getDefaultLinePaint());
      model.setFillPaint(da.getFillPaint());
      model.setLinePaint(da.getLinePaint());
      model.setMatted(da.isMatted());
      model.setMattingPaint(da.getMattingPaint());
      model.setRadius(da.getRadius());
      model.setOval(da.getOval());
      model.setAltFillPaint(da.getAltFillPaint());
      model.setAltLinePaint(da.getAltLinePaint());
      model.setAltMattingPaint(da.getAltMattingPaint());
      ll.add(model);
      index++;
    }

    return ll.toArray(new NodusOMGraphic[ll.size()]);
  }

  /**
   * Opens a file chooser to select a project to open. Returns null if no project is selected. Else
   * the canonical path of the project's property file is returned.
   *
   * @return The path to the project or null if canceled or on error.
   */
  public String getProject() {
    // Find a project file (is a properties file with a .nodus extension)
    JFileChooser fileChooser =
        new JFileChooser(
            nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_LAST_PATH, "."));

    fileChooser.setFileFilter(
        new NodusFileFilter(
            NodusC.TYPE_NODUS, i18n.get(NodusProject.class, "Nodus_projects", "Nodus projects")));

    // Add checkbox to file chooser
    reImportCheckBox.setText(i18n.get(NodusProject.class, "Reimport", "Re-import"));
    reImportCheckBox.setOpaque(false);
    reImportCheckBox.setSelected(false);
    reImportCheckBox.setVerticalAlignment(SwingConstants.BOTTOM);

    fileChooser.setAccessory(reImportCheckBox);

    int returnVal = fileChooser.showOpenDialog(null);

    if (returnVal == JFileChooser.APPROVE_OPTION) {
      try {
        return fileChooser.getSelectedFile().getCanonicalPath();
      } catch (IOException ex1) {
        ex1.printStackTrace();
      }
    }

    return null;
  }

  /**
   * Loads a project, which canonical file name is passed as parameter.
   *
   * @param projectName The canonical path to the Nodus project file.
   */
  public void openProject(String projectName) throws OutOfMemoryError {

    projectResourceFileNameAndPath = projectName;

    // Save current view
    initialScale = getNodusMapPanel().getMapBean().getScale();
    initialCenterPoint = (LatLonPoint) getNodusMapPanel().getMapBean().getCenter();

    projectProperties = new CommentedProperties();

    try {
      projectProperties.load(new FileInputStream(projectResourceFileNameAndPath));
    } catch (FileNotFoundException e) {
      // Nodus may not have access to some folders on Mac...
      if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("operation not permitted")) {
          JOptionPane.showMessageDialog(
              null,
              MessageFormat.format(
                  i18n.get(
                      NodusProject.class,
                      "Operation_not_permitted",
                      "Cannot open {0}: operation not permitted"),
                  projectResourceFileNameAndPath),
              NodusC.APPNAME,
              JOptionPane.ERROR_MESSAGE);
          return;
        }
      }
      projectProperties = null;
    } catch (IOException e) {
      projectProperties = null;
    }

    if (projectProperties == null) { // File doesn't exist

      // Create a minimalist empty project
      int answer =
          JOptionPane.showConfirmDialog(
              nodusMapPanel,
              i18n.get(
                  NodusProject.class, "Create_new_empty_project?", "Create new empty project?"),
              i18n.get(NodusProject.class, "Project_doesnt_exist", "Project doesn't exist"),
              JOptionPane.YES_NO_OPTION);

      if (answer == JOptionPane.YES_OPTION) {
        try {
          if (projectResourceFileNameAndPath.lastIndexOf(NodusC.TYPE_NODUS) == -1) {
            projectResourceFileNameAndPath += NodusC.TYPE_NODUS;
          }

          projectProperties = new CommentedProperties();
          projectProperties.setProperty(NodusC.PROP_NETWORK_NODES, "");
          projectProperties.setProperty(NodusC.PROP_NETWORK_LINKS, "");
          projectProperties.store(new FileOutputStream(projectResourceFileNameAndPath), null);
        } catch (IOException e) {
          System.err.println("Caught IOException creating " + projectResourceFileNameAndPath);
        }
      } else {
        return;
      }
    }

    nodusMapPanel.setBusy(true);

    // Find out path and filename
    String projectPath = projectResourceFileNameAndPath;
    File f = new File(projectResourceFileNameAndPath);
    String name = f.getName();
    projectPath = projectPath.substring(0, projectPath.lastIndexOf(name) - 1) + File.separator;

    // Try to add the ".local" properties that contain previous saved values
    localProperties = new Properties();

    try {
      localProperties.load(new FileInputStream(projectResourceFileNameAndPath + NodusC.TYPE_LOCAL));
    } catch (IOException ex) {
      // Nothing to do. The .nodus.local is not mandatory.
    }

    // Merge the properties
    Enumeration<Object> keys = projectProperties.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      String value = projectProperties.getProperty(key);
      localProperties.setProperty(key, value);
    }

    localProperties.setProperty(NodusC.PROP_PROJECT_CANONICAL_NAME, projectResourceFileNameAndPath);
    localProperties.setProperty(NodusC.PROP_PROJECT_DOTPATH, projectPath);
    localProperties.setProperty(
        NodusC.PROP_PROJECT_DOTNAME, name.substring(0, name.indexOf(NodusC.TYPE_NODUS)));
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_LAST_PATH, projectPath);
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_LAST_PROJECT, name);

    // Try to lock this project
    if (!ProjectLocker.createLock(this)) {
      JOptionPane.showMessageDialog(
          null,
          i18n.get(NodusProject.class, "Project_already_open", "This project is already open"),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);
      nodusMapPanel.setBusy(false);
      return;
    }

    // Test the validity of the dbf files in the project
    if (!ProjectFilesTools.isValidProject(localProperties)) {
      nodusMapPanel.setBusy(false);
      nodusMapPanel.getMenuFile().setEnabled(true);
      return;
    }

    nodusMapPanel.getMenuFile().setEnabled(false);

    nodusMapPanel.loadPlugins(projectPath, true);

    // Load styles from property file
    stylesProperties = getStyleProperties();
    nodeStyle = loadStyles("node");
    linkStyle = loadStyles("link");

    // Open a connection to a JDBC compliant db manager

    // Test if this project is already associated with a db
    int defaultEmbeddedDbms = getLocalProperty(NodusC.PROP_EMBEDDED_DB, -1);
    if (defaultEmbeddedDbms == -1) {
      // Take system wide default embedded db
      String s =
          nodusMapPanel
              .getNodusProperties()
              .getProperty(NodusC.PROP_EMBEDDED_DB, "" + JDBCUtils.DB_HSQLDB);
      try {
        defaultEmbeddedDbms = Integer.parseInt(s);
      } catch (NumberFormatException e) {
        defaultEmbeddedDbms = JDBCUtils.DB_HSQLDB;
      }
    }

    String defaultDriver = "";
    String defaultURL = "";
    String defaultUser = "";
    String defaultPassword = "";

    switch (defaultEmbeddedDbms) {
      case JDBCUtils.DB_HSQLDB:
        defaultDriver = "org.hsqldb.jdbcDriver";
        defaultURL =
            "jdbc:hsqldb:file:"
                + projectPath
                + localProperties.getProperty(NodusC.PROP_PROJECT_DOTNAME)
                + "_hsqldb;shutdown=true";
        defaultUser = "SA";
        break;
      case JDBCUtils.DB_H2:
        defaultDriver = "org.h2.Driver";
        defaultURL =
            "jdbc:h2:" + projectPath + localProperties.getProperty(NodusC.PROP_PROJECT_DOTNAME);
        break;
      case JDBCUtils.DB_DERBY:
        System.setProperty("derby.system.home", projectPath);
        System.setProperty("derby.system.durability", "test");

        defaultDriver = "org.apache.derby.jdbc.EmbeddedDriver";
        defaultURL =
            "jdbc:derby:"
                + projectPath
                + localProperties.getProperty(NodusC.PROP_PROJECT_DOTNAME)
                + ";create=true";
        defaultUser = "dbuser";
        defaultPassword = "dbuserpwd";
        break;
      case JDBCUtils.DB_SQLITE:
        defaultDriver = "org.sqlite.JDBC";
        defaultURL =
            "jdbc:sqlite:"
                + projectPath
                + localProperties.getProperty(NodusC.PROP_PROJECT_DOTNAME)
                + ".sqlite";
        break;
      default:
        break;
    }

    // Store default DB in project
    setLocalProperty(NodusC.PROP_EMBEDDED_DB, defaultEmbeddedDbms);

    /*
     * Another DB engine can be specified in the project file
     */
    String jdbcDriver = projectProperties.getProperty(NodusC.PROP_JDBC_DRIVER, defaultDriver);
    String userName = projectProperties.getProperty(NodusC.PROP_JDBC_USERNAME, defaultUser);
    String password = projectProperties.getProperty(NodusC.PROP_JDBC_PASSWORD, defaultPassword);
    String jdbcURL = projectProperties.getProperty(NodusC.PROP_JDBC_URL, defaultURL);

    localProperties.setProperty(NodusC.PROP_JDBC_USERNAME, userName);
    localProperties.setProperty(NodusC.PROP_JDBC_PASSWORD, password);
    localProperties.setProperty(NodusC.PROP_JDBC_DRIVER, jdbcDriver);
    localProperties.setProperty(NodusC.PROP_JDBC_URL, jdbcURL);
    try {

      Class.forName(jdbcDriver).getDeclaredConstructor().newInstance();

      jdbcConnection = getMainJDBCConnection();
      if (jdbcConnection == null) {
        nodusMapPanel.setBusy(false);
        ProjectLocker.releaseLock();
        nodusMapPanel.getMenuFile().setEnabled(true);
        return;
      }

    } catch (Exception ex) {
      nodusMapPanel.setBusy(false);
      SwingUtilities.invokeLater(
          new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(
                  null, ex.toString(), NodusC.APPNAME, JOptionPane.WARNING_MESSAGE);
            }
          });

      ProjectLocker.releaseLock();
      nodusMapPanel.getMenuFile().setEnabled(true);
      return;
    }

    // Initialize JDBCUtils
    JDBCUtils.setConnection(jdbcConnection);

    // Set some defaults for HSQLDB
    if (JDBCUtils.getDbEngine() == JDBCUtils.DB_HSQLDB) {
      try {
        Statement stmt = jdbcConnection.createStatement();
        stmt.execute("SET DEFAULT TABLE TYPE CACHED");
        stmt.execute("SET PROPERTY \"sql.enforce_size\" true");
        stmt.close();
      } catch (SQLException ex) {
        System.err.println(ex.toString());
        nodusMapPanel.setBusy(false);
        ProjectLocker.releaseLock();
        nodusMapPanel.getMenuFile().setEnabled(true);
        return;
      }
    }

    /* Derby hasn't a ROUND function. Add it */
    if (JDBCUtils.getDbEngine() == JDBCUtils.DB_DERBY) {
      try {
        Statement stmt = jdbcConnection.createStatement();

        String s =
            "create function ROUND (value DOUBLE, precision INTEGER) "
                + "returns DOUBLE language java parameter style java no sql "
                + "external name 'edu.uclouvain.core.nodus.utils.NodusDerbyFunctions.round'";
        stmt.execute(s);
        stmt.close();
      } catch (SQLException ex) {
        // Probably because the function was already added
      }
    }

    // Open the log file for this project
    try {
      loggerHandler = new FileHandler(projectPath + "nodus.log", true);
      loggerHandler.setFormatter(new SimpleFormatter());
      Nodus.nodusLogger.addHandler(loggerHandler);
    } catch (SecurityException e1) {
      e1.printStackTrace();
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    // Add the project's directory to classpath
    // oldClasspath = ClassPathHacker.getClassPath();

    // Test if this project has valid virtual network tables
    if (!isValidVirtualNetworkVersion()) {
      nodusMapPanel.setBusy(false);
      ProjectLocker.releaseLock();
      nodusMapPanel.getMenuFile().setEnabled(true);
      try {
        jdbcConnection.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      return;
    }

    String projectionName =
        this.getLocalProperty(NodusC.PROP_PROJECTION, "com.bbn.openmap.proj.Mercator");
    Projection projection = nodusMapPanel.getMapBean().getProjection();
    Point2D ctr = projection.getCenter();
    Projection newProj =
        nodusMapPanel
            .getMapBean()
            .getProjectionFactory()
            .makeProjection(
                projectionName,
                ctr,
                projection.getScale(),
                projection.getWidth(),
                projection.getHeight());
    nodusMapPanel.getMapBean().setProjection(newProj);

    // Restore the saved view
    int rgb = this.getLocalProperty(NodusC.PROP_MAP_BACKGROUNDCOLOR, Integer.MAX_VALUE);

    if (rgb != Integer.MAX_VALUE) {
      nodusMapPanel.getMapBean().setBckgrnd(new Color(rgb));
    }

    float scale = this.getLocalProperty(NodusC.PROP_MAP_SCALE, Float.MAX_VALUE);
    double latitude = this.getLocalProperty(NodusC.PROP_MAP_LATITUDE, Double.MAX_VALUE);
    double longitude = this.getLocalProperty(NodusC.PROP_MAP_LONGITUDE, Double.MAX_VALUE);
    LatLonPoint.Double llp = new LatLonPoint.Double(latitude, longitude);

    // All the values must have been found in properties to restore view
    if (scale + latitude + longitude < Double.MAX_VALUE) {
      // Prepare map
      nodusMapPanel.getMapBean().setScale(scale);
      nodusMapPanel.getMapBean().setCenter(llp);
      nodusMapPanel.getMapBean().validate();
    }

    // Reset the projection stack
    nodusMapPanel.getProjectionStack().clearStacks(true, true);
    nodusMapPanel.getToolPanel().setVisible(true);

    // Display the build-in political boundaries
    nodusMapPanel.displayPoliticalBoundaries(
        getLocalProperty(NodusC.PROP_ADD_POLITICAL_BOUNDARIES, true),
        getLocalProperty(NodusC.PROP_DISPLAY_POLITICAL_BOUNDARIES, true));

    // Layer that represents the highlighted area to use during assignments
    boolean displayHighlightedArea = getLocalProperty(NodusC.PROP_ADD_HIGHLIGHTED_AREA, false);
    boolean enableHighlightedArea = getLocalProperty(NodusC.PROP_DISPLAY_HIGHLIGHTED_AREA, false);
    getNodusMapPanel().displayHighlightedAreaLayer(displayHighlightedArea, enableHighlightedArea);

    // Create a location layer for the location handlers
    labelsLayer = new NodusLocationLayer();
    labelsLayer.setName(i18n.get(NodusProject.class, "Labels", "Labels"));
    // labelsLayer.setRemovable(false);

    // Position of the layer
    int layerPosition = 0;

    // Create new ESRI layer for the nodes
    name = projectProperties.getProperty(NodusC.PROP_NETWORK_NODES);

    // How many layers?
    StringTokenizer st = new StringTokenizer(name);
    int n = st.countTokens();
    nodeLayers = new NodusEsriLayer[n];
    nodesLocationHandler = new NodusLocationHandler[n];
    n = 0;

    while (st.hasMoreTokens()) {
      final String currentName = st.nextToken();

      // Createlayer n
      String key;
      key = currentName + NodusC.PROP_NAME;
      localProperties.setProperty(key, currentName);

      // Force re-import?
      if (reImportCheckBox.isSelected()) {
        JDBCUtils.dropTable(currentName);
      }

      // Force reimport if dbf file is newer than the last one used in this project
      if (isDbfModified(currentName)) {
        JDBCUtils.dropTable(currentName);
      }

      // Get the pretty name given to the layer. Take shapefile name if none was defined.
      String prettyName =
          projectProperties.getProperty(currentName + NodusC.PROP_PRETTY_NAME, currentName);
      projectProperties.setProperty(currentName + NodusC.PROP_PRETTY_NAME, prettyName);

      nodeLayers[n] = new NodusEsriLayer();
      nodeLayers[n].setName(prettyName);

      final NodusEsriLayer nep = nodeLayers[n];
      final NodusProject _this = this;

      SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
      Thread work =
          new Thread() {
            public void run() {
              nep.setProject(_this, currentName);
              loop.exit();
            }
          };

      work.start();
      loop.enter();

      // Create a new location handler based on the ESRI layer
      nodesLocationHandler[n] = new NodusLocationHandler(nodeLayers[n]);
      nodesLocationHandler[n].setProperties(currentName, localProperties);
      nodesLocationHandler[n].setLayer(labelsLayer);
      nodeLayers[n].setLocationHandler(nodesLocationHandler[n]);
      nodeLayers[n].doPrepare();

      nodusMapPanel.getLayerHandler().addLayer(nodeLayers[n], layerPosition++);

      // Set the visibility
      boolean b = this.getLocalProperty(nodeLayers[n].getTableName() + NodusC.PROP_VISIBLE, true);
      nodesLocationHandler[n].setVisible(b);
      nodeLayers[n].setVisible(b);

      n++;
    }

    // Create new ESRI layers for the links
    name = projectProperties.getProperty(NodusC.PROP_NETWORK_LINKS);

    // How many layers?
    st = new StringTokenizer(name);
    n = st.countTokens();
    linkLayers = new NodusEsriLayer[n];
    linksLocationHandler = new NodusLocationHandler[n];
    n = 0;

    while (st.hasMoreTokens()) {
      final String currentLayerName = st.nextToken();

      // Create layer n
      String key;
      key = currentLayerName + NodusC.PROP_NAME;
      localProperties.setProperty(key, currentLayerName);

      // Force re-import?
      if (reImportCheckBox.isSelected()) {
        JDBCUtils.dropTable(currentLayerName);
      }

      // Force reimport if dbf file is newer than the last one used in this project
      if (isDbfModified(currentLayerName)) {
        JDBCUtils.dropTable(currentLayerName);
      }

      // Get the pretty name given to the layer. Take shapefile name if none was defined.
      String prettyName =
          projectProperties.getProperty(
              currentLayerName + NodusC.PROP_PRETTY_NAME, currentLayerName);
      projectProperties.setProperty(currentLayerName + NodusC.PROP_PRETTY_NAME, prettyName);

      linkLayers[n] = new NodusEsriLayer();
      linkLayers[n].setName(prettyName);

      final NodusEsriLayer nep = linkLayers[n];
      final NodusProject _this = this;
      SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
      Thread work =
          new Thread() {
            public void run() {
              nep.setProject(_this, currentLayerName);
              loop.exit();
            }
          };

      work.start();
      loop.enter();

      // Create a new location handler based on the ESRI layer
      linksLocationHandler[n] = new NodusLocationHandler(linkLayers[n]);
      linksLocationHandler[n].setProperties(currentLayerName, localProperties);
      linksLocationHandler[n].setLayer(labelsLayer);
      linkLayers[n].setLocationHandler(linksLocationHandler[n]);
      linkLayers[n].doPrepare();

      nodusMapPanel.getLayerHandler().addLayer(linkLayers[n], layerPosition++);

      // Set the visibility
      boolean b = this.getLocalProperty(linkLayers[n].getTableName() + NodusC.PROP_VISIBLE, true);
      linksLocationHandler[n].setVisible(b);
      linkLayers[n].setVisible(b);

      n++;
    }

    /*
     * Import the OD tables specified in the project if needed
     */
    importDBFTables();

    /* Load the numbers of the objects that are in shapefiles in this directory,
     * but not in current project */
    SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
    Thread work =
        new Thread() {
          public void run() {
            loadOtherLayersObjectNumbers();
            loop.exit();
          }
        };

    work.start();
    loop.enter();

    // Tell the drawing tool to which layers it has to speak with
    nodusMapPanel.getNodusDrawingTool().setNodusLayers(nodeLayers, linkLayers);

    // Add the location handlers
    if (nodeLayers.length > 0) {
      labelsLayer.addLocationHandler(nodesLocationHandler, linksLocationHandler);
      labelsLayer.setDeclutterMatrix(new DeclutterMatrix());
      boolean b = this.getLocalProperty(labelsLayer.getName() + NodusC.PROP_VISIBLE, true);
      labelsLayer.setVisible(b);
      nodusMapPanel.getLayerHandler().addLayer(labelsLayer, layerPosition++);
      labelsLayer.reloadData();
      labelsLayer.doPrepare();
    }

    // Create an invisible drawing layer
    NodusDrawingToolLayer drawingToolLayer = new NodusDrawingToolLayer();
    Properties p = new Properties();
    p.setProperty("DrawingLayer.prettyName", "Drawing layer");
    drawingToolLayer.setProperties("DrawingLayer", p);
    drawingToolLayer.setDrawingTool(nodusMapPanel.getNodusDrawingTool());
    nodusMapPanel.getLayerHandler().addLayer(drawingToolLayer);

    nodusMapPanel.getNodusDrawingToolLauncher().findAndInit(drawingToolLayer);
    nodusMapPanel.getNodusDrawingToolLauncher().setCurrentRequestor(drawingToolLayer.getName());

    // When all the layers are loaded, an integrity test will be performed
    // on the database
    new ShapeIntegrityTester(this);

    // Load additional OpenMap layers if any
    name = projectProperties.getProperty(NodusC.PROP_OPENMAP_LAYERS, null);

    Properties props = null;

    if (name != null) {
      props = new Properties();

      try {
        String fileName =
            localProperties.getProperty(NodusC.PROP_PROJECT_DOTPATH) + name + NodusC.TYPE_OPENMAP;
        props.load(new FileInputStream(fileName));
      } catch (IOException ex) {
        System.out.println(ex.toString());
      }

      addOpenMapLayers(props);
    }

    // Restore the order of the layers as saved in properties
    Layer[] layer = nodusMapPanel.getLayerHandler().getLayers();
    String layerOrder = localProperties.getProperty(NodusC.PROP_MAP_ORDER, "");

    st = new StringTokenizer(layerOrder, ",");
    int position = 0;
    while (st.hasMoreTokens()) {
      String currentLayer = st.nextToken();

      for (Layer element : layer) {
        if (element.getName().equals(currentLayer)) {
          try {
            nodusMapPanel.getLayerHandler().moveLayer(element, position);
            position++;
          } catch (Exception e) {
            // Layer list could be corrupted
          }
        }
      }
    }

    // Set the default preferences
    setDefaultPreferences();

    // Restore the scale rendering threshold
    nodusMapPanel.setRenderingScaleThreshold(
        getLocalProperty(NodusC.PROP_RENDERING_SCALE_THRESHOLD, (float) -1));

    // Verify if exclusion table is compatible with this version of Nodus
    NodeRulesReader.fixExclusionTableIfNeeded(this);

    // Load the service lines
    serviceEditor = new ServiceEditor(this);

    // Enable menus
    nodusMapPanel.getMenuFile().setEnabled(true);
    nodusMapPanel.getNodusLayersPanel().enableButtons(true);

    // Initialize the user defined modal split methods for this project
    new ModalSplitMethodsLoader(this);

    // Handle the project's Groovy initial script if exists
    Thread thread =
        new Thread() {
          @Override
          public void start() {

            GroovyShell shell = new GroovyShell();
            shell.setVariable("nodusMapPanel", nodusMapPanel);
            shell.setVariable("nodusMainFrame", nodusMapPanel);
            String fileName =
                localProperties.getProperty(NodusC.PROP_PROJECT_DOTPATH)
                    + localProperties.getProperty(NodusC.PROP_PROJECT_DOTNAME)
                    + NodusC.TYPE_GROOVY;

            try {
              shell.evaluate(new File(fileName));
            } catch (CompilationFailedException e) {
              System.err.println(e.getMessage());
            } catch (IOException e) {
              // Do nothing as the script is not mandatory
            }
          }
        };

    thread.start();

    nodusMapPanel.setBusy(false);
    isOpen = true;
  }

  /** Reload the project. Can be used when new node/link layers are added/removed to the project. */
  public void reload() {
    saveProperties();
    String fileNameAndPath =
        localProperties.getProperty(NodusC.PROP_PROJECT_DOTPATH)
            + localProperties.getProperty(NodusC.PROP_PROJECT_DOTNAME)
            + NodusC.TYPE_NODUS;
    close();
    openProject(fileNameAndPath);
  }

  /**
   * Removes the specified property from the project's properties.
   *
   * @param key The key string of the property.
   */
  public void removeLocalProperty(String key) {
    localProperties.remove(key);
  }

  /**
   * Removes all the output tables (vnet and path_xxx) for a given scenario.
   *
   * @param scenario ID of the scenario to delete from database.
   */
  public void removeScenario(int scenario) {
    String tableName;

    // Virtual network
    tableName = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName = getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + scenario;
    if (JDBCUtils.tableExists(tableName)) {
      JDBCUtils.dropTable(tableName);
    }

    // Paths
    tableName = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    tableName = getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, tableName);

    if (JDBCUtils.tableExists(tableName + scenario + NodusC.SUFFIX_HEADER)) {
      JDBCUtils.dropTable(tableName + scenario + NodusC.SUFFIX_HEADER);
    }

    if (JDBCUtils.tableExists(tableName + scenario + NodusC.SUFFIX_DETAIL)) {
      JDBCUtils.dropTable(tableName + scenario + NodusC.SUFFIX_DETAIL);
    }

    removeLocalProperty(NodusC.PROP_COST_FUNCTIONS + scenario);
    removeLocalProperty(NodusC.PROP_OD_TABLE + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_TAB + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_METHOD + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_NB_ITERATIONS + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_PRECISION + scenario);
    removeLocalProperty(NodusC.PROP_COST_MARKUP + scenario);
    removeLocalProperty(NodusC.PROP_MAX_DETOUR + scenario);
    removeLocalProperty(NodusC.PROP_THREADS + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_PATHS + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS + scenario);
    removeLocalProperty(NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_QUERY + scenario);
    removeLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + scenario);

    nodusMapPanel.updateScenarioComboBox();
  }

  /**
   * Renames a property in the project's properties.
   *
   * @param oldKey The key string to rename.
   * @param newKey The new name of the key.
   */
  public void renameLocalProperty(String oldKey, String newKey) {
    String v = localProperties.getProperty(oldKey);
    localProperties.setProperty(newKey, v);
    removeLocalProperty(oldKey);
  }

  /**
   * Renames all the output tables (vnet and path_xxx) for a given scenario.
   *
   * @param oldNum ID of the scenario to rename.
   * @param newNum New ID of the scenario.
   */
  public void renameScenario(int oldNum, int newNum) {
    String tableName;

    // Virtual network
    tableName = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName = getLocalProperty(NodusC.PROP_VNET_TABLE, tableName);
    if (JDBCUtils.tableExists(tableName + oldNum)) {
      if (!JDBCUtils.tableExists(tableName + newNum)) {
        JDBCUtils.renameTable(tableName + oldNum, tableName + newNum);
      }
    }

    // Paths
    tableName = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    tableName = getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, tableName);

    if (JDBCUtils.tableExists(tableName + oldNum + NodusC.SUFFIX_HEADER)) {
      if (!JDBCUtils.tableExists(tableName + newNum + NodusC.SUFFIX_HEADER)) {
        JDBCUtils.renameTable(
            tableName + oldNum + NodusC.SUFFIX_HEADER, tableName + newNum + NodusC.SUFFIX_HEADER);
      }
    }
    if (JDBCUtils.tableExists(tableName + oldNum + NodusC.SUFFIX_DETAIL)) {
      if (!JDBCUtils.tableExists(tableName + newNum + NodusC.SUFFIX_DETAIL)) {
        JDBCUtils.renameTable(
            tableName + oldNum + NodusC.SUFFIX_DETAIL, tableName + newNum + NodusC.SUFFIX_DETAIL);
      }
    }
    renameLocalProperty(NodusC.PROP_COST_FUNCTIONS + oldNum, NodusC.PROP_COST_FUNCTIONS + newNum);
    renameLocalProperty(NodusC.PROP_OD_TABLE + oldNum, NodusC.PROP_OD_TABLE + newNum);
    renameLocalProperty(NodusC.PROP_ASSIGNMENT_TAB + oldNum, NodusC.PROP_ASSIGNMENT_TAB + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_METHOD + oldNum, NodusC.PROP_ASSIGNMENT_METHOD + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_NB_ITERATIONS + oldNum,
        NodusC.PROP_ASSIGNMENT_NB_ITERATIONS + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_PRECISION + oldNum, NodusC.PROP_ASSIGNMENT_PRECISION + newNum);
    renameLocalProperty(NodusC.PROP_COST_MARKUP + oldNum, NodusC.PROP_COST_MARKUP + newNum);
    renameLocalProperty(NodusC.PROP_MAX_DETOUR + oldNum, NodusC.PROP_MAX_DETOUR + newNum);
    renameLocalProperty(NodusC.PROP_THREADS + oldNum, NodusC.PROP_THREADS + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD + oldNum,
        NodusC.PROP_ASSIGNMENT_MODAL_SPLIT_METHOD + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_SAVE_PATHS + oldNum, NodusC.PROP_ASSIGNMENT_SAVE_PATHS + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS + oldNum,
        NodusC.PROP_ASSIGNMENT_SAVE_DETAILED_PATHS + newNum);
    renameLocalProperty(
        NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY + oldNum,
        NodusC.PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS + oldNum,
        NodusC.PROP_ASSIGNMENT_LOG_LOST_PATHS + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT + oldNum,
        NodusC.PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT + oldNum,
        NodusC.PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA + oldNum,
        NodusC.PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_QUERY + oldNum, NodusC.PROP_ASSIGNMENT_QUERY + newNum);
    renameLocalProperty(
        NodusC.PROP_ASSIGNMENT_DESCRIPTION + oldNum, NodusC.PROP_ASSIGNMENT_DESCRIPTION + newNum);

    // Change current scenario to new one
    setLocalProperty(NodusC.PROP_SCENARIO, newNum);

    getNodusMapPanel().updateScenarioComboBox();
  }

  /**
   * Called when the user decides not to save the changes performed on nodes and links. The rollback
   * procedure is simple: as the database tables are not synchronized with the .dbf files, the
   * relevant database tables will be deleted. In this ways, the .dbf tables will be automatically
   * imported in the database the next time the project will be opened.
   */
  public void rollBack() {
    if (isOpen) {
      nodusMapPanel.setBusy(true);

      if (nodeLayers != null) {
        for (NodusEsriLayer element : nodeLayers) {
          element.rollback();
        }
      }

      if (linkLayers != null) {
        for (NodusEsriLayer element : linkLayers) {
          element.rollback();
        }
      }

      Nodus.nodusLogger.info("Rollback project");
      nodusMapPanel.setBusy(false);
    }
  }

  /**
   * Saves any changes brought to the nodes or links layers. Nor rollback will be possible after
   * this, as the .dbf files associated to the shape files will be updated.
   */
  public void saveEsriLayers() {
    if (isOpen) {
      nodusMapPanel.setBusy(true);

      if (nodeLayers != null) {
        for (NodusEsriLayer element : nodeLayers) {
          element.save();
        }
      }

      if (linkLayers != null) {
        for (NodusEsriLayer element : linkLayers) {
          element.save();
        }
      }

      Nodus.nodusLogger.info("Save project");
      nodusMapPanel.setBusy(false);
    }
  }

  /** Saves the project's properties on disk. */
  public void saveProperties() {

    try {
      projectProperties.store(new FileOutputStream(projectResourceFileNameAndPath), null);
    } catch (IOException e) {
      System.err.println("Caught IOException saving resources: " + projectResourceFileNameAndPath);
    }
  }

  /** Fills the local properties with some default values if not yet set. */
  private void setDefaultPreferences() {

    // Default scenario
    if (localProperties.getProperty(NodusC.PROP_SCENARIO) == null) {
      localProperties.setProperty(NodusC.PROP_SCENARIO, "0");
    }

    // Default max width of links
    if (localProperties.getProperty(NodusC.PROP_MAX_WIDTH) == null) {
      localProperties.setProperty(NodusC.PROP_MAX_WIDTH, String.valueOf(NodusC.MAX_WIDTH));
    }

    // Default max radius of nodes
    if (localProperties.getProperty(NodusC.PROP_MAX_RADIUS) == null) {
      localProperties.setProperty(NodusC.PROP_MAX_RADIUS, String.valueOf(NodusC.MAX_RADIUS));
    }

    // Default OD table name
    if (localProperties.getProperty(NodusC.PROP_OD_TABLE) == null) {
      String stringDefValue = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_OD;
      localProperties.setProperty(NodusC.PROP_OD_TABLE, stringDefValue);
    }

    // Default cost functions file name
    if (localProperties.getProperty(NodusC.PROP_COST_FUNCTIONS) == null) {
      String stringDefValue = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.TYPE_COSTS;
      localProperties.setProperty(NodusC.PROP_COST_FUNCTIONS, stringDefValue);
    }

    // Default exclusions table
    if (localProperties.getProperty(NodusC.PROP_EXC_TABLE) == null) {
      String stringDefValue = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
      localProperties.setProperty(NodusC.PROP_EXC_TABLE, stringDefValue);
    }

    // Default virtual network table name prefix
    if (localProperties.getProperty(NodusC.PROP_VNET_TABLE) == null) {
      String stringDefValue = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
      localProperties.setProperty(NodusC.PROP_VNET_TABLE, stringDefValue);
    }

    // Default path tables prefix
    if (localProperties.getProperty(NodusC.PROP_PATH_TABLE_PREFIX) == null) {
      String stringDefValue = getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_PATH;
      localProperties.setProperty(NodusC.PROP_PATH_TABLE_PREFIX, stringDefValue);
    }

    // The basic boundaries are displayed by default
    if (localProperties.getProperty(NodusC.PROP_ADD_POLITICAL_BOUNDARIES) == null) {
      localProperties.setProperty(NodusC.PROP_ADD_POLITICAL_BOUNDARIES, Boolean.toString(true));
      localProperties.setProperty(NodusC.PROP_DISPLAY_POLITICAL_BOUNDARIES, Boolean.toString(true));
    }
  }

  /**
   * Stores a value in the project's local properties.
   *
   * @param key The key string of the property.
   * @param value The value to store.
   */
  public void setLocalProperty(String key, boolean value) {
    localProperties.setProperty(key, Boolean.toString(value));
  }

  /**
   * Stores a value in the project's local properties.
   *
   * @param key The key string of the property.
   * @param value The value to store.
   */
  public void setLocalProperty(String key, double value) {
    localProperties.setProperty(key, String.valueOf(value));
  }

  /**
   * Stores a value in the project's local properties.
   *
   * @param key The key string of the property.
   * @param value The value to store.
   */
  public void setLocalProperty(String key, float value) {
    localProperties.setProperty(key, String.valueOf(value));
  }

  /**
   * Stores a value in the project's local properties.
   *
   * @param key The key string of the property.
   * @param value The value to store.
   */
  public void setLocalProperty(String key, int value) {
    localProperties.setProperty(key, String.valueOf(value));
  }

  /**
   * Stores a value in the project's local properties.
   *
   * @param key The key string of the property.
   * @param value The value to store.
   */
  public void setLocalProperty(String key, long value) {
    localProperties.setProperty(key, String.valueOf(value));
  }

  /**
   * Stores a value in the project's local properties.
   *
   * @param key The key string of the property.
   * @param value The value to store.
   */
  public void setLocalProperty(String key, String value) {
    localProperties.setProperty(key, value);
  }

  /**
   * Insert or update an entry in the project's properties.
   *
   * @param key The key string of the property.
   * @param value The value to store.
   */
  public void setProperty(String key, String value) {
    projectProperties.setProperty(key, value);
  }

  /**
   * Set the current scenario to a given number.
   *
   * @param num Scenario number.
   */
  public void setScenario(int num) {
    setScenario(num, "");
  }

  /**
   * Set the current scenario to a given number and give it a description.
   *
   * @param num Scenario number.
   * @param description String that describes the scenario.
   */
  public void setScenario(int num, String description) {
    setLocalProperty(NodusC.PROP_SCENARIO, num);
    setLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + num, description);
    nodusMapPanel.updateScenarioComboBox();
  }
}
