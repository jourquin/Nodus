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
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;
import java.util.Properties;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/**
 * Simple dialog that displays the available languages and permits to choose one for the GUI.
 *
 * @author Bart Jourquin
 */
public class LanguageChooser extends EscapeDialog {

  /** Handles combo items that contain an icon and a label. */
  private class ComboBoxRenderer extends JLabel implements ListCellRenderer<Object> {

    private static final long serialVersionUID = -54925652348730259L;

    /** Font to use if no icon was found. */
    private Font noIconFont;

    /** Constructor. */
    public ComboBoxRenderer() {
      setOpaque(true);
      setHorizontalAlignment(LEFT);
      setVerticalAlignment(CENTER);
    }

    /**
     * This method finds the image and text corresponding to the selected value and returns the
     * label, set up to display the text and image.
     */
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      // Get the selected index. (The index param isn't always valid, so
      // just use the value.)
      int selectedIndex = ((Integer) value).intValue();

      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      } else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }

      // Set the icon and text. If icon was null, say so.
      ImageIcon icon = images[selectedIndex];
      String language = languageNames[selectedIndex];
      setIcon(icon);

      if (icon != null) {
        setText(" " + language);
        setFont(list.getFont());
      } else {
        setNoIconText(language + " (?) ", list.getFont());
      }

      return this;
    }

    // Set the font and text when no image was found.
    protected void setNoIconText(String noIconText, Font normalFont) {
      if (noIconFont == null) {
        noIconFont = normalFont.deriveFont(Font.ITALIC);
      }

      setFont(noIconFont);
      setText(noIconText);
    }
  }

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 6238550975227060565L;

  /**
   * Available locales. Add more if needed. Also add a .png with the relevant flag in the directory.
   */
  private String[] availableLocales = {"en", "fr"};

  /** Cancel button. */
  private JButton cancelButton;

  /** Index of current language in combo. */
  private int currentLanguageIndex;

  /** Array of images that will contain the flags. */
  private ImageIcon[] images;

  /** Label. */
  private JLabel label;

  /** Will contain the localized language names. */
  private String[] languageNames;

  /** Combo with the language names and flags. */
  private JComboBox<?> languagesCombo;

  private JPanel mainPanel = new JPanel();

  private NodusMapPanel nodusMapPanel;

  /** OK button. */
  private JButton okButton;

  /** Properties to retrieve/store the language info. */
  private Properties properties = null;

  /**
   * Creates the dialog.
   *
   * @param nodusMapPanel The Nodus main panel.
   */
  public LanguageChooser(NodusMapPanel nodusMapPanel) {
    super(
        nodusMapPanel.getMainFrame(),
        i18n.get(LanguageChooser.class, "Language_chooser", "Language chooser"),
        true);

    this.nodusMapPanel = nodusMapPanel;

    // Get the Nodus properties
    properties = nodusMapPanel.getNodusProperties();

    // Set-up GUI and connect to help system
    initialize();
    getRootPane().setDefaultButton(okButton);
    setLocationRelativeTo(nodusMapPanel);
  }

  /**
   * The cancel button and its action listener.
   *
   * @return The button
   */
  private JButton getCancelButton() {
    if (cancelButton == null) {
      cancelButton = new JButton();
      cancelButton.setText(i18n.get(LanguageChooser.class, "Cancel", "Cancel"));
      cancelButton.addActionListener(
          new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
              setVisible(false);
            }
          });
    }

    return cancelButton;
  }

  /**
   * Just a label with an info text.
   *
   * @return The label
   */
  private JLabel getJLabel1() {
    if (label == null) {
      label = new JLabel();
      label.setText(
          i18n.get(
              LanguageChooser.class,
              "Please_select_the_language_to_use",
              "Please select the language to use"));
    }

    return label;
  }

  /**
   * The combo and its initialisation.
   *
   * @return The combo
   */
  private JComboBox<?> getLanguagesCombo() {
    if (languagesCombo == null) {

      if (properties == null) {
        languagesCombo = new JComboBox<Object>();
      } else {

        // Get official language name
        int l = availableLocales.length;
        languageNames = new String[l];

        for (int i = 0; i < l; i++) {
          Locale locale =
              new Locale(availableLocales[i].toLowerCase(), availableLocales[i].toUpperCase());
          languageNames[i] = locale.getDisplayLanguage();

          if (Locale.getDefault().getDisplayLanguage().equals(languageNames[i])) {
            currentLanguageIndex = i;
          }
        }

        // Load the flags
        images = new ImageIcon[l];

        Integer[] intArray = new Integer[l];

        for (int i = 0; i < l; i++) {
          intArray[i] = Integer.valueOf(i);

          images[i] =
              new ImageIcon(getClass().getResource("flags/" + availableLocales[i] + ".png"));

          if (images[i] != null) {
            images[i].setDescription(languageNames[i]);
          }
        }

        // Create and fill the combo
        languagesCombo = new JComboBox<Object>(intArray);

        // Special rendener that handles the flag icon and the
        // associated language name
        ComboBoxRenderer renderer = new ComboBoxRenderer();
        languagesCombo.setRenderer(renderer);
        languagesCombo.setSelectedIndex(currentLanguageIndex);
      }
    }

    return languagesCombo;
  }

  /**
   * The ok button initialisation.
   *
   * @return The button
   */
  private JButton getOkButton() {
    if (okButton == null) {
      okButton = new JButton();
      okButton.setText(i18n.get(LanguageChooser.class, "Ok", "Ok"));
      okButton.addActionListener(
          new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
              okButtonActionPerformed(evt);
            }
          });
    }

    return okButton;
  }

  /** Create and initializes the components of the dialog. */
  private void initialize() {

    GridBagLayout mainPanelLayout = new GridBagLayout();
    mainPanelLayout.rowWeights = new double[] {0.1, 0.1, 0.1, 0.1};
    mainPanelLayout.rowHeights = new int[] {7, 7, 7, 7};
    mainPanelLayout.columnWeights = new double[] {0.1, 0.1};
    mainPanelLayout.columnWidths = new int[] {7, 7};
    mainPanel.setLayout(mainPanelLayout);
    mainPanel.add(
        getLanguagesCombo(),
        new GridBagConstraints(
            0,
            1,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(5, 5, 5, 5),
            0,
            0));
    mainPanel.add(
        getOkButton(),
        new GridBagConstraints(
            0,
            2,
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
        getCancelButton(),
        new GridBagConstraints(
            1,
            2,
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
        getJLabel1(),
        new GridBagConstraints(
            0,
            0,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 0, 5),
            0,
            0));
    setContentPane(mainPanel);
    pack();
  }

  /**
   * Handles the OK button action. Stores the new language in the properties file.
   *
   * @param evt Intercepted event
   */
  private void okButtonActionPerformed(ActionEvent evt) {
    int selectedIndex = languagesCombo.getSelectedIndex();

    // Which item is chosen?
    if (selectedIndex != -1) {
      if (selectedIndex != currentLanguageIndex) {
        setVisible(false);
        JOptionPane.showMessageDialog(
            nodusMapPanel,
            i18n.get(
                LanguageChooser.class,
                "New_language_will_be_applied_at_next_restart",
                "New language will be applied at next restart"),
            NodusC.APPNAME,
            JOptionPane.INFORMATION_MESSAGE);
        properties.setProperty(NodusC.PROP_LOCALE, availableLocales[selectedIndex]);
      }
    }
  }
}
