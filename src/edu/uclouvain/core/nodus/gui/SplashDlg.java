/**
 * Copyright (c) 1991-2019 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.utils.BuildIdGenerator;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Displays a dialog with the splash image and access to the "License" and the "About" dialogs.
 *
 * @author Bart Jourquin
 */
public class SplashDlg extends EscapeDialog {
  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = -4375799813094308880L;

  private JButton aboutButton = new JButton();

  private JButton closeButton = new JButton();

  private JLabel iconLabel = new JLabel();

  private JPanel mainPanel = new JPanel();

  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  private NodusMapPanel nodusMapPanel;

  /**
   * Creates a new "splash" dialog.
   *
   * @param nodusMapPanel The Nodus map panel.
   */
  public SplashDlg(NodusMapPanel nodusMapPanel) {
    super(nodusMapPanel.getMainFrame(), "", false);
    this.nodusMapPanel = nodusMapPanel;

    initialize();
    getRootPane().setDefaultButton(closeButton);
  }

  /**
   * Opens a "About" dialog box when pressed.
   *
   * @param e ActionEvent
   */
  private void aboutButton_actionPerformed(ActionEvent e) {
    setVisible(false);
    AboutDlg aboutDlg = new AboutDlg(nodusMapPanel);
    aboutDlg.setLocationRelativeTo(this);
    aboutDlg.setVisible(true);
  }

  /**
   * Closes the dialog and updated the properties with the current state of the "show at startup"
   * checkbox.
   *
   * @param e ActionEvent
   */
  private void closeButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /** Initializes the GUI components of the dialog. */
  private void initialize() {
    String version = NodusC.VERSION;
    version = version.replace('.', '_');
    final ImageIcon splash =
        new ImageIcon(getClass().getResource("NodusSplash" + version + ".png"));
    mainPanel.setLayout(mainPanelGridBagLayout);

    // Get Build ID
    BuildIdGenerator generator = new BuildIdGenerator();
    JLabel buildIdLabel = new JLabel(generator.getBuildId());
    buildIdLabel.setOpaque(false);
    buildIdLabel.setFont(new Font("Dialog", Font.BOLD, 12));
    buildIdLabel.setForeground(Color.white);
    mainPanel.add(
        buildIdLabel,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.5,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    iconLabel.setForeground(Color.blue);
    iconLabel.setMinimumSize(new Dimension(640, 320));
    iconLabel.setPreferredSize(new Dimension(640, 320));
    iconLabel.setText("");
    iconLabel.setIcon(splash);

    closeButton.setActionCommand("Close");
    closeButton.setText(i18n.get(SplashDlg.class, "Close", "Close"));
    closeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            closeButton_actionPerformed(e);
          }
        });
    mainPanel.setFont(new java.awt.Font("Dialog", 0, 14));
    setResizable(false);
    setTitle(NodusC.COPYRIGHT);

    aboutButton.setText(i18n.get(SplashDlg.class, "About", "About"));
    aboutButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            aboutButton_actionPerformed(e);
          }
        });

    mainPanel.add(
        aboutButton,
        new GridBagConstraints(
            1,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    mainPanel.add(
        closeButton,
        new GridBagConstraints(
            2,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHEAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    mainPanel.add(
        iconLabel,
        new GridBagConstraints(
            0,
            0,
            3,
            2,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 0, 0, 0),
            0,
            0));
    setContentPane(mainPanel);

    pack();
  }
}
