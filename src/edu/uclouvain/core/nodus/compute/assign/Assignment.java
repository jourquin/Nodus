/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.assign;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import edu.uclouvain.core.nodus.utils.ModalSplitMethodsLoader;
import edu.uclouvain.core.nodus.utils.SoundPlayer;

import groovy.lang.GroovyShell;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.LinkedList;

import javax.swing.JOptionPane;

import org.codehaus.groovy.control.CompilationFailedException;

/**
 * Base class for all assignments. Defines some basic variables used for each method.
 *
 * @author Bart Jourquin
 */
public abstract class Assignment implements Runnable {

  /** The All-Or-Nothing assignment type. */
  public static final int ALL_OR_NOTHING = 0;

  /** The All-Or-Nothing time dependent assignment type. */
  public static final int AON_TIME_DEPENDENT = 7;

  /** The Dynamic time dependent assignment type. */
  public static final int DYNAMIC_TIME_DEPENDENT = 8;

  /** The Exact Multi-Flow assignment type. */
  public static final int EXACT_MULTI_FLOW = 6;

  /** The Fast Multi-Flow assignment type. */
  public static final int FAST_MULTI_FLOW = 5;

  /** The Frank-Wolfe equilibrium assignment type. */
  public static final int FRANK_WOLFE = 3;

  /** The Incremental equilibrium assignment type. */
  public static final int INCREMENTAL = 2;

  /** The Incremental + Frank-Wolfe equilibrium assignment type. */
  public static final int INCREMENTAL_FRANK_WOLFE = 4;

  /** The Method of Successive Averages equilibrium assignment type. */
  public static final int MSA = 1;

  /** I18N mechanism. */
  static I18n i18n = Environment.getI18n();

  /** Contains the choices made into the assignment dialog box. */
  AssignmentParameters assignmentParameters;

  /** Used to open a console to log lost paths. */
  boolean isFirstLostPath = true;

  /** The project this assignment refers to. */
  NodusProject nodusProject;

  /** Used the save the detailed paths if asked. */
  PathWriter pathWriter;

  /** Virtual network that will be generated before the assignment. */
  VirtualNetwork virtualNet;

  AssignmentWorker[] assignmentWorkers = null;

  /**
   * Initializes the assignment procedure. The effective computation starts calling the run()
   * methode.
   *
   * @param ap AssignmentParameters
   */
  public Assignment(AssignmentParameters ap) {
    nodusProject = ap.getNodusProject();
    assignmentParameters = ap;
  }

  /**
   * Must be implemented for each particular assignment method.
   *
   * @return True on success.
   */
  public abstract boolean assign() throws OutOfMemoryError;

  void displayConsoleIfNeeded() {
    if (isFirstLostPath) {
      isFirstLostPath = false;
      new NodusConsole(nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH));
    }
  }

  /**
   * Returns the assignment parameters.
   *
   * @return AssignmentParameters
   */
  public AssignmentParameters getAssignmentParameters() {
    return assignmentParameters;
  }

  /**
   * Returns the Nodus project.
   *
   * @return NodusProject
   */
  public NodusProject getNodusProjectl() {
    return nodusProject;
  }

  public PathWriter getPathWriter() {
    return pathWriter;
  }

  /**
   * Returns the Virtual Network.
   *
   * @return VirtualNetwork
   */
  public VirtualNetwork getVNet() {
    return virtualNet;
  }

  /**
   * Returns the assignment workers associated to this assignment.
   *
   * @return AssignmentWorker[]
   */
  public AssignmentWorker[] getAssignmentWorkers() {
    return assignmentWorkers;
  }

  /** Main routine that calls the actual assignment algorithm in the derived classes. */
  @Override
  public void run() {
    boolean success = false;

    NodusMapPanel nodusMapPanel = nodusProject.getNodusMapPanel();
    nodusMapPanel.getAssignmentMenuItem().setEnabled(false);

    // Update the scenario combo of the main window
    nodusMapPanel.updateScenarioComboBox();

    try {
      success = assign();
    } catch (OutOfMemoryError e) {
      // Free memory and force garbage collection
      virtualNet = null;
      System.gc();

      JOptionPane.showMessageDialog(
          nodusProject.getNodusMapPanel(),
          i18n.get(
              Assignment.class,
              "Out_of_memory",
              "Out of memory. Increase JVM Heap size in launcher script"),
          NodusC.APPNAME,
          JOptionPane.ERROR_MESSAGE);

      nodusProject.getNodusMapPanel().closeAndSaveState();
      System.exit(0);
    }

    // Run the post assignment script, if any
    if (success) {
      success = runPostAssignmentScript();
    }

    // Play a sound
    if (success) {
      nodusMapPanel.getSoundPlayer().play(SoundPlayer.SOUND_OK);
    } else {
      nodusMapPanel.getSoundPlayer().play(SoundPlayer.SOUND_FAILURE);
    }
    nodusMapPanel.getAssignmentMenuItem().setEnabled(true);

    nodusMapPanel.updateScenarioComboBox();
  }

  /**
   * Runs the post-assignment script if needed.
   *
   * @return True on success.
   */
  protected boolean runPostAssignmentScript() {
    if (!assignmentParameters.isRunPostAssignmentScript()) {
      return true;
    }

    if (assignmentParameters.getPostAssignmentScript() == null) {
      return false;
    }

    NodusMapPanel nodusMapPanel = nodusProject.getNodusMapPanel();
    GroovyShell shell = new GroovyShell();
    shell.setVariable("nodusMapPanel", nodusMapPanel);
    shell.setVariable("nodusMainFrame", nodusMapPanel);

    // Get absolute script file name
    String fileName =
        nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
            + assignmentParameters.getPostAssignmentScript();

    // Add extension if not given
    if (!assignmentParameters
        .getPostAssignmentScript()
        .toLowerCase()
        .endsWith(NodusC.TYPE_GROOVY)) {
      fileName += NodusC.TYPE_GROOVY;
    }

    try {
      shell.evaluate(new File(fileName));
    } catch (CompilationFailedException e) {
      JOptionPane.showMessageDialog(
          null, e.getMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    } catch (IOException e) {
      JOptionPane.showMessageDialog(
          null, e.getMessage(), NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
      return false;
    }

    return true;
  }

  /**
   * Returns the ModalSplitMethod which name is given as parameter.
   *
   * @param methodName String
   * @return ModalSplitMethod
   */
  public ModalSplitMethod getModalSplitMethod(String methodName) {
    LinkedList<Class<ModalSplitMethod>> ll =
        ModalSplitMethodsLoader.getAvailableModalSplitMethods();
    Iterator<Class<ModalSplitMethod>> it = ll.iterator();

    while (it.hasNext()) {
      Class<ModalSplitMethod> loadedClass = it.next();

      try {
        Constructor<ModalSplitMethod> cons = loadedClass.getConstructor();
        ModalSplitMethod modalSplitMethod = cons.newInstance();

        // Is this the method we are looking for ?
        if (modalSplitMethod.getName().equals(methodName)) {
          return modalSplitMethod;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    System.err.println("Modal split method not found. This should not be possible!");
    return null;
  }
}
