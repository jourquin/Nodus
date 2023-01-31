/*
 * Copyright (c) 1991-2023 Université catholique de Louvain
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

package com.bbn.openmap.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusMapPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Iterator;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Modified OMControlPanel that fixes the size of the navigation panel to a maximum value, and
 * replaces the standard LayersPanel with a NodusLayersPanel.<br>
 * It also intercepts a double-click on the scale text field. This action allows fixing the minimum
 * scale under which full rendering is performed.
 *
 * @author Bart Jourquin
 */
public class NodusOMControlPanel extends OMControlPanel {

  static final long serialVersionUID = 604132807971966678L;

  private static I18n i18n = Environment.getI18n();

  /** Text field used to enter scale. */
  private JTextField scaleField = null;

  /**
   * The constructor fixes the size of the navigation panel and add listeners to the scale text
   * field.
   *
   * @param nodusMapPanel The NodusMapPanel this control panel belongs to.
   */
  public NodusOMControlPanel(final NodusMapPanel nodusMapPanel) {
    super();

    // fix the size of the navigation panel (it is the first JPanel)
    Object[] c = getComponents();

    for (Object element : c) {
      if (element instanceof JPanel) {
        JPanel p = (JPanel) element;
        p.setMaximumSize(new Dimension(400, 300));
        revalidate();

        // Find the ScaleTextPanel inside this panel
        @SuppressWarnings("unchecked")
        Iterator<Component> it = children.iterator();
        while (it.hasNext()) {
          Component component = it.next();
          if (component instanceof ScaleTextPanel) {
            scaleField = ((ScaleTextPanel) component).scaleField;

            // Intercept a double click
            scaleField.addMouseListener(
                new MouseListener() {

                  @Override
                  public void mouseReleased(MouseEvent e) {
               
                  }

                  @Override
                  public void mousePressed(MouseEvent e) {
                
                  }

                  @Override
                  public void mouseExited(MouseEvent e) {
                
                  }

                  @Override
                  public void mouseEntered(MouseEvent e) {
                
                  }

                  @Override
                  public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                      scaleField.setSelectionEnd(0);

                      // Replace the "No" by "Reset" in the option pane
                      String oldText = (String) UIManager.get("OptionPane.noButtonText");
                      UIManager.put(
                          "OptionPane.noButtonText",
                          i18n.get(NodusOMControlPanel.class, "Reset", "Reset"));

                      // Ask the user if he wants to change the rendering scale threshold
                      int reply =
                          JOptionPane.showConfirmDialog(
                              null,
                              i18n.get(
                                  NodusOMControlPanel.class,
                                  "Set_threshold_to_current_scale",
                                  "Set the rendering threshold to the current scale ?"),
                              i18n.get(
                                  NodusOMControlPanel.class, "Rendering_scale", "Rendering scale"),
                              JOptionPane.YES_NO_CANCEL_OPTION);

                      if (reply == JOptionPane.YES_OPTION) {
                        nodusMapPanel.setRenderingScaleThreshold(
                            nodusMapPanel.getMapBean().getScale());
                      } else if (reply == JOptionPane.NO_OPTION) {
                        // Reset threshold
                        nodusMapPanel.setRenderingScaleThreshold(-1);
                      }

                      // Reset original text in the option pane
                      UIManager.put("OptionPane.noButtonText", oldText);
                    }
                  }
                });

            /*
             * If no rendering threshold is set, the text in the scale field is black.
             * Otherwise, it becomes blue when the current scale is lower or equal
             * than the threshold or red if higher.
             */
            scaleField
                .getDocument()
                .addDocumentListener(
                    new DocumentListener() {
                      public void changedUpdate(DocumentEvent e) {
                        updateColor();
                      }

                      public void removeUpdate(DocumentEvent e) {
                        updateColor();
                      }

                      public void insertUpdate(DocumentEvent e) {
                        updateColor();
                      }

                      public void updateColor() {
                        float renderingThreshold = nodusMapPanel.getRenderingScaleThreshold();
                        if (renderingThreshold == -1) {
                          scaleField.setForeground(Color.BLACK);
                        } else {
                          float scale = nodusMapPanel.getMapBean().getScale();
                          if (scale > renderingThreshold) {
                            scaleField.setForeground(Color.RED);
                          } else {
                            scaleField.setForeground(Color.BLUE);
                          }
                        }
                      }
                    });

            break;
          }
        }

        break;
      }
    }
  }

  /**
   * Returns the overview map handler associated to this layers panel.
   *
   * @return OverviewMapHandler
   */
  public OverviewMapHandler getOverviewMapHandler() {
    OverviewMapHandler omh = null;
    @SuppressWarnings("unchecked")
    Iterator<NodusLayersPanel> it = children.iterator();

    while (it.hasNext()) {
      Object obj = it.next();

      if (obj instanceof OverviewMapHandler) {
        omh = (OverviewMapHandler) obj;
      }
    }

    return omh;
  }

  /**
   * Associated a NodusLayersPanel to the control panel (instead of the native OpenMap layersPanel.
   * See NodusLayersPanel for more details on why this is needed.
   *
   * @param lp NodusLayersPanel
   */
  @SuppressWarnings("unchecked")
  public void setLayersPanel(NodusLayersPanel lp) {
    // Create panel
    NodusLayersPanel layersPanel = lp;
    layersPanel.setPropertyPrefix("LayersPanel");

    // Remove existing layers panel
    Object[] c = getComponents();

    for (int i = 0; i < c.length; i++) {
      if (c[i] instanceof LayersPanel) {
        this.remove(i);
      }
    }

    // Replace it with our NodusLayersPanel
    this.add(layersPanel);

    Iterator<NodusLayersPanel> it = children.iterator();
    int i = 0;

    while (it.hasNext()) {
      Object obj = it.next();

      if (obj instanceof LayersPanel) {
        children.set(i, layersPanel);
      }

      i++;
    }
  }

  /** Can be called to force color rendering of the scale field. */
  public void refreshScale() {
    if (scaleField != null) {
      scaleField.setText(scaleField.getText());
    }
  }
}
