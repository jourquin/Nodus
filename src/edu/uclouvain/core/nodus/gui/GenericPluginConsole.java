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
import edu.uclouvain.core.nodus.NodusPlugin;
import foxtrot.Task;
import foxtrot.Worker;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Simple dialog with start-cancel buttons and a text area that display the progression of the task.
 * Can be used as basic GUI for a Nodus PlugIn
 *
 * @author Bart Jourquin
 */
public class GenericPluginConsole extends JDialog {

  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = 8603049111333400467L;

  private JButton closeButton = new JButton();

  private GridBagLayout gridBagLayout = new GridBagLayout();

  private JScrollPane scrollPane = null;

  private JTextArea textArea = null;

  private Cursor originalCursor;

  private JPanel panel1 = new JPanel();

  private NodusPlugin nodusPlugin;

  private JButton startButton = new JButton();

  /**
   * Creates a simple output console for a Nodus plugin.
   *
   * @param nodusPlugin The Nodus plugin.
   * @param title The title to display.
   */
  public GenericPluginConsole(NodusPlugin nodusPlugin, String title) {
    super(nodusPlugin.getNodusMapPanel().getMainFrame(), title, true);

    this.nodusPlugin = nodusPlugin;

    try {
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      initialize();
      pack();
      setLocationRelativeTo(nodusPlugin.getNodusMapPanel());
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  /** Closes the console. */
  private void closeButton_actionPerformed(ActionEvent e) {
    setVisible(false);
  }

  /**
   * This method initializes scrollPane.
   *
   * @return javax.swing.JScrollPane
   */
  private JScrollPane getJScrollPane() {
    if (scrollPane == null) {
      scrollPane = new JScrollPane();
      scrollPane.setViewportView(getJTextArea());
    }

    return scrollPane;
  }

  /**
   * This method initializes textArea.
   *
   * @return javax.swing.JTextArea
   */
  private JTextArea getJTextArea() {
    if (textArea == null) {
      textArea = new JTextArea();
      textArea.setText(
          i18n.get(
                  GenericPluginConsole.class,
                  "Click_on_Start_to_launch_the_computation_process",
                  "Click on Start to launch the computation process")
              + ".");
    }

    return textArea;
  }

  /**
   * Returns the TextArea the output of the plugin can be written.
   *
   * @return The TextArea of the console.
   */
  public JTextArea getTextArea() {
    return textArea;
  }

  private void initialize() throws Exception {

    panel1.setLayout(gridBagLayout);
    startButton.setText(i18n.get(GenericPluginConsole.class, "Start", "Start"));

    ActionListener startListener =
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            try {
              Worker.post(
                  new Task() {
                    @Override
                    public Object run() throws Exception {
                      nodusPlugin.doStart();
                      return null;
                    }
                  });
            } catch (Exception e1) {
              e1.printStackTrace();
            }
          }
        };

    startButton.addActionListener(startListener);
    closeButton.setActionCommand("closeButton");
    closeButton.setText(i18n.get(GenericPluginConsole.class, "Close", "Close"));

    closeButton.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            closeButton_actionPerformed(e);
          }
        });
    panel1.setPreferredSize(new Dimension(500, 300));

    panel1.add(
        startButton,
        new GridBagConstraints(
            0,
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
    panel1.add(
        closeButton,
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

    GridBagConstraints gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    panel1.add(getJScrollPane(), gridBagConstraints);
    originalCursor = getCursor();

    setContentPane(panel1);
  }

  /**
   * Sets or not the wait cursor.
   *
   * @param busy If true, sets the wait cursor.
   */
  public void setBusy(boolean busy) {
    startButton.setEnabled(!busy);
    closeButton.setEnabled(!busy);

    if (busy) {
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      textArea.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    } else {
      setCursor(originalCursor);
      textArea.setCursor(originalCursor);
    }
  }
}
