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

package edu.uclouvain.core.nodus.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Checks real file-lock ownership, including what another JVM can actually acquire. */
@ResourceLock("ProjectLocker")
@Timeout(30)
class ProjectLockerTest {
  @TempDir Path directory;

  @BeforeEach
  void startWithoutLock() {
    ProjectLocker.releaseLock();
  }

  @AfterEach
  void releaseOwnedLock() {
    ProjectLocker.releaseLock();
  }

  @Test
  void releaseIsIdempotentAndTheSameOrAnotherProjectCanBeReacquired() throws Exception {
    ProjectLocker.releaseLock();
    ProjectLocker.releaseLock();
    assertTrue(ProjectLocker.createLock(project("first")));
    ProjectLocker.releaseLock();
    ProjectLocker.releaseLock();
    assertTrue(canAcquire("first"));
    assertTrue(ProjectLocker.createLock(project("first")));
    ProjectLocker.releaseLock();
    assertTrue(ProjectLocker.createLock(project("second with spaces")));
    assertTrue(canAcquire("first"));
    assertFalse(canAcquire("second with spaces"));
    ProjectLocker.releaseLock();
    assertTrue(canAcquire("second with spaces"));
  }

  @Test
  void rejectedSameJvmAcquisitionKeepsTheOriginalOperatingSystemLock() throws Exception {
    final NodusProject owner = project("project");
    assertTrue(ProjectLocker.createLock(owner));
    assertFalse(ProjectLocker.createLock(owner));
    assertFalse(canAcquire("project"), "A rejected acquisition must retain the OS lock");
    assertFalse(ProjectLocker.createLock(project("project")));
    assertFalse(canAcquire("project"));
    ProjectLocker.releaseLock();
    assertTrue(canAcquire("project"));
    assertTrue(ProjectLocker.createLock(owner));
  }

  @Test
  void failedLockFileOpeningCanBeRetriedAfterThePathIsRepaired() throws Exception {
    final Path lockFile = directory.resolve("project" + NodusC.TYPE_LOCK);
    Files.createDirectory(lockFile);
    assertFalse(ProjectLocker.createLock(project("project")));
    ProjectLocker.releaseLock();
    Files.delete(lockFile);
    assertTrue(ProjectLocker.createLock(project("project")));
    assertFalse(canAcquire("project"));
    ProjectLocker.releaseLock();
    assertTrue(canAcquire("project"));
  }

  @Test
  void failedOtherProjectAcquisitionDoesNotDisturbTheCurrentOwner() throws Exception {
    assertTrue(ProjectLocker.createLock(project("project")));
    final Path missing = directory.resolve("missing");
    assertFalse(ProjectLocker.createLock(new LockProject(missing, "other")));
    assertFalse(Files.exists(missing));
    assertFalse(canAcquire("project"));
    ProjectLocker.releaseLock();
    Files.createDirectory(missing);
    assertTrue(ProjectLocker.createLock(new LockProject(missing, "other")));
    ProjectLocker.releaseLock();
    assertTrue(canAcquire("project"));
  }

  @Test
  void anotherProjectCannotReplaceAnActiveLock() throws Exception {
    // Isolate a broken implementation's potentially orphaned channels in a child JVM.
    try (Probe owner = new Probe("replacement", "first", "second")) {
      owner.awaitReady();
      assertFalse(canAcquire("first"));
      assertTrue(canAcquire("second"));
      owner.release();
    }
    assertTrue(canAcquire("first"));
    assertTrue(canAcquire("second"));
  }

  @Test
  void foreignProcessBlocksAcquisitionUntilItReleasesItsLock() throws Exception {
    try (Probe owner = new Probe("hold", "project", "-")) {
      owner.awaitReady();
      assertFalse(ProjectLocker.createLock(project("project")));
      assertFalse(ProjectLocker.createLock(project("project")));
      assertFalse(canAcquire("project"));
      owner.release();
      assertTrue(ProjectLocker.createLock(project("project")));
      assertFalse(canAcquire("project"));
    }
    ProjectLocker.releaseLock();
    assertTrue(canAcquire("project"));
  }

  @Test
  void processTerminationReleasesTheLockEvenWhenTheLockFileRemains() throws Exception {
    final Path lockFile = directory.resolve("project" + NodusC.TYPE_LOCK);
    try (Probe owner = new Probe("hold", "project", "-")) {
      owner.awaitReady();
      assertTrue(Files.exists(lockFile));
      assertFalse(ProjectLocker.createLock(project("project")));
      owner.terminate();
    }
    assertTrue(Files.exists(lockFile));
    assertTrue(ProjectLocker.createLock(project("project")));
    assertFalse(canAcquire("project"));
    ProjectLocker.releaseLock();
    assertTrue(canAcquire("project"));
  }

  @Test
  void simultaneousDifferentProjectAcquisitionsHaveExactlyOneOwner() throws Exception {
    try (Probe owner = new Probe("race", "first", "second")) {
      owner.awaitReady();
      final String winner = Files.readString(owner.control.resolve("result"));
      assertTrue(winner.equals("first") || winner.equals("second"));
      assertFalse(canAcquire(winner));
      assertTrue(canAcquire(winner.equals("first") ? "second" : "first"));
      owner.release();
    }
    assertTrue(canAcquire("first"));
    assertTrue(canAcquire("second"));
  }

  private NodusProject project(String name) {
    return new LockProject(directory, name);
  }

  private boolean canAcquire(String name) throws Exception {
    try (Probe probe = new Probe("try", name, "-")) {
      probe.awaitExit();
      final String result = Files.readString(probe.control.resolve("result"));
      assertTrue(result.equals("true") || result.equals("false"), result);
      return Boolean.parseBoolean(result);
    }
  }

  /** Each probe owns its JVM and only touches this test's temporary directory. */
  private final class Probe implements AutoCloseable {
    final Path control = Files.createTempDirectory(directory, "probe-");
    final Path output = control.resolve("output.log");
    final Process process;

    Probe(String mode, String name, String otherName) throws IOException {
      final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
      process =
          new ProcessBuilder(
                  java,
                  "-Djava.awt.headless=true",
                  "-cp",
                  System.getProperty("java.class.path"),
                  LockProbe.class.getName(),
                  directory.toString(),
                  name,
                  mode,
                  otherName,
                  control.toString())
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
    }

    void awaitReady() throws Exception {
      final Path ready = control.resolve("ready");
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (Files.notExists(ready) && process.isAlive() && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      assertTrue(
          Files.exists(ready), "Lock owner did not become ready: " + Files.readString(output));
    }

    void awaitExit() throws Exception {
      assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Lock probe timed out");
      assertEquals(0, process.exitValue(), Files.readString(output));
    }

    void release() throws Exception {
      Files.createFile(control.resolve("release"));
      awaitExit();
    }

    void terminate() throws InterruptedException {
      process.destroyForcibly();
      assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Lock probe did not terminate");
    }

    @Override
    public void close() throws InterruptedException {
      if (process.isAlive()) {
        terminate();
      }
    }
  }

  /** Headless project that supplies only the two settings consumed by ProjectLocker. */
  private static final class LockProject extends NodusProject {
    private final Path directory;
    private final String name;

    LockProject(Path directory, String name) {
      super(null);
      this.directory = directory;
      this.name = name;
    }

    @Override
    public String getLocalProperty(String key) {
      if (NodusC.PROP_PROJECT_DOTPATH.equals(key)) {
        return directory + File.separator;
      }
      return NodusC.PROP_PROJECT_DOTNAME.equals(key) ? name : null;
    }
  }

  /** Child process used to observe real contention without sharing Java's lock bookkeeping. */
  public static final class LockProbe {
    private LockProbe() {}

    /**
     * Attempts or holds a lock, optionally checking replacement or simultaneous acquisition.
     *
     * @param args project directory, project name, mode, second project name, control directory
     * @throws Exception if a lock check or bounded synchronization fails
     */
    public static void main(String[] args) throws Exception {
      final Path directory = Path.of(args[0]);
      final NodusProject project = new LockProject(directory, args[1]);
      final String mode = args[2];
      final Path control = Path.of(args[4]);
      try {
        if (mode.equals("try")) {
          Files.writeString(
              control.resolve("result"), Boolean.toString(ProjectLocker.createLock(project)));
          return;
        }
        if (mode.equals("race")) {
          race(project, new LockProject(directory, args[3]), args[1], args[3], control);
        } else {
          assertTrue(ProjectLocker.createLock(project));
          if (mode.equals("replacement")) {
            assertFalse(
                ProjectLocker.createLock(new LockProject(directory, args[3])),
                "An active project lock must not be replaced");
          }
        }
        Files.createFile(control.resolve("ready"));
        final Path release = control.resolve("release");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (Files.notExists(release) && System.nanoTime() < deadline) {
          Thread.sleep(10);
        }
        assertTrue(Files.exists(release), "Timed out waiting for the release signal");
      } finally {
        ProjectLocker.releaseLock();
      }
    }

    private static void race(
        NodusProject first, NodusProject second, String firstName, String secondName, Path control)
        throws Exception {
      final var executor = Executors.newFixedThreadPool(2);
      final CountDownLatch ready = new CountDownLatch(2);
      final CountDownLatch start = new CountDownLatch(1);
      try {
        final var firstResult =
            executor.submit(
                () -> {
                  ready.countDown();
                  assertTrue(start.await(5, TimeUnit.SECONDS));
                  return ProjectLocker.createLock(first);
                });
        final var secondResult =
            executor.submit(
                () -> {
                  ready.countDown();
                  assertTrue(start.await(5, TimeUnit.SECONDS));
                  return ProjectLocker.createLock(second);
                });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        final boolean firstAcquired = firstResult.get(5, TimeUnit.SECONDS);
        final boolean secondAcquired = secondResult.get(5, TimeUnit.SECONDS);
        assertEquals(
            1,
            (firstAcquired ? 1 : 0) + (secondAcquired ? 1 : 0),
            "Only one project lock can be owned by a JVM");
        Files.writeString(control.resolve("result"), firstAcquired ? firstName : secondName);
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
      }
    }
  }
}
