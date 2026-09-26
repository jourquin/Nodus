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

package com.bbn.openmap.tools.drawing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Independent projections onto horizontal, vertical, diagonal and degenerate segments. */
class InsertedPointTest {
  @TestFactory
  Stream<DynamicTest> knownProjections() {
    double[][] cases = {
      {0, 0, 10, 0, 4, 3, 4, 0, 3, 1},
      {2, 0, 2, 10, 5, 4, 2, 4, 3, 1},
      {0, 0, 10, 10, 2, 4, 3, 3, Math.sqrt(2), 1},
      {10, 10, 0, 0, 2, 4, 3, 3, Math.sqrt(2), 1},
      {0, 0, 10, 0, -2, 3, -2, 0, 3, 0},
      {0, 0, 10, 0, 12, 3, 12, 0, 3, 0},
      {0, 0, 10, 0, 0, 3, 0, 0, 3, 1},
      {0, 0, 10, 0, 10, 3, 10, 0, 3, 1},
      {2, 3, 2, 3, 5, 7, 2, 3, 5, 0}
    };
    return java.util.stream.IntStream.range(0, cases.length)
        .mapToObj(
            i ->
                DynamicTest.dynamicTest(
                    "projection " + i,
                    () -> {
                      double[] c = cases[i];
                      InsertedPoint point = new InsertedPoint(c[0], c[1], c[2], c[3], c[4], c[5]);
                      assertEquals(c[6], point.xi, 1e-12);
                      assertEquals(c[7], point.yi, 1e-12);
                      assertEquals(c[8], point.length, 1e-12);
                      assertEquals(c[9] == 1, point.inclu);
                    }));
  }
}
