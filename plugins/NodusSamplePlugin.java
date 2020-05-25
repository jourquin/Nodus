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

import java.util.Properties;

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusPlugin;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;

/**
 * This is a very simple plugin for Nodus, showing how the basics can be implemented. A plugin class
 * must extend NodusPlugin.<br>
 * - This plugin is added to the "Tools" menu, and appears as "Sample plugin". <br>
 * - It gets the NodusMapPanel, which is the main entry point of the Nodus API. <br>
 * - It opens a Nodus console and writes the name if the loaded project, if any. <br>
 * <br>
 * Once the jar generated from this file, it can be placed in two locations: <br>
 * - In the "plugins" subdirectory of the Nodus installation directory. In this case, the plugin is
 * loaded at Nodus launch time, and is available as long as Nodus is running. This is used for
 * general plugins.<br>
 * - In a project directory. In this case, the plugin is loaded when a project located in this
 * directory is loaded. The plugin will also be unloaded when the project is closed. This is used
 * for project specific plugins.
 * 
 * A plugin must extend edu.uclouvain.core.nodus.NodusPlugin. See the API doc for this class
 * for more information. 
 * 
 * @author Bart Jourquin
 */
public class NodusSamplePlugin extends NodusPlugin {

  /**
   * This is the where real work starts.
   */
  public void execute() {
    /* 
     * Display a Nodus console. It intercepts System.out and System.err in order to display the
     * output in a JTextPane.
	  */
    new NodusConsole();

    /*
     *  Get the Nodus map panel, which is the main entry point to the Nodus API
     */
    NodusMapPanel nmp = getNodusMapPanel();

    /*
     *  Write the name of the loaded project in the consolde
     */
    String msg = "No project is loaded";
    if (nmp.getNodusProject().isOpen()) {
      msg = nmp.getNodusProject().getName() + " is loaded";
    }
    System.out.println(msg);
  }

  /**
   * Controls the way the menuItem relative to this plugin will be displayed.
   * See edu.uclouvain.core.nodus.NodusPlugin for more information.
   * 
   * @return Properties
   */
  public Properties getProperties() {
    Properties p = new Properties();
    
    /*
     *  Text associated to the menu item 
     */
    p.setProperty(MENU_ITEM__TEXT, "Sample Plugin");

    /*
     *  Add it to the "tools" menu
     */
    p.setProperty(MENUBAR_ID, String.valueOf(MENU_TOOLS));

    return p;
  }

}
