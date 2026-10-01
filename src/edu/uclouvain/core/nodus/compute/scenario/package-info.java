/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Management and combination of saved assignment scenarios.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.scenario.Scenarios} removes and renumbers scenarios
 * and creates result scenarios by comparing or summing saved scenario data. Comparison and
 * summation accept a filter for the records involved.
 *
 * <p>These operations act on stored results and project scenario information. Running an assignment
 * is the responsibility of {@link edu.uclouvain.core.nodus.compute.assign}.
 */
package edu.uclouvain.core.nodus.compute.scenario;
