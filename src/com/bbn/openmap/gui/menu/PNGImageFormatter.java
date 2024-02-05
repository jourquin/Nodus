/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
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

package com.bbn.openmap.gui.menu;

import com.bbn.openmap.image.PNG8ImageFormatter;
import com.bbn.openmap.image.WMTConstants;

/**
 * Just a convenient class used to display PNG instead of PNG8.
 *
 * @author Bart Jourquin
 */
public class PNGImageFormatter extends PNG8ImageFormatter {

  @Override
  public String getFormatLabel() {
    return WMTConstants.IMAGEFORMAT_PNG;
  }
}
