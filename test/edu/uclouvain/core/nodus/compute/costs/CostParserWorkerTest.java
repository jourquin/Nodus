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

package edu.uclouvain.core.nodus.compute.costs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.rules.NodeRule;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Applies complete rule sets through the cost worker, without a database, parser or GUI. */
class CostParserWorkerTest {
  private static final int[] FIRST = {1, 1, 2, 1};
  private static final int[] SECOND = {1, 2, 2, 2};
  private static final int[] OTHER = {3, 1, 4, 1};
  private static final int[] REVERSE = {2, 1, 1, 1};
  private static final int[] TRANSIT = {1, 1, 1, 1};
  private static final int[] CHANGE_MEANS = {1, 1, 1, 2};

  @TestFactory
  List<DynamicTest> supportedRuleSets() {
    List<DynamicTest> tests = new ArrayList<>();
    for (int handling :
        new int[] {NodusC.HANDLING_ALL, NodusC.HANDLING_TRANSHIP, NodusC.HANDLING_LOAD_UNLOAD}) {
      for (boolean reverseOrder : new boolean[] {false, true}) {
        String name = "handling " + handling + (reverseOrder ? ", reversed: " : ": ");
        tests.add(
            dynamicTest(
                name + "all exclusions are applied and other movements remain allowed",
                () -> {
                  Fixture fixture = new Fixture(handling);
                  fixture.rules(false, reverseOrder, FIRST, SECOND);
                  assertTrue(fixture.excluded(FIRST, 7, 3));
                  assertTrue(fixture.excluded(SECOND, 7, 3));
                  assertFalse(fixture.excluded(OTHER, 7, 3));
                  assertFalse(fixture.excluded(REVERSE, 7, 3));
                }));
        tests.add(
            dynamicTest(
                name + "any inclusion permits a movement and unmatched ones are rejected",
                () -> {
                  Fixture fixture = new Fixture(handling);
                  fixture.rules(true, reverseOrder, FIRST, SECOND);
                  assertFalse(fixture.excluded(FIRST, 7, 3));
                  assertFalse(fixture.excluded(SECOND, 7, 3));
                  assertTrue(fixture.excluded(OTHER, 7, 3));
                  assertTrue(fixture.excluded(REVERSE, 7, 3));
                }));
      }
      tests.add(
          dynamicTest(
              "handling " + handling + ": no rules leave movements available",
              () -> {
                Fixture fixture = new Fixture(handling);
                assertFalse(fixture.excluded(FIRST, 7, 3));
                assertFalse(fixture.excluded(OTHER, 7, 3));
              }));
      tests.add(
          dynamicTest(
              "handling " + handling + ": transit survives a blanket exclusion",
              () -> {
                Fixture fixture = new Fixture(handling);
                fixture.add(false, -1, -1, new int[] {-1, -1, -1, -1});
                assertFalse(fixture.excluded(TRANSIT, 7, 3));
                assertTrue(fixture.excluded(CHANGE_MEANS, 7, 3));
                assertTrue(fixture.excluded(FIRST, 7, 3));
              }));
      tests.add(
          dynamicTest(
              "handling " + handling + ": transit survives restrictive inclusions",
              () -> {
                Fixture fixture = new Fixture(handling);
                fixture.add(true, -1, -1, FIRST);
                assertFalse(fixture.excluded(TRANSIT, 7, 3));
                assertFalse(fixture.excluded(FIRST, 7, 3));
                assertTrue(fixture.excluded(CHANGE_MEANS, 7, 3));
              }));
    }
    return tests;
  }

  @Test
  void wildcardRulesApplyWithoutAllowingUnmatchedCombinations() throws Exception {
    Fixture fixture = new Fixture(NodusC.HANDLING_ALL);
    fixture.add(true, -1, -1, new int[] {1, -1, 2, 1});
    fixture.add(true, -1, -1, new int[] {3, 1, -1, 2});
    assertFalse(fixture.excluded(FIRST, 7, 3));
    assertFalse(fixture.excluded(new int[] {1, 9, 2, 1}, 7, 3));
    assertFalse(fixture.excluded(new int[] {3, 1, 8, 2}, 7, 3));
    assertTrue(fixture.excluded(new int[] {1, 9, 2, 3}, 7, 3));
    assertTrue(fixture.excluded(REVERSE, 7, 3));
  }

  @Test
  void loadingAndUnloadingHaveDistinctDirections() throws Exception {
    Fixture fixture = new Fixture(NodusC.HANDLING_LOAD_UNLOAD);
    fixture.add(true, -1, -1, new int[] {0, 0, 1, 1});
    assertFalse(fixture.excluded(0, new int[] {0, 0, 1, 1}, VirtualLink.TYPE_LOAD, 7, 3));
    assertTrue(fixture.excluded(0, new int[] {1, 1, 0, 0}, VirtualLink.TYPE_UNLOAD, 7, 3));
    fixture.add(true, -1, -1, new int[] {2, 1, 0, 0});
    assertFalse(fixture.excluded(0, new int[] {2, 1, 0, 0}, VirtualLink.TYPE_UNLOAD, 7, 3));
  }

  @Test
  void groupSpecificRulesReplaceGenericRulesWithoutLeakingIntoOtherGroups() throws Exception {
    Fixture fixture = new Fixture(NodusC.HANDLING_ALL);
    fixture.add(false, -1, -1, FIRST);
    fixture.add(false, -1, 3, SECOND);
    for (int group : new int[] {3, 4, 3}) {
      assertTrue(fixture.excluded(group == 3 ? SECOND : FIRST, 7, group));
      assertFalse(fixture.excluded(group == 3 ? FIRST : SECOND, 7, group));
    }
  }

  @Test
  void groupSpecificInclusionsDoNotRestrictOtherGroups() throws Exception {
    Fixture fixture = new Fixture(NodusC.HANDLING_ALL);
    fixture.add(true, -1, 3, FIRST);
    assertTrue(fixture.excluded(OTHER, 7, 3));
    assertFalse(fixture.excluded(OTHER, 7, 4));
    assertTrue(fixture.excluded(OTHER, 7, 3));
  }

  @Test
  void scenarioSpecificInclusionsDoNotRestrictOtherScenarios() throws Exception {
    Fixture fixture = new Fixture(NodusC.HANDLING_ALL);
    fixture.add(true, 7, -1, FIRST);
    assertTrue(fixture.excluded(OTHER, 7, 3));
    assertFalse(fixture.excluded(FIRST, 8, 3));
    assertFalse(fixture.excluded(OTHER, 8, 3));
    assertTrue(fixture.excluded(OTHER, 7, 3));
  }

  @Test
  void selectedScenarioReplacesGenericRulesAndOtherScenariosStillUseTheDefaults() throws Exception {
    Fixture fixture = new Fixture(NodusC.HANDLING_ALL);
    fixture.add(false, -1, -1, FIRST);
    fixture.add(true, 7, 3, SECOND);
    assertFalse(fixture.excluded(SECOND, 7, 3));
    assertTrue(fixture.excluded(FIRST, 7, 3));
    assertTrue(fixture.excluded(OTHER, 7, 3));
    // A scenario supplies a complete rule set, including the absence of rules for group 4.
    assertFalse(fixture.excluded(FIRST, 7, 4));
    assertTrue(fixture.excluded(FIRST, 8, 3));
    assertTrue(fixture.excluded(FIRST, 8, 4));
    assertFalse(fixture.excluded(SECOND, 8, 3));
    assertFalse(fixture.excluded(SECOND, 7, 3));
  }

  @Test
  void severalScenariosKeepTheirOwnGroupRules() throws Exception {
    Fixture fixture = new Fixture(NodusC.HANDLING_ALL);
    fixture.add(true, 7, 3, FIRST);
    fixture.add(true, 8, 3, SECOND);
    assertFalse(fixture.excluded(FIRST, 7, 3));
    assertTrue(fixture.excluded(SECOND, 7, 3));
    assertTrue(fixture.excluded(FIRST, 8, 3));
    assertFalse(fixture.excluded(SECOND, 8, 3));
    assertFalse(fixture.excluded(OTHER, 9, 3));
  }

  @Test
  void rulesBelongToTheirNodeAndDoNotLeakThroughWorkerReuse() throws Exception {
    Fixture fixture = new Fixture(NodusC.HANDLING_ALL);
    fixture.add(true, -1, -1, FIRST);
    assertTrue(fixture.excluded(OTHER, 7, 3));
    assertFalse(fixture.excluded(1, OTHER, VirtualLink.TYPE_TRANSHIP, 7, 3));
    assertTrue(fixture.excluded(OTHER, 7, 3));
  }

  @Test
  void nodesWithoutLoadingOrTranshipmentBypassNodeRules() throws Exception {
    for (int handling : new int[] {NodusC.HANDLING_NONE, NodusC.SERVICE_CHANGE}) {
      Fixture fixture = new Fixture(handling);
      fixture.add(false, -1, -1, new int[] {-1, -1, -1, -1});
      assertFalse(fixture.excluded(FIRST, 7, 3));
      assertFalse(fixture.excluded(OTHER, 7, 3));
    }
  }

  private static final class Fixture {
    final VirtualNodeList[] nodes;
    final CostParserWorker worker = new CostParserWorker(new WorkQueue());

    Fixture(int handling) throws Exception {
      nodes =
          new VirtualNodeList[] {
            new VirtualNodeList(10, handling, null),
            new VirtualNodeList(20, NodusC.HANDLING_ALL, null)
          };
      // Only seed the worker's input network; rule selection and application remain real.
      Field field = CostParserWorker.class.getDeclaredField("vnl");
      field.setAccessible(true);
      field.set(worker, nodes);
    }

    void rules(boolean inclusion, boolean reverse, int[] first, int[] second) {
      add(inclusion, -1, -1, reverse ? second : first);
      add(inclusion, -1, -1, reverse ? first : second);
    }

    void add(boolean inclusion, int scenario, int group, int[] movement) {
      nodes[0].addExclusion(
          new NodeRule(
              inclusion ? 10 : -10,
              scenario,
              group,
              movement[0],
              movement[1],
              movement[2],
              movement[3]));
    }

    boolean excluded(int[] movement, int scenario, int group) {
      return excluded(0, movement, VirtualLink.TYPE_TRANSHIP, scenario, group);
    }

    boolean excluded(int index, int[] movement, byte type, int scenario, int group) {
      int realNode = nodes[index].getRealNodeId();
      VirtualNode from =
          new VirtualNode(1, realNode, 11, (byte) movement[0], (byte) movement[1], (short) 0, 0, 0);
      VirtualNode to =
          new VirtualNode(2, realNode, 12, (byte) movement[2], (byte) movement[3], (short) 0, 0, 0);
      VirtualLink link = new VirtualLink(1, 0, index, from, to, type);
      return worker.isVirtualLinkExcluded(index, link, scenario, (byte) group);
    }
  }
}
