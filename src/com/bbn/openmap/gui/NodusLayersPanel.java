/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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
import com.bbn.openmap.layer.drawing.NodusDrawingToolLayer;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusMapPanel;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.Iterator;
import javax.swing.ImageIcon;
import javax.swing.JButton;

/**
 * This class overrides LayersPanel in order not to allow removing the mandatory layers for a Nodus
 * project. It also adds a button that open an EditLayersDlg, in which the mandatory layers can be
 * edited. Finally, the "drawing tool layers" is also hidden, because drawing is only allowed in the
 * NodusEsri layers.
 *
 * @author Bart Jourquin
 */
public class NodusLayersPanel extends LayersPanel {

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
        nes.getLocationHandler().setVisible(!nes.isVisible());
        /*javax.swing.SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            nes.getLocationHandler().setVisible(nes.isVisible());
          }
        });*/
      }
    }
  }
}
