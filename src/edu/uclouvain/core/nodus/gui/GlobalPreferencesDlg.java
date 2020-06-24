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

package edu.uclouvain.core.nodus.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

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

  private JPanel contentPanel = new JPanel();
  private JPanel dbPanel;
  private JRadioButton derbyRadioButton;
  private JCheckBox displayFullPathCheckBox;
  private JTextField gcIntervalTextField;
  private JRadioButton h2RadioButton;
  private JRadioButton hsqldbRadioButton;
  private JTextField maxSqlRowsTextField;
  private JCheckBox navMouseModeCheckBox;
  private NodusMapPanel nodusMapPanel;
  private ButtonGroup sgdbGroup;
  private JRadioButton sqliteRadioButton;

  private JCheckBox stickyDrawingToolCheckBox;

  private JCheckBox reloadLastProjectCheckBox;
  private JCheckBox subframesAlwaysOnCheckBox;
  private JCheckBox useNativeGroovyConsoleCheckBox;
  private JCheckBox antialiasingCheckBox;

  private boolean oldAntialiasing;

  /**
   * Creates the system preferences dialog box.
   *
   * @param nodusMapPanel The Nodus map panel.
   */
  public GlobalPreferencesDlg(NodusMapPanel nodusMapPanel) {
    setTitle(i18n.get(GlobalPreferencesDlg.class, "Global_preferences", "Global preferences"));
    this.nodusMapPanel = nodusMapPanel;

    GridBagLayout contentPaneGridBagLayout = new GridBagLayout();
    getContentPane().setLayout(contentPaneGridBagLayout);

    GridBagConstraints gbcContentPanel = new GridBagConstraints();
    gbcContentPanel.insets = new Insets(0, 0, 0, 5);
    getContentPane().add(contentPanel, gbcContentPanel);
    GridBagLayout gblContentPanel = new GridBagLayout();
    gblContentPanel.columnWeights = new double[] {0.0, 1.0};
    gblContentPanel.rowWeights = new double[] {0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0};
    contentPanel.setLayout(gblContentPanel);

    final JLabel forcedGcIntervalLabel =
        new JLabel(
            i18n.get(
                GlobalPreferencesDlg.class,
                "GC_interval",
                "GC interval in seconds (0 if not forced)"));
    GridBagConstraints gbcLblForcedGcInterval = new GridBagConstraints();
    gbcLblForcedGcInterval.fill = GridBagConstraints.HORIZONTAL;
    gbcLblForcedGcInterval.anchor = GridBagConstraints.WEST;
    gbcLblForcedGcInterval.insets = new Insets(5, 5, 5, 5);
    gbcLblForcedGcInterval.gridx = 0;
    gbcLblForcedGcInterval.gridy = 0;
    contentPanel.add(forcedGcIntervalLabel, gbcLblForcedGcInterval);

    gcIntervalTextField = new JTextField();
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
    GridBagConstraints gbcTextField = new GridBagConstraints();
    gbcTextField.insets = new Insets(5, 5, 5, 0);
    gbcTextField.fill = GridBagConstraints.HORIZONTAL;
    gbcTextField.gridx = 1;
    gbcTextField.gridy = 0;
    contentPanel.add(gcIntervalTextField, gbcTextField);

    final JLabel maxSqlRowsLabel =
        new JLabel(i18n.get(GlobalPreferencesDlg.class, "Max_SQL_rows", "Max SQL rows"));
    GridBagConstraints gbcLblMaxSqlRows = new GridBagConstraints();
    gbcLblMaxSqlRows.anchor = GridBagConstraints.WEST;
    gbcLblMaxSqlRows.insets = new Insets(5, 5, 5, 5);
    gbcLblMaxSqlRows.gridx = 0;
    gbcLblMaxSqlRows.gridy = 1;
    contentPanel.add(maxSqlRowsLabel, gbcLblMaxSqlRows);

    maxSqlRowsTextField = new JTextField();
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
    GridBagConstraints gbcSqltextField = new GridBagConstraints();
    gbcSqltextField.insets = new Insets(5, 5, 5, 0);
    gbcSqltextField.fill = GridBagConstraints.HORIZONTAL;
    gbcSqltextField.gridx = 1;
    gbcSqltextField.gridy = 1;
    contentPanel.add(maxSqlRowsTextField, gbcSqltextField);

    reloadLastProjectCheckBox =
        new JCheckBox(
            i18n.get(GlobalPreferencesDlg.class, "Reopen_last_project", "Reopen last project"));
    GridBagConstraints gbcChckbxReloadLastProject = new GridBagConstraints();
    gbcChckbxReloadLastProject.anchor = GridBagConstraints.WEST;
    gbcChckbxReloadLastProject.insets = new Insets(5, 5, 5, 5);
    gbcChckbxReloadLastProject.gridx = 0;
    gbcChckbxReloadLastProject.gridy = 2;
    contentPanel.add(reloadLastProjectCheckBox, gbcChckbxReloadLastProject);

    subframesAlwaysOnCheckBox =
        new JCheckBox(
            i18n.get(
                GlobalPreferencesDlg.class, "Subframes_always_on_top", "Subframes always on top"));
    subframesAlwaysOnCheckBox.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (subframesAlwaysOnCheckBox.isSelected()) {
              stickyDrawingToolCheckBox.setEnabled(true);
            } else {
              stickyDrawingToolCheckBox.setEnabled(false);
            }
          }
        });
    GridBagConstraints gbcChckbxSubframesAlwaysOn = new GridBagConstraints();
    gbcChckbxSubframesAlwaysOn.anchor = GridBagConstraints.WEST;
    gbcChckbxSubframesAlwaysOn.insets = new Insets(5, 5, 5, 5);
    gbcChckbxSubframesAlwaysOn.gridx = 0;
    gbcChckbxSubframesAlwaysOn.gridy = 3;
    contentPanel.add(subframesAlwaysOnCheckBox, gbcChckbxSubframesAlwaysOn);

    stickyDrawingToolCheckBox =
        new JCheckBox(
            i18n.get(GlobalPreferencesDlg.class, "Sticky_drawing_tool", "Sticky drawing tool"));
    GridBagConstraints gbcTglbtnStickyDrawingTool = new GridBagConstraints();
    gbcTglbtnStickyDrawingTool.anchor = GridBagConstraints.WEST;
    gbcTglbtnStickyDrawingTool.insets = new Insets(5, 5, 5, 5);
    gbcTglbtnStickyDrawingTool.gridx = 0;
    gbcTglbtnStickyDrawingTool.gridy = 4;
    contentPanel.add(stickyDrawingToolCheckBox, gbcTglbtnStickyDrawingTool);

    displayFullPathCheckBox =
        new JCheckBox(
            i18n.get(
                GlobalPreferencesDlg.class,
                "Display_full_path_in_title",
                "Display full path in title"));
    GridBagConstraints gbcChckbxDisplayFullPath = new GridBagConstraints();
    gbcChckbxDisplayFullPath.anchor = GridBagConstraints.WEST;
    gbcChckbxDisplayFullPath.insets = new Insets(5, 5, 5, 5);
    gbcChckbxDisplayFullPath.gridx = 0;
    gbcChckbxDisplayFullPath.gridy = 5;
    contentPanel.add(displayFullPathCheckBox, gbcChckbxDisplayFullPath);

    useNativeGroovyConsoleCheckBox =
        new JCheckBox(
            i18n.get(
                GlobalPreferencesDlg.class,
                "Use_native_Groovy_console",
                "Use native Groovy console"));
    GridBagConstraints gbcChckbxUseNativeGroovy = new GridBagConstraints();
    gbcChckbxUseNativeGroovy.anchor = GridBagConstraints.WEST;
    gbcChckbxUseNativeGroovy.insets = new Insets(5, 5, 5, 5);
    gbcChckbxUseNativeGroovy.gridx = 0;
    gbcChckbxUseNativeGroovy.gridy = 6;
    contentPanel.add(useNativeGroovyConsoleCheckBox, gbcChckbxUseNativeGroovy);

    dbPanel = new JPanel();
    String title = i18n.get(GlobalPreferencesDlg.class, "Default_DBMS", "Default DBMS");
    dbPanel.setBorder(
        new TitledBorder(
            null, title, TitledBorder.LEADING, TitledBorder.TOP, null, new Color(59, 59, 59)));

    GridBagConstraints gbcPanel = new GridBagConstraints();
    gbcPanel.gridheight = 5;
    gbcPanel.insets = new Insets(5, 5, 0, 0);
    gbcPanel.fill = GridBagConstraints.BOTH;
    gbcPanel.gridx = 1;
    gbcPanel.gridy = 2;
    dbPanel.setLayout(new GridBagLayout());
    contentPanel.add(dbPanel, gbcPanel);

    hsqldbRadioButton = new JRadioButton("HSQLDB");
    GridBagConstraints gbcRdbtnNewRadioButton = new GridBagConstraints();
    gbcRdbtnNewRadioButton.ipadx = 50;
    gbcRdbtnNewRadioButton.anchor = GridBagConstraints.WEST;
    gbcRdbtnNewRadioButton.insets = new Insets(5, 5, 5, 5);
    gbcRdbtnNewRadioButton.gridx = 0;
    gbcRdbtnNewRadioButton.gridy = 0;
    dbPanel.add(hsqldbRadioButton, gbcRdbtnNewRadioButton);

    h2RadioButton = new JRadioButton("H2");
    GridBagConstraints gbcRdbtnH = new GridBagConstraints();
    gbcRdbtnH.anchor = GridBagConstraints.WEST;
    gbcRdbtnH.insets = new Insets(5, 5, 5, 5);
    gbcRdbtnH.gridx = 0;
    gbcRdbtnH.gridy = 1;
    dbPanel.add(h2RadioButton, gbcRdbtnH);

    derbyRadioButton = new JRadioButton("Derby");
    GridBagConstraints gbcRdbtnDerby = new GridBagConstraints();
    gbcRdbtnDerby.anchor = GridBagConstraints.WEST;
    gbcRdbtnDerby.insets = new Insets(5, 5, 5, 5);
    gbcRdbtnDerby.gridx = 0;
    gbcRdbtnDerby.gridy = 2;
    dbPanel.add(derbyRadioButton, gbcRdbtnDerby);

    sqliteRadioButton = new JRadioButton("SQLite");
    GridBagConstraints gbcRdbtnSqlite = new GridBagConstraints();
    gbcRdbtnSqlite.anchor = GridBagConstraints.WEST;
    gbcRdbtnSqlite.insets = new Insets(5, 5, 5, 5);
    gbcRdbtnSqlite.gridx = 0;
    gbcRdbtnSqlite.gridy = 3;
    dbPanel.add(sqliteRadioButton, gbcRdbtnSqlite);

    // The SQLite JDBC driver is provided with Nodus, but SQLite may not be installed
    if (!JDBCUtils.isSQliteInstalled()) {
      sqliteRadioButton.setEnabled(false);
    }

    sgdbGroup = new ButtonGroup();
    sgdbGroup.add(hsqldbRadioButton);
    sgdbGroup.add(h2RadioButton);
    sgdbGroup.add(derbyRadioButton);
    sgdbGroup.add(sqliteRadioButton);

    navMouseModeCheckBox =
        new JCheckBox(
            i18n.get(GlobalPreferencesDlg.class, "Use_NavMouseMode2", "Centered zoom navigation"));
    GridBagConstraints gbcChckbxUseCenteredNav = new GridBagConstraints();
    gbcChckbxUseCenteredNav.anchor = GridBagConstraints.WEST;
    gbcChckbxUseCenteredNav.insets = new Insets(5, 5, 5, 5);
    gbcChckbxUseCenteredNav.gridx = 0;
    gbcChckbxUseCenteredNav.gridy = 7;
    contentPanel.add(navMouseModeCheckBox, gbcChckbxUseCenteredNav);

    antialiasingCheckBox =
        new JCheckBox(i18n.get(GlobalPreferencesDlg.class, "Antialiasing", "Antialiasing"));
    GridBagConstraints gbcChckbxUseAntialiasing = new GridBagConstraints();
    gbcChckbxUseAntialiasing.anchor = GridBagConstraints.WEST;
    gbcChckbxUseAntialiasing.insets = new Insets(5, 5, 5, 5);
    gbcChckbxUseAntialiasing.gridx = 0;
    gbcChckbxUseAntialiasing.gridy = 8;
    contentPanel.add(antialiasingCheckBox, gbcChckbxUseAntialiasing);

    final JPanel buttonPane = new JPanel();
    GridBagConstraints gbcButtonPane = new GridBagConstraints();
    gbcButtonPane.anchor = GridBagConstraints.NORTH;
    gbcButtonPane.fill = GridBagConstraints.HORIZONTAL;
    gbcButtonPane.gridx = 0;
    gbcButtonPane.gridy = 1;
    getContentPane().add(buttonPane, gbcButtonPane);
    GridBagLayout gblButtonPane = new GridBagLayout();
    gblButtonPane.columnWidths = new int[] {0, 0, 0};
    gblButtonPane.columnWeights = new double[] {0.0, 0.0, Double.MIN_VALUE};
    buttonPane.setLayout(gblButtonPane);

    final JButton cancelButton = new JButton("Cancel");
    i18n.get(GlobalPreferencesDlg.class, "", "");
    GridBagConstraints gbcCancelbutton = new GridBagConstraints();
    gbcCancelbutton.insets = new Insets(5, 5, 5, 5);
    gbcCancelbutton.gridx = 1;
    gbcCancelbutton.gridy = 0;
    buttonPane.add(cancelButton, gbcCancelbutton);
    cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            setVisible(false);
          }
        });

    final JButton okButton = new JButton("OK");
    i18n.get(GlobalPreferencesDlg.class, "", "");
    // okButton.setActionCommand("OK");
    GridBagConstraints gbcOkbutton = new GridBagConstraints();
    gbcOkbutton.insets = new Insets(5, 5, 5, 5);
    gbcOkbutton.gridx = 0;
    gbcOkbutton.gridy = 0;
    buttonPane.add(okButton, gbcOkbutton);
    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            saveSettings();
            setVisible(false);
          }
        });

    loadSettings();

    getRootPane().setDefaultButton(okButton);
    setLocationRelativeTo(nodusMapPanel);
    setModal(true);
    pack();
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

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_REOPEN_LATST_PROJECT, FALSE);
    reloadLastProjectCheckBox.setSelected(Boolean.parseBoolean(value));

    value = nodusMapPanel.getNodusProperties().getProperty(NodusC.PROP_DISPLAY_FULL_PATH, FALSE);
    displayFullPathCheckBox.setSelected(Boolean.parseBoolean(value));

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
      case JDBCUtils.DB_DERBY:
        derbyRadioButton.setSelected(true);
        break;
      case JDBCUtils.DB_SQLITE:
        sqliteRadioButton.setSelected(true);
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

    // Nav mouse mode
    String navType = "1";
    if (navMouseModeCheckBox.isSelected()) {
      navType = "2";
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_NAV_MOUSE_MODE, navType);

    // Default DBMS
    int intValue = JDBCUtils.DB_HSQLDB;
    if (h2RadioButton.isSelected()) {
      intValue = JDBCUtils.DB_H2;
    }
    if (derbyRadioButton.isSelected()) {
      intValue = JDBCUtils.DB_DERBY;
    }
    if (sqliteRadioButton.isSelected()) {
      intValue = JDBCUtils.DB_SQLITE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_EMBEDDED_DB, "" + intValue);

    nodusMapPanel.updateScenarioComboBox();

    // Update state of OnTopKeeper
    nodusMapPanel.runOnTopKeeper(
        subframesAlwaysOnCheckBox.isSelected(), stickyDrawingToolCheckBox.isSelected());

    // Update type of navigation mode
    nodusMapPanel.addNavMouseMode();

    // Use antialiasing
    value = FALSE;
    if (antialiasingCheckBox.isSelected()) {
      value = TRUE;
    }
    nodusMapPanel.getNodusProperties().setProperty(NodusC.PROP_ANTIALIASING, value);

    if (Boolean.parseBoolean(value) != oldAntialiasing) {
      /*JOptionPane.showMessageDialog(
      nodusMapPanel,
      i18n.get(
          GlobalPreferencesDlg.class,
          "Antialiasing_needs_restart",
          "Antialiasing setting will be applied at next restart"),
      NodusC.APPNAME,
      JOptionPane.INFORMATION_MESSAGE);*/
      nodusMapPanel.setAntialising();
    }
  }
}
