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

package com.bbn.openmap.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.layer.drawing.NodusDrawingToolLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusMapPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Iterator;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;

/**
 * This class overrides LayersPanel in order not to allow removing the mandatory layers for a Nodus
 * project. It also adds a button that open an EditLayersDlg, in which the mandatory layers can be
 * edited. Finally, the "drawing tool layers" is also hidden, because drawing is only allowed in the
 * NodusEsri layers.
 *
 * @author Bart Jourquin
 */
public class NodusLayersPanel extends LayersPanel {

  /** Action key used to close a layer palette with the ESC key. */
  private static final String ESCAPE_CLOSE_LAYER_PALETTE_ACTION =
      "edu.uclouvain.core.nodus.closeLayerPalette";

  /** Layer pane that adds an ESC shortcut to layer palettes. */
  private static class EscapePaletteLayerPane extends LayerPane {

    private static final long serialVersionUID = 6959316825376506161L;

    /**
     * Creates a layer pane.
     *
     * @param layer the layer represented by this pane
     * @param layerHandler the layer handler
     * @param bg the layer selection button group
     */
    EscapePaletteLayerPane(Layer layer, LayerHandler layerHandler, ButtonGroup bg) {
      super(layer, layerHandler, bg);
    }

    @Override
    protected void showPalette() {
      super.showPalette();
      installEscapeCloseAction(getLayer(), this);
    }
  }

  /** Layer status pane that adds an ESC shortcut to layer palettes. */
  private static class EscapePaletteLayerStatusPane extends LayerStatusPane {

    private static final long serialVersionUID = -6366212064843239483L;

    /**
     * Creates a layer status pane.
     *
     * @param layer the layer represented by this pane
     * @param layerHandler the layer handler
     * @param bg the layer selection button group
     */
    EscapePaletteLayerStatusPane(Layer layer, LayerHandler layerHandler, ButtonGroup bg) {
      super(layer, layerHandler, bg);
    }

    @Override
    protected void showPalette() {
      super.showPalette();
      installEscapeCloseAction(getLayer(), this);
    }
  }

  private static I18n i18n = Environment.getI18n();

  /** Command string associated to the "edit layer" command. */
  private static final String LAYER_EDIT_CMD = "LayerEditCmd";

  /** Icon to be associated to the "layers edit" command. */
  private ImageIcon layersgif;

  static final long serialVersionUID = -5097425479334326616L;

  /** URL of the "edit layers" icon. */
  private URL urllayers;

  /** "Edit layer" button. */
  private JButton editLayersButton;

  /** Main frame of the application. */
  private NodusMapPanel nodusMapPanel;

  /**
   * Adds a new button and icon to the original LayersPanel GUI.
   *
   * @param mapPanel NodusMapPanel
   */
  public NodusLayersPanel(NodusMapPanel mapPanel) {
    super();

    nodusMapPanel = mapPanel;

    urllayers = getClass().getResource("editlayers.png");
    layersgif = new ImageIcon(urllayers);

    editLayersButton = new JButton(layersgif);
    editLayersButton.setActionCommand(LAYER_EDIT_CMD);
    editLayersButton.setToolTipText(
        i18n.get(NodusLayersPanel.class, "Add_and_remove_layers", "Add and remove layers"));

    editLayersButton.addActionListener(this);

    setControls(createControlButtons());
    getControls().configuration = "NORTH";
    getControls().createInterface();
    getControls().add(editLayersButton);
  }

  /**
   * Creates a layer pane whose palette window can be closed with the ESC key.
   *
   * @param layer the layer represented by this pane
   * @param layerHandler the layer handler
   * @param bg the layer selection button group
   * @return the layer pane
   */
  @Override
  protected LayerPane createLayerPaneForLayer(
      Layer layer, LayerHandler layerHandler, ButtonGroup bg) {
    if (showStatus) {
      return new EscapePaletteLayerStatusPane(layer, layerHandler, bg);
    }

    return new EscapePaletteLayerPane(layer, layerHandler, bg);
  }

  /**
   * Installs an ESC key action on the palette window for a layer.
   *
   * @param layer the layer whose palette is displayed
   * @param layerPane the layer pane that owns the palette button
   */
  private static void installEscapeCloseAction(Layer layer, LayerPane layerPane) {
    if (layer == null) {
      return;
    }

    WindowSupport windowSupport = layer.getWindowSupport();

    if (windowSupport == null) {
      return;
    }

    JRootPane rootPane = getPaletteRootPane(windowSupport);
    Action closeAction =
        new AbstractAction() {
          private static final long serialVersionUID = 3174951125653169808L;

          @Override
          public void actionPerformed(ActionEvent e) {
            windowSupport.killWindow();
            layerPane.setPaletteOn(false);
          }
        };

    if (rootPane != null) {
      rootPane
          .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ESCAPE_CLOSE_LAYER_PALETTE_ACTION);
      rootPane.getActionMap().put(ESCAPE_CLOSE_LAYER_PALETTE_ACTION, closeAction);
      return;
    }

    Component content = windowSupport.getContent();

    if (content instanceof JComponent) {
      JComponent component = (JComponent) content;
      component
          .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ESCAPE_CLOSE_LAYER_PALETTE_ACTION);
      component.getActionMap().put(ESCAPE_CLOSE_LAYER_PALETTE_ACTION, closeAction);
    }
  }

  /**
   * Returns the root pane used by the palette window, if any.
   *
   * @param windowSupport the OpenMap window support
   * @return the root pane, or null if the window does not expose one
   */
  private static JRootPane getPaletteRootPane(WindowSupport windowSupport) {
    Container window = windowSupport.getWindow();

    if (window instanceof RootPaneContainer) {
      return ((RootPaneContainer) window).getRootPane();
    }

    return null;
  }

  /**
   * Intercepts the click on the "edit layers" button.
   *
   * @param e ActionEvent
   */
  @Override
  public void actionPerformed(java.awt.event.ActionEvent e) {
    String command = e.getActionCommand();

    if (command.equals(LAYER_EDIT_CMD)) {
      if (nodusMapPanel.getNodusProject().isOpen()) {
        editLayers();
      }

      return;
    }

    super.actionPerformed(e);
  }

  /**
   * Modified from original tool in order not to display the NodusDrawingToolLayer...
   *
   * @param inLayers Layer[]
   */
  @Override
  public void createPanel(Layer[] inLayers) {

    super.createPanel(inLayers);

    // Now make the NodusDrawingToolLayer invisible
    Iterator<?> it = getPanes().iterator();
    while (it.hasNext()) {
      Object o = it.next();

      if (o instanceof LayerPane) {
        LayerPane lp = (LayerPane) o;

        if (lp.getLayer() instanceof NodusDrawingToolLayer) {
          lp.setVisible(false);
        }
      }
    }

    // Hide remove button
    Component[] c = controls.getComponents();
    for (int i = 0; i < c.length; i++) {
      if (c[i] instanceof JButton) {
        JButton jb = (JButton) c[i];

        if (jb.getActionCommand() == LayersPanel.LayerRemoveCmd) {
          jb.setEnabled(false);
          jb.setVisible(false);
        }
      }
    }

    enableButtons(false);
  }

  /** Opens a new instance of the EditLayers dialog. */
  void editLayers() {
    NodusEditLayersDlg dlg = new NodusEditLayersDlg(nodusMapPanel);
    dlg.setVisible(true);
  }

  /**
   * Enables or not the controls of this layer.
   *
   * @param enable if true, all the controls but the "remove" button are enabled.
   */
  public void enableButtons(boolean enable) {
    Component[] components = getControls().getComponents();
    for (Component o : components) {
      if (o instanceof JButton) {
        JButton jb = (JButton) o;
        jb.setEnabled(enable);
      }
    }
  }

  /**
   * Intercept the LayerSelectedCmd in order to avoid the removing of Nodus core layers. this also
   * links the visibility of labels of a Nodus layer to the visibility of the layer itself.
   */
  @Override
  public void propertyChange(PropertyChangeEvent pce) {

    super.propertyChange(pce);

    String command = pce.getPropertyName();
    Object obj = pce.getNewValue();

    // Intercept the selection commands for a layer
    if (command == LayerSelectedCmd && obj instanceof Layer) {
      // Synchronize the display of the labels with the display of the layer itself
      Layer layer = (Layer) obj;
      if (layer instanceof NodusEsriLayer) {
        NodusEsriLayer nes = (NodusEsriLayer) layer;
        // nes.getLocationHandler().setVisible(!nes.isVisible());
        javax.swing.SwingUtilities.invokeLater(
            new Runnable() {
              public void run() {
                nes.getLocationHandler().setVisible(nes.isVisible());
              }
            });
      }
    }
  }
}
