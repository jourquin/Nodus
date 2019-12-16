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

package com.bbn.openmap.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.ProjectFilesTools;
import edu.uclouvain.core.nodus.swing.EscapeDialog;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Dialog box that offers the possibility to add and remove ESRI layers to a Nodus project. It also
 * allows to create new empty layers with or without a template.
 *
 * @author Bart Jourquin
 */
public class NodusEditLayersDlg extends EscapeDialog implements ShapeConstants {

  /** IN18. */
  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = 1650306149898072838L;

  /** Add layer button. */
  private JButton addButton = new JButton();

  /** Label. */
  private JLabel availableLayersLabel = new JLabel();

  /** List of available layers. */
  private JList<String> availableList = new JList<String>(new DefaultListModel<String>());

  /** Another scroll pane. */
  private JScrollPane availableScrollPane = new JScrollPane();

  /** Button group. */
  private ButtonGroup buttonGroup = new ButtonGroup();

  /** Cancel button. */
  private JButton cancelButton = new JButton();

  /** Delete layer button. */
  private JButton deleteButton = new JButton();

  /** List of link layers. */
  private LinkedList<String> linkLayerList;

  /** Select link layers. */
  private JRadioButton linkRadioButton = new JRadioButton();

  /** Panel. */
  private JPanel mainPanel = new JPanel();

  /** Layout manager. */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** New layer button. */
  private JButton newButton = new JButton();

  /** List of node layers. */
  private LinkedList<String> nodeLayerList;

  /** Select node layers. */
  private JRadioButton nodeRadioButton = new JRadioButton();

  /** Project currently loaded. */
  private NodusProject nodusProject = null;

  /** Path to project. */
  private String path;

  /** Remove layer button. */
  private JButton removeButton = new JButton();

  /** Save button. */
  private JButton saveButton = new JButton();

  /** Label. */
  private JLabel selectedLayersLabel = new JLabel();

  /** List of selected layers. */
  private JList<String> selectedList = new JList<String>(new DefaultListModel<String>());

  /** Scroll pane. */
  private JScrollPane selectedScrollPane = new JScrollPane();

  /**
   * Initialization of the GUI.
   *
   * @param mapPanel NodusMapPanel
   */
  public NodusEditLayersDlg(NodusMapPanel mapPanel) {
    super(
        mapPanel.getMainFrame(),
        i18n.get(
            NodusEditLayersDlg.class, "Add_and_remove_Nodus_layers", "Add and remove Nodus layers"),
        true);

    nodusProject = mapPanel.getNodusProject();

    // Initialize the lists of current layers
    nodeLayerList = new LinkedList<>();
    linkLayerList = new LinkedList<>();

    path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

    String layers = nodusProject.getProperty(NodusC.PROP_NETWORK_NODES);
    StringTokenizer st = new StringTokenizer(layers);

    while (st.hasMoreTokens()) {
      String currentName = st.nextToken();
      nodeLayerList.add(currentName);
    }

    layers = nodusProject.getProperty(NodusC.PROP_NETWORK_LINKS);
    st = new StringTokenizer(layers);

    while (st.hasMoreTokens()) {
      String currentName = st.nextToken();
      linkLayerList.add(currentName);
    }

    initialize();
    getRootPane().setDefaultButton(saveButton);
    setLocationRelativeTo(mapPanel);
  }

  /**
   * Add a new layer to the list, if one is selected and not yet present in the project list.
   *
   * @param e ActionEvent
   */
  void addButtonActionPerformed(ActionEvent e) {

    String layerName = availableList.getSelectedValue();

    if (layerName == null) {
      return;
    }

    byte layerType = SHAPE_TYPE_POINT;
    LinkedList<String> list = nodeLayerList;

    if (linkRadioButton.isSelected()) {
      layerType = SHAPE_TYPE_POLYLINE;
      list = linkLayerList;
    }

    if (!list.contains(layerName)) {
      list.add(layerName);
    }

    fillLayerList(layerType);
  }

  /**
   * Just close the dialog-box.
   *
   * @param e ActionEvent
   */
  void cancelButtonActionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /**
   * Deletes (after confirmation) the files related to the selected layer from the disk.
   *
   * @param e ActionEvent
   */
  void deleteButtonActionPerformed(ActionEvent e) {

    // A layer must be selected
    String layerName = availableList.getSelectedValue();

    if (layerName == null) {
      return;
    }

    // Ask a confirmation
    int answer =
        JOptionPane.showConfirmDialog(
            this,
            i18n.get(
                NodusEditLayersDlg.class,
                "Permanently_delete_selected_layers?",
                "Permanently delete selected layers?"),
            NodusC.APPNAME,
            JOptionPane.YES_NO_OPTION);

    if (answer == JOptionPane.NO_OPTION) {
      return;
    }

    String tableName = layerName;
    ProjectFilesTools.deleteLayerFiles(path, tableName);
    JDBCUtils jdbcUtils = new JDBCUtils(nodusProject.getMainJDBCConnection());
    jdbcUtils.dropTable(tableName);

    // Refresh list
    byte layerType = SHAPE_TYPE_POINT;

    if (linkRadioButton.isSelected()) {
      layerType = SHAPE_TYPE_POLYLINE;
    }

    fillLayerList(layerType);
  }

  /**
   * Fill the list with the available node or link layers.
   *
   * @param layerType int
   */
  void fillLayerList(int layerType) {
    // Get available valid layers
    String[] layers = ProjectFilesTools.getAvailableLayers(path, layerType);

    ((DefaultListModel<String>) availableList.getModel()).clear();

    for (String layer : layers) {
      ((DefaultListModel<String>) availableList.getModel()).addElement(layer);
    }

    LinkedList<String> currentLayers;

    if (layerType == SHAPE_TYPE_POINT) {
      currentLayers = nodeLayerList;
    } else {
      currentLayers = linkLayerList;
    }

    ((DefaultListModel<String>) selectedList.getModel()).clear();

    Iterator<String> it = currentLayers.iterator();

    while (it.hasNext()) {
      ((DefaultListModel<String>) selectedList.getModel()).addElement(it.next());
    }
  }

  /** Creates and initializes all the GUI components. */
  private void initialize() {
    GridBagConstraints gridBagConstraints =
        new GridBagConstraints(
            0,
            1,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 10, 0, 0),
            0,
            0);
    gridBagConstraints.insets = new Insets(0, 10, 0, 10);
    mainPanel.setLayout(mainPanelGridBagLayout);
    nodeRadioButton.setSelected(true);
    nodeRadioButton.setText(i18n.get(NodusEditLayersDlg.class, "Nodes", "Nodes"));

    nodeRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            nodeRadioButtonActionPerformed(e);
          }
        });

    linkRadioButton.setMargin(new Insets(2, 2, 2, 2));
    linkRadioButton.setText(i18n.get(NodusEditLayersDlg.class, "Links", "Links"));

    linkRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            linkRadioButtonActionPerformed(e);
          }
        });

    setResizable(true);

    buttonGroup.add(nodeRadioButton);
    buttonGroup.add(linkRadioButton);

    addButton.setText("<");

    addButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            addButtonActionPerformed(e);
          }
        });

    cancelButton.setText(i18n.get(NodusEditLayersDlg.class, "Cancel", "Cancel"));

    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelButtonActionPerformed(e);
          }
        });

    newButton.setText(i18n.get(NodusEditLayersDlg.class, "New", "New"));

    newButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            newButtonActionPerformed(e);
          }
        });

    saveButton.setText(i18n.get(NodusEditLayersDlg.class, "Save", "Save"));

    saveButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            saveButtonActionPerformed(e);
          }
        });

    removeButton.setText(">");

    removeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            removeButtonActionPerformed(e);
          }
        });

    selectedLayersLabel.setText(
        i18n.get(NodusEditLayersDlg.class, "Selected_layers", "Selected layers"));

    availableLayersLabel.setText(
        i18n.get(NodusEditLayersDlg.class, "Avail_layers_models", "Avail. layers/models"));

    deleteButton.setText(i18n.get(NodusEditLayersDlg.class, "Delete", "Delete"));

    deleteButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            deleteButtonActionPerformed(e);
          }
        });

    selectedScrollPane.setMaximumSize(new Dimension(100, 150));
    selectedScrollPane.setMinimumSize(new Dimension(100, 150));
    selectedScrollPane.setPreferredSize(new Dimension(100, 150));
    availableScrollPane.setMaximumSize(new Dimension(100, 150));
    availableScrollPane.setMinimumSize(new Dimension(100, 150));
    availableScrollPane.setPreferredSize(new Dimension(100, 150));
    setModal(true);

    setContentPane(mainPanel);

    availableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    mainPanel.add(
        selectedScrollPane,
        new GridBagConstraints(
            0,
            2,
            2,
            2,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        nodeRadioButton,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(10, 10, 0, 0),
            0,
            0));
    mainPanel.add(
        availableScrollPane,
        new GridBagConstraints(
            3,
            2,
            2,
            2,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        linkRadioButton,
        new GridBagConstraints(
            1,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(10, 0, 0, 5),
            0,
            0));
    mainPanel.add(
        saveButton,
        new GridBagConstraints(
            3,
            4,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 5),
            0,
            0));
    mainPanel.add(selectedLayersLabel, gridBagConstraints);
    availableScrollPane.getViewport().add(availableList, null);
    selectedScrollPane.getViewport().add(selectedList, null);
    mainPanel.add(
        cancelButton,
        new GridBagConstraints(
            4,
            4,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(10, 0, 10, 5),
            0,
            0));
    mainPanel.add(
        availableLayersLabel,
        new GridBagConstraints(
            3,
            1,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 10, 0, 0),
            0,
            0));
    mainPanel.add(
        deleteButton,
        new GridBagConstraints(
            4,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(10, 0, 5, 5),
            0,
            0));
    mainPanel.add(
        newButton,
        new GridBagConstraints(
            3,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(10, 5, 5, 0),
            0,
            0));
    mainPanel.add(
        addButton,
        new GridBagConstraints(
            2,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTH,
            GridBagConstraints.NONE,
            new Insets(0, 10, 5, 10),
            0,
            0));
    mainPanel.add(
        removeButton,
        new GridBagConstraints(
            2,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTH,
            GridBagConstraints.NONE,
            new Insets(5, 10, 0, 10),
            0,
            0));

    // Now fill the list with the available node layers
    fillLayerList(SHAPE_TYPE_POINT);

    pack();
  }

  /**
   * Initializes the list with the available link layers.
   *
   * @param e ActionEvent
   */
  void linkRadioButtonActionPerformed(ActionEvent e) {
    fillLayerList(SHAPE_TYPE_POLYLINE);
  }

  /**
   * Creates a new layer. The type depends on the selection found in the radiobutton. If no layer is
   * selected in the project list, only the mandatory fields will be created in the .dbf file. Else,
   * the user if asked if he wants to create a new .dbf with the same structure as the selected one.
   *
   * @param e ActionEvent
   */
  void newButtonActionPerformed(ActionEvent e) {
    if (nodusProject == null) {
      // Just for VE
    } else {
      // Ask for layer name
      String text =
          JOptionPane.showInputDialog(
              nodusProject.getMainFrame(),
              i18n.get(NodusEditLayersDlg.class, "Name_of_new_layer", "Name of new layer"));

      if (text == null) {
        return;
      }

      // Test if layer doesn't already exist
      File f = new File(path + text + NodusC.TYPE_SHP);
      if (f.exists() && !f.isDirectory()) {
        int answer =
            JOptionPane.showConfirmDialog(
                nodusProject.getMainFrame(),
                i18n.get(NodusEditLayersDlg.class, "Overwrite?", "Overwrite?"),
                i18n.get(NodusEditLayersDlg.class, "Layer_already_exists", "Layer already exists"),
                JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.NO_OPTION) {
          return;
        }
      }

      // Ask if new dbf file must be the same structure as the selected
      // one
      Object o = availableList.getSelectedValue();
      String model = null;

      if (o != null) {
        String question =
            MessageFormat.format(
                i18n.get(
                    NodusEditLayersDlg.class,
                    "Same_Dbf_structure_as",
                    "Same Dbf structure as {0}?"),
                (String) o);

        int answer =
            JOptionPane.showConfirmDialog(
                this, question, NodusC.APPNAME, JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.YES_OPTION) {
          model = (String) o;
        }
      }

      // OK, create new layer and update listbox
      byte layerType = SHAPE_TYPE_POINT;

      if (linkRadioButton.isSelected()) {
        layerType = SHAPE_TYPE_POLYLINE;
      }

      ProjectFilesTools.createEmptyLayer(path, text, layerType, model);
      fillLayerList(layerType);
    }
  }

  /**
   * Initializes the list with the available node layers.
   *
   * @param e ActionEvent
   */
  void nodeRadioButtonActionPerformed(ActionEvent e) {
    fillLayerList(SHAPE_TYPE_POINT);
  }

  /**
   * Removes a layer from the project list.
   *
   * @param e ActionEvent
   */
  void removeButtonActionPerformed(ActionEvent e) {

    String layerName = selectedList.getSelectedValue();

    if (layerName == null) {
      return;
    }

    byte layerType = SHAPE_TYPE_POINT;
    LinkedList<String> list = nodeLayerList;

    if (linkRadioButton.isSelected()) {
      layerType = SHAPE_TYPE_POLYLINE;
      list = linkLayerList;
    }

    list.remove(layerName);

    fillLayerList(layerType);
  }

  /**
   * Asks if the changes must be saved. If yes, the new layer lists are saved in the project
   * property file and the project is reloaded.
   *
   * @param e ActionEvent
   */
  void saveButtonActionPerformed(ActionEvent e) {
    if (nodusProject == null) {
      // Just for VE
    } else {
      // Save into property file and reload project
      int answer =
          JOptionPane.showConfirmDialog(
              this,
              i18n.get(
                  NodusEditLayersDlg.class, "Save_and_reload_project?", "Save and reload project?"),
              NodusC.APPNAME,
              JOptionPane.YES_NO_OPTION);

      if (answer == JOptionPane.NO_OPTION) {
        return;
      }

      // Save the layer names in the project property file
      // The layer names are trimmed
      String s = "";
      Iterator<String> it = nodeLayerList.iterator();

      while (it.hasNext()) {
        s += it.next() + " ";
      }

      s = s.trim();
      nodusProject.setProperty(NodusC.PROP_NETWORK_NODES, s);

      s = "";
      it = linkLayerList.iterator();

      while (it.hasNext()) {
        s += it.next() + " ";
      }

      s = s.trim();
      nodusProject.setProperty(NodusC.PROP_NETWORK_LINKS, s);

      // Close the dialog and reload project
      setVisible(false);
      String msg =
          i18n.get(
              NodusEditLayersDlg.class,
              "Project_must_be_reloaded",
              "Project must be reloaded to take new layer structure into account.");
      
      JOptionPane.showMessageDialog(null, msg, NodusC.APPNAME, JOptionPane.WARNING_MESSAGE);

      nodusProject.reload();
    }
  }
}
