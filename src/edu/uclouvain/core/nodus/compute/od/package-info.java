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
