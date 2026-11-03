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

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.swing.ScriptEditorWindowSupport;
import edu.uclouvain.core.nodus.utils.NodusGroovyShell;
import groovy.lang.Binding;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import org.codehaus.groovy.control.CompilerConfiguration;

/** Manages the optional native Groovy console and its Nodus script naming convention. */
public final class NativeGroovyConsole {
  /** Native Groovy console instance, when the user selected that console implementation. */
  private groovy.console.ui.Console nativeGroovyConsole;

  private final NodusMapPanel panel;
  private final Runnable restoreApplicationMenus;

  /**
   * Creates a native Groovy console controller for a map panel.
   *
   * @param panel panel exposed to Groovy scripts
   * @param restoreApplicationMenus action that restores the application menus
   */
  public NativeGroovyConsole(NodusMapPanel panel, Runnable restoreApplicationMenus) {
    this.panel = panel;
    this.restoreApplicationMenus = restoreApplicationMenus;
  }

  /**
   * Shows the native Groovy console, or focuses the already open one.
   *
   * @param path initial directory for the console's file chooser
   */
  public void show(String path) {
    if (focusNativeGroovyConsole()) {
      return;
    }

    final groovy.console.ui.Console console = new ScriptConsole(panel.getMainFrame());
    nativeGroovyConsole = console;

    // Set some defaults in UI
    console.askToInterruptScript();
    console.setAutoClearOutput(true);
    console.setSaveOnRun(true);

    JCheckBox dummyCheckBox = new JCheckBox();
    dummyCheckBox.setSelected(false);
    console.showScriptInOutput(new EventObject(dummyCheckBox));

    dummyCheckBox.setSelected(true);
    console.threadInterruption(new EventObject(dummyCheckBox));

    console.setCurrentFileChooserDir(new File(path));
    console.setVariable("nodusMapPanel", panel);
    console.run();
    configureCopyTarget(console);

    JFrame consoleFrame = getNativeGroovyConsoleFrame(console);
    if (consoleFrame != null) {
      consoleFrame.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
              clearNativeGroovyConsole(console);
            }

            @Override
            public void windowClosing(WindowEvent e) {
              clearNativeGroovyConsole(console);
            }
          });

      consoleFrame.setLocationRelativeTo(null);
      focusFrame(consoleFrame);
    }

    // Reset the preferences menu
    restoreApplicationMenus.run();
  }

  /**
   * Creates a console that retains Nodus script naming when its shell is created or reset.
   *
   * @return native console with the Nodus shell and Groovy's standard compiler configuration
   */
  public static groovy.console.ui.Console createConsole() {
    return new ScriptConsole(null);
  }

  /**
   * Creates a new native console window context with a copy of the parent's script variables.
   *
   * @param parent console opening a new window
   * @return console retaining Nodus naming and the parent's output interceptors
   */
  public static groovy.console.ui.Console createConsole(groovy.console.ui.Console parent) {
    Map<?, ?> variables = parent.getShell().getContext().getVariables();
    Binding binding = new Binding(new HashMap<>(variables));
    Window mainWindow =
        parent instanceof ScriptConsole ? ((ScriptConsole) parent).mainWindow : null;
    groovy.console.ui.Console child =
        new ScriptConsole(binding, parent.getBaseConfig(), mainWindow);
    child.setSystemOutInterceptor(parent.getSystemOutInterceptor());
    child.setSystemErrorInterceptor(parent.getSystemErrorInterceptor());
    return child;
  }

  /** Keeps resets and File → New Window on the same shell implementation. */
  private static final class ScriptConsole extends groovy.console.ui.Console {
    private final Window mainWindow;

    ScriptConsole(Window mainWindow) {
      super();
      this.mainWindow = mainWindow;
    }

    ScriptConsole(Binding binding, CompilerConfiguration configuration, Window mainWindow) {
      super(null, binding, configuration);
      this.mainWindow = mainWindow;
    }

    // Groovy exposes a raw Map here; parameterizing the override would cause a name clash.
    @Override
    @SuppressWarnings("rawtypes")
    public void run(Map delegates) {
      super.run(delegates);
      if (getFrame() != null) {
        Window window = SwingUtilities.getWindowAncestor(getFrame().getRootPane());
        ScriptEditorWindowSupport.install(window, mainWindow);
      }
    }

    @Override
    public void newScript(ClassLoader parent, Binding binding) {
      super.newScript(parent, binding);
      setShell(new NodusGroovyShell(parent, binding, getConfig()));
    }

    @Override
    public void fileNewWindow(EventObject event) {
      groovy.console.ui.Console child = createConsole(this);
      child.run();
      configureCopyTarget(child);
    }

    @Override
    public void installInterceptor() {
      // Groovy's New Window action shares the existing interceptors instead of nesting them.
      if (getSystemOutInterceptor() == null) {
        super.installInterceptor();
      }
    }
  }

  /**
   * Keeps Groovy's menu and toolbar copy actions pointed at the pane the user selected.
   *
   * @param console console whose copy target is configured
   */
  public static void configureCopyTarget(groovy.console.ui.Console console) {
    // Groovy remembers the output pane on focus gain, but does not reset that reference when
    // the editor regains focus. Mouse selection must also work before a focus event arrives.
    FocusAdapter focusListener =
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent event) {
            console.setCopyFromComponent(event.getComponent());
          }
        };
    MouseAdapter mouseListener =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            console.setCopyFromComponent(event.getComponent());
            event.getComponent().requestFocusInWindow();
          }
        };
    for (JTextPane pane : new JTextPane[] {console.getInputArea(), console.getOutputArea()}) {
      pane.addFocusListener(focusListener);
      pane.addMouseListener(mouseListener);
    }
  }

  /** Brings the already open native Groovy console to the front. */
  private boolean focusNativeGroovyConsole() {
    JFrame consoleFrame = getNativeGroovyConsoleFrame(nativeGroovyConsole);
    if (consoleFrame == null || !consoleFrame.isDisplayable()) {
      nativeGroovyConsole = null;
      return false;
    }

    focusFrame(consoleFrame);
    return true;
  }

  /** Clears the retained native console reference if it still points to the closed console. */
  private void clearNativeGroovyConsole(groovy.console.ui.Console console) {
    if (nativeGroovyConsole == console) {
      nativeGroovyConsole = null;
    }
  }

  /** Returns the Swing frame that hosts a native Groovy console. */
  private JFrame getNativeGroovyConsoleFrame(groovy.console.ui.Console console) {
    if (console == null || console.getFrame() == null) {
      return null;
    }
    return (JFrame) console.getFrame().getRootPane().getParent();
  }

  /** Makes a frame visible, de-iconified and focused. */
  private void focusFrame(JFrame frame) {
    if (frame.getState() == Frame.ICONIFIED) {
      frame.setState(Frame.NORMAL);
    }
    frame.setVisible(true);
    frame.toFront();
    frame.requestFocus();
  }
}
