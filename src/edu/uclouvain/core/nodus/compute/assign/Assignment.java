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

package edu.uclouvain.core.nodus.compute.assign;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.OutsidePhase;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.OutsideScope;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.compute.costs.VehiclesParser;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.tools.console.NodusConsole;
import edu.uclouvain.core.nodus.utils.GarbageCollectionRunner;
import edu.uclouvain.core.nodus.utils.ScriptRunner;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.util.Iterator;
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

  /** Periodic garbage collector runner used during long assignments. */
  private GarbageCollectionRunner garbageCollectionRunner = null;

  private String errorMessage = "";

  /** Normal termination details for iterative assignments. */
  private volatile AssignmentCompletion completion;

  /** Most recently computed relative volume gap. */
  private double lastRelativeVolumeGap = Double.NaN;

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
   * @return true on success
   * @throws OutOfMemoryError when not enough heap space is available.
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
   * Returns the normal termination details, or null if the assignment has not completed normally or
   * does not use an iterative equilibrium algorithm.
   *
   * @return The assignment completion details.
   */
  public AssignmentCompletion getCompletion() {
    return completion;
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

  /** Requests cancellation of every worker associated with the current assignment. */
  protected void cancelAssignmentWorkers() {
    if (assignmentWorkers == null) {
      return;
    }

    for (AssignmentWorker worker : assignmentWorkers) {
      if (worker != null) {
        worker.requestCancel();
        worker.interrupt();
      }
    }
  }

  /**
   * Waits for all assignment workers to complete. If interrupted, workers are canceled before this
   * method returns false.
   *
   * @return true if all workers completed, false if the waiting thread was interrupted and workers
   *     were canceled.
   */
  protected boolean waitForAssignmentWorkers() {
    if (assignmentWorkers == null) {
      return true;
    }

    boolean interrupted = false;

    for (AssignmentWorker worker : assignmentWorkers) {
      if (worker == null) {
        continue;
      }

      try {
        worker.join();
      } catch (InterruptedException e) {
        interrupted = true;
        break;
      }
    }

    if (!interrupted) {
      return true;
    }

    cancelAssignmentWorkers();

    for (AssignmentWorker worker : assignmentWorkers) {
      if (worker == null) {
        continue;
      }

      try {
        worker.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    Thread.currentThread().interrupt();
    return false;
  }

  /**
   * Creates the PathWriter for concrete assignment classes. The base run() method owns the final
   * close/discard lifecycle of this writer.
   *
   * @return the PathWriter created for this assignment
   */
  protected PathWriter createPathWriter() {
    if (pathWriter != null) {
      discardPathWriter();
    }

    pathWriter = new PathWriter(assignmentParameters);
    return pathWriter;
  }

  /**
   * Finalizes path tables after a successful assignment.
   *
   * @return True if all path data was saved.
   */
  private boolean closePathWriter() {
    if (pathWriter != null) {
      return pathWriter.close();
    }
    return true;
  }

  /** Releases and deletes path tables after a failed or cancelled assignment. */
  private void discardPathWriter() {
    if (pathWriter != null) {
      pathWriter.discard();
    }
  }

  /**
   * Starts the periodic garbage collector runner. Concrete assignment classes should call this
   * instead of creating their own GarbageCollectionRunner instance. The base run() method stops it
   * automatically.
   */
  protected void startGarbageCollectionRunner() {
    stopGarbageCollectionRunner();

    NodusMapPanel nodusMapPanel = nodusProject.getNodusMapPanel();
    int gcInterval = nodusMapPanel.getGarbageCollectorInterval();
    garbageCollectionRunner = new GarbageCollectionRunner(gcInterval);
  }

  /** Stops and releases the periodic garbage collector runner, if one was started. */
  private void stopGarbageCollectionRunner() {
    if (garbageCollectionRunner != null) {
      garbageCollectionRunner.stop();
      garbageCollectionRunner = null;
    }
  }

  /** Main routine that calls the actual assignment algorithm in the derived classes. */
  @Override
  public void run() {
   
    NodusMapPanel nodusMapPanel = nodusProject.getNodusMapPanel();
    nodusMapPanel.getAssignmentMenuItem().setEnabled(false);
    completion = null;
    lastRelativeVolumeGap = Double.NaN;

    // Update the scenario combo of the main window
    nodusMapPanel.updateScenarioComboBox(true);

    boolean success = false;
    boolean outOfMemory = false;
    AssignmentComputingTimes computingTimes = assignmentParameters.getComputingTimes();
    computingTimes.startAssignment();
    try {
      virtualNet = null;

      boolean computationCompleted = false;
      try {
        try {
          long started = computingTimes.start();
          try (OutsideScope timing = computingTimes.outside(OutsidePhase.NETWORK)) {
            virtualNet = new VirtualNetwork(assignmentParameters);
          } finally {
            computingTimes.add(AssignmentComputingTimes.Phase.NETWORK, started);
          }
          success = assign();
        } finally {
          computingTimes.beginFinalization();
          if (virtualNet != null) {
            virtualNet.dispose();
          }
        }

        if (success) {
          success = closePathWriter();
        }
        computationCompleted = success;
      } finally {
        computingTimes.finishAndPrint(
            getClass().getSimpleName(),
            assignmentParameters.getScenario(),
            assignmentParameters.getThreads(),
            computationCompleted);
      }

      if (!success && !errorMessage.isEmpty()) {
        nodusMapPanel.showAssignmentMessage(errorMessage, JOptionPane.ERROR_MESSAGE);
      }

      // Run the post assignment script, if any
      if (success) {
        success = runPostAssignmentScript();
      }

      // Play a sound
      if (success) {
        nodusMapPanel.getSoundPlayer().play(SoundPlayer.SOUND_OK);
        showCompletionMessage();
      } else {
        nodusMapPanel.getSoundPlayer().play(SoundPlayer.SOUND_FAILURE);
        discardPathWriter();
      }
    } catch (OutOfMemoryError e) {
      outOfMemory = true;

      // Free memory and force garbage collection
      virtualNet = null;
      discardPathWriter();
      System.gc();

      nodusMapPanel.showAssignmentMessage(
          i18n.get(
              Assignment.class,
              "Out_of_memory",
              "Out of memory. Increase JVM Heap size in launcher script"),
          JOptionPane.ERROR_MESSAGE);

      nodusProject.getNodusMapPanel().closeAndSaveState();
      // System.exit(0);
    } finally {
      stopGarbageCollectionRunner();
      assignmentWorkers = null;
      if (!success) {
        discardPathWriter();
      }
      pathWriter = null;
      nodusMapPanel.resetText();

      if (!outOfMemory) {
        nodusMapPanel.getAssignmentMenuItem().setEnabled(true);
      }
    }
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
    // scriptRunner.run(true);

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
   * Evaluates the convergence rule and retains the gap for the completion report.
   *
   * @param iteration Current iteration number.
   * @param threshold Requested convergence threshold.
   * @return True if the convergence threshold was reached.
   */
  protected final boolean convergenceReached(int iteration, double threshold) {
    try (OutsideScope timing =
        assignmentParameters.getComputingTimes().outside(OutsidePhase.VOLUME_UPDATES)) {
      return evaluateConvergence(iteration, threshold);
    }
  }

  /** Evaluates the volume gap within the coordinator's volume-processing timing scope. */
  private boolean evaluateConvergence(int iteration, double threshold) {
    if (iteration <= 1) {
      lastRelativeVolumeGap = Double.NaN;
      return false;
    }

    double numerator = 0.0;
    double denominator = 0.0;
    double maxGap = 0.0;

    for (VirtualNodeList nodeList : virtualNet.getVirtualNodeLists()) {
      Iterator<VirtualNode> nodeIterator = nodeList.getVirtualNodeList().iterator();

      while (nodeIterator.hasNext()) {
        VirtualNode node = nodeIterator.next();
        Iterator<VirtualLink> linkIterator = node.getVirtualLinkList().iterator();

        while (linkIterator.hasNext()) {
          VirtualLink link = linkIterator.next();

          for (byte groupIndex = 0;
              groupIndex < (byte) virtualNet.getGroups().length;
              groupIndex++) {
            numerator +=
                Math.abs(link.getCurrentVolume(groupIndex) - link.getPreviousVolume(groupIndex));
            denominator += link.getCurrentVolume(groupIndex);
          }
        }

        double currentGap = numerator / denominator;
        if (currentGap > maxGap) {
          maxGap = currentGap;
        }
      }
    }

    lastRelativeVolumeGap = maxGap;
    return maxGap < threshold;
  }

  
  /**
   * Records the normal completion of a convergence-controlled assignment. 
   * 
   * @param converged True if the assignment converged, false if the maximum number of iterations
   *     was reached.
   * @param iterations The number of iterations performed.
   * @param maximumIterations The maximum number of iterations allowed.
   * @param initializationIterations The number of iterations used for initialization, if any.
   */
  protected final void setConvergenceCompletion(
      boolean converged, int iterations, int maximumIterations, int initializationIterations) {
    AssignmentCompletion.Reason reason =
        converged
            ? AssignmentCompletion.Reason.CONVERGED
            : AssignmentCompletion.Reason.MAX_ITERATIONS_REACHED;
    completion =
        new AssignmentCompletion(
            reason,
            iterations,
            maximumIterations,
            initializationIterations,
            lastRelativeVolumeGap,
            assignmentParameters.getPrecision());
  }

  /**
   * Sets the normal completion of an assignment that was configured to run for a fixed number of
   * iterations. This method is used for assignments that do not use convergence control.
   * 
   * @param iterations The number of iterations performed.
   */
  protected final void setFixedIterationsCompletion(int iterations) {
    completion =
        new AssignmentCompletion(
            AssignmentCompletion.Reason.FIXED_ITERATIONS_COMPLETED,
            iterations,
            iterations,
            0,
            Double.NaN,
            Double.NaN);
  }

  /** Displays the stopping condition of an iterative equilibrium assignment. */
  private void showCompletionMessage() {
    if (completion == null) {
      return;
    }

    String message;
    int messageType = JOptionPane.INFORMATION_MESSAGE;

    if (completion.getReason() == AssignmentCompletion.Reason.FIXED_ITERATIONS_COMPLETED) {
      message =
          MessageFormat.format(
              i18n.get(
                  Assignment.class,
                  "Fixed_iterations_completed",
                  "Assignment completed after the configured {0} iterations."),
              completion.getIterationsPerformed());
    } else {
      message = getConvergenceCompletionMessage();
      if (completion.getReason() == AssignmentCompletion.Reason.MAX_ITERATIONS_REACHED) {
        messageType = JOptionPane.WARNING_MESSAGE;
      }
    }

    if (messageType == JOptionPane.INFORMATION_MESSAGE
        && !NodusC.displayAssignmentInformationDialogs) {
      return;
    }
    nodusProject.getNodusMapPanel().showAssignmentMessage(message, messageType);
  }

  /** Builds the localized completion message for a convergence-controlled assignment. */
  private String getConvergenceCompletionMessage() {
    DecimalFormat formatter = new DecimalFormat("0.#####", DecimalFormatSymbols.getInstance());
    String gap =
        completion.hasFinalRelativeGap()
            ? formatter.format(completion.getFinalRelativeGap())
            : i18n.get(Assignment.class, "Not_available", "not available");
    String threshold = formatter.format(completion.getConvergenceThreshold());
    boolean converged = completion.getReason() == AssignmentCompletion.Reason.CONVERGED;
    String message;

    if (converged) {
      message =
          MessageFormat.format(
              i18n.get(
                  Assignment.class,
                  "Assignment_converged",
                  "Assignment converged after {0} of {1} iterations.\n"
                      + "Final relative gap: {2}; threshold: {3}."),
              completion.getIterationsPerformed(),
              completion.getMaximumIterations(),
              gap,
              threshold);
    } else {
      message =
          MessageFormat.format(
              i18n.get(
                  Assignment.class,
                  "Maximum_iterations_reached",
                  "Assignment completed after the maximum of {0} iterations without reaching "
                      + "convergence.\nFinal relative gap: {1}; threshold: {2}.\n"
                      + "Results were saved."),
              completion.getMaximumIterations(),
              gap,
              threshold);
    }

    if (completion.getInitializationIterations() > 0) {
      message +=
          "\n"
              + MessageFormat.format(
                  i18n.get(
                      Assignment.class,
                      "Initialization_iterations",
                      "Count shown above: Frank-Wolfe phase.\n"
                          + "Incremental initialization: {0} iterations ({1} in total)."),
                  completion.getInitializationIterations(),
                  completion.getTotalIterationsPerformed());
    }

    return message;
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

  /** Get the maxDetourReferenceMode, if any. */
  protected void getMaxDetourReferenceMode() {
    Properties costFunctions = assignmentParameters.getCostFunctions();

    byte maxDetourReferenceMode =
        Byte.parseByte(costFunctions.getProperty(NodusC.VARNAME_MAX_DETOUR_REF_MODE, "-1"));
    assignmentParameters.setMaxDetourReferenceMode(maxDetourReferenceMode);

    if (maxDetourReferenceMode != -1) {
      System.out.println("Max detour reference mode: " + maxDetourReferenceMode);
    }
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

    // Scan the costs function to detect the presence of old xx_DURATION, ESV or FLOW variables
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

        Path path = Paths.get(costFunctionsFileName);
        Charset charset = StandardCharsets.UTF_8;

        // Upgrade : replace old variables with new cost functions
        if (hasDeprecatedDurations) {
          // Express the times in seconds instead of hours
          Path tmpPath = Paths.get(costFunctionsFileName + ".tmp");

          try {
            Files.deleteIfExists(tmpPath);
            Files.move(path, tmpPath);

            try (BufferedReader br = Files.newBufferedReader(tmpPath, charset);
                BufferedWriter output = Files.newBufferedWriter(path, charset)) {

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
            }

            Files.deleteIfExists(tmpPath);

          } catch (IOException e1) {
            e1.printStackTrace();
            return true;
          }

          // Replace the deprecated variables
          boolean[][] availableModeMeans = new boolean[NodusC.MAXMM][NodusC.MAXMM];

          try {
            String content = new String(Files.readAllBytes(path), charset);
            content = content.replace("LD_DURATION.", "ld@");
            content = content.replace("UL_DURATION.", "ul@");
            content = content.replace("TP_DURATION.", "tp@");

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
            try (BufferedWriter output =
                Files.newBufferedWriter(path, charset, StandardOpenOption.APPEND)) {
              for (int mode = 0; mode < NodusC.MAXMM; mode++) {
                for (int means = 0; means < NodusC.MAXMM; means++) {
                  if (availableModeMeans[mode][means]) {
                    String newFunction = "mv@" + mode + "," + means + " = 3600*LENGTH/SPEED";
                    if (!content.contains(newFunction)) {
                      output.append(newFunction);
                      output.newLine();
                    }
                  }
                }
              }
            }

          } catch (IOException e) {
            e.printStackTrace();
            return true;
          }
        }

        // Replace the ESV and FLOW variables, if any
        if (hasDeprecatedVariables) {
          try {
            String content = new String(Files.readAllBytes(path), charset);
            content = content.replace("ESV.", "PCU.");
            content = content.replace("FLOW", "VOLUME");
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
