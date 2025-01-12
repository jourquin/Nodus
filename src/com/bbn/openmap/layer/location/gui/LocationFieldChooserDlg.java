/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

package com.bbn.openmap.layer.location.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.layer.location.NodusLocationHandler;
import com.bbn.openmap.omGraphics.OMColorChooser;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.swing.JFontChooser;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Dialog box that allows the user to choose which field of the dbf file must be used as label. It
 * also allows to filter the labels to display through an SQL statement. Finally, a dedicated
 * check-box allows to toggle between regular labels and data representing a result to display.
 *
 * @author Bart Jourquin
 */
public class LocationFieldChooserDlg extends EscapeDialog {

  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = -2641799568965263398L;

  /** . */
  private JButton cancelButton = new JButton();

  /** . */
  private JButton colorButton = new JButton();

  /** . */
  private JCheckBox displayResultsCheckBox = new JCheckBox();

  /** . */
  private JList<String> fieldNames = new JList<String>(new DefaultListModel<String>());

  /** . */
  private JScrollPane fieldsScrollPane = new JScrollPane();

  /** . */
  private JButton fontsButton = new JButton();

  /** . */
  private RSyntaxTextArea fromLabel = new RSyntaxTextArea();

  /** . */
  private JPanel mainPanel = new JPanel();

  /** . */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** . */
  private NodusLocationHandler nodusLocationHandler;

  /** . */
  private JButton noFieldButton = new JButton();

  /** . */
  private JButton okButton = new JButton();

  /** . */
  private RSyntaxTextArea selectLabel = new RSyntaxTextArea();

  /** . */
  private RSyntaxTextArea whereStmt = new RSyntaxTextArea();

  /**
   * Initializes the GUI and places the dialog box relatively to the main frame.
   *
   * @param frame The parent frame.
   * @param title The title to display.
   * @param layer A NodusLocationHandler.
   */
  public LocationFieldChooserDlg(Frame frame, String title, NodusLocationHandler layer) {
    super(frame, title, true);
    nodusLocationHandler = layer;

    initialize();
    getRootPane().setDefaultButton(okButton);
    pack();
    setLocationRelativeTo(frame);
    setAlwaysOnTop(true);
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
   * Opens a JColorChooser dialog and set the name color for the labels.
   *
   * @param e ActionEvent
   */
  private void colorButton_actionPerformed(ActionEvent e) {
    setAlwaysOnTop(false);
    Color newColor =
        OMColorChooser.showDialog(
            null,
            i18n.get(LocationFieldChooserDlg.class, "Label_color", "Label color"),
            nodusLocationHandler.getNameColor());
    setAlwaysOnTop(true);

    if (newColor != null) {
      nodusLocationHandler.setNameColor(newColor);
    }
  }

  /**
   * Opens a FontChooserDialog and set the font for the labels.
   *
   * @param e ActionEvent
   */
  private void fontsButton_actionPerformed(ActionEvent e) {
    setAlwaysOnTop(false);

    Font font =
        JFontChooser.showDialog(
            nodusLocationHandler.getEsriLayer().getNodusMapPanel(),
            nodusLocationHandler.getFontName());

    setAlwaysOnTop(true);
    if (font != null) {
      nodusLocationHandler.setFontName(font);
    }
  }

  /**
   * Initializes the GUI components of the dialog box.
   *
   * @throws Exception On error
   */
  private void initialize() {
    mainPanel.setLayout(mainPanelGridBagLayout);
    okButton.setText(i18n.get(LocationFieldChooserDlg.class, "Ok", "Ok"));

    okButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            okButton_actionPerformed(e);
          }
        });
    cancelButton.setText(i18n.get(LocationFieldChooserDlg.class, "Cancel", "Cancel"));

    cancelButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelButton_actionPerformed(e);
          }
        });

    selectLabel.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    selectLabel.setHighlightCurrentLine(false);
    selectLabel.setEditable(false);
    selectLabel.setBackground(mainPanel.getBackground());
    selectLabel.setText("SELECT");

    fromLabel.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    fromLabel.setHighlightCurrentLine(false);
    fromLabel.setEditable(false);
    fromLabel.setBackground(mainPanel.getBackground());
    fromLabel.setText("FROM TABLENAME WHERE");

    whereStmt.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
    whereStmt.setHighlightCurrentLine(false);
    whereStmt.setMinimumSize(new Dimension(280, 16));
    whereStmt.setPreferredSize(new Dimension(280, 16));

    fieldsScrollPane.setMinimumSize(new Dimension(150, 100));
    fieldsScrollPane.setPreferredSize(new Dimension(150, 100));
    noFieldButton.setText(i18n.get(LocationFieldChooserDlg.class, "Reset_Field", "Reset Field"));

    noFieldButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            noFieldButton_actionPerformed(e);
          }
        });
    fontsButton.setText(i18n.get(LocationFieldChooserDlg.class, "Fonts", "Fonts"));

    fontsButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            fontsButton_actionPerformed(e);
          }
        });
    colorButton.setText(i18n.get(LocationFieldChooserDlg.class, "Color", "Color"));

    colorButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            colorButton_actionPerformed(e);
          }
        });

    displayResultsCheckBox.setText(
        i18n.get(LocationFieldChooserDlg.class, "Display_results", "Display results"));
    displayResultsCheckBox.setOpaque(false);

    setContentPane(mainPanel);

    mainPanel.add(
        fieldsScrollPane,
        new GridBagConstraints(
            0,
            1,
            4,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 5, 5, 5),
            0,
            0));
    fieldsScrollPane.getViewport().add(fieldNames, null);
    mainPanel.add(
        selectLabel,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 5),
            0,
            0));
    mainPanel.add(
        cancelButton,
        new GridBagConstraints(
            2,
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
        fromLabel,
        new GridBagConstraints(
            0,
            2,
            4,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 5, 0, 5),
            0,
            0));
    mainPanel.add(
        whereStmt,
        new GridBagConstraints(
            0,
            3,
            4,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        okButton,
        new GridBagConstraints(
            3,
            4,
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
        colorButton,
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
        fontsButton,
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
        noFieldButton,
        new GridBagConstraints(
            1,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHEAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        displayResultsCheckBox,
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

    // Fill the listboxes with field names
    for (int i = 0; i < nodusLocationHandler.getModel().getColumnCount(); i++) {
      String s = nodusLocationHandler.getModel().getColumnName(i);
      ((DefaultListModel<String>) fieldNames.getModel()).add(i, s);
    }

    // Create the FROM ... WHERE statement
    fromLabel.setText("FROM " + nodusLocationHandler.getTableName() + " WHERE");

    // Update components with previous selection
    int index = nodusLocationHandler.getLocationFieldIndex();
    if (index >= 0) {
      fieldNames.setSelectedIndex(index);
    }

    whereStmt.setText(nodusLocationHandler.getWhereStmt());

    displayResultsCheckBox.setSelected(nodusLocationHandler.isDisplayResults());
  }

  /**
   * Reset the field name, so that no label will be displayed.
   *
   * @param e ActionEvent
   */
  private void noFieldButton_actionPerformed(ActionEvent e) {
    // Don't select any field to display
    fieldNames.clearSelection();
  }

  /**
   * Saves the choices in the parent NodusLocationLayer, then closes the dialog box.
   *
   * @param e ActionEvent
   */
  private void okButton_actionPerformed(ActionEvent e) {
    // Save current settings
    nodusLocationHandler.setLocationFieldName((String) fieldNames.getSelectedValue());
    nodusLocationHandler.setWhereStmt(whereStmt.getText().trim());
    nodusLocationHandler.setDisplayResults(displayResultsCheckBox.isSelected());
    setVisible(false);
  }
}
