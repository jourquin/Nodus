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

import org.junit.jupiter.api.Test;

/** Checks that every transport operation contributes to reported path totals. */
class PathWeightsTest {
  @Test
  void includesAllSevenCostComponents() {
    PathWeights weights = new PathWeights();
    weights.ldCost = 1;
    weights.mvCost = 2;
    weights.tpCost = 4;
    weights.trCost = 8;
    weights.ulCost = 16;
    weights.stpCost = 32;
    weights.swCost = 64;
    assertEquals(127, weights.getCost(), 1e-12);
  }

  @Test
  void includesAllSevenDurationComponents() {
    PathWeights weights = new PathWeights();
    weights.ldDuration = 1;
    weights.mvDuration = 2;
    weights.tpDuration = 4;
    weights.trDuration = 8;
    weights.ulDuration = 16;
    weights.stpDuration = 32;
    weights.swDuration = 64;
    assertEquals(127, weights.getTransitTime(), 1e-6);
  }
}
