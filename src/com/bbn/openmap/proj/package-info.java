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
 * The Equal Earth map projection and its OpenMap loader.
 *
 * <p>{@link com.bbn.openmap.proj.EqualEarth} implements forward and inverse projection, with
 * helpers used when preparing map geometry for display. {@link
 * com.bbn.openmap.proj.EqualEarthLoader} constructs projections from OpenMap properties so they can
 * be selected by the application.
 *
 * <p>The projection changes map presentation, not the stored geographic coordinates of the
 * transport network. See the projection class for its equations and references.
 */
package com.bbn.openmap.proj;
