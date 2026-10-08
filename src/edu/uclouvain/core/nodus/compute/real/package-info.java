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
 * Computation and display data attached to physical network nodes and links.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.real.RealNetworkObject} associates a network graphic
 * with its source record, label, displayed result and size. {@link
 * edu.uclouvain.core.nodus.compute.real.RealNode} represents a node, while {@link
 * edu.uclouvain.core.nodus.compute.real.RealLink} adds physical length, speed, service membership
 * and vehicle-flow information.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.real.RealLinkInitializer} initializes link data from
 * shape records and geometry. The physical network remains distinct from the
 * mode/means/service-specific graph in {@link edu.uclouvain.core.nodus.compute.virtual}.
 */
package edu.uclouvain.core.nodus.compute.real;
