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

package edu.uclouvain.core.nodus.mappanel;

import com.bbn.openmap.Environment;
import com.bbn.openmap.gui.NodusOMControlPanel;
import com.bbn.openmap.gui.ToolPanel;
import com.bbn.openmap.gui.menu.NodusSaveAsImageMenuItem;
import com.bbn.openmap.gui.menu.PNGImageFormatter;
import com.bbn.openmap.gui.menu.ProjectionMenu;
import com.bbn.openmap.image.AcmeGifFormatter;
import com.bbn.openmap.image.SunJPEGFormatter;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import java.awt.Component;
import java.awt.Desktop;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/** Owns built-in menu components, layout and translated labels. */
public final class MapMenus {
  /** JPEG image formatter : See OpenMap documentation for more details. */
  private SunJPEGFormatter jpegFormatter = new SunJPEGFormatter();
  /** "Control" menu. */
  public final JMenu menuControl = new JMenu();
  /** "File" menu. */
  public final JMenu menuFile = new JMenu();
  /** "Help" menu. */
  public final JMenu menuHelp = new JMenu();
  /** "Control|Background color" menu item. */
  public final JMenuItem menuItemControlBackground = new JMenuItem();
  /** "Control|Hide/Display Controlpanel" menu item. */
  public final JMenuItem menuItemControlControlpanel = new JMenuItem();
  /** "Control|Hide/Display Toolpanel" menu item. */
  public final JMenuItem menuItemControlToolpanel = new JMenuItem();
  /** "File|Close" menu item. */
  public final JMenuItem menuItemFileClose = new JMenuItem();
  /** "File|Exit" menu item. */
  public final JMenuItem menuItemFileExit = new JMenuItem();
  /** "File|Open" menu item. */
  public final JMenuItem menuItemFileOpen = new JMenuItem();
  /** "File|Print" menu item. */
  public final JMenuItem menuItemFilePrint = new JMenuItem();
  /** "File|Save" menu item. */
  public final JMenuItem menuItemFileSave = new JMenuItem();
  /** "File|Save as" menu item. */
  public final JMenu menuItemFileSaveAs = new JMenu();
  /** Save as GIF image : See OpenMap documentation for more details. */
  private NodusSaveAsImageMenuItem menuItemFileSaveMapAsGIF;
  /** Save as JPEG image : See OpenMap documentation for more details. */
  private NodusSaveAsImageMenuItem menuItemFileSaveMapAsJPEG;
  /** Save as JPEG image : See OpenMap documentation for more details. */
  private NodusSaveAsImageMenuItem menuItemFileSaveMapAsPNG;
  /** "Help|About" menu. */
  public final JMenuItem menuItemHelpAbout = new JMenuItem();
  /** "Help|API JavaDoc" menu. */
  public final JMenuItem menuItemHelpApiDoc = new JMenuItem();
  /** Main help. */
  public final JMenuItem menuItemHelpHelp = new JMenuItem();
  /** "Project|Modal choice estimation" menu item. */
  public final JMenuItem menuItemProjectModalChoice = new JMenuItem();
  /** "Project|Assignment" menu item. */
  public final JMenuItem menuItemProjectAssignment = new JMenuItem();
  /** "Project|Cost functions" menu item. */
  public final JMenuItem menuItemProjectCosts = new JMenuItem();
  /** "Project|Display results" menu item. */
  public final JMenuItem menuItemProjectDisplayResults = new JMenuItem();
  /** "Project|Properties" menu item. */
  public final JMenuItem menuItemProjectPreferences = new JMenuItem();
  /** "Project|Scenarios" menu item. */
  public final JMenuItem menuItemProjectScenarios = new JMenuItem();
  /** "Project|Services functions" menu item. */
  public final JMenuItem menuItemProjectServices = new JMenuItem();
  /** "Project|SQL console" menu item. */
  public final JMenuItem menuItemProjectSQLConsole = new JMenuItem();
  /** "File|Open" menu item. */
  public final JMenuItem menuItemSystemProperties = new JMenuItem();
  /** "Tools|Console" menu item. */
  public final JMenuItem menuItemToolConsole = new JMenuItem();
  /** "Tools|Groovy console" menu item. */
  public final JMenuItem menuItemToolGroovyScripts = new JMenuItem();
  /** "Tools|Language" menu item. */
  public final JMenuItem menuItemToolLanguage = new JMenuItem();
  /** "Tools|Look And feel" menu item. */
  public final JMenuItem menuItemToolLookAndFeel = new JMenuItem();
  /** "Tools|Memory monitor" menu item. */
  public final JMenuItem menuItemToolRessourcesMonitor = new JMenuItem();
  /** "Project" menu. */
  public final JMenu menuProject = new JMenu();
  /** "Projection" menu. See OpenMap documentation for more details. */
  public final ProjectionMenu menuProjection = new ProjectionMenu();
  /** "Tools" menu. */
  public final JMenu menuTools = new JMenu();
  /** Main menu bar. */
  public final JMenuBar nodusMenuBar = new JMenuBar();

  private static final I18n i18n = Environment.getI18n();
  private final NodusMapPanel panel;
  private final ToolPanel toolPanel;
  private final NodusOMControlPanel controlPanel;
  private final Desktop desktop;
  private final Runnable installActionListeners;
  private final Runnable installPreferences;
  private final Runnable installApplicationHandlers;
  private final java.util.function.BooleanSupplier useMacDesktopIntegration;

  public MapMenus(
      NodusMapPanel panel,
      ToolPanel toolPanel,
      NodusOMControlPanel controlPanel,
      Desktop desktop,
      Runnable installActionListeners,
      Runnable installPreferences,
      Runnable installApplicationHandlers,
      java.util.function.BooleanSupplier useMacDesktopIntegration) {
    this.panel = panel;
    this.toolPanel = toolPanel;
    this.controlPanel = controlPanel;
    this.desktop = desktop;
    this.installActionListeners = installActionListeners;
    this.installPreferences = installPreferences;
    this.installApplicationHandlers = installApplicationHandlers;
    this.useMacDesktopIntegration = useMacDesktopIntegration;
  }

  /** Create the menus. */
  public void initMenus() {
    // Initialize the action listeners
    setMenuItemsText();
    installActionListeners.run();

    setMenusText();

    // menuFile.setText(i18n.get(NodusMapPanel.class, "File", "File"));

    menuItemFileSaveMapAsGIF = new NodusSaveAsImageMenuItem("GIF", new AcmeGifFormatter());

    menuItemFileSaveMapAsPNG = new NodusSaveAsImageMenuItem("PNG", new PNGImageFormatter());

    jpegFormatter.setImageQuality(NodusC.JPEG_QUALITY);
    menuItemFileSaveMapAsJPEG = new NodusSaveAsImageMenuItem("JPEG", jpegFormatter);

    // menuProject.setText(i18n.get(NodusMapPanel.class, "Project", "Project"));

    menuProject.setEnabled(false);

    // menuTools.setText(i18n.get(NodusMapPanel.class, "Tools", "Tools"));

    // menuHelp.setText(i18n.get(NodusMapPanel.class, "Help", "Help"));

    menuItemFileSaveAs.setEnabled(false);
    // menuItemFileSaveAs.setText(i18n.get(NodusMapPanel.class, "Save_as_", "Save as..."));

    menuItemFileSaveMapAsGIF.setText("GIF");
    menuItemFileSaveMapAsGIF.setMapHandler(panel.getMapHandler());
    menuItemFileSaveAs.add(menuItemFileSaveMapAsGIF);

    menuItemFileSaveMapAsJPEG.setText("JPEG");
    menuItemFileSaveMapAsJPEG.setMapHandler(panel.getMapHandler());
    menuItemFileSaveAs.add(menuItemFileSaveMapAsJPEG);

    menuItemFileSaveMapAsPNG.setText("PNG");
    menuItemFileSaveMapAsPNG.setMapHandler(panel.getMapHandler());
    menuItemFileSaveAs.add(menuItemFileSaveMapAsPNG);

    // menuControl.setText(i18n.get(NodusMapPanel.class, "Control", "Control"));

    menuControl.setEnabled(false);

    menuProjection.setEnabled(false);

    int n = menuProjection.getMenuComponentCount();

    // Translate the projection names
    for (int i = 0; i < n; i++) {

      Component c = menuProjection.getMenuComponent(i);
      if (c instanceof JMenuItem) {
        JMenuItem mi = (JMenuItem) c;
        mi.setText(i18n.get(NodusMapPanel.class, mi.getText(), mi.getText()));
      }
    }

    nodusMenuBar.add(menuFile);
    nodusMenuBar.add(menuProject);
    nodusMenuBar.add(menuControl);
    nodusMenuBar.add(menuProjection);

    nodusMenuBar.add(menuTools);
    nodusMenuBar.add(menuHelp);

    menuFile.add(menuItemFileOpen);

    installPreferences.run();

    menuFile.add(menuItemFileSave);
    menuFile.add(menuItemFileClose);
    menuFile.add(menuItemFileSaveAs);
    menuFile.add(menuItemFilePrint);

    installApplicationHandlers.run();

    if (useMacDesktopIntegration.getAsBoolean()
        && desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
      // The Quit item is provided by the macOS application menu.
    } else {
      menuFile.addSeparator();
      menuFile.add(menuItemFileExit);
    }

    menuProject.add(menuItemProjectPreferences);
    menuProject.add(menuItemProjectCosts);
    menuProject.add(menuItemProjectServices);
    menuProject.add(menuItemProjectModalChoice);
    menuProject.add(menuItemProjectAssignment);
    menuProject.add(menuItemProjectDisplayResults);
    menuProject.add(menuItemProjectScenarios);
    menuProject.add(menuItemProjectSQLConsole);

    menuControl.add(menuItemControlBackground);
    menuControl.add(menuItemControlToolpanel);
    menuControl.add(menuItemControlControlpanel);

    menuTools.add(menuItemToolLookAndFeel);
    menuTools.add(menuItemToolLanguage);
    menuTools.add(menuItemToolConsole);
    menuTools.add(menuItemToolGroovyScripts);
    menuTools.add(menuItemToolRessourcesMonitor);

    if (!useMacDesktopIntegration.getAsBoolean()
        || !desktop.isSupported(Desktop.Action.APP_ABOUT)) {
      menuHelp.add(menuItemHelpAbout);
    }
  }

  public void setMenusText() {
    menuFile.setText(i18n.get(NodusMapPanel.class, "File", "File"));

    menuProject.setText(i18n.get(NodusMapPanel.class, "Project", "Project"));

    menuTools.setText(i18n.get(NodusMapPanel.class, "Tools", "Tools"));

    menuHelp.setText(i18n.get(NodusMapPanel.class, "Help", "Help"));

    menuItemFileSaveAs.setText(i18n.get(NodusMapPanel.class, "Save_as_", "Save as..."));

    menuControl.setText(i18n.get(NodusMapPanel.class, "Control", "Control"));
  }

  public void setMenuItemsText() {
    menuItemFileOpen.setText(i18n.get(NodusMapPanel.class, "Open_project", "Open project"));

    menuItemSystemProperties.setText(
        i18n.get(NodusMapPanel.class, "Open_preferences", "Global preferences"));

    menuItemFileSave.setText(i18n.get(NodusMapPanel.class, "Save_project", "Save project"));

    menuItemFileClose.setText(i18n.get(NodusMapPanel.class, "Close_project", "Close project"));

    menuItemFilePrint.setText(i18n.get(NodusMapPanel.class, "Print", "Print"));

    menuItemFileExit.setText(i18n.get(NodusMapPanel.class, "Exit", "Exit"));

    menuItemProjectModalChoice.setText(
        i18n.get(NodusMapPanel.class, "Estimate_modal_choice", "Modal choice estimation"));

    menuItemProjectAssignment.setText(i18n.get(NodusMapPanel.class, "Assignment", "Assignment"));

    menuItemProjectSQLConsole.setText(i18n.get(NodusMapPanel.class, "SQL_Console", "SQL Console"));

    menuItemProjectDisplayResults.setText(
        i18n.get(NodusMapPanel.class, "Display_results", "Display results"));

    menuItemProjectScenarios.setText(i18n.get(NodusMapPanel.class, "Scenarios", "Scenarios"));

    menuItemProjectCosts.setText(
        i18n.get(NodusMapPanel.class, "Edit_cost_functions", "Edit cost functions"));

    menuItemProjectServices.setText(
        i18n.get(NodusMapPanel.class, "Edit_services", "Edit services"));

    menuItemProjectPreferences.setText(
        i18n.get(NodusMapPanel.class, "Project_preferences", "Project preferences"));

    menuItemToolLookAndFeel.setText(i18n.get(NodusMapPanel.class, "Look_&_Feel", "Look & Feel"));

    menuItemToolLanguage.setText(i18n.get(NodusMapPanel.class, "Language", "Language"));

    menuItemToolConsole.setText(i18n.get(NodusMapPanel.class, "Console", "Console"));

    menuItemToolGroovyScripts.setText(
        i18n.get(NodusMapPanel.class, "Groovy_scripts", "Groovy scripts"));

    menuItemToolRessourcesMonitor.setText(
        i18n.get(NodusMapPanel.class, "Resources_monitor", "Resources monitor"));

    menuItemHelpAbout.setText(i18n.get(NodusMapPanel.class, "About", "About"));

    menuItemHelpHelp.setText(i18n.get(NodusMapPanel.class, "Help", "Help"));

    menuItemHelpApiDoc.setText(i18n.get(NodusMapPanel.class, "API_Doc", "API Javadoc"));

    menuItemControlBackground.setText(
        i18n.get(NodusMapPanel.class, "Set_Background_color", "Set Background color"));

    setControlMenuItemsText();
  }

  /** Updates labels of control menu items whose text depends on component visibility. */
  public void setControlMenuItemsText() {
    if (toolPanel != null && !toolPanel.isVisible()) {
      menuItemControlToolpanel.setText(
          i18n.get(NodusMapPanel.class, "Display_Tool_Panel", "Display Tool Panel"));
    } else {
      menuItemControlToolpanel.setText(
          i18n.get(NodusMapPanel.class, "Hide_Tool_Panel", "Hide Tool Panel"));
    }

    if (controlPanel != null && !controlPanel.isVisible()) {
      menuItemControlControlpanel.setText(
          i18n.get(NodusMapPanel.class, "Display_Control_Panel", "Display Control Panel"));
    } else {
      menuItemControlControlpanel.setText(
          i18n.get(NodusMapPanel.class, "Hide_Control_Panel", "Hide Control Panel"));
    }
  }
}
