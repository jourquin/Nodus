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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

/**
 * Edits modal matrix mappings for standalone estimation or assignment performance comparisons.
 *
 * <p>Each row maps a Nodus mode ID to an observed OD table. Empty table selections are ignored;
 * selected rows must have unique mode IDs. The tables supply quantities by group, origin,
 * destination and optional OD class. Estimation requires at least two modes; comparisons can use
 * one mode and hide the reference control. Logit and probit fix the reference intercept at zero;
 * proportional choice fixes its cost factor at one.
 *
 * <p>The estimator computes costs for the union of these observations. An unavailable mode with
 * zero observed flow is valid. Positive flow on an unavailable mode excludes the entire OD record
 * from estimation and is reported in the {@code _params.txt} report. No control in this panel
 * selects, filters or replaces assignment demand. Assignments remain separate operations.
 *
 * <p>The panel edits a draft: {@link #getSettings()} commits active cell/spinner edits and
 * validates an immutable snapshot. It does not persist project properties or launch computation.
 *
 * <p>Table entries use Nodus network mode IDs; they are not positional estimator column indices.
 * The reference spinner retains its draft value when models change. Its tooltip describes a zero
 * intercept for logit/probit and a unit cost factor for proportional choice. The panel checks draft
 * consistency, but the workflow later checks table schemas, route feasibility and statistical
 * identification.
 *
 * <p>The parent dialog saves the snapshot only when the user starts estimation; closing it discards
 * the draft. All access must occur on Swing's event dispatch thread.
 */
public final class LogitCalibrationPanel extends JPanel {
  private static final long serialVersionUID = -5719556462890100316L;
  /** Translates labels and validation messages. */
  private final I18n i18n = Environment.getI18n();
  /** Draft mode-to-table mappings edited by the table. */
  private final DefaultTableModel model;
  /** Editor for the draft mappings. */
  private final JTable table;
  /** Draft network mode ID used to identify the estimation model. */
  private final JSpinner reference =
      new JSpinner(new SpinnerNumberModel(1, 1, NodusC.MAXMM - 1, 1));
  /** Label hidden together with the reference spinner during performance comparisons. */
  private final JLabel referenceLabel = new JLabel();
  /** Selected estimation method, used for reference-mode labels and availability. */
  private String method = "MNL";

  /**
   * Builds the draft editor from a snapshot of the project's available OD tables.
   *
   * @param tables table names accepted by the OD reader
   * @param settings previously saved observed-table mapping and reference mode
   */
  LogitCalibrationPanel(List<String> tables, LogitCalibrationSettings settings) {
    super(new BorderLayout(0, 8));
    model =
        new DefaultTableModel(
            new Object[] {text("Mode", "Mode ID"), text("Observed", "Observed OD table")}, 0);
    if (settings.getTables().isEmpty()) {
      for (int mode = 1; mode <= 3; mode++) {
        model.addRow(new Object[] {mode, ""});
      }
    } else {
      settings.getTables().forEach((mode, name) -> model.addRow(new Object[] {mode, name}));
    }
    table = new JTable(model);
    table.setPreferredScrollableViewportSize(new Dimension(490, 140));
    table.setToolTipText(
        text(
            "tooltip.table",
            "<html>Match each network mode ID to its observed OD table."
                + "<br>Tables supply grp, org, dst and qty; class is optional."
                + "<br>These matrices supply parameter estimation and optional merged demand."
                + "</html>"));
    table.getTableHeader().setToolTipText(table.getToolTipText());
    JTextField modeEditor = new JTextField();
    modeEditor.setToolTipText(
        text(
            "tooltip.mode",
            "Enter the network's numeric mode ID; each mode may appear only once."));
    table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(modeEditor));
    JComboBox<String> choices = new JComboBox<>();
    choices.setToolTipText(
        text(
            "tooltip.observed",
            "Choose this mode's observed OD quantities; a blank selection leaves the row unused."));
    choices.addItem("");
    tables.forEach(choices::addItem);
    table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(choices));
    reference.setValue(settings.getReferenceMode());
    referenceLabel.setLabelFor(reference);
    JButton add = new JButton(text("Add", "Add mode"));
    JButton remove = new JButton(text("Remove", "Remove mode"));
    add.setToolTipText(
        text("tooltip.add", "Add a mode and its observed OD table to the calibration."));
    remove.setToolTipText(
        text(
            "tooltip.remove",
            "Remove the selected mapping from this calibration; "
                + "the OD table itself is not deleted."));
    add.addActionListener(event -> model.addRow(new Object[] {"", ""}));
    remove.addActionListener(
        event -> {
          if (table.getSelectedRow() >= 0) {
            model.removeRow(table.getSelectedRow());
          }
        });
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    actions.add(add);
    actions.add(remove);
    referenceLabel.setText(text("Reference", "Reference mode:"));
    actions.add(referenceLabel);
    actions.add(reference);
    add(new JLabel(text("Scope", "Observed modal matrices:")), BorderLayout.NORTH);
    add(new JScrollPane(table), BorderLayout.CENTER);
    add(actions, BorderLayout.SOUTH);
    setMethod(method);
  }

  /**
   * Reuses the modal matrix editor for result comparisons, without a reference-mode control.
   *
   * @param tables available observed OD table names
   * @param mapping previously selected network mode IDs and reference matrices
   */
  public LogitCalibrationPanel(List<String> tables, Map<Integer, String> mapping) {
    this(tables, new LogitCalibrationSettings(1, mapping));
    reference.setVisible(false);
    reference.setEnabled(false);
    referenceLabel.setVisible(false);
    table.setToolTipText(
        text(
            "tooltip.performanceTable",
            "Match each network mode ID to its reference OD matrix. Quantities are summed by"
                + " commodity group, origin and destination."));
    table.getTableHeader().setToolTipText(table.getToolTipText());
  }

  /**
   * Updates model-specific controls without losing the draft reference choice.
   *
   * @param value short model name, MNL, MNP or Proportional
   */
  void setMethod(String value) {
    method = value;
    reference.setEnabled(LogitCalibrationSettings.supportsMethod(method));
    referenceLabel.setEnabled(reference.isEnabled());
    reference.setToolTipText(
        "Proportional".equals(method)
            ? text(
                "tooltip.reference.proportional",
                "Choose a mapped mode whose cost factor is fixed at one; "
                    + "other factors are relative to it.")
            : text(
                "tooltip.reference",
                "Choose a mapped mode whose intercept is fixed at zero; "
                    + "other intercepts are relative to it."));
    ((JSpinner.DefaultEditor) reference.getEditor())
        .getTextField()
        .setToolTipText(reference.getToolTipText());
    referenceLabel.setToolTipText(reference.getToolTipText());
  }

  /** Returns the reference mode currently selected in the draft. */
  int getReferenceMode() {
    return ((Number) reference.getValue()).intValue();
  }

  /** Notifies the enclosing dialog when the reference mode changes. */
  void addReferenceChangeListener(ChangeListener listener) {
    reference.addChangeListener(listener);
  }

  /**
   * Commits active edits and validates the observed-data snapshot.
   *
   * @return settings independent of every assignment scenario and OD selection
   * @throws IllegalArgumentException when a mode is repeated or inputs are incomplete
   */
  LogitCalibrationSettings getSettings() {
    Map<Integer, String> mapping = getTableMapping();
    if (reference.isEnabled()) {
      try {
        reference.commitEdit();
      } catch (java.text.ParseException failure) {
        throw new IllegalArgumentException(failure.getMessage(), failure);
      }
    }
    LogitCalibrationSettings settings = new LogitCalibrationSettings(getReferenceMode(), mapping);
    settings.validate();
    return settings;
  }

  /**
   * Commits edits and returns the selected matrices; comparison requires at least one mode.
   *
   * @return an immutable mapping from network mode IDs to observed OD table names
   * @throws IllegalArgumentException for incomplete edits, invalid or duplicate IDs, or no matrices
   */
  public Map<Integer, String> getTableMapping() {
    if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
      throw new IllegalArgumentException(text("InvalidEdit", "Complete the observed-table edit"));
    }
    Map<Integer, String> mapping = new TreeMap<>();
    for (int row = 0; row < model.getRowCount(); row++) {
      String mode = String.valueOf(model.getValueAt(row, 0)).trim();
      String name = String.valueOf(model.getValueAt(row, 1)).trim();
      if (name.isEmpty() || name.equals("null")) {
        continue;
      }
      int id;
      try {
        id = Integer.parseInt(mode);
      } catch (NumberFormatException failure) {
        throw new IllegalArgumentException(text("InvalidMode", "Enter a valid mode ID"));
      }
      if (id <= 0 || id >= NodusC.MAXMM) {
        throw new IllegalArgumentException(text("InvalidMode", "Enter a valid mode ID"));
      }
      if (mapping.put(id, name) != null) {
        throw new IllegalArgumentException(text("Duplicate", "Each mode ID must appear only once"));
      }
    }
    if (mapping.isEmpty()) {
      throw new IllegalArgumentException(
          text("EmptyMapping", "Select at least one modal OD table"));
    }
    return java.util.Collections.unmodifiableMap(mapping);
  }

  private String text(String key, String fallback) {
    return i18n.get(LogitCalibrationPanel.class, key, fallback);
  }
}
