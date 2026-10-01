/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * WMS capabilities parsing and the server/layer tree used by the imagery chooser.
 *
 * <p>{@link com.bbn.openmap.layer.image.capabilities.WmsCapabilitiesXmlParser} reads server
 * metadata, image formats and nested layer descriptions using SAX callbacks. The tree-node wrappers
 * expose {@link com.bbn.openmap.layer.image.capabilities.WmsLayerInfo} objects to the chooser, with
 * shared server information and inherited layer bounds.
 *
 * <p>The parser reads the older {@code LatLonBoundingBox} representation. Its class documentation
 * records a WMS 1.3 limitation; this package should not be treated as a general implementation of
 * every WMS capabilities format.
 */
package com.bbn.openmap.layer.image.capabilities;
