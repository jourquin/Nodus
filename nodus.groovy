/*
 * Copyright (c) 1991-2025 Université catholique de Louvain, 
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
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */

import com.bbn.openmap.proj.coords.LatLonPoint;
import j4r.net.server.JavaGatewayServer;
import j4r.net.server.ServerConfiguration;
import py4j.GatewayServer;

/**
 * Sample "autoexec" Groovy script. The "nodus.groovy" script, if exists, is run by Nodus at
 * launch time and also just before exit.
 * 
 * Three variables are set by Nodus when this script is called:
 * - startNodus = true and quitNodus = false at startup
 * - startNodus = false and quiteNodus = true at closing
 * - nodusMapPanel, the NodusMapPanel instance of the running Nodus. This is used as entry point 
 *   to the API.
 * 
 * This example shows how to interact with the Nodus API (zoom on the European zone of the 
 * World map). 
 * 
 * The same "autoexec" mechanism exists at the projet level: if a [projetname].groovy script is
 * found in the directory that contains the [projetname].nodus file, it is executed when the 
 * project is loaded and closed. The "openProject", "closeProject" and "nodusMapPanel" variables
 * are then set by Nodus.
 * 
 * Example Groovy, Python and R scripts can be found in the script directory. 
 */

if (startNodus) {
	/* 
	 * Present an European view to the user instead of the World map.
	 */
	nodusMapPanel.getMapBean().setScale((float) 1.4E7);
	nodusMapPanel.getMapBean().setCenter(new LatLonPoint.Double(50.0, 4.0));
	nodusMapPanel.getMapBean().validate();
}
