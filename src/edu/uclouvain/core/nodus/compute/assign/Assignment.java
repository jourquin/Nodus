/*
 * Copyright (c) 1991-2023 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.assign;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.costs.VehiclesParser;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import edu.uclouvain.core.nodus.utils.ScriptRunner;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import javax.swing.JOptionPane;

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

  private String errorMessage = "";

  /** A parser and place holder for the vehicles characteristics (average load and PCU. */
  protected VehiclesParser vehiclesParser = null;

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

  /**
   * Get the PathWriter for this assignment.
   *
   * @return The PathWriter.
   */
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
    nodusMapPanel.updateScenarioComboBox(true);

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
      //System.exit(0);
    }

    if (!success && !errorMessage.isEmpty()) {
      JOptionPane.showMessageDialog(
          nodusProject.getNodusMapPanel(), errorMessage, NodusC.APPNAME, JOptionPane.ERROR_MESSAGE);
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

      // Path tables are create before the assignment starts. Delete them on error.
      pathWriter.deletePathsTables();
    }
    nodusMapPanel.getAssignmentMenuItem().setEnabled(true);
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

    // Get absolute script file name
    String scriptFileName =
        nodusMapPanel.getNodusProject().getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
            + assignmentParameters.getPostAssignmentScript();

    // Add extension if not given
    if (!assignmentParameters
        .getPostAssignmentScript()
        .toLowerCase()
        .endsWith(NodusC.TYPE_GROOVY)) {
      scriptFileName += NodusC.TYPE_GROOVY;
    }

    ScriptRunner scriptRunner = new ScriptRunner(scriptFileName);
    scriptRunner.setVariable("nodusMapPanel", nodusMapPanel);
    scriptRunner.run(true);

    return scriptRunner.run(false);
  }

  /**
   * Sets an error message that will be displayed once the assignment canceled.
   *
   * @param msg The message to display.
   */
  public void setErrorMessage(String msg) {
    errorMessage = msg;
  }

  /**
   * Test if the cost functions file contains duration functions.
   *
   * @return true if at least one type of duration functions is present
   */
  protected boolean hasDurationFunctions() {
    Properties costFunctions = assignmentParameters.getCostFunctions();

    // Scan the costs function to detect the presence of duration cost function
    Set<Object> keys = costFunctions.keySet();
    for (Object key : keys) {
      if (((String) key).contains("ld@")) {
        return true;
      }
      if (((String) key).contains("ul@")) {
        return true;
      }
      if (((String) key).contains("tr@")) {
        return true;
      }
      if (((String) key).contains("tp@")) {
        return true;
      }
      if (((String) key).contains("mv@")) {
        return true;
      }
    }
    return false;
  }

  /**
   * A quick and dirty way to introduce durations was introduces in Nodus 7, using XX_DURATION
   * variables in the costs functions files. Since Nodus 8, durations are handled in the same way
   * than cost functions, using the '@' separator instead of '.' after the type of function.
   *
   * <p>Example : "mv.1,1 = " for costs and "mv@1,1 = " for durations. If a duration function is not
   * defined, Nodus put it to 0.
   *
   * <p>The ESV (Equivalent Standard Vehicles) variables must also be replaced by the PCU (Personal
   * Car Units variable.
   *
   * @return true if the cost functions contain at least one of these variables.
   */
  protected boolean costsContainDeprecatedVariables() {

    Properties costFunctions = assignmentParameters.getCostFunctions();
    boolean hasDeprecatedDurations = false;
    boolean hasDeprecatedVariables = false;

    // Scan the costs function to detect the presence of old xx_DURATUION, ESV or FLOW variables
    Set<Object> keys = costFunctions.keySet();
    for (Object key : keys) {
      if (((String) key).contains("LD_DURATION")) {
        hasDeprecatedDurations = true;
        break;
      }
      if (((String) key).contains("UL_DURATION")) {
        hasDeprecatedDurations = true;
        break;
      }
      if (((String) key).contains("TP_DURATION")) {
        hasDeprecatedDurations = true;
        break;
      }

      if (((String) key).contains("ESV")) {
        hasDeprecatedVariables = true;
        break;
      }

      // The FLOW variable can be found in the moving cost functions
      if (((String) key).contains("mv.")) {
        String value = costFunctions.getProperty((String) key);
        if (value.contains("FLOW")) {
          hasDeprecatedVariables = true;
        }
        break;
      }
    }

    // If something to upgrade
    if (hasDeprecatedDurations || hasDeprecatedVariables) {
      int check =
          JOptionPane.showConfirmDialog(
              null,
              i18n.get(
                  Assignment.class,
                  "DeprecatedVariables",
                  "Costs contain deprecated xx_DURATION, ESV or FLOW variables. Upgrade ?"),
              NodusC.APPNAME,
              JOptionPane.YES_NO_OPTION);

      if (check == JOptionPane.YES_OPTION) {
        // Get the file to upgrade
        String costFunctionsFileName =
            nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)
                + nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS);

        // Upgrade : replace old variables with new cost functions
        if (hasDeprecatedDurations) {
          // Express the times in seconds instead of hours
          BufferedWriter output;
          try {
            File file = new File(costFunctionsFileName);
            File tmpFile = new File(costFunctionsFileName + ".tmp");
            if (tmpFile.exists()) {
              tmpFile.delete();
            }
            file.renameTo(tmpFile);
            FileReader input = new FileReader(tmpFile);
            BufferedReader br = new BufferedReader(input);
            output = new BufferedWriter(new FileWriter(costFunctionsFileName));

            String line;
            while ((line = br.readLine()) != null) {
              if (line.contains("LD_DURATION")
                  || line.contains("UL_DURATION")
                  || line.contains("TP_DURATION")) {
                line += "*3600";
              }
              output.append(line);
              output.newLine();
            }
            input.close();
            output.close();
            tmpFile.delete();
          } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            return true;
          } catch (IOException e1) {
            e1.printStackTrace();
            return true;
          }

          // Replace the deprecated variables
          Path path = Paths.get(costFunctionsFileName);
          Charset charset = StandardCharsets.UTF_8;

          boolean[][] availableModeMeans = new boolean[NodusC.MAXMM][NodusC.MAXMM];

          try {
            String content = new String(Files.readAllBytes(path), charset);
            content = content.replaceAll("LD_DURATION.", "ld@");
            content = content.replaceAll("UL_DURATION.", "ul@");
            content = content.replaceAll("TP_DURATION.", "tp@");

            for (int mode = 0; mode < NodusC.MAXMM; mode++) {
              for (int means = 0; means < NodusC.MAXMM; means++) {
                String key = "mv." + mode + "," + means;
                if (content.contains(key)) {
                  availableModeMeans[mode][means] = true;
                }
              }
            }

            Files.write(path, content.getBytes(charset));

            // Add the default moving durations for available mode-means
            // expressed in seconds, as in Nodus <= 7.2
            output = new BufferedWriter(new FileWriter(costFunctionsFileName, true));
            for (int mode = 0; mode < NodusC.MAXMM; mode++) {
              for (int means = 0; means < NodusC.MAXMM; means++) {
                if (availableModeMeans[mode][means]) {
                  String newFunction = "mv@" + mode + "," + means + " = 3600*LENGTH/SPEED";
                  if ((!content.contains(newFunction))) {
                    output.append(newFunction);
                    output.newLine();
                  }
                }
              }
            }
            output.close();

          } catch (IOException e) {
            e.printStackTrace();
            return true;
          }
        }

        // Replace the ESV and FLOW variables, if any
        if (hasDeprecatedVariables) {
          Path path = Paths.get(costFunctionsFileName);
          Charset charset = StandardCharsets.UTF_8;

          try {
            String content = new String(Files.readAllBytes(path), charset);
            content = content.replaceAll("ESV.", "PCU.");
            content = content.replaceAll("FLOW", "VOLUME");
            Files.write(path, content.getBytes(charset));
          } catch (IOException e) {
            e.printStackTrace();
            return true;
          }
        }

        // Load upgraded cost functions
        assignmentParameters.setCostFunctions(
            nodusProject.getLocalProperty(NodusC.PROP_COST_FUNCTIONS));

        return false;
      } else {
        // Stop assignment if the cost functions are not upgraded
        return true;
      }
    }
    return false;
  }
}
