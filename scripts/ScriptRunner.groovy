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


/*
 * This is a sample script that illustrates the possibility to run a R or a Python script.
 * Note that this doesn't allow the launched script to interact with the Nodus API. To do that,
 * use Groovy scripts run from Nodus or Python scripts run from your favorite Python environment
 * using the Py4J brigde. Both Groovy scripts and Python/Py4J scripts have access to the same
 * main entry point to the running Nodus, which is nodusMapPanel, an instance of NodusMapManel.
 */

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.NodusProject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;


// Define the interpreter to run
//String interpreter = "python3";
String interpreter = "/usr/local/bin/RScript";

// Define the script to run, located in the Nodus project directory
//String script = "hello.py"
String script = "hello.R"

private List<String> readProcessOutput(InputStream inputStream) throws IOException {
	try (BufferedReader output = new BufferedReader(new InputStreamReader(inputStream))) {
		return output.lines().collect(Collectors.toList());
	}
}


// Main entry point ///////////////////////////////////////////////////////////////

// Get curent Nodus project
NodusProject nodusProject = nodusMapPanel.getNodusProject()
if (!nodusProject.isOpen()) {
	return;
}

// Retrieve the path to the project
String path =  path = nodusProject.getLocalProperty("project.path");

ProcessBuilder processBuilder = new ProcessBuilder(interpreter, path + script);
processBuilder.redirectErrorStream(true);

int exitCode = 0;
List<String> results = null;
try {
	Process process = processBuilder.start();
	results = readProcessOutput(process.getInputStream());

	exitCode = process.waitFor();
} catch (IOException e) {
	//e.printStackTrace();
} catch (InterruptedException e) {
	//e.printStackTrace();
}

if (exitCode != 0) {
	System.out.println("Some error occurred.");
}

if (results != null) {
	Iterator<String> it = results.iterator();
	while (it.hasNext()) {
		System.out.println(it.next());
	}
}


