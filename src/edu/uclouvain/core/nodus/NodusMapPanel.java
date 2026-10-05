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

import com.bbn.openmap.BufferedMapBean;
import com.bbn.openmap.Environment;
import com.bbn.openmap.HintsMapBeanRepaintPolicy;
import com.bbn.openmap.InformationDelegator;
import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.MapBeanRepaintPolicy;
import com.bbn.openmap.MapHandler;
import com.bbn.openmap.MouseDelegator;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.ShapeConstants;
import com.bbn.openmap.event.DistanceMouseMode;
import com.bbn.openmap.event.NavMouseMode;
import com.bbn.openmap.event.NavMouseMode2;
import com.bbn.openmap.event.NodusProjMapBeanKeyListener;
import com.bbn.openmap.event.PanMouseMode;
import com.bbn.openmap.event.SelectMouseMode;
import com.bbn.openmap.gui.MapPanel;
import com.bbn.openmap.gui.MouseModeButtonPanel;
import com.bbn.openmap.gui.NodusLayersPanel;
import com.bbn.openmap.gui.NodusOMControlPanel;
import com.bbn.openmap.gui.OpenMapFrame;
import com.bbn.openmap.gui.OverviewMapHandler;
import com.bbn.openmap.gui.ToolPanel;
import com.bbn.openmap.image.MapBeanPrinter;
import com.bbn.openmap.layer.LabelLayer;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.layer.highlightedarea.HighlightedAreaLayer;
import com.bbn.openmap.layer.policy.RenderingHintsRenderPolicy;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.layer.shape.PoliticalBoundariesLayer;
import com.bbn.openmap.layer.shape.ShapeLayer;
import com.bbn.openmap.omGraphics.OMColorChooser;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.proj.CADRGLoader;
import com.bbn.openmap.proj.EqualEarthLoader;
import com.bbn.openmap.proj.GnomonicLoader;
import com.bbn.openmap.proj.LLXYLoader;
import com.bbn.openmap.proj.Length;
import com.bbn.openmap.proj.Mercator;
import com.bbn.openmap.proj.MercatorLoader;
import com.bbn.openmap.proj.OrthographicLoader;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.ProjectionFactory;
import com.bbn.openmap.proj.ProjectionLoader;
import com.bbn.openmap.proj.ProjectionStack;
import com.bbn.openmap.tools.drawing.NodusOMDrawingTool;
import com.bbn.openmap.tools.drawing.NodusOMDrawingToolLauncher;
import com.bbn.openmap.tools.drawing.NodusOMPointLoader;
import com.bbn.openmap.tools.drawing.NodusOMPolyLoader;
import com.bbn.openmap.tools.drawing.OMDrawingToolMouseMode;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.compute.assign.gui.AssignmentDlg;
import edu.uclouvain.core.nodus.compute.modalsplit.ModalChoiceEstimationDlg;
import edu.uclouvain.core.nodus.compute.real.RealNetworkObject;
import edu.uclouvain.core.nodus.compute.results.gui.ResultsDlg;
import edu.uclouvain.core.nodus.compute.scenario.gui.ScenariosDlg;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.database.gui.SQLConsole;
import edu.uclouvain.core.nodus.gui.GlobalPreferencesDlg;
import edu.uclouvain.core.nodus.gui.LanguageChooser;
import edu.uclouvain.core.nodus.gui.LookAndFeelChooser;
import edu.uclouvain.core.nodus.gui.ProjectPreferencesDlg;
import edu.uclouvain.core.nodus.gui.SplashDlg;
import edu.uclouvain.core.nodus.helpbrowser.HelpBrowser;
import edu.uclouvain.core.nodus.swing.GUIUtils;
import edu.uclouvain.core.nodus.swing.OnTopKeeper;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import edu.uclouvain.core.nodus.tools.notepad.NodusGroovyConsole;
import edu.uclouvain.core.nodus.tools.notepad.NotePad;
import edu.uclouvain.core.nodus.utils.GitHubRelease;
import edu.uclouvain.core.nodus.utils.HardwareUtils;
import edu.uclouvain.core.nodus.utils.ScriptRunner;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;

/**
 * The NodusMapPanel class initialized the Nodus GUI and is the central place where all the menu
 * actions are intercepted. It is also the main entry to the whole Nodus API.
 *
 * @author Bart Jourquin
 */
public class NodusMapPanel extends MapPanel implements ShapeConstants {

  private final NativeGroovyConsole nativeConsole =
      new NativeGroovyConsole(
          this,
          () -> {
            registerMacApplicationHandlers();
            setGlobalPreferencesMenu();
          });

  /** This class is used to hold an image while on the clipboard. */
  private static class ImageSelection implements Transferable, ClipboardOwner {

    private Image image;

    public ImageSelection(Image image) {
      this.image = image;
    }

    @Override
    public Object getTransferData(DataFlavor flavor)
        throws UnsupportedFlavorException, IOException {
      if (!DataFlavor.imageFlavor.equals(flavor)) {
        throw new UnsupportedFlavorException(flavor);
      }
      return image;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[] {DataFlavor.imageFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return DataFlavor.imageFlavor.equals(flavor);
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
      image = null;
    }
  }

  /** Internationalization mechanism. */
  private static I18n i18n = Environment.getI18n();

  /** Serial version UID. */
  static final long serialVersionUID = -2848516994072912179L;

  /** If true, wait before displaying the default political boundaries at startup. */
  private boolean deferDefaultPoliticalBoundaries;

  /** OpenMap component. See OpenMap's documentation for more details. */
  private NodusOMControlPanel controlPanel = new NodusOMControlPanel(this);

  /** Track the state of the "control" key. */
  private boolean controlPressed = false;

  /** Key listener that handles projection navigation on the map bean. */
  private NodusProjMapBeanKeyListener mapBeanKeyListener = null;

  /** Key listener that handles Nodus command shortcuts on this panel and on the map bean. */
  private KeyAdapter commandKeyListener = null;

  /** Default background color. */
  private Color defaultBackgroundColor;

  /** The browser used for the user guide. */
  HelpBrowser helpBrowser = null;

  /** Internal layer that displays a highlighted area to which the assignment can be limited. */
  private HighlightedAreaLayer highlightedAreaLayer = null;

  /** OpenMap component. See OpenMap documentation for more details. */
  private InformationDelegator infoDelegator = new InformationDelegator();

  private final MapProgress progress = new MapProgress(this, infoDelegator);

  /** The browser used for the Nodus API. */
  HelpBrowser javaDocBrowser = null;

  /** OpenMap component. See OpenMap documentation for more details. */
  private LayerHandler layerHandler = new LayerHandler();

  /** Mapbean to use. See OpenMap documentation for more details. */
  private MapBean mapBean;

  /** MapHandler to use. See OpenMap documentation for more details. */
  private MapHandler mapHandler;

  /** OpenMap component. See OpenMap documentation for more details. */
  private MouseDelegator mouseDelegator = new MouseDelegator();

  /** OpenMap component. See OpenMap documentation for more details. */
  private NavMouseMode navMouseMode1 = new NavMouseMode();

  /** OpenMap component. See OpenMap documentation for more details. */
  private NavMouseMode2 navMouseMode2 = new NavMouseMode2();

  /** OpenMap component. See OpenMap documentation for more details. */
  private NodusOMDrawingTool nodusDrawingTool;

  /** OpenMap component. See OpenMap documentation for more details. */
  private NodusOMDrawingToolLauncher nodusDrawingToolLauncher;

  /** The home directory of the Nodus application. */
  private String nodusHomeDir;

  /** OpenMap component. See OpenMap documentation for more details. */
  private NodusLayersPanel nodusLayersPanel;

  /** Place holder for the Nodus project that will be opened. */
  private NodusProject nodusProject = null;

  /**
   * Nodus properties, that maintain information about the location and size of the main frame, Look
   * And Feel, ....
   */
  private Properties nodusProperties;

  /** Used to force subframes to remain on top. */
  private OnTopKeeper onTopKeeper;

  /** OpenMap component. See OpenMap documentation for more details */
  private OverviewMapHandler overviewMapHandler = null;

  /** Internal layer that displays the political boundaries. */
  private ShapeLayer politicalBoundariesLayer = null;

  /** OpenMap component. See OpenMap documentation for more details */
  private ProjectionStack projectionStack = new ProjectionStack();

  /** Used to know if the scenario is changed in order to reset the displayed results. */
  private int lastScenario = -1;

  /**
   * Max scale above which the links and nodes are not anymore rendered with their specific
   * attributes.
   */
  private float renderingScaleThresold = -1;

  /** Combo in tool panel that makes it possible to choose the scenario. */
  private JComboBox<String> scenarioComboBox;

  /** Label associated to the scenario combo. */
  private JLabel scenarioLabel = new JLabel("", SwingConstants.RIGHT);

  /** Sound feedback. */
  private SoundPlayer soundPlayer;

  /** OpenMap component. See OpenMap documentation for more details. */
  private ToolPanel toolPanel = new ToolPanel();

  private static MapBeanRepaintPolicy defaultMapBeanRepaintPolicy;

  /** Current desktop context. */
  private Desktop desktop = getSupportedDesktop();

  private final MapMenus menus =
      new MapMenus(
          this,
          toolPanel,
          controlPanel,
          desktop,
          this::createMenuActionListeners,
          this::setGlobalPreferencesMenu,
          this::registerMacApplicationHandlers,
          this::useMacDesktopIntegration);

  private final MapPluginManager pluginManager =
      new MapPluginManager(
          this,
          menus.nodusMenuBar,
          menus.menuFile,
          menus.menuProject,
          menus.menuControl,
          menus.menuTools,
          menus.menuHelp);

  /**
   * Creates an uninitialized panel for adapters that provide their own project and UI callbacks.
   * No application UI, plugins or update checks are started. Subclasses must supply the callbacks
   * they use.
   */
  protected NodusMapPanel() {
    nodusProperties = new Properties();
  }

  /**
   * Creates all the GUI components needed by Nodus on the application's panel. The application's
   * properties are also passed as a parameter in order to restore and save the application "state".
   * <br>
   * See also OpenMap documentation for more details on the behavior of the MapBeans.
   *
   * @param properties The .nodus9.properties file content
   */
  public NodusMapPanel(Properties properties) {
    this(properties, false);
  }

  /**
   * Creates all the GUI components needed by Nodus on the application's panel. The application's
   * properties are also passed as a parameter in order to restore and save the application "state".
   * <br>
   * See also OpenMap documentation for more details on the behavior of the MapBeans.
   *
   * @param properties The .nodus9.properties file content.
   * @param deferDefaultPoliticalBoundaries If true, do not display the built-in political
   *     boundaries during initialization.
   */
  public NodusMapPanel(Properties properties, boolean deferDefaultPoliticalBoundaries) {
    MapHandler mh = getMapHandler();
    mh.add(this);
    nodusProperties = properties;
    this.deferDefaultPoliticalBoundaries = deferDefaultPoliticalBoundaries;

    create();
    GUIUtils.installToolTips(this, this);

    // Check if a newer version is available
    String value = getNodusProperties().getProperty(NodusC.PROP_CHECK_FOR_UPDATES, "true");
    if (Boolean.parseBoolean(value)) {
      GitHubRelease.checkForNewerRelease();
    }
  }

  /** Returns the local desktop integration object only when the runtime actually supports it. */
  private static Desktop getSupportedDesktop() {
    if (!Desktop.isDesktopSupported()) {
      return null;
    }

    try {
      return Desktop.getDesktop();
    } catch (SecurityException | UnsupportedOperationException e) {
      return null;
    }
  }

  /** True when native macOS application handlers can be registered safely. */
  private boolean useMacDesktopIntegration() {
    return desktop != null
        && System.getProperty("os.name").toLowerCase().startsWith("mac")
        && UIManager.getLookAndFeel().isNativeLookAndFeel();
  }

  /** Registers Nodus handlers for the macOS application menu. */
  private void registerMacApplicationHandlers() {
    if (!useMacDesktopIntegration()) {
      return;
    }

    if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
      desktop.setQuitHandler(
          (e, r) -> {
            menuItemFileExitActionPerformed();
          });
    }

    if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
      desktop.setAboutHandler(e -> menuItemAboutActionPerformed());
    }

    if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
      desktop.setPreferencesHandler(e -> menuItemFileGlobalPreferencesActionPerformed());
    }
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public void addMapComponent(Object mapComponent) {
    if (mapComponent != null) {
      getMapHandler().add(mapComponent);
    }
  }

  /** Sets the NavModeMouse, depending on the type selected in the global preferences. */
  public void addNavMouseMode() {

    int type = 1;
    try {
      type = Integer.parseInt(nodusProperties.getProperty(NodusC.PROP_NAV_MOUSE_MODE, "1"));
    } catch (NumberFormatException e) {
      type = 1;
    }

    if (type == 2) {
      getMapHandler().remove(navMouseMode1);
      getMapHandler().add(navMouseMode2);
    } else {
      getMapHandler().remove(navMouseMode2);
      getMapHandler().add(navMouseMode1);
    }
  }

  /** Developer/test hook: calls the class configured with the F8 property. */
  private void callF8() {
    callFunctionKey("F8");
  }

  /** Developer/test hook: calls the class configured with the F9 property. */
  private void callF9() {
    callFunctionKey("F9");
  }

  /**
   * Calls a developer/test extension associated with a programmable function key.
   *
   * <p>The class name is read from the open project properties first, using the key {@code F8} or
   * {@code F9}. If no project-specific value is found, the global Nodus properties are checked. The
   * target class must be available on the application classpath and must expose a public
   * constructor accepting one {@link NodusMapPanel} argument.
   *
   * <p><strong>Lifecycle contract:</strong> F8/F9 classes should be short-lived. They must not keep
   * long-lived strong references to {@code NodusMapPanel}, {@code NodusProject}, layers, Swing
   * components, JDBC objects, timers, threads, or listeners. If such a class creates windows,
   * starts threads, installs listeners, opens resources, or keeps caches, it is responsible for
   * cleaning them up.
   *
   * <p>There is no automatic project-close callback for these hooks. Long-lived tools should be
   * implemented as normal Nodus plugins instead.
   *
   * @param functionKey the programmable key name, normally {@code "F8"} or {@code "F9"}
   */
  private void callFunctionKey(String functionKey) {
    String className = null;

    if (nodusProject != null && nodusProject.isOpen()) {
      className = nodusProject.getProperty(functionKey);
    }

    if (className == null) {
      className = nodusProperties.getProperty(functionKey, null);
    }

    if (className == null || className.isBlank()) {
      return;
    }

    try {
      Class<?> hookClass = Class.forName(className);
      Constructor<?> ctor = hookClass.getConstructor(NodusMapPanel.class);
      ctor.newInstance(this);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | SecurityException
        | InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Cancels a ProgressBar. See OpenMap documentation for more details on the progress bar mechanism
   * implemented on the MapBean.
   */
  public void cancelLongTask() {
    progress.cancelLongTask();
  }

  /** Closes the main frame after having save its state in the Nodus properties file. */
  public void closeAndSaveState() {
    if (nodusProject != null) {
      nodusProject.close(this::finishCloseAndSaveState);
      return;
    }
    finishCloseAndSaveState();
  }

  /** Persists frame/application state and exits once any project close sequence is complete. */
  private void finishCloseAndSaveState() {

    // Save current settings
    Frame mainFrame = getMainFrame();
    int frameWidth = mainFrame.getWidth();
    int frameHeight = mainFrame.getHeight();
    Point p = mainFrame.getLocation();

    if (mainFrame.isShowing()) {
      try {
        p = mainFrame.getLocationOnScreen();
      } catch (IllegalComponentStateException e) {
        p = mainFrame.getLocation();
      }
    }

    // Avoid saving minimized state values (Windows only)
    if (p.x != -32000) {
      nodusProperties.setProperty(NodusC.PROP_FRAME_X, String.valueOf(p.x));
      nodusProperties.setProperty(NodusC.PROP_FRAME_Y, String.valueOf(p.y));
      nodusProperties.setProperty(NodusC.PROP_FRAME_WIDTH, String.valueOf(frameWidth));
      nodusProperties.setProperty(NodusC.PROP_FRAME_HEIGTH, String.valueOf(frameHeight));
    }

    String defaultDbEngine = nodusProperties.getProperty(NodusC.PROP_EMBEDDED_DB, "h2");
    nodusProperties.setProperty(NodusC.PROP_EMBEDDED_DB, defaultDbEngine);

    try {
      NodusPreferences.save(Paths.get(System.getProperty("user.home")), nodusProperties);
    } catch (IOException ex) {
      System.err.println("Unable to save .nodus9.properties: " + ex.getMessage());
    }

    // Run the "nodus.groovy" script if exists
    String scriptFileName = System.getProperty("NODUS_HOME", ".") + "/nodus" + NodusC.TYPE_GROOVY;
    ScriptRunner scriptRunner = new ScriptRunner(scriptFileName);
    scriptRunner.setVariable("nodusMapPanel", this);
    scriptRunner.setVariable("startNodus", false);
    scriptRunner.setVariable("quitNodus", true);
    scriptRunner.runAsync(
        true,
        success -> {
          /*
           * Explicitly dispose application-wide plugins and close their loaders before exiting.
           * Do not rely on Swing disposal here because this method calls System.exit(0).
           */
          pluginManager.disposeAllPlugins();

          setVisible(false);
          System.exit(0);
        });
  }

  /** Copy the content of the MapBean as an image in the clipboard. */
  private void copyMapToClipboard() {
    int width = getMapBean().getWidth();
    int height = getMapBean().getHeight();

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    Graphics g = ge.createGraphics(image);

    try {
      g.setClip(0, 0, width, height);
      getMapBean().paintAll(g);
    } finally {
      g.dispose();
    }

    ImageSelection imgSel = new ImageSelection(image);
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(imgSel, imgSel);
    Toolkit.getDefaultToolkit().beep();
  }

  /** Create the MapPanel and associate some keyboard shortcuts. */
  private void create() {

    setLayout(new BorderLayout());

    try {
      initialize();
    } catch (Exception e) {
      e.printStackTrace();
    }

    onTopKeeper = new OnTopKeeper(this);
    if (getAlwaysOnTop()) {
      onTopKeeper.run(isStickyDrawingTool());
    }

    // MapBean mb = getMapBean();
    mapBean.setBckgrnd(Environment.getCustomBackgroundColor());

    // Navigate with keys in the map
    mapBeanKeyListener = new NodusProjMapBeanKeyListener();
    mapBeanKeyListener.findAndInit(projectionStack);
    mapBeanKeyListener.setMapBean(mapBean);
    mapBean.addKeyListener(mapBeanKeyListener);

    commandKeyListener =
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent evt) {
            // Escape key to interrupt long tasks
            if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
              cancelLongTask();
            }

            // F8 key for test functions (not documented)
            if (evt.getKeyCode() == KeyEvent.VK_F8) {
              callF8();
            }

            // F9 key to test functions (not documented)
            if (evt.getKeyCode() == KeyEvent.VK_F9) {
              callF9();
            }

            // Ctrl key for drawing tool
            if (evt.getKeyCode() == KeyEvent.VK_CONTROL || evt.getKeyCode() == KeyEvent.VK_META) {
              // nodusDrawingTool.setControlPressed(true);
              controlPressed = true;
            }

            // Ctrl key + C
            if ((evt.getKeyCode() == 67 && evt.isControlDown())
                || (evt.getKeyCode() == 67 && evt.isMetaDown())) {
              copyMapToClipboard();
            }
          }

          @Override
          public void keyReleased(KeyEvent evt) {
            // Ctrl key for drawing tool
            if (evt.getKeyCode() == KeyEvent.VK_CONTROL || evt.getKeyCode() == KeyEvent.VK_META) {
              // nodusDrawingTool.setControlPressed(false);
              controlPressed = false;
            }
          }
        };

    // Intercept a few command keys...
    addKeyListener(commandKeyListener);
    getMapBean().addKeyListener(commandKeyListener);

    infoDelegator.setShowWaitCursor(true);

    initializeHelp();
  }

  /** Creates the action listeners to add to the menu items. */
  // @SuppressWarnings("deprecation")
  private void createMenuActionListeners() {

    menus.menuItemFileOpen.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
    menus.menuItemFileOpen.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemFileOpenActionPerformed(e);
          }
        });

    menus.menuItemSystemProperties.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemFileGlobalPreferencesActionPerformed();
          }
        });

    menus.menuItemFileSave.setEnabled(false);
    menus.menuItemFileSave.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
    menus.menuItemFileSave.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemFileSaveActionPerformed(e);
          }
        });

    menus.menuItemFileClose.setEnabled(false);
    menus.menuItemFileClose.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemFileCloseActionPerformed(e);
          }
        });

    menus.menuItemFilePrint.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_P, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
    menus.menuItemFilePrint.setEnabled(false);
    menus.menuItemFilePrint.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemFilePrintActionPerformed(e);
          }
        });

    menus.menuItemFileExit.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

    menus.menuItemFileExit.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemFileExitActionPerformed();
          }
        });

    menus.menuItemProjectModalChoice.addActionListener(
        event -> {
          if (nodusProject.isOpen() && menus.menuItemProjectAssignment.isEnabled()) {
            new ModalChoiceEstimationDlg(this).setVisible(true);
          }
        });
    // Both commands share routing resources and must not run concurrently.
    menus.menuItemProjectAssignment.addPropertyChangeListener(
        "enabled",
        event ->
            menus.menuItemProjectModalChoice.setEnabled(
                menus.menuItemProjectAssignment.isEnabled()));

    menus.menuItemProjectAssignment.setAccelerator(
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, 0));
    menus.menuItemProjectAssignment.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemProjectAssignmentActionPerformed(e);
          }
        });

    menus.menuItemProjectSQLConsole.setAccelerator(
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, 0));
    menus.menuItemProjectSQLConsole.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemProjectSQLConsoleActionPerformed(e);
          }
        });

    menus.menuItemProjectDisplayResults.setAccelerator(
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0));
    menus.menuItemProjectDisplayResults.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemProjectDisplayResultsActionPerformed(e);
          }
        });

    menus.menuItemProjectScenarios.setAccelerator(
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6, 0));
    menus.menuItemProjectScenarios.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemProjectScenariosActionPerformed(e);
          }
        });

    menus.menuItemProjectCosts.setAccelerator(
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0));
    menus.menuItemProjectCosts.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemProjectCostsActionPerformed(e);
          }
        });

    menus.menuItemProjectServices.setAccelerator(
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F3, 0));

    menus.menuItemProjectServices.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemProjectServicesActionPerformed(e);
          }
        });

    menus.menuItemProjectPreferences.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemProjectPreferencesActionPerformed(e);
          }
        });

    menus.menuItemToolLookAndFeel.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemToolLookAndFeelActionPerformed(e);
          }
        });

    menus.menuItemToolLanguage.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemToolLanguageActionPerformed(e);
          }
        });

    menus.menuItemToolConsole.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            String defaultDir = null;
            if (getNodusProject().isOpen()) {
              defaultDir = getNodusProject().getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);
            }
            new NodusConsole(defaultDir);
          }
        });

    menus.menuItemToolGroovyScripts.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemGroovyConsoleActionPerformed(e);
          }
        });

    menus.menuItemToolRessourcesMonitor.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            HardwareUtils.displayRessourcesMonitor();
          }
        });

    menus.menuItemHelpAbout.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemAboutActionPerformed();
          }
        });

    menus.menuItemControlBackground.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemControlBackgroundActionPerformed(e);
          }
        });

    menus.menuItemControlToolpanel.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemControlToolpanelActionPerformed(e);
          }
        });

    menus.menuItemControlControlpanel.addActionListener(
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            menuItemControlControlpanelActionPerformed(e);
          }
        });
  }

  /** Setup the overview map using the embedded dcwpo-browse map data. */
  private void createOverviewMap() {
    overviewMapHandler = controlPanel.getOverviewMapHandler();

    // Create the overview layer, with defaults values (dcwpo-browse)
    Properties p = new Properties();
    try (InputStream in = getClass().getResourceAsStream("overviewMap.properties")) {
      if (in == null) {
        throw new IOException("overviewMap.properties not found");
      }
      p.load(in);
    } catch (IOException ioe) { // Should never happen
      ioe.printStackTrace();
    }

    // Get the filenames of the dcwpo-browser map data
    String shpFileName = getResourceLocation("dcwpo-browse.shp");
    if (shpFileName == null) {
      return;
    }

    p.setProperty("overviewLayer.shapeFile", shpFileName);
    p.setProperty(
        "overviewLayer.prettyName", i18n.get(NodusMapPanel.class, "Overview", "Overview"));

    overviewMapHandler.setProperties("overviewMapHandler", p);
    overviewMapHandler.activateMouseMode();

    getMapHandler().add(overviewMapHandler);
  }

  /** Returns a filesystem path or jar URL suitable for embedded shape resources. */
  private String getResourceLocation(String resourceName) {
    URL resource = getClass().getResource(resourceName);
    if (resource == null) {
      System.err.println("Resource not found: " + resourceName);
      return null;
    }

    if ("jar".equalsIgnoreCase(resource.getProtocol())) {
      return resource.toExternalForm();
    }

    try {
      return Paths.get(resource.toURI()).toString();
    } catch (URISyntaxException | IllegalArgumentException ex) {
      return resource.getPath();
    }
  }

  /**
   * Displays the highlighted area, used to limit the assignment to a given rectangular area.
   *
   * @param added If true, add the layer, else remove it.
   * @param visible If true, display the layer, else hide it.
   */
  public void displayHighlightedAreaLayer(boolean added, boolean visible) {

    // Remove existing if exists
    if (highlightedAreaLayer != null) {
      highlightedAreaLayer.setVisible(false);
      layerHandler.removeLayer(highlightedAreaLayer);
      highlightedAreaLayer = null;
    }

    if (added) {
      highlightedAreaLayer = new HighlightedAreaLayer(nodusProject);
      layerHandler.addLayer(highlightedAreaLayer, 0);
      highlightedAreaLayer.setVisible(visible);
    }

    layerHandler.setLayers();
    nodusLayersPanel.enableButtons(true);
  }

  /**
   * Displays the "build-in" political boundaries.
   *
   * @param added If true, add the layer, else remove it.
   * @param visible If true, display the layer, else hide it.
   */
  public void displayPoliticalBoundaries(boolean added, boolean visible) {

    // Remove existing if exists
    if (politicalBoundariesLayer != null) {
      politicalBoundariesLayer.setVisible(false);
      layerHandler.removeLayer(politicalBoundariesLayer);
      politicalBoundariesLayer = null;
    }

    if (added) {
      politicalBoundariesLayer = PoliticalBoundariesLayer.getLayer(mapBean);
      try {
        layerHandler.addLayer(politicalBoundariesLayer);
        politicalBoundariesLayer.setVisible(visible);
      } catch (Exception e) {
        System.err.println(
            "displayPoliticalBoundaries throws an exception. This should not happen...");
      }
    }

    layerHandler.setLayers();
    nodusLayersPanel.enableButtons(true);
  }

  /**
   * Displays the "scenario" combo box above the MapBean.
   *
   * @param visible If true, display it, else hide it.
   */
  private void displayScenarioCombo(boolean visible) {
    scenarioLabel.setVisible(visible);
    scenarioComboBox.setVisible(visible);
  }

  /** Removes the key listeners installed by {@link #create()}. */
  private void removeRegisteredKeyListeners() {
    if (mapBean != null && mapBeanKeyListener != null) {
      mapBean.removeKeyListener(mapBeanKeyListener);
      mapBeanKeyListener = null;
    }

    if (commandKeyListener != null) {
      removeKeyListener(commandKeyListener);

      if (mapBean != null) {
        mapBean.removeKeyListener(commandKeyListener);
      }

      commandKeyListener = null;
    }
  }

  /** Sets the MapBean variable to null and removes all children. */
  @Override
  public void dispose() {
    pluginManager.disposeAllPlugins();
    removeRegisteredKeyListeners();

    if (onTopKeeper != null) {
      onTopKeeper.stop();
      onTopKeeper = null;
    }

    setMapBean(null);
    setLayout(null);
    removeAll();
  }

  /**
   * Enable/Disable the menu items that are accessible only when a project is loaded.
   *
   * @param state boolean
   */
  public void enableMenus(boolean state) {
    Runnable updateMenus =
        new Runnable() {
          @Override
          public void run() {
            // Enable some menu items
            menus.menuProject.setEnabled(state);
            menus.menuProject.setVisible(state);
            pluginManager.enableMenus(state);

            menus.menuControl.setEnabled(state);
            menus.menuControl.setVisible(state);
            menus.menuProjection.setEnabled(state);
            menus.menuProjection.setVisible(state);

            // In the "File" menu, not all items must be disables/enabled
            menus.menuItemFileOpen.setEnabled(true);
            menus.menuItemFileSave.setEnabled(state);
            menus.menuItemFileClose.setEnabled(state);
            menus.menuItemFileSaveAs.setEnabled(state);
            menus.menuItemFilePrint.setEnabled(state);

            menus.menuFile.setEnabled(true);
            menus.menuFile.setVisible(true);
            refreshMenuBarUI();
          }
        };

    if (javax.swing.SwingUtilities.isEventDispatchThread()) {
      updateMenus.run();
    } else {
      javax.swing.SwingUtilities.invokeLater(updateMenus);
    }
  }

  /**
   * Temporarily disables the actions in the File menu without disabling the top-level menu itself.
   *
   * <p>This is important on macOS when using the native screen menu bar, where disabling the whole
   * {@link JMenu} can leave it visually grayed after it is re-enabled.
   *
   * @param busy true while a project is opening/closing, false to restore the regular state
   */
  public void setFileMenuBusy(boolean busy) {
    Runnable updateMenus =
        new Runnable() {
          @Override
          public void run() {
            menus.menuFile.setEnabled(true);
            menus.menuFile.setVisible(true);

            boolean projectOpen = nodusProject != null && nodusProject.isOpen();
            boolean enabled = !busy;

            menus.menuItemFileOpen.setEnabled(enabled);
            menus.menuItemFileSave.setEnabled(enabled && projectOpen);
            menus.menuItemFileClose.setEnabled(enabled && projectOpen);
            menus.menuItemFileSaveAs.setEnabled(enabled && projectOpen);
            menus.menuItemFilePrint.setEnabled(enabled && projectOpen);

            if (menus.menuItemFileExit != null) {
              menus.menuItemFileExit.setEnabled(enabled);
            }

            refreshMenuBarUI();
          }
        };

    if (javax.swing.SwingUtilities.isEventDispatchThread()) {
      updateMenus.run();
    } else {
      javax.swing.SwingUtilities.invokeLater(updateMenus);
    }
  }

  /** Forces Swing and the host window system to refresh the menu bar state. */
  private void refreshMenuBarUI() {
    menus.nodusMenuBar.revalidate();
    menus.nodusMenuBar.repaint();

    Frame mainFrame = getMainFrame();
    if (mainFrame instanceof JFrame) {
      JFrame frame = (JFrame) mainFrame;
      if (frame.getJMenuBar() != menus.nodusMenuBar) {
        frame.setJMenuBar(menus.nodusMenuBar);
      }
      if (frame.getJMenuBar() != null) {
        frame.getJMenuBar().revalidate();
        frame.getJMenuBar().repaint();
      }
    }

    if (mainFrame != null) {
      mainFrame.invalidate();
      mainFrame.repaint();
    }
  }

  /** Brings the main frame back to the foreground after modal dialogs are closed. */
  public void restoreMainFrameFocus() {
    Runnable restoreFocus =
        new Runnable() {
          @Override
          public void run() {
            Frame mainFrame = getMainFrame();
            if (mainFrame == null) {
              return;
            }

            mainFrame.toFront();
            mainFrame.requestFocus();
            getMapBean().requestFocus();

            // Toggling always-on-top is a pragmatic way to make macOS re-activate the window.
            boolean alwaysOnTop = mainFrame.isAlwaysOnTop();
            mainFrame.setAlwaysOnTop(true);
            mainFrame.setAlwaysOnTop(alwaysOnTop);
          }
        };

    if (javax.swing.SwingUtilities.isEventDispatchThread()) {
      restoreFocus.run();
    } else {
      javax.swing.SwingUtilities.invokeLater(restoreFocus);
    }
  }

  /**
   * Returns the active mouse mode ID (See OpenMap API for more information).
   *
   * @return The mouse mode ID.
   */
  public String getActiveMouseMode() {
    return mouseDelegator.getActiveMouseModeID();
  }

  /**
   * Returns true if the subframes must always remain on top.
   *
   * @return True if the subframes must remain on top.
   */
  public boolean getAlwaysOnTop() {
    String str = nodusProperties.getProperty(NodusC.PROP_SUBFRAMES_ALWAYS_ON_TOP, "true");
    return Boolean.parseBoolean(str);
  }

  /**
   * Returns the "Assignment" menu item.
   *
   * @return The "Assignment" menu item.
   */
  public JMenuItem getAssignmentMenuItem() {
    return menus.menuItemProjectAssignment;
  }

  /**
   * Returns the default background color used by OpenMap.
   *
   * @return Color
   */
  private Color getDefaultBackgroundColor() {
    return defaultBackgroundColor;
  }

  /**
   * Returns true if the full path of the project must be displayed in the title bar.
   *
   * @return True if the full path of the project must be displayed in the title bar.
   */
  public boolean getDisplayFullPath() {
    String str = nodusProperties.getProperty(NodusC.PROP_DISPLAY_FULL_PATH, "false");
    return Boolean.parseBoolean(str);
  }

  /**
   * Returns the interval used for garbage collection during assignments.
   *
   * @return The GC interval expressed in seconds.
   */
  public int getGarbageCollectorInterval() {
    int interval;
    String str = nodusProperties.getProperty(NodusC.PROP_GC_INTERVAL, "0");
    try {
      interval = Integer.parseInt(str);
    } catch (NumberFormatException e) {
      return 0;
    }
    return interval;
  }

  /**
   * Accessor for the highlighted area.
   *
   * @return The highlighted area.
   */
  public HighlightedAreaLayer getHighlightedAreaLayer() {
    return highlightedAreaLayer;
  }

  /**
   * Accessor for the LayerHandler.
   *
   * @return LayerHandler
   */
  public LayerHandler getLayerHandler() {
    return layerHandler;
  }

  /**
   * Returns true while one or more long-running tasks keep the UI busy.
   *
   * @return True if the UI is busy, false otherwise.
   */
  public boolean isBusy() {
    return progress.isBusy();
  }

  /**
   * Returns the main frame of the application.
   *
   * @return The main frame of the application.
   */
  public synchronized Frame getMainFrame() {
    OpenMapFrame omf = getMapHandler().get(com.bbn.openmap.gui.OpenMapFrame.class);
    return omf;
  }

  /**
   * Accessor for the MapBean.
   *
   * @return MapBean
   */
  @Override
  public MapBean getMapBean() {
    if (mapBean == null) {

      MapBean.suppressCopyright = true;

      mapBean = new BufferedMapBean();
      mapBean.setBorder(BorderFactory.createEtchedBorder(BevelBorder.LOWERED));

      Projection proj = new ProjectionFactory().getDefaultProjectionFromEnvironment();
      mapBean.setProjection(proj);

      mapBean.setPreferredSize(new Dimension(proj.getWidth(), proj.getHeight()));
      defaultMapBeanRepaintPolicy = mapBean.getMapBeanRepaintPolicy();

      setMapBean(mapBean);
      setAntialising();
    }

    return mapBean;
  }

  /**
   * Accessor for the MapHandler.
   *
   * @return MapHandler
   */
  @Override
  public MapHandler getMapHandler() {
    if (mapHandler == null) {
      mapHandler = new MapHandler();
    }
    return mapHandler;
  }

  /**
   * MapPanel method. Get a JMenu containing sub-menus created from properties.
   *
   * @hidden
   */
  @Override
  public JMenu getMapMenu() {
    return null;
  }

  /**
   * MapPanel method. Get a JMenuBar containing menus created from properties.
   *
   * @hidden
   */
  @Override
  public JMenuBar getMapMenuBar() {
    return null;
  }

  /**
   * Accessor for the "File" menu.
   *
   * @return The "File" menu.
   */
  public JMenu getMenuFile() {
    return menus.menuFile;
  }

  /**
   * Accessor for the drawing tool used to edit nodes and links.
   *
   * @return The drawing tool.
   */
  public NodusOMDrawingTool getNodusDrawingTool() {
    return nodusDrawingTool;
  }

  /**
   * Accessor for the drawing tool launcher.
   *
   * @return NodusOMDrawingToolLauncher
   */
  public NodusOMDrawingToolLauncher getNodusDrawingToolLauncher() {
    return nodusDrawingToolLauncher;
  }

  /**
   * Accessor for the layers panel.
   *
   * @return The layers panel.
   */
  public NodusLayersPanel getNodusLayersPanel() {
    return nodusLayersPanel;
  }

  /**
   * Accessor to get the Nodus project.
   *
   * @return The Nodus project.
   */
  public NodusProject getNodusProject() {
    return nodusProject;
  }

  /**
   * Returns the properties used by the application.
   *
   * @return The Nodus global properties.
   */
  public Properties getNodusProperties() {
    return nodusProperties;
  }

  /**
   * Accessor for the projection stack.
   *
   * @return ProjectionStack
   */
  public ProjectionStack getProjectionStack() {
    return projectionStack;
  }

  /**
   * Returns the scale under which the nodes and link styles are fully rendered.
   *
   * @return The scale threshold.
   */
  public float getRenderingScaleThreshold() {
    return renderingScaleThresold;
  }

  /**
   * Accessor for the sound player.
   *
   * @return The sound player.
   */
  public SoundPlayer getSoundPlayer() {
    return soundPlayer;
  }

  /**
   * Accessor for the tool panel.
   *
   * @return The tool panel
   */
  public ToolPanel getToolPanel() {
    return toolPanel;
  }

  /** Initializes the projections menu. */
  private void initProjections() {
    /*
     * Add a set of possible projections. <br> See OpenMap documentation for more details on the
     * ProjectionLoader mechanism.
     */

    LLXYLoader llxyl = new LLXYLoader();
    MercatorLoader mercatorl = new MercatorLoader();
    CADRGLoader cadrgl = new CADRGLoader();
    OrthographicLoader orthol = new OrthographicLoader();
    GnomonicLoader gnomonicl = new GnomonicLoader();
    EqualEarthLoader equalEarthl = new EqualEarthLoader();

    Vector<ProjectionLoader> loaders = new Vector<>();
    loaders.add(llxyl);
    loaders.add(mercatorl);
    loaders.add(cadrgl);
    loaders.add(orthol);
    loaders.add(gnomonicl);
    loaders.add(equalEarthl);

    registerProjectionLoaders(loaders);
    menus.menuProjection.configure(loaders);
    menus.menuProjection.setProjectionFactory(mapBean.getProjectionFactory());

    menus.menuProjection.findAndInit(mapBean);

    // Set default projection
    Projection projection = mapBean.getProjection();
    Point2D ctr = projection.getCenter();
    Projection newProj =
        getMapBean()
            .getProjectionFactory()
            .makeProjection(
                Mercator.class.getName(),
                ctr,
                projection.getScale(),
                projection.getWidth(),
                projection.getHeight());
    mapBean.setProjection(newProj);
  }

  /**
   * Registers the projection loaders used by the menu in the MapBean's projection factory.
   *
   * @param loaders projection loaders to register
   */
  private void registerProjectionLoaders(Vector<ProjectionLoader> loaders) {
    ProjectionFactory projectionFactory = mapBean.getProjectionFactory();
    for (ProjectionLoader loader : loaders) {
      boolean alreadyRegistered = false;
      for (ProjectionLoader registeredLoader : projectionFactory.getProjectionLoaders()) {
        if (registeredLoader.getProjectionClass() == loader.getProjectionClass()) {
          alreadyRegistered = true;
          break;
        }
      }

      if (!alreadyRegistered) {
        projectionFactory.addProjectionLoader(loader);
      }
    }
  }

  /**
   * Real initialization of the GUI components. See also OpenMap documentation for more details on
   * the OpenMap specific components.
   *
   * @throws Exception on error during GUI initialization
   */
  private void initialize() throws Exception {

    // Initialize sound system
    boolean sound = Boolean.parseBoolean(nodusProperties.getProperty(NodusC.PROP_SOUND, "true"));
    soundPlayer = new SoundPlayer(sound);

    // Create the openMap components
    initOpenMapComponents();

    // Initialize the defaut XY projection
    initProjections();

    // Create all the menus
    menus.initMenus();

    // Add the plugins, if any
    nodusHomeDir = System.getProperty("NODUS_HOME", ".");
    loadPlugins(nodusHomeDir + "/plugins", false);

    getMapHandler().add(menus.nodusMenuBar);

    // Prepare project
    enableMenus(false);

    nodusProject = new NodusProject(this);

    resetMap();
    if (!deferDefaultPoliticalBoundaries) {
      displayPoliticalBoundaries(true, true);
    }

    JButton fakeButton = new JButton();
    JLabel fakeLabel = new JLabel();
    fakeLabel.setPreferredSize(new Dimension(50, fakeButton.getPreferredSize().height));
    toolPanel.add(
        fakeLabel,
        new GridBagConstraints(
            2,
            0,
            1,
            1,
            1.0,
            1.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 0, 0, 0),
            0,
            0));
    JPanel scenarioPanel = new JPanel(new GridBagLayout());

    scenarioLabel = new JLabel(i18n.get(NodusMapPanel.class, "Scenario", "Scenario"));

    scenarioPanel.add(
        scenarioLabel,
        new GridBagConstraints(
            1,
            0,
            1,
            1,
            1.0,
            1.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 0, 0, 0),
            0,
            0));

    scenarioComboBox = new JComboBox<>();

    scenarioPanel.add(
        scenarioComboBox,
        new GridBagConstraints(
            2,
            0,
            1,
            1,
            50.0,
            50.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 0, 0, 0),
            0,
            0));

    toolPanel.add(
        scenarioPanel,
        new GridBagConstraints(
            3,
            0,
            1,
            1,
            100.0,
            100.0,
            GridBagConstraints.EAST,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 0, 0, 0),
            0,
            0));

    displayScenarioCombo(false);

    scenarioComboBox.addActionListener(
        new ActionListener() {
          // Set the current scenario
          @Override
          public void actionPerformed(ActionEvent e) {
            // Get the scenario number and update
            String item = (String) scenarioComboBox.getSelectedItem();
            int n = Integer.parseInt(item.substring(0, item.indexOf("-")).trim());
            nodusProject.setLocalProperty(NodusC.PROP_SCENARIO, n);
            updateScenarioComboBox(false);

            // Update cost function name
            String fileName = nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + n, null);
            if (fileName != null) {
              nodusProject.setLocalProperty(NodusC.PROP_COST_FUNCTIONS, fileName);
            }
          }
        });
  }

  /** Initializes the help system. */
  private void initializeHelp() {

    // Nodus help
    menus.menuItemHelpHelp.setText(i18n.get(NodusMapPanel.class, "Help", "Help"));
    menus.menuItemHelpHelp.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
    menus.menuItemHelpHelp.addActionListener(
        new ActionListener() {

          @Override
          public void actionPerformed(ActionEvent e) {
            if (helpBrowser == null) {
              helpBrowser = new HelpBrowser();
            }
            helpBrowser.launchBrowser(true);
          }
        });
    menus.menuHelp.add(menus.menuItemHelpHelp);

    // API JavaDoc help
    menus.menuItemHelpApiDoc.setText(i18n.get(NodusMapPanel.class, "API_Doc", "API Javadoc"));
    menus.menuItemHelpApiDoc.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (javaDocBrowser == null) {
              javaDocBrowser = new HelpBrowser();
            }
            javaDocBrowser.launchBrowser(false);
          }
        });
    menus.menuHelp.add(menus.menuItemHelpApiDoc);
  }

  /** Initialize the openMap components used in Nodus. */
  private void initOpenMapComponents() {

    // Initialize map handler
    getMapHandler().add(layerHandler);
    getMapHandler().add(projectionStack);

    getMapHandler().add(mouseDelegator);

    // Add tool panel
    getMapHandler().add(toolPanel);
    add(toolPanel, BorderLayout.NORTH);

    // Add mouse modes
    MouseModeButtonPanel mmbp = new MouseModeButtonPanel();
    getMapHandler().add(mmbp);
    SelectMouseMode selectMouseMode = new SelectMouseMode();
    getMapHandler().add(selectMouseMode);
    PanMouseMode panMouseMode = new PanMouseMode();
    getMapHandler().add(panMouseMode);
    DistanceMouseMode distanceMouseMode = new DistanceMouseMode();
    distanceMouseMode.setUnit(Length.KM);
    getMapHandler().add(distanceMouseMode);
    addNavMouseMode();

    // Add info delegator
    getMapHandler().add(infoDelegator);
    add(infoDelegator, BorderLayout.SOUTH);

    // Add control panel with specific NodusLayersPanel
    nodusLayersPanel = new NodusLayersPanel(this);
    controlPanel.setLayersPanel(nodusLayersPanel);

    getMapHandler().add(controlPanel);

    // Create overview map
    createOverviewMap();

    add(controlPanel, BorderLayout.WEST);

    // Add drawing tool
    nodusDrawingToolLauncher = new NodusOMDrawingToolLauncher(this);
    nodusDrawingTool = new NodusOMDrawingTool(this, nodusDrawingToolLauncher);
    nodusDrawingTool.addLoader(new NodusOMPointLoader());
    nodusDrawingTool.addLoader(new NodusOMPolyLoader());
    getMapHandler().add(nodusDrawingTool);
    getMapHandler().add(nodusDrawingToolLauncher);

    // Add and hide a drawing tool mouse mode (the drawing layer is also invisible)
    OMDrawingToolMouseMode dtmm = new OMDrawingToolMouseMode();
    dtmm.setVisible(false);
    getMapHandler().add(dtmm);

    // Background color
    defaultBackgroundColor = getMapBean().getBackground();
  }

  /**
   * Returns true if the control key is pressed.
   *
   * @return True if pressed.
   */
  public boolean isControlPressed() {
    return controlPressed;
  }

  /**
   * Returns true if the highlighted area layer is added to the list of layers.
   *
   * @return True if the layer is present.
   */
  public boolean isHighlightedAreaLayerAdded() {
    if (highlightedAreaLayer == null) {
      return false;
    }
    return true;
  }

  /**
   * Returns true if the highlighted area layer is visible.
   *
   * @return True if the layer is visible.
   */
  public boolean isHighlightedAreaLayerVisible() {
    if (highlightedAreaLayer == null) {
      return false;
    } else {
      return highlightedAreaLayer.isVisible();
    }
  }

  /**
   * Returns true if the embedded political boundaries layer is added to the list of layers.
   *
   * @return True if the layer is present.
   */
  public boolean isPoliticalBoundariesAdded() {
    if (politicalBoundariesLayer == null) {
      return false;
    }
    return true;
  }

  /**
   * Returns true if the embedded political boundaries layer is visible.
   *
   * @return True if the layer is visible.
   */
  public boolean isPoliticalBoundariesVisible() {
    if (politicalBoundariesLayer == null) {
      return false;
    } else {
      return politicalBoundariesLayer.isVisible();
    }
  }

  /**
   * Returns true if the drawing tool must stay on the left bottom corner of the main window.
   *
   * @return True if the drawing tool must remain in the corner.
   */
  private boolean isStickyDrawingTool() {
    String str = nodusProperties.getProperty(NodusC.PROP_STICKY_DRAWING_TOOL, "false");
    return Boolean.parseBoolean(str);
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
  public void loadPlugins(String dir, boolean projectPlugin) {
    pluginManager.loadPlugins(dir, projectPlugin);
  }

  /** "About Nodus" splash screen and info. */
  public void menuItemAboutActionPerformed() {
    SplashDlg splashDialog = new SplashDlg(this);
    splashDialog.setLocationRelativeTo(this);
    splashDialog.setVisible(true);
  }

  /**
   * Change the MapBean's background color...
   *
   * @param e ActionEvent
   */
  private void menuItemControlBackgroundActionPerformed(ActionEvent e) {
    Paint newPaint =
        OMColorChooser.showDialog(
            this, menus.menuItemControlBackground.getText(), getMapBean().getBackground());

    if (newPaint != null) {
      String colorString = Integer.toString(((java.awt.Color) newPaint).getRGB());
      Environment.set(Environment.BackgroundColor, colorString);
      getMapBean().setBackground((java.awt.Color) newPaint);
      getMapBean().setBckgrnd(newPaint);
    }
  }

  /**
   * Toggle the visibility of the ControlPanel.
   *
   * @param e ActionEvent
   */
  private void menuItemControlControlpanelActionPerformed(ActionEvent e) {
    boolean selected = controlPanel.isVisible();
    controlPanel.setVisible(!selected);

    if (selected) {
      menus.menuItemControlControlpanel.setText(
          i18n.get(NodusMapPanel.class, "Display_Control_Panel", "Display Control Panel"));

    } else {
      menus.menuItemControlControlpanel.setText(
          i18n.get(NodusMapPanel.class, "Hide_Control_Panel", "Hide Control Panel"));
    }
  }

  /**
   * Toggle the visibility of the Toolpanel.
   *
   * @param e ActionEvent
   */
  private void menuItemControlToolpanelActionPerformed(ActionEvent e) {
    boolean selected = toolPanel.isVisible();
    toolPanel.setVisible(!selected);

    if (selected) {
      menus.menuItemControlToolpanel.setText(
          i18n.get(NodusMapPanel.class, "Display_Tool_Panel", "Display Tool Panel"));

    } else {
      menus.menuItemControlToolpanel.setText(
          i18n.get(NodusMapPanel.class, "Hide_Tool_Panel", "Hide Tool Panel"));
    }
  }

  /**
   * Close a open project.
   *
   * @param e ActionEvent
   */
  private void menuItemFileCloseActionPerformed(ActionEvent e) {
    if (nodusProject != null) {
      nodusProject.close(
          () -> {
            displayScenarioCombo(false);
            displayPoliticalBoundaries(true, true);
          });
    }
  }

  /** Tasks that must be performed when the application is closed. */
  public void menuItemFileExitActionPerformed() {
    if (projectHasUnsavedLayerChanges()
        || projectWillAskForCompactOnClose()
        || confirmQuit(getMainFrame())) {
      closeAndSaveState();
    }
  }

  /**
   * Returns true if the open project has unsaved layer changes.
   *
   * @return true if a project is open and dirty
   */
  private boolean projectHasUnsavedLayerChanges() {
    return nodusProject != null && nodusProject.isOpen() && nodusProject.isDirty();
  }

  /**
   * Returns true if closing the current project will already ask about database compaction.
   *
   * @return true if the compact database prompt will be displayed
   */
  private boolean projectWillAskForCompactOnClose() {
    return nodusProject != null && nodusProject.willAskForCompactOnClose();
  }

  /**
   * Asks the user to confirm the application quit when the global preference is enabled.
   *
   * @param parent The parent component.
   * @return true if quitting can continue.
   */
  private boolean confirmQuit(Component parent) {
    boolean confirmQuit =
        Boolean.parseBoolean(nodusProperties.getProperty(NodusC.PROP_CONFIRM_QUIT, "true"));
    if (!confirmQuit) {
      return true;
    }

    int result =
        JOptionPane.showConfirmDialog(
            parent,
            i18n.get(Nodus.class, "Confirm_quit", "Do you really want to quit Nodus?"),
            i18n.get(Nodus.class, "Quit_Nodus", "Quit Nodus"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

    return result == JOptionPane.YES_OPTION;
  }

  /** Opens the global preferences dialog box. */
  public void menuItemFileGlobalPreferencesActionPerformed() {
    GlobalPreferencesDlg dlg = new GlobalPreferencesDlg(this);
    dlg.setVisible(true);
  }

  /**
   * Open a new project.
   *
   * @param e ActionEvent
   */
  private void menuItemFileOpenActionPerformed(ActionEvent e) {
    javax.swing.SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            String projectName = nodusProject.getProject();
            if (projectName != null) {
              openProject(projectName);
            }
          }
        });
  }

  /**
   * Print the content of the current MapBean.
   *
   * @param e ActionEvent
   */
  private void menuItemFilePrintActionPerformed(ActionEvent e) {
    MapBeanPrinter.printMap(getMapBean());
  }

  /**
   * Save the project and update/commit into the database.
   *
   * @param e ActionEvent.
   */
  private void menuItemFileSaveActionPerformed(ActionEvent e) {
    nodusProject.saveEsriLayersSafely();
  }

  /**
   * Launch Groovy console.
   *
   * @param e ActionEvent
   */
  private void menuItemGroovyConsoleActionPerformed(ActionEvent e) {

    String path = "";
    if (nodusProject.isOpen()) {
      path = nodusProject.getLocalProperty("project.path");
    }

    boolean useGroovyConsole =
        Boolean.parseBoolean(nodusProperties.getProperty(NodusC.PROP_USE_GROOVY_CONSOLE, "false"));

    if (!useGroovyConsole) {
      new NodusGroovyConsole(this, path, "");
    } else {
      nativeConsole.show(path);
    }
  }

  /**
   * Opens the assignment dialog that will launch a chosen assignment procedure.
   *
   * @param a ActionEvent
   */
  public void menuItemProjectAssignmentActionPerformed(ActionEvent a) {

    if (nodusProject.isOpen()) {
      JDialog dlg = new AssignmentDlg(this);
      javax.swing.SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              dlg.setVisible(true);
              getMapBean().requestFocus();
            }
          });
    }
  }

  /**
   * Opens the cost function file in a text editor (embedded Notepad).
   *
   * @param e ActionEvent
   */
  private void menuItemProjectCostsActionPerformed(ActionEvent e) {
    if (nodusProject.isOpen()) {
      // Retrieve the cost function file name for the current scenario
      int currentScenario = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0);
      String fileName = nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS + currentScenario);
      if (fileName == null) {
        // There is no scenario specific cost function file
        String defaultValue =
            nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.TYPE_COSTS;
        fileName = nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS, defaultValue);
      }
      String path = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH);

      new NotePad(this, path, fileName);
    }
  }

  /**
   * Opens the "Display results" dialog box, that makes it possible to visualize volumes, paths, ...
   *
   * @param e ActionEvent
   */
  private void menuItemProjectDisplayResultsActionPerformed(ActionEvent e) {
    if (nodusProject.isOpen()) {

      ResultsDlg dlg = new ResultsDlg(this);
      javax.swing.SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              dlg.setVisible(true);
            }
          });
    }
  }

  /**
   * Opens the "Project preferences" dialog box.
   *
   * @param e ActionEvent
   */
  private void menuItemProjectPreferencesActionPerformed(ActionEvent e) {
    if (nodusProject.isOpen()) {
      JDialog dlg = new ProjectPreferencesDlg(nodusProject);
      dlg.setVisible(true);
    }
  }

  /**
   * Opens the "Compare scenarios" dialog box that makes it possible to generate a new scenario that
   * represents the differences between two existing scenarios.
   *
   * @param e ActionEvent
   */
  private void menuItemProjectScenariosActionPerformed(ActionEvent e) {
    if (nodusProject.isOpen()) {

      ScenariosDlg dlg = new ScenariosDlg(this);
      javax.swing.SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              dlg.setVisible(true);
            }
          });
    }
  }

  /**
   * Opens the service editor.
   *
   * @param e Action event
   */
  private void menuItemProjectServicesActionPerformed(ActionEvent e) {
    if (nodusProject.isOpen()) {
      getNodusProject().getServiceHandler().showGUI();
    }
  }

  /**
   * Opens the SQL console on the database that contains the open project.
   *
   * @param a ActionEvent
   */
  private void menuItemProjectSQLConsoleActionPerformed(ActionEvent a) {
    if (nodusProject.isOpen()) {
      // new SQLConsole(nodusProject);
      javax.swing.SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              new SQLConsole(nodusProject);
            }
          });
    }
  }

  /**
   * Allows the user to make a choice between the available languages.
   *
   * @param e ActionEvent
   */
  private void menuItemToolLanguageActionPerformed(ActionEvent e) {

    new LanguageChooser(this).setVisible(true);
  }

  /**
   * Allows the user to make a choice between the available and supported PLAFs.
   *
   * @param e ActionEvent
   */
  private void menuItemToolLookAndFeelActionPerformed(ActionEvent e) {
    new LookAndFeelChooser(this).setVisible(true);
  }

  /**
   * Loads a project.
   *
   * @param projectName The project file name (with full path)
   */
  public void openProject(String projectName) {
    if (projectName != null) {
      Runnable openTask =
          () -> {
            displayScenarioCombo(true);
            try {
              nodusProject.openProject(projectName);
            } catch (OutOfMemoryError e) {
              // Free memory and force garbage collection
              Layer[] layer = getLayerHandler().getLayers();
              for (int i = 0; i < layer.length; i++) {
                layer[i] = null;
              }
              System.gc();

              JOptionPane.showMessageDialog(
                  nodusProject.getNodusMapPanel(),
                  i18n.get(
                      NodusMapPanel.class,
                      "Out_of_memory",
                      "Out of memory. Increase JVM Heap size in launcher script"),
                  NodusC.APPNAME,
                  JOptionPane.ERROR_MESSAGE);

              nodusProject.getNodusMapPanel().closeAndSaveState();
              // System.exit(0);
            }
          };

      if (nodusProject.isOpen()) {
        nodusProject.close(openTask); // In case a project was already loaded
      } else {
        openTask.run();
      }
    }
  }

  /**
   * Removes project plugin menu items and disposes the associated project plugin instances.
   *
   * <p>This method is called when a project is closed. It gives project plugins a deterministic
   * cleanup point for listeners, timers, threads, windows, and other resources.
   */
  public void removeProjectPlugins() {
    pluginManager.removeProjectPlugins();
  }

  /** Resets the "display results" state of the layers. */
  private void resetResults(NodusEsriLayer[] layers) {

    for (NodusEsriLayer element : layers) {

      // Reset the user defined attribute of each graphic
      EsriGraphicList egl = element.getEsriGraphicList();
      Iterator<OMGraphic> it = egl.iterator();
      while (it.hasNext()) {
        OMGraphic omg = it.next();
        RealNetworkObject rn = (RealNetworkObject) omg.getAttribute(0);
        if (rn != null) {
          rn.setResult(0.0);
        }
      }

      element.setDisplayResults(false);
      element.getLocationHandler().setDisplayResults(false);
      element.getLocationHandler().reloadData();
      element.applyWhereFilter(element.getWhereStmt());
      element.attachStyles();
      element.doPrepare();
    }

    // Is a label layer present?
    LabelLayer labelLayer = null;
    Layer[] l = getLayerHandler().getLayers();
    for (Layer element : l) {
      if (element instanceof LabelLayer) {
        labelLayer = (LabelLayer) element;
        break;
      }
    }
    if (labelLayer != null) {
      labelLayer.setLabelText("");
      labelLayer.doPrepare();
    }
  }

  /** Resets/clears the default map. */
  public void resetMap() {
    Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
    float scale = NodusC.SCALE_FACTOR / (float) (dim.getWidth() / NodusC.DEFAULT_SCREEN_WIDTH);
    mapBean.setScale(scale);

    mapBean.setCenter(NodusC.MAPBEAN_CENTER_X, NodusC.MAPBEAN_CENTER_Y);
    mapBean.setBackground(getDefaultBackgroundColor());
    getProjectionStack().clearStacks(true, true);
  }

  /** Resets the text in the info delegator (see OpenMap API for more information). */
  public void resetText() {
    setText("");
  }

  /** Resets the title of the main frame. */
  public void resetTitle() {
    getMainFrame().setTitle(NodusC.APPNAME);
  }

  /**
   * Restores the size and location of the main frame, depending on the information saved in the
   * Nodus properties file.
   */
  public void restoreSizeAndLocation() {
    // Restore last saved size and position
    int frameWidth =
        PropUtils.intFromProperties(nodusProperties, NodusC.PROP_FRAME_WIDTH, Integer.MIN_VALUE);
    int frameHeigth =
        PropUtils.intFromProperties(nodusProperties, NodusC.PROP_FRAME_HEIGTH, Integer.MIN_VALUE);
    int x = PropUtils.intFromProperties(nodusProperties, NodusC.PROP_FRAME_X, Integer.MIN_VALUE);
    int y = PropUtils.intFromProperties(nodusProperties, NodusC.PROP_FRAME_Y, Integer.MIN_VALUE);

    if (x != Integer.MIN_VALUE
        && y != Integer.MIN_VALUE
        && frameWidth != Integer.MIN_VALUE
        && frameHeigth != Integer.MIN_VALUE) {

      getMainFrame().setSize(frameWidth, frameHeigth);
      getMainFrame().setLocation(x, y);

      validate();
    }

    projectionStack.clearStacks(true, true);
  }

  /**
   * Launches (or stops) the "on top keeper" that forces the sub windows to remain on top of main
   * frame.
   *
   * @param run If true, launched the mechanism. Else stops it.
   * @param stickyDrawingTool If true, the drawing tool will be "sticked" at the bottom left of the
   *     main frame.
   */
  public void runOnTopKeeper(boolean run, boolean stickyDrawingTool) {
    if (onTopKeeper != null) {
      if (!run) {
        onTopKeeper.stop();
      } else {
        onTopKeeper.run(stickyDrawingTool);
      }
    }
  }

  /**
   * Sets the active mouse mode (see OpenMap API for more information).
   *
   * @param modeId The ID of the mode mouse.
   */
  public void setActiveMouseMode(String modeId) {

    try {
      mouseDelegator.setActiveMouseModeWithID(modeId);
      Cursor c = mouseDelegator.getActiveMouseMode().getModeCursor();
      mapBean.setCursor(c);
    } catch (Exception e) {
      // Invalid mouse mode ID. Do nothing
    }
  }

  /** Set antialiasing on or off, depending on the value of stored in the Properties. */
  public void setAntialising() {

    String value = getNodusProperties().getProperty(NodusC.PROP_ANTIALIASING, "true");
    boolean antialisaing = Boolean.parseBoolean(value);

    if (antialisaing) {
      RenderingHints rh =
          new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      RenderingHintsRenderPolicy hints = new RenderingHintsRenderPolicy();
      hints.setRenderingHints(rh);
      HintsMapBeanRepaintPolicy hmbrp = new HintsMapBeanRepaintPolicy(mapBean);
      hmbrp.setHints(hints);
      mapBean.setMapBeanRepaintPolicy(hmbrp);
    } else {
      mapBean.setMapBeanRepaintPolicy(defaultMapBeanRepaintPolicy);
    }

    // Repaint all layers that can be affected by antialising
    Layer[] layers = layerHandler.getLayers();
    for (int i = 0; i < layers.length; i++) {

      if (layers[i] instanceof OMGraphicHandlerLayer) {
        OMGraphicHandlerLayer l = (OMGraphicHandlerLayer) layers[i];
        if (l.isEnabled() && l.isVisible()) {
          l.doPrepare();
          // System.out.println(l.getName());
        }
      }
    }
  }

  /**
   * Sets the wait cursor in the MapPanel.
   *
   * @param busy If true, set the wait cursor, else sets the default cursor.
   */
  public void setBusy(boolean busy) {
    progress.setBusy(busy);
  }

  /** Set application preferences. */
  private void setGlobalPreferencesMenu() {
    if (useMacDesktopIntegration() && desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
      desktop.setPreferencesHandler(e -> menuItemFileGlobalPreferencesActionPerformed());
    } else if (menus.menuItemSystemProperties.getParent() == null) {
      menus.menuFile.add(menus.menuItemSystemProperties);
    }
  }

  /**
   * Sets the map bean used in this map panel, replace the map bean in the MapHandler if there isn't
   * already one, or if the policy allows replacement. The MapHandler will be created if it doesn't
   * exist via a getMapHandler() method call.
   *
   * @param bean The MapBean to set.
   */
  public void setMapBean(MapBean bean) {
    if (bean == null && mapBean != null) {
      // remove the current MapBean from the application...
      getMapHandler().remove(mapBean);
    }

    mapBean = bean;

    if (mapBean != null) {
      getMapHandler().add(mapBean);
      add(mapBean, BorderLayout.CENTER);
    }
  }

  /**
   * When double-clicking on the "scale" component of the control panel, a scale threshold can be
   * set. When the zoom level results is a lower scale, the nodes and links styles are rendered.
   * With a higher level zoom threshold, only the color of the edges of the nodes or links are
   * rendered.
   *
   * @param renderingScaleThresold The scale under which nodes and links are fully rendered.
   */
  public void setRenderingScaleThreshold(float renderingScaleThresold) {
    this.renderingScaleThresold = renderingScaleThresold;
    controlPanel.refreshScale();

    if (nodusProject.isOpen()) {
      nodusProject.setLocalProperty(NodusC.PROP_RENDERING_SCALE_THRESHOLD, renderingScaleThresold);

      // Refresh all the Nodus layers
      NodusEsriLayer[] nel = nodusProject.getNodeLayers();
      for (int i = 0; i < nel.length; i++) {
        nel[i].doPrepare();
      }
      nel = nodusProject.getLinkLayers();
      for (int i = 0; i < nel.length; i++) {
        nel[i].doPrepare();
      }
    }
  }

  /**
   * Displays a message in the InfoDelegator.
   *
   * @param msg The message to display.
   */
  public void setText(String msg) {
    infoDelegator.setLabel("  " + msg);
  }

  /** Sets the title of the main frame, which contains the db name and the project name. */
  public void setTitle() {
    String title;

    // Display only the project name or also its full path
    if (getDisplayFullPath()) {
      title = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_CANONICAL_NAME);
    } else {
      title = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.TYPE_NODUS;
    }

    // Get database engine name
    String dbName = JDBCUtils.getDbEngineName();

    // Set title
    getMainFrame().setTitle(NodusC.APPNAME + " [(" + dbName + ") " + title + "]");
  }

  /**
   * Presents an assignment result or error. UI adapters may override this callback to collect
   * messages without opening dialogs. The default presentation is disabled in headless mode.
   *
   * @param message The localized message.
   * @param messageType The JOptionPane message type.
   */
  public void showAssignmentMessage(String message, int messageType) {
    if (!GraphicsEnvironment.isHeadless()) {
      JOptionPane.showMessageDialog(this, message, NodusC.APPNAME, messageType);
    }
  }

  /**
   * Starts a new ProgressBar. See OpenMap documentation for more details on the progress bar
   * mechanism implemented on the MapBean.
   *
   * @param finishedValue The max value to reach; zero or negative selects an activity indicator for
   *     work whose total is unknown.
   */
  public void startProgress(int finishedValue) {
    progress.startProgress(finishedValue);
  }

  /**
   * Ends a ProgressBar. See OpenMap documentation for more details on the progress bar mechanism
   * implemented on the MapBean.
   */
  public void stopProgress() {
    progress.stopProgress();
  }

  /**
   * Update menu an other component texts. Must be called when tha applivation Locale is changed.
   */
  public void updateComponentsText() {
    menus.setMenusText();
    menus.setMenuItemsText();

    scenarioLabel.setText(i18n.get(NodusMapPanel.class, "Scenario", "Scenario"));
    refreshMenuBarUI();
    revalidate();
    repaint();

    Frame mainFrame = getMainFrame();

    if (mainFrame != null) {
      mainFrame.invalidate();
      mainFrame.validate();
      mainFrame.repaint();
    }
  }

  /**
   * Advances the progress bar by one step and refreshes its display.
   *
   * @param msg The message to display.
   * @return False if the user confirmed cancellation.
   */
  public boolean updateProgress(String msg) {
    return updateProgress(msg, 1);
  }

  /**
   * Advances progress by one step, refreshing the display only at the requested interval.
   *
   * <p>Every call still checks cancellation. Determinate tasks count each call as a step;
   * indeterminate tasks update only their status text. Expensive loops can avoid sending a GUI
   * event for every item while retaining accurate progress and prompt cancellation checks. The
   * first and final steps are always displayed.
   *
   * @param msg The message to display.
   * @param displayInterval Number of steps between display updates; values below one mean one.
   * @return False if the user confirmed cancellation.
   */
  public boolean updateProgress(String msg, int displayInterval) {
    return progress.updateProgress(msg, displayInterval);
  }

  /**
   * Updates the scenario combo with the current scenario.
   *
   * @param forceReset If true, force a rest of the displayed results if any.
   */
  public void updateScenarioComboBox(boolean forceReset) {

    if (!nodusProject.isOpen()) {
      return;
    }

    // Avoid reentrance by removing the action listeners
    ActionListener[] al = scenarioComboBox.getActionListeners();
    for (ActionListener element : al) {
      scenarioComboBox.removeActionListener(element);
    }

    // If an assignment is running, its descriptions is displayed, even if there is no output yet.
    boolean isAssignmentRunning = true;
    if (getAssignmentMenuItem().isEnabled()) {
      isAssignmentRunning = false;
    }

    int currentScenario = nodusProject.getLocalProperty(NodusC.PROP_SCENARIO, 0);
    scenarioComboBox.removeAllItems();

    for (int i = 0; i < NodusC.MAXSCENARIOS; i++) {
      String virtualNetTableName =
          nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
      virtualNetTableName =
          nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, virtualNetTableName) + i;
      virtualNetTableName = JDBCUtils.getCompliantIdentifier(virtualNetTableName);

      boolean tableExists = JDBCUtils.tableExists(virtualNetTableName);

      if (tableExists || i == currentScenario || isAssignmentRunning) {
        String description = i18n.get(NodusMapPanel.class, "Empty_scenario", "empty scenario");

        if (tableExists || isAssignmentRunning) {
          description = nodusProject.getLocalProperty(NodusC.PROP_ASSIGNMENT_DESCRIPTION + i, "");
          description = description.trim();
          if (description.equals("")) {
            description =
                i18n.get(
                    NodusMapPanel.class, "No_description_available", "No description available");
          }
        }

        description = i + " - " + description;

        scenarioComboBox.addItem(description);
        if (i == currentScenario) {
          scenarioComboBox.setSelectedItem(description);
        }
      }
    }

    // Reset the displayed results
    if (forceReset || currentScenario != lastScenario) {
      resetResults(nodusProject.getNodeLayers());
      resetResults(nodusProject.getLinkLayers());
    }
    lastScenario = currentScenario;

    setTitle();

    // Reset action listeners
    for (ActionListener element : al) {
      scenarioComboBox.addActionListener(element);
    }
  }

  /**
   * These methods are used to store / retrieve objects set from a Groovy script for instance. This
   * allows having access to the values of some variables set by a script and used by another.
   */
  private HashMap<String, Object> store = new HashMap<String, Object>();

  /**
   * Store an object for future use.
   *
   * @param objectName The name of the object to store.
   * @param value The object to store.
   */
  public void storeObject(String objectName, Object value) {
    store.put(objectName, value);
  }

  /**
   * Retrieve a previously stored object.
   *
   * @param objectName The name of the object to retrieve.
   * @return The stored Object or null if it doesn't exist.
   */
  public Object retrieveObject(String objectName) {
    return store.get(objectName);
  }

  /**
   * Returns the hash map with the stored objects.
   *
   * @return The HashMap with the stored objects.
   */
  public HashMap<String, Object> getStoredObjects() {
    return store;
  }

  /**
   * Removes a previously stored object.
   *
   * @param objectName The name of the object to remove.
   */
  public void removeStoredObject(String objectName) {
    store.remove(objectName);
  }

  /** Clears all stored objects. */
  public void clearStoredObjects() {
    store.clear();
  }
}
