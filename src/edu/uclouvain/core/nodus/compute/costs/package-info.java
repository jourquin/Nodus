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
 * Evaluation of cost, duration and vehicle-conversion functions on virtual networks.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.costs.CostParser} interprets the properties-style cost
 * definitions for a scenario, commodity group, demand class and time slice. Expressions can
 * describe movement and node operations, including loading, unloading, transhipment, service stops
 * and service changes.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.costs.CostParserWorker} evaluates queued parser jobs.
 * Parsed expressions are cached within their parser context and their variable bindings are
 * refreshed before reuse. {@link edu.uclouvain.core.nodus.compute.costs.VehiclesParser} reads
 * average loads and passenger-car-unit factors used to convert assigned quantities to vehicle
 * flows.
 */
package edu.uclouvain.core.nodus.compute.costs;
