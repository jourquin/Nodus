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

import j4r.net.server.JavaGatewayServer;
import j4r.net.server.ServerConfiguration;
import py4j.GatewayServer;

/**
 * Sample project specific "autoexec" Groovy script. Such a script must have the same name as the 
 * project itself. If exists, is run by Nodus when the project is open and closed.
 * 
 * Three variables are set by Nodus when a script is called:
 * - openProject = true and closeProject = false at opening
 * - openProject = false and closeProject = true at closing
 * - nodusMapPanel, the NodusMapPanel instance of the running Nodus. This is used as entry point 
 *   to the API.
 * 
 * This example shows how to launch a Py4J and a J4R bridge server, allowing to connect to Nodus from
 * your favorite Python or R environment, providing that the Python Py4J and the J4R packages are
 * installed (the corresponding jar files are provided with Nodus). Launching these servers is not
 * hardcoded in Nodus, allowing for more flexibility in the way they are used (remote access,
 * used ports...). Please refer to the documentation of both packages for more information.
 * 
 * Launching these servers is not mandatory if you don't plan to use Python or R scripts. 
 * If you use only one of these languages, you obvioulsy can decide to launch only the relevant
 * server. 
 *  
 * This script also shows how to store objects for later use in another script (or a subsequent
 * run of the same script. The storeObject(...) and retrieveObject() methods of the Nodud API are
 * here used to store the instances of the Py4J and J4R servers at launch time. Theys are later
 * retrieved for shutdown when the project is closed.
 *
 * 
 * Example Groovy, Python and R scripts can be found in the scripts directory. 
 */

if (openProject) {

	/*
	 * Launch a J4R server (for R scripting). It could also be launched from the [project].groovy
	 * script, with alternative ports for each projet, allowing several instance of Nodus to run
	 * with a server for each open project.
	 */
	try {
		ServerConfiguration servConf =
				new ServerConfiguration(1, 10, new int[] {18000, 18001}, new int[] {50000, 50001}, 212);
		JavaGatewayServer rGatewayServer = new JavaGatewayServer(servConf, nodusMapPanel);
		rGatewayServer.startApplication();
		nodusMapPanel.storeObject("rGatewayServer",rGatewayServer);
	} catch (Exception e) {
		System.err.println("Could not start R4J bridge. Is another instance of Nodus running?");
	}

	/*
	 * Launch a Py4J server (for Python scripting). It could also be launched from the [project].groovy
	 * script, with alternative ports for each projet, allowing several instance of Nodus to run
	 * with a server for each open project.
	 */
	try {
		GatewayServer pyGatewayServer = new GatewayServer(nodusMapPanel);
		pyGatewayServer.start();
		nodusMapPanel.storeObject("pyGatewayServer",pyGatewayServer);
	} catch (Exception e) {
		System.err.println("Could not start Py4J bridge. Is another instance of Nodus running?");
	}

} else {
	
	/*
	 * Close the J4R server
	 */
	JavaGatewayServer rGatewayServer = nodusMapPanel.retrieveObject("rGatewayServer");
	if (rGatewayServer != null) {		
		rGatewayServer.requestShutdown();
	}

	/*
	 * Close the Py4J server
	 */
	GatewayServer pyGatewayServer = nodusMapPanel.retrieveObject("pyGatewayServer");
	if (pyGatewayServer != null) {
		pyGatewayServer.shutdown();
	}
}
