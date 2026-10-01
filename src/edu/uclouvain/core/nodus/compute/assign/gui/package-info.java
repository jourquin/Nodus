/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
