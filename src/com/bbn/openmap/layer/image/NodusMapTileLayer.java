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

package com.bbn.openmap.layer.image;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.mapTile.ServerMapTileFactory;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.utils.FileUtils;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * TileMapLayer that creates a cache dir in the system tmp dir, stores the transparency level in the
 * props and allows the user to clear the memory and physical cache.
 *
 * @author Bart Jourquin
 */
public class NodusMapTileLayer extends MapTileLayer {

  static final String CACHE_DIR_PROPERTY = "cacheDirName";

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = 2145564724102637208L;

  // Default cache dir name
  private String cacheDirName = "TilesCache";

  private JPanel clearCachePanel = null;

  // Is the server reachable ?
  protected boolean isServerReachable;

  private String prefix;

  private Properties props;

  private JPanel transparencyPanel = null;

  /** Tells the factory to clean up resources, including the physical cache. */
  @Override
  public void clearCache() {
    getTileFactory().reset();

    // Delete physical cache
    String key =
        PropUtils.getScopedPropertyPrefix(prefix)
            + ServerMapTileFactory.LOCAL_CACHE_ROOT_DIR_PROPERTY;
    String path = props.getProperty(key);
    File directory = new File(path);
    if (directory.exists()) {
      try {
        FileUtils.deleteFile(directory);
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * A GUI to clear the memory and physical cache manually.
   *
   * @return JPanel
   */
  JPanel getClearCachePanel() {

    if (clearCachePanel == null) {
      clearCachePanel = new JPanel();
      JButton clearButton =
          new JButton(i18n.get(NodusMapTileLayer.class, "clearCacheLabel", "Clear cache"));
      clearCachePanel.add(clearButton);
      clearButton.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              clearCache();
            }
          });
    }
    return clearCachePanel;
  }

  /**
   * Returns a GUI that makes it possible to set the transparency level and to clear the memory and
   * disk caches.
   */
  @Override
  public java.awt.Component getGUI() {

    if (!isServerReachable) {
      return null;
    }

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    panel.add(getTransparencyPanel());
    panel.add(getClearCachePanel());

    return panel;
  }

  /**
   * Returns the prefix used for the layer in the properties.
   *
   * @return String
   */
  public String getPrefix() {
    return prefix;
  }

  /**
   * Intercept the slider of the original GUI in order to save its current value in the props.
   *
   * @return JPanel
   */
  private JPanel getTransparencyPanel() {

    if (transparencyPanel == null) {
      JPanel gui = (JPanel) super.getGUI();

      // The transparency panel is the first panel in the gui
      for (int i = 0; i < gui.getComponentCount(); i++) {
        if (gui.getComponent(i).getClass() == JPanel.class) {
          transparencyPanel = (JPanel) gui.getComponent(i);
          break;
        }
      }

      int n = transparencyPanel.getComponentCount();
      for (int i = 0; i < n; i++) {
        Component obj = transparencyPanel.getComponent(i);
        if (obj.getClass() == JSlider.class) {
          JSlider transparencySlider = (JSlider) obj;
          transparencySlider.addChangeListener(
              new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                  JSlider s = (JSlider) e.getSource();
                  // Compute the transparency value
                  float value = (float) s.getValue() / (float) (s.getMaximum() - s.getMinimum());
                  String key =
                      PropUtils.getScopedPropertyPrefix(prefix)
                          + OMGraphicHandlerLayer.TransparencyProperty;
                  props.setProperty(key, Float.toString(value));
                }
              });
          break;
        }
      }
    }

    return transparencyPanel;
  }

  /** Call prepare only if the server is reachable. */
  @Override
  public synchronized OMGraphicList prepare() {
    if (isServerReachable) {
      return super.prepare();
    } else {
      return null;
    }
  }

  /**
   * Child layers can choose an alternative subdir name to store tiles. This allows to use several
   * map tile based layers to be displayed at the same time without "mixing" the physical caches.
   *
   * @param cacheDirName Name of the cache dir
   */
  public void setCacheDirName(String cacheDirName) {
    this.cacheDirName = cacheDirName;
  }

  /**
   * Sets the layer prefix used in the properties.
   *
   * @param prefix Prefix used in the properties
   */
  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  /** Specify the cache dir name. */
  @Override
  public void setProperties(String prefix, Properties props) {

    // Avoid display of tile info in each tile.
    Logger.getLogger("MAPTILE_DEBUGGING").setLevel(Level.OFF);

    this.props = props;
    this.prefix = prefix;

    // Use alternative cache dir name if specified
    String key =
        props.getProperty(PropUtils.getScopedPropertyPrefix(prefix) + CACHE_DIR_PROPERTY, null);
    if (key != null) {
      cacheDirName = key;
    }

    // Create the tiles cache in the system temporary directory
    key =
        PropUtils.getScopedPropertyPrefix(prefix)
            + ServerMapTileFactory.LOCAL_CACHE_ROOT_DIR_PROPERTY;
    String path = System.getProperty("java.io.tmpdir") + "/" + cacheDirName;
    props.setProperty(key, path);

    // Test if server is reachable
    String url =
        props.getProperty(
            PropUtils.getScopedPropertyPrefix(prefix) + ServerMapTileFactory.ROOT_DIR_PROPERTY,
            null);
    if (url == null) {
      isServerReachable = false;
    } else {
      isServerReachable = NetUtils.pingHost(url);
    }

    super.setProperties(prefix, props);

    // Just to avoid ugly java message during reprojections
    setInterruptable(false);
  }
}
