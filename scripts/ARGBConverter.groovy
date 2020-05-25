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

import com.bbn.openmap.util.ColorFactory;

import edu.uclouvain.core.nodus.NodusMapPanel;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ARGBConverter_ extends JDialog {
  static final long serialVersionUID = -4355930570911227672L;

  JPanel panel1 = new JPanel();

  GridBagLayout gridBagLayout1 = new GridBagLayout();

  JColorChooser colorChooser = new JColorChooser();

  JButton closeButton = new JButton();

  JPanel jPanel1 = new JPanel();

  GridBagLayout gridBagLayout2 = new GridBagLayout();

  JLabel argbLabel = new JLabel();

  JLabel label1 = new JLabel();

  /**
   * Creates a new ARGB dialog
   *
   * @param frame Frame
   */
  public ARGBConverter_(NodusMapPanel nodusMapPanel) {
    super(nodusMapPanel.getMainFrame(), "Color to ARGB string converter", false);
    try {
      jbInit();
      pack();
      setVisible(true);
      setLocationRelativeTo(nodusMapPanel.getMainFrame());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Initializes the GUI components of the dialog.
   *
   * @throws Exception
   */
  private void jbInit() throws Exception {
    panel1.setLayout(gridBagLayout1);
    closeButton.setToolTipText("");
    closeButton.setText("Close");
    
    closeButton.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(ActionEvent e) {
            closeButton_actionPerformed(e);
          }
        });

    ColorSelectionModel model = colorChooser.getSelectionModel();

    // Add listener on model to detect changes to selected color
    model.addChangeListener(
        new ChangeListener() {
          public void stateChanged(ChangeEvent evt) {
            ColorSelectionModel mod = (ColorSelectionModel) evt.getSource();
            // Transform it in ARGB String
            argbLabel.setText(
                ColorFactory.getHexColorString(mod.getSelectedColor()).toUpperCase());
          }
        });

    this.setResizable(false);
    jPanel1.setLayout(gridBagLayout2);
    argbLabel.setFont(new java.awt.Font("Dialog", 1, 20));
    argbLabel.setText("--------");
    label1.setFont(new java.awt.Font("Dialog", 1, 20));
    label1.setText("ARGB value:");

    getContentPane().add(panel1);
    panel1.add(
        colorChooser,
        new GridBagConstraints(
            0,
            0,
            3,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    panel1.add(
        closeButton,
        new GridBagConstraints(
            2,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    panel1.add(
        jPanel1,
        new GridBagConstraints(
            1,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));
    jPanel1.add(
        argbLabel,
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
    jPanel1.add(
        label1,
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
  }

  /**
   * Just closes the dialog
   *
   * @param e ActionEvent
   */
  void closeButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }
}

new ARGBConverter_(nodusMapPanel);