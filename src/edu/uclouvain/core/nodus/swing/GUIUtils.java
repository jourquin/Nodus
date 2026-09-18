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

package edu.uclouvain.core.nodus.swing;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;

/**
 * A few convenient static methods used in Swing related java code.
 *
 * @author Bart Jourquin
 */
public class GUIUtils {

  private static final I18n I18N = Environment.getI18n();

  private static final String MISSING_TOOLTIP = "__NODUS_MISSING_TOOLTIP__";

  /** Default constructor. */
  public GUIUtils() {}

  /**
   * Recompute a new position of a dialog in order to keep it entirely in the screen.
   *
   * @param dialog The JDialog to control.
   */
  public static void keepDialogInScreen(JDialog dialog) {
    // Be sure the dialog is completely visible
    boolean move = false;
    Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
    int x = dialog.getX();
    int y = dialog.getY();

    if (x + dialog.getWidth() > d.width) {
      x = d.width - dialog.getWidth();
      move = true;
    }

    if (y + dialog.getHeight() > d.height) {
      y = d.height - dialog.getHeight();
      move = true;
    }

    if (x < 0) {
      x = 0;
      move = true;
    }

    if (y < 0) {
      y = 0;
      move = true;
    }

    if (move) {
      dialog.setLocation(x, y);
    }
  }

  /**
   * Enables or disables every Swing tooltip in the application.
   *
   * @param enabled true to enable tooltips, false to disable them
   */
  public static void setToolTipsEnabled(boolean enabled) {
    ToolTipManager.sharedInstance().setEnabled(enabled);
  }

  /**
   * Sets one localized tooltip from the owning class's package catalogue.
   *
   * @param component the Swing component to set the tooltip for
   * @param ownerClass the class whose package contains the resource bundle for the tooltip
   * @param key the key for the tooltip in the resource bundle
   * @param defaultValue the default tooltip text to use if the key is not found in the resource
   *     bundle
   */
  public static void setToolTip(
      JComponent component, Class<?> ownerClass, String key, String defaultValue) {
    component.setToolTipText(I18N.get(ownerClass, "tooltip." + key, defaultValue));
  }

  /**
   * Installs the localized Nodus tooltips registered for a window or panel.
   *
   * <p>Components are matched to their Java field names, which keeps the tooltip catalogue in the
   * resource bundles instead of scattering literal text across the GUI classes. Existing bespoke
   * tooltips are preserved unless the catalogue contains an explicit replacement. Text buttons that
   * are local constructor variables receive a localized generic action tooltip.
   *
   * @param owner object that owns the component fields
   * @param root root of the component tree to update
   */
  public static void installToolTips(Object owner, Container root) {
    Map<Component, Field> componentKeys = new IdentityHashMap<>();
    collectComponentKeys(owner, componentKeys);
    installToolTipsRecursively(root, componentKeys);
    installLabelToolTips(componentKeys);
  }

  /** Collects component field names from Nodus-owned objects and their class hierarchy. */
  private static void collectComponentKeys(Object owner, Map<Component, Field> componentKeys) {
    if (owner == null) {
      return;
    }

    Class<?> ownerClass = owner.getClass();
    while (ownerClass != null && ownerClass != Object.class) {
      String className = ownerClass.getName();
      if (className.startsWith("java.") || className.startsWith("javax.")) {
        break;
      }

      for (Field field : ownerClass.getDeclaredFields()) {
        if (!Component.class.isAssignableFrom(field.getType())) {
          continue;
        }

        try {
          field.setAccessible(true);
          Object value = field.get(owner);
          if (value instanceof Component) {
            componentKeys.put((Component) value, field);
          }
        } catch (RuntimeException | IllegalAccessException ignored) {
          // A tooltip must never prevent a window from opening.
        }
      }
      ownerClass = ownerClass.getSuperclass();
    }
  }

  /** Applies registered tooltips to a complete component tree. */
  private static void installToolTipsRecursively(
      Component component, Map<Component, Field> componentKeys) {
    if (component instanceof JComponent) {
      JComponent swingComponent = (JComponent) component;
      Field componentField = componentKeys.get(component);

      if (componentField != null) {
        String tooltip =
            I18N.get(
                componentField.getDeclaringClass(),
                "tooltip." + componentField.getName(),
                MISSING_TOOLTIP);
        if (!MISSING_TOOLTIP.equals(tooltip)) {
          swingComponent.setToolTipText(tooltip);
        }
      }

      if (swingComponent.getToolTipText() == null && component instanceof AbstractButton) {
        String text = ((AbstractButton) component).getText();
        if (text != null && !text.trim().isEmpty()) {
          String template = I18N.get(GUIUtils.class, "Activate_action", "Activate \"{0}\".");
          swingComponent.setToolTipText(MessageFormat.format(template, text.trim()));
        }
      }
    }

    if (component instanceof Container) {
      for (Component child : ((Container) component).getComponents()) {
        installToolTipsRecursively(child, componentKeys);
      }
    }
  }

  /** Gives a field label the same help text as its associated control when their names match. */
  private static void installLabelToolTips(Map<Component, Field> componentKeys) {
    for (Map.Entry<Component, Field> labelEntry : componentKeys.entrySet()) {
      if (!(labelEntry.getKey() instanceof JLabel)) {
        continue;
      }

      JLabel label = (JLabel) labelEntry.getKey();
      if (label.getToolTipText() != null) {
        continue;
      }

      String labelStem = getComponentKeyStem(labelEntry.getValue());
      for (Map.Entry<Component, Field> controlEntry : componentKeys.entrySet()) {
        if (!(controlEntry.getKey() instanceof JComponent)
            || controlEntry.getKey() instanceof JLabel
            || !labelStem.equals(getComponentKeyStem(controlEntry.getValue()))) {
          continue;
        }

        String tooltip = ((JComponent) controlEntry.getKey()).getToolTipText();
        if (tooltip != null) {
          label.setToolTipText(tooltip);
          break;
        }
      }
    }
  }

  /** Returns a normalized component-field key without its Swing type suffix. */
  private static String getComponentKeyStem(Field componentField) {
    String normalized =
        (componentField.getDeclaringClass().getName() + "." + componentField.getName())
            .toLowerCase(Locale.ROOT);
    String[] suffixes = {
      "radiobutton",
      "tabbedpane",
      "combobox",
      "textfield",
      "textarea",
      "checkbox",
      "spinner",
      "button",
      "label",
      "table",
      "list"
    };
    for (String suffix : suffixes) {
      if (normalized.endsWith(suffix)) {
        return normalized.substring(0, normalized.length() - suffix.length());
      }
    }
    return normalized;
  }
}
