/**
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

package com.bbn.openmap.layer.image.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.image.NodusWMSLayer;
import com.bbn.openmap.layer.image.WMSLayer;
import com.bbn.openmap.layer.image.capabilities.CapabilitiesTreeNode;
import com.bbn.openmap.layer.image.capabilities.ICapabilitiesNodeInterface;
import com.bbn.openmap.layer.image.capabilities.WmsLayerInfo;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.swing.EscapeDialog;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * Dialog-box that lists the available layers on the server and the layers currently present in the
 * query string. The inputStream must correspond to the XML file returned by a GetCapabilities query
 * on the WMS server.
 *
 * @author Bart Jourquin
 */
public class WmsLayersChooserDlg extends EscapeDialog implements MouseListener {

  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = -5315204521446748054L;

  private JButton addButton = new JButton(i18n.get(WmsLayersChooserDlg.class, "Add", "Add"));

  private JScrollPane availableLayersScrollPane = new JScrollPane();

  private JButton cancelButton = new JButton();

  private JButton clearButton = new JButton();

  private JPanel controlButtonsPanel = new JPanel();

  private GridBagLayout controlButtonsPanelGridBagLayout = new GridBagLayout();

  private JButton deleteButton = new JButton();

  private JButton downButton = new JButton();

  private JLabel infoLabel = new JLabel();

  private JScrollPane infoScrollPane = new JScrollPane();

  private JTextArea infoTextArea = new JTextArea();

  private JList<Object> layerList = new JList<Object>();

  private JPanel layersControlPanel = new JPanel();

  private GridBagLayout layersControlPanelGridBagLayout = new GridBagLayout();

  private JTree layerTree;

  private DefaultListModel<Object> listModel = new DefaultListModel<Object>();

  private JPanel mainPanel = new JPanel();

  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  private NodusWMSLayer nodusWmsLayer;

  private JButton okButton = new JButton();

  private String oldLayers;

  private String prefix;

  private Properties properties;

  private JScrollPane selectedLayersScrollPane = new JScrollPane();

  private Vector<?> treeData;

  private JButton upButton = new JButton();

  /**
   * Constructs and initializes the dialog-box.
   *
   * <p>The treeData vector must contain the information parsed from a getCapabilities request.
   * TreeData is typically created by the getCapabilities() method of NodusWMSLayer.
   *
   * @param nodusWmsLayer The layer for which the WMS server is queried.
   */
  public WmsLayersChooserDlg(NodusWMSLayer nodusWmsLayer) {
    super((Frame) null, "", false);

    setTitle(i18n.get(WmsLayersChooserDlg.class, "WMS_layers", "WMS layers"));

    this.nodusWmsLayer = nodusWmsLayer;
    this.treeData = nodusWmsLayer.getTreeData();
    this.properties = nodusWmsLayer.getProperties();
    this.prefix = nodusWmsLayer.getPrefix();

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    initialize();
    getRootPane().setDefaultButton(okButton);
    setLocationRelativeTo(null);
    setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
  }

  private void addLayerToListActionPerformed(ActionEvent e) {
    TreePath selPath = layerTree.getSelectionPath();
    if (selPath != null) {
      ICapabilitiesNodeInterface node =
          (ICapabilitiesNodeInterface)
              ((DefaultMutableTreeNode) selPath.getPathComponent(selPath.getPathCount() - 1))
                  .getUserObject();
      WmsLayerInfo info = node.getLayerInformation();

      // Only takes "real" layers, for which there is a LatLonBoundingBox...
      if (info.getField("name") == null) {
        infoTextArea.setText(
            i18n.get(WmsLayersChooserDlg.class, "No_ a_valid_layer", "Not a valid layer"));
      } else {
        if (!listModel.contains(info)) {
          listModel.addElement(info);
        }

        infoTextArea.setText(i18n.get(WmsLayersChooserDlg.class, "Layer_added", "Layer added"));
        updateProperties();
      }
    }
  }

  /**
   * Just closes the dialog-box.
   *
   * @param e ActionEvent
   */
  private void cancelButton_actionPerformed(ActionEvent e) {
    // Put the result in the properties
    properties.put(prefix + WMSLayer.LayersProperty, oldLayers);
    nodusWmsLayer.updateLayer(properties);
    setVisible(false);
  }

  /**
   * Clears the selected layers list.
   *
   * @param e ActionEvent
   */
  private void clearButton_actionPerformed(ActionEvent e) {
    listModel.clear();
    updateProperties();
  }

  /**
   * Deletes a layer from the selected layers list.
   *
   * @param e ActionEvent
   */
  private void deleteButton_actionPerformed(ActionEvent e) {
    // Be sure one is selected
    int n = layerList.getSelectedIndex();

    if (n == -1) {
      return;
    }

    List<Object> entries = layerList.getSelectedValuesList();

    for (Object element : entries) {
      listModel.removeElement(element);
    }

    layerList.setSelectedIndex(n - 1);

    if (layerList.getSelectedIndex() < 0) {
      layerList.setSelectedIndex(listModel.size() - 1);
    }
    updateProperties();
  }

  /**
   * Moves the selected layer down in the list.
   *
   * @param e ActionEvent
   */
  private void downButton_actionPerformed(ActionEvent e) {
    int index = layerList.getSelectedIndex();

    if (!(index < listModel.size() - 1) || index == -1) {
      return;
    }

    Object curr = listModel.remove(index);
    listModel.add(index + 1, curr);
    layerList.setSelectedIndex(index + 1);
    updateProperties();
  }

  /**
   * Find the layer in the tree which corresponds to the given name. Adds the corresponding
   * WmsLayerInfo if valid layers is found.
   *
   * @param tnode TreeNode
   * @param name String
   */
  private void findNodeForLayer(TreeNode tnode, String name) {
    // Get the user data associated to this TreeNode
    boolean found = false;
    DefaultMutableTreeNode dftn = (DefaultMutableTreeNode) tnode;
    Object o = dftn.getUserObject();

    // Transform into WmsLayerinfo if possible
    if (o instanceof CapabilitiesTreeNode) {
      CapabilitiesTreeNode ctn = (CapabilitiesTreeNode) o;
      WmsLayerInfo info = ctn.getLayerInformation();

      // Is it the layer we are looking for?
      String n = info.getField("name");

      if (n != null && info.getLatLonBoundingBox() != null && n.equals(name)) {
        // Node found. Add it to the list.
        listModel.addElement(info);
        found = true;
      }
    }

    // Recursively browse the JTree
    if (tnode.getChildCount() >= 0) {
      for (Enumeration<?> e = tnode.children(); e.hasMoreElements(); ) {
        TreeNode n = (TreeNode) e.nextElement();

        if (!found) {
          findNodeForLayer(n, name);
        }
      }
    }
  }

  /**
   * GUI components setup.
   *
   * @throws Exception On error
   */
  private void initialize() {
    infoScrollPane.setPreferredSize(new Dimension(250, 100));
    availableLayersScrollPane.setPreferredSize(new Dimension(250, 100));
    selectedLayersScrollPane.setPreferredSize(new Dimension(250, 100));
    mainPanel.setLayout(mainPanelGridBagLayout);
    cancelButton.setText(i18n.get(WmsLayersChooserDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelButton_actionPerformed(e);
          }
        });

    okButton.setText(i18n.get(WmsLayersChooserDlg.class, "Ok", "Ok"));
    okButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            okButton_actionPerformed(e);
          }
        });

    upButton.setText(i18n.get(WmsLayersChooserDlg.class, "Up", "Up"));
    upButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            upButton_actionPerformed(e);
          }
        });

    downButton.setText(i18n.get(WmsLayersChooserDlg.class, "Down", "Down"));
    downButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            downButton_actionPerformed(e);
          }
        });

    deleteButton.setText(i18n.get(WmsLayersChooserDlg.class, "Delete", "Delete"));
    deleteButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            deleteButton_actionPerformed(e);
          }
        });

    clearButton.setText(i18n.get(WmsLayersChooserDlg.class, "Clear", "Clear"));
    clearButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            clearButton_actionPerformed(e);
          }
        });

    infoLabel.setText(i18n.get(WmsLayersChooserDlg.class, "Layer_info", "Layer info"));
    infoTextArea.setRows(4);
    infoTextArea.setEditable(false);

    setResizable(true);
    controlButtonsPanel.setLayout(controlButtonsPanelGridBagLayout);
    layersControlPanel.setLayout(layersControlPanelGridBagLayout);

    setContentPane(mainPanel);
    selectedLayersScrollPane.setViewportView(layerList);

    layerTree = new JTree(treeData);
    layerTree.setModel(new JTree(treeData).getModel());

    layerTree.addMouseListener(this);
    availableLayersScrollPane.setViewportView(layerTree);
    infoScrollPane.setViewportView(infoTextArea);

    if (treeData != null) {
      layerList.setModel(listModel);
    }

    controlButtonsPanel.add(
        okButton,
        new GridBagConstraints(
            1,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    layersControlPanel.add(
        clearButton,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 5),
            0,
            0));

    GridBagConstraints gbcAddButton = new GridBagConstraints();
    gbcAddButton.insets = new Insets(5, 5, 0, 5);
    gbcAddButton.gridx = 1;
    gbcAddButton.gridy = 0;
    addButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            addLayerToListActionPerformed(e);
          }
        });
    layersControlPanel.add(addButton, gbcAddButton);
    layersControlPanel.add(
        upButton,
        new GridBagConstraints(
            4,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 0),
            0,
            0));
    layersControlPanel.add(
        deleteButton,
        new GridBagConstraints(
            2,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 5),
            0,
            0));
    layersControlPanel.add(
        downButton,
        new GridBagConstraints(
            3,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 5),
            0,
            0));
    mainPanel.add(
        infoLabel,
        new GridBagConstraints(
            0,
            3,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 0),
            0,
            0));
    controlButtonsPanel.add(
        cancelButton,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        controlButtonsPanel,
        new GridBagConstraints(
            0,
            5,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 0, 0),
            0,
            0));
    mainPanel.add(
        layersControlPanel,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 0, 0),
            0,
            0));
    mainPanel.add(
        selectedLayersScrollPane,
        new GridBagConstraints(
            0,
            0,
            3,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        infoScrollPane,
        new GridBagConstraints(
            0,
            4,
            3,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        availableLayersScrollPane,
        new GridBagConstraints(
            0,
            2,
            3,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(5, 5, 5, 5),
            0,
            0));

    // Initialises the JList with the already selected layers
    String layers = properties.getProperty(prefix + WMSLayer.LayersProperty, "");
    oldLayers = layers;

    if (!layers.equals("")) {
      StringTokenizer st = new StringTokenizer(layers, ",");

      while (st.hasMoreTokens()) {
        String currentName = st.nextToken().replace('+', ' ');
        findNodeForLayer((TreeNode) layerTree.getModel().getRoot(), currentName);
      }
    }
    nodusWmsLayer.updateLayer(properties);
    pack();
    setAlwaysOnTop(true);
  }

  /**
   * .
   *
   * @exclude
   */
  @Override
  public void mouseClicked(MouseEvent e) {

  }

  /**
   * .
   *
   * @exclude
   */
  @Override
  public void mouseEntered(MouseEvent e) {

  }

  /**
   * .
   *
   * @exclude
   */
  @Override
  public void mouseExited(MouseEvent e) {

  }

  /**
   * Handles mouse events: A single click will update the info text area with the details of the
   * selected layer. A double click will add the selected layer to the list of layers to display.
   *
   * @param e MouseEvent
   */
  @Override
  public void mousePressed(MouseEvent e) {
    // Retrieve the WmsLayerInfo associated to the selected node
    TreePath selPath = layerTree.getPathForLocation(e.getX(), e.getY());

    if (selPath != null) {
      ICapabilitiesNodeInterface node =
          (ICapabilitiesNodeInterface)
              ((DefaultMutableTreeNode) selPath.getPathComponent(selPath.getPathCount() - 1))
                  .getUserObject();
      WmsLayerInfo info = node.getLayerInformation();

      infoLabel.setText(
          MessageFormat.format(i18n.get(WmsLayersChooserDlg.class, "Layer", "Layer: {0}"), info));

      // Handles a double-click. Only takes "real" layers, for which there is a LatLonBoundingBox...
      if (e.getClickCount() == 2) {
        if (info.getField("name") == null) {
          infoTextArea.setText(
              i18n.get(WmsLayersChooserDlg.class, "No_ a_valid_layer", "Not a valid layer"));
        } else {
          if (!listModel.contains(info)) {
            listModel.addElement(info);
          }

          infoTextArea.setText(i18n.get(WmsLayersChooserDlg.class, "Layer_added", "Layer added"));
          updateProperties();
        }
      } else {
        // A single click...
        String abstr;

        if (info.getField("abstract") != null) {
          abstr = info.getField("abstract");
        } else {
          abstr = "";
        }

        Vector<Float> bb = new Vector<>();
        float[] bbF = info.getLatLonBoundingBox();

        if (bbF != null) {
          for (float element : bbF) {
            bb.add(Float.valueOf(element));
          }
        }

        infoTextArea.setText(
            "LatLonBoundingBox: "
                + bb
                + "\n"
                + "Formats"
                + ": "
                + info.getServerInformation().getGetMapFormats()
                + "\n\n"
                + "Abstract"
                + ":\n"
                + abstr);
      }
    }
  }

  /**
   * .
   *
   * @exclude
   */
  @Override
  public void mouseReleased(MouseEvent e) {

  }

  /**
   * Close the dialog box.
   *
   * @param e ActionEvent
   */
  private void okButton_actionPerformed(ActionEvent e) {
    // Close dialog
    setVisible(false);
  }

  /**
   * Moves the selected layer up in the list.
   *
   * @param e ActionEvent
   */
  private void upButton_actionPerformed(ActionEvent e) {
    int index = layerList.getSelectedIndex();

    if (index < 1) {
      return;
    }

    Object oldPrev = listModel.remove(index - 1);
    listModel.add(index, oldPrev);
    layerList.setSelectedIndex(index - 1);
    updateProperties();
  }

  /** Store the selected layers in the properties for later use. */
  private void updateProperties() {
    // Create a string that represent the selected layers
    String layers = "";

    int n = layerList.getModel().getSize();

    for (int i = 0; i < n; i++) {
      if (layers.length() > 0) {
        layers += ",";
      }

      Object o = layerList.getModel().getElementAt(i);

      if (o instanceof WmsLayerInfo) {
        WmsLayerInfo info = (WmsLayerInfo) layerList.getModel().getElementAt(i);
        layers += info.getField("name").replace(' ', '+');
      }
    }

    // Put the result in the properties
    properties.put(prefix + WMSLayer.LayersProperty, layers);
    nodusWmsLayer.updateLayer(properties);
  }
}
