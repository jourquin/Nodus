/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * This file is part of Nodus, distributed under the GNU General Public License,
 * version 3 or later. See http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.utils.ScriptRunner;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/** Global defaults must survive project overrides, including enum and string settings. */
@Isolated("Restores all mutable NodusC settings")
@Timeout(20)
class NodusRuntimeSettingsTest {
  @TempDir Path directory;

  @Test
  void declaredDefaultsAreRetainedBeforeTheFirstProjectRestore() throws Exception {
    try (RuntimeSettingsFixture ignored = new RuntimeSettingsFixture()) {
      NodusC.displayComputingTimes = true;
      NodusC.displayMapComputingTimes = true;
      NodusC.useCompactShortestPaths = false;
      NodusC.exactMFAlgorithm = NodusC.ExactMFAlgorithm.IMPROVED_ASTAR;
      NodusC.restoreApplicationSettings();
      assertFalse(NodusC.displayComputingTimes);
      assertFalse(NodusC.displayMapComputingTimes);
      assertTrue(NodusC.useCompactShortestPaths);
      assertEquals(NodusC.ExactMFAlgorithm.DESTINATION_ASTAR, NodusC.exactMFAlgorithm);
    }
  }

  @Test
  void startupScriptBecomesTheBaselineForEveryLaterProject() throws Exception {
    try (RuntimeSettingsFixture ignored = new RuntimeSettingsFixture()) {
      final Path startup =
          Files.writeString(
              directory.resolve("nodus.groovy"),
              "import edu.uclouvain.core.nodus.NodusC\n"
                  + "assert startNodus && !quitNodus\n"
                  + "NodusC.displayComputingTimes = true\n"
                  + "NodusC.displayAssignmentInformationDialogs = false\n"
                  + "NodusC.exactMFAlgorithm = NodusC.ExactMFAlgorithm.IMPROVED_ASTAR\n"
                  + "NodusC.nodusUrl = 'https://application.example/'\n");
      final ScriptRunner runner = new ScriptRunner(startup.toString());
      runner.setVariable("startNodus", true);
      runner.setVariable("quitNodus", false);
      assertTrue(runner.run(false));
      NodusC.captureApplicationSettings();
      for (int project = 0; project < 3; project++) {
        NodusC.displayComputingTimes = false;
        NodusC.displayAssignmentInformationDialogs = true;
        NodusC.exactMFAlgorithm = NodusC.ExactMFAlgorithm.DESTINATION_ASTAR;
        NodusC.nodusUrl = "https://project.example/";
        NodusC.restoreApplicationSettings();
        assertTrue(NodusC.displayComputingTimes);
        assertFalse(NodusC.displayAssignmentInformationDialogs);
        assertEquals(NodusC.ExactMFAlgorithm.IMPROVED_ASTAR, NodusC.exactMFAlgorithm);
        assertEquals("https://application.example/", NodusC.nodusUrl);
      }
    }
  }

  @Test
  void allPublicMutableSettingsAreRestoredWithoutChangingFinalConstants() throws Exception {
    try (RuntimeSettingsFixture ignored = new RuntimeSettingsFixture()) {
      final Map<Field, Object> expected = new LinkedHashMap<>();
      for (Field field : NodusC.class.getFields()) {
        expected.put(field, field.get(null));
      }
      NodusC.captureApplicationSettings();
      for (Map.Entry<Field, Object> entry : expected.entrySet()) {
        final Field field = entry.getKey();
        if (Modifier.isFinal(field.getModifiers())) {
          continue;
        }
        final Object previous = entry.getValue();
        if (field.getType() == boolean.class) {
          field.setBoolean(null, !((Boolean) previous));
        } else if (field.getType() == String.class) {
          field.set(null, previous + "/project");
        } else if (field.getType().isEnum()) {
          final Object[] choices = field.getType().getEnumConstants();
          field.set(null, choices[0] == previous ? choices[1] : choices[0]);
        } else {
          throw new AssertionError("Add a mutation for setting " + field.getName());
        }
      }
      NodusC.restoreApplicationSettings();
      for (Map.Entry<Field, Object> entry : expected.entrySet()) {
        assertEquals(entry.getValue(), entry.getKey().get(null), entry.getKey().getName());
      }
    }
  }
}
