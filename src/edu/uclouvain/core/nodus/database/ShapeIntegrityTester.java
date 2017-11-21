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

package edu.uclouvain.core.nodus.database;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;

import javax.swing.JOptionPane;

/**
 * Tests the integrity of a Nodus project:
 *
 * <p>- Node ID must be unique; <br>
 * - Link ID must be unique; <br>
 * - ID of begin and end nodes of links must exist in the node layers.
 *
 * @author Bart Jourquin
 */
public class ShapeIntegrityTester {
  private static I18n i18n = Environment.getI18n();

  /** Timer used to launch the tester. */
  private java.util.Timer integrityTestTimer;

  /** Array of link layers in project. */
  private NodusEsriLayer[] linkLayers;

  /** Array of node layers in project. */
  private NodusEsriLayer[] nodeLayers;

  /** Nodus project to be tested. */
  private NodusProject nodusProject;

  /**
   * The tester works in a timer that is launched with an interval of one second, on a given
   * project.
   *
   * @param nodusProject The Nodus project to test.
   */
  public ShapeIntegrityTester(NodusProject nodusProject) {
    this.nodusProject = nodusProject;

    integrityTestTimer = new java.util.Timer();
    integrityTestTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            runIntegrityTest();
          }
        },
        0,
        1000);
  }

  /**
   * Tests if all the node and link layers are loaded in memory.
   *
   * @return True if all the layers are loaded
   */
  private boolean isProjectLoaded() {
    for (NodusEsriLayer element : nodeLayers) {
      if (element.getModel() == null) {
        return false;
      }

      if (!element.isReady()) {
        return false;
      }
    }

    for (NodusEsriLayer element : linkLayers) {
      if (element.getModel() == null) {
        return false;
      }

      if (!element.isReady()) {
        return false;
      }
    }

    if (!nodusProject.isOtherObjectsLoaded()) {
      return false;
    }

    return true;
  }

  /**
   * Real integrity tests starts here. The test only can start when all the node and link layers are
   * loaded.
   */
  private void runIntegrityTest() {

    linkLayers = nodusProject.getLinkLayers();
    nodeLayers = nodusProject.getNodeLayers();

    if (!isProjectLoaded()) {
      return;
    }

    integrityTestTimer.cancel();

    HashMap<Integer, String> nodes;
    HashMap<Integer, String> links;
    HashMap<Integer, Integer> otherObjects;
    boolean error = false;
    String errorMessage = null;

    // Test if each node number only occurs one time
    nodes = new HashMap<>();
    otherObjects = nodusProject.getOtherNodeNumbers();

    for (NodusEsriLayer element : nodeLayers) {
      DbfTableModel model = element.getModel();
      String layerName = element.getName();

      if (element.getEsriGraphicList().size() != model.getRowCount()) {
        error = true;
        errorMessage =
            MessageFormat.format(
                i18n.get(ShapeIntegrityTester.class, "is_unbalanced", "Layer '{0}' is unbalanced"),
                layerName);

      } else {
        for (int j = 0; j < model.getRowCount(); j++) {
          // A num must be > 0 and <= Integer.MAX_VALUE
          long id = JDBCUtils.getLong(model.getValueAt(j, NodusC.DBF_IDX_NUM));
          if (id <= 0 || id > Integer.MAX_VALUE) {
            error = true;
            errorMessage =
                MessageFormat.format(
                    i18n.get(
                        ShapeIntegrityTester.class,
                        "NodeNum",
                        "Layer {0} : Node {1} has an invalid Num value"),
                    layerName,
                    id);
          }

          // Integer node = new Integer(JDBCUtils.getInt(model.getValueAt(j, NodusC.DBF_IDX_NUM)));
          int node = (int) id;
          String foundName = nodes.get(node);

          // Is already in a non-project layer?
          if (otherObjects.get(node) != null) {
            error = true;
            errorMessage =
                MessageFormat.format(
                    i18n.get(
                        ShapeIntegrityTester.class,
                        "ExternalNode",
                        "Layer {0} : Node {1} already found in external layer"),
                    layerName,
                    node);
            break;
          }

          if (foundName == null) {
            nodes.put(node, layerName);
          } else {
            error = true;
            errorMessage =
                MessageFormat.format(
                    i18n.get(
                        ShapeIntegrityTester.class,
                        "Node",
                        "Layer {0} : Node {1} already found in \"{2}\""),
                    layerName,
                    node,
                    foundName);
            break;
          }
        }
      }

      if (error) {
        break;
      }

      // testOverlap(nodeLayers[i]);
    }

    if (!error) {
      // Test if each link number occurs only one time and is linked to
      // two existent nodes
      links = new HashMap<>();
      otherObjects = nodusProject.getOtherLinkNumbers();

      for (NodusEsriLayer element : linkLayers) {
        DbfTableModel model = element.getModel();
        String layerName = element.getName();

        if (element.getEsriGraphicList().size() != model.getRowCount()) {
          error = true;
          errorMessage =
              MessageFormat.format(
                  i18n.get(
                      ShapeIntegrityTester.class, "is_unbalanced", "Layer \"{0}\" is unbalanced"),
                  layerName);
        } else {
          for (int j = 0; j < model.getRowCount(); j++) {

            // A num must be > 0 and <= Integer.MAX_VALUE
            long id = JDBCUtils.getLong(model.getValueAt(j, NodusC.DBF_IDX_NUM));
            if (id <= 0 || id > Integer.MAX_VALUE) {
              error = true;
              errorMessage =
                  MessageFormat.format(
                      i18n.get(
                          ShapeIntegrityTester.class,
                          "LinkNum",
                          "Layer {0} : Link {1} has an invalid Num value"),
                      layerName,
                      id);
            }

            int link = (int) id;

            String foundName = links.get(link);

            // Is already in a non-project layer?
            if (otherObjects.get(link) != null) {
              error = true;
              errorMessage =
                  MessageFormat.format(
                      i18n.get(
                          ShapeIntegrityTester.class,
                          "ExternalLink",
                          "Layer {0} : Link {1} already found in external layer"),
                      layerName,
                      link);
              break;
            }

            if (foundName == null) {
              links.put(link, layerName);
            } else {
              error = true;
              errorMessage =
                  MessageFormat.format(
                      i18n.get(
                          ShapeIntegrityTester.class,
                          "Link",
                          "Layer {0}: Link {1} already found in \"{2}\""),
                      layerName,
                      link,
                      foundName);
              break;
            }

            // Test the existence of the two end nodes. End nodes
            // must also be different

            Integer node1 =
                new Integer(JDBCUtils.getInt(model.getValueAt(j, NodusC.DBF_IDX_NODE1)));
            Integer node2 = null;

            if (nodes.get(node1) == null) {
              errorMessage =
                  MessageFormat.format(
                      i18n.get(
                          ShapeIntegrityTester.class,
                          "makes_reference_to_inexistant_node",
                          "Layer {0} Link {1} makes reference to inexistant node {2}"),
                      layerName,
                      link,
                      node1.intValue());

              error = true;
            } else {
              node2 = new Integer(JDBCUtils.getInt(model.getValueAt(j, NodusC.DBF_IDX_NODE2)));

              if (nodes.get(node2) == null) {
                errorMessage =
                    MessageFormat.format(
                        i18n.get(
                            ShapeIntegrityTester.class,
                            "makes_reference_to_inexistant_node",
                            "Layer {0} Link {1} makes reference to inexistant node {2}"),
                        layerName,
                        link,
                        node2.intValue());

                error = true;
              }
            }

            // Remove "loops"
            if (node1.equals(node2)) {
              element.addLinkToRemove(link);
            }
          }
        }

        if (error) {
          break;
        }
      }
    }

    nodes = null;
    links = null;

    if (error) {
      // Display error message  the project
      JOptionPane.showMessageDialog(null, errorMessage, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
    }

    // new DatabaseIntegrityTester(nodusProject);

    for (NodusEsriLayer element : linkLayers) {
      element.purgeLinks();
    }

    // testFreeNodes();

  }

  /** Additional tests, not used in production. */
  @SuppressWarnings("unused")
  private void testFreeNodes() {
    System.out.println("Test node locations");

    nodeLayers = nodusProject.getNodeLayers();

    HashMap<Object, LatLonPoint> nodes;

    // Put the lat-lon of each point in hashmap
    nodes = new HashMap<>();

    for (NodusEsriLayer element : nodeLayers) {
      DbfTableModel model = element.getModel();
      EsriGraphicList list = element.getEsriGraphicList();
      Iterator<?> it = list.iterator();
      for (int j = 0; j < model.getRowCount(); j++) {
        EsriPoint p = (EsriPoint) it.next();
        LatLonPoint.Double llp = new LatLonPoint.Double(p.getLat(), p.getLon());
        nodes.put(model.getValueAt(j, NodusC.DBF_IDX_NUM), llp);
      }
    }

    // Test the extremities of each link
    for (NodusEsriLayer element : linkLayers) {
      DbfTableModel model = element.getModel();
      EsriGraphicList list = element.getEsriGraphicList();
      Iterator<?> it = list.iterator();
      for (int j = 0; j < model.getRowCount(); j++) {
        // Get lat/lon of end nodes
        EsriPolyline epl = (EsriPolyline) it.next();
        double[] pts = epl.getLatLonArray();
        for (int k = 0; k < pts.length; k++) {
          pts[k] = ProjMath.radToDeg(pts[k]);
        }
        LatLonPoint.Double ll1 = new LatLonPoint.Double(pts[0], pts[1]);
        LatLonPoint.Double ll2 = new LatLonPoint.Double(pts[pts.length - 2], pts[pts.length - 1]);

        // get origin node
        LatLonPoint llp = nodes.get(model.getValueAt(j, NodusC.DBF_IDX_NODE1));
        if (!llp.equals(ll1) && !llp.equals(ll2)) {
          System.out.println(
              "Invalid location for node " + model.getValueAt(j, NodusC.DBF_IDX_NODE1));
        }

        // destination node
        llp = nodes.get(model.getValueAt(j, NodusC.DBF_IDX_NODE2));
        if (!llp.equals(ll1) && !llp.equals(ll2)) {
          System.out.println(
              "Invalid location for node " + model.getValueAt(j, NodusC.DBF_IDX_NODE2));
        }
      }
    }
  }

  /** Test if two nodes are at the same place. Not used in production. */
  @SuppressWarnings("unused")
  private void testOverlap(NodusEsriLayer p) {
    DbfTableModel model = p.getModel();
    EsriGraphicList l = p.getEsriGraphicList();
    Iterator<?> it1 = l.iterator();
    int index = 0;

    while (it1.hasNext()) {
      EsriPoint p1 = (EsriPoint) it1.next();
      Iterator<?> it2 = l.iterator();

      while (it2.hasNext()) {
        EsriPoint p2 = (EsriPoint) it2.next();
        if (p1 != p2) {
          if (p1.getLat() == p2.getLat() && p1.getLon() == p2.getLon()) {

            System.err.println(JDBCUtils.getInt(model.getValueAt(index, NodusC.DBF_IDX_NUM)));
          }
        }
      }
      index++;
    }
  }
}
