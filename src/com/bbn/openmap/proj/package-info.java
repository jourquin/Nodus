/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
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
