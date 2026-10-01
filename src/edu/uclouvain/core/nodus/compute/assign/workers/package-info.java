/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
