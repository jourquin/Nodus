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
import edu.uclouvain.core.nodus.Nodus;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public class LookAndFeelChooser extends EscapeDialog {

  private static I18n i18n = Environment.getI18n();

  static final long serialVersionUID = 1470446142398349546L;

  /**
   * This method differs from the original UIManager.getInstalledLookAndFeels() in two different
   * ways:
   *
   * <p>- Only the supported L&F are returned; <br>
   * - If a L&F is installed more than once, the associated LookAndFeelInfo is only returned once.
   *
   * @return LookAndFeelInfo[]
   */
  private static UIManager.LookAndFeelInfo[] getInstalledLookAndFeels() {

    HashSet<String> classNames = new HashSet<>();
    LinkedList<LookAndFeelInfo> lafs = new LinkedList<>();
    UIManager.LookAndFeelInfo[] info = UIManager.getInstalledLookAndFeels();

    if (info != null) {
      for (LookAndFeelInfo element : info) {
        if (classNames.contains(element.getClassName())) {
          // work around for lafs that were installed twice
          continue;
        }

        Object supported = null;
        try {
          Class<?> c = ClassLoader.getSystemClassLoader().loadClass(element.getClassName());
          Object laf = c.getDeclaredConstructor().newInstance();

          Method m = c.getMethod("isSupportedLookAndFeel", (Class[]) null);
          supported = m.invoke(laf, (Object[]) null);
        } catch (SecurityException e) {
          e.printStackTrace();
        } catch (IllegalArgumentException e) {
          e.printStackTrace();
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        } catch (InstantiationException e) {
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        } catch (NoSuchMethodException e) {
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          e.printStackTrace();
        }

        if (supported != null && ((Boolean) supported).booleanValue()) {
          lafs.add(element);
          classNames.add(element.getClassName());
        }
      }
    }

    return lafs.toArray(new UIManager.LookAndFeelInfo[0]);
  }

  private JComboBox<String> availableLookAndFeelsComboBox;

  private JButton cancelButton;

  private UIManager.LookAndFeelInfo[] info;

  private String[] lfNames;

  private JPanel mainPanel = new JPanel();

  private NodusMapPanel nodusMapPanel = null;

  private JButton okButton;

  private String currentLookAndFeelName;

  private Properties properties = null;

  private JCheckBox soundCheckBox = null;

  /**
   * Creates a dialog with the available look&feels.
   *
   * @param nodusMapPanel The Nodus map panel.
   */
  public LookAndFeelChooser(NodusMapPanel nodusMapPanel) {
    super(
        nodusMapPanel.getMainFrame(),
        i18n.get(LookAndFeelChooser.class, "Look_and_Feel", "Look and Feel"),
        true);

    currentLookAndFeelName = UIManager.getLookAndFeel().getClass().getName();

    this.nodusMapPanel = nodusMapPanel;
    properties = nodusMapPanel.getNodusProperties();

    initialize();
    getRootPane().setDefaultButton(okButton);
    setLocationRelativeTo(nodusMapPanel);
  }

  /**
   * Get the available PLAF's.
   *
   * @return JComboBox
   */
  private JComboBox<String> getAvailableLookAndFeelsFComboBox() {
    if (availableLookAndFeelsComboBox == null) {
      if (properties != null) {
        // Get current look & feel
        LookAndFeel lf = UIManager.getLookAndFeel();
        String className = lf.getClass().toString();

        /* Remove "class " from the returned string in order just to keep the class name */
        String currentLookAndFeelClassName = className.substring(6, className.length());
        int currentLookAndFeelIndex = 0;

        // Get the available look&feel names
        info = getInstalledLookAndFeels();

        // Locate current index
        lfNames = new String[info.length];

        for (int i = 0; i < info.length; i++) {
          lfNames[i] = info[i].getName();

          if (info[i].getClassName().equals(currentLookAndFeelClassName)) {
            currentLookAndFeelIndex = i;
          }
        }

        ComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<String>(lfNames);
        availableLookAndFeelsComboBox = new JComboBox<String>();
        availableLookAndFeelsComboBox.setModel(comboBoxModel);
        availableLookAndFeelsComboBox.setSelectedIndex(currentLookAndFeelIndex);
      } else {
        ComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<String>();
        availableLookAndFeelsComboBox = new JComboBox<String>();
        availableLookAndFeelsComboBox.setModel(comboBoxModel);
      }
    }

    return availableLookAndFeelsComboBox;
  }

  /**
   * Initializes the "Cancel" button.
   *
   * @return JButton
   */
  private JButton getCancelButton() {
    if (cancelButton == null) {
      cancelButton = new JButton();
      cancelButton.setText(i18n.get(LookAndFeelChooser.class, "Cancel", "Cancel"));
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
   * Initializes the "Ok" button.
   *
   * @return JButton.
   */
  private JButton getOkButton() {
    if (okButton == null) {
      okButton = new JButton();
      okButton.setText(i18n.get(LookAndFeelChooser.class, "Ok", "Ok"));
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

  /**
   * Initializes the "Sound" checkbox.
   *
   * @return JCheckBox
   */
  private JCheckBox getSoundCheckBox() {
    if (soundCheckBox == null) {
      soundCheckBox = new JCheckBox();
      soundCheckBox.setText(i18n.get(LookAndFeelChooser.class, "Sound", "Sound"));
    }
    return soundCheckBox;
  }

  /** Initializes the panel. */
  private void initialize() {

    GridBagConstraints cancelButtonConstraints =
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
            0);
    cancelButtonConstraints.gridy = 3;

    GridBagConstraints okButtonConstraints =
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
            0);
    okButtonConstraints.gridy = 3;

    GridBagConstraints soundButtonConstraints = new GridBagConstraints();
    soundButtonConstraints.gridx = 0;
    soundButtonConstraints.insets = new Insets(5, 5, 5, 5);
    soundButtonConstraints.anchor = GridBagConstraints.WEST;
    soundButtonConstraints.gridy = 2;

    GridBagLayout mainPanelLayout = new GridBagLayout();
    mainPanelLayout.rowWeights = new double[] {0.1, 0.1, 0.1, 0.1};
    mainPanelLayout.rowHeights = new int[] {7, 7, 7, 7};
    mainPanelLayout.columnWeights = new double[] {0.1, 0.1};
    mainPanelLayout.columnWidths = new int[] {7, 7};
    mainPanel.setLayout(mainPanelLayout);
    mainPanel.add(
        getAvailableLookAndFeelsFComboBox(),
        new GridBagConstraints(
            0,
            0,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(10, 5, 5, 5),
            0,
            0));

    mainPanel.add(getOkButton(), okButtonConstraints);
    mainPanel.add(getCancelButton(), cancelButtonConstraints);
    mainPanel.add(getSoundCheckBox(), soundButtonConstraints);
    setContentPane(mainPanel);

    // Sound
    boolean sound = Boolean.parseBoolean(properties.getProperty(NodusC.PROP_SOUND, "true"));
    getSoundCheckBox().setSelected(sound);
    setPreferredSize(new Dimension(300, 150));
    pack();
  }

  /** Processes a press on the "Ok" button. */
  private void okButtonActionPerformed(ActionEvent evt) {

    // Sound
    boolean sound = getSoundCheckBox().isSelected();
    properties.setProperty(NodusC.PROP_SOUND, Boolean.toString(sound));
    nodusMapPanel.getSoundPlayer().enableSound(sound);

    // Which L&F is chosen?
    int n = availableLookAndFeelsComboBox.getSelectedIndex();

    setVisible(false);

    // Save look and feel
    if (n != -1 && !currentLookAndFeelName.equals(info[n].getClassName())) {
      properties.setProperty(NodusC.PROP_LOOK_AND_FEEL, info[n].getClassName());

      Nodus.setLookAndFeel();

      SwingUtilities.updateComponentTreeUI(nodusMapPanel.getMainFrame());
    }
  }
}
