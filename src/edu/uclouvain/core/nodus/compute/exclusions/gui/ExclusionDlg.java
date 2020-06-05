/*
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

package edu.uclouvain.core.nodus.compute.exclusions.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.exclusions.ExclusionReader;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.swing.TableSorter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedList;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

/**
 * This dialog box handles the exclusions that can be maintained for a given node. Exclusions
 * represent operations are not allowed between two given pairs of mode and means, for a given
 * group. Using a 0 value means "any" group or mode or means.
 *
 * @author Bart Jourquin
 */
public class ExclusionDlg extends EscapeDialog {

  private static final String RULE_EXCLUSION = "exclusion";
  private static final String RULE_INCLUSION = "inclusion";

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 2034841416338333759L;

  private JButton addButton = new JButton();

  private JButton cancelButton = new JButton();

  private JTable exclusionTable;

  private JLabel groupLabel = new JLabel();

  private JSpinner groupSpinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  private JScrollPane scrollPane1 = new JScrollPane();

  private JPanel mainPanel = new JPanel();

  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  private JLabel means1Label = new JLabel();

  private JSpinner means1Spinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  private JLabel means2Label = new JLabel();

  private JSpinner means2Spinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  private JLabel mode1Label = new JLabel();

  private JSpinner mode1Spinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  private JLabel mode2Label = new JLabel();

  private JSpinner mode2Spinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  private final JLabel scenarioLabel = new JLabel();

  private final JSpinner scenarioSpinner = new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));

  private int nodeNum;

  private NodusProject nodusProject;

  private JButton removeButton = new JButton();

  private JButton saveButton = new JButton();

  private TableSorter sorter = new TableSorter(new DefaultTableModel());

  private JPanel spinnersPanel = new JPanel();

  private GridBagLayout spinnersPanelGridBagLayout = new GridBagLayout();

  private final JRadioButton includeRadioButton = new JRadioButton();

  private final JRadioButton excludeRadioButton = new JRadioButton();

  private final JSeparator separator = new JSeparator();

  private final ButtonGroup buttonGroup = new ButtonGroup();

  /**
   * The different add and remove operations will be stored in SQL statements pushed in a linked
   * list. The update of the table can then be performed by means of a batch provess of the
   * different statements included in the list.
   */
  private LinkedList<String> sqlBatch = new LinkedList<>();

  private String tableName;

  private String defaultExclusionRule;

  /**
   * Initializes the dialog box that will give the possibility to edit the exclusions related to
   * node num of the currently loaded NodusProject.
   *
   * @param dialog Parent dialog
   * @param layer NodusEsriLayer the node belongs to.
   * @param nodeNum The node num for which the exclusions list must be edited.
   */
  public ExclusionDlg(JDialog dialog, NodusEsriLayer layer, int nodeNum) {
    super(dialog, "", false);

    nodusProject = layer.getNodusMapPanel().getNodusProject();
    this.nodeNum = nodeNum;

    // Latest rule used becomes default
    defaultExclusionRule =
        nodusProject.getLocalProperty(NodusC.PROP_DEFAUT_EXCLUSION_RULE, RULE_EXCLUSION);

    // Set title
    setTitle(
        MessageFormat.format(
            i18n.get(ExclusionDlg.class, "Exclusions_for_node", "Exclusions for node {0}:"),
            nodeNum));

    initialize();
    getRootPane().setDefaultButton(saveButton);
    setLocationRelativeTo(dialog);
  }

  /** Add a new exclusion rule to the table. */
  private void addRule(
      String scenario, String group, String mode1, String means1, String mode2, String means2) {
    // Add INSERT statement to batch
    String sqlStmt =
        "INSERT INTO "
            + tableName
            + " VALUES( "
            + nodeNum
            + ", "
            + scenario
            + ", "
            + group
            + ", "
            + mode1
            + ", "
            + means1
            + ", "
            + mode2
            + ", "
            + means2
            + ")";
    sqlBatch.add(sqlStmt);

    // Add new row to table
    String[] row = new String[6];
    row[0] = scenario;
    row[1] = group;
    row[2] = mode1;
    row[3] = means1;
    row[4] = mode2;
    row[5] = means2;
    ((DefaultTableModel) sorter.getTableModel()).addRow(row);
  }

  /**
   * Adds a new rule to the list, based on the settings of the spinners.
   *
   * @param e ActionEvent
   */
  private void addButton_actionPerformed(ActionEvent e) {

    int result =
        JOptionPane.showConfirmDialog(
            this,
            "Add rule for both directions ?",
            NodusC.APPNAME,
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);
    boolean addReverseMovement = false;
    if (result == JOptionPane.CANCEL_OPTION) {
      return;
    } else if (result == JOptionPane.YES_OPTION) {
      addReverseMovement = true;
    }

    // Get values from spinners
    String scenario = scenarioSpinner.getValue().toString();
    String group = groupSpinner.getValue().toString();
    String mode1 = mode1Spinner.getValue().toString();
    String means1 = means1Spinner.getValue().toString();
    String mode2 = mode2Spinner.getValue().toString();
    String means2 = means2Spinner.getValue().toString();

    addRule(scenario, group, mode1, means1, mode2, means2);
    if (addReverseMovement) {
      addRule(scenario, group, mode2, means2, mode1, means1);
    }

    // Select added row and remove button
    int n = exclusionTable.getRowCount();
    exclusionTable.setRowSelectionInterval(n - 1, n - 1);
    removeButton.setEnabled(true);
  }

  /**
   * Just closed the dialog bax.
   *
   * @param e ActionEvent
   */
  private void cancelButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /** Creates the GUI and loads the existing exclusions stored in the project exclusion table. */
  private void initialize() {

    // Add the listener to avoid invalid mode-means combinations
    class SpinnersListener implements ChangeListener {
      @Override
      public void stateChanged(ChangeEvent evt) {
        int mode1 = Integer.parseInt(mode1Spinner.getValue().toString());
        int means1 = Integer.parseInt(means1Spinner.getValue().toString());
        int mode2 = Integer.parseInt(mode2Spinner.getValue().toString());
        int means2 = Integer.parseInt(means2Spinner.getValue().toString());

        boolean canAdd = true;

        // Loading : both mode and means must be = 0
        if (mode1 == 0 && means1 != 0) {
          canAdd = false;
        } else if (mode1 != 0 && means1 == 0) {
          canAdd = false;
        }

        if (mode2 == 0 && means2 != 0) {
          canAdd = false;
        } else if (mode2 != 0 && means2 == 0) {
          canAdd = false;
        } else if (mode1 == -1 && means1 != -1) {
          canAdd = false;
        } else if (mode2 == -1 && means2 != -1) {
          canAdd = false;
        } else if (mode1 == 0 && mode2 == 0) {
          canAdd = false;
        }

        addButton.setEnabled(canAdd);
      }
    }

    // Create an uneditable table
    exclusionTable =
        new JTable() {
          private static final long serialVersionUID = -7104802055801606119L;

          @Override
          public boolean isCellEditable(int rowIndex, int colIndex) {
            // not an editable table
            return false;
          }
        };

    mainPanel.setLayout(mainPanelGridBagLayout);
    setContentPane(mainPanel);

    spinnersPanel.setLayout(spinnersPanelGridBagLayout);
    spinnersPanel.setOpaque(false);

    cancelButton.setText(i18n.get(ExclusionDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelButton_actionPerformed(e);
          }
        });

    addButton.setText("+");
    addButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            addButton_actionPerformed(e);
          }
        });

    removeButton.setText("-");
    removeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            removeButton_actionPerformed(e);
          }
        });

    mode1Label.setText(i18n.get(ExclusionDlg.class, "Mode_1", "Mode 1"));
    means1Label.setText(i18n.get(ExclusionDlg.class, "Means_1", "Means 1"));
    mode2Label.setText(i18n.get(ExclusionDlg.class, "Mode_2", "Mode 2"));
    means2Label.setText(i18n.get(ExclusionDlg.class, "Means_2", "Means 2"));
    groupLabel.setText(i18n.get(ExclusionDlg.class, "Group", "Group"));

    groupSpinner.setMinimumSize(new Dimension(70, 24));
    mode1Spinner.setMinimumSize(new Dimension(70, 24));
    mode2Spinner.setMinimumSize(new Dimension(70, 24));
    means1Spinner.setMinimumSize(new Dimension(70, 24));
    means2Spinner.setMinimumSize(new Dimension(70, 24));
    scrollPane1.setViewportView(exclusionTable);

    scenarioLabel.setText(i18n.get(ExclusionDlg.class, "Scenario", "Scenario"));
    GridBagConstraints scenarioLabelGbc = new GridBagConstraints();
    scenarioLabelGbc.insets = new Insets(5, 5, 5, 5);
    scenarioLabelGbc.gridx = 0;
    scenarioLabelGbc.gridy = 0;
    spinnersPanel.add(scenarioLabel, scenarioLabelGbc);

    GridBagConstraints scenarioSpinnerGbc = new GridBagConstraints();
    scenarioSpinnerGbc.insets = new Insets(0, 5, 5, 5);
    scenarioSpinnerGbc.gridx = 0;
    scenarioSpinnerGbc.gridy = 1;
    spinnersPanel.add(scenarioSpinner, scenarioSpinnerGbc);
    spinnersPanel.add(
        groupLabel,
        new GridBagConstraints(
            0,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        mode1Label,
        new GridBagConstraints(
            0,
            4,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        mode1Spinner,
        new GridBagConstraints(
            0,
            5,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        means1Label,
        new GridBagConstraints(
            0,
            6,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        means1Spinner,
        new GridBagConstraints(
            0,
            7,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        mode2Label,
        new GridBagConstraints(
            0,
            8,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        mode2Spinner,
        new GridBagConstraints(
            0,
            9,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        means2Spinner,
        new GridBagConstraints(
            0,
            11,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));
    spinnersPanel.add(
        means2Label,
        new GridBagConstraints(
            0,
            10,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    spinnersPanel.add(
        groupSpinner,
        new GridBagConstraints(
            0,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 5),
            0,
            0));

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

    saveButton.setText(i18n.get(ExclusionDlg.class, "Save", "Save"));
    saveButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            saveButton_actionPerformed(e);
          }
        });

    GridBagConstraints separatorGbc = new GridBagConstraints();
    separatorGbc.insets = new Insets(5, 5, 5, 5);
    separatorGbc.gridx = 0;
    separatorGbc.gridy = 12;
    spinnersPanel.add(separator, separatorGbc);

    excludeRadioButton.setText(i18n.get(ExclusionDlg.class, "Exclusions", "Exclusions"));
    GridBagConstraints excludeRaduiButtonGbc = new GridBagConstraints();
    excludeRaduiButtonGbc.insets = new Insets(0, 0, 5, 0);
    excludeRaduiButtonGbc.anchor = GridBagConstraints.WEST;
    excludeRaduiButtonGbc.gridwidth = 1;
    excludeRaduiButtonGbc.gridx = 0;
    excludeRaduiButtonGbc.gridy = 13;
    spinnersPanel.add(excludeRadioButton, excludeRaduiButtonGbc);

    includeRadioButton.setText(i18n.get(ExclusionDlg.class, "Inclusion", "Inclusion"));
    GridBagConstraints includeRaduiButtonGbc = new GridBagConstraints();
    includeRaduiButtonGbc.anchor = GridBagConstraints.WEST;
    includeRaduiButtonGbc.gridwidth = 1;
    includeRaduiButtonGbc.gridx = 0;
    includeRaduiButtonGbc.gridy = 14;
    spinnersPanel.add(includeRadioButton, includeRaduiButtonGbc);

    buttonGroup.add(excludeRadioButton);
    buttonGroup.add(includeRadioButton);

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

      SpinnersListener sl = new SpinnersListener();
      mode1Spinner.addChangeListener(sl);
      means1Spinner.addChangeListener(sl);
      mode2Spinner.addChangeListener(sl);
      means2Spinner.addChangeListener(sl);
    }

    addButton.setEnabled(false);
    removeButton.setEnabled(false);

    String[] h = new String[6];
    h[0] = NodusC.DBF_SCENARIO;
    h[1] = NodusC.DBF_GROUP;
    h[2] = NodusC.DBF_MODE1;
    h[3] = NodusC.DBF_MEANS1;
    h[4] = NodusC.DBF_MODE2;
    h[5] = NodusC.DBF_MEANS2;
    ((DefaultTableModel) sorter.getTableModel()).setColumnIdentifiers(h);

    if (loadExclusionsInTable()) {
      // exclusionTable.setModel(model);
      exclusionTable.setModel(sorter);
      sorter.setTableHeader(exclusionTable.getTableHeader());
      // Select first row
      if (exclusionTable.getRowCount() > 0) {
        exclusionTable.setRowSelectionInterval(0, 0);
        removeButton.setEnabled(true);
      }
    }

    pack();
  }

  /**
   * Loads the exclusions for the current edited node in the list.
   *
   * @return True on success.
   */
  private boolean loadExclusionsInTable() {

    boolean isExcluded = true;
    int nbRecords = 0;

    // Create SQL statement
    String defValue =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_EXC;
    tableName = nodusProject.getLocalProperty(NodusC.PROP_EXC_TABLE, defValue);

    // Exclusions may not exist: create an empty table
    if (!JDBCUtils.tableExists(tableName)) {
      ExclusionReader.createExclusionsTable(tableName);
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
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + " FROM "
            + tableName
            + " WHERE ABS("
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + ") = "
            + nodeNum
            + " ORDER BY "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
            + ", "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP);

    // connect to database and execute query
    try {
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sql);

      // model.setColumnIdentifiers(h);
      while (rs.next()) {
        nbRecords++;
        String[] row = new String[6];

        for (int i = 0; i < 6; i++) {
          row[i] = rs.getObject(i + 1).toString();
        }

        // Negative node ID's are exclusions, positive are inclusions
        int id = rs.getInt(7);
        if (id > 0) {
          isExcluded = false;
        }

        ((DefaultTableModel) sorter.getTableModel()).addRow(row);
      }

      rs.close();
      stmt.close();

    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, e.toString(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);

      return false;
    }

    // Set the radio button to exclude or include
    if (nbRecords > 0) {
      if (isExcluded) {
        excludeRadioButton.setSelected(true);
      } else {
        includeRadioButton.setSelected(true);
      }
    } else {
      if (defaultExclusionRule.equals(RULE_EXCLUSION)) {
        excludeRadioButton.setSelected(true);
      } else {
        includeRadioButton.setSelected(true);
      }
    }

    return true;
  }

  /**
   * Removes an exclusion for the list.
   *
   * @param e ActionEvent
   */
  void removeButton_actionPerformed(ActionEvent e) {
    DefaultTableModel sorterModel = (DefaultTableModel) sorter.getTableModel();
    // Get values from selected row
    int sr = exclusionTable.getSelectedRow();
    int scenario = Integer.parseInt(sorterModel.getValueAt(sr, 0).toString());
    int group = Integer.parseInt(sorterModel.getValueAt(sr, 1).toString());
    int mode1 = Integer.parseInt(sorterModel.getValueAt(sr, 2).toString());
    int means1 = Integer.parseInt(sorterModel.getValueAt(sr, 3).toString());
    int mode2 = Integer.parseInt(sorterModel.getValueAt(sr, 4).toString());
    int means2 = Integer.parseInt(sorterModel.getValueAt(sr, 5).toString());

    // Add DELETE statement to batch
    String sqlStmt =
        "DELETE FROM "
            + tableName
            + " WHERE ABS("
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + ") = "
            + nodeNum
            + " AND "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_SCENARIO)
            + " = "
            + scenario
            + " AND "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_GROUP)
            + " = "
            + group
            + " AND "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE1)
            + " = "
            + mode1
            + " AND "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS1)
            + " = "
            + means1
            + " AND "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MODE2)
            + " = "
            + mode2
            + " AND "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_MEANS2)
            + " = "
            + means2;
    sqlBatch.add(sqlStmt);

    // Remove row from table
    sorterModel.removeRow(sr);

    if (exclusionTable.getRowCount() == 0) {
      removeButton.setEnabled(false);
    } else {
      exclusionTable.setRowSelectionInterval(0, 0);
    }
  }

  /**
   * Update/Saves the exclusions the the exclusions database table, then closes the dialog box.
   *
   * @param e ActionEvent
   */
  void saveButton_actionPerformed(ActionEvent e) {
    // Execute batch
    try {
      Connection con = nodusProject.getMainJDBCConnection();
      Statement stmt = con.createStatement();
      Iterator<String> it = sqlBatch.iterator();

      while (it.hasNext()) {
        String sqlStmt = it.next();
        stmt.executeUpdate(sqlStmt);
      }

      // Update the "all but" or "nothing but" status, using a negative node ID for "all but" rules
      int signedNodeNum = nodeNum;
      if (excludeRadioButton.isSelected()) {
        signedNodeNum = -nodeNum;
      }

      String sqlStmt =
          "UPDATE "
              + tableName
              + " SET "
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
              + " = "
              + signedNodeNum
              + " WHERE ABS("
              + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
              + ") = "
              + nodeNum;
      stmt.executeUpdate(sqlStmt);

      stmt.close();

    } catch (SQLException ex) {
      System.out.println(ex.toString());
    }

    // Save this rule as default for later use
    if (excludeRadioButton.isSelected()) {
      defaultExclusionRule = RULE_EXCLUSION;
    } else {
      defaultExclusionRule = RULE_INCLUSION;
    }
    nodusProject.setLocalProperty(NodusC.PROP_DEFAUT_EXCLUSION_RULE, defaultExclusionRule);

    // Close dialog
    setVisible(false);
  }
}
