/*
 * Copyright (c) 1991-2023 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.apache.commons.io.filefilter.WildcardFileFilter;

/**
 * Creates a dialog that allows the user to change the preferences for a given Nodus project.
 *
 * @author Bart Jourquin
 */
public class ProjectPreferencesDlg extends EscapeDialog {

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 291675906672191547L;

  /** . */
  private JCheckBox boundariesCheckbox = null;

  /** . */
  private JButton cancelButton = new JButton();

  /** . */
  private JCheckBox compactCheckBox;

  /** . */
  private JComboBox<String> costFilesCombo = new JComboBox<>();

  /** . */
  private JLabel costLabel = new JLabel();

  /** . */
  private File[] costsFilesNames = null;

  /** . */
  private JTextField descriptionTextField = new JTextField();

  /** . */
  private JLabel excLabel = new JLabel();

  /** . */
  private JTextField excTextField = new JTextField();

  /** . */
  private JCheckBox highlightedAreaCheckBox = null;

  /** . */
  private JPanel mainPanel = new JPanel();

  /** . */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** . */
  private NodusProject nodusProject;

  /** . */
  private JLabel odLabel = new JLabel();

  /** . */
  private Vector<String> odTables = null;

  /** . */
  private JComboBox<String> odTablesCombo = new JComboBox<>();

  /** . */
  private JButton okButton = new JButton();

  /** . */
  private boolean oldAddHighlightedArea;

  /** . */
  private boolean oldAddPoliticalBoundaries;

  /** . */
  private JLabel pathLabel = new JLabel();

  /** . */
  private JTextField pathTextField = new JTextField();

  /** . */
  private JLabel radiusLabel = new JLabel();

  /** . */
  private JSpinner radiusSpinner = new JSpinner();

  /** . */
  private JCheckBox saveAllVirtualLinksCheckBox;

  /** . */
  private JLabel scenarioLabel = new JLabel();

  /** . */
  private JSpinner scenarioSpinner = new JSpinner();

  /** . */
  private JLabel servicesLabel = null;

  /** . */
  private JTextField servicesTextField = null;
  
  /** . */
  private JLabel unitLabel = new JLabel();

  /** . */
  private JLabel virtNetLabel = new JLabel();

  /** . */
  private JTextField virtNetTextField = new JTextField();

  /** . */
  private JLabel widthLabel = new JLabel();

  /** . */
  private JSpinner widthSpinner = new JSpinner();

  /**
   * Opens a dialog in which the user can modify some preferences for the given project.
   *
   * @param nodusProject The Nodus project.
   */
  public ProjectPreferencesDlg(NodusProject nodusProject) {
    super(nodusProject.getNodusMapPanel().getMainFrame(), "", true);

    this.nodusProject = nodusProject;
  
    initialize();
    getRootPane().setDefaultButton(okButton);
    setLocationRelativeTo(nodusProject.getNodusMapPanel());
  }

  /**
   * Just closes the dialog.
   *
   * @param e ActionEvent
   */
  private void cancelButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /** Fills the OD table combo with the available OD tables. */
  private void fillODTablesCombo() {
    final Cursor oldCursor = getCursor();
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    final Object currentSelection = odTablesCombo.getSelectedItem();

    // Fill the combo of OD tables
    odTablesCombo.removeAllItems();
    odTables = ODReader.getValidODTables(nodusProject);
    Iterator<String> it = odTables.iterator();
    while (it.hasNext()) {
      odTablesCombo.addItem(it.next());
    }

    // Force a redraw of the popup menu
    int n = odTablesCombo.getMaximumRowCount();
    odTablesCombo.setMaximumRowCount(n - 1);
    odTablesCombo.setMaximumRowCount(n);

    if (currentSelection != null) {
      odTablesCombo.setSelectedItem(currentSelection);
    }
    setCursor(oldCursor);
  }

  /**
   * This method initializes jCheckBox.
   *
   * @return JCheckBox
   */
  private JCheckBox getBoundariesCheckbox() {
    if (boundariesCheckbox == null) {
      boundariesCheckbox = new JCheckBox();
      boundariesCheckbox.setText(
          i18n.get(ProjectPreferencesDlg.class, "Display_boundaries", "Display boundaries"));
      boundariesCheckbox.setOpaque(false);
    }

    return boundariesCheckbox;
  }

  /**
   * This method initializes activeAreaCheckBox.
   *
   * @returnJCheckBox
   */
  private JCheckBox getHighlightedAreaCheckBox() {
    if (highlightedAreaCheckBox == null) {
      highlightedAreaCheckBox = new JCheckBox();
      highlightedAreaCheckBox.setText(
          i18n.get(
              ProjectPreferencesDlg.class, "Display_highlighted_area", "Display highlighted area"));

      highlightedAreaCheckBox.setOpaque(false);
    }
    return highlightedAreaCheckBox;
  }

  /** GUI initialization. */
  private void initialize() {
    GridBagConstraints forceGarbageCollectorConstraints = new GridBagConstraints();
    forceGarbageCollectorConstraints.gridx = 1;
    forceGarbageCollectorConstraints.gridwidth = 4;
    forceGarbageCollectorConstraints.insets = new Insets(5, 5, 5, 0);
    forceGarbageCollectorConstraints.anchor = GridBagConstraints.WEST;
    forceGarbageCollectorConstraints.gridy = 11;
    GridBagConstraints servicesTextFieldConstraints = new GridBagConstraints();
    servicesTextFieldConstraints.fill = GridBagConstraints.BOTH;
    servicesTextFieldConstraints.gridy = 7;
    servicesTextFieldConstraints.insets = new Insets(5, 5, 5, 0);
    servicesTextFieldConstraints.gridwidth = 4;
    servicesTextFieldConstraints.gridx = 1;

    GridBagConstraints servicesLabelConstraints = new GridBagConstraints();
    servicesLabelConstraints.gridx = 0;
    servicesLabelConstraints.insets = new Insets(5, 5, 5, 5);
    servicesLabelConstraints.anchor = GridBagConstraints.WEST;
    servicesLabelConstraints.gridy = 7;
    servicesLabel = new JLabel();
    servicesLabel.setText(
        i18n.get(ProjectPreferencesDlg.class, "Services_prefix", "Services prefix:"));
    servicesTextField = new JTextField();

    GridBagConstraints radiusSpinnerConstraints =
        new GridBagConstraints(
            1,
            7,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    radiusSpinnerConstraints.gridy = 9;
    radiusSpinnerConstraints.fill = GridBagConstraints.HORIZONTAL;

    GridBagConstraints widthSpinnerConstraints =
        new GridBagConstraints(
            1,
            6,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    widthSpinnerConstraints.gridy = 8;
    widthSpinnerConstraints.fill = GridBagConstraints.HORIZONTAL;

    GridBagConstraints radiusLabelConstraints =
        new GridBagConstraints(
            0,
            7,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    radiusLabelConstraints.gridy = 9;

    GridBagConstraints widthLabelConstraints =
        new GridBagConstraints(
            0,
            6,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0);
    widthLabelConstraints.gridy = 8;

    GridBagConstraints cancelButtonConstraints =
        new GridBagConstraints(
            4,
            8,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0);
    cancelButtonConstraints.gridx = 4;
    cancelButtonConstraints.gridy = 14;

    GridBagConstraints okButtonConstraints =
        new GridBagConstraints(
            2,
            8,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 0, 5),
            0,
            0);
    okButtonConstraints.gridx = 1;
    okButtonConstraints.gridy = 14;

    GridBagConstraints highlightedAreaCheckBoxConstraint = new GridBagConstraints();
    highlightedAreaCheckBoxConstraint.gridx = 0;
    highlightedAreaCheckBoxConstraint.anchor = GridBagConstraints.WEST;
    highlightedAreaCheckBoxConstraint.insets = new Insets(0, 5, 5, 5);
    highlightedAreaCheckBoxConstraint.gridy = 11;
    mainPanelGridBagLayout.columnWeights = new double[] {0.0, 1.0, 0.0, 0.0, 0.0};
    mainPanel.setLayout(mainPanelGridBagLayout);
    setTitle(i18n.get(ProjectPreferencesDlg.class, "Project_properties", "Project properties"));
    setModal(true);

    cancelButton.setText(i18n.get(ProjectPreferencesDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelButton_actionPerformed(e);
          }
        });
    okButton.setText(i18n.get(ProjectPreferencesDlg.class, "Ok", "Ok"));
    okButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            okButton_actionPerformed(e);
          }
        });

    odLabel.setText(i18n.get(ProjectPreferencesDlg.class, "OD_matrix_table", "OD matrix table:"));

    /* Because filling the combo with available valid OD tables can take a while,
     * fill it on request only.
     * Adding a MouseListener is a little bit tricky... */
    odTablesCombo.addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent me) {
            if (odTables == null) {
              fillODTablesCombo();
            }
          }
        });
    odTablesCombo.addPopupMenuListener(
        new PopupMenuListener() {

          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            if (odTables == null) {
              fillODTablesCombo();
            }
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {

          }

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {

          }
        });

    GridBagConstraints gbcCommentTextField = new GridBagConstraints();
    gbcCommentTextField.gridwidth = 5;
    gbcCommentTextField.insets = new Insets(5, 5, 5, 0);
    gbcCommentTextField.fill = GridBagConstraints.HORIZONTAL;
    gbcCommentTextField.gridx = 0;
    gbcCommentTextField.gridy = 1;
    mainPanel.add(descriptionTextField, gbcCommentTextField);

    GridBagConstraints boundariesCheckboxConstraints = new GridBagConstraints();

    boundariesCheckboxConstraints.gridx = 0;
    boundariesCheckboxConstraints.gridy = 10;
    boundariesCheckboxConstraints.anchor = java.awt.GridBagConstraints.WEST;
    boundariesCheckboxConstraints.insets = new Insets(5, 5, 5, 5);
    mainPanel.add(getBoundariesCheckbox(), boundariesCheckboxConstraints);

    GridBagConstraints gbcChckbxSaveAllVirtual = new GridBagConstraints();
    gbcChckbxSaveAllVirtual.anchor = GridBagConstraints.WEST;
    gbcChckbxSaveAllVirtual.insets = new Insets(0, 5, 5, 5);
    gbcChckbxSaveAllVirtual.gridx = 0;
    gbcChckbxSaveAllVirtual.gridy = 12;
    saveAllVirtualLinksCheckBox =
        new JCheckBox(
            i18n.get(ProjectPreferencesDlg.class, "Save_all_VL", "Save all virtual links"));
    mainPanel.add(saveAllVirtualLinksCheckBox, gbcChckbxSaveAllVirtual);

    compactCheckBox =
        new JCheckBox(
            i18n.get(ProjectPreferencesDlg.class, "Compact_DB", "Compact DB when closing"));
    GridBagConstraints gbcCompactCheckBox = new GridBagConstraints();
    gbcCompactCheckBox.anchor = GridBagConstraints.WEST;
    gbcCompactCheckBox.insets = new Insets(0, 5, 5, 5);
    gbcCompactCheckBox.gridx = 0;
    gbcCompactCheckBox.gridy = 13;
    mainPanel.add(compactCheckBox, gbcCompactCheckBox);
    if (JDBCUtils.getDbEngine() != JDBCUtils.DB_HSQLDB
        && JDBCUtils.getDbEngine() != JDBCUtils.DB_H2) {
      compactCheckBox.setEnabled(false);
    }

    mainPanel.add(cancelButton, cancelButtonConstraints);
    mainPanel.add(okButton, okButtonConstraints);
    mainPanel.add(
        odLabel,
        new GridBagConstraints(
            0,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    costLabel.setText(
        i18n.get(ProjectPreferencesDlg.class, "Cost_functions_file", "Cost functions file:"));
    mainPanel.add(
        costLabel,
        new GridBagConstraints(
            0,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    virtNetLabel.setText(
        i18n.get(
            ProjectPreferencesDlg.class,
            "Virtual_network_table_prefix",
            "Virtual network table prefix:"));
    mainPanel.add(
        virtNetLabel,
        new GridBagConstraints(
            0,
            5,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    excLabel.setText(
        i18n.get(ProjectPreferencesDlg.class, "Exclusions_table", "Exclusions table:"));
    excTextField.setHorizontalAlignment(SwingConstants.LEFT);
    mainPanel.add(
        excTextField,
        new GridBagConstraints(
            1,
            4,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 5, 0),
            0,
            0));

    pathLabel.setText(
        i18n.get(ProjectPreferencesDlg.class, "Paths_tables_prefix", "Paths tables prefix:"));
    mainPanel.add(
        pathLabel,
        new GridBagConstraints(
            0,
            6,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    unitLabel.setText(i18n.get(ProjectPreferencesDlg.class, "Unit", "Unit:"));
    mainPanel.add(
        unitLabel,
        new GridBagConstraints(
            3,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 0, 5, 5),
            0,
            0));
    unitLabel.setVisible(false);

    widthLabel.setText(i18n.get(ProjectPreferencesDlg.class, "Max_link_width", "Max link width:"));
    radiusLabel.setText(
        i18n.get(ProjectPreferencesDlg.class, "Max_node_radius", "Max node radius:"));
    mainPanel.add(widthLabel, widthLabelConstraints);
    scenarioLabel.setText(
        i18n.get(ProjectPreferencesDlg.class, "Current_scenario", "Scenario and description:"));
    mainPanel.add(radiusLabel, radiusLabelConstraints);
    mainPanel.add(
        scenarioLabel,
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
    setContentPane(mainPanel);

    mainPanel.add(
        odTablesCombo,
        new GridBagConstraints(
            1,
            2,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 5, 0),
            0,
            0));

    mainPanel.add(
        costFilesCombo,
        new GridBagConstraints(
            1,
            3,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 5, 0),
            0,
            0));
    mainPanel.add(
        excLabel,
        new GridBagConstraints(
            0,
            4,
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
        virtNetTextField,
        new GridBagConstraints(
            1,
            5,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 5, 0),
            0,
            0));

    mainPanel.add(
        pathTextField,
        new GridBagConstraints(
            1,
            6,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 5, 0),
            0,
            0));

    mainPanel.add(
        scenarioSpinner,
        new GridBagConstraints(
            1,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 5, 5),
            0,
            0));

    mainPanel.add(widthSpinner, widthSpinnerConstraints);
    mainPanel.add(radiusSpinner, radiusSpinnerConstraints);
    mainPanel.add(getHighlightedAreaCheckBox(), highlightedAreaCheckBoxConstraint);
    mainPanel.add(servicesLabel, servicesLabelConstraints);
    mainPanel.add(servicesTextField, servicesTextFieldConstraints);

    int intDefValue = 0;
    int intValue = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, intDefValue);
    SpinnerNumberModel sp = new SpinnerNumberModel(intValue, 0, NodusC.MAXSCENARIOS - 1, 1);
    scenarioSpinner.setModel(sp);
    scenarioSpinner.addChangeListener(
        new ChangeListener() {
          /* If a new scenario is chosen, try to reload the parameters
           * of a previous assignment for it */
          @Override
          public void stateChanged(ChangeEvent evt) {
            updateValues();
          }
        });

    String stringValue =
        nodusProject.getLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + intValue, "");
    descriptionTextField.setText(stringValue);

    intDefValue = NodusC.MAX_WIDTH;
    intValue = nodusProject.getLocalProperty(NodusC.PROP_MAX_WIDTH, intDefValue);
    sp = new SpinnerNumberModel(intValue, 1, 100, 1);
    widthSpinner.setModel(sp);

    intDefValue = NodusC.MAX_RADIUS;
    intValue = nodusProject.getLocalProperty(NodusC.PROP_MAX_RADIUS, intDefValue);
    sp = new SpinnerNumberModel(intValue, 1, 100, 1);
    radiusSpinner.setModel(sp);

    String stringDefValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
    stringValue = nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, stringDefValue);
    excTextField.setText(stringValue);

    stringDefValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    stringValue = nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, stringDefValue);
    virtNetTextField.setText(stringValue);

    stringDefValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_PATH;
    stringValue = nodusProject.getLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, stringDefValue);
    pathTextField.setText(stringValue);

    stringDefValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_SERVICES;
    stringValue = nodusProject.getLocalProperty(NodusC.PROP_SERVICES_TABLE_PREFIX, stringDefValue);
    servicesTextField.setText(stringValue);

    /*
     * sValue = nodusProject.getLocalProperty(NodusC.PROP_DISTANCES_UNIT, Length.KM.toString());
     * unitComboBox.setSelectedItem(sValue);
     */

    oldAddPoliticalBoundaries =
        nodusProject.getLocalProperty(NodusC.PROP_ADD_POLITICAL_BOUNDARIES, false);
    boundariesCheckbox.setSelected(oldAddPoliticalBoundaries);

    oldAddHighlightedArea = nodusProject.getLocalProperty(NodusC.PROP_ADD_HIGHLIGHTED_AREA, false);
    highlightedAreaCheckBox.setSelected(oldAddHighlightedArea);

    updateValues();
    pack();
  }

  /**
   * Stores the settings in the project property file and closes the dialog.
   *
   * @param e ActionEvent
   */
  private void okButton_actionPerformed(ActionEvent e) {

    String odTableName = "";
    if (odTablesCombo.getSelectedItem() != null) {
      odTableName = odTablesCombo.getSelectedItem().toString().trim();
    }

    final String costFileName = costFilesCombo.getSelectedItem().toString().trim();
    final String excTable = excTextField.getText().trim();
    final String vNetTablePrefix = virtNetTextField.getText().trim();
    final String pathTablePrefix = pathTextField.getText().trim();
    final String serviceTablePrefix = servicesTextField.getText().trim();

    // Save into project properties
    nodusProject.setLocalProperty(
        NodusC.PROP_ADD_POLITICAL_BOUNDARIES, boundariesCheckbox.isSelected());
    nodusProject.setLocalProperty(
        NodusC.PROP_ADD_HIGHLIGHTED_AREA, highlightedAreaCheckBox.isSelected());
    nodusProject.setLocalProperty(
        NodusC.PROP_SAVE_ALL_VN, saveAllVirtualLinksCheckBox.isSelected());
    nodusProject.setLocalProperty(NodusC.PROP_SHUTDOWN_COMPACT, compactCheckBox.isSelected());
    nodusProject.setLocalProperty(NodusC.PROP_SCENARIO, scenarioSpinner.getValue().toString());
    nodusProject.setLocalProperty(NodusC.PROP_MAX_RADIUS, radiusSpinner.getValue().toString());
    nodusProject.setLocalProperty(NodusC.PROP_MAX_WIDTH, widthSpinner.getValue().toString());

    // OD table and cost functions file can be scenario dependent
    String scenarioSuffix = scenarioSpinner.getValue().toString();

    nodusProject.setLocalProperty(
        NodusC.PROP_ASSIGNMENT_DESCRIPTION + scenarioSuffix, descriptionTextField.getText());

    if (odTableName.length() > 0) {
      nodusProject.setLocalProperty(NodusC.PROP_OD_TABLE, odTableName);
      nodusProject.setLocalProperty(NodusC.PROP_OD_TABLE + scenarioSuffix, odTableName);
    }

    if (costFileName.length() > 0) {
      nodusProject.setLocalProperty(NodusC.PROP_COST_FUNCTIONS, costFileName);
      nodusProject.setLocalProperty(NodusC.PROP_COST_FUNCTIONS + scenarioSuffix, costFileName);
    }

    if (excTable.length() > 0) {
      nodusProject.setLocalProperty(NodusC.PROP_EXC_TABLE, excTable);
    }

    if (vNetTablePrefix.length() > 0) {
      nodusProject.setLocalProperty(NodusC.PROP_VNET_TABLE, vNetTablePrefix);
    }

    if (pathTablePrefix.length() > 0) {
      nodusProject.setLocalProperty(NodusC.PROP_PATH_TABLE_PREFIX, pathTablePrefix);
    }

    if (serviceTablePrefix.length() > 0) {
      nodusProject.setLocalProperty(NodusC.PROP_SERVICES_TABLE_PREFIX, serviceTablePrefix);
    }

    // Update the displayed boundaries layer if needed
    if (oldAddPoliticalBoundaries != boundariesCheckbox.isSelected()) {
      nodusProject
          .getNodusMapPanel()
          .displayPoliticalBoundaries(boundariesCheckbox.isSelected(), true);
    }

    // Update the active area layer if needed
    if (oldAddHighlightedArea != highlightedAreaCheckBox.isSelected()) {
      nodusProject
          .getNodusMapPanel()
          .displayHighlightedAreaLayer(highlightedAreaCheckBox.isSelected(), true);
    }

    // Update title with current scenario number
    nodusProject.getNodusMapPanel().updateScenarioComboBox(false);

    setVisible(false);
  }

  /** Updates the content of all the components. */
  private void updateValues() {

    cancelButton.setEnabled(false);
    okButton.setEnabled(false);

    // Suffix to test if a scenario specific value exists
    String scenarioSuffix = scenarioSpinner.getValue().toString();

    String stringValue =
        nodusProject.getLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + scenarioSuffix, "");
    descriptionTextField.setText(stringValue);

    // Is there an OD table already associated with this scenario
    String tableName = nodusProject.getLocalProperty(NodusC.PROP_OD_TABLE + scenarioSuffix);
    if (tableName != null) {
      // The list of OD tables could not already be loaded
      if (odTables != null) {
        odTablesCombo.setSelectedItem(tableName);
      } else {
        if (JDBCUtils.tableExists(tableName)) {
          // odTablesCombo.removeAll();
          odTablesCombo.addItem(tableName);
          odTablesCombo.setSelectedItem(tableName);
        }
      }
    }
    
    // Fill cost functions combo
    if (costsFilesNames == null) {
      File dir = new File(nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH));
      FileFilter fileFilter = new WildcardFileFilter("*.costs");
      File[] costsFilesNames = dir.listFiles(fileFilter);
      if (costsFilesNames == null) {
        return;
      }

      List<String> items = new LinkedList<>();
      for (File costsFilesName : costsFilesNames) {
        items.add(costsFilesName.getName());
      }
      Collections.sort(items);

      Iterator<String> it = items.iterator();
      while (it.hasNext()) {
        costFilesCombo.addItem(it.next());
      }
    }

    // Cost functions file name
    String defaultValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.TYPE_COSTS;
    if (costFilesCombo.getModel().getSize() == 0) {
      costFilesCombo.addItem(defaultValue);
    }

    stringValue = nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + scenarioSuffix);
    if (stringValue == null) {
      stringValue = nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS, defaultValue);
    }
    costFilesCombo.setSelectedItem(stringValue);

    saveAllVirtualLinksCheckBox.setSelected(
        nodusProject.getLocalProperty(NodusC.PROP_SAVE_ALL_VN, false));

    compactCheckBox.setSelected(nodusProject.getLocalProperty(NodusC.PROP_SHUTDOWN_COMPACT, true));

    cancelButton.setEnabled(true);
    okButton.setEnabled(true);
  }
}
