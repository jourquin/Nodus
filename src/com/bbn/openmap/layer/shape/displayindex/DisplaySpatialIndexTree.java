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

package com.bbn.openmap.layer.shape.displayindex;

import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.util.DataBounds;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * An immutable bounding-box tree for repeated viewport queries on one geometry revision.
 *
 * <p>Small leaves are scanned directly. Internal bounds let a query skip whole branches outside the
 * view. Matches are collected by their original positions, so spatial partitioning never changes
 * drawing order, overlapping-object selection or multipart graphic structure. Query state is local
 * and the tree can be shared by successive rendering workers.
 */
public final class DisplaySpatialIndexTree extends DisplaySpatialIndexLinear {

  private static final int LEAF_SIZE = 32;

  /** Original position accompanies each entry when construction sorts it into spatial groups. */
  private static final class Entry {
    final int position;
    final double minX;
    final double minY;
    final double maxX;
    final double maxY;

    Entry(int position, BoundsEntry original) {
      this.position = position;
      minX = original.bounds.getMin().getX();
      minY = original.bounds.getMin().getY();
      maxX = original.bounds.getMax().getX();
      maxY = original.bounds.getMax().getY();
    }

    boolean isFinite() {
      return Double.isFinite(minX)
          && Double.isFinite(minY)
          && Double.isFinite(maxX)
          && Double.isFinite(maxY);
    }
  }

  /** A contiguous range of spatially ordered entries, with a bounding box for pruning. */
  private static final class Branch {
    final int from;
    final int to;
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    Branch left;
    Branch right;

    Branch(Entry[] entries, int from, int to) {
      this.from = from;
      this.to = to;
      for (int i = from; i < to; i++) {
        Entry entry = entries[i];
        minX = Math.min(minX, entry.minX);
        minY = Math.min(minY, entry.minY);
        maxX = Math.max(maxX, entry.maxX);
        maxY = Math.max(maxY, entry.maxY);
      }
      if (to - from > LEAF_SIZE) {
        boolean splitX = maxX - minX >= maxY - minY;
        int middle = from + (to - from) / 2;
        partition(entries, from, to, middle, splitX);
        left = new Branch(entries, from, middle);
        right = new Branch(entries, middle, to);
      }
    }
  }

  /**
   * Partitions around the median without fully sorting each branch. This avoids repeating an O(n
   * log n) sort at every tree level; equal centers still split into balanced ranges.
   */
  private static void partition(Entry[] entries, int from, int to, int middle, boolean splitX) {
    int left = from;
    int right = to - 1;
    while (left < right) {
      double pivot = center(entries[left + (right - left) / 2], splitX);
      int low = left;
      int high = right;
      while (low <= high) {
        while (low <= right && center(entries[low], splitX) < pivot) {
          low++;
        }
        while (high >= left && center(entries[high], splitX) > pivot) {
          high--;
        }
        if (low <= high) {
          Entry swap = entries[low];
          entries[low++] = entries[high];
          entries[high--] = swap;
        }
      }
      if (middle <= high) {
        right = high;
      } else if (middle >= low) {
        left = low;
      } else {
        return;
      }
    }
  }

  /** Computes a center without overflowing the sum of two finite bounds. */
  private static double center(Entry entry, boolean x) {
    return x ? entry.minX / 2 + entry.maxX / 2 : entry.minY / 2 + entry.maxY / 2;
  }

  private final Entry[] entries;
  private final Branch root;

  /**
   * Builds a geographic tree from the current source geometry.
   *
   * @param list Source graphics; rebuild the index after adding, removing or moving geometry.
   */
  public DisplaySpatialIndexTree(OMGraphicList list) {
    super(list);
    List<BoundsEntry> source = getBoundsEntryList();
    entries = new Entry[source.size()];
    boolean finite = true;
    for (int i = 0; i < entries.length; i++) {
      entries[i] = new Entry(i, source.get(i));
      finite &= entries[i].isFinite();
    }
    // Non-finite bounds retain the linear implementation's comparison semantics.
    root = finite && entries.length > LEAF_SIZE ? new Branch(entries, 0, entries.length) : null;
  }

  @Override
  public String getName() {
    return "SpatialIndexTree";
  }

  @Override
  protected List<BoundsEntry> getBoundsEntrySubSet(DataBounds area, DataBounds secondArea) {
    if (root == null) {
      return super.getBoundsEntrySubSet(area, secondArea);
    }
    if (area.getMin().getX() < root.minX
        && area.getMin().getY() < root.minY
        && area.getMax().getX() > root.maxX
        && area.getMax().getY() > root.maxY) {
      // A zoomed-out view covering the whole layer needs neither traversal nor a match bit set.
      return new ArrayList<>(getBoundsEntryList());
    }
    BitSet positions = new BitSet();
    collect(root, area, positions);
    if (secondArea != null) {
      collect(root, secondArea, positions);
    }
    List<BoundsEntry> source = getBoundsEntryList();
    List<BoundsEntry> matches = new ArrayList<>(positions.cardinality());
    for (int i = positions.nextSetBit(0); i >= 0; i = positions.nextSetBit(i + 1)) {
      matches.add(source.get(i));
    }
    return matches;
  }

  /** Prunes disjoint branches and uses the same strict boundary comparisons as the linear index. */
  private void collect(Branch branch, DataBounds area, BitSet positions) {
    double minX = area.getMin().getX();
    double minY = area.getMin().getY();
    double maxX = area.getMax().getX();
    double maxY = area.getMax().getY();
    if (!intersects(branch.minX, branch.minY, branch.maxX, branch.maxY, minX, minY, maxX, maxY)) {
      return;
    }
    if (minX < branch.minX && minY < branch.minY && maxX > branch.maxX && maxY > branch.maxY) {
      // Strict containment also handles points and lines on leaf boundaries correctly.
      for (int i = branch.from; i < branch.to; i++) {
        positions.set(entries[i].position);
      }
    } else if (branch.left == null) {
      for (int i = branch.from; i < branch.to; i++) {
        Entry entry = entries[i];
        if (intersects(entry.minX, entry.minY, entry.maxX, entry.maxY, minX, minY, maxX, maxY)) {
          positions.set(entry.position);
        }
      }
    } else {
      collect(branch.left, area, positions);
      collect(branch.right, area, positions);
    }
  }
}
