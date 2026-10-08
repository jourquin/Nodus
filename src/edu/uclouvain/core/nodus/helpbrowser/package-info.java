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
 * Opening installed user help and API documentation in the system browser.
 *
 * <p>{@link edu.uclouvain.core.nodus.helpbrowser.HelpBrowser} resolves documentation relative to
 * {@code NODUS_HOME}. User help prefers a localized HTML file and falls back to the default help
 * page; API documentation opens the generated {@code api/index.html}. Browser launch uses the Java
 * desktop API.
 */
package edu.uclouvain.core.nodus.helpbrowser;
