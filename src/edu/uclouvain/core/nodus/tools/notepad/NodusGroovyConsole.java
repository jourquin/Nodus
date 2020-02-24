/**
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

package edu.uclouvain.core.nodus.tools.notepad;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import groovy.lang.GroovyShell;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.codehaus.groovy.control.CompilationFailedException;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * This class extends the Notepad in order to handle Groovy scripts. The scripts are run in a
 * separate thread.<br>
 * - The syntax highlighting is set to Java.<br>
 * - A "run" button is added for running scripts. <br>
 *
 * @author Bart Jourquin
 */
public class NodusGroovyConsole extends NotePad {

  /** A thread intended to run scripts and stop running scripts. */
  private class GroovyThread extends Thread {

    private NodusMapPanel nodusMapPanel;

    private File script;

    private GroovyShell shell = new GroovyShell();

    public GroovyThread(NodusMapPanel mapPanel, File script) {
      nodusMapPanel = mapPanel;
      this.script = script;
      shell.setVariable("nodusMapPanel", nodusMapPanel);
      shell.setVariable("nodusMainFrame", nodusMapPanel);
    }

    @SuppressWarnings("deprecation")
    public void cancel() {

      /*
       * Groovy cannot interrupt a running long Nodus tasks, such as an assignment, launched from
       * a script. So, explicitly call cancelLongTask()
       */
      nodusMapPanel.cancelLongTask();

      /*
       * This is ugly because unsafe and deprecated, but it allows to stop a running script
       */
      try {
        stop();
        runButton.setIcon(runIcon);
        running = false;
        if (NodusConsole.isVisible()) {
          String msg =
              i18n.get(
                  NodusGroovyConsole.class, "interrupted", "Groovy script interrupted by user.");
          System.err.println(msg);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @Override
    public void run() {

      running = true;
      runButton.setIcon(stopIcon);

      try {
        shell.evaluate(script);
      } catch (CompilationFailedException e) {
        System.err.println(e.getMessage());

      } catch (IOException e) {
        System.err.println(e.getMessage());
      }

      runButton.setIcon(runIcon);
      running = false;
    }
  }

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = -3216907298513667469L;

  private static String thisComponentName = "NodusGroovyConsole";

  private GroovyThread job = null;

  private NodusMapPanel nodusMapPanel;

  private JMenu run;

  private JButton runButton;

  private JMenuItem runGroovyShell;

  private ImageIcon runIcon = new ImageIcon(getClass().getResource("images/run.png"));

  private boolean running = false;

  private ImageIcon stopIcon = new ImageIcon(getClass().getResource("images/stop.png"));

  /**
   * Creates a new Groovy console.
   *
   * @param nodusMapPanel The Nodus map panel.
   * @param path The path to the script.
   * @param fileName The name of the script file.
   */
  public NodusGroovyConsole(NodusMapPanel nodusMapPanel, String path, String fileName) {
    super(nodusMapPanel, path, fileName, thisComponentName);

    // Create a new GUI only if no other instance is displayed
    if (!super.isNewInstance()) {
      return;
    }

    this.nodusMapPanel = nodusMapPanel;

    getTextPane().setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
    getTextPane().setCodeFoldingEnabled(true);
    getTextPane().setHighlightCurrentLine(true);
    nodusFileFilter.setExtension(NodusC.TYPE_GROOVY);
    nodusFileFilter.setDescription(
        i18n.get(NodusGroovyConsole.class, "Groovy_scripts", "Groovy scripts"));

    getMenubar().add(run = new JMenu(i18n.get(NodusGroovyConsole.class, "Run", "Run")));
    run.add(
        runGroovyShell =
            new JMenuItem(
                i18n.get(NodusGroovyConsole.class, "Run", "Run"),
                new ImageIcon(getClass().getResource("images/run.png"))));

    getToolBar().addSeparator();
    getToolBar().add(runButton = new JButton(runIcon));
    runButton.setToolTipText(
        i18n.get(NodusGroovyConsole.class, "Run_Groovy_script", "Run Groovy script"));

    runGroovyShell.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {

            if (!running) {
              executeScript();
            } else {
              cancelRunningScript();
            }
          }
        });

    runButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            if (!running) {
              executeScript();
            } else {
              cancelRunningScript();
            }
          }
        });
  }

  /** Cancels a running script, if any. */
  private void cancelRunningScript() {
    if (job != null) {
      job.cancel();
    }
  }

  /** Executes the Groovy script in a thread... */
  private void executeScript() {
    actions.askForSave();

    // File script = GroovyPreParser.preParseScript(getFileName(true));
    File script = new File(getFileName(true));
    if (script != null) {
      job = new GroovyThread(nodusMapPanel, script);
      job.start();
    }
  }
}
