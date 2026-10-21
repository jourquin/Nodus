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

package edu.uclouvain.core.nodus.compute.rules.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Actual editor comparisons and validation without constructing a dialog. */
class NodeRulesComparisonTest {
  @Test
  void ambiguousNumberConcatenationsCannotHideChangedRules() {
    final DefaultTableModel first = model("7", "3", "1", "11", "2", "1", "→", "-2");
    final DefaultTableModel second = model("7", "3", "11", "1", "2", "1", "→", "-2");
    assertEquals(join(first), join(second));
    assertNotEquals(NodeRulesPersistence.snapshot(first), NodeRulesPersistence.snapshot(second));
  }

  @Test
  void differentRuleStringsWithTheSameHashRemainDifferentEdits() {
    final DefaultTableModel first = model("7", "3", "8", "73", "28", "5", "→", "-2");
    final DefaultTableModel second = model("7", "3", "86", "16", "92", "98", "→", "-2");
    assertNotEquals(join(first), join(second));
    assertEquals(join(first).hashCode(), join(second).hashCode());
    assertNotEquals(NodeRulesPersistence.snapshot(first), NodeRulesPersistence.snapshot(second));
  }

  @Test
  void snapshotKeepsRowAndCellBoundariesAndDoesNotChangeWithTheModel() {
    final DefaultTableModel model = model("7", "3", "1", "1", "2", "1", "→", "-2");
    final List<List<Object>> initial = NodeRulesPersistence.snapshot(model);
    model.setValueAt("11", 0, 3);
    assertEquals("1", initial.get(0).get(3));
    assertNotEquals(initial, NodeRulesPersistence.snapshot(model));
    model.setValueAt("1", 0, 3);
    assertEquals(initial, NodeRulesPersistence.snapshot(model));
    model.addRow(new String[] {"-1", "-1", "0", "0", "1", "-1", "↔", "2"});
    assertNotEquals(initial, NodeRulesPersistence.snapshot(model));
    final List<List<Object>> ordered = NodeRulesPersistence.snapshot(model);
    model.moveRow(0, 0, 1);
    assertNotEquals(ordered, NodeRulesPersistence.snapshot(model));
    model.removeRow(0);
    assertEquals(initial, NodeRulesPersistence.snapshot(model));
    assertThrows(UnsupportedOperationException.class, () -> initial.clear());
    assertThrows(UnsupportedOperationException.class, () -> initial.get(0).set(0, "8"));
  }

  @Test
  void invalidPendingCellsAreRetainedAndComparedWithoutThrowing() {
    final DefaultTableModel model = model("7", "3", "1", "1", "2", "1", "→", "-2");
    final List<List<Object>> initial = NodeRulesPersistence.snapshot(model);
    model.setValueAt(null, 0, 2);
    assertNull(NodeRulesPersistence.snapshot(model).get(0).get(2));
    assertNotEquals(initial, NodeRulesPersistence.snapshot(model));
    assertFalse(NodeRulesPersistence.isValid(model, 0));
  }

  @TestFactory
  List<DynamicTest> validAndInvalidModeMeansCombinationsMatchEditorConstraints() {
    final List<DynamicTest> tests = new ArrayList<>();
    final int[][] valid = {
      {0, 0, 1, 1}, {1, 1, 0, 0}, {-1, -1, 2, 1}, {2, -1, 3, 99}, {99, 99, 1, -1}
    };
    final int[][] invalid = {
      {0, 1, 1, 1},
      {1, 0, 2, 1},
      {-1, 1, 2, 1},
      {1, 1, 0, 1},
      {1, 1, -1, 1},
      {1, 1, 2, 0},
      {1, 1, 1, 1},
      {-1, -1, -1, -1},
      {100, 1, 2, 1},
      {1, 100, 2, 1}
    };
    for (boolean accepted : new boolean[] {false, true}) {
      for (int[] pair : accepted ? valid : invalid) {
        tests.add(
            dynamicTest(
                accepted + "/" + java.util.Arrays.toString(pair),
                () -> {
                  final DefaultTableModel model =
                      model(
                          "7",
                          "3",
                          String.valueOf(pair[0]),
                          String.valueOf(pair[1]),
                          String.valueOf(pair[2]),
                          String.valueOf(pair[3]),
                          "→",
                          "-2");
                  assertEquals(accepted, NodeRulesPersistence.isValid(model, 0));
                }));
      }
    }
    return tests;
  }

  @Test
  void incompleteOrNonnumericRowsCannotBeSavedAsValid() {
    final DefaultTableModel model = model("7", "3", "1", "1", "2", "1", "→", "-2");
    assertTrue(NodeRulesPersistence.isValid(model, -1));
    for (int column = 0; column < 8; column++) {
      final Object previous = model.getValueAt(0, column);
      model.setValueAt("invalid", 0, column);
      assertFalse(NodeRulesPersistence.isValid(model, 0));
      model.setValueAt(previous, 0, column);
    }
  }

  static DefaultTableModel model(Object... cells) {
    final DefaultTableModel model =
        new DefaultTableModel(
            new String[] {
              "scenario", "group", "mode1", "means1", "mode2", "means2", "symmetry", "num"
            },
            0);
    if (cells.length > 0) {
      model.addRow(cells);
    }
    return model;
  }

  private static String join(DefaultTableModel model) {
    final StringBuilder result = new StringBuilder();
    for (int col = 0; col < model.getColumnCount(); col++) {
      result.append(model.getValueAt(0, col));
    }
    return result.toString();
  }
}
