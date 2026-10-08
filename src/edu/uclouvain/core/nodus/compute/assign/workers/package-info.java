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
 * Queued routing jobs, path reconstruction and flow updates for assignment algorithms.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker} consumes jobs from a
 * work queue and provides common cancellation and error handling. {@link
 * edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorkerParameters} identifies the
 * assignment context, commodity group, demand class and iteration.
 *
 * <p>Specialized workers perform the routing and loading steps of their corresponding assignment
 * algorithms. Multi-flow workers gather alternatives and apply modal-choice shares, while
 * time-dependent workers handle departure and arrival timing. {@link
 * edu.uclouvain.core.nodus.compute.assign.workers.PathWeights} and {@link
 * edu.uclouvain.core.nodus.compute.assign.workers.MFPathHeader} hold path attributes for
 * computation and output.
 */
package edu.uclouvain.core.nodus.compute.assign.workers;
