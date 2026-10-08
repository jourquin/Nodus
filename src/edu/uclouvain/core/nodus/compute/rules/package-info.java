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
