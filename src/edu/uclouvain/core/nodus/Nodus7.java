/**
 * Copyright (c) 1991-2019 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus;

import com.bbn.openmap.Environment;
import com.bbn.openmap.MapHandler;
import com.bbn.openmap.gui.OpenMapFrame;

import edu.uclouvain.core.nodus.gui.Splash;
import edu.uclouvain.core.nodus.swing.GUIUtils;

import groovy.lang.GroovyShell;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.InputMap;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.text.DefaultEditorKit;

import org.codehaus.groovy.control.CompilationFailedException;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;

/**
 * Main class that creates a new NodusMapPanel, but also initializes the application with a
 * previously saved LookAndFeel and runs a startup Groovy script if it exists.
 *
 * @author Bart Jourquin
 */
public class Nodus7 {
  /* Nodus icon */
  private static Image icn =
      Toolkit.getDefaultToolkit().createImage(Nodus7.class.getResource("nodus7.png"));

  /* Logger */
  public static final Logger nodusLogger = Logger.getLogger(Nodus7.class.getName());

  /**
   * Properties file used to load/save the "state" of the application when it was closed the last
   * time (frame position and size, Look&Feel, ...).
   */
  private static Properties nodusProperties = new Properties();

  /** Serial version UID. */
  static final long serialVersionUID = -7812175433457058853L;

  /* Application MapPanel */
  private NodusMapPanel nodusMapPanel;

  /**
   * Main entry point of the java application. Initializes a new MapBean after having read the saved
   * "state" of the application in a properties file.
   *
   * @param args The name (and path) of the project to load
   */
  public static void main(String[] args) {

    // Display splash screen for at least 100 milliseconds
    Splash splash = new Splash();
    splash.display(100);

    Environment.init();

    nodusLogger.setUseParentHandlers(false);
    nodusLogger.setLevel(Level.ALL);

    // Trick used to avoid "no appender" Log4J warning message from HSQLDB
    //Logger rootLogger = Logger.getRootLogger();
    //rootLogger.addAppender(new NullAppender());
    
    // Open the properties file
    try {
      String home = System.getProperty("user.home") + "/";
      nodusProperties.load(new FileInputStream(home + ".nodus7.properties"));
    } catch (IOException ex) {
      // Nothing to do. The properties file will be created later.
    }

    // Prepare i18n mechanism
    String locale = nodusProperties.getProperty(NodusC.PROP_LOCALE, null);
    if (locale != null) {
      Locale.setDefault(new Locale(locale.toLowerCase(), locale.toUpperCase()));
    } else {
      Locale.setDefault(Locale.ENGLISH);
    }

    setLookAndFeel();

    // Improve Mac OS experience
    if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
      // Set icon in Mac OS Dock
      GUIUtils.setMacOSDockImage(icn);

      if (UIManager.getLookAndFeel().isNativeLookAndFeel()) {
        // use the mac system menu bar
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        // set the "About" menu item name
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", NodusC.APPNAME);
      }

      // use smoother fonts
      System.setProperty("apple.awt.textantialiasing", "true");

      // Be sure cmd key is used instead of ctrl in some controls for copy and paste
      InputMap im;
      final String[] componentName = {
        "TextField", "TextArea", "TextPane", "EditorPane", "FormattedTextField"
      };

      for (int i = 0; i < componentName.length; i++) {
        im = (InputMap) UIManager.get(componentName[i] + ".focusInputMap");
        if (im != null) {
          im.put(
              KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.META_DOWN_MASK),
              DefaultEditorKit.copyAction);
          im.put(
              KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.META_DOWN_MASK),
              DefaultEditorKit.pasteAction);
          im.put(
              KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.META_DOWN_MASK),
              DefaultEditorKit.cutAction);
        }
      }

      // Try to improve hardware acceleration
      System.setProperty("apple.awt.graphics.EnableQ2DX", "true");

      // If the app is launched without argument, a "-psn_xxx" argument seems to appear
      // In this case, ignore the argument
      if (args.length > 0) {
        if (args[0].startsWith("-psn")) {
          args = new String[0];
        }
      }
    }

    // Handle the parameter if any (project to load)
    String projectToLoad = null;

    if (args.length == 1 && args[0].length() > 0) {
      projectToLoad = args[0];
      try {
        // Be sure the suffix is present
        if (!projectToLoad.endsWith(NodusC.TYPE_NODUS)) {
          projectToLoad += NodusC.TYPE_NODUS;
        }

        // Add current path ?
        Path p = Paths.get(projectToLoad);
        if (p.getParent() == null) {

          projectToLoad = new File(".").getCanonicalPath() + File.separator + projectToLoad;
        }

        // Test if the file exists
        File f = new File(projectToLoad);
        if (!f.exists() || f.isDirectory()) {
          projectToLoad = null;
        }

      } catch (IOException e) {
        projectToLoad = null;
      }
    } else {
      // If no parameter is passed, reload the last project
      // This (can be set in the global preferences dialog)
      String value = nodusProperties.getProperty(NodusC.PROP_REOPEN_LATST_PROJECT, "false");
      if (value.equalsIgnoreCase("true")) {
        // Try to load last project
        projectToLoad =
            nodusProperties.getProperty(NodusC.PROP_LAST_PATH)
                // + File.separator
                + nodusProperties.getProperty(NodusC.PROP_LAST_PROJECT);

        // Test if the file exists
        File f = new File(projectToLoad);
        if (!f.exists() || f.isDirectory()) {
          projectToLoad = null;
        }
      }
    }

    new Nodus7(projectToLoad);

    splash.dispose();
  }

  /**
   * Main constructor that initializes the application.
   *
   * @param projectToLoad Full path to the Nodus project file to load at startup.
   */
  public Nodus7(final String projectToLoad) {

    /*
     * Register the NodusSQL language (Nodus specific convenient commands added,
     * that will be highlighted as regular SQL commands
     */
    AbstractTokenMakerFactory atmf =
        (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
    atmf.putMapping("text/NodusSQL", "edu.uclouvain.core.nodus.database.gui.NodusSQLTokenMaker");

    // Create main window
    nodusMapPanel = new NodusMapPanel(nodusProperties);

    // Initialize the components
    javax.swing.SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            showInFrame();
            nodusMapPanel.restoreSizeAndLocation();

            // Run the "nodus.groovy" script if exists
            Thread thread =
                new Thread() {
                  @Override
                  public void start() {

                    String homeDir = System.getProperty("NODUS_HOME", ".");

                    GroovyShell shell = new GroovyShell();
                    shell.setVariable("nodusMapPanel", nodusMapPanel);
                    try {
                      shell.evaluate(new File(homeDir + "/nodus" + NodusC.TYPE_GROOVY));
                    } catch (CompilationFailedException e) {
                      System.err.println(e.getMessage());
                    } catch (IOException e) {
                      // Do nothing. the nodus.groovy script is not mandatory
                    }
                  }
                };
            thread.start();

            // Load the project passed as parameter
            if (projectToLoad != null) {
              nodusMapPanel.openProject(projectToLoad);
            }
          }
        });
  }

  /** Sets the Look and Feel to the system defaulr, but to Nimbus for Linux. */
  private static void setLookAndFeel() {

    // Set Look & Feel (default to system l&f, but Nimbus for Linux)
    String lookAndFeel = null;
    lookAndFeel = nodusProperties.getProperty("look&feel", null);
    if (lookAndFeel == null) {
      lookAndFeel = UIManager.getSystemLookAndFeelClassName();
    }

    JFrame.setDefaultLookAndFeelDecorated(true);
    JDialog.setDefaultLookAndFeelDecorated(true);

    try {
      UIManager.setLookAndFeel(lookAndFeel);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Handles the closing of the main frame.
   *
   * @param openMapFrame The main frame of the application.
   */
  private void setWindowListenerOnFrame(OpenMapFrame openMapFrame) {
    openMapFrame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            nodusMapPanel.closeAndSaveState();
            System.exit(0);
          }
        });
  }

  /** Associates the OpenMap frame with the application. */
  private void showInFrame() {
    MapHandler mapHandler = nodusMapPanel.getMapHandler();
    OpenMapFrame omf = mapHandler.get(com.bbn.openmap.gui.OpenMapFrame.class);

    if (omf == null) {
      omf = new OpenMapFrame(NodusC.APPNAME);
      omf.setIconImage(icn);
      mapHandler.add(omf);
    }

    setWindowListenerOnFrame(omf);

    omf.setVisible(true);
    nodusMapPanel.getMapBean().showLayerPalettes();
  }
}
