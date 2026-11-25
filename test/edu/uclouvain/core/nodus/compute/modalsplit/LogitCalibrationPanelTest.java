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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.Environment;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Checks draft validation and model-specific controls independently of assignment settings. */
class LogitCalibrationPanelTest {
  @Test
  void separateSelectorUpdatesOnlyTheSelectedModeAndLeavesTheGridAsText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LogitCalibrationPanel panel =
              new LogitCalibrationPanel(List.of("road", "rail"), LogitCalibrationSettings.NONE);
          JTable table = find(panel, JTable.class);
          JComboBox<?> selector = find(panel, JComboBox.class);
          assertFalse(SwingUtilities.isDescendingFrom(selector, table));
          assertEquals(0, table.getSelectedRow());
          assertEquals("", table.getValueAt(0, 1));
          assertFalse(table.editCellAt(0, 1));
          JLabel empty =
              assertInstanceOf(
                  JLabel.class,
                  table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1));
          assertEquals(
              Environment.getI18n().get(LogitCalibrationPanel.class, "NoTable", "No OD table"),
              empty.getText());
          assertThrows(IllegalArgumentException.class, panel::getTableMapping);
          JLabel placeholder =
              assertInstanceOf(
                  JLabel.class,
                  selector
                      .getRenderer()
                      .getListCellRendererComponent(new JList<>(), null, -1, false, false));
          assertEquals(
              Environment.getI18n()
                  .get(LogitCalibrationPanel.class, "ChooseTable", "Choose an OD table…"),
              placeholder.getText());
          selector.setSelectedItem("road");
          assertEquals(Map.of(1, "road"), panel.getTableMapping());
          JLabel selected =
              assertInstanceOf(
                  JLabel.class, table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1));
          assertEquals("road", selected.getText());
          assertFalse(table.isEditing());

          table.setRowSelectionInterval(1, 1);
          assertEquals("", selector.getSelectedItem());
          assertEquals(Map.of(1, "road"), panel.getTableMapping());
          selector.setSelectedItem("rail");
          assertEquals(Map.of(1, "road", 2, "rail"), panel.getSettings().getTables());
          selector.setSelectedItem("");
          assertEquals(Map.of(1, "road"), panel.getTableMapping());
          table.clearSelection();
          assertFalse(selector.isEnabled());
        });
  }

  @Test
  void switchingModesPreservesANameMissingFromTheChoicesUntilItIsCleared() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LogitCalibrationPanel panel =
              new LogitCalibrationPanel(List.of("road"), Map.of(1, "road", 2, "old_rail"));
          JTable table = find(panel, JTable.class);
          JComboBox<?> selector = find(panel, JComboBox.class);
          table.setRowSelectionInterval(1, 1);
          assertEquals("old_rail", selector.getSelectedItem());
          table.setRowSelectionInterval(0, 0);
          assertEquals("road", selector.getSelectedItem());
          assertEquals(Map.of(1, "road", 2, "old_rail"), panel.getTableMapping());
          table.setRowSelectionInterval(1, 1);
          selector.setSelectedItem("");
          assertEquals(Map.of(1, "road"), panel.getTableMapping());
        });
  }

  @Test
  void resultComparisonReusesMappingsWithoutRequiringAReferenceMode() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LogitCalibrationPanel panel =
              new LogitCalibrationPanel(List.of("road"), Map.of(5, "road"));
          assertEquals(Map.of(5, "road"), panel.getTableMapping());
          assertTrue(!find(panel, JSpinner.class).isVisible());
          JTable table = find(panel, JTable.class);
          table.editCellAt(0, 0);
          ((javax.swing.JTextField) table.getEditorComponent()).setText("6");
          assertEquals(Map.of(6, "road"), panel.getTableMapping());
          table.setValueAt(0, 0, 0);
          assertThrows(IllegalArgumentException.class, panel::getTableMapping);
          table.setValueAt(6, 0, 0);
          table.setValueAt("", 0, 1);
          assertThrows(IllegalArgumentException.class, panel::getTableMapping);
        });
  }

  @Test
  void probitKeepsTheReferenceControlAndTheMnlReferenceChoice() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LogitCalibrationPanel panel =
              new LogitCalibrationPanel(
                  List.of("road", "rail"),
                  new LogitCalibrationSettings(3, Map.of(3, "road", 5, "rail")));
          panel.setMethod("MNP");
          assertTrue(find(panel, JSpinner.class).isEnabled());
          assertEquals(3, panel.getSettings().getReferenceMode());
          panel.setMethod("MNL");
          assertTrue(find(panel, JSpinner.class).isEnabled());
          assertEquals(3, panel.getSettings().getReferenceMode());
          assertEquals(Map.of(3, "road", 5, "rail"), panel.getSettings().getTables());
          panel.setMethod("Proportional");
          assertTrue(find(panel, JSpinner.class).isEnabled());
          assertEquals(3, panel.getSettings().getReferenceMode());
        });
  }

  @Test
  void activeTableEditsAreCommittedAndDuplicateModesAreRejected() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LogitCalibrationPanel panel =
              new LogitCalibrationPanel(
                  List.of("road", "rail"),
                  new LogitCalibrationSettings(1, Map.of(1, "road", 2, "rail")));
          JTable table = find(panel, JTable.class);
          table.editCellAt(1, 0);
          ((javax.swing.JTextField) table.getEditorComponent()).setText("1");
          assertThrows(IllegalArgumentException.class, panel::getSettings);
          table.setValueAt(3, 1, 0);
          assertEquals(Map.of(1, "road", 3, "rail"), panel.getSettings().getTables());
        });
  }

  @Test
  void validationRequiresMappedReferenceForEveryModel() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LogitCalibrationSettings saved =
              new LogitCalibrationSettings(3, Map.of(1, "road", 2, "rail"));
          LogitCalibrationPanel panel = new LogitCalibrationPanel(List.of("road", "rail"), saved);
          assertThrows(IllegalArgumentException.class, panel::getSettings);
          panel.setMethod("MNP");
          assertThrows(IllegalArgumentException.class, panel::getSettings);
          panel.setMethod("Proportional");
          assertThrows(IllegalArgumentException.class, panel::getSettings);
          find(panel, JSpinner.class).setValue(1);
          assertEquals(saved.getTables(), panel.getSettings().getTables());
          find(panel, JTable.class).setValueAt("", 1, 1);
          assertThrows(IllegalArgumentException.class, panel::getSettings);
          assertEquals(
              2, saved.getTables().size(), "Editing the draft must not mutate saved settings");
        });
  }

  private <T extends Component> T find(Container container, Class<T> type) {
    for (Component child : container.getComponents()) {
      if (type.isInstance(child)) {
        return type.cast(child);
      }
      if (child instanceof Container) {
        T match = find((Container) child, type);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }
}
