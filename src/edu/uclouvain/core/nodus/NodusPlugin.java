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

package edu.uclouvain.core.nodus;

import java.util.Properties;

/**
 * Skeleton that must be used for all plugins written for Nodus. The development of a plugin for
 * Nodus is very simple:
 *
 * <p>- Create a new class that extends NodusPlugin. - In this class, getProperties must return a
 * set of properties which control where and how the menu item relative to the plugin must be added.
 * - Start real work of the plugin in the "execute" member function To use the plugin, just put a
 * copy (or a link) to the plugin jar into the "plugins" directory of your Nodus installation. When
 * launched, Nodus will detect all the jar's in this directory which contain a valid
 * NodusPlugin and will create a relevant "plugins" menu.
 *
 * <p>If a plugin is developed for a given project, it can be put in a "projectname_plugins" dir of
 * the project directory. Such project dependent plugins will only appear when the project is
 * loaded.
 *
 * @author Bart Jourquin
 */
public class NodusPlugin {

  /**
   * Set the initial state of the menuItem for the plugin. Useful if the plugin is added to one of
   * the enabled menus at Nodus startup. The menu will automatically be enabled when a project is
   * loaded. True by default.
   */
  public static final String IS_ENABLED = "isEnabled";

  /** Menu ID than can be used if the plugin must be added to the "Control" menu. */
  public static final int MENU_CONTROL = 2;

  /** Menu ID than can be used if the plugin must be added to the "File" menu. */
  public static final int MENU_FILE = 0;

  /** Menu ID than can be used if the plugin must be added to the "Help" menu. */
  public static final int MENU_HELP = 4;

  /** Menu ID than can be used if the plugin must be added to the "Project" menu. */
  public static final int MENU_PROJECT = 1;

  /** Menu ID than can be used if the plugin must be added to the "Tools" menu. */
  public static final int MENU_TOOLS = 3;

  /** Mandatory property. Gives the text to be displayed as menuItem for the plugin. */
  public static final String MENU_ITEM__TEXT = "menuItemText";

  /**
   * This property gives the position of the plugin in the menu. If this property is not defined,
   * the plugin menu item will be added at the end of the list, or just before the last separator,
   * if any.
   */
  public static final String MENU_ITEM_ID = "menuItemID";

  /**
   * This property gives the ID of the menu to which the plugin must be added. This property is
   * mandatory if no USER_DEFINED_MENUBAR_TEXT is defined.
   */
  public static final String MENUBAR_ID = "menuBarID";

  /** String used to represent a true boolean value. */
  public static final String TRUE = "true";

  /** String used to represent a false boolean value. */
  public static final String FALSE = "false";

  /**
   * If set, this property creates a new menu in the menu bar, with the given text. This property
   * has priority on the MENUBAR_ID property, which will be ignored if this one is set.
   */
  public static final String USER_DEFINED_MENUBAR_TEXT = "menuBarText";

  /** Main variable that will give access to the complete Nodus API and variables. */
  private NodusMapPanel nodusMapPanel = null;

  /** Is called when the Ok button is pressed if the GenericPluginConsole is used. */
  public void doStart() {
    System.err.println("plugin 'doStart()' method is not implemented!");
  }

  /** Main entry point of the plugin, to which the NodusMapPanel will be passed. */
  public void execute() {
    System.err.println("plugin 'public void execute()' method is not implemented!");
  }

  /**
   * Returns access to the NodusMapPanel, and thus everything that can be needed by a plugin.
   *
   * @return The Nodus map panel.
   */
  public NodusMapPanel getNodusMapPanel() {
    return nodusMapPanel;
  }

  /**
   * Returns a set of properties that controls the way the plugin will be displayed in the menus.
   *
   * @return Properties The plugin properties
   */
  public Properties getProperties() {
    System.err.println("plugin 'getProperties()' method is not implemented!");
    return null;
  }

  /**
   * Called by Nodus when the plugins are loaded.
   *
   * @param nodusMapPanel The Nodus main MapPanel, which gives access to the whole Nodus API.
   */
  public void setNodusMapPanel(NodusMapPanel nodusMapPanel) {
    this.nodusMapPanel = nodusMapPanel;
  }
}
