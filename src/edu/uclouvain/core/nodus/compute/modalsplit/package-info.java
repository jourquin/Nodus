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
 * Modal-choice models, numerical parameter estimation and the standalone estimation dialog.
 *
 * <p>The assignment-time extension point is {@link
 * edu.uclouvain.core.nodus.compute.modalsplit.ModalSplitMethod}. Routing supplies available
 * alternatives as {@link edu.uclouvain.core.nodus.compute.modalsplit.PathsForMode} groups of
 * mutable {@link edu.uclouvain.core.nodus.compute.modalsplit.Path} objects. A model updates their
 * modal and route shares. A worker uses its own cloned method, initializes its commodity group and
 * reuses it for that group's OD records. Choice does not rewrite physical route costs.
 *
 * <p>The built-in models share availability conventions but use distinct parameters:
 *
 * <ul>
 *   <li>{@link edu.uclouvain.core.nodus.compute.modalsplit.MultinomialLogit} uses a modal intercept
 *       and common or mode-specific coefficients of log cost, with softmax probabilities.
 *   <li>{@link edu.uclouvain.core.nodus.compute.modalsplit.MultinomialProbit} uses the same utility
 *       form with independent normal errors of variance one and joint multinomial probabilities.
 *   <li>{@link edu.uclouvain.core.nodus.compute.modalsplit.Proportional} uses inverse adjusted
 *       cost, with positive modal factors and the reference factor fixed at one.
 * </ul>
 *
 * <p>When a group has no parameters for the selected logit or probit model, assignment warns once
 * and uses {@code V = -C}, with cost factor one and zero constants. Default MNL retains its former
 * modal probabilities and exponential route allocation; default probit uses the same utility and
 * route allocation with normal modal-choice errors. Saved models retain their log-cost utility and
 * inverse-cost route allocation. Incomplete saved coefficients remain errors; defaults are never
 * written into cost files.
 *
 * <p>The numerical estimators consume one commodity group's cost/quantity matrices in {@code [OD
 * row][mode column]} order. Their reference arguments are zero-based column indices, whereas
 * routing, observed-table mappings and exported parameters use Nodus mode IDs. Quantities are
 * frequency weights. Unavailable alternatives require zero observed quantity; the retained choice
 * sets must jointly identify finite coefficients. See the estimator classes for validation,
 * statistical assumptions, uncertainty and cooperative cancellation contracts. Logit and probit
 * estimation defaults to a common cost coefficient (the Conditional checkbox); unchecking it
 * estimates one slope per mode. MNL cost slopes are bounded above by zero, so a fitted zero means
 * no cost sensitivity for that mode. MNP fits still fail if a converged slope is zero or positive.
 * Proportional choice fixes the common slope at -1.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.modalsplit.LogitCalibration} connects these numerical
 * APIs to a project: it reads observed modal matrices, builds scratch demand, routes once per
 * mode/means, retains the cheapest admissible modal costs and fits each commodity group. Scratch
 * tables are removed on close; routing does not publish scenario result tables. Only after all
 * groups succeed are coefficients and optional OD/group pivots written to the named database table.
 * The source file supplies transport costs and receives the {@code @paramTable} pointer. The
 * estimation report is written as {@code <cost-file-stem>_params.txt} in the project directory.
 * Existing tables require confirmation before replacement. Older cost files with embedded
 * coefficients remain readable when they have no table pointer.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.modalsplit.ModalChoiceEstimationDlg} owns the Swing
 * workflow and its background worker. Parameter estimation and assignment are separate operations;
 * estimation never changes the demand selected for a later assignment. Historical names beginning
 * with {@code LogitCalibration} now cover all three supported estimation methods.
 */
package edu.uclouvain.core.nodus.compute.modalsplit;
