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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.workers.AllOrNothingAssignmentWorker;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.testing.AssignmentOutputFixture;
import edu.uclouvain.core.nodus.testing.AssignmentOutputFixture.Fault;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock("JDBCUtils")
@ResourceLock(Resources.SYSTEM_ERR)
@Timeout(30)
class AssignmentOutputFailureTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> failedOutputRetainsTheWholePreviousScenarioAndSkipsCompletion() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(
                        Fault.LATE_BATCH,
                        Fault.BATCH_STATUS,
                        Fault.NETWORK_COMMIT,
                        Fault.COMMIT_ACK_LOST,
                        Fault.PATH_FINAL_BATCH,
                        Fault.PATH_COMMIT,
                        Fault.STATEMENT_CLOSE,
                        Fault.PUBLISH_RENAME,
                        Fault.PUBLISH_RENAME_AFTER,
                        Fault.PUBLISH_BACKUP_AFTER,
                        Fault.PUBLISH_COMMIT)
                    .map(
                        fault ->
                            DynamicTest.dynamicTest(
                                "HSQLDB=" + hsql + ", " + fault,
                                () -> {
                                  try (AssignmentOutputFixture project =
                                      new AssignmentOutputFixture(directory, hsql)) {
                                    baseline(project);
                                    final List<List<String>> previous = project.snapshot();
                                    project.execute("UPDATE mini_od SET qty=qty*2");
                                    project.getMainJDBCConnection().commit();
                                    if (fault == Fault.PATH_FINAL_BATCH) {
                                      project.properties.setProperty(
                                          NodusC.PROP_MAX_SQL_BATCH_SIZE, "1000");
                                    }
                                    project.jdbc.reset(fault);
                                    project.outputPanel.reset();
                                    final ObservedAssignment assignment = observed(project);
                                    assignment.run();
                                    assertTrue(
                                        project.jdbc.triggered,
                                        "Fault must reach the real output boundary: " + fault);
                                    assertEquals(previous, project.snapshot());
                                    assertFailed(project, assignment);
                                    if (fault == Fault.PATH_FINAL_BATCH
                                        || fault == Fault.PATH_COMMIT
                                        || fault == Fault.STATEMENT_CLOSE) {
                                      assertTrue(
                                          project.jdbc.networkCommitted,
                                          "VNET must be saved before the final path fault");
                                    }
                                    // A retry must create a fresh attempt rather than reuse
                                    // canceled writers/table names.
                                    project.jdbc.reset(Fault.NONE);
                                    project.outputPanel.reset();
                                    final ObservedAssignment retry = observed(project);
                                    retry.run();
                                    assertEquals(
                                        List.of(SoundPlayer.SOUND_OK), project.outputPanel.sounds);
                                    assertEquals(1, retry.postCalls);
                                    assertTrue(Files.exists(directory.resolve("post-ran")));
                                    assertEquals(
                                        280,
                                        project.number("SELECT SUM(qty) FROM mini_paths1_header"));
                                    project.assertClean();
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> cancellationAfterNetworkBatchKeepsPreviousResults() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (AssignmentOutputFixture project =
                          new AssignmentOutputFixture(directory, hsql)) {
                        baseline(project);
                        final List<List<String>> previous = project.snapshot();
                        project.jdbc.reset(Fault.NONE);
                        project.outputPanel.reset();
                        project.outputPanel.progress = message -> project.jdbc.networkBatches == 0;
                        final ObservedAssignment assignment = observed(project);
                        assignment.run();
                        assertTrue(project.jdbc.networkBatches >= 1);
                        assertEquals(previous, project.snapshot());
                        assertEquals(0, project.jdbc.rowsAfterRollback);
                        assertFailed(project, assignment);
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> firstAttemptFailureLeavesNoScenarioOutput() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (AssignmentOutputFixture project =
                          new AssignmentOutputFixture(directory, hsql)) {
                        project.jdbc.reset(Fault.PATH_COMMIT);
                        final ObservedAssignment assignment = observed(project);
                        assignment.run();
                        assertTrue(project.jdbc.triggered);
                        assertFalse(JDBCUtils.tableExists("mini_vnet1"));
                        assertFalse(JDBCUtils.tableExists("mini_paths1_header"));
                        assertFalse(JDBCUtils.tableExists("mini_paths1_detail"));
                        assertFailed(project, assignment);
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> nonBatchSuccessPublishesAllOutputsBeforeThePostScript() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (AssignmentOutputFixture project =
                          new AssignmentOutputFixture(directory, hsql)) {
                        baseline(project);
                        final List<List<String>> expected = project.snapshot();
                        project.jdbc.reset(Fault.NO_BATCH);
                        project.outputPanel.reset();
                        final ObservedAssignment assignment = observed(project);
                        assignment.run();
                        assertEquals(expected, project.snapshot());
                        assertEquals(0, project.jdbc.networkBatches);
                        assertTrue(project.jdbc.networkUpdates > 0);
                        assertEquals(1, assignment.postCalls);
                        assertTrue(assignment.sawCompleteOutputs);
                        assertEquals(List.of(SoundPlayer.SOUND_OK), project.outputPanel.sounds);
                        assertTrue(Files.exists(directory.resolve("post-ran")));
                        project.assertClean();
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> headerOnlySuccessRemovesStaleDetailsAfterSuccessfulReplacement() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (AssignmentOutputFixture project =
                          new AssignmentOutputFixture(directory, hsql)) {
                        baseline(project);
                        final ObservedAssignment assignment = observed(project);
                        assignment.getAssignmentParameters().setDetailedPaths(false);
                        assignment.run();
                        assertEquals(
                            List.of(SoundPlayer.SOUND_OK, SoundPlayer.SOUND_OK),
                            project.outputPanel.sounds);
                        assertTrue(JDBCUtils.tableExists("mini_vnet1"));
                        assertTrue(JDBCUtils.tableExists("mini_paths1_header"));
                        assertFalse(JDBCUtils.tableExists("mini_paths1_detail"));
                        project.assertClean();
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> unexpectedCoordinatorFailureStopsWorkersAndPreservesPreviousOutput() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (AssignmentOutputFixture project =
                          new AssignmentOutputFixture(directory, hsql)) {
                        baseline(project);
                        final List<List<String>> previous = project.snapshot();
                        project.outputPanel.reset();
                        project.jdbc.reset(Fault.NONE);
                        final CountDownLatch waiting = new CountDownLatch(1);
                        final WorkQueue queue =
                            new WorkQueue() {
                              @Override
                              public synchronized Object getWork() throws InterruptedException {
                                waiting.countDown();
                                return super.getWork();
                              }
                            };
                        final AssignmentWorker worker = new AllOrNothingAssignmentWorker(queue);
                        final ObservedAssignment assignment =
                            new ObservedAssignment(observed(project).getAssignmentParameters()) {
                              @Override
                              public boolean assign() {
                                createPathWriter();
                                startGarbageCollectionRunner();
                                assignmentWorkers = new AssignmentWorker[] {worker};
                                worker.start();
                                try {
                                  if (!waiting.await(5, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("Worker did not start");
                                  }
                                } catch (InterruptedException interrupted) {
                                  Thread.currentThread().interrupt();
                                  return false;
                                }
                                throw new IllegalStateException("Injected coordinator failure");
                              }
                            };
                        try {
                          assignment.run();
                          assertFalse(worker.isAlive());
                          assertTrue(worker.isCancelled());
                          assertEquals(previous, project.snapshot());
                          assertFailed(project, assignment);
                        } finally {
                          worker.interrupt();
                          worker.join(5000);
                        }
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> failedPostScriptRetainsTheAlreadyCompleteOutputSet() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (AssignmentOutputFixture project =
                          new AssignmentOutputFixture(directory, hsql)) {
                        baseline(project);
                        project.execute("UPDATE mini_od SET qty=qty*2");
                        project.getMainJDBCConnection().commit();
                        project.outputPanel.reset();
                        project.jdbc.reset(Fault.NONE);
                        final ObservedAssignment assignment = observed(project);
                        assignment.failPostScript = true;
                        assignment.run();
                        assertEquals(1, assignment.postCalls);
                        assertEquals(
                            List.of(SoundPlayer.SOUND_FAILURE), project.outputPanel.sounds);
                        assertEquals(
                            280, project.number("SELECT SUM(qty) FROM mini_paths1_header"));
                        assertTrue(JDBCUtils.tableExists("mini_vnet1"));
                        assertTrue(JDBCUtils.tableExists("mini_paths1_detail"));
                        project.assertClean();
                      }
                    }));
  }

  private static void baseline(AssignmentOutputFixture project) {
    new AllOrNothingAssignment(project.parameters(1)).run();
    assertEquals(List.of(SoundPlayer.SOUND_OK), project.outputPanel.sounds);
  }

  private ObservedAssignment observed(AssignmentOutputFixture project) throws Exception {
    Files.deleteIfExists(directory.resolve("post-ran"));
    Files.writeString(
        directory.resolve("after.groovy"),
        "new File(nodusMapPanel.nodusProject.getLocalProperty('project.path'), "
            + "'post-ran').text='done'\n");
    AssignmentParameters parameters = project.parameters(1);
    parameters.setNbIterations(1);
    parameters.setRunPostAssignmentScript(true);
    parameters.setPostAssignmentScript("after.groovy");
    return new ObservedAssignment(parameters);
  }

  private void assertFailed(AssignmentOutputFixture project, ObservedAssignment assignment)
      throws Exception {
    assertEquals(List.of(SoundPlayer.SOUND_FAILURE), project.outputPanel.sounds);
    assertFalse(project.outputPanel.messages.contains(JOptionPane.INFORMATION_MESSAGE));
    assertFalse(project.outputPanel.messages.contains(JOptionPane.WARNING_MESSAGE));
    assertEquals(0, assignment.postCalls);
    assertFalse(Files.exists(directory.resolve("post-ran")));
    assertNull(assignment.getCompletion());
    assertNull(assignment.getPathWriter());
    assertNull(assignment.getAssignmentWorkers());
    assertNull(assignment.getAssignmentParameters().getOutputTables());
    assertTrue(project.outputPanel.getAssignmentMenuItem().isEnabled());
    assertTrue(project.outputPanel.textResets > 0);
    assertTrue(project.outputPanel.scenarioRefreshes >= 2);
    assertTrue(project.outputPanel.lastRefreshWasIdle);
    for (Thread worker : project.outputPanel.workers) {
      assertFalse(worker.isAlive());
    }
    project.assertClean();
  }

  private static class ObservedAssignment extends IncrementalAssignment {
    int postCalls;
    boolean sawCompleteOutputs;
    boolean failPostScript;

    ObservedAssignment(AssignmentParameters parameters) {
      super(parameters);
    }

    @Override
    protected boolean runPostAssignmentScript() {
      postCalls++;
      sawCompleteOutputs =
          JDBCUtils.tableExists("mini_vnet1") && JDBCUtils.tableExists("mini_paths1_header");
      return !failPostScript && super.runPostAssignmentScript();
    }
  }
}
