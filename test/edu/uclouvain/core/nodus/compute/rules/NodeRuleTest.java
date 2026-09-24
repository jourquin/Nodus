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

package edu.uclouvain.core.nodus.compute.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks movement matching and the documented precedence of node rules. */
class NodeRuleTest {
  @Test
  void exactRuleRequiresMatchingScenarioGroupAndBothModeMeansPairs() {
    NodeRule rule = new NodeRule(-10, 7, 3, 1, 2, 4, 5);
    assertTrue(rule.isExcluded(10, 7, 3, 1, 2, 4, 5));
    assertFalse(rule.isExcluded(10, 8, 3, 1, 2, 4, 5));
    assertFalse(rule.isExcluded(10, 7, 6, 1, 2, 4, 5));
    assertFalse(rule.isExcluded(10, 7, 3, 9, 2, 4, 5));
    assertFalse(rule.isExcluded(10, 7, 3, 1, 9, 4, 5));
    assertFalse(rule.isExcluded(10, 7, 3, 1, 2, 9, 5));
    assertFalse(rule.isExcluded(10, 7, 3, 1, 2, 4, 9));
    assertFalse(rule.isExcluded(10, 7, 3, 4, 5, 1, 2));
  }

  @Test
  void wildcardFieldsMatchIndependentlyInExclusionAndInclusionRules() {
    // Only one field is wildcarded at a time; other fields must still match.
    for (int wildcard = 0; wildcard < 6; wildcard++) {
      final int[] pattern = {7, 3, 1, 2, 4, 5};
      final int[] movement = pattern.clone();
      pattern[wildcard] = -1;
      movement[wildcard] = 9;
      NodeRule rule =
          new NodeRule(-10, pattern[0], pattern[1], pattern[2], pattern[3], pattern[4], pattern[5]);
      assertTrue(
          rule.isExcluded(
              10, movement[0], movement[1], movement[2], movement[3], movement[4], movement[5]),
          "Wildcard field " + wildcard);
      NodeRule inclusion =
          new NodeRule(10, pattern[0], pattern[1], pattern[2], pattern[3], pattern[4], pattern[5]);
      assertTrue(
          inclusion.isIncluded(
              10, movement[0], movement[1], movement[2], movement[3], movement[4], movement[5]),
          "Wildcard field " + wildcard);
      movement[(wildcard + 1) % 6] = 8;
      assertFalse(
          rule.isExcluded(
              10, movement[0], movement[1], movement[2], movement[3], movement[4], movement[5]));
      assertFalse(
          inclusion.isIncluded(
              10, movement[0], movement[1], movement[2], movement[3], movement[4], movement[5]));
    }
  }

  @Test
  void transitRemainsAllowedButChangingMeansIsStillAMovement() {
    NodeRule exclusion = new NodeRule(-10, -1, -1, -1, -1, -1, -1);
    assertFalse(exclusion.isExcluded(10, 7, 3, 1, 2, 1, 2));
    assertTrue(exclusion.isExcluded(10, 7, 3, 1, 2, 1, 3));
    NodeRule inclusion = new NodeRule(10, 99, 99, 9, 9, 8, 8);
    assertTrue(inclusion.isIncluded(10, 7, 3, 1, 2, 1, 2));
    assertFalse(inclusion.isIncluded(10, 7, 3, 1, 2, 1, 3));
  }

  @Test
  void specificGroupRulesOverrideGenericRulesRegardlessOfInsertionOrder() {
    final VirtualNodeList node = new VirtualNodeList(10, NodusC.HANDLING_ALL, null);
    final NodeRule specific = new NodeRule(-10, -1, 3, 1, 2, 4, 5);
    final NodeRule generic = new NodeRule(-10, -1, -1, 6, 7, 8, 9);
    node.addExclusion(specific);
    node.addExclusion(generic);
    assertEquals(List.of(specific), node.getExclusions(7, 3));
    assertEquals(List.of(generic), node.getExclusions(7, 4));
  }

  @Test
  void selectedScenarioRulesOverrideScenarioIndependentRules() {
    final VirtualNodeList node = new VirtualNodeList(10, NodusC.HANDLING_ALL, null);
    final NodeRule generic = new NodeRule(-10, -1, -1, 1, 2, 4, 5);
    final NodeRule specific = new NodeRule(10, 7, 3, 1, 2, 4, 5);
    node.addExclusion(generic);
    node.addExclusion(specific);
    assertEquals(List.of(specific), node.getExclusions(7, 3));
    assertFalse(node.getExclusions(7, 3).getFirst().isExclusion());
    // The selected scenario defines its own rule set, with no rule for group 4.
    assertNull(node.getExclusions(7, 4));
  }
}
