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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.util.Properties;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The dialog defaults to the current scenario, even after another header was used previously. */
class PerformanceHeaderSelectionTest {
  @Test
  void currentScenarioOverridesRememberedChoicesAndListOrder() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Project project = new Project();
          project.properties.setProperty("performanceIndicators.pathHeader", "demo_path1_header");
          JComboBox<String> headers =
              new JComboBox<>(
                  new String[] {"demo_path1_header", "demo_path12_header", "DEMO_PATH6_HEADER"});
          project.properties.setProperty(NodusC.PROP_SCENARIO, "6");
          PerformanceDlg.selectCurrentScenarioHeader(project, headers);
          assertEquals("DEMO_PATH6_HEADER", headers.getSelectedItem());
          // A manual selection in one opening must not become the default for the next scenario.
          headers.setSelectedItem("demo_path1_header");
          project.properties.setProperty(NodusC.PROP_SCENARIO, "12");
          PerformanceDlg.selectCurrentScenarioHeader(project, headers);
          assertEquals("demo_path12_header", headers.getSelectedItem());
        });
  }

  @Test
  void customPrefixesAndScenarioZeroAreRespected() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Project project = new Project();
          project.properties.setProperty(NodusC.PROP_PATH_TABLE_PREFIX, "Custom paths_");
          project.properties.setProperty(
              "performanceIndicators.pathHeader", "Custom paths_3_header");
          JComboBox<String> headers =
              new JComboBox<>(
                  new String[] {
                    "Custom paths_3_header", "Custom paths_0_header", "demo_path0_header"
                  });
          PerformanceDlg.selectCurrentScenarioHeader(project, headers);
          assertEquals("Custom paths_0_header", headers.getSelectedItem());
        });
  }

  @Test
  void missingCurrentScenarioDoesNotDefaultToAnotherScenariosTable() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Project project = new Project();
          project.properties.setProperty(NodusC.PROP_SCENARIO, "6");
          project.properties.setProperty("performanceIndicators.pathHeader", "demo_path1_header");
          JComboBox<String> headers = new JComboBox<>(new String[] {"demo_path1_header"});
          PerformanceDlg.selectCurrentScenarioHeader(project, headers);
          assertNull(headers.getSelectedItem());
          assertEquals(-1, headers.getSelectedIndex());
          assertEquals(1, headers.getItemCount());
          // Other saved assignments remain available for an explicit selection.
          headers.setSelectedIndex(0);
          assertEquals("demo_path1_header", headers.getSelectedItem());
          headers.removeAllItems();
          PerformanceDlg.selectCurrentScenarioHeader(project, headers);
          assertNull(headers.getSelectedItem());
        });
  }

  private static final class Project extends NodusProject {
    final Properties properties = new Properties();

    Project() {
      super(null);
      properties.setProperty(NodusC.PROP_PROJECT_DOTNAME, "demo");
    }

    @Override
    public String getLocalProperty(String key) {
      return properties.getProperty(key);
    }

    @Override
    public String getLocalProperty(String key, String fallback) {
      return properties.getProperty(key, fallback);
    }

    @Override
    public int getLocalProperty(String key, int fallback) {
      return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
    }
  }
}
