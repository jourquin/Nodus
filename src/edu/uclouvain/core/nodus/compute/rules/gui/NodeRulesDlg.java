/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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
import edu.uclouvain.core.nodus.compute.rules.NodeRulesReader;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.HashMap;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
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
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

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

  private static String doubleArrow = "\u2194"; // bidirectional arrow
  private static String singleArrow = "\u2192"; // Simple arrow

  private static Color errorColor = Color.LIGHT_GRAY;
  private static Color excludeColor = new Color(220, 10, 10);
  private static Color includeColor = new Color(10, 220, 50);

  private static I18n i18n = Environment.getI18n();

  private static final byte idxGroup = 1;
  private static final byte idxMeans1 = 3;
  private static final byte idxMeans2 = 5;
  private static final byte idxMode1 = 2;
  private static final byte idxMode2 = 4;
  private static final byte idxNum = 7;
  private static final byte idxScenario = 0;
  private static final byte idxSymmetry = 6;

  private static final String RULE_EXCLUSION = "exclusion";
  private static final String RULE_INCLUSION = "inclusion";

  /** . */
  private JButton addButton = new JButton();

  /** . */
  private JButton cancelButton = new JButton();

  /** . */
  private int currentRow = -1;

  /** . */
  private int currentScenario;

  /** . */
  private String defaultExclusionRule;

  /** . */
  private final ButtonGroup excludeButtonGroup = new ButtonGroup();

  /** . */
  private final JRadioButton excludeRadioButton = new JRadioButton();

  /** . */
  private DefaultTableModel exclusionsTableModel;

  /** . */
  private final JLabel fromLabel = new JLabel();

  /** . */
  private final JRadioButton genercicRadioButton = new JRadioButton();

  /** . */
  private JLabel groupLabel = new JLabel();

  /** . */
  private JSpinner groupSpinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  /** . */
  private final JRadioButton includeRadioButton = new JRadioButton();

  /** . */
  private int initialRulesHashCode;

  /** . */
  private JPanel mainPanel = new JPanel();

  /** . */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** . */
  private JLabel means1Label = new JLabel();

  /** . */
  private JSpinner means1Spinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  /** . */
  private JLabel means2Label = new JLabel();

  /** . */
  private JSpinner means2Spinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  /** . */
  private JLabel mode1Label = new JLabel();

  /** . */
  private JSpinner mode1Spinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  /** . */
  private JLabel mode2Label = new JLabel();

  /** . */
  private JSpinner mode2Spinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  /** . */
  private int nodeNum;

  /** . */
  private NodusProject nodusProject;

  /** . */
  private JButton removeButton = new JButton();

  /** . */
  private JTable rulesTable;

  /** . */
  private JButton saveButton = new JButton();

  /** . */
  private final ButtonGroup scenarioButtonGroup = new ButtonGroup();

  /** . */
  private final JRadioButton scenarioRadioButton = new JRadioButton();

  /** . */
  private JScrollPane scrollPane1 = new JScrollPane();

  /** . */
  private JPanel spinnersPanel = new JPanel();

  /** . */
  private GridBagLayout spinnersPanelGridBagLayout = new GridBagLayout();

  /** . */
  private final JCheckBox symmetryCheckBox = new JCheckBox();

  /** . */
  private String tableName;

  /** . */
  private final JLabel toLabel = new JLabel();

  /** . */
  private HashMap<Integer, Integer> existingRules = new HashMap<>();

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
    currentScenario = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0);

    // Latest rule used becomes default
    defaultExclusionRule =
        nodusProject.getLocalProperty(NodusC.PROP_DEFAUT_EXCLUSION_RULE, RULE_EXCLUSION);

    // Set title
    setTitle(
        MessageFormat.format(
            i18n.get(
                NodeRulesDlg.class, "Operation_rules_for_node", "Operation rules for node {0}:"),
            new DecimalFormat("###").format(nodeNum)));

    initialize();
    getRootPane().setDefaultButton(saveButton);
    setLocationRelativeTo(nodusProject.getNodusMapPanel());
  }

  /**
   * Adds a new rule to the list. Must still be edited.
   *
   * @param e ActionEvent
   */
  private void addButton_actionPerformed(ActionEvent e) {

    scenarioRadioButton.setEnabled(true);
    genercicRadioButton.setEnabled(true);
    enableRuleControls(true);

    // Set default rule type
    String nodeId = "" + nodeNum;
    if (defaultExclusionRule == RULE_EXCLUSION) {
      nodeId = "-" + nodeId;
    }

    // Add new row to table
    String[] row = new String[8];
    row[idxScenario] = "-1";
    row[idxGroup] = "-1";
    row[idxMode1] = "-1";
    row[idxMeans1] = "-1";
    row[idxMode2] = "-1";
    row[idxMeans2] = "-1";
    row[idxSymmetry] = singleArrow;
    row[idxNum] = nodeId;

    ((DefaultTableModel) rulesTable.getModel()).addRow(row);

    int lastRowIdx = rulesTable.getRowCount() - 1;

    rulesTable.setRowSelectionInterval(lastRowIdx, lastRowIdx);

    exclusionsTableModel.fireTableRowsUpdated(0, rulesTable.getRowCount() - 1);
  }

  /**
   * Enables or not the spinners and radio button.
   *
   * @param enable Enable controls if true.
   */
  private void enableRuleControls(boolean enable) {

    excludeRadioButton.setEnabled(enable);
    includeRadioButton.setEnabled(enable);
    groupSpinner.setEnabled(enable);
    mode1Spinner.setEnabled(enable);
    means1Spinner.setEnabled(enable);
    mode2Spinner.setEnabled(enable);
    means2Spinner.setEnabled(enable);

    groupLabel.setEnabled(enable);
    mode1Label.setEnabled(enable);
    means1Label.setEnabled(enable);
    mode2Label.setEnabled(enable);
    means2Label.setEnabled(enable);
    fromLabel.setEnabled(enable);
    toLabel.setEnabled(enable);

    symmetryCheckBox.setEnabled(enable);
  }

  /**
   * Computes a hash for the set of rules.
   *
   * @return The hash code
   */
  private int getRulesHashCode() {

    // Compute the hash of the current rules
    String rules = "";
    int nbRows = rulesTable.getRowCount();
    int nbColumns = rulesTable.getColumnCount();
    for (int row = 0; row < nbRows; row++) {
      for (int col = 0; col < nbColumns; col++) {
        rules += exclusionsTableModel.getValueAt(row, col).toString();
      }
    }

    return rules.hashCode();
  }

  /**
   * Returns the foreground of the cells of a given row index.
   *
   * @param row The row index to test.
   * @return The foreground color or null for non valid row indexes.
   */
  private Color getTableRowForeground(int row) {
    if (row == -1 || row >= rulesTable.getRowCount()) {
      return null;
    }
    TableCellRenderer renderer = rulesTable.getCellRenderer(row, 0);
    Component component = rulesTable.prepareRenderer(renderer, row, 0);
    return component.getForeground();
  }

  /** Creates the GUI and loads the existing exclusions stored in the project exclusion table. */
  private void initialize() {

    // Create a non editable table
    rulesTable =
        new JTable() {
          private static final long serialVersionUID = -7104802055801606119L;

          @Override
          public boolean isCellEditable(int rowIndex, int colIndex) {
            // not an editable table
            return false;
          }
        };
    rulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    exclusionsTableModel = (DefaultTableModel) rulesTable.getModel();

    // Display inclusions and exclusions using a specific color
    rulesTable.setDefaultRenderer(
        Object.class,
        new DefaultTableCellRenderer() {

          private static final long serialVersionUID = 1L;

          @Override
          public Component getTableCellRendererComponent(
              JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

            if (!isValidRule(row)) {
              setForeground(errorColor);
              ((AbstractTableModel) exclusionsTableModel).fireTableRowsUpdated(row, row);
              return this;
            }

            String status = (String) table.getModel().getValueAt(row, idxNum);
            if (status.startsWith("-")) {
              setForeground(excludeColor);
            } else {
              setForeground(includeColor);
            }

            ((AbstractTableModel) exclusionsTableModel).fireTableRowsUpdated(row, row);
            return this;
          }
        });

    // Center values in cells
    DefaultTableCellRenderer tcr =
        (DefaultTableCellRenderer) rulesTable.getDefaultRenderer(String.class);
    tcr.setHorizontalAlignment(JLabel.CENTER);

    rulesTable.setDefaultRenderer(String.class, tcr);

    // Synchronize the selected row with the spinners
    rulesTable
        .getSelectionModel()
        .addListSelectionListener(
            new ListSelectionListener() {
              public void valueChanged(ListSelectionEvent event) {
                // Don't allow to change row if current rule is wrong
                if (currentRow != -1 && errorColor.equals(getTableRowForeground(currentRow))) {
                  rulesTable.setRowSelectionInterval(currentRow, currentRow);
                  // saveButton.setEnabled(false);
                  return;
                }

                int row = rulesTable.getSelectedRow();
                currentRow = row;
                if (row == -1) {
                  removeButton.setEnabled(false);
                  return;
                }

                // Generic or scenario specific ?
                String s = (String) exclusionsTableModel.getValueAt(row, idxScenario);

                if (Integer.parseInt(s) == -1) {
                  genercicRadioButton.setSelected(true);
                } else {
                  scenarioRadioButton.setSelected(true);
                }

                // Inclusion or exclusion
                s = (String) exclusionsTableModel.getValueAt(row, idxNum);
                if (s.startsWith("-")) {
                  excludeRadioButton.setSelected(true);
                } else {
                  includeRadioButton.setSelected(true);
                }

                s = (String) exclusionsTableModel.getValueAt(row, idxGroup);
                groupSpinner.setValue(Integer.parseInt(s));

                s = (String) exclusionsTableModel.getValueAt(row, idxMode1);
                mode1Spinner.setValue(Integer.parseInt(s));

                s = (String) exclusionsTableModel.getValueAt(row, idxMeans1);
                means1Spinner.setValue(Integer.parseInt(s));

                s = (String) exclusionsTableModel.getValueAt(row, idxMode2);
                mode2Spinner.setValue(Integer.parseInt(s));

                s = (String) exclusionsTableModel.getValueAt(row, idxMeans2);
                means2Spinner.setValue(Integer.parseInt(s));

                s = (String) exclusionsTableModel.getValueAt(row, idxSymmetry);
                if (s.equals(singleArrow)) {
                  symmetryCheckBox.setSelected(false);
                } else {
                  symmetryCheckBox.setSelected(true);
                }

                enableRuleControls(true);
              }
            });

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
    genercicRadioButtonGbc.gridwidth = 1;
    genercicRadioButtonGbc.anchor = GridBagConstraints.WEST;
    genercicRadioButtonGbc.insets = new Insets(5, 5, 0, 5);
    genercicRadioButtonGbc.gridx = 0;
    genercicRadioButtonGbc.gridy = 0;
    scenarioPannel.add(genercicRadioButton, genercicRadioButtonGbc);

    genercicRadioButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JRadioButton radioButton = (JRadioButton) e.getSource();

            int selectedRow = rulesTable.getSelectedRow();
            if (selectedRow != -1) {

              if (radioButton.isSelected()) {
                // Set rule scope
                exclusionsTableModel.setValueAt("-1", selectedRow, idxScenario);

                // It at least one rule exists for this scenario, use the same type of
                // inclusion/exclusion
                int nbRows = rulesTable.getRowCount();
                String nodeId = "" + nodeNum;
                for (int row = 0; row < nbRows; row++) {
                  String s = (String) exclusionsTableModel.getValueAt(row, idxNum);
                  if (s.startsWith("-")) {
                    nodeId = "-" + nodeId;
                    break;
                  }
                }
                exclusionsTableModel.setValueAt(nodeId, selectedRow, idxNum);

                setButtonsState();
              }
            }
          }
        });

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

    scenarioRadioButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JRadioButton radioButton = (JRadioButton) e.getSource();

            int selectedRow = rulesTable.getSelectedRow();
            if (selectedRow != -1) {

              if (radioButton.isSelected()) {
                // Set rule scope
                exclusionsTableModel.setValueAt("" + currentScenario, selectedRow, idxScenario);

                // It at least one rule exists for this scenario, use the same type of
                // inclusion/exclusion
                int nbRows = rulesTable.getRowCount();
                String nodeId = "" + nodeNum;
                for (int row = 0; row < nbRows; row++) {
                  String s = (String) exclusionsTableModel.getValueAt(row, idxNum);
                  if (s.startsWith("-")) {
                    nodeId = "-" + nodeId;
                    break;
                  }
                }
                exclusionsTableModel.setValueAt(nodeId, selectedRow, idxNum);

                setButtonsState();
              }
            }
          }
        });

    scenarioButtonGroup.add(genercicRadioButton);
    scenarioButtonGroup.add(scenarioRadioButton);

    excludeRadioButton.setText(i18n.get(NodeRulesDlg.class, "All_but", "All but..."));
    excludeRadioButton.setForeground(excludeColor);
    GridBagConstraints excludeRaduiButtonGbc = new GridBagConstraints();
    excludeRaduiButtonGbc.insets = new Insets(15, 5, 5, 0);
    excludeRaduiButtonGbc.anchor = GridBagConstraints.NORTHWEST;
    excludeRaduiButtonGbc.gridwidth = 3;
    excludeRaduiButtonGbc.gridx = 0;
    excludeRaduiButtonGbc.gridy = 2;
    spinnersPanel.add(excludeRadioButton, excludeRaduiButtonGbc);

    excludeRadioButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JRadioButton radioButton = (JRadioButton) e.getSource();

            int selectedRow = rulesTable.getSelectedRow();
            if (selectedRow != -1) {

              if (radioButton.isSelected()) {
                // Update all rows for this scenario
                String nodeId = (String) exclusionsTableModel.getValueAt(selectedRow, idxNum);
                int num = Integer.parseInt(nodeId) * -1;
                nodeId = String.valueOf(num);
                String refScenario =
                    (String) exclusionsTableModel.getValueAt(selectedRow, idxScenario);
                for (int i = 0; i < exclusionsTableModel.getRowCount(); i++) {
                  String rowScenario = (String) exclusionsTableModel.getValueAt(i, idxScenario);

                  if (rowScenario.equals(refScenario)) {
                    exclusionsTableModel.setValueAt(
                        nodeId, i, exclusionsTableModel.getColumnCount() - 1);
                    setButtonsState();
                  }
                }
              }
            }
          }
        });

    includeRadioButton.setText(i18n.get(NodeRulesDlg.class, "Nothing_but", "Nothing but..."));
    includeRadioButton.setForeground(includeColor);
    GridBagConstraints includeRaduiButtonGbc = new GridBagConstraints();
    includeRaduiButtonGbc.insets = new Insets(0, 5, 5, 0);
    includeRaduiButtonGbc.anchor = GridBagConstraints.NORTHWEST;
    includeRaduiButtonGbc.gridwidth = 3;
    includeRaduiButtonGbc.gridx = 0;
    includeRaduiButtonGbc.gridy = 3;
    spinnersPanel.add(includeRadioButton, includeRaduiButtonGbc);

    includeRadioButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JRadioButton radioButton = (JRadioButton) e.getSource();

            int selectedRow = rulesTable.getSelectedRow();
            if (selectedRow != -1) {

              if (radioButton.isSelected()) {
                // Update all rows for this scenario
                String nodeId = (String) exclusionsTableModel.getValueAt(selectedRow, idxNum);
                int num = Integer.parseInt(nodeId) * -1;
                nodeId = String.valueOf(num);
                String refScenario =
                    (String) exclusionsTableModel.getValueAt(selectedRow, idxScenario);
                for (int i = 0; i < exclusionsTableModel.getRowCount(); i++) {
                  String rowScenario = (String) exclusionsTableModel.getValueAt(i, idxScenario);

                  if (rowScenario.equals(refScenario)) {
                    exclusionsTableModel.setValueAt(
                        nodeId, i, exclusionsTableModel.getColumnCount() - 1);
                    setButtonsState();
                  }
                }
              }
            }
          }
        });

    excludeButtonGroup.add(excludeRadioButton);
    excludeButtonGroup.add(includeRadioButton);

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

    symmetryCheckBox.addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            JCheckBox checkBox = (JCheckBox) e.getSource();
            boolean isBidirectional = checkBox.isSelected();
            String value = singleArrow;
            if (isBidirectional) {
              value = doubleArrow;
            }
            int row = rulesTable.getSelectedRow();
            if (row != -1) {
              rulesTable.setValueAt(value, row, idxSymmetry);
              setButtonsState();
            }
          }
        });

    cancelButton.setText(i18n.get(NodeRulesDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            setVisible(false);
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
            new Insets(5, 5, 0, 5),
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
            new Insets(5, 5, 0, 5),
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

      groupSpinner.addChangeListener(
          new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              JSpinner spinner = (JSpinner) e.getSource();
              String value = spinner.getValue().toString();
              int row = rulesTable.getSelectedRow();
              if (row != -1) {
                rulesTable.setValueAt(value, row, idxGroup);
                setButtonsState();
              }
            }
          });

      mode1Spinner.addChangeListener(
          new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              JSpinner spinner = (JSpinner) e.getSource();
              String value = spinner.getValue().toString();
              int row = rulesTable.getSelectedRow();
              if (row != -1) {
                rulesTable.setValueAt(value, row, idxMode1);
                setButtonsState();
              }
            }
          });

      means1Spinner.addChangeListener(
          new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              JSpinner spinner = (JSpinner) e.getSource();
              String value = spinner.getValue().toString();
              int row = rulesTable.getSelectedRow();
              if (row != -1) {
                rulesTable.setValueAt(value, row, idxMeans1);
                setButtonsState();
              }
            }
          });

      mode2Spinner.addChangeListener(
          new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              JSpinner spinner = (JSpinner) e.getSource();
              String value = spinner.getValue().toString();
              int row = rulesTable.getSelectedRow();
              if (row != -1) {
                rulesTable.setValueAt(value, row, idxMode2);
                setButtonsState();
              }
            }
          });

      means2Spinner.addChangeListener(
          new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              JSpinner spinner = (JSpinner) e.getSource();
              String value = spinner.getValue().toString();
              int row = rulesTable.getSelectedRow();
              if (row != -1) {
                rulesTable.setValueAt(value, row, idxMeans2);
                setButtonsState();
              }
            }
          });
    }

    removeButton.setEnabled(false);

    String[] header = new String[8];
    header[idxScenario] = NodusC.DBF_SCENARIO;
    header[idxGroup] = NodusC.DBF_GROUP;
    header[idxMode1] = NodusC.DBF_MODE1;
    header[idxMeans1] = NodusC.DBF_MEANS1;
    header[idxMode2] = NodusC.DBF_MODE2;
    header[idxMeans2] = NodusC.DBF_MEANS2;
    header[idxSymmetry] = NodusC.DBF_SYMMETRY;
    header[idxNum] = NodusC.DBF_NUM;

    exclusionsTableModel.setColumnIdentifiers(header);

    if (loadExclusionsInTable()) {
      rulesTable.getColumnModel().getColumn(idxNum).setMinWidth(0);
      rulesTable.getColumnModel().getColumn(idxNum).setMaxWidth(0);

      currentRow = -1;
      initialRulesHashCode = getRulesHashCode();

      // Reset all controls
      enableRuleControls(false);
      setButtonsState();
      scenarioButtonGroup.clearSelection();
      excludeButtonGroup.clearSelection();
      groupSpinner.setValue(-1);
      mode1Spinner.setValue(-1);
      means1Spinner.setValue(-1);
      mode2Spinner.setValue(-1);
      means2Spinner.setValue(-1);
    }
    pack();
  }

  /**
   * Test if the rule stored in the given row has already been stored.
   *
   * @param row The row index in the exclusion table
   * @return True if the rule already exists.
   */
  private boolean isDuplicate(int row) {

    boolean symmetric = false;
    if (rulesTable.getValueAt(row, idxSymmetry).equals(doubleArrow)) {
      symmetric = true;
    }

    String nodeId = (String) rulesTable.getModel().getValueAt(row, idxNum);
    String scenario = (String) rulesTable.getModel().getValueAt(row, idxScenario);
    String group = (String) rulesTable.getModel().getValueAt(row, idxGroup);
    String mode1 = (String) rulesTable.getModel().getValueAt(row, idxMode1);
    String means1 = (String) rulesTable.getModel().getValueAt(row, idxMeans1);
    String mode2 = (String) rulesTable.getModel().getValueAt(row, idxMode2);
    String means2 = (String) rulesTable.getModel().getValueAt(row, idxMeans2);

    String rule = nodeId + scenario + group + mode1 + means1 + mode2 + means2;
    int hashKey = rule.hashCode();

    // Test if rule exists
    if (existingRules.get(hashKey) != null) {
      return true;
    }

    // Store new rule
    existingRules.put(hashKey, hashKey);

    // Also store reverse rule if needed
    if (symmetric) {
      rule = nodeId + scenario + group + mode2 + means2 + mode1 + means1;
      hashKey = rule.hashCode();
      existingRules.put(hashKey, hashKey);
    }

    return false;
  }

  /**
   * Tests if the rule stored in a given row is valid.
   *
   * @param row The row index in the table.
   * @return True if valid rule.
   */
  private boolean isValidRule(int row) {

    if (row == -1) {
      return true;
    }

    int mode1 = Integer.parseInt((String) rulesTable.getModel().getValueAt(row, idxMode1));
    int means1 = Integer.parseInt((String) rulesTable.getModel().getValueAt(row, idxMeans1));
    int mode2 = Integer.parseInt((String) rulesTable.getModel().getValueAt(row, idxMode2));
    int means2 = Integer.parseInt((String) rulesTable.getModel().getValueAt(row, idxMeans2));

    // Loading : both mode and means must be = 0
    if (mode1 == 0 && means1 != 0) {
      return false;
    }

    if (mode1 != 0 && means1 == 0) {
      return false;
    }

    // Same for unloading
    if (mode2 == 0 && means2 != 0) {
      return false;
    }
    if (mode2 != 0 && means2 == 0) {
      return false;
    }

    if (mode1 == 0 && mode2 == 0) {
      return false;
    }

    if (mode1 == -1 && means1 != -1) {
      return false;
    }

    if (mode2 == -1 && means2 != -1) {
      return false;
    }

    // Transit
    if (mode1 == mode2 && means1 == means2) {
      return false;
    }

    return true;
  }

  /**
   * Loads the generic and scenario specific exclusion rules for the current edited node in the
   * list. Ignore invalid rules.
   *
   * @return True on success.
   */
  private boolean loadExclusionsInTable() {

    // Create SQL statement
    String defValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
    tableName = nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, defValue);

    // Exclusions may not exist: create an empty table
    if (!JDBCUtils.tableExists(tableName)) {
      NodeRulesReader.createExclusionsTable(tableName);
    }

    String sql =
        "SELECT "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS2)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SYMMETRY)
            + ","
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + " FROM "
            + tableName
            + " WHERE ABS("
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + ") = "
            + nodeNum
            + " AND ("
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
            + " = -1 OR "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
            + " = "
            + currentScenario
            + ")"
            + " ORDER BY "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS2);

    // Connect to database and execute query
    try {
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sql);

      // model.setColumnIdentifiers(h);
      while (rs.next()) {
        // nbRecords++;
        String[] columns = new String[8];

        for (int i = 0; i < columns.length; i++) {
          String value = rs.getObject(i + 1).toString();
          if (i == idxSymmetry) {
            if (value.equals("0")) {
              value = singleArrow;
            } else {
              value = doubleArrow;
            }
          }
          columns[i] = value;
        }

        // Remove this rule if not valid
        exclusionsTableModel.addRow(columns);
        int n = rulesTable.getRowCount() - 1;
        rulesTable.setRowSelectionInterval(n, n);
        if (!isValidRule(n)) {
          exclusionsTableModel.removeRow(n);
        }
      }

      rs.close();
      stmt.close();

    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);

      return false;
    }

    rulesTable.clearSelection();
    return true;
  }

  /**
   * Removes an exclusion for the table.
   *
   * @param e ActionEvent
   */
  void removeButton_actionPerformed(ActionEvent e) {

    // Get values from selected row
    int sr = rulesTable.getSelectedRow();
    if (sr == -1) {
      return;
    }

    // Remove row from table
    exclusionsTableModel.removeRow(sr);

    // Select a row close to the deleted one
    int n = rulesTable.getRowCount();
    if (n > sr) {
      rulesTable.setRowSelectionInterval(sr, sr);
      currentRow = rulesTable.getSelectedRow();
    } else if (n > 0) {
      rulesTable.setRowSelectionInterval(n - 1, n - 1);
      currentRow = rulesTable.getSelectedRow();
    } else {
      currentRow = -1;
      rulesTable.clearSelection();
    }
    
    setButtonsState();
  }

  /**
   * Update/Saves the exclusions the the exclusions database table, then closes the dialog box.
   *
   * @param e ActionEvent
   */
  void saveButton_actionPerformed(ActionEvent e) {

    boolean hasDuplicates = false;

    // Execute batch
    try {
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();

      // Delete rules in the table
      String sqlStmt =
          "DELETE FROM "
              + tableName
              + " WHERE ABS("
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
              + ") = "
              + nodeNum
              + " AND ("
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
              + " = -1 OR "
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
              + " = "
              + currentScenario
              + ")";
      stmt.executeUpdate(sqlStmt);

      // Save new rules, but avoid duplicates. herefore, a 2 pass procedure is
      // used, starting with the symectric rules
      existingRules.clear();
      for (int pass = 0; pass < 2; pass++) {
        for (int row = 0; row < rulesTable.getRowCount(); row++) {
          String symmetry = "0";
          if (rulesTable.getValueAt(row, idxSymmetry).equals(doubleArrow)) {
            symmetry = "1";
          }

          if (pass == 0 && symmetry.equals("0")) {
            continue;
          }

          if (pass == 1 && symmetry.equals("1")) {
            continue;
          }

          if (!isDuplicate(row)) {
            sqlStmt =
                "INSERT INTO "
                    + tableName
                    + " VALUES( "
                    + rulesTable.getValueAt(row, idxNum)
                    + ", "
                    + rulesTable.getValueAt(row, idxScenario)
                    + ", "
                    + rulesTable.getValueAt(row, idxGroup)
                    + ", "
                    + rulesTable.getValueAt(row, idxMode1)
                    + ", "
                    + rulesTable.getValueAt(row, idxMeans1)
                    + ", "
                    + rulesTable.getValueAt(row, idxMode2)
                    + ", "
                    + rulesTable.getValueAt(row, idxMeans2)
                    + ", "
                    + symmetry
                    + ")";
            stmt.executeUpdate(sqlStmt);
          } else {
            hasDuplicates = true;
          }
        }
      }

      stmt.close();

    } catch (SQLException ex) {
      ex.printStackTrace();
    }

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

  /**
   * The "Save" button is enabled if the current rules are different from the initial ones and if
   * all the rules are valid. The "Add" button is enabled when all the existing rules are valid. The
   * "Remove" button is enabled if a rule is selected, eben if it is wrong.
   */
  private void setButtonsState() {

    boolean allRulesAreValid = true;
    int nbRows = rulesTable.getRowCount();

    if (rulesTable.getSelectedRow() == -1) {
      removeButton.setEnabled(false);
    } else {
      removeButton.setEnabled(true);
    }

    for (int row = 0; row < nbRows; row++) {
      if (getTableRowForeground(row).equals(errorColor)) {
        allRulesAreValid = false;
        break;
      }
    }

    if (!allRulesAreValid) {
      addButton.setEnabled(false);
      saveButton.setEnabled(false);
    } else {
      addButton.setEnabled(true);

      if (getRulesHashCode() == initialRulesHashCode) {
        saveButton.setEnabled(false);
      } else {
        saveButton.setEnabled(true);
      }
    }
  }
}
