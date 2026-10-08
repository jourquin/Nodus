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
 * Application startup, project lifecycle and the main Nodus extension points.
 *
 * <p>{@link edu.uclouvain.core.nodus.Nodus} starts the application. {@link
 * edu.uclouvain.core.nodus.NodusMapPanel} assembles the map interface, dispatches menu actions and
 * exposes project and map services to tools and plugins.
 *
 * <p>{@link edu.uclouvain.core.nodus.NodusProject} manages a project described by a {@code .nodus}
 * properties file, its network layers, database connection and project settings. {@link
 * edu.uclouvain.core.nodus.NodusC} defines shared constants.
 * <p>Map-panel and project implementation helpers live in the {@link
 * edu.uclouvain.core.nodus.mappanel} and {@link edu.uclouvain.core.nodus.project} packages.
 *
 * <p>{@link edu.uclouvain.core.nodus.NodusPlugin} defines the application-plugin lifecycle.
 * Modal-choice extensions use the separate {@link
 * edu.uclouvain.core.nodus.compute.modalsplit.ModalSplitMethod} contract.
 */
package edu.uclouvain.core.nodus;
