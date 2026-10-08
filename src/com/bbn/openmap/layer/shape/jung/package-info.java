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
 * Node and edge data for graphical inspection of the virtual transport network.
 *
 * <p>{@link com.bbn.openmap.layer.shape.jung.JungVirtualNode} identifies a displayed node by its
 * real-network location, mode, means and service. {@link
 * com.bbn.openmap.layer.shape.jung.JungVirtualLink} carries edge attributes such as quantity, unit
 * cost, vehicles, time and operation type.
 *
 * <p>The JUNG viewer uses these objects to represent queried network results. The assignment graph
 * itself is defined in {@link edu.uclouvain.core.nodus.compute.virtual}.
 */
package com.bbn.openmap.layer.shape.jung;
