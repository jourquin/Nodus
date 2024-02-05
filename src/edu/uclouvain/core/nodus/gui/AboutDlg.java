/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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
import com.bbn.openmap.MapBean;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Displays the "About" dialog.
 *
 * @author Bart Jourquin
 */
public class AboutDlg extends EscapeDialog {

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = -6000434231982983552L;

  /** . */
  private JPanel mainPanel = new JPanel();

  /** . */
  private GridBagLayout mainPanelGridBagLayout = new GridBagLayout();

  /** . */
  private JEditorPane nodusInfoHTMLPane = new JEditorPane();

  /** . */
  private JScrollPane nodusInfoScrollPane = new JScrollPane();

  /** . */
  private JButton okButton = new JButton();

  /** . */
  private JButton systemInfoButton = null;

  /**
   * Creates a new "About" box.
   *
   * @param nodusMapPanel The NodusMapPanel.
   */
  public AboutDlg(NodusMapPanel nodusMapPanel) {
    super(nodusMapPanel.getMainFrame(), i18n.get(AboutDlg.class, "About", "About"), true);

    initialize();
    getRootPane().setDefaultButton(okButton);
    requestFocus();
  }

  /**
   * This method initializes systemInfoButton.
   *
   * @return javax.swing.JButton
   */
  private JButton getSystemInfoButton() {
    if (systemInfoButton == null) {
      systemInfoButton = new JButton();
      systemInfoButton.setText(i18n.get(AboutDlg.class, "System_info", "System info"));

      final EscapeDialog _this = this;
      systemInfoButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
              SystemInfoDlg dlg = new SystemInfoDlg(_this);
              dlg.setVisible(true);
            }
          });
    }
    return systemInfoButton;
  }

  /**
   * Called by the constructor. Set up a dialog box and displays the "About" text contained in
   * resource bundle.
   */
  private void initialize() {
    GridBagConstraints okButtonConstraints =
        new GridBagConstraints(
            0,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 0, 10, 0),
            0,
            0);
    okButtonConstraints.gridy = 3;

    GridBagConstraints systemInfoButtonConstraints = new GridBagConstraints();
    systemInfoButtonConstraints.gridx = 0;
    systemInfoButtonConstraints.insets = new Insets(0, 0, 5, 0);
    systemInfoButtonConstraints.gridy = 1;

    mainPanel.setLayout(mainPanelGridBagLayout);

    nodusInfoHTMLPane.setEditable(false);
    nodusInfoHTMLPane.setContentType("text/html");

    String fontFamily = okButton.getFont().getFamily();
    String prefix = "<html><body style=\"font-family: " + fontFamily + "\"<b>";
    String suffix = "</b></html>";
    String imgSrc = getClass().getResource("uclouvain.png").toString();

    String openMapCopyright = MapBean.getCopyrightMessage();
    openMapCopyright = openMapCopyright.replaceAll("\r\n", "<br>");

    String content =
        prefix
            + "<div align=\"center\"><font color=\"#000099\"><br>"
            + "<strong><big><big><big><big>"
            + NodusC.APPNAME
            + "</big></big></big></big></strong></font><br>"
            + "<br>"
            + "Nodus is a trademark of UCLouvain<br>"
            + "<br>"
            + "<strong>"
            + NodusC.COPYRIGHT
            + "</strong><strong><br>"
            + "</strong><strong>Center for Operations Research and Econometrics (CORE)<br>"
            + "</strong><strong> http://www.uclouvain.be/core</strong><br>"
            + "<img src=\""
            + imgSrc
            + "\" width=\"250\" height=\"125\">"
            + "<br>"
            + openMapCopyright
            + "<br></div>"
            + suffix;

    nodusInfoHTMLPane.setText(content);
    nodusInfoHTMLPane.setOpaque(true);

    okButton.setText(i18n.get(AboutDlg.class, "Ok", "Ok"));
    okButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            okButton_actionPerformed(e);
          }
        });

    nodusInfoScrollPane.setViewportView(nodusInfoHTMLPane);
    nodusInfoScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    nodusInfoScrollPane.setViewportView(nodusInfoHTMLPane);

    setResizable(false);
    mainPanel.add(
        nodusInfoScrollPane,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.05,
            0.05,
            GridBagConstraints.NORTH,
            GridBagConstraints.HORIZONTAL,
            new Insets(10, 10, 10, 10),
            0,
            0));
    mainPanel.add(okButton, okButtonConstraints);

    mainPanel.add(getSystemInfoButton(), systemInfoButtonConstraints);

    setContentPane(mainPanel);
    pack();

    // Give a little more space around the text
    int width = getWidth();
    int height = getHeight();
    setSize(width + 20, height + 20);
  }

  /**
   * Close "About" dialog.
   *
   * @param e ActionEvent
   */
  private void okButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }
}
