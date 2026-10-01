/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Opening installed user help and API documentation in the system browser.
 *
 * <p>{@link edu.uclouvain.core.nodus.helpbrowser.HelpBrowser} resolves documentation relative to
 * {@code NODUS_HOME}. User help prefers a localized HTML file and falls back to the default help
 * page; API documentation opens the generated {@code api/index.html}. Browser launch uses the Java
 * desktop API.
 */
package edu.uclouvain.core.nodus.helpbrowser;
