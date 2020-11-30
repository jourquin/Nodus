/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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

package com.bbn.openmap.layer.drawing;

import com.bbn.openmap.layer.DrawingToolLayer;

/**
 * This is a "dummy" class used by Nodus. The only reason it exists is that the drawing layer
 * associated to the nodes and links has no utility for the user. So, it is best to hide it.
 *
 * <p>In order to do that, a call to setVisible(false) is not enough, because the layer still
 * appears as grayed in the layer panel. So a subclass of the LayersPanel is created, which
 * tests the presence of a NodusDrawingToolLayer. The instances of this class are not displayed in
 * the panel.
 *
 * @author Bart Jourquin
 */
public class NodusDrawingToolLayer extends DrawingToolLayer {

  private static final long serialVersionUID = 151840316366117657L;
}
