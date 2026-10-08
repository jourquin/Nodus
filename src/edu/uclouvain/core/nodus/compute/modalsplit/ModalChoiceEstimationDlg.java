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

package edu.uclouvain.core.nodus.compute.modalsplit;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import edu.uclouvain.core.nodus.utils.HardwareUtils;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

/**
 * Starts independent modal-choice estimation from the Project menu.
 *
 * <p>Observed matrices and routing controls belong to the project, not to an assignment scenario.
 * Estimation runs in the background, reports progress through the main window (including its cancel
 * control), and writes coefficients only after all groups fit successfully. The normal Assignment
 * command remains disabled while routing and fitting run. Completion never launches an assignment,
 * changes its OD selection or publishes scenario results.
 *
 * <p>Estimation uses the selected source cost file without numbered scenario overrides. A database
 * table named after the cost file receives behavioral parameters and optional bounded OD/group
 * pivots. A single {@code @paramTable} key is written to the selected cost file, and diagnostics
 * are saved as {@code <cost-file-stem>_params.txt} in the project directory. An existing table
 * requires confirmation. An optional merged OD table sums the selected modal matrices by commodity
 * group, origin and destination for later selection in Assignment; replacing its contents also
 * requires confirmation. The isolated routing pass always performs one search per mode/means
 * without cost markup. Legacy scenario, iteration and markup preferences are ignored.
 *
 * <p>Construct and show the dialog on Swing's event dispatch thread. Controls hold a draft until
 * Estimate validates it, persists project-level preferences and acquires the shared computation
 * slot by disabling Assignment. The dialog then closes and a {@link SwingWorker} runs the {@link
 * LogitCalibration} workflow; completion restores the menu and plays the outcome sound on the event
 * thread. Closing this dialog before starting discards draft edits. Once started, the main progress
 * control handles cancellation and no partial coefficients are saved.
 *
 * <p>{@link LogitCalibrationPanel} owns the observed-table editor and reference selection; {@link
 * ModalChoiceFormula} displays the selected model's equations. The model selector stores stable
 * short names, not translated captions. Preferences under {@code modalChoiceEstimation.} are
 * separate from assignment scenarios. No field in this dialog overrides assignment demand.
 */
public final class ModalChoiceEstimationDlg extends EscapeDialog {
  private static final long serialVersionUID = -3764840165326503487L;
  /** Prefix for estimation preferences stored independently of assignment scenarios. */
  private static final String PREFIX = "modalChoiceEstimation.";
  /** Stable model identifiers in the same order as the translated selector entries. */
  private static final String[] METHODS = {"MNL", "MNP", "Proportional"};
  /** Translation service for labels, tooltips and estimation messages. */
  private final I18n i18n = Environment.getI18n();
  /** Main window providing progress, cancellation, assignment access and sounds. */
  private final NodusMapPanel mapPanel;
  /** Open project supplying observed tables, cost files and saved estimation preferences. */
  private final NodusProject project;
  /** Model selector whose index maps to the stable identifiers in {@link #METHODS}. */
  private final JComboBox<String> method = new JComboBox<>();
  /** Existing project cost file supplying transport costs and receiving the table reference. */
  private final JComboBox<String> costFile = new JComboBox<>();
  /** Optional destination for summed modal demand, for later selection in Assignment. */
  private final JTextField mergedMatrix = new JTextField(24);
  /** Fast or exact multi-flow algorithm used to compute modal route costs. */
  private final JComboBox<String> routing = new JComboBox<>();
  /** Adds bounded OD/group residual utilities after behavioral estimation. */
  private final JCheckBox estimatePivots = new JCheckBox();
  /** Maximum absolute utility correction fitted for each nonreference mode. */
  private final JSpinner pivotMaxAbs;
  /** Maximum admissible route-length ratio; zero disables the detour limit. */
  private final JSpinner detour;
  /** Worker count for routing and independent commodity-group fits. */
  private final JSpinner threads;
  /** Draft mapping of modes to observed OD tables, including the reference mode selection. */
  private final LogitCalibrationPanel observations;
  /** Probability and utility equations for the currently selected model. */
  private final ModalChoiceFormula formula = new ModalChoiceFormula();

  /**
   * Opens an estimation draft, restoring project preferences and legacy observed-table mappings.
   *
   * @param panel the open project's main window
   */
  public ModalChoiceEstimationDlg(NodusMapPanel panel) {
    super(panel.getMainFrame(), "", true);
    mapPanel = panel;
    project = panel.getNodusProject();
    setTitle(text("Title", "Modal choice estimation"));
    method.addItem(text("MNL", "Multinomial logit"));
    method.addItem(text("MNP", "Multinomial probit"));
    method.addItem(text("Proportional", "Proportional"));
    String savedMethod = project.getLocalProperty(PREFIX + "method", "MNL");
    for (int index = 0; index < METHODS.length; index++) {
      if (METHODS[index].equals(savedMethod)) {
        method.setSelectedIndex(index);
      }
    }
    routing.addItem(text("Fast", "Fast multi-flow"));
    routing.addItem(text("Exact", "Exact multi-flow"));
    routing.setSelectedIndex(project.getLocalProperty(PREFIX + "exact", false) ? 1 : 0);
    File directory = new File(project.getLocalProperty(NodusC.PROP_PROJECT_DOTPATH));
    String[] files = directory.list((dir, name) -> name.endsWith(".costs"));
    if (files != null) {
      Arrays.sort(files);
      Arrays.stream(files).forEach(costFile::addItem);
    }
    // Estimation keeps its own last-used file, even when Assignment selects another one.
    String previousFile =
        project.getLocalProperty(
            PREFIX + "costFile", project.getLocalProperty(NodusC.PROP_COST_FUNCTIONS, ""));
    for (int index = 0; index < costFile.getItemCount(); index++) {
      if (previousFile.equals(costFile.getItemAt(index))) {
        costFile.setSelectedIndex(index);
      }
    }
    detour = spinner("detour", 0, 0, 1000, 0.1);
    threads = spinner("threads", HardwareUtils.getNbCores(), 1, 1024, 1);
    double savedPivotMaxAbs =
        project.getLocalProperty(PREFIX + "pivotMaxAbs", ModalParameterTable.DEFAULT_PIVOT_MAX_ABS);
    if (!Double.isFinite(savedPivotMaxAbs) || savedPivotMaxAbs <= 0) {
      savedPivotMaxAbs = ModalParameterTable.DEFAULT_PIVOT_MAX_ABS;
    }
    pivotMaxAbs = new JSpinner(new SpinnerNumberModel(savedPivotMaxAbs, 0.0, null, 0.5));
    tooltip(
        pivotMaxAbs,
        "pivotMaxAbs",
        "Maximum absolute pivot utility; enter a positive value (default 8).");
    estimatePivots.setText(text("EstimatePivots", "Estimate pivots"));
    estimatePivots.setSelected(project.getLocalProperty(PREFIX + "estimatePivots", false));
    pivotMaxAbs.setEnabled(estimatePivots.isSelected());
    estimatePivots.addActionListener(
        event -> {
          pivotMaxAbs.setEnabled(estimatePivots.isSelected());
          updateFormula();
        });
    tooltip(
        estimatePivots,
        "estimatePivots",
        "Add bounded utility constants for each mode, OD pair and commodity group.");
    LogitCalibrationSettings settings = LogitCalibrationSettings.NONE;
    try {
      settings =
          LogitCalibrationSettings.decode(
              project.getLocalProperty(
                  LogitCalibrationSettings.PROPERTY,
                  project.getLocalProperty("logitCalibration", "")));
    } catch (IllegalArgumentException failure) {
      panel.showAssignmentMessage(failure.getMessage(), JOptionPane.WARNING_MESSAGE);
    }
    observations = new LogitCalibrationPanel(ODReader.getValidODTables(project), settings);
    observations.addReferenceChangeListener(event -> updateFormula());
    updateModel();
    method.addActionListener(event -> updateModel());
    tooltip(
        method,
        "method",
        "Each run estimates only the selected method using all mapped modal OD tables.");
    tooltip(
        costFile,
        "costFile",
        "<html>Choose the existing file supplying transport costs and receiving @paramTable."
            + "<br>Its name without .costs plus _params names the parameter table."
            + "<br>Base and commodity/class definitions apply;"
            + " numbered scenario"
            + " overrides are ignored.</html>");
    tooltip(
        routing,
        "routing",
        "<html>Compute modal costs with fast Dijkstra or exact A-star routing."
            + "<br>Both use one search per mode/means, without cost markup.</html>");
    tooltip(
        detour,
        "detour",
        "<html>Maximum route length as a multiple of the least-cost reference"
            + " route's length.<br>Set 0 to disable the detour limit.</html>");
    tooltip(
        threads,
        "threads",
        "<html>Set the number of workers used for route costs and"
            + " commodity-group parameter fitting.</html>");
    JPanel controls = new JPanel(new GridLayout(0, 2, 12, 8));
    addControl(controls, text("Method", "Modal-choice method:"), method);
    addControl(controls, text("Costs", "Source cost functions:"), costFile);
    mergedMatrix.setText(project.getLocalProperty(PREFIX + "mergedMatrix", ""));
    tooltip(
        mergedMatrix,
        "mergedMatrix",
        "<html>Optional OD table containing summed quantities by commodity group, origin and destination."
            + "<br>Leave blank to skip merging. Select this table later in Assignment.</html>");
    addControl(controls, text("MergedMatrix", "Merged OD table (optional):"), mergedMatrix);
    addControl(controls, text("PivotMaxAbs", "Maximum absolute pivot:"), pivotMaxAbs);
    addControl(controls, text("Routing", "Route-cost computation:"), routing);
    addControl(controls, text("Detour", "Maximum detour ratio (0 = unlimited):"), detour);
    addControl(controls, text("Threads", "Worker threads:"), threads);
    JPanel content = new JPanel(new BorderLayout(0, 16));
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    content.add(controls, BorderLayout.NORTH);
    content.add(observations, BorderLayout.CENTER);
    JPanel specification = new JPanel(new BorderLayout(0, 6));
    JLabel heading = new JLabel(text("Specification", "Model specification:"));
    heading.setFont(heading.getFont().deriveFont(Font.BOLD));
    specification.add(heading, BorderLayout.NORTH);
    specification.add(formula, BorderLayout.CENTER);
    JPanel bottom = new JPanel(new BorderLayout(0, 10));
    bottom.add(estimatePivots, BorderLayout.NORTH);
    bottom.add(specification, BorderLayout.CENTER);
    final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton cancel = new JButton(text("Cancel", "Cancel"));
    JButton estimate = new JButton(text("Estimate", "Estimate"));
    tooltip(cancel, "cancel", "Close the dialog without estimating or saving changes.");
    tooltip(
        estimate,
        "estimate",
        "Compute modal costs and save estimated parameters in the database table.");
    estimate.setEnabled(costFile.getItemCount() > 0);
    cancel.addActionListener(event -> dispose());
    estimate.addActionListener(event -> startEstimation());
    buttons.add(cancel);
    buttons.add(estimate);
    bottom.add(buttons, BorderLayout.SOUTH);
    content.add(bottom, BorderLayout.SOUTH);
    setContentPane(content);
    getRootPane().setDefaultButton(estimate);
    pack();
    setLocationRelativeTo(panel);
  }

  /** Keeps the reference controls and visible equations synchronized with the selected model. */
  private void updateModel() {
    observations.setMethod(selectedMethod());
    updateFormula();
  }

  /** Keeps the displayed equations synchronized with the pivot checkbox. */
  private void updateFormula() {
    formula.setMethod(
        selectedMethod(), estimatePivots.isSelected(), observations.getReferenceMode());
  }

  /**
   * Adds a label/control pair and makes the control's explanatory tooltip available on its label.
   */
  private void addControl(JPanel controls, String text, JComponent component) {
    JLabel label = new JLabel(text);
    label.setLabelFor(component);
    label.setToolTipText(component.getToolTipText());
    controls.add(label);
    controls.add(component);
  }

  /** Uses Nodus's localized tooltip keys, including the editable part of a spinner. */
  private void tooltip(JComponent component, String key, String fallback) {
    component.setToolTipText(text("tooltip." + key, fallback));
    if (component instanceof JSpinner) {
      JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) ((JSpinner) component).getEditor();
      editor.getTextField().setToolTipText(component.getToolTipText());
    }
  }

  /** Returns the stable stored method ID corresponding to the localized selector caption. */
  private String selectedMethod() {
    return METHODS[method.getSelectedIndex()];
  }

  /**
   * Restores a finite in-range project preference, falling back to the supplied default otherwise.
   */
  private JSpinner spinner(String key, double fallback, double min, double max, double step) {
    double saved = project.getLocalProperty(PREFIX + key, fallback);
    if (!Double.isFinite(saved) || saved < min || saved > max) {
      saved = fallback;
    }
    if (step == 1) {
      return new JSpinner(new SpinnerNumberModel((int) saved, (int) min, (int) max, 1));
    }
    return new JSpinner(new SpinnerNumberModel(saved, min, max, step));
  }

  /**
   * Commits the draft, persists valid preferences and starts one background estimation.
   *
   * <p>Validation errors keep the dialog open. Only after validation and the assignment-menu guard
   * pass are preferences saved and the dialog disposed. The worker owns the calibration's
   * try-with-resources scope and always restores the shared menu on completion. Confirmed
   * cancellation is silent here because the progress control already handled that interaction.
   */
  private void startEstimation() {
    final LogitCalibrationSettings settings;
    final AssignmentParameters parameters = new AssignmentParameters(project);
    final boolean exact = routing.getSelectedIndex() == 1;
    final boolean pivots = estimatePivots.isSelected();
    final String table;
    final String selectedCostFile;
    final double maxAbs;
    final String mergedTable;
    try {
      settings = observations.getSettings();
      for (JSpinner spinner : new JSpinner[] {detour, threads}) {
        spinner.commitEdit();
      }
      if (pivots) {
        pivotMaxAbs.commitEdit();
      }
      maxAbs =
          ModalParameterTable.validatePivotMaxAbs(((Number) pivotMaxAbs.getValue()).doubleValue());
      parameters.setModalSplitMethodName(selectedMethod());
      selectedCostFile = costFile.getSelectedItem().toString();
      parameters.setCostFunctions(selectedCostFile);
      table = ModalParameterTable.nameForCostFile(selectedCostFile);
      mergedTable =
          ModalMatrixMerge.validateName(
              mergedMatrix.getText(), settings.getTables().values(), table);
      parameters.setMaxDetourRatio(((Number) detour.getValue()).doubleValue());
      parameters.setThreads(((Number) threads.getValue()).intValue());
    } catch (Exception failure) {
      JOptionPane.showMessageDialog(
          this, failure.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (!mapPanel.getAssignmentMenuItem().isEnabled()) {
      return;
    }
    boolean tableExists;
    final boolean mergedExists;
    try {
      tableExists = ModalParameterTable.exists(project.getMainJDBCConnection(), table);
      if (tableExists) {
        ModalParameterTable.checkSchema(project.getMainJDBCConnection(), table);
      }
      mergedExists =
          !mergedTable.isEmpty()
              && ModalMatrixMerge.exists(project.getMainJDBCConnection(), mergedTable);
    } catch (Exception failure) {
      JOptionPane.showMessageDialog(
          this, failure.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (tableExists
        && JOptionPane.showConfirmDialog(
                this,
                text("OverwriteTable", "The parameter table already exists. Replace its contents?")
                    + "\n"
                    + table,
                getTitle(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE)
            != JOptionPane.YES_OPTION) {
      return;
    }
    if (mergedExists
        && JOptionPane.showConfirmDialog(
                this,
                text(
                        "OverwriteMergedMatrix",
                        "The merged OD table already exists. Replace its contents?")
                    + "\n"
                    + mergedTable,
                getTitle(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE)
            != JOptionPane.YES_OPTION) {
      return;
    }
    project.setLocalProperty(LogitCalibrationSettings.PROPERTY, settings.encode());
    project.setLocalProperty(PREFIX + "mergedMatrix", mergedTable);
    project.setLocalProperty(PREFIX + "method", selectedMethod());
    project.setLocalProperty(PREFIX + "costFile", selectedCostFile);
    project.setLocalProperty(PREFIX + "exact", exact);
    project.setLocalProperty(PREFIX + "detour", detour.getValue().toString());
    project.setLocalProperty(PREFIX + "threads", threads.getValue().toString());
    project.setLocalProperty(PREFIX + "estimatePivots", pivots);
    project.setLocalProperty(PREFIX + "pivotMaxAbs", Double.toString(maxAbs));
    mapPanel.getAssignmentMenuItem().setEnabled(false);
    dispose();
    new SwingWorker<Boolean, Void>() {
      @Override
      protected Boolean doInBackground() throws Exception {
        try (LogitCalibration calibration = new LogitCalibration(parameters, settings)) {
          return calibration.estimateToTable(
              exact, table, pivots, maxAbs, mergedTable, mergedExists);
        }
      }

      @Override
      protected void done() {
        mapPanel.getAssignmentMenuItem().setEnabled(true);
        mapPanel.resetText();
        try {
          if (get()) {
            mapPanel.getSoundPlayer().play(SoundPlayer.SOUND_OK);
          } else {
            mapPanel.getSoundPlayer().play(SoundPlayer.SOUND_FAILURE);
          }
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          mapPanel.getSoundPlayer().play(SoundPlayer.SOUND_FAILURE);
        } catch (ExecutionException failure) {
          if (!(failure.getCause() instanceof CancellationException)) {
            mapPanel.showAssignmentMessage(
                failure.getCause().getMessage(), JOptionPane.ERROR_MESSAGE);
          }
          mapPanel.getSoundPlayer().play(SoundPlayer.SOUND_FAILURE);
        } catch (CancellationException cancelled) {
          // Cancellation leaves previous coefficients and scenario results untouched.
          mapPanel.getSoundPlayer().play(SoundPlayer.SOUND_FAILURE);
        }
      }
    }.execute();
  }

  private String text(String key, String fallback) {
    return i18n.get(ModalChoiceEstimationDlg.class, key, fallback);
  }
}
