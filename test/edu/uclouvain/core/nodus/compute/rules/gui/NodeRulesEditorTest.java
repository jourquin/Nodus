/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.rules.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Filtering and Swing control events must preserve every rule in the underlying model. */
class NodeRulesEditorTest {
  @Test
  void currentScenarioFilterKeepsGenericRulesAndDoesNotChangeStoredRows() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(8, 4, 0, 0, 1, 1, false, -1020201));
          editor.model.addRow(row(-1, 2, 0, 0, 2, 1, true, -1020201));
          editor.model.addRow(row(1000, 0, 0, 0, 3, 1, false, -1020201));
          editor.model.addRow(row(7, 3, 1, 1, 0, 0, false, 1020201));
          final List<List<Object>> stored = NodeRulesPersistence.snapshot(editor.model);
          assertFalse(editor.currentScenarioOnly.isSelected());
          assertEquals(4, editor.table.getRowCount());
          editor.table.setRowSelectionInterval(3, 3);
          editor.currentScenarioOnly.doClick();
          assertEquals(2, editor.table.getRowCount());
          assertEquals("-1", editor.table.getValueAt(0, 0));
          assertEquals("7", editor.table.getValueAt(1, 0));
          assertEquals(1, editor.table.getSelectedRow());
          assertEquals(3, editor.group.getValue());
          assertEquals(stored, NodeRulesPersistence.snapshot(editor.model));
          editor.currentScenarioOnly.doClick();
          editor.table.setRowSelectionInterval(0, 0);
          editor.currentScenarioOnly.doClick();
          assertEquals(-1, editor.table.getSelectedRow());
          assertDisabled(editor);
          editor.currentScenarioOnly.doClick();
          assertEquals(4, editor.table.getRowCount());
          assertEquals(stored, NodeRulesPersistence.snapshot(editor.model));
        });
  }

  @Test
  void filteredEditsAndDeletionUseModelRowsAndKeepHiddenRules() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(8, 4, 0, 0, 3, 1, false, -1020201));
          editor.model.addRow(row(-1, 2, 0, 0, 2, 1, true, -1020201));
          editor.model.addRow(row(1000, 0, 0, 0, 3, 1, false, -1020201));
          editor.model.addRow(row(7, 3, 1, 1, 0, 0, false, 1020201));
          final List<List<Object>> stored = NodeRulesPersistence.snapshot(editor.model);
          editor.currentScenarioOnly.doClick();
          editor.table.setRowSelectionInterval(1, 1);
          editor.group.setValue(5);
          editor.symmetry.doClick();
          editor.exclusion.doClick();
          assertEquals("5", editor.model.getValueAt(3, 1));
          assertEquals(NodeRulesPersistence.DOUBLE_ARROW, editor.model.getValueAt(3, 6));
          assertEquals("-1020201", editor.model.getValueAt(3, 7));
          assertEquals(
              stored.subList(0, 3), NodeRulesPersistence.snapshot(editor.model).subList(0, 3));
          editor.removeSelectedRule();
          assertEquals(1, editor.table.getRowCount());
          assertEquals(3, editor.model.getRowCount());
          assertEquals(stored.subList(0, 3), NodeRulesPersistence.snapshot(editor.model));
          assertEquals(2, editor.group.getValue());
        });
  }

  @Test
  void addingWhileFilteredRemainsEditableAndChangingScopeHidesOnlyTheViewRow() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(8, 4, 0, 0, 3, 1, false, -1020201));
          editor.model.addRow(row(-1, 2, 0, 0, 2, 1, true, -1020201));
          editor.model.addRow(row(7, 3, 1, 1, 0, 0, false, 1020201));
          final List<List<Object>> stored = NodeRulesPersistence.snapshot(editor.model);
          editor.currentScenarioOnly.doClick();
          editor.addRule(true);
          assertEquals(2, editor.table.getSelectedRow());
          assertEquals(4, editor.model.getRowCount());
          assertTrue(editor.group.isEnabled());
          editor.group.setValue(5);
          editor.mode1.setValue(0);
          editor.means1.setValue(0);
          editor.mode2.setValue(1);
          editor.means2.setValue(1);
          editor.specific.doClick();
          editor.scenario.setValue(8);
          assertEquals(2, editor.table.getRowCount());
          assertEquals(4, editor.model.getRowCount());
          assertEquals("8", editor.model.getValueAt(3, 0));
          assertEquals("5", editor.model.getValueAt(3, 1));
          assertEquals(stored, NodeRulesPersistence.snapshot(editor.model).subList(0, 3));
          assertDisabled(editor);
          editor.currentScenarioOnly.doClick();
          assertEquals(4, editor.table.getRowCount());
          assertEquals("8", editor.table.getValueAt(3, 0));
        });
  }

  @Test
  void deletingTheOnlyVisibleRuleLeavesHiddenRulesAndDisablesControls() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(8, 4, 0, 0, 3, 1, false, -1020201));
          editor.model.addRow(row(7, 3, 1, 1, 0, 0, false, 1020201));
          final List<Object> hidden = NodeRulesPersistence.snapshot(editor.model).get(0);
          editor.currentScenarioOnly.doClick();
          editor.table.setRowSelectionInterval(0, 0);
          editor.removeSelectedRule();
          assertEquals(0, editor.table.getRowCount());
          assertEquals(List.of(hidden), NodeRulesPersistence.snapshot(editor.model));
          assertDisabled(editor);
          editor.currentScenarioOnly.doClick();
          assertEquals(1, editor.table.getRowCount());
        });
  }

  @Test
  void selectingEveryScopeAndPolarityReflectsTheRowWithoutChangingAnyRules() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(-1, 4, 0, 0, 1, 2, false, -1020201));
          editor.model.addRow(row(7, -1, 2, 3, 0, 0, true, 1020201));
          editor.model.addRow(row(1000, 0, 3, -1, 1, 1, false, -1020201));
          List<List<Object>> original = NodeRulesPersistence.snapshot(editor.model);
          assertDisabled(editor);
          for (int selected : new int[] {2, 0, 1, 2, 1, 0}) {
            editor.table.setRowSelectionInterval(selected, selected);
            assertTrue(editor.generic.isEnabled());
            assertTrue(editor.specific.isEnabled());
            assertTrue(editor.exclusion.isEnabled());
            assertTrue(editor.symmetry.isEnabled());
            assertEquals(original.get(selected).get(0).equals("-1"), editor.generic.isSelected());
            assertEquals(
                original.get(selected).get(7).toString().startsWith("-"),
                editor.exclusion.isSelected());
            assertEquals(
                original.get(selected).get(6).equals(NodeRulesPersistence.DOUBLE_ARROW),
                editor.symmetry.isSelected());
            JSpinner[] spinners = {
              editor.group, editor.mode1, editor.means1, editor.mode2, editor.means2
            };
            for (int column = 1; column <= 5; column++) {
              assertEquals(
                  Integer.valueOf(original.get(selected).get(column).toString()),
                  spinners[column - 1].getValue());
              assertTrue(spinners[column - 1].isEnabled());
            }
            if (editor.specific.isSelected()) {
              assertTrue(editor.scenario.isEnabled());
              assertEquals(
                  Integer.valueOf(original.get(selected).get(0).toString()),
                  editor.scenario.getValue());
            } else {
              assertFalse(editor.scenario.isEnabled());
            }
            assertEquals(original, NodeRulesPersistence.snapshot(editor.model));
            assertEquals(3, editor.table.getRowCount());
          }
          editor.table.clearSelection();
          assertDisabled(editor);
          assertEquals(original, NodeRulesPersistence.snapshot(editor.model));
        });
  }

  @Test
  void addingSelectsAnEditableNewRuleAndInvalidRulesCanBeDeselected() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(7, 4, 1, 1, 2, 1, true, 1020201));
          final List<Object> stored = NodeRulesPersistence.snapshot(editor.model).get(0);
          editor.table.setRowSelectionInterval(0, 0);
          editor.addRule(true);
          assertEquals(1, editor.table.getSelectedRow());
          assertTrue(editor.generic.isEnabled());
          assertTrue(editor.exclusion.isEnabled());
          assertTrue(editor.group.isEnabled());
          assertTrue(editor.mode1.isEnabled());
          assertTrue(editor.symmetry.isEnabled());
          assertEquals(-1, editor.group.getValue());
          assertFalse(editor.symmetry.isSelected());
          assertFalse(NodeRulesPersistence.isValid(editor.model, 1));
          editor.table.clearSelection();
          assertDisabled(editor);
          editor.table.setRowSelectionInterval(1, 1);
          editor.mode1.setValue(0);
          editor.means1.setValue(0);
          editor.mode2.setValue(1);
          editor.means2.setValue(1);
          assertTrue(NodeRulesPersistence.isValid(editor.model, 1));
          assertEquals(stored, NodeRulesPersistence.snapshot(editor.model).get(0));
          editor.model.removeRow(1);
          editor.model.removeRow(0);
          assertDisabled(editor);
        });
  }

  @Test
  void editingChangesOnlyTheSelectedRuleAndPolarityStaysWithinItsScenario() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(7, 0, 0, 0, 1, 1, false, 1020201));
          editor.model.addRow(row(7, 1, 0, 0, 2, 1, false, 1020201));
          editor.model.addRow(row(8, 0, 0, 0, 3, 1, false, -1020201));
          final List<Object> otherScenario = NodeRulesPersistence.snapshot(editor.model).get(2);
          editor.table.setRowSelectionInterval(0, 0);
          editor.group.setValue(3);
          editor.symmetry.doClick();
          editor.exclusion.doClick();
          editor.exclusion.doClick();
          assertEquals("3", editor.model.getValueAt(0, 1));
          assertEquals("1", editor.model.getValueAt(1, 1));
          assertEquals(NodeRulesPersistence.DOUBLE_ARROW, editor.model.getValueAt(0, 6));
          assertEquals(NodeRulesPersistence.SINGLE_ARROW, editor.model.getValueAt(1, 6));
          assertEquals("-1020201", editor.model.getValueAt(0, 7));
          assertEquals("-1020201", editor.model.getValueAt(1, 7));
          assertEquals(otherScenario, NodeRulesPersistence.snapshot(editor.model).get(2));
          assertEquals(3, editor.table.getRowCount());
        });
  }

  @Test
  void changingScopeUsesOnlyTheDestinationScenariosPolarity() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(-1, 0, 0, 0, 1, 1, false, -1020201));
          editor.model.addRow(row(7, 1, 0, 0, 2, 1, false, 1020201));
          editor.model.addRow(row(1000, 0, 0, 0, 3, 1, false, -1020201));
          editor.table.setRowSelectionInterval(0, 0);
          editor.specific.doClick();
          assertEquals("7", editor.model.getValueAt(0, 0));
          assertEquals("1020201", editor.model.getValueAt(0, 7));
          editor.scenario.setValue(1000);
          assertEquals("1000", editor.model.getValueAt(0, 0));
          assertEquals("-1020201", editor.model.getValueAt(0, 7));
          editor.generic.doClick();
          assertEquals("-1", editor.model.getValueAt(0, 0));
          assertFalse(editor.scenario.isEnabled());
          assertEquals("7", editor.model.getValueAt(1, 0));
          assertEquals("1000", editor.model.getValueAt(2, 0));
          assertEquals(3, editor.table.getRowCount());
        });
  }

  @Test
  void malformedStoredCellsRemainVisibleUntilExplicitlyEdited() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          NodeRulesEditor editor = editor();
          editor.model.addRow(row(7, 0, 0, 0, 1, 1, false, -1020201));
          editor.model.setValueAt(null, 0, 3);
          List<List<Object>> stored = NodeRulesPersistence.snapshot(editor.model);
          editor.table.setRowSelectionInterval(0, 0);
          assertEquals(stored, NodeRulesPersistence.snapshot(editor.model));
          assertFalse(NodeRulesPersistence.isValid(editor.model, 0));
          editor.means1.setValue(0);
          assertTrue(NodeRulesPersistence.isValid(editor.model, 0));
        });
  }

  private static NodeRulesEditor editor() {
    NodeRulesEditor editor = new NodeRulesEditor();
    editor.configure(1020201, 7, () -> {});
    return editor;
  }

  private static void assertDisabled(NodeRulesEditor editor) {
    assertFalse(editor.generic.isEnabled());
    assertFalse(editor.specific.isEnabled());
    assertFalse(editor.scenario.isEnabled());
    assertFalse(editor.exclusion.isEnabled());
    assertFalse(editor.inclusion.isEnabled());
    assertFalse(editor.group.isEnabled());
    assertFalse(editor.mode1.isEnabled());
    assertFalse(editor.means1.isEnabled());
    assertFalse(editor.mode2.isEnabled());
    assertFalse(editor.means2.isEnabled());
    assertFalse(editor.symmetry.isEnabled());
  }

  private static String[] row(
      int scenario,
      int group,
      int mode1,
      int means1,
      int mode2,
      int means2,
      boolean symmetric,
      int node) {
    return new String[] {
      String.valueOf(scenario),
      String.valueOf(group),
      String.valueOf(mode1),
      String.valueOf(means1),
      String.valueOf(mode2),
      String.valueOf(means2),
      symmetric ? NodeRulesPersistence.DOUBLE_ARROW : NodeRulesPersistence.SINGLE_ARROW,
      String.valueOf(node)
    };
  }
}
