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
 * Shared utilities for plugins, scripts, project files and application support.
 *
 * <p>{@link edu.uclouvain.core.nodus.utils.PluginsLoader}, {@link
 * edu.uclouvain.core.nodus.utils.ModalSplitMethodsLoader} and {@link
 * edu.uclouvain.core.nodus.utils.PluginClassPath} discover extensions and manage their class
 * loaders. {@link edu.uclouvain.core.nodus.utils.ScriptRunner} executes Groovy scripts with
 * supplied variables. Plugin resources must remain available for the lifetime of the loaded
 * extensions.
 *
 * <p>Other helpers cover project locking, file operations, properties with preserved comments,
 * locale and color handling, system information, platform integration, release checks and build
 * metadata. {@link edu.uclouvain.core.nodus.utils.WorkQueue} provides the synchronized job queue
 * used by computation workers. These utilities have separate ownership and error-handling contracts
 * documented by their classes.
 * 
 * <p>{@link edu.uclouvain.core.nodus.utils.NodusPreferences} stores application preferences;
 * {@link edu.uclouvain.core.nodus.utils.BridgeCredentials} prepares optional bridge credentials;
 * {@link edu.uclouvain.core.nodus.utils.ProjectConnectionStatus} exposes the project's network
 * save safety check to layer code.
 */
package edu.uclouvain.core.nodus.utils;
