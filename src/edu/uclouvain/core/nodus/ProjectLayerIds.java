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

package edu.uclouvain.core.nodus;

import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.ProjectFilesTools;
import edu.uclouvain.core.nodus.database.dbf.DBFException;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import java.util.HashMap;

/** Tracks IDs in unloaded network layers when allocating new node and link IDs. */
final class ProjectLayerIds implements ShapeConstants {
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

  private final NodusProject project;

  ProjectLayerIds(NodusProject project) {
    this.project = project;
  }

  void clear() {
    otherNodeNumbers.clear();
    otherLinkNumbers.clear();
    otherObjectsLoaded = false;
  }

  /**
   * Returns an hashmap that contains the ID's of the links that are found in the Nodus compatible
   * layers, not loaded in the project.
   *
   * @return The hashmap of "other" links.
   */
  HashMap<Integer, Integer> getOtherLinkNumbers() {
    return otherLinkNumbers;
  }

  /**
   * Returns an hashmap that contains the ID's of the nodes that are found in the Nodus compatible
   * layers, not loaded in the project.
   *
   * @return The hashmap of "other" nodes.
   */
  HashMap<Integer, Integer> getOtherNodeNumbers() {
    return otherNodeNumbers;
  }

  /**
   * Returns true if the ID's of the nodes or links of the "non project" layers found in the
   * project's directory are loaded.
   *
   * @return True if the ID's are loaded.
   */
  boolean isOtherObjectsLoaded() {
    return otherObjectsLoaded;
  }

  /**
   * Searches a new unique ID for a node or a link. Existent ID's are searched in the loaded layers,
   * but also in all the Nodus compatible layers found in the project directory.
   *
   * @param layer The array of links or nodes layers.
   * @return The ID of the new link or node.
   */
  int getNewId(NodusMapPanel nodusMapPanel, NodusEsriLayer[] layer) {
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
   * Loads the numbers of the objects of a layer in the given HashMap.
   *
   * @param layerName The name of the shapefile containing the layer.
   * @param The HashMap used to store the ID's
   */
  private void loadObjectIDs(String layerName, HashMap<Integer, Integer> map) {

    try (DBFReader dbfReader =
        new DBFReader(
            project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH) + layerName + NodusC.TYPE_DBF)) {
      Object[] o;
      if (dbfReader.isOpen()) {
        while (dbfReader.hasNextRecord()) {
          o = dbfReader.nextRecord();
          int num = JDBCUtils.getInt(o[NodusC.DBF_IDX_NUM]);
          map.put(num, num);
        }
      }
    } catch (DBFException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Scan the directory to find other Nodus compatible shapefiles, which object ID's will be stored.
   */
  void loadOtherLayersObjectNumbers(NodusEsriLayer[] nodeLayers, NodusEsriLayer[] linkLayers) {
    otherObjectsLoaded = false;
    // Scan all node layers
    String[] layerName =
        ProjectFilesTools.getAvailableLayers(
            project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH), SHAPE_TYPE_POINT);
    for (String element : layerName) {
      if (isOtherNodusLayer(element, SHAPE_TYPE_POINT, nodeLayers, linkLayers)) {
        loadObjectIDs(element, otherNodeNumbers);
      }
    }

    // Scan all link layers
    layerName =
        ProjectFilesTools.getAvailableLayers(
            project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH), SHAPE_TYPE_POLYLINE);
    for (String element : layerName) {
      if (isOtherNodusLayer(element, SHAPE_TYPE_POLYLINE, nodeLayers, linkLayers)) {
        loadObjectIDs(element, otherLinkNumbers);
      }
    }
    otherObjectsLoaded = true;
  }

  /**
   * Returns true if the name corresponds to a layer of the project.
   *
   * @param layerName The name of the shapefile containing the layer.
   * @param type SHAPE_TYPE_POINT or SHAPE_TYPE_ARC.
   * @return True if the layer is not associated to the project.
   */
  private boolean isOtherNodusLayer(
      String layerName, int type, NodusEsriLayer[] nodeLayers, NodusEsriLayer[] linkLayers) {

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
}
