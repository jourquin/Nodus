/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Assignment algorithms and coordination of network loading, workers and results.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.assign.Assignment} provides the common execution
 * framework. Implementations include all-or-nothing, incremental, successive-averages, Frank-Wolfe,
 * multi-flow and time-dependent procedures. They coordinate virtual-network preparation, cost
 * evaluation and worker jobs for the selected demand.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.assign.AssignmentParameters} carries the selected
 * settings. {@link edu.uclouvain.core.nodus.compute.assign.AssignmentCompletion} records stopping
 * and convergence information, and {@link
 * edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes} provides optional timing
 * diagnostics.
 *
 * <p>Multi-flow workers delegate modal shares to {@link
 * edu.uclouvain.core.nodus.compute.modalsplit}. Parameter estimation is a separate workflow that
 * reuses route-cost computation without publishing ordinary assignment result tables. See each
 * algorithm for its own iteration and stopping rules.
 */
package edu.uclouvain.core.nodus.compute.assign;
