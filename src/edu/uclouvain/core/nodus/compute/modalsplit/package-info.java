/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
 *       and a common coefficient of log cost, with softmax probabilities.
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
 * statistical assumptions, uncertainty and cooperative cancellation contracts.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.modalsplit.LogitCalibration} connects these numerical
 * APIs to a project: it reads observed modal matrices, builds scratch demand, routes once per
 * mode/means, retains the cheapest admissible modal costs and fits each commodity group. Scratch
 * tables are removed on close; routing does not publish scenario result tables. Only after all
 * groups succeed are coefficients and optional OD/group pivots written to the named database table.
 * The source file supplies transport costs and receives the {@code @paramTable} pointer. The
 * estimation report is written as {@code <cost-file-stem>_params.txt} in the project directory.
 * Existing tables
 * require confirmation before replacement. Older cost files with embedded coefficients remain
 * readable when they have no table pointer.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.modalsplit.ModalChoiceEstimationDlg} owns the Swing
 * workflow and its background worker. Parameter estimation and assignment are separate operations;
 * estimation never changes the demand selected for a later assignment. Historical names beginning
 * with {@code LogitCalibration} now cover all three supported estimation methods.
 */
package edu.uclouvain.core.nodus.compute.modalsplit;
