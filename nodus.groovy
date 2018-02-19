/*******************************************************************************
 * Copyright (c) 1991-2018 Université catholique de Louvain, 
 * Center for Operations Research and Econometrics (CORE)
 * http://www.uclouvain.be
 * 
 * This file is part of Nodus.
 * 
 * Nodus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

import com.bbn.openmap.proj.coords.LatLonPoint;

/**
 * 
 * Sample "autoexec" Groovy script that presents
 * an European view to the user instead of the World map.
 */

nodusMapPanel.getMapBean().setScale((float) 1.4E7);
nodusMapPanel.getMapBean().setCenter(new LatLonPoint.Double(50.0, 4.0));
nodusMapPanel.getMapBean().validate();
