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

package edu.uclouvain.core.nodus.swing;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * A simple font chooser.
 *
 * @author Bart Jourquin
 */
public class JFontChooser extends JDialog {

  private static final long serialVersionUID = -8712590260466014458L;

  /** i19n mechanism. */
  private static I18n i18n = Environment.getI18n();

  /** Possible sizes for the font. */
  static final String[] availableSizes =
      new String[] {
        "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18",
        "19", "20", "22", "24", "27", "30", "34", "39", "45", "51", "60"
      };

  /** Possible styles for the fonts. */
  static final String[] availableStyles =
      new String[] {
        i18n.get(JFontChooser.class, "Plain", "Plain"),
        i18n.get(JFontChooser.class, "Bold", "Bold"),
        i18n.get(JFontChooser.class, "Italic", "Italic")
      };

  /** Possible names for the fonts. */
  static final String[] availableFontNames =
      GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();

  /** The Swing component that display the font names. */
  private JList<String> fontNameList = new JList<String>(availableFontNames);

  /** The Swing component that display the font styles. */
  private JList<String> fontStyleList = new JList<String>(availableStyles);

  /** The Wwing component that display the font sizes. */
  private JList<String> fontSizeList = new JList<String>(availableSizes);

  /** The Swing component that displays a sample text with the chosen font. */
  static JLabel fontPreview = new JLabel("Abcdefghijklmnopqrstuvwxyz");

  /** Used to detect that the dialog was closed using the OK button. */
  private boolean okPressed = false;

  /**
   * Create the dialog.
   *
   * @param parent The parent component, or null.
   * @param font The initial font to display. If null, a default font will be selected.
   */
  public JFontChooser(Component parent, Font font) {
    JPanel contentPanel = new JPanel();
    setTitle(i18n.get(JFontChooser.class, "FontChooser", "Font chooser"));
    getContentPane().setLayout(new BorderLayout());
    contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
    getContentPane().add(contentPanel, BorderLayout.CENTER);
    GridBagLayout gblContentPanel = new GridBagLayout();
    gblContentPanel.columnWidths = new int[] {0, 0, 0, 0};
    gblContentPanel.rowHeights = new int[] {0, 0, 0, 0};
    gblContentPanel.columnWeights = new double[] {1.0, 1.0, 1.0, Double.MIN_VALUE};
    gblContentPanel.rowWeights = new double[] {0.0, 1.0, 1.0, Double.MIN_VALUE};
    contentPanel.setLayout(gblContentPanel);

    JScrollPane fontNameScrollPane = new JScrollPane();
    fontNameScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    GridBagConstraints gbcFontNameScrollPane = new GridBagConstraints();
    gbcFontNameScrollPane.insets = new Insets(0, 5, 5, 5);
    gbcFontNameScrollPane.fill = GridBagConstraints.BOTH;
    gbcFontNameScrollPane.gridx = 0;
    gbcFontNameScrollPane.gridy = 0;
    contentPanel.add(fontNameScrollPane, gbcFontNameScrollPane);

    fontNameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fontNameScrollPane.setViewportView(fontNameList);
    fontNameList.addListSelectionListener(
        new ListSelectionListener() {
          @Override
          public void valueChanged(ListSelectionEvent e) {
            showSample();
          }
        });

    JScrollPane fontStyleScrollPane = new JScrollPane();
    fontStyleScrollPane.setHorizontalScrollBarPolicy(
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    GridBagConstraints gbcFontStyleScrollPane = new GridBagConstraints();
    gbcFontStyleScrollPane.insets = new Insets(0, 5, 5, 5);
    gbcFontStyleScrollPane.fill = GridBagConstraints.BOTH;
    gbcFontStyleScrollPane.gridx = 1;
    gbcFontStyleScrollPane.gridy = 0;
    contentPanel.add(fontStyleScrollPane, gbcFontStyleScrollPane);

    fontStyleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fontStyleScrollPane.setViewportView(fontStyleList);
    fontStyleList.addListSelectionListener(
        new ListSelectionListener() {
          @Override
          public void valueChanged(ListSelectionEvent e) {
            showSample();
          }
        });

    JScrollPane fontSizeScrollPane = new JScrollPane();
    fontSizeScrollPane.setBackground(Color.WHITE);
    fontSizeScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    GridBagConstraints gbcFontSizeScrollPane = new GridBagConstraints();
    gbcFontSizeScrollPane.insets = new Insets(0, 5, 5, 0);
    gbcFontSizeScrollPane.fill = GridBagConstraints.BOTH;
    gbcFontSizeScrollPane.gridx = 2;
    gbcFontSizeScrollPane.gridy = 0;
    contentPanel.add(fontSizeScrollPane, gbcFontSizeScrollPane);

    fontSizeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fontSizeScrollPane.setViewportView(fontSizeList);
    fontSizeList.addListSelectionListener(
        new ListSelectionListener() {
          @Override
          public void valueChanged(ListSelectionEvent e) {
            showSample();
          }
        });

    JPanel buttonPane = new JPanel();
    getContentPane().add(buttonPane, BorderLayout.SOUTH);
    GridBagLayout gblButtonPane = new GridBagLayout();
    gblButtonPane.columnWidths = new int[] {305, 54, 81, 0};
    gblButtonPane.rowHeights = new int[] {25, 0};
    gblButtonPane.columnWeights = new double[] {0.0, 0.0, 0.0, Double.MIN_VALUE};
    gblButtonPane.rowWeights = new double[] {0.0, Double.MIN_VALUE};
    buttonPane.setLayout(gblButtonPane);

    GridBagConstraints gbcFontPreview = new GridBagConstraints();
    gbcFontPreview.fill = GridBagConstraints.BOTH;
    gbcFontPreview.insets = new Insets(5, 5, 5, 5);
    gbcFontPreview.gridx = 0;
    gbcFontPreview.gridy = 0;
    fontPreview.setHorizontalAlignment(SwingConstants.CENTER);
    fontPreview.setHorizontalTextPosition(SwingConstants.LEADING);
    fontPreview.setPreferredSize(new Dimension(250, 65));
    fontPreview.setBorder(new LineBorder(new Color(0, 0, 0)));
    fontPreview.setOpaque(true);
    fontPreview.setBackground(Color.WHITE);
    buttonPane.add(fontPreview, gbcFontPreview);

    JButton okButton = new JButton("OK");
    okButton.setActionCommand("OK");
    GridBagConstraints gbcOkButton = new GridBagConstraints();
    gbcOkButton.anchor = GridBagConstraints.SOUTH;
    gbcOkButton.insets = new Insets(5, 5, 5, 5);
    gbcOkButton.gridx = 1;
    gbcOkButton.gridy = 0;
    buttonPane.add(okButton, gbcOkButton);
    getRootPane().setDefaultButton(okButton);
    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            setVisible(false);
            okPressed = true;
          }
        });

    JButton cancelButton = new JButton("Cancel");
    cancelButton.setActionCommand("Cancel");
    GridBagConstraints gbcCancelButton = new GridBagConstraints();
    gbcCancelButton.insets = new Insets(5, 5, 5, 10);
    gbcCancelButton.anchor = GridBagConstraints.SOUTH;
    gbcCancelButton.gridx = 2;
    gbcCancelButton.gridy = 0;
    buttonPane.add(cancelButton, gbcCancelButton);
    cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            setVisible(false);
          }
        });

    // Set the current font
    if (font == null) {
      // Get default font
      font = fontPreview.getFont();
    }
    fontNameList.setSelectedValue(font.getName(), true);
    fontSizeList.setSelectedValue("" + font.getSize(), true);
    fontStyleList.setSelectedValue(availableStyles[font.getStyle()], true);

    setModal(true);
    pack();
    setLocationRelativeTo(parent);
  }

  /**
   * Called when a new element is chosen in one of the three Jlists. Displays a sample text with the
   * selected font.
   */
  private void showSample() {
    int fontSize = 0;

    try {
      fontSize = Integer.parseInt(fontSizeList.getSelectedValue());
    } catch (NumberFormatException nfe) {
      return;
    }

    String fontStyle = fontStyleList.getSelectedValue();

    if (fontStyle == null) {
      return;
    }

    int style = Font.PLAIN;

    if (fontStyle.equalsIgnoreCase(i18n.get(JFontChooser.class, "Bold", "Bold"))) {
      style = Font.BOLD;
    }

    if (fontStyle.equalsIgnoreCase(i18n.get(JFontChooser.class, "Italic", "Italic"))) {
      style = Font.ITALIC;
    }

    fontPreview.setFont(new Font(fontNameList.getSelectedValue(), style, fontSize));
  }

  /**
   * Convenient static method that displays the font chooser and returns the selected font.
   *
   * @param parent The parent component.
   * @param font The initial font to select. Can be null.
   * @return The selected font, or null if the dialog was closed using the Cancel button.
   */
  public static Font showDialog(Component parent, Font font) {
    JFontChooser fontChooser = new JFontChooser(parent, font);

    fontChooser.setVisible(true);
    fontChooser.setAlwaysOnTop(true);
    Font newFont = null;

    if (fontChooser.okPressed) {
      newFont = fontPreview.getFont();
    }

    fontChooser.dispose();

    return newFont;
  }
}
