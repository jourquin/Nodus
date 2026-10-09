/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * Center for Operations Research and Econometrics (CORE)
 * http://www.uclouvain.be
 *
 * This file is part of Nodus.
 * Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.compute.results.gui;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.modalsplit.LogitCalibrationPanel;
import edu.uclouvain.core.nodus.compute.modalsplit.LogitCalibrationSettings;
import edu.uclouvain.core.nodus.compute.od.ODReader;
import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators;
import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators.Indicator;
import edu.uclouvain.core.nodus.compute.results.PerformanceIndicators.Report;
import edu.uclouvain.core.nodus.swing.EscapeDialog;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

/**
 * Selects observed modal matrices and renders chosen performance indicators without writing data.
 */
public final class PerformanceDlg extends EscapeDialog {
  private static final long serialVersionUID = 1L;
  private static final String PREFIX = "performanceIndicators.";
  /** Translates the dialog's labels, tooltips and messages. */
  private final I18n i18n = Environment.getI18n();
  /** Main window whose assignment menu is guarded during computation. */
  private final NodusMapPanel mapPanel;
  /** Current project providing database access and saved comparison preferences. */
  private final NodusProject project;
  /** Editor mapping selected network modes to observed OD matrices. */
  private final LogitCalibrationPanel observations;
  /** Selector of available assignment path-header tables. */
  private final JComboBox<String> pathHeaders = new JComboBox<>();
  /** Checkboxes choosing the report sections to display. */
  private final Map<Indicator, JCheckBox> indicators = new EnumMap<>(Indicator.class);
  /** Read-only HTML view of the computed report. */
  private final JEditorPane output = new JEditorPane("text/html", "");
  /** Starts a background performance calculation. */
  private final JButton compute = new JButton();
  /** Closes the dialog or requests cancellation while a calculation is running. */
  private final JButton close = new JButton();
  /** Displays calculation progress and completion state. */
  private final JLabel status = new JLabel(" ");
  /** Groups input controls that are disabled while computation is running. */
  private final JPanel inputs = new JPanel(new BorderLayout(0, 8));
  /** Component cursors to restore after the busy state ends. */
  private final Map<Component, Cursor> originalCursors = new IdentityHashMap<>();
  /** Active calculation, or null when no background work is running. */
  private SwingWorker<Report, Void> worker;
  /** Cooperative cancellation flag shared with the active worker. */
  private AtomicBoolean cancelled;
  /** Whether to dispose the dialog once the active calculation finishes cancelling. */
  private boolean closeRequested;

  /**
   * Opens the modal performance comparison dialog for the current project.
   *
   * @param owner results dialog that opens this comparison
   * @param mapPanel main window supplying the project and computation guard
   */
  public PerformanceDlg(ResultsDlg owner, NodusMapPanel mapPanel) {
    super(owner, "", true);
    this.mapPanel = mapPanel;
    project = mapPanel.getNodusProject();
    setTitle(text("Title", "Model performance"));
    LogitCalibrationSettings saved = LogitCalibrationSettings.NONE;
    try {
      saved =
          LogitCalibrationSettings.decode(
              project.getLocalProperty(
                  PREFIX + "matrices",
                  project.getLocalProperty(LogitCalibrationSettings.PROPERTY, "")));
    } catch (IllegalArgumentException ignored) {
      // Invalid/stale preferences do not prevent the user selecting reference matrices anew.
    }
    observations = new LogitCalibrationPanel(ODReader.getValidODTables(project), saved.getTables());
    try {
      PerformanceIndicators.pathHeaderTables(project.getMainJDBCConnection())
          .forEach(pathHeaders::addItem);
    } catch (Exception failure) {
      JOptionPane.showMessageDialog(
          this, failure.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
    }
    String defaultHeader =
        project.getLocalProperty(
                NodusC.PROP_PATH_TABLE_PREFIX,
                project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_PATH)
            + project.getLocalProperty(NodusC.PROP_SCENARIO, 0)
            + NodusC.SUFFIX_HEADER;
    selectHeader(defaultHeader);
    selectHeader(project.getLocalProperty(PREFIX + "pathHeader", defaultHeader));
    pathHeaders.setToolTipText(
        text(
            "tooltip.pathHeaders",
            "Choose a saved assignment path-header table. All paths are summed"
                + " by mode, group and OD."));
    JPanel source = new JPanel(new BorderLayout(8, 0));
    JLabel pathLabel = new JLabel(text("PathHeader", "Assignment path-header table:"));
    pathLabel.setLabelFor(pathHeaders);
    source.add(pathLabel, BorderLayout.WEST);
    source.add(pathHeaders, BorderLayout.CENTER);
    inputs.add(source, BorderLayout.NORTH);
    inputs.add(observations, BorderLayout.CENTER);
    JPanel selection = new JPanel(new GridLayout(0, 2, 12, 4));
    selection.setBorder(BorderFactory.createTitledBorder(text("Indicators", "Indicators")));
    EnumSet<Indicator> chosen =
        savedIndicators(project.getLocalProperty(PREFIX + "indicators", ""));
    for (Indicator indicator : Indicator.values()) {
      JCheckBox checkbox = new JCheckBox(text(indicator.key, indicator.label));
      checkbox.setSelected(chosen.contains(indicator));
      checkbox.setToolTipText(text("tooltip." + indicator.key, indicatorTooltip(indicator)));
      indicators.put(indicator, checkbox);
      selection.add(checkbox);
    }
    inputs.add(selection, BorderLayout.SOUTH);
    output.setEditable(false);
    output.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    output.setText(
        "<html><body><p>"
            + PerformanceReportHtml.escape(
                pathHeaders.getItemCount() == 0
                    ? text("NoHeader", "No assignment path-header table is available.")
                    : text(
                        "Instructions",
                        "Select reference modal matrices, an assignment table and the"
                            + " indicators to compute."))
            + "</p></body></html>");
    JScrollPane results = new JScrollPane(output);
    results.setPreferredSize(new Dimension(1250, 580));
    results.setBorder(BorderFactory.createTitledBorder(text("Results", "Results")));
    compute.setText(text("Compute", "Compute"));
    compute.setToolTipText(
        text(
            "tooltip.compute",
            "Compare the selected matrices and display the selected indicators."));
    compute.setEnabled(pathHeaders.getItemCount() > 0);
    compute.addActionListener(event -> compute());
    close.setText(text("Close", "Close"));
    close.addActionListener(
        event -> {
          if (worker != null) {
            cancelled.set(true);
            status.setText(text("Cancelling", "Cancelling computation..."));
          } else {
            dispose();
          }
        });
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    actions.add(status);
    actions.add(compute);
    actions.add(close);
    JPanel content = new JPanel(new BorderLayout(0, 12));
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    content.add(inputs, BorderLayout.NORTH);
    content.add(results, BorderLayout.CENTER);
    content.add(actions, BorderLayout.SOUTH);
    setContentPane(content);
    getRootPane().setDefaultButton(compute);
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent event) {
            dispose();
          }
        });
    pack();
    java.awt.Rectangle screen = getGraphicsConfiguration().getBounds();
    java.awt.Insets insets = getToolkit().getScreenInsets(getGraphicsConfiguration());
    screen =
        new java.awt.Rectangle(
            screen.x + insets.left,
            screen.y + insets.top,
            screen.width - insets.left - insets.right,
            screen.height - insets.top - insets.bottom);
    setSize(Math.min(getWidth(), screen.width - 40), Math.min(getHeight(), screen.height - 40));
    setLocationRelativeTo(owner);
    setLocation(
        Math.max(screen.x, Math.min(getX(), screen.x + screen.width - getWidth())),
        Math.max(screen.y, Math.min(getY(), screen.y + screen.height - getHeight())));
  }

  private void selectHeader(String name) {
    for (int index = 0; index < pathHeaders.getItemCount(); index++) {
      if (pathHeaders.getItemAt(index).equalsIgnoreCase(name)) {
        pathHeaders.setSelectedIndex(index);
        return;
      }
    }
  }

  /** Restores checkbox choices, migrating the former KL checkbox to Jensen–Shannon. */
  static EnumSet<Indicator> savedIndicators(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      return EnumSet.allOf(Indicator.class);
    }
    EnumSet<Indicator> selected = EnumSet.noneOf(Indicator.class);
    for (String name : encoded.split(",")) {
      name = name.trim();
      if ("KL_DIVERGENCE".equals(name)) {
        selected.add(Indicator.JENSEN_SHANNON);
      } else {
        try {
          selected.add(Indicator.valueOf(name));
        } catch (IllegalArgumentException ignored) {
          // Obsolete or unknown saved options do not prevent opening the dialog.
        }
      }
    }
    return selected;
  }

  private void compute() {
    final Map<Integer, String> mapping;
    final String header;
    final EnumSet<Indicator> selected = EnumSet.noneOf(Indicator.class);
    try {
      mapping = observations.getTableMapping();
      if (pathHeaders.getSelectedItem() == null) {
        throw new IllegalArgumentException(
            text("NoHeader", "No assignment path-header table is available."));
      }
      header = pathHeaders.getSelectedItem().toString();
      indicators.forEach(
          (indicator, checkbox) -> {
            if (checkbox.isSelected()) {
              selected.add(indicator);
            }
          });
      if (selected.isEmpty()) {
        throw new IllegalArgumentException(text("NoIndicators", "Select at least one indicator."));
      }
      if (!mapPanel.getAssignmentMenuItem().isEnabled()) {
        throw new IllegalStateException(
            text("Busy", "Wait for the current computation to finish."));
      }
    } catch (Exception failure) {
      JOptionPane.showMessageDialog(
          this, failure.getMessage(), getTitle(), JOptionPane.WARNING_MESSAGE);
      return;
    }
    project.setLocalProperty(
        PREFIX + "matrices", new LogitCalibrationSettings(1, mapping).encode());
    project.setLocalProperty(PREFIX + "pathHeader", header);
    project.setLocalProperty(
        PREFIX + "indicators", selected.stream().map(Enum::name).collect(Collectors.joining(",")));
    cancelled = new AtomicBoolean();
    closeRequested = false;
    mapPanel.getAssignmentMenuItem().setEnabled(false);
    enable(inputs, false);
    compute.setEnabled(false);
    close.setText(text("Cancel", "Cancel"));
    status.setText(text("Computing", "Computing..."));
    setBusyCursor(true);
    worker =
        new SwingWorker<Report, Void>() {
          @Override
          protected Report doInBackground() throws Exception {
            // The shared project connection includes pending reference-matrix edits. The modal
            // dialog
            // and assignment guard retain exclusive computation access until reading actually
            // finishes.
            return PerformanceIndicators.compute(
                project.getMainJDBCConnection(),
                mapping,
                header,
                selected.contains(Indicator.OD_SHARE_ERROR),
                () -> !cancelled.get());
          }

          @Override
          protected void done() {
            try {
              output.setText(
                  PerformanceReportHtml.render(get(), selected, PerformanceDlg.this::text));
              output.setCaretPosition(0);
              status.setText(text("Finished", "Finished"));
            } catch (InterruptedException failure) {
              Thread.currentThread().interrupt();
              status.setText(text("Cancelled", "Computation cancelled"));
            } catch (CancellationException failure) {
              status.setText(text("Cancelled", "Computation cancelled"));
            } catch (ExecutionException failure) {
              if (failure.getCause() instanceof CancellationException) {
                status.setText(text("Cancelled", "Computation cancelled"));
              } else {
                status.setText(text("Failed", "Computation failed"));
                setBusyCursor(false);
                if (!closeRequested) {
                  JOptionPane.showMessageDialog(
                      PerformanceDlg.this,
                      failure.getCause().getMessage(),
                      getTitle(),
                      JOptionPane.ERROR_MESSAGE);
                }
              }
            } finally {
              setBusyCursor(false);
              worker = null;
              enable(inputs, true);
              compute.setEnabled(pathHeaders.getItemCount() > 0);
              close.setText(text("Close", "Close"));
              mapPanel.getAssignmentMenuItem().setEnabled(true);
              if (closeRequested) {
                PerformanceDlg.super.dispose();
              }
            }
          }
        };
    worker.execute();
  }

  private static void enable(Container container, boolean enabled) {
    for (Component component : container.getComponents()) {
      component.setEnabled(enabled);
      if (component instanceof Container) {
        enable((Container) component, enabled);
      }
    }
  }

  /** Covers controls with their own cursors too, while keeping Cancel usable. */
  private void setBusyCursor(boolean busy) {
    if (busy) {
      markBusy(this);
    } else {
      originalCursors.forEach(Component::setCursor);
      originalCursors.clear();
    }
  }

  private void markBusy(Component component) {
    originalCursors.put(component, component.isCursorSet() ? component.getCursor() : null);
    component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    if (component instanceof Container) {
      for (Component child : ((Container) component).getComponents()) {
        markBusy(child);
      }
    }
  }

  @Override
  public void dispose() {
    if (worker != null) {
      closeRequested = true;
      cancelled.set(true);
      status.setText(text("Cancelling", "Cancelling computation..."));
    } else {
      super.dispose();
    }
  }

  @Override
  public void setVisible(boolean visible) {
    if (!visible && worker != null) {
      dispose();
    } else {
      super.setVisible(visible);
    }
  }

  private String text(String key, String fallback) {
    return i18n.get(PerformanceDlg.class, key, fallback);
  }

  private static String indicatorTooltip(Indicator indicator) {
    switch (indicator) {
      case WAPE:
        return "100 times total absolute error divided by observed quantity.";
      case BIAS:
        return "Signed quantity error relative to observed quantity; positive"
            + " means overprediction.";
      case RMSE:
        return "Root mean squared cell error, in quantity units; larger errors"
            + " receive more weight.";
      case MODAL_SHARES:
        return "Observed and assigned modal shares and their difference in percentage points.";
      case OD_SHARE_ERROR:
        return "Median and 90th percentile of OD modal-share distance; every"
            + " eligible OD has equal weight.";
      case CROSS_ENTROPY:
        return "Observed-quantity-weighted negative log predicted modal share; lower is better.";
      case JENSEN_SHANNON:
        return "Observed-quantity-weighted Jensen–Shannon divergence using base-2 logarithms."
            + " Ranges from 0 (matching shares) to 1 (disjoint shares), and handles zero shares.";
      case COVERAGE:
        return "OD quantity coverage, shortfalls, excesses and observed-positive"
            + " cells with no assigned flow.";
      default:
        throw new IllegalArgumentException("Unknown performance indicator");
    }
  }
}
