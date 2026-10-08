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
