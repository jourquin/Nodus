/**
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.compute.exclusions.Exclusion;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.utils.WorkQueue;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class is intended to run a a thread, and runs CostParser fetched from a WorkQueue.
 *
 * @author Bart Jourquin
 */
public class CostParserWorker extends Thread {

  // I18N mechanism
  private static I18n i18n = Environment.getI18n();

  // Will obtain true if job is canceled
  private boolean canceled = false;

  // Cost parser (already initialized)
  private CostParser costParser;

  // Set of workers (needed in order to be able to cancel a job)
  private CostParserWorker[] costWorker;

  // Error message returned by the parser
  private String errorMessage = null;

  // Queue of groups to compute
  private WorkQueue workQueue;

  // Virtual networ structure
  private VirtualNodeList[] vnl;

  // True if first derivative must be computed
  private boolean withFirstDerivative = false;

  // Value of first derivative of the objective function for this group
  private double firstDerivative = 0.0;

  /**
   * Constructor.
   *
   * @param queue The queue of works (compute costs for a group) to achieve
   */
  public CostParserWorker(WorkQueue queue) {
    this.workQueue = queue;
  }

  /**
   * Cancels the worker.
   *
   * @param errorMessage error message to display.
   */
  public void cancel(String errorMessage) {
    this.errorMessage = errorMessage;
    canceled = true;
  }

  /**
   * Real work starts here. Creates a costParser and computes the cost for a given group and class.
   * Returns false if something went wrong during parsing.
   *
   * @param cwp The parameters needed to achieve the work.
   * @return True on success.
   */
  private boolean computeCosts(CostParserWorkerParameters cwp) {

    VirtualNetwork virtualNetwork = cwp.getVirtualNetwork();
    vnl = virtualNetwork.getVirtualNodeLists();
    byte groupNum = cwp.getGroupNum();
    byte groupIndex = cwp.getGroupIndex();

    costParser = cwp.getCostParser();

    // Scan the VNL structure
    for (int i = 0; i < vnl.length; i++) {

      String msg;
      if (virtualNetwork.getNbODClasses() > 1) {
        msg =
            MessageFormat.format(
                i18n.get(
                    CostParserWorker.class,
                    "Computing_costs_for_class",
                    "Computing costs for class {0}"),
                cwp.getODClass());
      } else {
        msg = i18n.get(CostParserWorker.class, "Computing_costs", "Computing costs");
      }

      if (!cwp.getNodusProject().getNodusMapPanel().updateProgress(msg)) {
        return false;
      }

      Iterator<VirtualNode> nodeLit = vnl[i].getVirtualNodeList().iterator();

      while (nodeLit.hasNext()) {
        VirtualNode vn = nodeLit.next();

        // Iterate through all the virtual links that start from this virtual node
        Iterator<VirtualLink> linkLit = vn.getVirtualLinkList().iterator();

        while (linkLit.hasNext()) {
          if (canceled) {
            return false;
          }

          VirtualLink vl = linkLit.next();

          // Reset
          vl.setWeight(groupIndex, CostParser.UNDEFINED_FUNCTION);

          if (!isVirtualLinkExcluded(i, vl, groupNum)) {
            double cost = 0.0;

            cost = costParser.compute(vl);

            if (cost == CostParser.PARSER_ERROR) {
              return false;
            }

            vl.setWeight(groupIndex, cost);

            if (withFirstDerivative) {
              firstDerivative +=
                  (vl.getAuxiliaryFlow(groupIndex) - vl.getCurrentFlow(groupIndex))
                      * vl.getWeight(groupIndex);
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * Returns the error message of the parser.
   *
   * @return The error message, or null if no error.
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Returns the first derivative of the objective function value computed for this group (for
   * Frank-Wolfe assignments only).
   *
   * @return The value of the first derivative of the objective function.
   */
  public double getFirstDerivative() {
    return firstDerivative;
  }

  /**
   * Returns true if the worker was canceled.
   *
   * @return True if the worker is canceled.
   */
  public boolean isCancelled() {
    return canceled;
  }

  /**
   * Returns true if a given virtual link is excluded.
   *
   * @param index Index of virtual node.
   * @param vl Virtual link.
   * @param group Group of commodities.
   * @return True if virtual link is excluded.
   */
  boolean isVirtualLinkExcluded(int index, VirtualLink vl, byte group) {
    // Exclusion lists only exist for transhipment nodes
    if (!vnl[index].isTranshipmentNode() && !vnl[index].isLoadingUnloadingNode()) {
      return false;
    }

    LinkedList<Exclusion> exclusionList = vnl[index].getExclusionList();

    // Scan the exclusions list
    Iterator<Exclusion> lit = exclusionList.iterator();

    while (lit.hasNext()) {
      Exclusion exc = lit.next();

      if (exc.isExcluded(
          group,
          vnl[index].getRealNodeId(),
          vl.getBeginVirtualNode().getMode(),
          vl.getBeginVirtualNode().getMeans(),
          vl.getEndVirtualNode().getMode(),
          vl.getEndVirtualNode().getMeans())) {
        return true;
      }
    }

    return false;
  }

  /** Main entry point of the worker thread. Listens until no more tasks are in queue. */
  @Override
  public void run() {
    try {
      while (true) {
        // Retrieve some work; block if the queue is empty
        Object x = workQueue.getWork();

        // Terminate if the end-of-stream marker was retrieved
        if (x == WorkQueue.NO_MORE_WORK) {
          break;
        }
        CostParserWorkerParameters cwp = (CostParserWorkerParameters) x;
        costWorker = cwp.getCostWorkers();
        withFirstDerivative = cwp.isWithFirstDerivative();

        // Start the real work
        if (!computeCosts(cwp) && !isCancelled()) {
          errorMessage = costParser.getErrorMessage();
          // Cancel all workers
          for (CostParserWorker element : costWorker) {
            element.cancel(errorMessage);
          }
        }
      }
    } catch (InterruptedException e) {
      // Nothing to do
    }
  }
}
