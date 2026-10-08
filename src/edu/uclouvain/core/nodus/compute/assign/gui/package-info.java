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
 * Selection and launch of transport-network assignments.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.assign.gui.AssignmentDlg} gathers the assignment
 * method, demand table, cost functions, scenario and applicable routing/output options. It creates
 * the corresponding {@link edu.uclouvain.core.nodus.compute.assign.AssignmentParameters} and
 * assignment implementation.
 *
 * <p>The available controls depend on the selected procedure. Modal-choice parameter estimation has
 * its own dialog in {@link edu.uclouvain.core.nodus.compute.modalsplit}; this package configures
 * the subsequent assignment.
 */
package edu.uclouvain.core.nodus.compute.assign.gui;
