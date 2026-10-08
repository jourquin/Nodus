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
