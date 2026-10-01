/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
