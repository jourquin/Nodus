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

package edu.uclouvain.core.nodus.compute.assign.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.compute.modalsplit.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Route identity includes every ordered virtual link and does not rely on checksums. */
class MultiFlowPathIdentityTest {
  @Test
  void identicalSequencesMatchButOrderDirectionAndExtraOperationsMatter() {
    MultiFlowPathIdentity identity = new MultiFlowPathIdentity();
    Path first = new Path();
    Path duplicate = new Path();
    identity.record(first, List.of(11, 12, 13));
    identity.record(duplicate, List.of(11, 12, 13));
    assertTrue(identity.sameRoute(first, duplicate));

    for (List<Integer> sequence :
        List.of(List.of(12, 11, 13), List.of(11, -12, 13), List.of(11, 12, 14, 13))) {
      Path other = new Path();
      identity.record(other, sequence);
      assertFalse(identity.sameRoute(first, other));
    }
  }

  @Test
  void collidingSequenceHashesDoNotMergeRoutes() {
    MultiFlowPathIdentity identity = new MultiFlowPathIdentity();
    List<Integer> firstLinks = List.of(1, 33);
    List<Integer> otherLinks = List.of(2, 2);
    assertEquals(firstLinks.hashCode(), otherLinks.hashCode());
    Path first = new Path();
    Path other = new Path();
    identity.record(first, firstLinks);
    identity.record(other, otherLinks);
    assertFalse(identity.sameRoute(first, other));
  }

  @Test
  void recordedRoutesSurviveCallerMutationAndAreClearedBetweenChoiceSets() {
    MultiFlowPathIdentity identity = new MultiFlowPathIdentity();
    final Path first = new Path();
    final Path duplicate = new Path();
    List<Integer> links = new ArrayList<>(List.of(1, 2, 3));
    identity.record(first, links);
    links.clear();
    identity.record(duplicate, List.of(1, 2, 3));
    assertTrue(identity.sameRoute(first, duplicate));
    identity.clear();
    identity.record(duplicate, List.of(1, 2, 3));
    assertFalse(identity.sameRoute(first, duplicate));
  }
}
