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

import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.Nodus;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.utils.CheckForOM5;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/** Performs layer lookup, editing and persistence through the existing project API. */
public final class ProjectLayerOperations {
  private final NodusProject project;

  /**
   * Creates layer operations for a project.
   *
   * @param project project whose layers are managed
   */
  public ProjectLayerOperations(NodusProject project) {
    this.project = project;
  }

  /**
   * Adds OpenMap layers to the project. All kinds of layers can be added to the display. These
   * layers are described in a property file. See OpenMap documentation for more details on the
   * available layer types and the way they must be described in the property file.
   *
   * @param props Properties that contains the description of the layers.
   * @param localProperties project properties used to resolve layer paths
   * @param nodusMapPanel map panel to which the layers are added
   */
  public void addOpenMapLayers(
      Properties props, Properties localProperties, NodusMapPanel nodusMapPanel) {
    // Test if valid openmap file
    String s = props.getProperty(NodusC.PROP_OPENMAP_LAYERS);

    if (s == null) {
      return;
    }

    // Fetch the path to the project
    String projectPath = localProperties.getProperty(NodusC.PROP_PROJECT_DOTPATH);

    Path projectDir = null;
    if (projectPath != null && !projectPath.isBlank()) {
      projectDir = Paths.get(projectPath).toAbsolutePath().normalize();
    }

    // Upgrade layer class names
    props = CheckForOM5.upgradeApiNames(props);

    // Merge this property file with the local property
    Enumeration<?> enumerator = props.propertyNames();

    while (enumerator.hasMoreElements()) {
      String propName = (String) enumerator.nextElement();
      String propValue = props.getProperty(propName);

      // Add project path to resource file name that starts with "./" (project's directory)
      if (propValue != null && propValue.startsWith("./") && projectDir != null) {
        String relativePath = propValue.substring(2);
        propValue = projectDir.resolve(relativePath).normalize().toString();
      }

      if (propValue != null) {
        localProperties.setProperty(propName, propValue);
      }
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
    NodusEsriLayer layer = project.getLayer(layerName);
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
   * Returns the node or link layer which pretty name or table name corresponds to the given name.
   *
   * @param name Pretty name or table name of the layer.
   * @param nodeLayers node layers to search
   * @param linkLayers link layers to search
   * @return The corresponding layer or null if not found.
   */
  public NodusEsriLayer getLayer(
      String name, NodusEsriLayer[] nodeLayers, NodusEsriLayer[] linkLayers) {

    if (name == null) {
      return null;
    }

    NodusEsriLayer layer = getLayerFromArray(nodeLayers, name);

    if (layer != null) {
      return layer;
    }

    return getLayerFromArray(linkLayers, name);
  }

  /**
   * Returns true if any node or link layer is dirty (modified).
   *
   * @param nodeLayers node layers to inspect
   * @param linkLayers link layers to inspect
   * @return True if at least one layer was modified.
   */
  public boolean isDirty(NodusEsriLayer[] nodeLayers, NodusEsriLayer[] linkLayers) {
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
   * Called when the user decides not to save the changes performed on nodes and links. The rollback
   * procedure is simple: as the database tables are not synchronized with the .dbf files, the
   * relevant database tables will be deleted. In this ways, the .dbf tables will be automatically
   * imported in the database the next time the project will be opened.
   *
   * @param isOpen whether the project is open
   * @param nodeLayers node layers to roll back
   * @param linkLayers link layers to roll back
   * @param nodusMapPanel map panel whose busy state is updated
   */
  public void rollBack(
      boolean isOpen,
      NodusEsriLayer[] nodeLayers,
      NodusEsriLayer[] linkLayers,
      NodusMapPanel nodusMapPanel) {
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
   * Saves the project layers while keeping the project available for retry on failure.
   *
   * @return false if a layer could not be saved
   */
  public boolean saveEsriLayersSafely() {
    if (!project.isOpen()) {
      return true;
    }
    project.getNodusMapPanel().setBusy(true);
    try {
      for (NodusEsriLayer[] layers :
          new NodusEsriLayer[][] {project.getNodeLayers(), project.getLinkLayers()}) {
        if (layers != null) {
          for (NodusEsriLayer layer : layers) {
            if (!layer.saveChanges()) {
              return false;
            }
          }
        }
      }
      Nodus.nodusLogger.info("Save project");
      return true;
    } finally {
      project.getNodusMapPanel().setBusy(false);
    }
  }

  /**
   * Returns the first layer in the given array whose layer name or table name matches the given
   * name.
   *
   * @param layers the array of layers to search, may be {@code null}
   * @param name the layer or table name to look for
   * @return the matching layer, or {@code null} if none is found
   */
  private NodusEsriLayer getLayerFromArray(NodusEsriLayer[] layers, String name) {

    if (layers == null) {
      return null;
    }

    for (NodusEsriLayer element : layers) {

      if (element == null) {
        continue;
      }

      String layerName = element.getName();

      if (layerName != null && layerName.equalsIgnoreCase(name)) {
        return element;
      }

      String tableName = element.getTableName();

      if (tableName != null && tableName.equalsIgnoreCase(name)) {
        return element;
      }
    }

    return null;
  }
}
