/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
 */
package edu.uclouvain.core.nodus.utils;
