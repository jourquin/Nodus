/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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

import edu.uclouvain.core.nodus.swing.EscapeDialog;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * A dialog box that display several informations about the system Nodus is running on.
 *
 * @author Bart Jourquin
 */
public class ArchInfoDlg extends EscapeDialog {

  private static final long serialVersionUID = 1L;

  private static I18n i18n = Environment.getI18n();

  private JButton closeButton = null;

  private JPanel mainPanel = null;

  private JLabel versionInfo = null;

  /**
   * Creates the dialog box.
   *
   * @param aboutDlg The parent dialog.
   */
  public ArchInfoDlg(JDialog aboutDlg) {
    super(aboutDlg, i18n.get(ArchInfoDlg.class, "System_info", "System info"), true);
    this.setSize(300, 200);
    this.setContentPane(getMainPanel());
    getRootPane().setDefaultButton(closeButton);
    setLocationRelativeTo(aboutDlg);
  }

  /**
   * This method initializes jButton
   *
   * @return javax.swing.JButton
   */
  private JButton getCloseButton() {
    if (closeButton == null) {
      closeButton = new JButton();
      closeButton.setText("Close");
      closeButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              setVisible(false);
            }
          });
    }
    return closeButton;
  }

  /**
   * This method initializes jContentPane
   *
   * @return javax.swing.JPanel
   */
  private JPanel getMainPanel() {
    if (mainPanel == null) {
      GridBagConstraints closeButtonConstraints = new GridBagConstraints();
      closeButtonConstraints.gridx = 0;
      closeButtonConstraints.anchor = GridBagConstraints.SOUTH;
      closeButtonConstraints.insets = new Insets(5, 5, 5, 5);
      closeButtonConstraints.gridy = 1;

      GridBagConstraints versionInfoConstraints = new GridBagConstraints();
      versionInfoConstraints.gridx = 0;
      versionInfoConstraints.insets = new Insets(5, 5, 0, 5);
      versionInfoConstraints.weightx = 0.5;
      versionInfoConstraints.weighty = 0.5;
      versionInfoConstraints.fill = GridBagConstraints.BOTH;
      versionInfoConstraints.gridy = 0;

      /* Build a html page with the version info of the JVM and the OS. */
      long maxHeap = Runtime.getRuntime().maxMemory() / (1024 * 1000);
      String s =
          "<html><body><div style=\"text-align: center;\">"
              + System.getProperty("java.vm.name")
              + "<br>"
              + System.getProperty("java.version")
              + "<br>"
              + System.getProperty("java.vm.vendor")
              + "<br>"
              + System.getProperty("os.name")
              + " - "
              + System.getProperty("os.arch")
              + "<br>"
              + "MaxHeap = "
              + maxHeap
              + "Mb"
              + "</div></body></html>";

      versionInfo = new JLabel(s);
      versionInfo.setHorizontalAlignment(SwingConstants.CENTER);

      mainPanel = new JPanel();
      mainPanel.setLayout(new GridBagLayout());
      mainPanel.add(versionInfo, versionInfoConstraints);
      mainPanel.add(getCloseButton(), closeButtonConstraints);
    }
    return mainPanel;
  }
}
