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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Map;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Checks draft validation and model-specific controls independently of assignment settings. */
class LogitCalibrationPanelTest {
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
