/*
 * Copyright (c) 1991-2021 Université catholique de Louvain, 
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
 * Sample "autoexec" Groovy script. Three variables are set when this script is called:
 * - startNodus = true and closeNodus = false at startup
 * - startNodus = false and closeNodus = true at closing
 * - nodusMapPanel, the instance of NodusMapPanel of the running Nodus. This is used as entry point to the API 
 */

// Python and R bridges
GatewayServer pyGatewayServer = null
JavaGatewayServer rGatewayServer = null

if (startNodus) {
	
	/* 
	 * Present an European view to the user instead of the World map.
	 */
	nodusMapPanel.getMapBean().setScale((float) 1.4E7);
	nodusMapPanel.getMapBean().setCenter(new LatLonPoint.Double(50.0, 4.0));
	nodusMapPanel.getMapBean().validate();

	/*
	 * Launch a J4R server (for R scripting)
	 */
	try {
		ServerConfiguration servConf =
				new ServerConfiguration(1, 10, new int[] {18000, 18001}, new int[] {50000, 50001}, 212);
		rGatewayServer = new JavaGatewayServer(servConf, nodusMapPanel);
		rGatewayServer.startApplication();
	} catch (Exception e) {
		System.err.println("Could not start R4J bridge. Is another instance of Nodus running?");
	}

	/*
	 * Launch a Py4J server (for Python scripting)
	 */
	try {
		pyGatewayServer = new GatewayServer(nodusMapPanel);
		pyGatewayServer.start();
	} catch (Exception e) {
		System.err.println("Could not start Py4J bridge. Is another instance of Nodus running?");
	}

} else {
	
	/*
	 * Close the J4R server
	 */
	if (rGatewayServer != null) {
		rGatewayServer.shutdown(0);
	}
	
	/*
	 * Close the Py4J server
	 */
	if (pyGatewayServer != null) {
		pyGatewayServer.shutdown();
	}
}
