/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.Layer;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.results.ResultsFixture.Links;
import edu.uclouvain.core.nodus.compute.results.ResultsFixture.ManualTimer;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Explicit EDT events replace wall-clock waits; real SQL still supplies every displayed period. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(20)
class ResultPlaybackTest {
  @TempDir Path directory;
  private static final String QUERY =
      "SELECT id,SUM(qty) FROM results WHERE grp=1 OR grp=2 GROUP BY id,time ORDER BY id";

  @FunctionalInterface
  private interface Example {
    void run(ResultsFixture fixture, Links links) throws Throwable;
  }

  private Stream<DynamicTest> examples(String label, Example example) {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql + ": " + label,
                    () -> {
                      try (ResultsFixture f = new ResultsFixture(directory, hsql)) {
                        f.add(30, 10, 1, 1, 0);
                        f.add(30, 20, 2, 2, 30);
                        f.add(30, 999, 99, 1, 60);
                        final Links lr = new Links(f, false, true);
                        example.run(f, lr);
                        f.assertClean();
                      }
                    }));
  }

  private static void edt(Executable work) throws Throwable {
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            work.execute();
          } catch (Throwable problem) {
            failure.set(problem);
          }
        });
    if (failure.get() != null) {
      throw failure.get();
    }
  }

  @TestFactory
  Stream<DynamicTest> automaticPlaybackUsesAnExclusiveEndAndAStableScale() {
    return examples(
        "period order and completion",
        (f, lr) ->
            edt(
                () -> {
                  final List<Boolean> done = new ArrayList<>();
                  final List<KeyListener> original = Arrays.asList(f.panel.map.getKeyListeners());
                  assertTrue(
                      lr.displayTimeDependentFlows(
                          QUERY,
                          success -> {
                            assertTrue(SwingUtilities.isEventDispatchThread());
                            done.add(success);
                          }));
                  assertEquals(10, f.object(true, 30).getResult());
                  assertEquals(6, f.object(true, 30).getSize());
                  assertTrue(f.label.getLabelText().endsWith("00:00"));
                  assertEquals(original.size() + 1, f.panel.map.getKeyListeners().length);
                  final ManualTimer first = lr.timers.get(0);
                  assertEquals(500, first.getDelay());
                  lr.tick();
                  assertEquals(20, f.object(true, 30).getResult());
                  assertEquals(11, f.object(true, 30).getSize());
                  assertTrue(f.label.getLabelText().endsWith("00:30"));
                  first.fire(); // An already queued delivery must not skip or repeat a period.
                  assertEquals(2, periods(f).size());
                  lr.tick();
                  assertEquals(List.of(0, 30), periods(f));
                  assertEquals(List.of(true), done);
                  assertEquals(1, lr.completed);
                  assertEquals("original label", f.label.getLabelText());
                  assertEquals(original, Arrays.asList(f.panel.map.getKeyListeners()));
                  for (ManualTimer timer : lr.timers) {
                    assertFalse(timer.isRunning());
                    timer.fire();
                  }
                  assertEquals(List.of(true), done);
                  assertEquals(List.of(0, 30), periods(f));
                  assertFalse(Files.exists(directory.resolve("links_a_results.dbf")));
                }));
  }

  @TestFactory
  Stream<DynamicTest> manualEnterAndEscapeRestoreExistingListenersAndLabels() {
    return examples(
        "manual keys",
        (f, lr) ->
            edt(
                () -> {
                  final KeyAdapter existing = new KeyAdapter() {};
                  f.panel.map.addKeyListener(existing);
                  final List<KeyListener> original = Arrays.asList(f.panel.map.getKeyListeners());
                  final List<Boolean> done = new ArrayList<>();
                  lr.interval = 0;
                  assertTrue(lr.displayTimeDependentFlows(QUERY, done::add));
                  assertTrue(lr.timers.isEmpty());
                  assertEquals(List.of(0), periods(f));
                  f.key(KeyEvent.VK_A);
                  assertEquals(List.of(0), periods(f));
                  f.key(KeyEvent.VK_ENTER);
                  assertEquals(List.of(0, 30), periods(f));
                  f.key(KeyEvent.VK_ESCAPE);
                  f.key(KeyEvent.VK_ESCAPE);
                  f.key(KeyEvent.VK_ENTER);
                  assertEquals(List.of(true), done);
                  assertEquals(0, lr.completed);
                  assertEquals(original, Arrays.asList(f.panel.map.getKeyListeners()));
                  assertEquals("original label", f.label.getLabelText());
                }));
  }

  @TestFactory
  Stream<DynamicTest> periodSettingsUseTheSelectedScenarioAndOnlyCompleteSlices() {
    return examples(
        "scenario settings and final partial slice",
        (f, lr) -> {
          f.costs("60", "151", "30");
          Files.move(
              directory.resolve("results.costs"),
              directory.resolve("selected.costs"),
              StandardCopyOption.REPLACE_EXISTING);
          f.properties.setProperty(NodusC.PROP_COST_FUNCTIONS + "2", "selected.costs");
          f.properties.setProperty(NodusC.PROP_SCENARIO, "2");
          f.add(30, 3, 1, 1, 90);
          f.add(30, 4, 1, 1, 120);
          f.add(30, 5000, 1, 1, 150);
          edt(
              () -> {
                final List<Boolean> done = new ArrayList<>();
                lr.interval = 0;
                assertTrue(lr.displayTimeDependentFlows(QUERY, done::add));
                assertTrue(f.label.getLabelText().endsWith("01:00"));
                f.key(KeyEvent.VK_ENTER);
                f.key(KeyEvent.VK_ENTER);
                assertTrue(f.label.getLabelText().endsWith("02:00"));
                f.key(KeyEvent.VK_ENTER);
                assertEquals(List.of(60, 90, 120), periods(f));
                assertEquals(List.of(true), done);
              });
        });
  }

  @TestFactory
  Stream<DynamicTest> escapeStopsAutomaticPlaybackEvenWhenAnEventWasAlreadyQueued() {
    return examples(
        "queued tick after Escape",
        (f, lr) ->
            edt(
                () -> {
                  final List<Boolean> done = new ArrayList<>();
                  assertTrue(lr.displayTimeDependentFlows(QUERY, done::add));
                  f.key(KeyEvent.VK_ESCAPE);
                  final int queries = f.queries.size();
                  lr.tick();
                  lr.tick();
                  assertEquals(queries, f.queries.size());
                  assertEquals(List.of(true), done);
                  assertFalse(lr.timers.get(0).isRunning());
                }));
  }

  @TestFactory
  Stream<DynamicTest> sqlFailureDuringPlaybackStopsAndReportsExactlyOnce() {
    return Stream.of("time >=", "time = 0", "time = 30")
        .flatMap(
            failure ->
                examples(
                    "SQL failure at " + failure,
                    (f, lr) ->
                        edt(
                            () -> {
                              final List<Boolean> done = new ArrayList<>();
                              final int listeners = f.panel.map.getKeyListeners().length;
                              f.failQuery = failure;
                              boolean started = lr.displayTimeDependentFlows(QUERY, done::add);
                              if (failure.equals("time = 30")) {
                                assertTrue(started);
                                lr.tick();
                                lr.tick();
                                assertEquals(10, f.object(true, 30).getResult());
                              } else {
                                assertFalse(started);
                              }
                              assertEquals(List.of(false), done);
                              assertEquals(1, lr.errors.size());
                              assertEquals(0, lr.completed);
                              assertEquals(listeners, f.panel.map.getKeyListeners().length);
                              assertEquals("original label", f.label.getLabelText());
                              assertFalse(f.panel.busy);
                            })));
  }

  @TestFactory
  Stream<DynamicTest> malformedTimeSettingsFailWithoutInstallingAListenerOrTimer() {
    return Stream.of(
            new String[] {"bad", "60", "30"},
            new String[] {"0", "bad", "30"},
            new String[] {"0", "60", "bad"},
            new String[] {"-1", "60", "30"},
            new String[] {"0", "0", "30"},
            new String[] {"60", "0", "30"},
            new String[] {"0", "60", "0"},
            new String[] {"0", "60", "-30"},
            new String[] {"0", "20", "30"},
            new String[] {"", "60", "30"})
        .flatMap(
            settings ->
                examples(
                    "invalid " + Arrays.toString(settings),
                    (f, lr) -> {
                      f.costs(settings[0], settings[1], settings[2]);
                      edt(
                          () -> {
                            final List<Boolean> done = new ArrayList<>();
                            final int listeners = f.panel.map.getKeyListeners().length;
                            assertFalse(lr.displayTimeDependentFlows(QUERY, done::add));
                            assertEquals(List.of(false), done);
                            assertEquals(1, lr.errors.size());
                            assertTrue(lr.timers.isEmpty());
                            assertTrue(f.queries.isEmpty());
                            assertEquals(listeners, f.panel.map.getKeyListeners().length);
                          });
                    }));
  }

  @TestFactory
  Stream<DynamicTest> missingCostFileAndCanceledPromptHaveDifferentOutcomes() {
    return examples(
        "missing file and canceled prompt",
        (f, lr) -> {
          Files.delete(directory.resolve("results.costs"));
          edt(
              () -> {
                final List<Boolean> done = new ArrayList<>();
                assertFalse(lr.displayTimeDependentFlows(QUERY, done::add));
                lr.interval = null;
                assertTrue(lr.displayTimeDependentFlows(QUERY, done::add));
                assertEquals(List.of(false, true), done);
                assertEquals(1, lr.errors.size());
                assertTrue(lr.timers.isEmpty());
              });
        });
  }

  @TestFactory
  Stream<DynamicTest> replacementPlaybackCannotBeAdvancedByAnOldTimer() {
    return examples(
        "replaced playback",
        (f, lr) ->
            edt(
                () -> {
                  final List<Boolean> first = new ArrayList<>();
                  final List<Boolean> second = new ArrayList<>();
                  assertTrue(lr.displayTimeDependentFlows(QUERY, first::add));
                  final ManualTimer stale = lr.timers.get(0);
                  assertTrue(lr.displayTimeDependentFlows(QUERY, second::add));
                  stale.fire();
                  assertEquals(List.of(0, 0), periods(f));
                  assertEquals(List.of(true), first);
                  assertTrue(second.isEmpty());
                  lr.tick();
                  lr.tick();
                  assertEquals(List.of(true), second);
                }));
  }

  @TestFactory
  Stream<DynamicTest> projectCloseStopsPlaybackBeforeTheConnectionAndMapAreReused() {
    return examples(
        "project cleanup",
        (f, lr) -> {
          final List<Boolean> done = new ArrayList<>();
          final int listeners = f.panel.map.getKeyListeners().length;
          edt(
              () ->
                  assertTrue(
                      lr.displayTimeDependentFlows(
                          QUERY,
                          success -> {
                            assertTrue(SwingUtilities.isEventDispatchThread());
                            done.add(success);
                          })));
          f.closeProject();
          assertEquals(List.of(true), done);
          assertEquals(listeners, f.panel.map.getKeyListeners().length);
          assertEquals("original label", f.label.getLabelText());
          final int queries = f.queries.size();
          edt(
              () -> {
                f.panel.project = new NodusProject(null);
                f.label.setLabelText("next project");
                f.panel.resets = 0;
                for (ManualTimer timer : lr.timers) {
                  assertFalse(timer.isRunning());
                  timer.fire();
                }
                f.key(KeyEvent.VK_ENTER);
                assertEquals("next project", f.label.getLabelText());
                assertEquals(0, f.panel.resets);
              });
          assertEquals(queries, f.queries.size());
          assertEquals(0, f.closedQueries);
          assertEquals(List.of(true), done);
        });
  }

  @TestFactory
  Stream<DynamicTest> playbackWorksWithoutALabelAndWithLargeTimeValues() {
    return examples(
        "no label and wide time",
        (f, lr) -> {
          f.costs("2147483580", "2147483647", "30");
          f.execute("DELETE FROM results");
          f.add(30, 5, 1, 1, 2147483580);
          f.add(30, 7, 1, 1, 2147483610);
          edt(
              () -> {
                f.panel.layers.setLayers(new Layer[0]);
                final List<Boolean> done = new ArrayList<>();
                assertTrue(lr.displayTimeDependentFlows(QUERY, done::add));
                lr.tick();
                lr.tick();
                assertEquals(List.of(2147483580, 2147483610), periods(f));
                assertEquals(List.of(true), done);
              });
        });
  }

  @TestFactory
  Stream<DynamicTest> periodPredicatesRespectSqlStructureAndExistingOrConditions() {
    return Stream.of(
            "SELECT id,SUM(qty) FROM results GROUP BY id,time ORDER BY id;",
            "SELECT id,SUM(qty) AS \"WHERE\" FROM results /* WHERE ignored */ "
                + "WHERE grp=1 OR grp=2 GROUP BY id,time",
            "SELECT id,SUM(qty) FROM (SELECT * FROM results WHERE grp IN (1,2)) r "
                + "GROUP BY id,time",
            "SELECT id,SUM(qty) FROM results WHERE 'where in a string' <> '' "
                + "AND (grp=1 OR grp=2) GROUP BY id,time HAVING SUM(qty)>0",
            "SELECT id,SUM(qty) FROM results WHERE grp=1 OR grp=2 -- WHERE ignored\n"
                + "GROUP BY id,time")
        .flatMap(
            query ->
                examples(
                    query,
                    (f, lr) ->
                        edt(
                            () -> {
                              final List<Boolean> done = new ArrayList<>();
                              assertTrue(lr.displayTimeDependentFlows(query, done::add));
                              assertEquals(10, f.object(true, 30).getResult());
                              lr.tick();
                              assertEquals(20, f.object(true, 30).getResult());
                              lr.tick();
                              assertEquals(List.of(true), done);
                              assertTrue(lr.errors.isEmpty());
                            })));
  }

  @TestFactory
  Stream<DynamicTest> playbackScalingUsesVisibleFilteredLinksInsideTheView() {
    return examples(
        "playback visibility and scale",
        (f, unused) -> {
          f.add(40, 1000, 100, 1, 0);
          f.add(50, 5000, 500, 1, 30);
          f.add(10, 5, 1, 1, 0);
          f.add(10, 10, 1, 1, 30);
          final Links lr = new Links(f, true, false);
          edt(
              () -> {
                assertTrue(lr.displayTimeDependentFlows(QUERY));
                assertEquals(6, f.object(true, 30).getSize());
                f.key(KeyEvent.VK_ESCAPE);
                f.links[0].applyWhereFilter("NUM=10");
                assertTrue(lr.displayTimeDependentFlows(QUERY));
                assertFalse(f.graphic(true, 30).isVisible());
                assertEquals(6, f.object(true, 10).getSize());
                lr.tick();
                assertEquals(11, f.object(true, 10).getSize());
                lr.tick();
              });
        });
  }

  @TestFactory
  Stream<DynamicTest> unsupportedSetQueriesFailBeforeAnyTimerStarts() {
    return examples(
        "set query rejection",
        (f, lr) ->
            edt(
                () -> {
                  final List<Boolean> done = new ArrayList<>();
                  assertFalse(
                      lr.displayTimeDependentFlows(
                          "SELECT id,SUM(qty) FROM results GROUP BY id,time UNION "
                              + "SELECT id,SUM(qty) FROM results GROUP BY id,time",
                          done::add));
                  assertEquals(List.of(false), done);
                  assertEquals(1, lr.errors.size());
                  assertTrue(lr.timers.isEmpty());
                  assertTrue(f.queries.isEmpty());
                }));
  }

  @TestFactory
  Stream<DynamicTest> playbackKeepsItsOriginalConnectionBetweenPeriods() {
    return examples(
        "captured JDBC connection",
        (f, lr) ->
            edt(
                () -> {
                  final List<Boolean> done = new ArrayList<>();
                  assertTrue(lr.displayTimeDependentFlows(QUERY, done::add));
                  final int requests = f.connectionRequests;
                  lr.tick();
                  lr.tick();
                  assertEquals(requests, f.connectionRequests);
                  assertEquals(List.of(true), done);
                  assertTrue(lr.isPlaybackProjectOpen());
                }));
  }

  @TestFactory
  Stream<DynamicTest> closedConnectionsStopBeforeQueryOrDialogRefresh() {
    return examples(
        "connection closed before queued tick",
        (f, lr) -> {
          final List<Boolean> done = new ArrayList<>();
          edt(
              () ->
                  assertTrue(
                      lr.displayTimeDependentFlows(
                          QUERY,
                          success -> {
                            assertFalse(lr.isPlaybackProjectOpen());
                            done.add(success);
                          })));
          final int queries = f.queries.size();
          final int requests = f.connectionRequests;
          f.raw.close();
          edt(
              () -> {
                lr.tick();
                lr.tick();
                assertFalse(lr.isPlaybackProjectOpen());
                assertEquals("original label", f.label.getLabelText());
              });
          assertEquals(queries, f.queries.size());
          assertEquals(requests, f.connectionRequests);
          assertEquals(0, f.closedQueries);
          assertTrue(lr.errors.isEmpty());
          assertEquals(List.of(true), done);
        });
  }

  private static List<Integer> periods(ResultsFixture fixture) {
    return fixture
        .queries
        .stream()
        .filter(sql -> sql.contains("time = "))
        .map(sql -> sql.substring(sql.indexOf("time = ") + 7).split("\\s")[0])
        .map(Integer::valueOf)
        .collect(Collectors.toList());
  }
}
