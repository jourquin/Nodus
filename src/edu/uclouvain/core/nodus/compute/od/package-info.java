/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Origin-destination demand records and loading of project demand tables.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.od.ODReader} identifies candidate OD tables and loads
 * the selected demand into a virtual network, applying the assignment filters and interpreting the
 * available group, class and time information.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.od.ODCell} stores origin, destination and quantity
 * together with the commodity group and optional demand class or departure time. It also retains
 * relocation state used when a dynamic assignment continues a journey in a later time slice.
 */
package edu.uclouvain.core.nodus.compute.od;
