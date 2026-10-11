/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Exercises actual Swing option-pane buttons without opening windows. */
@ResourceLock("Locale")
@ResourceLock("UIManager")
class LocaleUtilsTest {
  private Locale originalLocale;
  private Locale originalDisplay;
  private Locale originalFormat;
  private Locale originalComponentLocale;
  private Locale originalUiLocale;
  private LookAndFeel originalLookAndFeel;
  private final Map<String, Object> originalLabels = new HashMap<>();

  @BeforeEach
  void rememberDefaults() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      originalLocale = Locale.getDefault();
      originalDisplay = Locale.getDefault(Locale.Category.DISPLAY);
      originalFormat = Locale.getDefault(Locale.Category.FORMAT);
      originalComponentLocale = JComponent.getDefaultLocale();
      originalUiLocale = UIManager.getDefaults().getDefaultLocale();
      originalLookAndFeel = UIManager.getLookAndFeel();
      for (String key : ResourceBundle.getBundle(
          "edu.uclouvain.core.nodus.utils.SwingMessages", Locale.ENGLISH).keySet()) {
        originalLabels.put(key, UIManager.get(key));
      }
    });
  }

  @AfterEach
  void restoreDefaults() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        UIManager.setLookAndFeel(originalLookAndFeel);
      } catch (UnsupportedLookAndFeelException failure) {
        throw new AssertionError(failure);
      }
      LocaleUtils.applyLocale(originalLocale);
      Locale.setDefault(Locale.Category.DISPLAY, originalDisplay);
      Locale.setDefault(Locale.Category.FORMAT, originalFormat);
      JComponent.setDefaultLocale(originalComponentLocale);
      UIManager.getDefaults().setDefaultLocale(originalUiLocale);
      originalLabels.forEach(UIManager::put);
    });
  }

  @Test
  void standardButtonsFollowFrenchAfterEnglishSwingInitialization() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      LocaleUtils.applyLocale(Locale.ENGLISH);
      assertButtons(JOptionPane.YES_NO_OPTION, "Yes", "No");
      LocaleUtils.applyLocale(Locale.FRANCE);
      assertEquals(Locale.FRANCE, JComponent.getDefaultLocale());
      assertEquals("79", UIManager.getString("OptionPane.yesButtonMnemonic"));
      assertButtons(JOptionPane.YES_NO_OPTION, "Oui", "Non");
      assertButtons(JOptionPane.YES_NO_CANCEL_OPTION, "Oui", "Non", "Annuler");
      assertButtons(JOptionPane.OK_CANCEL_OPTION, "OK", "Annuler");
      assertButtons(JOptionPane.DEFAULT_OPTION, "OK");
      JOptionPane confirmation = pane(JOptionPane.YES_NO_OPTION);
      button(confirmation, "Oui").doClick(0);
      assertEquals(JOptionPane.YES_OPTION, confirmation.getValue());
      button(confirmation, "Non").doClick(0);
      assertEquals(JOptionPane.NO_OPTION, confirmation.getValue());
    });
  }

  @Test
  void regionalFrenchAndSwitchingBackToEnglishKeepCustomOptionsIntact() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      LocaleUtils.applyLocale(Locale.forLanguageTag("fr-BE"));
      assertButtons(JOptionPane.YES_NO_OPTION, "Oui", "Non");
      assertEquals("Saisie", UIManager.getString("OptionPane.inputDialogTitle"));
      JOptionPane custom = new JOptionPane("Scale", JOptionPane.QUESTION_MESSAGE,
          JOptionPane.YES_NO_CANCEL_OPTION, null, new Object[] {"Oui", "Reset", "Annuler"});
      assertEquals(3, buttons(custom).size());
      assertTrue(buttons(custom).stream().anyMatch(b -> b.getText().equals("Reset")));
      assertEquals("Non", UIManager.getString("OptionPane.noButtonText"));
      LocaleUtils.applyLocale(Locale.ENGLISH);
      assertButtons(JOptionPane.YES_NO_CANCEL_OPTION, "Yes", "No", "Cancel");
    });
  }

  @Test
  void localizedButtonsWorkWithEverySupportedInstalledLookAndFeel() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      LocaleUtils.applyLocale(Locale.FRANCE);
      for (UIManager.LookAndFeelInfo lookAndFeel : UIManager.getInstalledLookAndFeels()) {
        try {
          UIManager.setLookAndFeel(lookAndFeel.getClassName());
        } catch (UnsupportedLookAndFeelException unavailable) {
          continue;
        } catch (ReflectiveOperationException failure) {
          throw new AssertionError(failure);
        }
        LocaleUtils.applySwingLocale(Locale.FRANCE);
        assertButtons(JOptionPane.YES_NO_OPTION, "Oui", "Non");
      }
    });
  }

  private static JOptionPane pane(int options) {
    return new JOptionPane("Confirmation", JOptionPane.QUESTION_MESSAGE, options);
  }

  private static void assertButtons(int options, String... labels) {
    List<JButton> actual = buttons(pane(options));
    assertEquals(labels.length, actual.size());
    for (String label : labels) {
      assertTrue(actual.stream().anyMatch(button -> label.equals(button.getText())),
          "Missing localized button " + label + " for " + UIManager.getLookAndFeel().getName());
    }
  }

  private static JButton button(Container parent, String label) {
    return buttons(parent).stream().filter(button -> label.equals(button.getText()))
        .findFirst().orElseThrow();
  }

  private static List<JButton> buttons(Container parent) {
    List<JButton> result = new ArrayList<>();
    for (Component component : parent.getComponents()) {
      if (component instanceof JButton) {
        result.add((JButton) component);
      } else if (component instanceof Container) {
        result.addAll(buttons((Container) component));
      }
    }
    return result;
  }
}
