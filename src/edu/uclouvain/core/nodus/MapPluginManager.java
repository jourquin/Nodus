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

package edu.uclouvain.core.nodus;

import edu.uclouvain.core.nodus.utils.PluginsLoader;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Vector;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

/** Owns plugin instances, menus and class loaders for one map panel. */
final class MapPluginManager {
  /** Vector of global plugins. */
  private Vector<JMenuItem> globalPluginsMenuItems = new Vector<>();
  /** Class loaders that must stay alive for application-wide plugins. */
  private Vector<PluginsLoader> globalPluginLoaders = new Vector<>();
  /** Class loaders that must stay alive for project-specific plugins. */
  private Vector<PluginsLoader> projectPluginLoaders = new Vector<>();
  /** Number of Nodus wide plugins. */
  private int nbNodusPlugins = 0;
  /** Array of plugins. */
  private NodusPlugin[] nodusPlugins;
  /** Vector of project specific plugins menu items. */
  private Vector<JMenuItem> projectPluginsMenuItems = new Vector<>();
  /** "User defined menus (used by plugins). */
  private Vector<JMenuItem> userDefinedMenus = new Vector<>();

  private final NodusMapPanel panel;
  private final JMenuBar nodusMenuBar;
  private final JMenu menuFile;
  private final JMenu menuProject;
  private final JMenu menuControl;
  private final JMenu menuTools;
  private final JMenu menuHelp;

  MapPluginManager(
      NodusMapPanel panel,
      JMenuBar menuBar,
      JMenu file,
      JMenu project,
      JMenu control,
      JMenu tools,
      JMenu help) {
    this.panel = panel;
    nodusMenuBar = menuBar;
    menuFile = file;
    menuProject = project;
    menuControl = control;
    menuTools = tools;
    menuHelp = help;
  }

  /** Updates plugin menus on the same event-thread turn as the built-in menus. */
  void enableMenus(boolean state) {
    for (JMenuItem item : projectPluginsMenuItems) {
      item.setEnabled(state);
    }
    for (JMenuItem menu : userDefinedMenus) {
      menu.setEnabled(state);
      menu.setVisible(state);
    }
    for (JMenuItem item : globalPluginsMenuItems) {
      item.setEnabled(true);
    }
  }

  /**
   * Creates a new menu item at the right place for the plugin.
   *
   * @param plugin The plugin for which a menu item must be created
   * @param commandId The command ID to associate to this plugin
   * @param menu The menu to which the item must be added to
   * @param pluginProp The properties associated to the plugin
   * @return The created menu item
   */
  private JMenuItem createPluginMenuItem(
      NodusPlugin plugin, int commandId, JMenu menu, Properties pluginProp) {
    JMenuItem menuItem = null;
    String text;
    text = pluginProp.getProperty(NodusPlugin.MENU_ITEM__TEXT);

    if (text != null) {
      menuItem = new JMenuItem(text);

      // Enable the menu?
      boolean enable = true;
      text = pluginProp.getProperty(NodusPlugin.IS_ENABLED);

      if (text != null) {
        if (text.equalsIgnoreCase(NodusPlugin.FALSE)) {
          enable = false;
        }
      }

      menuItem.setEnabled(enable);

      menuItem.setActionCommand(Integer.toString(commandId));
      menuItem.addActionListener(
          new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              pluginMenuActionPerformed(e);
            }
          });
    } else {
      System.err.println(
          "Plugin " + plugin.getClass().toString() + " doesn't define a MenuItemText");
    }

    // Now add the menu item at the right place
    if (menu != null && menuItem != null) {
      text = pluginProp.getProperty(NodusPlugin.MENU_ITEM_ID);

      int n = -1;

      if (text != null) {
        n = Integer.parseInt(text);
      }

      if (n < -1) {
        System.err.println(
            "Plugin " + plugin.getClass().toString() + " returns invalid menuItemID");
      } else {
        if (n == -1) {
          // Add item just before last separator, if any
          int lastSeparator = -1;

          for (int j = 0; j < menu.getItemCount(); j++) {
            Component c = menu.getMenuComponent(j);

            if (c instanceof JSeparator) {
              lastSeparator = j;
            }
          }

          if (lastSeparator == -1) {
            // Just append to menu
            menu.add(menuItem);
          } else {
            // Insert before separator
            menu.insert(menuItem, lastSeparator);
          }
        } else {
          // Insert at the given place
          menu.insert(menuItem, n);
        }
      }
    }

    return menuItem;
  }

  /**
   * Returns the standard menu to which the plugin must be added to..
   *
   * @param plugin The plugin to add.
   * @param menuId The menu number.
   * @return The JMenu that gies access to the plugins.
   */
  private JMenu getPluginMenu(NodusPlugin plugin, int menuId) {
    JMenu menu = null;

    switch (menuId) {
      case NodusPlugin.MENU_FILE:
        menu = menuFile;

        break;

      case NodusPlugin.MENU_PROJECT:
        menu = menuProject;

        break;

      case NodusPlugin.MENU_CONTROL:
        menu = menuControl;

        break;

      case NodusPlugin.MENU_TOOLS:
        menu = menuTools;

        break;

      case NodusPlugin.MENU_HELP:
        menu = menuHelp;

        break;

      default:
        System.err.println(
            "Plugin " + plugin.getClass().toString() + " returns undefined MenuBarID");
    }

    return menu;
  }

  /**
   * Loads all the plugins which jar is stored in the given directory and creates the relevant
   * "plugins" menu items. The boolean parameter is used to qualify the nature of the plugin: when
   * set to true, the plugins are considered to be relevant only for the loaded project, and the
   * related menu items will be removed when the project will be closed.
   *
   * @param dir Place where the plugin is located
   * @param projectPlugin True if plugin a project specific. False for global plugins.
   */
  void loadPlugins(String dir, boolean projectPlugin) {

    // Load all the plugins.
    PluginsLoader nodusPluginLoader = new PluginsLoader(dir);
    boolean keepLoader = false;

    try {
      LinkedList<Class<NodusPlugin>> availableClasses = nodusPluginLoader.getAvailablePlugins();

      if (availableClasses.isEmpty()) {
        if (!projectPlugin) {
          nbNodusPlugins = 0;
        }
        return;
      }

      Vector<NodusPlugin> loadedPlugins = new Vector<>();

      Iterator<Class<NodusPlugin>> classIterator = availableClasses.iterator();
      while (classIterator.hasNext()) {
        Class<NodusPlugin> loadableClass = classIterator.next();
        NodusPlugin plugin = null;

        try {
          plugin = loadableClass.getConstructor().newInstance();
          plugin.setNodusMapPanel(panel);
        } catch (Exception e) {
          e.printStackTrace();
          continue;
        }

        Properties pluginProp = plugin.getProperties();
        if (pluginProp == null) {
          System.err.println("Plugin " + plugin.getClass().toString() + " returns no properties");
          disposePlugin(plugin);
          continue;
        }

        // Where must the plugin be inserted?
        JMenu menu = null;
        String text = pluginProp.getProperty(NodusPlugin.USER_DEFINED_MENUBAR_TEXT);

        if (text != null) {
          // Does this user-defined menu already exists?
          boolean found = false;
          Iterator<JMenuItem> it = userDefinedMenus.iterator();

          while (it.hasNext()) {
            JMenu m = (JMenu) it.next();

            if (m.getText().equals(text)) {
              found = true;
              menu = m;

              break;
            }
          }

          // No. Add it, just before the "help" menu!
          if (!found) {
            menu = new JMenu(text);
            userDefinedMenus.add(menu);
            nodusMenuBar.add(menu, nodusMenuBar.getMenuCount() - 1);
            menu.setEnabled(false);
          }
        } else {
          // Menu item must be added to a Nodus menu
          text = pluginProp.getProperty(NodusPlugin.MENUBAR_ID);

          if (text != null) {
            int n = Integer.parseInt(text);
            menu = getPluginMenu(plugin, n);
          } else {
            System.err.println(
                "Plugin "
                    + plugin.getClass().toString()
                    + " doesn't define a MenuBarID "
                    + "or a UserDefinedMenubarText");
          }
        }

        // Create the relevant menu item and associate an actionCommand to it.
        int commandId = loadedPlugins.size();

        if (projectPlugin) {
          commandId += nbNodusPlugins;
        }

        JMenuItem menuItem = createPluginMenuItem(plugin, commandId, menu, pluginProp);

        if (menuItem == null) {
          disposePlugin(plugin);
          removeEmptyUserDefinedPluginMenus();
          continue;
        }

        // Store the menu in a vector in order to enable/disable/remove it easily.
        if (!projectPlugin) {
          globalPluginsMenuItems.add(menuItem);
        } else {
          projectPluginsMenuItems.add(menuItem);
        }

        loadedPlugins.add(plugin);
      }

      if (loadedPlugins.isEmpty()) {
        if (!projectPlugin) {
          nbNodusPlugins = 0;
        }
        return;
      }

      NodusPlugin[] plugins = loadedPlugins.toArray(new NodusPlugin[loadedPlugins.size()]);

      // Add project plugins after global plugins. They will be removed again on project close.
      if (!projectPlugin) {
        nodusPlugins = plugins;
        nbNodusPlugins = plugins.length;
      } else if (nodusPlugins == null) {
        nodusPlugins = plugins;
      } else {
        NodusPlugin[] tmp = nodusPlugins.clone();
        int newSize = nodusPlugins.length + plugins.length;
        nodusPlugins = new NodusPlugin[newSize];

        for (int i = 0; i < tmp.length; i++) {
          nodusPlugins[i] = tmp[i];
        }

        for (int i = 0; i < plugins.length; i++) {
          nodusPlugins[tmp.length + i] = plugins[i];
        }
      }

      /*
       * Keep the loader alive while the plugin instances loaded from it are alive.
       * Application-wide plugin loaders are closed at Nodus exit.
       * Project-specific plugin loaders are closed when the project is closed.
       */
      if (!projectPlugin) {
        globalPluginLoaders.add(nodusPluginLoader);
      } else {
        projectPluginLoaders.add(nodusPluginLoader);
      }

      keepLoader = true;
    } finally {
      if (!keepLoader) {
        nodusPluginLoader.close();
      }
    }
  }

  /**
   * Handles the call to the relevant plugin's "1execute" command.
   *
   * @param e ActionEvent
   */
  private void pluginMenuActionPerformed(ActionEvent e) {
    int n = Integer.parseInt(e.getActionCommand());

    if (nodusPlugins == null || n < 0 || n >= nodusPlugins.length || nodusPlugins[n] == null) {
      return;
    }

    nodusPlugins[n].execute();
  }

  /**
   * Removes project plugin menu items and disposes the associated project plugin instances.
   *
   * <p>This method is called when a project is closed. It gives project plugins a deterministic
   * cleanup point for listeners, timers, threads, windows, and other resources.
   */
  void removeProjectPlugins() {
    // Remove all project plugin menu items and detach their listeners.
    Iterator<JMenuItem> it = projectPluginsMenuItems.iterator();

    while (it.hasNext()) {
      JMenuItem searchedMenuItem = it.next();

      if (searchedMenuItem == null) {
        continue;
      }

      removeActionListeners(searchedMenuItem);
      removePluginMenuItem(searchedMenuItem);
    }

    projectPluginsMenuItems.clear();

    removeProjectPluginInstances();

    /*
     * Project plugin instances have now been disposed. Their project-scoped
     * class loaders can be closed and released.
     */
    closePluginLoaders(projectPluginLoaders);

    removeEmptyUserDefinedPluginMenus();
  }

  /** Closes all plugin loaders owned by the given lifecycle scope. */
  private void closePluginLoaders(Vector<PluginsLoader> loaders) {
    Iterator<PluginsLoader> it = loaders.iterator();

    while (it.hasNext()) {
      PluginsLoader loader = it.next();

      if (loader == null) {
        continue;
      }

      try {
        loader.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    loaders.clear();
  }

  /** Calls the plugin lifecycle cleanup method before the plugin instance is released. */
  private void disposePlugin(NodusPlugin plugin) {
    if (plugin == null) {
      return;
    }

    try {
      plugin.dispose();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      detachPlugin(plugin);
    }
  }

  /** Detaches a plugin from this panel before the plugin instance is released. */
  private void detachPlugin(NodusPlugin plugin) {
    if (plugin == null) {
      return;
    }

    try {
      plugin.setNodusMapPanel(null);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** Removes all action listeners from a plugin menu item. */
  private void removeActionListeners(JMenuItem menuItem) {
    ActionListener[] listeners = menuItem.getActionListeners();

    for (ActionListener listener : listeners) {
      menuItem.removeActionListener(listener);
    }
  }

  /** Removes a plugin menu item from any top-level menu in the menu bar. */
  private void removePluginMenuItem(JMenuItem searchedMenuItem) {
    int nbMenus = nodusMenuBar.getMenuCount();

    for (int i = 0; i < nbMenus; i++) {
      JMenu menu = nodusMenuBar.getMenu(i);

      if (menu == null) {
        continue;
      }

      int nbItems = menu.getMenuComponentCount();

      // Go through the menu from the end because items may be removed.
      for (int j = nbItems - 1; j >= 0; j--) {
        JMenuItem menuItem = menu.getItem(j);

        // Could be a separator...
        if (menuItem == null) {
          continue;
        }

        if (menuItem.equals(searchedMenuItem)) {
          menu.remove(menuItem);
        }
      }
    }
  }

  /** Removes project-specific plugins from the plugin dispatch array. */
  private void removeProjectPluginInstances() {
    if (nodusPlugins == null || nodusPlugins.length <= nbNodusPlugins) {
      return;
    }

    for (int i = nbNodusPlugins; i < nodusPlugins.length; i++) {
      disposePlugin(nodusPlugins[i]);
      nodusPlugins[i] = null;
    }

    if (nbNodusPlugins == 0) {
      nodusPlugins = null;
      return;
    }

    NodusPlugin[] globalPlugins = new NodusPlugin[nbNodusPlugins];

    for (int i = 0; i < nbNodusPlugins; i++) {
      globalPlugins[i] = nodusPlugins[i];
    }

    nodusPlugins = globalPlugins;
  }

  /** Disposes all loaded plugins, including global plugins, when the map panel is disposed. */
  void disposeAllPlugins() {
    if (nodusPlugins != null) {
      for (NodusPlugin plugin : nodusPlugins) {
        disposePlugin(plugin);
      }
      nodusPlugins = null;
    }

    nbNodusPlugins = 0;
    globalPluginsMenuItems.clear();
    projectPluginsMenuItems.clear();
    userDefinedMenus.clear();

    /*
     * Project loaders may already have been closed by removeProjectPlugins().
     * The close operation is intentionally idempotent for the application exit path.
     */
    closePluginLoaders(projectPluginLoaders);
    closePluginLoaders(globalPluginLoaders);
  }

  /** Removes empty user-defined plugin menus from the menu bar. */
  private void removeEmptyUserDefinedPluginMenus() {
    for (int i = userDefinedMenus.size() - 1; i >= 0; i--) {
      JMenu menu = (JMenu) userDefinedMenus.get(i);

      if (menu.getItemCount() == 0) {
        nodusMenuBar.remove(menu);
        userDefinedMenus.remove(i);
      }
    }
  }
}
