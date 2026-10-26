/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * This file is part of Nodus, distributed under the GNU General Public License,
 * version 3 or later. See http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/** Worker-local horizon totals, with bounded examples instead of one warning per OD demand. */
final class TimeHorizonDiagnostics {
  private static final int MAX_EXAMPLES = 3;

  private final int startMinutes;
  private final int endMinutes;
  private final List<String> examples = new ArrayList<>();
  private long outsideDepartures;
  private double outsideQuantity;
  private long unfinishedRoutes;
  private double unfinishedQuantity;

  TimeHorizonDiagnostics(int startMinutes, int endMinutes) {
    this.startMinutes = startMinutes;
    this.endMinutes = endMinutes;
  }

  boolean isOutsideDeparture(ODCell demand) {
    return demand.getStartingTime() < startMinutes * 60.0
        || demand.getStartingTime() >= endMinutes * 60.0;
  }

  void outsideDeparture(ODCell demand) {
    outsideDepartures++;
    outsideQuantity += demand.getQuantity();
    if (examples.size() < MAX_EXAMPLES) {
      examples.add(
          MessageFormat.format(
              AssignmentWorker.i18n.get(
                  Assignment.class,
                  "Time_horizon_departure",
                  "{0}-{1}: departure {2,number,0.###} minutes is outside the assignment window."),
              Integer.toString(demand.getOriginNodeId()),
              Integer.toString(demand.getDestinationNodeId()),
              demand.getStartingTime() / 60.0));
    }
  }

  void unfinishedRoute(ODCell demand, double arrivalSeconds) {
    unfinishedRoutes++;
    unfinishedQuantity += demand.getQuantity();
    if (examples.size() < MAX_EXAMPLES) {
      examples.add(
          MessageFormat.format(
              AssignmentWorker.i18n.get(
                  Assignment.class,
                  "Time_horizon_arrival",
                  "{0}-{1}: departure {2,number,0.###} minutes; next link arrival "
                      + "{3,number,0.###} minutes reaches or exceeds the assignment end."),
              Integer.toString(demand.getOriginNodeId()),
              Integer.toString(demand.getDestinationNodeId()),
              demand.getStartingTime() / 60.0,
              arrivalSeconds / 60.0));
    }
  }

  void report(int group, byte odClass) {
    if (outsideDepartures == 0 && unfinishedRoutes == 0) {
      return;
    }
    final String summary =
        MessageFormat.format(
            AssignmentWorker.i18n.get(
                Assignment.class,
                "Time_horizon_summary",
                "Time-dependent assignment (group {0}, class {1}, window [{2}, {3}) minutes): "
                    + "{4} OD demands depart outside the window (quantity {5}); "
                    + "{6} OD demands have unfinished routes (quantity {7}). "
                    + "Only the accepted route prefixes are assigned. Examples (at most 3):"),
            group,
            odClass,
            Integer.toString(startMinutes),
            Integer.toString(endMinutes),
            outsideDepartures,
            outsideQuantity,
            unfinishedRoutes,
            unfinishedQuantity);
    System.out.println(
        summary + System.lineSeparator() + String.join(System.lineSeparator(), examples));
  }
}
