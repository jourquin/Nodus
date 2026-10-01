/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Node-level inclusion and exclusion rules for transport-mode transitions.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.rules.NodeRule} matches operations between mode/means
 * pairs for a scenario and commodity group. Wildcard values allow a rule to cover multiple
 * contexts.
 *
 * <p>{@link edu.uclouvain.core.nodus.compute.rules.NodeRulesReader} loads the project rule table
 * into the virtual-network structure and provides table creation and compatibility helpers. The
 * historical term {@code exclusions} also appears in APIs that manage both inclusion and exclusion
 * rules.
 */
package edu.uclouvain.core.nodus.compute.rules;
