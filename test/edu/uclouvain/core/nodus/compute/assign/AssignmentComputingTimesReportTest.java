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

package edu.uclouvain.core.nodus.compute.assign;

import static edu.uclouvain.core.nodus.compute.assign.ComputingTimesTestSupport.capture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusC.ExactMFAlgorithm;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** HTML metadata, escaped cells, independent benchmark files and safe report replacement. */
@ResourceLock("NodusC.displayComputingTimes")
@ResourceLock("NodusC.exactMFAlgorithm")
@ResourceLock(Resources.SYSTEM_OUT)
class AssignmentComputingTimesReportTest {
  @TempDir Path directory;

  @Test
  void savesReadableStandaloneUtf8TablesWithoutPrinting() throws Exception {
    String project = "Projet été <rail>&\"cargo\"";
    AssignmentComputingTimesReport report =
        new AssignmentComputingTimesReport(
            project, "ExactMFAssignment", 7, 10, true, ExactMFAlgorithm.DESTINATION_ASTAR);
    report.time("Total elapsed", 12_345_000_000L);
    report.section("Searches <&>", "Algorithm", "Calls");
    report.count("Destination A* searches", 42);
    report.paragraph("Worker elapsed time, not CPU time.");
    String html = report.document();
    Path destination =
        AssignmentComputingTimesReport.reportPath(
            directory, project, "ExactMFAssignment", 7, ExactMFAlgorithm.DESTINATION_ASTAR);
    Files.writeString(destination, "Old report");
    assertEquals(
        "",
        capture(
            () -> {
              try {
                assertEquals(destination, AssignmentComputingTimesReport.save(destination, html));
              } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
              }
            }));
    assertEquals(html, Files.readString(destination, StandardCharsets.UTF_8));
    assertTrue(html.startsWith("<!DOCTYPE html>"));
    assertTrue(html.contains("<meta charset=\"UTF-8\">"));
    assertTrue(html.contains("<meta name=\"viewport\""));
    assertTrue(html.contains("Projet été &lt;rail&gt;&amp;&quot;cargo&quot;"));
    assertFalse(html.contains("<rail>"));
    assertTrue(html.matches("(?s).*Generated at: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"));
    assertTrue(html.contains("<tr><td>Total elapsed:</td><td>12.345 s</td></tr>"));
    assertTrue(html.contains("<tr><td>Destination A* searches:</td><td>42</td></tr>"));
    assertTrue(html.contains("<h2>Searches &lt;&amp;&gt;</h2>"));
    assertTrue(html.contains("@media print"));
    assertTrue(html.endsWith("</body></html>"));
    assertFalse(html.contains("<pre>"));
    assertOnlyDestination(destination);
  }

  @Test
  void scenarioMethodAndExactSelectorHaveIndependentSafeFilenames() {
    Path baseline =
        AssignmentComputingTimesReport.reportPath(
            directory, "demo", "ExactMFAssignment", 7, ExactMFAlgorithm.DESTINATION_ASTAR);
    assertEquals(
        "demo_scenario7_ExactMFAssignment_DESTINATION_ASTAR_computing_times.html",
        baseline.getFileName().toString());
    assertNotEquals(
        baseline,
        AssignmentComputingTimesReport.reportPath(
            directory, "demo", "ExactMFAssignment", 8, ExactMFAlgorithm.DESTINATION_ASTAR));
    assertNotEquals(
        baseline,
        AssignmentComputingTimesReport.reportPath(
            directory, "demo", "ExactMFAssignment", 7, ExactMFAlgorithm.IMPROVED_ASTAR));
    assertNotEquals(
        baseline,
        AssignmentComputingTimesReport.reportPath(
            directory, "demo", "FastMFAssignment", 7, ExactMFAlgorithm.DESTINATION_ASTAR));
    Path unsafe =
        AssignmentComputingTimesReport.reportPath(
            directory,
            "../a\\b:c*?\"<>|\n",
            "../Assignment",
            7,
            ExactMFAlgorithm.DESTINATION_ASTAR);
    assertEquals(directory.toAbsolutePath(), unsafe.getParent());
  }

  @Test
  void failedInstallationRetainsExistingDestinationAndCleansStaging() throws Exception {
    Path destination = Files.createDirectory(directory.resolve("report.html"));
    Path marker = Files.writeString(destination.resolve("marker"), "Keep previous output");
    assertThrows(
        IOException.class, () -> AssignmentComputingTimesReport.save(destination, "New report"));
    assertEquals("Keep previous output", Files.readString(marker));
    assertOnlyDestination(destination);
  }

  @Test
  void disabledAuditNeverTouchesAnInvalidDestinationAndHasNoSavedReport() throws Exception {
    try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(false)) {
      assertNull(fixture.audit.finishAndSave(null, null, null, 7, 1, true));
      assertNull(fixture.audit.getReportPath());
      assertEquals(0, fixture.clock.reads());
      try (var files = Files.list(fixture.directory)) {
        assertEquals(0, files.count());
      }
    }
  }

  @Test
  void selectorIsSampledAtStartAndFailedRunsAreMarkedInHtml() throws Exception {
    ExactMFAlgorithm previous = NodusC.exactMFAlgorithm;
    try {
      NodusC.exactMFAlgorithm = ExactMFAlgorithm.DESTINATION_ASTAR;
      try (ComputingTimesTestSupport fixture = new ComputingTimesTestSupport(true)) {
        NodusC.exactMFAlgorithm = ExactMFAlgorithm.IMPROVED_ASTAR;
        fixture.clock.at(2);
        Path saved =
            fixture.audit.finishAndSave(directory, "demo", "ExactMFAssignment", 7, 1, false);
        assertEquals(saved, fixture.audit.getReportPath());
        assertTrue(saved.getFileName().toString().contains("_DESTINATION_ASTAR_"));
        String html = Files.readString(saved);
        assertTrue(html.contains("<td>Status:</td><td>failed/cancelled</td>"));
        assertTrue(html.contains("<td>Exact MF selector:</td><td>DESTINATION_ASTAR</td>"));
        assertNull(fixture.audit.finishAndSave(null, null, null, 7, 1, true));
        assertFalse(fixture.audit.isEnabled());
      }
    } finally {
      NodusC.exactMFAlgorithm = previous;
    }
  }

  private void assertOnlyDestination(Path destination) throws IOException {
    try (var files = Files.list(directory)) {
      assertEquals(List.of(destination), files.collect(java.util.stream.Collectors.toList()));
    }
  }
}
