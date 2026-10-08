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
