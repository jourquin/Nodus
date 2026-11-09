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

package edu.uclouvain.core.nodus.compute.rules.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * This dialog box handles the rules that can be maintained for a given node. These rules represent
 * operations are allowed or not between two given pairs of mode and means, for a given scenario and
 * group. Using a -1 value means "any" scenario, group, mode or means. Rules can be defined as being
 * symmetric.
 *
 * @author Bart Jourquin
 */
public class NodeRulesDlg extends EscapeDialog {

  private static final long serialVersionUID = 2034841416338333759L;

  private static Color errorColor = Color.LIGHT_GRAY;
  private static Color excludeColor = new Color(220, 10, 10);
  private static Color includeColor = new Color(10, 220, 50);

  private static I18n i18n = Environment.getI18n();

  private static final byte idxNum = 7;

  private static final String RULE_EXCLUSION = "exclusion";
  private static final String RULE_INCLUSION = "inclusion";

  /** Rule controls, selection state and filtering for the complete node-rule list. */
  private final NodeRulesEditor editor = new NodeRulesEditor();

  /** . */
  private JButton addButton = new JButton();

  /** . */
  private JButton cancelButton = new JButton();

  /** . */
  private String defaultExclusionRule;

  /** . */
  private final JRadioButton excludeRadioButton = editor.exclusion;

  /** . */
  private DefaultTableModel exclusionsTableModel = editor.model;

  /** . */
  private final JLabel fromLabel = new JLabel();

  /** . */
  private final JRadioButton genercicRadioButton = editor.generic;

  /** . */
  private JLabel groupLabel = new JLabel();

  /** . */
  private JSpinner groupSpinner = editor.group;

  /** . */
  private final JRadioButton includeRadioButton = editor.inclusion;

  /** . */
  private List<List<Object>> initialRules = List.of();

  /** Whether all persisted rows have been read successfully. */
  private boolean rulesLoaded;

  /** . */
  private JPanel mainPanel = new JPanel();

  /** . */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** . */
  private JLabel means1Label = new JLabel();

  /** . */
  private JSpinner means1Spinner = editor.means1;

  /** . */
  private JLabel means2Label = new JLabel();

  /** . */
  private JSpinner means2Spinner = editor.means2;

  /** . */
  private JLabel mode1Label = new JLabel();

  /** . */
  private JSpinner mode1Spinner = editor.mode1;

  /** . */
  private JLabel mode2Label = new JLabel();

  /** . */
  private JSpinner mode2Spinner = editor.mode2;

  /** . */
  private int nodeNum;

  /** . */
  private NodusProject nodusProject;

  /** . */
  private JButton removeButton = new JButton();

  /** . */
  private JTable rulesTable = editor.table;

  /** . */
  private JButton saveButton = new JButton();

  /** . */
  private final JRadioButton scenarioRadioButton = editor.specific;

  /** . */
  private JScrollPane scrollPane1 = new JScrollPane();

  /** . */
  private JPanel spinnersPanel = new JPanel();

  /** . */
  private GridBagLayout spinnersPanelGridBagLayout = new GridBagLayout();

  /** . */
  private final JCheckBox symmetryCheckBox = editor.symmetry;

  /** . */
  private String tableName;

  /** . */
  private final JLabel toLabel = new JLabel();

  /**
   * Initializes the dialog box that will give the possibility to edit the exclusions related to
   * node num of the currently loaded NodusProject.
   *
   * @param dialog Parent dialog
   * @param layer NodusEsriLayer the node belongs to.
   * @param nodeNum The node num for which the exclusions list must be edited.
   */
  public NodeRulesDlg(JDialog dialog, NodusEsriLayer layer, int nodeNum) {
    super(dialog, "", false);

    nodusProject = layer.getNodusMapPanel().getNodusProject();
    this.nodeNum = nodeNum;
    int currentScenario = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0);
    editor.configure(nodeNum, currentScenario, this::setButtonsState);

    // Latest rule used becomes default
    defaultExclusionRule =
        nodusProject.getLocalProperty(NodusC.PROP_DEFAUT_EXCLUSION_RULE, RULE_EXCLUSION);

    // Set title
    setTitle(
        MessageFormat.format(
            i18n.get(
                NodeRulesDlg.class, "Operation_rules_for_node", "Operation rules for node {0}"),
            new DecimalFormat("###").format(nodeNum)));

    initialize();
    getRootPane().setDefaultButton(saveButton);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            requestCloseDialog();
          }
        });
    setLocationRelativeTo(nodusProject.getNodusMapPanel());
  }

  /**
   * Adds a new rule to the list. Must still be edited.
   *
   * @param e ActionEvent
   */
  private void addButton_actionPerformed(ActionEvent e) {
    editor.addRule(RULE_EXCLUSION.equals(defaultExclusionRule));
  }

  /**
   * Enables or not the spinners and radio button.
   *
   * @param enable Enable controls if true.
   */
  private void enableRuleControls(boolean enable) {

    editor.setControlsEnabled(enable);

    groupLabel.setEnabled(enable);
    mode1Label.setEnabled(enable);
    means1Label.setEnabled(enable);
    mode2Label.setEnabled(enable);
    means2Label.setEnabled(enable);
    fromLabel.setEnabled(enable);
    toLabel.setEnabled(enable);
  }

  /**
   * Copies the current rule cells for exact change detection.
   *
   * @return An exact copy of the current rule cells
   */
  private List<List<Object>> getRulesSnapshot() {
    return NodeRulesPersistence.snapshot(exclusionsTableModel);
  }

  /** Creates the GUI and loads the existing exclusions stored in the project exclusion table. */
  private void initialize() {

    // Display inclusions and exclusions using a specific color
    rulesTable.setDefaultRenderer(
        Object.class,
        new DefaultTableCellRenderer() {

          private static final long serialVersionUID = 1L;

          @Override
          public Component getTableCellRendererComponent(
              JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

            int modelRow = table.convertRowIndexToModel(row);
            if (!isValidRule(modelRow)) {
              setForeground(errorColor);
              return this;
            }

            String status = (String) table.getModel().getValueAt(modelRow, idxNum);
            if (status.startsWith("-")) {
              setForeground(excludeColor);
            } else {
              setForeground(includeColor);
            }
            return this;
          }
        });

    // Center values in cells
    DefaultTableCellRenderer tcr =
        (DefaultTableCellRenderer) rulesTable.getDefaultRenderer(String.class);
    tcr.setHorizontalAlignment(JLabel.CENTER);

    rulesTable.setDefaultRenderer(String.class, tcr);

    scrollPane1.setViewportView(rulesTable);

    mainPanel.setLayout(mainPanelGridBagLayout);
    setContentPane(mainPanel);

    spinnersPanel.setLayout(spinnersPanelGridBagLayout);
    spinnersPanel.setOpaque(false);

    JPanel scenarioPannel = new JPanel();
    scenarioPannel.setLayout(new GridBagLayout());
    TitledBorder border;
    border =
        BorderFactory.createTitledBorder(
            i18n.get(NodeRulesDlg.class, "Scope_of_rule", "Scope of rule"));
    border.setTitleJustification(TitledBorder.LEFT);
    scenarioPannel.setBorder(border);

    GridBagConstraints scenarioPannelGbd = new GridBagConstraints();
    scenarioPannelGbd.insets = new Insets(0, 0, 5, 0);
    scenarioPannelGbd.gridx = 0;
    scenarioPannelGbd.gridy = 0;
    scenarioPannelGbd.gridwidth = 3;
    scenarioPannelGbd.gridheight = 2;
    spinnersPanel.add(scenarioPannel, scenarioPannelGbd);

    genercicRadioButton.setText(i18n.get(NodeRulesDlg.class, "Generic", "Generic"));
    genercicRadioButton.setEnabled(false);
    GridBagConstraints genercicRadioButtonGbc = new GridBagConstraints();
    genercicRadioButtonGbc.gridwidth = 2;
    genercicRadioButtonGbc.anchor = GridBagConstraints.WEST;
    genercicRadioButtonGbc.insets = new Insets(5, 5, 0, 5);
    genercicRadioButtonGbc.gridx = 0;
    genercicRadioButtonGbc.gridy = 0;
    scenarioPannel.add(genercicRadioButton, genercicRadioButtonGbc);

    scenarioRadioButton.setText(
        i18n.get(NodeRulesDlg.class, "Scenario_specific", "Scenario specific"));
    scenarioRadioButton.setEnabled(false);
    GridBagConstraints scenarioRadioButtonGbc = new GridBagConstraints();
    scenarioRadioButtonGbc.gridwidth = 1;
    scenarioRadioButtonGbc.anchor = GridBagConstraints.WEST;
    scenarioRadioButtonGbc.insets = new Insets(0, 5, 5, 5);
    scenarioRadioButtonGbc.gridx = 0;
    scenarioRadioButtonGbc.gridy = 1;
    scenarioPannel.add(scenarioRadioButton, scenarioRadioButtonGbc);
    GridBagConstraints scenarioSpinnerGbc = new GridBagConstraints();
    scenarioSpinnerGbc.gridx = 1;
    scenarioSpinnerGbc.gridy = 1;
    scenarioSpinnerGbc.insets = new Insets(0, 0, 5, 5);
    editor.scenario.setPreferredSize(new Dimension(75, 24));
    scenarioPannel.add(editor.scenario, scenarioSpinnerGbc);

    excludeRadioButton.setText(i18n.get(NodeRulesDlg.class, "All_but", "All but..."));
    excludeRadioButton.setForeground(excludeColor);
    GridBagConstraints excludeRaduiButtonGbc = new GridBagConstraints();
    excludeRaduiButtonGbc.insets = new Insets(15, 5, 5, 0);
    excludeRaduiButtonGbc.anchor = GridBagConstraints.NORTHWEST;
    excludeRaduiButtonGbc.gridwidth = 3;
    excludeRaduiButtonGbc.gridx = 0;
    excludeRaduiButtonGbc.gridy = 2;
    spinnersPanel.add(excludeRadioButton, excludeRaduiButtonGbc);

    includeRadioButton.setText(i18n.get(NodeRulesDlg.class, "Nothing_but", "Nothing but..."));
    includeRadioButton.setForeground(includeColor);
    GridBagConstraints includeRaduiButtonGbc = new GridBagConstraints();
    includeRaduiButtonGbc.insets = new Insets(0, 5, 5, 0);
    includeRaduiButtonGbc.anchor = GridBagConstraints.NORTHWEST;
    includeRaduiButtonGbc.gridwidth = 3;
    includeRaduiButtonGbc.gridx = 0;
    includeRaduiButtonGbc.gridy = 3;
    spinnersPanel.add(includeRadioButton, includeRaduiButtonGbc);

    groupLabel.setText(i18n.get(NodeRulesDlg.class, "Group", "Group"));
    spinnersPanel.add(
        groupLabel,
        new GridBagConstraints(
            0,
            4,
            3,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 0, 5, 0),
            0,
            0));
    spinnersPanel.add(
        groupSpinner,
        new GridBagConstraints(
            0,
            5,
            3,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 0, 5, 0),
            0,
            0));

    GridBagConstraints fromLabelGbc = new GridBagConstraints();
    fromLabelGbc.insets = new Insets(5, 0, 5, 0);
    fromLabelGbc.gridwidth = 3;
    fromLabelGbc.gridx = 0;
    fromLabelGbc.gridy = 6;
    fromLabel.setText(i18n.get(NodeRulesDlg.class, "from", "from"));
    spinnersPanel.add(fromLabel, fromLabelGbc);

    mode1Label.setText(i18n.get(NodeRulesDlg.class, "Mode_1", "Mode 1"));
    spinnersPanel.add(
        mode1Label,
        new GridBagConstraints(
            0,
            7,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        mode1Spinner,
        new GridBagConstraints(
            0,
            8,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));

    means1Label.setText(i18n.get(NodeRulesDlg.class, "Means_1", "Means 1"));
    spinnersPanel.add(
        means1Label,
        new GridBagConstraints(
            2,
            7,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHEAST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0));
    spinnersPanel.add(
        means1Spinner,
        new GridBagConstraints(
            2,
            8,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHEAST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0));

    GridBagConstraints toLabelGbc = new GridBagConstraints();
    toLabelGbc.insets = new Insets(0, 0, 5, 0);
    toLabelGbc.gridwidth = 3;
    toLabelGbc.gridx = 0;
    toLabelGbc.gridy = 9;
    toLabel.setText(i18n.get(NodeRulesDlg.class, "to", "to"));
    spinnersPanel.add(toLabel, toLabelGbc);

    mode2Label.setText(i18n.get(NodeRulesDlg.class, "Mode_2", "Mode 2"));
    spinnersPanel.add(
        mode2Label,
        new GridBagConstraints(
            0,
            10,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));

    means2Label.setText(i18n.get(NodeRulesDlg.class, "Means_2", "Means 2"));
    spinnersPanel.add(
        means2Label,
        new GridBagConstraints(
            2,
            10,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHEAST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0));

    groupSpinner.setMinimumSize(new Dimension(70, 24));
    mode1Spinner.setMinimumSize(new Dimension(70, 24));
    means1Spinner.setMinimumSize(new Dimension(70, 24));

    mainPanel.add(
        spinnersPanel,
        new GridBagConstraints(
            0,
            0,
            1,
            11,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        mode2Spinner,
        new GridBagConstraints(
            0,
            11,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));

    mode2Spinner.setMinimumSize(new Dimension(70, 24));

    symmetryCheckBox.setText(
        i18n.get(NodeRulesDlg.class, "Symmetric_operations", "Symmetric operations"));
    GridBagConstraints biDirectionCheckBoxGbc = new GridBagConstraints();
    biDirectionCheckBoxGbc.gridwidth = 3;
    biDirectionCheckBoxGbc.insets = new Insets(5, 5, 5, 5);
    biDirectionCheckBoxGbc.gridx = 0;
    biDirectionCheckBoxGbc.gridy = 12;
    spinnersPanel.add(symmetryCheckBox, biDirectionCheckBoxGbc);
    means2Spinner.setMinimumSize(new Dimension(70, 24));
    spinnersPanel.add(
        means2Spinner,
        new GridBagConstraints(
            2,
            11,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHEAST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0));

    mainPanel.add(
        scrollPane1,
        new GridBagConstraints(
            1,
            0,
            6,
            11,
            0.2,
            0.1,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.BOTH,
            new Insets(5, 5, 5, 5),
            0,
            0));

    cancelButton.setText(i18n.get(NodeRulesDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            requestCloseDialog();
          }
        });

    addButton.setText(i18n.get(NodeRulesDlg.class, "Add", "Add"));
    addButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            addButton_actionPerformed(e);
          }
        });

    removeButton.setText(i18n.get(NodeRulesDlg.class, "Delete", "Delete"));
    removeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            removeButton_actionPerformed(e);
          }
        });

    saveButton.setText(i18n.get(NodeRulesDlg.class, "Save", "Save"));
    saveButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            saveButton_actionPerformed(e);
          }
        });

    editor.currentScenarioOnly.setText(
        i18n.get(NodeRulesDlg.class, "Current_scenario_rules_only", "Current scenario rules only"));
    mainPanel.add(
        editor.currentScenarioOnly,
        new GridBagConstraints(
            0,
            11,
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
        saveButton,
        new GridBagConstraints(
            5,
            11,
            1,
            1,
            0.1,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        cancelButton,
        new GridBagConstraints(
            6,
            11,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        removeButton,
        new GridBagConstraints(
            3,
            11,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        addButton,
        new GridBagConstraints(
            2,
            11,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    if (nodusProject == null) {
      // Just for VE
    } else {
      // Disable keyboard editing in spinners, but keep background white
      JFormattedTextField tf = ((JSpinner.DefaultEditor) groupSpinner.getEditor()).getTextField();
      tf.setEditable(false);
      tf.setBackground(Color.white);
      tf = ((JSpinner.DefaultEditor) mode1Spinner.getEditor()).getTextField();
      tf.setEditable(false);
      tf.setBackground(Color.white);
      tf = ((JSpinner.DefaultEditor) means1Spinner.getEditor()).getTextField();
      tf.setEditable(false);
      tf.setBackground(Color.white);
      tf = ((JSpinner.DefaultEditor) mode2Spinner.getEditor()).getTextField();
      tf.setEditable(false);
      tf.setBackground(Color.white);
      tf = ((JSpinner.DefaultEditor) means2Spinner.getEditor()).getTextField();
      tf.setEditable(false);
      tf.setBackground(Color.white);
    }

    removeButton.setEnabled(false);

    rulesTable.getColumnModel().getColumn(idxNum).setMinWidth(0);
    rulesTable.getColumnModel().getColumn(idxNum).setMaxWidth(0);
    enableRuleControls(false);
    if (loadExclusionsInTable()) {
      initialRules = getRulesSnapshot();
    }
    editor.refreshSelection();
    pack();
  }

  /**
   * Tests if the rule stored in a given row is valid.
   *
   * @param row The row index in the table.
   * @return True if valid rule.
   */
  private boolean isValidRule(int row) {
    return NodeRulesPersistence.isValid(exclusionsTableModel, row);
  }

  /**
   * Loads every stored rule for the edited node, across all scenarios and groups.
   *
   * @return True on success.
   */
  private boolean loadExclusionsInTable() {
    rulesLoaded = false;
    final String defValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
    tableName = nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, defValue);
    try {
      for (String[] columns : NodeRulesPersistence.load(nodusProject, tableName, nodeNum)) {
        exclusionsTableModel.addRow(columns);
      }
    } catch (Exception failure) {
      JOptionPane.showMessageDialog(
          this, failure.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }
    rulesLoaded = true;
    rulesTable.clearSelection();
    return true;
  }

  /**
   * Removes an exclusion for the table.
   *
   * @param e ActionEvent
   */
  void removeButton_actionPerformed(ActionEvent e) {
    editor.removeSelectedRule();
  }

  /**
   * Update/Saves the exclusions the the exclusions database table, then closes the dialog box.
   *
   * @param e ActionEvent
   */
  void saveButton_actionPerformed(ActionEvent e) {
    if (!rulesLoaded) {
      return;
    }
    final boolean hasDuplicates;
    try {
      hasDuplicates =
          NodeRulesPersistence.save(nodusProject, tableName, nodeNum, exclusionsTableModel);
    } catch (Exception failure) {
      JOptionPane.showMessageDialog(
          this, failure.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return;
    }
    initialRules = getRulesSnapshot();

    // Save this rule as default for later use
    if (excludeRadioButton.isSelected()) {
      defaultExclusionRule = RULE_EXCLUSION;
    } else {
      defaultExclusionRule = RULE_INCLUSION;
    }
    nodusProject.setLocalProperty(NodusC.PROP_DEFAUT_EXCLUSION_RULE, defaultExclusionRule);

    if (hasDuplicates) {
      JOptionPane.showMessageDialog(
          this,
          i18n.get(
              NodeRulesDlg.class, "Duplicate_rules_removed", "Duplicate rules will be removed"),
          NodusC.APPNAME,
          JOptionPane.INFORMATION_MESSAGE);
    }

    // Close dialog
    setVisible(false);
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
      requestCloseDialog();
      e.consume();
      return;
    }
    super.keyPressed(e);
  }

  /** Closes the dialog after resolving pending rule changes, if any. */
  private void requestCloseDialog() {
    if (!hasPendingChanges()) {
      setVisible(false);
      return;
    }

    Object[] options = {
      i18n.get(NodeRulesDlg.class, "Discard_changes", "Discard changes"),
      i18n.get(NodeRulesDlg.class, "Save_changes", "Save changes")
    };
    int answer =
        JOptionPane.showOptionDialog(
            this,
            i18n.get(NodeRulesDlg.class, "Discard_changes_question", "Discard changes?"),
            NodusC.APPNAME,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]);

    if (answer == JOptionPane.YES_OPTION) {
      setVisible(false);
    } else if (answer == JOptionPane.NO_OPTION && saveButton.isEnabled()) {
      saveButton_actionPerformed(null);
    }
  }

  /** Returns true when the rules differ from the initially loaded rules. */
  private boolean hasPendingChanges() {
    return rulesLoaded && !getRulesSnapshot().equals(initialRules);
  }

  /**
   * The "Save" button is enabled if the current rules are different from the initial ones and if
   * all the rules are valid. The "Add" button is enabled when all the existing rules are valid. The
   * "Remove" button is enabled if a rule is selected, even if it is wrong.
   */
  private void setButtonsState() {
    enableRuleControls(rulesLoaded && rulesTable.getSelectedRow() >= 0);
    if (!rulesLoaded) {
      addButton.setEnabled(false);
      removeButton.setEnabled(false);
      saveButton.setEnabled(false);
      return;
    }

    boolean allRulesAreValid = true;
    int nbRows = exclusionsTableModel.getRowCount();

    if (rulesTable.getSelectedRow() == -1) {
      removeButton.setEnabled(false);
    } else {
      removeButton.setEnabled(true);
    }

    for (int row = 0; row < nbRows; row++) {
      if (!isValidRule(row)) {
        allRulesAreValid = false;
        break;
      }
    }

    if (!allRulesAreValid) {
      addButton.setEnabled(false);
      saveButton.setEnabled(false);
    } else {
      addButton.setEnabled(true);

      if (!hasPendingChanges()) {
        saveButton.setEnabled(false);
      } else {
        saveButton.setEnabled(true);
      }
    }
  }
}
