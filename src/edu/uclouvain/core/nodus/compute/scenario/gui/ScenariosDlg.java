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

package edu.uclouvain.core.nodus.compute.scenario.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.scenario.Scenarios;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.LinkedList;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Dialog box permitting different actions to be performed on scenarios (delete, rename, compare or
 * sum).
 *
 * @author Bart Jourquin
 */
public class ScenariosDlg extends EscapeDialog {

  private static final long serialVersionUID = -8717944856601913002L;

  private static I18n i18n = Environment.getI18n();

  /** . */
  private ButtonGroup actionButtonGroup = new ButtonGroup();

  /** . */
  private JPanel actionPanel = new JPanel();

  /** . */
  private GridBagLayout actionPanelGridBagLayout = new GridBagLayout();

  /** . */
  private JButton closeButton = new JButton();

  /** . */
  private JRadioButton compareRadioButton = new JRadioButton();

  /** . */
  private JButton executeButton = new JButton();

  /** . */
  private boolean isBusy = false;

  /** . */
  private JPanel mainPanel = new JPanel();

  /** . */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** . */
  private NodusProject nodusProject;

  /** . */
  private JRadioButton removeRadioButton = new JRadioButton();

  /** . */
  private JRadioButton renameRadioButton = new JRadioButton();

  /** . */
  private JLabel resultScenarioLabel = new JLabel();

  /** . */
  private JSpinner resultSpinner = new JSpinner();

  /** . */
  private JLabel scenario1Label = new JLabel();

  /** . */
  private JSpinner scenario1Spinner = new JSpinner();

  /** . */
  private JLabel scenario2Label = new JLabel();

  /** . */
  private JSpinner scenario2Spinner = new JSpinner();

  /** . */
  private Scenarios scenarios;

  /** . */
  private JPanel spinnersPanel = new JPanel();

  /** . */
  private GridBagLayout spinnersPanelGridBagLayout = new GridBagLayout();

  /** . */
  private RSyntaxTextArea whereLabel = new RSyntaxTextArea();

  /** . */
  private JRadioButton sumRadioButton = null;

  /** . */
  private RSyntaxTextArea whereTextPane = new RSyntaxTextArea();

  private static Toolkit toolKit = Toolkit.getDefaultToolkit();

  /**
   * Initializes a new dialog box.
   *
   * @param mapPanel The NodusMapPanel.
   */
  public ScenariosDlg(NodusMapPanel mapPanel) {
    super(mapPanel.getMainFrame(), "", true);
    setTitle(i18n.get(ScenariosDlg.class, "Scenarios", "Scenarios"));

    nodusProject = mapPanel.getNodusProject();

    scenarios = new Scenarios(nodusProject);

    initialize();
    getRootPane().setDefaultButton(executeButton);
    setLocationRelativeTo(mapPanel);
  }

  /**
   * Just closes the dialog box.
   *
   * @param e ActionEvent
   */
  private void closeButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /**
   * Updates the GUI components when the "compare" checkbox is checked.
   *
   * @param e ActionEvent
   */
  private void compareRadioButton_actionPerformed(ActionEvent e) {
    scenario1Spinner.setEnabled(true);
    scenario2Spinner.setEnabled(true);
    resultSpinner.setEnabled(true);
    whereLabel.setText("WHERE");
    whereTextPane.setEnabled(true);
  }

  /**
   * Calls the method that executes the chosen action.
   *
   * @param e ActionEvent
   */
  private void executeButton_actionPerformed(ActionEvent e) {
    final int scenario1 = Integer.parseInt(((String) scenario1Spinner.getValue()).trim());
    final int scenario2 = Integer.parseInt(((String) scenario2Spinner.getValue()).trim());
    final int result = Integer.parseInt(((String) resultSpinner.getValue()).trim());
    final String whereString = whereTextPane.getText().trim();

    /*
     * Remove a scenario
     */
    if (removeRadioButton.isSelected()) {
      // Scenario must exist
      if (!scenarioExists(scenario1)) {
        JOptionPane.showMessageDialog(
            this,
            MessageFormat.format(
                i18n.get(ScenariosDlg.class, "Scenario_missing", "Scenario {0} doesn''t exists"),
                scenario1),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);
        return;
      }

      // Confirm deletion
      int answer =
          JOptionPane.showConfirmDialog(
              this,
              MessageFormat.format(
                  i18n.get(ScenariosDlg.class, "Delete_scenario", "Delete scenario {0}?"),
                  scenario1),
              NodusC.APPNAME,
              JOptionPane.YES_NO_OPTION);

      if (answer == JOptionPane.YES_OPTION) {
        scenarios.remove(scenario1);
        JOptionPane.showMessageDialog(
            this,
            i18n.get(ScenariosDlg.class, "Scenario_deleted", "Scenario deleted"),
            NodusC.APPNAME,
            JOptionPane.INFORMATION_MESSAGE);
      }
    }

    /*
     * Rename a scenario
     */
    if (renameRadioButton.isSelected()) {

      // Scenario to rename must exist
      if (!scenarioExists(scenario1)) {
        JOptionPane.showMessageDialog(
            this,
            MessageFormat.format(
                i18n.get(ScenariosDlg.class, "Scenario_missing", "Scenario {0} doesn''t exists"),
                scenario1),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);
        return;
      }

      // Confirm renaming
      int answer =
          JOptionPane.showConfirmDialog(
              this,
              MessageFormat.format(
                  i18n.get(ScenariosDlg.class, "Rename_scenario", "Rename scenario {0} to {1}?"),
                  scenario1,
                  result),
              NodusC.APPNAME,
              JOptionPane.YES_NO_OPTION);

      if (answer == JOptionPane.YES_OPTION) {
        // Overwrite ?
        if (scenarioExists(result)) {
          answer =
              JOptionPane.showConfirmDialog(
                  this,
                  MessageFormat.format(
                      i18n.get(
                          ScenariosDlg.class,
                          "Result_exists",
                          "Scenario {0} already exists. Overwrite ?"),
                      result),
                  NodusC.APPNAME,
                  JOptionPane.YES_NO_OPTION);
          if (answer == JOptionPane.NO_OPTION) {
            return;
          }
        }

        // Rename
        scenarios.rename(scenario1, result);
        JOptionPane.showMessageDialog(
            this,
            i18n.get(ScenariosDlg.class, "Scenario_renamed", "Scenario renamed"),
            NodusC.APPNAME,
            JOptionPane.INFORMATION_MESSAGE);
      }
    }

    /*
     * Compare two scenarios
     */
    if (compareRadioButton.isSelected()) {

      // Scenarios 1, 2 and result must be different
      if (scenario1 == scenario2 || scenario1 == result || scenario2 == result) {
        JOptionPane.showMessageDialog(
            this,
            i18n.get(
                ScenariosDlg.class,
                "Cannot_compare_a_scenario_to_itself",
                "Cannot compare a scenario to itself"),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);

        return;
      }

      // Scenario 1 must exist
      if (!scenarioExists(scenario1)) {
        JOptionPane.showMessageDialog(
            this,
            MessageFormat.format(
                i18n.get(ScenariosDlg.class, "Scenario_missing", "Scenario {0} doesn''t exists"),
                scenario1),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);

        return;
      }

      // Scenario 2 must exist
      if (!scenarioExists(scenario2)) {
        JOptionPane.showMessageDialog(
            this,
            MessageFormat.format(
                i18n.get(ScenariosDlg.class, "Scenario_missing", "Scenario {0} doesn''t exists"),
                scenario2),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);

        return;
      }

      // Confirm comparison
      int answer =
          JOptionPane.showConfirmDialog(
              this,
              MessageFormat.format(
                  i18n.get(
                      ScenariosDlg.class,
                      "Compare_scenarios",
                      "Compare scenarios {0} and {1} into {2}?"),
                  scenario1,
                  scenario2,
                  result),
              NodusC.APPNAME,
              JOptionPane.YES_NO_OPTION);

      if (answer == JOptionPane.YES_OPTION) {

        // Overwrite ?
        if (scenarioExists(result)) {
          answer =
              JOptionPane.showConfirmDialog(
                  this,
                  MessageFormat.format(
                      i18n.get(
                          ScenariosDlg.class,
                          "Result_exists",
                          "Scenario {0} already exists. Overwrite ?"),
                      result),
                  NodusC.APPNAME,
                  JOptionPane.YES_NO_OPTION);
          if (answer == JOptionPane.NO_OPTION) {
            return;
          }
        }

        // Compare
        setBusy(true);
        SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
        Thread work =
            new Thread() {
              public void run() {
                scenarios.compare(scenario1, scenario2, result, whereString);
                loop.exit();
              }
            };

        work.start();
        loop.enter();

        setBusy(false);

        // Describe the resulting scenario
        nodusProject.setLocalProperty(
            NodusC.PROP_ASSIGNMENT_DESCRIPTION + result,
            MessageFormat.format(
                i18n.get(
                    ScenariosDlg.class,
                    "Comparison_description",
                    "Comparison of scenarios {0} and {1}"),
                scenario1,
                scenario2));
        nodusProject.setLocalProperty(NodusC.PROP_SCENARIO, result);
        nodusProject.getNodusMapPanel().updateScenarioComboBox();
        // Done
        JOptionPane.showMessageDialog(
            this,
            i18n.get(ScenariosDlg.class, "Comparison_completed", "Comparison completed"),
            NodusC.APPNAME,
            JOptionPane.INFORMATION_MESSAGE);
      }
    }

    /* Add the results of two scenarios */
    if (sumRadioButton.isSelected()) {

      // Scenarios 1, 2 and result must be different
      if (scenario1 == scenario2 || scenario1 == result || scenario2 == result) {
        JOptionPane.showMessageDialog(
            this,
            i18n.get(
                ScenariosDlg.class,
                "Cannot_sum_a_scenario_to_itself",
                "Cannot sum a scenario to itself"),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);

        return;
      }

      // Scenario 1 must exist
      if (!scenarioExists(scenario1)) {
        JOptionPane.showMessageDialog(
            this,
            MessageFormat.format(
                i18n.get(ScenariosDlg.class, "Scenario_missing", "Scenario {0} doesn''t exists"),
                scenario1),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);

        return;
      }

      // Scenario 2 must exist
      if (!scenarioExists(scenario2)) {
        JOptionPane.showMessageDialog(
            this,
            MessageFormat.format(
                i18n.get(ScenariosDlg.class, "Scenario_missing", "Scenario {0} doesn''t exists"),
                scenario2),
            NodusC.APPNAME,
            JOptionPane.ERROR_MESSAGE);

        return;
      }

      // Confirm sum
      int answer =
          JOptionPane.showConfirmDialog(
              this,
              MessageFormat.format(
                  i18n.get(
                      ScenariosDlg.class, "Sum_scenarios", "Sum scenarios {0} and {1} into {2}?"),
                  scenario1,
                  scenario2,
                  result),
              NodusC.APPNAME,
              JOptionPane.YES_NO_OPTION);

      if (answer == JOptionPane.YES_OPTION) {

        // Overwrite ?
        if (scenarioExists(result)) {
          answer =
              JOptionPane.showConfirmDialog(
                  this,
                  MessageFormat.format(
                      i18n.get(
                          ScenariosDlg.class,
                          "Result_exists",
                          "Scenario {0} already exists. Overwrite ?"),
                      result),
                  NodusC.APPNAME,
                  JOptionPane.YES_NO_OPTION);
          if (answer == JOptionPane.NO_OPTION) {
            return;
          }
        }

        // Sum
        setBusy(true);

        SecondaryLoop loop = toolKit.getSystemEventQueue().createSecondaryLoop();
        Thread work =
            new Thread() {
              public void run() {
                scenarios.sum(scenario1, scenario2, result, whereString);
                loop.exit();
              }
            };

        work.start();
        loop.enter();

        setBusy(false);

        // Describe the resulting scenario
        nodusProject.setLocalProperty(
            NodusC.PROP_ASSIGNMENT_DESCRIPTION + result,
            MessageFormat.format(
                i18n.get(ScenariosDlg.class, "Sum_description", "Sum of scenarios {0} and {1}"),
                scenario1,
                scenario2));
        nodusProject.setLocalProperty(NodusC.PROP_SCENARIO, result);
        nodusProject.getNodusMapPanel().updateScenarioComboBox();

        // Done
        JOptionPane.showMessageDialog(
            this,
            i18n.get(ScenariosDlg.class, "Sum_completed", "Sum completed"),
            NodusC.APPNAME,
            JOptionPane.INFORMATION_MESSAGE);
      }
    }
  }

  /**
   * This method initializes SumRadioButton.
   *
   * @return javax.swing.JRadioButton
   */
  private JRadioButton getSumRadioButton() {
    if (sumRadioButton == null) {
      sumRadioButton = new JRadioButton();
      sumRadioButton.setText(i18n.get(ScenariosDlg.class, "Sum", "Sum"));
      sumRadioButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              scenario1Spinner.setEnabled(true);
              scenario2Spinner.setEnabled(true);
              resultSpinner.setEnabled(true);
              whereLabel.setText("WHERE");
              whereTextPane.setEnabled(true);
            }
          });
    }
    return sumRadioButton;
  }

  /** Initializes the GUI components of the dialog box. */
  private void initialize() {

    GridBagConstraints gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.insets = new Insets(5, 5, 5, 5);
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.gridy = 0;

    mainPanel.setLayout(mainPanelGridBagLayout);

    // SwingTweaks.textPaneNimbusTweak(whereLabel);
    whereLabel.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    whereLabel.setHighlightCurrentLine(false);
    whereLabel.setEditable(false);
    whereLabel.setBackground(mainPanel.getBackground());
    whereLabel.setText("WHERE");

    whereTextPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    whereTextPane.setHighlightCurrentLine(false);
    whereTextPane.setPreferredSize(new Dimension(100, 100));
    whereTextPane.setText("");

    closeButton.setText(i18n.get(ScenariosDlg.class, "Close", "Close"));
    closeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            closeButton_actionPerformed(e);
          }
        });

    executeButton.setText(i18n.get(ScenariosDlg.class, "Execute", "Execute"));

    executeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            executeButton_actionPerformed(e);
          }
        });

    removeRadioButton.setText(i18n.get(ScenariosDlg.class, "Remove", "Remove"));
    removeRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            removeRadioButton_actionPerformed(e);
          }
        });

    renameRadioButton.setText(i18n.get(ScenariosDlg.class, "Rename", "Rename"));
    renameRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            renameRadioButton_actionPerformed(e);
          }
        });

    actionPanel.setLayout(actionPanelGridBagLayout);

    compareRadioButton.setSelected(true);
    compareRadioButton.setText(i18n.get(ScenariosDlg.class, "Compare", "Compare"));
    compareRadioButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            compareRadioButton_actionPerformed(e);
          }
        });

    spinnersPanel.setLayout(spinnersPanelGridBagLayout);
    scenario1Label.setText(i18n.get(ScenariosDlg.class, "Scenario_1", "Scenario 1"));

    scenario2Label.setText(i18n.get(ScenariosDlg.class, "Scenario_2", "Scenario 2"));

    resultScenarioLabel.setText(i18n.get(ScenariosDlg.class, "Result", "Result"));

    setContentPane(mainPanel);

    mainPanel.add(
        whereLabel,
        new GridBagConstraints(
            0,
            2,
            5,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 0, 5),
            0,
            0));
    mainPanel.add(
        whereTextPane,
        new GridBagConstraints(
            0,
            3,
            4,
            1,
            0.5,
            0.05,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        closeButton,
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
    mainPanel.add(
        executeButton,
        new GridBagConstraints(
            1,
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
        actionPanel,
        new GridBagConstraints(
            0,
            0,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 0, 5),
            0,
            0));
    actionPanel.add(
        removeRadioButton,
        new GridBagConstraints(
            1,
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
    actionPanel.add(
        renameRadioButton,
        new GridBagConstraints(
            2,
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
    actionPanel.add(
        compareRadioButton,
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
    actionPanel.add(getSumRadioButton(), gridBagConstraints);
    spinnersPanel.add(
        scenario1Label,
        new GridBagConstraints(
            0,
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
    initializeSpinners();
    actionButtonGroup.add(compareRadioButton);
    actionButtonGroup.add(removeRadioButton);
    actionButtonGroup.add(renameRadioButton);
    actionButtonGroup.add(sumRadioButton);
    mainPanel.add(
        spinnersPanel,
        new GridBagConstraints(
            0,
            1,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 5, 0, 5),
            0,
            0));
    spinnersPanel.add(
        scenario1Spinner,
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
    spinnersPanel.add(
        scenario2Label,
        new GridBagConstraints(
            2,
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
    spinnersPanel.add(
        scenario2Spinner,
        new GridBagConstraints(
            3,
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
    spinnersPanel.add(
        resultScenarioLabel,
        new GridBagConstraints(
            4,
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
    spinnersPanel.add(
        resultSpinner,
        new GridBagConstraints(
            5,
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
    compareRadioButton_actionPerformed(null);

    pack();
  }

  /** Initializes the spinners with the available scenarios. */
  private void initializeSpinners() {

    LinkedList<String> list = new LinkedList<>();
    DecimalFormat formatter = new DecimalFormat("000");
    for (int i = 0; i < NodusC.MAXSCENARIOS; i++) {
      String s = formatter.format(i);
      list.add(s);
    }

    scenario1Spinner.setModel(new SpinnerListModel(list));
    scenario2Spinner.setModel(new SpinnerListModel(list));
    resultSpinner.setModel(new SpinnerListModel(list));
  }

  /**
   * Intercepts the key pressed events. They are forwarded to the parent class only if this dialog
   * is not busy (comparing...).
   *
   * @hidden
   */
  @Override
  public void keyPressed(KeyEvent e) {
    if (!isBusy) {
      super.keyPressed(e);
    }
  }

  /**
   * Updates the GUI components when the "remove" checkbox is checked.
   *
   * @param e ActionEvent
   */
  private void removeRadioButton_actionPerformed(ActionEvent e) {
    scenario2Spinner.setEnabled(false);
    resultSpinner.setEnabled(false);
    whereLabel.setText("");
    whereTextPane.setEnabled(false);
  }

  /**
   * Updates the GUI components when the "rename" checkbox is checked.
   *
   * @param e ActionEvent
   */
  private void renameRadioButton_actionPerformed(ActionEvent e) {
    scenario2Spinner.setEnabled(false);
    whereLabel.setText("");
    whereTextPane.setEnabled(false);
  }

  /**
   * Tests if the given scenario number already exists.
   *
   * @param scenarioId The scenario ID to test.
   * @return True if the scenario exists.
   */
  private boolean scenarioExists(int scenarioId) {
    String tableName =
        nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    tableName = nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, tableName) + scenarioId;
    if (JDBCUtils.tableExists(tableName)) {
      return true;
    }
    return false;
  }

  /**
   * Sets or resets the "busy" state (cursor, ...)
   *
   * @param busy True to set the busy state, or false to restore the normal state.
   */
  private void setBusy(boolean busy) {
    if (busy) {
      isBusy = true;
      closeButton.setEnabled(false);
      executeButton.setEnabled(false);
      getRootPane().getGlassPane().setVisible(true);
      getRootPane().getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    } else {
      getRootPane().getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      getRootPane().getGlassPane().setVisible(false);
      closeButton.setEnabled(true);
      executeButton.setEnabled(true);
      isBusy = false;
    }
  }
}
