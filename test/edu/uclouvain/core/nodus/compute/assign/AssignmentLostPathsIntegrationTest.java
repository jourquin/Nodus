/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.assign;

import static edu.uclouvain.core.nodus.compute.assign.ComputingTimesTestSupport.capture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.modalsplit.PathsForMode;
import edu.uclouvain.core.nodus.compute.modalsplit.Proportional;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Real workers report pairs while the coordinator saves a separate, executable SQL artifact. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock(Resources.SYSTEM_OUT)
@Timeout(30)
class AssignmentLostPathsIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> everyAssignmentReportsPairsAndSavesCleanupSqlOnBothDatabases() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (int method = 0; method <= 8; method++) {
        final int selectedMethod = method;
        tests.add(
            dynamicTest(
                "method=" + method + "/HSQLDB=" + hsql,
                () -> {
                  try (AssignmentTestProject project = disconnectedProject(hsql)) {
                    AssignmentParameters parameters = parameters(project, selectedMethod);
                    Assignment assignment = assignment(parameters, selectedMethod);
                    String output = capture(() -> project.run(assignment, 2));
                    assertEquals(
                        List.of(
                            "No path: origin=1, destination=4, group=1",
                            "No path: origin=3, destination=2, group=2"),
                        output
                            .lines()
                            .filter(line -> line.startsWith("No path: "))
                            .sorted()
                            .collect(Collectors.toList()));
                    assertFalse(output.contains("delete from "));
                    assertEquals(4, project.number("SELECT COUNT(*) FROM mini_od"));
                    Path script = script(output);
                    assertEquals(directory.toAbsolutePath(), script.getParent());
                    String sql = Files.readString(script);
                    assertTrue(sql.contains("-- Scenario: 1\n"));
                    assertTrue(
                        sql.contains("-- Description: Test assignment café\n-- continued\n"));
                    assertTrue(
                        sql.contains(
                            "-- Assignment method: "
                                + assignment.getClass().getSuperclass().getSimpleName()
                                + "\n"));
                    assertTrue(sql.contains("-- OD table: mini_od\n"));
                    assertTrue(sql.contains("-- OD filter: qty > 0\n"));
                    assertTrue(sql.contains("-- Assignment started: "));
                    assertTrue(sql.contains("-- Assignment status: completed\n"));
                    assertTrue(sql.contains("-- Threads: 4\n"));
                    List<String> statements =
                        sql.lines()
                            .filter(line -> !line.startsWith("--"))
                            .filter(line -> !line.isBlank())
                            .collect(Collectors.toList());
                    assertEquals(
                        List.of(
                            "delete from mini_od where grp=1 and org=1 and dst=4;",
                            "delete from mini_od where grp=2 and org=3 and dst=2;"),
                        statements);
                    for (String statement : statements) {
                      project.execute(statement);
                    }
                    assertEquals(
                        List.of(List.of("1", "1", "2"), List.of("2", "3", "4")),
                        project.rows("SELECT grp,org,dst FROM mini_od ORDER BY grp"));
                  }
                }));
      }
    }
    return tests;
  }

  @Test
  void cancelledRunsRetainTheirDiagnosticsAndRetriesPreserveEarlierScripts() throws Exception {
    try (AssignmentTestProject project = disconnectedProject(false)) {
      AssignmentParameters parameters = parameters(project, Assignment.ALL_OR_NOTHING);
      Assignment cancelled =
          new AllOrNothingAssignment(parameters) {
            @Override
            void displayConsoleIfNeeded() {}

            @Override
            public boolean assign() {
              super.assign();
              return false;
            }
          };
      Path first = script(capture(() -> project.runCancelled(cancelled, 2)));
      String original = Files.readString(first);
      assertTrue(original.contains("-- Assignment status: failed/cancelled\n"));
      Path second = script(capture(() -> project.run(assignment(parameters, 0), 2)));
      assertFalse(first.equals(second));
      assertEquals(original, Files.readString(first));
      assertTrue(Files.readString(second).contains("-- Assignment status: completed\n"));
      assertEquals(4, project.number("SELECT COUNT(*) FROM mini_od"));
    }
  }

  @Test
  void scriptWriteFailureWarnsWithoutInvalidatingAssignmentResults() throws Exception {
    try (AssignmentTestProject project = disconnectedProject(false)) {
      Path blocked = Files.writeString(directory.resolve("blocked"), "Keep this file");
      project.properties.setProperty(NodusC.PROP_PROJECT_DOTPATH, blocked + java.io.File.separator);
      AssignmentParameters parameters = parameters(project, Assignment.ALL_OR_NOTHING);
      String output = capture(() -> project.run(assignment(parameters, 0), 2, 1));
      assertEquals(2, output.lines().filter(line -> line.startsWith("No path: ")).count());
      assertFalse(output.contains("Lost-path SQL script:"));
      assertEquals("Keep this file", Files.readString(blocked));
      assertEquals(300, project.number("SELECT SUM(qty) FROM mini_paths1_header"));
      assertEquals(4, project.number("SELECT COUNT(*) FROM mini_od"));
    }
  }

  @Test
  void disabledLoggingAndFullyRoutableDemandDoNotCreateScripts() throws Exception {
    try (AssignmentTestProject project = disconnectedProject(false)) {
      AssignmentParameters parameters = parameters(project, Assignment.ALL_OR_NOTHING);
      parameters.setLogLostPaths(false);
      assertFalse(capture(() -> project.run(assignment(parameters, 0), 2)).contains("No path:"));
      project.execute("DELETE FROM mini_od WHERE (org=1 AND dst=4) OR (org=3 AND dst=2)");
      parameters.setLogLostPaths(true);
      String output = capture(() -> project.run(assignment(parameters, 0), 2));
      assertFalse(output.contains("No path:"));
      assertFalse(output.contains("Lost-path SQL script:"));
      try (var files = Files.list(directory)) {
        assertFalse(files.anyMatch(file -> file.toString().endsWith(".sql")));
      }
    }
  }

  private AssignmentTestProject disconnectedProject(boolean hsql) throws Exception {
    AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 4, new double[][] {{11, 1, 2, 1, 0}, {12, 3, 4, 1, 0}}, hsql);
    project.execute(
        "INSERT INTO mini_od VALUES " + "(1,1,2,100),(1,1,4,20),(2,3,2,40),(2,3,4,200)");
    project.properties.setProperty(
        NodusC.PROP_ASSIGNMENT_DESCRIPTION + 1, "Test assignment café\ncontinued");
    return project;
  }

  private AssignmentParameters parameters(AssignmentTestProject project, int method)
      throws Exception {
    AssignmentParameters parameters = project.parameters(4);
    parameters.setLogLostPaths(true);
    parameters.setWhereStmt("qty > 0");
    parameters.setNbIterations(3);
    parameters.setPrecision(0);
    parameters.setCostMarkup(1);
    if (method == Assignment.AON_TIME_DEPENDENT || method == Assignment.DYNAMIC_TIME_DEPENDENT) {
      project.execute("ALTER TABLE mini_od ADD time INTEGER DEFAULT 10");
      parameters.setTimeDependent(true);
      parameters.getCostFunctions().setProperty(NodusC.VARNAME_STARTTIME, "10");
      parameters.getCostFunctions().setProperty(NodusC.VARNAME_ENDTIME, "16");
      parameters.getCostFunctions().setProperty(NodusC.VARNAME_TIMESLICE, "2");
    }
    return parameters;
  }

  private Assignment assignment(AssignmentParameters parameters, int method) {
    ModalSplitMethod split =
        new Proportional(parameters.getNodusProject()) {
          @Override
          public boolean split(ODCell demand, List<PathsForMode> modes) {
            return modes.isEmpty() || super.split(demand, modes);
          }
        };
    switch (method) {
      case Assignment.ALL_OR_NOTHING:
        return new AllOrNothingAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}
        };
      case Assignment.MSA:
        return new MSAAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}
        };
      case Assignment.INCREMENTAL:
        return new IncrementalAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}
        };
      case Assignment.FRANK_WOLFE:
        return new FrankWolfeAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}
        };
      case Assignment.INCREMENTAL_FRANK_WOLFE:
        return new IncFrankWolfeAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}
        };
      case Assignment.FAST_MULTI_FLOW:
        return new FastMFAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}

          @Override
          public ModalSplitMethod getModalSplitMethod() {
            return split;
          }
        };
      case Assignment.EXACT_MULTI_FLOW:
        return new ExactMFAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}

          @Override
          public ModalSplitMethod getModalSplitMethod() {
            return split;
          }
        };
      case Assignment.AON_TIME_DEPENDENT:
        return new StaticAoNTimeDependentAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}
        };
      case Assignment.DYNAMIC_TIME_DEPENDENT:
        return new DynamicTimeDependentAssignment(parameters) {
          @Override
          void displayConsoleIfNeeded() {}
        };
      default:
        throw new IllegalArgumentException("Unknown method: " + method);
    }
  }

  private Path script(String output) {
    return Path.of(
        output
            .lines()
            .filter(line -> line.startsWith("Lost-path SQL script: "))
            .findFirst()
            .orElseThrow()
            .substring("Lost-path SQL script: ".length()));
  }
}
