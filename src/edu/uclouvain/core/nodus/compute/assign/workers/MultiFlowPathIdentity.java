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

import edu.uclouvain.core.nodus.compute.modalsplit.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Exact route identities for one worker's current set of multi-flow alternatives. */
final class MultiFlowPathIdentity {

  private final Map<List<Integer>, List<Integer>> sequences = new HashMap<>();

  private final Map<Path, List<Integer>> paths = new IdentityHashMap<>();

  /** Records all virtual links in reconstruction order, including handling operations. */
  void record(Path path, List<Integer> linkIds) {
    List<Integer> sequence = sequences.get(linkIds);
    if (sequence == null) {
      sequence = List.copyOf(linkIds);
      sequences.put(sequence, sequence);
    }
    paths.put(path, sequence);
  }

  /** Compares canonical sequences without relying on a potentially colliding numeric checksum. */
  boolean sameRoute(Path first, Path second) {
    List<Integer> sequence = paths.get(first);
    return sequence != null && sequence == paths.get(second);
  }

  /** Releases identities before computing alternatives for the next origin or OD pair. */
  void clear() {
    sequences.clear();
    paths.clear();
  }
}
