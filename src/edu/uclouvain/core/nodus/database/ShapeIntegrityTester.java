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

package edu.uclouvain.core.nodus.database;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.TimerTask;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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

  /** Snapshot of a node layer captured on the EDT for background validation. */
  private static final class NodeLayerSnapshot {
    private final String layerName;
    private final int graphicCount;
    private final long[] nodeIds;

    private NodeLayerSnapshot(String layerName, int graphicCount, long[] nodeIds) {
      this.layerName = layerName;
      this.graphicCount = graphicCount;
      this.nodeIds = nodeIds;
    }
  }

  /** Snapshot of a link layer captured on the EDT for background validation. */
  private static final class LinkLayerSnapshot {
    private final NodusEsriLayer layer;
    private final String layerName;
    private final int graphicCount;
    private final long[] linkIds;
    private final int[] node1Ids;
    private final int[] node2Ids;

    private LinkLayerSnapshot(
        NodusEsriLayer layer,
        String layerName,
        int graphicCount,
        long[] linkIds,
        int[] node1Ids,
        int[] node2Ids) {
      this.layer = layer;
      this.layerName = layerName;
      this.graphicCount = graphicCount;
      this.linkIds = linkIds;
      this.node1Ids = node1Ids;
      this.node2Ids = node2Ids;
    }
  }

  /** Immutable data used by the background integrity validation pass. */
  private static final class IntegritySnapshot {
    private final NodeLayerSnapshot[] nodeLayers;
    private final LinkLayerSnapshot[] linkLayers;
    private final HashMap<Integer, Integer> otherNodeNumbers;
    private final HashMap<Integer, Integer> otherLinkNumbers;

    private IntegritySnapshot(
        NodeLayerSnapshot[] nodeLayers,
        LinkLayerSnapshot[] linkLayers,
        HashMap<Integer, Integer> otherNodeNumbers,
        HashMap<Integer, Integer> otherLinkNumbers) {
      this.nodeLayers = nodeLayers;
      this.linkLayers = linkLayers;
      this.otherNodeNumbers = otherNodeNumbers;
      this.otherLinkNumbers = otherLinkNumbers;
    }
  }

  /** Timer used to launch the tester. */
  private java.util.Timer integrityTestTimer;

  /** Array of link layers in project. */
  private NodusEsriLayer[] linkLayers;

  /** Array of node layers in project. */
  private NodusEsriLayer[] nodeLayers;

  /** Nodus project to be tested. */
  private NodusProject nodusProject;

  /** True once the tester is shutting down and should ignore pending work. */
  private volatile boolean stopped = false;

  /**
   * The tester works in a timer that is launched with an interval of one second, on a given
   * project.
   *
   * @param nodusProject The Nodus project to test.
   */
  public ShapeIntegrityTester(NodusProject nodusProject) {
    this(nodusProject, true);
  }

  /** Allows a caller in this package to perform validation without scheduling a timer. */
  ShapeIntegrityTester(NodusProject nodusProject, boolean schedule) {
    this.nodusProject = nodusProject;
    if (!schedule) {
      return;
    }

    integrityTestTimer = new java.util.Timer("Nodus-ShapeIntegrityTester", true);
    integrityTestTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            if (!stopped && nodusProject != null) {
              runIntegrityTest();
            }
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
    if (stopped || nodusProject == null) {
      return false;
    }

    linkLayers = nodusProject.getLinkLayers();
    nodeLayers = nodusProject.getNodeLayers();

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

  /** Captures the layer/model state on the EDT so validation can run safely off-thread. */
  private IntegritySnapshot captureSnapshot() {
    final IntegritySnapshot[] snapshotHolder = new IntegritySnapshot[1];
    final RuntimeException[] failureHolder = new RuntimeException[1];

    Runnable captureTask =
        () -> {
          if (stopped || !isProjectLoaded()) {
            return;
          }

          try {
            NodeLayerSnapshot[] nodeSnapshots = new NodeLayerSnapshot[nodeLayers.length];
            for (int i = 0; i < nodeLayers.length; i++) {
              NodusEsriLayer layer = nodeLayers[i];
              DbfTableModel model = layer.getModel();
              long[] nodeIds = new long[model.getRowCount()];
              for (int row = 0; row < model.getRowCount(); row++) {
                nodeIds[row] = JDBCUtils.getLong(model.getValueAt(row, NodusC.DBF_IDX_NUM));
              }
              nodeSnapshots[i] =
                  new NodeLayerSnapshot(
                      layer.getName(), layer.getEsriGraphicList().size(), nodeIds);
            }

            LinkLayerSnapshot[] linkSnapshots = new LinkLayerSnapshot[linkLayers.length];
            for (int i = 0; i < linkLayers.length; i++) {
              NodusEsriLayer layer = linkLayers[i];
              DbfTableModel model = layer.getModel();
              long[] linkIds = new long[model.getRowCount()];
              int[] node1Ids = new int[model.getRowCount()];
              int[] node2Ids = new int[model.getRowCount()];
              for (int row = 0; row < model.getRowCount(); row++) {
                linkIds[row] = JDBCUtils.getLong(model.getValueAt(row, NodusC.DBF_IDX_NUM));
                node1Ids[row] = JDBCUtils.getInt(model.getValueAt(row, NodusC.DBF_IDX_NODE1));
                node2Ids[row] = JDBCUtils.getInt(model.getValueAt(row, NodusC.DBF_IDX_NODE2));
              }
              linkSnapshots[i] =
                  new LinkLayerSnapshot(
                      layer,
                      layer.getName(),
                      layer.getEsriGraphicList().size(),
                      linkIds,
                      node1Ids,
                      node2Ids);
            }

            snapshotHolder[0] =
                new IntegritySnapshot(
                    nodeSnapshots,
                    linkSnapshots,
                    new HashMap<>(nodusProject.getOtherNodeNumbers()),
                    new HashMap<>(nodusProject.getOtherLinkNumbers()));
          } catch (RuntimeException e) {
            failureHolder[0] = e;
          }
        };

    if (SwingUtilities.isEventDispatchThread()) {
      captureTask.run();
    } else {
      try {
        SwingUtilities.invokeAndWait(captureTask);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        }
        throw new RuntimeException(cause);
      }
    }

    if (failureHolder[0] != null) {
      throw failureHolder[0];
    }

    return snapshotHolder[0];
  }

  /**
   * Real integrity tests starts here. The test only can start when all the node and link layers are
   * loaded.
   */
  void runIntegrityTest() {

    IntegritySnapshot snapshot = captureSnapshot();
    if (snapshot == null || stopped) {
      return;
    }

    cancelTimer();

    HashMap<Integer, String> nodes;
    HashMap<Integer, Integer> otherObjects;
    boolean error = false;
    String errorMessage = null;

    // Test if each node number only occurs one time
    nodes = new HashMap<>();
    otherObjects = snapshot.otherNodeNumbers;

    for (NodeLayerSnapshot element : snapshot.nodeLayers) {
      String layerName = element.layerName;

      if (element.graphicCount != element.nodeIds.length) {
        error = true;
        errorMessage =
            MessageFormat.format(
                i18n.get(ShapeIntegrityTester.class, "is_unbalanced", "Layer '{0}' is unbalanced"),
                layerName);

      } else {
        for (int j = 0; j < element.nodeIds.length; j++) {
          // A num must be > 0 and <= Integer.MAX_VALUE
          long id = element.nodeIds[j];
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
            break;
          }

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
    }

    HashMap<Integer, String> links;
    if (!error) {
      // Test if each link number occurs only one time and is linked to
      // two existent nodes
      links = new HashMap<>();
      otherObjects = snapshot.otherLinkNumbers;

      for (LinkLayerSnapshot element : snapshot.linkLayers) {
        String layerName = element.layerName;

        if (element.graphicCount != element.linkIds.length) {
          error = true;
          errorMessage =
              MessageFormat.format(
                  i18n.get(
                      ShapeIntegrityTester.class, "is_unbalanced", "Layer \"{0}\" is unbalanced"),
                  layerName);
        } else {
          for (int j = 0; j < element.linkIds.length; j++) {

            // A num must be > 0 and <= Integer.MAX_VALUE
            long id = element.linkIds[j];
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
              break;
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

            Integer node1 = Integer.valueOf(element.node1Ids[j]);
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
              node2 = Integer.valueOf(element.node2Ids[j]);

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
              element.layer.addLinkToRemove(link);
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

    final boolean hasError = error;
    final String integrityErrorMessage = errorMessage;
    final LinkLayerSnapshot[] linkLayersToPurge = snapshot.linkLayers;

    SwingUtilities.invokeLater(
        () -> {
          if (stopped) {
            return;
          }

          if (hasError && integrityErrorMessage != null) {
            reportError(integrityErrorMessage);
          }

          for (LinkLayerSnapshot element : linkLayersToPurge) {
            element.layer.purgeLinks();
          }
        });
  }

  /**
   * Presents an integrity error after validation has finished on the event dispatch thread.
   *
   * @param message Description of the invalid network data.
   */
  protected void reportError(String message) {
    JOptionPane.showMessageDialog(null, message, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
  }

  /** Stops the periodic integrity test and releases retained references. */
  public synchronized void stop() {
    stopped = true;
    cancelTimer();

    linkLayers = null;
    nodeLayers = null;
    nodusProject = null;
  }

  /** Cancels the timer without clearing the project state used by the current validation run. */
  private synchronized void cancelTimer() {
    if (integrityTestTimer != null) {
      integrityTestTimer.cancel();
      integrityTestTimer.purge();
      integrityTestTimer = null;
    }
  }
}
