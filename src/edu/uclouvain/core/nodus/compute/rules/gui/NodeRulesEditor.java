/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.rules.gui;

import edu.uclouvain.core.nodus.NodusC;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/** Selection and editing behavior shared by the dialog and headless Swing regression tests. */
final class NodeRulesEditor {
  final JRadioButton generic = new JRadioButton();
  final JRadioButton specific = new JRadioButton();
  final JRadioButton exclusion = new JRadioButton();
  final JRadioButton inclusion = new JRadioButton();
  final JSpinner scenario = new JSpinner(new SpinnerNumberModel(0, 0, NodusC.MAXSCENARIOS, 1));
  final JSpinner group = spinner();
  final JSpinner mode1 = spinner();
  final JSpinner means1 = spinner();
  final JSpinner mode2 = spinner();
  final JSpinner means2 = spinner();
  final JCheckBox symmetry = new JCheckBox();
  final JCheckBox currentScenarioOnly = new JCheckBox();
  final DefaultTableModel model =
      new DefaultTableModel() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  final JTable table = new JTable(model);
  private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
  private final ButtonGroup scopes = new ButtonGroup();
  private final ButtonGroup polarities = new ButtonGroup();
  private int node;
  private int currentScenario;
  private boolean updatingControls;
  private Runnable changed = () -> {};

  NodeRulesEditor() {
    model.setColumnIdentifiers(
        new String[] {
          NodusC.DBF_SCENARIO, NodusC.DBF_GROUP, NodusC.DBF_MODE1, NodusC.DBF_MEANS1,
          NodusC.DBF_MODE2, NodusC.DBF_MEANS2, NodusC.DBF_SYMMETRY, NodusC.DBF_NUM
        });
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowSorter(sorter);
    sorter.setSortsOnUpdates(true);
    for (int column = 0; column < model.getColumnCount(); column++) {
      sorter.setSortable(column, false);
    }
    currentScenarioOnly.addActionListener(event -> filterRules());
    scopes.add(generic);
    scopes.add(specific);
    polarities.add(exclusion);
    polarities.add(inclusion);
    table
        .getSelectionModel()
        .addListSelectionListener(
            event -> {
              if (!event.getValueIsAdjusting()) {
                refreshSelection();
              }
            });
    generic.addActionListener(event -> changeScenario(-1));
    specific.addActionListener(event -> changeScenario((Integer) scenario.getValue()));
    scenario.addChangeListener(
        event -> {
          if (specific.isSelected()) {
            changeScenario((Integer) scenario.getValue());
          }
        });
    exclusion.addActionListener(event -> changePolarity(true));
    inclusion.addActionListener(event -> changePolarity(false));
    bind(group, 1);
    bind(mode1, 2);
    bind(means1, 3);
    bind(mode2, 4);
    bind(means2, 5);
    symmetry.addActionListener(
        event ->
            updateCell(
                6,
                symmetry.isSelected()
                    ? NodeRulesPersistence.DOUBLE_ARROW
                    : NodeRulesPersistence.SINGLE_ARROW));
    refreshSelection();
  }

  void configure(int node, int currentScenario, Runnable changed) {
    this.node = Math.abs(node);
    this.currentScenario = currentScenario;
    this.changed = changed;
  }

  private static JSpinner spinner() {
    return new JSpinner(new SpinnerNumberModel(-1, -1, 99, 1));
  }

  private void bind(JSpinner spinner, int column) {
    spinner.addChangeListener(event -> updateCell(column, spinner.getValue().toString()));
  }

  private void updateCell(int column, String value) {
    int row = selectedModelRow();
    if (!updatingControls && row >= 0) {
      model.setValueAt(value, row, column);
      changed.run();
    }
  }

  /** Reflects a selection without interpreting any control updates as user edits. */
  void refreshSelection() {
    updatingControls = true;
    try {
      int row = selectedModelRow();
      setControlsEnabled(row >= 0);
      if (row < 0) {
        scopes.clearSelection();
        polarities.clearSelection();
        scenario.setValue(currentScenario);
        for (JSpinner spinner : new JSpinner[] {group, mode1, means1, mode2, means2}) {
          spinner.setValue(-1);
        }
        symmetry.setSelected(false);
      } else {
        int selectedScenario = number(row, 0);
        generic.setSelected(selectedScenario == -1);
        specific.setSelected(selectedScenario != -1);
        scenario.setValue(selectedScenario == -1 ? currentScenario : selectedScenario);
        scenario.setEnabled(selectedScenario != -1);
        exclusion.setSelected(number(row, 7) < 0);
        inclusion.setSelected(number(row, 7) >= 0);
        group.setValue(number(row, 1));
        mode1.setValue(number(row, 2));
        means1.setValue(number(row, 3));
        mode2.setValue(number(row, 4));
        means2.setValue(number(row, 5));
        symmetry.setSelected(NodeRulesPersistence.DOUBLE_ARROW.equals(model.getValueAt(row, 6)));
      }
    } finally {
      updatingControls = false;
    }
    changed.run();
  }

  void setControlsEnabled(boolean enabled) {
    generic.setEnabled(enabled);
    specific.setEnabled(enabled);
    exclusion.setEnabled(enabled);
    inclusion.setEnabled(enabled);
    scenario.setEnabled(enabled && specific.isSelected());
    for (JSpinner spinner : new JSpinner[] {group, mode1, means1, mode2, means2}) {
      spinner.setEnabled(enabled);
    }
    symmetry.setEnabled(enabled);
  }

  private int number(int row, int column) {
    try {
      return Integer.parseInt(String.valueOf(model.getValueAt(row, column)));
    } catch (NumberFormatException invalid) {
      // Keep malformed stored cells visible and unchanged until explicitly edited or deleted.
      return -1;
    }
  }

  private int selectedModelRow() {
    int row = table.getSelectedRow();
    return row < 0 || row >= table.getRowCount() ? -1 : table.convertRowIndexToModel(row);
  }

  /** Filters only the view; the complete model remains available for validation and saving. */
  private void filterRules() {
    int selected = selectedModelRow();
    sorter.setRowFilter(
        currentScenarioOnly.isSelected()
            ? new RowFilter<DefaultTableModel, Integer>() {
              @Override
              public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                try {
                  int ruleScenario = Integer.parseInt(String.valueOf(entry.getValue(0)));
                  return ruleScenario == -1 || ruleScenario == currentScenario;
                } catch (NumberFormatException invalid) {
                  return false;
                }
              }
            }
            : null);
    int visible = selected < 0 ? -1 : table.convertRowIndexToView(selected);
    if (visible < 0) {
      table.clearSelection();
    } else {
      table.setRowSelectionInterval(visible, visible);
    }
    refreshSelection();
  }

  private void changeScenario(int value) {
    int selected = selectedModelRow();
    if (updatingControls || selected < 0) {
      return;
    }
    // Keep a common polarity within the destination scenario, as in the existing editor.
    for (int row = 0; row < model.getRowCount(); row++) {
      if (row != selected && number(row, 0) == value) {
        model.setValueAt(String.valueOf(number(row, 7)), selected, 7);
        break;
      }
    }
    model.setValueAt(String.valueOf(value), selected, 0);
    refreshSelection();
  }

  private void changePolarity(boolean exclude) {
    int selected = selectedModelRow();
    if (updatingControls || selected < 0) {
      return;
    }
    int selectedScenario = number(selected, 0);
    for (int row = 0; row < model.getRowCount(); row++) {
      if (number(row, 0) == selectedScenario) {
        model.setValueAt(String.valueOf(exclude ? -node : node), row, 7);
      }
    }
    refreshSelection();
  }

  void addRule(boolean excludeByDefault) {
    int signedNode = excludeByDefault ? -node : node;
    for (int row = 0; row < model.getRowCount(); row++) {
      if (number(row, 0) == -1) {
        signedNode = number(row, 7);
        break;
      }
    }
    model.addRow(
        new String[] {
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          NodeRulesPersistence.SINGLE_ARROW,
          String.valueOf(signedNode)
        });
    int last = table.convertRowIndexToView(model.getRowCount() - 1);
    table.setRowSelectionInterval(last, last);
  }

  void removeSelectedRule() {
    int visible = table.getSelectedRow();
    int selected = selectedModelRow();
    if (selected < 0) {
      return;
    }
    model.removeRow(selected);
    if (table.getRowCount() > 0) {
      int next = Math.min(visible, table.getRowCount() - 1);
      table.setRowSelectionInterval(next, next);
    } else {
      table.clearSelection();
    }
    refreshSelection();
  }
}
