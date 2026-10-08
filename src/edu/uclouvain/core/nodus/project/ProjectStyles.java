/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.project;

import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.omGraphics.NodusDrawingAttributes;
import com.bbn.openmap.omGraphics.NodusOMGraphic;
import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Properties;

/** Loads and owns the node and link drawing styles for one project. */
public final class ProjectStyles {
  /** Creates an empty style cache for a project. */
  public ProjectStyles() {}

  /**
   * Array of drawings attributes used for each defined numeric style. The style can be used to
   * render the nodes on a map.
   */
  private NodusOMGraphic[] nodeStyle;
  /**
   * Array of drawings attributes used for each defined numeric style. The style can be used to
   * render the links on a map (Railway, highway, ...).
   */
  private NodusOMGraphic[] linkStyle;
  /** Properties file that contains the styles for the nodes and links. */
  private Properties stylesProperties;

  /**
   * Loads styles through the project's public loading hooks.
   *
   * @param project project whose styles are loaded
   */
  public void initialize(NodusProject project) {
    stylesProperties = project.getStyleProperties();
    nodeStyle = project.loadStyles("node");
    linkStyle = project.loadStyles("link");
  }

  /** Releases the styles and properties cached for the current project. */
  public void clear() {
    nodeStyle = null;
    linkStyle = null;
    stylesProperties = null;
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
   * @param localProperties project properties, or null to load default styles
   * @return The loaded properties or null on error
   */
  public Properties getStyleProperties(Properties localProperties) {

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
    try (FileInputStream inputStream = new FileInputStream(fileName)) {
      prop.load(inputStream);
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
      return null;
    } catch (IOException ex) {
      ex.printStackTrace();
      return null;
    }

    return prop;
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
   * A set of default styles is embedded in the source tree. These are loaded if no
   * "shapes.properties" file is found in the project directory.
   *
   * @return The default style properties.
   */
  private Properties getDefaultStyle() {
    Properties p = new Properties();

    try (InputStream in = NodusProject.class.getResourceAsStream("shapes.properties")) {
      if (in == null) {
        System.err.println("Resource not found: shapes.properties");
        return null;
      }

      p.load(in);
      return p;

    } catch (IOException ioe) { // Should never happen
      ioe.printStackTrace();
      return null;
    }
  }
}
