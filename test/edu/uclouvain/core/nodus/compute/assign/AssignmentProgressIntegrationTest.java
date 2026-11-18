/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * This file is part of Nodus, distributed under the GNU General Public License, version 3
 * or later. See http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.compute.assign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.Environment;
import com.bbn.openmap.InformationDelegator;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetworkWriter;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import java.awt.Cursor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Uses the real progress widget and glass-pane cursor through a complete detailed assignment. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class AssignmentProgressIntegrationTest {
  @TempDir Path directory;

  @Test
  void detailedPathsKeepTheCursorAndMessagesThroughFinalizationAndPublication() throws Exception {
    try (ProgressProject project = new ProgressProject(directory)) {
      Assignment assignment =
          new AllOrNothingAssignment(project.parameters(2)) {
            @Override
            public boolean assign() {
              boolean success = super.assign();
              // Result saving has stopped its own progress, but disposal and indexing remain.
              project.progressPanel.assertBusy();
              return success;
            }
          };
      assignment.run();
      assertEquals(List.of(SoundPlayer.SOUND_OK), project.progressPanel.sounds);
      assertTrue(project.progressPanel.errors.isEmpty());
      assertEquals(
          List.of(
              message(
                  VirtualNetwork.class,
                  "Converting_volumes_to_vehicles",
                  "Converting volumes to vehicles..."),
              message(
                  VirtualNetworkWriter.class, "Saving_virtual_network", "Saving virtual network")),
          project.progressPanel.outputPreparationMessages);
      assertEquals(
          List.of(
              message(Assignment.class, "Finalizing_assignment", "Finalizing assignment..."),
              message(PathWriter.class, "Creating_indexes", "Creating indexes..."),
              message(Assignment.class, "Publishing_results", "Publishing assignment results...")),
          project.progressPanel.finalizationMessages);
      assertEquals(2, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
      assertTrue(project.number("SELECT COUNT(*) FROM mini_paths1_detail") > 0);
      project.progressPanel.assertIdle();
    }
  }

  @Test
  void cancellationAndExceptionsReleaseTheAssignmentCursor() throws Exception {
    try (ProgressProject project = new ProgressProject(directory)) {
      for (boolean exception : new boolean[] {false, true}) {
        Assignment assignment =
            new Assignment(project.parameters(1)) {
              @Override
              public boolean assign() {
                project.progressPanel.startProgress(10);
                if (exception) {
                  throw new IllegalStateException("Injected assignment failure");
                }
                throw new CancellationException("Injected cancellation");
              }
            };
        assignment.run();
        project.progressPanel.assertIdle();
      }
      assertEquals(
          List.of(SoundPlayer.SOUND_FAILURE, SoundPlayer.SOUND_FAILURE),
          project.progressPanel.sounds);
      assertEquals(List.of("Injected assignment failure"), project.progressPanel.errors);
    }
  }

  @Test
  void redundantStopsAndReplacementPhasesPreserveAnOuterBusyCursor() throws Exception {
    try (ProgressProject project = new ProgressProject(directory)) {
      ProgressPanel panel = project.progressPanel;
      panel.setBusy(true);
      panel.startProgress(10);
      panel.startProgress(0);
      panel.stopProgress();
      panel.stopProgress();
      panel.assertBusy();
      panel.setBusy(false);
      panel.assertIdle();
    }
  }

  private static String message(Class<?> type, String key, String fallback) {
    return Environment.getI18n().get(type, key, fallback);
  }

  private static final class ProgressProject extends AssignmentTestProject {
    private ProgressPanel progressPanel;

    ProgressProject(Path directory) throws Exception {
      super(directory);
      SwingUtilities.invokeAndWait(() -> progressPanel = new ProgressPanel(this));
    }

    @Override
    public NodusMapPanel getNodusMapPanel() {
      return progressPanel == null ? super.getNodusMapPanel() : progressPanel;
    }
  }

  /** Substitutes dialogs and sound only; progress, labels and cursor use production code. */
  private static final class ProgressPanel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    private final ProgressProject project;
    private final InformationDelegator info;
    private final JLabel status;
    private final List<String> outputPreparationMessages = new ArrayList<>();
    private final List<String> finalizationMessages = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<Integer> sounds = new ArrayList<>();
    private final SoundPlayer player =
        new SoundPlayer(false) {
          @Override
          public void play(int sound) {
            sounds.add(sound);
          }
        };

    ProgressPanel(ProgressProject project) {
      this.project = project;
      JRootPane root = new JRootPane();
      root.setContentPane(this);
      getMapBean().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      try {
        Field field = NodusMapPanel.class.getDeclaredField("infoDelegator");
        field.setAccessible(true);
        info = (InformationDelegator) field.get(this);
        Field label = InformationDelegator.class.getDeclaredField("infoLineHolder2");
        label.setAccessible(true);
        status = (JLabel) label.get(info);
      } catch (ReflectiveOperationException failure) {
        throw new AssertionError(failure);
      }
    }

    @Override
    public ProgressProject getNodusProject() {
      return project;
    }

    @Override
    public SoundPlayer getSoundPlayer() {
      return player;
    }

    @Override
    public void updateScenarioComboBox(boolean reset) {}

    @Override
    public void showAssignmentMessage(String text, int type) {
      if (type == JOptionPane.ERROR_MESSAGE) {
        errors.add(text);
      }
    }

    @Override
    public void setText(String text) {
      super.setText(text);
      if (info != null
          && (text.equals(
                  message(
                      VirtualNetwork.class,
                      "Converting_volumes_to_vehicles",
                      "Converting volumes to vehicles..."))
              || text.equals(
                  message(
                      VirtualNetworkWriter.class,
                      "Saving_virtual_network",
                      "Saving virtual network")))) {
        outputPreparationMessages.add(text);
        assertBusy();
        onSwing(() -> assertEquals(text, status.getText().trim()));
      }
      if (info != null
          && !text.isBlank()
          && (!finalizationMessages.isEmpty()
              || text.equals(
                  message(
                      Assignment.class, "Finalizing_assignment", "Finalizing assignment...")))) {
        finalizationMessages.add(text);
        assertBusy();
        onSwing(
            () -> {
              assertEquals(text, status.getText().trim());
              assertTrue(info.getProgressBar().isVisible());
              assertTrue(info.getProgressBar().isIndeterminate());
            });
      }
    }

    void assertBusy() {
      onSwing(
          () -> {
            assertTrue(isBusy());
            assertTrue(getRootPane().getGlassPane().isVisible());
            assertEquals(Cursor.WAIT_CURSOR, getRootPane().getGlassPane().getCursor().getType());
          });
    }

    void assertIdle() {
      onSwing(
          () -> {
            assertFalse(isBusy());
            assertFalse(getRootPane().getGlassPane().isVisible());
            assertEquals(Cursor.HAND_CURSOR, getMapBean().getCursor().getType());
            assertFalse(info.getProgressBar().isVisible());
            assertTrue(status.getText().isBlank());
            assertTrue(getAssignmentMenuItem().isEnabled());
          });
    }

    private void onSwing(Runnable check) {
      try {
        if (SwingUtilities.isEventDispatchThread()) {
          check.run();
        } else {
          SwingUtilities.invokeAndWait(check);
        }
      } catch (Exception failure) {
        throw new AssertionError(failure);
      }
    }
  }
}
