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

package com.bbn.openmap.layer.shape.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * This dialog box controls the way the links or nodes are displayed. <br>
 * - Filter the graphics to display with a SQL statement <br>
 * - Render or not the style of the graphic <br>
 * - Render or not the results of an operation (flows for instance)
 *
 * @author Bart Jourquin
 */
public class SelectPropertiesDlg extends EscapeDialog implements ShapeConstants {

  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = -6021795944941747818L;

  /** . */
  private JButton cancelButton = new JButton();

  /** . */
  private JPanel mainPanel = new JPanel();

  /** . */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** . */
  private NodusEsriLayer nodusEsriLayer;

  /** . */
  private JButton okButton = new JButton();

  /** . */
  private JCheckBox renderCheckBox = new JCheckBox();

  /** . */
  private JCheckBox resultsCheckBox = new JCheckBox();

  /** . */
  private RSyntaxTextArea sqlLabel = new RSyntaxTextArea();

  /** . */
  private JCheckBox tooltipsCheckBox = new JCheckBox();

  /** . */
  private RSyntaxTextArea whereStmt = new RSyntaxTextArea();

  /**
   * Calls the initialization process of the dialog box (GUI, default parameters) with reference to
   * the NodusEsriLayer that has to be controlled.
   *
   * @param layer NodusEsriLayer
   */
  public SelectPropertiesDlg(NodusEsriLayer layer) {
    super(layer.getNodusMapPanel().getMainFrame(), "", true);
    nodusEsriLayer = layer;

    initialize();

    getRootPane().setDefaultButton(okButton);

    // Create the FROM ... WHERE statement
    sqlLabel.setText(
        "SELECT "
            + JDBCUtils.getQuotedCompliantIdentifier(NodusC.DBF_NUM)
            + " FROM "
            + nodusEsriLayer.getTableName()
            + " WHERE");

    // Display the latest where statement
    whereStmt.setText(nodusEsriLayer.getWhereStmt());

    pack();
    setLocationRelativeTo(layer.getNodusMapPanel().getMainFrame());
    setAlwaysOnTop(true);

    whereStmt.requestFocusInWindow();
  }

  /**
   * Just closes the dialog box.
   *
   * @param e ActionEvent
   */
  private void cancelButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /**
   * Real initialization work.
   *
   * @throws Exception On error
   */
  private void initialize() {
    mainPanel.setLayout(mainPanelGridBagLayout);

    setTitle(i18n.get(SelectPropertiesDlg.class, "Display_properties", "Display properties"));
    setTitle(
        MessageFormat.format(
            i18n.get(
                SelectPropertiesDlg.class, "Display_properties", "Display properties of \"{0}\""),
            nodusEsriLayer.getName()));

    okButton.setText(i18n.get(SelectPropertiesDlg.class, "Ok", "Ok"));
    okButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            okButton_actionPerformed(e);
          }
        });

    cancelButton.setText(i18n.get(SelectPropertiesDlg.class, "Cancel", "Cancel"));
    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelButton_actionPerformed(e);
          }
        });

    //SwingTweaks.textPaneNimbusTweak(sqlLabel);
    sqlLabel.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    sqlLabel.setHighlightCurrentLine(false);
    sqlLabel.setEditable(false);
    sqlLabel.setBackground(mainPanel.getBackground());
    sqlLabel.setText("SELECT num  FROM xxx WHERE");

    whereStmt.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    whereStmt.setHighlightCurrentLine(false);
    whereStmt.setText("  ");

    renderCheckBox.setText(i18n.get(SelectPropertiesDlg.class, "Render_styles", "Render styles"));
    renderCheckBox.setOpaque(false);

    tooltipsCheckBox.setText(i18n.get(SelectPropertiesDlg.class, "Dbf_tooltips", "Dbf tooltips"));
    tooltipsCheckBox.setOpaque(false);

    resultsCheckBox.setText(
        i18n.get(SelectPropertiesDlg.class, "Render_results", "Render results"));
    resultsCheckBox.setOpaque(false);

    setContentPane(mainPanel);
    mainPanel.add(
        sqlLabel,
        new GridBagConstraints(
            0,
            1,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(5, 10, 0, 10),
            0,
            0));

    mainPanel.add(
        whereStmt,
        new GridBagConstraints(
            0,
            2,
            4,
            1,
            0.05,
            0.05,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 10, 10, 10),
            0,
            0));
    mainPanel.add(
        okButton,
        new GridBagConstraints(
            3,
            3,
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
        renderCheckBox,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 0),
            0,
            0));
    mainPanel.add(
        cancelButton,
        new GridBagConstraints(
            2,
            3,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 0, 5, 5),
            0,
            0));
    mainPanel.add(
        resultsCheckBox,
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
    mainPanel.add(
        tooltipsCheckBox,
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

    tooltipsCheckBox.setSelected(nodusEsriLayer.getDisplayDbfToolTips());
    renderCheckBox.setSelected(nodusEsriLayer.getStyleRendering());
    resultsCheckBox.setSelected(nodusEsriLayer.isDisplayResults());
  }

  /**
   * Fetch the contents of the controls and pass them to the parent NodusEsriLayer.
   *
   * @param e ActionEvent
   */
  private void okButton_actionPerformed(ActionEvent e) {
    // Set display options and filter
    nodusEsriLayer.setDisplayDbfToolTips(tooltipsCheckBox.isSelected());
    nodusEsriLayer.setStyleRendering(renderCheckBox.isSelected());
    nodusEsriLayer.setDisplayResults(resultsCheckBox.isSelected());
    nodusEsriLayer.applyWhereFilter(whereStmt.getText().trim());
    nodusEsriLayer.attachStyles();
    nodusEsriLayer.doPrepare();

    setVisible(false);
  }
}
