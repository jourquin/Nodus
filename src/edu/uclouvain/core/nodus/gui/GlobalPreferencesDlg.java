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

package edu.uclouvain.core.nodus.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.swing.GUIUtils;
import edu.uclouvain.core.nodus.utils.GitHubRelease;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Dialog that permits to modify some Nodus system wide preferences.
 *
 * @author Bart Jourquin
 */
public class GlobalPreferencesDlg extends EscapeDialog {

  private static final long serialVersionUID = -2615897070697552476L;

  private static final String FALSE = "false";
  private static final String TRUE = "true";

  private static I18n i18n = Environment.getI18n();

  /** . */
  private JPanel contentPanel = new JPanel();

  /** . */
  private JPanel dbPanel;

  /** . */
  private JCheckBox displayFullPathCheckBox;

  /** . */
  private JCheckBox displayToolTipsCheckBox;

  /** . */
  private JTextField gcIntervalTextField;

  /** . */
  private JRadioButton h2RadioButton;

  /** . */
  private JRadioButton hsqldbRadioButton;

  /** . */
  private JTextField maxSqlRowsTextField;

  /** . */
  private JCheckBox navMouseModeCheckBox;

  /** . */
  private NodusMapPanel nodusMapPanel;

  /** . */
  private ButtonGroup sgdbGroup;

  /** . */
  private JCheckBox stickyDrawingToolCheckBox;

  /** . */
  private JCheckBox reloadLastProjectCheckBox;

  /** . */
  private JCheckBox subframesAlwaysOnCheckBox;

  /** . */
  private JCheckBox useNativeGroovyConsoleCheckBox;

  /** . */
  private JCheckBox antialiasingCheckBox;

  /** . */
  private JCheckBox confirmQuitCheckBox;

  /** . */
  private boolean oldAntialiasing;

  /** Snapshot of values loaded when the dialog opened. */
  private String originalValuesSnapshot = "";

  /** . */
  private JCheckBox checkForUpdatesCheckBox;

  /**
   * Creates the system preferences dialog box.
   *
   * @param nodusMapPanel The Nodus map panel.
   */
  public GlobalPreferencesDlg(NodusMapPanel nodusMapPanel) {
    setTitle(i18n.get(GlobalPreferencesDlg.class, "Global_preferences", "Global preferences"));
    this.nodusMapPanel = nodusMapPanel;

    getContentPane().setLayout(new BorderLayout());
    contentPanel.setLayout(new GridBagLayout());
    contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
    getContentPane().add(contentPanel, BorderLayout.CENTER);

    gcIntervalTextField = new JTextField(7);
    gcIntervalTextField.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyTyped(KeyEvent e) {
            char c = e.getKeyChar();
            if (!(c >= '0' && c <= '9' || c == KeyEvent.VK_BACK_SPACE || c == KeyEvent.VK_DELETE)) {
              getToolkit().beep();
              e.consume();
            }
          }
        });
    gcIntervalTextField.setHorizontalAlignment(SwingConstants.LEFT);
    gcIntervalTextField.setText("0");

    maxSqlRowsTextField = new JTextField(7);
    maxSqlRowsTextField.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyTyped(KeyEvent e) {
            char c = e.getKeyChar();
            if (!(c >= '0' && c <= '9' || c == KeyEvent.VK_BACK_SPACE || c == KeyEvent.VK_DELETE)) {
              getToolkit().beep();
              e.consume();
            }
          }
        });
    maxSqlRowsTextField.setHorizontalAlignment(SwingConstants.LEFT);
    maxSqlRowsTextField.setText("0");

    reloadLastProjectCheckBox =
        new JCheckBox(
            i18n.get(GlobalPreferencesDlg.class, "Reopen_last_project", "Reopen last project"));
    subframesAlwaysOnCheckBox =
        new JCheckBox(
            i18n.get(
                GlobalPreferencesDlg.class, "Subframes_always_on_top", "Subframes always on top"));
    subframesAlwaysOnCheckBox.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            stickyDrawingToolCheckBox.setEnabled(subframesAlwaysOnCheckBox.isSelected());
          }
        });
    stickyDrawingToolCheckBox =
        new JCheckBox(
            i18n.get(GlobalPreferencesDlg.class, "Sticky_drawing_tool", "Sticky drawing tool"));
    displayFullPathCheckBox =
        new JCheckBox(
            i18n.get(
                GlobalPreferencesDlg.class,
                "Display_full_path_in_title",
                "Display full path in title"));
    useNativeGroovyConsoleCheckBox =
        new JCheckBox(
            i18n.get(
                GlobalPreferencesDlg.class,
                "Use_native_Groovy_console",
                "Use native Groovy console"));

    hsqldbRadioButton = new JRadioButton("HSQLDB");
    h2RadioButton = new JRadioButton("H2");
    sgdbGroup = new ButtonGroup();
    sgdbGroup.add(hsqldbRadioButton);
    sgdbGroup.add(h2RadioButton);

    navMouseModeCheckBox =
        new JCheckBox(
            i18n.get(GlobalPreferencesDlg.class, "Use_NavMouseMode2", "Centered zoom navigation"));
    antialiasingCheckBox =
        new JCheckBox(i18n.get(GlobalPreferencesDlg.class, "Antialiasing", "Antialiasing"));
    confirmQuitCheckBox =
        new JCheckBox(
            i18n.get(
                GlobalPreferencesDlg.class, "Confirm_before_quitting", "Confirm before quitting"));
    displayToolTipsCheckBox =
        new JCheckBox(i18n.get(GlobalPreferencesDlg.class, "Display_tooltips", "Display tooltips"));
    checkForUpdatesCheckBox =
        new JCheckBox(
            i18n.get(
                GlobalPreferencesDlg.class, "AutoCheckForUpdates", "Check for updates at startup"));

    final JButton checkForUpdateButton =
        new JButton(
            i18n.get(GlobalPreferencesDlg.class, "CheckForUpdates", "Check for updates now"));
    EscapeDialog parent = this;
    checkForUpdateButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            GitHubRelease.checkForNewerRelease(parent);
          }
        });

    JPanel startupPanel =
        createSection(i18n.get(GlobalPreferencesDlg.class, "Startup_and_exit", "Startup and exit"));

    addControl(startupPanel, reloadLastProjectCheckBox, 0, 8);
    addControl(startupPanel, checkForUpdatesCheckBox, 1, 8);
    JPanel updateButtonRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    updateButtonRow.add(checkForUpdateButton);
    addControl(startupPanel, updateButtonRow, 2, 28);
    addControl(startupPanel, confirmQuitCheckBox, 3, 8);
    JPanel leftColumn = new JPanel(new GridBagLayout());
    addSection(leftColumn, startupPanel, 0);

    dbPanel = createSection(i18n.get(GlobalPreferencesDlg.class, "Database", "Database"));
    addControl(
        dbPanel,
        new JLabel(i18n.get(GlobalPreferencesDlg.class, "Default_DBMS", "Default DBMS")),
        0,
        8);
    JPanel dbChoices = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 0));
    dbChoices.add(hsqldbRadioButton);
    dbChoices.add(h2RadioButton);
    addControl(dbPanel, dbChoices, 1, 8);
    addLabeledField(
        dbPanel,
        new JLabel(i18n.get(GlobalPreferencesDlg.class, "Max_SQL_rows", "Max SQL rows")),
        maxSqlRowsTextField,
        2);
    JPanel rightColumn = new JPanel(new GridBagLayout());
    addSection(rightColumn, dbPanel, 0);

    JPanel windowsPanel =
        createSection(
            i18n.get(GlobalPreferencesDlg.class, "Windows_and_tools", "Windows and tools"));
    addControl(windowsPanel, subframesAlwaysOnCheckBox, 0, 8);
    addControl(windowsPanel, stickyDrawingToolCheckBox, 1, 28);
    addControl(windowsPanel, displayFullPathCheckBox, 2, 8);
    addControl(windowsPanel, useNativeGroovyConsoleCheckBox, 3, 8);
    addSection(leftColumn, windowsPanel, 1);

    JPanel displayPanel =
        createSection(i18n.get(GlobalPreferencesDlg.class, "Map_and_display", "Map and display"));
    addControl(displayPanel, navMouseModeCheckBox, 0, 8);
    addControl(displayPanel, displayToolTipsCheckBox, 1, 8);
    addSection(rightColumn, displayPanel, 1);

    JPanel performancePanel =
        createSection(i18n.get(GlobalPreferencesDlg.class, "Performance", "Performance"));
    addControl(performancePanel, antialiasingCheckBox, 0, 8);
    addLabeledField(
        performancePanel,
        new JLabel(
            i18n.get(GlobalPreferencesDlg.class, "GC_interval", "GC interval (seconds; 0 = off)")),
        gcIntervalTextField,
        1);
    addSection(rightColumn, performancePanel, 2);

    addColumn(leftColumn, 0);
    addColumn(rightColumn, 1);

    final JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 4));
    getContentPane().add(buttonPane, BorderLayout.SOUTH);

    final JButton okButton = new JButton("OK");
    buttonPane.add(okButton);
    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            saveSettings();
            setVisible(false);
          }
        });

    final JButton cancelButton = new JButton("Cancel");
    buttonPane.add(cancelButton);
    cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            requestCloseDialog();
          }
        });

    loadSettings();
    originalValuesSnapshot = getValuesSnapshot();

    getRootPane().setDefaultButton(okButton);
    setModal(true);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            requestCloseDialog();
          }
        });
    pack();
    setLocationRelativeTo(nodusMapPanel);
  }

  private static JPanel createSection(String title) {
    JPanel section = new JPanel(new GridBagLayout());
    section.setBorder(BorderFactory.createTitledBorder(title));
    return section;
  }

  private void addColumn(JPanel column, int index) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = index;
    constraints.gridy = 0;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTH;
    contentPanel.add(column, constraints);
  }

  private static void addSection(JPanel container, JPanel section, int row) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTH;
    constraints.insets = new Insets(4, 4, 4, 4);
    container.add(section, constraints);
  }

  private static void addControl(JPanel section, Component control, int row, int leftInset) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.gridwidth = 2;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.insets = new Insets(2, leftInset, 2, 8);
    section.add(control, constraints);
  }

  private static void addLabeledField(JPanel section, JLabel label, JTextField field, int row) {
    GridBagConstraints labelConstraints = new GridBagConstraints();
    labelConstraints.gridx = 0;
    labelConstraints.gridy = row;
    labelConstraints.anchor = GridBagConstraints.WEST;
    labelConstraints.insets = new Insets(3, 8, 3, 8);
    section.add(label, labelConstraints);

    GridBagConstraints fieldConstraints = new GridBagConstraints();
    fieldConstraints.gridx = 1;
    fieldConstraints.gridy = row;
    fieldConstraints.weightx = 1;
    fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
    fieldConstraints.insets = new Insets(3, 0, 3, 8);
    section.add(field, fieldConstraints);
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

  /** Closes the dialog after resolving pending preference changes, if any. */
  private void requestCloseDialog() {
    if (!hasPendingChanges()) {
      setVisible(false);
      return;
    }

    Object[] options = {
      i18n.get(GlobalPreferencesDlg.class, "Discard_changes", "Discard changes"),
      i18n.get(GlobalPreferencesDlg.class, "Save_changes", "Save changes")
    };
    int answer =
        JOptionPane.showOptionDialog(
            this,
            i18n.get(GlobalPreferencesDlg.class, "Discard_changes_question", "Discard changes?"),
            i18n.get(GlobalPreferencesDlg.class, "Global_preferences", "Global preferences"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]);

    if (answer == JOptionPane.YES_OPTION) {
      setVisible(false);
    } else if (answer == JOptionPane.NO_OPTION) {
      saveSettings();
      setVisible(false);
    }
  }

  /** Returns true when the current controls differ from the values loaded when opened. */
  private boolean hasPendingChanges() {
    return !getValuesSnapshot().equals(originalValuesSnapshot);
  }

  /** Captures the current values of all persisted controls. */
  private String getValuesSnapshot() {
    return gcIntervalTextField.getText()
        + '\n'
        + maxSqlRowsTextField.getText()
        + '\n'
        + subframesAlwaysOnCheckBox.isSelected()
        + '\n'
        + stickyDrawingToolCheckBox.isSelected()
        + '\n'
        + displayFullPathCheckBox.isSelected()
        + '\n'
        + reloadLastProjectCheckBox.isSelected()
        + '\n'
        + useNativeGroovyConsoleCheckBox.isSelected()
        + '\n'
        + checkForUpdatesCheckBox.isSelected()
        + '\n'
        + navMouseModeCheckBox.isSelected()
        + '\n'
        + antialiasingCheckBox.isSelected()
        + '\n'
        + confirmQuitCheckBox.isSelected()
        + '\n'
        + displayToolTipsCheckBox.isSelected()
        + '\n'
        + getSelectedDbEngine();
  }

  /** Returns the selected DB engine constant. */
  private int getSelectedDbEngine() {
    if (h2RadioButton.isSelected()) {
      return JDBCUtils.DB_H2;
    }
    return JDBCUtils.DB_HSQLDB;
  }

  /** Sets the values of the different components. */
  private void loadSettings() {

    String value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_GC_INTERVAL, "0");
    gcIntervalTextField.setText(value);

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_MAX_SQL_ROWS, "200");
    maxSqlRowsTextField.setText(value);

    value =
        nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_SUBFRAMES_ALWAYS_ON_TOP, FALSE);
    subframesAlwaysOnCheckBox.setSelected(Boolean.parseBoolean(value));

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_STICKY_DRAWING_TOOL, FALSE);
    stickyDrawingToolCheckBox.setSelected(Boolean.parseBoolean(value));

    if (!subframesAlwaysOnCheckBox.isSelected()) {
      stickyDrawingToolCheckBox.setEnabled(false);
    } else {
      stickyDrawingToolCheckBox.setEnabled(true);
    }

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_NAV_MOUSE_MODE, "1").trim();
    if (value.equals("2")) {
      navMouseModeCheckBox.setSelected(true);
    }

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_ANTIALIASING, TRUE);
    antialiasingCheckBox.setSelected(Boolean.parseBoolean(value));
    oldAntialiasing = Boolean.parseBoolean(value);

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_CONFIRM_QUIT, TRUE);
    confirmQuitCheckBox.setSelected(Boolean.parseBoolean(value));

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_DISPLAY_TOOLTIPS, TRUE);
    displayToolTipsCheckBox.setSelected(Boolean.parseBoolean(value));

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_REOPEN_LATST_PROJECT, FALSE);
    reloadLastProjectCheckBox.setSelected(Boolean.parseBoolean(value));

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_DISPLAY_FULL_PATH, FALSE);
    displayFullPathCheckBox.setSelected(Boolean.parseBoolean(value));

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_CHECK_FOR_UPDATES, TRUE);
    checkForUpdatesCheckBox.setSelected(Boolean.parseBoolean(value));

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_USE_GROOVY_CONSOLE, FALSE);

    useNativeGroovyConsoleCheckBox.setSelected(Boolean.parseBoolean(value));

    value =
        nodusMapPanel
            .getNodusProperties()
            .getProperty(NodusC.PROP_EMBEDDED_DB, "" + JDBCUtils.DB_HSQLDB);

    // Avoid exception
    int db;
    try {
      db = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      db = JDBCUtils.DB_HSQLDB;
    }

    switch (db) {
      case JDBCUtils.DB_H2:
        h2RadioButton.setSelected(true);
        break;
      default:
        hsqldbRadioButton.setSelected(true);
    }
  }

  /** Save the settings in the Nodus properties file. */
  private void saveSettings() {

    // GC interval
    String value = gcIntervalTextField.getText();
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_GC_INTERVAL, value);

    // Max SQL rows
    value = maxSqlRowsTextField.getText();
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_MAX_SQL_ROWS, value);

    // Subframes always in top
    value = FALSE;
    if (subframesAlwaysOnCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_SUBFRAMES_ALWAYS_ON_TOP, value);

    // Sticky drawing tool
    value = FALSE;
    if (stickyDrawingToolCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_STICKY_DRAWING_TOOL, value);

    // Display full path
    value = FALSE;
    if (displayFullPathCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_DISPLAY_FULL_PATH, value);

    // Reload last project when Nodus is launched
    value = FALSE;
    if (reloadLastProjectCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_REOPEN_LATST_PROJECT, value);

    // Use native Groovy console
    value = FALSE;
    if (useNativeGroovyConsoleCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_USE_GROOVY_CONSOLE, value);

    // Check for updates
    value = FALSE;
    if (checkForUpdatesCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_CHECK_FOR_UPDATES, value);

    // Nav mouse mode
    String navType = "1";
    if (navMouseModeCheckBox.isSelected()) {
      navType = "2";
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_NAV_MOUSE_MODE, navType);

    // Default DBMS
    nodusMapPanel
        .getNodusProperties()
        .setProperty(NodusC.PROP_EMBEDDED_DB, "" + getSelectedDbEngine());

    nodusMapPanel.updateScenarioComboBox(false);

    // Update state of OnTopKeeper
    nodusMapPanel.runOnTopKeeper(
        subframesAlwaysOnCheckBox.isSelected(), stickyDrawingToolCheckBox.isSelected());
    if (nodusMapPanel.getNodusProject() != null
        && nodusMapPanel.getNodusProject().getServiceHandler() != null) {
      nodusMapPanel.getNodusProject().getServiceHandler().syncServiceEditorAlwaysOnTop();
    }

    // Update type of navigation mode
    nodusMapPanel.addNavMouseMode();

    // Use antialiasing
    value = FALSE;
    if (antialiasingCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_ANTIALIASING, value);

    if (Boolean.parseBoolean(value) != oldAntialiasing) {
      nodusMapPanel.setAntialising();
    }

    // Ask confirmation when quitting
    value = FALSE;
    if (confirmQuitCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_CONFIRM_QUIT, value);

    // Display contextual tooltips throughout Nodus
    value = displayToolTipsCheckBox.isSelected() ? TRUE : FALSE;
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_DISPLAY_TOOLTIPS, value);
    GUIUtils.setToolTipsEnabled(displayToolTipsCheckBox.isSelected());
  }
}
