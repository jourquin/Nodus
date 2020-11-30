/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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

package com.bbn.openmap.layer.image;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.mapTile.GoogleMapTileFactory;
import com.bbn.openmap.dataAccess.mapTile.ServerMapTileFactory;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import java.awt.event.ActionEvent;
import java.util.Properties;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.TitledBorder;

/**
 * Simplified layer to use Google maps. Includes a GUI component to choose the Map Type.
 *
 * @author Bart Jourquin
 */
public class NodusGoogleTileLayer extends NodusMapTileLayer {

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 2865864512036579668L;

  /**
   * Map types are stored in two arrays. One used for display (can be translated),
   * the other containing the type names as used by the Google API.
   */
  private final String[] displayMapType = {
    i18n.get(NodusGoogleTileLayer.class, "Roadmap", "Roadmap"),
    i18n.get(NodusGoogleTileLayer.class, "Terrain", "Terrain"),
    i18n.get(NodusGoogleTileLayer.class, "Satellite", "Satellite"),
    i18n.get(NodusGoogleTileLayer.class, "Hybrid", "Hybrid")
  };

  /**
   * Map types are stored in two arrays. One used for display (can be translated),
   * the other containing the type names as used by the Google API.
   */
  private final String[] googleMapType = {"roadmap", "terrain", "satellite", "hybrid"};

  /** . */
  private JPanel mapTypesPanel = null;
  
  /** . */
  private Properties props;
  
  /** . */
  private JRadioButton[] typeRadioButtons;

  /** New GUI with the possibility to choose the map type to display. */
  @Override
  public java.awt.Component getGUI() {
    if (!isServerReachable) {
      return null;
    }
    JPanel panel = (JPanel) super.getGUI();
    panel.add(getMapTypesPanel());
    return panel;
  }

  /** Returns a panel containing a RadiBbox group with the possible map types. */
  private JPanel getMapTypesPanel() {

    if (mapTypesPanel == null) {
      mapTypesPanel = new JPanel();
      ButtonGroup group = new ButtonGroup();
      mapTypesPanel.setBorder(
          new TitledBorder(i18n.get(NodusGoogleTileLayer.class, "Map_type", "Map type")));
      typeRadioButtons = new JRadioButton[displayMapType.length];
      for (int i = 0; i < displayMapType.length; i++) {
        typeRadioButtons[i] = new JRadioButton(displayMapType[i]);
        typeRadioButtons[i].addActionListener(
            new java.awt.event.ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                radioButton_actionPerformed(e);
              }
            });
        mapTypesPanel.add(typeRadioButtons[i]);
        group.add(typeRadioButtons[i]);
      }
    }

    // Select the saved map type
    String key =
        PropUtils.getScopedPropertyPrefix(getPropertyPrefix())
            + GoogleMapTileFactory.MAPTYPE_PROPERTY;
    String mapType = props.getProperty(key, googleMapType[0]);
    for (byte i = 0; i < googleMapType.length; i++) {
      if (mapType.equals(googleMapType[i])) {
        typeRadioButtons[i].setSelected(true);
        break;
      }
    }

    return mapTypesPanel;
  }

  /**
   * Refresh the layer with a Google map of the chosen type.
   *
   * @param e Action event
   */
  private void radioButton_actionPerformed(ActionEvent e) {
    JRadioButton r = (JRadioButton) e.getSource();
    String type = r.getText();
    byte index;
    for (index = 0; index < displayMapType.length; index++) {
      if (type.equals(displayMapType[index])) {
        break;
      }
    }

    // Save the map type
    String key =
        PropUtils.getScopedPropertyPrefix(getPropertyPrefix())
            + GoogleMapTileFactory.MAPTYPE_PROPERTY;
    props.setProperty(key, googleMapType[index]);

    // Update layer
    ((GoogleMapTileFactory) getTileFactory()).setMapType(googleMapType[index]);
    clearCache();
    doPrepare();
  }

  /**
   * Specify a cache directory in the system tmp directory and associate a GoogleMapTileFactory to
   * the layer.
   */
  @Override
  public void setProperties(String prefix, Properties props) {

    this.props = props;
    setPrefix(prefix);

    // Set the tile factory
    String key =
        PropUtils.getScopedPropertyPrefix(prefix) + MapTileLayer.TILE_FACTORY_CLASS_PROPERTY;
    props.setProperty(key, "com.bbn.openmap.dataAccess.mapTile.GoogleMapTileFactory");

    // Set the server URL to test if reachable
    String url = PropUtils.getScopedPropertyPrefix(prefix) + ServerMapTileFactory.ROOT_DIR_PROPERTY;
    props.setProperty(url, "http://maps.googleapis.com/maps/api/staticmap?");

    // Set the cache name
    setCacheDirName("GoogleTilesCache");

    super.setProperties(prefix, props);
  }
}
