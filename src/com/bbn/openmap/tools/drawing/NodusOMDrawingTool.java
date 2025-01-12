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

package com.bbn.openmap.tools.drawing;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.EsriGraphic;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.gui.WindowSupport;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicConstants;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.Nodus;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

// TODO (services) Interaction with services not working

/**
 * This drawing tool makes it possible to modify the graphics stored into a set of nodes and link
 * layers. The tool also checks that the asked operation is possible. - A new node can be added if
 * there isn't already a node at the same place in any node layer; <br>
 * - A node can be deleted if no link is attached to it; <br>
 * - A node can be transfered to another node layer if both layers have the same .dbf structure;
 * <br>
 * - A node can be moved to another location if no other node, from any layer, is already located at
 * the new place; <br>
 * - A link can only be added between two existing nodes; <br>
 * - A link can be removed; <br>
 * - A link can be transfered to another link layer if both layers have the same .dbf structure;
 * <br>
 * - The shape of a link can be redrawn between its two end nodes.<br>
 * - A link can be split into two links.<br>
 *
 * @author Bart Jourquin
 */
public class NodusOMDrawingTool extends OMDrawingTool implements OMGraphicConstants {
  /** Action ID used to delete a link. */
  private static final byte DELETE_LINK = 5;

  /** Action ID used to delete a node. */
  private static final byte DELETE_NODE = 2;

  private static I18n i18n = Environment.getI18n();

  /** Action ID used to move a link. */
  private static final byte MOVE_LINK = 4;

  /** Action ID used to move a node. */
  private static final byte MOVE_NODE = 1;

  /** Serial version. */
  static final long serialVersionUID = -3316912226670871226L;

  /** Action ID used to fusion a node in a link. */
  private static final byte SPLIT_LINK = 7;

  /** Action ID used to transfert a link to another layer. */
  private static final byte TRANSFERT_LINK = 6;

  /** Action ID used to transfert a node between layers. */
  private static final byte TRANSFERT_NODE = 3;

  // private boolean controlPressed;

  /** Traces if an object is currently selected and moved to another location. */
  private boolean isMoving = false;

  /** Index layer of currently selected graphic. */
  private int layerIndexOfSelectedGraphic;

  /** Will contain the "limit" factor to be used with "findClosest" method. */
  private float limit = 0;

  /** Object of the Fusion class used for store all of characteristics about the link. */
  private LineSplitter lineSplitter = new LineSplitter();

  /** The set of link layers managed by this Nodus project. */
  private NodusEsriLayer[] linksLayers;

  /** The set of node layers managed by this Nodus project. */
  private NodusEsriLayer[] nodesLayers;

  /** The map panel. */
  private NodusMapPanel nodusMapPanel;

  /** The launcher that is associated to this drawing tool. */
  private NodusOMDrawingToolLauncher nodusOMDrawingToolLauncher;

  /** Currently selected graphic. */
  private OMGraphic selectedGraphic = null;

  /** Used to intercept the closing of the launcher window. */
  private WindowAdapter closeAdapter = null;

  /**
   * Initializes the drawing tool.
   *
   * @param mapPanel The NodusMapPanel this drawing tool is associated to.
   * @param launcher The NodusOMDrawingToolLauncher this drawing tool is associated to.
   */
  public NodusOMDrawingTool(NodusMapPanel mapPanel, NodusOMDrawingToolLauncher launcher) {
    nodusOMDrawingToolLauncher = launcher;
    nodusMapPanel = mapPanel;
  }

  /**
   * Add new link to the database. If several link layers are currently displayed, the user will be
   * asked which layer the link must be added to.
   *
   * @param pts points of the polyline to add
   */
  private void addLink(double[] pts) {

    boolean isSuccessfullyAdded;
    // Find x,y coordinates of origin and end nodes of the poly
    OMPoint omp1 = new OMPoint(ProjMath.radToDeg(pts[0]), ProjMath.radToDeg(pts[1]));
    OMPoint omp2 =
        new OMPoint(ProjMath.radToDeg(pts[pts.length - 2]), ProjMath.radToDeg(pts[pts.length - 1]));
    Point2D pxy1 = getProjection().forward(omp1.getLat(), omp1.getLon());
    Point2D pxy2 = getProjection().forward(omp2.getLat(), omp2.getLon());

    int numNode1 = 0;
    int numNode2 = 0;
    if (pxy1.getX() != pxy2.getX() || pxy1.getY() != pxy2.getY()) {

      if (!lineSplitter.getFlag()) {
        // Test if the link is attached to existent nodes
        GraphicLocation node1 =
            selectClosestNode((int) pxy1.getX(), (int) pxy1.getY(), limit, false);
        GraphicLocation node2 =
            selectClosestNode((int) pxy2.getX(), (int) pxy2.getY(), limit, false);

        if (node1.omg == null || node2.omg == null) {
          JOptionPane.showMessageDialog(
              nodusMapPanel,
              i18n.get(
                  NodusOMDrawingTool.class,
                  "link_is_not_attached_to_nodes",
                  "New link isn't attached to two existant nodes"),
              NodusC.APPNAME,
              JOptionPane.ERROR_MESSAGE);

          return;
        }

        // Find node nums in nodes dbf table
        GraphicLocation gil = indexOfNode(node1.omg);
        List<Object> values = nodesLayers[gil.indexOfLayer].getModel().getRecord(gil.indexInLayer);

        Double d = Double.valueOf(values.get(NodusC.DBF_IDX_NUM).toString());
        numNode1 = d.intValue();
        gil = indexOfNode(node2.omg);

        values = nodesLayers[gil.indexOfLayer].getModel().getRecord(gil.indexInLayer);
        d = Double.valueOf(values.get(NodusC.DBF_IDX_NUM).toString());
        numNode2 = d.intValue();

        if (numNode1 == numNode2) {

          JOptionPane.showMessageDialog(
              nodusMapPanel,
              i18n.get(
                  NodusOMDrawingTool.class,
                  "Begin_and_end_nodes_must_be_different",
                  "Begin and end nodes must be different"),
              NodusC.APPNAME,
              JOptionPane.ERROR_MESSAGE);

          return;
        }

        // We need the exact coords of the end nodes

        for (int k = 0; k < pts.length; k++) {
          pts[k] = ProjMath.radToDeg(pts[k]);
        }

        OMPoint node = (OMPoint) node1.omg;
        pts[0] = node.getLat();
        pts[1] = node.getLon();
        node = (OMPoint) node2.omg;
        pts[pts.length - 2] = node.getLat();
        pts[pts.length - 1] = node.getLon();
      } else { // Create a new poly based on these new coords, and put it in the layer

        for (int k = 0; k < pts.length; k++) {
          pts[k] = ProjMath.radToDeg(pts[k]);
        }
      }
      EsriPolyline ompl = new EsriPolyline(pts, DECIMAL_DEGREES, LINETYPE_STRAIGHT);

      // Add the graphic to a visible link layer
      /*
       * In this section we make the new link with the same characteristics of the old link.
       *
       * @author Jorge Pinna
       */
      if (lineSplitter.getFlag()) {
        int layerindex = lineSplitter.getLayerIndex();

        // Add the link to the relevant layer
        int newNumber = nodusMapPanel.getNodusProject().getNewLinkId();
        isSuccessfullyAdded =
            linksLayers[layerindex].addRecord(
                ompl,
                newNumber,
                lineSplitter.getInsertedNode(),
                lineSplitter.getDestinationNode(),
                false);
        if (isSuccessfullyAdded) {
          Nodus.nodusLogger.info(
              "Add link " + newNumber + " to " + linksLayers[layerindex].getName());

          int index = 0;
          EsriGraphicList list = linksLayers[layerindex].getEsriGraphicList();
          Iterator<OMGraphic> it = list.iterator();

          while (it.hasNext()) {
            EsriPolyline epl = (EsriPolyline) it.next();

            if (epl == ompl) {
              break;
            } else {
              index++;
            }
          }

          for (int i = 0; i < linksLayers[layerindex].getModel().getColumnCount(); i++) {
            if (i != NodusC.DBF_IDX_NODE1 && i != NodusC.DBF_IDX_NODE2 && i != NodusC.DBF_IDX_NUM) {
              linksLayers[layerindex]
                  .getModel()
                  .setValueAt(lineSplitter.getLineProperty(i), index, i);
            }
          }

          // Insert the new link into the same services like the oldest one.
          nodusMapPanel
              .getNodusProject()
              .getServiceHandler()
              .addServicesToLink(newNumber, lineSplitter.getServices());
        }

        linksLayers[layerindex].attachStyles();
        linksLayers[layerindex].doPrepare();
        selectedGraphic = null;
        isMoving = false;

      } else {
        /* End of section */
        int n = 0;

        for (NodusEsriLayer element : linksLayers) {
          if (element.isVisible()) {
            n++;
          }
        }

        Object[] possibleValues = new String[n];
        n = 0;

        for (NodusEsriLayer element : linksLayers) {
          if (element.isVisible()) {
            possibleValues[n++] = element.getName();
          }
        }

        int index = 0;
        switch (n) {
          case 0:
            JOptionPane.showMessageDialog(
                nodusMapPanel,
                i18n.get(
                    NodusOMDrawingTool.class,
                    "No_link_layer_is_visible",
                    "No link layer is visible"),
                NodusC.APPNAME,
                JOptionPane.ERROR_MESSAGE);

            break;

          case 1: // Only one link layer is

            // visible. Dont' ask the user

            for (int i = 0; i < linksLayers.length; i++) {
              if (linksLayers[i].isVisible()) {
                index = i;

                break;
              }
            }

            // Add the link to the visible layer
            // int num = getNewNumber(linksLayers);
            int num = nodusMapPanel.getNodusProject().getNewLinkId();
            isSuccessfullyAdded = linksLayers[index].addRecord(ompl, num, numNode1, numNode2, true);
            if (isSuccessfullyAdded) {
              Nodus.nodusLogger.info("Add link " + num + " to " + linksLayers[index].getName());
            }
            // linksLayers[index].doPrepare();

            break;

          default:
            Object selectedValue =
                JOptionPane.showInputDialog(
                    nodusMapPanel,
                    MessageFormat.format(
                        "{0}:",
                        i18n.get(
                            NodusOMDrawingTool.class,
                            "Visible_link_layer_to_add_to",
                            "Visible link layer to add to")),
                    NodusC.APPNAME,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    possibleValues,
                    possibleValues[0]);

            if (selectedValue != null) {
              // Find the index of the chosen string
              index = 0;

              for (int i = 0; i < linksLayers.length; i++) {
                if (selectedValue.equals(linksLayers[i].getName())) {
                  index = i;

                  break;
                }
              }

              // Add the link to the relevant layer
              // num = getNewNumber(linksLayers);
              num = nodusMapPanel.getNodusProject().getNewLinkId();
              isSuccessfullyAdded =
                  linksLayers[index].addRecord(ompl, num, numNode1, numNode2, true);
              if (isSuccessfullyAdded) {
                Nodus.nodusLogger.info("Add link " + num + " to " + linksLayers[index].getName());
              }
            }
        }

        if (n > 0) {
          linksLayers[index].attachStyles();
          linksLayers[index].doPrepare();
        }
      }
    }
  }

  /**
   * Add new link to the database. If several link layers are currently displayed, the user will be
   * asked which layer the link must be added to.
   *
   * @param link Link to add
   */
  private void addLink(OMPoly link) {
    double[] pts = link.getLatLonArray();
    addLink(pts);
  }

  /**
   * Add new node to the database. If several node layers are currently displayed, the user will be
   * asked which layer the node must be added to.
   *
   * @param ep Points of Node object to add
   */
  private boolean addNode(EsriPoint ep) {

    // Add the graphic to a visible node layer
    int n = 0;

    for (NodusEsriLayer element : nodesLayers) {
      if (element.isVisible()) {
        n++;
      }
    }

    Object[] possibleValues = new String[n];
    n = 0;

    for (NodusEsriLayer element : nodesLayers) {
      if (element.isVisible()) {
        possibleValues[n++] = element.getName();
      }
    }

    switch (n) {
      case 0:
        JOptionPane.showMessageDialog(
            nodusMapPanel,
            i18n.get(
                NodusOMDrawingTool.class, "No_node_layer_is_visible", "No node layer is visible"),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);

        lineSplitter.setInsertedNode(0);
        return false;

      case 1: // Only one node layer is visible.

        // Dont'ask the user
        int index = 0;
        for (int i = 0; i < nodesLayers.length; i++) {
          if (nodesLayers[i].isVisible()) {
            index = i;
            break;
          }
        }

        // Add node to the relevant layer

        /*
         * In this section we store the characteristics of the new point (the fusion point), and we
         * put it 0 if the node wasn't created.
         *
         * @author Jorge Pinna
         */
        // lineSplitter.setInsertedNode(getNewNumber(nodesLayers));
        lineSplitter.setInsertedNode(nodusMapPanel.getNodusProject().getNewNodeId());
        boolean isSuccessfullyAdded =
            nodesLayers[index].addRecord(ep, lineSplitter.getInsertedNode(), true);
        // System.out.println(isSuccessfullyAdded);
        if (isSuccessfullyAdded) {
          Nodus.nodusLogger.info(
              "Add node " + lineSplitter.getInsertedNode() + " to " + nodesLayers[index].getName());

          try {
            Double d =
                Double.valueOf(
                    nodesLayers[index]
                        .getModel()
                        .getValueAt(
                            nodesLayers[index].getModel().getRowCount() - 1, NodusC.DBF_IDX_NUM)
                        .toString());
            if (d.intValue() != lineSplitter.getInsertedNode()) {
              lineSplitter.setInsertedNode(0);
            }
          } catch (Exception e) {
            System.out.println("???");
            lineSplitter.setInsertedNode(0);
            return false;
          }
          nodesLayers[index].attachStyles();
          nodesLayers[index].doPrepare();
          return true;
        }
        nodesLayers[index].attachStyles();
        nodesLayers[index].doPrepare();

        return false;

        /* End of section */
      default:
        Object selectedValue =
            JOptionPane.showInputDialog(
                nodusMapPanel,
                MessageFormat.format(
                    "{0}:",
                    i18n.get(
                        NodusOMDrawingTool.class,
                        "Visible_node_layer_to_add_to",
                        "Visible node layer to add to")),
                NodusC.APPNAME,
                JOptionPane.QUESTION_MESSAGE,
                null,
                possibleValues,
                possibleValues[0]);

        if (selectedValue != null) {
          // Find the index of the choosen string
          index = 0;

          for (int i = 0; i < nodesLayers.length; i++) {
            if (selectedValue.equals(nodesLayers[i].getName())) {
              index = i;

              break;
            }
          }

          // Add node to the relevant layer
          /*
           * In this section we store the characteristics of the new point (the fusion point), and
           * we put it 0 if the node wasn't created.
           *
           * @author Jorge Pinna
           */
          // lineSplitter.setInsertedNode(getNewNumber(nodesLayers));
          lineSplitter.setInsertedNode(nodusMapPanel.getNodusProject().getNewNodeId());
          isSuccessfullyAdded =
              nodesLayers[index].addRecord(ep, lineSplitter.getInsertedNode(), true);

          if (isSuccessfullyAdded) {
            Nodus.nodusLogger.info(
                "Add node "
                    + lineSplitter.getInsertedNode()
                    + " to "
                    + nodesLayers[index].getName());

            try {
              Double d =
                  Double.valueOf(
                      nodesLayers[index]
                          .getModel()
                          .getValueAt(
                              nodesLayers[index].getModel().getRowCount() - 1, NodusC.DBF_IDX_NUM)
                          .toString());
              if (d.intValue() != lineSplitter.getInsertedNode()) {
                lineSplitter.setInsertedNode(0);
              }
            } catch (Exception e) {
              System.out.println("???");
              lineSplitter.setInsertedNode(0);
              return false;
            }
            nodesLayers[index].attachStyles();
            nodesLayers[index].doPrepare();
            return true;
          }
          return false;
          // nodesLayers[index].doPrepare();

        } else {
          lineSplitter.setInsertedNode(0);
          return false;
          /* End of section */
        }
    }
  }

  /**
   * Add new node to the database. If several node layers are currently displayed, the user will be
   * asked which layer the node must be added to.
   *
   * @param node Node object to add
   */
  private boolean addNode(OMPoint node) {

    EsriPoint ep = new EsriPoint(node.getLat(), node.getLon());
    return addNode(ep);
  }

  /**
   * Avoid the default behavior (not relevant here).
   *
   * @hidden
   */
  @Override
  public JPopupMenu createPopupMenu() {
    return null;
  }

  @Override
  public synchronized void activate() {
    // Avoid re-entrance
    nodusOMDrawingToolLauncher.setBusy(true);

    // Create and add a listener to detect a closing of the launcher
    if (closeAdapter == null) {
      closeAdapter =
          new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
              nodusOMDrawingToolLauncher.cancel();
            }
          };
    }
    WindowSupport.Frm frm =
        (WindowSupport.Frm) nodusOMDrawingToolLauncher.getWindowSupport().getDisplay();
    frm.addWindowListener(closeAdapter);

    nodusMapPanel.getMapBean().requestFocus();
    super.activate();
  }

  /** Cancels the current editing. - */
  public synchronized void cancel() {
    nodusOMDrawingToolLauncher.setBusy(false);

    // Remove the closing listener on the launcher
    WindowSupport.Frm frm =
        (WindowSupport.Frm) nodusOMDrawingToolLauncher.getWindowSupport().getDisplay();
    if (frm != null) {
      frm.removeWindowListener(closeAdapter);
    }

    // If there was an ongoing link moving action, restore to node tool
    if (isMoving) {
      nodusOMDrawingToolLauncher.selectNodeTool(false);
    }

    isMoving = false;

    // Reset display
    if (currentEditable != null) {
      currentEditable.getGraphic().setSelected(false);
      setCurrentEditable(null);
    }

    nodusMapPanel.repaint();

    if (selectedGraphic != null) {
      selectedGraphic.deselect();
      selectedGraphic.generate(getProjection());

      if (selectedGraphic instanceof EsriPoint) {
        nodesLayers[layerIndexOfSelectedGraphic].repaint();
      } else {
        linksLayers[layerIndexOfSelectedGraphic].repaint();
      }
    }

    super.deactivate();
  }

  /**
   * Turns the drawing tool off, disconnecting it from the MouseDelegator or canvas component, and
   * removing the palette. This is the actual place where all the necessary tests are performed in
   * order to perform valid operations on nodes or links.
   */
  @Override
  public synchronized void deactivate() {

    /*
     * Verify if at least one node layer is loaded and that a editable object exists
     */
    if (currentEditable != null && nodesLayers.length > 0) {
      // Get the drawn graphic
      OMGraphic currentEditableGraphic = currentEditable.getGraphic();

      // If it is a point, different scenarios are possible:
      // 1) If isMovig is set, that means that an object was previously set for moving
      // 2) if not, two other possibilities are open
      // a) if an object was already found at the location, the user is asked
      // what he wants to do with.
      // b) if not, a new object is added to the node layers
      if (currentEditableGraphic instanceof OMPoint) {
        if (isMoving) {
          moveNode((OMPoint) currentEditableGraphic);
        } else {
          OMPoint point = (OMPoint) currentEditableGraphic;
          OMGraphic closestObject = getClostedObject(point);

          // If nothing is found here, then a new node must be created
          if (closestObject == null) {
            addNode(point);
          } else {
            /*
             * There is already an existing object here. Ask the user what to do with.
             */
            switch (getEditAction(closestObject)) {
              case SPLIT_LINK:
                splitLink(point, closestObject);
                break;

              case MOVE_NODE:
                isMoving = true;
                return;

              case MOVE_LINK:
                isMoving = true;

                // Deactivate and change to link tool
                currentEditable = null;
                activated = false;
                nodusOMDrawingToolLauncher.selectLinkTool(true);
                return;

              case DELETE_NODE:
                deleteNode(point);
                break;

              case DELETE_LINK:
                deleteLink((EsriPolyline) closestObject);
                break;

              case TRANSFERT_NODE:
              case TRANSFERT_LINK:
                transfertGraphic();
                break;

              default:
            }
          }
        }
      } else { // CurrentEditableGraphic = link
        if (!isMoving) {
          addLink((OMPoly) currentEditableGraphic);
        } else {
          // Move the link
          moveLink((OMPoly) currentEditableGraphic);
          nodusOMDrawingToolLauncher.selectNodeTool(false);
        }
      }
    }
    cancel();
  }

  /**
   * Deletes the given link.
   *
   * @param link Link to delete
   */
  private void deleteLink(EsriPolyline link) {
    for (NodusEsriLayer element : linksLayers) {
      EsriGraphicList list = element.getEsriGraphicList();
      int index = 0;
      Iterator<OMGraphic> it = list.iterator();

      while (it.hasNext()) {
        EsriPolyline epl = (EsriPolyline) it.next();

        if (epl == link) { // Found!

          int num = JDBCUtils.getInt(element.getModel().getValueAt(index, NodusC.DBF_IDX_NUM));

          if (!nodusMapPanel
              .getNodusProject()
              .getServiceHandler()
              .getServiceNamesForLink(num)
              .isEmpty()) {
            JOptionPane.showMessageDialog(
                nodusMapPanel,
                i18n.get(
                    NodusOMDrawingTool.class,
                    "Services_from_link_must_be_empty",
                    "Link has services"),
                NodusC.APPNAME,
                JOptionPane.ERROR_MESSAGE);

            return;
          }

          Nodus.nodusLogger.info("Delete link " + num + " from " + element.getName());

          element.removeRecord(index);
          element.doPrepare();
          selectedGraphic = null;

          return;
        }

        index++;
      }
    }
  }

  /**
   * Deletes the given node if no links are attached to.
   *
   * @param node Node to delete
   */
  private void deleteNode(OMPoint node) {
    // Get x,y coordinates of point
    Point2D pxy = getProjection().forward(node.getLat(), node.getLon());

    GraphicLocation gl = selectClosestNode((int) pxy.getX(), (int) pxy.getY(), limit, true);

    // Get node num
    List<Object> values = nodesLayers[gl.indexOfLayer].getModel().getRecord(gl.indexInLayer);

    int nodeId = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NUM));

    for (NodusEsriLayer element : linksLayers) {
      for (int i = 0; i < element.getModel().getRowCount(); i++) {
        values = element.getModel().getRecord(i);

        int node1Id = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE1));
        int node2Id = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE2));

        if (nodeId == node1Id || nodeId == node2Id) {
          JOptionPane.showMessageDialog(
              nodusMapPanel,
              i18n.get(
                  NodusOMDrawingTool.class,
                  "At_least_one_link_is_connected_to_this_node",
                  "At least one link is connected to this node"),
              NodusC.APPNAME,
              JOptionPane.ERROR_MESSAGE);

          return;
        }
      }
    }

    // Delete
    int num =
        JDBCUtils.getInt(
            nodesLayers[gl.indexOfLayer]
                .getModel()
                .getValueAt(gl.indexInLayer, NodusC.DBF_IDX_NUM));
    Nodus.nodusLogger.info(
        "Delete node " + num + " from " + nodesLayers[gl.indexOfLayer].getName());

    nodesLayers[gl.indexOfLayer].removeRecord(gl.indexInLayer);
    nodesLayers[gl.indexOfLayer].doPrepare();
    selectedGraphic = null;
  }

  /**
   * Try to find an object at a given location.
   *
   * @param point The point which coordinates will be used to test the existence of a close object
   * @return The closest OMGraphic or null if no one was found.
   */
  private OMGraphic getClostedObject(OMPoint point) {
    OMGraphic omg = null;

    if (point.getLat() != 90 && point.getLon() != -180) {
      // Get x,y coordinates of point
      Point2D pxy = getProjection().forward(point.getLat(), point.getLon());

      for (int i = 0; i < nodesLayers.length; i++) {
        // Look only in visible layers
        if (!nodesLayers[i].isVisible()) {
          continue;
        }

        /* Look only into the displayed objects. */
        OMGraphicList list = nodesLayers[i].getVisibleEsriGraphicList();
        // OMGraphicList list = nodesLayers[i].getEsriGraphicList();

        omg = list.selectClosest((int) pxy.getX(), (int) pxy.getY(), 4);

        // if found
        if (omg != null) { // Select the node
          nodesLayers[i].repaint();
          selectedGraphic = omg;
          layerIndexOfSelectedGraphic = i;

          break;
        }
      }

      // Return if found
      if (omg != null) {
        return omg;
      }

      // Dont look to links if asked so (when control is pressed)
      if (nodusMapPanel.isControlPressed()) {
        return null;
      }

      // Try with links
      for (int i = 0; i < linksLayers.length; i++) {
        // Look only in visible layers
        if (!linksLayers[i].isVisible()) {
          continue;
        }

        OMGraphicList list = linksLayers[i].getEsriGraphicList();
        omg = list.selectClosest((int) pxy.getX(), (int) pxy.getY(), 4);

        if (omg != null) { // Select the link
          linksLayers[i].repaint();
          selectedGraphic = omg;
          layerIndexOfSelectedGraphic = i;

          break;
        }
      }
    }

    return omg;
  }

  /**
   * Returns the action to perform on the given object. Nodes and links can be moved, deleted or
   * transferred to another layer. Links can also be added.
   *
   * @param object Object to edit
   * @return Action to perform
   */
  private byte getEditAction(OMGraphic object) {
    Object[] possibleValuesNodes = {
      i18n.get(NodusOMDrawingTool.class, "Move", "Move"),
      i18n.get(NodusOMDrawingTool.class, "Delete", "Delete"),
      i18n.get(NodusOMDrawingTool.class, "Transfer", "Transfer")
    };

    Object[] possibleValuesLink = {
      i18n.get(NodusOMDrawingTool.class, "Move", "Move"),
      i18n.get(NodusOMDrawingTool.class, "Delete", "Delete"),
      i18n.get(NodusOMDrawingTool.class, "Transfer", "Transfer"),
      i18n.get(NodusOMDrawingTool.class, "Split_link", "Split link")
    };

    if (object instanceof EsriPoint) {
      Object selectedValue =
          JOptionPane.showInputDialog(
              nodusMapPanel,
              i18n.get(
                  NodusOMDrawingTool.class,
                  "A_node_already_exists_at_this_location",
                  "A node already exists at this location"),
              NodusC.APPNAME,
              JOptionPane.QUESTION_MESSAGE,
              null,
              possibleValuesNodes,
              possibleValuesNodes[0]);

      if (selectedValue != null) {
        if (selectedValue.equals(possibleValuesNodes[0])) {
          return MOVE_NODE;
        }

        if (selectedValue.equals(possibleValuesNodes[1])) {
          return DELETE_NODE;
        }

        if (selectedValue.equals(possibleValuesNodes[2])) {
          return TRANSFERT_NODE;
        }
      }
    } else {

      /*
       * It seems there is a bug in the Nimbus L&F for Mac. Indeed, a call to the static
       * showInputDialog method with a parent frame set causes a refresh problem in
       * the parent frame when the combo contains more than 3 options. Strange. A
       * workaround is to create a JOptionPane with a null parent
       * and call setLocationRelative.
       */
      JOptionPane pane =
          new JOptionPane(
              i18n.get(
                  NodusOMDrawingTool.class,
                  "A_link_already_exists_at_this_location",
                  "A link already exists at this location"),
              JOptionPane.QUESTION_MESSAGE,
              JOptionPane.OK_CANCEL_OPTION,
              null,
              null,
              null);
      pane.setSelectionValues(possibleValuesLink);
      pane.setInitialSelectionValue(possibleValuesLink[0]);
      pane.setWantsInput(true);

      JDialog dialog = pane.createDialog(null, NodusC.APPNAME);
      dialog.setLocationRelativeTo(nodusMapPanel);
      dialog.setVisible(true);
      Object selectedValue = pane.getInputValue();

      if (selectedValue != null) {

        if (selectedValue.equals(possibleValuesLink[0])) {
          return MOVE_LINK;
        }

        if (selectedValue.equals(possibleValuesLink[1])) {
          return DELETE_LINK;
        }

        if (selectedValue.equals(possibleValuesLink[2])) {
          return TRANSFERT_LINK;
        }

        if (selectedValue.equals(possibleValuesLink[3])) {
          return SPLIT_LINK;
        }
      }
    }

    return 0;
  }

  /**
   * Returns true if two NodusEsriLayers have the same .dbf database structure. This is used to test
   * if a given object can me moved to another layer of the same type.
   *
   * @param layer1 NodusEsriLayer
   * @param layer2 NodusEsriLayer
   * @return boolean
   */
  private boolean haveSameDbfStructure(NodusEsriLayer layer1, NodusEsriLayer layer2) {
    if (layer1.getModel().getColumnCount() != layer2.getModel().getColumnCount()) {
      return false;
    }

    for (int i = 0; i < layer1.getModel().getColumnCount(); i++) {
      if (!layer1.getModel().getColumnName(i).equals(layer2.getModel().getColumnName(i))) {
        return false;
      }

      if (layer1.getModel().getType(i) != layer2.getModel().getType(i)) {
        return false;
      }

      if (layer1.getModel().getLength(i) != layer2.getModel().getLength(i)) {
        return false;
      }

      if (layer1.getModel().getDecimalCount(i) != layer2.getModel().getDecimalCount(i)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns the GraphicIndexLocation (layer index and index in layer) of a given OMGraphic.
   *
   * @param omg OMGraphic
   * @return GraphicIndexLocation
   */
  private GraphicLocation indexOfNode(OMGraphic omg) {
    GraphicLocation gil = new GraphicLocation();

    for (int i = 0; i < nodesLayers.length; i++) {
      EsriGraphicList list = nodesLayers[i].getEsriGraphicList();
      gil.indexInLayer = list.indexOf(omg);

      if (gil.indexInLayer != -1) {
        gil.indexOfLayer = i;

        break;
      }
    }

    return gil;
  }

  /**
   * Move the previously selected link to the new location. Must be between two different existing
   * nodes.
   *
   * @param pts points of the polyline to add
   */
  private void moveLink(double[] pts) {

    // Find x,y coordinates of origin and end nodes of the poly
    OMPoint omp1 = new OMPoint(ProjMath.radToDeg(pts[0]), ProjMath.radToDeg(pts[1]));
    OMPoint omp2 =
        new OMPoint(ProjMath.radToDeg(pts[pts.length - 2]), ProjMath.radToDeg(pts[pts.length - 1]));
    Point2D pxy1 = getProjection().forward(omp1.getLat(), omp1.getLon());
    Point2D pxy2 = getProjection().forward(omp2.getLat(), omp2.getLon());

    Double d1 = 0.0;
    Double d2 = 0.0;
    int numNode1 = 0;
    int numNode2 = 0;

    if (pxy1.getX() != pxy2.getX() || pxy1.getY() != pxy2.getY()) {

      if (!lineSplitter.getFlag()) {
        // Test if the link is attached to existent nodes
        GraphicLocation node1 =
            selectClosestNode((int) pxy1.getX(), (int) pxy1.getY(), limit, false);
        GraphicLocation node2 =
            selectClosestNode((int) pxy2.getX(), (int) pxy2.getY(), limit, false);

        if (node1.omg == null || node2.omg == null) {
          JOptionPane.showMessageDialog(
              nodusMapPanel,
              i18n.get(
                  NodusOMDrawingTool.class,
                  "New_link_isn_t_attached_to_two_existant_nodes",
                  "New link isn't attached to two existant nodes"),
              NodusC.APPNAME,
              JOptionPane.ERROR_MESSAGE);

          return;
        }

        // Find node nums in nodes dbf table
        GraphicLocation gil = indexOfNode(node1.omg);
        List<Object> values = nodesLayers[gil.indexOfLayer].getModel().getRecord(gil.indexInLayer);

        Double d = Double.valueOf(values.get(NodusC.DBF_IDX_NUM).toString());
        numNode1 = d.intValue();
        gil = indexOfNode(node2.omg);

        values = nodesLayers[gil.indexOfLayer].getModel().getRecord(gil.indexInLayer);
        d = Double.valueOf(values.get(NodusC.DBF_IDX_NUM).toString());
        numNode2 = d.intValue();

        if (numNode1 == numNode2) {
          JOptionPane.showMessageDialog(
              nodusMapPanel,
              i18n.get(
                  NodusOMDrawingTool.class,
                  "Begin_and_end_nodes_must_be_different",
                  "Begin and end nodes must be different"),
              NodusC.APPNAME,
              JOptionPane.ERROR_MESSAGE);

          return;
        }

        // We need the exact coords of the end nodes
        for (int k = 0; k < pts.length; k++) {
          pts[k] = ProjMath.radToDeg(pts[k]);
        }

        OMPoint node = (OMPoint) node1.omg;
        pts[0] = node.getLat();
        pts[1] = node.getLon();
        node = (OMPoint) node2.omg;
        pts[pts.length - 2] = node.getLat();
        pts[pts.length - 1] = node.getLon();
        d1 = Double.valueOf(numNode1);
        d2 = Double.valueOf(numNode2);

      } else {

        for (int k = 0; k < pts.length; k++) {
          pts[k] = ProjMath.radToDeg(pts[k]);
        }
      }
      /*
       * Create a new poly based on these new coords, and put it in the layer
       */
      EsriPolyline ompl = new EsriPolyline(pts, DECIMAL_DEGREES, LINETYPE_STRAIGHT);

      // Find index of selected graphic
      int index = 0;
      EsriGraphicList list = linksLayers[layerIndexOfSelectedGraphic].getEsriGraphicList();
      Iterator<OMGraphic> it = list.iterator();

      while (it.hasNext()) {
        EsriPolyline epl = (EsriPolyline) it.next();

        if (epl == selectedGraphic) {
          break;
        } else {
          index++;
        }
      }

      LinkedList<String> services =
          nodusMapPanel
              .getNodusProject()
              .getServiceHandler()
              .getServiceNamesForLink(
                  JDBCUtils.getInt(
                      linksLayers[layerIndexOfSelectedGraphic]
                          .getModel()
                          .getValueAt(index, NodusC.DBF_IDX_NUM)));

      if (!lineSplitter.getFlag()) {
        Double d =
            Double.valueOf(
                linksLayers[layerIndexOfSelectedGraphic]
                    .getModel()
                    .getValueAt(index, NodusC.DBF_IDX_NODE1)
                    .toString());
        int oldnode1 = d.intValue();

        d =
            Double.valueOf(
                linksLayers[layerIndexOfSelectedGraphic]
                    .getModel()
                    .getValueAt(index, NodusC.DBF_IDX_NODE2)
                    .toString());
        int oldnode2 = d.intValue();

        if ((oldnode1 != numNode1 || oldnode2 != numNode2) && !services.isEmpty()) {
          JOptionPane.showMessageDialog(
              nodusMapPanel,
              i18n.get(
                  NodusOMDrawingTool.class,
                  "Services_from_link_must_be_empty",
                  "Link have Services"),
              NodusC.APPNAME,
              JOptionPane.ERROR_MESSAGE);
          return;
        }
      }

      // Replace the poly with the new one
      linksLayers[layerIndexOfSelectedGraphic].getEsriGraphicList().setOMGraphicAt(ompl, index);
      linksLayers[layerIndexOfSelectedGraphic].attachStyle(ompl, index);

      // Exact coordinates of end nodes

      /*
       * In this Section we store all of characteristics of the old link, and we modify the first
       * link.
       *
       * @author Jorge Pinna
       */
      if (lineSplitter.getFlag()) {
        lineSplitter.setLayerIndex(layerIndexOfSelectedGraphic);
        lineSplitter.setLineProperties(
            linksLayers[layerIndexOfSelectedGraphic].getModel().getColumnCount());
        for (int i = 0;
            i < linksLayers[layerIndexOfSelectedGraphic].getModel().getColumnCount();
            i++) {
          lineSplitter.setLineproperty(
              i, linksLayers[layerIndexOfSelectedGraphic].getModel().getValueAt(index, i));
        }

        Double d =
            Double.valueOf(
                linksLayers[layerIndexOfSelectedGraphic]
                    .getModel()
                    .getValueAt(index, NodusC.DBF_IDX_NODE1)
                    .toString());
        lineSplitter.setOriginNode(d.intValue());

        d =
            Double.valueOf(
                (linksLayers[layerIndexOfSelectedGraphic]
                    .getModel()
                    .getValueAt(index, NodusC.DBF_IDX_NODE2)
                    .toString()));
        lineSplitter.setDestinationNode(d.intValue());

        linksLayers[layerIndexOfSelectedGraphic]
            .getModel()
            .setValueAt(lineSplitter.getOriginNode(), index, NodusC.DBF_IDX_NODE1);
        if (lineSplitter.getInsertedNode() != 0) {
          linksLayers[layerIndexOfSelectedGraphic]
              .getModel()
              .setValueAt(lineSplitter.getInsertedNode(), index, NodusC.DBF_IDX_NODE2);
        } else {
          linksLayers[layerIndexOfSelectedGraphic]
              .getModel()
              .setValueAt(lineSplitter.getDestinationNode(), index, NodusC.DBF_IDX_NODE2);
        }

        lineSplitter.setServices(services);

      } else {
        /* End of Section */
        linksLayers[layerIndexOfSelectedGraphic]
            .getModel()
            .setValueAt(d1, index, NodusC.DBF_IDX_NODE1);
        linksLayers[layerIndexOfSelectedGraphic]
            .getModel()
            .setValueAt(d2, index, NodusC.DBF_IDX_NODE2);
        int num =
            JDBCUtils.getInt(
                linksLayers[layerIndexOfSelectedGraphic]
                    .getModel()
                    .getValueAt(index, NodusC.DBF_IDX_NUM));
        Nodus.nodusLogger.info(
            "Move link " + num + " in " + linksLayers[layerIndexOfSelectedGraphic].getName());
      }
      // set the end nodes in dbf
      nodusMapPanel
          .getNodusProject()
          .getServiceHandler()
          .addServicesToLink(
              JDBCUtils.getInt(
                  linksLayers[layerIndexOfSelectedGraphic]
                      .getModel()
                      .getValueAt(index, NodusC.DBF_IDX_NUM)),
              services);

      isMoving = false;
      linksLayers[layerIndexOfSelectedGraphic].setDirtyShp(true);
      linksLayers[layerIndexOfSelectedGraphic].setDirtyDbf(true);
      linksLayers[layerIndexOfSelectedGraphic].reloadLabels();
      linksLayers[layerIndexOfSelectedGraphic].attachStyles();
      linksLayers[layerIndexOfSelectedGraphic].doPrepare();

      isMoving = false;
    }
  }

  /**
   * Move the previously selected link to the new location. Must be between two different exiating
   * nodes.
   *
   * @param link Link that represents the new location of the previously selected link.
   */
  private void moveLink(OMPoly link) {
    // Get the X,Y points of the poly
    double[] pts = link.getLatLonArray();
    moveLink(pts);
  }

  /**
   * Move the previously selected node to the new location. Also move all the attached links.
   *
   * @param node Node that represents the new location of the previously selected node.
   */
  private void moveNode(OMPoint node) {
    // Put the new coordinates in the node to move
    OMPoint nodeToMove = (OMPoint) selectedGraphic; // ***
    nodeToMove.setLat(node.getLat());
    nodeToMove.setLon(node.getLon());

    isMoving = false;

    // Move all related links
    // 1.) Get node num
    GraphicLocation gil = indexOfNode(nodeToMove);
    List<Object> values = nodesLayers[gil.indexOfLayer].getModel().getRecord(gil.indexInLayer);

    int nodeId = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NUM));
    Nodus.nodusLogger.info(
        "Move node " + nodeId + " in " + nodesLayers[gil.indexOfLayer].getName());

    for (NodusEsriLayer element : linksLayers) {
      // boolean isDirty = false;

      for (int i = 0; i < element.getModel().getRowCount(); i++) {
        values = element.getModel().getRecord(i);

        int node1Id = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE1));

        int node2Id = JDBCUtils.getInt(values.get(NodusC.DBF_IDX_NODE2));

        if (nodeId == node1Id || nodeId == node2Id) {
          // Modify the coordinates of the end nodes of the link
          OMGraphic omg = element.getEsriGraphicList().getOMGraphicAt(i);

          if (omg instanceof EsriPolyline) { // Normally always the
            // case

            EsriPolyline epll = (EsriPolyline) omg;

            // Get the lat/lon and transform in degrees
            double[] pts = epll.getLatLonArray();
            for (int k = 0; k < pts.length; k++) {
              pts[k] = ProjMath.radToDeg(pts[k]);
            }

            // Put the new coords in the array
            if (nodeId == node1Id) {
              pts[0] = nodeToMove.getLat();
              pts[1] = nodeToMove.getLon();
            } else {
              pts[pts.length - 2] = nodeToMove.getLat();
              pts[pts.length - 1] = nodeToMove.getLon();
            }

            // Create a new poly based on these new coords, and put
            // it in the layer
            EsriPolyline epl = new EsriPolyline(pts, DECIMAL_DEGREES, LINETYPE_STRAIGHT);

            // Keep the previous user data
            epl.putAttribute(0, omg.getAttribute(0));
            element.getEsriGraphicList().setOMGraphicAt(epl, i);
            element.attachStyle(epl, i);
            element.setDirtyShp(true);
            // isDirty = true;
          }
        }
      }
    }

    nodesLayers[gil.indexOfLayer].setDirtyShp(true);
    nodesLayers[gil.indexOfLayer].reloadLabels();
    nodesLayers[gil.indexOfLayer].doPrepare();

    // Refresh also all the link layers because links could be attached to this node
    for (int i = 0; i < linksLayers.length; i++) {
      linksLayers[i].attachStyles();
      linksLayers[i].doPrepare();
    }

    selectedGraphic = null;
  }

  /**
   * Returns the closest node from a given location. The node can be stored in any of the available
   * node layers. The third parameter represents the max distance that a graphic has to be within to
   * be returned, in pixels. The last paramater indicates if the invisible graphics should also be
   * taken into account.
   *
   * @param x int
   * @param y int
   * @param e float
   * @param processAllgeometries boolean
   * @return GraphicLocation
   */
  private GraphicLocation selectClosestNode(int x, int y, float e, boolean processAllgeometries) {
    GraphicLocation gl = new GraphicLocation();
    float distance = Float.MAX_VALUE;

    for (int i = 0; i < nodesLayers.length; i++) {
      EsriGraphicList list = nodesLayers[i].getEsriGraphicList();
      boolean oldProcessAllGeometries = list.getProcessAllGeometries();
      list.setProcessAllGeometries(processAllgeometries);

      OMGraphic omg = list.findClosest(x, y, e);

      if (omg != null) {
        float d = omg.distance(x, y);

        if (d < distance) {
          distance = d;
          gl.omg = omg;
          gl.indexOfLayer = i;
          gl.indexInLayer = list.indexOf(omg);
        }
      }

      list.setProcessAllGeometries(oldProcessAllGeometries);
    }

    return gl;
  }

  /**
   * Tells the drawing tool on which layers it can draw.
   *
   * @param nodes The set of Node layers.
   * @param links The set of Link layers.
   */
  public synchronized void setNodusLayers(NodusEsriLayer[] nodes, NodusEsriLayer[] links) {
    nodesLayers = nodes;
    linksLayers = links;

    // Compute a good "limit" factor to be used in "findClosest" methods
    // (1% of screen width)
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    limit = (float) (screenSize.getWidth() / 100);
  }

  /**
   * Here we are going to make the fusion. First we found the close point in the line, then we make
   * a new node, then we modify the line and we put a new one.
   *
   * @param node OMPoint, the point of the click
   * @param omg OMGraphic, The link when we are going to fusion.
   * @author Jorge Pinna
   */
  private void splitLink(OMPoint node, OMGraphic omg) {

    // test if the omg is a link
    if (omg instanceof OMPoly) {
      lineSplitter.setFlag(true);

      double xtemp1 = 0;
      double ytemp1 = 0;
      double htemp;
      double x1 = 0;
      double y1 = 0;
      double x2 = 0;
      double y2 = 0;
      double x0 = 0;
      double y0 = 0;
      double h = Double.MAX_VALUE;
      double nodey = node.getLat();
      double nodex = node.getLon();
      int cutflag = 0;
      int x1flag = 0;
      double x = 0;
      double y = 0;
      int k;
      OMPoly link = (OMPoly) omg;
      double[] pts = link.getLatLonArrayCopy();

      // We transform the points of the polyline in Degrees.
      for (k = 0; k < pts.length; k++) {
        pts[k] = ProjMath.radToDeg(pts[k]);
      }

      // We finde the near point in the polyline x1,y1
      for (k = 0; k <= pts.length - 2; k += 2) {
        ytemp1 = pts[k];
        xtemp1 = pts[k + 1];
        htemp = Math.sqrt(Math.pow(xtemp1 - nodex, 2) + Math.pow(ytemp1 - nodey, 2));
        if (htemp < h) {
          h = htemp;
          y1 = ytemp1;
          x1 = xtemp1;
          x1flag = k + 1;
        }
      }

      // if the near point is the last point in the polyline
      if (pts.length == x1flag + 1) {
        x0 = pts[x1flag - 2];
        y0 = pts[x1flag - 3];
        InsertedPoint p = new InsertedPoint(x0, y0, x1, y1, nodex, nodey);
        cutflag = x1flag - 2;
        if (p.inclu) {
          x = (float) p.xi;
          y = (float) p.yi;
        } else {
          x = x1;
          y = y1;
        }
      } else if (x1flag == 1) { // if the near point is the first point in the polyline
        x2 = pts[x1flag + 2];
        y2 = pts[x1flag + 1];
        InsertedPoint p = new InsertedPoint(x1, y1, x2, y2, nodex, nodey);
        cutflag = x1flag;
        if (p.inclu) {
          x = (float) p.xi;
          y = (float) p.yi;
        } else {
          x = x1;
          y = y1;
        }
      } else {
        x0 = pts[x1flag + 2];
        y0 = pts[x1flag + 1];
        x2 = pts[x1flag - 2];
        y2 = pts[x1flag - 3];
        InsertedPoint p1 = new InsertedPoint(x0, y0, x1, y1, nodex, nodey);
        InsertedPoint p2 = new InsertedPoint(x1, y1, x2, y2, nodex, nodey);
        if (p1.inclu && !p2.inclu) {
          x = (float) p1.xi;
          y = (float) p1.yi;
          cutflag = x1flag;
        }
        if (!p1.inclu && p2.inclu) {
          x = (float) p2.xi;
          y = (float) p2.yi;
          cutflag = x1flag - 2;
        }
        if (!p1.inclu && !p2.inclu) {
          x = x1;
          y = y1; // the
          // laye
          cutflag = x1flag;
        }
        if (p1.inclu && p2.inclu) {
          if (p1.length < p2.length) {
            x = (float) p1.xi;
            y = (float) p1.yi;
            cutflag = x1flag;
          } else {
            x = (float) p2.xi;
            y = (float) p2.yi;
            cutflag = x1flag - 2;
          }
        }
      }
      // create the new point
      EsriPoint ep = new EsriPoint(y, x);
      if (addNode(ep)) {

        if (lineSplitter.getInsertedNode() == 0) {
          moveLink(pts);
          lineSplitter.setFlag(false);
          return;
        }

        // the new first link
        double[] pts1 = new double[cutflag + 3];
        // the new second link
        
        int i = 0;
        // the new points for the first link
        do {
          pts1[i] = pts[i];
          i++;

        } while (i <= cutflag);
        pts1[i] = y;
        pts1[i + 1] = x;

        k = 2;
        // the new points for the second link
        double[] pts2 = new double[pts.length - cutflag + 1];
        pts2[0] = y;
        pts2[1] = x;
        do {
          pts2[k] = pts[i];
          i++;
          k++;
        } while (k < pts2.length);

        for (k = 0; k < pts1.length; k++) {
          pts1[k] = ProjMath.degToRad(pts1[k]);
        }
        for (k = 0; k < pts2.length; k++) {
          pts2[k] = ProjMath.degToRad(pts2[k]);
        }

        // create the first link
        moveLink(pts1);

        // create the second link
        addLink(pts2);
      }
      lineSplitter.setFlag(false);

      for (int i = 0; i < nodesLayers.length; i++) {
        nodesLayers[i].attachStyles();
        nodesLayers[i].doPrepare();
      }
      for (int i = 0; i < linksLayers.length; i++) {
        linksLayers[i].attachStyles();
        linksLayers[i].doPrepare();
      }
    }
  }

  /** Transfers the given link to a compatible layers, if any. */
  private void transfertGraphic() {
    NodusEsriLayer[] layers;

    if (selectedGraphic instanceof EsriPoint) {
      layers = nodesLayers;
    } else {
      layers = linksLayers;
    }

    // Make a list of compatible layers
    LinkedList<String> ll = new LinkedList<>();

    for (int i = 0; i < layers.length; i++) {
      if (layerIndexOfSelectedGraphic != i
          && haveSameDbfStructure(layers[i], layers[layerIndexOfSelectedGraphic])) {
        ll.add(layers[i].getName());
      }
    }

    if (ll.size() == 0) {
      JOptionPane.showMessageDialog(
          nodusMapPanel,
          i18n.get(
              NodusOMDrawingTool.class, "No_compatible_layers_found", "No compatible layers found"),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);

    } else {
      // Select one of the compatible layers
      Object[] possibleValues = ll.toArray(new String[ll.size()]);
      Object selectedValue =
          JOptionPane.showInputDialog(
              nodusMapPanel,
              MessageFormat.format(
                  "{0}:",
                  i18n.get(
                      NodusOMDrawingTool.class, "Layer to transfer to", "Layer to transfer to")),
              NodusC.APPNAME,
              JOptionPane.QUESTION_MESSAGE,
              null,
              possibleValues,
              possibleValues[0]);

      if (selectedValue != null) {
        // Find index of the layer to transfer to
        int index;

        for (index = 0; index < layers.length; index++) {
          if (selectedValue.equals(layers[index].getName())) {
            break;
          }
        }

        // Find index of selected graphic
        int idx = 0;
        EsriGraphicList list = layers[layerIndexOfSelectedGraphic].getEsriGraphicList();
        Iterator<OMGraphic> it = list.iterator();

        while (it.hasNext()) {
          EsriGraphic epl = (EsriGraphic) it.next();

          if (epl == selectedGraphic) {
            break;
          } else {
            idx++;
          }
        }

        int num =
            JDBCUtils.getInt(
                layers[layerIndexOfSelectedGraphic].getModel().getValueAt(idx, NodusC.DBF_IDX_NUM));
        String t = "link";
        if (selectedGraphic instanceof EsriPoint) {
          t = "node";
        }
        Nodus.nodusLogger.info(
            "Transfer "
                + t
                + " "
                + num
                + " from "
                + layers[layerIndexOfSelectedGraphic].getName()
                + " to "
                + layers[index].getName());

        // Add to link to the relevant layer
        layers[index].addRecord(
            (EsriPolyline) selectedGraphic,
            layers[layerIndexOfSelectedGraphic].getModel().getRecord(idx));
        layers[index].attachStyles();
        layers[index].doPrepare();

        // Delete from original layer
        layers[layerIndexOfSelectedGraphic].removeRecord(idx);
        layers[layerIndexOfSelectedGraphic].doPrepare();
      }
    }
  }
}
