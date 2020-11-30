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

package com.bbn.openmap.tools.drawing;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;

/**
 * Wrapper around the equivalent OpenMap class created in order to use "Link" instead of
 * "Polygons/Polylines" as drawing tool name.
 *
 * @author Bart Jourquin
 */
public class NodusOMPolyLoader extends OMPolyLoader {
  private static I18n i18n = Environment.getI18n();

  /** Initializes the class wrapper. */
  @Override
  public void init() {
    EditClassWrapper ecw =
        new EditClassWrapper(
            graphicClassName,
            "com.bbn.openmap.omGraphics.EditableOMPoly",
            "editablepoly.png",
            i18n.get(NodusOMPolyLoader.class, "Link", "Link"));

    addEditClassWrapper(ecw);
  }
}
